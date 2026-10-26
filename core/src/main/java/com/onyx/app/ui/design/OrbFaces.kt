package com.onyx.app.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.core.R
import com.onyx.app.localization.OnyxLocalization

/**
 * Les 22 orbes de `design/icons/orbs`, utilisés comme visages de bots.
 *
 * C'est ce que la grille des bots montre: les orbes dessinés de l'app (wave / blank ×
 * matte, glass, gold, ink, navy), un par bot. Le choix est stable pour un nom donné.
 */
object OrbFaces {

    @DrawableRes
    val all: List<Int> = listOf(
        R.drawable.ic_glasswave,
        R.drawable.ic_glasswaveblank,
        R.drawable.ic_flatwave,
        R.drawable.ic_flatwaveblank,
        R.drawable.ic_goldwave,
        R.drawable.ic_goldwaveblank,
        R.drawable.ic_inkwave,
        R.drawable.ic_inkwaveblank,
        R.drawable.ic_mattewave,
        R.drawable.ic_mattewaveblank,
        R.drawable.ic_navyglasswave,
        R.drawable.ic_navyglasswaveblank,
        R.drawable.ic_navymattewave,
        R.drawable.ic_navymattewaveblank,
        R.drawable.ic_steelglasswave,
        R.drawable.ic_steelglasswaveblank,
        R.drawable.ic_bubblewave,
        R.drawable.ic_bubblewaveblank,
        R.drawable.ic_bubblemattewave,
        R.drawable.ic_bubblemattewaveblank,
        R.drawable.ic_waveform,
        R.drawable.ic_waveformblank,
    )

    /** L'orbe d'un bot: le même à chaque fois, pour un nom donné. */
    @DrawableRes
    fun forName(name: String): Int {
        val hash = name.lowercase().fold(11) { acc, c -> acc * 31 + c.code }
        return all[Math.floorMod(hash, all.size)]
    }
}

/**
 * Le visage d'un bot: l'un des 22 orbes, à la taille demandée.
 *
 * L'orbe décrit ce qu'il est à qui ne le voit pas; sans description donnée, il se nomme d'après le
 * bot. `ombre` pose sous l'orbe la même ombre douce que sous un visage dessiné: utile quand l'orbe
 * tient lieu de visage, à laisser de côté dans une grille où les ombres se marcheraient dessus.
 */
@Composable
fun OrbAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    @DrawableRes override: Int? = null,
    contentDescription: String? = null,
    ombre: Boolean = false,
) {
    val orb = remember(name, override) { override ?: OrbFaces.forName(name) }
    Image(
        painter = painterResource(orb),
        contentDescription = contentDescription ?: OnyxLocalization.format("Orb of %1\$s", name),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .then(if (ombre) Modifier.drawBehind { drawOrbShadow() } else Modifier),
    )
}

/** L'ombre portée sous un orbe, douce, comme celle d'un visage dessiné. */
private fun DrawScope.drawOrbShadow() {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(this.size.width / 2f, this.size.height * 0.95f),
            radius = this.size.minDimension * 0.46f,
        ),
        topLeft = Offset(this.size.width * 0.08f, this.size.height * 0.86f),
        size = Size(this.size.width * 0.84f, this.size.height * 0.15f),
    )
}
