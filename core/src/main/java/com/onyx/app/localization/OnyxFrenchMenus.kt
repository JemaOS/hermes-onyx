package com.onyx.app.localization

/**
 * Les libellés des **menus**, **feuilles d'action** et **puces** qui n'avaient pas encore d'entrée
 * française : le balayage des menus de l'app n'en a trouvé que ceux-ci (le reste passait déjà par
 * un des autres catalogues).
 *
 * Le cas signalé — « Move to Archived » dans le menu d'une carte Kanban — n'est **pas** ici : le
 * libellé se compose (le nom de la colonne s'insère), il est donc produit par
 * [onyxMenuSheetFrenchPatterns] plutôt que par une entrée exacte.
 *
 * Généré à la main : les clés sont les chaînes anglaises écrites dans le code, jamais renommées.
 */
internal val onyxMenuSheetFrench: Map<String, String> = linkedMapOf(
    // La feuille d'action d'une formule (appui long sur un bloc KaTeX).
    "Math" to "Maths",
    "Copy LaTeX" to "Copier le LaTeX",
    // Le bloc d'un diagramme : sa puce de type, la bascule source/diagramme, et les libellés que
    // l'accessibilité annonce.
    "DIAGRAM" to "DIAGRAMME",
    "Diagram" to "Diagramme",
    "Mermaid diagram" to "Diagramme Mermaid",
    "Open diagram full screen" to "Ouvrir le diagramme en plein écran",
    // Le titre de l'alerte quand une action sur un projet n'a pas abouti.
    "Couldn't do it" to "Impossible de le faire",
    // La feuille qui monte du bas pour joindre quelque chose au message : son titre, et les
    // **vraies** limites d'Onyx en sous-titre (les poids sont insérés par `AttachmentLimits`).
    "Add to message" to "Ajouter au message",
    "Up to %1\$s attachments · %2\$s in all · %3\$s per file · %4\$s for a text file" to
        "Jusqu’à %1\$s pièces jointes · %2\$s au total · %3\$s par fichier · %4\$s pour un fichier texte",
    // Ce qu'un ajout refusé dit : le nom, le poids mesuré, et le plafond — les chiffres sont ceux
    // du code (`AttachmentLimits`), jamais une recopie.
    "%1\$s is %2\$s, over the %3\$s limit: it was not attached." to
        "« %1\$s » fait %2\$s, au-delà de la limite de %3\$s : il n’a pas été joint.",
    "%1\$s is %2\$s: a text file goes into the message up to %3\$s." to
        "« %1\$s » fait %2\$s : un fichier texte entre dans le message jusqu’à %3\$s.",
    "%1\$s is %2\$s: a message is limited to %3\$s in all." to
        "« %1\$s » fait %2\$s : un message est limité à %3\$s au total.",
    // La feuille des actions d'une conversation (`ChatActionSheet`) : le nouveau départ, ses
    // deux questions, et les trois listes que le serveur alimente.
    //
    // Les libellés déjà traduits ailleurs (Jetons, Entrée, Sortie, Coût, Arrêter…) ne sont **pas**
    // répétés ici : une clé en double écrase l'existante, et le dictionnaire des menus refuse ses
    // entrées mortes.
    "Start fresh?" to "Repartir de zéro ?",
    "A new conversation starts with the same bot, with a fresh context. The one you are in right now stays in your history, whole." to
        "Une nouvelle conversation démarre avec le même bot, le contexte à neuf. Celle dans laquelle vous êtes en ce moment reste dans l'historique, entière.",
    "Background processes" to "Processus en arrière-plan",
    "Restore points" to "Points de restauration",
    "Copy session id" to "Copier l'identifiant de session",
    // Le détail de la consommation et du coût.
    "Cache share" to "Part du cache",
    "Context used" to "Contexte utilisé",
    "What is filling the window" to "Ce qui remplit la fenêtre",
    "Reading the counters…" to "Lecture des compteurs…",
    "This server would not give the counters of this conversation." to
        "Ce serveur n'a pas donné la consommation de cette conversation.",
    "This server gives no token count for this conversation yet." to
        "Ce serveur ne donne encore aucun compte de jetons pour cette conversation.",
    "This server gives no price for this conversation; the counters above are all it has." to
        "Ce serveur ne donne aucun prix pour cette conversation ; les compteurs ci-dessus sont tout ce qu'il a.",
    "Costs are the server's own estimate from provider pricing." to
        "Le coût est l'estimation du serveur, d'après les tarifs du fournisseur.",
    "The window is estimated from the text; the server did not give the exact count." to
        "La fenêtre est estimée d'après le texte ; le serveur n'a pas donné le compte exact.",
    "Everything said here is read again with each message: this is what is filling the window." to
        "Tout ce qui s'est dit ici est relu à chaque message : voici ce qui remplit la fenêtre.",
    // Les processus en arrière-plan.
    "Reading what is running…" to "Lecture de ce qui tourne…",
    "This server would not give the background processes of this conversation." to
        "Ce serveur n'a pas donné les processus en arrière-plan de cette conversation.",
    "Nothing is running in the background for this conversation." to
        "Rien ne tourne en arrière-plan pour cette conversation.",
    "Process stopped" to "Processus arrêté",
    // Les points de restauration.
    "Reading the restore points…" to "Lecture des points de restauration…",
    "This server would not give the restore points of this conversation." to
        "Ce serveur n'a pas donné les points de restauration de cette conversation.",
    "This server does not keep restore points for this folder." to
        "Ce serveur ne garde pas de point de restauration pour ce dossier.",
    "No restore point for this conversation yet." to
        "Aucun point de restauration pour cette conversation pour le moment.",
    "Restore" to "Restaurer",
    "Restored" to "Restauré",
    // La copie de l'identifiant de session.
    "this conversation has no session id yet" to "Cette conversation n'a pas encore d'identifiant de session",
    // Le retour du panneau à la feuille des actions : la ligne est **dans** la feuille, pour que le
    // retour soit visible et pas seulement matériel.
    "Back to the actions" to "Retour aux actions",
    // Les refus du serveur, dits tels quels plutôt que par un échec muet.
    "the server answered nothing" to "le serveur n'a rien répondu",
    "this process is no longer running" to "ce processus ne tourne plus",
    "the server refused to stop it" to "le serveur a refusé de l'arrêter",
    "the server refused to restore it" to "le serveur a refusé de le restaurer",
    // La **feuille de compte du volet** (`AccountSheet`) : son titre d'état, sa ligne mesurée et
    // ses cinq rangées. Le titre vient de la mesure (« joignable » / « muette ») ; la ligne porte
    // la latence relevée ou le compte à rebours de la prochaine tentative réelle.
    "Offline" to "Hors ligne",
    "Refresh conversations" to "Actualiser les conversations",
    "Refreshing the conversations…" to "Actualisation des conversations…",
    "Conversations refreshed · %1\$d" to "Conversations actualisées · %1\$d",
    "Couldn't refresh the conversations." to "Impossible d'actualiser les conversations.",
    "Retry the connection now" to "Réessayer la connexion maintenant",
    "Disconnect" to "Se déconnecter",
    "Disconnect from this gateway?" to "Se déconnecter de cette passerelle ?",
    "Onyx goes back to its setup screen. The address and keys stay in its settings, and nothing is removed from the server." to
        "Onyx revient à son écran de configuration. L'adresse et les clés restent dans ses réglages, et rien n'est supprimé du serveur.",
    "Change or add a gateway" to "Changer ou ajouter une passerelle",
    // La **consommation réelle** (`ChatActionSheet`, n° 9) : les motifs d'un échec de lecture, **en
    // clair** — le gateway refuse l'identifiant du registre en « session not found », et ce code brut
    // s'affichait tel quel sous une phrase française.
    "the server gave no reason" to "le serveur n'a pas donné de raison",
    "the server no longer holds this conversation's live session" to
        "Le serveur ne tient plus la session vivante de cette conversation.",
    "this server does not offer this reading" to "Ce serveur n'offre pas cette lecture.",
    "the server refused to read it: %1\$s" to "Le serveur a refusé la lecture : %1\$s",
    "Usage and its lists are only available while the conversation is running." to
        "La consommation et ses listes ne sont disponibles que pour une conversation en cours.",
    "Retry the reading" to "Relire",
    // ————— Le menu d'une carte de bot : les neuf entrées reprises de la référence —————
    // Les mots qui existent déjà ailleurs (« Hide », « Edit », « Delete », « Unpin », « Start
    // fresh ») ne sont **pas** répétés ici : une clé en double écrase l'existante, et le
    // dictionnaire des menus refuse ses entrées mortes.
    "Pin to top" to "Épingler en haut",
    "Unhide" to "Afficher",
    "Open recent session" to "Ouvrir la dernière session",
    // Le quatrième mot **porte le nom du bot** : la clé est un gabarit, composé par
    // `OnyxLocalization.format` (comme les autres libellés à trous du dictionnaire).
    "New chat with %1\$s" to "Nouvelle conversation avec %1\$s",
    "Routines" to "Routines",
    "Duplicate" to "Dupliquer",
    // La marque d'épingle d'une carte, que l'accessibilité annonce (l'entrée, elle, dit « Épingler
    // en haut » — « Pinned » tout court est déjà pris par un titre de section, au pluriel).
    "Pinned to top" to "Épinglé en haut",
    // Le sous-titre d'une carte de bot sans phrase et sans aperçu, comme la référence : la carte ne
    // reste jamais vide sous son nom.
    "Say hello" to "Dis bonjour",
    // Ce que la copie d'un bot devient : son titre porte « (copie) », comme « (copy) » à la source.
    "%1\$s (copy)" to "%1\$s (copie)",
    "No free name for the copy." to "Aucun nom libre pour la copie.",
    "Couldn't copy %1\$s. %2\$s" to "Impossible de copier %1\$s. %2\$s",
    "Couldn't start %1\$s over. %2\$s" to "Impossible de repartir à zéro avec %1\$s. %2\$s",
    // La confirmation de « Supprimer » : ce qui part, et ce qui reste.
    "Delete %1\$s?" to "Supprimer %1\$s ?",
    "This removes the bot from your server. Its conversations stay in your history on this phone." to
        "Cela supprime le bot de votre serveur. Ses conversations restent dans l'historique de ce téléphone.",
)

/**
 * Les libellés **composés** des menus : le nom de la colonne Kanban s'insère dans « Move to … », et
 * ce nom se traduit à part (la capsule du haut de l'écran affiche le même mot — « Archivé »).
 *
 * Deux gabarits, dans cet ordre : la question de la confirmation (« Move to Archived? ») garde son
 * « ? » final, là où l'entrée du menu ne l'a pas. Une entrée **exacte** du dictionnaire reste
 * prioritaire sur ces gabarits — « Move to Project » garde donc « Déplacer vers un projet ».
 */
internal val onyxMenuSheetFrenchPatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    Regex("^Move to (.+)\\?$") to { match ->
        "Déplacer vers « ${OnyxLocalization.translate(match.groupValues[1], java.util.Locale.FRANCE)} » ?"
    },
    Regex("^Move to (.+)$") to { match ->
        "Déplacer vers « ${OnyxLocalization.translate(match.groupValues[1], java.util.Locale.FRANCE)} »"
    },
)
