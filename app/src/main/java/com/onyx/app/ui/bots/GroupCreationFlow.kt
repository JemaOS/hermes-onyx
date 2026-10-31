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
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.conversations.GroupCreation
import kotlinx.coroutines.CancellationException

/**
 * Le nom de la salle demandée : celui qu'on a tapé, ou à défaut les membres nommés comme on les
 * appelle — « Onyx, Scout, and Quill » (`formatted(.list(type: .and))` de la référence).
 */
internal fun groupTitle(name: String, bots: List<BotProfile>, mainName: String): String =
    name.trim().ifBlank { roomMembersLabel(bots.map { it.label(mainName) }) }

/** Une salle telle que la liste la dessine (`GroupChat`) : ses membres reconnus dans le roster. */
internal fun roomChat(room: GroupRoom, bots: List<BotProfile>): GroupChat = GroupChat(
    id = room.id,
    name = room.name,
    members = room.members.map { member -> botFor(member, bots) },
    updatedAt = room.updatedAtMillis,
    activityMark = room.activityMark,
    inDesktop = room.inDesktop,
    local = room.local,
)

/** Le profil d'un membre de salle : le bot quand on le connaît, sinon ses seuls noms. */
internal fun botFor(member: GroupMember, bots: List<BotProfile>): BotProfile =
    bots.firstOrNull { it.name == member.profile }
        ?: BotProfile(
            name = member.profile,
            title = member.displayName,
            isDefault = member.profile == "default",
        )

/** Ouvre une salle sur le Dashboard (`groups.create`). Le vrai appel ; hors appareil il se donne. */
internal suspend fun openGroupOnServer(config: ServerConfig, draft: GroupRoomDraft): GroupRoom? {
    val rpc = ServeRpc(config)
    return try {
        ServeGroupsRpc(rpc).create(draft.id, draft.name, draft.members)
    } finally {
        rpc.close()
    }
}

/**
 * « New Group Chat », de bout en bout : la salle de ces bots, faite **maintenant**.
 *
 * Deux chemins, comme la référence. Quand la connexion porte des identifiants de Dashboard, la
 * salle est **ouverte sur le serveur** (`groups.create`, [server]) : elle apparaît alors dans la
 * liste avec les autres salles d'Hermes. Sans Dashboard, elle est **gardée sur ce téléphone**
 * ([store]), avec ses membres, et se retrouve après une relance.
 *
 * Le même nom ne fait jamais deux salles : celle qui porte déjà ce nom (`known`) revient telle
 * quelle. Un échec du serveur rend ce qui l'a causé, et **rien** n'est gardé — jamais une salle à
 * moitié faite, jamais un succès muet.
 */
internal suspend fun createGroupRoom(
    config: ServerConfig,
    bots: List<BotProfile>,
    known: List<GroupRoom>,
    store: GroupRoomStore,
    /** La connexion dont la salle gardée porte le nom (`GroupRoomStore`, `BotActivityStore`). */
    serverKey: String,
    name: String,
    members: Set<String>,
    mainName: String,
    /** Ouvre la salle sur le serveur ; sans lui, c'est le RPC du Dashboard qui s'en charge. */
    server: (suspend (GroupRoomDraft) -> GroupRoom?)? = null,
): GroupCreation {
    // La contrainte est **celle du serveur** (`gateway/hosted_room_discussion.py` : « between 2 and 6
    // entries »), et elle s'applique **avant** l'appel : un septième bot ne part pas, il est refusé
    // ici avec sa raison — et rien n'est tronqué en silence.
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
    // Un nom déjà pris : c'est cette salle qui revient, rien de neuf n'est ouvert.
    GroupRoom.named(known, title)?.let { existing ->
        return GroupCreation.Made(roomChat(existing, bots))
    }

    val draft = GroupRoomDraft.of(name = title, bots = chosen, mainName = mainName)
    if (!config.hasServeCredentials) {
        // Pas de Dashboard : la salle reste ici, avec ses membres, et survit à la relance.
        return GroupCreation.Made(roomChat(store.save(serverKey, draft), bots))
    }
    return try {
        val made = (server ?: { openGroupOnServer(config, it) })(draft)
            ?: return GroupCreation.Failed(OnyxLocalization["The server made no group."])
        GroupCreation.Made(roomChat(made, bots))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Le refus se lit tel quel : la feuille le montre, et rien n'est resté à moitié.
        GroupCreation.Failed(
            OnyxLocalization.format("Couldn't create the group: %1\$s", reason(error)),
        )
    }
}

/** Le texte d'une erreur, tel qu'on le montre ailleurs. */
private fun reason(error: Exception): String =
    (error as? TransportError)?.userMessage() ?: error.message ?: OnyxLocalization["unknown error"]
