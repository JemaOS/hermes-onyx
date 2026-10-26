package com.onyx.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.OutboxState
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Une bulle estompée et pointillée, son statut, et les actions réelles de l'outbox. */
@Composable
fun QueuedMessageRow(
    item: OutboxItem,
    isFirst: Boolean,
    onSend: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    var showMenu by remember(item.id) { mutableStateOf(false) }
    val status = outboxStatusText(item, isFirst)
    val bubbleShape = RoundedCornerShape(
        topStart = theme.bubbleRadius,
        topEnd = theme.bubbleRadius,
        bottomEnd = minOf(5.dp, theme.bubbleRadius),
        bottomStart = theme.bubbleRadius,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(item.id) {
                detectTapGestures(onLongPress = { showMenu = true })
            }
            .semantics {
                contentDescription = OnyxLocalization["Not sent yet:"] + " ${item.text}. " + OnyxLocalization[status]
            },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (item.attachments.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OnyxIcon(Symbols.Paperclip, tint = theme.secondaryText, size = 12.dp)
                BasicText(
                    text = outboxAttachmentLabel(item.attachments.size),
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }
        }
        if (item.text.isNotEmpty()) {
            BasicText(
                text = item.text,
                localize = false,
                style = theme.userFont.copy(color = theme.userBubbleText.copy(alpha = 0.85f)),
                modifier = Modifier
                    .background(theme.userBubbleColor.copy(alpha = 0.45f), bubbleShape)
                    .dashedBubble(theme.userBubbleColor.copy(alpha = 0.8f), theme.bubbleRadius)
                    .padding(horizontal = 14.dp, vertical = theme.bubbleVerticalPadding),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OnyxIcon(
                    symbol = when (item.state) {
                        OutboxState.QUEUED -> Symbols.Clock
                        OutboxState.WAITING_FOR_CONNECTION -> Symbols.WifiExclamation
                        OutboxState.PAUSED -> Symbols.Pause
                    },
                    tint = theme.secondaryText,
                    size = 12.dp,
                )
                BasicText(text = status, style = theme.caption.copy(color = theme.secondaryText))
            }
            if (isFirst && item.state != OutboxState.QUEUED) {
                BasicText(
                    text = "Send now",
                    style = theme.caption.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .background(theme.fill, RoundedCornerShape(7.dp))
                        .onyxClickable(onClick = onSend)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showMenu) {
        OnyxMenu(
            items = buildList {
                if (isFirst) {
                    add(MenuItem("Send now", Symbols.ArrowUpCircle) {
                        showMenu = false
                        onSend()
                    })
                }
                add(MenuItem("Edit", Symbols.Pencil) {
                    showMenu = false
                    onEdit()
                })
                add(MenuItem("Copy", Symbols.DocOnDoc) {
                    showMenu = false
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Message", item.text))
                })
                add(MenuItem("Delete", Symbols.Trash, destructive = true) {
                    showMenu = false
                    onDelete()
                })
            },
            onDismiss = { showMenu = false },
        )
    }
}

/** La géométrie de `userBubbleShape`, réutilisée par le contour discontinu. */
internal fun userBubbleRoundRect(rect: Rect, radius: CornerRadius, tuckedRadius: CornerRadius): RoundRect =
    RoundRect(rect, radius, radius, tuckedRadius, radius)

/** Le contour discontinu [4, 3] de `QueuedMessageRow`, dessiné dans la forme du thème. */
private fun Modifier.dashedBubble(color: Color, radius: Dp): Modifier = drawBehind {
    val strokeWidth = 1.dp.toPx()
    val inset = strokeWidth / 2f
    val corner = CornerRadius(radius.toPx())
    val tucked = CornerRadius(minOf(5.dp, radius).toPx())
    val path = Path().apply {
        addRoundRect(
            userBubbleRoundRect(
                Rect(inset, inset, size.width - inset, size.height - inset),
                corner,
                tucked,
            ),
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        ),
    )
}
