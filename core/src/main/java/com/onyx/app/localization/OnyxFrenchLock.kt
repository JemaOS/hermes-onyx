package com.onyx.app.localization

/**
 * Libellés de l'écran de verrouillage et de Réglages → Verrouillage.
 *
 * Le bouton et la ligne « Exiger … » s'écrivent à partir du nom que le téléphone donne à sa
 * biométrie (`AppLock.biometryLabel`) : les phrases composées, et non seulement les mots, sont donc
 * traduites ici — « Unlock with Face or fingerprint » restait en anglais parce que seule la partie
 * fixe était connue.
 */
internal val onyxLockFrench: Map<String, String> = linkedMapOf(
    // Ce que le téléphone a vraiment. Jamais « Touch ID », qui n'existe que sur le téléphone de référence.
    "Fingerprint" to "Empreinte digitale",
    "Face unlock" to "Déverrouillage par le visage",

    // La seconde ligne de la barre du haut quand aucun modèle n'est nommé (`ContentView` :
    // `modelChipLabel` retombe sur « default model »).
    "default model" to "modèle par défaut",

    // Le bouton de l'écran de verrouillage.
    "Unlock with Fingerprint" to "Déverrouiller avec l’empreinte",
    "Unlock with Face unlock" to "Déverrouiller avec le visage",
    "Unlock with Face or fingerprint" to "Déverrouiller avec le visage ou l’empreinte",

    // Ce qui manque, quand le bouton n'est pas proposé.
    "Face or fingerprint isn't set up on this phone. Use the passcode." to
        "Le visage ou l’empreinte n’est pas configuré sur ce téléphone. Utilisez le code.",
    "Face or fingerprint isn't set up on this phone. Add one in Settings › Lock." to
        "Le visage ou l’empreinte n’est pas configuré sur ce téléphone. Ajoutez-le dans Réglages › Verrouillage.",

    // Les erreurs de la feuille biométrique : dites à l'écran, pas seulement dans le journal.
    "That didn't match. Try again, or use the passcode." to
        "Ça ne correspond pas. Réessayez, ou utilisez le code.",
    "Unlock didn't work. Try again, or use the passcode." to
        "Le déverrouillage n’a pas fonctionné. Réessayez, ou utilisez le code.",
    // Le bouton « Utiliser le code » de la feuille : ce n'est pas une panne, mais la suite se passe ici.
    "Enter your passcode." to "Saisissez votre code.",
    "Biometric unlock isn't available on this screen. Use the passcode." to
        "Le déverrouillage biométrique n’est pas disponible sur cet écran. Utilisez le code.",

    // Réglages → Verrouillage : la ligne « Exiger … » nomme la biométrie du téléphone.
    "Require Fingerprint" to "Exiger l’empreinte",
    "Require Face unlock" to "Exiger le visage",
)
