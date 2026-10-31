package com.onyx.app.ui.computer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onyx.app.data.net.ComputerFit
import com.onyx.app.data.net.ComputerGain
import com.onyx.app.data.net.ComputerPoint
import com.onyx.app.data.net.ComputerPointer
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlin.math.roundToInt

/**
 * Le curseur de l'aperçu : **où est le pointeur du PC**, dessiné chez nous.
 *
 * C'est ce qui manquait le plus : le doigt *était* le pointeur, donc on ne voyait jamais la flèche
 * du PC et on ne savait pas ce qu'on allait cliquer. Ici, le pointeur se dessine à sa position
 * réelle — un anneau clair (contraste garanti sur un bureau clair comme sombre) avec un point
 * sombre au centre, exactement sur le pixel qui partira au PC. Pendant un appui long, un anneau de
 * **progression** se remplit jusqu'au clic droit, et **s'efface** si le doigt s'éloigne : le geste
 * est annulable, il le voit.
 *
 * En **mode précision**, une **loupe** se pose au-dessus du doigt : la zone entourant le pointeur y
 * est agrandie, avec une croix de visée. On clique alors sur une case minuscule sans que le doigt
 * couvre la cible — l'appareil de visée qui manquait à un bureau 4K.
 */
@Composable
internal fun ComputerCursorOverlay(
    pointer: ComputerPoint,
    fit: ComputerFit,
    screenWidth: Int,
    screenHeight: Int,
    image: ImageBitmap?,
    gain: ComputerGain,
    precise: Boolean,
    pressProgress: Float,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val accent = theme.accent
    if (screenWidth <= 0 || screenHeight <= 0) return
    val (viewX, viewY) = ComputerPointer.toView(pointer, fit, screenWidth, screenHeight)

    Canvas(modifier = modifier.fillMaxSize()) {
        val at = Offset(viewX, viewY)
        val radius = if (precise) 11.dp.toPx() else 9.dp.toPx()

        // La loupe d'abord : elle se pose au-dessus du curseur, jamais dessous.
        if (precise || pressProgress > 0f) {
            drawLoupe(image = image, at = at, pointer = pointer, fit = fit,
                screenWidth = screenWidth, screenHeight = screenHeight,
                radius = 46.dp.toPx(), magnification = 2.4f, theme = theme, accent = accent)
        }

        // Le halo clair : la lisibilité du curseur sur n'importe quel fond.
        drawCircle(color = Color.White.copy(alpha = 0.92f), radius = radius, center = at)
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = radius,
            center = at,
            style = Stroke(width = 1.6.dp.toPx()),
        )
        // Le cœur : ce qui marque le pixel exact qui partira au PC.
        drawCircle(color = Color.Black.copy(alpha = 0.88f), radius = 2.2.dp.toPx(), center = at)
        if (gain == ComputerGain.PRECISION || gain == ComputerGain.FAST) {
            // Le mode se lit sur le curseur sans quitter l'image des yeux : un cran de plus.
            drawCircle(color = accent, radius = radius, center = at, style = Stroke(width = 1.6.dp.toPx()))
        }

        // La croix de visée : quatre traits courts, pour poser le pixel au croisement.
        val tick = 5.dp.toPx()
        val gap = radius + 2.dp.toPx()
        val cross = accent.copy(alpha = 0.95f)
        drawLine(cross, Offset(at.x - gap - tick, at.y), Offset(at.x - gap, at.y), 1.4.dp.toPx())
        drawLine(cross, Offset(at.x + gap, at.y), Offset(at.x + gap + tick, at.y), 1.4.dp.toPx())
        drawLine(cross, Offset(at.x, at.y - gap - tick), Offset(at.x, at.y - gap), 1.4.dp.toPx())
        drawLine(cross, Offset(at.x, at.y + gap), Offset(at.x, at.y + gap + tick), 1.4.dp.toPx())

        // La progression du clic droit : un anneau qui se referme, et s'efface si l'on s'éloigne.
        if (pressProgress > 0f) {
            val sweep = pressProgress.coerceIn(0f, 1f)
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(at.x - radius * 1.7f, at.y - radius * 1.7f),
                size = Size(radius * 3.4f, radius * 3.4f),
                style = Stroke(width = 2.4.dp.toPx()),
            )
        }
    }
}

/**
 * La loupe : la région du pointeur, agrandie, dans un disque — avec la croix au centre.
 *
 * Elle se **pose au-dessus** du doigt (et se rabat en dessous en haut de l'écran) pour ne pas
 * cacher ce qu'on vise : le doigt couvre déjà le pixel, la loupe le montre ailleurs.
 */
private fun DrawScope.drawLoupe(
    image: ImageBitmap?,
    at: Offset,
    pointer: ComputerPoint,
    fit: ComputerFit,
    screenWidth: Int,
    screenHeight: Int,
    radius: Float,
    magnification: Float,
    theme: com.onyx.app.ui.theme.ResolvedTheme,
    accent: Color,
) {
    val above = at.y - radius * 2.1f
    val center = Offset(
        x = at.x.coerceIn(radius + 4f, size.width - radius - 4f),
        y = (if (above - radius >= 4f) above else at.y + radius * 2.1f)
            .coerceIn(radius + 4f, size.height - radius - 4f),
    )
    val circle = Path().apply { addOval(
        androidx.compose.ui.geometry.Rect(
            left = center.x - radius,
            top = center.y - radius,
            right = center.x + radius,
            bottom = center.y + radius,
        ),
    ) }

    clipPath(circle) {
        // Le fond de la loupe : la scène du thème, pour un disque opaque.
        drawCircle(theme.stageColor, radius = radius, center = center)
        if (image != null && fit.width > 0f && fit.height > 0f) {
            val halfView = radius / magnification
            val imageX = pointer.x.toFloat() / screenWidth * image.width
            val imageY = pointer.y.toFloat() / screenHeight * image.height
            val halfImageX = halfView / fit.width * image.width
            val halfImageY = halfView / fit.height * image.height
            val left = (imageX - halfImageX).roundToInt().coerceIn(0, (image.width - 1).coerceAtLeast(0))
            val top = (imageY - halfImageY).roundToInt().coerceIn(0, (image.height - 1).coerceAtLeast(0))
            val width = (halfImageX * 2f).roundToInt().coerceIn(1, image.width - left)
            val height = (halfImageY * 2f).roundToInt().coerceIn(1, image.height - top)
            drawImage(
                image = image,
                srcOffset = IntOffset(left, top),
                srcSize = IntSize(width, height),
                dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
                dstSize = IntSize((radius * 2f).roundToInt(), (radius * 2f).roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }

    // Le bord, puis la croix de visée : le point à cliquer est au croisement.
    drawCircle(Color.White.copy(alpha = 0.95f), radius = radius, center = center, style = Stroke(2f))
    drawCircle(accent.copy(alpha = 0.9f), radius = radius, center = center, style = Stroke(1f))
    val inner = radius * 0.55f
    drawLine(accent, Offset(center.x - inner, center.y), Offset(center.x + inner, center.y), 1.4f)
    drawLine(accent, Offset(center.x, center.y - inner), Offset(center.x, center.y + inner), 1.4f)
}
