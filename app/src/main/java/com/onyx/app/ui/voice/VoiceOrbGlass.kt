package com.onyx.app.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.voice.VoicePhase
import kotlin.math.pow

/**
 * L'orbe de la voix, **dessiné** et non plus une image.
 *
 * Ce qui était cassé, et pourquoi :
 *
 *  - l'orbe était un PNG (`ic_dark_glass`) posé par-dessus la lumière. Ce PNG porte **sa propre
 *    couture** : un croissant bleu nuit franc en bas à droite du disque, là où son dégradé se
 *    raccorde. Aucun réglage d'alpha ne le rattrape, il est dans les pixels. L'orbe est donc
 *    maintenant **entièrement calculé** : un disque de couleur unie, la lumière par-dessus, puis
 *    la finition (vignette, reflet, liseré) — la forme que `GlassOrb` de la référence dessine,
 *    elle aussi, sans image.
 *  - les anneaux concentriques venaient de deux sources : un `border(3.dp)` dur collé au disque et
 *    les `VoiceHalos` (des `drawCircle(style = Stroke)` empilés à faible opacité). Deux cercles
 *    tracés à quelques pixels l'un de l'autre se lisent comme un **liseré double**, et leurs
 *    raccords comme des marches. Il ne reste qu'**un halo** : un seul dégradé radial, à arrêts
 *    progressifs, dont la luminance décroît sans palier ni marche.
 *  - l'aura avait un **plateau** (opacité constante jusqu'à 66 pt puis extinction linéaire) : le
 *    passage du plateau à l'extinction est une rupture de pente, visible comme un bord. Remplacé
 *    par une extinction continue.
 *
 * Tout est mesuré en points du disque de 190 (`GlassOrb.diameter`), comme la référence.
 */

/**
 * `GlassOrb.face` : le fond du disque de verre, plus sombre selon la lumière allumée dedans.
 *
 * Les trois fonds portaient une **teinte** — un bleu de nébuleuse, un bleu d'aurore — de 19 à 22
 * points d'écart entre le rouge et le bleu : c'étaient les « accents qui tirent au violet » relevés
 * sur le bouton vocal. Ils gardent leur **hiérarchie** (la nébuleuse est la plus claire, le cœur le
 * plus sombre, et c'est ce qui les distingue à l'écran) mais prennent les **barreaux neutres** de la
 * référence : plus aucune couleur n'est peinte ici en dehors de l'accent.
 */
internal fun orbFaceColour(light: OrbLight): Color = when (light) {
    OrbLight.NEBULA -> ReferencePalette.DarkSurface2
    OrbLight.AURORA -> ReferencePalette.DarkSurface
    OrbLight.CORE -> ReferencePalette.DarkPage
}

/** Le diamètre du halo à l'écoute, et au repos (les nombres de `OrbFace` de la référence). */
private const val OrbHaloFrameLive = 297f
private const val OrbHaloFrameRest = 216f

/** Combien le halo s'ouvre encore avec la voix, en points. */
private const val OrbHaloGrow = 55f

/**
 * Le cadre de la lumière : **l'échelle du disque**. `GlassOrb` de la référence dessine sa lumière
 * dans le cadre du disque, et son masque (la moitié du cadre) s'éteint donc pile au bord du disque.
 * Un cadre plus petit découpait la lumière avant ce bord et laissait un anneau sombre à
 * l'intérieur — vu sur la capture, écarté.
 */
internal val OrbLightFrame = OrbFaceDiameter.dp

/**
 * Les arrêts du halo sont **calculés**, pas écrits à la main : une poignée d'arrêts fait des
 * **bandes de Mach** — l'œil exagère chaque rupture de pente en un anneau, même quand la couleur,
 * elle, est continue. C'est ce qui restait visible autour de l'orbe (« des anneaux concentriques »).
 * On échantillonne donc une courbe lisse en [HaloSteps] pas : les ruptures de pente disparaissent
 * sous le seuil de perception, et la courbe atterrit sans pente d'un coup au bord.
 */
private const val HaloSteps = 32

/** Le halo tient son plein jusqu'ici (ce plateau est **caché sous le disque** : 0,64 R). */
private const val HaloPlateau = 0.55f

/** L'exposant de l'extinction : supérieur à 1 pour que la pente s'annule au bord du halo. */
private const val HaloFalloff = 1.5f

/** L'opacité du halo à une fraction `t` de son rayon — la courbe, isolée pour être éprouvée. */
internal fun orbHaloAlpha(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    if (x <= HaloPlateau) return 1f
    val k = (1f - x) / (1f - HaloPlateau)
    return k.pow(HaloFalloff)
}

/**
 * L'**unique** halo de l'orbe : un seul dégradé radial, jamais un cercle tracé.
 *
 * Le halo ne se reconstruit pas quand la voix bouge : le dégradé est **mémorisé** et c'est le
 * calque qui s'ouvre et s'éclaircit avec `amplitude` (`graphicsLayer`), donc aucun dégradé n'est
 * recalculé à chaque image.
 */
@Composable
internal fun VoiceOrbHalo(
    color: Color,
    listening: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val frame = if (listening) OrbHaloFrameLive else OrbHaloFrameRest
    val radius = with(density) { (frame / 2f).dp.toPx() }
    val peak = if (listening) 0.30f else 0.13f
    val stops = remember(color, listening) {
        Array(HaloSteps + 1) { index ->
            val t = index.toFloat() / HaloSteps
            val alpha = orbHaloAlpha(t) * peak
            t to if (alpha <= 0.0004f) Color.Transparent else color.copy(alpha = alpha)
        }
    }
    val brush = remember(stops) {
        Brush.radialGradient(colorStops = stops, center = Offset.Unspecified, radius = radius)
    }
    val grow = if (listening) 1f + amplitude * (OrbHaloGrow / frame) else 1f
    Box(
        modifier = modifier
            .size(frame.dp)
            .background(brush, CircleShape)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
                alpha = 0.88f + amplitude * 0.12f
            },
    )
}

/**
 * L'ombre douce sous la sphère (`GlassOrb.shadow` : `radius 30`, `y 20`, la teinte de l'état).
 *
 * L'ombre suit les nombres de la référence : un décalage de 20 points vers le bas et un flou de
 * 30. La première version posait un très large dégradé teinté — sur une page sombre il se lisait
 * comme un **anneau** bleu nuit autour de la sphère, exactement le défaut qu'on corrige. Ici le
 * dégradé tient son plein jusqu'à 74 % de son rayon (partie cachée par le disque) puis s'éteint :
 * il ne reste qu'un croissant sous la sphère, et rien qui fasse le tour.
 */
@Composable
internal fun OrbSoftShadow(tint: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val radius = with(density) { (OrbFaceDiameter / 2f).dp.toPx() }
    val centre = Offset(radius, radius + 0.21f * radius)
    val outer = radius + 0.32f * radius
    val brush = remember(tint, radius) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to tint.copy(alpha = 0.24f),
                0.74f to tint.copy(alpha = 0.24f),
                0.90f to tint.copy(alpha = 0.10f),
                1f to Color.Transparent,
            ),
            center = centre,
            radius = outer,
        )
    }
    Canvas(modifier.size(OrbFaceDiameter.dp)) {
        drawCircle(brush = brush, radius = outer, center = centre)
    }
}

/**
 * La finition du verre, par-dessus la lumière : la vignette qui assombrit le bord, le reflet
 * spéculaire en haut, et **un seul** liseré fin de la couleur du thème.
 *
 * - La **vignette** est un dégradé radial transparent au centre qui s'assombrit vers le bord :
 *   elle donne le volume de la sphère sans jamais tracer de bord.
 * - Le **reflet** est un dégradé radial blanc posé à 42 % au-dessus du centre : la lumière qui
 *   accroche le verre. Très ténu (13 % au plus) — un reflet appuyé ferait une seconde pastille.
 * - Le **liseré** est un `Stroke` **unique** dont la couleur s'éclaircit en haut et s'éteint en
 *   bas (`Brush.sweepGradient`) : c'est la lumière sur la tranche du verre, pas un anneau. Un seul
 *   trait — un second à quelques pixels ferait exactement le « liseré double » signalé.
 */
@Composable
internal fun OrbGlassFinishing(
    phaseColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radius = with(density) { (OrbFaceDiameter / 2f).dp.toPx() }
    val stroke = with(density) { 1.5.dp.toPx() }
    val vignette = remember(radius) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.58f to Color.Transparent,
                0.80f to Color.Black.copy(alpha = 0.16f),
                0.93f to Color.Black.copy(alpha = 0.34f),
                1f to Color.Black.copy(alpha = 0.48f),
            ),
            center = Offset.Unspecified,
            radius = radius,
        )
    }
    val specular = remember(radius) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.13f),
                0.55f to Color.White.copy(alpha = 0.05f),
                1f to Color.Transparent,
            ),
            center = Offset(radius, radius * 0.58f),
            radius = radius * 0.60f,
        )
    }
    val rim = remember(phaseColor) {
        // Le balayage part à droite (0) puis descend (0,25), à gauche (0,5), en haut (0,75) : le
        // haut est le plus clair, le bas le plus éteint — la lumière vient d'en haut.
        Brush.sweepGradient(
            colors = listOf(
                phaseColor.copy(alpha = 0.16f),
                phaseColor.copy(alpha = 0.06f),
                phaseColor.copy(alpha = 0.10f),
                phaseColor.copy(alpha = 0.34f),
                phaseColor.copy(alpha = 0.16f),
            ),
            center = Offset(radius, radius),
        )
    }
    Canvas(modifier.size(OrbFaceDiameter.dp)) {
        drawCircle(brush = vignette, radius = radius)
        drawCircle(brush = specular, radius = radius * 0.60f, center = Offset(radius, radius * 0.58f))
        drawCircle(
            brush = rim,
            radius = radius - stroke / 2f,
            style = Stroke(width = stroke),
        )
    }
}

/**
 * Le disque de verre complet : la face, la lumière choisie par-dessus, la finition, la vague.
 * Aucune image, donc aucune couture héritée d'une image.
 */
@Composable
internal fun LiquidOrbBody(
    light: OrbLight,
    phase: VoicePhase,
    phaseColor: Color,
    strength: Float,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val swirling = phase == VoicePhase.THINKING
    Box(modifier, contentAlignment = Alignment.Center) {
        // 1. La face : un aplat, pas un dégradé d'image. Le volume vient de la lumière.
        Box(
            modifier = Modifier
                .size(OrbFaceDiameter.dp)
                .background(orbFaceColour(light), CircleShape),
        )
        // 2. La lumière — les mêmes trois que la référence, dessinées, jamais une image.
        // À l'échelle du disque : le masque de la lumière s'éteint alors **pile au bord du disque**
        // (il vaut la moitié du cadre), donc la lueur remplit le disque sans jamais poser d'anneau
        // sombre à l'intérieur de son bord. Un cadre plus petit (172 dp) coupait la lumière avant le
        // bord et laissait un anneau noir — essayé, mesuré à la capture, écarté.
        when (light) {
            OrbLight.NEBULA -> DriftingBlobs(
                color = phaseColor,
                strength = strength,
                swirling = swirling,
                modifier = Modifier.size(OrbLightFrame),
            )

            OrbLight.AURORA -> AuroraLight(
                color = phaseColor,
                strength = strength,
                swirling = swirling,
                modifier = Modifier.size(OrbLightFrame),
            )

            OrbLight.CORE -> CoreLight(
                color = phaseColor,
                strength = strength,
                swirling = swirling,
                modifier = Modifier.size(OrbLightFrame),
            )
        }
        // 3. La finition du verre.
        OrbGlassFinishing(phaseColor = phaseColor)
        // 4. La vague, vivante, par-dessus le disque.
        LiquidOrbWave(
            phase = phase,
            level = level,
            modifier = Modifier.size(OrbFaceDiameter.dp),
        )
    }
}
