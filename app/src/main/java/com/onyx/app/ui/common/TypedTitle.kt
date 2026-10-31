package com.onyx.app.ui.common

import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.OnyxMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay

/**
 * Le titre d'une conversation, qui s'écrit quand elle reçoit son nom (`TypedTitle`).
 *
 * Quand une nouvelle conversation reçoit son titre (sa première question), « New conversation »
 * s'efface vers le haut et le titre se tape derrière un curseur. Ouvrir une autre conversation
 * l'affiche d'un coup.
 */
@Composable
fun TypedTitle(
    title: String,
    conversationKey: String,
    isPlaceholder: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current

    var typed by remember(conversationKey) { mutableStateOf<String?>(null) }
    var leaving by remember(conversationKey) { mutableStateOf<String?>(null) }
    var caret by remember(conversationKey) { mutableStateOf(false) }
    var wasPlaceholder by remember(conversationKey) { mutableStateOf(isPlaceholder) }

    LaunchedEffect(conversationKey, title, isPlaceholder) {
        // `guard old.id == new.id, old.placeholder, !new.placeholder, !reduceMotion else { … }`
        // (`TypedTitle:51`) : sous Reduce Motion le titre paraît d'un coup, sans écriture.
        val shouldType = wasPlaceholder && !isPlaceholder && title.isNotEmpty() && !reduceMotion
        wasPlaceholder = isPlaceholder

        if (!shouldType) {
            typed = null
            leaving = null
            caret = false
            return@LaunchedEffect
        }

        // `TypedTitle.type(_:over:)` : le libellé d'attente part, puis 240 ms plus tard le
        // curseur paraît et le titre s'écrit derrière lui, à 28 ms le caractère.
        typed = ""
        leaving = PLACEHOLDER
        delay(30)
        leaving = null
        delay(240)
        caret = true
        val shown = title.take(TYPED_CHARACTERS)
        for (index in 1..maxOf(1, shown.length)) {
            typed = shown.take(index)
            delay(28)
        }
        // Le curseur se repose un moment, puis le titre entier prend sa place.
        delay(350)
        typed = null
        caret = false
    }

    Box(modifier = modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = leaving != null,
            exit = slideOutVertically(
                animationSpec = OnyxMotion.easeIn<androidx.compose.ui.unit.IntOffset>(OnyxMotion.MS_220),
            ) { -it } + fadeOut(animationSpec = OnyxMotion.easeIn(OnyxMotion.MS_220)),
        ) {
            BasicText(
                text = leaving ?: PLACEHOLDER,
                style = style.copy(color = theme.textColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = typed ?: title,
                localize = isPlaceholder,
                style = style.copy(color = theme.textColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (caret) {
                BlinkingCaret(color = theme.accent)
            }
        }
    }
}

/** Le curseur d'écriture : un trait d'accent qui clignote (`Caret`, `easeInOut` 0,4 s). */
@Composable
private fun BlinkingCaret(color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "caret")
    val dim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = OnyxMotion.MS_400, easing = OnyxMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretDim",
    )
    Box(
        modifier = Modifier
            .size(width = 2.dp, height = 17.dp)
            .alpha(1f - 0.85f * dim)
            .background(color),
    )
}

private const val PLACEHOLDER = "New conversation"

/** Un en-tête montre à peu près ceci ; le reste d'un long titre arrive d'un coup. */
private const val TYPED_CHARACTERS = 26
