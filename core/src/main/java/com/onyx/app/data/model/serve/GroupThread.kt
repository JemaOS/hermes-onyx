package com.onyx.app.data.model.serve

import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.net.array
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.asString
import com.onyx.app.data.net.bool
import com.onyx.app.data.net.firstString
import com.onyx.app.data.net.int
import com.onyx.app.data.net.long
import com.onyx.app.data.net.obj
import com.onyx.app.data.net.str
import com.onyx.app.data.net.strOrNull
import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Le fil d'une salle de bots: ce qui y a été dit, ce qu'elle attend, et la réponse qu'un bot est
 * en train d'écrire.
 *
 * Comme `GroupEvent` / `GroupState` / `GroupApproval` / `GroupDraft`
 * (198,:279 et:237).
 */

/** Une entrée du journal d'une salle (`groups.log`). */
data class GroupEvent(
    val seq: Int,
    val kind: String,
    /** `user`, `member` (ou `system`, `gateway`). */
    val actorKind: String,
    /**
     * Le membre dont il s'agit: celui qui a parlé, ou celui dont c'était le tour (Hermes écrit la
     * fin d'un tour en son nom propre, la passerelle, avec le membre dans la charge utile).
     */
    val actorID: String?,
    val payload: JsonObject,
    val createdAtMillis: Long,
) {
    val text: String get() = payload.str("text")

    /** Quelque chose que quelqu'un a dit: la personne, ou un bot. */
    val isMessage: Boolean get() = kind == "message.user" || kind == "message.member"

    /** La discussion est finie: elle a fait son cours, touché sa limite, ou a été arrêtée. */
    val endsDiscussion: Boolean get() = kind == "room.activity" || kind == "room.stop_requested"

    /** Une ligne sur la salle plutôt que quelque chose de dit, ou `null` quand elle ne vaut rien. */
    fun note(room: GroupRoom): String? {
        val who = room.member(actorID)?.label ?: OnyxLocalization["A bot"]
        return when (kind) {
            "turn.settled" -> if (payload.bool("passed") == true) OnyxLocalization.format("%1\$s passed", who) else null
            "turn.failed" ->
                OnyxLocalization.format("%1\$s couldn't answer", who) +
                    (payload.strOrNull("error")?.let { ": $it" } ?: "")
            "turn.cancelled" ->
                if (payload.strOrNull("reason") == "superseded_by_newer_user_event") null
                else OnyxLocalization.format("%1\$s was stopped", who)
            "turn.deferred" -> OnyxLocalization.format("%1\$s didn't finish", who)
            "room.activity" ->
                if (payload.strOrNull("status") == "bounded") {
                    OnyxLocalization["That's as long as a round goes. Say something to carry on."]
                } else {
                    null
                }
            "room.renamed" -> payload.strOrNull("name")?.let { OnyxLocalization.format("Renamed “%1\$s”", it) }
            else -> null
        }
    }

    companion object {
        /** Une ligne de `groups.log`; `null` quand il lui manque son numéro ou son genre. */
        fun from(json: JsonObject): GroupEvent? {
            val seq = json.int("seq") ?: return null
            val kind = json.strOrNull("kind") ?: return null
            val actorKind = json.obj("actor")?.str("kind").orEmpty()
            val payload = json.obj("payload")
            return GroupEvent(
                seq = seq,
                kind = kind,
                actorKind = actorKind,
                // Le membre d'un événement de tour (« turn.settled ») n'est que dans la charge utile.
                actorID = payload?.firstString("member_id")
                    ?: if (actorKind == "member") json.obj("actor")?.strOrNull("id") else null,
                payload = payload ?: JsonObject(emptyMap()),
                createdAtMillis = json.long("created_at")?.let { GroupRoom.secondsToMillis(it) }
                    ?: System.currentTimeMillis(),
            )
        }
    }
}

/** Ce qu'une salle attend, et si un bot est au travail (`groups.state`). */
data class GroupState(
    val working: Boolean = false,
    /**
     * Un arrêt en cours, ou une réponse qu'Hermes a perdue de vue et remet en ordre: pas une
     * question pour la personne (celles-là sont les `approvals` et les `retries`).
     */
    val blocked: Boolean = false,
    /** Les bots qui s'arrêtent après Stop. */
    val stopping: Boolean = false,
    val approvals: List<GroupApproval> = emptyList(),
    /**
     * Les réponses arrêtées à mi-chemin, Hermes ne sachant pas jusqu'où elles sont allées: leurs
     * tâches, chacune relancée seulement si la personne le demande (`groups.retry`).
     */
    val retries: List<String> = emptyList(),
) {
    /** Quelque chose que seule la personne peut régler: une commande à approuver, ou une réponse à relancer. */
    val waitsOnPerson: Boolean get() = approvals.isNotEmpty() || retries.isNotEmpty()

    companion object {
        /** Le `driver_status` de `groups.state`. */
        fun from(status: JsonElement?): GroupState {
            val root = status.asObject()
            val pending = root?.array("pending_actions").orEmpty()
            return GroupState(
                working = root?.bool("working") == true,
                blocked = root?.bool("blocked") == true,
                stopping = (root?.obj("counts")?.int("stopping") ?: 0) > 0,
                approvals = pending.mapNotNull { it.asObject()?.let(GroupApproval::from) },
                retries = pending.mapNotNull { row ->
                    row.asObject()?.takeIf { it.strOrNull("kind") == "retry" }?.strOrNull("task_id")
                },
            )
        }
    }
}

/** Une commande qu'un bot d'une salle attend de faire approuver. */
data class GroupApproval(
    val memberID: String,
    val taskID: String,
    val executionGeneration: Int,
    val request: ApprovalRequest,
) {
    val id: String get() = request.id

    companion object {
        /**
         * Une ligne de `pending_actions` (`kind` = « approval »). Une salle ne prend que « once »
         * ou « deny »: les autres choix sont retirés, comme.
         */
        fun from(json: JsonObject): GroupApproval? {
            if (json.strOrNull("kind") != "approval") return null
            val member = json.strOrNull("member_id") ?: return null
            val task = json.strOrNull("task_id") ?: return null
            val generation = json.int("execution_generation") ?: return null
            val approval = json.obj("approval")
            val requestID = json.strOrNull("request_id") ?: approval?.strOrNull("request_id") ?: return null
            val choices = (approval?.array("choices").orEmpty().mapNotNull { it.asString() })
                .filter { it == ApprovalRequest.CHOICE_ONCE || it == ApprovalRequest.CHOICE_DENY }
            return GroupApproval(
                memberID = member,
                taskID = task,
                executionGeneration = generation,
                request = ApprovalRequest.fromPayload(
                    id = requestID,
                    command = approval?.str("command").orEmpty(),
                    description = approval?.strOrNull("description"),
                    choices = choices.ifEmpty { listOf(ApprovalRequest.CHOICE_ONCE, ApprovalRequest.CHOICE_DENY) },
                    allowSession = false,
                    allowPermanent = false,
                    runId = null,
                ),
            )
        }
    }
}

/**
 * La réponse d'un bot dans une salle, telle qu'elle s'écrit. Hermes n'écrit une réponse dans la
 * salle qu'une fois finie; entre-temps elle est dans la session propre du bot pour cette salle,
 * une session cachée de son profil titrée « Group: <salle> », dont Hermes garde les derniers
 * événements pour un client qui se reconnecte (`session.events.since`). C'est de là qu'elle est lue.
 */
data class GroupDraft(
    /** L'identifiant de la session vivante. */
    val id: String,
    /** Le bot qui l'écrit, une fois sa session retrouvée sur son profil. */
    val memberID: String? = null,
    val text: String = "",
    /** Quand la session du bot a fini la réponse: elle s'affiche jusqu'à ce que la salle l'ait. */
    val finishedAtMillis: Long? = null,
    /** Le dernier événement du journal de la salle quand elle a commencé. */
    val after: Int,
    val beganMillis: Long = System.currentTimeMillis(),
) {
    /** Le résultat d'une lecture: le brouillon à jour, et si un début de réponse y était. */
    data class Read(val draft: GroupDraft, val started: Boolean)

    /**
     * `events` (de `session.events.since`, du plus ancien au plus récent) lus sur le brouillon:
     * une réponse recommence à chaque `message.start`, et à chaque `message.interim` (ce qu'un bot
     * a écrit à côté d'un appel d'outil, que la réponse dans la salle laisse de côté), grandit à
     * chaque `message.delta`, et est entière à `message.complete`. `started` est vrai quand un
     * début de réponse était parmi eux.
     */
    fun read(events: List<JsonElement>): Read {
        var text = this.text
        var finished = finishedAtMillis
        var started = false
        for (event in events) {
            val row = event.asObject() ?: continue
            when (row.strOrNull("type")) {
                "message.start" -> {
                    text = ""
                    finished = null
                    started = true
                }

                "message.interim" -> text = ""
                "message.delta" -> text += row.obj("payload")?.str("text").orEmpty()
                "message.complete" -> {
                    val whole = row.obj("payload")?.strOrNull("text")
                    if (!whole.isNullOrEmpty()) text = whole
                    finished = System.currentTimeMillis()
                }

                else -> Unit
            }
        }
        return Read(copy(text = text, finishedAtMillis = finished), started)
    }

    /** Une réponse finie depuis un moment (`finishedAtMillis`), et qui n'écrit donc plus. */
    fun finishedBefore(cutoffMillis: Long): Boolean = finishedAtMillis?.let { it <= cutoffMillis } == true

    companion object {
        /** Le titre qu'Hermes donne à la session d'un bot pour une salle. */
        fun sessionTitle(room: String): String = "Group: $room"
    }
}

/** Une session vivante d'un bot dans une salle (`session.active_list`). */
data class GroupLiveSession(val id: String, val key: String, val status: String)

/** Ce qu'une session vivante a envoyé depuis un numéro (`session.events.since`). */
data class GroupSessionSlice(
    val events: List<JsonElement> = emptyList(),
    val latest: Int = 0,
    /** Vrai quand des événements intermédiaires sont partis (Hermes garde les 512 derniers). */
    val truncated: Boolean = false,
)

/** Ce que `groups.state` répond pour une salle: la salle telle qu'Hermes la voit, et son état. */
data class GroupRoomStatus(val room: GroupRoom?, val state: GroupState)
