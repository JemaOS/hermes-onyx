package com.onyx.app.ui.chat.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onyx.app.ui.design.BotIdle
import com.onyx.app.ui.design.BotIdleFrame
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.BotPose
import com.onyx.app.ui.design.BotPress
import com.onyx.app.ui.design.BotPressReaction
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.botFace
import com.onyx.app.ui.design.botIdleFrame
import com.onyx.app.ui.design.botPressPose
import kotlinx.coroutines.delay

/**
 * Le visage du bot, **vivant** : des pouls de repos tirés au sort sur sa graine, et une réaction
 * quand on le touche.
 *
 * Le propriétaire a demandé « des animations aléatoires en boucle pour égayer » et « quand je le
 * touche, il est content ». Trois choses ne se négocient pas :
 *
 * - **Des pulsions courtes séparées par des pauses**, pas un mouvement continu : entre deux pouls
 *   le visage est immobile et **rien n'est redessiné** (aucune image n'est demandée). Le rythme
 *   vient de [BotIdle], une fonction pure à graine — la composition ne tire rien au sort elle-même.
 * - **Aucun décalage de mise en page** : la boîte ([boxSize]) est **constante**, et tout ce qui
 *   bouge (clignement, regard, respiration, inclinaison) est **dessiné** dans un canevas de taille
 *   fixe. Le titre voisin n'est jamais poussé.
 * - **Rien quand l'écran n'est pas visible** : la boucle s'arrête à la mise en pause (elle est
 *   annulée, pas seulement suspendue) et elle ne démarre pas du tout hors composition.
 *
 * Sous « Réduire les animations », **tout est figé** : aucun pouls automatique. Seule la réaction à
 * l'appui subsiste, et en **bascule nette** — le visage change d'expression, rien ne se déplace, et
 * le retour haptique est tu.
 */
@Composable
internal fun LiveBotFace(
    name: String,
    faceCode: String?,
    modifier: Modifier = Modifier,
    faceSize: Dp = TopBarAvatarSize,
    boxSize: Dp = TopBarAvatarBoxSize,
    showsInitial: Boolean = false,
) {
    val reduceMotion = LocalReduceMotion.current
    // L'écran est-il visible ? Un `LaunchedEffect` est déjà annulé hors composition ; ce drapeau
    // ajoute l'arrière-plan (mise en pause / arrêt) pour qu'aucune image ne soit demandée quand
    // personne ne regarde le visage.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var onScreen by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onScreen = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> onScreen = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // L'inclinaison propre du visage : un hochement part de là, jamais de zéro.
    val tilt = remember(name, faceCode) { botFace(name, faceCode).tilt.toFloat() }
    // La graine suit le bot : deux bots ne clignent pas ensemble, et la même capture se rejoue.
    val seed = remember(name, faceCode) { BotIdle.seedFor(name, faceCode) }

    var idle by remember(name, faceCode) { mutableStateOf<BotIdleFrame?>(null) }
    var reaction by remember(name, faceCode) { mutableStateOf<BotPressReaction?>(null) }
    var reactionProgress by remember(name, faceCode) { mutableFloatStateOf(1f) }
    // Le numéro de l'appui, et non la réaction elle-même : deux appuis courts donnent la **même**
    // réaction, et un `LaunchedEffect` qui la prendrait pour clé ne relancerait pas le second.
    var pressSeq by remember(name, faceCode) { mutableIntStateOf(0) }

    // Les pouls de repos. Entre deux pouls, la boucle attend — aucune image n'est consommée.
    LaunchedEffect(seed, onScreen, reduceMotion) {
        idle = null
        reaction = null
        if (reduceMotion || !onScreen) return@LaunchedEffect
        var index = 0
        while (true) {
            val pulse = BotIdle.pulse(seed, index)
            delay(pulse.gapMillis)
            // Le pouls se joue image par image, puis le visage revient au repos **exactement**.
            val started = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val progress = (now - started) / 1_000_000f / pulse.durationMillis
                if (progress >= 1f) break
                idle = botIdleFrame(pulse, progress, tilt)
            }
            idle = null
            index += 1
        }
    }

    // La réaction à l'appui : la pose est tenue le temps prévu, et le corps rebondit une fois —
    // sauf sous « Réduire les animations », où la bascule est nette.
    LaunchedEffect(pressSeq) {
        val taken = reaction ?: return@LaunchedEffect
        if (!taken.bounce) {
            reactionProgress = 1f
            delay(taken.durationMillis)
        } else {
            val started = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val progress = (now - started) / 1_000_000f / taken.durationMillis
                if (progress >= 1f) break
                reactionProgress = progress
            }
        }
        reaction = null
    }

    val frame = idle
    val shown = reaction?.let { taken ->
        if (taken.bounce) botPressPose(reactionProgress, tilt) else BotPose(tilt = tilt)
    } ?: frame?.pose ?: BotPose()
    val mood = reaction?.mood ?: frame?.mood ?: BotMood.IDLE

    val view = LocalView.current
    Box(
        modifier = modifier
            .size(boxSize)
            .pointerInput(name, faceCode, reduceMotion) {
                detectTapGestures(
                    onTap = {
                        // Un rien dans la main : de quoi sentir que le visage a répondu. Rien sous
                        // « Réduire les animations », où la réaction se voit déjà à l'écran.
                        if (!reduceMotion) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        reaction = BotPress.reaction(long = false, reduceMotion = reduceMotion)
                        pressSeq += 1
                    },
                    onLongPress = {
                        if (!reduceMotion) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        reaction = BotPress.reaction(long = true, reduceMotion = reduceMotion)
                        pressSeq += 1
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BotLook(
            name = name,
            faceCode = faceCode,
            size = faceSize,
            mood = mood,
            showsInitial = showsInitial,
            // Le clignement du moteur est éteint : c'est la scène qui donne l'instant du visage,
            // sans quoi deux clignotements se superposeraient.
            blinks = false,
            pose = shown,
        )
    }
}
