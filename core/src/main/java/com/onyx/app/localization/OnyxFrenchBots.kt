package com.onyx.app.localization

/**
 * Le catalogue du **partage de modèle de bot** : les refus du codec, l'aperçu et ce que le lien ne
 * porte pas.
 *
 * Ces phrases sont les clés anglaises du code, traduites ici. Un refus se dit au destinataire avec
 * ses mots : « ce modèle porte une clé » n'est pas une panne technique, c'est une règle — et elle
 * se lit en français.
 */
internal val onyxBotsFrench: Map<String, String> = linkedMapOf(
    // ————— Les refus du codec —————
    "That isn't a Onyx bot template link."
        to "Ce n’est pas un lien de modèle de bot Onyx.",
    "This bot template was made by a newer version of Onyx (format %1\$s). Update the app, then try again."
        to "Ce modèle de bot a été créé par une version plus récente d’Onyx (format %1\$s). Mettez l’app à jour, puis réessayez.",
    "This bot template carries a credential or a server address (%1\$s). Onyx refuses it."
        to "Ce modèle de bot porte un identifiant secret ou une adresse de serveur (%1\$s). Onyx le refuse.",
    "This bot template link is damaged and can't be read."
        to "Ce lien de modèle de bot est abîmé et ne peut pas être lu.",
    "This bot template carries a field Onyx doesn't know: %1\$s."
        to "Ce modèle de bot porte un champ qu’Onyx ne connaît pas : %1\$s.",
    "This bot template link is too long to be one of Onyx's."
        to "Ce lien de modèle de bot est trop long pour être un des liens d’Onyx.",
    "This bot template has an invalid %1\$s."
        to "Ce modèle de bot a un champ invalide : %1\$s.",

    // ————— L'aperçu —————
    "No role given."
        to "Aucun rôle indiqué.",
    "Untitled bot"
        to "Bot sans nom",

    // ————— Ce que le lien ne porte pas —————
    "Not in the link: the API key, the token and the server address. A template that carries one is refused."
        to "Pas dans le lien : la clé API, le jeton et l’adresse du serveur. Un modèle qui en porte un est refusé.",
    "Not in the link: the conversations, the memory and what the bot has learned."
        to "Pas dans le lien : les conversations, la mémoire et ce que le bot a appris.",
    "Not in the link: the routines and schedules."
        to "Pas dans le lien : les routines et les horaires.",
    "Not in the link: the voice."
        to "Pas dans le lien : la voix.",
    "Not in the link: who can see the bot, and its sharing settings."
        to "Pas dans le lien : qui peut voir le bot, et ses réglages de partage.",
    "Not in the link: the files and attachments the bot can reach."
        to "Pas dans le lien : les fichiers et les pièces jointes auxquels le bot a accès.",

    // ————— La feuille du partage —————
    "Share as Template…"
        to "Partager comme modèle…",
    "Anyone who opens this link sees its name, role, tools, skills, model and face before adding the bot. It carries no key, no token and no server address."
        to "Toute personne qui ouvre ce lien voit le nom du bot, son rôle, ses outils, ses compétences, son modèle et son visage avant de l’ajouter. Il ne porte ni clé, ni jeton, ni adresse de serveur.",
    "Copy link"
        to "Copier le lien",
    "Bot template link"
        to "Lien du modèle de bot",
    "Bot template QR code"
        to "QR du modèle de bot",
    "The link is ready to send. Scan the code from another phone to open it there."
        to "Le lien est prêt à être envoyé. Scannez le code depuis un autre téléphone pour l’y ouvrir.",
    "The prompt (SOUL.md) goes with the link."
        to "Le prompt (SOUL.md) part avec le lien.",
    "This bot has no SOUL.md: the link carries no prompt."
        to "Ce bot n’a pas de SOUL.md : le lien part sans prompt.",
    "Preparing the link…"
        to "Préparation du lien…",

    // ————— La feuille d'arrivée d'un lien —————
    "Add this bot?"
        to "Ajouter ce bot ?",
    "This link came from someone else. Check what it brings before adding the bot."
        to "Ce lien vient de quelqu’un d’autre. Vérifiez ce qu’il apporte avant d’ajouter le bot.",
    "What the link doesn't carry"
        to "Ce que le lien ne porte pas",
    "Added %1\$s, but not everything was written: %2\$s."
        to "« %1\$s » a été ajouté, mais tout n’a pas été écrit : %2\$s.",
    "the tool sets"
        to "les jeux d’outils",
    "the default model"
        to "le modèle par défaut",
    "the skills"
        to "les compétences",
)
