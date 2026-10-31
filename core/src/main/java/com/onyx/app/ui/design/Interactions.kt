package com.onyx.app.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Les gestes de l'app de référence : aucun ripple, un enfoncement qui réduit légèrement le contenu.
 *
 * Le seul retour au toucher est l'échelle et l'opacité, comme `ButtonStyle(.plain)` sur la référence.
 *
 * [disabledAlpha] est l'opacité d'un contrôle **désactivé** — 0,35 par défaut, l'atténuation
 * habituelle de l'app. Elle ne convient pas à un contrôle dont l'encre au repos est **déjà**
 * choisie par mesure (les touches de la barre de l'écran Ordinateur) : une encre calculée pour
 * tenir 3:1 est ramenée à 1,5:1 par cette couche, sans que rien ne le dise. Ces contrôles-là
 * passent `disabledAlpha = 1f` et portent leur propre encre de repos.
 */
fun Modifier.onyxClickable(
    enabled: Boolean = true,
    pressedScale: Float = Feedback.PressScale,
    pressedAlpha: Float = 0.6f,
    disabledAlpha: Float = 0.35f,
    haptics: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = OnyxMotion.Press,
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedAlpha else 1f,
        animationSpec = OnyxMotion.Press,
        label = "pressAlpha",
    )
    // **La vibration de l'appui.** Un clic tactile court ([Feedback.PressHapticsMs], 12 ms) au
    // moment où le doigt **se pose**, pas quand il relâche : c'est le retour qui dit « j'ai pris »,
    // et le relâchement est déjà couvert par le changement d'état. Elle est éteignable
    // ([haptics]) pour les surfaces qui se peignent hors d'une activité (aperçus, mesures).
    // Elle n'est **pas** soumise à « Réduire les animations » : ce n'est pas un mouvement, et c'est
    // justement le retour qui reste quand les animations sont coupées.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled && haptics) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    this
        .scale(if (enabled) scale else 1f)
        .alpha(if (enabled) alpha else disabledAlpha)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * Un simple clic sans ripple, sans effet d'enfoncement (lignes de liste, zones neutres).
 *
 * [onLongPress] ajoute l'appui long, qui est le `contextMenu` de la référence : c'est lui qui ouvre le menu
 * d'une conversation. Sans lui, le geste n'existe pas et rien ne s'ouvre.
 *
 * [interaction] laisse l'appelant **partager** la source d'appui de la zone : un dessin qui suit le
 * doigt (un visage de bot, par exemple) y lit son enfoncement et prend sa pose d'appui, là où
 * `onyxClickable` s'en sert pour l'échelle. Sans elle, la zone garde la sienne, invisible.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.onyxTappable(
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    interaction: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val own = remember { MutableInteractionSource() }
    val press = interaction ?: own
    if (onLongPress == null) {
        clickable(
            interactionSource = press,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        combinedClickable(
            interactionSource = press,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongPress,
        )
    }
}
