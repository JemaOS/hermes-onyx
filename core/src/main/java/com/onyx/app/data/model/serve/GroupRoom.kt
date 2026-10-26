package com.onyx.app.data.model.serve

import com.onyx.app.data.net.array
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.asString
import com.onyx.app.data.net.firstString
import com.onyx.app.data.net.int
import com.onyx.app.data.net.long
import com.onyx.app.data.net.obj
import com.onyx.app.data.net.str
import com.onyx.app.data.net.strOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les discussions de groupe du serveur — les salles de bots d'Hermes (`groups.*`).
 *
 * Comme `GroupMember` / `GroupRoom` (et:45).
 * Deux sources, comme: les salles qu'Hermes héberge lui-même (`groups.list`) et celles
 * qu'Hermes Desktop tient de son côté (sa copie dans `ui_meta["hermes-bots-groups"]` du profil
 * principal, version 3: « so mobile can show the same messages »).
 */

/** Un bot dans une salle (`GroupMember`). Son id dans la salle est son profil. */
data class GroupMember(
    val id: String,
    val profile: String,
    val handle: String,
    val displayName: String? = null,
) {
    /** Ce qu'on écrit pour lui: son nom de salle, sinon sa poignée. */
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: handle

    /**
     * Le membre tel que `groups.create` le prend (`GroupMember.wire` du modèle): son
     * identifiant dans la salle, son profil, sa poignée, et son nom de salle quand il en a un.
     */
    fun wire(): JsonObject = buildJsonObject {
        put("member_id", id)
        put("profile", profile)
        put("handle", handle)
        displayName?.takeIf { it.isNotBlank() }?.let { put("display_name", it.take(200)) }
    }

    companion object {
        fun from(json: JsonObject): GroupMember? {
            val id = json.firstString("member_id", "profile") ?: return null
            return GroupMember(
                id = id,
                profile = json.firstString("profile") ?: id,
                handle = json.firstString("handle") ?: id,
                displayName = json.strOrNull("display_name"),
            )
        }
    }
}

/** Une salle (`GroupRoom`). */
data class GroupRoom(
    val id: String,
    val name: String,
    val members: List<GroupMember> = emptyList(),
    /** Dernière activité, en millisecondes Unix (`updated_at` du serveur, `at` de la copie Desktop). */
    val updatedAtMillis: Long? = null,
    val latestSeq: Int? = null,
    /**
     * Une salle qu'Hermes Desktop tient lui-même, montrée depuis sa copie: lecture seule.
     * Son id porte le préfixe `desktop:` (`liveRooms` la nomme `desktop:id:<salle>`).
     */
    val inDesktop: Boolean = false,
    /**
     * Les messages de sa copie, les seize derniers au plus, du plus ancien au plus récent
     * (`GroupRoom.desktopMessages` ): une salle Desktop se lit ici et se parle dans Desktop.
     */
    val desktopMessages: List<DesktopMessage> = emptyList(),
    /** Combien de messages antérieurs la copie laisse de côté (`omitted`). */
    val omitted: Int = 0,
    /**
     * Une salle que ce téléphone garde lui-même, faute de Dashboard pour la tenir: elle vit dans
     * le magasin des salles gardées ici, avec ses membres, et se retrouve après une relance.
     */
    val local: Boolean = false,
) {
    /**
     * Où la salle en est, pour dire ce qui est nouveau depuis qu'on l'a vue (`ChatActivity`):
     * le numéro du dernier événement d'une salle hébergée, l'heure de la copie d'une salle Desktop.
     */
    val activityMark: Double?
        get() = if (inDesktop) updatedAtMillis?.let { it / 1000.0 } else latestSeq?.toDouble()

    fun member(id: String?): GroupMember? = members.firstOrNull { it.id == id }

    /**
     * Les bots de la salle appelés comme le roster les appelle, là où la copie ne donne pas de nom
     * (le profil principal, en règle générale: le nom que Onyx lui donne).
     */
    fun naming(roster: List<BotProfile>?, mainName: String): GroupRoom {
        if (roster == null) return this
        return copy(
            members = members.map { member ->
                if (member.displayName != null) member
                else member.copy(
                    displayName = roster.firstOrNull { it.name == member.profile }?.label(mainName)
                        ?: if (member.profile == "default") mainName else null,
                )
            },
        )
    }

    companion object {
        /** Deux bots suffisent, six au plus: au-delà, Hermes refuse la salle. */
        const val MIN_MEMBERS = 2
        const val MAX_MEMBERS = 6

        /**
         * La salle du même nom, s'il y en a déjà une: redemander le même nom n'en ouvre pas une
         * seconde (l'idempotence que le modèle laisse au serveur, ici tenue aussi côté client).
         */
        fun named(rooms: List<GroupRoom>, name: String): GroupRoom? {
            val wanted = name.trim()
            if (wanted.isEmpty()) return null
            return rooms.firstOrNull { it.name.trim().equals(wanted, ignoreCase = true) }
        }

        /** Une ligne de `groups.list` / de `groups.state`. */
        fun from(json: JsonObject): GroupRoom? {
            val id = json.strOrNull("room_id") ?: return null
            return GroupRoom(
                id = id,
                name = json.firstString("name") ?: id,
                members = json.array("members").mapNotNull { it.asObject()?.let(GroupMember::from) },
                updatedAtMillis = json.long("updated_at")?.let(::secondsToMillis),
                latestSeq = json.int("latest_seq"),
            )
        }

        /**
         * Les salles qu'Hermes Desktop tient lui-même, depuis sa copie (version 3 et plus), les
         * salles terminées laissées de côté (`deleted`: un numéro de révision par clé).
         */
        fun desktopRooms(copy: JsonElement?): List<GroupRoom> {
            val root = copy.asObject() ?: return emptyList()
            if ((root.int("version") ?: 0) < 3) return emptyList()
            val rooms = root.obj("rooms") ?: return emptyList()
            val deleted = root.obj("deleted")
            return rooms.mapNotNull { (key, room) ->
                val json = room.asObject() ?: return@mapNotNull null
                val gone = deleted?.get(key)?.asString()?.toIntOrNull()
                if (gone != null && gone >= (json.int("revision") ?: 0)) return@mapNotNull null
                desktopRoom(key, json)
            }.sortedByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }
        }

        private fun desktopRoom(key: String, json: JsonObject): GroupRoom? {
            if (json.isEmpty()) return null
            val messages = desktopMessages(json)
            var members = json.array("members").mapNotNull { entry ->
                val member = entry.asObject() ?: return@mapNotNull null
                val name = member.strOrNull("name") ?: return@mapNotNull null
                GroupMember(
                    id = name,
                    profile = name,
                    handle = member.strOrNull("handle") ?: name,
                    displayName = member.strOrNull("title") ?: member.strOrNull("display_name"),
                )
            }
            // Une copie sans ses membres (celle d'un Desktop plus ancien) montre quand même qui a parlé.
            if (members.isEmpty()) {
                val seen = LinkedHashSet<String>()
                members = messages.mapNotNull { message -> message.profile?.takeIf { seen.add(it) } }
                    .map { GroupMember(id = it, profile = it, handle = it) }
            }
            return GroupRoom(
                id = "desktop:" + key,
                name = json.strOrNull("name") ?: key.substringAfter(':', "").ifEmpty { key },
                members = members,
                updatedAtMillis = messages.lastOrNull()?.atMillis,
                inDesktop = true,
                desktopMessages = messages,
                omitted = json.int("omitted") ?: 0,
            )
        }

        /** Les messages de la copie d'une salle Desktop, les seize derniers au plus, du plus ancien au plus récent. */
        internal fun desktopMessages(json: JsonObject): List<DesktopMessage> =
            json.array("log").mapNotNull { entry ->
                val line = entry.asObject() ?: return@mapNotNull null
                val text = line.str("text")
                if (text.isEmpty()) return@mapNotNull null
                DesktopMessage(
                    key = line.strOrNull("id") ?: "${line.long("at") ?: 0L}|${line.obj("from")?.str("name").orEmpty()}|${text.take(40)}",
                    atMillis = line.long("at") ?: 0L,
                    fromPerson = line.obj("from")?.strOrNull("kind") != "member",
                    profile = line.obj("from")?.strOrNull("name"),
                    text = text,
                )
            }

        /** Une ligne du journal d'une salle Desktop. */
        data class DesktopMessage(
            val key: String,
            val atMillis: Long,
            val fromPerson: Boolean,
            val profile: String?,
            /** Ce qui a été dit (`text`), tel quel: jamais traduit. */
            val text: String = "",
        )

        /** `updated_at` est en secondes Unix (`Date(timeIntervalSince1970:)`). */
        internal fun secondsToMillis(seconds: Long): Long =
            if (seconds < 100_000_000_000L) seconds * 1000 else seconds
    }
}

/**
 * Une salle à ouvrir: son identifiant neuf, son nom et ses membres.
 *
 * C'est ce que `groups.create` demande au Dashboard (`createGroup(named:bots:mainName:)` de la
 * référence), et ce qu'un téléphone **sans Dashboard** garde chez lui — la salle réapparaît alors
 * après une relance, comme une salle que le serveur tiendrait.
 */
data class GroupRoomDraft(
    val id: String,
    val name: String,
    val members: List<GroupMember>,
    /** Quand la salle a été demandée: l'heure que la liste montre en attendant son premier mot. */
    val createdAtMillis: Long,
) {
    companion object {
        /**
         * L'identifiant de la salle sur le serveur, tiré comme celui du modèle
         * (`"onyx-" + UUID`): un identifiant opaque pour Hermes, unique par salle neuve.
         */
        fun newID(): String = "onyx-" + java.util.UUID.randomUUID().toString().lowercase()

        /**
         * La salle demandée pour ces bots: chacun devient un membre de la salle, nommé comme le
         * roster le nomme (le profil principal prend le nom de Onyx), exactement le `wire` que
         * `groups.create` prend.
         */
        fun of(
            name: String,
            bots: List<BotProfile>,
            mainName: String,
            id: String = newID(),
            now: Long = System.currentTimeMillis(),
        ): GroupRoomDraft = GroupRoomDraft(
            id = id,
            name = name,
            members = bots.map { bot ->
                GroupMember(
                    id = bot.name,
                    profile = bot.name,
                    handle = bot.handle,
                    displayName = bot.label(mainName),
                )
            },
            createdAtMillis = now,
        )
    }
}
