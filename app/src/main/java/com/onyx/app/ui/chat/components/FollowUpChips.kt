package com.onyx.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.reducedStagger
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay

/**
 * Les questions de relance de la dernière réponse (`FollowUpChips`).
 *
 * Elles arrivent une à une (90 ms d'écart), et partent ensemble ; un tap l'envoie.
 */
@Composable
fun FollowUpChips(
    suggestions: List<String>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val theme = LocalOnyxTheme.current
    var shown by remember(suggestions) { mutableIntStateOf(0) }
    // `if !reduceMotion { try? await Task.sleep(…) }` (`FollowUpChips:43`).
    val firstChip = reducedStagger(40)
    val nextChip = reducedStagger(90)

    LaunchedEffect(suggestions) {
        shown = 0
        for (index in 1..suggestions.size) {
            delay(if (index == 1) firstChip else nextChip)
            shown = index
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEachIndexed { index, text ->
            AnimatedVisibility(
                visible = index < shown,
                enter = slideInVertically(animationSpec = OnyxMotion.Sites.FollowUpChipOffset) { it } +
                    fadeIn(animationSpec = OnyxMotion.Sites.FollowUpChip),
            ) {
                Box(
                    modifier = Modifier
                        .background(theme.surfaceColor, RoundedCornerShape(50))
                        .onyxClickable(pressedScale = 0.96f) { onSend(text) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    BasicText(
                        text = text,
                        localize = false,
                        style = theme.subheadline.copy(color = theme.textColor),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
