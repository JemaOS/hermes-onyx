package com.onyx.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import java.util.zip.CRC32
import kotlin.math.cos
import kotlin.math.sin

/**
 * **La seule usine à images du langage — la couverture d'une conversation, dessinée par le code.**
 *
 * Le titre et l'identifiant de la conversation déterminent la vignette : une composition de
 * dégradés, de formes et de grain, **toujours la même** pour la même conversation. Il n'y a **aucun
 * aléatoire** — pas de `Random`, pas d'horloge, pas d'état : deux appels avec le même couple
 * `(titre, identifiant)` rendent une image **identique au pixel**.
 *
 * C'est la reprise exacte du générateur du jeu corrigé (`maquettes-hermes-v2/sources/hlib.py`) : la
 * même fonction de hachage (`crc32`, hlib.py:172), les six mêmes palettes (hlib.py:149-170), la même
 * variation (hlib.py:176 `v = (hsh >> 5) % 4`) et la même composition `warm`/`cool`. Aucun fichier
 * image, aucune ressource tierce : tout est composé sur place.
 */
object ConversationCover {

    /** Une palette : le fond multi-arrêts, la tache lumineuse, les formes, le trait. */
    data class Palette(
        val angleDeg: Float,
        val stops: List<Pair<Float, Int>>,
        val radial: Offset,
        val radialScale: Offset,
        val radialHex: Int,
        val radialAlpha: Float,
        val spot1: Int,
        val spot2: Int,
        val line: Int,
    )

    /**
     * **Les six palettes du générateur**, recopiées à l'identique (`hlib.py:149-170`). L'ordre est
     * celui de la source : c'est un index de palette, pas une couleur choisie.
     */
    val palettes: List<Palette> = listOf(
        Palette(
            angleDeg = 158f,
            stops = listOf(0f to 0x2A1B3D, 0.44f to 0x7A3B5C, 0.78f to 0xD2743F, 1f to 0xF0A85E),
            radial = Offset(0.78f, 0.08f), radialScale = Offset(0.70f, 1.20f),
            radialHex = 0xFFCE96, radialAlpha = 0.55f,
            spot1 = 0xFFD79B, spot2 = 0x5E2148, line = 0xFFE3B8,
        ),
        Palette(
            angleDeg = 165f,
            stops = listOf(0f to 0x06202A, 0.46f to 0x0E5A62, 0.78f to 0x1E8D86, 1f to 0x9FD9C9),
            radial = Offset(0.24f, 0.10f), radialScale = Offset(0.80f, 1.30f),
            radialHex = 0xBEF0E2, radialAlpha = 0.40f,
            spot1 = 0x8FE6D2, spot2 = 0x082C38, line = 0xDFF6EC,
        ),
        Palette(
            angleDeg = 162f,
            stops = listOf(0f to 0x3A2A18, 0.48f to 0x8C5A2E, 0.84f to 0xC9924F, 1f to 0xE8C089),
            radial = Offset(0.18f, 0.06f), radialScale = Offset(0.70f, 1.20f),
            radialHex = 0xFFE8C4, radialAlpha = 0.50f,
            spot1 = 0xFFE2B4, spot2 = 0x4E2F14, line = 0xFFF0CE,
        ),
        Palette(
            angleDeg = 150f,
            stops = listOf(0f to 0x0A0F20, 0.52f to 0x1C2B55, 1f to 0x3A5AA8),
            radial = Offset(0.84f, 0.12f), radialScale = Offset(0.60f, 1.10f),
            radialHex = 0x96B4FF, radialAlpha = 0.42f,
            spot1 = 0x9FB8FF, spot2 = 0x060A18, line = 0xD6E2FF,
        ),
        Palette(
            angleDeg = 155f,
            stops = listOf(0f to 0x2B0F26, 0.48f to 0x7A2450, 0.82f to 0xC9506F, 1f to 0xE88FA0),
            radial = Offset(0.30f, 0.10f), radialScale = Offset(0.70f, 1.20f),
            radialHex = 0xFFC4D6, radialAlpha = 0.42f,
            spot1 = 0xFFC2D6, spot2 = 0x43112B, line = 0xFFE0E8,
        ),
        Palette(
            angleDeg = 160f,
            stops = listOf(0f to 0x101C14, 0.50f to 0x23402A, 1f to 0x4C6B3A),
            radial = Offset(0.70f, 0.06f), radialScale = Offset(0.70f, 1.20f),
            radialHex = 0xD6F0C4, radialAlpha = 0.38f,
            spot1 = 0xCFE8B8, spot2 = 0x0C1A12, line = 0xEDF7DE,
        ),
    )

    /** La graine : `crc32` du titre et de l'identifiant, exactement comme `hlib.py:172`. */
    fun seed(title: String, id: String): Long {
        val crc = CRC32()
        crc.update("${title}\u0000$id".toByteArray(Charsets.UTF_8))
        return crc.value
    }

    /** L'index de palette : `hsh % 6` (le nombre de palettes). */
    fun paletteIndex(title: String, id: String): Int = (seed(title, id).toInt() and Int.MAX_VALUE) % palettes.size

    /** La variation de composition : `(hsh >> 5) % 4` (`hlib.py:176`). */
    fun variantIndex(title: String, id: String): Int =
        ((seed(title, id) ushr 5).toInt() and Int.MAX_VALUE) % 4

    /** Le plan complet d'une couverture — valeur pure, testable sans rendu. */
    data class Plan(val paletteIndex: Int, val variant: Int, val warm: Boolean)

    /** Le plan déterministe de `(titre, identifiant)`. Même entrée → **même plan**. */
    fun plan(title: String, id: String): Plan {
        val p = paletteIndex(title, id)
        return Plan(paletteIndex = p, variant = variantIndex(title, id), warm = p <= 2)
    }

    private fun Int.opaque(): Color = Color(0xFF000000.toInt() or (this and 0xFFFFFF))

    @Composable
    fun ConversationCover(title: String, id: String, modifier: Modifier = Modifier) {
        val p = plan(title, id)
        Canvas(modifier = modifier) { drawCover(p) }
    }

    /** Le dessin, pur [DrawScope] : ni état, ni aléatoire, ni horloge. */
    fun DrawScope.drawCover(plan: Plan) {
        val pal = palettes[plan.paletteIndex]
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return

        // 1 · le fond multi-arrêts, à l'angle de la palette
        val rad = Math.toRadians(plan_angle(pal).toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val half = (w + h) / 2f
        drawRect(
            brush = Brush.linearGradient(
                colors = pal.stops.map { it.second.opaque() },
                start = Offset(w / 2f - dx * half, h / 2f - dy * half),
                end = Offset(w / 2f + dx * half, h / 2f + dy * half),
            ),
            size = Size(w, h),
        )

        // 2 · la tache lumineuse (radial-gradient de la palette)
        val cx = pal.radial.x * w
        val cy = pal.radial.y * h
        val rx = pal.radialScale.x * w
        val ry = pal.radialScale.y * h
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    pal.radialHex.opaque().copy(alpha = pal.radialAlpha),
                    pal.radialHex.opaque().copy(alpha = 0f),
                ),
                center = Offset(cx, cy),
                radius = maxOf(rx, ry),
            ),
            radius = maxOf(rx, ry),
            center = Offset(cx, cy),
        )

        // 3 · la composition (4 variations, `hlib.py:177-231`)
        val s1 = pal.spot1.opaque()
        val s2 = pal.spot2.opaque()
        val ln = pal.line.opaque()
        when (plan.variant) {
            0 -> sunHorizon(w, h, s1, s2, ln)
            1 -> ribbons(w, h, s1, s2, ln)
            2 -> arcs(w, h, s1, ln)
            else -> blobs(w, h, s1, s2, ln)
        }

        // 4 · le trait signature, quel que soit le variant
        drawLine(ln.copy(alpha = 0.55f), Offset(0f, h * 0.62f), Offset(w, h * 0.62f), 1.4f)
    }

    private fun plan_angle(pal: Palette): Float = pal.angleDeg

    private fun DrawScope.sunHorizon(w: Float, h: Float, s1: Color, s2: Color, ln: Color) {
        drawCircle(s1.copy(alpha = 0.95f), radius = h * 0.17f, center = Offset(w * 0.72f, h * 0.46f))
        drawRect(s2.copy(alpha = 0.92f), topLeft = Offset(0f, h * 0.62f), size = Size(w, h * 0.38f))
        drawLine(ln.copy(alpha = 0.6f), Offset(0f, h * 0.72f), Offset(w * 0.62f, h * 0.72f), 1.8f)
        drawLine(ln.copy(alpha = 0.6f), Offset(w * 0.10f, h * 0.80f), Offset(w * 0.60f, h * 0.80f), 1.6f)
    }

    private fun DrawScope.ribbons(w: Float, h: Float, s1: Color, s2: Color, ln: Color) {
        val p1 = Path().apply {
            moveTo(-w * 0.1f, h * 0.34f)
            cubicTo(w * 0.28f, h * 0.02f, w * 0.62f, h * 0.58f, w * 1.1f, h * 0.20f)
        }
        drawPath(p1, s1.copy(alpha = 0.9f), style = Stroke(width = h * 0.2f))
        val p2 = Path().apply {
            moveTo(-w * 0.1f, h * 0.66f)
            cubicTo(w * 0.30f, h * 0.36f, w * 0.66f, h * 0.90f, w * 1.1f, h * 0.50f)
        }
        drawPath(p2, s2.copy(alpha = 0.9f), style = Stroke(width = h * 0.16f))
        drawCircle(ln.copy(alpha = 0.9f), radius = 3f, center = Offset(w * 0.78f, h * 0.26f))
    }

    private fun DrawScope.arcs(w: Float, h: Float, s1: Color, ln: Color) {
        val widths = listOf(h * 0.04f, h * 0.10f, h * 0.18f, h * 0.28f)
        widths.forEachIndexed { i, r ->
            val p = Path().apply {
                moveTo(-w * 0.05f, h * (0.22f + i * 0.28f))
                cubicTo(w * 0.26f, h * (0.04f + i * 0.28f), w * 0.52f, h * (0.50f + i * 0.28f), w * 1.05f, h * (0.26f + i * 0.28f))
            }
            drawPath(p, ln.copy(alpha = 0.85f), style = Stroke(width = 1.6f))
        }
        drawCircle(s1, radius = 5f, center = Offset(w * 0.52f, h * 0.5f))
        drawCircle(ln, radius = 11f, center = Offset(w * 0.52f, h * 0.5f), style = Stroke(width = 1.8f))
    }

    private fun DrawScope.blobs(w: Float, h: Float, s1: Color, s2: Color, ln: Color) {
        drawCircle(s1.copy(alpha = 0.55f), radius = w * 0.34f, center = Offset(w * 0.30f, h * 0.30f))
        drawCircle(s2.copy(alpha = 0.50f), radius = w * 0.42f, center = Offset(w * 0.78f, h * 0.72f))
        drawLine(ln.copy(alpha = 0.7f), Offset(w * 0.08f, h * 0.86f), Offset(w * 0.92f, h * 0.14f), 1.6f)
    }
}
