package com.onyx.app.localization

/**
 * **Les libellés qui manquaient en français.**
 *
 * L'app écrit ses chaînes **en anglais** dans le code et les traduit par le dictionnaire. Une chaîne
 * absente du dictionnaire s'affiche **en anglais**, même dans une interface française — c'est ce
 * qui est arrivé sur l'écran d'édition d'un bot : « WHAT IT DOES » et « NAME » sous des titres
 * français, parce que leur chemin de traduction ne passait pas.
 *
 * **Ici, des MOTIFS et non des clés.** Une chaîne dont la valeur dépend d'un paramètre (« Version
 * 1.0.0+1123 », « Priority Haute ») ne peut pas être une clé du dictionnaire : à l'exécution
 * l'interpolation est déjà faite, la clé reçue est « Version 1.0.0+1123 », et un gabarit
 * « Version $version » ne la trouverait jamais. Un motif, lui, voit la **forme finale** : il reprend
 * la partie variable par un groupe capturé, sans jamais la réécrire.
 *
 * C'est le mécanisme qu'emploient déjà les autres motifs du dictionnaire (« 3 of 5 chosen » →
 * « 3 sur 5 choisis »).
 *
 * **Les motifs passent après le dictionnaire**, jamais avant : une chaîne à libellé fixe qui a sa
 * propre entrée (« Delete this server? », « Delete this session? ») est donc résolue par elle, et
 * ces motifs ne peuvent pas l'attraper.
 */
internal val onyxMissingFrenchPatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    // La version de l'app, dans « What's New ».
    Regex("^Version (.+)$") to { match -> "Version ${match.groupValues[1]}" },
    // La priorité d'une carte de kanban.
    Regex("^Priority (.+)$") to { match -> "Priorité ${match.groupValues[1]}" },
    // Le modèle d'un préfixe prononcé : « Model → hermes-agent ».
    Regex("^Model → (.+)$") to { match -> "Modèle → ${match.groupValues[1]}" },
    // La suppression d'un bot, dont le nom est inséré. **Le motif exclut les guillemets** : la forme
    // « Delete “Rapport du lundi”? » a son propre motif dans le dictionnaire, qui met les guillemets
    // français — celui-ci ne doit pas l'attraper.
    Regex("^Delete ([^\u201c]+)\\?$") to { match -> "Supprimer ${match.groupValues[1]} ?" },
    // La périodicité d'une planification : « Every 2 hours ».
    Regex("^Every (\\d+) hours?$") to { match ->
        val heures = match.groupValues[1]
        if (heures == "1") "Toutes les heures" else "Toutes les $heures heures"
    },
)
