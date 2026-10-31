package com.onyx.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Le curseur de la référence : une piste fine, et un pouce blanc qui glisse.
 *
 * Compose n'en a pas sans Material : celui-ci est dessiné, et se règle au glissement comme au tap.
 */
@Composable
fun OnyxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val theme = LocalOnyxTheme.current
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f

    fun valueAt(x: Float, width: Float): Float {
        val fraction = (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return range.start + fraction * span
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onValueChange(valueAt(offset.x, size.width.toFloat())) },
                ) { change, _ ->
                    onValueChange(valueAt(change.position.x, size.width.toFloat()))
                }
            }
            .pointerInput(range) {
                detectTapGestures { offset ->
                    onValueChange(valueAt(offset.x, size.width.toFloat()))
                }
            },
    ) {
        val trackHeight = 4.dp.toPx()
        val centerY = size.height / 2f
        drawRoundRect(
            color = theme.fill,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )
        val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
        val thumbX = fraction * size.width
        drawRoundRect(
            color = theme.accent,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(thumbX, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )
        drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 13.dp.toPx(), center = Offset(thumbX, centerY))
        drawCircle(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f),
            radius = 13.dp.toPx(),
            center = Offset(thumbX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx()),
        )
    }
}
