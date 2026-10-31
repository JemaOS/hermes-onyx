package com.onyx.app.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.ambientLoop
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port exact de `DriftingBlobs` (VoiceView:709-764), la lumière « nebula » de l'orbe.
 *
 * Tout est repris de la référence, sans approximation :
 *  - trois taches en **gradient radial** à trois arrêts (`tint`, `tint × 0,55`, transparent) ;
 *  - tailles **187 / 154 / 143**, cadre **172** ;
 *  - opacités `min(0,95 ; 0,75·strength)`, `min(0,85 ; 0,55·strength)`, `min(0,85 ; 0,6·strength)` ;
 *  - teintes : la couleur de phase, mélangée à **12 % de blanc**, puis à **50 % de violet** ;
 *  - offsets et échelles exacts, interpolés entre les deux états du Booléen la référence ;
 *  - **fusion additive** (`plusLighter`) et **rotation de teinte par matrice** sur le calque,
 *    appliquée après composition — comme `.hueRotation` de la référence, et non par retouche HSV
 *    des teintes, qui ne donnerait pas la même image ;
 *  - rotations `linear` 16 s et 2 s, durées 2,8 / 3,9 / 2,3 / 6 s en `easeInOut` aller-retour.
 */
@Composable
fun DriftingBlobs(
    color: Color,
    strength: Float,
    swirling: Boolean,
    modifier: Modifier = Modifier,
) {
    val ambient = rememberInfiniteTransition(label = "driftingBlobs")

    fun cycle(ms: Int, label: String) = infiniteRepeatable(
        animation = tween<Float>(durationMillis = ms, easing = OnyxMotion.EaseInOut),
        repeatMode = RepeatMode.Reverse,
    )

    // `DriftingBlobs.onAppear { guard !reduceMotion else { return } }` (`VoiceView:739-753`) :
    // aucune des cinq boucles ne démarre, les taches restent à leur pose de repos (offsets et
    // échelles du premier état des Booléens).
    val a = ambientLoop(0f) { ambient.animateFloat(0f, 1f, cycle(2800, "a"), label = "blobA").value }
    val b = ambientLoop(0f) { ambient.animateFloat(0f, 1f, cycle(3900, "b"), label = "blobB").value }
    val c = ambientLoop(0f) { ambient.animateFloat(0f, 1f, cycle(2300, "c"), label = "blobC").value }
    val hue = ambientLoop(0f) { ambient.animateFloat(0f, 1f, cycle(6000, "hue"), label = "blobHue").value }
    val turn = ambientLoop(0f) {
        ambient.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(16000, easing = OnyxMotion.Linear)),
            label = "blobTurn",
        ).value
    }
    val spin = ambientLoop(0f) {
        ambient.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2000, easing = OnyxMotion.Linear)),
            label = "blobSpin",
        ).value
    }

    // `.hueRotation(.degrees(hue ? 28 : -18))`
    val hueDegrees = -18f + 46f * hue
    // `.rotationEffect(turn)` + `.rotationEffect(spin)` : les deux s'additionnent, comme en la référence.
    val rotation = turn + if (swirling) spin else 0f

    // Offsets et échelles exacts de la référence, interpolés entre les deux états.
    data class Blob(val size: Float, val alpha: Float, val tint: Color, val x: Float, val y: Float, val scale: Float)
    val blobs = listOf(
        Blob(
            size = 187f,
            alpha = minOf(0.95f, 0.75f * strength),
            tint = color,
            x = -46f + 88f * a,           // a ? 42 : -46
            y = -26f + 55f * a,           // a ? 29 : -26
            scale = 0.85f + 0.35f * a,    // a ? 1.2 : 0.85
        ),
        Blob(
            size = 154f,
            alpha = minOf(0.85f, 0.55f * strength),
            tint = lerp(color, Color.White, 0.12f),
            x = 37f - 77f * b,            // b ? -40 : 37
            y = 24f - 61f * b,            // b ? -37 : 24
            scale = 1.25f - 0.45f * b,    // b ? 0.8 : 1.25
        ),
        Blob(
            size = 143f,
            alpha = minOf(0.85f, 0.60f * strength),
            tint = lerp(color, Color.White, 0.25f),
            x = -20f + 35f * c,           // c ? 15 : -20
            y = -44f + 88f * c,           // c ? 44 : -44
            scale = 0.85f + 0.30f * c,    // c ? 1.15 : 0.85
        ),
    )

    Canvas(
        modifier = modifier
            // Les taches sont dessinées dans un **calque carré** et, décalées, elles débordent de
            // leur boîte : le dégradé étant encore vif au bord, ce carré se voyait derrière l'orbe
            // (le pavé à bords droits de la capture). Le calque est donc découpé au cercle — avant
            // la rotation, pour que rien ne puisse sortir — et son bord s'éteint (voir le masque
            // ci-dessous), de sorte qu'aucune arête, carrée ni circulaire, n'apparaisse.
            .clip(CircleShape)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val w = size.width
        val h = size.height
        drawIntoCanvas { canvas ->
            // La rotation de teinte s'applique au **calque composé**, comme `.hueRotation`.
            val filter = android.graphics.ColorMatrixColorFilter(hueMatrix(hueDegrees))
            val paint = android.graphics.Paint().apply { colorFilter = filter }
            val saved = canvas.nativeCanvas.saveLayer(0f, 0f, w, h, paint)
            for (blob in blobs) {
                val centre = Offset(w / 2f + blob.x.dp.toPx(), h / 2f + blob.y.dp.toPx())
                val radius = blob.size.dp.toPx() * blob.scale / 2f
                val brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to blob.tint.copy(alpha = blob.alpha),
                        0.5f to blob.tint.copy(alpha = blob.alpha * 0.55f),
                        1.0f to blob.tint.copy(alpha = 0f),
                    ),
                    center = centre,
                    radius = radius,
                )
                drawCircle(brush = brush, radius = radius, center = centre, blendMode = BlendMode.Plus)
            }
            // Le masque qui éteint le calque vers son bord : un dégradé radial blanc → transparent
            // posé en `DST_IN`. La lumière s'éteint **avant** le bord, donc aucune arête — et
            // l'aura reste sobre et douce, quoi que fassent les taches.
            val mask = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    w / 2f,
                    h / 2f,
                    minOf(w, h) / 2f,
                    intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE, 0),
                    floatArrayOf(0f, 0.6f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            }
            canvas.nativeCanvas.drawRect(0f, 0f, w, h, mask)
            canvas.nativeCanvas.restoreToCount(saved)
        }
    }
}

/**
 * Matrice de rotation de teinte (la matrice `feColorMatrix type="hueRotate"` du SVG) : c'est une
 * transformation linéaire déterministe, pas une approximation. `setToSat` ne suffit pas ici.
 */
private fun hueMatrix(degrees: Float): android.graphics.ColorMatrix {
    val angle = degrees * Math.PI / 180.0
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    val m = android.graphics.ColorMatrix()
    val values = floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    m.set(values)
    return m
}
