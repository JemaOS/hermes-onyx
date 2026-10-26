package com.onyx.app.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Les deux « mini barres verticales » aux extrémités de la rangée de points.
 *
 * Ce n'étaient pas des points de la vague: c'était le **reste des barres figées du bitmap** de
 * verre (`ic_dark_glass`). Le découpage calculait ses rectangles sur une géométrie supposée — pas de
 * 15,5 pt, largeur de 10 pt — alors que le dessin a des barres de 9,2 pt de large à 14,85 pt de pas.
 * L'écart était nul au centre et valait ~5 px aux extrémités: sur les deux barres du bout, une ou
 * deux colonnes de blanc survivaient, juste à l'extérieur des points.
 *
 * Les mesures ci-dessous sont celles du vrai PNG (480×480): centres 127, 164,5, 202, 239,5, 277,
 * 314,5 et 352; largeur 23–24 px; hauteurs 52, 82, 134, 212, 134, 82 et 52 px.
 */
class VoiceOrbBarsTest {

    private val size = 480
    private val centres = doubleArrayOf(127.0, 164.5, 202.0, 239.5, 277.0, 314.5, 352.0)
    private val widths = intArrayOf(23, 24, 23, 24, 23, 24, 23)
    private val heights = intArrayOf(52, 82, 134, 212, 134, 82, 52)

    /** Le disque: bleu et sombre (le fond), avec les sept barres claires et sans couleur. */
    private fun disc(): IntArray {
        val pixels = IntArray(size * size)
        val centre = size / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - centre
                val dy = y - centre
                val inside = dx * dx + dy * dy <= (size / 2f - 10f) * (size / 2f - 10f)
                pixels[y * size + x] = if (inside) argb(255, 40, 70, 140) else 0
            }
        }
        centres.forEachIndexed { index, cx ->
            val half = widths[index] / 2
            val halfHeight = heights[index] / 2
            for (y in (centre - halfHeight).roundToInt()..(centre + halfHeight).roundToInt()) {
                for (x in (cx - half).roundToInt()..(cx + half).roundToInt()) {
                    if (y in 0 until size && x in 0 until size) {
                        pixels[y * size + x] = argb(255, 220, 227, 238)
                    }
                }
            }
        }
        return pixels
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    /** La marge que `stripFrozenBars` laisse autour d'une barre trouvée. */
    private val pad = (size / 190f * 0.75f).roundToInt().coerceAtLeast(2)

    @Test
    fun `les sept barres figees sont trouvees dans l image`() {
        val runs = frozenBarColumns(disc(), size, size)
        assertEquals("sept barres, comme la vague de la référence", 7, runs.size)
        runs.forEachIndexed { index, run ->
            val centre = (run.first + run.last) / 2f
            assertTrue(
                "barre ${index + 1} : centre $centre attendu ≈ ${centres[index]}",
                abs(centre - centres[index]) <= 2f,
            )
        }
    }

    @Test
    fun `chaque colonne de chaque barre est couverte, extremes compris`() {
        val runs = frozenBarColumns(disc(), size, size)
        centres.forEachIndexed { index, cx ->
            val half = widths[index] / 2f
            val left = (cx - half).roundToInt()
            val right = (cx + half).roundToInt()
            val run = runs[index]
            assertTrue(
                "la barre ${index + 1} ($left..$right) doit être entièrement effacée (trouvé ${run.first}..${run.last}, marge $pad)",
                run.first - pad <= left && right <= run.last + pad,
            )
        }
    }

    @Test
    fun `le defaut d origine est bien celui-la`() {
        // Les rectangles de l'ancienne géométrie: des centres à 240 ± 3×39,16 px, larges de ±15,2.
        val scale = size / 190f
        fun oldRect(index: Int): IntRange {
            val centre = size / 2f + (index - 3) * (10 + 5.5) * scale
            val half = 10 * scale / 2f
            return (centre - half - scale).roundToInt()..(centre + half + scale).roundToInt()
        }
        // Mesuré sur le vrai PNG: le bord adouci de la barre de gauche est en x = 139, celui de la
        // barre de droite en x = 340 et 341. C'est exactement ce qui restait — les deux traits de
        // 1–2 px que le propriétaire voyait à l'extérieur des points.
        assertTrue("l'ancien rectangle s'arrêtait avant le bord de gauche", oldRect(0).last < 139)
        assertTrue("l'ancien rectangle commençait après le bord de droite", oldRect(6).first > 341)
        // La détection + la marge les effacent, sans toucher à la vague.
        val runs = frozenBarColumns(disc(), size, size)
        assertTrue("139 dans la première barre", runs.first().last + pad >= 139)
        assertTrue("340 dans la dernière barre", runs.last().first - pad <= 340)
    }

    @Test
    fun `la hauteur d une barre est mesuree, pas supposee`() {
        val pixels = disc()
        val runs = frozenBarColumns(pixels, size, size)
        runs.forEachIndexed { index, run ->
            val rows = frozenBarRows(pixels, size, size, run)
            assertTrue(rows != null)
            val height = rows!!.last - rows.first + 1
            assertTrue(
                "barre ${index + 1} : hauteur $height attendue ${heights[index]}",
                abs(height - heights[index]) <= 2,
            )
        }
    }

    @Test
    fun `un disque sans barre n efface rien`() {
        val plain = IntArray(size * size) { argb(255, 40, 70, 140) }
        assertTrue(frozenBarColumns(plain, size, size).isEmpty())
        assertTrue(frozenBarColumns(IntArray(0), 0, 0).isEmpty())
    }

    @Test
    fun `la vague garde ses sept points`() {
        val motion = java.io.File("src/main/java/com/onyx/app/ui/voice/VoiceLiveMotion.kt").readText()
        // Sept poids, comme `LiveWaveform.weights` du modèle: retirer les deux extrêmes avait
        // raccourci la vague (7 → 5) sans faire partir les deux traits, qui ne venaient pas d'elle.
        assertTrue(motion.contains("floatArrayOf(0.13f, 0.27f, 0.51f, 1f, 0.51f, 0.27f, 0.13f)"))
        assertTrue(motion.contains("val weights = LiveWaveWeights"))
        assertTrue("plus de poids retirés", !motion.contains("drop(1).dropLast(1)"))
    }
}
