package com.onyx.app.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay

/**
 * Une chose qui s'ouvre depuis ce qu'on a touché (`OpeningCover`).
 *
 * Une surface grandit depuis la chose touchée jusqu'à couvrir ce qui est derrière, ce qui a été
 * ouvert est échangé dessous, puis la surface s'efface par-dessus.
 */
@Composable
fun OpeningCover(
    source: Rect?,
    title: String,
    /** Ce qui est découvert sous la surface ; `null` : rien à échanger (`OpeningCover.run`). */
    onSwapped: (() -> Unit)? = null,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (source == null) return
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var expanded by remember { mutableStateOf(false) }
    var swapped by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    val growth by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = OnyxMotion.Sites.CoverGrowth,
        label = "openingGrowth",
    )
    val fade by animateFloatAsState(
        targetValue = if (clearing) 0f else 1f,
        animationSpec = OnyxMotion.Sites.CoverFade,
        label = "openingFade",
    )

    LaunchedEffect(Unit) {
        expanded = true
        delay(300)
        swapped = true
        onSwapped?.invoke()
        delay(60)
        clearing = true
        delay(240)
        onFinished()
    }

    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val startLeft = with(density) { source.left.toDp().value }
    val startTop = with(density) { source.top.toDp().value }
    val startWidth = with(density) { source.width.toDp().value }
    val startHeight = with(density) { source.height.toDp().value }

    val left = startLeft + (0f - startLeft) * growth
    val top = startTop + (0f - startTop) * growth
    val width = startWidth + (screenWidth - startWidth) * growth
    val height = startHeight + (screenHeight - startHeight) * growth
    val radius = 16f * (1f - growth)

    Box(modifier = modifier.fillMaxSize().zIndex(10f)) {
        Box(
            modifier = Modifier
                .offset(x = left.dp, y = top.dp)
                .size(width = width.dp, height = height.dp)
                .background(theme.pageColor, RoundedCornerShape(radius.dp))
                .alpha(fade),
        )
        if (!swapped && title.isNotEmpty()) {
            BasicText(
                text = title,
                style = theme.body.copy(color = theme.textColor),
                modifier = Modifier.padding(start = (startLeft + 16f).dp, top = startTop.dp),
            )
        }
    }
}
