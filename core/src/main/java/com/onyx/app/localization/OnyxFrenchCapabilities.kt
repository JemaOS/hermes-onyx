package com.onyx.app.localization

/**
 * Les libellés de l'écran « Capacités » (compétences, outils, serveurs MCP).
 *
 * Les clés sont les chaînes anglaises écrites dans l'écran, comme partout dans l'app. Les
 * **identifiants** du serveur — un nom de compétence, un nom d'outil (`web_search`), l'adresse d'un
 * serveur MCP — ne passent jamais par ici : l'écran les rend verbatim (`localize = false`).
 */
internal val onyxCapabilitiesFrench: Map<String, String> = linkedMapOf(
    // Titre et sous-titre.
    "Capabilities" to "Capacités",
    "What the agent can use in new chats" to
        "Ce que l’agent peut utiliser dans les nouvelles conversations",

    // Les trois familles du sélecteur.
    "Skills" to "Compétences",
    "Toolsets" to "Outils",
    "MCP" to "MCP",

    // La recherche et le catalogue.
    "Search %d skills" to "Rechercher les %d compétences",
    "Browse the catalogue" to "Parcourir le catalogue",
    // La pilule du catalogue, sur l'écran des familles : le mot de la source était « Browse hub »,
    // resté en anglais faute d'entrée. Elle ouvre **la même page** que « Parcourir le catalogue » —
    // la page s'appelle « Le catalogue » sur cet écran, donc le mot est celui-là.
    "Browse hub" to "Parcourir le catalogue",
    "Nothing matches this search." to "Aucun résultat pour cette recherche.",
    "The catalogue" to "Le catalogue",
    "Search the catalogue" to "Rechercher dans le catalogue",
    "The catalogue isn’t available on this Hermes." to
        "Le catalogue n’est pas disponible sur cet Hermes.",
    "Loading the catalogue…" to "Chargement du catalogue…",
    "No skills in the catalogue." to "Aucune compétence dans le catalogue.",
    "Installed" to "Installée",
    "Install" to "Installer",
    "Installing…" to "Installation…",
    "Remove" to "Retirer",
    "Removing…" to "Retrait…",
    "Close" to "Fermer",

    // Les états de la liste.
    "No skills yet" to "Aucune compétence pour l’instant",
    "No toolsets on this Hermes." to "Aucun jeu d’outils sur cet Hermes.",
    "No MCP servers on this Hermes." to "Aucun serveur MCP sur cet Hermes.",
    "Toolsets the agent gets in new chats." to
        "Les jeux d’outils que l’agent reçoit dans les nouvelles conversations.",
    "Changes apply from the next chat." to
        "Les changements s’appliquent à partir de la prochaine conversation.",
    "Turned on" to "Allumée",
    "Turned off" to "Éteinte",
    "Couldn’t load" to "Impossible de charger",
    "Try again" to "Réessayer",

    // Les serveurs MCP : le test de connexion et ce qu'il rend.
    "Test" to "Tester",
    "Testing…" to "Test en cours…",
    "Connected" to "Connecté",
    "Connected · %d tools" to "Connecté · %d outils",
    "Connected · %d tool" to "Connecté · %d outil",
    "Not connected" to "Non connecté",
    "Set by the plugin" to "Défini par le greffon",

    // ---------------------------------------------------------------------------------------
    // Le complément du lot « Capacités exhaustives » : les trois familles au complet.
    // ---------------------------------------------------------------------------------------

    // Les comptes, un gabarit par famille (le nombre et l'accord changent avec la famille), et le
    // **singulier** à côté : « 1 outils » n'est pas du français.
    "%d skills" to "%d compétences",
    "%d skill" to "%d compétence",
    "%d toolsets" to "%d jeux d’outils",
    "%d toolset" to "%d jeu d’outils",
    "%d MCP servers" to "%d serveurs MCP",
    "%d MCP server" to "%d serveur MCP",
    "%d of %d" to "%1\$d sur %2\$d",
    "%d skills on" to "%1\$d compétences allumées",
    "%d skill on" to "%1\$d compétence allumée",
    "%d toolsets on" to "%1\$d jeux d’outils allumés",
    "%d toolset on" to "%1\$d jeu d’outils allumé",
    "%d MCP servers on" to "%1\$d serveurs actifs",
    "%d MCP server on" to "%1\$d serveur actif",
    "%d tools" to "%d outils",
    "%d tool" to "%d outil",
    "Prompts" to "Invites",
    "Resources" to "Ressources",

    // Ce que la famille engage, dit sur son onglet.
    "Skills the agent can read when a request matches." to
        "Les compétences que l’agent peut lire quand une demande y correspond.",

    // La recherche, une par famille (le champ dit ce qu'il cherche).
    "Search %d toolsets" to "Rechercher les %d jeux d’outils",
    "Search %d MCP servers" to "Rechercher les %d serveurs MCP",

    // Les outils d'un jeu : le dépli, et ce qu'il contient.
    "Show tools" to "Afficher les outils",
    "Hide tools" to "Masquer les outils",
    "Tools in this toolset" to "Outils de ce jeu d’outils",
    "No tools listed for this toolset." to "Aucun outil listé pour ce jeu d’outils.",

    // La fiche d'un serveur MCP : sa forme, ce qu'il expose, et ce qu'on peut en faire.
    "Over the network" to "Par le réseau",
    "Local command" to "Commande locale",
    "Transport unknown" to "Transport inconnu",
    "Sign-in (OAuth)" to "Connexion (OAuth)",
    "Header token" to "Jeton d’en-tête",
    "Tools this server exposes" to "Outils exposés par ce serveur",
    "Test the server to list its tools." to "Testez le serveur pour lister ses outils.",
    "Test again" to "Tester à nouveau",
    "Took %1\$s ms" to "%1\$s ms",
    "Back to the servers" to "Retour aux serveurs",

    // « Ajouter une source MCP » : le formulaire, ses champs et ses refus.
    "Add an MCP source" to "Ajouter une source MCP",
    "Add a server" to "Ajouter un serveur",
    "Adding…" to "Ajout…",
    "Command" to "Commande",
    "Arguments" to "Arguments",
    "Environment variables" to "Variables d’environnement",
    "One per line, like KEY=value." to "Une par ligne, sous la forme CLE=valeur.",
    "Authentication" to "Authentification",
    "Bearer token" to "Jeton Bearer",
    "A local command, or a server over the network." to "Une commande locale, ou un serveur par le réseau.",
    "Add the server" to "Ajouter le serveur",
    "Give the server a name." to "Donnez un nom au serveur.",
    "Give the server's address." to "Saisissez l’adresse du serveur.",
    "Give the command that starts the server." to "Saisissez la commande qui lance le serveur.",
    "Each environment variable needs a name and a value, like KEY=value." to
        "Chaque variable d’environnement a besoin d’un nom et d’une valeur, sous la forme CLE=valeur.",
    "A token is required for header authentication." to
        "Un jeton est nécessaire pour l’authentification par en-tête.",
    "Give one transport only: an address, or a command — not both." to
        "Un seul transport : une adresse, ou une commande — pas les deux.",
    "Unsupported authentication: %1\$s." to "Authentification non prise en charge : %1\$s.",
    "Arguments belong to a command, not to an address." to
        "Les arguments vont avec une commande, pas avec une adresse.",
    "Environment variables belong to a command, not to an address." to
        "Les variables d’environnement vont avec une commande, pas avec une adresse.",
    "A token goes with header authentication only." to
        "Un jeton ne s’utilise qu’avec l’authentification par en-tête.",
    "A command-launched server has no HTTP authentication." to
        "Un serveur lancé par une commande n’a pas d’authentification HTTP.",
    "A server named %1\$s already exists." to "Un serveur nommé %1\$s existe déjà.",
    "%1\$s is supplied by the plugin %2\$s; it can't be changed here." to
        "%1\$s vient du greffon %2\$s ; il ne se règle pas ici.",
    "The server %1\$s wasn't found." to "Le serveur %1\$s est introuvable.",
    "%1\$s was refused: %2\$s" to "%1\$s a été refusé : %2\$s",
    "The server refused: %1\$s" to "Le serveur a refusé : %1\$s",
    "The server needs every required field. Check the form and send again." to
        "Le serveur attend tous les champs obligatoires. Vérifiez le formulaire et renvoyez.",

    // Supprimer un serveur : la question, et le geste qui suit.
    "Delete this server?" to "Supprimer ce serveur ?",
    "Deleting…" to "Suppression…",
    "This removes %1\$s from the Hermes config file. It can't be undone." to
        "Cela retire %1\$s du fichier de configuration d’Hermes. C’est irréversible.",
    "Couldn't add the server" to "Impossible d’ajouter le serveur",
    "Couldn't remove the server" to "Impossible de retirer le serveur",

    // Lire une compétence : sa famille, son état, et son contenu quand la route le donne.
    "Read" to "Lire",

    // Le catalogue : l'état réel d'une entrée, et les gestes pour la poser ou la retirer.
    "Available" to "Disponible",
    "Built-in" to "Intégrée",
    "Trusted" to "Vérifiée",
    "Community" to "Communauté",
    "Everything the server offers to install, with what is already installed." to
        "Tout ce que le serveur propose d’installer, avec ce qui l’est déjà.",
    "Couldn't install it" to "Impossible de l’installer",
    "Couldn't remove it" to "Impossible de le retirer",

    // Les trois familles quand leur route manque : le nom de la route, pas un code nu.
    "This Hermes does not serve the skills route." to "Ce Hermes ne sert pas la route des compétences.",
    "This Hermes does not serve the toolsets route." to "Ce Hermes ne sert pas la route des jeux d’outils.",
    "This Hermes does not serve the MCP servers route." to "Ce Hermes ne sert pas la route des serveurs MCP.",

    // Les mots partagés par les trois familles : le dépli, l'ouverture, la lecture.
    "Expanded" to "Déplié",
    "Collapsed" to "Plié",
    "Open %1\$s" to "Ouvrir %1\$s",
    "Reading…" to "Lecture…",
    "Category" to "Famille",
    "No tools were listed." to "Aucun outil n’a été listé.",
    "The content isn't available for this skill." to "Le contenu n’est pas disponible pour cette compétence.",
    "Nothing here yet. Use “Add an MCP source” above." to
        "Rien ici pour l’instant : utilisez « Ajouter une source MCP » ci-dessus.",
)
