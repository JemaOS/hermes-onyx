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

    /** Vrai quand l'app a laissé le tour sans l'arrêter : l'hôte le finit. */
    val hasLeft: Boolean get() = left

    /** Marque l'abandon. Appelé avant l'annulation, jamais après. */
    fun leave() {
        left = true
    }
}
