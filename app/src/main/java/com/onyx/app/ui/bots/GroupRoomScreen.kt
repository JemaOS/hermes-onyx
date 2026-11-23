package com.onyx.app.ui.bots

import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.runtime.mutableIntStateOf

import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.GroupDraft
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupState
import com.onyx.app.data.net.strOrNull
import com.onyx.app.data.room.ROOM_HANDOFF_KIND
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.components.ApprovalCard
import com.onyx.app.ui.chat.components.MarkdownText
import com.onyx.app.ui.common.Mutes
import com.onyx.app.ui.common.muteMenuItems
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.GlassCircleButton
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.SafeBarSpacing
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.SurfaceRole
import com.onyx.app.ui.design.glassBarShell
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxHazeSource
import com.onyx.app.ui.design.LocalGlassSourcePresence
import com.onyx.app.ui.design.OnyxLens
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.SpeechEngine
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import com.onyx.app.data.local.ChatPanelStore
import com.onyx.app.ui.chat.ChatFeed
import com.onyx.app.ui.chat.ChatFiles
import com.onyx.app.ui.chat.ChatFilesPane
import com.onyx.app.ui.chat.ChatFeedPane
import com.onyx.app.ui.chat.FileViewers
import com.onyx.app.ui.design.ChatTab
import com.onyx.app.ui.design.ChatTabBar
import com.onyx.app.ui.design.chatTabCount
import com.onyx.app.ui.design.chatTabIndexOf
import com.onyx.app.ui.design.TabFace
import com.onyx.app.ui.design.botFaceColour
import com.onyx.app.ui.design.rememberTabFaceCycleIndex
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.PlateGeometry
import com.onyx.app.ui.design.onyxLensPage
import com.onyx.app.ui.design.OnyxBase
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.composerBottomInsets
import com.onyx.app.ui.design.liquidGlass
import com.onyx.app.ui.design.socleShadow
import kotlinx.coroutines.launch

/**
 * The thread of a server bot room.
 *
 * Port of `GroupChatView` (Echo/Views/GroupChatView:8): the header (Conversations, the
 * members' faces, the name, the room menu), what has been said read from the room log
 * (each reply under its bot's face and name), the person's messages, the replies
 * the bots are writing at that instant, the room state (« The bots are talking… » (the bots are talking), what awaits
 * the person), and the composer — a Desktop room's replaced by the note that says where it is
 * spoken.
 *
 * Nothing here touches the app's local thread (`GroupChatViewModel`): what is written goes
 * into the room (`groups.send`), and what is displayed comes from its log.
 */
@Composable
fun GroupRoomScreen(
    viewModel: GroupRoomViewModel,
    server: ServerConfig,
    onBack: () -> Unit,
    /**
     * **The drawer control**, the one of a bot thread: one press collapses the list, another
     * expands it. The room's left pill only went back, whereas the
     * room lives **inside** the drawer.
     */
    sidebarToggle: (@Composable () -> Unit)? = null,
    /**
     * The room's pencil: a **new conversation of this room** (its members, its name).
     * Provided, the button is drawn; without it, the header carries none.
     */
    onNewConversation: (() -> Unit)? = null,
    /**
     * The composer's Talk button opens the room's voice scene: it reads this thread
     * (`GroupRoomFeed`), hence the server's replies and who is speaking; the recognized
     * question goes out through `groups.send` and the red Stop through `groups.stop`. Without this lambda, no
     * Talk button is drawn.
     */
    onOpenVoice: (() -> Unit)? = null,
    /** The room was ended here: the list lets it go. */
    onEnded: () -> Unit,
    /**
     * The AI model **of a room member**, chosen from here: the room menu lists its bots
     * (« Bot Models » (Bot Models)) and this lambda opens the sheet for the one touched. The choice is written
     * to its profile (`profiles.configure`), so it is the model Hermes gives it **in this
     * room** as in its own conversations. Absent, the entry is not drawn.
     */
    onPickBotModel: ((String) -> Unit)? = null,
    speechLocale: String = SpeechEngine.DEFAULT_LOCALE,
    focusComposer: Boolean = false,
    onComposerFocused: () -> Unit = {},
    /**
     * The members' face, resolved by the **same** function as the list ([BotFaceResolver]):
     * the header, the room's large faces, the replies, the drafts and the `@` mentions
     * read it, so a member shows here exactly the face of its profile. Absent, the names
     * decide alone — never a random draw: the same face on every opening.
     */
    faceResolver: BotFaceResolver = BotFaceResolver.none,
    /**
     * Opens a file exchanged **in this room** (the Files tab), with the viewer of its family:
     * the route is the one of a conversation, so a room file opens like a bot file.
     */
    onOpenFile: (android.net.Uri, String, String) -> Unit,
    modifier: Modifier = Modifier,
    chatTextScale: Float = 1f,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // **The room's lead**: a default recipient, kept on this phone. The menu
    // checks it, and sending reads it — without a lead, the room behaves exactly as before.
    val lead by viewModel.lead.collectAsStateWithLifecycle()
    val room = state.room
    val listState = rememberLazyListState()
    val composerFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val mutes = remember(context) { Mutes(context) }
    // The members' face: the **same** resolution as the list (`BotFaceResolver`), never a
    // separate read of the local choice — that is what made the room diverge from the list.
    val face: (String) -> String? = faceResolver::codeOf
    // And this room's voice scene reads the **same** table: the voice thread and the orb therefore
    // draw the room's face, never a creature inferred from the name.
    LaunchedEffect(faceResolver) { viewModel.useFaces(faceResolver::codeOf) }

    // ---- The bottom tabs: the same bar as a one-bot conversation. ----
    // The chosen tab is kept **per room** (`ChatPanelStore`): each room reopens its own.
    val panels = remember(context) { ChatPanelStore(context) }
    val panelKey = "room:" + room.id
    val pagerState = rememberPagerState(
        initialPage = chatTabIndexOf(panels.selectedTab(panelKey)),
        pageCount = { chatTabCount },
    )
    val scope = rememberCoroutineScope()
    // ---- The room's face marker: it CYCLES the faces of its members ----
    // The owner's request, looking at a group screenshot: « faire passer le visage de un bot
    // par un bot avec un temps pour passer à l'autre : 8 secondes » (show each bot's face one after another, 8 seconds apart).
    // Each room member is therefore a face of the queue ([TabFaceCycle.PeriodMs] each, in a loop), and the shown index comes
    // from **a single** loop ([rememberTabFaceCycleIndex]) — it is the one that **also** gives the tint,
    // so the marker cannot show one bot in another's color.
    //
    // A room with fewer than two members does not cycle: `count <= 1` holds the index at zero and the
    // marker keeps its single face. That is the rule, not a special case added here.
    val roomMembers = room.members
    val faceCycleIndex = rememberTabFaceCycleIndex(roomMembers.size)
    val faceCycleFaces = remember(roomMembers, faceResolver, theme) {
        roomMembers.map { member ->
            TabFace(
                key = member.profile,
                accent = botFaceColour(member.profile, face(member.profile), theme),
                content = {
                    BotLook(
                        name = member.profile,
                        faceCode = face(member.profile),
                        size = PlateGeometry.TabCell,
                    )
                },
            )
        }
    }
    // The room's tint follows the shown face (the accent of the tabs, the feed and the files):
    // it is this bot's `botFaceColour`, so the identity does not contradict itself. Without members, the theme's
    // accent remains — an empty room has no face to tint.
    val tabAccent = faceCycleFaces.getOrNull(faceCycleIndex)?.accent ?: theme.accent
    val nowMillis = remember(panelKey) { System.currentTimeMillis() }
    var feedFeedback by remember(panelKey) { mutableStateOf(panels.feedbackFor(panelKey)) }
    // The subject a feed « Discuss » writes into the field: the draft lives in the
    // composer, the room only passes it the subject **once**.
    var pendingDraft by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState, panelKey) {
        val saved = chatTabIndexOf(panels.selectedTab(panelKey))
        if (pagerState.settledPage != saved) pagerState.scrollToPage(saved)
        snapshotFlow { pagerState.settledPage }.collect { tabPage ->
            panels.setSelectedTab(panelKey, ChatTab.entries[tabPage.coerceIn(0, chatTabCount - 1)].key)
        }
    }

    var showingMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var confirmingEnd by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var findIndex by remember { mutableStateOf(0) }
    /** The first message shown, by its number in the log (`shownFrom` of the reference). */
    var shownFrom by remember { mutableStateOf<Int?>(null) }

    val rows = state.rows
    val page = remember(rows, shownFrom) { roomPage(rows, shownFrom) }
    val matches = remember(rows, findText, findOpen) { roomMatches(rows, findOpen, findText) }
    val current = matches.getOrNull(findIndex.coerceIn(0, (matches.size - 1).coerceAtLeast(0)))

    // The room's log, read as messages: the feed and the Files tab read the
    // **same** log as the thread — so each room keeps ITS files.
    val roomMessages = remember(rows, room.members) { roomThreadMessages(rows, room.members) }
    val feedCards = remember(roomMessages, room.name) {
        ChatFeed.cards(roomMessages.map { it.copy(text = AttachedFileNote.strip(it.text)) }, room.name)
    }
    val roomFiles = remember(roomMessages) { ChatFiles.conversationFiles(roomMessages) }

    // The thread is followed while the screen is open and the app is in front, like the
    // `task(id: scenePhase == .active)` of the reference: the log and the in-progress replies are re-read
    // once per second while the bots are talking, and every five seconds otherwise.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, room.id) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.feed.follow() }
    }

    LaunchedEffect(focusComposer) {
        if (focusComposer) {
            composerFocusRequester.requestFocus()
            keyboard?.show()
            onComposerFocused()
        }
    }

    // The first log that arrives opens the page on its last messages (`settleShown`).
    LaunchedEffect(rows) {
        if (shownFrom == null && rows.isNotEmpty()) shownFrom = rows[(rows.size - PAGE).coerceAtLeast(0)].seq
    }

    // A novelty is followed from the bottom only: re-reading higher up is not pulled back.
    val itemCount = roomItemCount(state, page, rows)
    var previousCount by remember { mutableStateOf(itemCount) }
    LaunchedEffect(itemCount) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val wasAtEnd = previousCount == 0 || lastVisible >= previousCount - 1
        previousCount = itemCount
        if (itemCount > 0 && wasAtEnd) listState.animateScrollToItem(itemCount - 1)
    }

    LaunchedEffect(findText) {
        findIndex = 0
        if (matches.isNotEmpty()) findGoTo(matches.first(), page, shownFrom)?.let { shownFrom = it }
    }

    LaunchedEffect(findIndex, page.shown, matches) {
        matches.getOrNull(findIndex)?.let { seq ->
            val index = page.shown.indexOfFirst { it.seq == seq }
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }

    // **The bars' height, measured.** The thread extends **underneath**: without this there is, under the
    // plate and under the bar, only the page background — a flat color, which the lens cannot
    // bend. It is the measurement that gives the glass something to curve.
    var topInsetPx by remember { mutableIntStateOf(0) }
    var bottomInsetPx by remember { mutableIntStateOf(0) }
    val insetDensity = LocalDensity.current
    val topInsetDp = with(insetDensity) { topInsetPx.toDp() }
    val bottomInsetDp = with(insetDensity) { bottomInsetPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
    // **THE GLASS PAGE, OVER THE WHOLE SCREEN.** It is what the room's bar and plate
    // sample (see `OnyxLensGlass`). It is registered **here**, and the header as well as the
    // plate are painted **afterwards**, hence **outside** the layer: a glass pane never draws inside the
    // texture it reads — that is what crashed the screen when the whole `Column`
    // was registered. Without it, the bar and the plate read as matte: nothing to curve.
    Box(
        modifier = Modifier
            .fillMaxSize()
            // **The Haze source**, as in a bot thread: what the glass samples when the
            // lens is not available. The room had none — hence the matte glass.
            .then(
                com.onyx.app.ui.design.LocalHazeState.current
                    ?.let { state -> Modifier.onyxHazeSource(state) }
                    ?: Modifier,
            )
            .then(
                com.onyx.app.ui.design.LocalOnyxLensPage.current
                    ?.let { page -> page.let(Modifier::onyxLensPage) }
                    ?: Modifier,
            ),
    ) {
        // **THE PAGE'S MATERIAL, INSIDE THE HAZE SOURCE.** It was on the root `Box`, hence
        // **outside** the source: the glass sampled only the thread and never the background — it therefore
        // sat on a flat fill, and the glass read beneath it was only a tint laid over
        // emptiness (measured: behind the anchored menu, the background's standard deviation was 1.3 for real
        // content varying from 0.6 to 94.7). Placed **here**, it is what the glass samples — that is
        // exactly what the bot thread does (`Box(Modifier.matchParentSize().matierePage(theme))`
        // in the source).
        Box(Modifier.matchParentSize().onyxPage(theme))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = SafeBarSpacing.GapDp.dp),
        ) {
            Box(
                // **The thread, full screen, offset by the height of the bars.** It is **it**, the texture that
                // the room's glass panes curve — a flat color shows no bend, a scrolling thread
                // does. It lives in the registered layer (see above), so no glass pane
                // draws inside what it reads.
                modifier = Modifier
                    .fillMaxSize()
                    // **No layout margin.** The thread goes down **under the bar and under the
                // plate**: that is what the lens curves. Both margins live in the
                // **scroll** (`contentPadding` of the log), otherwise there would be, under the glass,
                // only a flat color — and the glass would look matte.
            ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { tabPage ->
                when (tabPage) {
                // ---- Chat: the room's log, unchanged. ----
                0 -> {
                if (rows.isEmpty() && state.sending.isEmpty() && state.shownDrafts.isEmpty() && !state.loaded) {
                    RoomOpening(room = room, face = face)
                } else {
                    // The Settings text scale is set at the **root** — the activity reads it
                    // (`LocalOnyxTextScale`), the theme applies it to the whole tree. `theme` is therefore
                    // **already** the chosen scale: setting it again here with `chatTextScale` would apply it
                    // a second time (1.23 × 1.23 = 1.51).
                    CompositionLocalProvider(LocalOnyxTheme provides theme) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp + topInsetDp,
                                // The composer's plate: the last line passes **above**
                                // it instead of hiding beneath, and the thread keeps scrolling
                                // **under** the glass (see the registered page).
                                bottom = 12.dp + bottomInsetDp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            if (room.omitted > 0) {
                                item(key = "omitted") {
                                    SelectableText(
                                        text = "Earlier messages are in Hermes Desktop.",
                                        style = theme.footnote.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (page.hidden > 0) {
                                item(key = "earlier") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onyxClickable {
                                                shownFrom = rows[(page.start - PAGE).coerceAtLeast(0)].seq
                                            },
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OnyxIcon(Symbols.ArrowUpCircle, tint = theme.accent, size = 15.dp)
                                        Spacer(Modifier.size(6.dp))
                                        BasicText(
                                            text = OnyxLocalization.format("Show earlier messages (%1\$d)", page.hidden),
                                            style = theme.footnote.copy(color = theme.accent),
                                        )
                                    }
                                }
                            }
                            if (rows.isEmpty() && state.sending.isEmpty()) {
                                item(key = "opening") { RoomOpening(room = room, face = face, inline = true) }
                            }
                            items(page.shown, key = { "seq-" + it.seq + "-" + it.kind }) { event ->
                                RoomEventRow(
                                    event = event,
                                    room = room,
                                    server = server,
                                    face = face,
                                    highlighted = current == event.seq,
                                )
                            }
                            // The person's messages not yet in the log.
                            items(state.sending, key = { "sending-" + it.id }) { pending ->
                                MineRow(
                                    text = AttachedFileNote.split(pending.text).text,
                                    paths = AttachedFileNote.split(pending.text).paths,
                                    files = pending.files,
                                    server = server,
                                    // A definitive send is no longer « en attente » (waiting): it is there, marked,
                                    // and the reason is read in the problems banner.
                                    waiting = !pending.failed,
                                )
                            }
                            // The replies being written, under their bot, until the room has them.
                            items(state.shownDrafts, key = { "draft-" + it.id }) { draft ->
                                RoomDraftRow(draft = draft, room = room, face = face)
                            }
                            if (state.status.waitsOnPerson || (state.busy && state.writing == null)) {
                                item(key = "talking") { TalkingLine(status = state.status) }
                            }
                            items(
                                state.status.approvals.filterNot { state.answering.contains(it.id) },
                                key = { "approval-" + it.id },
                            ) { approval ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BasicText(
                                        text = OnyxLocalization.format(
                                            "%1\$s wants to run a command",
                                            room.member(approval.memberID)?.label ?: OnyxLocalization["A bot"],
                                        ),
                                        localize = false,
                                        style = theme.footnote.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
                                    )
                                    ApprovalCard(
                                        request = approval.request,
                                        onChoose = { choice -> viewModel.answer(approval, choice) },
                                    )
                                    BasicText(
                                        text = "Not answered in about 5 minutes, it isn't run.",
                                        style = theme.caption.copy(color = theme.secondaryText),
                                    )
                                }
                            }
                            items(
                                state.status.retries.filterNot { state.answering.contains(it) },
                                key = { "retry-" + it },
                            ) { task ->
                                RetryCard(
                                    task = task,
                                    who = viewModel.feed.memberForTask(task) ?: OnyxLocalization["A bot"],
                                    onRetry = { viewModel.retry(task) },
                                )
                            }
                            state.problem?.let { problem ->
                                item(key = "problem") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 14.dp)
                                        // What the server refused passes as is, never translated.
                                        SelectableText(
                                            text = problem,
                                            localize = false,
                                            style = theme.footnote.copy(color = theme.warning),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                // ---- Feed: the room's replies, as dated cards (the same log). ----
                1 -> ChatFeedPane(
                    cards = feedCards,
                    feedback = feedFeedback,
                    botName = room.name,
                    // The head keeps the room's profile; **each card** paints its own with the
                    // same resolver as the thread — the face of the bot that replied, not the
                    // room's (the defect « toutes les cartes ont le même visage » (all cards have the same face)).
                    faceName = room.name,
                    face = { name -> face(name) },
                    fallbackFaceCode = face(room.name),
                    accent = tabAccent,
                    nowMillis = nowMillis,
                    onFeedback = { cardId, value ->
                        feedFeedback = panels.setFeedback(panelKey, cardId, value)
                    },
                    onDiscuss = { card -> pendingDraft = card.title },
                    onCompose = { scope.launch { pagerState.animateScrollToPage(0) } },
                    // **The place of the bars, measured.** Without it, the content of these two
                    // tabs started **under the header** (and under the status bar): the Chat list
                    // has its `contentPadding`, these have none — they are given both
                    // margins, in the layout.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInsetDp, bottom = bottomInsetDp),
                )
                // ---- Files: what was exchanged IN this room — same filters, same sort. ----
                else -> ChatFilesPane(
                    files = roomFiles,
                    accent = tabAccent,
                    config = server,
                    onOpen = { file, local ->
                        onOpenFile(
                            android.net.Uri.fromFile(local),
                            file.name,
                            file.mime.ifBlank { FileViewers.mimeFor(file.name) },
                        )
                    },
                    // **The place of the bars, measured.** Without it, the content of these two
                    // tabs started **under the header** (and under the status bar): the Chat list
                    // has its `contentPadding`, these have none — they are given both
                    // margins, in the layout.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInsetDp, bottom = bottomInsetDp),
                )
                }
            }
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The system area first, then the house's visual space: the room's
            // input bar can no longer be buried under the navigation bar,
            // nor look stuck to the edge.
            .navigationBarsPadding()
            .padding(bottom = SafeBarSpacing.GapDp.dp),
    ) {
        Box(Modifier.onSizeChanged { topInsetPx = it.height }) {
        RoomHeader(
            room = room,
            face = face,
            onBack = onBack,
            sidebarToggle = sidebarToggle,
            onMenu = { showingMenu = true },
            onNewConversation = onNewConversation,
        )

        if (findOpen) {
            RoomFindBar(
                text = findText,
                matches = matches.size,
                position = findIndex,
                onText = { findText = it },
                onStep = { step ->
                    if (matches.isNotEmpty()) {
                        findIndex = ((findIndex + step) % matches.size + matches.size) % matches.size
                        findGoTo(matches[findIndex], page, shownFrom)?.let { shownFrom = it }
                    }
                },
                onClose = { findOpen = false; findText = ""; findIndex = 0 },
            )
        }

        // The thread lives in the registered layer, above; here, only its place.
        }
        Spacer(Modifier.weight(1f))

        Box(Modifier.onSizeChanged { bottomInsetPx = it.height }) {
        if (room.inDesktop) {
            DesktopNote()
        } else {
            // **The same bottom stack as a one-bot conversation**: the house's composer,
            // then the tab bar, in **a single** plate and with **a single** system
            // margin (`composerBottomInsets`) — the two never add up.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .composerBottomInsets()
                    .padding(
                        start = PlateGeometry.SideMargin,
                        end = PlateGeometry.SideMargin,
                        bottom = OnyxSpace.Half,
                    )
                    .socleShadow(OnyxBase.ShadowFar, PlateGeometry.Radius, theme.dark)
                    .clip(RoundedCornerShape(PlateGeometry.Radius))
                    .border(
                        PlateGeometry.Outline,
                        OnyxSurfaces.hairline(theme),
                        RoundedCornerShape(PlateGeometry.Radius),
                    )
                    // **The house's lens**, the same as in a bot thread — now clipped to the
                    // shape (see `LiquidGlass`), hence no more rectangle in the middle of the plate.
                    .liquidGlass(GlassSurface.TAB_BAR, RoundedCornerShape(PlateGeometry.Radius), masking = true),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    GroupComposer(
                        bots = room.composerBots(face),
                        isStreaming = state.busy,
                        onSend = { text, files -> viewModel.send(text, files) },
                        onStop = { viewModel.stop() },
                        onTalk = onOpenVoice,
                        serverError = state.problem,
                        speechLocale = speechLocale,
                        focusRequester = composerFocusRequester,
                        plate = true,
                        // The plate takes the system margin **only once** (see above): the
                        // field does not take it again, the two no longer add up.
                        applyBottomInsets = false,
                        prefill = pendingDraft,
                        onPrefillConsumed = { pendingDraft = null },
                    )
                }
                // The plate's inner rule: the boundary between the field and the tab row
                // (`.ask{border-bottom}` of the mockup), painted and not measured.
                val separatorColour = OnyxSurfaces.hairline(theme)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PlateGeometry.TabRowHeight)
                        .drawBehind {
                            drawRect(
                                color = separatorColour,
                                topLeft = Offset.Zero,
                                size = Size(size.width, PlateGeometry.Separator.toPx()),
                            )
                        },
                    verticalArrangement = Arrangement.Center,
                ) {
                    // **The bar is the house's**, with its four markers: Chat, Feed,
                    // Files, then the face marker — here the faces of the room's members.
                    val faceIndex = ChatTab.drawn.lastIndex
                    ChatTabBar(
                        selectedIndex = pagerState.currentPage,
                        tabs = ChatTab.drawn,
                        accent = tabAccent,
                        badges = List(chatTabCount) { 0 },
                        plate = true,
                        faceIndex = faceIndex,
                        faceLabel = room.name,
                        // **The members' faces cycle** on the marker, one bot after another
                        // (`faceCycle`), and the current index — a single loop — also gives the
                        // tint. `face` remains the fallback of a room without members: nothing to
                        // cycle, so the marker paints only one face.
                        faceCycle = faceCycleFaces,
                        faceCycleIndex = faceCycleIndex,
                        face = {
                            RoomFaceStack(
                                members = room.members,
                                face = face,
                                size = PlateGeometry.TabCell,
                            )
                        },
                        onSelect = { index ->
                            when {
                                // The room's face marker opens its menu (its identity).
                                index == faceIndex -> showingMenu = true
                                index in 0 until chatTabCount -> scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }
        }
    }
    }

    if (showingMenu) {
        OnyxMenu(
            items = buildList {
                add(MenuItem(label = "Find in Chat", symbol = Symbols.MagnifyingGlass) {
                    showingMenu = false
                    findOpen = true
                })
                // **The room's lead**: a **default recipient**, never a chief — and
                // a local setting, with no server route. « No lead » (No lead) removes it, and the room
                // then behaves exactly as before.
                if (room.members.isNotEmpty()) {
                    add(
                        MenuItem(
                            label = "Lead of this room",
                            symbol = Symbols.HandRaised,
                            submenu = buildList {
                                add(
                                    MenuItem(
                                        label = "No lead",
                                        checked = lead == null,
                                    ) {
                                        showingMenu = false
                                        viewModel.setLead(null)
                                    },
                                )
                                room.members.forEach { member ->
                                    add(
                                        MenuItem(
                                            label = member.label,
                                            localize = false,
                                            checked = lead == member.profile,
                                        ) {
                                            showingMenu = false
                                            viewModel.setLead(member.profile, member.id)
                                        },
                                    )
                                }
                            },
                            onSelect = {},
                        ),
                    )
                }
                // The AI model **of each bot**, from the room: a room had no path
                // to a member's model (only a bot's conversation carried its sheet).
                // The submenu names the members — the one touched opens its model sheet.
                if (onPickBotModel != null && room.members.isNotEmpty()) {
                    add(
                        MenuItem(
                            label = "Bot Models",
                            symbol = Symbols.Sparkles,
                            submenu = room.members.map { member ->
                                MenuItem(label = member.label, localize = false) {
                                    showingMenu = false
                                    onPickBotModel(member.profile)
                                }
                            },
                            onSelect = {},
                        ),
                    )
                }
                if (!room.inDesktop) {
                    add(MenuItem(label = "Rename", symbol = Symbols.Pencil) {
                        showingMenu = false
                        renameText = room.name
                        renaming = true
                    })
                    addAll(muteMenuItems(mutes, "room:" + room.id))
                    add(MenuItem(label = "End Group Chat", symbol = Symbols.Trash, destructive = true) {
                        showingMenu = false
                        confirmingEnd = true
                    })
                }
            },
            onDismiss = { showingMenu = false },
        )
    }

    if (renaming) {
        OnyxPrompt(
            title = "Rename group chat",
            placeholder = "Name",
            value = renameText,
            onValueChange = { renameText = it },
            confirmLabel = "Save",
            onConfirm = {
                val name = renameText.trim()
                renaming = false
                if (name.isNotEmpty()) viewModel.rename(name)
            },
            onDismiss = { renaming = false },
            confirmEnabled = renameText.isNotBlank(),
        )
    }

    if (confirmingEnd) {
        OnyxAlert(
            title = OnyxLocalization.format("End “%1\$s”?", room.name),
            message = "The bots stop and the group chat goes, on the server too. The bots' own chats stay.",
            localizeMessage = false,
            actions = listOf(
                AlertAction(label = "End Group Chat", destructive = true) {
                    confirmingEnd = false
                    viewModel.disband(onEnded)
                },
                AlertAction(label = "Cancel") { confirmingEnd = false },
            ),
            onDismiss = { confirmingEnd = false },
        )
    }
}

/** How many messages a page holds (`GroupChatView.pageSize`). */
private const val PAGE = 30

/** A page of the log: the rows shown, how many are before, and where the page starts. */
internal data class RoomPage(val shown: List<GroupEvent>, val hidden: Int, val start: Int)

/** The log rows from `from`, or the most recent ones as long as it is not set. */
internal fun roomPage(rows: List<GroupEvent>, from: Int?): RoomPage {
    val start = from?.let { seq -> rows.indexOfFirst { it.seq >= seq }.takeIf { it >= 0 } ?: rows.size }
        ?: (rows.size - PAGE).coerceAtLeast(0)
    return RoomPage(shown = if (start < rows.size) rows.subList(start, rows.size) else emptyList(), hidden = start, start = start)
}

/** The messages where the searched words are found, by their number, from oldest to newest. */
internal fun roomMatches(rows: List<GroupEvent>, open: Boolean, query: String): List<Int> {
    val words = query.trim()
    if (!open || words.isEmpty()) return emptyList()
    return rows.filter { it.isMessage && AttachedFileNote.strip(it.text).contains(words, ignoreCase = true) }.map { it.seq }
}

/**
 * How many rows the room list draws: what the screen stacks, in order — the log rows
 * shown, the messages on their way, the replies being written, the room line, the
 * cards to answer, and what the server refused.
 */
internal fun roomItemCount(
    state: GroupRoomFeedState,
    page: RoomPage,
    rows: List<GroupEvent>,
): Int {
    var count = page.shown.size + state.sending.size + state.shownDrafts.size
    if (state.room.omitted > 0) count += 1
    if (page.hidden > 0) count += 1
    if (rows.isEmpty() && state.sending.isEmpty()) count += 1
    if (state.status.waitsOnPerson || (state.busy && state.writing == null)) count += 1
    count += state.status.approvals.count { !state.answering.contains(it.id) }
    count += state.status.retries.count { !state.answering.contains(it) }
    if (state.problem != null) count += 1
    return count
}

/** Goes to a found message: when it is before the shown page, the page opens on it. */
internal fun findGoTo(seq: Int, page: RoomPage, shownFrom: Int?): Int? =
    if (page.shown.any { it.seq == seq }) null else seq

/**
 * True when a room has something to open **another** conversation with: its log carries
 * something. A still blank room returns false and is reused as is — the pencil does not stack
 * two empty conversations.
 */
internal fun roomStartsAnotherConversation(rows: List<GroupEvent>): Boolean = rows.isNotEmpty()

/** The header: Conversations, the members' faces, the room name, the pencil, and the « … » menu. */
@Composable
private fun RoomHeader(
    room: GroupRoom,
    face: (String) -> String?,
    onBack: () -> Unit,
    // The **same** control as in a bot thread: the drawer pill, with its state and its
    // label (« Déplier » / « Replier la liste » (Expand / Collapse the list)). The room had only a back button, whereas it is
    // open **inside** the drawer: the expected gesture is the drawer's.
    sidebarToggle: (@Composable () -> Unit)? = null,
    onMenu: () -> Unit,
    onNewConversation: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // **The same bar as in a bot thread**: a glass capsule detached from the page,
            // and not a full-width banner. Without it, the room read as another screen
            // than the thread — whereas it is the same place, with several voices.
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .glassBarShell(SurfaceRole.Bar)
            .liquidGlass(GlassSurface.THREAD_TOP_BAR, SurfaceRole.Bar.shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // **The drawer control**, the bot thread's — it collapses and expands the list. Without it (a
        // room opened outside the drawer), the conversations pill keeps its back role.
        if (sidebarToggle != null) {
            sidebarToggle()
        } else {
            GlassCircleButton(
                symbol = Symbols.SidebarLeft,
                label = "Conversations",
                size = 36.dp,
                iconSize = 18.dp,
                tint = theme.secondaryText,
                onClick = onBack,
            )
        }
        RoomFaceStack(members = room.members, face = face, size = 22.dp)
        BasicText(
            text = room.name,
            localize = false,
            style = theme.headline.copy(color = theme.textColor),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        GlassCircleButton(
            symbol = Symbols.Ellipsis,
            label = "More",
            size = 36.dp,
            iconSize = 18.dp,
            tint = theme.secondaryText,
            onClick = onMenu,
        )
        if (onNewConversation != null) {
            // The pencil, to the right of the menu: the same thing as in a bot thread — a
            // conversation of **this** room, with its members and its name.
            GlassCircleButton(
                symbol = Symbols.SquareAndPencil,
                label = "New conversation",
                size = 36.dp,
                iconSize = 18.dp,
                tint = theme.accent,
                onClick = onNewConversation,
            )
        }
    }
}

/** The faces of a few overlapping members (`FaceStack`), each on the page background. */
@Composable
private fun RoomFaceStack(members: List<GroupMember>, face: (String) -> String?, size: Dp) {
    val theme = LocalOnyxTheme.current
    if (members.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(-(size * 0.35f))) {
        members.take(3).forEach { member ->
            Box(
                modifier = Modifier
                    .background(theme.pageColor, CircleShape)
                    .padding(1.dp),
            ) {
                BotLook(name = member.profile, faceCode = face(member.profile), size = size)
            }
        }
    }
}

/** The empty state: the members' faces, their joined names, and the reference's help sentence. */
@Composable
private fun RoomOpening(room: GroupRoom, face: (String) -> String?, inline: Boolean = false) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (inline) 16.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoomFaceStack(members = room.members, face = face, size = 44.dp)
        if (room.members.isNotEmpty()) {
            BasicText(
                text = roomMembersLabel(room.members.map { it.label }),
                localize = false,
                style = theme.headline.copy(color = theme.textColor, textAlign = TextAlign.Center),
            )
        }
        BasicText(
            text = if (room.inDesktop) {
                "Nothing said yet in Hermes Desktop."
            } else {
                "Say something to all of them, or start with @ and a name to ask one. " +
                    "A bot that has nothing to add stays quiet."
            },
            style = theme.subheadline.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
        )
    }
}

/** The members' names joined like `formatted(.list(type: .and))`. */
internal fun roomMembersLabel(labels: List<String>): String {
    return when (labels.size) {
        0 -> ""
        1 -> labels[0]
        2 -> "${labels[0]} ${OnyxLocalization["and"]} ${labels[1]}"
        // Three and more: the tail states the language — « , and » in English (the Oxford comma),
        // « et » (and) in French, where the comma does not precede it.
        else -> labels.dropLast(1).joinToString(", ") + OnyxLocalization[", and"] + " " + labels.last()
    }
}

/** A log event: what the person said, a bot's reply, or a turn note. */
@Composable
private fun RoomEventRow(
    event: GroupEvent,
    room: GroupRoom,
    server: ServerConfig,
    face: (String) -> String?,
    highlighted: Boolean,
) {
    val theme = LocalOnyxTheme.current
    when {
        event.kind == "message.user" -> {
            val said = AttachedFileNote.split(event.text)
            MineRow(
                text = said.text,
                paths = said.paths,
                files = emptyList(),
                server = server,
                waiting = false,
                whenMillis = event.createdAtMillis,
                highlighted = highlighted,
            )
        }

        event.kind == "message.member" -> RoomMemberRow(
            name = room.member(event.actorID)?.label ?: OnyxLocalization["A bot"],
            profile = room.member(event.actorID)?.profile.orEmpty(),
            faceCode = face(room.member(event.actorID)?.profile.orEmpty()),
            text = event.text,
            live = false,
            highlighted = highlighted,
        )

        // **The handoff**: the line the room adds when a member names the next one.
        // It is drawn **by the same list** as the other events, in its place — after the
        // reply that carries it —, and it reorders nothing.
        event.kind == ROOM_HANDOFF_KIND -> RoomHandoffRow(
            from = event.payload.strOrNull("from").orEmpty(),
            to = event.payload.strOrNull("to").orEmpty(),
            sentence = event.payload.strOrNull("sentence").orEmpty(),
            room = room,
        )

        else -> event.note(room)?.let { note ->
            SelectableText(
                text = note,
                localize = false,
                style = theme.footnote.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * **The handoff, in the log.** A line, not a card: who takes over, and the
 * sentence that said it.
 *
 * The lead is not a chief — the line states neither order nor permission, only who receives
 * the turn. The names are the room's (`label`), the same as the reply above: the line does not
 * rename anyone. A member the room no longer knows keeps its profile, as is.
 */
@Composable
private fun RoomHandoffRow(from: String, to: String, sentence: String, room: GroupRoom) {
    val theme = LocalOnyxTheme.current
    val giver = room.members.firstOrNull { it.profile == from }?.label ?: from
    val taker = room.members.firstOrNull { it.profile == to }?.label ?: to
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OnyxIcon(symbol = Symbols.ArrowTurnDownRight, tint = theme.secondaryText, size = 13.dp)
            BasicText(
                text = OnyxLocalization.format("%1\$s hands the room to %2\$s", giver, taker),
                localize = false,
                style = theme.footnote.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
            )
        }
        if (sentence.isNotEmpty()) {
            BasicText(
                text = sentence,
                localize = false,
                style = theme.caption.copy(color = theme.tertiaryText),
            )
        }
    }
}

/**
 * A bot's reply: its face, its name, then what it said — **flat**, under the face,
 * like the reference's `MessageRow` when the theme prints the replies on the page
 * (`palette.flatReplies`): the text is at the thread's edge, not indented under the name, and the name
 * line is 8 pt from what it announces.
 */
@Composable
internal fun RoomMemberRow(
    name: String,
    profile: String,
    faceCode: String?,
    text: String,
    live: Boolean,
    whenMillis: Long? = null,
    highlighted: Boolean = false,
    /** This bot's voice is coming out at this instant: its line stands out from the others. */
    speaking: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotLook(
                // The bot's **profile name**. Empty (an author the room does not know), the
                // face is `botFace`'s **neutral** — never the creature of the "bot" string,
                // which read as an expressive bot.
                name = profile,
                faceCode = faceCode,
                size = 20.dp,
                mood = if (live) BotMood.THINKING else BotMood.PLEASED,
            )
            if (live) LiveMark(color = theme.accent)
            // What is being read now: a speaker in the accent color, and the name
            // in that color — the reply whose voice is coming out is visible in the thread.
            if (speaking && !live) OnyxIcon(symbol = Symbols.SpeakerWave, tint = theme.accent, size = 13.dp)
            BasicText(
                text = name,
                localize = false,
                style = theme.subheadline.copy(
                    color = if (speaking) theme.accent else theme.textColor,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        MarkdownText(
            markdown = text,
            isStreaming = live,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** A reply being written (`GroupDraft`), drawn like the chat's live reply. */
@Composable
private fun RoomDraftRow(draft: GroupDraft, room: GroupRoom, face: (String) -> String?) {
    RoomMemberRow(
        name = room.member(draft.memberID)?.label ?: OnyxLocalization["A bot"],
        profile = room.member(draft.memberID)?.profile.orEmpty(),
        faceCode = face(room.member(draft.memberID)?.profile.orEmpty()),
        text = draft.text,
        live = draft.finishedAtMillis == null,
        whenMillis = draft.beganMillis,
    )
}

/**
 * What the person said, as the chat draws one of its messages, with the files it
 * names by their paths on the server, or their names while it is on its way (`waiting`).
 */
@Composable
internal fun MineRow(
    text: String,
    paths: List<String>,
    files: List<String>,
    server: ServerConfig,
    waiting: Boolean,
    whenMillis: Long = 0L,
    highlighted: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (waiting) 0.6f else 1f),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (text.isNotEmpty()) {
            com.onyx.app.ui.chat.components.MessageRow(
                message = ChatMessage(
                    id = "room-mine-" + whenMillis + "-" + text.take(24),
                    role = Role.USER,
                    text = text,
                    createdAt = whenMillis,
                ),
                replyName = "",
                isLive = false,
                showActions = false,
                highlighted = highlighted,
            )
        }
        if (paths.isNotEmpty() || files.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                paths.forEach { path -> GroupFileChip(path = path, server = server) }
                files.forEach { name -> PendingGroupFileChip(name = name) }
            }
        }
    }
}

/** What the room is waiting on: the bots are talking, it awaits the person, it stops, or resumes. */
@Composable
private fun TalkingLine(status: GroupState) {
    val theme = LocalOnyxTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LiveMark(color = theme.accent)
        BasicText(
            text = when {
                status.waitsOnPerson -> "Waiting on you"
                status.stopping -> "Stopping…"
                status.blocked -> "Sorting out a reply that was cut off…"
                else -> "The bots are talking…"
            },
            style = theme.footnote.copy(color = theme.secondaryText),
        )
    }
}

/** A reply stopped midway: Hermes does not know how far it got, it is run again if wanted. */
@Composable
private fun RetryCard(task: String, who: String, onRetry: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.warning.copy(alpha = 0.12f), RoundedCornerShape(OnyxSpace.FieldCorner))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OnyxIcon(Symbols.ArrowClockwise, tint = theme.textColor, size = 15.dp)
            BasicText(
                text = OnyxLocalization.format("%1\$s's reply didn't finish", who),
                localize = false,
                style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            )
        }
        BasicText(
            text = "It stopped partway, and Hermes can't tell how far it got. Run it again?",
            style = theme.footnote.copy(color = theme.secondaryText),
        )
        Row(
            modifier = Modifier
                .background(theme.surfaceColor, RoundedCornerShape(50))
                .onyxClickable { onRetry() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            BasicText(text = "Retry", style = theme.footnote.copy(color = theme.accent))
        }
    }
}

/**
 * In place of the composer, for a room held by Hermes Desktop: Desktop is what answers its
 * messages, and it only shares a copy of the latest, so it is read here and spoken there.
 */
@Composable
private fun DesktopNote() {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnyxIcon(Symbols.DesktopComputer, tint = theme.secondaryText, size = 15.dp)
        SelectableText(
            text = "This group chat runs in Hermes Desktop. Its last messages show here; to talk in it, use Desktop.",
            style = theme.footnote.copy(color = theme.secondaryText),
        )
    }
}

/** The search in the room (`Find in Chat`): the words, the count, and the arrows. */
@Composable
private fun RoomFindBar(
    text: String,
    matches: Int,
    position: Int,
    onText: (String) -> Unit,
    onStep: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surfaceColor)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OnyxIcon(Symbols.MagnifyingGlass, tint = theme.secondaryText, size = 15.dp)
        Box(modifier = Modifier.weight(1f)) {
            if (text.isEmpty()) {
                BasicText(text = "Find in chat", style = theme.body.copy(color = theme.tertiaryText))
            }
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = theme.body.copy(color = theme.textColor),
                singleLine = true,
                cursorBrush = SolidColor(theme.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (text.isNotEmpty()) {
            BasicText(
                text = if (matches == 0) "0" else "${position + 1} of $matches",
                style = theme.caption.copy(color = theme.secondaryText),
            )
            OnyxIcon(
                symbol = Symbols.ChevronUp,
                tint = theme.accent,
                size = 13.dp,
                modifier = Modifier.onyxClickable { onStep(-1) },
            )
            OnyxIcon(
                symbol = Symbols.ChevronDown,
                tint = theme.accent,
                size = 13.dp,
                modifier = Modifier.onyxClickable { onStep(1) },
            )
        }
        BasicText(
            text = "Done",
            style = theme.body.copy(color = theme.accent),
            modifier = Modifier.onyxClickable(onClick = onClose),
        )
    }
}
