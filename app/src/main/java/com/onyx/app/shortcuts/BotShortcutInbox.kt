package com.onyx.app.shortcuts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Un bot demandé depuis l'extérieur : le raccourci de son icône, pris dans le menu d'appui long.
 *
 * Les trois valeurs voyagent avec l'intention du raccourci ([BotShortcut]) : l'app n'a donc rien à
 * redemander au serveur pour ouvrir la bonne conversation.
 */
data class BotShortcutRequest(
    val bot: String,
    val label: String,
    val chatId: String?,
    val isDefault: Boolean,
)

/**
 * What enters the app from the outside **for a specific bot**: its icon's shortcut.
 *
 * Same holder as [com.onyx.app.intake.ShareInbox] and same contract — the activity posts the request
 * on creation or on a new intent, and the UI consumes it **once**. The holder is separate because
 * the request does not follow the same path: a share goes to the composer, a bot shortcut **opens
 * that bot's conversation**, and a request already handled must not be replayed on return to the
 * app.
 */
object BotShortcutInbox {

    private val _requested = MutableStateFlow<BotShortcutRequest?>(null)

    /** The pending request, or `null`. */
    val requested: StateFlow<BotShortcutRequest?> = _requested.asStateFlow()

    fun request(request: BotShortcutRequest) {
        _requested.value = request
    }

    fun consume() {
        _requested.value = null
    }
}
