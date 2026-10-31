package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les **deux** glyphes du plein écran, relevés dans le vectoriel — pas dans une capture.
 *
 * Le bouton de plein écran de l'écran « Ordinateur » montrait `arrow.up.left.and.arrow.down.right`
 * — les deux flèches en diagonale qui disent « agrandir un bloc » sur une réponse — dans les deux
 * états, à un détail près : le glyphe ne représentait pas l'action. Il fallait deux **quatre
 * coins** : qui s'écartent pour entrer, qui se resserrent pour sortir.
 *
 * Ce test tient la grille du jeu d'icônes (24 dp, trait 2, bouts arrondis, marge optique) et la
 * **différence** entre les deux états : même cadre, coins à l'extérieur pour l'un, ramenés au
 * centre pour l'autre, et les bras du second atteignent exactement les lignes où le premier pose ses
 * coins.
 */
class FullScreenGlyphTest {

    private fun drawable(name: String): String {
        val path = "src/main/res/drawable/$name"
        return listOf(File(path), File("core/$path")).firstOrNull { it.exists() }?.readText()
            ?: error("introuvable : $path")
    }

    private val enter = drawable("ic_sym_fullscreen_enter.xml")
    private val exit = drawable("ic_sym_fullscreen_exit.xml")

    private fun pathDatas(xml: String): List<String> =
        Regex("android:pathData=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList()

    private fun nums(path: String): List<Double> =
        Regex("-?\\d+(?:\\.\\d+)?").findAll(path).map { it.value.toDouble() }.toList()

    /** Les points **posés** par un tracé (extrémités de `M`/`L`/`Q`), pas ses points de contrôle. */
    private fun anchors(path: String): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        Regex("([MLQ])([^MLQZ]*)").findAll(path).forEach { m ->
            val n = nums(m.groupValues[2])
            val last = n.size - 2
            if (last >= 0) out += Pair(n[last], n[last + 1])
        }
        return out
    }

    /** Le coin d'un équerre : le point du milieu (les deux autres sont les bouts des bras). */
    private fun corner(bracket: List<Pair<Double, Double>>) = bracket[1]

    private fun brackets(xml: String): List<List<Pair<Double, Double>>> =
        pathDatas(xml).map(::anchors).onEach { assertEquals("une équerre a trois points", 3, it.size) }

    private fun distance(point: Pair<Double, Double>) = hypot(point.first - 12.0, point.second - 12.0)

    @Test
    fun `les deux glyphes suivent la grille du jeu`() {
        for (xml in listOf(enter, exit)) {
            assertTrue("24 dp", xml.contains("android:width=\"24dp\"") && xml.contains("android:height=\"24dp\""))
            assertTrue("grille de 24", xml.contains("android:viewportWidth=\"24\"") && xml.contains("android:viewportHeight=\"24\""))
            assertEquals("une seule épaisseur de trait", listOf("2", "2", "2", "2"), Regex("android:strokeWidth=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList())
            assertEquals("des bouts arrondis partout", 4, Regex("android:strokeLineCap=\"round\"").findAll(xml).count())
            assertEquals("des jointures arrondies partout", 4, Regex("android:strokeLineJoin=\"round\"").findAll(xml).count())
            assertEquals("aucun aplat", 4, Regex("android:fillColor=\"@android:color/transparent\"").findAll(xml).count())
            // Quatre équerres : les quatre coins.
            assertEquals("quatre coins", 4, brackets(xml).size)
        }
    }

    @Test
    fun `le plein ecran entrant ecarte les quatre coins au bord du cadre`() {
        val corners = brackets(enter).map(::corner)
        // Les quatre coins sont aux quatre coins : même distance au centre, signes opposés.
        val radius = distance(corners.first())
        assertEquals("les quatre coins sont à la même distance du centre", 1, corners.map { "%.3f".format(distance(it)) }.toSet().size)
        assertEquals("le coin est au bord : 5,5", hypot(7.0, 7.0), radius, 0.001)
        // Le tracé reste dans la marge du jeu : 5 ± 1 de demi-trait = 4 … 20.
        val painted = brackets(enter).flatten()
        assertEquals(4.0, painted.minOf { it.first } - 1.0, 0.001)
        assertEquals(20.0, painted.maxOf { it.first } + 1.0, 0.001)
        assertEquals("centré optiquement : la marge est la même des deux côtés", 24.0, painted.minOf { it.first } + painted.maxOf { it.first }, 0.001)
        // Les bras rentrent vers le milieu : de (5,5) vers (10,5) et vers (5,10).
        val first = brackets(enter).first()
        assertEquals(5.0, corner(first).first, 0.001)
        assertEquals(5.0, corner(first).second, 0.001)
        assertTrue("un bras va vers le centre en x", first.any { it == 10.0 to 5.0 })
        assertTrue("un bras va vers le centre en y", first.any { it == 5.0 to 10.0 })
        // Le glyphe est symétrique sous une rotation d'un demi-tour : aucun coin ne penche.
        val rotated = painted.map { (24.0 - it.first) to (24.0 - it.second) }
        assertEquals(painted.toSet(), rotated.toSet())
    }

    @Test
    fun `le plein ecran sortant resserre les quatre coins au milieu`() {
        val corners = brackets(exit).map(::corner)
        val radius = distance(corners.first())
        assertEquals("les quatre coins sont à la même distance du centre", 1, corners.map { "%.3f".format(distance(it)) }.toSet().size)
        // Ramenés au milieu : (9,9) au lieu de (5,5) — trois unités de chaque côté.
        assertEquals(hypot(3.0, 3.0), radius, 0.001)
        assertEquals("le coin est ramené au milieu : 9,9", 9.0, corners.first().first, 0.001)
        // Et les bras atteignent exactement les lignes où l'autre pose ses coins : 5 et 19.
        val painted = brackets(exit).flatten()
        assertEquals("les bras atteignent le bord du cadre (5 − 1 de demi-trait)", 4.0, painted.minOf { it.first } - 1.0, 0.001)
        assertEquals(20.0, painted.maxOf { it.first } + 1.0, 0.001)
        val rotated = painted.map { (24.0 - it.first) to (24.0 - it.second) }
        assertEquals(painted.toSet(), rotated.toSet())
    }

    @Test
    fun `les deux etats ne montrent pas le meme dessin, et disent bien l'inverse l'un de l'autre`() {
        assertNotEquals("deux glyphes distincts", enter, exit)
        val out = brackets(enter).map(::corner)
        val inside = brackets(exit).map(::corner)
        // Pour chaque coin : celui de « sortir » est plus près du milieu que celui de « entrer ».
        val outwards = out.map(::distance).sorted()
        val inwards = inside.map(::distance).sorted()
        assertTrue("« entrer » doit écarter les coins", outwards.zip(inwards).all { (a, b) -> a > b + 1.0 })
        // Les coins des deux états sont sur les **mêmes diagonales** : un dessin se déduit de
        // l'autre, il n'y a pas deux géométries à tenir.
        fun diagonals(points: List<Pair<Double, Double>>) =
            points.map { p ->
                val x = if (p.first - 12.0 < 0) -1 else 1
                val y = if (p.second - 12.0 < 0) -1 else 1
                "$x$y"
            }.sorted()
        assertEquals(diagonals(out), diagonals(inside))
    }
}
