package com.onyx.app.data.net

/**
 * Comment un tour a été laissé partir. Miroir de `TurnDeparture` de la référence : un tour
 * **arrêté** est arrêté chez l'hôte ; un tour dont l'app s'est seulement **détachée** y continue,
 * et sa réponse est là quand la conversation est rouverte.
 *
 * Un abandon ne se devine pas après coup : l'arrêt de la coroutine est le même dans les deux cas,
 * seul le drapeau dit au transport s'il doit interrompre l'hôte.
 */
class TurnDeparture {
    @Volatile
    private var left = false

    @Volatile
    private var stopped = false

    /** Vrai quand l'app a laissé le tour sans l'arrêter : l'hôte le finit. */
    val hasLeft: Boolean get() = left

    /**
     * Vrai quand **l'utilisateur** a arrêté le tour : lui seul interrompt l'hôte.
     *
     * Une annulation n'est pas un arrêt : l'app qui s'en va, le processus qui meurt, leDrawer qui
     * se ferme — le travail continue chez l'hôte, et sa réponse est là au retour. Avant, toute
     * annulation ordonnait au serveur d'arrêter : le tour mourait au moment précis où la personne
     * l'attendait (elle était sortie de l'app).
     */
    val isStopped: Boolean get() = stopped

    /** Marque l'abandon. Appelé avant l'annulation, jamais après. */
    fun leave() {
        left = true
    }

    /** Marque l'arrêt demandé par l'utilisateur. Appelé avant l'annulation, jamais après. */
    fun stop() {
        stopped = true
    }
}
