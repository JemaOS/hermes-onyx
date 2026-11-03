package com.onyx.app.localization

/**
 * Les mots de la section « Server capabilities » (Connection details).
 *
 * Une capacité est nommée par le **rôle** qu'elle joue — « Secrets vault », « Teach a task »,
 * « Room handoff » — jamais par le nom de la route qui la sert. L'état, lui, ne ment pas :
 * « Not confirmed » pour une réponse [com.onyx.app.data.net.Support.UNKNOWN], parce que la sonde
 * n'a pas pu prouver la présence de la route.
 */
val onyxProbeFrench: Map<String, String> = mapOf(
    "Server capabilities" to "Capacités du serveur",
    "What this server can do. A capability it does not have is not drawn anywhere in the app, " +
        "rather than shown empty." to
        "Ce que ce serveur sait faire. Une capacité qu'il n'a pas n'est dessinée nulle part dans " +
        "l'application, plutôt que montrée vide.",
    "Secrets vault" to "Coffre de secrets",
    "Teach a task" to "Montrer une tâche",
    "Room handoff" to "Passage de main",
    "Checking…" to "Vérification…",
    "Available" to "Disponible",
    "Not on this server" to "Absent de ce serveur",
    "Not confirmed" to "Non confirmé",
)
