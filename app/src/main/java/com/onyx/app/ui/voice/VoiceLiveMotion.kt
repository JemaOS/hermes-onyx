package com.onyx.app.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.ambientLoop
import com.onyx.app.ui.design.ambientState
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.voice.VoicePhase
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Les hauteurs que la vague prend avec la voix : le centre le plus haut, chaque barre vers
 * l'extérieur un cran plus bas, symétrique. **Sept** barres, comme `LiveWaveform.weights` de la
 * référence — les deux barres des extrémités en font partie : ce sont des points de la vague, pas
 * des parasites.
 */
internal val LiveWaveWeights = floatArrayOf(0.13f, 0.27f, 0.51f, 1f, 0.51f, 0.27f, 0.13f)

private const val LiveWaveRest = 6f
private const val LiveWaveTallest = 88f
private const val LiveWaveGate = 0.06f
private const val LiveWaveGain = 1.4f
private val LiveWaveColor = ReferencePalette.DarkText

/** La largeur d'une barre, l'écart entre deux, dans le repère de 190 points du disque. */
private const val LiveWaveBarWidth = 10f
private const val LiveWaveBarSpacing = 5.5f

/** Le bas d'une barre est très légèrement fondu : la lumière du disque descend. */
private const val LiveWaveBottomAlpha = 0.88f

/** Le disque dessiné fait 190 points (`GlassOrb.diameter`) : la vague s'y mesure. */
private const val ORB_POINT_SIZE = 190f

/**
 * L'ondulation : une crête qui **se propage** d'un point au suivant, toutes les 5 s.
 * Au repos elle fait 3 pt, avec la voix 10 pt — c'est elle qui distingue les hauteurs à un instant
 * donné, même quand le niveau du micro est stable.
 */
private const val LiveWaveRipplePeriod = 5f
private const val LiveWaveRippleRest = 3f
private const val LiveWaveRippleLive = 10f
private const val LiveWaveRippleStep = 0.12f
private const val LiveWaveRippleWidth = 0.16f

/** La respiration du repos : chaque point monte et descend un peu, jamais tous ensemble. */
private const val LiveWaveBreathHeight = 1.7f
private const val LiveWaveBreathPeriod = 3.6f
private const val LiveWaveBreathOffset = 0.62f

/**
 * La hauteur d'un point de la vague, en points du disque de 190.
 *
 * Trois choses s'ajoutent, et c'est ce qui la fait vivre :
 *  - la **voix** (`drive`), pondérée par le profil des sept points (`LiveWaveWeights`) ;
 *  - l'**ondulation** qui se propage, dont l'amplitude grandit avec la voix (3 → 10 pt) : dès
 *    qu'elle est vivante, les sept points ne sont plus à la même hauteur un même instant ;
 *  - la **respiration** du repos, une houle lente et décalée d'un point au suivant, qui s'efface
 *    quand la voix prend la main.
 *
 * Fonction pure (aucun état, aucune horloge) : c'est elle que les tests éprouvent. Sous Reduce
 * Motion (`animate = false`), elle rend le repos exact — sept points à la même hauteur, comme le
 * `TimelineView(paused:)` de la référence.
 */
internal fun liveWaveHeight(
    index: Int,
    drive: Float,
    rippleTime: Float,
    breatheTime: Float,
    animate: Boolean = true,
): Float {
    if (!animate) return LiveWaveRest
    val weight = LiveWaveWeights[index.coerceIn(0, LiveWaveWeights.lastIndex)]
    val d = drive.coerceIn(0f, 1f)
    val ripple = liveWaveRipple(index, rippleTime) *
        (LiveWaveRippleRest + (LiveWaveRippleLive - LiveWaveRippleRest) * d)
    val breathe = LiveWaveBreathHeight * (1f - d) * sin(
        (breatheTime + index * LiveWaveBreathOffset) *
            (2f * Math.PI.toFloat() / LiveWaveBreathPeriod),
    )
    return LiveWaveRest + (LiveWaveTallest - LiveWaveRest) * weight * d + ripple + breathe
}

/**
 * Une crête de 5 s qui traverse les sept points de gauche à droite : le point `index` la reçoit
 * `index × 0,12 s` après le premier. `exp(-d²)` retombe à zéro avant la fin de la période : au
 * redémarrage il reste moins de 0,03 pt (invisible sur un disque de 190), donc aucun saut visible.
 */
internal fun liveWaveRipple(index: Int, timeSeconds: Float): Float {
    val phase = timeSeconds % LiveWaveRipplePeriod
    val crest = 0.3f + index * LiveWaveRippleStep
    val distance = (phase - crest) / LiveWaveRippleWidth
    return exp(-distance * distance)
}

/** `LiveWaveform.loudness` : seuil de pièce et gain du micro, plancher propre à la lecture. */
private fun liveWaveLoudness(phase: VoicePhase, level: Float): Float {
    val normalized = if (phase == VoicePhase.SPEAKING) {
        ((level - 0.2f) / 0.6f).coerceIn(0f, 1f)
    } else {
        ((level - LiveWaveGate) / (1f - LiveWaveGate) * LiveWaveGain).coerceIn(0f, 1f)
    }
    return sqrt(normalized)
}

/**
 * Le niveau du micro, **lissé** : montée vive, retombée douce (`attack` court, `release` long).
 * C'est le lissage qui fait la différence entre une vague qui suit la voix et une vague qui saute
 * d'un cran à chaque trame — sur une attaque de consonne, le niveau brut fait des marches.
 *
 * L'état est lu dans le **dessin**, pas dans la composition : une trame de niveau n'invalide donc
 * que le tracé de la vague, pas l'écran.
 */
@Composable
private fun rememberSmoothedLevel(raw: Float, reduceMotion: Boolean): State<Float> {
    val value = remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(raw)
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            value.floatValue = 0f
            return@LaunchedEffect
        }
        var current = value.floatValue
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                last = now
                val target = latest
                // Montée en ~45 ms, retombée en ~160 ms : la vague respire avec la parole.
                val time = if (target > current) 0.045f else 0.16f
                current += (target - current) * (1f - exp(-dt / time))
                value.floatValue = current
            }
        }
    }
    return value
}

/**
 * `GlassOrb.overlay { LiveWaveform(...) }` — la vague vivante sur le disque.
 *
 * Ce qui a changé, et pourquoi :
 *  - l'ancienne vague était dessinée sur le disque en **image** et ses sept barres figées devaient
 *    être découpées du bitmap ; le disque étant maintenant calculé, il n'y a plus rien à découper,
 *    donc plus de couture ni de carré autour des barres.
 *  - elle ne bougeait, au repos, que par une ondulation de 3 pt **sans respiration** : sept points
 *    identiques et immobiles, exactement ce que le propriétaire a vu. Elle respire maintenant.
 *  - l'ondulation ne tournait **pas** pendant la parole (`drive == nil` la gardait) : les sept
 *    points montaient et descendaient ensemble, à la même hauteur. Elle tourne désormais toujours
 *    et grandit avec la voix.
 *  - le niveau du micro est **lissé** (montée vive, retombée douce) au lieu de suivre la trame
 *    brute.
 *
 * Sous **Reduce Motion**, aucune horloge ne tourne et le niveau n'est pas lu : la vague est figée
 * au repos, ses sept points à la même hauteur — comme le `TimelineView(paused:)` de la référence.
 */
@Composable
internal fun LiquidOrbWave(
    phase: VoicePhase,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val smoothed = rememberSmoothedLevel(level, reduceMotion)
    val clock = rememberInfiniteTransition(label = "orbWaveClock")
    // `LiveWaveform` : « At rest the timeline still ticks, slowly, for the ripple; off under
    // Reduce Motion ». Les deux horloges sont coupées sous Reduce Motion (`ambientLoop` renvoie le
    // repos sans jamais créer l'animation), et leurs valeurs sont lues **dans le dessin** : une
    // trame d'animation ne fait redessiner que la vague.
    val ripple: State<Float>? = ambientState {
        clock.animateFloat(
            initialValue = 0f,
            targetValue = LiveWaveRipplePeriod,
            animationSpec = infiniteRepeatable(
                animation = tween(OnyxMotion.MS_5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "orbWaveRipple",
        )
    }
    val breathe: State<Float>? = ambientState {
        clock.animateFloat(
            initialValue = 0f,
            targetValue = LiveWaveBreathPeriod,
            animationSpec = infiniteRepeatable(
                animation = tween((LiveWaveBreathPeriod * 1000).toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "orbWaveBreathe",
        )
    }

    Canvas(modifier = modifier) {
        val unit = size.width / ORB_POINT_SIZE
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        val live = phase == VoicePhase.LISTENING || phase == VoicePhase.SPEAKING
        val rawLevel = if (live && !reduceMotion) smoothed.value else 0f
        val moving = phase == VoicePhase.SPEAKING || rawLevel > LiveWaveGate
        val drive = if (moving) liveWaveLoudness(phase, rawLevel) else 0f
        val rippleTime = ripple?.value ?: 0f
        val breatheTime = breathe?.value ?: 0f

        val barWidth = LiveWaveBarWidth * unit
        val gap = LiveWaveBarSpacing * unit
        for (index in LiveWaveWeights.indices) {
            val height = (liveWaveHeight(index, drive, rippleTime, breatheTime, animate = !reduceMotion) * unit)
                .coerceAtLeast(barWidth)
            val left = centreX + (index - (LiveWaveWeights.size - 1) / 2f) * (barWidth + gap) -
                barWidth / 2f
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

/** Le diamètre de base des anneaux : `GlassOrb.haloDiameter`, pour l'orbe dessiné. */
internal val OrbHaloBaseDiameter = 228.dp

/** Combien de fois le diamètre de base va l'anneau le plus large (`Halos.ring`). */
private const val HaloMaxScale = 1.6f

/** Où part un anneau, et jusqu'où il s'ouvre : `Halos.ring`. */
private const val HaloRestScale = 0.95f
private const val HaloLiveTravel = 0.65f
private const val HaloSubtleTravel = 0.30f

/**
 * `Halos` : les anneaux qui s'ouvrent depuis le bord de l'orbe, pour les **faces-image** et la
 * scène d'une salle. L'orbe dessiné de la voix solo ne s'en sert plus : il porte un halo unique.
 *
 * `diameter` est le diamètre de base (`Halos.diameter` de la référence) : 228 points pour l'orbe dessiné,
 * 198 pour les faces-image. L'anneau part à 0,95 de ce diamètre et s'ouvre jusqu'à 1,6 (1,25 au
 * repos), l'opacité s'éteignant au fil de l'ouverture — 0,45 à l'écoute et pendant la lecture,
 * 0,22 au repos, et un trait de 1,5 pt (1 pt au repos). Le rythme est celui de la référence : 2,6 s et
 * deux anneaux décalés de 1,3 s en activité, 4,5 s et un seul anneau au repos.
 *
 * Le cadre porte l'anneau le plus large et se mesure **sans contrainte** : les cercles débordent
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
        // `Halos.onAppear { guard !reduceMotion else { return } }` (`VoiceView:1157-1160`) :
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
        // Le rayon de base se relit de la taille **réellement** mesurée : si l'écran a borné le
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

/** `ThinkingArc` : un arc de 22 % du cercle, une rotation linéaire toutes les 1,4 s. */
@Composable
internal fun VoiceThinkingArc(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "thinkingArc")
    // `ThinkingArc` : `guard !reduceMotion else { return }` (`VoiceView:740-748`) — l'arc
    // reste à sa rotation de départ au lieu de tourner.
    val spinning = ambientLoop(rest = 0f) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(OnyxMotion.MS_1400, easing = LinearEasing),
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
