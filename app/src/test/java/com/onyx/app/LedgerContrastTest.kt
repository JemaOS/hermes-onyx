package com.onyx.app

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.ledger.ledgerLabelInk
import com.onyx.app.ui.ledger.ledgerNoteInk
import com.onyx.app.ui.ledger.ledgerValueInk
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le relevé d'agent, mesuré** : les seuils WCAG 2.1 AA sur les thèmes et les deux apparences —
 * 4,5:1 pour un texte.
 *
 * L'écran est un écran de **données** : les libellés, les valeurs et leurs notes sont du texte, et
 * c'est tout ce qu'il y a à lire. Les encres passent par `ledgerLabelInk` / `ledgerValueInk` /
 * `ledgerNoteInk`, qui écartent la teinte du fond juste assez pour tenir le seuil — ce test mesure
 * donc **ce que l'écran compose vraiment**, pas une intention. Le tableau complet part dans la
 * sortie du test : c'est la preuve du lot.
 *
 * Une valeur qui dit une absence (« non fourni ») est **reculée** par rapport à une valeur présente
 * — mais jamais sous le seuil : une donnée manquante reste une donnée à lire.
 */
class LedgerContrastTest {

    private fun theme(theme: OnyxTheme, dark: Boolean) =
        ResolvedTheme.resolve(theme, dark = dark, fonts = OnyxFonts.System)

    private data class Pair(val what: String, val ink: Color, val ground: Color, val min: Float)

    private fun pairs(theme: OnyxTheme, dark: Boolean): List<Pair> {
        val t = theme(theme, dark)
        val page = t.formPageColor
        val surface = t.formSurfaceColor
        val text = OnyxContrast.TEXT_MIN
        return listOf(
            Pair("libellé de rangée · carte", ledgerLabelInk(t, surface), surface, text),
            Pair("libellé de section · page", ledgerLabelInk(t, page), page, text),
            Pair("valeur d'une donnée · carte", ledgerValueInk(t, surface, absent = false), surface, text),
            Pair("valeur d'une absence · carte", ledgerValueInk(t, surface, absent = true), surface, text),
            Pair("note de provenance · carte", ledgerNoteInk(t, surface), surface, text),
            Pair("note de provenance · page", ledgerNoteInk(t, page), page, text),
            Pair("sous-titre · page", ledgerNoteInk(t, page), page, text),
        )
    }

    @Test
    fun `tous les textes du releve tiennent 4,5 - 1, sur tous les themes et les deux apparences`() {
        val report = StringBuilder()
        report.append("relevé · thème · apparence · ce qui se lit · seuil · lu\n")
        var failures = 0
        var measured = 0
        for (theme in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                for (pair in pairs(theme, dark)) {
                    val ratio = OnyxContrast.ratioOver(pair.ink, pair.ground)
                    val ok = ratio + 1e-4f >= pair.min
                    measured++
                    if (!ok) failures++
                    report.append(
                        "${theme.id} · ${if (dark) "sombre" else "clair"} · ${pair.what} · " +
                            "${"%.1f".format(pair.min)} · ${"%.2f".format(ratio)} ${if (ok) "ok" else "SOUS LE SEUIL"}\n",
                    )
                }
            }
        }
        println(report)
        assertTrue("des couples mesurés attendus", measured >= 7 * 7 * 2)
        assertTrue(
            "des couples passent sous leur seuil :\n" +
                report.lineSequence().filter { it.contains("SOUS LE SEUIL") }.joinToString("\n"),
            failures == 0,
        )
    }
}
