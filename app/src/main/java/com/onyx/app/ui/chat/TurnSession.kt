package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.PrefillProgress
import com.onyx.app.data.model.ReplyCards
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.data.model.TurnMetrics
import com.onyx.app.data.net.StreamLeak
import com.onyx.app.data.net.TurnDeparture
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** L'étape connue d'un tour en cours : ce que l'écran a le droit de dire. */
enum class TurnStep {
    /** Rien n'est encore arrivé du serveur. */
    STARTING,

    /** Le modèle réfléchit (du raisonnement est arrivé). */
    THINKING,

    /** Un outil tourne. */
    TOOL,

    /** La réponse s'écrit. */
    WRITING,

    /** Le tour est fini, sa réponse est là. */
    DONE,

    /** L'utilisateur a arrêté le tour. */
    STOPPED,

    /** Le tour a échoué : `error` porte la raison. */
    FAILED,
}

/**
 * L'état d'un tour en cours, tel que le fil le montre.
 *
 * Un tour doit **toujours** dire ce qui se passe : le temps écoulé et la dernière étape connue.
 * Quand le serveur n'envoie rien (réflexion non transportée, outils absents, prompt en file chez
 * l'hôte), l'écran le dit honnêtement au lieu de laisser une onde muette.
 */
data class TurnPhase(
    val startedAt: Long,
    /** Dernier moment où le serveur a dit quelque chose, quelle que soit la chose. */
    val lastEventAt: Long,
    val step: TurnStep = TurnStep.STARTING,
    /** L'outil en cours, quand l'étape est [TurnStep.TOOL]. */
    val toolName: String? = null,
    val isStreaming: Boolean = true,
    /**
     * Le serveur a renvoyé le message de l'utilisateur au lieu d'y répondre (un écho du tour).
     * C'est ce que le fil dit alors, au lieu de peindre le message de l'utilisateur comme une
     * réponse du bot.
     */
    val echoedPrompt: Boolean = false,
    val prefill: PrefillProgress? = null,
    val providerStatus: String? = null,
    val error: String? = null,
    val connectivity: Boolean = false,
) {
    /** Secondes écoulées depuis l'envoi. */
    fun elapsedSeconds(now: Long): Double = ((now - startedAt).coerceAtLeast(0)).toDouble() / 1_000.0

    /** Secondes depuis le dernier mot du serveur. */
    fun quietSeconds(now: Long): Double = ((now - lastEventAt).coerceAtLeast(0)).toDouble() / 1_000.0

    /**
     * Un tour qui n'a rien produit depuis un délai anormal le dit plutôt que de tourner
     * indéfiniment en silence.
     */
    fun stalled(now: Long): Boolean = isStreaming && quietSeconds(now) >= STALL_SECONDS

    companion object {
        /** Au-delà, un tour en cours sans nouvelle est signalé comme silencieux. */
        const val STALL_SECONDS = 45.0
    }
}

/** Ce qu'un tour en cours montre : son message qui s'écrit, et son état honnête. */
data class TurnLive(
    val conversationId: String,
    val assistantId: String,
    val message: ChatMessage,
    val phase: TurnPhase,
    /** L'identifiant de session serveur, quand le tour en a ouvert une. */
    val serverSessionId: String? = null,
    /** Le modèle servi, quand le serveur le dit. */
    val modelInUse: String? = null,
    /**
     * Le **repli déclaré** du tour, quand la passerelle a basculé sur un autre modèle que celui
     * configuré. C'est lui qui fait que l'interface nomme le modèle **en force** et **dit** le
     * repli, au lieu d'annoncer le modèle configuré (`session.info`).
     */
    val fallback: com.onyx.app.ui.design.FallbackDeclaration? = null,
)

/**
 * Un tour de conversation, gardé **hors de l'écran**.
 *
 * C'est lui qui accumule ce que le serveur envoie : le fil peut être quitté, l'écran détruit et
 * recomposé, la réponse continue de s'écrire ici, et se retrouve entière au retour.
 *
 * Miroir partiel de `Conversation.apply(_:to:)` de la référence : les mêmes événements, écrits
 * dans le même message, avec l'étape et les horaires en plus pour que l'écran sache quoi dire.
 */
class TurnSession(
    val conversationId: String,
    val assistantId: String,
    /** Le message de l'utilisateur qui a lancé ce tour : il ne devient jamais la réponse. */
    val userText: String,
    val startedAt: Long,
    val serverSessionId: String? = null,
    val departure: TurnDeparture = TurnDeparture(),
) {
    private val _live = MutableStateFlow(
        TurnLive(
            conversationId = conversationId,
            assistantId = assistantId,
            message = ChatMessage(
                id = assistantId,
                role = Role.ASSISTANT,
                createdAt = startedAt,
                metrics = TurnMetrics(startedAt = startedAt),
                isStreaming = true,
            ),
            phase = TurnPhase(startedAt = startedAt, lastEventAt = startedAt),
            serverSessionId = serverSessionId,
        ),
    )
    val live: StateFlow<TurnLive> = _live.asStateFlow()

    val phase: TurnPhase get() = _live.value.phase
    val message: ChatMessage get() = _live.value.message

    /** La raison de l'échec, quand le tour a échoué. */
    var failure: TransportError? = null
        private set

    /** La coroutine qui alimente ce tour, tenue par le magasin (pas par un écran). */
    var job: Job? = null

    /** Vrai quand ce tour a fini d'écrire (réussi, arrêté ou échoué). */
    val isFinished: Boolean get() = !_live.value.phase.isStreaming

    /** Le modèle que le serveur sert pour ce tour. */
    fun noteModel(model: String) {
        _live.update { it.copy(modelInUse = model) }
    }

    /** Une modification de la réponse en cours, par l'app (une carte répondue, un secret donné). */
    fun edit(transform: (ChatMessage) -> ChatMessage) {
        update(transform(_live.value.message))
    }

    /**
     * Une révision à écrire sur disque : la forme du message a changé (premier mot, une étape de
     * plus, la fin). Chaque fragment n'est pas écrit — le disque n'a pas besoin de ça — mais ce
     * qui compte survit à l'écran.
     */
    private var persistedShape: String = ""
    fun shouldPersist(): Boolean {
        val message = _live.value.message
        val shape = "${message.text.length}:${message.reasoning.length}:${message.tools.size}:" +
            "${message.tools.count { it.isRunning }}:${message.cards?.size ?: -1}:${message.isStreaming}:" +
            "${message.error.orEmpty().length}:${message.subagents.size}:${message.subagents.count { it.isLive }}"
        if (shape == persistedShape) return false
        persistedShape = shape
        return true
    }

    /** Un événement du flux, écrit dans la réponse de ce tour. */
    fun apply(event: TurnEvent, now: Long = System.currentTimeMillis()) {
        when (event) {
            is TurnEvent.TextDelta -> text(event.text, now)

            is TurnEvent.TextFinal -> text(event.text, now, replaces = true)

            is TurnEvent.ReasoningDelta -> {
                // **Un fragment machine ne se montre pas.** Une pièce qui porte le JSON brut d'un
                // appel d'outil, un chemin interne ou une commande n'est pas un raisonnement : elle
                // est écartée au lieu de salir le bloc « Raisonnement du modèle ». Le tour continue.
                if (StreamLeak.looksLikeLeak(event.text)) {
                    touch(now)
                } else {
                    val message = _live.value.message
                    update(
                        message.copy(
                            reasoning = message.reasoning + event.text,
                            reasoningStartedAt = message.reasoningStartedAt ?: now,
                            metrics = message.metrics?.withFirstToken(now),
                        ),
                    )
                    step(TurnStep.THINKING, now)
                }
            }

            is TurnEvent.ToolStarted -> {
                val message = _live.value.message
                // Un même outil peut être relancé : on n'ouvre une carte que si elle diffère.
                val alreadyRunning = message.tools.any { it.name == event.name && it.isRunning }
                if (!alreadyRunning) {
                    val activity = ToolActivity(
                        id = UUID.randomUUID().toString(),
                        name = event.name,
                        preview = event.preview,
                        args = event.args?.take(ToolActivity.DETAIL_CAP),
                        subagentId = ToolActivity.subagentIdOf(event.args),
                        status = ToolStatus.RUNNING,
                        startedAt = now,
                    )
                    val withStep = message.copy(tools = message.tools + activity)
                    // Un appel de cartes (`onyx_show`) : la carte est montrée dès qu'il est fait,
                    // sans attendre la fin du tour.
                    val shown = ReplyCards.cards(from = activity)
                    update(
                        if (shown.isNullOrEmpty()) {
                            withStep
                        } else {
                            withStep.copy(cards = (withStep.cards ?: emptyList()) + shown)
                        },
                    )
                }
                step(TurnStep.TOOL, now, tool = event.name)
            }

            is TurnEvent.ToolFinished -> {
                val message = _live.value.message
                val index = message.tools.indexOfLast {
                    it.isRunning && (event.name.isEmpty() || it.name == event.name)
                }
                if (index >= 0) {
                    val tools = message.tools.mapIndexed { i, tool ->
                        if (i != index) {
                            tool
                        } else {
                            tool.copy(
                                status = if (event.failed) ToolStatus.FAILED else ToolStatus.COMPLETED,
                                endedAt = now,
                                output = event.output?.take(ToolActivity.DETAIL_CAP) ?: tool.output,
                            )
                        }
                    }
                    val finished = message.copy(tools = tools)
                    // Un appel de cartes : la réponse du plugin a la météo et l'heure remplies, et un
                    // appel qu'il a refusé ne montre plus rien.
                    update(
                        if (tools[index].name == ReplyCards.tool) {
                            val kept = ReplyCards.cards(ofSteps = tools)
                            finished.copy(cards = kept.ifEmpty { null })
                        } else {
                            finished
                        },
                    )
                }
                // Le tour repart vers le modèle : la réponse s'écrit, ou un autre outil part.
                step(message.stepAfterTool(), now)
            }

            is TurnEvent.Prefill -> _live.update { it.copy(phase = it.phase.copy(prefill = event.progress, lastEventAt = now)) }

            is TurnEvent.Status -> _live.update { live ->
                // Une **déclaration de repli** arrive ici, en clair : c'est d'elle que l'on
                // apprend quel modèle a réellement servi le tour (`session.info` ne portant que le
                // modèle configuré). Les autres statuts restent la ligne d'état du tour.
                val declared = com.onyx.app.ui.design.FallbackNotice.parse(event.text)
                live.copy(
                    fallback = declared ?: live.fallback,
                    phase = live.phase.copy(providerStatus = event.text, lastEventAt = now),
                )
            }

            is TurnEvent.Usage -> update(_live.value.message.copy(usage = event.usage))

            is TurnEvent.Interrupt -> {
                val message = _live.value.message
                if (message.approvals.none { it.id == event.request.id }) {
                    update(message.copy(approvals = message.approvals + event.request.copy(createdAt = now)))
                }
                touch(now)
            }

            is TurnEvent.ModelInUse -> _live.update { it.copy(modelInUse = event.model) }

            // Un sous-agent délégué : la ligne se crée au premier mot du gateway et se met à jour
            // sur place. `startedAt` est posé ici, à la première vue — c'est ce qui donne le temps
            // écoulé quand le serveur n'en fournit pas.
            is TurnEvent.Subagent -> {
                val message = _live.value.message
                val existing = message.subagents.firstOrNull { it.id == event.update.id }
                val row = (existing ?: SubagentActivity(id = event.update.id, startedAt = now))
                    .applying(event.update, now)
                update(
                    message.copy(
                        subagents = if (existing == null) {
                            message.subagents + row
                        } else {
                            message.subagents.map { if (it.id == event.update.id) row else it }
                        },
                    ),
                )
                touch(now)
            }

            is TurnEvent.SessionId -> _live.update { it.copy(serverSessionId = event.id) }

            TurnEvent.Done -> touch(now)
        }
    }

    /**
     * Un fragment de réponse. Un fragment qui **répète le message de l'utilisateur** n'est pas une
     * réponse : c'est un écho du tour, et il est jeté au lieu d'être peint comme la réponse du bot.
     */
    private fun text(incoming: String, now: Long, replaces: Boolean = false) {
        if (isPromptEcho(incoming)) {
            _live.update {
                it.copy(
                    phase = it.phase.copy(
                        echoedPrompt = true,
                        providerStatus = null,
                        lastEventAt = it.phase.lastEventAt,
                    ),
                )
            }
            return
        }
        val message = _live.value.message
        val body = if (replaces) incoming else message.text + incoming
        update(
            message.copy(
                text = body,
                reasoningEndedAt = message.reasoningEndedAt
                    ?: if (message.reasoning.isNotEmpty()) now else null,
                metrics = message.metrics?.withFirstToken(now),
            ),
        )
        step(TurnStep.WRITING, now)
    }

    /**
     * Le serveur a-t-il renvoyé le message de l'utilisateur ? On ne compare que tant que la
     * réponse est vide, et seulement pour un message assez long pour ne jamais être une réponse
     * légitime (« oui », « OK » restent des réponses possibles).
     */
    private fun isPromptEcho(incoming: String): Boolean {
        val message = _live.value.message
        if (message.text.isNotEmpty() || message.reasoning.isNotEmpty()) return false
        if (!message.tools.isEmpty() && message.tools.any { it.name.isNotEmpty() }) return false
        val mine = normalize(userText)
        if (mine.length < ECHO_MIN_CHARS) return false
        return normalize(incoming) == mine
    }

    /** La réponse est écrite et le flux s'est arrêté normalement. */
    fun succeed(now: Long = System.currentTimeMillis()) {
        val message = _live.value.message
        // Le flux s'est terminé sans le moindre texte : si le gateway n'a remonté qu'un statut
        // (« provider indisponible, réessai »), on le dit comme un échec au lieu d'une bulle vide.
        val onlyStatus = _live.value.phase.providerStatus
        val bare = message.text.isEmpty() && message.reasoning.isEmpty() &&
            message.tools.isEmpty() && message.approvals.isEmpty() && message.attachments.isEmpty()
        if (bare && onlyStatus != null) {
            fail(TransportError.Malformed(onlyStatus), now)
            return
        }
        // Un tour qui finit sans rien dire laisse sinon une bulle vide, coiffée du nom du bot :
        // le message de l'utilisateur qui suit se lit alors comme sa réponse (signalé par le
        // propriétaire, « mon message s'affiche comme la réponse du bot »). On le dit.
        if (bare) {
            fail(TransportError.Malformed(OnyxLocalization["The server sent no answer for this turn."]), now, translated = true)
            return
        }
        val closed = if (message.reasoning.isNotEmpty()) message.reasoningEndedAt ?: now else message.reasoningEndedAt
        val metrics = message.metrics?.let { if (it.completedAt != null) it else it.completed(now, message.usage?.output ?: 0) }
        update(message.copy(isStreaming = false, reasoningEndedAt = closed, metrics = metrics))
        _live.update {
            it.copy(
                phase = it.phase.copy(
                    step = TurnStep.DONE,
                    isStreaming = false,
                    prefill = null,
                    providerStatus = null,
                    lastEventAt = now,
                ),
            )
        }
    }

    /**
     * Le tour a échoué : la raison est écrite sur la réponse, et le tour est fini.
     *
     * `translated` : la phrase est déjà dans la langue de l'appareil (les mots d'un tour vide),
     * il ne faut pas la repasser dans `userMessage`.
     */
    fun fail(error: TransportError, now: Long = System.currentTimeMillis(), translated: Boolean = false) {
        failure = error
        val message = _live.value.message
        // **Aucun fragment brut dans la ligne d'erreur** : elle est redactée — jamais le JSON du
        // fil, jamais un chemin interne, jamais une commande.
        val reason = StreamLeak.redact(if (translated) error.message.orEmpty() else error.userMessage())
        update(message.copy(isStreaming = false, error = reason))
        _live.update {
            it.copy(
                phase = it.phase.copy(
                    step = TurnStep.FAILED,
                    isStreaming = false,
                    prefill = null,
                    error = reason,
                    connectivity = error.isConnectivity,
                    lastEventAt = now,
                ),
            )
        }
    }

    /** L'utilisateur a arrêté le tour : l'hôte est interrompu. */
    fun stop(now: Long = System.currentTimeMillis()) {
        if (isFinished) return
        val message = _live.value.message
        update(message.copy(isStreaming = false))
        _live.update {
            it.copy(
                phase = it.phase.copy(step = TurnStep.STOPPED, isStreaming = false, prefill = null, lastEventAt = now),
            )
        }
    }

    private fun update(message: ChatMessage) {
        _live.update { it.copy(message = message) }
    }

    private fun touch(now: Long) {
        _live.update { it.copy(phase = it.phase.copy(lastEventAt = now)) }
    }

    private fun step(step: TurnStep, now: Long, tool: String? = null) {
        _live.update {
            if (!it.phase.isStreaming) return@update it
            it.copy(phase = it.phase.copy(step = step, toolName = tool, lastEventAt = now))
        }
    }

    private fun ChatMessage.stepAfterTool(): TurnStep =
        if (text.isNotEmpty()) TurnStep.WRITING else TurnStep.THINKING

    private companion object {
        /** En dessous, un texte identique peut être une vraie réponse (« oui », « OK »). */
        const val ECHO_MIN_CHARS = 24

        fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ").lowercase()
    }
}

/**
 * Ce que le fil dit d'un tour en cours — toujours une ligne, jamais une onde muette.
 *
 * Le temps écoulé vient du tour lui-même ; l'étape est la dernière que le serveur a fait
 * connaître. Sans nouvelle depuis un délai anormal, la ligne le dit.
 */
object TurnStatusCopy {
    /**
     * L'état est un avertissement : le serveur se tait depuis un délai anormal, ou il a renvoyé
     * le message de l'utilisateur au lieu d'y répondre.
     */
    fun isWarning(phase: TurnPhase, now: Long): Boolean = phase.isStreaming && (phase.stalled(now) || phase.echoedPrompt)

    /** La ligne d'état, ou `null` quand le tour n'a plus rien à dire (fini). */
    fun line(phase: TurnPhase, now: Long): String? {
        if (!phase.isStreaming) return null
        val elapsed = TurnMetrics.format(phase.elapsedSeconds(now))
        val quiet = TurnMetrics.format(phase.quietSeconds(now))
        val head = when {
            // Le serveur a renvoyé le message de l'utilisateur : on le dit, au lieu de laisser
            // croire que c'est la réponse.
            phase.echoedPrompt -> OnyxLocalization["The server sent your message back — waiting for the answer"]

            phase.stalled(now) -> OnyxLocalization.format("No news from the server for %1\$s", quiet)

            phase.step == TurnStep.TOOL -> OnyxLocalization.format(
                "Running %1\$s",
                phase.toolName?.takeIf { it.isNotBlank() } ?: OnyxLocalization["a tool"],
            )

            phase.step == TurnStep.THINKING -> OnyxLocalization["Thinking"]

            phase.step == TurnStep.WRITING -> OnyxLocalization["Writing the reply"]

            else -> OnyxLocalization["Waiting for the server"]
        }
        return "$head · $elapsed"
    }
}
