package com.onyx.app.localization

/**
 * Les mots du **Coffre** — la liste des secrets d'un profil, l'écriture d'un secret et sa
 * révocation.
 *
 * Les clés sont les chaînes anglaises écrites dans `VaultScreen.kt` et dans la ligne « Secrets › »
 * de « Capacités ». Les **données** du serveur — le nom d'un secret, son empreinte, les noms des
 * tâches qui l'utilisent — ne passent jamais par ici : l'écran les rend verbatim.
 *
 * Deux choix de mot qui valent d'être dits :
 *
 *  - **« Coffre de secrets »**, comme la section « Capacités du serveur » ([onyxProbeFrench]) : une
 *    capacité et l'écran qu'elle ouvre ne portent pas deux noms différents ;
 *  - **la valeur n'est jamais nommée ailleurs que pour dire qu'elle ne se montre pas** : les
 *    phrases « La valeur n'est plus jamais affichée » sont là pour que l'écran tienne sa règle à
 *    voix haute, pas pour décrire un aperçu qui existerait.
 */
internal val onyxVaultFrench: Map<String, String> = linkedMapOf(
    // La ligne d'entrée, dans « Capacités ».
    "Secrets ›" to "Coffre de secrets ›",

    // Le titre de l'écran et la section de liste.
    "Secrets" to "Secrets du profil",
    "Secret names and fingerprints only. A value is never shown, here or anywhere else in Onyx." to
        "Des noms et des empreintes, rien de plus. Une valeur n'est jamais affichée, ici ni " +
        "nulle part ailleurs dans Onyx.",
    "No secrets for this profile yet. Add one below." to
        "Aucun secret pour ce profil pour l'instant. Ajoutez-en un ci-dessous.",

    // Une ligne de secret : l'empreinte, l'usage, et les deux formes de la date.
    "Fingerprint %1\$s" to "Empreinte %1\$s",
    "Used by %1\$s" to "Utilisé par %1\$s",
    "Not used by any task" to "Utilisé par aucune tâche",

    // L'écriture.
    "Add a secret" to "Ajouter un secret",
    "The value is written to the vault and never shown again." to
        "La valeur est écrite dans le coffre et n'est plus jamais affichée.",
    "A name and a value are both required." to "Un nom et une valeur sont nécessaires.",
    "Saved %1\$s. The value is never shown again." to
        "« %1\$s » enregistré. La valeur n'est plus jamais affichée.",

    // La révocation, et sa confirmation qui nomme le secret.
    "Revoke" to "Révoquer",
    "Revoke this secret?" to "Révoquer ce secret ?",
    "Revoke %1\$s for this profile. The agent stops seeing it." to
        "Révoquer « %1\$s » pour ce profil. L’agent ne le voit plus.",
    "The secret %1\$s was revoked." to "« %1\$s » a été révoqué.",
    "There was no secret named %1\$s." to "Aucun secret ne s'appelait « %1\$s ».",

    // Les deux échecs que l'écran nomme lui-même.
    "This Hermes does not serve the secrets vault route." to
        "Cet Hermes ne sert pas la route du coffre de secrets.",
)
