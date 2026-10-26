package com.onyx.app.ui.common

import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.OnyxMotion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Ce qui arrive: rien, votre message envoyé, ou la rangée de la réponse qui attend. */
enum class ArrivalKind { NONE, SENT, WAITING_REPLY, QUEUED }

/**
 * Un message qui arrive dans le fil (`MessageArrival`).
 *
 * Le vôtre monte du composeur: il part plus bas, derrière le champ, et remonte en place. La
 * rangée de la réponse attend un battement, pour que les deux ne se croisent pas.
 */
fun Modifier.messageArrival(
    kind: ArrivalKind,
    leading: Boolean = false,
): Modifier = composed {
    if (kind == ArrivalKind.NONE) return@composed this
    var landed by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(kind) {
        when (kind) {
            ArrivalKind.SENT, ArrivalKind.QUEUED -> {
                visible = true
                landed = true
            }
            ArrivalKind.WAITING_REPLY -> {
                // Le délai de 240 ms appartient à l'animation d'opacité de 250 ms, pas à un
                // délai de coroutine suivi d'une courbe différente.
                visible = true
            }
            ArrivalKind.NONE -> Unit
        }
    }

    // `MessageArrival`: `let rising = kind == .sent && !landed && !reduceMotion`
    // — sous Reduce Motion le message ne monte pas, il paraît.
    val reduceMotion = LocalReduceMotion.current
    val rising = (kind == ArrivalKind.SENT || kind == ArrivalKind.QUEUED) && !landed && !reduceMotion
    val density = LocalDensity.current
    val travelDistance = if (kind == ArrivalKind.QUEUED) 70.dp else 96.dp
    val travel by animateFloatAsState(
        targetValue = if (rising) with(density) { travelDistance.toPx() } else 0f,
        animationSpec = OnyxMotion.Sites.MessageLanded,
        label = "arrivalTravel",
    )
    val scale by animateFloatAsState(
        targetValue = if (kind == ArrivalKind.SENT && rising) 0.9f else 1f,
        animationSpec = OnyxMotion.Sites.MessageLanded,
        label = "arrivalScale",
    )
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = when {
            // `.easeOut(duration: 0.25).delay(reduceMotion ? 0: 0.24)`.
            kind == ArrivalKind.WAITING_REPLY ->
                if (reduceMotion) OnyxMotion.Sites.MessageVisibleImmediate else OnyxMotion.Sites.MessageVisibleDelayed
            kind == ArrivalKind.QUEUED -> OnyxMotion.Sites.MessageLanded
            else -> OnyxMotion.Sites.MessageVisible
        },
        label = "arrivalOpacity",
    )

    this
        .offset { IntOffset(0, travel.roundToInt()) }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = if (leading) TransformOrigin(0f, 1f) else TransformOrigin(1f, 1f)
        }
        .alpha(opacity)
}
