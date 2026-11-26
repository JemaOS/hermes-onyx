package com.onyx.app.ui.code

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux débordements de l'écran **Code** : la quatrième puce de filtre coupée au bord droit, et
 * la puce de branche du compositeur tronquée.
 *
 * Ce test lit le câblage réel : les deux rangées **mesurent** leurs libellés (le même `TextMeasurer`
 * que la rangée d'onglets déjà corrigée), **réduisent** la taille, et ne défilent **que** si rien ne
 * tient — jamais une ellipse au milieu d'un mot. Les mesures elles-mêmes sont éprouvées dans
 * `:core` (`ChipRowFitTest`, avec la police livrée dans l'app) ; ici, c'est le câblage qui est tenu.
 */
class CodeChipsFitTest {

    private val ui = File("src/main/java/com/onyx/app/ui")
    private val screen = File(ui, "code/CodeScreen.kt").readText()

    // MARK: - La rangée de filtres : les quatre puces tiennent

    @Test
    fun `la rangee de filtres mesure ses libelles au lieu de defiler`() {
        val filters = screen.substringAfter("private fun FilterRow(").substringBefore("/**\n * Une carte")
        assertTrue("elle mesure sous la police courante", filters.contains("rememberTextMeasurer()"))
        assertTrue("elle demande le plan", filters.contains("ChipRowFit.plan("))
        assertTrue("avec la place réellement offerte", filters.contains("availableDp = available"))
        // Le dégagement et l'écart sont ceux de la mesure, pas un chiffre écrit deux fois.
        assertTrue(filters.contains("chipInsetDp = FILTER_CHIP_INSET_DP"))
        assertTrue(filters.contains("gapDp = FILTER_CHIP_GAP_DP"))
        // La taille et les compteurs viennent du plan.
        assertTrue(filters.contains("fontSize = plan.fontSizeSp.sp"))
        assertTrue(filters.contains("showCounts = plan.showCounts"))
        // Et le défilement n'existe que si **rien** ne tient : plus de rangée défilable par défaut.
        assertTrue(filters.contains("if (plan.fits) Modifier else Modifier.horizontalScroll(rememberScrollState())"))
        // Un seul `horizontalScroll` dans toute la rangée : celui du repli, jamais un par défaut.
        assertTrue(
            "la rangée ne défile plus d'office",
            filters.split("horizontalScroll").size - 1 == 1,
        )
    }

    @Test
    fun `les quatre filtres gardent leur nom entier et leur compteur quand la place le permet`() {
        // Le plan garde les compteurs ; il ne les lâche qu'en dernier recours (testé dans `:core`).
        assertTrue(screen.contains("counts[filter] ?: 0,"))
        assertTrue(screen.contains("showCounts = plan.showCounts,"))
        // Aucune ellipse sur un libellé de filtre : ni coupe au hasard, ni « … ».
        val filters = screen.substringAfter("private fun FilterRow(").substringBefore("/**\n * Une carte")
        assertFalse("pas d'ellipse sur un filtre", filters.contains("TextOverflow.Ellipsis"))
        assertTrue("le texte garde sa largeur naturelle", filters.contains("softWrap = false"))
    }

    @Test
    fun `la quatrieme puce a de la place pour son bord`() {
        // Le plan réserve l'écart entre les puces et le dégagement de chacune : une puce entière,
        // pas un fragment de bord. Les quatre filtres sont tous rendus, sans exception.
        assertTrue(screen.contains("CodeFilter.entries"))
        assertTrue(screen.contains("filters.forEach { filter ->"))
        assertTrue("le dégagement d'une puce", screen.contains("private const val FILTER_CHIP_INSET_DP = 24f"))
        assertTrue("l'écart entre deux puces", screen.contains("private const val FILTER_CHIP_GAP_DP = 8f"))
    }

    // MARK: - Les puces du compositeur : aucun libellé tronqué

    @Test
    fun `les puces du compositeur mesurent, reduisent, puis abregent proprement`() {
        val composer = screen.substringAfter("private fun CodeComposer(").substringBefore("/**\n * Une puce du compositeur")
        assertTrue("elles mesurent", composer.contains("rememberTextMeasurer()"))
        assertTrue("elles demandent le plan de la ligne", composer.contains("ChipRowFit.labelPlan("))
        // Trois leviers, dans l'ordre : la taille, l'abréviation aux frontières, et les puces sobres.
        assertTrue("la taille", composer.contains("baseSp = base,"))
        assertTrue("le plancher est 11 sp **déclarés** — jamais divisé par l'échelle de police", composer.contains("floorSp = ChipRowFit.FLOOR_SP.coerceAtMost(base)"))
        assertFalse("un plancher divisé par fontScale retombe sous 11 sp", composer.contains("/ density.fontScale)"))
        assertTrue("les puces sobres en dernier", composer.contains("insetsDp = listOf(COMPOSER_CHIP_INSET_DP, COMPOSER_PLAIN_INSET_DP)"))
        assertTrue("elles ne défilent que si rien ne tient", composer.contains("if (plan.fits) Modifier else Modifier.horizontalScroll(rememberScrollState())"))
        assertTrue("un seul repli, pas un défilement par défaut", composer.split("horizontalScroll").size - 1 == 1)
    }

    @Test
    fun `une ellipse ne tombe jamais au milieu d un mot`() {
        // Aucune ellipse sur les puces : les libellés sont entiers, abrégés aux frontières de
        // segment, ou la ligne défile — jamais de « … » posé au milieu d'un mot.
        val chips = screen.substringAfter("private fun Chip(").substringBefore("/** Le détail d'une session")
        assertFalse("pas d'ellipse sur une puce", chips.contains("TextOverflow.Ellipsis"))
        assertTrue("le libellé garde ses caractères", chips.contains("overflow = TextOverflow.Clip,"))
        // Les ornements tombent avant qu'un mot ne se coupe.
        assertTrue("l'icône s'efface en puce sobre", chips.contains("if (!plain) OnyxIcon(symbol = symbol"))
        assertTrue("le chevron aussi", chips.contains("if (!plain) OnyxIcon(symbol = Symbols.ChevronDown"))
    }

    @Test
    fun `le compositeur annonce ses deux degagements`() {
        assertTrue("la puce entière", screen.contains("private const val COMPOSER_CHIP_INSET_DP = 52f"))
        assertTrue("la puce sobre", screen.contains("private const val COMPOSER_PLAIN_INSET_DP = 20f"))
    }
}
