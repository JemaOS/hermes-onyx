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
 * Ce que les onglets d'une conversation gardent localement, par conversation :
 *
 * - l'**onglet choisi** — revenir dans une conversation rouvre le dernier onglet ;
 * - le **retour** sur une carte du fil (pouce haut / pouce bas), par carte : aucune route serveur
 *   n'existe pour l'envoyer, donc il ne quitte pas le téléphone, et l'app le dit à l'écran ;
 * - ce qui a été **vu** du fil, pour la pastille de non-lu de l'onglet Feed.
 *
 * Les **listes locales Idées et Objectifs** ont été **supprimées** (demande explicite du
 * propriétaire) : leur stockage et leurs opérations ne vivent plus ici.
 *
 * Tout tient sous une seule clé, un objet JSON ; un magasin en mémoire ([TextStore]) suffit donc à
 * éprouver les règles sans appareil.
 */
class ChatPanelStore(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    // — L'onglet choisi, par conversation —

    /** La clé de l'onglet gardé pour cette conversation, ou `null` si aucune n'a été choisie. */
    fun selectedTab(conversation: String): String? = group("tabs")[conversation]?.asString()

    /** Garde l'onglet choisi de cette conversation. */
    fun setSelectedTab(conversation: String, tab: String) {
        putInGroup("tabs", conversation, JsonPrimitive(tab))
    }

    // — Le retour sur une carte, par conversation et par carte —

    /** Ce qui a été répondu à une carte : `"up"`, `"down"`, ou rien. */
    fun feedback(conversation: String, cardId: String): String? = feedbackFor(conversation)[cardId]

    /** Tout le retour d'une conversation, carte par carte (une seule lecture pour l'écran). */
    fun feedbackFor(conversation: String): Map<String, String> =
        group("feedback")[conversation]?.asObject()?.mapNotNull { (id, value) ->
            value.asString()?.let { id to it }
        }?.toMap().orEmpty()

    /**
     * Garde le retour d'une carte. Reposer le **même** pouce l'efface, comme un interrupteur : c'est
     * le seul moyen de défaire un choix sans laisser un état que rien ne peut changer.
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

    // — Ce qui a été vu du fil, pour la pastille de non-lu —

    /** Les cartes du fil déjà vues dans cette conversation. */
    fun seenCards(conversation: String): Set<String> =
        group("seen")[conversation]?.asArray()?.mapNotNull { it.asString() }?.toSet().orEmpty()

    /** Marque des cartes comme vues (l'onglet ouvert : la pastille s'éteint). */
    fun markSeen(conversation: String, ids: Collection<String>): Set<String> {
        val all = seenCards(conversation) + ids
        putInGroup(
            "seen",
            conversation,
            buildJsonArray { all.sorted().forEach { value -> add(JsonPrimitive(value)) } },
        )
        return all
    }

    /** « Erase everything » : plus rien de gardé ici. */
    fun clearAll() {
        text.put(KEY, EMPTY)
    }

    // — Ce qui est écrit sur le disque —

    /** Un groupe du document (`tabs`, `tabs_hidden`, `feedback`, `seen`). */
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
        /** Le nom des préférences où tout cela vit. */
        const val PREFERENCES = "onyx_chat_panels"

        /** La clé unique sous laquelle tout est écrit, et sa valeur quand il n'y a rien. */
        const val KEY = "panels"
        const val EMPTY = "{}"
    }
}

/** Un objet JSON, ou rien : un document abîmé ne fait jamais tomber l'écran. */
private fun JsonElement.asObject(): JsonObject? = runCatching { jsonObject }.getOrNull()

/** Un tableau JSON, ou rien. */
private fun JsonElement.asArray(): JsonArray? = runCatching { jsonArray }.getOrNull()

/** La valeur texte d'une primitive JSON. */
private fun JsonElement.asString(): String? = runCatching { jsonPrimitive.content }.getOrNull()
