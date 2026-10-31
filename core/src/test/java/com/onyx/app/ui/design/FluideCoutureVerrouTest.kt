package com.onyx.app.ui.design

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de COUTURE — les trois défauts que l'œil a vus, et que les moyennes ne voyaient pas.**
 *
 * La passe précédente mesurait la structure (isotropie, périodicité) : elle ne pouvait pas voir que
 * le contour était **polygonal**, que le cœur était **écrêté à blanc** ou que le fond portait du
 * **grain parasite**. Ce verrou-ci mesure ces trois choses-là, sur la même source que le verrou
 * structurel ([Fluide.sample], la bande `v` 0,05 → 0,45, 379 × 192) :
 *
 *  1. **la courbure locale** — `|dθ|` par pixel d'arc le long des crêtes (rad) : une courbe qui
 *     s'enroule donne peu, un polygone donne beaucoup ;
 *  2. **la part de pixels écrêtés** — au moins un canal à la valeur maximale : un cœur brûlé donne
 *     une part non nulle, un dégradé donne ~0 ;
 *  3. **l'énergie haute fréquence du fond**, hors des volutes — l'écart-type du laplacien.
 *
 * **La morsure est prouvée par mutation** : les trois mutations du rapport (seuil de crête franc,
 * clip franc, grain à 0,090) font rougir ce verrou et le rapport les chiffre.
 */
class FluideCoutureVerrouTest {

    private val TITRE = "Réunion de chantier"
    private val ID = "conv-1"
    private val W = 379
    private val H = 192
    private val HB = (0.45f * H).toInt() - (0.05f * H).toInt()

    private fun bandeRGB(plan: Fluide.Plan): Array<FloatArray> {
        val y0 = (0.05f * H).toInt()
        val y1 = (0.45f * H).toInt()
        val n = W * (y1 - y0)
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        for (y in y0 until y1) {
            for (x in 0 until W) {
                val c = Fluide.sample(plan, (x + 0.5f) / W, (y + 0.5f) / H, Fluide.souffleDuCycle(0f), phase = 0f)
                val i = (y - y0) * W + x
                r[i] = c[0]; g[i] = c[1]; b[i] = c[2]
            }
        }
        return arrayOf(r, g, b)
    }

    private fun bande(plan: Fluide.Plan): FloatArray {
        val rgb = bandeRGB(plan)
        return FloatArray(rgb[0].size) { 0.2126f * rgb[0][it] + 0.7152f * rgb[1][it] + 0.0722f * rgb[2][it] }
    }

    /** Le rehaussé : la luminance moins son fond local (flou 14 px). */
    private fun haut(l: FloatArray, W: Int, HB: Int): FloatArray {
        val fonde = FloatArray(l.size)
        val r = 42
        val k = DoubleArray(2 * r + 1)
        val sig = 14.0
        var s = 0.0
        for (i in -r..r) { k[i + r] = Math.exp(-(i * i) / (2 * sig * sig)); s += k[i + r] }
        for (i in k.indices) k[i] /= s
        // flou horizontal puis vertical, bords repliés
        val tmp = FloatArray(l.size)
        for (y in 0 until HB) for (x in 0 until W) {
            var a = 0.0
            for (i in -r..r) { val xx = (x + i).coerceIn(0, W - 1); a += k[i + r] * l[y * W + xx] }
            tmp[y * W + x] = a.toFloat()
        }
        for (y in 0 until HB) for (x in 0 until W) {
            var a = 0.0
            for (i in -r..r) { val yy = (y + i).coerceIn(0, HB - 1); a += k[i + r] * tmp[yy * W + x] }
            fonde[y * W + x] = a.toFloat()
        }
        return FloatArray(l.size) { l[it] - fonde[it] }
    }

    private fun grad(l: FloatArray, W: Int, HB: Int): Pair<FloatArray, FloatArray> {
        val gx = FloatArray(l.size); val gy = FloatArray(l.size)
        for (y in 1 until HB - 1) for (x in 1 until W - 1) {
            val i = y * W + x
            gx[i] = (l[i + 1] - l[i - 1]) * 0.5f
            gy[i] = (l[i + W] - l[i - W]) * 0.5f
        }
        return gx to gy
    }

    /** La courbure locale : |dθ| par pixel d'arc, tangente suivie à ±2 px le long de la crête. */
    private fun courbure(l: FloatArray, W: Int, HB: Int): Double {
        val (gx, gy) = grad(l, W, HB)
        val mags = ArrayList<Float>(l.size)
        for (i in l.indices) mags.add(hypot(gx[i], gy[i]))
        val nonZero = mags.filter { it > 0f }.sorted()
        val seuil = if (nonZero.isEmpty()) 0f else nonZero[(nonZero.size * 0.75).toInt()] * 0.55f
        var tot = 0.0; var n = 0
        for (y in 3 until HB - 3) for (x in 3 until W - 3) {
            val i = y * W + x
            val nv = hypot(gx[i], gy[i])
            if (nv < 1e-9f || nv <= seuil) continue
            val tx = -gy[i] / nv; val ty = gx[i] / nv
            val sy = (y + 2 * ty).roundToInt(); val sx = (x + 2 * tx).roundToInt()
            if (sy < 1 || sy >= HB - 1 || sx < 1 || sx >= W - 1) continue
            val j = sy * W + sx
            val nv2 = hypot(gx[j], gy[j])
            if (nv2 < 1e-9f) continue
            val t2x = -gy[j] / nv2; val t2y = gx[j] / nv2
            val d = abs(tx * t2y - ty * t2x).toDouble()
            tot += asin(minOf(1.0, d)) / 2.0
            n++
        }
        return if (n == 0) 0.0 else tot / n
    }

    /** La part de pixels écrêtés à la valeur maximale (au moins un canal ≥ 254/255). */
    private fun ecrevement(plan: Fluide.Plan): Double {
        val rgb = bandeRGB(plan)
        var c = 0
        for (i in rgb[0].indices) {
            if (maxOf(rgb[0][i], rgb[1][i], rgb[2][i]) >= 254f / 255f) c++
        }
        return c.toDouble() / rgb[0].size
    }

    /** L'énergie HF du fond, hors des volutes : écart-type du laplacien hors des crêtes. */
    private fun grainFond(plan: Fluide.Plan): Double {
        val l = bande(plan)
        val lh = haut(l, W, HB)
        var s = 0.0; var s2 = 0.0; var n = 0
        for (y in 1 until HB - 1) for (x in 1 until W - 1) {
            val i = y * W + x
            if (lh[i] >= 0.015f || lh[i] <= -0.015f) continue
            if (l[i] >= 0.35f) continue
            val lap = (l[i - W] + l[i + W] + l[i - 1] + l[i + 1] - 4f * l[i]).toDouble()
            s += lap; s2 += lap * lap; n++
        }
        if (n < 50) return 0.0
        val m = s / n
        return sqrt((s2 / n) - m * m) * 255.0
    }

    @Test
    fun `la crete s enroule — la courbure locale reste dans la famille de la planche`() {
        listOf(true, false).forEach { dark ->
            val plan = Fluide.plan(TITRE, ID, dark = dark, choix = ChoixCouverture.DEFAUT)
            val l = bande(plan)
            val c = courbure(haut(l, W, HB), W, HB)
            println("MESURE couture (%s) : courbure locale %.4f rad/px d'arc".format(if (dark) "sombre" else "clair", c))
            assertTrue(
                ("la crête doit s'ENROULER — la courbure locale doit rester dans la famille de la planche " +
                    "(%s : %.4f rad/px d'arc, seuil 0,26 ; planche 0,140 / 0,143 ; app d'avant 0,271 / 0,311)")
                    .format(if (dark) "sombre" else "clair", c),
                c <= 0.26,
            )
        }
    }

    @Test
    fun `le coeur n est plus ecrete a blanc`() {
        listOf(true, false).forEach { dark ->
            val plan = Fluide.plan(TITRE, ID, dark = dark, choix = ChoixCouverture.DEFAUT)
            val e = ecrevement(plan)
            println("MESURE couture (%s) : pixels écrêtés %.3f %%".format(if (dark) "sombre" else "clair", e * 100))
            assertTrue(
                ("le cœur ne doit plus être ÉCRÊTÉ à blanc — la part de pixels à la valeur maximale doit " +
                    "tomber à ~0 (%s : %.3f %%, seuil 0,05 %% ; app d.avant 1,41 %% / 0,34 %%)")
                    .format(if (dark) "sombre" else "clair", e * 100),
                e <= 0.0005,
            )
        }
    }

    @Test
    fun `le fond ne porte plus de grain parasite`() {
        listOf(true, false).forEach { dark ->
            val plan = Fluide.plan(TITRE, ID, dark = dark, choix = ChoixCouverture.DEFAUT)
            val g = grainFond(plan)
            println("MESURE couture (%s) : grain du fond %.2f".format(if (dark) "sombre" else "clair", g))
            assertTrue(
                ("le fond ne doit plus porter de GRAIN PARASITE — l'énergie haute fréquence hors des " +
                    "volutes doit rester au niveau de la planche (%s : %.2f, seuil 4,50 ; planche au même " +
                    "cadre 3,10 ; app d'avant 11,10)").format(if (dark) "sombre" else "clair", g),
                g <= 4.50,
            )
        }
    }
}
