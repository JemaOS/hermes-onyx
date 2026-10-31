package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du compteur.** Il mord sur trois choses, et rien d'autre :
 *
 * 1. **la boîte du texte qui dépasse la boîte peinte** — la pastille doit laisser au moins
 *    [BadgeGeometry.PadVertical] au-dessus et au-dessous du chiffre et
 *    [BadgeGeometry.PadHorizontal] de chaque côté, pour un, deux et trois chiffres et aux deux
 *    échelles de police ;
 * 2. **la largeur fixe** — la largeur peinte doit **croître** avec le nombre de chiffres, et aucun
 *    site de compteur ne doit porter une largeur écrite à la main (ni le rembourrage vertical de
 *    1 dp qui était la cause du rognage) ;
 * 3. **la pastille qui touche le bord** — l'ancrage doit laisser une marge positive dans sa cellule,
 *    et aucune colonne d'onglet ne doit découper la pastille (l'ancien `.clip(capsule)` est refusé).
 *
 * Les messages d'échec **nomment le fichier et la ligne** concernés : un verrou qui dit seulement
 * « faux » ne dit pas où regarder.
 */
class BadgeGeometryTest {

    // ---------------------------------------------------------------------------------------------
    // Les boîtes de texte mesurées
    // ---------------------------------------------------------------------------------------------

    /**
     * **Les boîtes de texte réellement mesurées.** Un chiffre à `caption2` mesuré sur l'appareil à
     * 420 dpi : 16 px de large, 4 chiffres d'avance à 2,1 px — soit **0,56 em** par chiffre — et une
     * hauteur de ligne de `11 sp × 1,2 × échelle`.
     *
     * Ce sont les six cas que le propriétaire a demandés : **1 / 2 / 3 chiffres** × **police 1,0× /
     * 1,3×**. Les boîtes verticales sont exactes (le `lineHeight` du thème est une formule), les
     * largeurs viennent de l'avance mesurée ; le dessin, lui, ne s'en sert jamais — il mesure
     * (`CounterBadge`).
     */
    private val cases: List<Case> = listOf(
        Case("1 chiffre, 1,0×", digits = 1, fontScale = 1.0f, textWidthDp = 6.2f, textHeightDp = 13.2f),
        Case("2 chiffres, 1,0×", digits = 2, fontScale = 1.0f, textWidthDp = 12.3f, textHeightDp = 13.2f),
        Case("3 chiffres, 1,0×", digits = 3, fontScale = 1.0f, textWidthDp = 18.5f, textHeightDp = 13.2f),
        Case("1 chiffre, 1,3×", digits = 1, fontScale = 1.3f, textWidthDp = 8.0f, textHeightDp = 17.2f),
        Case("2 chiffres, 1,3×", digits = 2, fontScale = 1.3f, textWidthDp = 16.0f, textHeightDp = 17.2f),
        Case("3 chiffres, 1,3×", digits = 3, fontScale = 1.3f, textWidthDp = 24.0f, textHeightDp = 17.2f),
    )

    private data class Case(
        val label: String,
        val digits: Int,
        val fontScale: Float,
        val textWidthDp: Float,
        val textHeightDp: Float,
    )

    private val geometry = File("src/main/java/com/onyx/app/ui/design/BadgeGeometry.kt")
    private val badge = File("src/main/java/com/onyx/app/ui/design/CounterBadge.kt")
    private val bar = File("src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")

    /** Le numéro de la ligne où [needle] apparaît, ou 0 : c'est lui qui nomme le défaut. */
    private fun lineOf(file: File, needle: String): Int {
        val index = file.readLines().indexOfFirst { it.contains(needle) }
        return if (index < 0) 0 else index + 1
    }

    /** « fichier:ligne » — la forme que porte chaque message d'échec. */
    private fun where(file: File, needle: String): String = "${file.path}:${lineOf(file, needle)}"

    // ---------------------------------------------------------------------------------------------
    // 1. La boîte du texte ne dépasse jamais la boîte peinte
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la boite du texte tient dans la boite peinte, avec 2 dp de chaque cote`() {
        val report = StringBuilder()
        cases.forEach { case ->
            val paintedWidth = BadgeGeometry.paintedWidthDp(case.textWidthDp, case.textHeightDp)
            val paintedHeight = BadgeGeometry.paintedHeightDp(case.textHeightDp)
            val padX = BadgeGeometry.residualPadDp(paintedWidth, case.textWidthDp)
            val padY = BadgeGeometry.residualPadDp(paintedHeight, case.textHeightDp)
            report.append(
                "%-16s texte %.1f×%.1f → peint %.1f×%.1f · rembourrage %.2f / %.2f\n"
                    .format(case.label, case.textWidthDp, case.textHeightDp, paintedWidth, paintedHeight, padX, padY),
            )
            assertTrue(
                "la boîte du texte dépasse la boîte peinte (${case.label}) — voir " +
                    where(geometry, "val PadHorizontal") + " et " + where(geometry, "val PadVertical"),
                BadgeGeometry.fits(
                    paintedWidth, paintedHeight,
                    case.textWidthDp, case.textHeightDp,
                ),
            )
            assertTrue(
                "rembourrage horizontal sous 5 dp (${case.label}) — " + where(geometry, "val PadHorizontal"),
                padX >= BadgeGeometry.PadHorizontal.value - BadgeGeometry.TOLERANCE,
            )
            assertTrue(
                "rembourrage vertical sous 2 dp (${case.label}) — " + where(geometry, "val PadVertical"),
                padY >= BadgeGeometry.PadVertical.value - BadgeGeometry.TOLERANCE,
            )
            // La boîte peinte est *exactement* le texte plus deux rembourrages : rien n'est rogné.
            assertEquals(paintedHeight, case.textHeightDp + 2f * BadgeGeometry.PadVertical.value, 0.001f)
        }
        println(report)
    }

    // ---------------------------------------------------------------------------------------------
    // 2. La largeur est calculée — jamais fixe
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la largeur croit avec le nombre de chiffres et n'est jamais une constante`() {
        listOf(1.0f, 1.3f).forEach { scale ->
            val widths = (1..3).map { digits ->
                val textWidth = digits * BadgeGeometry.DigitAdvanceEm * BadgeGeometry.BodySp * scale
                val textHeight = BadgeGeometry.lineHeightDp(BadgeGeometry.BodySp, scale)
                BadgeGeometry.paintedWidthDp(textWidth, textHeight)
            }
            println("police ${scale}× → largeurs peintes 1/2/3 chiffres : $widths dp")
            assertTrue("1 → 2 chiffres n'élargit pas la pastille (police ${scale}×)", widths[1] > widths[0])
            assertTrue("2 → 3 chiffres n'élargit pas la pastille (police ${scale}×)", widths[2] > widths[1])
        }
        // La largeur fixe de 18 dp de l'ancien compteur de tuile est **trop courte dès deux
        // chiffres** : c'est le refus que porte [BadgeGeometry.reserveDp].
        assertTrue(
            "la largeur fixe de 18 dp tiendrait trois chiffres : la réserve doit être calculée",
            BadgeGeometry.reserveDp(3) > 18f,
        )
        assertEquals(0f, BadgeGeometry.reserveDp(0), 0f)
    }

    // ---------------------------------------------------------------------------------------------
    // 3. La hauteur est dérivée du corps du texte
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la hauteur vient du corps du texte, pas d'une valeur ecrite en dur`() {
        val small = BadgeGeometry.paintedHeightDp(BadgeGeometry.lineHeightDp(BadgeGeometry.BodySp, 1.0f))
        val large = BadgeGeometry.paintedHeightDp(BadgeGeometry.lineHeightDp(BadgeGeometry.BodySp, 1.3f))
        println("hauteur peinte : 1,0× = %.2f dp · 1,3× = %.2f dp".format(small, large))
        assertTrue("la police 1,3× ne donne pas une pastille plus haute", large > small)
        assertEquals(BadgeGeometry.lineHeightDp(BadgeGeometry.BodySp, 1.3f) + 2f * BadgeGeometry.PadVertical.value, large, 0.001f)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. L'ancrage garde la pastille entière dans sa cellule
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la pastille reste entiere dans sa cellule, ancree en dedans`() {
        val cell = PlaqueGeometry.TabCell.value
        cases.forEach { case ->
            val paintedWidth = BadgeGeometry.paintedWidthDp(case.textWidthDp, case.textHeightDp)
            val paintedHeight = BadgeGeometry.paintedHeightDp(case.textHeightDp)
            val margin = BadgeGeometry.marginToCellDp(cell, paintedWidth, paintedHeight)
            println("%-16s pastille %.1f×%.1f dans %.0f dp → marge %.1f dp".format(case.label, paintedWidth, paintedHeight, cell, margin))
            assertTrue(
                "la pastille sort de sa cellule (${case.label}) — ancrage à revoir : " +
                    where(geometry, "val AnchorInset"),
                margin >= 0f,
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 5. La morsure : la géométrie d'avant est refusée
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `le verrou mord sur la geometrie d'avant`() {
        // Ce que la barre faisait : rembourrage vertical de 1 dp, largeur de 18 dp, et un ancrage
        // sorti de la cellule (`offset(x = +7.dp, y = (-4).dp)`).
        val textWidth = 18.5f
        val textHeight = 13.2f
        val oldPaintedWidth = 18f
        val oldPaintedHeight = textHeight + 2f * 1f
        assertFalse(
            "le verrou laisse passer la largeur fixe de 18 dp",
            BadgeGeometry.fits(oldPaintedWidth, oldPaintedHeight, textWidth, textHeight),
        )
        assertTrue(
            "le rembourrage vertical de 1 dp passe le seuil de 2 dp",
            BadgeGeometry.residualPadDp(oldPaintedHeight, textHeight) < BadgeGeometry.PadVertical.value,
        )
        // L'ancrage d'avant sortait du coin : la marge est négative, donc le verrou le refuse.
        val cell = PlaqueGeometry.TabCell.value
        val oldWidthDp = BadgeGeometry.paintedWidthDp(textWidth, textHeight)
        val oldHeightDp = BadgeGeometry.paintedHeightDp(textHeight, padVerticalDp = 1f)
        assertTrue(
            "l'ancien ancrage (x = +7 dp, y = -4 dp) ne sort pas de la cellule : le verrou ne mord plus",
            BadgeGeometry.marginToCellDp(cell, oldWidthDp, oldHeightDp, insetDp = -7f) < 0f &&
                BadgeGeometry.marginToCellDp(cell, oldWidthDp, oldHeightDp, insetDp = -4f) < 0f,
        )
        // Et l'ancrage en vigueur, lui, laisse une marge positive pour tous les cas mesurés.
        val margin = BadgeGeometry.marginToCellDp(
            cell,
            BadgeGeometry.paintedWidthDp(24f, 17.2f),
            BadgeGeometry.paintedHeightDp(17.2f),
        )
        assertEquals(4f, margin, 0.05f)
        // Et la colonne d'onglet d'avant découpait la pastille : la source ne doit plus porter ce
        // découpage sur la colonne (`.weight(1f)` immédiatement suivi d'un `.clip(`).
        val column = bar.readText()
        assertFalse(
            "la colonne d'onglet découpe encore ses enfants — " + where(bar, ".weight(1f)"),
            Regex("""weight\(1f\)\s*\n\s*\.clip\(""").containsMatchIn(column),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 6. Les sources : ce qui doit être là, et ce qui ne doit plus y être
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `le compteur emploie la geometrie et aucune largeur ecrite a la main`() {
        val text = badge.readText()
        assertTrue(
            "le compteur n'emploie plus la largeur calculée — " + where(badge, "paintedWidthDp"),
            text.contains("BadgeGeometry.paintedWidthDp"),
        )
        assertTrue(
            "le compteur n'emploie plus la hauteur calculée — " + where(badge, "defaultMinSize"),
            text.contains("defaultMinSize(minWidth = paintedWidthDp.dp, minHeight = paintedHeightDp.dp)"),
        )
        assertTrue(
            "le compteur n'emploie plus le rembourrage de la maison — " + where(badge, "BadgeGeometry.PadVertical"),
            text.contains("vertical = BadgeGeometry.PadVertical"),
        )
        // Aucune largeur ni hauteur **littérale** sur un compteur : c'est la définition du défaut.
        val literal = Regex("""(minWidth|minHeight|width|height|size)\s*=\s*\d+(\.\d+)?\.dp""")
        val offenders = text.lineSequence().withIndex()
            .filter { literal.containsMatchIn(it.value) }
            .map { "${badge.path}:${it.index + 1} : ${it.value.trim()}" }
            .toList()
        assertTrue("largeur/hauteur écrite à la main dans le compteur : $offenders", offenders.isEmpty())

        val geometryText = geometry.readText()
        assertTrue(
            "le rembourrage vertical est redescendu sous 2 dp — " + where(geometry, "val PadVertical"),
            geometryText.contains("val PadVertical: Dp = 2.dp"),
        )
        assertTrue(
            "l'ancrage n'est plus en dedans de la cellule — " + where(geometry, "val AnchorInset"),
            geometryText.contains("val AnchorInset: Dp = OnyxSpace.Hair"),
        )
    }

    @Test
    fun `la barre d'onglets porte le compteur de la maison, ancré en dedans`() {
        val text = bar.readText()
        assertTrue(
            "la barre ne porte plus le compteur de la maison — " + where(bar, "CounterBadge("),
            text.contains("CounterBadge(") && text.contains("count = count"),
        )
        assertTrue(
            "l'ancrage de la pastille n'est plus rentré dans la cellule — " + where(bar, "AnchorInset"),
            text.contains(".offset(x = -BadgeGeometry.AnchorInset, y = BadgeGeometry.AnchorInset)"),
        )
        assertFalse(
            "l'ancien ancrage qui sortait de la cellule est revenu — " + where(bar, ".offset("),
            text.contains(".offset(x = 7.dp, y = (-4).dp)"),
        )
        assertFalse(
            "un rembourrage vertical codé à la main est revenu dans la barre — " + where(bar, "CounterBadge("),
            text.contains("vertical = 1.dp"),
        )
    }
}
