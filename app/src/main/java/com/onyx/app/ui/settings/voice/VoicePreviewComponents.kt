package com.onyx.app.ui.settings.voice

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Le petit ProgressView des réglages vocaux, sans composant Material. */
@Composable
internal fun VoicePreviewSpinner(size: Dp = 18.dp, tint: Color? = null) {
    val color = tint ?: LocalOnyxTheme.current.secondaryText
    val transition = rememberInfiniteTransition(label = "voicePreviewSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800)),
        label = "voicePreviewSpinnerAngle",
    )
    Canvas(modifier = Modifier.size(size).rotate(angle)) {
        drawArc(
            color = color,
            startAngle = 35f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

internal val VoicePreviewGlyphSize = 20.dp
internal val VoicePreviewTouchSize = 36.dp

/** `play.circle` / `stop.circle.fill`, avec le libellé d'accessibilité de KokoroVoicesView. */
@Composable
internal fun VoicePreviewButton(playing: Boolean, label: String, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(VoicePreviewTouchSize)
            .onyxClickable(onClick = onClick)
            .semanticsLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(VoicePreviewGlyphSize)) {
            val stroke = 1.7.dp.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            if (playing) {
                drawCircle(color = theme.accent, radius = radius, center = center)
                val side = size.minDimension * 0.27f
                drawRoundRect(
                    color = theme.surfaceColor,
                    topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
                    size = Size(side, side),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            } else {
                drawCircle(color = theme.accent, radius = radius, center = center, style = Stroke(width = stroke))
                val half = size.minDimension * 0.16f
                val path = Path().apply {
                    moveTo(center.x - half * 0.65f, center.y - half)
                    lineTo(center.x + half, center.y)
                    lineTo(center.x - half * 0.65f, center.y + half)
                    close()
                }
                drawPath(path, color = theme.accent)
            }
        }
    }
}
