package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.serve.BotListItem
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.ui.common.Mutes
import com.onyx.app.ui.common.mutedMark
import com.onyx.app.ui.conversations.BotActivity
import com.onyx.app.ui.conversations.rowTime
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BadgeGeometry
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlin.math.roundToInt

/**
 * The sources of the badges of a row in the "Bots" section.
 *
 * As in the reference (`ChatActivity`), they do not all come from the same place:
 * - "working" and "waiting for you": `session.active_list`, which the server keeps up to date
 *   (`liveKinds`, by chat id; `roomKinds`, by room id);
 * - "not yet read": what **this phone** has seen of the row, against the date the server
 *   gives for its last word — Hermes keeps no read mark for a bot's chat nor
 *   for a room (`seenMarks`, by row id of `BotListItem`).
 */
data class BotBadges(
    val liveKinds: Map<String, ActivityKind> = emptyMap(),
    val roomKinds: Map<String, ActivityKind> = emptyMap(),
    val seenMarks: Map<String, Double?> = emptyMap(),
) {
    /** What a bot shows: its chat opened here, the live session the server reports, its worker. */
    fun forBot(
        bot: BotProfile,
        isOpen: Boolean,
        openStreaming: Boolean,
        muted: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): BotActivity = botActivity(
        bot = bot,
        isOpen = isOpen,
        seen = seenMarks[BotListItem.Bot(bot).id],
        kind = botKind(bot, liveKinds, openStreaming, nowMillis),
        muted = muted,
    )

    /** What a room shows: its bots working, one of them waiting, or something new since last time. */
    fun forRoom(id: String, activityMark: Double?, isOpen: Boolean, muted: Boolean): BotActivity = roomActivity(
        markSeconds = activityMark,
        seen = seenMarks[BotListItem.Room(GroupRoom(id = id, name = id)).id],
        isOpen = isOpen,
        kind = roomKinds[id],
        muted = muted,
    )
}

/** The height of a row we put in order ("Reorder Bots"). */
internal val ARRANGE_ROW_HEIGHT: Dp = 58.dp

/**
 * The row targeted by a drag of `dy` from `from`.
 *
 * The reference's "Reorder Bots" mode moves one row at a time (`List.onMove`), following the
 * drag: it is therefore the displacement, divided by the height of a row, that says how many
 * ranks it goes down or up.
 */
internal fun arrangeTarget(from: Int, dy: Float, rowHeight: Float, count: Int): Int {
    if (rowHeight <= 0f || count <= 1) return from.coerceIn(0, (count - 1).coerceAtLeast(0))
    val moved = from + (dy / rowHeight).roundToInt()
    return moved.coerceIn(0, count - 1)
}

/**
 * The rows of the section during "Reorder Bots", like `botsSection` in `arrangingBots`:
 * each row is dragged by its handle and opens nothing, the header says "Bots", and the section
 * footer explains the gesture.
 */
@Composable
fun BotsArrangeList(
    items: List<BotListItem>,
    bots: List<BotProfile>,
    badges: BotBadges,
    mutes: Mutes,
    openedChatId: String?,
    mainName: String,
    onMove: (from: Int, to: Int) -> Unit,
    onOpenBot: (BotProfile) -> Unit,
    onOpenRoom: ((GroupRoom) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // The row being dragged, and by how much: the displacement does not leave the screen.
    var dragging by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    val rowHeight = with(LocalDensity.current) { ARRANGE_ROW_HEIGHT.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val offset = dragging?.takeIf { it.first == index }?.second ?: 0f
            Box(modifier = Modifier.offset(y = with(LocalDensity.current) { offset.toDp() })) {
                when (item) {
                    is BotListItem.Bot -> ArrangeRow(
                        isDragging = dragging?.first == index,
                        onShift = { dy -> dragging = (dragging ?: (index to 0f)).let { index to (it.second + dy) } },
                        onRelease = {
                            val dy = dragging?.second ?: 0f
                            dragging = null
                            // The last targeted rank, keeping the row within the section.
                            val target = arrangeTarget(index, dy, rowHeight, items.size)
                            if (target != index) onMove(index, target)
                        },
                    ) {
                        BotArrangeRow(
                            bot = item.profile,
                            faceCode = item.profile.faceCode,
                            activity = badges.forBot(
                                bot = item.profile,
                                isOpen = item.profile.chatId != null && item.profile.chatId == openedChatId,
                                openStreaming = false,
                                muted = mutes.isMuted("bot:" + item.profile.name),
                            ),
                            muted = mutes.isMuted("bot:" + item.profile.name),
                            mainName = mainName,
                            onClick = { onOpenBot(item.profile) },
                        )
                    }

                    is BotListItem.Room -> ArrangeRow(
                        isDragging = dragging?.first == index,
                        onShift = { dy -> dragging = (dragging ?: (index to 0f)).let { index to (it.second + dy) } },
                        onRelease = {
                            val dy = dragging?.second ?: 0f
                            dragging = null
                            val target = arrangeTarget(index, dy, rowHeight, items.size)
                            if (target != index) onMove(index, target)
                        },
                    ) {
                        RoomArrangeRow(
                            room = item.room,
                            bots = bots,
                            activity = badges.forRoom(
                                id = item.room.id,
                                activityMark = item.room.activityMark,
                                isOpen = false,
                                muted = mutes.isMuted("room:" + item.room.id),
                            ),
                            muted = mutes.isMuted("room:" + item.room.id),
                            onClick = { onOpenRoom?.invoke(item.room) },
                        )
                    }
                }
            }
        }

        // The reference's section footer, as is.
        BasicText(
            text = "Drag bots and group chats by their handles into the order you want.",
            style = LocalOnyxTheme.current.footnote.copy(color = LocalOnyxTheme.current.secondaryText),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** A row being put in order: its background, and its handle on the right; it opens nothing. */
@Composable
private fun ArrangeRow(
    isDragging: Boolean,
    onShift: (Float) -> Unit,
    onRelease: () -> Unit,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ARRANGE_ROW_HEIGHT)
            .background(if (isDragging) theme.fill else theme.pageColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) { content() }
        DragHandle(onShift = onShift, onRelease = onRelease)
    }
}

/**
 * The handle of a row (`line.3.horizontal`): it alone moves the row, and nothing else
 * responds to touch while the section is being put in order (`allowsHitTesting(false)` on the reference side).
 */
@Composable
private fun DragHandle(onShift: (Float) -> Unit, onRelease: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = ARRANGE_ROW_HEIGHT)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() },
                    onDrag = { change, amount ->
                        change.consume()
                        onShift(amount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(
            symbol = Symbols.Line3Horizontal,
            tint = theme.tertiaryText,
            size = 18.dp,
            contentDescription = "Reorder",
        )
    }
}

/**
 * A bot during "Reorder Bots", like the reference's `botRow`: its face, its name, the mute
 * mark, the time of its last word — or the spinning circle —, then its last line.
 */
@Composable
private fun BotArrangeRow(
    bot: BotProfile,
    faceCode: String?,
    activity: BotActivity,
    muted: Boolean,
    mainName: String,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val label = bot.label(mainName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BotLook(name = bot.name, faceCode = faceCode, size = 34.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = label,
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (muted) {
                    OnyxIcon(symbol = mutedMark, tint = theme.secondaryText, size = 13.dp, contentDescription = "Muted")
                }
                Box(modifier = Modifier.weight(1f))
                if (activity.working) {
                    OnyxIcon(symbol = Symbols.Clock, tint = theme.secondaryText, size = 13.dp, contentDescription = "Working")
                } else {
                    bot.lastActive?.let {
                        BasicText(text = rowTime(it), style = theme.caption.copy(color = theme.secondaryText))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (activity.needsYou) {
                    Box(
                        modifier = Modifier
                            .background(theme.warning, CircleShape)
                            .padding(horizontal = 6.dp, vertical = BadgeGeometry.PadVertical),
                    ) {
                        BasicText(text = "Needs you", style = theme.caption2.copy(color = theme.userText))
                    }
                }
                BasicText(
                    text = bot.preview?.takeIf { it.isNotBlank() }
                        ?: bot.description.takeIf { it.isNotBlank() }
                        ?: "@" + bot.handle,
                    localize = false,
                    style = theme.subheadline.copy(color = theme.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (activity.unread) {
            Box(modifier = Modifier.size(9.dp).background(theme.accent, CircleShape))
        }
    }
}

/**
 * A room during "Reorder Bots", like the reference's `groupRow`: the faces of
 * its members, its name, and who belongs to it.
 */
@Composable
private fun RoomArrangeRow(
    room: GroupRoom,
    bots: List<BotProfile>,
    activity: BotActivity,
    muted: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.size(width = 40.dp, height = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(-8.dp),
        ) {
            room.members.take(2).forEach { member ->
                val bot = bots.firstOrNull { it.name == member.profile }
                BotLook(name = member.profile, faceCode = bot?.faceCode, size = 26.dp)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = room.name,
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (muted) {
                    OnyxIcon(symbol = mutedMark, tint = theme.secondaryText, size = 13.dp, contentDescription = "Muted")
                }
                Box(modifier = Modifier.weight(1f))
                if (activity.working) {
                    OnyxIcon(symbol = Symbols.Clock, tint = theme.secondaryText, size = 13.dp, contentDescription = "Working")
                } else {
                    room.updatedAtMillis?.let {
                        BasicText(text = rowTime(it), style = theme.caption.copy(color = theme.secondaryText))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (activity.needsYou) {
                    Box(
                        modifier = Modifier
                            .background(theme.warning, CircleShape)
                            .padding(horizontal = 6.dp, vertical = BadgeGeometry.PadVertical),
                    ) {
                        BasicText(text = "Needs you", style = theme.caption2.copy(color = theme.userText))
                    }
                }
                if (room.inDesktop) {
                    OnyxIcon(
                        symbol = Symbols.DesktopComputer,
                        tint = theme.secondaryText,
                        size = 13.dp,
                        contentDescription = "Runs in Hermes Desktop",
                    )
                }
                BasicText(
                    text = room.members.joinToString(", ") { it.label },
                    localize = false,
                    style = theme.subheadline.copy(color = theme.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (activity.unread) {
            Box(modifier = Modifier.size(9.dp).background(theme.accent, CircleShape))
        }
    }
}
