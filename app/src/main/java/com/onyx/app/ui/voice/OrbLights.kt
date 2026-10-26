package com.onyx.app.ui.voice

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.ambientLoop
import kotlin.math.cos
import kotlin.math.sin

/** Les trois lumières de `OrbLight`. */
enum class OrbLight(val id: String, val label: String, val detail: String) {
    NEBULA("nebula", "Nebula", "Colour drifting and turning"),
    AURORA("aurora", "Aurora", "Ribbons of light sweeping across"),
    CORE("core", "Core", "A pulsing heart with sparks around it"),
    ;

    companion object {
        /** La lumière enregistrée, ou Nebula quand rien ne correspond (défaut ). */
        fun fromId(raw: String?): OrbLight = entries.firstOrNull { it.id == raw } ?: NEBULA
    }
}

private fun argb(color: Color, alpha: Float): Int =
    color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()

private fun additivePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    // `blendMode(.plusLighter)`: addition des canaux, sans remplacement.
    xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
}

/**
 * `AuroraLight`: trois capsules de 300 pt de large, floues à 9 pt,
 * en dégradé horizontal transparent → teinte → autre teinte → transparent, avec leurs offsets,
 * opacités et durées exactes. Les shaders sont ceux d'Android pour éviter une réécriture
 * approximative du flou et du mode de fusion.
 */
@Composable
fun AuroraLight(color: Color, strength: Float, swirling: Boolean, modifier: Modifier = Modifier) {
    val ambient = rememberInfiniteTransition(label = "aurora")
    val animatedStrength by animateFloatAsState(
        targetValue = strength,
        animationSpec = OnyxMotion.easeOut(OnyxMotion.MS_250),
        label = "auroraStrength",
    )
    val speed = if (swirling) 0.45f else 1.0f
    fun loop(seconds: Float) = infiniteRepeatable(
        animation = tween<Float>((seconds * 1000).toInt(), easing = OnyxMotion.EaseInOut),
        repeatMode = RepeatMode.Reverse,
    )
    // `AuroraLight.start { guard !reduceMotion else { return } }`:
    // les rubans restent à leur repos (offsets de départ, inclinaison +10°).
    val a = ambientLoop(0f) { ambient.animateFloat(0f, 1f, loop(4.0f * speed), label = "auroraA").value }
    val b = ambientLoop(0f) { ambient.animateFloat(0f, 1f, loop(5.5f * speed), label = "auroraB").value }
    val c = ambientLoop(0f) { ambient.animateFloat(0f, 1f, loop(3.2f * speed), label = "auroraC").value }
    val tilt = ambientLoop(0f) { ambient.animateFloat(0f, 1f, loop(7f * speed), label = "auroraTilt").value }
    val rotation = 10f - 20f * tilt

    data class Ribbon(val y: Float, val height: Float, val alpha: Float, val from: Color, val to: Color, val x: Float)
    val ribbons = listOf(
        Ribbon(-36f, 56f, minOf(0.85f, 0.65f * animatedStrength), color, lerp(color, Color.White, 0.12f), -70f + 140f * a),
        Ribbon(6f, 50f, minOf(0.9f, 0.7f * animatedStrength), lerp(color, Color(0xFF800080), 0.5f), color, 62f - 124f * b),
        Ribbon(44f, 42f, minOf(0.8f, 0.55f * animatedStrength), lerp(color, Color(0xFF98FB98), 0.45f), color, -54f + 108f * c),
    )

    Canvas(modifier = modifier.graphicsLayer { rotationZ = rotation }) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (ribbon in ribbons) {
                val cx = size.width / 2f + ribbon.x.dp.toPx()
                val cy = size.height / 2f + ribbon.y.dp.toPx()
                val halfW = 150.dp.toPx()
                val halfH = (ribbon.height / 2f).dp.toPx()
                val rect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
                val paint = additivePaint().apply {
                    shader = LinearGradient(
                        rect.left, rect.top, rect.right, rect.top,
                        intArrayOf(
                            argb(ribbon.from, 0f),
                            argb(ribbon.from, ribbon.alpha),
                            argb(ribbon.to, ribbon.alpha),
                            argb(ribbon.from, 0f),
                        ),
                        null,
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                    maskFilter = BlurMaskFilter(9.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
                native.drawRoundRect(rect, halfH, halfH, paint)
            }
        }
    }
}

/**
 * `CoreLight`: noyau radial de 124 pt, étincelles sur deux orbites
 * inverses (66 et 54 pt) et trois particules montantes avec délai de 1,1 s. Durées reprises:
 * pulse 2,2 s, orbites 9 s / 2,2 s en réflexion, montées 3,4 s en `easeIn`.
 */
@Composable
fun CoreLight(color: Color, strength: Float, swirling: Boolean, modifier: Modifier = Modifier) {
    val ambient = rememberInfiniteTransition(label = "core")
    val animatedStrength by animateFloatAsState(
        targetValue = strength,
        animationSpec = OnyxMotion.easeOut(OnyxMotion.MS_250),
        label = "coreStrength",
    )
    // `CoreLight.onAppear { guard !reduceMotion else { return } }`:
    // rien ne tourne — le pulse, les orbites et les montées restent à leur repos.
    val pulse = ambientLoop(0f) {
        ambient.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2200, easing = OnyxMotion.EaseInOut), RepeatMode.Reverse),
            label = "corePulse",
        ).value
    }
    val orbitDuration = if (swirling) 2200 else 9000
    val orbit = ambientLoop(0f) {
        ambient.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(orbitDuration, easing = OnyxMotion.Linear)),
            label = "coreOrbit",
        ).value
    }
    val rises = List(3) { index ->
        ambientLoop(0f) {
            ambient.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(3400, delayMillis = index * 1100, easing = OnyxMotion.EaseIn),
                    RepeatMode.Restart,
                ),
                label = "coreRise$index",
            ).value
        }
    }
    val nucleusScale = 0.94f + 0.24f * pulse
    val nucleusTint = lerp(color, Color.White, 0.2f)
    val sparkTint = lerp(color, Color.White, 0.6f)
    val secondSparkTint = lerp(color, Color(0xFF800080), 0.5f)

    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val cx = size.width / 2f
            val cy = size.height / 2f
            val nucleusRadius = 62.dp.toPx() * nucleusScale
            val nucleusPaint = additivePaint().apply {
                shader = RadialGradient(
                    cx, cy, nucleusRadius,
                    intArrayOf(
                        argb(nucleusTint, minOf(1f, 0.95f * animatedStrength)),
                        argb(color, minOf(0.9f, 0.6f * animatedStrength)),
                        argb(color, 0f),
                    ),
                    null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
            }
            native.drawCircle(cx, cy, nucleusRadius, nucleusPaint)

            fun drawSparks(angles: List<Float>, radius: Float, tint: Color, rotation: Float) {
                for (angle in angles) {
                    val rad = (angle + rotation) * Math.PI / 180.0
                    val x = cx + (radius * cos(rad)).toFloat().dp.toPx()
                    val y = cy + (radius * sin(rad)).toFloat().dp.toPx()
                    val paint = additivePaint().apply {
                        this.color = argb(tint, minOf(1f, 0.9f * animatedStrength))
                        setShadowLayer(4.dp.toPx(), 0f, 0f, argb(tint, 0.9f))
                    }
                    native.drawCircle(x, y, 2.5.dp.toPx(), paint)
                }
            }
            drawSparks(listOf(0f, 120f, 240f), 66f, sparkTint, orbit)
            drawSparks(listOf(60f, 180f, 300f), 54f, secondSparkTint, -orbit)

            rises.forEachIndexed { index, rise ->
                val t = rise
                val diameter = (5 + index).dp.toPx()
                val x = cx + (index - 1) * 30.dp.toPx()
                val y = cy + (60f - 130f * t).dp.toPx()
                val paint = additivePaint().apply {
                    this.color = argb(lerp(color, Color.White, 0.5f), minOf(0.9f, 0.7f * animatedStrength) * (1f - t))
                }
                native.drawCircle(x, y, diameter / 2f, paint)
            }
        }
    }
}
