package com.onyx.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Un point de la liste de tâches de l'agent (`TodoItem`). */
data class TodoItem(val text: String, val status: Status) {
    enum class Status { PENDING, IN_PROGRESS, DONE, DROPPED }
}

/**
 * La liste de tâches d'une réponse, en coche (`TodoChecklist`).
 *
 * Ce qui est fait est barré, ce qui est en cours est marqué, le reste attend. Une longue liste
 * montre son début, le reste d'un tap.
 */
@Composable
fun TodoChecklist(
    items: List<TodoItem>,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
) {
    if (items.isEmpty()) return
    val theme = LocalOnyxTheme.current
    val visible = if (items.size <= SHOWN + 1) items else items.take(SHOWN)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surfaceColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        BasicText(
            text = "Tasks",
            style = theme.footnote.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
        )
        visible.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    when (item.status) {
                        TodoItem.Status.DONE -> OnyxIcon(Symbols.Checkmark, tint = theme.success, size = 12.dp)
                        TodoItem.Status.IN_PROGRESS -> Box(
                            modifier = Modifier.size(8.dp).background(theme.accent, CircleShape),
                        )
                        TodoItem.Status.DROPPED -> OnyxIcon(Symbols.Xmark, tint = theme.tertiaryText, size = 11.dp)
                        TodoItem.Status.PENDING -> Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                .padding(1.dp),
                        ) {
                            BasicText("", style = theme.caption)
                        }
                    }
                }
                BasicText(
                    text = item.text,
                    localize = false,
                    style = theme.footnote.copy(
                        color = when (item.status) {
                            TodoItem.Status.DONE -> theme.tertiaryText
                            TodoItem.Status.DROPPED -> theme.tertiaryText
                            else -> theme.textColor
                        },
                        textDecoration = if (item.status == TodoItem.Status.DONE) TextDecoration.LineThrough else null,
                        fontWeight = if (item.status == TodoItem.Status.IN_PROGRESS) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            }
        }
        if (visible.size < items.size) {
            BasicText(
                text = "${items.size - visible.size} more",
                style = theme.caption.copy(color = theme.accent),
            )
        }
    }
}

private const val SHOWN = 8
