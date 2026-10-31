package com.onyx.app.ui.design

/**
 * L'ellipse de la maison : **un seul caractère**, celui du projet (`…`), jamais trois points.
 * Le même signe que celui des libellés de tuiles et des comptes, pour qu'une coupure se lise
 * partout de la même façon.
 */
internal const val ONYX_ELLIPSIS = "\u2026"

/**
 * Un libellé **coupé à une frontière de mot**, jamais en plein mot.
 *
 * C'est la règle de la maison (charte : « troncature : **jamais au milieu d'un mot** »), et elle
 * manquait à la ligne des feuilles : `TextOverflow.Ellipsis` seul coupe au dernier caractère qui
 * tient, si bien que « Réessayer la connexion **maint…** » et « Changer ou ajouter une **passer…** »
 * (feuille de compte à police 1,3×) rognaient le dernier mot en deux — mesuré à 411 dp.
 *
 * [measure] rend la largeur d'un texte à sa taille réelle ; [available] est la largeur que le texte
 * a vraiment. La fonction :
 *
 * 1. range le texte en lignes **aux espaces** (repli glouton) ;
 * 2. si tout tient en [maxLines] lignes, **rend le texte inchangé** — aucune ellipse n'est posée
 *    sur un libellé qui n'a pas besoin d'être coupé ;
 * 3. sinon, garde les [maxLines] premières lignes et **retire des mots entiers** de la dernière
 *    jusqu'à ce que l'ellipse y entre : la ligne se termine alors par un mot entier suivi de `…`.
 *
 * Un mot plus large que [available] n'est **jamais** cassé : il tient sa ligne, et le moteur de
 * texte posera son ellipse s'il le faut (`measure` ne peut pas le rendre plus étroit).
 *
 * Publique parce que la **feuille de compte** (module `app`) coupe son motif d'état avec la même
 * règle que les lignes de la famille, qui vivent ici.
 */
fun fitWords(
    text: String,
    measure: (String) -> Float,
    available: Float,
    maxLines: Int = 1,
): String {
    if (text.isEmpty() || available <= 0f || maxLines <= 0) return text
    val words = text.trim().split(SPACES).filter { it.isNotEmpty() }
    if (words.isEmpty()) return text

    // 1. Le repli glouton, mot à mot : chaque ligne est un groupe de mots entiers.
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (current.isNotEmpty() && measure(candidate) > available) {
            lines += current
            current = word
        } else {
            current = candidate
        }
    }
    if (current.isNotEmpty()) lines += current

    // 2. Tout tient : on ne touche à rien, aucune ellipse.
    if (lines.size <= maxLines) return text

    // 3. On coupe : la dernière ligne gardée perd des mots entiers jusqu'à ce que `…` y entre.
    val kept = lines.take(maxLines).toMutableList()
    val lastWords = kept.last().split(" ").toMutableList()
    while (lastWords.isNotEmpty() && measure(lastWords.joinToString(" ") + " " + ONYX_ELLIPSIS) > available) {
        lastWords.removeAt(lastWords.size - 1)
    }
    kept[kept.size - 1] = if (lastWords.isEmpty()) {
        // Même le dernier mot seul ne laisse pas la place à l'ellipse : on garde la ligne telle
        // qu'elle était (elle tient déjà) — jamais un mot coupé, quitte à renoncer à l'ellipse.
        kept.last()
    } else {
        lastWords.joinToString(" ") + " " + ONYX_ELLIPSIS
    }
    return kept.joinToString("\n")
}

private val SPACES = Regex("\\s+")
