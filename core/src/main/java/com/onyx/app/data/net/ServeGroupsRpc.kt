package com.onyx.app.data.net

import com.onyx.app.data.model.serve.GroupApproval
import com.onyx.app.data.model.serve.GroupDraft
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupLiveSession
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupRoomStatus
import com.onyx.app.data.model.serve.GroupSessionSlice
import com.onyx.app.data.model.serve.GroupState
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les salles de bots d'Hermes, lues par le RPC du Dashboard.
 *
 * Miroir de `HermesServeClient.groupSupport` / `groupRooms` / `desktopRooms` / `groupState` /
 * `sendToGroup` / `groupLog` / `stopGroup` / `renameGroup` / `disbandGroup` / `retryGroupTask` /
 * `answerGroupApproval` / `groupLiveSessions` / `groupMemberSession` / `sessionEvents`
 * (Echo/Services/Transport/ServerGroups:306) : `groups.capabilities` dit si ce Dashboard sait
 * tenir une salle et s'il peut en ouvrir une maintenant ; `groups.list` les liste, paginées ;
 * `groups.log` donne ce qui y a été dit, `groups.state` ce qu'elle attend, `groups.send` y écrit et
 * `groups.stop` arrête les bots ; `profiles.list` porte la copie des salles qu'Hermes Desktop tient
 * lui-même (`ui_meta["hermes-bots-groups"]`) ; `session.active_list` / `session.list` /
 * `session.events.since` donnent les réponses qu'un bot écrit dans sa session cachée
 * « Group: <salle> », que la salle n'a pas encore.
 */
class ServeGroupsRpc(private val rpc: RpcChannel) : GroupRoomApi {

    constructor(rpc: ServeRpc) : this(rpc.asChannel())

    /** Ce que ce Dashboard sait des salles : `supported`, et `writable` s'il peut en ouvrir une. */
    suspend fun support(): GroupSupport {
        val caps = try {
            rpc.call("groups.capabilities", buildJsonObject { }, SUPPORT_TIMEOUT)
        } catch (_: TransportError) {
            // Méthode inconnue (un Hermes plus ancien, ou pas de plugin) : pas de salles.
            return GroupSupport(supported = false, writable = false)
        }
        return supportFrom(caps)
    }

    /** Toutes les salles qu'Hermes héberge, page après page (`groups.list`). */
    suspend fun rooms(): List<GroupRoom> {
        val rooms = mutableListOf<GroupRoom>()
        var offset = 0
        repeat(PAGES) {
            val page = rpc.call(
                "groups.list",
                buildJsonObject {
                    put("limit", MAX_PAGE)
                    put("offset", offset)
                },
                CALL_TIMEOUT,
            )
            rooms += roomsPage(page)
            val next = page.asObject()?.int("next_offset") ?: return rooms
            offset = next
        }
        return rooms
    }

    /**
     * Ouvre une salle (`groups.create`), deux à six membres : le corps d'appel est celui de la
     * référence — l'identifiant de la salle, son nom, et ses membres, chacun avec son profil, sa
     * poignée et son nom de salle. Rend la salle telle qu'Hermes l'a faite, `null` s'il n'en a
     * rien dit : l'appelant ne doit alors pas croire qu'elle existe.
     */
    suspend fun create(id: String, name: String, members: List<GroupMember>): GroupRoom? {
        val result = rpc.call(
            "groups.create",
            buildJsonObject {
                put("room_id", id)
                put("name", name.take(MAX_NAME))
                put("members", buildJsonArray { members.forEach { add(it.wire()) } })
            },
            CALL_TIMEOUT,
        )
        return result.asObject()?.obj("room")?.let(GroupRoom::from)
    }

    /**
     * Les salles qu'Hermes Desktop tient lui-même : sa copie est dans le `ui_meta` du profil
     * principal (`GroupRoom.desktopRooms`).
     */
    override suspend fun desktopRooms(): List<GroupRoom> {
        val result = rpc.call(
            "profiles.list",
            buildJsonObject { put("include_sessions", false) },
            SUPPORT_TIMEOUT,
        )
        return desktopRoomsFrom(result)
    }

    /** La salle telle qu'Hermes la voit, et ce qu'elle attend (`groups.state`). */
    override suspend fun roomState(room: String): GroupRoomStatus {
        val result = rpc.call("groups.state", buildJsonObject { put("room_id", room) }, CALL_TIMEOUT)
        val root = result.asObject()
        return GroupRoomStatus(
            room = root?.obj("room")?.let(GroupRoom::from),
            state = GroupState.from(root?.get("driver_status")),
        )
    }

    /**
     * Dit `text` dans la salle (`groups.send`). Le même corps d'appel que la référence : la salle, le
     * message et son identifiant propre (`event_id` : envoyé deux fois, c'est le même message), et
     * la charge utile du texte dans le fil « main ».
     */
    override suspend fun send(room: String, text: String, eventID: String) {
        rpc.call(
            "groups.send",
            buildJsonObject {
                put("room_id", room)
                put("event_id", eventID)
                put(
                    "payload",
                    buildJsonObject {
                        put("text", text)
                        put("thread_id", "main")
                    },
                )
            },
            CALL_TIMEOUT,
        )
    }

    /** Le journal de la salle après `since`, page après page (`groups.log`). */
    override suspend fun log(room: String, since: Int): List<GroupEvent> {
        val events = mutableListOf<GroupEvent>()
        var cursor = since
        repeat(LOG_PAGES) {
            val page = rpc.call(
                "groups.log",
                buildJsonObject {
                    put("room_id", room)
                    put("since_seq", cursor)
                    put("limit", LOG_PAGE)
                },
                CALL_TIMEOUT,
            )
            val root = page.asObject()
            val rows = root?.array("events").orEmpty()
                .mapNotNull { it.asObject()?.let(GroupEvent::from) }
                .filter { it.seq > cursor }
            events += rows
            cursor = maxOf(cursor, root?.int("cursor") ?: rows.lastOrNull()?.seq ?: cursor)
            if (root?.bool("has_more") != true) return events
        }
        return events
    }

    /** Arrête les bots de la salle (`groups.stop`). Un `cancel_id` neuf à chaque fois. */
    override suspend fun stop(room: String) {
        rpc.call(
            "groups.stop",
            buildJsonObject {
                put("room_id", room)
                put("cancel_id", "stop-" + uuid())
            },
            CALL_TIMEOUT,
        )
    }

    /** Renomme la salle (`groups.rename`). */
    override suspend fun rename(room: String, name: String) {
        rpc.call(
            "groups.rename",
            buildJsonObject {
                put("room_id", room)
                put("event_id", "rename-" + uuid())
                put("name", name.take(MAX_NAME))
            },
            CALL_TIMEOUT,
        )
    }

    /**
     * Termine la salle pour de bon (`groups.disband`). Des bots encore en train de s'arrêter font
     * qu'Hermes refuse un moment : on essaie de nouveau.
     */
    override suspend fun disband(room: String) {
        repeat(DISBAND_TRIES) { attempt ->
            try {
                rpc.call("groups.disband", buildJsonObject { put("room_id", room) ; put("cancel_id", "disband-" + uuid()) }, CALL_TIMEOUT)
                return
            } catch (error: TransportError) {
                val stopping = error.message.orEmpty().contains("stopping", ignoreCase = true)
                if (!stopping || attempt == DISBAND_TRIES - 1) throw error
                kotlinx.coroutines.delay(DISBAND_PAUSE)
            }
        }
    }

    /** Relance une réponse arrêtée à mi-chemin (`groups.retry`). */
    override suspend fun retry(room: String, task: String) {
        rpc.call(
            "groups.retry",
            buildJsonObject {
                put("room_id", room)
                put("task_id", task)
            },
            CALL_TIMEOUT,
        )
    }

    /** Répond à la commande d'un bot : « once » ou « deny » (`groups.approve`). */
    override suspend fun approve(room: String, approval: GroupApproval, choice: String) {
        rpc.call(
            "groups.approve",
            buildJsonObject {
                put("room_id", room)
                put("member_id", approval.memberID)
                put("task_id", approval.taskID)
                put("execution_generation", approval.executionGeneration)
                put("request_id", approval.request.id)
                put("choice", choice)
            },
            CALL_TIMEOUT,
        )
    }

    /**
     * Les sessions vivantes des bots d'une salle (`session.active_list`), avec leur état
     * (« working », « starting », « waiting », « idle »). Celles d'une salle sont titrées
     * « Group: <salle> » et vivent sur le profil de chaque bot.
     */
    override suspend fun liveSessions(room: String): List<GroupLiveSession> {
        val result = rpc.call("session.active_list", buildJsonObject { }, SUPPORT_TIMEOUT)
        val title = GroupDraft.sessionTitle(room)
        return result.asObject()?.array("sessions").orEmpty().mapNotNull { row ->
            val session = row.asObject() ?: return@mapNotNull null
            if (session.strOrNull("title") != title) return@mapNotNull null
            val id = session.strOrNull("id") ?: return@mapNotNull null
            GroupLiveSession(id = id, key = session.str("session_key"), status = session.str("status"))
        }
    }

    /**
     * La session d'un bot pour une salle, par son titre sur le profil du bot : son identifiant
     * gardé (la dernière partie, quand Hermes l'a comprimé), celui dont sa session vivante se sert.
     */
    override suspend fun memberSession(room: String, profile: String): String? {
        val title = GroupDraft.sessionTitle(room)
        val result = rpc.call(
            "session.list",
            buildJsonObject {
                put("profile", profile)
                put("title", title)
                put("include_hidden", true)
                put("limit", 20)
            },
            CALL_TIMEOUT,
        )
        val row = result.asObject()?.array("sessions").orEmpty()
            .mapNotNull { it.asObject() }
            .firstOrNull { it.strOrNull("title") == title } ?: return null
        return row.strOrNull("resolved_id") ?: row.strOrNull("id")
    }

    /**
     * Ce qu'une session vivante a envoyé après l'événement `since`, tel qu'Hermes le garde pour un
     * client qui se reconnecte : `truncated` quand certains sont partis (il garde les 512 derniers).
     */
    override suspend fun sessionEvents(session: String, since: Int): GroupSessionSlice {
        val result = rpc.call(
            "session.events.since",
            buildJsonObject {
                put("session_id", session)
                put("last_seen", since)
            },
            CALL_TIMEOUT,
        )
        val root = result.asObject()
        return GroupSessionSlice(
            events = root?.array("events").orEmpty(),
            latest = root?.int("latest_seq") ?: since,
            truncated = root?.bool("truncated") == true,
        )
    }

    companion object {
        const val CALL_TIMEOUT = 60_000L
        const val SUPPORT_TIMEOUT = 15_000L
        const val MAX_PAGE = 100
        const val PAGES = 10
        const val LOG_PAGE = 200
        const val LOG_PAGES = 50
        const val MAX_NAME = 200
        const val DISBAND_TRIES = 5
        const val DISBAND_PAUSE = 2_000L

        /** Ce qu'un `groups.capabilities` autorise : les cinq méthodes, et le worker à l'écoute. */
        fun supportFrom(caps: JsonElement): GroupSupport {
            val root = caps.asObject()
            val methods = root?.array("methods")?.mapNotNull { it.asString() }?.toSet().orEmpty()
            val needed = listOf("groups.list", "groups.create", "groups.state", "groups.send", "groups.log")
            val supported = needed.all { it in methods }
            return GroupSupport(supported = supported, writable = supported && root?.bool("driver") == true)
        }

        /** Les salles d'une page de `groups.list`. */
        fun roomsPage(page: JsonElement): List<GroupRoom> =
            page.asObject()?.array("rooms")?.mapNotNull { it.asObject()?.let(GroupRoom::from) }.orEmpty()

        /** Les salles Desktop d'une réponse de `profiles.list`. */
        fun desktopRoomsFrom(profiles: JsonElement): List<GroupRoom> {
            val rows = profiles.asObject()?.array("profiles").orEmpty()
            val main = rows.mapNotNull { it.asObject() }
                .firstOrNull { it.strOrNull("name") == "default" || it.bool("is_default") == true }
            return GroupRoom.desktopRooms(main?.obj("ui_meta")?.get("hermes-bots-groups"))
        }

        private fun uuid(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * Les appels d'une salle dont un fil a besoin : ce qui y a été dit, ce qu'elle attend, l'envoi,
 * l'arrêt, et les sessions cachées où s'écrivent les réponses en cours. `ServeGroupsRpc` les fait
 * sur le socket du Dashboard ; un fil se laisse ainsi éprouver sans socket.
 */
interface GroupRoomApi {
    suspend fun log(room: String, since: Int): List<GroupEvent>
    suspend fun roomState(room: String): GroupRoomStatus
    suspend fun send(room: String, text: String, eventID: String)
    suspend fun stop(room: String)
    suspend fun rename(room: String, name: String)
    suspend fun disband(room: String)
    suspend fun retry(room: String, task: String)
    suspend fun approve(room: String, approval: GroupApproval, choice: String)
    suspend fun desktopRooms(): List<GroupRoom>
    suspend fun liveSessions(room: String): List<GroupLiveSession>
    suspend fun memberSession(room: String, profile: String): String?
    suspend fun sessionEvents(session: String, since: Int): GroupSessionSlice
}

/** Ce qu'un Dashboard sait des salles (`groups.capabilities`). */
data class GroupSupport(val supported: Boolean, val writable: Boolean)
