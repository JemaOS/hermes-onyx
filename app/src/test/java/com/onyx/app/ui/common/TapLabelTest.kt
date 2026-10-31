package com.onyx.app.ui.common

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des libellés.** Une zone cliquable doit porter **son** nom.
 *
 * Le relevé d'arbre qui a ouvert ce lot : dans le volet, **18 nœuds cliquables** n'avaient ni texte
 * ni description — les cellules de tuiles comprises, dont le libellé vivait dans un **nœud enfant**
 * (une icône, ou le texte de la cellule). Un lecteur d'écran y annonçait une zone muette, et rien
 * dans le code ne le disait.
 *
 * Trois règles, et elles se lisent dans la source — donc elles valent pour un écran écrit demain :
 *
 * 1. **une zone cliquable dont le contenu est une icône qui porte un libellé** doit porter ce
 *    libellé **elle-même** (`tapLabel` / `semanticsLabel`) ;
 * 2. **le volet** — l'écran du relevé — n'a plus **aucune** zone cliquable sans nom ;
 * 3. à l'inverse, une icône **décorative** reste **muette** : quand la zone porte le nom, l'icône ne
 *    le redit pas, sinon le même bouton s'annonce deux fois.
 */
class TapLabelTest {

    private val main = File("src/main/java")

    /** Ce qui, dans une chaîne de modificateurs, donne un nom à la zone. */
    private val nameMarkers = listOf(
        "tapLabel(", "semanticsLabel(", "contentDescription =",
        "mergeDescendants = true", "clearAndSetSemantics", "stateDescription =",
    )

    private val tappables = listOf(
        "onyxClickable(", "onyxTappable(", ".clickable(", "combinedClickable(", "toggleable(", "selectable(",
    )

    /** Le début d'un appel de composable qui porte du texte : le contrôle a déjà sa voix. */
    private val textCall = Regex("""\b(BasicText|Text|SelectableText|MarkdownText)\s*\(""")

    private val iconWithLabel = Regex("""OnyxIcon\([^)]*contentDescription\s*=\s*"[^"]+""", RegexOption.DOT_MATCHES_ALL)

    private val chainStep = Regex("""\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)\s*(\(?)""")

    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()

    /** La source **privée de ses commentaires** : un libellé cité n'est pas un libellé posé. */
    private fun source(f: File): String =
        f.readText().replace(Regex("""//[^\n]*"""), "").replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")

    private fun lineOf(src: String, offset: Int): Int = src.substring(0, offset).count { it == '\n' } + 1

    /** La fin de la chaîne de modificateurs qui **commence** à l'appel repéré. */
    private fun chainEnd(src: String, from: Int): Int {
        val open = src.indexOf('(', from)
        if (open < 0) return from
        var depth = 0
        var i = open
        while (i < src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return endOfChain(src, i + 1)
                }
            }
            i++
        }
        return src.length
    }

    /** `… .padding(…)` continue la chaîne ; tout autre texte l'arrête. */
    private fun endOfChain(src: String, from: Int): Int {
        var end = from
        while (true) {
            val m = chainStep.find(src, end) ?: return end
            if (src.substring(end, m.range.first).isNotBlank()) return end
            end = if (m.groupValues[2].isEmpty()) {
                m.range.last + 1
            } else {
                // L'appel d'un modificateur : on saute son bloc de parenthèses.
                var depth = 0
                var i = m.range.last
                while (i < src.length) {
                    when (src[i]) {
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                    i++
                }
                i + 1
            }
        }
    }

    /** Parcourt les zones cliquables d'un fichier et rend celles que [predicate] désigne. */
    private fun scan(f: File, predicate: (String, String) -> Boolean): List<String> {
        val src = source(f)
        val out = mutableListOf<String>()
        tappables.forEach { marker ->
            Regex(Regex.escape(marker)).findAll(src).forEach { m ->
                val end = chainEnd(src, m.range.first)
                val chain = src.substring(m.range.first, end)
                val body = src.substring(end, minOf(src.length, end + 1200))
                if (predicate(chain, body)) out += "${f.path.removePrefix(main.path + "/")}:${lineOf(src, m.range.first)}"
            }
        }
        return out
    }

    @Test
    fun `aucune zone cliquable ne laisse son libelle sur l icone`() {
        val bad = kotlinFiles(main).flatMap { f ->
            scan(f) { chain, body ->
                nameMarkers.none { chain.contains(it) } &&
                    !textCall.containsMatchIn(body) &&
                    iconWithLabel.containsMatchIn(body)
            }
        }
        assertTrue(
            "Ces zones cliquables n'ont pas de nom : le libellé est posé sur l'icône, dans un nœud " +
                "enfant, et l'arbre rend la zone muette. Poser `tapLabel(...)` sur la zone et " +
                "laisser l'icône décorative.\n" + bad.joinToString("\n") { "  $it" },
            bad.isEmpty(),
        )
    }

    @Test
    fun `le volet ne contient plus aucune zone cliquable sans nom`() {
        // Le volet est le paquet de l'écran relevé : la règle y est plus stricte encore — **toutes**
        // les zones cliquables portent un nom, pas seulement celles à icône.
        val sidebar = File("src/main/java/com/onyx/app/ui/sidebar")
        val bad = kotlinFiles(sidebar).flatMap { f ->
            scan(f) { chain, _ -> nameMarkers.none { chain.contains(it) } }
        }
        assertTrue(
            "Ces zones cliquables du volet n'ont pas de nom (relevé d'arbre : 18 en avaient, dont " +
                "les cellules de tuiles) :\n" + bad.joinToString("\n") { "  $it" },
            bad.isEmpty(),
        )
    }

    @Test
    fun `une icone decorative reste muette et ne redit pas le nom de la zone`() {
        val bad = kotlinFiles(main).flatMap { f ->
            scan(f) { chain, body -> chain.contains("tapLabel(") && iconWithLabel.containsMatchIn(body) }
        }
        assertTrue(
            "La zone porte déjà son nom : l'icône doit rester décorative " +
                "(`contentDescription = null`), sinon le contrôle s'annonce deux fois.\n" +
                bad.joinToString("\n") { "  $it" },
            bad.isEmpty(),
        )
    }
}
