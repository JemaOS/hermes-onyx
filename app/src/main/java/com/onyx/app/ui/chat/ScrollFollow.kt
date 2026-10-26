package com.onyx.app.ui.chat

/**
 * Le suivi du bas du fil: « on suit » ou « détaché ».
 *
 * Toute la décision vit ici, sans dépendre de Compose, pour rester testable sur la JVM — même
 * forme que [ChatShortcuts]. Le suivi se détache **au premier doigt posé sur la liste**, et pas
 * quand la position quitte le bas: c'est la règle qui manquait, et sans elle chaque jeton de
 * raisonnement — chaque ligne d'outil, chaque battement du compteur de durée — ramenait la vue en
 * bas avant qu'on ait pu s'en éloigner, donc on ne pouvait pas remonter lire pendant que l'agent
 * travaillait.
 *
 * Comme le modèle (`TranscriptView.following`, `TranscriptEnd`):
 * 1. un geste (`phase == .interacting`) détache le suivi, quel que soit l'endroit;
 * 2. au repos, il se ré-attache si le bas est à l'écran (à [Reach] points près);
 * 3. le contenu qui grandit n'entraîne la vue que si le suivi est attaché.
 */
object ScrollFollow {
    /**
     * Combien de points avant la fin comptent comme « à la fin ». Le modèle prend 120
     * (`TranscriptEnd.reach`): arriver dans les derniers 120 points du fil vaut y être.
     */
    const val Reach = 120f

    /** Un doigt sur la liste: la personne décide, le suivi se détache tout de suite. */
    fun afterTouch(): Boolean = false

    /**
     * L'état du suivi au repos. Un geste en cours ne ré-attache **jamais** le suivi; au repos,
     * être à l'écran près du bas ré-attache.
     */
    fun atRest(scrolling: Boolean, atBottom: Boolean, following: Boolean): Boolean = when {
        // Un geste en cours ne ré-attache jamais: pendant le geste, le contenu qui grandit peut
        // faire lire « au bas » à tort, et le suivi viendrait justement d'y ramener la vue.
        scrolling -> following
        atBottom -> true
        else -> following
    }

    /** Un nouveau message de la personne, ou « aller au dernier message »: on repart en suivant. */
    fun jumpToLatest(): Boolean = true
}
