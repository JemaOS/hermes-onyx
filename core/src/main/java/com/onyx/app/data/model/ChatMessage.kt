package com.onyx.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Rôle d'un message, aligné sur le vocabulaire OpenAI (`user` / `assistant` / `system`). */
@Serializable
enum class Role(val wire: String) {
    @SerialName("user")
    USER("user"),

    @SerialName("assistant")
    ASSISTANT("assistant"),

    @SerialName("system")
    SYSTEM("system"),
    ;

    companion object {
        fun fromWire(raw: String?): Role =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: ASSISTANT
    }
}

/** Statut d'un appel d'outil affiché dans le fil. */
@Serializable
enum class ToolStatus(val wire: String) {
    @SerialName("running")
    RUNNING("running"),

    @SerialName("completed")
    COMPLETED("completed"),

    @SerialName("failed")
    FAILED("failed"),
}

/**
 * Un appel d'outil (tool call) tel qu'il est affiché dans la conversation.
 *
 * Comme `ToolActivity`.
 */
@Serializable
data class ToolActivity(
    val id: String,
    val name: String,
    val preview: String? = null,
    val status: ToolStatus = ToolStatus.RUNNING,
    val startedAt: Long = 0L,
    val endedAt: Long? = null,
    val args: String? = null,
    val output: String? = null,
    /**
     * L'identifiant du sous-agent auquel cet appel délègue, quand le gateway le donne (`subagent_id`
     * des arguments). C'est lui qu'attendent `subagent.steer` et `subagent.interrupt`; sans lui, la
     * transcription s'ouvre en lecture seule, quand le tour tourne dans le gateway.
     */
    val subagentId: String? = null,
) {
    val isRunning: Boolean get() = status == ToolStatus.RUNNING

    companion object {
        /** Les sorties d'outils sont tronquées: au-delà, elles noient le fil. */
        const val DETAIL_CAP = 12_000

        /**
         * L'identifiant de sous-agent porté par les arguments d'un `tool.start`, ou `null`.
         *
         * Le gateway le nomme `subagent_id`; certains transports le rangent sous `id`. On ne
         * devine pas d'autre forme: un identifiant inventé ferait échouer steer et stop en
         * silence, ce qui vaut moins qu'un écran qui ne propose pas ces boutons.
         */
        fun subagentIdOf(args: String?): String? {
            val text = args?.trim().orEmpty()
            if (text.isEmpty() || !text.startsWith("{")) return null
            val element = runCatching { Json.parseToJsonElement(text) }.getOrNull()
            val body = element as? JsonObject ?: return null
            for (key in listOf("subagent_id", "id")) {
                val value = (body[key] as? JsonPrimitive)?.contentOrNull
                if (!value.isNullOrBlank()) return value
            }
            return null
        }

        /**
         * Le texte d'une étape: rogné, `null` quand il est vide, coupé à [DETAIL_CAP] avec la note
         * de ce qui manque. Comme `ToolActivity.detail(_:)` (ligne 64).
         */
        fun detail(text: String?): String? {
            val trimmed = text?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            if (trimmed.length <= DETAIL_CAP) return trimmed
            return trimmed.take(DETAIL_CAP) + "\n… (${trimmed.length - DETAIL_CAP} more characters)"
        }

        /**
         * Une valeur JSON comme ce texte: une chaîne reste elle-même; un objet devient une ligne
         * par champ, les champs de texte en entier (la sortie d'un terminal se lit donc, au lieu
         * d'une seule ligne échappée); le reste est du JSON compact.
         */
        fun detail(value: JsonElement?): String? {
            if (value == null || value is JsonNull) return null
            return when (value) {
                is JsonPrimitive -> detail(if (value.isString) value.contentOrNull else value.toString())
                is JsonObject -> {
                    if (value.isEmpty()) return null
                    detail(
                        value.entries.sortedBy { it.key }.joinToString("\n") { (key, field) ->
                            val text = (field as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                            when {
                                text == null -> "$key: $field"
                                text.contains("\n") -> "$key:\n$text"
                                else -> "$key: $text"
                            }
                        },
                    )
                }

                else -> detail(value.toString())
            }
        }

        /**
         * Les arguments d'un appel tels que son étape les garde: pour la lecture ([detail]), sauf
         * ceux d'un appel de cartes, gardés comme le JSON reçu pour que les cartes s'y lisent
         * ([ReplyCards]). Comme `ToolActivity.args(_:_:)` (ligne 101).
         */
        fun args(name: String, value: JsonElement?): String? {
            if (name != ReplyCards.tool || value == null || value is JsonNull) return detail(value)
            val text = value.toString()
            return if (text.length < 256_000) text else detail(value)
        }
    }
}

/** Compteurs de tokens remontés par le serveur (`usage` du flux). */
@Serializable
data class TokenUsage(
    val input: Int = 0,
    val output: Int = 0,
    val cached: Int? = null,
    val contextUsed: Int? = null,
    val contextMax: Int? = null,
) {
    val total: Int get() = input + output
}

/**
 * Demande d'approbation envoyée par le gateway avant d'exécuter un outil sensible.
 *
 * Comme `ApprovalRequest`.
 * Les réponses possibles sont `once` | `session` | `always` | `deny`; l'UI à deux boutons
 * n'utilise que `once` (« approuver ») et `deny` (« refuser »).
 */
@Serializable
data class ApprovalRequest(
    val id: String,
    val command: String = "",
    val description: String? = null,
    val choices: List<String> = listOf(CHOICE_ONCE, CHOICE_DENY),
    /** Run (`POST /v1/runs/{id}/approval`) auquel la réponse doit être adressée. */
    val runId: String? = null,
    /** Décision déjà prise, ou `null` tant que la question est ouverte. */
    val decision: String? = null,
    val createdAt: Long = 0L,
) {
    val isPending: Boolean get() = decision == null

    /** Réponse « approuver », ou `null` si le serveur ne la propose pas. */
    val approveChoice: String?
        get() = listOf(CHOICE_ONCE, CHOICE_SESSION, CHOICE_ALWAYS).firstOrNull { it in choices }

    /** Réponse « refuser », ou `null` si le serveur ne la propose pas. */
    val denyChoice: String? get() = CHOICE_DENY.takeIf { it in choices }

    companion object {
        const val CHOICE_ONCE = "once"
        const val CHOICE_SESSION = "session"
        const val CHOICE_ALWAYS = "always"
        const val CHOICE_DENY = "deny"

        /**
         * Construit une demande à partir de la charge utile du gateway.
         *
         * Comme `ApprovalRequest(payload:)`: les choix sont pris tels quels s'ils sont listés,
         * sinon reconstruits à partir de `allow_session` / `allow_permanent`.
         */
        fun fromPayload(
            id: String,
            command: String,
            description: String?,
            choices: List<String>,
            allowSession: Boolean?,
            allowPermanent: Boolean?,
            runId: String?,
        ): ApprovalRequest {
            val resolved = choices.ifEmpty {
                buildList {
                    add(CHOICE_ONCE)
                    if (allowSession != false) add(CHOICE_SESSION)
                    if (allowPermanent != false) add(CHOICE_ALWAYS)
                    add(CHOICE_DENY)
                }
            }
            return ApprovalRequest(
                id = id,
                command = command,
                description = description?.takeIf { it.isNotBlank() },
                choices = resolved,
                runId = runId,
            )
        }
    }
}

/** Progression du préremplissage (prompt) remontée par llama.cpp via `return_progress`. */
data class PrefillProgress(
    val processed: Int,
    val total: Int,
    val cached: Int,
) {
    val fraction: Float get() = if (total > 0) (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Un message du fil de conversation.
 *
 * Comme `Message` , en gardant ce qui est utile à l'UI Android.
 */
@Serializable
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String = "",
    val createdAt: Long,
    val reasoning: String = "",
    val reasoningStartedAt: Long? = null,
    val reasoningEndedAt: Long? = null,
    val tools: List<ToolActivity> = emptyList(),
    /**
     * Les sous-agents que cette réponse a lancés (`delegate_task`), tels que le gateway les
     * annonce. Distincts des [tools]: un sous-agent a un but, une transcription et un état de
     * marche, ce qu'une carte d'outil ne porte pas.
     */
    val subagents: List<SubagentActivity> = emptyList(),
    /**
     * Les cartes que cette réponse montre sous ses mots (une carte, des lieux, la météo, l'heure),
     * demandées avec `onyx_show`. `null` tant qu'aucun appel ne les a demandées — c'est la
     * distinction entre « pas encore cherché » et « cherché, rien à dessiner ».
     */
    val cards: List<ReplyCard>? = null,
    val approvals: List<ApprovalRequest> = emptyList(),
    /** Une question de l'agent, un secret demandé, ou un mot de passe sudo en attente. */
    val clarify: ClarifyRequest? = null,
    val secret: SecretRequest? = null,
    val sudoRequestId: String? = null,
    val usage: TokenUsage? = null,
    val metrics: TurnMetrics? = null,
    val error: String? = null,
    val isStreaming: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    /**
     * Une note écrite pendant une réponse pour piloter le tour en cours plutôt que de le suivre
     * (`Message.isSteer`). Elle s'affiche en pastille, sans bulle, et n'est pas une question.
     */
    val isSteer: Boolean = false,
) {
    val isUser: Boolean get() = role == Role.USER
    val hasBody: Boolean get() =
        text.isNotEmpty() || reasoning.isNotEmpty() || tools.isNotEmpty() || subagents.isNotEmpty()

    /** Une question de l'utilisateur, mais pas une note de pilotage. */
    val isQuestion: Boolean get() = isUser && !isSteer
}

/** Pièce jointe (image principalement) — portée depuis `Attachment`. */
@Serializable
data class Attachment(
    val id: String,
    val mimeType: String,
    val uri: String,
    val name: String? = null,
)
