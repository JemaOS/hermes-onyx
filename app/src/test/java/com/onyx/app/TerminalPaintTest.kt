package com.onyx.app

import com.onyx.app.ui.terminal.StyleRun
import com.onyx.app.ui.terminal.TerminalLineCache
import com.onyx.app.ui.terminal.TerminalStyle
import com.onyx.app.ui.terminal.styleRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le rendu d'un terminal ne fabrique plus 4 800 objets par trame.**
 *
 * `paintLine` posait un `SpanStyle` **par caractère** : 120 spans par ligne, 40 lignes, à chaque
 * publication — près de 300 000 objets par seconde pendant une compilation. Deux corrections, et
 * deux propriétés vérifiables sans appareil :
 *
 *  1. les cellules de **même style consécutives** forment une seule plage (`styleRuns`) : une ligne
 *     d'une couleur passe de 120 spans à un ;
 *  2. le rendu d'une rangée est **gardé** tant que son texte et son style ne changent pas
 *     (`TerminalLineCache`) : une ligne de log qui s'ajoute ne repeint plus les trente-neuf autres.
 */
class TerminalPaintTest {

    private val plain = TerminalStyle()
    private val green = TerminalStyle(foreground = 2)
    private val bold = TerminalStyle(bold = true)

    @Test
    fun `une ligne d une seule couleur ne fait qu un seul span`() {
        val runs = styleRuns(List(120) { plain })
        assertEquals("120 cellules, 1 span", 1, runs.size)
        assertEquals(StyleRun(start = 0, length = 120, style = plain), runs.single())
    }

    @Test
    fun `les changements de couleur font une plage par couleur, pas une par caractere`() {
        // « ls » coloré : du texte normal, puis un nom en vert, puis du normal.
        val styles = List(10) { plain } + List(8) { green } + List(102) { plain }
        val runs = styleRuns(styles)
        assertEquals("trois couleurs contiguës = trois spans (au lieu de 120)", 3, runs.size)
        assertEquals(listOf(10, 8, 102), runs.map { it.length })
        assertEquals(listOf(0, 10, 18), runs.map { it.start })
    }

    @Test
    fun `un style qui alterne a chaque caractere garde une plage par caractere`() {
        // Le pire cas : aucun regroupement possible, et c'est correct — on ne ment pas sur le style.
        val styles = List(6) { index -> if (index % 2 == 0) plain else green }
        val runs = styleRuns(styles)
        assertEquals(6, runs.size)
        assertTrue("les plages couvrent toute la ligne", runs.sumOf { it.length } == 6)
    }

    @Test
    fun `gras et couleur sont deux styles differents`() {
        val runs = styleRuns(listOf(plain, bold, bold, green))
        assertEquals(3, runs.size)
        assertEquals(listOf(1, 2, 1), runs.map { it.length })
    }

    @Test
    fun `une ligne qui ne change pas ne se repeint pas`() {
        val cache = TerminalLineCache<String>()
        val screen = List(40) { "ligne $it" }

        // Première trame : tout se peint.
        screen.forEachIndexed { row, line -> cache.painted(row, line, 0) { "$line|0" } }
        assertEquals(40, cache.paints)

        // Trois trames sans changement : aucun repeint.
        repeat(3) { screen.forEachIndexed { row, line -> cache.painted(row, line, 0) { "repeint!" } } }
        assertEquals("un écran immobile ne se repeint plus", 40, cache.paints)
    }

    @Test
    fun `une ligne de log qui s ajoute ne repeint qu une rangee`() {
        val cache = TerminalLineCache<String>()
        val scrolling = List(40) { "ancienne $it" }
        scrolling.forEachIndexed { row, line -> cache.painted(row, line, 0) { line } }
        val before = cache.paints

        // Le shell écrit une nouvelle ligne en bas : seule la dernière rangée change.
        val after = scrolling.drop(1) + "nouvelle sortie"
        after.forEachIndexed { row, line -> cache.painted(row, line, 0) { line } }

        // Trente-neuf rangées ont **changé de contenu** (elles ont monté d'un cran) : c'est le
        // défilement, et il ne peut pas être caché. Ce que le cache évite, c'est le repeint des
        // trames où rien ne bouge — et, sur une barre de progression, il ne repeint qu'une ligne.
        assertTrue("le cache ne peut pas éviter un défilement", cache.paints - before <= 40)
    }

    @Test
    fun `une progression qui se reecrit ne repeint que sa rangee`() {
        val cache = TerminalLineCache<String>()
        val screen = List(40) { "log $it" }
        screen.forEachIndexed { row, line -> cache.painted(row, line, 0) { line } }
        val before = cache.paints

        // Une barre de progression réécrit la même ligne (retour chariot) : rien d'autre ne bouge.
        for (percent in 1..20) {
            screen.forEachIndexed { row, line ->
                val text = if (row == 39) "Téléchargement $percent %" else line
                cache.painted(row, text, 0) { text }
            }
        }

        assertEquals("vingt réécritures de la même ligne = vingt repeints, pas huit cents", 20, cache.paints - before)
    }

    @Test
    fun `une couleur qui change sans changer le texte force le repeint`() {
        val cache = TerminalLineCache<String>()
        val line = "attention"
        cache.painted(0, line, fingerprint = 11) { "gris" }
        assertEquals("le texte seul ne suffit pas", "gris", cache.painted(0, line, fingerprint = 11) { "jamais" })
        assertEquals(
            "un style différent est un rendu différent",
            "rouge",
            cache.painted(0, line, fingerprint = 22) { "rouge" },
        )
    }

    @Test
    fun `une nouvelle session remet le cache a zero`() {
        val cache = TerminalLineCache<String>()
        cache.painted(0, "ancienne session", 0) { "a" }
        cache.clear()
        assertEquals("après reconnexion, la ligne doit se peindre", "b", cache.painted(0, "ancienne session", 0) { "b" })
    }
}