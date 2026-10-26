package com.onyx.app.data.local

import android.content.Context
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupRoomDraft
import com.onyx.app.data.net.array
import com.onyx.app.data.net.asArray
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.long
import com.onyx.app.data.net.strOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Un endroit où l'on écrit du texte par clé: les préférences d'Android, ou une carte en mémoire
 * quand on éprouve ce qui est gardé sans appareil.
 */
interface TextStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** [TextStore] sur les préférences d'Android: ce qui est écrit là survit à une relance. */
class PreferencesTextStore(context: Context, name: String) : TextStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/**
 * Le magasin des salles gardées **sur ce téléphone**.
 *
 * Un « New Group Chat » fait sans Dashboard n'a pas de serveur pour tenir sa salle: celle-ci est
 * donc écrite ici, avec son nom et ses membres, sous la connexion qui l'a demandée. Elle réapparaît
 * dans la liste au prochain lancement — la même promesse qu'une salle que le serveur tient, sans le
 * serveur.
 *
 * Même nom, même salle: redemander deux fois le même nom n'en garde qu'une (idempotence). Tout
 * tient sous une seule clé, un objet JSON `{"<connexion>": [ salle, … ]}`.
 */
class GroupRoomStore(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    /** Les salles gardées pour cette connexion, la plus récente d'abord. */
    fun rooms(server: String): List<GroupRoom> =
        all()[server].orEmpty().sortedByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }

    /**
     * Garde une salle neuve et la rend telle que la liste la montrera. Une salle du même nom déjà
     * gardée revient telle quelle: rien n'est écrit deux fois.
     */
    fun save(server: String, draft: GroupRoomDraft): GroupRoom {
        val kept = all()
        val here = kept[server].orEmpty()
        here.firstOrNull { it.name.trim().equals(draft.name.trim(), ignoreCase = true) }?.let { return it }
        val room = GroupRoom(
            id = draft.id,
            name = draft.name,
            members = draft.members,
            updatedAtMillis = draft.createdAtMillis,
            local = true,
        )
        kept[server] = here + room
        write(kept)
        return room
    }

    /** Une salle de cette connexion s'en va (terminée, ou retirée). */
    fun remove(server: String, id: String) {
        val kept = all()
        val here = kept[server].orEmpty().filterNot { it.id == id }
        if (here.isEmpty()) kept.remove(server) else kept[server] = here
        write(kept)
    }

    /** « Erase everything »: plus aucune salle gardée ici. */
    fun clearAll() {
        text.put(KEY, EMPTY)
    }

    // — Ce qui est écrit sur le disque —

    private fun all(): MutableMap<String, List<GroupRoom>> =
        decode(text.get(KEY)).toMutableMap()

    private fun write(rooms: Map<String, List<GroupRoom>>) {
        val json = buildJsonObject {
            for ((server, list) in rooms) {
                put(server, buildJsonArray { list.forEach { add(encodeRoom(it)) } })
            }
        }
        text.put(KEY, json.toString())
    }

    private fun encodeRoom(room: GroupRoom): JsonObject = buildJsonObject {
        put("room_id", room.id)
        put("name", room.name)
        put("created_at", room.updatedAtMillis ?: 0L)
        put(
            "members",
            buildJsonArray {
                room.members.forEach { member ->
                    add(
                        buildJsonObject {
                            put("member_id", member.id)
                            put("profile", member.profile)
                            put("handle", member.handle)
                            member.displayName?.let { name -> put("display_name", name) }
                        },
                    )
                }
            },
        )
    }

    private fun decode(text: String?): Map<String, List<GroupRoom>> {
        if (text.isNullOrBlank()) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(text).asObject() }.getOrNull() ?: return emptyMap()
        val rooms = LinkedHashMap<String, List<GroupRoom>>()
        for ((server, value) in root) {
            val decoded = value.asArray().orEmpty().mapNotNull { entry -> entry.asObject()?.let(::decodeRoom) }
            if (decoded.isNotEmpty()) rooms[server] = decoded
        }
        return rooms
    }

    private fun decodeRoom(json: JsonObject): GroupRoom? {
        val id = json.strOrNull("room_id") ?: return null
        val members = json.array("members").mapNotNull { entry ->
            val member = entry.asObject() ?: return@mapNotNull null
            val profile = member.strOrNull("profile") ?: member.strOrNull("member_id") ?: return@mapNotNull null
            GroupMember(
                id = member.strOrNull("member_id") ?: profile,
                profile = profile,
                handle = member.strOrNull("handle") ?: profile,
                displayName = member.strOrNull("display_name"),
            )
        }
        return GroupRoom(
            id = id,
            name = json.strOrNull("name") ?: id,
            members = members,
            updatedAtMillis = json.long("created_at"),
            local = true,
        )
    }

    private companion object {
        /** Le nom des préférences où les salles gardées vivent. */
        const val PREFERENCES = "onyx_group_rooms"

        /** La clé unique sous laquelle tout est écrit, et sa valeur quand il n'y a rien. */
        const val KEY = "rooms"
        const val EMPTY = "{}"
    }
}
