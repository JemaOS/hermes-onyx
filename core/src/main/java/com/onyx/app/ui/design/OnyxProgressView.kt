package com.onyx.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Le `ProgressView` de la référence : un arc qui tourne, dessiné à la main — aucun composant Material n'a sa
 * place ici. Partagé par les écrans qui attendent le serveur, comme le `ProgressView` de la référence.
 */
@Composable
fun OnyxProgressView(size: Dp = 20.dp, tint: Color? = null, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val color = tint ?: theme.secondaryText
    val transition = rememberInfiniteTransition(label = "progress")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier = modifier.size(size).rotate(angle)) {
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = this.size.minDimension * 0.18f, cap = StrokeCap.Round),
        )
    }
}
