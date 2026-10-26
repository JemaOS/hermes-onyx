package com.onyx.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Un sous-agent délégué (une tâche lancée par l'agent en parallèle du fil).
 *
 * Comme `SubagentActivity`: le même
 * vocabulaire que le gateway (`subagent.start` / `.tool` / `.progress` / `.complete`, et le
 * roster de `subagent.list`).
 *
 * Deux provenances, dans cet ordre de confiance:
 * - le socket du Dashboard (`hermes serve`) émet `subagent.*` **sur la session du parent**, avec
 * l'identifiant de l'enfant (`subagent_id`), son but, sa place dans une série, son dernier
 * outil, son modèle et l'identifiant de sa session (`child_session_id`) — tout est transporté;
 * - l'API de conversation (`/v1`) ne transporte **aucun** de ces champs: le seul indice est
 * l'appel d'outil `delegate_task`, dont les arguments nomment les tâches déléguées. D'où
 * [SubagentStatus.DISPATCHED]: le but est connu, l'état réel de l'enfant ne l'est pas.
 */
@Serializable
data class SubagentActivity(
    /** Le `subagent_id` du gateway (ou l'identifiant fabriqué depuis les arguments). */
    val id: String,
    /** Le but de la tâche déléguée, tel que le gateway l'annonce. */
    val goal: String = "",
    /** Sa place dans la série d'un même `delegate_task`: `1/N`. */
    val taskIndex: Int = 0,
    val taskCount: Int = 1,
    /** La profondeur d'imbrication (un enfant d'enfant), pour l'indentation des rangées. */
    val depth: Int = 0,
    val status: SubagentStatus = SubagentStatus.RUNNING,
    val toolCount: Int = 0,
    /** Le dernier outil appelé, ou la dernière ligne de progression. */
    val lastTool: String? = null,
    val summary: String? = null,
    val durationSeconds: Double? = null,
    /** La session de l'enfant, seule clé qui ouvre sa transcription (`session.resume`). */
    val childSessionId: String? = null,
    val model: String? = null,
    /** Quand l'app a vu la tâche commencer: sert au temps écoulé tant que le serveur n'en donne pas. */
    val startedAt: Long = 0L,
    /** `accepting_steer` du roster: faux quand l'enfant n'écoute plus de consigne. */
    val acceptingSteer: Boolean = true,
) {
    /** Vrai tant que la tâche travaille (ou est réputée travailler). */
    val isLive: Boolean get() = status.isLive

    /** Vrai quand on sait ouvrir sa transcription: il faut la session de l'enfant. */
    val hasTranscript: Boolean get() = !childSessionId.isNullOrBlank()

    /**
     * Le temps écoulé en secondes: la durée que le serveur a donnée si elle est là, sinon
     * l'horloge de l'app depuis le début de la tâche (le transport muet n'en fournit aucune).
     */
    fun elapsedSeconds(now: Long): Double =
        durationSeconds ?: ((now - startedAt).coerceAtLeast(0L) / 1000.0)

    /** Cette activité, mise à jour par [update]: la ligne se crée puis se modifie sur place. */
    fun applying(update: SubagentUpdate, now: Long): SubagentActivity {
        var next = copy(
            goal = update.goal.ifBlank { goal },
            taskIndex = if (update.taskCount > 1) update.taskIndex else taskIndex,
            taskCount = if (update.taskCount > 1) update.taskCount else taskCount,
            depth = maxOf(depth, update.depth),
            toolCount = update.toolCount ?: toolCount,
            childSessionId = update.childSessionId ?: childSessionId,
            model = update.model ?: model,
            acceptingSteer = update.acceptingSteer ?: acceptingSteer,
        )
        when (val phase = update.phase) {
            SubagentUpdate.Phase.Started -> next = next.copy(status = SubagentStatus.RUNNING)
            is SubagentUpdate.Phase.Tool -> next = next.copy(
                status = SubagentStatus.RUNNING,
                lastTool = phase.name.takeIf { it.isNotBlank() } ?: next.lastTool,
            )
            is SubagentUpdate.Phase.Progress -> next = next.copy(
                status = if (next.status == SubagentStatus.DISPATCHED) next.status else SubagentStatus.RUNNING,
                lastTool = phase.text.takeIf { it.isNotBlank() } ?: next.lastTool,
            )
            is SubagentUpdate.Phase.Completed -> next = next.copy(
                status = if (phase.succeeded) SubagentStatus.COMPLETED else SubagentStatus.FAILED,
                summary = phase.summary ?: next.summary,
                durationSeconds = phase.duration ?: next.durationSeconds,
            )
            SubagentUpdate.Phase.Dispatched -> next =
                if (next.status == SubagentStatus.RUNNING) next.copy(status = SubagentStatus.DISPATCHED) else next
            SubagentUpdate.Phase.SpawnRequested -> Unit
        }
        // Une tâche close du côté du serveur reste close: une trame rejouée ne la rouvre pas,
        // sinon une ligne terminée repasserait « en cours » et la liste mentirait.
        val completes = update.phase is SubagentUpdate.Phase.Completed
        return if (isFinished && next.status.isLive && !completes) next.copy(status = status) else next
    }

    /** Vrai quand le serveur (ou l'app) a clos cette tâche. */
    private val isFinished: Boolean
        get() = status == SubagentStatus.COMPLETED || status == SubagentStatus.FAILED
}

/** L'état d'un sous-agent, avec le vocabulaire du gateway. */
@Serializable
enum class SubagentStatus(val wire: String) {
    @SerialName("running")
    RUNNING("running"),

    /** Lancé mais suivi à l'aveugle: le but est connu, l'enfant travaille ailleurs. */
    @SerialName("dispatched")
    DISPATCHED("dispatched"),

    @SerialName("completed")
    COMPLETED("completed"),

    @SerialName("failed")
    FAILED("failed"),
    ;

    val isLive: Boolean get() = this == RUNNING || this == DISPATCHED
}

/**
 * Un changement d'un sous-agent (`TurnEvent.Subagent`).
 *
 * Comme `SubagentUpdate`. Les champs sont
 * tous optionnels sauf l'identifiant et la phase: chaque trame du gateway n'en porte qu'une part.
 */
data class SubagentUpdate(
    val id: String,
    val goal: String = "",
    val taskIndex: Int = 0,
    val taskCount: Int = 1,
    val depth: Int = 0,
    val toolCount: Int? = null,
    val childSessionId: String? = null,
    val model: String? = null,
    val acceptingSteer: Boolean? = null,
    val phase: Phase,
) {
    /** La phase d'une trame: ce qui vient de se passer pour cet enfant. */
    sealed interface Phase {
        data object Started : Phase
        data class Tool(val name: String) : Phase
        data class Progress(val text: String) : Phase
        data class Completed(val succeeded: Boolean, val summary: String?, val duration: Double?) : Phase
        data object Dispatched : Phase
        data object SpawnRequested : Phase
    }

    companion object {
        /**
         * Les types de `subagent.*` que le socket du Dashboard émet (contrat du gateway,
         * `tui_gateway/contracts/events.py`): `subagent.spawn_requested`, `.start`, `.progress`,
         * `.thinking`, `.tool`, `.complete`.
         */
        val EVENT_TYPES = setOf(
            "subagent.spawn_requested", "subagent.start", "subagent.progress",
            "subagent.thinking", "subagent.tool", "subagent.complete",
        )

        fun isSubagentEvent(type: String): Boolean = type in EVENT_TYPES

        /**
         * Une frame du gateway, telle que `SubagentUpdate(event:_:)` la lit.
         * `null` quand elle n'identifie pas l'enfant: sans identifiant, steer et stop
         * échoueraient en silence, ce qui vaut moins que pas de ligne du tout.
         */
        fun parse(type: String, payload: JsonObject): SubagentUpdate? {
            val id = payload.str("subagent_id") ?: payload.str("child_session_id") ?: return null
            val phase = when (type) {
                "subagent.start", "subagent.spawn_requested" -> Phase.Started
                "subagent.tool" -> Phase.Tool(payload.str("tool_name") ?: payload.str("text") ?: "tool")
                "subagent.progress", "subagent.thinking" ->
                    Phase.Progress(payload.str("text") ?: payload.str("tool_preview") ?: "")
                "subagent.complete" -> {
                    val status = (payload.str("status") ?: "success").lowercase()
                    Phase.Completed(
                        succeeded = status !in setOf("error", "failed", "timeout"),
                        summary = payload.str("summary") ?: payload.str("text"),
                        duration = payload.dbl("duration_seconds"),
                    )
                }
                else -> return null
            }
            return SubagentUpdate(
                id = id,
                goal = payload.str("goal") ?: "",
                taskIndex = payload.int("task_index") ?: 0,
                taskCount = payload.int("task_count") ?: 1,
                depth = payload.int("depth") ?: 0,
                toolCount = payload.int("tool_count"),
                childSessionId = payload.str("child_session_id"),
                model = payload.str("model"),
                acceptingSteer = payload.bool("accepting_steer"),
                phase = phase,
            )
        }

        /**
         * Une ligne de `subagent.list` (le roster du gateway, lu sans tour en cours).
         *
         * Le roster nomme les champs autrement que les trames: `parent_id`, `started_at`,
         * `last_tool`, `accepting_steer`. C'est la source du **temps écoulé** et du **dernier
         * outil** d'une liste ouverte alors que rien ne streame.
         */
        fun fromRoster(row: JsonObject): SubagentActivity? {
            val id = row.str("subagent_id") ?: return null
            val status = when ((row.str("status") ?: "running").lowercase()) {
                "completed", "done", "success" -> SubagentStatus.COMPLETED
                "failed", "error", "timeout" -> SubagentStatus.FAILED
                "dispatched", "queued" -> SubagentStatus.DISPATCHED
                else -> SubagentStatus.RUNNING
            }
            return SubagentActivity(
                id = id,
                goal = row.str("goal") ?: "",
                depth = row.int("depth") ?: 0,
                status = status,
                toolCount = row.int("tool_count") ?: 0,
                lastTool = row.str("last_tool"),
                childSessionId = row.str("child_session_id"),
                model = row.str("model"),
                startedAt = ((row.dbl("started_at") ?: 0.0) * 1000).toLong(),
                acceptingSteer = row.bool("accepting_steer") ?: true,
            )
        }

        /**
         * Les tâches déléguées lues dans les arguments d'un `delegate_task`.
         *
         * Comme `HermesSessionsTransport.delegatedGoals`
         * . Le gateway accepte une
         * `goal` unique ou un lot `tasks=[{goal:…}]`: les deux se lisent, et l'identifiant est
         * fabriqué (`delegate:{run}:{i}`) puisque l'API de conversation ne donne pas de
         * `subagent_id`. C'est le **seul** vestige d'un sous-agent sur ce transport.
         */
        fun delegatedGoals(args: String?, runId: String?): List<SubagentUpdate> {
            val text = args?.trim().orEmpty()
            if (text.isEmpty() || !text.startsWith("{")) return emptyList()
            val body = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
            val tasks = (body["tasks"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.mapNotNull { it.str("goal")?.takeIf { goal -> goal.isNotBlank() } }
                .orEmpty()
            val goals = tasks.ifEmpty {
                listOfNotNull(body.str("goal")?.takeIf { it.isNotBlank() })
            }
            if (goals.isEmpty()) return emptyList()
            val run = runId ?: "run"
            return goals.mapIndexed { index, goal ->
                SubagentUpdate(
                    id = "delegate:$run:$index",
                    goal = goal,
                    taskIndex = index,
                    taskCount = goals.size,
                    phase = Phase.Dispatched,
                )
            }
        }

        internal fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        internal fun JsonObject.int(key: String): Int? =
            (this[key] as? JsonPrimitive)?.intOrNull

        internal fun JsonObject.dbl(key: String): Double? =
            (this[key] as? JsonPrimitive)?.doubleOrNull

        internal fun JsonObject.bool(key: String): Boolean? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.let { content ->
                when (content.lowercase()) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> null
                }
            }
    }
}
