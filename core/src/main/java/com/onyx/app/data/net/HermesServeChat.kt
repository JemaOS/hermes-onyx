package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentUpdate
import com.onyx.app.data.model.TokenUsage
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Le chat du transport **Hermes Dashboard (WebSocket)**.
 *
 * Jusqu'ici `ServeRpc` ne servait que le RPC du Dashboard (commandes `/`, interruptions, bots):
 * le chat partait toujours par `ChatCompletionsClient`, qui a besoin d'une adresse `/v1` — un
 * serveur en mode Dashboard (`serveBaseUrl` seul) n'en a pas, et l'envoi échouait. Ce transport
 * branche le tour de conversation sur le même canal JSON-RPC que le RPC, comme
 * `HermesServeTransport`:
 *
 * 1. `session.resume` (session connue) ou `session.create` (`source = desktop`);
 * 2. `prompt.submit { session_id, text }`;
 * 3. les notifications `method == "event"` du socket alimentent les [TurnEvent] — `message.delta`
 * pour le texte, `message.complete` pour la fin et la consommation, `session.info` pour la
 * reprise après un changement d'état, `error` pour un échec.
 *
 * Le repli est franc: si le Dashboard ne connaît pas ces méthodes (`-32601`) ou refuse
 * `prompt.submit`, l'erreur remonte telle quelle à l'appelant (bulle d'erreur dans le chat),
 * jamais un faux succès.
 */
class HermesServeChat(
    private val config: ServerConfig,
    private val link: ServeChatChannel,
    /**
     * Faux quand le canal est **partagé** avec les RPC de contrôle (steer / stop / roster).
     *
     * Le gateway n'accorde le pilotage d'un sous-agent qu'au **transport** attaché à la session
     * (`_current_session_steer_authority`): un second socket, même avec la bonne session, est
     * refusé en `4001`. Le tour et les commandes doivent donc passer par le même socket — et le
     * tour ne doit pas le fermer en finissant, sinon l'écran des tâches perd le sien.
     */
    private val ownsLink: Boolean = true,
) {
    constructor(config: ServerConfig) : this(config, ServeRpc(config).asChatChannel())

    /**
     * Un tour, du `prompt.submit` à la dernière notification.
     *
     * [storedSessionId]: la session serveur à réutiliser (`Conversation.serverSessionId`), ou
     * `null` pour en ouvrir une neuve. Les tours suivants réutilisent celle que le premier a
     * annoncée (événement [TurnEvent.SessionId]).
     */
    fun stream(
        request: ChatRequest,
        storedSessionId: String? = null,
        /**
         * Ce qui a été décidé du tour: un abandon laisse l'hôte le finir, un arrêt l'interrompt.
         * Sans drapeau, toute annulation arrête l'hôte (comportement d'avant).
         */
        departure: TurnDeparture? = null,
        /**
         * L'identifiant **runtime** de la session, celui sous lequel le gateway l'a enregistrée.
         *
         * C'est lui (et pas l'identifiant conservé côté app) que `subagent.list` / `.tail` /
         * `.steer` / `.interrupt` attendent: `_current_session_steer_authority` le cherche dans
         * les sessions vivantes du processus. L'app ne le voyait pas — elle ne gardait que
         * l'identifiant à réutiliser.
         */
        onRuntimeSessionId: ((String) -> Unit)? = null,
    ): Flow<TurnEvent> = channelFlow {
        val scope = this
        val events = Channel<ServeEvent>(Channel.UNLIMITED)
        val listener = link.addListener { event -> events.trySend(event) }
        // `runtime` sert au nettoyage: un arrêt de l'utilisateur interrompt le tour côté serveur.
        var runtime: String? = null
        var completed = false
        try {
            // `ServeRpc.connect` ouvre déjà la session du Dashboard (bocal à cookies partagé):
            // le ticket WebSocket part avec elle.
            link.connect()

            val session = openSession(request, storedSessionId)
            runtime = session.runtime
            onRuntimeSessionId?.invoke(session.runtime)
            send(TurnEvent.SessionId(session.stored))

            val submitted = link.call(
                "prompt.submit",
                buildJsonObject {
                    put("session_id", session.runtime)
                    put("text", promptText(request))
                },
                PROMPT_TIMEOUT_MS,
            )
            if (submitted.asObject()?.str("status") == "queued") {
                send(TurnEvent.Status(OnyxLocalization["queued behind a running turn…"]))
            }

            var streamed = false
            var underWay = false
            withTimeout(timeoutMillis()) {
                for (event in events) {
                    if (!ServeChatEvents.matches(event, session.runtime, session.stored)) continue
                    if (ServeChatEvents.isTurnEvent(event.type)) underWay = true
                    when (val step = ServeChatEvents.step(event, streamed = streamed, underWay = underWay)) {
                        is ServeStep.Emit -> for (mapped in step.events) {
                            if (mapped is TurnEvent.TextDelta) streamed = true
                            send(mapped)
                        }
                        ServeStep.Continue -> Unit
                        is ServeStep.Finish -> {
                            for (mapped in step.events) {
                                if (mapped is TurnEvent.TextDelta) streamed = true
                                send(mapped)
                            }
                            send(TurnEvent.Done)
                            completed = true
                            return@withTimeout
                        }
                        ServeStep.Done -> {
                            send(TurnEvent.Done)
                            completed = true
                            return@withTimeout
                        }
                        is ServeStep.Failed -> throw TransportError.Malformed(step.message)
                    }
                }
            }
        } finally {
            link.removeListener(listener)
            // Interrompu par l'utilisateur: le tour s'arrête aussi côté serveur (le nôtre, sans
            // travail: on n'attend pas la réponse). Un tour dont on s'est **détaché** n'est pas
            // arrêté: l'hôte le finit, et sa réponse est là au retour. Un tour fini ne l'est jamais.
            if (!completed && runtime != null && departure?.hasLeft != true) {
                val session = runtime
                withContext(NonCancellable) {
                    runCatching {
                        link.call("session.interrupt", buildJsonObject { put("session_id", session) }, 5_000)
                    }
                }
            }
            if (ownsLink) link.close()
        }
    }

    private suspend fun openSession(request: ChatRequest, stored: String?): ServeSession {
        val params = buildJsonObject {
            if (stored != null) {
                put("session_id", stored)
                put("omit_messages", true)
            } else {
                // « desktop », pas « mobile »: le hint plateforme du prompt système.
                put("source", "desktop")
                put("close_on_disconnect", false)
                request.model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
                request.provider?.takeIf { it.isNotBlank() }?.let { put("provider", it) }
                request.reasoningEffort?.takeIf { it.isNotBlank() }?.let { put("reasoning_effort", it) }
            }
        }
        val method = if (stored != null) "session.resume" else "session.create"
        val result = link.call(method, params, OPEN_TIMEOUT_MS).asObject()
            ?: throw TransportError.Malformed(OnyxLocalization.format("%1\$s gave no session", method))
        val runtime = result.strOrNull("session_id")
            ?: throw TransportError.Malformed(OnyxLocalization.format("%1\$s gave no session id", method))
        if (stored != null) return ServeSession(runtime, stored)
        val storedID = result.strOrNull("stored_session_id") ?: runtime
        return ServeSession(runtime, storedID)
    }

    private fun promptText(request: ChatRequest): String =
        request.history.lastOrNull { it.role == Role.USER }?.text.orEmpty()

    private fun timeoutMillis(): Long = config.callTimeoutSeconds.coerceAtLeast(1) * 1_000

    private companion object {
        const val OPEN_TIMEOUT_MS = 60_000L
        const val PROMPT_TIMEOUT_MS = 60_000L
    }
}

/** La session ouverte: l'identifiant runtime du tour, et l'identifiant à garder côté app. */
internal data class ServeSession(val runtime: String, val stored: String)

/**
 * Le canal minimal dont le chat du Dashboard a besoin: vrai `ServeRpc` en production, faux en
 * test. Évite d'ouvrir un socket pour éprouver la boucle d'événements.
 */
interface ServeChatChannel {
    suspend fun connect()
    suspend fun call(method: String, params: JsonObject, timeoutMillis: Long): JsonElement
    fun addListener(listener: (ServeEvent) -> Unit): String
    fun removeListener(key: String)
    fun close()
}

/** Le `ServeRpc` de production vu par [HermesServeChat]. */
fun ServeRpc.asChatChannel(): ServeChatChannel = object : ServeChatChannel {
    override suspend fun connect() {
        this@asChatChannel.connect()
    }

    override suspend fun call(method: String, params: JsonObject, timeoutMillis: Long): JsonElement =
        this@asChatChannel.call(method, params, timeoutMillis)

    override fun addListener(listener: (ServeEvent) -> Unit): String = this@asChatChannel.addListener(listener)

    override fun removeListener(key: String) {
        this@asChatChannel.removeListener(key)
    }

    override fun close() {
        this@asChatChannel.close()
    }
}

/** Une étape de la machine à états des notifications du Dashboard. */
internal sealed interface ServeStep {
    /** Pousse ces événements et continue d'écouter. */
    data class Emit(val events: List<TurnEvent>) : ServeStep

    /** Pousse ces derniers événements puis clôt le tour (`message.complete`). */
    data class Finish(val events: List<TurnEvent>) : ServeStep

    data object Continue : ServeStep
    data object Done : ServeStep
    data class Failed(val message: String) : ServeStep
}

/**
 * Traduit une notification du Dashboard en [TurnEvent] — pur, donc éprouvable sans socket.
 *
 * Comme `HermesServeTransport.hear` pour
 * les types qu'un tour de conversation emploie. `streamed`: du texte a déjà été poussé (décide si
 * `message.complete` reprend le texte complet); `underWay`: un événement de tour a été vu
 * (décide si un `session.info running=false` clôt *ce* tour et non celui d'avant).
 */
internal object ServeChatEvents {
    private val TURN_EVENTS = setOf(
        "message.start", "message.delta", "reasoning.delta", "reasoning.available",
        "tool.start", "tool.complete",
    ) + SubagentUpdate.EVENT_TYPES

    fun isTurnEvent(type: String): Boolean = type in TURN_EVENTS

    /** L'événement concerne-t-il cette session ? Sans `session_id`, il est entendu. */
    fun matches(event: ServeEvent, runtime: String, stored: String): Boolean =
        event.sessionId == null || event.sessionId == runtime || event.sessionId == stored

    fun step(event: ServeEvent, streamed: Boolean, underWay: Boolean): ServeStep {
        val payload = event.payload
        return when (event.type) {
            "message.delta" -> payload.strOrNull("text")
                ?.let { ServeStep.Emit(listOf(TurnEvent.TextDelta(it))) }
                ?: ServeStep.Continue

            "reasoning.delta", "reasoning.available" -> payload.strOrNull("text")
                ?.let { ServeStep.Emit(listOf(TurnEvent.ReasoningDelta(it))) }
                ?: ServeStep.Continue

            "tool.start" -> {
                val name = payload.strOrNull("name") ?: "tool"
                ServeStep.Emit(
                    listOf(
                        TurnEvent.ToolStarted(
                            name = name,
                            preview = payload.strOrNull("context"),
                            // Les arguments d'un appel de cartes (`onyx_show`) sont gardés en JSON:
                            // c'est d'eux que la carte se lit; les autres se lisent en toutes lettres.
                            args = ToolActivity.args(name, payload["args"]),
                        ),
                    ),
                )
            }

            "tool.complete" -> {
                val error = payload["error"]
                val failed = error != null && error !is JsonNull
                // Le résultat de l'appel: la réponse du plugin (`result_text`), ou le résultat brut.
                val output = ToolActivity.detail(payload["result_text"])
                    ?: ToolActivity.detail(payload["result"])
                    ?: ToolActivity.detail(error)
                ServeStep.Emit(
                    listOf(
                        TurnEvent.ToolFinished(
                            name = payload.strOrNull("name").orEmpty(),
                            failed = failed,
                            output = output,
                        ),
                    ),
                )
            }

            "status.update" -> payload.strOrNull("text")
                ?.let { ServeStep.Emit(listOf(TurnEvent.Status(it))) }
                ?: ServeStep.Continue

            "message.complete" -> {
                val events = buildList {
                    if (!streamed) {
                        payload.strOrNull("text")?.takeIf { it.isNotEmpty() }?.let { add(TurnEvent.TextDelta(it)) }
                    }
                    usageOf(payload.obj("usage"))?.let { add(TurnEvent.Usage(it)) }
                }
                if (payload.strOrNull("status") == "error") {
                    ServeStep.Failed(payload.strOrNull("error") ?: payload.strOrNull("text") ?: "the turn failed")
                } else {
                    ServeStep.Finish(events)
                }
            }

            "session.info" -> {
                // Le modèle servi, tel que la session le dit (l'alias envoyé peut ne pas être le
                // modèle réellement employé).
                val served = payload.obj("model")?.strOrNull("id")
                    ?: payload.strOrNull("model")
                    ?: payload.obj("session_info")?.strOrNull("model")
                val modelEvent = served?.takeIf { it.isNotBlank() }?.let { TurnEvent.ModelInUse(it) }
                val closing = payload.bool("running") == false && underWay
                when {
                    modelEvent != null && closing -> ServeStep.Emit(listOf(modelEvent))
                    modelEvent != null -> ServeStep.Emit(listOf(modelEvent))
                    closing -> ServeStep.Done
                    else -> ServeStep.Continue
                }
            }

            // Un sous-agent délégué: le Dashboard émet ses trames sur la session du **parent**,
            // l'enfant est nommé par `subagent_id`. C'est la route qui était morte: sans ce cas,
            // tout le vocabulaire `subagent.*` tombait dans le `else` et l'app ne voyait rien.
            in SubagentUpdate.EVENT_TYPES ->
                SubagentUpdate.parse(event.type, payload)
                    ?.let { ServeStep.Emit(listOf(TurnEvent.Subagent(it))) }
                    ?: ServeStep.Continue

            "error" -> ServeStep.Failed(payload.strOrNull("message") ?: "the Dashboard reported an error")

            "connection.lost" -> ServeStep.Failed(OnyxLocalization["Lost the connection to the Hermes Dashboard."])

            else -> ServeStep.Continue
        }
    }

    private fun usageOf(usage: JsonObject?): TokenUsage? {
        if (usage == null) return null
        val input = usage.int("input") ?: usage.int("prompt") ?: return null
        val output = usage.int("output") ?: usage.int("completion") ?: 0
        return TokenUsage(
            input = input,
            output = output,
            contextUsed = usage.int("context_used"),
            contextMax = usage.int("context_max"),
        )
    }
}
