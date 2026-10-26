package com.onyx.app.localization

/**
 * Réglages → Outils: les titres et les descriptions que le serveur envoie.
 *
 * La page affichait les libellés du serveur tels quels — « Browser Automation », « navigate, click,
 * type, scroll », « background desktop control via cua-driver »… Le propriétaire: « traduis toute
 * cette page, on comprend rien ».
 *
 * Les clés sont **exactement** le libellé et la description renvoyés par le serveur (le libellé
 * sans son emoji, les descriptions paragraphe par paragraphe: [OnyxLocalization.translateParagraphs]).
 * Les noms d'outils (`web_search`, `a2a_call`, `execute_code`, `clarify`…) sont des identifiants
 * d'API: ils restent tels quels, et n'ont donc pas d'entrée ici.
 *
 * Les clés déjà traduites ailleurs (« Skills », « Memory », « navigate, click, type, scroll »…)
 * ne sont pas répétées: une seconde entrée écraserait la première.
 */
internal val onyxToolsPageFrench: Map<String, String> = linkedMapOf(
    // Les titres des jeux d'outils.
    "Web Search & Scraping" to "Recherche et lecture web",
    "Browser Automation" to "Automatisation du navigateur",
    "Terminal & Processes" to "Terminal et processus",
    "File Operations" to "Opérations sur les fichiers",
    "Code Execution" to "Exécution de code",
    "Vision / Image Analysis" to "Analyse d’images",
    "Video Analysis" to "Analyse de vidéos",
    "Image Generation" to "Création d’images",
    "Video Generation" to "Création de vidéos",
    "X (Twitter) Search" to "Recherche sur X (Twitter)",
    "Text-to-Speech" to "Synthèse vocale",
    "Speech-to-Text" to "Transcription vocale",
    "Task Planning" to "Planification des tâches",
    "Context Engine" to "Moteur de contexte",
    "Session Search" to "Recherche dans les conversations",
    "Clarifying Questions" to "Questions de clarification",
    "Task Delegation" to "Délégation de tâches",
    "Cron Jobs" to "Tâches planifiées",
    "Discord (read/participate)" to "Discord (lire et participer)",
    "Discord Server Admin" to "Administration d’un serveur Discord",
    "Computer Use (macOS/Windows/Linux)" to "Utilisation de l’ordinateur (macOS/Windows/Linux)",

    // Les descriptions, là où elles ne sont pas déjà traduites.
    "video_analyze (requires video-capable model)" to
        "video_analyze (nécessite un modèle capable de lire la vidéo)",
    "video_generate (text/image/reference)" to "video_generate (texte/image/référence)",
    "x_search (requires xAI OAuth or XAI_API_KEY)" to
        "x_search (nécessite xAI OAuth ou XAI_API_KEY)",
    "opt-in task board tools for this platform" to
        "outils de tableau de tâches, à activer pour cette plateforme",
    "runtime tools from the active context engine" to
        "outils d’exécution du moteur de contexte actif",
    "search past conversations" to "chercher dans les conversations passées",
    "remote connector tools and account authorization" to
        "outils de connexion à distance et autorisation de compte",
    "create/list/update/pause/resume/run, with optional attached skills" to
        "créer, lister, modifier, mettre en pause, reprendre, exécuter — avec des compétences jointes en option",
    "fetch messages, search members, create thread" to
        "lire les messages, chercher des membres, créer un fil",
    "list channels/roles, pin, assign roles" to
        "lister les salons et les rôles, épingler, attribuer des rôles",
    "group info, member queries, DM" to "infos du groupe, recherche de membres, messages privés",
    "background desktop control via cua-driver" to
        "contrôle du bureau en arrière-plan via cua-driver",

    // A2A: la fiche du greffon, un paragraphe à la fois.
    "A2A (Agent-to-Agent) protocol v1.0 support for Hermes Agent — both directions of the open Linux Foundation standard for inter-agent communication." to
        "Prise en charge du protocole A2A (Agent-to-Agent) v1.0 pour Hermes Agent — les deux sens de la norme ouverte de la Linux Foundation pour la communication entre agents.",
    "OUTBOUND (client tools): a2a_discover, a2a_call, a2a_list, a2a_history, and a2a_orchestrate let the agent fetch another agent's Agent Card and send it tasks over JSON-RPC — works with any A2A-compliant peer (Hermes, LangChain, CrewAI, Google ADK, OpenClaw, ...)." to
        "SORTANT (outils clients) : a2a_discover, a2a_call, a2a_list, a2a_history et a2a_orchestrate permettent à l’agent de récupérer la fiche d’un autre agent et de lui envoyer des tâches en JSON-RPC — compatible avec tout pair conforme A2A (Hermes, LangChain, CrewAI, Google ADK, OpenClaw, ...).",
    "INBOUND (platform adapter): exposes Hermes as an A2A-discoverable agent. An Agent Card is served at /.well-known/agent-card.json (v1.0 canonical path; legacy agent.json also answers) and incoming tasks are routed into the agent's live gateway session like any other platform — so the agent that replies is the same one talking to its user, with full memory and context, not a throwaway clone." to
        "ENTRANT (adaptateur de plateforme) : expose Hermes comme un agent découvrable en A2A. Une fiche d’agent est servie sur /.well-known/agent-card.json (chemin canonique v1.0 ; l’ancien agent.json répond aussi) et les tâches entrantes sont acheminées vers la session de passerelle active de l’agent, comme pour n’importe quelle autre plateforme — l’agent qui répond est donc celui qui parle à son utilisateur, avec toute sa mémoire et son contexte, et non un clone jetable.",
    "Security is on by default: no bearer token configured => localhost-only bind. Inbound task text passes through prompt-injection filters; outbound text is scrubbed of credential-shaped strings; every exchange is audit-logged and persisted to disk outside the context-compaction pipeline so conversations survive compaction and restarts." to
        "La sécurité est active par défaut : sans jeton configuré, l’écoute reste limitée à localhost. Le texte des tâches entrantes passe par les filtres d’injection de consignes ; le texte sortant est nettoyé des chaînes qui ressemblent à des identifiants ; chaque échange est journalisé et conservé sur disque hors du pipeline de compactage du contexte, pour que les conversations survivent au compactage et aux redémarrages.",
    "Pure stdlib transport (http.server + urllib) — no a2a-sdk dependency required." to
        "Transport en bibliothèque standard uniquement (http.server + urllib) — aucune dépendance a2a-sdk.",

    // Le pied de page de la section et l'état d'erreur.
    "Switches change the toolset's platform setting in Hermes's config, the same as the dashboard. The API server's own list (platform_toolsets.api_server) is separate and set in config.yaml on the Hermes host." to
        "Les interrupteurs changent le réglage de plateforme du jeu d’outils dans la configuration de Hermes, comme le tableau de bord. La liste propre au serveur d’API (platform_toolsets.api_server) est distincte et se règle dans config.yaml, sur l’hôte qui fait tourner Hermes.",
    "Anything here can be used by asking in plain words. Add the Hermes Dashboard login to toggle toolsets from here." to
        "Tout ce qui est ici s’utilise en le demandant en mots simples. Ajoutez l’identifiant du tableau de bord Hermes pour activer ou désactiver les jeux d’outils depuis ici.",
    "Couldn't reach the gateway" to "Passerelle injoignable",
)
