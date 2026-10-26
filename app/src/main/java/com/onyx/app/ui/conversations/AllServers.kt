package com.onyx.app.ui.conversations

import com.onyx.app.data.model.Conversation
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.roomMembersLabel

/** Un serveur enregistré, tel qu'un menu ou une étiquette le porte (`ServerTag` ). */
data class ServerTag(val id: String, val name: String)

/**
 * Une rangée de la liste unifiée: la conversation, et le serveur qui la porte.
 *
 * `serverName` absent = la conversation du serveur où l'app est (jamais marquée); présent =
 * celle d'un autre serveur, marquée de son nom — le `SessionSummary.otherServer`.
 */
data class AllServerRow(
    val conversation: Conversation,
    val serverId: String,
    val serverName: String? = null,
) {
    /** La rangée vient d'un autre serveur que celui où l'app est. */
    val remote: Boolean get() = serverName != null

    /**
     * La clé **composite** de la rangée. Deux serveurs peuvent nommer deux de leurs conversations
     * de la même façon (`id` identique): la sélection, le volet ouvert et la liste ne doivent
     * jamais les confondre, donc la clé porte le serveur autant que la conversation.
     */
    val key: String get() = AllServers.key(serverId, conversation.id)
}

/**
 * « Tous vos serveurs dans une seule liste »: les conversations de chaque serveur enregistré
 * réunies, chacune marquée de son serveur.
 *
 * Comme: le serveur où l'app est liste ses conversations comme avant;
 * chacun des autres liste les siennes, et un serveur qui ne répond pas est **nommé** au-dessus de
 * la liste au lieu de laisser un vide. Les identifiants de conversation ne sont pas uniques d'un
 * serveur à l'autre, d'où la clé composite [key].
 */
object AllServers {

    /** Combien de temps un serveur peut prendre: un serveur éteint ne bloque pas la liste. */
    const val PATIENCE_MS = 8_000L

    /** La clé composite d'une rangée: le serveur, puis l'identifiant de la conversation. */
    fun key(serverId: String, conversationId: String): String = "$serverId\u0000$conversationId"

    /**
     * Les conversations de chaque serveur **autre** que celui où l'app est, chacune étiquetée de
     * son serveur — dans l'ordre des serveurs enregistrés, comme `settings.servers.filter` du
     *
     * Android garde ses conversations sur le téléphone, chacune marquée du serveur avec qui elle a
     * été écrite ([Conversation.serverId]): « les conversations d'un autre serveur » sont donc
     * celles qui portent son identifiant. Une conversation sans serveur ([Conversation.serverId]
     * vide, données d'avant les serveurs multiples) appartient à celui où l'app est, jamais à un
     * autre; celle d'un serveur qu'on a retiré ne s'affiche pas ici, comme.
     */
    fun others(
        conversations: List<Conversation>,
        activeServerId: String,
        servers: List<ServerTag>,
    ): List<Pair<ServerTag, List<Conversation>>> {
        val byServer = conversations
            .filter { it.serverId.isNotBlank() && it.serverId != activeServerId }
            .groupBy { it.serverId }
        return servers
            .filter { it.id != activeServerId }
            .map { tag -> tag to byServer[tag.id].orEmpty() }
    }

    /**
     * Les rangées du serveur où l'app est, puis celles des autres, chacune marquée de son serveur,
     * triées de la plus récente à la plus ancienne — le tri 
     * (`($0.lastActiveDate ?? .distantPast) > …`), ici `updatedAt` (millisecondes).
     *
     * Une conversation déjà listée ne l'est qu'une fois, comme le `seen.insert($0.id).inserted` du
     * (un même Hermes enregistré deux fois).
     */
    fun merge(
        own: List<Conversation>,
        ownServerId: String,
        others: List<Pair<ServerTag, List<Conversation>>>,
    ): List<AllServerRow> {
        val rows = ArrayList<AllServerRow>(own.size)
        val seen = HashSet<String>(own.size)
        own.forEach { conversation ->
            if (seen.add(conversation.id)) rows += AllServerRow(conversation, ownServerId)
        }
        others.forEach { (tag, list) ->
            list.forEach { conversation ->
                if (seen.add(conversation.id)) rows += AllServerRow(conversation, tag.id, tag.name)
            }
        }
        return rows.sortedWith(compareByDescending { it.conversation.updatedAt })
    }

    /**
     * La phrase qui nomme les serveurs qui n'ont pas répondu, au-dessus de la liste
     * (`serversUnreachable` ): « Cabin and Atelier didn't answer, so their chats aren't
     * here. » Rien quand ils ont tous répondu.
     */
    fun unreachableLine(names: List<String>): String? {
        val sorted = names.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
        if (sorted.isEmpty()) return null
        val who = roomMembersLabel(sorted)
        return if (sorted.size == 1) {
            OnyxLocalization.format("%1\$s didn't answer, so its chats aren't here.", who)
        } else {
            OnyxLocalization.format("%1\$s didn't answer, so their chats aren't here.", who)
        }
    }
}
