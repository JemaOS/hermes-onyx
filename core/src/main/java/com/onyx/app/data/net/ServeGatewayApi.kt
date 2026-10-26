package com.onyx.app.data.net

import com.onyx.app.data.model.serve.GatewayConnection
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.data.model.serve.GatewayStatus
import com.onyx.app.data.model.serve.GatewayTrouble
import com.onyx.app.data.model.serve.LogLevel
import com.onyx.app.data.model.serve.McpProbe
import com.onyx.app.data.model.serve.McpServer
import com.onyx.app.data.model.serve.ServeLogLine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les routes d'administration du gateway (`hermes serve`).
 *
 * Comme et: même ordre de lecture,
 * mêmes routes, et les mêmes tolérances sur les noms de champs.
 */
class ServeGatewayApi(private val client: ServeClient) {

    /** `GET api/status` + `GET api/system/stats`: l'état, les connexions, les problèmes. */
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
                // Le gateway Hermes répond `gateway_running` à la racine de `api/status`:
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
        // (`api`, `telegram`, …); `platforms` reste accepté pour d'autres serveurs.
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

    /** `GET api/mcp/servers`. */
    suspend fun mcpServers(): List<McpServer> =
        client.request("api/mcp/servers")
            .asObject()
            ?.array("servers")
            .orEmpty()
            .mapNotNull { element ->
                val obj = element.asObject() ?: return@mapNotNull null
                val name = obj.firstString("name", "id") ?: return@mapNotNull null
                McpServer(
                    name = name,
                    target = obj.firstString("target", "command", "url").orEmpty(),
                    enabled = obj["enabled"].asBool() ?: true,
                    plugin = obj.strOrNull("plugin"),
                )
            }

    /** `PUT api/mcp/servers/{name}/enabled`. */
    suspend fun setMcpEnabled(name: String, enabled: Boolean) {
        client.request(
            path = "api/mcp/servers/$name/enabled",
            method = "PUT",
            body = buildJsonObject { put("enabled", enabled) },
        )
    }

    /** `POST api/mcp/servers/{name}/test` — 90 s, le temps qu'Hermes se connecte. */
    suspend fun testMcp(name: String): McpProbe {
        val payload = runCatching {
            client.request(
                path = "api/mcp/servers/$name/test",
                method = "POST",
                body = client.emptyBody(),
                timeoutSeconds = 90,
            ).asObject()
        }.getOrElse { error ->
            return McpProbe(ok = false, summary = "", error = (error as? TransportError)?.userMessage() ?: error.message)
        }
        val ok = payload?.get("ok").asBool() ?: true
        val tools = payload?.int("tools") ?: payload?.array("tool_names")?.size ?: 0
        val summary = when {
            !ok -> payload?.firstString("error", "summary") ?: "Not connected"
            tools > 0 -> "Connected · $tools tools"
            else -> payload?.firstString("summary") ?: "Connected"
        }
        return McpProbe(ok = ok, summary = summary, error = if (ok) null else summary)
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

    /** Le niveau d'une ligne, deviné de son texte — comme la coloration. */
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

    /** `GET api/hermes/update/check`: la version disponible, s'il y en a une. */
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
