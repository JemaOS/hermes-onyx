package com.onyx.app.data.local

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a conversation's tabs keep locally, per conversation:
 *
 * - the **chosen tab** — returning to a conversation reopens the last tab;
 * - the **feedback** on a feed card (thumbs up / thumbs down), per card: no server route
 *   exists to send it, so it does not leave the phone, and the app says so on screen;
 * - what has been **seen** of the feed, for the Feed tab's unread badge.
 *
 * The **local Ideas and Goals lists** have been **removed** (explicit request from the
 * owner): their storage and their operations no longer live here.
 *
 * Everything fits under a single key, a JSON object; an in-memory store ([TextStore]) is therefore enough
 * to exercise the rules without a device.
 */
class ChatPanelStore(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    // — The chosen tab, per conversation —

    /** The key of the tab kept for this conversation, or `null` if none has been chosen. */
    fun selectedTab(conversation: String): String? = group("tabs")[conversation]?.asString()

    /** Keeps the chosen tab of this conversation. */
    fun setSelectedTab(conversation: String, tab: String) {
        putInGroup("tabs", conversation, JsonPrimitive(tab))
    }

    // — The feedback on a card, per conversation and per card —

    /** What was answered to a card: `"up"`, `"down"`, or nothing. */
    fun feedback(conversation: String, cardId: String): String? = feedbackFor(conversation)[cardId]

    /** All the feedback of a conversation, card by card (a single read for the screen). */
    fun feedbackFor(conversation: String): Map<String, String> =
        group("feedback")[conversation]?.asObject()?.mapNotNull { (id, value) ->
            value.asString()?.let { id to it }
        }?.toMap().orEmpty()

    /**
     * Keeps a card's feedback. Pressing the **same** thumb again erases it, like a toggle: it is
     * the only way to undo a choice without leaving a state nothing can change.
     */
    fun setFeedback(conversation: String, cardId: String, value: String): Map<String, String> {
        val here = feedbackFor(conversation).toMutableMap()
        if (here[cardId] == value) here.remove(cardId) else here[cardId] = value
        if (here.isEmpty()) {
            removeFromGroup("feedback", conversation)
        } else {
            putInGroup("feedback", conversation, JsonObject(here.mapValues { (_, v) -> JsonPrimitive(v) }))
        }
        return here
    }

    // — What has been seen of the feed, for the unread badge —

    /** The feed cards already seen in this conversation. */
    fun seenCards(conversation: String): Set<String> =
        group("seen")[conversation]?.asArray()?.mapNotNull { it.asString() }?.toSet().orEmpty()

    /** Marks cards as seen (the open tab: the badge turns off). */
    fun markSeen(conversation: String, ids: Collection<String>): Set<String> {
        val all = seenCards(conversation) + ids
        putInGroup(
            "seen",
            conversation,
            buildJsonArray { all.sorted().forEach { value -> add(JsonPrimitive(value)) } },
        )
        return all
    }

    /** "Erase everything": nothing kept here anymore. */
    fun clearAll() {
        text.put(KEY, EMPTY)
    }

    // — What is written to disk —

    /** A group of the document (`tabs`, `tabs_hidden`, `feedback`, `seen`). */
    private fun group(name: String): Map<String, JsonElement> =
        root()[name]?.asObject().orEmpty()

    private fun putInGroup(name: String, key: String, value: JsonElement) {
        val groups = root().toMutableMap()
        val here = (groups[name]?.asObject() ?: JsonObject(emptyMap())).toMutableMap()
        here[key] = value
        groups[name] = JsonObject(here)
        write(JsonObject(groups))
    }

    private fun removeFromGroup(name: String, key: String) {
        val groups = root().toMutableMap()
        val here = (groups[name]?.asObject() ?: return).toMutableMap()
        here.remove(key)
        if (here.isEmpty()) groups.remove(name) else groups[name] = JsonObject(here)
        write(JsonObject(groups))
    }

    private fun root(): JsonObject =
        text.get(KEY)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: JsonObject(emptyMap())

    private fun write(root: JsonObject) {
        text.put(KEY, if (root.isEmpty()) EMPTY else root.toString())
    }

    private companion object {
        /** The name of the preferences where all this lives. */
        const val PREFERENCES = "onyx_chat_panels"

        /** The single key under which everything is written, and its value when there is nothing. */
        const val KEY = "panels"
        const val EMPTY = "{}"
    }
}

/** A JSON object, or nothing: a damaged document never brings the screen down. */
private fun JsonElement.asObject(): JsonObject? = runCatching { jsonObject }.getOrNull()

/** A JSON array, or nothing. */
private fun JsonElement.asArray(): JsonArray? = runCatching { jsonArray }.getOrNull()

/** The text value of a JSON primitive. */
private fun JsonElement.asString(): String? = runCatching { jsonPrimitive.content }.getOrNull()
