package com.onyx.app.data.local

import android.content.Context
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile

/**
 * Ce que le téléphone retient d'un bot pour ce qui ne lit pas le roster : **le widget**.
 *
 * Le widget d'écran d'accueil ne compose pas l'app : il lit ses données à froid, hors de tout
 * écran, et n'a donc ni `BotsViewModel` ni table de visages sous la main. Il lui manquait le nom
 * du bot : une réponse de bot s'y lisait sous « Onyx », en dur.
 *
 * Cette table est **publiée au même moment que les raccourcis** ([com.onyx.app.shortcuts.
 * RosterShortcuts.publish], rappelée à chaque lecture du roster) : le nom, le libellé et le
 * `canonical_session` de chaque profil y sont écrits, et le widget y retrouve le bot d'une
 * conversation par le **même lien** que l'app : le chat serveur du bot
 * ([BotProfile.chatId]), puis le titre que la conversation porte quand il n'y en a pas.
 *
 * Elle ne décide de **rien** : le visage reste la résolution unique (`BotFaceResolver`), le
 * modèle reste `BotsViewModel.modelOf`. Ici, il n'y a qu'un libellé à rendre à un écran qui n'en
 * a pas d'autre.
 */
data class BotPresence(
    /** Le nom de profil du bot. */
    val name: String,
    /** Ce qu'il est appelé (le libellé du fil). */
    val label: String,
    /** Son `canonical_session`, quand le roster le nomme. */
    val chatId: String?,
)

/** La table des bots telle que le widget la lit, gardée sur le téléphone. */
class BotPresenceIndex(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Republie la table depuis le roster. Un bot sans libellé n'y entre pas : mieux vaut le repli
     * du widget que le nom d'un bot vide.
     */
    fun publish(bots: List<BotProfile>, mainName: String) {
        val lines = bots.distinctBy { it.name }.mapNotNull { bot ->
            val label = bot.label(mainName).trim().ifBlank { bot.name }
            if (label.isBlank()) return@mapNotNull null
            listOf(bot.name, label, bot.chatId.orEmpty()).joinToString(SEPARATOR)
        }
        prefs.edit().putString(KEY, lines.joinToString(LINE)).apply()
    }

    /** La table publiée ; vide tant que le roster n'a pas été lu. */
    fun entries(): List<BotPresence> = prefs.getString(KEY, null).orEmpty()
        .split(LINE)
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            val name = parts.getOrNull(0).orEmpty()
            val label = parts.getOrNull(1).orEmpty()
            if (name.isBlank() || label.isBlank()) null
            else BotPresence(name = name, label = label, chatId = parts.getOrNull(2)?.takeIf { it.isNotBlank() })
        }

    /**
     * « Erase everything » : le téléphone ne garde plus de nom de bot pour le widget.
     */
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {

        const val FILE = "onyx_bot_presence"

        private const val KEY = "roster"
        private const val SEPARATOR = "\u001F"
        private const val LINE = "\n"

        /**
         * Le libellé du bot dont c'est la conversation, ou `null` quand aucun ne la revendique —
         * le widget garde alors son repli.
         *
         * Les deux liens sont ceux de [com.onyx.app.ui.bots.botOfConversation] : le **chat serveur
         * du bot** d'abord, puis le **titre posé** par l'ouverture de son chat, qui ne compte que
         * s'il ne désigne qu'un seul bot.
         */
        fun labelOf(conversation: Conversation, entries: List<BotPresence>): String? {
            entries.firstOrNull { presence ->
                presence.chatId != null &&
                    (presence.chatId == conversation.serverSessionId || presence.chatId == conversation.id)
            }?.let { return it.label }
            val name = conversation.customName() ?: return null
            return entries.filter { it.label == name }.map { it.label }.distinct().singleOrNull()
        }
    }
}