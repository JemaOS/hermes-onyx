package com.onyx.app.ui.voice

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onyx.app.core.R
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.ambientLoop
import com.onyx.app.voice.VoicePhase
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Les hauteurs que la vague prend avec la voix: le centre le plus haut, chaque barre vers
 * l'extérieur un cran plus bas, symétrique. **Sept** barres, comme `LiveWaveform.weights` de la
 * référence — les deux barres des extrémités en font partie: ce sont des points de la vague, pas
 * des parasites.
 */
private val LiveWaveWeights = floatArrayOf(0.13f, 0.27f, 0.51f, 1f, 0.51f, 0.27f, 0.13f)

/**
 * Une barre figée du PNG est **claire et sans couleur**; le disque autour est bleu et sombre.
 * C'est ce contraste qui la fait trouver dans l'image, sans géométrie supposée.
 */
private const val FrozenBarColourTolerance = 48
private const val FrozenBarMinLuminance = 140

/** La part de la bande centrale qu'une colonne doit couvrir pour être une barre. */
private const val FrozenBarMinCoverage = 0.25f

/** La marge laissée de chaque côté d'une barre trouvée, en points du disque (190 pt). */
private const val FrozenBarPadPoints = 0.75f

private const val LiveWaveRest = 6f
private const val LiveWaveTallest = 88f
private const val LiveWaveGate = 0.06f
private const val LiveWaveGain = 1.4f
private val LiveWaveColor = Color(0xFFDCE3EE)

/** La largeur d'une barre, l'écart entre deux, dans le repère de 190 points du disque. */
private const val LiveWaveBarWidth = 10f
private const val LiveWaveBarSpacing = 5.5f

/** Le bas d'une barre est très légèrement fondu: la lumière du disque descend. */
private const val LiveWaveBottomAlpha = 0.88f

/** Le disque de `ic_dark_glass` est dessiné comme un orbe de 190 points. */
private const val ORB_POINT_SIZE = 190f

/**
 * `GlassOrb.overlay { LiveWaveform(...) }`.
 *
 * Le PNG reste la source de tous les pixels de l'orbe. Ses sept barres figées sont découpées dans
 * un calque hors écran (`BlendMode.Clear`), puis remplacées par les barres vivantes. Le reste du
 * disque n'est ni repeint ni remplacé par une approximation vectorielle.
 */
/**
 * `GlassOrb.overlay { LiveWaveform(...) }`.
 *
 * Le PNG de verre donne le disque; **ses sept barres figées sont effacées du bitmap au
 * chargement**, puis remplacées par les barres vivantes, dessinées en rectangles arrondis qui
 * montent et descendent avec la voix.
 *
 * Ce qui a changé, et pourquoi:
 * - l'ancien code découpait les barres figées avec `BlendMode.Clear` dans un calque hors écran.
 * Sous l'orbe, ce `Clear` trouait aussi ce qu'il y avait dessous: le fond et les lumières
 * apparaissaient dans les trous, laissant des **carrés** nets et des **points** colorés autour
 * des barres. Une image nettoyée une fois pour toutes supprime la cause, sans composition
 * hors écran ni mode de fusion.
 * - les barres vivantes sont maintenant des rectangles arrondis (rayon = demi-largeur), avec un
 * dégradé vertical clair en haut et légèrement fondu en bas, cohérent avec la lumière du
 * disque — au lieu d'aplats blancs aux extrémités carrées.
 */
@Composable
internal fun DarkGlassWaveOrb(
    phase: VoicePhase,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image = remember(context) {
        val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.ic_dark_glass)
        stripFrozenBars(decoded).asImageBitmap()
    }
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = OnyxMotion.Sites.VoiceLevel,
        label = "darkGlassLevel",
    )
    val reduceMotion = LocalReduceMotion.current
    val clock = rememberInfiniteTransition(label = "darkGlassWaveClock")
    // `LiveWaveform`: « At rest the timeline still ticks, slowly, for the ripple; off under
    // Reduce Motion ». La crête ne passe donc plus.
    val rippleTime = ambientLoop(rest = 0f) {
        clock.animateFloat(
            initialValue = 0f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(OnyxMotion.MS_5000, easing = OnyxMotion.Linear),
                repeatMode = RepeatMode.Restart,
            ),
            label = "darkGlassWaveRipple",
        ).value
    }

    Canvas(modifier = modifier) {
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )

        val unit = size.width / 190f
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        val live = phase == VoicePhase.LISTENING || phase == VoicePhase.SPEAKING
        val rawLevel = if (live) animatedLevel else 0f
        val moving = phase == VoicePhase.SPEAKING || rawLevel > LiveWaveGate
        val drive = if (moving) liveWaveLoudness(phase, rawLevel) else null
        val barWidth = LiveWaveBarWidth * unit
        val gap = LiveWaveBarSpacing * unit

        // **Les sept barres du modèle**, toutes dessinées. Un lot précédent avait retiré les
        // deux poids extrêmes pour faire disparaître « deux mini barres » vues aux extrémités de la
        // rangée: le remède était à côté — ces deux traits n'étaient pas des barres vivantes mais
        // le **reste des barres figées du PNG** (voir [stripFrozenBars]), que rien n'effaçait
        // complètement. La vague reste donc entière (sept barres, comme `LiveWaveform.weights`).
        val weights = LiveWaveWeights
        weights.forEachIndexed { index, weight ->
            // `drive == nil && !reduceMotion ? ripple(i, t): 0`.
            val ripple = if (drive == null && !reduceMotion) liveWaveRipple(index, rippleTime) else 0f
            val barHeight = LiveWaveRest +
                (LiveWaveTallest - LiveWaveRest) * weight * (drive ?: 0f) + ripple
            val height = (barHeight * unit).coerceAtLeast(barWidth)
            val left = centreX + (index - (weights.size - 1) / 2f) * (barWidth + gap) - barWidth / 2f
            val top = centreY - height / 2f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(LiveWaveColor, LiveWaveColor.copy(alpha = LiveWaveBottomAlpha)),
                    startY = top,
                    endY = top + height,
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

/**
 * Le bitmap de verre, ses barres figées remplacées par la couleur du disque juste à côté.
 *
 * **Les barres sont trouvées dans l'image**, pas devinées. C'est la correction du défaut: le code
 * découpait des rectangles calculés sur une géométrie supposée (pas de 15,5 pt, largeur de 10 pt),
 * alors que le dessin a des barres de 9,2 pt de large à 14,85 pt de pas. L'écart se rattrapait au
 * centre et **débordait aux extrémités**: sur les deux barres du bout, 2 px de blanc survivaient,
 * juste à l'extérieur des points de la vague — les « deux mini barres verticales » signalées, qui
 * ne devaient rien aux poids de la vague et que retirer deux points ne pouvait pas faire partir.
 *
 * La couleur de remplacement est échantillonnée de part et d'autre de chaque barre, à la même
 * hauteur: le dégradé du disque est donc suivi, et non recouvert d'un aplat. Fait une fois au
 * chargement.
 *
 * `internal` et sans dépendance à Compose: c'est ce qui permet d'éprouver la **décision** sur une
 * image fabriquée (`VoiceOrbBarsTest`).
 */
internal fun stripFrozenBars(source: android.graphics.Bitmap): android.graphics.Bitmap {
    if (source.width == 0 || source.height == 0) return source
    val out = source.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: return source
    val pixels = IntArray(out.width * out.height)
    out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
    val pad = (out.width / ORB_POINT_SIZE * FrozenBarPadPoints).roundToInt().coerceAtLeast(2)

    frozenBarColumns(pixels, out.width, out.height).forEach { run ->
        val extent = frozenBarRows(pixels, out.width, out.height, run) ?: return@forEach
        val left = (run.first - pad).coerceAtLeast(0)
        val right = (run.last + pad).coerceAtMost(out.width - 1)
        val top = (extent.first - pad).coerceAtLeast(0)
        val bottom = (extent.last + pad).coerceAtMost(out.height - 1)

        for (y in top..bottom) {
            // La couleur du disque: prise à gauche et à droite de la barre, à la même hauteur, et
            // **interpolée d'un bord à l'autre**. La moyenne des deux couleurs laissait une bande
            // plate là où le disque, lui, glisse: les endroits des barres figées se voyaient
            // encore comme des rectangles un peu plus clairs.
            val outsideLeft = (left - Math.max(2, pad)).coerceAtLeast(0)
            val outsideRight = (right + Math.max(2, pad)).coerceAtMost(out.width - 1)
            val leftColour = pixels[y * out.width + outsideLeft]
            val rightColour = pixels[y * out.width + outsideRight]
            val span = (right - left).coerceAtLeast(1)
            for (x in left..right) {
                pixels[y * out.width + x] = blendAcross(leftColour, rightColour, (x - left).toFloat() / span)
            }
        }
    }
    out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
    return out
}

/** Vrai pour un pixel de barre figée: clair et sans couleur, là où le disque est bleu. */
private fun isFrozenBarPixel(pixel: Int): Boolean {
    val red = (pixel shr 16) and 0xFF
    val green = (pixel shr 8) and 0xFF
    val blue = pixel and 0xFF
    val brightest = maxOf(red, green, blue)
    val darkest = minOf(red, green, blue)
    return brightest - darkest <= FrozenBarColourTolerance && brightest >= FrozenBarMinLuminance
}

/**
 * Les colonnes des barres figées, trouvées dans les pixels du disque.
 *
 * Une colonne est une barre quand elle est claire et sans couleur sur au moins un quart de la bande
 * centrale — c'est-à-dire là où le disque, lui, est bleu et sombre. Les colonnes voisines sont
 * réunies, et un trou d'une colonne est comblé (le bord anti-aliasé d'une barre n'est pas une
 * séparation).
 *
 * Recherché autour du centre seul: un reflet clair au bord du disque n'est pas une barre.
 */
internal fun frozenBarColumns(pixels: IntArray, width: Int, height: Int): List<IntRange> {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return emptyList()
    val bandTop = height * 3 / 8
    val bandBottom = (height * 5 / 8).coerceAtLeast(bandTop + 1)
    val needed = ((bandBottom - bandTop) * FrozenBarMinCoverage).toInt().coerceAtLeast(1)
    val centre = width / 2f
    val span = width * 0.45f

    val runs = ArrayList<IntRange>()
    var start = -1
    var hole = 0
    for (x in 0 until width) {
        val candidate = if (kotlin.math.abs(x - centre) > span) false else {
            var lit = 0
            for (y in bandTop until bandBottom) {
                if (isFrozenBarPixel(pixels[y * width + x])) lit++
            }
            lit >= needed
        }
        if (candidate) {
            if (start < 0) start = x
            hole = 0
        } else if (start >= 0) {
            // Un trou d'une seule colonne: le bord d'une barre, pas deux barres.
            hole++
            if (hole > 1) {
                runs.add(start..(x - hole))
                start = -1
                hole = 0
            }
        }
    }
    if (start >= 0) runs.add(start..(width - 1 - hole))
    return runs
}

/** Les lignes d'une barre: ses colonnes, de la première à la dernière ligne claire. */
internal fun frozenBarRows(pixels: IntArray, width: Int, height: Int, columns: IntRange): IntRange? {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null
    var top = -1
    var bottom = -1
    for (y in 0 until height) {
        var lit = false
        for (x in columns.first..columns.last) {
            if (isFrozenBarPixel(pixels[y * width + x])) {
                lit = true
                break
            }
        }
        if (lit) {
            if (top < 0) top = y
            bottom = y
        }
    }
    if (top < 0) return null
    return top..bottom
}

/** La couleur d'un point entre deux colonnes du disque, pour suivre son dégradé. */
private fun blendAcross(first: Int, second: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val a = (first shr shift) and 0xFF
        val b = (second shr shift) and 0xFF
        return (a + (b - a) * k).roundToInt().coerceIn(0, 255)
    }
    return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** `LiveWaveform.loudness`: seuil de pièce et gain du micro, plancher propre à la lecture. */
private fun liveWaveLoudness(phase: VoicePhase, level: Float): Float {
    val normalized = if (phase == VoicePhase.SPEAKING) {
        ((level - 0.2f) / 0.6f).coerceIn(0f, 1f)
    } else {
        ((level - LiveWaveGate) / (1f - LiveWaveGate) * LiveWaveGain).coerceIn(0f, 1f)
    }
    return sqrt(normalized)
}

/** Une crête de 3 pt traverse les sept barres toutes les 5 s, puis retombe à plat. */
private fun liveWaveRipple(index: Int, timeSeconds: Float): Float {
    val crest = 0.3f + index * 0.12f
    val distance = (timeSeconds - crest) / 0.16f
    return 3f * exp(-distance * distance)
}

/** Le diamètre de base des anneaux: `GlassOrb.haloDiameter`, pour l'orbe dessiné. */
internal val OrbHaloBaseDiameter = 228.dp

/** Combien de fois le diamètre de base va l'anneau le plus large (`Halos.ring`). */
private const val HaloMaxScale = 1.6f

/** Où part un anneau, et jusqu'où il s'ouvre: `Halos.ring`. */
private const val HaloRestScale = 0.95f
private const val HaloLiveTravel = 0.65f
private const val HaloSubtleTravel = 0.30f

/**
 * `Halos`: les anneaux qui s'ouvrent depuis le bord de l'orbe. Avec l'anneau de phase collé au
 * disque, ce sont les **trois cercles concentriques** du modèle.
 *
 * `diameter` est le diamètre de base (`Halos.diameter` ): 228 points pour l'orbe dessiné
 * (`GlassOrb.haloDiameter`), 198 pour les faces-image. L'anneau part à 0,95 de ce diamètre et
 * s'ouvre jusqu'à 1,6 (1,25 au repos), l'opacité s'éteignant au fil de l'ouverture — 0,45 à
 * l'écoute et pendant la lecture, 0,22 au repos, et un trait de 1,5 pt (1 pt au repos). Le rythme
 * est celui: 2,6 s et deux anneaux décalés de 1,3 s en activité, 4,5 s et un seul anneau
 * au repos.
 *
 * Le cadre porte l'anneau le plus large et se mesure **sans contrainte**: les cercles débordent
 * donc du carré de l'orbe — c'est ce qui se voit sur la capture, les anneaux sont bien plus larges
 * que le disque — sans rien pousser sous eux. Aucun anneau ne touche le bord du cadre (son opacité
 * y est nulle), donc il n'apparaît jamais d'arête droite.
 */
@Composable
internal fun VoiceHalos(
    color: Color,
    subtle: Boolean,
    diameter: Dp = OrbHaloBaseDiameter,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = if (subtle) "idleHalos" else "liveHalos")
    val period = if (subtle) OnyxMotion.MS_4500 else OnyxMotion.MS_2600
    val delays = if (subtle) intArrayOf(0) else intArrayOf(0, 1300)
    val pulses = delays.map { delay ->
        // `Halos.onAppear { guard !reduceMotion else { return } }`:
        // la boucle ne démarre pas, l'anneau reste à son repos (échelle 0,95, opacité pleine).
        ambientLoop(rest = 0f) {
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(period, delayMillis = delay, easing = OnyxMotion.EaseOut),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "halo$delay",
            ).value
        }
    }

    Canvas(
        modifier = modifier
            .wrapContentSize(align = Alignment.Center, unbounded = true)
            .clip(CircleShape)
            .requiredSize(diameter * HaloMaxScale),
    ) {
        // Le rayon de base se relit de la taille **réellement** mesurée: si l'écran a borné le
        // cadre, le rapport des anneaux au disque reste le même.
        val base = size.minDimension / (2f * HaloMaxScale)
        val travel = if (subtle) HaloSubtleTravel else HaloLiveTravel
        pulses.forEach { progress ->
            drawCircle(
                color = color.copy(alpha = (1f - progress) * if (subtle) 0.22f else 0.45f),
                radius = base * (HaloRestScale + travel * progress),
                style = Stroke(width = (if (subtle) 1f else 1.5f).dp.toPx()),
            )
        }
    }
}

/** `ThinkingArc`: un arc de 22 % du cercle, une rotation linéaire toutes les 1,4 s. */
@Composable
internal fun VoiceThinkingArc(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "thinkingArc")
    // `ThinkingArc`: `guard !reduceMotion else { return }` — l'arc
    // reste à sa rotation de départ au lieu de tourner.
    val spinning = ambientLoop(rest = 0f) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(OnyxMotion.MS_1400, easing = OnyxMotion.Linear),
                repeatMode = RepeatMode.Restart,
            ),
            label = "thinkingArcRotation",
        ).value
    }

    Canvas(modifier = modifier.graphicsLayer { rotationZ = spinning }) {
        val strokeWidth = 3.dp.toPx()
        val inset = strokeWidth / 2f
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(color.copy(alpha = 0f), color, lerp(color, Color.White, 0.5f)),
                center = Offset(size.width / 2f, size.height / 2f),
            ),
            startAngle = 0f,
            sweepAngle = 360f * 0.22f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}