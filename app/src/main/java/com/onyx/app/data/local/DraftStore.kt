package com.onyx.app.data.local

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Le brouillon de chaque conversation.
 *
 * Comme: le texte en cours est gardé par conversation — donc quand
 * on en rouvre une, ce qu'on était en train d'écrire est encore là. Les plus anciens brouillons
 * s'effacent au-delà de [limit], comme.
 */
class DraftStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_drafts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Le brouillon d'une conversation, ou `""`. */
    fun get(conversationId: String): String = load()[conversationId].orEmpty()

    /** Enregistre (ou efface, si vide) le brouillon d'une conversation. */
    fun put(conversationId: String, text: String) {
        val drafts = load().toMutableMap()
        if (text.isEmpty()) drafts.remove(conversationId) else drafts[conversationId] = text
        store(drafts)
    }

    fun clear(conversationId: String) {
        put(conversationId, "")
    }

    /** Efface tous les brouillons de ce téléphone (le « Erase everything » des Réglages). */
    fun clearAll() {
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): Map<String, String> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching { json.decodeFromString(MAP_SERIALIZER, raw) }.getOrDefault(emptyMap())
    }

    private fun store(drafts: Map<String, String>) {
        // Au-delà de la limite, on garde les derniers écrits: la carte n'a pas d'ordre, on
        // élague donc arbitrairement — un brouillon oublié n'est pas une perte.
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
