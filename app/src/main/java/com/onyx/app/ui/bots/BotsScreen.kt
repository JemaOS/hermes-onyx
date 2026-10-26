package com.onyx.app.ui.bots

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
import com.onyx.app.ui.common.mutedMark
import com.onyx.app.ui.conversations.BotActivity
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.conversations.rowTime
import com.onyx.app.ui.conversations.botActivity
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Bots, ou la liste atteinte depuis les conversations.
 *
 * Comme la section « Bots » de: les visages des bots côte à côte
 * (`botGrid`) avec leurs marques (au travail, en attente de vous, pas encore lu, tu), la tuile
 * qui en fait un nouveau, la tuile des bots cachés, puis les salles du serveur, les discussions de
 * groupe et les lignes qui agissent.
 *
 * **Ce que la source impose.** `botGrid` est le dessin *par défaut* de la section
 * (`showBotRows == true`, l'`@AppStorage` par défaut), et la capture « réelle »
 * (`01755fbe…/img_aff8bdadf42e.jpg`) le confirme: des vignettes de visage, deux ici, puis
 * « + Nouveau … ». Les lignes d'aperçu d'une conversation (`botRow`) n'apparaissent dans le
 * que pendant « Reorder Bots » (`arrangingBots`), tenues par leurs poignées; la maquette
 * promo « Meet the crew. » (`37f71832…`) montre cet état-là, pas le défaut.
 */
@Composable
fun BotsScreen(
    viewModel: BotsViewModel,
    /** Nul en deux panneaux: la barre latérale est là, la flèche n'aurait rien à quitter. */
    onBack: (() -> Unit)?,
    /** Le tap d'une tuile: le **chat** du bot, comme `openBot` — jamais son éditeur. */
    onOpenBotChat: (BotProfile) -> Unit,
    /** « Edit Bot… » du menu d'une tuile. */
    onEditBot: (BotProfile) -> Unit,
    onGroupChat: () -> Unit,
    conversations: List<Conversation> = emptyList(),
    groups: List<GroupChat> = emptyList(),
    hasDashboard: Boolean = true,
    openedChatId: String? = null,
    mainName: String = "Onyx",
    onNewBot: () -> Unit = {},
    onLetBotsMessage: () -> Unit = {},
    /** « Change Face… » du menu d'une tuile; sans lui, l'entrée n'apparaît pas. */
    onChangeFace: ((BotProfile) -> Unit)? = null,
    /** « Schedules… » du menu d'une tuile; sans lui, l'entrée n'apparaît pas. */
    onSchedules: ((BotProfile) -> Unit)? = null,
    /** « Voice… » du menu d'une tuile (`BotVoicePicker`); sans lui, l'entrée n'apparaît pas. */
    onVoice: ((BotProfile) -> Unit)? = null,
    /** Combien de bots sont cachés: la tuile « Hidden » n'apparaît que s'il y en a. */
    hiddenCount: Int = 0,
    /** Ouvre l'écran « Hidden Bots »; sans lui, la tuile reste absente. */
    onOpenHiddenBots: (() -> Unit)? = null,
    onOpenGroup: ((GroupChat) -> Unit)? = null,
    onRoomSeen: ((GroupChat) -> Unit)? = null,
    /**
     * Le contrôle du volet (`sidebar.left`): la grille des bots **est** le volet des deux
     * panneaux; repliée, c'est le détail qui le porte.
     */
    sidebarControl: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mutes = remember { Mutes(context) }
    // Plus de six bots: la grille se replie sur une rangée, ouverte ou fermée comme elle a été
    // laissée (`sessions.botsExpanded` ).
    var expanded by remember { mutableStateOf(false) }
    // La section entière se replie aussi sous son titre, comme le `foldingHeader`: le
    // compte n'apparaît que repliée.
    var open by remember { mutableStateOf(true) }
    // « Reorder Bots »: la section ne montre que ses lignes, à poignées, et n'ouvre rien.
    var arranging by remember { mutableStateOf(false) }
    // La tuile ou la salle dont le menu est ouvert (l'appui long), comme le `contextMenu`.
    var menuBot by remember { mutableStateOf<BotProfile?>(null) }
    var menuRoom by remember { mutableStateOf<GroupChat?>(null) }
    // Où chaque tuile (et chaque salle) est à l'écran, dans la fenêtre: son menu s'ouvre près
    // d'elle. La table se remplit aux mises en page et ne recompose rien.
    val tileBounds = remember { mutableMapOf<String, Rect>() }
    val badges = BotBadges(
        liveKinds = state.liveKinds,
        roomKinds = state.roomKinds,
        seenMarks = state.seenMarks,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Bots",
                onBack = onBack,
                leading = sidebarControl,
                action = if (arranging) "Done" else "Group chat",
                onAction = { if (arranging) arranging = false else onGroupChat() },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            state.problem?.let { problem ->
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                        BasicText(
                            text = problem,
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OnyxRowDivider()
                    OnyxRow(onClick = viewModel::refresh) {
                        BasicText("Try Again", style = theme.body.copy(color = theme.accent))
                    }
                }
            }

            if (state.loading) {
                OnyxFormSection {
                    OnyxRow { BasicText("Loading from Onyx…", style = theme.body.copy(color = theme.secondaryText)) }
                }
                return@Column
            }

            // L'en-tête de section, comme le `foldingHeader("Bots", count:)`: son titre, le
            // compte — mais seulement repliée —, et le chevron qui la plie.
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
                onExpand = { expanded = true },
                onFewer = { expanded = false },
                onOpenBot = onOpenBotChat,
                onOpenMenu = { bot -> menuBot = bot },
                // Où la tuile est: son menu s'ouvre près d'elle, comme celui d'une rangée.
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

            // Les discussions de groupe, sous la grille (`groupRow`), puis les lignes qui
            // font agir (`sectionAction`).
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
                        // L'appui long ouvre le menu de la salle, comme le `contextMenu` du `groupRow`.
                        onMenu = { menuRoom = group },
                    )
                }
            }

            ActionRow(title = "New Group Chat", symbol = Symbols.Person3, onClick = onGroupChat)
            // Comme: tant qu'aucun profil n'est déjà réglé en bot, la ligne qui propose
            // de les faire se parler.
            if (hasDashboard && state.bots.isNotEmpty() && state.bots.none { it.setUp }) {
                ActionRow(
                    title = "Let bots message each other",
                    symbol = Symbols.Bubbles,
                    onClick = onLetBotsMessage,
                )
            }
            // « Reorder Bots » et « Sort Bots by Recent » ne sont plus des lignes de la section:
            // les met dans le menu d'une tuile (et d'une salle), dernier groupe après le
            // filet (`orderingBots`).
        }

        // Le menu d'une tuile, ouvert à l'appui long: les mêmes entrées que le `botMenu` ,
        // dans son ordre, et chacune son action.
        menuBot?.let { bot ->
            val isOpen = bot.chatId != null && bot.chatId == openedChatId
            val muted = mutes.isMuted("bot:" + bot.name)
            BotTileMenu(
                bot = bot,
                isOpen = isOpen,
                unread = badges.forBot(
                    bot = bot,
                    isOpen = isOpen,
                    openStreaming = conversations.any { it.serverSessionId == bot.chatId && it.messages.any { message -> message.isStreaming } },
                    muted = muted,
                ).unread,
                mutes = mutes,
                order = state.order,
                // Le panneau s'ouvre contre la tuile touchée, comme le `Menu` du modèle.
                anchor = tileBounds["bot:" + bot.name],
                onDismiss = { menuBot = null },
                onMarkRead = { viewModel.markBotSeen(bot) },
                onMarkUnread = { viewModel.markBotUnread(bot) },
                // « Voice… »: la voix du bot, choisie pour lui seul (`BotVoicePicker`).
                onVoice = onVoice?.let { open -> { open(bot) } },
                onSchedules = onSchedules?.let { open -> { open(bot) } },
                onRemoveFromOnyx = { viewModel.removeAPIBot(bot) },
                onEdit = { onEditBot(bot) },
                onChangeFace = onChangeFace?.let { open -> { open(bot) } },
                onHide = { viewModel.hideBot(bot) },
                onReorder = { arranging = true },
                onSortByRecent = viewModel::sortByRecent,
            )
        }

        // Le menu d'une salle: marquée lue ou non lue quand le serveur lui connaît une activité,
        // le silence — sauf une salle d'Hermes Desktop, qui ne notifie pas —, puis l'ordre.
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
                // Le panneau s'ouvre contre la rangée de la salle, comme le `Menu` du modèle.
                anchor = tileBounds["room:" + room.id],
                onDismiss = { menuRoom = null },
                onMarkRead = { viewModel.markRoomSeen(room) },
                onMarkUnread = { viewModel.markRoomUnread(room) },
                onReorder = { arranging = true },
                onSortByRecent = viewModel::sortByRecent,
            )
        }
    }
}

/** Bots à la rangée, et à partir de combien ils se replient, comme `botGrid`. */
internal const val BOT_GRID_COLUMNS = 5
internal const val BOT_GRID_FOLDS_AFTER = 6

/** La taille d'un visage là (`botGrid.tileFace`). */
internal val BOT_TILE_FACE: Dp = 52.dp

/** Plus de six bots: la grille se replie sur une rangée. */
internal fun botGridFolds(count: Int): Boolean = count > BOT_GRID_FOLDS_AFTER

/** Combien de visages la grille montre: les quatre premiers tant qu'elle est repliée. */
internal fun botGridShown(count: Int, expanded: Boolean): Int =
    if (botGridFolds(count) && !expanded) BOT_GRID_COLUMNS - 1 else count

/** Ce qu'une case de la grille est: un visage, ou la tuile qui suit les visages. */
internal enum class BotGridCell { Face, More, NewBot, AddBot, Hidden }

/**
 * Les cases de la grille, dans l'ordre: les visages, puis — repliée — « N more », sinon
 * « New Bot » (« Add Bot » quand il n'y a pas de Dashboard), puis la tuile « Hidden » quand des
 * bots sont cachés (`gridAction("Hidden", systemImage: "eye.slash")`).
 */
internal fun botGridCells(count: Int, expanded: Boolean, hasDashboard: Boolean, hidden: Int = 0): List<BotGridCell> {
    val cells = MutableList(botGridShown(count, expanded)) { BotGridCell.Face }
    if (botGridFolds(count) && !expanded) {
        cells += BotGridCell.More
    } else {
        cells += if (hasDashboard) BotGridCell.NewBot else BotGridCell.AddBot
        if (hidden > 0) cells += BotGridCell.Hidden
    }
    return cells
}

/** « 4 more »: la tuile qui déplie la grille (`gridAction("\\(n) more")`). */
internal fun moreLabel(hidden: Int): String = "$hidden more"

/** L'en-tête de la section, comme le `foldingHeader`: son titre, le compte, et son chevron. */
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
        // Pendant « Reorder Bots », l'en-tête dit « Done » à la place du chevron, comme la barre
        // (`if arrangingBots { Button("Done") }`).
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
 * La grille des bots (`botGrid`): cinq visages à la rangée, leur nom dessous, puis la tuile
 * d'action; au-delà de six bots, les quatre premiers et « N more », et « Fewer » une fois
 * dépliée.
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
    /** Combien de bots sont cachés, tel que l'écran le tient. */
    hiddenCount: Int,
    /** Ce que la tuile « Hidden » montre: le compte, ou 0 quand aucun écran ne peut l'ouvrir. */
    hidden: Int = hiddenCount,
    onExpand: () -> Unit,
    onFewer: () -> Unit,
    onOpenBot: (BotProfile) -> Unit,
    /** L'appui long d'une tuile: son menu (`contextMenu` ). */
    onOpenMenu: (BotProfile) -> Unit,
    /** Où chaque tuile est (son nom, ses limites): le menu s'ouvre près d'elle. */
    onTileBounds: (String, Rect) -> Unit = { _, _ -> },
    onNewBot: () -> Unit,
    onOpenHiddenBots: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val folds = botGridFolds(bots.size)
    val cells = botGridCells(count = bots.size, expanded = expanded, hasDashboard = hasDashboard, hidden = hidden)

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
                                            openStreaming = conversations.any { it.serverSessionId == bot.chatId && it.messages.any { message -> message.isStreaming } },
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

                            // La tuile des bots cachés (`gridAction("Hidden", systemImage: "eye.slash")`).
                            BotGridCell.Hidden -> GridAction(
                                title = "Hidden",
                                symbol = Symbols.EyeSlash,
                                onClick = onOpenHiddenBots,
                            )
                        }
                    }
                }
                // Les cases manquantes de la dernière rangée: leur largeur tient les colonnes.
                repeat(BOT_GRID_COLUMNS - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            position += row.size
        }

        // Dépliée, la grille se referme d'un mot, sous elle (`Fewer`).
        if (folds && expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(onClick = onFewer)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnyxIcon(symbol = Symbols.ChevronUp, tint = theme.accent, size = 12.dp)
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
 * Un bot dans la grille (`botTile`): son visage — toujours, jamais son initiale —, son nom
 * dessous, et ses marques: le rond qui tourne quand il travaille, le point orange quand il
 * attend une réponse, le point d'accent quand sa réponse n'a pas été vue, la cloche barrée quand
 * il est tu. Le tap ouvre son **chat**; l'appui long ouvre son menu, comme le `Menu`.
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
        // Il compose: sourcils rapprochés, regard de côté (le rond qui tourne dit déjà qu'il
        // travaille). Il attend une réponse, ou il se tient tranquille.
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
                shape = RoundedCornerShape(10.dp),
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
                        .background(theme.pageColor, RoundedCornerShape(6.dp))
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ce qui suit les visages (New Bot, « N more »): un symbole là où va un visage, et son nom. */
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
            OnyxIcon(symbol = symbol, tint = theme.accent, size = 24.dp, contentDescription = title)
        }
        BasicText(
            text = title,
            style = theme.caption.copy(color = theme.textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Le petit rond qui tourne d'une tuile (`ProgressView.controlSize(.mini)`). */
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
 * Les visages superposés des membres d'un groupe (`FaceStack`), pour la rangée d'une discussion
 * de groupe.
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
 * Une discussion de groupe, comme le `groupRow`: les visages superposés de ses
 * membres, son nom, puis qui en fait partie — avec les mêmes marques qu'un bot: le rond qui
 * tourne quand un bot y travaille, « Needs you » quand l'un attend, le point du non-lu, et la
 * marque d'une salle qu'Hermes Desktop tient.
 */
@Composable
private fun GroupRow(
    group: GroupChat,
    muted: Boolean,
    activity: BotActivity = BotActivity(),
    onClick: () -> Unit,
    /** L'appui long de la rangée: son menu (`contextMenu` du `groupRow`). */
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
        // Le point « non lu » , dans la marge gauche.
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
                    maxLines = 1,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Une ligne qui fait agir (New Group Chat, laisser les bots se parler): symbole teinté, titre. */
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
            OnyxIcon(symbol = symbol, tint = theme.accent, size = 18.dp)
        }
        BasicText(text = title, style = theme.body.copy(color = theme.textColor))
    }
}

/** Le sélecteur de visage: forme, yeux, bouche, couleur, et de quoi surprendre. */
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
