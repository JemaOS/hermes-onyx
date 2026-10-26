package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Le canal JSON-RPC du Dashboard Hermes.
 *
 * Comme `HermesServeClient.call`: un ticket à usage unique
 * (`POST api/auth/ws-ticket`), puis `ws(s)://…/api/ws?ticket=…` et des trames JSON-RPC 2.0.
 * C'est par là que passent les commandes `/`, l'écriture d'un profil de bot et les réponses aux
 * interruptions — les routes REST ne les portent pas.
 */
class ServeRpc(
    private val config: ServerConfig,
    /**
     * Le même bocal à cookies que le client REST: le ticket est demandé avec la session ouverte
     * par le login, sinon le Dashboard le refuse.
     */
    private val httpClient: OkHttpClient = ServeClient.clientFor(config),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()

    /**
     * Les abonnés aux notifications du serveur (`method == "event"`).
     *
     * Le Dashboard pousse les événements d'un tour (`message.delta`, `message.complete`,
     * `session.info`, `tool.start`…) sur le même socket que les réponses RPC: ce sont des
     * trames sans `id`, que le canal ignorait jusqu'ici. C'est par là que passe le chat du
     * transport Dashboard (voir [HermesServeChat]).
     */
    private val listeners = ConcurrentHashMap<String, (ServeEvent) -> Unit>()

    /** S'abonne aux notifications du serveur; rend la clé à passer à [removeListener]. */
    fun addListener(listener: (ServeEvent) -> Unit): String {
        val key = UUID.randomUUID().toString()
        listeners[key] = listener
        return key
    }

    fun removeListener(key: String) {
        listeners.remove(key)
    }

    /** Distribue une notification à tous les abonnés; un abonné fautif ne bloque pas les autres. */
    private fun notify(event: ServeEvent) {
        for (listener in listeners.values) {
            runCatching { listener(event) }
        }
    }

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var ready = false

    val isReady: Boolean get() = ready && socket != null

    /** Ouvre le canal si besoin. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        // Le ticket est demandé avec la session du Dashboard: on s'assure d'abord qu'elle est
        // ouverte (bocal à cookies partagé par tout le processus). Un Dashboard sans login
        // exigé passe la sonde de `login` sans rien faire; l'échec éventuel laisse le ticket
        // parler de lui-même.
        runCatching { ServeClient(config).login() }
        val ticket = ticket()
        val base = config.serveBase
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> "ws://$base"
        }
        val url = "$wsBase/api/ws?ticket=$ticket"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()

        val opened = CompletableDeferred<Boolean>()
        val client = httpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                ready = true
                opened.complete(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                val id = frame["id"]?.jsonPrimitive?.content
                if (id != null) {
                    val waiting = pending.remove(id) ?: return
                    val error = frame["error"]?.jsonObject
                    if (error != null) {
                        waiting.completeExceptionally(
                            TransportError.Rpc(
                                code = error["code"]?.jsonPrimitive?.intOrNull ?: 0,
                                detail = error["message"]?.jsonPrimitive?.content ?: "RPC error",
                            ),
                        )
                    } else {
                        waiting.complete(frame["result"] ?: JsonObject(emptyMap()))
                    }
                    return
                }
                // Notification du serveur: une trame d'événement, sans `id`.
                if (frame["method"]?.asString() != "event") return
                val params = frame["params"].asObject() ?: return
                val event = ServeEvent(
                    type = params["type"].asString().orEmpty(),
                    sessionId = params["session_id"].asString(),
                    payload = params["payload"].asObject() ?: JsonObject(emptyMap()),
                )
                if (event.type.isEmpty()) return
                notify(event)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                ready = false
                pending.values.forEach { it.completeExceptionally(TransportError.from(t, afterHeaders = true)) }
                pending.clear()
                opened.complete(false)
                notify(ServeEvent("connection.lost", null, JsonObject(emptyMap())))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                ready = false
                opened.complete(false)
                notify(ServeEvent("connection.lost", null, JsonObject(emptyMap())))
            }
        }

        client.newWebSocket(request, listener)
        withTimeoutOrNull(10_000) { opened.await() }
    }

    /** Un appel JSON-RPC; le profil est ajouté aux paramètres comme. */
    suspend fun call(method: String, params: JsonObject = buildJsonObject { }, timeoutMillis: Long = 60_000): JsonElement {
        connect()
        val webSocket = socket ?: throw TransportError.Unreachable(OnyxLocalization["no channel to the Dashboard"])

        val id = "onyx-${nextId.getAndIncrement()}"
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred

        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", withProfile(method, params))
        }
        if (!webSocket.send(frame.toString())) {
            pending.remove(id)
            throw TransportError.Unreachable(OnyxLocalization["the channel closed before sending"])
        }

        return withTimeoutOrNull(timeoutMillis) { deferred.await() }
            ?: run {
                pending.remove(id)
                throw TransportError.Malformed(OnyxLocalization.format("%1\$s did not answer", method))
            }
    }

    fun close() {
        runCatching { socket?.close(1000, "closing") }
        socket = null
        ready = false
    }

    private suspend fun ticket(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(config.serveBase + "/api/auth/ws-ticket")
            .post("{}".toRequestBody())
            .header("Content-Type", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw TransportError.Http(response.code, "")
                val text = response.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                obj?.get("ticket")?.jsonPrimitive?.content
                    ?: throw TransportError.Malformed(OnyxLocalization["unreadable ticket"])
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = false)
        }
    }

    /**
     * Le profil, sur les seules méthodes qui l'acceptent: les autres refusent une clé inconnue.
     */
    private fun withProfile(method: String, params: JsonObject): JsonObject {
        val profile = config.effectiveProfile ?: return params
        if (method !in PROFILE_SCOPED) return params
        return buildJsonObject {
            params.forEach { (key, value) -> put(key, value) }
            put("profile", profile)
        }
    }

    private fun String.toRequestBody() = this.toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        /** Les méthodes du gateway qui prennent un profil; les autres rejettent la clé. */
        val PROFILE_SCOPED = setOf(
            "chat.send", "session.create", "session.list", "session.resume",
            "complete.slash", "skills.list", "skills.read", "tools.list",
            "cron.list", "files.list", "files.read", "files.write",
        )
    }
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Une notification du Dashboard (`method == "event"`).
 *
 * `type`: `message.delta`, `reasoning.delta`, `tool.start`, `tool.complete`, `status.update`,
 * `message.complete`, `session.info`, `error`… `sessionId`: la session runtime concernée.
 * `payload`: le corps varié de l'événement (texte, usage, drapeaux), lu à la demande.
 */
data class ServeEvent(
    val type: String,
    val sessionId: String?,
    val payload: JsonObject,
)
