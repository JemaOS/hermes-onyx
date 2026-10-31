package com.onyx.app.calls

import android.content.Context
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.model.ServerConfig
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
 * Le pont de l'**appel de l'agent vers le téléphone** (`phone_call_me`).
 *
 * Port de `androidApp/…/phone/CallMeTool.kt` **et** de `shared/core/…/phone/PhoneToolsBridge.kt`
 * de la référence : l'app **déclare** au gateway l'outil d'appel, **interroge** les appels qui
 * l'attendent, **sonne** — et **rend l'issue** (`answered` / `declined` / `no_answer` / `quiet`).
 *
 * ## Ce que la référence fait, et que ce pont refait
 *
 * L'outil `phone_call_me` n'est **pas** un outil du gateway : c'est le **plugin** installé sur le
 * gateway (chez elle `herald-push`, ici `onyx-calls`) qui le déclare à l'agent, et l'app lui parle
 * par **trois commandes** :
 *
 * | téléphone → gateway | ce que ça dit | référence |
 * |---|---|---|
 * | `phone tools {…}` | « ces outils, je peux les servir » | `PhoneToolsBridge.report` / `PushApi.phoneTools` |
 * | `phone pending {…}` | « qu'est-ce qui m'attend ? » | `PhoneToolsBridge.drain` / `PushApi.phonePending` |
 * | `phone answer {…}` | « voilà l'issue » | `PhoneToolsBridge.answer` / `PushApi.phoneAnswer` |
 *
 * Ces trois-là passent par la **même commande du gateway** (`command.dispatch`), la seule route
 * qu'un client a pour parler à un plugin.
 *
 * Rien de ce fichier n'est un mensonge : s'il n'y a pas de plugin `onyx-calls` sur le gateway, la
 * déclaration échoue, l'état le dit (`NotInstalled`), et **l'appel ne sonne jamais** — c'est
 * exactement la règle du lot.
 */
class AgentCallBridge(
    private val context: Context,
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
    /** Le nom du fil, pour l'écran d'appel (« qui appelle »). */
    private val titleOf: (String) -> String? = { null },
    /** L'horloge, en secondes — celle du gateway pour comparer les échéances. */
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {

    /** Où en est le pont, pour l'écran Réglages → Phone → Calls. */
    data class Status(val state: State = State.Off, val detail: String? = null) {
        enum class State { Off, Offline, Working, NotInstalled, On, Failed }
    }

    /** Ce que l'agent a demandé récemment — le journal de la page, du plus récent au plus ancien. */
    data class Use(val tool: String, val at: Long, val outcome: String, val error: String? = null)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _recent = MutableStateFlow<List<Use>>(emptyList())
    val recent: StateFlow<List<Use>> = _recent.asStateFlow()

    private val identity = AgentCallIdentity(context)

    /** La session vivante du gateway : `command.dispatch` l'exige. Posée par l'app quand elle en a une. */
    @Volatile var sessionId: String? = null

    /** L'appel qui sonne, s'il y en a un : on ne sonne pas par-dessus. */
    @Volatile private var ringing: AgentCallRequest? = null

    private var loop: Job? = null
    private var scope: CoroutineScope? = null
    private var channel: ServeRpc? = null
    private var channelFor: String? = null

    /** Les identifiants déjà répondus, pour ne pas sonner deux fois pour le même appel. */
    private val answered = ArrayDeque<String>()

    /** Démarre la boucle d'interrogation, portée par [scope] (la portée du processus). */
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
     * Un tour d'interrogation : déclare l'outil, puis sert ce qui attend.
     *
     * Sans connexion, sans outil allumé, ou sans plugin : on ne fait **rien** — jamais d'exception
     * qui remonterait dans la boucle.
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
            // 1) Déclarer l'outil : l'agent ne le voit qu'une fois déclaré.
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
            // 2) Servir ce qui attend.
            val pending = command(
                rpc,
                "phone pending " + AgentCallProtocol.pendingQuery(identity.deviceId, identity.key),
            ) ?: return
            serveRequests(rpc, AgentCallProtocol.parsePending(pending, nowSeconds()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = Status(Status.State.Failed, e.message)
        }
    }

    /** Sonne, ou refuse tout de suite : les heures de tranquillité ne sonnent pas. */
    private suspend fun serveRequests(rpc: ServeRpc, requests: List<AgentCallRequest>) {
        for (request in requests) {
            if (request.id in answered) continue
            // Un appel sonne déjà : l'agent reçoit « occupé », on ne sonne pas par-dessus.
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

    /** Sonne comme un appel entrant, et rend l'issue dès qu'elle tombe. */
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
            // L'app ne peut pas publier de notification : l'agent doit le savoir, pas croire au silence.
            ringing = null
            markAnswered(request.id)
            record(request.tool, CallOutcome.NO_ANSWER, "l'app ne peut pas sonner")
            sendOnScope { answerError(rpc, request.id, "Le téléphone ne peut pas faire sonner d'appel.") }
        }
    }

    /** Envoie un travail de fond dans la portée du pont — décrocher ferme l'écran, la boucle vit encore. */
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

    /** L'outil d'appel est-il allumé ? (« Appels pour une réponse », comme la référence.) */
    private fun phoneCallsOn(): Boolean = settings.phoneRings.value

    private suspend fun answer(rpc: ServeRpc, requestId: String, answer: AgentCallAnswer) {
        command(rpc, "phone answer " + AgentCallProtocol.answerBody(identity.deviceId, identity.key, requestId, answer))
    }

    private suspend fun answerError(rpc: ServeRpc, requestId: String, error: String) {
        command(rpc, "phone answer " + AgentCallProtocol.errorBody(identity.deviceId, identity.key, requestId, error))
    }

    /**
     * L'unique route client → plugin : `command.dispatch`. Rend l'`output` du plugin, ou `null`
     * quand il n'y a pas de plugin (erreur 4018, « pas une commande »).
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
        /** Pour une conversation que l'app ne regarde pas : l'intervalle entre deux regards. */
        const val POLL_MS = 15_000L
        private const val MAX_RECENT = 20
        private const val MAX_ANSWERED = 200
    }
}

/**
 * L'identité du téléphone pour les outils : un identifiant et un secret, faits une fois et gardés.
 *
 * Reprise de `PhoneToolsBridge.identity` (une paire faite la première fois, jamais régénérée).
 */
class AgentCallIdentity(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_agent_calls", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    val deviceId: String get() = stored(KEY_ID, ID_BYTES)
    val key: String get() = stored(KEY_KEY, KEY_BYTES)

    /** Le nom que le gateway liste pour ce téléphone. */
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
