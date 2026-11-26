package com.onyx.app.data.local

import android.content.Context
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.asString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * **A room's lead, kept on this phone.**
 *
 * It is the choice the person makes, and it **survives closing** — reopening the app means
 * finding the room and its default recipient again. It depends on no server route: a
 * basic Hermes has no room lead, and the app must not lose the choice because of that.
 *
 * When the room can receive it (`groups.lead`, cf. `GroupRoomViewModel.setLead`), the same choice
 * also goes **into** the room's log: the bots read it, and the room queries the lead
 * first. This store remains this phone's copy — the local source of truth, never the
 * only one.
 *
 * The lead is not a boss: it is the member the room writes to **by default** when the
 * person names no one. `@name` always addresses directly, whatever this setting.
 *
 * Everything fits under a single key, a JSON object `{"<salle>": "<profil>"}`: a room without an entry
 * has no lead, and “No lead” removes the entry instead of writing an empty word into it.
 */
class RoomLeadStore(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    /** The profile of this room's lead, or `null` when it has none. */
    fun lead(room: String): String? = all()[room]?.takeIf { it.isNotBlank() }

    /** Sets the lead; `null` (“No lead”) removes it. Returning the same choice rewrites nothing. */
    fun set(room: String, profile: String?) {
        val clean = profile?.trim()?.takeIf { it.isNotEmpty() }
        val kept = all().toMutableMap()
        if (clean == null) kept.remove(room) else kept[room] = clean
        write(kept)
    }

    /** “Erase everything”: no lead kept here anymore. */
    fun clearAll() {
        text.put(KEY, EMPTY)
    }

    // — What is written on disk —

    private fun all(): Map<String, String> {
        val raw = text.get(KEY)?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val root = runCatching { Json.parseToJsonElement(raw).asObject() }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (room, value) -> value.asString()?.takeIf { it.isNotBlank() }?.let { room to it } }.toMap()
    }

    private fun write(leads: Map<String, String>) {
        if (leads.isEmpty()) {
            text.put(KEY, EMPTY)
            return
        }
        text.put(KEY, JsonObject(leads.mapValues { (_, profile) -> JsonPrimitive(profile) }).toString())
    }

    private companion object {
        /** The name of the preferences where the leads live. */
        const val PREFERENCES = "onyx_room_leads"

        /** The single key under which everything is written, and its value when there is nothing. */
        const val KEY = "leads"
        const val EMPTY = "{}"
    }
}
