package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la rangée d'onglets de la plaque — quatre colonnes égales, un axe commun, une
 * pastille contenue et centrée.**
 *
 * Le propriétaire juge la propreté avant la beauté : « les boutons débordent sur la ligne, c'est
 * même pas centré ». Deux de ces reproches sont **géométriques**, et un chiffre ne se corrige pas
 * par une intention :
 *
 *  1. **les quatre repères ne se partagent pas la rangée à égalité** — une poignée posée *dans* la
 *     barre, une largeur écrite pour l'un des quatre, et les colonnes divergent (Chat collé à
 *     gauche, les autres serrés au centre) ;
 *  2. **la pastille de l'onglet actif ne tient pas dans sa rangée** — posée sur une cellule qui
 *     montait jusqu'au filet, elle le touche (relevé `+742` : **0 dp** de dégagement en haut,
 *     **11 dp** en bas — « pas centré »).
 *
 * Ce test relit la **même** table que l'écran ([PlaqueGeometry], [TabLabelFit]) et exige : la somme
 * des quatre colonnes, de leurs trois écarts et des deux marges **remplit** la rangée, sans reste ;
 * les colonnes sont **égales** et tombent sur une **marge unique** ; et la cellule de la pastille
 * laisse un **dégagement franc** au-dessus comme au-dessous — jamais un bord franchi.
 *
 * Le verrou **mord** : mettre `TabCellHeight` à 72 dp (dégagement 2 dp) le fait échouer en nommant
 * la valeur et sa cible — c'est la mutation rejouée au rapport du lot.
 */
class PlaqueOngletsVerrouTest {

    /** La largeur de l'écran de la maison, en dp — c'est sur elle que la planche est dessinée. */
    private val houseDp = 411f

    /** Le dégagement minimal exigé entre la pastille et chacun des deux bords de la rangée. */
    private val minClearance = 4.dp

    // -----------------------------------------------------------------------------------------
    // 1 · les quatre colonnes
    // -----------------------------------------------------------------------------------------

    /**
     * **La largeur intérieure de la rangée** : l'écran, moins les deux marges de la plaque, moins
     * le rembourrage horizontal de la rangée. C'est ce que les quatre colonnes et leurs trois
     * écarts doivent remplir exactement.
     */
    private fun rowInner(screenDp: Float): Float =
        screenDp - 2f * PlaqueGeometry.SideMargin.value - 2f * OnyxSpace.ControlGap.value

    /**
     * Les **quatre colonnes** d'une rangée de `count` repères, telles que l'écran les dessine :
     * `weight(1f)` sur chaque repère, la marge d'écran, le rembourrage de la rangée, puis un écart
     * de `ControlGap` entre chaque paire.
     *
     * Rend, pour chaque repère, son bord gauche et son bord droit — c'est cette liste que les
     * assertions comparent, et non un seul chiffre écrit à la main.
     */
    private fun columns(screenDp: Float, count: Int = ChatTab.drawn.size): List<ClosedFloatingPointRange<Float>> {
        val gap = OnyxSpace.ControlGap.value
        val rowLeft = PlaqueGeometry.SideMargin.value + gap
        val width = TabLabelFit.columnsDp(
            innerDp = rowInner(screenDp), count = count, handleDp = 0f, gapDp = gap,
        )
        return (0 until count).map { i ->
            val left = rowLeft + i * (width + gap)
            left..(left + width)
        }
    }

    @Test
    fun `les quatre colonnes sont strictement egales`() {
        val cols = columns(houseDp)
        assertEquals("la planche n'en dessine que quatre", 4, cols.size)
        val widths = cols.map { it.endInclusive - it.start }
        widths.forEachIndexed { i, w ->
            assertEquals(
                "la colonne $i doit valoir la première — « Chat collé à gauche » est un défaut, " +
                    "pas un style (relevé sur l'appareil : 89,9 / 89,5 / 89,5 / 89,5 dp)",
                widths[0],
                w,
                0.05f,
            )
        }
        assertTrue("une colonne doit rester large devant sa cible tactile", widths[0] >= 44f)
    }

    @Test
    fun `les colonnes remplissent la rangee, sans reste et sans element en trop`() {
        val cols = columns(houseDp)
        val gap = OnyxSpace.ControlGap.value
        val used = cols.sumOf { (it.endInclusive - it.start).toDouble() } + (cols.size - 1) * gap
        assertEquals(
            "les quatre colonnes et leurs trois écarts prennent TOUTE la largeur intérieure de la " +
                "rangée : un repère de plus (une poignée dans la barre, une largeur écrite) " +
                "laisserait un reste",
            rowInner(houseDp).toDouble(),
            used,
            0.05,
        )
    }

    @Test
    fun `les deux marges laterales sont identiques, et viennent de l'echelle`() {
        val cols = columns(houseDp)
        val leftMargin = cols.first().start
        val rightMargin = houseDp - cols.last().endInclusive
        assertEquals(
            "les deux bords de la rangée sont à la même distance — sinon les repères « glissent » " +
                "d'un côté (« Chat collé à gauche »)",
            leftMargin,
            rightMargin,
            0.05f,
        )
        assertEquals(
            "la marge unique de la maison, plus le rembourrage de la rangée",
            PlaqueGeometry.SideMargin.value + OnyxSpace.ControlGap.value,
            leftMargin,
            0.05f,
        )
        assertTrue("la marge vient de l'échelle", OnyxSpace.isOnScale(PlaqueGeometry.SideMargin))
    }

    @Test
    fun `l ecart entre deux reperes est le meme partout`() {
        val cols = columns(houseDp)
        cols.zipWithNext { a, b -> b.start - a.endInclusive }.forEach { g ->
            assertEquals(
                "un écart inégal, c'est « Flux et Fichiers serrés au centre »",
                OnyxSpace.ControlGap.value,
                g,
                0.05f,
            )
        }
    }

    @Test
    fun `les quatre reperes ont le meme axe vertical`() {
        // Les colonnes partagent la même ligne de base **par construction** : la cellule d'un
        // repère est la même pour les quatre ([PlaqueGeometry.TabCell]), et le libellé est le
        // dernier enfant de la colonne. Le verrou fige la moitié qui pourrait diverger : la
        // hauteur de cellule est **une seule valeur**, jamais une par repère.
        assertEquals(
            "une seule hauteur d'icône pour les quatre repères — la cible de la maison",
            OnyxSpace.ThumbnailSize,
            PlaqueGeometry.TabCell,
        )
        assertTrue(PlaqueGeometry.TabCell >= 44.dp)
    }

    // -----------------------------------------------------------------------------------------
    // 2 · la pastille : contenue, et centrée
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la pastille tient dans la rangee, avec un degagement franc en haut et en bas`() {
        assertTrue(
            "un dégagement nul ou négatif, c'est la pastille collée au filet — le défaut relevé",
            PlaqueGeometry.TabPillClearance >= minClearance,
        )
        assertTrue(
            "la cellule et ses deux dégagements doivent tenir dans la rangée",
            PlaqueGeometry.TabCellHeight + PlaqueGeometry.TabPillClearance * 2
                <= PlaqueGeometry.TabRowHeight,
        )
        assertTrue(
            "et la cellule doit porter l'icône et son écart avec du blanc à revendre",
            PlaqueGeometry.TabCellHeight.value
                >= PlaqueGeometry.TabCell.value + OnyxSpace.IconLabelGap.value,
        )
        assertEquals(
            "la cellule de la pastille est la hauteur de `.navi` dans `.navrow` (hcss.py:227-229)",
            60.dp,
            PlaqueGeometry.TabCellHeight,
        )
        assertEquals("le dégagement de la planche : (76 − 60) / 2", 8.dp, PlaqueGeometry.TabPillClearance)
    }

    @Test
    fun `la hauteur de la plaque ne bouge pas, la pastille tient DANS la rangee`() {
        // La pastille est plus basse que sa rangée : elle n'ajoute rien à la somme. La plaque
        // reste à 140 dp — 64 + 76.
        assertEquals(140.dp, PlaqueGeometry.Height)
        assertTrue(
            "être contenue, c'est ne pas allonger la plaque",
            PlaqueGeometry.TabCellHeight <= PlaqueGeometry.TabRowHeight,
        )
    }
}
