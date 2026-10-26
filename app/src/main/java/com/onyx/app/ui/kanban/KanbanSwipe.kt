package com.onyx.app.ui.kanban

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** L'action qu'un glissement révèle: son libellé, son symbole, et ses deux couleurs. */
data class KanbanSwipeAction(
    val label: String,
    val symbol: Int,
    val tint: Color,
    val foreground: Color,
)

/**
 * Une rangée du tableau qui se glisse vers l'avant ou vers l'arrière, comme `swipeActions`
 *: vers la droite la colonne suivante, vers la gauche la
 * suppression.
 *
 * Le geste suit le doigt; lâché avant la moitié de l'action, la rangée revient à sa place, et un
 * glissement franc — 45 % de la largeur, ou 800 px/s — déclenche l'action sans s'arrêter dessus
 * (`allowsFullSwipe: true`). La rangée révélée reste appuyable, comme le bouton.
 */
@Composable
fun KanbanSwipeRow(
    forward: KanbanSwipeAction?,
    trailing: KanbanSwipeAction?,
    onForward: () -> Unit,
    onTrailing: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val actionWidth = 96.dp
    val actionPx = with(density) { actionWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var travelled by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val rowWidth = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val forwardLimit = if (forward != null) actionPx else 0f
        val trailingLimit = if (trailing != null) actionPx else 0f

        Row(modifier = Modifier.matchParentSize()) {
            if (forward != null) {
                SwipeActionPanel(action = forward, width = actionWidth) {
                    scope.launch {
                        offset.snapTo(0f)
                        onForward()
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (trailing != null) {
                SwipeActionPanel(action = trailing, width = actionWidth) {
                    scope.launch {
                        offset.snapTo(0f)
                        onTrailing()
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(theme.pageColor)
                .pointerInput(forward, trailing, reduceMotion) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            travelled = 0f
                            tracker.resetTracking()
                        },
                        onHorizontalDrag = { change, amount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            travelled += amount
                            val next = (offset.value + amount).coerceIn(-trailingLimit, forwardLimit)
                            scope.launch { offset.snapTo(next) }
                            change.consume()
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x
                            val flungForward = forward != null && travelled > 0f && fullSwipeReached(travelled, rowWidth, velocity)
                            val flungTrailing = trailing != null && travelled < 0f && fullSwipeReached(travelled, rowWidth, velocity)
                            // Un glissement qui n'atteint pas le seuil **revient en place**: avant,
                            // il restait ouvert à mi-course et la rangée gardait son panneau
                            // d'action affiché pour toujours (« Archivé ça marche pas »: le
                            // panneau restait collé à la rangée, la carte ne bougeait pas).
                            val target = when {
                                flungForward -> forwardLimit
                                flungTrailing -> -trailingLimit
                                else -> 0f
                            }
                            scope.launch {
                                if (reduceMotion) {
                                    offset.snapTo(target)
                                } else {
                                    offset.animateTo(target, OnyxMotion.Sites.SidePanel)
                                }
                                when {
                                    flungForward -> {
                                        offset.snapTo(0f)
                                        onForward()
                                    }

                                    flungTrailing -> {
                                        offset.snapTo(0f)
                                        onTrailing()
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                if (reduceMotion) offset.snapTo(0f)
                                else offset.animateTo(0f, OnyxMotion.Sites.SidePanel)
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

/** Le panneau révélé: la teinte de la colonne visée, son symbole et son libellé. */
@Composable
private fun SwipeActionPanel(action: KanbanSwipeAction, width: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(action.tint)
            .onyxClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnyxIcon(symbol = action.symbol, tint = action.foreground, size = 17.dp)
        BasicText(text = action.label, style = LocalOnyxTheme.current.caption.copy(color = action.foreground))
    }
}

/**
 * Vrai quand le geste suffit à déclencher l'action d'un coup: 45 % de la rangée couverts, ou
 * l'élan de 800 px/s — le seuil d'`allowsFullSwipe`.
 */
internal fun fullSwipeReached(travelled: Float, rowWidth: Float, velocity: Float): Boolean =
    abs(travelled) >= rowWidth * FULL_SWIPE || abs(velocity) >= FLING

/** La part de la largeur qu'un glissement doit couvrir pour partir d'un coup (`allowsFullSwipe`). */
internal const val FULL_SWIPE = 0.45f

/** L'élan (px/s) qui suffit à déclencher l'action, même court. */
internal const val FLING = 800f
