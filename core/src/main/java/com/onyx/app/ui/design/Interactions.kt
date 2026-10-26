package com.onyx.app.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

/**
 * Les gestes de l'app: aucun ripple, un enfoncement qui réduit légèrement le contenu.
 *
 * Le seul retour au toucher est l'échelle et l'opacité, comme `ButtonStyle(.plain)`.
 */
fun Modifier.onyxClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    pressedAlpha: Float = 0.6f,
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

    this
        .scale(if (enabled) scale else 1f)
        .alpha(if (enabled) alpha else 0.35f)
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
 * [onLongPress] ajoute l'appui long, qui est le `contextMenu`: c'est lui qui ouvre le menu
 * d'une conversation. Sans lui, le geste n'existe pas et rien ne s'ouvre.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.onyxTappable(
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    if (onLongPress == null) {
        clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongPress,
        )
    }
}
