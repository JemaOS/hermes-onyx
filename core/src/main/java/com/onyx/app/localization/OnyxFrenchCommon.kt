package com.onyx.app.localization

/** Libellés des écrans transverses: serveurs, modèles, erreurs, verrouillage et nouveautés. */
internal val onyxCommonFrench: Map<String, String> = linkedMapOf(
    // Serveurs et profils.
    "A new server can be filled in from a setup code: scan or paste one on its setup screen." to "Un nouveau serveur peut être configuré à partir d’un code : scannez-en un ou collez-le sur son écran de configuration.",
    "Add server…" to "Ajouter un serveur…",
    "Edit connection…" to "Modifier la connexion…",
    "Name, e.g. Home or Office" to "Nom, p. ex. Maison ou Bureau",
    "New server" to "Nouveau serveur",
    "Rename server" to "Renommer le serveur",
    "Show setup code" to "Afficher le code de configuration",
    "Tap a server to switch to it; that starts a new conversation. Touch and hold to rename or to show its setup code for another device, swipe to remove one you're not using. Each server keeps its own addresses, keys, profile and model." to "Touchez un serveur pour le sélectionner ; cela démarre une nouvelle conversation. Maintenez pour le renommer ou afficher son code de configuration pour un autre appareil, balayez pour en supprimer un inutilisé. Chaque serveur conserve ses propres adresses, clés, profil et modèle.",
    "Then set up how Onyx reaches it." to "Configurez ensuite la façon dont Onyx s’y connecte.",
    "Add the Hermes Dashboard login in Connection details to pick from a list. Over the Hermes API, a profile other than Default needs gateway.multiplex_profiles turned on in the gateway's config." to "Ajoutez les identifiants du Dashboard Hermes dans Détails de connexion pour choisir dans une liste. Avec l’API Hermes, un profil autre que Default nécessite gateway.multiplex_profiles dans la configuration du gateway.",
    "By name" to "Par nom",
    "Chats, sessions, projects, skills, tools, cron jobs and the context and memory files follow the profile. The Kanban board is shared by every profile. Switching starts a new conversation." to "Les discussions, sessions, projets, compétences, outils, tâches cron et fichiers de contexte et de mémoire suivent le profil. Le tableau Kanban est partagé par tous les profils. Changer de profil démarre une nouvelle conversation.",
    "Couldn't load the list, but you can still name a profile." to "Impossible de charger la liste, mais vous pouvez toujours nommer un profil.",
    "Loading profiles…" to "Chargement des profils…",
    "The server's main profile." to "Le profil principal du serveur.",
    "Use this profile" to "Utiliser ce profil",

    // Diagnostic de connexion.
    "Add your gateway key" to "Ajouter la clé de votre gateway",
    "Can't reach your gateway" to "Impossible de contacter votre gateway",
    "Couldn't reach the server" to "Impossible de contacter le serveur",
    "Check that the gateway is running and that this phone is on your network or tailnet." to "Vérifiez que le gateway est actif et que ce téléphone est sur votre réseau ou votre tailnet.",
    "Hide details" to "Masquer les détails",
    "Onyx needs the gateway's API key before it can read your conversations." to "Onyx a besoin de la clé API du gateway pour lire vos conversations.",
    "Sign in to the Hermes Dashboard" to "Se connecter au Dashboard Hermes",
    "The gateway answered, but not in a way Onyx understood." to "Le gateway a répondu, mais d’une façon que Onyx n’a pas comprise.",
    "This profile isn't available" to "Ce profil n’est pas disponible",
    "This server has no profile by that name, or doesn't serve it over the API. Pick another under Settings → Servers." to "Ce serveur ne connaît pas ce profil ou ne l’expose pas via l’API. Choisissez-en un autre dans Réglages → Serveurs.",
    "Your conversations live on the desktop gateway, and it needs you to sign in before it will share them." to "Vos conversations sont sur le gateway de bureau, qui exige une connexion avant de les partager.",

    // Modèles et raisonnement.
    "Applies to this conversation and to new ones." to "S’applique à cette conversation et aux nouvelles.",
    "Gateway default" to "Valeur par défaut du gateway",
    "Hide" to "Masquer",
    "Mute" to "Mettre en silence",
    "How much the model thinks before answering. None asks the model not to think; any other level asks it to, and is passed on as its effort. Models that don't grade effort treat every level the same, and a server that fixes thinking on or off has the last word. Default leaves it to the server." to "Le degré de réflexion du modèle avant sa réponse. Aucun demande au modèle de ne pas réfléchir ; tout autre niveau lui demande de réfléchir et est transmis comme effort. Les modèles qui ne graduent pas l’effort traitent tous les niveaux de la même façon, et un serveur qui impose la réflexion a le dernier mot. Défaut laisse le choix au serveur.",
    "How much the model thinks before answering. None asks the model not to think; some models and servers decide that themselves. X-High and Max are for frontier models such as Claude and GPT; others treat them as High or may refuse them. Applies to this conversation and to new ones. Default leaves it to the gateway, and leaves the open conversation at the level it has." to "Le degré de réflexion du modèle avant sa réponse. Aucun demande au modèle de ne pas réfléchir ; certains modèles et serveurs décident eux-mêmes. X-High et Max sont destinés aux modèles de pointe comme Claude et GPT ; les autres les traitent comme High ou peuvent les refuser. S’applique à cette conversation et aux nouvelles. Défaut laisse le choix au gateway et conserve le niveau de la conversation ouverte.",
    "The endpoint's default" to "Valeur par défaut du point de terminaison",
    "Whatever Onyx is configured to use" to "Ce que Onyx est configuré pour utiliser",
    "Whatever the endpoint is configured to use" to "Ce que le point de terminaison est configuré pour utiliser",

    // Verrouillage et muettes.
    "Onyx is locked" to "Onyx est verrouillé",
    "That passcode isn't right." to "Ce code est incorrect.",
    "Unlock to see your conversations" to "Déverrouillez pour voir vos conversations",
    "Use the passcode" to "Utiliser le code",
    "For 1 Hour" to "Pendant 1 heure",
    "For 8 Hours" to "Pendant 8 heures",
    "Until Tomorrow" to "Jusqu’à demain",
    "Until I Turn It Back On" to "Jusqu’à réactivation",
    "Unmuted" to "Son rétabli",
    "Muted until " to "Silencieux jusqu’à ",

    // Nouveautés et remerciements.
    "Bots, with faces" to "Des bots avec un visage",
    "Code, diagrams and math" to "Code, diagrammes et mathématiques",
    "Every Hermes profile shows up as a bot, with an orb or a face you pick. Group chats let several answer in turn." to "Chaque profil Hermes apparaît comme un bot, avec une orbe ou un visage de votre choix. Les discussions de groupe permettent à plusieurs bots de répondre à tour de rôle.",
    "Code is coloured and numbered, with a copy button; Mermaid and formulas are drawn on the phone, offline." to "Le code est coloré et numéroté, avec un bouton Copier ; Mermaid et les formules sont dessinés sur le téléphone, hors ligne.",
    "Voice that stops when you say so" to "Une voix qui s’arrête quand vous le demandez",
    "Hands-free listening ends on your own stop phrases, and reading a reply aloud stops the moment you speak." to "L’écoute mains libres s’arrête avec vos propres phrases d’arrêt, et la lecture d’une réponse s’interrompt dès que vous parlez.",
    "Several servers" to "Plusieurs serveurs",
    "Switch between gateways, each with its own addresses, keys, profile and model." to "Passez d’un gateway à l’autre, chacun avec ses propres adresses, clés, profil et modèle.",
    "What's New in Onyx" to "Nouveautés de Onyx",

    // Soutien et pourboires.
    "A coffee" to "Un café",
    "A lunch" to "Un déjeuner",
    "A dinner" to "Un dîner",
    "A one-time tip through the store. It doesn't unlock anything: every feature is already yours." to "Un pourboire unique via la boutique. Il ne débloque rien : toutes les fonctionnalités sont déjà à vous.",
    "It really does help." to "Cela aide vraiment.",
    "Onyx is made by one small company, with no ads, no accounts and no tracking. If it's useful to you, a tip helps keep it going." to "Onyx est développé par une petite entreprise, sans publicité, sans compte et sans suivi. Si l’app vous est utile, un pourboire l’aide à continuer.",
    "Tips aren't available right now. Nothing's wrong on your side; every feature is already yours." to "Les pourboires ne sont pas disponibles pour le moment. Tout va bien de votre côté ; toutes les fonctionnalités sont déjà à vous.",
    "Thank you!" to "Merci !",

    // Divers transverse.
    "Its profile's SOUL.md." to "Le fichier SOUL.md de son profil.",
    "Opens your most recent conversation" to "Ouvre votre conversation la plus récente",
    "Go" to "Aller",
    "and" to "et",
    // La queue d'une liste de trois noms et plus: met la virgule d'Oxford devant « and »,
    // là où le français n'en met pas devant « et » (« Onyx, Scout, and Quill » / « Onyx, Scout et
    // Quill », `formatted(.list(type: .and))`).
    ", and" to " et",
    "Couldn't connect." to "Impossible de se connecter.",

    // « Tous vos serveurs dans une seule liste » (`AllServers`): le menu du serveur, l'étiquette
    // d'une rangée et la ligne des serveurs qui n'ont pas répondu.
    "All Servers" to "Tous les serveurs",
    "Server" to "Serveur",
    "%1\$s didn't answer, so its chats aren't here." to "%1\$s n’a pas répondu, donc ses conversations ne sont pas ici.",
    "%1\$s didn't answer, so their chats aren't here." to "%1\$s n’ont pas répondu, donc leurs conversations ne sont pas ici.",
    "Open on %1\$s" to "Ouvrir sur %1\$s",
    "· new chats on %1\$s" to "· nouvelles conversations sur %1\$s",
    "Opens it on %1\$s. Onyx moves to that server." to "L’ouvre sur %1\$s. Onyx bascule sur ce serveur.",
)
