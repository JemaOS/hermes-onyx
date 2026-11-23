package com.onyx.app.data.local

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Each conversation's draft.
 *
 * Mirror of `Drafts` on the reference: the text in progress is kept per conversation — so when
 * one reopens one, what one was writing is still there. The oldest drafts
 * are erased beyond [limit], as on the reference.
 */
class DraftStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_drafts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The drafts map, decoded **once**.
     *
     * `put` is called on every typed character: redoing a `getString` + a JSON decoding of all
     * the drafts per keystroke made the keyboard pay several tens of KB of reads and
     * allocations per key. The map stays here; the disk only sees the encoding.
     */
    private var cache: MutableMap<String, String>? = null

    private fun all(): MutableMap<String, String> = cache ?: load().toMutableMap().also { cache = it }

    /** A conversation's draft, or `""`. */
    fun get(conversationId: String): String = all()[conversationId].orEmpty()

    /** Saves (or erases, if empty) a conversation's draft. */
    fun put(conversationId: String, text: String) {
        val drafts = all()
        if (text.isEmpty()) drafts.remove(conversationId) else drafts[conversationId] = text
        store(drafts)
    }

    fun clear(conversationId: String) {
        put(conversationId, "")
    }

    /** Erases all drafts on this phone (the Settings' "Erase everything"). */
    fun clearAll() {
        cache = null
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): Map<String, String> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching { json.decodeFromString(MAP_SERIALIZER, raw) }.getOrDefault(emptyMap())
    }

    private fun store(drafts: Map<String, String>) {
        // Beyond the limit, the latest writes are kept: the map has no order, so it is
        // pruned arbitrarily — a forgotten draft is not a loss.
        val capped = if (drafts.size <= limit) {
            drafts
        } else {
            drafts.entries.toList().takeLast(limit).associate { it.key to it.value }
        }
        prefs.edit().putString(KEY, json.encodeToString(MAP_SERIALIZER, capped)).apply()
    }

    private companion object {
        const val KEY = "drafts"
        const val limit = 40
        val MAP_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}
