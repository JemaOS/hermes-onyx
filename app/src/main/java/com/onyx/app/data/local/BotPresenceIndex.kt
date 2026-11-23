package com.onyx.app.data.local

import android.content.Context
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile

/**
 * What the phone remembers about a bot for what does not read the roster: **the widget**.
 *
 * The home screen widget does not compose the app: it reads its data cold, outside of any
 * screen, and therefore has neither `BotsViewModel` nor a faces table at hand. It was missing the bot's
 * name: a bot reply there read under "Onyx", hardcoded.
 *
 * This table is **published at the same time as the shortcuts** ([com.onyx.app.shortcuts.
 * RosterShortcuts.publish], replayed on every roster read): the name, the label and the
 * `canonical_session` of each profile are written there, and the widget finds a conversation's bot
 * through the **same link** as the app: the bot's server chat
 * ([BotProfile.chatId]), then the title the conversation carries when there is none.
 *
 * It decides **nothing**: the face remains the single resolution (`BotFaceResolver`), the
 * model remains `BotsViewModel.modelOf`. Here there is only a label to return to a screen that has
 * no other.
 */
data class BotPresence(
    /** The bot's profile name. */
    val name: String,
    /** What it is called (the thread's label). */
    val label: String,
    /** Its `canonical_session`, when the roster names it. */
    val chatId: String?,
)

/** The bots table as the widget reads it, kept on the phone. */
class BotPresenceIndex(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Republishes the table from the roster. A bot without a label does not enter it: the widget's
     * fallback is better than the name of an empty bot.
     */
    fun publish(bots: List<BotProfile>, mainName: String) {
        val lines = bots.distinctBy { it.name }.mapNotNull { bot ->
            val label = bot.label(mainName).trim().ifBlank { bot.name }
            if (label.isBlank()) return@mapNotNull null
            listOf(bot.name, label, bot.chatId.orEmpty()).joinToString(SEPARATOR)
        }
        prefs.edit().putString(KEY, lines.joinToString(LINE)).apply()
    }

    /** The published table; empty until the roster has been read. */
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
     * "Erase everything": the phone no longer keeps any bot name for the widget.
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
         * The label of the bot whose conversation this is, or `null` when none claims it —
         * the widget then keeps its fallback.
         *
         * The two links are those of [com.onyx.app.ui.bots.botOfConversation]: the **bot's server
         * chat** first, then the **title set** by opening its chat, which only counts
         * if it designates a single bot. The compared name is [Conversation.botName]: the title
         * **without** the branch suffix — a branch keeps the bot of its original.
         */
        fun labelOf(conversation: Conversation, entries: List<BotPresence>): String? {
            val byChat = entries.filter { presence ->
                presence.chatId != null &&
                    (presence.chatId == conversation.serverSessionId || presence.chatId == conversation.id)
            }
            val name = conversation.botName()
            // **A shared session designates no one.** A cloned bot inherits its original's
            // `canonical_session`: two bots claim it, and `firstOrNull`
            // gave the first one to both conversations (same label, hence same face in the
            // widget). The title decides between them, as in [com.onyx.app.ui.bots.botOfConversation].
            if (byChat.size == 1) return byChat.first().label
            if (byChat.size > 1) {
                return name?.let { wanted ->
                    byChat.filter { it.label == wanted }.map { it.label }.distinct().singleOrNull()
                }
            }
            name ?: return null
            return entries.filter { it.label == name }.map { it.label }.distinct().singleOrNull()
        }
    }
}