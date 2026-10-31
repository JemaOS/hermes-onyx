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
 * Ce qui entre dans l'app de l'extérieur **pour un bot précis** : le raccourci de son icône.
 *
 * Même dépôt que [com.onyx.app.intake.ShareInbox] et même contrat — l'activité pose la demande à
 * la création ou sur un nouvel intent, et l'UI la consomme **une fois**. Le dépôt est distinct
 * parce que la demande ne suit pas le même chemin : un partage va au composeur, un raccourci de
 * bot **ouvre la conversation de ce bot**, et une demande déjà traitée ne doit pas se rejouer au
 * retour dans l'app.
 */
object BotShortcutInbox {

    private val _requested = MutableStateFlow<BotShortcutRequest?>(null)

    /** La demande en attente, ou `null`. */
    val requested: StateFlow<BotShortcutRequest?> = _requested.asStateFlow()

    fun request(request: BotShortcutRequest) {
        _requested.value = request
    }

    fun consume() {
        _requested.value = null
    }
}
