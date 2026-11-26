package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La géométrie du dossier et du dossier « + », relevée **dans le vectoriel** — pas dans une capture.
 *
 * La section Projets montrait une **icône pleine** (le dossier d'une rangée de projet) juste
 * au-dessus d'une **icône filaire** (la rangée « Nouveau projet »), de deux tailles voisines : le
 * plein, dont l'encre tenait dans 18 × 12,5 et dont les coins étaient **carrés**, paraissait plus
 * gros que le contour, dont l'encre faisait 20 × 15,5 pour un trait de 2. Et le « + » de ce
 * contour était **collé au coin inférieur droit** : son encre mordait celle du dossier de 0,5 à
 * 1,0 unité (deux tracés qui se croisent), à la place d'une marge.
 *
 * Les quatre dessins partagent donc **une seule silhouette** : le tracé sur la ligne médiane (encre
 * de 20 × 16 une fois le trait posé, dans la zone de 20, centrée sur (12, 12)), et le plein sur le
 * **contour** de ce tracé — même bord extérieur, donc même taille à l'œil. Le « + » se pose sur le
 * corps du dossier, détaché du bord, et se lit à 20 px.
 */
class FolderIconGeometryTest {

    private val folder = "ic_sym_folder.xml"
    private val folderFill = "ic_sym_folder_fill.xml"
    private val badge = "ic_sym_folder_badge_plus.xml"
    private val badgeFill = "ic_sym_folder_badge_plus_fill.xml"

    private fun drawable(name: String): String {
        val path = "src/main/res/drawable/$name"
        return listOf(File(path), File("core/$path")).firstOrNull { it.exists() }?.readText()
            ?: error("introuvable : $path")
    }

    private fun pathDatas(xml: String): List<String> =
        Regex("android:pathData=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList()

    private fun strokes(xml: String): List<Double> =
        Regex("android:strokeWidth=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1].toDouble() }
            .toList()

    // ---- aplatissement des tracés (M/L/H/V/A/Z, en absolu : c'est ce que portent ces dessins) ----

    private fun nums(v: String): List<Double> =
        Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)").findAll(v).map { it.value.toDouble() }.toList()

    private fun arcPoints(
        p1: Pair<Double, Double>, rx0: Double, ry0: Double, p2: Pair<Double, Double>,
        fa: Int, fs: Int, steps: Int = 64,
    ): List<Pair<Double, Double>> {
        var rx = abs(rx0)
        var ry = abs(ry0)
        val dx = (p1.first - p2.first) / 2.0
        val dy = (p1.second - p2.second) / 2.0
        val lam = dx * dx / (rx * rx) + dy * dy / (ry * ry)
        if (lam > 1) {
            val s = sqrt(lam)
            rx *= s
            ry *= s
        }
        val num = max(0.0, rx * rx * ry * ry - rx * rx * dy * dy - ry * ry * dx * dx)
        val den = rx * rx * dy * dy + ry * ry * dx * dx
        val coef = (if (fa != fs) 1 else -1) * sqrt(if (den == 0.0) 0.0 else num / den)
        val cx = coef * (rx * dy / ry) + (p1.first + p2.first) / 2.0
        val cy = coef * (-ry * dx / rx) + (p1.second + p2.second) / 2.0
        val a1 = atan2((p1.second - cy) / ry, (p1.first - cx) / rx)
        val a2 = atan2((p2.second - cy) / ry, (p2.first - cx) / rx)
        var d = a2 - a1
        if (fs == 1 && d < 0) d += 2 * Math.PI
        if (fs == 0 && d > 0) d -= 2 * Math.PI
        return (1..steps).map {
            val a = a1 + d * it / steps
            Pair(cx + rx * cos(a), cy + ry * sin(a))
        }
    }

    /** Une polyligne par sous-chemin. */
    private fun subpaths(d: String): List<List<Pair<Double, Double>>> {
        val subs = mutableListOf<MutableList<Pair<Double, Double>>>()
        var cur = Pair(0.0, 0.0)
        var start = Pair(0.0, 0.0)
        Regex("([MLHVAZ])([^MLHVAZ]*)").findAll(d).forEach { m ->
            val c = m.groupValues[1]
            val v = nums(m.groupValues[2])
            when (c) {
                "M" -> {
                    cur = Pair(v[0], v[1])
                    subs += mutableListOf(cur)
                    start = cur
                }
                "L" -> for (i in 0 until v.size - 1 step 2) {
                    cur = Pair(v[i], v[i + 1])
                    subs.last() += cur
                }
                "H" -> for (x in v) {
                    cur = Pair(x, cur.second)
                    subs.last() += cur
                }
                "V" -> for (y in v) {
                    cur = Pair(cur.first, y)
                    subs.last() += cur
                }
                "A" -> for (i in 0 until v.size - 6 step 7) {
                    val e = Pair(v[i + 5], v[i + 6])
                    subs.last() += arcPoints(cur, v[i], v[i + 1], e, v[i + 3].toInt(), v[i + 4].toInt())
                    cur = e
                }
                "Z" -> {
                    subs.last() += start
                    cur = start
                }
            }
        }
        return subs
    }

    /** Boîte d'encre d'un dessin : chaque sous-chemin élargi d'un demi-trait. */
    private fun inkBox(xml: String): DoubleArray {
        val half = (strokes(xml).maxOrNull() ?: 0.0) / 2.0
        var x0 = 1e9
        var y0 = 1e9
        var x1 = -1e9
        var y1 = -1e9
        pathDatas(xml).flatMap { subpaths(it) }.forEach { sub ->
            sub.forEach { (x, y) ->
                x0 = min(x0, x - half); x1 = max(x1, x + half)
                y0 = min(y0, y - half); y1 = max(y1, y + half)
            }
        }
        return doubleArrayOf(x0, y0, x1, y1)
    }

    private fun segDist(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val vx = b.first - a.first
        val vy = b.second - a.second
        val l2 = vx * vx + vy * vy
        if (l2 == 0.0) return hypot(p.first - a.first, p.second - a.second)
        val t = min(1.0, max(0.0, ((p.first - a.first) * vx + (p.second - a.second) * vy) / l2))
        return hypot(p.first - (a.first + t * vx), p.second - (a.second + t * vy))
    }

    private fun polyDistance(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>): Double {
        var best = 1e9
        for (i in 0 until a.size - 1) {
            for (j in 0 until b.size - 1) {
                best = min(best, segDist(a[i], b[j], b[j + 1]))
                best = min(best, segDist(a[i + 1], b[j], b[j + 1]))
                best = min(best, segDist(b[j], a[i], a[i + 1]))
                best = min(best, segDist(b[j + 1], a[i], a[i + 1]))
            }
        }
        return best
    }

    /** L'écart d'encre entre deux sous-chemins d'un dessin, demi-traits déduits. */
    private fun gapBetween(xml: String, a: Pair<Int, Int>, b: Pair<Int, Int>): Double {
        val datas = pathDatas(xml)
        val widths = strokes(xml)
        fun half(i: Int): Double = widths.getOrElse(i) { 0.0 } / 2.0
        fun sub(p: Pair<Int, Int>) = subpaths(datas[p.first])[p.second]
        val (sa, sb) = sub(a) to sub(b)
        return polyDistance(sa, sb) - (half(a.first) + half(b.first))
    }

    // ---- la famille -----------------------------------------------------------------------------

    @Test
    fun `les quatre dossiers ont la meme encre, dans la zone de vingt, centree`() {
        listOf(folder, folderFill, badge, badgeFill).forEach { name ->
            val (x0, y0, x1, y1) = inkBox(drawable(name)).toList()
            assertEquals("$name : bord gauche", 2.0, x0, 0.001)
            assertEquals("$name : bord haut", 4.0, y0, 0.001)
            assertEquals("$name : bord droit", 22.0, x1, 0.001)
            assertEquals("$name : bord bas", 20.0, y1, 0.001)
            assertEquals("$name : centre d'encre x", 12.0, (x0 + x1) / 2, 0.001)
            assertEquals("$name : centre d'encre y", 12.0, (y0 + y1) / 2, 0.001)
        }
    }

    @Test
    fun `le contour est au trait de deux, bouts et jointures arrondis, sans remplissage`() {
        listOf(folder, badge).forEach { name ->
            val xml = drawable(name)
            assertEquals("$name : une épaisseur par tracé", List(pathDatas(xml).size) { 2.0 }, strokes(xml))
            assertEquals("$name : bouts", List(pathDatas(xml).size) { "round" },
                Regex("android:strokeLineCap=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList())
            assertEquals("$name : jointures", List(pathDatas(xml).size) { "round" },
                Regex("android:strokeLineJoin=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList())
            assertTrue("$name : rien de plein", xml.contains("@android:color/transparent"))
        }
    }

    @Test
    fun `le plein est le contour du tracé, pas une forme à part`() {
        val outline = pathDatas(drawable(folder)).first()
        val fill = pathDatas(drawable(folderFill)).first()
        // La même silhouette, poussée d'un demi-trait (1) vers l'extérieur : la languette descend
        // d'une unité (5 -> 4), le corps s'ouvre d'une unité en haut (7,8 -> 6,8), les rayons
        // passent de 1,1 à 2,1 et de 2,2 à 3,2, et le coin de la marche reste le point
        // d'intersection des deux bords — jamais un arc concave.
        assertTrue("le tracé part de la languette", outline.startsWith("M4.4,5 H9.9 A1.1,1.1"))
        assertTrue("le plein part de (4,4 ; 4)", fill.startsWith("M4.4,4 H9.9 A2.1,2.1"))
        assertTrue("les coins du tracé : 2,2", outline.contains("A2.2,2.2 0 0 1 21,10 "))
        assertTrue("les coins du plein : 3,2", fill.contains("A3.2,3.2 0 0 1 22,10 "))
        assertTrue("le bas du plein : 20", fill.contains("18.8,20 H5.2"))
        assertTrue("l'épaule gauche du plein : 2,4", fill.contains("V6.4 A2.4,2.4 0 0 1 4.4,4"))
    }

    @Test
    fun `les deux icones de dossier d'une liste portent exactement le meme contour`() {
        val fill = pathDatas(drawable(folderFill)).first()
        val marked = pathDatas(drawable(badgeFill)).first()
        assertTrue("le dossier « + » plein reprend le contour du dossier plein", marked.startsWith(fill.trimEnd()))
        assertTrue("et le « + » s'y perce en creux", drawable(badgeFill).contains("android:fillType=\"evenOdd\""))
    }

    @Test
    fun `le plus est dans le corps du dossier, detache du bord`() {
        // Le tracé : le dossier (0.0), puis les deux barres du « + » (1.0 et 2.0).
        // Le « + » est centre dans le corps : 3,4 entre les tracés, moins les deux demi-traits
        // (1 + 1) = 1,4 d'encre nette.
        val gap = gapBetween(drawable(badge), 0 to 0, 1 to 0)
        assertTrue("écart d'encre du « + » au contour : $gap", gap >= 1.35)
        val gapOther = gapBetween(drawable(badge), 0 to 0, 2 to 0)
        assertTrue("écart d'encre du « + » (barre horizontale) : $gapOther", gapOther >= 3.4)
        // Le creux du plein : c'est le **deuxième sous-chemin** du seul tracé plein, et la marge au
        // bord y est plus large — un plein a plus de corps qu'un contour.
        val hollow = gapBetween(drawable(badgeFill), 0 to 0, 0 to 1)
        assertTrue("marge du creux au bord du plein : $hollow", hollow >= 3.0)
    }

    @Test
    fun `le plus se lit a vingt pixels`() {
        // À 20 dp, l'unité de la grille vaut 20/24 px. La barre verticale du « + » fait 4,4 unités
        // et son bras 4,8 : 3,7 px et 4,0 px — un « + » lisible, pas une tache. Il est plus court
        // qu'avant (4,8) parce que le creux du plein doit garder 3 unités de marge au bord.
        val scale = 20.0 / 24.0
        val bar = 4.4
        val arm = 4.8
        assertTrue("les barres à l'écran", bar * scale >= 3.3)
        assertTrue("les bras à l'écran", arm * scale >= 3.3)
        // Le tracé du « + » : deux barres, à bouts ronds.
        val bars = pathDatas(drawable(badge)).drop(1)
        assertEquals("deux barres", 2, bars.size)
        assertTrue("barre verticale : ${bars[0]}", bars[0] == "M12.8,11.2 V15.6")
        assertTrue("barre horizontale : ${bars[1]}", bars[1] == "M10.4,13.4 H15.2")
    }

    @Test
    fun `le plus est pose sur le milieu du corps, un peu a droite`() {
        // Les deux barres du « + » au trait : verticale de 11,2 à 15,6, horizontale de 10,4 à 15,2.
        val bars = pathDatas(drawable(badge)).drop(1)
        val verticale = nums(bars[0])
        val horizontale = nums(bars[1])
        val x0 = horizontale[0] - 1.0      // demi-trait
        val x1 = horizontale[2] + 1.0
        val y0 = verticale[1] - 1.0
        val y1 = verticale[2] + 1.0
        // Le corps va de 7,8 à 19 : son milieu est 13,4 — le « + » y est centré.
        assertEquals("centre du « + » en y", (7.8 + 19.0) / 2, (y0 + y1) / 2, 0.001)
        // En x, il est décalé d'un peu à droite : la languette pèse à gauche du dossier.
        val bodyCentre = (3.0 + 21.0) / 2
        assertTrue("décalage à droite", (x0 + x1) / 2 - bodyCentre in 0.3..1.2)
        // Et la marge reste nette des quatre côtés, dans le corps.
        assertTrue("marge à gauche", x0 - 3.0 >= 3.0)
        assertTrue("marge à droite", 21.0 - x1 >= 3.0)
        assertTrue("marge en haut", y0 - 7.8 >= 1.4)
        assertTrue("marge en bas", 19.0 - y1 >= 1.4)
    }

    @Test
    fun `l ancien dessin n est plus la`() {
        val old = listOf(
            "M4,6 h4.5 l2,2.5",      // le contour d'avant, 20 × 15,5, centre (12 ; 12,75)
            "M3,6 h5.2 l2,2.4",      // le plein d'avant, 18 × 12,5, coins vifs
            "M16.5,12.5 V18.5",      // le « + » collé au coin inférieur droit
            "M13.5,15.5 H19.5",
            "M5.2,5 H10.5 V7.2",     // la languette courte fermée par un coin vif
            "M5.2,4 H10.5 A1,1 0 0 1 11.5,5 V6.2",
            "M12.8,10.7 V15.5",      // le « + » de la géométrie précédente
            "M10.4,13.1 H15.2",
        )
        listOf(folder, folderFill, badge, badgeFill).forEach { name ->
            val xml = drawable(name)
            old.forEach { artefact ->
                assertFalse("$name porte encore « $artefact »", xml.contains(artefact))
            }
        }
    }
}
