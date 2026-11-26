package com.onyx.app.localization

/**
 * Les mots de **« Montrer une fois »** — la démonstration qui devient un skill.
 *
 * Les clés sont les chaînes anglaises écrites dans `RecordScreen.kt`, dans le menu de l'écran
 * Ordinateur et dans `SkillRecordApi.kt`. Les données du serveur — le nom d'un skill, le code d'un
 * refus, le Markdown d'un brouillon — ne passent jamais par ici : l'écran les rend verbatim.
 *
 * Trois choix de mot valent d'être dits :
 *
 *  - **« Montrer une fois »**, jamais « enregistrer l'écran » : rien n'est filmé, rien n'est dit.
 *    L'écran promet des gestes, et le texte le dit à voix haute pour que personne n'ait à le
 *    supposer ;
 *  - **« à relire »** garde le mot du convertisseur (le brouillon écrit « — à relire : libellé
 *    absent ») : les raisons du serveur (`no_label`, `secret_field`…) sont traduites avec le même
 *    vocabulaire que le Markdown qu'elles annotent ;
 *  - **le refus d'un code inconnu cite le code** : « Le serveur a refusé : X. » Un refus qu'on ne
 *    sait pas nommer reste un refus, pas un « réessayez » qui ne veut rien dire.
 */
internal val onyxRecordFrench: Map<String, String> = linkedMapOf(
    // ---- L'entrée, dans le menu de l'écran Ordinateur ---------------------------------------
    "Show me once" to "Montrer une fois",

    // ---- La feuille de lancement ------------------------------------------------------------
    "Demonstration" to "Démonstration",
    "Onyx keeps your gestures, not your screen: no image, no sound, no password." to
        "Onyx garde vos gestes, pas votre écran : aucune image, aucun son, aucun mot de passe.",
    "The recording stops on its own after ten minutes." to
        "L’enregistrement s’arrête tout seul au bout de dix minutes.",
    "The bridge is not configured: there is nothing to demonstrate." to
        "Le pont n’est pas configuré : il n’y a rien à montrer.",
    "The computer screen is not open yet." to "L’écran du PC n’est pas encore ouvert.",

    // ---- Pendant la capture ------------------------------------------------------------------
    "Recording" to "Enregistrement",
    // Pas « Stop » nu : cette clé-là est celle de la maison (« Arrêter »), et la réécrire ici
    // changerait le bouton d'arrêt d'un tour de parole. Le geste de ce lot est nommé en entier.
    "Stop recording" to "Terminer l’enregistrement",
    "Recording — stops on its own in %1\$s." to "Enregistrement — s’arrête tout seul dans %1\$s.",
    "Secret typing" to "Saisie secrète",
    "What you type next is recorded as a secret: no text, no length." to
        "Ce que vous tapez ensuite est retenu comme un secret : aucun texte, aucune longueur.",
    "The bridge is no longer connected. Your gestures stay on this device." to
        "Le pont n’est plus connecté. Vos gestes restent sur cet appareil.",

    // ---- L'aperçu du brouillon ---------------------------------------------------------------
    "Demonstration finished: %1\$d actions, %2\$s." to "Démonstration terminée : %1\$d actions, %2\$s.",
    "Nothing was recorded: there is no gesture to turn into a skill." to
        "Rien n’a été enregistré : aucun geste à transformer en skill.",
    "Steps" to "Étapes",
    "%1\$d actions" to "%1\$d gestes",
    "Step %1\$d" to "Étape %1\$d",
    "To review" to "À relire",
    "To review · %1\$s" to "À relire · %1\$s",
    "Nothing to review." to "Rien à relire.",
    "Turn it into a skill" to "En faire un skill",
    "Throw it away" to "Jeter",
    "Throw this demonstration away?" to "Jeter cette démonstration ?",
    "This demonstration will be lost. Nothing is written to the server." to
        "Cette démonstration sera perdue. Rien n’est écrit sur le serveur.",

    // Les raisons de `review[]`, dans les mots du brouillon.
    "no_label" to "libellé absent",
    "secret_field" to "champ masqué",
    "no_window" to "fenêtre de taille inconnue",
    "long_wait" to "attente longue",
    "unsupported_kind" to "geste non reconnu",

    // ---- Les refus et les routes absentes ----------------------------------------------------
    "This server can't record a demonstration (route missing)." to
        "Ce serveur ne sait pas enregistrer une démonstration (route absente).",
    "This server can't close a recording (route missing)." to
        "Ce serveur ne sait pas arrêter un enregistrement (route absente).",
    "This server can't turn a demonstration into a skill (route missing). The journal stays on this device." to
        "Ce serveur ne sait pas transformer une démonstration en skill (route absente). " +
        "Le journal reste sur cet appareil.",
    "A recording is already open on this server." to "Un enregistrement est déjà ouvert sur ce serveur.",
    "A demonstration is waiting to be turned into a skill." to
        "Une démonstration attend d’être transformée en skill.",
    "No recording is open." to "Aucun enregistrement n’est ouvert.",
    "This server doesn't know that recording." to "Ce serveur ne connaît pas cet enregistrement.",
    "That recording belongs to another bridge." to "Cet enregistrement appartient à un autre pont.",
    "The recording window has expired. Your demonstration is still on this device." to
        "La fenêtre d’enregistrement est expirée. Votre démonstration est restée sur cet appareil.",
    "The demonstration is still being recorded." to "La démonstration est encore en cours d’enregistrement.",
    "Nothing to turn into a skill: the demonstration wasn't recorded." to
        "Rien à transformer en skill : la démonstration n’a pas été enregistrée.",
    "The demonstration is too long to send." to "La démonstration est trop longue pour être envoyée.",
    "The bridge address can't be used for a recording." to
        "L’adresse du pont ne peut pas servir à un enregistrement.",
    "The recording server isn't available." to "Le serveur d’enregistrement n’est pas disponible.",
    "The server refused: %1\$s." to "Le serveur a refusé : %1\$s.",
)
