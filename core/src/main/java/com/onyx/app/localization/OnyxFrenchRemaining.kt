package com.onyx.app.localization

/** Derniers libellés des pages secondaires et des états riches, séparés pour faciliter l’audit. */
internal val onyxRemainingFrench: Map<String, String> = linkedMapOf(
    // Lecteur de vidéo d'une pièce jointe: le refus, et les deux pas de la barre.
    "This video can't be played." to "Cette vidéo ne peut pas être lue.",
    "Back 15 seconds" to "Reculer de 15 secondes",
    "Forward 15 seconds" to "Avancer de 15 secondes",

    // « Capture rapide »: l'écran de la note, son bouton, et l'issue de l'appui.
    "Take a note" to "Prendre une note",
    "Take the note" to "Prendre la note",
    "The note" to "La note",
    "The plumber comes Thursday at ten" to "Le plombier passe jeudi à dix heures",
    "It is kept the moment you take it and filed as soon as the server answers — your memory, a list, a reminder, the calendar, whatever fits. Nothing comes back to you: Onyx files it and stays quiet." to
        "Elle est gardée dès que vous la prenez et classée dès que le serveur répond — votre mémoire, une liste, un rappel, le calendrier, ce qui convient. Rien ne vous revient : Onyx la classe et reste muet.",
    "Filing…" to "Classement…",
    "Filed." to "Classée.",
    "Waiting for the server. It will go on its own." to "En attente du serveur. Elle repartira toute seule.",
    "Waiting" to "En attente",
    "Nothing waiting." to "Rien en attente.",
    "They stay here until the server that was in use answers." to
        "Elles restent ici jusqu’à ce que le serveur en service réponde.",
    // Tableau kanban: l'état vide d'une colonne, avec le nom traduit de la colonne.
    "Nothing in %1\$s" to "Rien dans %1\$s",
    // Fichiers et contexte.
    "A path on the Hermes host. ~ is its user's home." to "Un chemin sur l’hôte Hermes. ~ désigne le dossier personnel de son utilisateur.",
    "Delete File" to "Supprimer le fichier",
    "Delete Folder and Its Contents" to "Supprimer le dossier et son contenu",
    "It is removed from the Hermes host. This can't be undone." to "Il est supprimé de l’hôte Hermes. Cette action est irréversible.",
    "Go to Folder" to "Aller au dossier",
    "Go to Folder…" to "Aller au dossier…",
    "Names that start with a dot are hidden." to "Les noms commençant par un point sont masqués.",
    "New Folder" to "Nouveau dossier",
    "New Folder…" to "Nouveau dossier…",
    "Path" to "Chemin",
    "Places" to "Emplacements",
    "Show Hidden Files" to "Afficher les fichiers cachés",
    "The file is longer than the editor shows; saving keeps what you see." to "Le fichier dépasse ce que l’éditeur affiche ; l’enregistrement conserve ce que vous voyez.",
    "This file isn't text, so it can't be edited here." to "Ce fichier n’est pas du texte et ne peut pas être modifié ici.",
    "This folder is empty." to "Ce dossier est vide.",
    "Upload Files…" to "Téléverser des fichiers…",
    "The gateway didn't say where the agent's folder is." to "Le gateway n’a pas indiqué l’emplacement du dossier de l’agent.",
    "Edited in place on the Hermes host through the Hermes Dashboard." to "Modifiés directement sur l’hôte Hermes via le Dashboard Hermes.",
    "The world it works in: the machine, the folders, the rules." to "Son environnement : la machine, les dossiers et les règles.",
    "What it has learned and keeps." to "Ce qu’il a appris et ce qu’il conserve.",
    "Who the agent is: its standing instructions." to "Qui est l’agent : ses instructions permanentes.",
    "Who you are to it." to "Qui vous êtes pour lui.",

    // Compétences et outils.
    "A skill is a SKILL.md the agent loads on demand when a request matches." to "Une compétence est un fichier SKILL.md que l’agent charge à la demande lorsqu’une requête correspond.",
    "Category (optional)" to "Catégorie (facultatif)",
    "Front matter (name, description, version, metadata) then When to Use, Procedure, Pitfalls, Verification. Onyx loads it on demand when a request matches." to "Front matter (nom, description, version, métadonnées), puis When to Use, Procedure, Pitfalls, Verification. Onyx la charge à la demande lorsqu’une requête correspond.",
    "Loading skills…" to "Chargement des compétences…",
    "Loading skill…" to "Chargement de la compétence…",
    "New skill" to "Nouvelle compétence",
    "Lowercase with hyphens, like the folder it becomes in ~/.hermes/skills." to "Minuscules avec des traits d’union, comme le dossier créé dans ~/.hermes/skills.",
    "Renaming isn't supported by the gateway; create a new skill instead." to "Le gateway ne permet pas de renommer ; créez plutôt une nouvelle compétence.",
    "Anything here can be used by asking in plain words. Add the Hermes Dashboard login to toggle toolsets from here." to "Tout ce qui figure ici peut être utilisé en langage naturel. Ajoutez les identifiants du Dashboard Hermes pour activer les jeux d’outils ici.",
    "Switches change the toolset's platform setting in Hermes's config, the same as the dashboard. The API server's own list (platform_toolsets.api_server) is separate and set in config.yaml on the Hermes host." to "Les interrupteurs modifient le réglage de plateforme du jeu d’outils dans la configuration de Hermes, comme dans le Dashboard. La liste propre au serveur API (platform_toolsets.api_server) est distincte et se règle dans config.yaml sur l’hôte Hermes.",

    // Kanban.
    "Add a comment" to "Ajouter un commentaire",
    "Ask Onyx about this card" to "Demander à Onyx à propos de cette carte",
    "Ask in a new conversation" to "Demander dans une nouvelle conversation",
    "Assignee (profile)" to "Assigné (profil)",
    "It is removed from the board. This can't be undone." to "Elle est supprimée du tableau. Cette action est irréversible.",
    "Details for the worker" to "Détails pour l’exécutant",
    "Edit card" to "Modifier la carte",
    "Moving…" to "Déplacement…",
    "New card" to "Nouvelle carte",
    "No comments yet" to "Aucun commentaire pour le moment",
    "Post" to "Publier",
    "Posting…" to "Publication…",
    "Reason" to "Raison",
    "Result" to "Résultat",
    "Start right away (Ready)" to "Démarrer immédiatement (Prêt)",
    "Summary" to "Résumé",
    "Why is it deferred?" to "Pourquoi est-ce reporté ?",
    "What is it waiting on?" to "Qu’est-ce qui bloque ?",
    "What should the reviewer look at?" to "Que doit vérifier le relecteur ?",
    "What was the outcome?" to "Quel a été le résultat ?",
    "To do" to "À faire",
    "Not running" to "À l’arrêt",
    "Every few hours" to "À intervalles de quelques heures",
    "On some days" to "Certains jours",

    // Pages riches et terminal.
    "Loading page…" to "Chargement de la page…",
    "Open in browser" to "Ouvrir dans le navigateur",
    "Reload" to "Recharger",
    "Share page" to "Partager la page",
    "This page didn't load." to "Cette page ne s’est pas chargée.",
    "This page has nothing in it." to "Cette page est vide.",
    "Rich" to "Contenu riche",
    "A shell on the machine Hermes runs on, over SSH. Nothing goes through the gateway." to "Un shell sur la machine où Hermes s’exécute, via SSH. Rien ne transite par le gateway.",
    "Host and user are needed." to "L’hôte et l’utilisateur sont requis.",
    "The Hermes host" to "L’hôte Hermes",
    "User" to "Utilisateur",

    // Montre: écran, tuile et erreurs produites localement.
    "Tap to talk" to "Touchez pour parler",
    "Read again" to "Réécouter",
    "Your server" to "Votre serveur",
    "READY" to "PRÊT",
    "LISTENING" to "ÉCOUTE",
    "THINKING" to "RÉFLEXION",
    "SPEAKING" to "LECTURE",
    "PROBLEM" to "PROBLÈME",
    "Ask Onyx" to "Demander à Onyx",
    "Tap and talk" to "Touchez et parlez",
    "The server sent nothing back." to "Le serveur n’a rien renvoyé.",
    "Something went wrong." to "Une erreur est survenue.",

    // Erreurs de transport produites par Onyx (le corps du serveur reste brut).
    "This bot was just changed elsewhere. Review it and choose again." to "Ce bot a été changé ailleurs à l'instant. Regardez et choisissez encore.",
    "Hermes did not keep the bot setting." to "Hermes n'a pas gardé le réglage du bot.",
    "no channel to the Dashboard" to "pas de canal avec le Dashboard",
    "the channel closed before sending" to "le canal s'est fermé avant l'envoi",
    "%1\$s did not answer" to "%1\$s n'a pas répondu",
    "unreadable ticket" to "ticket illisible",
    "empty response body" to "corps de réponse vide",
    "the server reported an error" to "le serveur a signalé une erreur",
    "Dashboard session rejected twice in a row" to "session du Dashboard refusée deux fois de suite",
    "unreadable response: %1\$s" to "réponse illisible : %1\$s",
    "Local server" to "Serveur local",
    "empty base URL" to "URL de base vide",
    "unreadable chunk: %1\$s" to "fragment illisible : %1\$s",
    "empty Dashboard address" to "adresse du Dashboard vide",
    "empty body" to "corps vide",
    "api/status returned nothing" to "api/status n’a rien renvoyé",

    // États vides des écrans sans Dashboard, et phrases d'aide qui les accompagnent.
    "No gateway configured" to "Aucun gateway configuré",
    "Cron jobs live on the Hermes gateway. Set up the Hermes Dashboard or the Hermes API in Settings." to
        "Les tâches cron vivent sur le gateway Hermes. Configurez le Dashboard Hermes ou l’API Hermes dans Réglages.",
    "The kanban board is part of the Hermes Dashboard. Add your Hermes Dashboard address and login in Settings." to
        "Le tableau kanban fait partie du Dashboard Hermes. Ajoutez l’adresse et les identifiants de votre Dashboard Hermes dans Réglages.",

    // Visage d'un bot: où il est conservé, selon la connexion.
    "Kept on your Hermes server, so Onyx on your other devices shows it too." to
        "Conservé sur votre serveur Hermes, pour que Onyx l’affiche aussi sur vos autres appareils.",
    "Kept on this phone; this connection has no Hermes server login." to
        "Conservé sur ce téléphone ; cette connexion n’a pas d’identifiants de serveur Hermes.",

    // Exemples saisis dans les horaires d'un bot (le texte affiché avant la saisie).
    "What to wear" to "Quoi porter",
    "Check the weather and my calendar, and tell me what to wear" to
        "Consulte la météo et mon agenda, et dis-moi quoi porter",

    // La suppression d'un bot, ses compétences, et le réglage qui les fait se parler.
    "Couldn't delete it: %1\$s" to "Impossible de le supprimer : %1\$s",
    "The main profile can't be deleted." to "Le profil principal ne peut pas être supprimé.",
    "%1\$s isn't on your server any more." to "%1\$s n’est plus sur votre serveur.",
    "Couldn't read this bot: %1\$s" to "Impossible de lire ce bot : %1\$s",
    "Couldn't switch %1\$s on: %2\$s" to "Impossible d’allumer %1\$s : %2\$s",
    "Couldn't switch %1\$s off: %2\$s" to "Impossible d’éteindre %1\$s : %2\$s",
    "Couldn't pin %1\$s: %2\$s" to "Impossible d’épingler %1\$s : %2\$s",
    "Couldn't unpin %1\$s: %2\$s" to "Impossible de détacher %1\$s : %2\$s",
    "Couldn't set that up: %1\$s" to "Impossible de régler ça : %1\$s",
    "Talk to %1\$s" to "Parler à %1\$s",
    "Let bots message each other?" to "Laisser les bots discuter entre eux ?",
    "Let Them" to "Les laisser",
    "Hermes gives each bot's chat a tool to message the other bots, so you can say “@scout, what do you think?” in one bot's chat and have it ask. Onyx turns this on the way Hermes Desktop does, by setting your main profile up as a bot on the server. Chats with your agent stay as they are." to
        "Hermes donne au chat de chaque bot un outil pour écrire aux autres, si bien que vous pouvez dire « @scout, qu’en penses-tu ? » dans le chat d’un bot et le faire demander. Onyx l’allume comme Hermes Desktop : en installant votre profil principal comme bot sur le serveur. Vos conversations avec votre agent ne changent pas.",
    // « Hidden Bots » (`HiddenBotsView`) — les libellés eux-mêmes sont dans `OnyxFrench`
    // (« Bots masqués », « Masqué »), qui dit la même chose que le reste des bots masqués.
    "No hidden bots" to "Aucun bot caché",
    "Couldn't show the bot" to "Impossible d’afficher le bot",

    // « Usage & cost » (`SessionUsageView`).
    "Usage & cost" to "Usage et coût",
    "Input" to "Entrée",
    "Output" to "Sortie",
    "Reasoning" to "Raisonnement",
    "Cache read" to "Cache lu",
    "Cache write" to "Cache écrit",
    "Total" to "Total",
    "Cost" to "Coût",
    "Estimated" to "Estimé",
    "Actual" to "Réel",
    "Activity" to "Activité",
    "Tool calls" to "Appels d’outils",
    "API calls" to "Appels d’API",
    "Model" to "Modèle",
    "Source" to "Origine",
    "Costs are the gateway's own estimate from provider pricing." to
        "Les coûts sont l’estimation du gateway, d’après les tarifs des fournisseurs.",

    // Les sessions d'un projet (`ProjectSessionsView`).
    "Couldn't load the project" to "Impossible de charger le projet",
    "No project" to "Aucun projet",
    "Try Again" to "Réessayer",

    // Le direct du tableau kanban (`KanbanView`: l'icône de diffusion de l'en-tête).
    "Live updates on" to "Mises à jour en direct",
    "Polling for updates" to "Interrogation du serveur",
    // Le lecteur de PDF des pièces jointes (`PdfViewer`).
    "This PDF can't be opened." to "Ce PDF ne peut pas être ouvert.",
    "Previous page" to "Page précédente",
    "Next page" to "Page suivante",
    "Fit" to "Ajuster",
    "Couldn't open it." to "Impossible de l’ouvrir.",
)
