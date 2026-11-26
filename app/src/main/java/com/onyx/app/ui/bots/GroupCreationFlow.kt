package com.onyx.app.ui.bots

import com.onyx.app.data.local.GroupRoomStore
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupRoomDraft
import com.onyx.app.data.net.ServeGroupsRpc
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.humanFailure
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.conversations.GroupCreation
import kotlinx.coroutines.CancellationException

/**
 * The name of the requested room: the one that was typed, or failing that the named members as
 * they are called — "Onyx, Scout, and Quill" (`formatted(.list(type: .and))` of the reference).
 */
internal fun groupTitle(name: String, bots: List<BotProfile>, mainName: String): String =
    name.trim().ifBlank { roomMembersLabel(bots.map { it.label(mainName) }) }

/** A room as the list draws it (`GroupChat`): its members recognized in the roster. */
internal fun roomChat(room: GroupRoom, bots: List<BotProfile>): GroupChat = GroupChat(
    id = room.id,
    name = room.name,
    members = room.members.map { member -> botFor(member, bots) },
    updatedAt = room.updatedAtMillis ?: room.createdAtMillis,
    activityMark = room.activityMark,
    inDesktop = room.inDesktop,
    local = room.local,
)

/** A room member's profile: the bot when it is known, otherwise only its names. */
internal fun botFor(member: GroupMember, bots: List<BotProfile>): BotProfile =
    bots.firstOrNull { it.name == member.profile }
        ?: BotProfile(
            name = member.profile,
            title = member.displayName,
            isDefault = member.profile == "default",
        )

/** Opens a room on the Dashboard (`groups.create`). The real call; off-device it gives nothing. */
internal suspend fun openGroupOnServer(config: ServerConfig, draft: GroupRoomDraft): GroupRoom? {
    val rpc = ServeRpc(config)
    return try {
        ServeGroupsRpc(rpc).create(draft.id, draft.name, draft.members)
    } finally {
        rpc.close()
    }
}

/**
 * "New Group Chat", end to end: the room of these bots, made **now**.
 *
 * Two paths, like the reference. When the connection carries Dashboard credentials, the room is
 * **opened on the server** (`groups.create`, [server]): it then appears in the list with Hermes's
 * other rooms. Without a Dashboard, it is **kept on this phone** ([store]), with its members, and
 * is found again after a restart.
 *
 * The same name never makes two rooms: the one that already carries this name (`known`) comes
 * back as is. A server failure returns what caused it, and **nothing** is kept — never a
 * half-made room, never a silent success.
 */
internal suspend fun createGroupRoom(
    config: ServerConfig,
    bots: List<BotProfile>,
    known: List<GroupRoom>,
    store: GroupRoomStore,
    /** The connection whose name the kept room carries (`GroupRoomStore`, `BotActivityStore`). */
    serverKey: String,
    name: String,
    members: Set<String>,
    mainName: String,
    /** Opens the room on the server; without it, the Dashboard RPC handles it. */
    server: (suspend (GroupRoomDraft) -> GroupRoom?)? = null,
): GroupCreation {
    // The constraint is **the server's** (`gateway/hosted_room_discussion.py`: "between 2 and 6
    // entries"), and it applies **before** the call: a seventh bot does not leave, it is refused
    // here with its reason — and nothing is truncated silently.
    val chosen = bots.filter { it.name in members }
    if (chosen.size < GroupRoom.MIN_MEMBERS) {
        return GroupCreation.Failed(OnyxLocalization["Pick two to six bots for the group."])
    }
    if (chosen.size > GroupRoom.MAX_MEMBERS) {
        return GroupCreation.Failed(
            OnyxLocalization.format(
                "Take %1\$d out: %2\$d bots at most.",
                chosen.size - GroupRoom.MAX_MEMBERS,
                GroupRoom.MAX_MEMBERS,
            ),
        )
    }
    val title = groupTitle(name, chosen, mainName)
    // A name already taken: it is that room that comes back, nothing new is opened.
    GroupRoom.named(known, title)?.let { existing ->
        return GroupCreation.Made(roomChat(existing, bots))
    }

    val draft = GroupRoomDraft.of(name = title, bots = chosen, mainName = mainName)
    if (!config.hasServeCredentials) {
        // No Dashboard: the room stays here, with its members, and survives the restart.
        return GroupCreation.Made(roomChat(store.save(serverKey, draft), bots))
    }
    return try {
        val made = (server ?: { openGroupOnServer(config, it) })(draft)
            ?: return GroupCreation.Failed(OnyxLocalization["The server made no group."])
        GroupCreation.Made(roomChat(made, bots))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // The refusal is read as is: the sheet shows it, and nothing was left half-done.
        GroupCreation.Failed(
            OnyxLocalization.format("Couldn't create the group: %1\$s", reason(error)),
        )
    }
}

/** An error's text, as it is shown elsewhere. */
private fun reason(error: Exception): String =
    humanFailure(error, OnyxLocalization["unknown error"])
