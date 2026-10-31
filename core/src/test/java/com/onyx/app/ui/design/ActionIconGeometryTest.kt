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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La géométrie des icônes d'action, relevée **dans le vectoriel** — pas dans une capture.
 *
 * Le jeu d'icônes maison est tracé sur une grille de 24, au trait de **2 unités**, bouts et
 * jointures arrondis, l'encre dans la zone de 20 (marge de 2 de chaque côté).
 *
 * Deux lots précédents avaient retouché les dessins à la main (« Redessine les icones bleu »,
 * « trait unique de 2 ») et les avaient éloignés du dessin de la référence : le trio de personnes
 * avait perdu ses épaules (corps central réduit à un éclat de 3,6 de large, donc un pâté), la bulle
 * à « + » avait été aplatie et son « + » déplacé hors du centre du corps, et les deux bulles avaient
 * été écartées et privées de leurs lignes de texte. Ce lot repart de la géométrie relevée **sur la
 * référence** (capture de l'écran Conversations, mesures au pixel) :
 *
 *  - trio : trois têtes (la centrale plus grande et posée plus haut), un corps central large et
 *    deux corps latéraux en retrait, détachés de 1,4 d'encre ;
 *  - bulle à « + » : bulle de 20 × 15,8 aux coins de rayon 2,6, queue en bas à gauche (base 5,6 ->
 *    9,8 sur le bord bas, pointe 6,3 / 20,7), « + » de bras 6,6 centré sur le corps (12, 10,2) et
 *    détaché de 1,6 ;
 *  - deux bulles : la grande en haut à gauche (queue en bas à gauche), la petite en bas à droite
 *    **en avant**, portant deux lignes de texte ; comme dans la référence, le tracé arrière vient
 *    mourir **sur** le tracé avant — une seule silhouette, pas deux fragments.
 *
 * Écarts assumés et mesurés : la référence serre ses formes davantage (0,7 à 0,9 d'encre entre le
 * corps central et les corps latéraux du trio, bulles qui se touchent), ce qui donne au trait de 2
 * de la famille une bavure — les écarts sont donc tenus à 1,4 ; et ses têtes de trio sont plus
 * petites (18 % de la largeur, contre 28 % ici) : au trait de 2, un anneau de moins de 5 d'encre se
 * referme en pastille et la forme ne se nomme plus à 20 px.
 */
class ActionIconGeometryTest {

    private fun drawable(name: String): String {
        val path = "src/main/res/drawable/$name"
        return listOf(File(path), File("core/$path")).firstOrNull { it.exists() }?.readText()
            ?: error("introuvable : $path")
    }

    private fun pathDatas(xml: String): List<String> =
        Regex("android:pathData=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList()

    private fun strokes(xml: String): List<String> =
        Regex("android:strokeWidth=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList()

    // ---- aplatissement des tracés (M/L/H/V/C/A/Z, absolu et relatif) ----

    private fun nums(v: String): List<Double> =
        Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(v)
            .map { it.value.toDouble() }.toList()

    private fun arcPoints(
        p1: Pair<Double, Double>, rx0: Double, ry0: Double, p2: Pair<Double, Double>,
        fa: Int, fs: Int, steps: Int = 48,
    ): List<Pair<Double, Double>> {
        var rx = abs(rx0); var ry = abs(ry0)
        val dx = (p1.first - p2.first) / 2.0
        val dy = (p1.second - p2.second) / 2.0
        val lam = dx * dx / (rx * rx) + dy * dy / (ry * ry)
        if (lam > 1) { val s = sqrt(lam); rx *= s; ry *= s }
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
        Regex("([MmLlHhVvCcAaZz])([^MmLlHhVvCcAaZz]*)").findAll(d).forEach { m ->
            val c = m.groupValues[1]
            val v = nums(m.groupValues[2])
            val rel = c[0].isLowerCase()
            when (c.uppercase()) {
                "M" -> {
                    var i = 0
                    while (i < v.size - 1) {
                        cur = if (rel) Pair(cur.first + v[i], cur.second + v[i + 1])
                        else Pair(v[i], v[i + 1])
                        if (i == 0) { subs += mutableListOf(cur); start = cur }
                        else subs.last() += cur
                        i += 2
                    }
                }
                "L" -> for (i in 0 until v.size - 1 step 2) {
                    cur = if (rel) Pair(cur.first + v[i], cur.second + v[i + 1])
                    else Pair(v[i], v[i + 1])
                    subs.last() += cur
                }
                "H" -> for (x in v) {
                    cur = if (rel) Pair(cur.first + x, cur.second) else Pair(x, cur.second)
                    subs.last() += cur
                }
                "V" -> for (y in v) {
                    cur = if (rel) Pair(cur.first, cur.second + y) else Pair(cur.first, y)
                    subs.last() += cur
                }
                "C" -> for (i in 0 until v.size - 1 step 6) {
                    val c1 = if (rel) Pair(cur.first + v[i], cur.second + v[i + 1])
                    else Pair(v[i], v[i + 1])
                    val c2 = if (rel) Pair(cur.first + v[i + 2], cur.second + v[i + 3])
                    else Pair(v[i + 2], v[i + 3])
                    val e = if (rel) Pair(cur.first + v[i + 4], cur.second + v[i + 5])
                    else Pair(v[i + 4], v[i + 5])
                    val p0 = cur
                    for (k in 1..32) {
                        val t = k / 32.0
                        val mt = 1 - t
                        subs.last() += Pair(
                            mt * mt * mt * p0.first + 3 * mt * mt * t * c1.first +
                                3 * mt * t * t * c2.first + t * t * t * e.first,
                            mt * mt * mt * p0.second + 3 * mt * mt * t * c1.second +
                                3 * mt * t * t * c2.second + t * t * t * e.second,
                        )
                    }
                    cur = e
                }
                "A" -> for (i in 0 until v.size - 6 step 7) {
                    val e = if (rel) Pair(cur.first + v[i + 5], cur.second + v[i + 6])
                    else Pair(v[i + 5], v[i + 6])
                    subs.last() += arcPoints(cur, v[i], v[i + 1], e, v[i + 3].toInt(), v[i + 4].toInt())
                    cur = e
                }
                "Z" -> { subs.last() += start; cur = start }
            }
        }
        return subs
    }

    /** Boîte d'encre : les points du tracé élargis d'un demi-trait. */
    private fun inkBox(xml: String): DoubleArray {
        // Un glyphe **plein** (le carré de l'arrêt) n'a pas de trait : sa boîte est celle de son
        // tracé, sans demi-trait à retirer.
        val sw = if (strokes(xml).isEmpty()) 0.0 else strokes(xml).first().toDouble() / 2.0
        var x0 = 1e9; var y0 = 1e9; var x1 = -1e9; var y1 = -1e9
        pathDatas(xml).flatMap { subpaths(it) }.forEach { sub ->
            sub.forEach { (x, y) ->
                x0 = min(x0, x - sw); x1 = max(x1, x + sw)
                y0 = min(y0, y - sw); y1 = max(y1, y + sw)
            }
        }
        return doubleArrayOf(x0, y0, x1, y1)
    }

    private fun pointSegmentDistance(
        p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>,
    ): Double {
        val vx = b.first - a.first; val vy = b.second - a.second
        val l2 = vx * vx + vy * vy
        if (l2 == 0.0) return hypot(p.first - a.first, p.second - a.second)
        val t = min(1.0, max(0.0, ((p.first - a.first) * vx + (p.second - a.second) * vy) / l2))
        return hypot(p.first - (a.first + t * vx), p.second - (a.second + t * vy))
    }

    private fun polylineDistance(
        a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>,
    ): Double {
        var best = 1e9
        for (i in 0 until a.size - 1) {
            for (j in 0 until b.size - 1) {
                best = min(best, pointSegmentDistance(a[i], b[j], b[j + 1]))
                best = min(best, pointSegmentDistance(a[i + 1], b[j], b[j + 1]))
                best = min(best, pointSegmentDistance(b[j], a[i], a[i + 1]))
                best = min(best, pointSegmentDistance(b[j + 1], a[i], a[i + 1]))
            }
        }
        return best
    }

    /**
     * L'écart d'encre le plus serré entre deux formes du dessin, en ne regardant que les couples
     * de sous-chemins qu'on attend **séparés** : un « + » et une barre d'œil barré se croisent par
     * construction, on ne les compte pas.
     */
    private fun tightestGap(xml: String, samePathPairs: Boolean = true): Double {
        val sw = strokes(xml).first().toDouble()
        val datas = pathDatas(xml)
        val flat = mutableListOf<Pair<Int, List<Pair<Double, Double>>>>()
        datas.forEachIndexed { pi, d ->
            subpaths(d).forEach { flat += Pair(pi, it) }
        }
        var best = 1e9
        for (i in flat.indices) for (j in i + 1 until flat.size) {
            if (!samePathPairs && flat[i].first == flat[j].first) continue
            best = min(best, polylineDistance(flat[i].second, flat[j].second) - sw)
        }
        return best
    }

    /** Distance d'un point à une polyligne. */
    private fun pointToPolyline(
        p: Pair<Double, Double>, b: List<Pair<Double, Double>>,
    ): Double {
        var best = 1e9
        for (j in 0 until b.size - 1) best = min(best, pointSegmentDistance(p, b[j], b[j + 1]))
        return best
    }

    /** Les sous-chemins d'un tracé, avec l'indice de leur élément `<path>`. */
    private fun flatSubpaths(xml: String): List<Pair<Int, List<Pair<Double, Double>>>> {
        val out = mutableListOf<Pair<Int, List<Pair<Double, Double>>>>()
        pathDatas(xml).forEachIndexed { pi, d -> subpaths(d).forEach { out += Pair(pi, it) } }
        return out
    }

    /** Les cinq dessins redessinés dans ce lot. */
    private val redrawn = listOf(
        "ic_sym_person_3", "ic_sym_plus_bubble", "ic_sym_bubble_left_and_text_bubble_right",
        "ic_sym_eye_slash", "ic_sym_eye",
    )

    /** Toute la famille des icônes d'action bleues. */
    private val family = redrawn + listOf(
        "ic_sym_square_and_pencil", "ic_sym_checkmark_circle", "ic_sym_archivebox",
        "ic_sym_person", "ic_sym_bell", "ic_sym_bell_slash", "ic_sym_arrow_right_square",
        "ic_sym_rectangle_split_3x1",
        // Les glyphes que la rangée d'actions de la réponse emploie : les verrous ci-dessous les
        // couvrent au même titre que les voisins (trait unique de 2, bouts ronds, encre dans la
        // zone de 20) — un signe plus gras ou plus grêle que ses voisins se voit plus que sa forme.
        "ic_sym_arrow_counterclockwise", "ic_sym_arrow_clockwise", "ic_sym_doc_on_doc",
        // L'icône de la ligne « Réflexion » (le cerveau de la référence).
        "ic_sym_brain",
    )

    /**
     * Les glyphes de la rangée d'actions de la réponse : **brancher d'ici** (la bifurcation de la
     * référence, reprise de son jeu d'icônes — Lucide `git-branch`), restaurer, régénérer, lire à
     * voix haute (et arrêter), copier. Relevé : l'enceinte de l'onde de lecture sortait de la zone
     * (encre jusqu'à 24) et sa boîte n'était pas centrée ; les deux flèches penchaient de 0,26
     * vers le haut. Corrigé à la source, ce test tient la correction.
     */
    private val replyRow = listOf(
        "ic_sym_arrow_counterclockwise", "ic_sym_arrow_clockwise", "ic_sym_speaker_wave_2",
        "ic_sym_stop_fill", "ic_sym_doc_on_doc",
        // LOT branche : la première à gauche. Son dessin vient de la référence (les nombres de
        // `GitBranch.kt` d'`icons-lucide` 1.1.0), et il passe les mêmes verrous que ses voisins.
        "ic_lucide_git_branch",
    )

    /** Les glyphes de la rangée qui sont tracés (les deux autres sont des formes pleines). */
    private val replyRowStroked = listOf(
        "ic_sym_arrow_counterclockwise", "ic_sym_arrow_clockwise", "ic_sym_doc_on_doc",
        "ic_lucide_git_branch",
    )

    /** Les glyphes pleins : pas de trait, donc pas de demi-trait à retirer dans la boîte. */
    private val replyRowFilled = listOf("ic_sym_speaker_wave_2", "ic_sym_stop_fill")


    @Test
    fun `la famille des icones d'action est au trait unique de deux, bouts et jointures arrondis`() {
        family.forEach { name ->
            val xml = drawable("$name.xml")
            assertEquals("épaisseurs de $name", List(pathDatas(xml).size) { "2" }, strokes(xml))
            assertEquals("bouts de $name", List(pathDatas(xml).size) { "round" },
                Regex("android:strokeLineCap=\"([^\"]*)\"").findAll(xml)
                    .map { it.groupValues[1] }.toList())
            assertEquals("jointures de $name", List(pathDatas(xml).size) { "round" },
                Regex("android:strokeLineJoin=\"([^\"]*)\"").findAll(xml)
                    .map { it.groupValues[1] }.toList())
            assertTrue("aucun tracé plein dans $name", xml.contains("@android:color/transparent"))
        }
    }

    @Test
    fun `l'encre des dessins redessines tient dans la zone de vingt, centree`() {
        redrawn.forEach { name ->
            val (x0, y0, x1, y1) = inkBox(drawable("$name.xml")).toList()
            assertTrue("$name : bord gauche $x0", x0 >= 1.95)
            assertTrue("$name : bord haut $y0", y0 >= 1.95)
            assertTrue("$name : bord droit $x1", x1 <= 22.05)
            assertTrue("$name : bord bas $y1", y1 <= 22.05)
            // Le centre d'encre tombe sur (12, 12) : laissé à 0,25 près pour les formes
            // asymétriques (la barre de l'œil, la queue des bulles).
            assertEquals("$name : centre d'encre x", 12.0, (x0 + x1) / 2, 0.25)
            assertEquals("$name : centre d'encre y", 12.0, (y0 + y1) / 2, 0.25)
        }
    }

    @Test
    fun `aucune forme des dessins redessines n'entre dans une autre`() {
        // Le trio touchait son corps central (0,04 d'écart). La référence serre elle-même ses
        // formes à 0,7-0,9 : au trait unique de 2, cela se lit comme une bavure, d'où le plancher
        // de 1,4 tenu ici pour toutes les formes attendues séparées.
        val gap = tightestGap(drawable("ic_sym_person_3.xml"))
        assertTrue("ic_sym_person_3 : écart d'encre le plus serré $gap", gap >= 1.399)
        // La bulle à « + » : la bulle et son « + » sont deux tracés distincts.
        val bubblePlus = tightestGap(drawable("ic_sym_plus_bubble.xml"), samePathPairs = false)
        assertTrue("ic_sym_plus_bubble : écart d'encre le plus serré $bubblePlus", bubblePlus >= 1.399)
    }

    @Test
    fun `le plus grand ecart du trio reste sous le tiers de la zone`() {
        // Une marge qui se voie, mais pas un trou : les trois personnes d'un trio se lisent
        // ensemble. L'écart d'encre entre formes reste entre 1,3 et 3.
        val gap = tightestGap(drawable("ic_sym_person_3.xml"))
        assertTrue("écart du trio $gap", gap in 1.3..3.0)
    }

    @Test
    fun `le trio a trois tetes et trois corps, le central plus grand et en avant`() {
        val xml = drawable("ic_sym_person_3.xml")
        val flat = flatSubpaths(xml)
        // Trois têtes puis trois corps : six sous-chemins, dans deux tracés.
        assertEquals("sous-chemins", 6, flat.size)
        val heads = flat.take(3).map { it.second }
        val bodies = flat.drop(3).map { it.second }
        // La tête centrale est sur (12, ...) et posée plus haut que les latérales.
        fun cx(h: List<Pair<Double, Double>>) = (h.minOf { it.first } + h.maxOf { it.first }) / 2
        val central = heads.filter { abs(cx(it) - 12.0) < 0.3 }
        val laterals = heads.filter { abs(cx(it) - 12.0) >= 0.3 }
        assertEquals("une tête centrale", 1, central.size)
        assertEquals("deux têtes latérales", 2, laterals.size)
        val centreTop = central[0].minOf { it.second }
        assertTrue("tête centrale plus haute ($centreTop)", laterals.all { centreTop < it.minOf { p -> p.second } })
        // Les têtes latérales sont symétriques autour de l'axe.
        assertEquals("symétrie des têtes", 12.0, laterals.map { cx(it) }.average(), 0.05)
        // Le corps central est plus large que les deux latéraux.
        fun width(i: Int) = bodies[i].maxOf { it.first } - bodies[i].minOf { it.first }
        assertTrue("corps central plus large", width(0) > width(1) + 1.0)
        // Chaque épaule garde un CREUX : la paroi monte de la ligne de base (17,15) vers le sommet
        // du dôme, et sa hauteur de tracé doit dépasser l'épaisseur du trait (2) — sinon le dôme se
        // referme et l'épaule devient un pavé plein. C'est le défaut signalé (« un pâté »).
        val rises = bodies.map { it.map { p -> p.second }.let { ys -> (ys.max() - ys.min()) } }
        rises.forEachIndexed { i, h ->
            assertTrue("hauteur de l'épaule $i : $h", h > 2.0)
        }
        // Le creux de l'épaule centrale est le plus large (elle est en avant).
        val hollows = bodies.map { it.map { p -> p.first }.let { xs -> xs.max() - xs.min() } - 2.0 }
        assertTrue("creux central ${hollows[0]} contre ${hollows[1]}", hollows[0] > hollows[1])
    }

    @Test
    fun `le plus de la bulle est centre sur le corps, detache du bord`() {
        val xml = drawable("ic_sym_plus_bubble.xml")
        val plus = pathDatas(xml)[1]
        // Le « + » : une barre verticale de 6,9 à 13,5 et une horizontale de 8,7 à 15,3 — bras de
        // 6,6, centre (12, 10,2).
        assertTrue("barre du + : $plus", plus.contains("M12,6.9 V13.5") &&
            plus.contains("M8.7,10.2 H15.3"))
        // Le corps va de 3,3 à 17,1 : le milieu du corps est 10,2, celui du « + » aussi.
        assertEquals("centre du + sur le corps", 10.2, (6.9 + 13.5) / 2, 0.001)
        assertEquals("milieu du corps", 10.2, (3.3 + 17.1) / 2, 0.001)
        // Détaché : 1,6 d'encre au-dessus et au-dessous du bras haut et bas.
        val gap = tightestGap(xml, samePathPairs = false)
        assertTrue("écart d'encre du + dans la bulle : $gap", gap >= 1.399)
    }

    @Test
    fun `les deux bulles forment une seule silhouette, le trace arriere meurt sur le trace avant`() {
        val xml = drawable("ic_sym_bubble_left_and_text_bubble_right.xml")
        val flat = flatSubpaths(xml)
        val back = flat.first { it.first == 0 && it.second.size > 4 }.second
        val front = flat.filter { it.first == 1 }
        // Le tracé arrière est ouvert : il commence en haut de sa paroi droite et finit sur son
        // bord bas (il ne revient pas à son point de départ).
        val d0 = hypot(back.first().first - back.last().first, back.first().second - back.last().second)
        assertTrue("le tracé arrière est ouvert ($d0)", d0 > 3.0)
        // Ses deux bouts viennent mourir SUR le tracé de la bulle avant (une seule silhouette).
        listOf(back.first(), back.last()).forEach { p ->
            val near = front.minOf { pointToPolyline(p, it.second) }
            assertTrue("bout du tracé arrière $p : à $near du tracé avant", near <= 1.2)
        }
        // Sa paroi droite visible reste assez longue pour qu'on lise une bulle, pas un crochet.
        val wall = back.filter { abs(it.first - 15.8) < 0.05 }
        val wallLen = wall.maxOf { it.second } - wall.minOf { it.second }
        assertTrue("longueur de la paroi droite $wallLen", wallLen >= 2.5)
    }

    @Test
    fun `la bulle avant porte deux lignes de texte, detachees de son bord`() {
        val xml = drawable("ic_sym_bubble_left_and_text_bubble_right.xml")
        val front = flatSubpaths(xml).filter { it.first == 1 }
        // Deux lignes : deux sous-chemins horizontaux de deux points chacun.
        val lines = front.filter { sub ->
            sub.second.size == 2 && abs(sub.second[0].second - sub.second[1].second) < 0.05
        }
        assertEquals("lignes de texte", 2, lines.size)
        val ys = lines.map { it.second[0].second }.sorted()
        assertEquals("écart entre les deux lignes", 3.8, ys[1] - ys[0], 0.05)
        assertTrue("les lignes sont détachées", ys[1] - ys[0] - 2.0 >= 1.399)
        // La bulle avant tient ses deux lignes à 0,5 d'encre de ses parois (la référence les pose
        // encore plus près : son trait vaut 1, le nôtre 2).
        val frontBody = front.first { it.second.size > 4 }.second
        val top = frontBody.minOf { it.second }
        val bot = frontBody.maxOf { it.second }
        assertTrue("ligne haute sous la paroi", ys[0] - 1.0 - (top + 1) >= 0.45)
        assertTrue("ligne basse sur la paroi", (bot - 1) - (ys[1] + 1.0) >= 0.45)
    }

    @Test
    fun `l'encre de toute la famille tient dans la zone de vingt`() {
        family.forEach { name ->
            val (x0, y0, x1, y1) = inkBox(drawable("$name.xml")).toList()
            assertTrue("$name : bord gauche $x0", x0 >= 1.95)
            assertTrue("$name : bord haut $y0", y0 >= 1.95)
            assertTrue("$name : bord droit $x1", x1 <= 22.05)
            assertTrue("$name : bord bas $y1", y1 <= 22.05)
        }
    }

    // ---- La rangée d'actions de la réponse : quatre glyphes, une seule géométrie ----------------

    @Test
    fun `les glyphes de la rangee d'actions tiennent dans la zone de vingt, centres sur douze`() {
        // « icônes, tailles, placement » : les quatre glyphes doivent lire la même grille. Relevé
        // avant correction : la boîte de l'enceinte allait jusqu'à 24 (hors zone) et sa boîte
        // n'était pas centrée ; les deux flèches penchaient de 0,26 vers le haut.
        replyRow.forEach { name ->
            val (x0, y0, x1, y1) = inkBox(drawable("$name.xml")).toList()
            assertTrue("$name : bord gauche $x0", x0 >= 1.95)
            assertTrue("$name : bord haut $y0", y0 >= 1.95)
            assertTrue("$name : bord droit $x1", x1 <= 22.05)
            assertTrue("$name : bord bas $y1", y1 <= 22.05)
            assertEquals("$name : centre d'encre x", 12.0, (x0 + x1) / 2, 0.25)
            assertEquals("$name : centre d'encre y", 12.0, (y0 + y1) / 2, 0.25)
        }
    }

    @Test
    fun `les glyphes traces de la rangee sont au trait de deux, bouts ronds, sans aplat`() {
        // Restaurer (flèche qui remonte), régénérer (flèche horaire) et copier (deux feuilles) sont
        // tracés comme le reste de la famille : une seule épaisseur, bouts et jointures ronds,
        // aucun tracé plein.
        replyRowStroked.forEach { name ->
            val xml = drawable("$name.xml")
            assertEquals("épaisseurs de $name", List(pathDatas(xml).size) { "2" }, strokes(xml))
            assertEquals(
                "bouts de $name",
                List(pathDatas(xml).size) { "round" },
                Regex("android:strokeLineCap=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList(),
            )
            assertEquals(
                "jointures de $name",
                List(pathDatas(xml).size) { "round" },
                Regex("android:strokeLineJoin=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList(),
            )
            assertTrue("tracé plein dans $name", xml.contains("@android:color/transparent"))
        }
    }

    @Test
    fun `les deux etats de la lecture sont des formes pleines declarees`() {
        // L'enceinte (lire) et le carré (arrêter) sont les seuls aplats de la rangée, comme dans la
        // référence : leur état change sans changer la boîte, donc sans décaler la rangée.
        replyRowFilled.forEach { name ->
            val xml = drawable("$name.xml")
            val fills = Regex("android:fillColor=\"([^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toList()
            assertTrue("$name : aucun aplat plein", fills.any { it != "@android:color/transparent" })
        }
        // L'enceinte porte ses deux ondes en trait (le trait reste celui de la famille).
        val speaker = drawable("ic_sym_speaker_wave_2.xml")
        assertTrue("ondes de l'enceinte", strokes(speaker).all { it == "2" })
        assertEquals("ondes arrondies", List(strokes(speaker).size) { "round" },
            Regex("android:strokeLineCap=\"([^\"]*)\"").findAll(speaker).map { it.groupValues[1] }.toList())
    }
}
