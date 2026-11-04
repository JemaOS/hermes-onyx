package com.onyx.app.data.local

import android.content.Context
import com.onyx.app.data.model.serve.BotProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * **Le dernier roster de bots connu, gardé sur ce téléphone.**
 *
 * Le roster est ce qui dit à quelle conversation appartient quel bot (`botOfConversation`). Il vient
 * du serveur — donc **après** la première frame : au redémarrage, l'app dessinait Onyx (visage bleu,
 * « @default ») puis sautait sur le vrai bot, parce que la liste était vide le temps du réseau.
 *
 * Ce magasin est la copie du dernier roster **réussi**. Il ne remplace jamais le serveur : il le
 * précède d'une frame. Un bot supprimé côté serveur disparaît au premier rafraîchissement réussi —
 * le seul risque est de le voir une seconde de trop, jamais de le perdre.
 *
 * On ne garde que ce que l'en-tête et la liste lisent : nom, libellé, profil principal, identité de
 * chat, visage, et l'origine (`viaAPI`). Rien d'autre ne s'affiche sans le serveur.
 */
class BotRosterCache(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    /** Le dernier roster connu, ou une liste vide quand ce téléphone n'en a jamais enregistré. */
    fun load(): List<BotProfile> {
        val raw = text.get(KEY)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val array = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element -> (element as? JsonObject)?.let(::read) }
    }

    /** Enregistre le roster qui vient du serveur. Une liste vide n'efface rien : sans serveur, on garde. */
    fun save(bots: List<BotProfile>) {
        if (bots.isEmpty()) return
        val array = JsonArray(
            bots.map { bot ->
                buildJsonObject {
                    put("name", JsonPrimitive(bot.name))
                    bot.title?.let { put("title", JsonPrimitive(it)) }
                    bot.chatId?.let { put("chatId", JsonPrimitive(it)) }
                    bot.faceCode?.let { put("faceCode", JsonPrimitive(it)) }
                    if (bot.isDefault) put("isDefault", JsonPrimitive(true))
                    if (bot.viaAPI) put("viaAPI", JsonPrimitive(true))
                }
            },
        )
        text.put(KEY, array.toString())
    }

    private fun read(obj: JsonObject): BotProfile? {
        val name = obj["name"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() } ?: return null
        return BotProfile(
            name = name,
            title = obj["title"]?.jsonPrimitive?.contentOrNullSafe(),
            chatId = obj["chatId"]?.jsonPrimitive?.contentOrNullSafe(),
            faceCode = obj["faceCode"]?.jsonPrimitive?.contentOrNullSafe(),
            isDefault = obj["isDefault"]?.jsonPrimitive?.booleanOrNull == true,
            viaAPI = obj["viaAPI"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content.takeIf { it.isNotEmpty() } else null

    private companion object {
        const val PREFERENCES = "onyx_bot_roster"
        const val KEY = "roster"
    }
}
