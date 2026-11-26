package com.onyx.app.calls

import android.content.Context
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.repo.ServerRepository
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.notifications.CallRinger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.security.SecureRandom

/**
 * The bridge for the **agent-to-phone call** (`phone_call_me`).
 *
 * Port of `androidApp/…/phone/CallMeTool.kt` **and** of `shared/core/…/phone/PhoneToolsBridge.kt`
 * from the reference: the app **declares** the call tool to the gateway, **polls** for the calls
 * waiting for it, **rings** — and **returns the outcome** (`answered` / `declined` / `no_answer` / `quiet`).
 *
 * ## What the reference does, and what this bridge redoes
 *
 * The `phone_call_me` tool is **not** a gateway tool: it is the **plugin** installed on the
 * gateway (theirs `herald-push`, ours `onyx-calls`) that declares it to the agent, and the app talks to it
 * through **three commands**:
 *
 * | phone → gateway | what it says | reference |
 * |---|---|---|
 * | `phone tools {…}` | "these tools, I can serve them" (French UI string « ces outils, je peux les servir ») | `PhoneToolsBridge.report` / `PushApi.phoneTools` |
 * | `phone pending {…}` | "what is waiting for me?" (French UI string « qu'est-ce qui m'attend ? ») | `PhoneToolsBridge.drain` / `PushApi.phonePending` |
 * | `phone answer {…}` | "here is the outcome" (French UI string « voilà l'issue ») | `PhoneToolsBridge.answer` / `PushApi.phoneAnswer` |
 *
 * Those three go through the **same gateway command** (`command.dispatch`), the only route
 * a client has to talk to a plugin.
 *
 * Nothing in this file is a lie: if there is no `onyx-calls` plugin on the gateway, the
 * declaration fails, the state says so (`NotInstalled`), and **the call never rings** — that is
 * exactly the batch's rule.
 */
class AgentCallBridge(
    private val context: Context,
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
    /** The thread's name, for the call screen ("who is calling" — French UI string « qui appelle »). */
    private val titleOf: (String) -> String? = { null },
    /** The clock, in seconds — the gateway's, to compare deadlines. */
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {

    /** Where the bridge stands, for the Settings → Phone → Calls screen. */
    data class Status(val state: State = State.Off, val detail: String? = null) {
        enum class State { Off, Offline, Working, NotInstalled, On, Failed }
    }

    /** What the agent requested recently — the page's log, most recent first. */
    data class Use(val tool: String, val at: Long, val outcome: String, val error: String? = null)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _recent = MutableStateFlow<List<Use>>(emptyList())
    val recent: StateFlow<List<Use>> = _recent.asStateFlow()

    private val identity = AgentCallIdentity(context)

    /** The gateway's live session: `command.dispatch` requires it. Set by the app when it has one. */
    @Volatile var sessionId: String? = null

    /** The call that is ringing, if any: we do not ring over it. */
    @Volatile private var ringing: AgentCallRequest? = null

    private var loop: Job? = null
    private var scope: CoroutineScope? = null
    private var channel: ServeRpc? = null
    private var channelFor: String? = null

    /** The already answered ids, so as not to ring twice for the same call. */
    private val answered = ArrayDeque<String>()

    /** Starts the polling loop, carried by [scope] (the process scope). */
    fun start(scope: CoroutineScope) {
        if (loop?.isActive == true) return
        this.scope = scope
        loop = scope.launch {
            while (true) {
                runCatching { pollNow() }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    /**
     * One polling round: declare the tool, then serve what is waiting.
     *
     * Without a connection, without the tool on, or without the plugin: we do **nothing** — never an exception
     * that would bubble back into the loop.
     */
    suspend fun pollNow() {
        val server = servers.activeServer.value
        if (!server.hasServeCredentials) {
            _status.value = Status(if (phoneCallsOn()) Status.State.Offline else Status.State.Off)
            return
        }
        if (!phoneCallsOn()) {
            _status.value = Status(Status.State.Off)
            return
        }
        _status.value = Status(Status.State.Working)
        val rpc = serve(server)
        try {
            // 1) Declare the tool: the agent only sees it once declared.
            val declared = command(
                rpc,
                "phone tools " + AgentCallProtocol.toolsReport(
                    deviceId = identity.deviceId,
                    key = identity.key,
                    name = identity.name,
                    tools = listOf(AgentCallTool.CALL_ME),
                ),
            )
            if (declared == null) {
                _status.value = Status(
                    Status.State.NotInstalled,
                    "Le plugin ${AgentCallTool.PLUGIN_COMMAND} n'est pas installé sur le gateway.",
                )
                return
            }
            _status.value = Status(Status.State.On)
            // 2) Serve what is waiting.
            val pending = command(
                rpc,
                "phone pending " + AgentCallProtocol.pendingQuery(identity.deviceId, identity.key),
            ) ?: return
            serveRequests(rpc, AgentCallProtocol.parsePending(pending, nowSeconds()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = Status(Status.State.Failed, humanFailure(e))
        }
    }

    /** Rings, or declines right away: quiet hours do not ring. */
    private suspend fun serveRequests(rpc: ServeRpc, requests: List<AgentCallRequest>) {
        for (request in requests) {
            if (request.id in answered) continue
            // A call is already ringing: the agent receives "busy" (French UI string « occupé »), we do not ring over it.
            if (ringing != null) {
                answer(rpc, request.id, AgentCallAnswer(CallOutcome.BUSY))
                continue
            }
            val quiet = settings.quietNow()
            val silent = AgentCallProtocol.silentAnswer(request, quiet, settings.quietUntil.value)
            if (silent != null) {
                markAnswered(request.id)
                record(request.tool, silent.status)
                answer(rpc, request.id, silent)
                continue
            }
            ring(rpc, request)
        }
    }

    /** Rings like an incoming call, and returns the outcome as soon as it lands. */
    private fun ring(rpc: ServeRpc, request: AgentCallRequest) {
        val reason = AgentCallProtocol.reasonOf(request)
        ringing = request
        val rung = CallRinger.ring(
            context,
            CallRinger.Ringing(
                id = request.id,
                title = titleOf(request.session) ?: "Onyx",
                why = reason.reason,
                talk = settings.phoneRingsTalk.value,
                conversationId = request.session.takeIf { it.isNotBlank() },
                onOutcome = { outcome ->
                    ringing = null
                    markAnswered(request.id)
                    record(request.tool, outcome)
                    sendOnScope { answer(rpc, request.id, AgentCallAnswer(outcome)) }
                },
            ),
        )
        if (!rung) {
            // The app cannot post a notification: the agent must know it, not believe in silence.
            ringing = null
            markAnswered(request.id)
            record(request.tool, CallOutcome.NO_ANSWER, "l'app ne peut pas sonner")
            sendOnScope { answerError(rpc, request.id, "Le téléphone ne peut pas faire sonner d'appel.") }
        }
    }

    /** Sends background work in the bridge's scope — picking up closes the screen, the loop still lives. */
    private fun sendOnScope(block: suspend () -> Unit) {
        val scope = scope
        if (scope != null) scope.launch { runCatching { block() } }
    }

    private fun markAnswered(id: String) {
        answered.addLast(id)
        while (answered.size > MAX_ANSWERED) answered.removeFirst()
    }

    private fun record(tool: String, outcome: CallOutcome?, error: String? = null) {
        _recent.update { (listOf(Use(tool, nowSeconds(), outcome?.id ?: "error", error)) + it).take(MAX_RECENT) }
    }

    /** Is the call tool on? ("Calls for an answer" — French UI string « Appels pour une réponse » — as in the reference.) */
    private fun phoneCallsOn(): Boolean = settings.phoneRings.value

    private suspend fun answer(rpc: ServeRpc, requestId: String, answer: AgentCallAnswer) {
        command(rpc, "phone answer " + AgentCallProtocol.answerBody(identity.deviceId, identity.key, requestId, answer))
    }

    private suspend fun answerError(rpc: ServeRpc, requestId: String, error: String) {
        command(rpc, "phone answer " + AgentCallProtocol.errorBody(identity.deviceId, identity.key, requestId, error))
    }

    /**
     * The only client → plugin route: `command.dispatch`. Returns the plugin's `output`, or `null`
     * when there is no plugin (error 4018, "not a command" — French UI string « pas une commande »).
     */
    private suspend fun command(rpc: ServeRpc, argument: String): String? {
        val reply = runCatching {
            rpc.call(
                method = "command.dispatch",
                params = buildJsonObject {
                    put("session_id", sessionId ?: "")
                    put("name", AgentCallTool.PLUGIN_COMMAND)
                    put("arg", argument)
                },
                timeoutMillis = 20_000,
            )
        }.getOrNull() as? JsonObject ?: return null
        val type = (reply["type"] as? JsonPrimitive)?.contentOrNull
        if (type != "plugin") return null
        return (reply["output"] as? JsonPrimitive)?.contentOrNull
    }

    private fun serve(server: ServerConfig): ServeRpc {
        val url = server.serveBase
        if (channelFor != url || channel == null) {
            channel?.close()
            channel = ServeRpc(server)
            channelFor = url
        }
        return channel!!
    }

    companion object {
        /** For a conversation the app is not watching: the interval between two checks. */
        const val POLL_MS = 15_000L
        private const val MAX_RECENT = 20
        private const val MAX_ANSWERED = 200
    }
}

/**
 * The phone's identity for the tools: an id and a secret, made once and kept.
 *
 * Taken from `PhoneToolsBridge.identity` (a pair made the first time, never regenerated).
 */
class AgentCallIdentity(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_agent_calls", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    val deviceId: String get() = stored(KEY_ID, ID_BYTES)
    val key: String get() = stored(KEY_KEY, KEY_BYTES)

    /** The name the gateway lists for this phone. */
    var name: String
        get() = prefs.getString(KEY_NAME, null).orEmpty().ifBlank { "Onyx" }
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    private fun stored(key: String, bytes: Int): String {
        prefs.getString(key, null)?.takeIf { it.length == bytes * 2 }?.let { return it }
        val made = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        prefs.edit().putString(key, made).apply()
        return made
    }

    private companion object {
        const val KEY_ID = "device_id"
        const val KEY_KEY = "key"
        const val KEY_NAME = "name"
        const val ID_BYTES = 16
        const val KEY_BYTES = 32
    }
}
