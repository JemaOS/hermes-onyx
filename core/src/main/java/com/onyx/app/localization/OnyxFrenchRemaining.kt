package com.onyx.app.localization

/** Derniers libellés des pages secondaires et des états riches, séparés pour faciliter l’audit. */
internal val onyxRemainingFrench: Map<String, String> = linkedMapOf(
    // Lecteur de vidéo d'une pièce jointe : le refus, et les deux pas de la barre.
    "This video can't be played." to "Cette vidéo ne peut pas être lue.",
    "Back 15 seconds" to "Reculer de 15 secondes",
    "Forward 15 seconds" to "Avancer de 15 secondes",

    // « Capture rapide » : l'écran de la note, son bouton, et l'issue de l'appui.
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
    // Tableau kanban : l'état vide d'une colonne, avec le nom traduit de la colonne.
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

    // Montre : écran, tuile et erreurs produites localement.
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
    "Hermes did not make the bot." to "Hermes n'a pas créé le bot.",
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

    // Visage d'un bot : où il est conservé, selon la connexion.
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
    "Usage & cost" to "Consommation et coût",
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

    // Les sessions d'un projet (`ProjectSessionsView`) et ce qu'on y fait. Le fourre-tout du
    // serveur s'appelle **« Home »** (`project_tree.NO_PROJECT_LABEL`), le nom que montre la
    // référence : c'est le nom qu'on affiche, tel quel — un projet porte son nom, et celui-ci est
    // le sien. Cette ligne ramène le mot d'une version antérieure du serveur (« No project ») au
    // nom d'aujourd'hui, pour qu'aucune phrase ne dise deux mots pour le même groupe.
    // (« Search in … » plus bas suit : « Rechercher dans Home ».)
    "Couldn't load the project" to "Impossible de charger le projet",
    "No project" to "Home",
    "Try Again" to "Réessayer",
    "Conversations started here work in its folder on your server:" to
        "Les conversations lancées ici travaillent dans son dossier sur votre serveur :",
    // Créer, renommer et retirer un projet : les mots de la référence, un par un.
    "Rename project" to "Renommer le projet",
    "Project menu" to "Menu du projet",
    "Remove Project" to "Supprimer le projet",
    "Its folder keeps its name." to "Son dossier garde son nom.",
    "A folder of that name is made in ~/Projects on your server. The project's conversations work in it." to
        "Un dossier de ce nom est créé dans ~/Projects sur votre serveur. Les conversations du projet y travaillent.",
    "Remove “%1\$s” from Projects?" to "Retirer « %1\$s » des projets ?",
    "Its folder and its conversations stay on the server. The conversations just aren't grouped under it any more." to
        "Son dossier et ses conversations restent sur le serveur. Les conversations ne sont simplement plus réunies sous ce projet.",
    "Couldn't make the project: %1\$s" to "Impossible de créer le projet : %1\$s",
    "Couldn't rename the project: %1\$s" to "Impossible de renommer le projet : %1\$s",
    "Couldn't remove the project: %1\$s" to "Impossible de supprimer le projet : %1\$s",
    "Couldn't move %1\$s" to "Impossible de déplacer %1\$s",
    "They are removed from Onyx's registry for every client." to
        "Elles sont retirées du registre d’Onyx pour tous les clients.",

    // Le direct du tableau kanban (`KanbanView` : l'icône de diffusion de l'en-tête).
    "Live updates on" to "Mises à jour en direct",
    "Polling for updates" to "Interrogation du serveur",
    // Le lecteur de PDF des pièces jointes (`PdfViewer`).
    "This PDF can't be opened." to "Ce PDF ne peut pas être ouvert.",
    "Previous page" to "Page précédente",
    "Next page" to "Page suivante",
    "Fit" to "Ajuster",
    "Couldn't open it." to "Impossible de l’ouvrir.",

    // L'onglet Fichiers : une cause **nommée** par ligne, et un geste qui peut aboutir.
    "Unavailable" to "Indisponible",
    "Unreadable" to "Illisible",
    "This file is no longer on this device." to "Ce fichier n’est plus sur cet appareil.",
    "Access to this file has expired: it comes from the gallery. Send it again from the device." to
        "L’accès à ce fichier a expiré : il vient de la galerie. Renvoyez-le depuis l’appareil.",
    "The server answered with a receipt instead of the file. Open it again from the reply." to
        "Le serveur a répondu par un accusé de réception, pas par le fichier. Rouvrez-le depuis la réponse.",
    "Its file is empty, cut short, or no longer on this device. Send it again from the device." to
        "Son fichier est vide, tronqué, ou n’est plus sur l’appareil. Renvoyez-le depuis l’appareil.",
    // La **carte de composition** et le **sélecteur de modèle** (les deux fonctions reprises de
    // la référence). Le mot du niveau de réflexion est celui de l'app — « Réflexion » — et non
    // « Raisonnement » : c'est le même mot que le repli d'un tour (« Réflexion pendant 1 s »).
    "Reasoning level" to "Réflexion",
    // Le cran « Extra high » de la référence : son mot est « Très élevé », et il pilote la valeur
    // `xhigh` du serveur (voir `ReasoningLevels.rowOrder`).
    "Extra high" to "Très élevé",
    "X-High" to "Très élevé",
    "Pick this bot's model. It applies to all of this bot's conversations and routines." to
        "Choisissez le modèle de ce bot. Il vaut pour toutes ses conversations et ses routines.",
    "Search a model or a provider" to "Rechercher un modèle ou un fournisseur",
    // L'invite **courte** : la longue ne tenait pas dans la largeur du champ et se lisait
    // « Rechercher un modèle ou un fo… » (défaut relevé sur la feuille de modèle).
    "Search a model" to "Rechercher un modèle",
    "This server doesn't offer every level: %1\$s." to
        "Ce serveur ne propose pas tous les crans : %1\$s.",
    // Ce qui remplace cette note de développement : **le compte** des crans offerts, qui est
    // l'information utile à qui choisit.
    "%1\$d of %2\$d reasoning levels on this server." to
        "%1\$d crans de réflexion sur %2\$d proposés par ce serveur.",
    "Favourites" to "Favoris",
    "Recents" to "Récents",
    "All models" to "Tous les modèles",
    "Loading models…" to "Chargement des modèles…",
    "This server lists no model." to "Ce serveur ne liste aucun modèle.",
    "Clear the search" to "Effacer la recherche",
    "Add to favourites" to "Ajouter aux favoris",
    "Remove from favourites" to "Retirer des favoris",
    // La **portée du choix**, écrite à droite du titre : elle dit ce que le choix fait vraiment.
    // « Pour cette conversation » quand le choix ne vaut que pour la conversation ouverte ; le bot
    // et le serveur ont chacun leur mot, parce que c'est *leur* modèle que l'app écrit alors.
    "For this conversation" to "Pour cette conversation",
    // La portée du bot, **en clair** : « Pour ce bot, partout » se lisait comme une abréviation
    // obscure (et se faisait trancher à droite du titre). La phrase dit maintenant ce qu'elle
    // signifie.
    "For every conversation of this bot" to "Pour toutes les conversations de ce bot",
    "For this bot, everywhere" to "Pour ce bot, partout",
    "For this server" to "Pour ce serveur",
    // La rangée des fournisseurs (les pastilles de filtre sous la recherche). « Tout » est la clé
    // « All » déjà traduite ; la pastille se lit donc « Tout » en français et « All » en anglais.
    "This server lists no provider." to "Ce serveur ne liste aucun fournisseur.",
    "No model matches." to "Aucun modèle ne correspond.",

    // **Le champ de la plaque** : le libellé de la maquette, mot pour mot —
    // `<span class="ph">Écrire à Onyx…</span>` (`fil-clair.html:479`). La clé courte est **la même
    // phrase sans l'ellipse** : la plaque porte quatre commandes, donc le champ ne garde que
    // ~127 dp de texte à 1,3× de police, et c'est l'ellipse (13,8 dp) qui tombe — jamais une
    // commande. Le choix n'est pas écrit ici, il est **mesuré** (`PlaqueInvite.key`).
    "Write to Onyx…" to "Écrire à Onyx…",
    "Write to Onyx" to "Écrire à Onyx",

    // **Les trois vues d'une conversation** : points de restauration (et leurs diffs), processus en
    // arrière-plan (et leurs sorties), consommation et coût. Le vocabulaire suit la référence.
    "model calls: " to "appels au modèle : ",
    "Estimated from the model's published rates." to "Estimé d'après les tarifs publiés du modèle.",
    "Used" to "Utilisé",
    "From cache" to "Depuis le cache",
    "This server does not announce the breakdown by category." to "Le serveur n'annonce pas le détail par catégorie.",
    "This server does not announce which files fill the context." to "Le serveur n'annonce pas quels fichiers remplissent le contexte.",
    "This server publishes no account usage; no weekly limit or renewal date is announced." to
        "Le serveur ne publie pas la consommation du compte : aucune limite hebdomadaire ni échéance n'est annoncée.",
    "Plan" to "Forfait",
    "Weekly limit used" to "Limite hebdomadaire utilisée",
    "Spent" to "Dépensé",
    "Renews" to "Renouvellement",
    "Files read" to "Fichiers lus",
    "Reading the counters…" to "Lecture des compteurs…",
    "Reading what is running…" to "Lecture de ce qui tourne…",
    "This server gave no output for this process." to "Le serveur n'a pas donné de sortie pour ce processus.",
    "Reading the restore points…" to "Lecture des points de restauration…",
    "Snapshots taken before the agent changes files." to "Instantanés pris avant que l'agent ne modifie des fichiers.",
    "Reading the diff…" to "Lecture du diff…",
    "This server gave no diff for this restore point." to "Le serveur n'a pas donné de diff pour ce point de restauration.",
    "%1\$d running" to "%1\$d en cours",
)
