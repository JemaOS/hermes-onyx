package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Le verre de l'app (`glassEffect`).
 *
 * Android n'expose pas de flou d'arrière-plan sur une surface Compose; on approche le rendu avec
 * un dégradé translucide et un liseré clair, ce qui donne la même lecture: une pastille posée
 * au-dessus de la page, plus claire en haut qu'en bas, détachée par un bord fin.
 */
fun Modifier.glassSurface(
    shape: Shape = CircleShape,
    tint: Color? = null,
    intensity: Float = 1f,
): Modifier = composed {
    val theme = LocalOnyxTheme.current
    val base = tint ?: theme.surfaceColor
    val topAlpha = (if (theme.dark) 0.34f else 0.78f) * intensity
    val bottomAlpha = (if (theme.dark) 0.22f else 0.62f) * intensity
    val borderAlpha = if (theme.dark) 0.16f else 0.55f

    this
        .background(
            brush = Brush.verticalGradient(
                listOf(
                    base.copy(alpha = topAlpha),
                    base.copy(alpha = bottomAlpha),
                ),
            ),
            shape = shape,
        )
        .border(0.5.dp, Color.White.copy(alpha = borderAlpha), shape)
}

/** Une pastille arrondie à coins droits (champs, cartes, barres). */
@Composable
fun glassCardShape(radius: androidx.compose.ui.unit.Dp) = RoundedCornerShape(radius)

/** Le fond des barres: translucide, sans bord. */
fun Modifier.barBackground(): Modifier = composed {
    val theme = LocalOnyxTheme.current
    background(theme.pageColor.copy(alpha = if (theme.dark) 0.86f else 0.9f))
}
