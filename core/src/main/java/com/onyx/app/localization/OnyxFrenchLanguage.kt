package com.onyx.app.localization

/**
 * Le réglage « Langue » de l'interface : la ligne de Réglages et la feuille de choix.
 *
 * Un fichier à part, comme `onyxToolsPageFrench`, pour que l'ajout se voie : le test
 * [LanguageSettingLocalizationTest] refuse qu'une de ces clés existe déjà ailleurs dans le
 * dictionnaire — une entrée en double écrase la traduction existante (« Schedules » →
 * « Programmes » avait cassé « Horaires »), et c'est exactement ce qu'il faut éviter ici.
 *
 * Les deux langues elles-mêmes (« Français », « English ») ne sont pas traduites : un nom de langue
 * s'écrit dans sa propre langue, pour qu'on retrouve toujours la sienne quelle que soit celle de
 * l'interface. « System », lui, passe par le dictionnaire (« Système »).
 */
val onyxLanguageFrench: Map<String, String> = linkedMapOf(
    "Language" to "Langue",
    "Onyx follows the phone's language unless you pick one. Screens, sheets, menus and " +
        "notifications follow it at once." to
        "Onyx suit la langue du téléphone, sauf si vous en choisissez une. Les écrans, les " +
        "feuilles, les menus et les notifications la suivent aussitôt.",
)
