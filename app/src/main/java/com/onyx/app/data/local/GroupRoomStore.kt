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
 * A place where one writes text by key: Android's preferences, or an in-memory map
 * when exercising what is kept without a device.
 */
interface TextStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** [TextStore] on Android's preferences: what is written there survives a restart. */
class PreferencesTextStore(context: Context, name: String) : TextStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/**
 * The store of rooms kept **on this phone**.
 *
 * A "New Group Chat" made without Dashboard has no server to hold its room: it is
 * therefore written here, with its name and its members, under the connection that requested it. It reappears
 * in the list on the next launch — the same promise as a room the server holds, without the
 * server.
 *
 * Same name, same room: requesting the same name twice keeps only one (idempotence). Everything
 * fits under a single key, a JSON object `{"<connection>": [ room, … ]}`.
 */
class GroupRoomStore(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    /** The rooms kept for this connection, most recent first. */
    fun rooms(server: String): List<GroupRoom> =
        all()[server].orEmpty().sortedByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }

    /**
     * Saves a new room and returns it as the list will show it. A room of the same name already
     * kept comes back as is: nothing is written twice.
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

    /** A room of this connection goes away (ended, or removed). */
    fun remove(server: String, id: String) {
        val kept = all()
        val here = kept[server].orEmpty().filterNot { it.id == id }
        if (here.isEmpty()) kept.remove(server) else kept[server] = here
        write(kept)
    }

    /** "Erase everything": no room kept here anymore. */
    fun clearAll() {
        text.put(KEY, EMPTY)
    }

    // — What is written to disk —

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
        /** The name of the preferences where the kept rooms live. */
        const val PREFERENCES = "onyx_group_rooms"

        /** The single key under which everything is written, and its value when there is nothing. */
        const val KEY = "rooms"
        const val EMPTY = "{}"
    }
}
