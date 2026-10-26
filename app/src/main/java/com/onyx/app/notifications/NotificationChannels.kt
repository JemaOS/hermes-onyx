package com.onyx.app.notifications

/**
 * Les canaux de notification qu'Android garde pour Onyx, et l'état que le système en rapporte.
 *
 * C'est le Comme la liste des appairages de: là où 
 * énumère les Hermes appairés et laisse en retirer un, Android énumère ses canaux et laisse
 * Android décider de chacun (son, importance) dans ses propres réglages. Chaque canal joue donc
 * le rôle qu'y joue un appairage: une destination à l'état suivi, que l'on vient régler.
 *
 * Les identifiants viennent des deux objets qui publient réellement les alertes
 * ([ApprovalNotifier] et `TurnService`): la liste ne peut donc pas dériver d'eux.
 */
object NotificationChannels {

    /** Un canal Onyx: son identifiant Android, son libellé localisé et sa description. */
    data class Channel(val id: String, val label: String, val description: String)

    /** Les trois canaux que Onyx enregistre, dans l'ordre où l'écran les présente. */
    val all: List<Channel> = listOf(
        Channel(
            id = ApprovalNotifier.CHANNEL_ID,
            label = "Approvals",
            description = "Asks when Onyx wants to run something on your machine.",
        ),
        Channel(
            id = ApprovalNotifier.SILENT_CHANNEL_ID,
            label = "Silent approvals",
            description = "Asks when Onyx wants to run something on your machine.",
        ),
        Channel(
            id = TurnService.CHANNEL_ID,
            label = "Answers",
            description = "Shows while Onyx is answering.",
        ),
    )

    /**
     * Comment Android traite un canal, d'après son importance.
     *
     * `ABSENT` est le Comme « Waiting for its first notification »: Android
     * ne crée un canal qu'à la première publication — un canal jamais créé n'a pas encore d'état.
     */
    enum class State { ABSENT, OFF, ON }

    /** `NotificationManager.IMPORTANCE_NONE`: le canal est réglé sur « aucun ». */
    const val IMPORTANCE_NONE = 0

    /** `importance` vient de `NotificationChannel.getImportance`; `null`: le canal n'existe pas. */
    fun state(importance: Int?): State = when {
        importance == null -> State.ABSENT
        importance == IMPORTANCE_NONE -> State.OFF
        else -> State.ON
    }
}
