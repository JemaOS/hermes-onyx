package com.onyx.app.localization

/**
 * Le vocabulaire de l'écran **Code** : la rangée de filtres, la carte d'une session, le
 * compositeur, et ce qui dit noir sur blanc ce qui est tenu sur ce téléphone.
 *
 * Clés neuves uniquement : aucune n'existe dans les autres catalogues (une clé en double écraserait
 * une traduction déjà en place — « Chats » reste le titre de section « Discussions », alors que
 * l'onglet de la rangée dit « Chat », plus court).
 */
val onyxCodeFrench: Map<String, String> = linkedMapOf(
    // L'onglet de la rangée d'accueil : « Discussions » raccourci en « Chat ».
    "Chat" to "Chat",

    // Les quatre filtres, avec leur compteur (« Tout 2 »).
    "Everything" to "Tout",
    "Yours" to "À toi",

    // La carte d'une session : ce qu'elle est, et où elle est tenue.
    "Code sessions" to "Sessions de code",
    "Code session" to "Session de code",
    "Held on this phone" to "Tenue sur ce téléphone",
    "The server keeps the repository, its worktrees and its branches; this card keeps the task, its branch and its state, and follows the real turn for the status."
        to "Le serveur tient le dépôt, ses copies de travail et ses branches ; cette fiche garde la tâche, sa branche et son état, et suit le tour réel pour le statut.",

    // L'état vide : il explique comment démarrer, jamais un écran blanc.
    "No code session yet" to "Aucune session de code pour le moment",
    "Pick a repository below, describe a change, then send: each message starts its own code session."
        to "Choisissez un dépôt ci-dessous, décrivez un changement, puis envoyez : chaque message démarre sa propre session de code.",
    "No session matches this filter." to "Aucune session ne correspond à ce filtre.",

    // Le compositeur et ses puces.
    "Describe a change…" to "Décris un changement…",
    "Repository" to "Dépôt",
    "Worktree" to "Copie de travail",
    "New worktree" to "Nouveau worktree",
    "None yet" to "Aucune pour le moment",
    "Starting the session…" to "Démarrage de la session…",
    "The session couldn't start: %1\$s" to "La session n’a pas pu démarrer : %1\$s",

    // Le dépôt et la branche d'une session, dans le détail.
    "Branch" to "Branche",
    "Started" to "Démarrée",

    // Les actions d'une fiche : renommer, modifier, relancer, supprimer. « Rename »,
    // « Rename session », « Save », « Cancel » et « Delete » viennent déjà du catalogue commun ;
    // ici, seules les clés neuves, sinon une clé en double écraserait sa traduction.
    "Edit…" to "Modifier…",
    "Run again" to "Relancer",
    "Edit session" to "Modifier la session",
    "Request" to "Demande",
    "The task's title. The branch keeps its name: it is the one in the repository."
        to "Le titre de la tâche. La branche garde son nom : c’est celle du dépôt.",
    "The card is kept on this phone; the server keeps the repository, its worktrees and its branches."
        to "La fiche est gardée sur ce téléphone ; le serveur tient le dépôt, ses copies de travail et ses branches.",
    "Delete this session?" to "Supprimer cette session ?",
    "The card is removed from this phone. The conversation stays in your history."
        to "La fiche est retirée de ce téléphone. La conversation reste dans l’historique.",
)

/**
 * Les tournures avec un nombre : « il y a 3 s », « il y a 2 min ».
 *
 * Un nombre et une durée se formatent **avec** leur langue ; la quantité seule est produite par
 * `codeElapsed`, la tournure est posée ici.
 */
val onyxCodeFrenchPatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    Regex("^(.+) ago$") to { match -> "il y a ${match.groupValues[1]}" },
)

/** Le retour français → anglais de ce catalogue, pour que la bascule de langue reste complète. */
val onyxCodeFrenchToEnglish: Map<String, String> = linkedMapOf(
    "Tenue sur ce téléphone" to "Held on this phone",
    "Aucune session de code pour le moment" to "No code session yet",
    "Aucune session ne correspond à ce filtre." to "No session matches this filter.",
    "Décris un changement…" to "Describe a change…",
    "Dépôt" to "Repository",
    "Copie de travail" to "Worktree",
    "Nouveau worktree" to "New worktree",
    "Démarrage de la session…" to "Starting the session…",
    "Modifier…" to "Edit…",
    "Relancer" to "Run again",
    "Modifier la session" to "Edit session",
    "Demande" to "Request",
    "Le titre de la tâche. La branche garde son nom : c’est celle du dépôt." to
        "The task's title. The branch keeps its name: it is the one in the repository.",
    "La fiche est gardée sur ce téléphone ; le serveur tient le dépôt, ses copies de travail et ses branches." to
        "The card is kept on this phone; the server keeps the repository, its worktrees and its branches.",
    "Supprimer cette session ?" to "Delete this session?",
    "La fiche est retirée de ce téléphone. La conversation reste dans l’historique." to
        "The card is removed from this phone. The conversation stays in your history.",
)
