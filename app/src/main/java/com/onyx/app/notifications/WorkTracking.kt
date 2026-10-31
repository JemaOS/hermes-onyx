package com.onyx.app.notifications

/**
 * Combien le travail d'un bot se suit dans la barre d'état.
 *
 * C'est le réglage « Suivi du travail » de Réglages → Notifications, en trois choix :
 *
 *  - [NEVER] : rien, jamais ;
 *  - [ON_FINISH] (le défaut) : **une** notification de fin, avec l'extrait de la réponse — rien
 *    ne s'installe dans la barre d'état pendant le tour, donc rien à subir à chaque tour ;
 *  - [EVERY_STEP] : la notification d'avancement reste dans la barre d'état pendant tout le
 *    travail — étape en cours, temps écoulé, bouton Arrêter, et pastille sur Android 16 — puis la
 *    notification de fin.
 *
 * Le libellé est la clé anglaise du dictionnaire : elle est traduite par `OnyxLocalization`, comme
 * partout ailleurs dans l'app.
 */
enum class WorkTracking(val id: String, val label: String) {
    NEVER("never", "Never"),
    ON_FINISH("finish", "When the turn ends"),
    EVERY_STEP("step", "At every step");

    /** Vrai dès que ce choix demande au moins une notification. */
    val notifies: Boolean get() = this != NEVER

    /** Vrai quand le travail se suit **pendant** le tour, et pas seulement à sa fin. */
    val live: Boolean get() = this == EVERY_STEP

    companion object {
        /**
         * Le défaut : la fin du travail, sans tenir la barre d'état pendant le tour — c'est le
         * choix à proposer pour ne pas transformer chaque tour en bandeau permanent.
         */
        val DEFAULT: WorkTracking = ON_FINISH

        fun fromId(id: String?): WorkTracking = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Les choix, dans l'ordre où l'écran les présente. */
        val choices: List<WorkTracking> = listOf(NEVER, ON_FINISH, EVERY_STEP)
    }
}
