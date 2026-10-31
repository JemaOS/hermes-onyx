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
 * Les sources des pastilles d'une ligne de la section « Bots ».
 *
 * Comme référence (`ChatActivity`), elles ne viennent pas toutes du même endroit :
 * - « au travail » et « en attente de vous » : `session.active_list`, que le serveur tient à jour
 *   (`liveKinds`, par id de chat ; `roomKinds`, par id de salle) ;
 * - « pas encore lu » : ce que **ce téléphone** a vu de la ligne, contre la date que le serveur
 *   donne pour son dernier mot — Hermes ne tient pas de marque de lecture pour le chat d'un bot ni
 *   pour une salle (`seenMarks`, par id de ligne de `BotListItem`).
 */
data class BotBadges(
    val liveKinds: Map<String, ActivityKind> = emptyMap(),
    val roomKinds: Map<String, ActivityKind> = emptyMap(),
    val seenMarks: Map<String, Double?> = emptyMap(),
) {
    /** Ce qu'un bot montre : son chat ouvert ici, la session vivante que le serveur signale, son worker. */
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

    /** Ce qu'une salle montre : ses bots au travail, l'un d'eux qui attend, ou du nouveau depuis la dernière fois. */
    fun forRoom(id: String, activityMark: Double?, isOpen: Boolean, muted: Boolean): BotActivity = roomActivity(
        markSeconds = activityMark,
        seen = seenMarks[BotListItem.Room(GroupRoom(id = id, name = id)).id],
        isOpen = isOpen,
        kind = roomKinds[id],
        muted = muted,
    )
}

/** La hauteur d'une ligne qu'on met en ordre (« Reorder Bots »). */
internal val ARRANGE_ROW_HEIGHT: Dp = 58.dp

/**
 * La ligne visée par un glissement de `dy` depuis `from`.
 *
 * Le mode « Reorder Bots » de la référence déplace une ligne à la fois (`List.onMove`), au rythme du
 * glissement : c'est donc le déplacement, divisé par la hauteur d'une ligne, qui dit de combien de
 * rangs elle descend ou monte.
 */
internal fun arrangeTarget(from: Int, dy: Float, rowHeight: Float, count: Int): Int {
    if (rowHeight <= 0f || count <= 1) return from.coerceIn(0, (count - 1).coerceAtLeast(0))
    val moved = from + (dy / rowHeight).roundToInt()
    return moved.coerceIn(0, count - 1)
}

/**
 * Les lignes de la section pendant « Reorder Bots », comme `botsSection` en `arrangingBots` :
 * chaque ligne se glisse par sa poignée et n'ouvre rien, l'en-tête dit « Bots », et le pied de
 * section explique le geste.
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
    // La ligne qu'on glisse, et de combien : le déplacement ne quitte pas l'écran.
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
                            // Le dernier rang visé, en gardant la ligne dans la section.
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

        // Le pied de section de la référence, tel quel.
        BasicText(
            text = "Drag bots and group chats by their handles into the order you want.",
            style = LocalOnyxTheme.current.footnote.copy(color = LocalOnyxTheme.current.secondaryText),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Une ligne qu'on met en ordre : son fond, et sa poignée à droite ; elle n'ouvre rien. */
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
 * La poignée d'une ligne (`line.3.horizontal`) : elle seule bouge la ligne, et rien d'autre ne
 * répond au toucher pendant qu'on met la section en ordre (`allowsHitTesting(false)` côté référence).
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
 * Un bot pendant « Reorder Bots », comme le `botRow` de la référence : son visage, son nom, la marque du
 * silence, l'heure de son dernier mot — ou le rond qui tourne —, puis sa dernière ligne.
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
 * Un salon pendant « Reorder Bots », comme le `groupRow` de la référence : les visages de
 * ses membres, son nom, et qui en fait partie.
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
