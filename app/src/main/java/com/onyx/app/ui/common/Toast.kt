package com.onyx.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay

/**
 * Le toast de l'app : une capsule en bas, qui s'efface d'elle-même (`ToastModifier`).
 *
 * C'est le même geste qu'référence : on pose un texte, il reste trois secondes, il part.
 */
class Toaster {
    internal val text = mutableStateOf<String?>(null)

    fun show(message: String) {
        text.value = message
    }
}

@Composable
fun rememberToaster(): Toaster = remember { Toaster() }

/** Le texte du toast, et sa durée de vie. */
@Composable
fun ToastHost(
    toaster: Toaster,
    modifier: Modifier = Modifier,
    bottomPadding: Int = 12,
    durationMillis: Long = 3_000,
) {
    val theme = LocalOnyxTheme.current
    val current by toaster.text

    LaunchedEffect(current) {
        val message = current ?: return@LaunchedEffect
        delay(durationMillis)
        if (toaster.text.value == message) toaster.text.value = null
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(animationSpec = OnyxMotion.Sites.ToastOffset) { it } +
                fadeIn(animationSpec = OnyxMotion.Sites.Toast),
            exit = slideOutVertically(animationSpec = OnyxMotion.Sites.ToastOffset) { it } +
                fadeOut(animationSpec = OnyxMotion.Sites.Toast),
        ) {
            BasicText(
                text = current.orEmpty(),
                style = theme.footnote.copy(color = theme.textColor, textAlign = TextAlign.Center),
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = bottomPadding.dp)
                    .background(theme.surfaceColor, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
