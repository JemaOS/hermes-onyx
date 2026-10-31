package com.onyx.app.data.net

import com.onyx.app.data.model.serve.GatewayConnection
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.data.model.serve.GatewayStatus
import com.onyx.app.data.model.serve.GatewayTrouble
import com.onyx.app.data.model.serve.LogLevel
import com.onyx.app.data.model.serve.McpProbe
import com.onyx.app.data.model.serve.McpServer
import com.onyx.app.data.model.serve.McpServerDraft
import com.onyx.app.data.model.serve.McpTool
import com.onyx.app.data.model.serve.ServeLogLine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les routes d'administration du gateway (`hermes serve`).
 *
 * Miroir de `Echo/Services/GatewayAdmin` et `GatewayModel` : même ordre de lecture,
 * mêmes routes, et les mêmes tolérances sur les noms de champs.
 */
class ServeGatewayApi(private val client: ServeClient) {

    /** `GET api/status` + `GET api/system/stats` : l'état, les connexions, les problèmes. */
    suspend fun status(): GatewayStatus {
        val status = runCatching { client.request("api/status").asObject() }.getOrNull()
            ?: throw TransportError.Malformed(OnyxLocalization["api/status returned nothing"])
        val stats = runCatching { client.request("api/system/stats").asObject() }.getOrNull()

        val hermes = status.obj("hermes")
        val gateway = status.obj("gateway")
        val troubles = status.array("troubles").mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val id = obj.firstString("id", "name") ?: return@mapNotNull null
            GatewayTrouble(id = id, status = obj.str("status"))
        }

        return GatewayStatus(
            version = hermes?.firstString("version") ?: status.str("version"),
            releaseDate = hermes?.firstString("release_date", "releaseDate") ?: status.str("release_date"),
            gatewayOk = gateway?.get("ok")?.asBool()
                ?: gateway?.get("running")?.asBool()
                // Le gateway Hermes répond `gateway_running` à la racine de `api/status` :
                // l'omettre faisait afficher « Not running » alors que tout tournait.
                ?: status["gateway_running"].asBool()
                ?: status["gateway_ok"].asBool()
                ?: (status.str("gateway").equals("running", ignoreCase = true)),
            exitReason = gateway?.strOrNull("exit_reason") ?: gateway?.strOrNull("exitReason"),
            turnsRunning = status["turns_running"].asInt() ?: status.int("turnsRunning") ?: 0,
            host = stats?.firstString("host", "hostname") ?: status.firstString("host"),
            system = stats?.firstString("system", "platform", "os"),
            upFor = stats?.firstString("uptime", "up_for", "up"),
            memory = stats?.firstString("memory", "memory_used", "memoryInUse"),
            disk = stats?.firstString("disk", "disk_used", "diskInUse"),
            troubles = troubles,
            connections = connections(status),
        )
    }

    /** Les plateformes connectées, quand le serveur les liste. */
    private fun connections(status: JsonObject): List<GatewayConnection> {
        // `api/status` du gateway Hermes liste ses plateformes sous `gateway_platforms`
        // (`api`, `telegram`, …) ; `platforms` reste accepté pour d'autres serveurs.
        val platforms = status.obj("platforms") ?: status.obj("gateway_platforms") ?: return emptyList()
        return platforms.keys.map { key ->
            val value = platforms[key]
            val obj = value.asObject()
            GatewayConnection(
                title = platformTitle(key),
                state = obj?.firstString("state", "status") ?: value.asString().orEmpty(),
                error = obj?.strOrNull("error"),
            )
        }
    }

    private fun platformTitle(key: String): String = when (key) {
        "api", "hermes", "hermes_api" -> "Hermes API"
        "whatsapp" -> "WhatsApp"
        "imessage", "iMessage" -> "iMessage"
        "sms" -> "SMS"
        "home_assistant", "homeassistant" -> "Home Assistant"
        "bluebubbles" -> "BlueBubbles"
        else -> key.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    /**
     * `GET api/mcp/servers` — chaque serveur avec sa **forme réelle** : le transport, l'adresse ou
     * la commande, ses arguments, ses variables d'environnement (masquées par le serveur pour ce
     * qui a l'air d'un secret), son mode d'authentification et ses outils restreints.
     *
     * La réponse réelle de la route porte ces champs (`_mcp_server_summary` du Dashboard) : les
     * lire est ce qui permet à la fiche d'un serveur de dire *comment* il se connecte, et pas
     * seulement son nom.
     */
    suspend fun mcpServers(): List<McpServer> =
        client.request("api/mcp/servers")
            .asObject()
            ?.array("servers")
            .orEmpty()
            .mapNotNull { element -> element.asObject()?.let(::mcpServer) }

    /** Un serveur MCP lu d'un objet JSON : la forme rendue par `GET`/`POST api/mcp/servers`. */
    private fun mcpServer(obj: JsonObject): McpServer? {
        val name = obj.firstString("name", "id") ?: return null
        val url = obj.strOrNull("url")
        val command = obj.strOrNull("command")
        val args = obj.stringList("args")
        val transport = obj.firstString("transport")
            ?: if (url != null) "http" else if (command != null) "stdio" else "unknown"
        return McpServer(
            name = name,
            target = url ?: listOfNotNull(command, *args.toTypedArray()).joinToString(" "),
            enabled = obj["enabled"].asBool() ?: true,
            plugin = obj.strOrNull("plugin"),
            transport = transport,
            url = url,
            command = command,
            args = args,
            env = obj.obj("env")?.let { entry ->
                entry.keys.mapNotNull { key -> entry[key]?.asString()?.let { key to it } }.toMap()
            }.orEmpty(),
            auth = obj.strOrNull("auth"),
            // `tools` est la sélection d'outils : une liste de noms, ou `null` (tous).
            toolSelection = obj["tools"]?.asArray()?.mapNotNull { it.asString() },
            source = obj.firstString("source") ?: if (obj.strOrNull("plugin") != null) "plugin" else "config",
        )
    }

    /**
     * `POST api/mcp/servers` — ajoute un serveur à la configuration du profil.
     *
     * Le serveur valide (un seul transport, cohérence de l'authentification, commande non
     * douteuse) et rend **le serveur tel qu'il l'a écrit**, ce qui évite de deviner un état.
     * Une entrée refusée lève : l'appelant lit le code et le corps pour dire la vraie raison.
     */
    suspend fun addMcpServer(draft: McpServerDraft): McpServer {
        val payload = client.request(
            path = "api/mcp/servers",
            method = "POST",
            body = buildJsonObject {
                put("name", draft.name.trim())
                when {
                    draft.transport == "http" -> {
                        put("url", draft.url.trim())
                        put("auth", draft.auth)
                        // Un jeton n'est envoyé que pour l'authentification par en-tête : pour
                        // « oauth », c'est la route `/auth` qui l'obtiendra, et pour « none » il
                        // n'y a rien à porter.
                        draft.bearerToken.trim().takeIf { it.isNotEmpty() && draft.auth == "header" }
                            ?.let { put("bearer_token", it) }
                    }
                    else -> {
                        put("command", draft.command.trim())
                        if (draft.argumentList.isNotEmpty()) {
                            put("args", buildJsonArray { draft.argumentList.forEach { add(JsonPrimitive(it)) } })
                        }
                        if (draft.environment.isNotEmpty()) {
                            put("env", buildJsonObject { draft.environment.forEach { (k, v) -> put(k, v) } })
                        }
                    }
                }
            },
        )
        return payload.asObject()?.let(::mcpServer)
            ?: McpServer(name = draft.name.trim(), enabled = true)
    }

    /** `DELETE api/mcp/servers/{name}` — retire un serveur de la configuration. */
    suspend fun removeMcpServer(name: String) {
        client.delete("api/mcp/servers/$name")
    }

    /** `PUT api/mcp/servers/{name}/enabled`. */
    suspend fun setMcpEnabled(name: String, enabled: Boolean) {
        client.request(
            path = "api/mcp/servers/$name/enabled",
            method = "PUT",
            body = buildJsonObject { put("enabled", enabled) },
        )
    }

    /**
     * `POST api/mcp/servers/{name}/test` — 90 s, le temps qu'Hermes se connecte.
     *
     * La route rend `{"ok":true,"tools":[{"name","description"}],"prompts":n,"resources":n}` ou
     * `{"ok":false,"error":"…"}` **avec un code 200** : un échec de connexion n'est pas une erreur
     * HTTP, et le lire comme tel est ce qui évite d'annoncer une réussite qui n'a pas eu lieu.
     * Le temps du test se mesure ici : le serveur ne le rend pas.
     */
    suspend fun testMcp(name: String): McpProbe {
        val started = System.nanoTime()
        val payload = runCatching {
            client.request(
                path = "api/mcp/servers/$name/test",
                method = "POST",
                body = client.emptyBody(),
                timeoutSeconds = 90,
            ).asObject()
        }.getOrElse { error ->
            val elapsed = (System.nanoTime() - started) / 1_000_000
            return McpProbe(
                ok = false,
                summary = "",
                error = (error as? TransportError)?.userMessage() ?: error.message,
                latencyMillis = elapsed,
            )
        }
        val latency = (System.nanoTime() - started) / 1_000_000
        val ok = payload?.get("ok").asBool() ?: true
        // Les outils se lisent dans trois formes rencontrées : la liste des outils du Dashboard
        // (`{"name":…,"description":…}`), la liste de leurs noms, ou un compte déjà calculé.
        val tools = payload?.array("tools").orEmpty().mapNotNull { element ->
            when {
                element.asString() != null -> McpTool(element.asString().orEmpty())
                element.asObject() != null -> {
                    val obj = element.asObject() ?: return@mapNotNull null
                    val toolName = obj.firstString("name") ?: return@mapNotNull null
                    McpTool(toolName, obj.str("description"))
                }
                else -> null
            }
        }
        val counted = payload?.int("tools") ?: tools.size
        val summary = when {
            !ok -> payload?.firstString("error", "summary") ?: "Not connected"
            counted > 0 -> "Connected · $counted tools"
            else -> payload?.firstString("summary") ?: "Connected"
        }
        return McpProbe(
            ok = ok,
            summary = summary,
            error = if (ok) null else summary,
            tools = if (ok) tools else emptyList(),
            prompts = payload?.int("prompts") ?: 0,
            resources = payload?.int("resources") ?: 0,
            latencyMillis = latency,
        )
    }

    /** `GET api/logs?file=…&lines=…&level=…&search=…`. */
    suspend fun logs(
        file: String = "agent",
        lines: Int = 400,
        level: String? = null,
        search: String? = null,
    ): List<ServeLogLine> {
        val query = buildMap {
            put("file", file)
            put("lines", lines.toString())
            level?.let { put("level", it) }
            search?.takeIf { it.isNotBlank() }?.let { put("search", it) }
        }
        val payload = client.request("api/logs", query = query)
        val raw = payload.asObject()?.array("lines")
            ?: payload.asArray()
        return raw.orEmpty().mapNotNull { element ->
            val text = element.asString()
                ?: element.asObject()?.firstString("text", "line", "message")
                ?: return@mapNotNull null
            ServeLogLine(text = text, level = levelOf(text))
        }
    }

    /** Le niveau d'une ligne, deviné de son texte — comme la coloration de la référence. */
    private fun levelOf(text: String): LogLevel = when {
        text.contains("ERROR", ignoreCase = true) || text.contains("Traceback") ||
            text.contains("Exception") -> LogLevel.ERROR
        text.contains("WARN", ignoreCase = true) -> LogLevel.WARNING
        else -> LogLevel.PLAIN
    }

    /** `POST api/gateway/restart`. */
    suspend fun restartGateway() {
        client.request("api/gateway/restart", method = "POST", body = client.emptyBody(), timeoutSeconds = 60)
    }

    /** Les dernières lignes d'une action longue (`gateway-restart`, `hermes-update`). */
    suspend fun actionStatus(action: String, lines: Int = 40): List<String> {
        val payload = client.request("api/actions/$action/status", query = mapOf("lines" to lines.toString()))
        val obj = payload.asObject()
        return obj?.array("lines").orEmpty().mapNotNull { it.asString() ?: it.asObject()?.firstString("text") }
    }

    /** `GET api/hermes/update/check` : la version disponible, s'il y en a une. */
    suspend fun checkUpdate(): UpdateInfo? {
        val payload = client.request("api/hermes/update/check").asObject() ?: return null
        val available = payload.get("available").asBool() ?: payload.get("update_available").asBool() ?: false
        val latest = payload.firstString("latest", "latest_version", "version")
        if (!available && latest == null) return null
        return UpdateInfo(
            available = available,
            currentVersion = payload.firstString("current", "current_version").orEmpty(),
            latestVersion = latest.orEmpty(),
            summary = payload.firstString("summary", "message").orEmpty(),
        )
    }

    /** `POST api/hermes/update`. */
    suspend fun updateHermes() {
        client.request("api/hermes/update", method = "POST", body = client.emptyBody(), timeoutSeconds = 60)
    }

    data class UpdateInfo(
        val available: Boolean,
        val currentVersion: String,
        val latestVersion: String,
        val summary: String,
    )
}
