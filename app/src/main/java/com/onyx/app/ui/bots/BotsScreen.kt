package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.common.Mutes
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.common.OnyxTroublePanel
import com.onyx.app.ui.common.mutedMark
import com.onyx.app.ui.conversations.BotActivity
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.conversations.GroupCreation
import com.onyx.app.ui.conversations.rowTime
import com.onyx.app.ui.conversations.botActivity
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.onyxPage
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch

/**
 * Settings → Bots, or the list reached from the conversations.
 *
 * Port of the "Bots" section of `ConversationsView`: the bot faces side by side
 * (`botGrid`) with their marks (working, waiting for you, not yet read, you), the tile
 * that makes a new one, the hidden bots tile, then the server's rooms, the group
 * chats and the rows that act.
 *
 * **What the reference source imposes.** `botGrid` is the *default* drawing of the section
 * (`showBotRows == true`, the default `@AppStorage`), and the "real" reference screenshot
 * (`01755fbe…/img_aff8bdadf42e.jpg`) confirms it: face thumbnails, two here, then
 * "+ New …" ("+ Nouveau …"). The preview rows of a conversation (`botRow`) appear in the
 * reference only during "Reorder Bots" (`arrangingBots`), held by their handles; the promo
 * mockup "Meet the crew." (`37f71832…`) shows that state, not the default.
 */
@Composable
fun BotsScreen(
    viewModel: BotsViewModel,
    /** Null in two-panel layout: the sidebar is there, the back arrow would have nothing to leave. */
    onBack: (() -> Unit)?,
    /** A tile tap: the bot's **chat**, like the reference's `openBot` — never its editor. */
    onOpenBotChat: (BotProfile) -> Unit,
    /** "Edit Bot…" of a tile's menu. */
    onEditBot: (BotProfile) -> Unit,
    onGroupChat: () -> Unit,
    conversations: List<Conversation> = emptyList(),
    groups: List<GroupChat> = emptyList(),
    hasDashboard: Boolean = true,
    openedChatId: String? = null,
    mainName: String = "Onyx",
    onNewBot: () -> Unit = {},
    onLetBotsMessage: () -> Unit = {},
    /** "Change Face…" of a tile's menu; without it, the entry does not appear. */
    onChangeFace: ((BotProfile) -> Unit)? = null,
    /** "Schedules…" of a tile's menu; without it, the entry does not appear. */
    onSchedules: ((BotProfile) -> Unit)? = null,
    /** "Voice…" of a tile's menu (`BotVoicePicker`); without it, the entry does not appear. */
    onVoice: ((BotProfile) -> Unit)? = null,
    /**
     * The **menu of a bot card**: the nine entries of the reference in their order, then the
     * house ones — the gesture table, supplied by the caller. Without it, the screen keeps its own
     * (the one it carried), so that it remains showable on its own.
     */
    botMenu: BotMenuHandlers? = null,
    /** How many bots are hidden: the "Hidden" tile appears only if there are any. */
    hiddenCount: Int = 0,
    /** Opens the "Hidden Bots" screen; without it, the tile stays absent. */
    onOpenHiddenBots: (() -> Unit)? = null,
    onOpenGroup: ((GroupChat) -> Unit)? = null,
    onRoomSeen: ((GroupChat) -> Unit)? = null,
    /**
     * The pane control (`sidebar.left`): the bots grid **is** the pane of the two-panel layout;
     * collapsed, the detail view carries it.
     */
    sidebarControl: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mutes = remember { Mutes(context) }
    /**
     * The creation box for a room (the reference's "New room"): open or not, what runs during
     * opening, and the server's refusal as-is. It **replaces** the earlier nameless
     * start: it is what names the room, picks its bots and states the 2-to-6 constraint.
     */
    var newRoomOpen by remember { mutableStateOf(false) }
    var makingRoom by remember { mutableStateOf<String?>(null) }
    var roomProblem by remember { mutableStateOf<String?>(null) }
    val roomScope = rememberCoroutineScope()
    // More than six bots: the grid folds onto one row, open or closed as it was
    // left (`sessions.botsExpanded` in the reference).
    var expanded by remember { mutableStateOf(false) }
    // The whole section also folds under its title, like the reference's `foldingHeader`: the
    // count appears only when folded.
    var open by remember { mutableStateOf(true) }
    // "Reorder Bots": the section shows only its rows, with handles, and opens nothing.
    var arranging by remember { mutableStateOf(false) }
    // The tile or room whose menu is open (the long press), like the reference's `contextMenu`.
    var menuBot by remember { mutableStateOf<BotProfile?>(null) }
    var menuRoom by remember { mutableStateOf<GroupChat?>(null) }
    // The bot whose **deletion** is requested: the menu requests it, the screen confirms it.
    var askDelete by remember { mutableStateOf<BotProfile?>(null) }
    // The menu's gesture table: what **this screen** does itself (reordering has its mode
    // here), and for the rest what the caller supplied — otherwise the screen's gestures, the earlier ones.
    // This screen therefore remains complete on its own.
    val table = (botMenu ?: BotMenuHandlers()).copy(
        onReorder = { arranging = true },
        onSortByRecent = viewModel::sortByRecent,
        onHide = botMenu?.onHide ?: { viewModel.hideBot(it) },
        onMarkSeen = botMenu?.onMarkSeen ?: { viewModel.markBotSeen(it) },
        onMarkUnread = botMenu?.onMarkUnread ?: { viewModel.markBotUnread(it) },
        onRemoveFromOnyx = botMenu?.onRemoveFromOnyx ?: { viewModel.removeAPIBot(it) },
        onEdit = botMenu?.onEdit ?: onEditBot,
        onChangeFace = botMenu?.onChangeFace ?: onChangeFace,
        onVoice = botMenu?.onVoice ?: onVoice,
        onRoutines = botMenu?.onRoutines ?: onSchedules,
        hasRecent = botMenu?.hasRecent
            ?: { bot -> botRecentConversation(bot, conversations, state.bots, mainName) != null },
    )
    // Where each tile (and each room) is on screen, in the window: its menu opens near
    // it. The table fills in on layout passes and recomposes nothing.
    val tileBounds = remember { mutableMapOf<String, Rect>() }
    val badges = BotBadges(
        liveKinds = state.liveKinds,
        roomKinds = state.roomKinds,
        seenMarks = state.seenMarks,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Bots",
                onBack = onBack,
                leading = sidebarControl,
                action = if (arranging) "Done" else "New room",
                onAction = { if (arranging) arranging = false else { roomProblem = null; newRoomOpen = true } },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            state.problem?.let { problem ->
                // The same panel as everywhere else: a server that did not answer shows the
                // thread card (plain cause + "Réessayer" ("Retry")), a refusal its warning row.
                OnyxTroublePanel(problem = problem, onRetry = viewModel::refresh)
            }

            if (state.loading) {
                OnyxLoadingRows(label = "Loading from Onyx…")
                return@Column
            }

            // The section header, like `foldingHeader("Bots", count:)`: its title, the
            // count — but only when folded —, and the chevron that folds it.
            SectionLabel(
                count = if (open && !arranging) null else state.bots.size,
                open = open && !arranging,
                arranging = arranging,
                onToggle = { if (arranging) arranging = false else open = !open },
            )

            if (arranging) {
                BotsArrangeList(
                    items = state.arranged,
                    bots = state.bots,
                    badges = badges,
                    mutes = mutes,
                    openedChatId = openedChatId,
                    mainName = mainName,
                    onMove = viewModel::move,
                    onOpenBot = onOpenBotChat,
                    onOpenRoom = { room -> groups.firstOrNull { it.id == room.id }?.let { onOpenGroup?.invoke(it) } },
                )
                return@Column
            }

            if (!open) return@Column

            BotsGrid(
                bots = state.bots,
                faces = state.faces,
                conversations = conversations,
                badges = badges,
                mutes = mutes,
                openedChatId = openedChatId,
                mainName = mainName,
                expanded = expanded,
                hasDashboard = hasDashboard,
                hiddenCount = state.hiddenBots.size,
                hidden = if (onOpenHiddenBots != null) hiddenCount else 0,
                // The "Hidden" entry is placed as soon as the screen can open it: it is what
                // makes the screen discoverable even when no bot is hidden yet.
                hiddenEntry = onOpenHiddenBots != null,
                onExpand = { expanded = true },
                onFewer = { expanded = false },
                onOpenBot = onOpenBotChat,
                onOpenMenu = { bot -> menuBot = bot },
                // Where the tile is: its menu opens near it, like that of a row.
                onTileBounds = { name, bounds -> tileBounds["bot:" + name] = bounds },
                onNewBot = onNewBot,
                onOpenHiddenBots = { onOpenHiddenBots?.invoke() },
            )

            if (state.bots.isEmpty() && groups.isEmpty()) {
                BasicText(
                    text = if (hasDashboard) {
                        "Bots are Hermes profiles. Add one in Hermes Desktop and it appears here."
                    } else {
                        "Bots are your server's other profiles. On the Hermes API, add each by its name and its own API key; with the Dashboard login saved in Settings, Onyx lists them and has group chats too."
                    },
                    style = theme.footnote.copy(color = theme.secondaryText),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            // The group chats, under the grid (`groupRow`), then the rows that
            // act (`sectionAction`).
            groups.forEach { group ->
                Box(
                    modifier = Modifier.onGloballyPositioned {
                        tileBounds["room:" + group.id] = it.boundsInWindow()
                    },
                ) {
                    GroupRow(
                        group = group,
                        muted = mutes.isMuted("room:" + group.id),
                        activity = badges.forRoom(
                            id = group.id,
                            activityMark = group.activityMark,
                            isOpen = false,
                            muted = mutes.isMuted("room:" + group.id),
                        ),
                        onClick = {
                            onRoomSeen?.invoke(group)
                            onOpenGroup?.invoke(group)
                        },
                        // The long press opens the room's menu, like the `groupRow`'s `contextMenu`.
                        onMenu = { menuRoom = group },
                    )
                }
            }

            // **No more "New room" row here.** The screen carried creation **twice** — this
            // row (with its icon) **and** the header action ("New room" in accent, top
            // right). On the owner's explicit order (30/10/2026), the bottom row is
            // removed: "Nouveau salon" ("New room") has only **one** entry left, the header action, which
            // opens the same box in all three cases (no room, one room, several rooms).
            // Like the reference: as long as no profile is already set up as a bot, the row that offers
            // to make them talk to each other.
            if (hasDashboard && state.bots.isNotEmpty() && state.bots.none { it.setUp }) {
                ActionRow(
                    title = "Let bots message each other",
                    symbol = Symbols.Bubbles,
                    onClick = onLetBotsMessage,
                )
            }
            // "Reorder Bots" and "Sort Bots by Recent" are no longer rows of the section:
            // the reference puts them in a tile's menu (and a room's), the last group after the
            // separator (`orderingBots`).
        }

        // A tile's menu, opened on long press: **the nine entries of the reference** in their
        // order, then the house ones, and each with its action. The gesture table comes from
        // the caller (`botMenu`); without it, the screen keeps its own, the earlier one.
        menuBot?.let { bot ->
            val isOpen = bot.chatId != null && bot.chatId == openedChatId
            val muted = mutes.isMuted("bot:" + bot.name)
            BotCardMenu(
                bot = bot,
                isOpen = isOpen,
                unread = badges.forBot(
                    bot = bot,
                    isOpen = isOpen,
                    openStreaming = botChatStreaming(bot.chatId, conversations),
                    muted = muted,
                ).unread,
                mutes = mutes,
                order = state.order,
                mainName = mainName,
                // The panel opens against the touched tile, like the model's `Menu`.
                anchor = tileBounds["bot:" + bot.name],
                handlers = table,
                onDismiss = { menuBot = null },
                // "Delete" asks for confirmation first.
                onAskDelete = table.onDelete?.let { { target -> askDelete = target } },
            )
        }

        // A room's menu: marked read or unread when the server knows an activity for it,
        // silence — except a Hermes Desktop room, which does not notify —, then the order.
        menuRoom?.let { room ->
            RoomMenu(
                room = room,
                canMark = room.activityMark != null,
                unread = badges.forRoom(
                    id = room.id,
                    activityMark = room.activityMark,
                    isOpen = false,
                    muted = mutes.isMuted("room:" + room.id),
                ).unread,
                mutes = mutes,
                order = state.order,
                // The panel opens against the room's row, like the model's `Menu`.
                anchor = tileBounds["room:" + room.id],
                onDismiss = { menuRoom = null },
                onMarkRead = { viewModel.markRoomSeen(room) },
                onMarkUnread = { viewModel.markRoomUnread(room) },
                onReorder = { arranging = true },
                onSortByRecent = viewModel::sortByRecent,
            )
        }

        // The "Delete" confirmation: the bot leaves the **server**, this phone's
        // conversations stay in the history — the house rule, stated on screen.
        askDelete?.let { bot ->
            OnyxAlert(
                title = OnyxLocalization.format("Delete %1\$s?", bot.label(mainName)),
                message = "This removes the bot from your server. Its conversations stay in your history on this phone.",
                actions = listOf(
                    AlertAction("Delete", destructive = true) {
                        val act = table.onDelete
                        askDelete = null
                        act?.invoke(bot)
                    },
                    AlertAction("Cancel") { askDelete = null },
                ),
                onDismiss = { askDelete = null },
            )
        }

        // **The room creation box** (the reference's "New room"): the title, the name
        // with its prompt, the paragraph that tells the truth, the list of bots (face + circle), the
        // 2-to-6 constraint, and the validation action. What is created is opened at once —
        // on the gateway when it holds the rooms, kept here otherwise.
        if (newRoomOpen) {
            RoomCreationDialog(
                bots = state.bots,
                // The paragraph follows **the same** source as the opening: the Dashboard
                // credentials (what `createGroupRoom` looks at) and what the gateway says it holds.
                hasDashboard = hasDashboard,
                gatewayRunsRooms = state.groupsWritable,
                busy = makingRoom,
                error = roomProblem,
                onDismiss = { if (makingRoom == null) newRoomOpen = false },
                onCreate = { name, members ->
                    makingRoom = OnyxLocalization["Making the room…"]
                    roomProblem = null
                    roomScope.launch {
                        when (val made = viewModel.createGroup(name = name, members = members, mainName = mainName)) {
                            is GroupCreation.Made -> {
                                newRoomOpen = false
                                // The room opened, as the list opens it, its members checked.
                                // Without a way to open it (no caller), the group thread takes
                                // over: the old path remains, it is not removed.
                                onOpenGroup?.invoke(made.room) ?: onGroupChat()
                            }

                            is GroupCreation.Failed -> roomProblem = made.problem
                        }
                        makingRoom = null
                    }
                },
            )
        }
    }
}

/** Bots per row, and after how many they fold, like the reference's `botGrid`. */
internal const val BOT_GRID_COLUMNS = 5
internal const val BOT_GRID_FOLDS_AFTER = 6

/** The size of a face there (`botGrid.tileFace`). */
internal val BOT_TILE_FACE: Dp = 52.dp

/** More than six bots: the grid folds onto one row. */
internal fun botGridFolds(count: Int): Boolean = count > BOT_GRID_FOLDS_AFTER

/** How many faces the grid shows: the first four as long as it is folded. */
internal fun botGridShown(count: Int, expanded: Boolean): Int =
    if (botGridFolds(count) && !expanded) BOT_GRID_COLUMNS - 1 else count

/** What a grid cell is: a face, or the tile that follows the faces. */
internal enum class BotGridCell { Face, More, NewBot, AddBot, Hidden }

/**
 * The grid's cells, in the reference order: the faces, then — folded — "N more", otherwise
 * "New Bot" ("Add Bot" when there is no Dashboard), then the "Hidden" tile.
 *
 * **The "Hidden" tile no longer depends on the roster's chance.** Before this change it appeared only
 * if the server **already** reported a hidden bot (`hidden > 0`): on a device whose roster is
 * empty, or where no bot is hidden yet, the "Bots masqués" ("Hidden Bots") screen was therefore **unfindable**
 * — the invisible entry that the 66-route audit flagged (`AUDIT-ROUTES.md` §4). It is
 * now placed as soon as a Dashboard is configured (`hasDashboard`: it is its roster
 * `profiles.list` that carries the hidden bots) **and** the caller provides the entry
 * ([hiddenEntry]): the grid never draws a tile that opens nothing.
 */
internal fun botGridCells(
    count: Int,
    expanded: Boolean,
    hasDashboard: Boolean,
    hidden: Int = 0,
    hiddenEntry: Boolean = false,
): List<BotGridCell> {
    val cells = MutableList(botGridShown(count, expanded)) { BotGridCell.Face }
    if (botGridFolds(count) && !expanded) {
        cells += BotGridCell.More
    } else {
        cells += if (hasDashboard) BotGridCell.NewBot else BotGridCell.AddBot
        if (hiddenEntry && (hidden > 0 || hasDashboard)) cells += BotGridCell.Hidden
    }
    return cells
}

/** "4 more": the tile that unfolds the grid (`gridAction("\\(n) more")`). */
internal fun moreLabel(hidden: Int): String = "$hidden more"

/** The section header, like `foldingHeader`: its title, the count, and its chevron. */
@Composable
private fun SectionLabel(
    count: Int?,
    open: Boolean,
    onToggle: () -> Unit,
    arranging: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onToggle)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = "Bots",
            style = theme.footnote.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
        )
        count?.takeIf { it > 0 }?.let {
            BasicText(
                text = "$it",
                localize = false,
                style = theme.caption2.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
            )
        }
        Box(modifier = Modifier.weight(1f))
        // During "Reorder Bots", the header says "Done" in place of the chevron, like the
        // reference's bar (`if arrangingBots { Button("Done") }`).
        if (arranging) {
            BasicText(
                text = "Done",
                style = theme.body.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.onyxClickable(onClick = onToggle),
            )
        } else {
            OnyxIcon(
                symbol = if (open) Symbols.ChevronDown else Symbols.ChevronRight,
                tint = theme.tertiaryText,
                size = 12.dp,
                contentDescription = if (open) "Hide bots" else "Show bots",
            )
        }
    }
}

/**
 * The bots grid (`botGrid`): five faces per row, their name underneath, then the action
 * tile; beyond six bots, the first four and "N more", and "Fewer" once
 * unfolded.
 */
@Composable
private fun BotsGrid(
    bots: List<BotProfile>,
    faces: Map<String, String>,
    conversations: List<Conversation>,
    badges: BotBadges,
    mutes: Mutes,
    openedChatId: String?,
    mainName: String,
    expanded: Boolean,
    hasDashboard: Boolean,
    /** How many bots are hidden, as the screen holds it. */
    hiddenCount: Int,
    /** What the "Hidden" tile shows: the count, or 0 when no screen can open it. */
    hidden: Int = hiddenCount,
    /**
     * Does the caller supply the "Hidden" entry? Without it the grid does not place the tile: a
     * cell that opens nothing would be exactly the lie this change fixes.
     */
    hiddenEntry: Boolean = false,
    onExpand: () -> Unit,
    onFewer: () -> Unit,
    onOpenBot: (BotProfile) -> Unit,
    /** A tile's long press: its menu (the reference's `contextMenu`). */
    onOpenMenu: (BotProfile) -> Unit,
    /** Where each tile is (its name, its bounds): the menu opens near it. */
    onTileBounds: (String, Rect) -> Unit = { _, _ -> },
    onNewBot: () -> Unit,
    onOpenHiddenBots: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val folds = botGridFolds(bots.size)
    val cells = botGridCells(
        count = bots.size,
        expanded = expanded,
        hasDashboard = hasDashboard,
        hidden = hidden,
        hiddenEntry = hiddenEntry,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        var position = 0
        cells.chunked(BOT_GRID_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEachIndexed { index, cell ->
                    val at = position + index
                    Box(modifier = Modifier.weight(1f)) {
                        when (cell) {
                            BotGridCell.Face -> bots.getOrNull(at)?.let { bot ->
                                Box(
                                    modifier = Modifier.onGloballyPositioned {
                                        onTileBounds(bot.name, it.boundsInWindow())
                                    },
                                ) {
                                    BotTile(
                                        bot = bot,
                                        faceCode = faces[bot.name] ?: bot.faceCode,
                                        activity = badges.forBot(
                                            bot = bot,
                                            isOpen = bot.chatId != null && bot.chatId == openedChatId,
                                            openStreaming = botChatStreaming(bot.chatId, conversations),
                                            muted = mutes.isMuted("bot:" + bot.name),
                                        ),
                                        muted = mutes.isMuted("bot:" + bot.name),
                                        isOpen = bot.chatId != null && bot.chatId == openedChatId,
                                        mainName = mainName,
                                        onClick = { onOpenBot(bot) },
                                        onLongPress = { onOpenMenu(bot) },
                                    )
                                }
                            }

                            BotGridCell.More -> GridAction(
                                title = moreLabel(bots.size - botGridShown(bots.size, expanded)),
                                symbol = Symbols.ChevronDown,
                                onClick = onExpand,
                            )

                            BotGridCell.NewBot -> GridAction(
                                title = "New Bot",
                                symbol = Symbols.Plus,
                                onClick = onNewBot,
                            )

                            BotGridCell.AddBot -> GridAction(
                                title = "Add Bot",
                                symbol = Symbols.Plus,
                                onClick = onNewBot,
                            )

                            // The hidden bots tile (`gridAction("Hidden", systemImage: "eye.slash")`).
                            BotGridCell.Hidden -> GridAction(
                                title = "Hidden",
                                symbol = Symbols.EyeSlash,
                                onClick = onOpenHiddenBots,
                            )
                        }
                    }
                }
                // The missing cells of the last row: their width holds the columns.
                repeat(BOT_GRID_COLUMNS - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            position += row.size
        }

        // Unfolded, the grid closes again with one word, below it (`Fewer`).
        if (folds && expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(onClick = onFewer)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnyxIcon(symbol = Symbols.ChevronUp, tint = theme.secondaryText, size = 12.dp)
                BasicText(
                    text = "Fewer",
                    style = theme.caption.copy(color = theme.accent, fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/**
 * A bot in the grid (`botTile`): its face — always, never its initial —, its name
 * underneath, and its marks: the circle that spins when it works, the orange dot when it is
 * waiting for an answer, the accent dot when its answer has not been seen, the crossed-out bell when
 * it is muted. The tap opens its **chat**; the long press opens its menu, like the reference's `Menu`.
 */
@Composable
private fun BotTile(
    bot: BotProfile,
    faceCode: String?,
    activity: BotActivity,
    muted: Boolean,
    isOpen: Boolean,
    mainName: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val mood = when {
        // It is composing: brows drawn together, sideways glance (the spinning circle already says it
        // is working). It is waiting for an answer, or it is sitting quietly.
        activity.working -> BotMood.THINKING
        activity.needsYou -> BotMood.WAITING
        else -> BotMood.IDLE
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onLongPress = onLongPress, onClick = onClick)
            .background(
                color = if (isOpen) theme.accent.copy(alpha = 0.1f) else Color.Transparent,
                shape = RoundedCornerShape(OnyxSpace.ControlCorner),
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(modifier = Modifier.size(BOT_TILE_FACE)) {
            BotLook(
                name = bot.name,
                faceCode = faceCode,
                size = BOT_TILE_FACE,
                mood = mood,
            )
            when {
                activity.working -> Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(theme.pageColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                        .padding(2.dp),
                ) {
                    TileSpinner()
                }

                activity.needsYou -> Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .background(theme.pageColor, CircleShape),
                ) {
                    OnyxIcon(
                        symbol = Symbols.ExclamationCircle,
                        tint = theme.warning,
                        size = 13.dp,
                        contentDescription = "Needs you",
                    )
                }

                activity.unread -> Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(9.dp)
                        .background(theme.accent, CircleShape),
                )
            }
            if (muted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 3.dp)
                        .background(theme.pageColor, CircleShape)
                        .padding(1.dp),
                ) {
                    OnyxIcon(
                        symbol = mutedMark,
                        tint = theme.secondaryText,
                        size = 10.dp,
                        contentDescription = "Muted",
                    )
                }
            }
        }
        BasicText(
            text = bot.label(mainName),
            localize = false,
            style = theme.caption.copy(
                color = if (isOpen) theme.accent else theme.textColor,
                fontWeight = if (isOpen) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What follows the faces (New Bot, "N more"): a symbol where a face goes, and its name. */
@Composable
private fun GridAction(title: String, symbol: Int, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(modifier = Modifier.size(BOT_TILE_FACE), contentAlignment = Alignment.Center) {
            OnyxIcon(symbol = symbol, tint = theme.secondaryText, size = 24.dp, contentDescription = title)
        }
        BasicText(
            text = title,
            style = theme.caption.copy(color = theme.textColor),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The little spinning circle of a tile (`ProgressView().controlSize(.mini)`). */
@Composable
private fun TileSpinner() {
    val theme = LocalOnyxTheme.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "tileSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900),
        ),
        label = "tileSpinnerAngle",
    )
    Canvas(modifier = Modifier.size(12.dp).rotate(angle)) {
        drawArc(
            color = theme.secondaryText.copy(alpha = 0.25f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
        drawArc(
            color = theme.secondaryText,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
    }
}

/**
 * The overlaid faces of a group's members (`FaceStack`), for the row of a group
 * chat.
 */
@Composable
private fun MemberFaces(members: List<BotProfile>, size: Dp = 24.dp) {
    val theme = LocalOnyxTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(-8.dp),
    ) {
        members.take(2).forEach { bot ->
            Box(
                modifier = Modifier
                    .size(size)
                    .background(theme.pageColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BotLook(name = bot.name, faceCode = bot.faceCode, size = size - 3.dp)
            }
        }
    }
}

/**
 * A group chat, like the reference's `groupRow`: the overlaid faces of its
 * members, its name, then who is part of it — with the same marks as a bot: the circle that
 * spins when a bot is working there, "Needs you" when one is waiting, the unread dot, and the
 * mark of a room that Hermes Desktop holds.
 */
@Composable
private fun GroupRow(
    group: GroupChat,
    muted: Boolean,
    activity: BotActivity = BotActivity(),
    onClick: () -> Unit,
    /** The row's long press: its menu (the `groupRow`'s `contextMenu`). */
    onMenu: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onLongPress = onMenu, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The reference's "unread" dot, in the left margin.
        Box(modifier = Modifier.size(width = 8.dp, height = 24.dp), contentAlignment = Alignment.Center) {
            if (activity.unread) {
                Box(modifier = Modifier.size(8.dp).background(theme.accent, CircleShape))
            }
        }
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.CenterStart) {
            MemberFaces(members = group.members)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = group.name,
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (muted) {
                    OnyxIcon(symbol = mutedMark, tint = theme.secondaryText, size = 13.dp, contentDescription = "Muted")
                }
                if (activity.working) {
                    TileSpinner()
                } else {
                    group.updatedAt?.let {
                        BasicText(text = rowTime(it), style = theme.caption.copy(color = theme.secondaryText))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (activity.needsYou) {
                    BasicText(
                        text = "Needs you",
                        style = theme.caption2.copy(color = theme.warning, fontWeight = FontWeight.SemiBold),
                    )
                }
                if (group.inDesktop) {
                    OnyxIcon(
                        symbol = Symbols.DesktopComputer,
                        tint = theme.secondaryText,
                        size = 13.dp,
                        contentDescription = "Runs in Hermes Desktop",
                    )
                }
                if (group.members.isNotEmpty()) {
                    BasicText(
                        text = group.members.map { it.label() }.joinToString(", "),
                        localize = false,
                        style = theme.subheadline.copy(color = theme.secondaryText),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A row that acts (New Group Chat, let bots message each other): tinted symbol, title. */
@Composable
private fun ActionRow(title: String, symbol: Int, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            OnyxIcon(symbol = symbol, tint = theme.textColor, size = 18.dp)
        }
        BasicText(text = title, style = theme.body.copy(color = theme.textColor))
    }
}

/** The face picker: shape, eyes, mouth, colour, and something to surprise you. */
@Composable
private fun FacePicker(current: BotFace, onChange: (BotFace) -> Unit) {
    val theme = LocalOnyxTheme.current

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        FaceRow(
            label = "Shape",
            options = FaceShape.entries.map { it.id },
            selected = current.shape.id,
        ) { index -> onChange(current.copy(shape = FaceShape.entries[index])) }

        FaceRow(
            label = "Eyes",
            options = FaceEyes.entries.map { it.id },
            selected = current.eyes.id,
        ) { index -> onChange(current.copy(eyes = FaceEyes.entries[index])) }

        FaceRow(
            label = "Mouth",
            options = FaceMouth.entries.map { it.id },
            selected = current.mouth.id,
        ) { index -> onChange(current.copy(mouth = FaceMouth.entries[index])) }

        BasicText(
            text = "Colour",
            style = theme.caption2.copy(color = theme.secondaryText),
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotFace.colours.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .onyxClickable(pressedScale = 0.9f) { onChange(current.copy(colour = index)) },
                ) {
                    BotLook(
                        name = "colour-$index",
                        size = 26.dp,
                        faceCode = BotFace(current.shape, current.eyes, current.mouth, index).code(),
                    )
                    if (index == current.colour) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(theme.accent.copy(alpha = 0.25f), RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }

        BasicText(
            text = "Surprise me",
            style = theme.body.copy(color = theme.accent),
            modifier = Modifier
                .padding(top = 10.dp)
                .onyxClickable {
                    onChange(
                        current.copy(
                            shape = FaceShape.entries.random(),
                            eyes = FaceEyes.entries.random(),
                            mouth = FaceMouth.entries.random(),
                            colour = (current.colour + 3) % BotFace.colours.size,
                        ),
                    )
                },
        )
    }
}

@Composable
private fun FaceRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (Int) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = label,
            style = theme.caption2.copy(color = theme.secondaryText),
            modifier = Modifier.size(width = 46.dp, height = 16.dp),
        )
        options.forEachIndexed { index, option ->
            val active = option == selected
            BasicText(
                text = option,
                localize = false,
                style = theme.caption.copy(
                    color = if (active) theme.userText else theme.secondaryText,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                modifier = Modifier
                    .background(
                        color = if (active) theme.accent else theme.fill,
                        shape = RoundedCornerShape(50),
                    )
                    .onyxClickable { onSelect(index) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}
