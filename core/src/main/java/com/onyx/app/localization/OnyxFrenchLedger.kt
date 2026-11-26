package com.onyx.app.localization

/**
 * Les libellés du **relevé d'agent** : les rangées de l'écran, et les phrases qui expliquent une
 * donnée absente.
 *
 * Deux sortes de clés, à ne pas confondre :
 *
 * - les **libellés** (« Working time », « Calls that succeeded ») passent par [BasicText] avec
 *   `localize = true`, donc par le dictionnaire ;
 * - les **notes et valeurs composées** portent des nombres déjà mis en forme ; elles sont traduites
 *   **morceau par morceau** avant d'être jointes (une phrase cousue ne se traduit plus), et l'écran
 *   les rend verbatim. Leurs clés sont ici aussi, y compris le singulier et le pluriel.
 *
 * Aucun chiffre n'est traduit : les montants, les jetons et les durées se forment en code
 * (`LedgerFormat`), jamais par une table.
 */
internal val onyxLedgerFrench: Map<String, String> = linkedMapOf(
    // Le titre de l'écran et ses deux échelles.
    "Agent ledger" to "Relevé de l’agent",
    "This conversation" to "Cette conversation",
    "Per bot" to "Par bot",
    "What the agent did, with what, how long, and what it cost." to
        "Ce que l’agent a fait, avec quoi, pendant combien de temps, et ce que cela a coûté.",
    "Nothing to report yet" to "Rien à relever pour l’instant",
    "This conversation has not run anything yet: its ledger fills up as the agent works." to
        "Cette conversation n’a encore rien exécuté : son relevé se remplit à mesure que l’agent travaille.",

    // Les sections.
    "Work" to "Travail",
    "Subagents" to "Sous-agents",
    "Cost" to "Coût",

    // Les rangées.
    // « Origin » et non « Source » : la clé « Source » du catalogue partagé dit d'où vient une
    // conversation ; ici, la question est **qui a compté** le chiffre, et le mot de la rangée le dit.
    "Origin" to "Origine",
    "Working time" to "Temps de travail",
    "Answers without a duration" to "Réponses sans durée mesurée",
    "Calls that succeeded" to "Appels réussis",
    "Calls that failed" to "Appels échoués",
    "Still running" to "En cours",
    "Tool calls (server)" to "Appels d’outils (serveur)",
    "Launched" to "Lancés",
    "Tracked by the server" to "Suivis par le serveur",
    "Price" to "Prix",
    "Conversations without a price" to "Conversations sans prix",

    // Les valeurs d'une donnée absente.
    "not recorded by this thread" to "non consigné dans ce fil",
    "not measured" to "non mesuré",
    "not reported" to "non remonté",
    "not provided" to "non fourni",

    // La provenance des comptes.
    "the server's registry" to "le registre du serveur",
    "this phone" to "ce téléphone",
    "counted by the server's session registry" to "comptés par le registre des sessions du serveur",
    "counted by this phone, from the thread it kept" to "comptés par ce téléphone, d’après le fil qu’il garde",
    "the server's registry did not answer for this session" to
        "le registre du serveur n’a pas répondu pour cette session",
    "this conversation has no session on the server" to
        "cette conversation n’a pas de session sur le serveur",
    "counted one by one in the thread" to "comptés un par un dans le fil",
    "every launch was announced by the server" to "chaque lancement a été annoncé par le serveur",

    // Ce qui manque, dit au lieu d'être tu.
    "no answer of this thread carried a measured duration" to
        "aucune réponse de ce fil ne porte de durée mesurée",
    "this transport did not report any tokens for this thread" to
        "ce transport n’a remonté aucun jeton pour ce fil",
    "they are missing from the total above, which stays incomplete" to
        "elles manquent au total ci-dessus, qui reste incomplet",
    "the server's registry counts a different total for the same session" to
        "le registre du serveur compte un total différent pour la même session",
    "measured on one answer" to "mesuré sur une réponse",
    "measured on %1\$d answers" to "mesuré sur %1\$d réponses",
    "one answer stays outside the total above" to "une réponse reste hors du total ci-dessus",
    "%1\$d answers stay outside the total above" to "%1\$d réponses restent hors du total ci-dessus",
    "one call had not finished when this ledger was written" to
        "un appel n’était pas terminé quand ce relevé a été écrit",
    "%1\$d calls had not finished when this ledger was written" to
        "%1\$d appels n’étaient pas terminés quand ce relevé a été écrit",
    "one task was launched blind: this transport says nothing about its state" to
        "une tâche a été lancée à l’aveugle : ce transport ne dit rien de son état",
    "%1\$d were launched blind: this transport says nothing about their state" to
        "%1\$d ont été lancées à l’aveugle : ce transport ne dit rien de leur état",

    // Le prix : d'où il vient, et ce qu'on écrit quand il n'y en a pas.
    "cost from the server's registry" to "coût du registre du serveur",
    "estimated at the price the server publishes" to "estimation au prix publié par le serveur",
    "the price line the server publishes" to "la ligne de prix publiée par le serveur",
    "read on %1\$s" to "relevé le %1\$s",
    "neither the server's registry nor its price table gave a price" to
        "ni le registre du serveur ni sa table de prix n’ont donné de prix",
    "the server publishes no price for this model" to
        "le serveur ne publie aucun prix pour ce modèle",
    "the server's price table did not answer" to "la table de prix du serveur n’a pas répondu",
    "the server's price table does not list this model" to
        "la table de prix du serveur ne liste pas ce modèle",
    "no tokens were counted, so no cost could be estimated" to
        "aucun jeton n’a été compté : aucun coût n’a pu être estimé",

    // Les deux lectures du serveur, et ce que l'écran dit de chacune : sans ces entrées, les deux
    // lignes de provenance restaient **en anglais** au bas d'un écran français — vu en capture.
    "reading the server's session registry…" to "lecture du registre des sessions du serveur…",
    "reading the server's price table…" to "lecture de la table de prix du serveur…",
    "the server's session registry answered" to "le registre des sessions du serveur a répondu",
    "the server's price table answered" to "la table de prix du serveur a répondu",
    "the session registry could not be read (%1\$s) — the counts below are this phone's" to
        "le registre des sessions n’a pas pu être lu (%1\$s) — les comptes ci-dessous sont ceux de ce téléphone",
    "the price table could not be read (%1\$s) — no cost can be estimated without it" to
        "la table de prix n’a pas pu être lue (%1\$s) — aucun coût ne peut être estimé sans elle",
    "the Dashboard's credentials are missing on this phone" to
        "les identifiants du tableau de bord manquent sur ce téléphone",
    "%1\$d bots have no conversation on this phone yet." to
        "%1\$d bots n’ont encore aucune conversation sur ce téléphone.",

    // La portée du cumul d'un bot.
    "1 conversation" to "1 conversation",
    "%d conversations" to "%d conversations",
    "This bot has no conversation on this phone yet." to
        "Ce bot n’a encore aucune conversation sur ce téléphone.",
    "Compare the bots" to "Comparer les bots",
    "A bot’s total adds up the conversations this phone ties to it." to
        "Le total d’un bot additionne les conversations que ce téléphone lui rattache.",
)
