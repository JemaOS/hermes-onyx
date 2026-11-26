package com.onyx.app.perf

import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.components.hasMarkup
import com.onyx.app.ui.localization.resolveOnyxText
import com.onyx.app.ui.rich.splitMarkdown
import org.junit.Test

/**
 * **Le coût du chemin de texte d'un message — le dictionnaire, pas le rendu.**
 *
 * `BasicText` (le texte de Compose de la maison) traduit chaque chaîne qu'on lui donne :
 * `resolveOnyxText` → `OnyxLocalization.translate`, qui interroge le dictionnaire **puis essaie une
 * centaine d'expressions régulières ancrées**. Pour un libellé de quinze caractères, c'est le prix
 * d'un `HashMap` ; pour le corps d'une réponse — plusieurs milliers de caractères — c'est un balayage
 * complet, refait à chaque composition.
 *
 * Ce test mesure les deux, plus les deux autres analyses qu'une rangée paie à l'entrée : la
 * détection de balisage et le découpage Markdown. Il **n'assertionne aucun temps** (même raison que
 * `RowWorkCostTest` : un seuil sur une JVM de CI échouerait au premier build à froid) ; les chiffres
 * se lisent dans `build/test-results/…/TextPathCostTest.xml`, balise `system-out`.
 */
class TextPathCostTest {

    /** Une réponse d'agent de ~15 000 caractères : la forme du relevé « beaucoup d'infos ». */
    private val prose: String = buildString {
        repeat(24) { index ->
            appendLine("## Section $index")
            appendLine()
            repeat(6) { line ->
                appendLine(
                    "Le poste $line de la section $index coûte cher parce que la rangée relit son " +
                        "contenu à chaque composition ; c'est **mesuré** et `documenté`, sans " +
                        "supposer que la liste garde ses rangées hors écran.",
                )
            }
            appendLine()
        }
    }

    private fun microsPerCall(label: String, iterations: Int = 200, block: () -> Unit) {
        repeat(3) { block() } // échauffement : JIT, caches de hash, constantes de regex
        val start = System.nanoTime()
        repeat(iterations) { block() }
        val elapsed = System.nanoTime() - start
        println(String.format("%-46s %8.1f µs/appel (%d appels, %d car.)", label,
            elapsed / 1000.0 / iterations, iterations, prose.length))
    }

    @Test
    fun `cout du chemin de texte`() {
        println("--- texte de ${prose.length} caractères ---")
        microsPerCall("translate(prose) — chemin du dictionnaire") {
            OnyxLocalization.translate(prose)
        }
        microsPerCall("resolveOnyxText(prose, localize = true)") {
            resolveOnyxText(prose, localize = true)
        }
        microsPerCall("resolveOnyxText(prose, localize = false)") {
            resolveOnyxText(prose, localize = false)
        }
        microsPerCall("hasMarkup(prose)") { hasMarkup(prose) }
        microsPerCall("splitMarkdown(prose)") { splitMarkdown(prose) }
        microsPerCall("translate(\"Réglages\") — un libellé") {
            OnyxLocalization.translate("Réglages")
        }
        println("--- fin ---")
    }
}
