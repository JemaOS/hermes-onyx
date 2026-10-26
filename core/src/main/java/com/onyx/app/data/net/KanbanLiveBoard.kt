package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Les changements d'une trame du socket d'événements du plugin kanban.
 *
 * Les trames sont `{"events":[{id, task_id, kind, payload, created_at}], "cursor": N}`.
 */
data class KanbanEventFrame(
    /** Le curseur à reprendre (`since=`) à la reconnexion. */
    val cursor: Int?,
    /** Les cartes touchées; vide avec [hadEvents] veut dire « relis le tableau ». */
    val taskIds: List<String>,
    /** La trame portait des événements, même sans identifiant de carte. */
    val hadEvents: Boolean,
)

/**
 * Décode une trame d'événements du tableau, ou `null` pour tout ce qui n'en est pas une.
 *
 * Comme le décodage de `LiveBoard.run`: tant que la clé `events` est là, la trame
 * demande une relecture, même quand elle ne nomme aucune carte.
 */
fun kanbanEventFrame(text: String): KanbanEventFrame? {
    val root = runCatching { KANBAN_JSON.parseToJsonElement(text) }.getOrNull()?.asObject() ?: return null
    val events = root["events"] as? kotlinx.serialization.json.JsonArray ?: return null
    return KanbanEventFrame(
        cursor = root.int("cursor"),
        taskIds = events.mapNotNull { it.asObject()?.firstString("task_id", "taskId") },
        hadEvents = true,
    )
}

/**
 * Les changements du tableau en direct (`api/plugins/kanban/events`).
 *
 * Comme `KanbanClient.LiveBoard`: le socket
 * reprend au curseur reçu du dernier `board`, et quand il ne peut pas s'ouvrir — connexion par
 * OAuth seule, passerelle ancienne — l'interrogation périodique prend le relais avec une
 * réouverture de plus en plus espacée (2 s, puis 15 s au plus).
 */
class KanbanLiveBoard(
    private val config: ServerConfig,
    private val client: OkHttpClient = ServeClient.clientFor(config),
) {
    private val rest = ServeClient(config, client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isLive = MutableStateFlow(false)

    /** `true` tant que le socket est ouvert: l'en-tête montre alors la diffusion. */
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private var job: Job? = null

    /** Ouvre le socket depuis [since]; [onChange] reçoit les cartes touchées, vide = tout relire. */
    fun start(since: Int, onChange: (List<String>) -> Unit) {
        stop()
        job = scope.launch { loop(since, onChange) }
    }

    fun stop() {
        job?.cancel()
        job = null
        _isLive.value = false
    }

    /** L'écran est quitté: plus rien à tenir. */
    fun close() {
        stop()
        if (scope.isActive) scope.cancel()
    }

    private suspend fun loop(since: Int, onChange: (List<String>) -> Unit) {
        var cursor = since
        var backoff = 5.0
        while (scope.isActive) {
            val socket = runCatching { open(cursor) }.getOrNull()
            if (socket == null) {
                // Refusé: on réessaie de plus en plus lentement, **sans** réveiller un
                // rafraîchissement — c'est ce réveil à chaque échec qui faisait clignoter
                // l'indicateur en boucle sur le tableau.
                _isLive.value = false
                delay(millis(backoff))
                backoff = (backoff * 2).coerceAtMost(15.0)
                continue
            }
            _isLive.value = true
            backoff = 5.0
            try {
                while (true) {
                    val frame = kanbanEventFrame(socket.frames.receive()) ?: continue
                    frame.cursor?.let { cursor = maxOf(cursor, it) }
                    if (frame.hadEvents) onChange(frame.taskIds)
                }
            } catch (cancelled: CancellationException) {
                socket.close()
                throw cancelled
            } catch (error: Exception) {
                _isLive.value = false
                delay(millis(backoff))
                backoff = (backoff * 2).coerceAtMost(15.0)
            } finally {
                socket.close()
            }
        }
    }

    private suspend fun open(since: Int): LiveSocket {
        val ticket = rest.socketTicket()
        val url = "${webSocketBase(config.serveBase)}/$EVENTS?since=$since&ticket=" +
            URLEncoder.encode(ticket, "UTF-8")
        val frames = Channel<String>(Channel.UNLIMITED)
        val opened = CompletableDeferred<Boolean>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                frames.trySend(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.complete(false)
                frames.close(IOException(t.message ?: "kanban events socket failed"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                frames.close()
            }
        }
        val socket = client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newWebSocket(
                Request.Builder().url(url).header("User-Agent", OnyxHttp.USER_AGENT).build(),
                listener,
            )
        if (withTimeoutOrNull(10_000) { opened.await() } != true) {
            runCatching { socket.close(1000, null) }
            throw IOException("kanban events socket refused")
        }
        return LiveSocket(socket, frames)
    }

    private class LiveSocket(val socket: WebSocket, val frames: Channel<String>) {
        fun close() {
            runCatching { socket.close(1000, "closing") }
        }
    }
}

private fun millis(seconds: Double): Long = (seconds * 1000).toLong()

private const val EVENTS = "api/plugins/kanban/events"

private val KANBAN_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

/** `https://hôte` → `wss://hôte`: les sockets passent par le même hôte que le REST. */
internal fun webSocketBase(base: String): String = when {
    base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
    base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
    else -> "ws://$base"
}
