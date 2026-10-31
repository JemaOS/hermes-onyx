package com.onyx.app.localization

/**
 * Les libellés des outils d'un bot (écran « Tools » de l'édition d'un bot).
 *
 * Ils étaient affichés **en anglais** : le rendu passait `localize = false`, et les mots viennent
 * d'une table (`TOOLSET_TITLES`) écrite en anglais. Le propriétaire : « Regarde c'est pas traduit ».
 *
 * Les clés sont les mots anglais de la table — ceux que le serveur envoie pour les descriptions
 * (identifiants d'outils, eux, restent tels quels : `web_search`, `text_to_speech`…).
 */
internal val onyxToolsFrench: Map<String, String> = linkedMapOf(
    // Les jeux d'outils (titres).
    "Web search" to "Recherche web",
    "Browser" to "Navigateur",
    "Terminal" to "Terminal",
    "Files" to "Fichiers",
    "Run code" to "Exécuter du code",
    "Look at pictures" to "Regarder des images",
    "Make pictures" to "Créer des images",
    "Speak aloud" to "Lire à voix haute",
    "Transcribe audio" to "Transcrire l’audio",
    "Skills" to "Compétences",
    "To-do list" to "Liste de tâches",
    "Memory" to "Mémoire",
    "Context engine" to "Moteur de contexte",
    "Search past chats" to "Chercher dans les anciennes conversations",
    "Connections" to "Connexions",
    "Ask you questions" to "Vous poser des questions",
    "Hand off tasks" to "Déléguer des tâches",
    "Use the computer" to "Utiliser l’ordinateur",
    "Home Assistant" to "Home Assistant",
    "Kanban" to "Kanban",
    "Watch video" to "Regarder des vidéos",
    "Make video" to "Créer des vidéos",
    "Search X" to "Chercher sur X",
    "Search" to "Rechercher",
    "Spotify" to "Spotify",

    // Les descriptions qu'envoie le serveur.
    "navigate, click, type, scroll" to "naviguer, cliquer, taper, défiler",
    "terminal, process" to "terminal, processus",
    "read, write, patch, search" to "lire, écrire, corriger, chercher",
    "list, view, manage" to "lister, voir, gérer",
    "persistent memory across sessions" to "mémoire persistante d’une session à l’autre",
    "voice transcription (gateway voice messages + voice mode)" to
        "transcription vocale (messages vocaux de la passerelle + mode vocal)",
    "runtime tools from the active context" to "outils d’exécution du contexte actif",
    "Fewer tools make a bot's every turn quicker to start on a model running on your own machine." to
        "Moins d’outils, et chaque tour du bot démarre plus vite sur un modèle qui tourne sur votre machine.",
    // « Laisser les bots se parler » : ce que l'appui a donné.
    "Bots can message each other" to "Les bots peuvent se parler",
    "Done — tag a bot with @ in any bot chat, and it will ask it." to
        "C’est fait — taguez un bot avec @ dans n’importe quel fil de bot, et il ira lui demander.",
)
