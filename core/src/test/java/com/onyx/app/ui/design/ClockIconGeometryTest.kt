package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La géométrie des deux horloges, relevée dans le vectoriel — pas dans une capture.
 *
 * Le cadran de la référence dessine **l'aiguille des heures sur 9 et celle des minutes sur 12**,
 * et sa variante à pastille pose un **disque plein avec le « ? » évidé**, séparé du bord du cadran.
 * Le portage avait gardé un tracé à la main : aiguilles en « 7 » (12 h et 4 h), cadran décalé d'une
 * unité à gauche, et un « ? » (crochet + point) qui **traversait le bord** — les deux traits se
 * rejoignaient en une tache pleine (la « plaque ») et le cadran paraissait cassé dans le coin.
 */
class ClockIconGeometryTest {
    private fun drawable(name: String): String {
        val path = "src/main/res/drawable/$name"
        return listOf(File(path), File("core/$path")).firstOrNull { it.exists() }?.readText()
            ?: error("introuvable : $path")
    }

    private fun pathDatas(xml: String): List<String> =
        Regex("android:pathData=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList()

    private fun strokes(xml: String): List<String> =
        Regex("android:strokeWidth=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList()

    /** Les couples de coordonnées d'un tracé, dans l'ordre du chemin. */
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

    /** Un cadran centré, au trait du jeu d'icônes (2 unités sur la grille de 24, bouts arrondis). */
    @Test
    fun `le cadran est rond centre et dessine a la meme epaisseur que le reste du jeu`() {
        val clock = drawable("ic_sym_clock.xml")
        val circle = pathDatas(clock).first()
        // `M12,3 a9,9 0 1 0 0,18 a9,9 0 1 0 0,-18 Z` : centre (12, 12), rayon 9.
        assertTrue("cadran : $circle", circle.startsWith("M12,3 a9,9 0 1 0 0,18"))
        assertEquals("centre x", 12.0, nums(circle)[0], 0.001)
        assertEquals("sommet", 3.0, nums(circle)[1], 0.001)
        assertEquals("rayon", 9.0, nums(circle)[2], 0.001)
        // 9 + 1 (demi-trait) = 10 : il reste deux unités de marge, comme les autres symboles du jeu.
        assertTrue("marge optique", 12.0 - (9.0 + 1.0) >= 1.5)
        assertEquals("une seule épaisseur de trait", listOf("2", "2"), strokes(clock))
    }

    /** Les deux aiguilles se lisent : heures sur 9 (à gauche du centre), minutes sur 12 (en haut). */
    @Test
    fun `les aiguilles des deux horloges pointent sur 9 et sur 12`() {
        val clock = pathDatas(drawable("ic_sym_clock.xml"))
        assertEquals("aiguilles (horloge)", "M6.69,12 L12,12 L12,5.65", clock[1])
        val badge = pathDatas(drawable("ic_sym_clock_badge_questionmark.xml"))
        assertEquals("aiguilles (pastille)", "M6.51,12.00 L11.06,12.00 L11.06,6.57", badge[1])

        listOf(clock[1] to 12.0, badge[1] to 11.06).forEach { (hands, centreX) ->
            val n = nums(hands)
            val hourTipX = n[0]
            val minuteTipY = n[5]
            assertTrue("l'aiguille des heures descend à gauche du centre ($hands)", hourTipX < centreX - 3)
            assertTrue("l'aiguille des minutes monte au-dessus du centre ($hands)", minuteTipY < 12.0 - 3)
            // Les deux aiguilles d'une horloge sont de longueurs différentes : heures plus courte.
            val hour = centreX - hourTipX
            val minute = 12.0 - minuteTipY
            assertTrue("l'aiguille des heures est plus courte que celle des minutes", hour < minute)
        }
    }

    /**
     * La pastille : un disque **plein** au « ? » **évidé** (`evenOdd`), et un bord de cadran
     * **interrompu** — le disque ne touche jamais le trait (c'était la « plaque » d'avant).
     */
    @Test
    fun `la pastille est un disque evide detache du bord du cadran`() {
        val xml = drawable("ic_sym_clock_badge_questionmark.xml")
        val paths = pathDatas(xml)
        assertEquals("trois tracés : bord, aiguilles, pastille", 3, paths.size)
        assertTrue("le bord est un arc, pas un cercle fermé", !paths[0].contains("Z"))
        assertTrue("le « ? » s'évide dans le disque", xml.contains("android:fillType=\"evenOdd\""))
        assertEquals("une seule épaisseur de trait", listOf("2", "2"), strokes(xml))

        // Le bord du cadran : arc de rayon 7,7 autour de (11,06 ; 12), de 69,9° à -9,1°.
        val arc = nums(paths[0])
        val p1 = Pair(arc[0], arc[1])
        val radius = arc[2]
        val p2 = Pair(arc[arc.size - 2], arc[arc.size - 1])
        val centre = arcCentre(p1, p2, radius)
        assertEquals("centre du cadran", 11.06, centre.first, 0.01)
        assertEquals("centre du cadran", 12.0, centre.second, 0.01)

        // La pastille : le disque est le premier sous-tracé du chemin plein.
        val disc = discOf(paths[2])
        assertTrue("la pastille tient dans le cadre", disc.second < 4.2)

        // Aucun point du bord peint n'entre dans le disque : le demi-trait (1) reste dehors.
        val a1 = angleAt(centre, p1)
        val a2 = angleAt(centre, p2)
        val span = if (a2 <= a1) a2 + 360.0 - a1 else a2 - a1
        var worst = Double.MAX_VALUE
        for (i in 0..400) {
            val theta = Math.toRadians(a1 + span * i / 400.0)
            val x = centre.first + radius * cos(theta)
            val y = centre.second + radius * sin(theta)
            worst = minOf(worst, hypot(x - disc.first.first, y - disc.first.second) - disc.second)
        }
        assertTrue("le bord du cadran traverse la pastille (écart $worst)", worst > 1.25)
    }

    /** Les artefacts de l'ancien tracé ne reviennent pas. */
    @Test
    fun `l ancien dessin n est plus la`() {
        val old = listOf("M11,2.5", "A9.5,9.5", "C19.5,15.5", "m-0.8,0", "V12 L15,14", "V12.5 L15.5,15")
        listOf("ic_sym_clock.xml", "ic_sym_clock_badge_questionmark.xml").forEach { name ->
            val xml = drawable(name)
            old.forEach { artefact ->
                assertFalse("$name porte encore « $artefact »", xml.contains(artefact))
            }
        }
    }

    // ---- petite géométrie, pour lire les tracés ----

    private fun angleAt(centre: Pair<Double, Double>, p: Pair<Double, Double>): Double =
        Math.toDegrees(atan2(p.second - centre.second, p.first - centre.first))

    /** Centre d'un arc SVG donné par ses extrémités (spécification F.6.5, cercle : rx = ry). */
    private fun arcCentre(p1: Pair<Double, Double>, p2: Pair<Double, Double>, r: Double): Pair<Double, Double> {
        val dx = (p1.first - p2.first) / 2.0
        val dy = (p1.second - p2.second) / 2.0
        val lambda = (dx * dx + dy * dy) / (r * r)
        val rr = if (lambda > 1) r * sqrt(lambda) else r
        // `large-arc = sweep = 1` : le signe de la spécification vaut -1.
        val num = max(0.0, rr * rr * rr * rr - rr * rr * dy * dy - rr * rr * dx * dx)
        val den = rr * rr * dy * dy + rr * rr * dx * dx
        val coef = if (den == 0.0) 0.0 else -sqrt(num / den)
        return Pair(
            coef * dy + (p1.first + p2.first) / 2.0,
            -coef * dx + (p1.second + p2.second) / 2.0,
        )
    }

    /** Centre et rayon du premier sous-tracé d'un chemin : le disque de la pastille. */
    private fun discOf(path: String): Pair<Pair<Double, Double>, Double> {
        val pts = anchors(path.substringBefore("Z") + "Z")
        val xs = pts.map { it.first }
        val ys = pts.map { it.second }
        val width = xs.max() - xs.min()
        val height = ys.max() - ys.min()
        assertTrue("la pastille n'est pas ronde (${abs(width - height)})", abs(width - height) < 0.1)
        return Pair(Pair((xs.min() + xs.max()) / 2.0, (ys.min() + ys.max()) / 2.0), (width + height) / 4.0)
    }
}
