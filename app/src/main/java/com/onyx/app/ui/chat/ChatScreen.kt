package com.onyx.app.ui.chat
import com.onyx.app.ui.design.frostedBar

import com.onyx.app.localization.OnyxLanguage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.onyx.app.ui.design.AttachmentAction
import com.onyx.app.ui.design.AttachmentSheet
import com.onyx.app.ui.design.MenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.onyx.app.data.model.PrefillProgress
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.ui.design.GlassScrollFreeze
import com.onyx.app.data.model.serve.BotMentions
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.components.FollowUpChips
import com.onyx.app.ui.chat.components.BrowserInvite
import com.onyx.app.ui.chat.components.EmptyHomeTopInset
import com.onyx.app.ui.chat.components.MentionSuggestions
import com.onyx.app.ui.chat.components.MessageRow
import com.onyx.app.ui.chat.components.BranchConfirmAlert
import com.onyx.app.ui.common.ToastHost
import com.onyx.app.ui.common.messageArrival
import com.onyx.app.ui.chat.components.OnyxComposer
import com.onyx.app.ui.chat.components.CompactComposerDock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.onyx.app.voice.SpeechEngine
import com.onyx.app.ui.bots.dictationJoined
import com.onyx.app.ui.chat.components.OnyxTopBar
import com.onyx.app.ui.chat.components.threadStatus
import com.onyx.app.ui.chat.components.threadHeaderSpeaker
import com.onyx.app.ui.chat.components.chatHeaderTitle
import com.onyx.app.ui.chat.components.SlashMenu
import com.onyx.app.ui.chat.components.StartCard
import com.onyx.app.ui.chat.components.StartScreen
import com.onyx.app.ui.chat.components.ChatActionSheet
import com.onyx.app.ui.chat.components.clipboardImages
import com.onyx.app.ui.design.GlassPill
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxConfirm
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.matierePage
import com.onyx.app.ui.design.ambientLoop
import com.onyx.app.ui.design.Lucide
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.swipeRight
import com.onyx.app.ui.theme.LocalOnyxTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.onyx.app.data.local.ChatPanelStore
import com.onyx.app.ui.design.ChatTab
import com.onyx.app.ui.design.OnyxBase
import com.onyx.app.ui.design.ComposerStacking
import com.onyx.app.ui.design.LocalHazeState
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.threadBottomReserve
import com.onyx.app.ui.design.threadFloatAnchor
import com.onyx.app.ui.design.TabSwipeEdgeGuards
import com.onyx.app.ui.design.botFaceColour
import com.onyx.app.ui.design.PlateGeometry
import com.onyx.app.ui.design.onyxLensPage
import com.onyx.app.ui.design.chatTabCount
import com.onyx.app.ui.design.chatTabIndexOf
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.onyxHazeSource
import kotlinx.coroutines.launch

/**
 * The conversation screen.
 *
 * The title bar, the thread and the composer are all hand-made — `OnyxTopBar`,
 * `MessageRow`, `OnyxComposer` — so that nothing from Material is recognisable on screen,
 * as the parity with the reference app requires.
 */
@Composable
fun ChatScreen(
    /**
     * The chat state, **held by reference**.
     *
     * The screen used to read it by value: each text publication (33 per second during a
     * response) replaced the state, so the screen body - 2,200 lines - was replayed on
     * every token. It now receives it **by reference** and only projects the stable part of it
     * ([ChatScreenState]): the live text is read only by the list and the line being
     * written.
     */
    stateFlow: androidx.compose.runtime.State<ChatUiState>,
    startCards: List<StartCard>,
    onInputChange: (String) -> Unit,
    /**
     * A line of the "/" menu was tapped: the text enters the draft **and the menu
     * closes** ([ChatViewModel.pickSlash]). By default, the same call as a keystroke — a screen
     * with no menu therefore changes nothing.
     */
    onPickSlash: (String) -> Unit = onInputChange,
    onSend: () -> Unit,
    /** A steering note written during a response: it joins the current turn. */
    onSteer: (String) -> Unit = { _ -> },
    /**
     * **Scroll back up the thread, beyond what is loaded.**
     *
     * The thread opens on its last page (60 messages, 145 KB measured) instead of the whole thread
     * (500 messages, 1.17 MB). "Earlier messages" therefore asks the server for the previous page —
     * `null` for a screen with no server, where the row does not appear.
     */
    onLoadEarlier: (() -> Unit)? = null,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversations: (() -> Unit)?,
    onPickModel: () -> Unit,
    /** A bare `/model`: same opening of the selector as through the header, but the request must also
     *  be consumed (the screen may be recreated). By default, the same call as the header. */
    onModelCommand: () -> Unit = onPickModel,
    /**
     * Opens the **model selector sheet** (feature 2) from the **card chip**.
     * `null`: the chip is not drawn — the screen has no model to choose.
     */
    onOpenModelSheet: (() -> Unit)? = null,
    /** The current reasoning level: the second segment of the chip ("Model | Reasoning"). */
    reasoningLabel: String = "",
    onOpenSettings: () -> Unit,
    /**
     * **The face on the bottom bar is a door, not a tab.**
     *
     * The fourth marker of a conversation is *drawn* as the **bot's face**; tapping it therefore
     * opens the **identity of that bot** — its face, its name, its voice, what it does, its
     * personality, its model, its tools, its skills — that is, exactly the page that
     * "Edit" opens from a bot tile's menu ([com.onyx.app.ui.bots.BotEditorScreen]).
     * This was the reported defect: the marker's appearance had changed, not what tapping triggers.
     *
     * `null`: the screen has no identity to open; the marker then falls back on its **safe**
     * behaviour — no out-of-page scrolling —, instead of looking like a dead control.
     */
    onOpenBotIdentity: (() -> Unit)? = null,
    onOpenVoice: () -> Unit,
    onReadAloud: ((com.onyx.app.data.model.ChatMessage) -> Unit)? = null,
    readingMessageId: String? = null,
    onExpandDraft: () -> Unit,
    onOpenCard: (StartCard) -> Unit,
    onAddAttachments: (List<android.net.Uri>) -> Unit,
    onApproval: (messageId: String, approvalId: String, choice: String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenDelivered: (com.onyx.app.data.model.serve.DeliveredFile) -> Unit,
    /**
     * A file of the **Files** tab already on the phone, opened by the reader of its
     * family. This is what a tap on a row does: without this wiring, the tab would have
     * rows with no effect — the reported defect.
     */
    onOpenLocalFile: (android.net.Uri, String, String) -> Unit,
    menuItems: List<MenuItem> = emptyList(),
    // "Find in conversation": a field, the result count and the arrows that step from
    // one to the other (`TranscriptView.searchBar`) — no result list to open, the reference
    // does not have one. The two parameters that announced one (`searchMatches`, `onOpenSearchResult`)
    // were neither read nor supplied: removed rather than drawing a control with no model.
    onSearch: (String) -> Unit = {},
    onFindRequested: () -> Unit = {},
    onStepSearch: (Int) -> Unit = {},
    onOpenSettingsFromError: () -> Unit = {},
    onOpenPatch: (String) -> Unit = {},
    onSelectText: (String) -> Unit = {},
    /**
     * Restore the conversation **to** a response: everything after it is undone. The confirmation
     * is requested before anything is removed.
     */
    onRestore: (com.onyx.app.data.model.ChatMessage) -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    /**
     * Branch **from here**: copy this thread up to this response into a new
     * conversation. Like restore, nothing is copied before the confirmation.
     */
    onBranch: (com.onyx.app.data.model.ChatMessage) -> Unit = {},
    onConfirmBranch: () -> Unit = {},
    onCancelBranch: () -> Unit = {},
    /** The restore notice has been shown: it does not replay. */
    onConsumeRestoreNotice: () -> Unit = {},
    onOpenPhotos: (List<com.onyx.app.data.model.Attachment>, Int) -> Unit = { _, _ -> },
    /** An already-sent attached file, opened on tap (the PDF in the app's reader). */
    onOpenFile: (com.onyx.app.data.model.Attachment) -> Unit = {},
    /** An attachment still in the field, opened on tap. */
    onOpenPending: (com.onyx.app.ui.chat.PendingAttachment) -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onOpenSubagent: (com.onyx.app.data.model.SubagentActivity) -> Unit = {},
    /**
     * LOT thread-organisation-skills: stops **one** still-live sub-agent
     * (`subagent.interrupt`). The same point as the tasks screen; without it, no card
     * draws a stop button.
     */
    onStopSubagent: (com.onyx.app.data.model.SubagentActivity) -> Unit = {},
    /** Opens the launched-tasks screen (sub-agents) from the conversation. */
    onOpenSubagents: () -> Unit = {},
    /**
     * The sub-agent roster to show, or `null` for the conversation's one. The QA
     * double (`onyx.subagentDemo`) fills it; without it, the thread is the source of truth.
     */
    subagentAgents: List<com.onyx.app.data.model.SubagentActivity>? = null,
    /**
     * **"Teach a task"** — the row of the "+" sheet, like "Teach a task" in
     * the reference.
     *
     * It does not do the demonstration here: the capture needs the **connected computer** (the
     * gestures, the screen, the server-side lease). The row therefore opens that screen and launches the
     * recording sheet there — the shortest path for the person, who no longer has to guess that
     * "Show me once" must be looked up in a menu elsewhere.
     *
     * `null`: no row (server without Dashboard credentials, or preview). Never a dead button.
     */
    onTeachTask: (() -> Unit)? = null,
    /**
     * Opens the agent's browser panel from the thread.
     *
     * This is what makes the **takeover card** clickable: the agent asks, the card drops
     * into the conversation, the person taps and the panel opens on the page already loaded.
     * `null`: no card, rather than a card with no way out.
     */
    onOpenBrowser: (() -> Unit)? = null,
    /**
     * QA double of the "/" menu (`am start --ez onyx.slashDemo true`): the emulator has no
     * gateway, so `complete.slash` returns nothing and the menu can neither be seen nor measured. The
     * flag injects a fixed list that **looks like the real one** (short app names, server
     * skill names and their long details), to check row heights,
     * the ceiling and the placement above the plate. `null`: the real menu, always.
     */
    slashDemoItems: List<SlashItem>? = null,
    onClarify: (messageId: String, answers: Map<String, String>) -> Unit = { _, _ -> },
    onSudo: (messageId: String, password: String) -> Unit = { _, _ -> },
    onSecret: (messageId: String, value: String) -> Unit = { _, _ -> },
    onSendQueued: (String) -> Unit = {},
    onEditQueued: (String) -> Unit = {},
    onDeleteQueued: (String) -> Unit = {},
    /** The "/" command of [ChatUiState.pendingSlash] is executed: it does not replay. */
    onConsumeSlash: () -> Unit = {},
    returnSends: Boolean = true,
    /**
     * Fallback start: the previous launch died while it was restoring its state.
     * The remembered tab is the one from the crash — we go back to **Chat** instead of reopening it,
     * otherwise the same image rejected by the renderer kills the app at the same place, in a loop.
     */
    startOnChatTab: Boolean = false,
    /** Microphone locale for dictation in the field (`Settings → Voice → Listening language`). */
    speechLocale: String = "",
    /**
     * What the header and the input area call the assistant: the name from Settings →
     * Appearance, or the server name when it is empty (`Settings.headerTitle` of the reference).
     */
    assistantName: String = "",
    /**
     * The conversation text scale (`Settings.chatTextSize`), applied to the thread only:
     * the composer and the surrounding bars keep the system size, like the reference's
     * `chatTextSize()` set on the thread's `ScrollView`.
     */
    chatTextScale: Float = 1f,
    /** Settings → Appearance → "Link previews": the card of the first link under a message. */
    linkPreviews: Boolean = false,
    /**
     * The bot whose conversation this is ([botOfConversation]: the bot's server chat
     * — `BotProfile.chatId`, the `canonical_session` — otherwise the title from which `botChatToOpen` named its
     * chat); `null` for a plain conversation, where the header then shows Onyx — its agent name
     * and its bubble face.
     */
    threadBot: com.onyx.app.data.model.serve.BotProfile? = null,
    /**
     * **The bot of this conversation is not known yet.**
     *
     * True only between the first frame and the roster response, when neither the roster nor the
     * conversation memory says who it is. The header then displays **neither a face
     * nor an "@profile"**: a bare header for an instant is better than a wrong header — it is what
     * showed Onyx's blue and then jumped to the real bot.
     */
    threadBotUnknown: Boolean = false,
    /**
     * The bots an `@` word in the draft can name (`BotMentions.rosterForMentions`): the list
     * above the field, like `ComposerView.mentionMenu` — a bot's thread as well as the main
     * agent's, and never a group room, which has its own (`GroupComposer`). Empty,
     * no list opens.
     */
    mentionBots: List<com.onyx.app.data.model.serve.BotProfile> = emptyList(),
    /**
     * The drawer control (`sidebar.left`) when the thread is the **detail** of the two panes and
     * the drawer is collapsed: it takes the place of the bar's "Conversations" button, and its
     * accessibility label announces "Expand the list".
     */
    sidebarToggle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // **The screen body no longer reads the live state.** See [ChatScreenState]: it carries only
    // what does not move from one token to the next, so that text publication (33 times per
    // second) no longer recomposes anything but the thread, and not the 2,200 lines of this screen.
    val state by remember { derivedStateOf { ChatScreenState.of(stateFlow.value) } }
    // "Shown in the header and the message box": one name, everywhere.
    val agentName = assistantName.ifBlank { state.server.displayName }
    // The thread header is a deliberate addition (the reference app has none): the face and name of who
    // speaks, at the top of the messages — the conversation's bot, otherwise Onyx.
    val threadSpeaker = remember(threadBot, agentName) { threadHeaderSpeaker(threadBot, agentName) }
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var namingProject by remember { mutableStateOf(false) }
    var projectProblem by remember { mutableStateOf<String?>(null) }
    val projectDirectory = rememberChatProjectDirectory(state.server, state.serverSessionId)
    val scope = rememberCoroutineScope()
    // A long conversation renders only the last page; "N earlier messages" goes up.
    //
    // **`-1` = no page unfolded**, and this is the sentinel that was missing: the computation was
    // `maxOf(visibleFrom, messagesSize - PAGE)`, so `visibleFrom`, always smaller, never
    // changed anything — the "N earlier messages" button appeared and each tap unfolded
    // nothing. The page is keyed on the **conversation**, not on its first message: when
    // an older page is placed at the top, the first message's identifier changes and the page
    // closed under the finger.
    var visibleFrom by remember(state.conversationId) { mutableIntStateOf(-1) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // The composer height (attachments, "N messages waiting" banner, field): "Jump to
    // latest" hangs on it to stay **above** it, never on top of it.
    var composerHeightPx by remember { mutableIntStateOf(0) }
    /** The tab bar height: "Go to the last message" sits above it. */
    var tabBarHeightPx by remember { mutableIntStateOf(0) }
    /**
     * The **measured** height of the top bar. The thread uses it to pass underneath: it is what
     * gives the glass content to sample, instead of placing the pane over a flat page
     * (the case the previous batch had measured and ruled out — here, the measurement is redone).
     */
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    /**
     * The `@` word that Escape silenced: the bot list stays closed as long as the current word
     * is that one, and comes back as soon as it changes (one more letter, an "@" erased then retyped).
     */
    var mentionDismissed by remember { mutableStateOf<String?>(null) }

    // The bots offered while an `@` word is being written (`BotMentions.menu`): at most six, those that
    // what follows the "@" starts. The bot whose conversation it is does not offer itself,
    // and the list does not open in a multi-line draft.
    //
    // **Memoised on its inputs alone.** The screen recomposes on every token while a
    // response is being written; without a key, the menu was re-split at that rate even though neither the draft nor
    // the roster had moved. The keys are exactly the arguments of `menu`.
    val mentionToken = BotMentions.typing(state.input)
    val mentionSuggestions = remember(state.input, mentionBots, threadBot?.name, agentName, mentionDismissed) {
        BotMentions.menu(
            draft = state.input,
            roster = mentionBots,
            speaking = threadBot?.name,
            mainName = agentName,
            dismissed = mentionDismissed,
        )
    }
    // No word in progress any more: what Escape had silenced is forgotten, and the list can reopen.
    LaunchedEffect(mentionToken) { if (mentionToken == null) mentionDismissed = null }

    // Keyboard shortcuts: Android's physical keyboard has none of the `keyboardShortcut`s of
    // the reference, so the screen receives them itself. The root has focus, which gives ⌘↩ the same
    // meaning as on that screen: send without leaving the field.
    val rootFocus = remember { FocusRequester() }
    val composerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    val hasDraft = state.input.isNotBlank() || state.attachments.isNotEmpty()

    fun closeTopmost(): Boolean = when {
        showMoreMenu -> { showMoreMenu = false; true }
        showAddMenu -> { showAddMenu = false; true }
        showProjectMenu -> { showProjectMenu = false; true }
        state.searchOpen -> { onFindRequested(); true }
        else -> false
    }

    fun handleShortcut(shortcut: ChatShortcut): Boolean {
        if (!allowsChatShortcut(
                shortcut = shortcut,
                isStreaming = state.isStreaming,
                hasDraft = hasDraft,
                hasMessages = state.messagesSize > 0,
                searchOpen = state.searchOpen,
            )
        ) {
            return false
        }
        when (shortcut) {
            ChatShortcut.NEW_CONVERSATION -> onNewConversation()
            ChatShortcut.FIND -> onFindRequested()
            ChatShortcut.SEND -> onSend()
            ChatShortcut.STOP -> onStop()
            ChatShortcut.FOCUS_COMPOSER -> runCatching { composerFocus.requestFocus() }
            // ⌘E exports the thread as Markdown: the shortcut duplicates the "…" menu entry.
            ChatShortcut.EXPORT -> menuItems.firstOrNull { it.label == "Export as Markdown" }?.onSelect()
            ChatShortcut.CLOSE -> if (!closeTopmost()) return false
        }
        return true
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onAddAttachments(uris)
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris -> onAddAttachments(uris) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) onAddAttachments(listOf(uri))
        cameraUri = null
    }

    // The field's microphone is an independent dictation: what is said joins the draft, nothing
    // is sent and the voice screen does not open — same behaviour as the group composer
    // and the reference's `DictateButton`.
    val speechEngine = remember(speechLocale) { SpeechEngine(context, speechLocale) }
    val speechState by speechEngine.state.collectAsStateWithLifecycle()
    var dictating by remember { mutableStateOf(false) }
    var dictationOpened by remember { mutableStateOf(false) }
    var dictationBase by remember { mutableStateOf<String?>(null) }
    var dictationProblem by remember { mutableStateOf<String?>(null) }
    DisposableEffect(speechEngine) { onDispose { speechEngine.release() } }

    fun beginDictation() {
        dictationProblem = null
        dictationBase = state.input
        dictationOpened = false
        dictating = true
        speechEngine.start(true)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginDictation() else {
            dictating = false
            dictationBase = null
            dictationProblem = "Onyx needs the microphone to listen. Allow it in Android's Settings → Apps → Onyx."
        }
    }

    fun toggleDictation() {
        if (dictating) {
            speechEngine.stop()
        } else {
            val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) beginDictation() else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(speechState, dictating) {
        if (!dictating) return@LaunchedEffect
        if (!dictationOpened) {
            if (!speechState.isListening) return@LaunchedEffect
            dictationOpened = true
        }
        dictationBase?.let { start ->
            if (speechState.transcript.isNotEmpty()) onInputChange(dictationJoined(start, speechState.transcript))
        }
        speechState.problem?.let { dictationProblem = it }
        if (speechState.isFinal) {
            dictating = false
            dictationOpened = false
            dictationBase = null
        }
    }

    val pageStart = remember(state.messagesSize, visibleFrom) {
        val tail = (state.messagesSize - PAGE).coerceAtLeast(0)
        if (visibleFrom < 0) tail else visibleFrom.coerceIn(0, tail)
    }
    val hiddenCount = pageStart
    val latestItemIndex = (if (hiddenCount > 0) 1 else 0) +
        (state.messagesSize - pageStart - 1).coerceAtLeast(0) + state.queuedSize
    // **We open on the last message.** The list is born already settled on the end of the thread, instead
    // of appearing at the top then jumping to the bottom on the first pass of the follow effect. The index
    // is read only on the **first** composition (it is an initial state); afterwards, it is the follow
    // that decides where the view looks.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = latestItemIndex)
    // The live text length is read **only** by the bottom-follow observer: it
    // changes on each publication, and reading it here would recompose the screen on each token.
    val lastIndex = state.messagesSize - 1
    val currentHit = state.searchHits.getOrNull(state.searchCurrent)
    LaunchedEffect(currentHit) {
        val index = stateFlow.value.messages.indexOfFirst { it.id == currentHit }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    // The thread follows the end **as long as the person does not scroll back up**: it is they who decide. The
    // decision lives in `ScrollFollow` (pure, JVM-testable); the screen only wires
    // it. Three gestures, those of the reference:
    //  1. a finger placed on the list detaches the follow **immediately**, even if the view is still
    //     stuck at the bottom;
    //  2. at rest, we resume following if the end of the thread is on screen **within the anchor margin**
    //     (`ScrollFollow.AnchorMarginDp`: one row height);
    //  3. content that grows (the reasoning, a tool line, "Used: N tools", the
    //     duration counter, the text being written) only goes to the bottom if the follow is attached.
    //
    // **The bottom measurement, redone for this batch.** The distance is counted from the end of the last
    // line to the viewport edge **minus the reserve the list places under itself**
    // (`afterContentPadding`: composer, tab bar, system inset, house
    // gap). Without this term, the reserve entered the margin: 330 dp measured on the device instead
    // of one row, so a third of the screen scrolled up fell back to the bottom on the first line
    // received — "when I scroll gently to read everything, it forces to the last message".
    val anchorMarginPx = with(LocalDensity.current) { ScrollFollow.AnchorMarginDp.dp.toPx() }
    val distanceToBottomPx by remember(anchorMarginPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf 0
            // The last row is not in the view: we are necessarily scrolled up.
            if (last.index < info.totalItemsCount - 1) {
                Int.MAX_VALUE
            } else {
                ScrollFollow.distanceToBottom(
                    itemEndPx = last.offset + last.size,
                    viewportEndPx = info.viewportEndOffset,
                    bottomPaddingPx = info.afterContentPadding,
                )
            }
        }
    }
    // **A state, not a recomputed value**: the `snapshotFlow` below only follows state
    // reads. A value computed in the composition stayed **frozen** there at the first pass — the
    // follow therefore re-attached on each release, and the view fell back to the bottom. This is what the
    // `logcat` trace showed ("follow=attached … within margin=false"), and it is the reason
    // this line exists.
    val withinAnchor by remember(anchorMarginPx) {
        derivedStateOf { ScrollFollow.withinAnchor(distanceToBottomPx, anchorMarginPx.toInt()) }
    }
    var following by remember { mutableStateOf(true) }
    // The **measurement trace**, one line per state change: a geometry decision is read
    // in `logcat` under its tag, otherwise it can only be verified by its effects (same
    // principle as `OnyxThreadReserve`, for the bottom reserve).
    LaunchedEffect(withinAnchor, following) {
        println(
            "$ScrollFollowLogTag suivi=${if (following) "attaché" else "détaché"} " +
                "distance au bas=${distanceToBottomPx}px marge=${anchorMarginPx.toInt()}px " +
                "dans la marge=$withinAnchor " +
                // The geometry that decides the visibility of new content: the thread height and
                // the padding the list places under its last row. Padding larger
                // than the thread empties the screen at the bottom of the scroll — this trace shows it.
                "fil=${
                    listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                }px rembourrage bas=${listState.layoutInfo.afterContentPadding}px",
        )
    }
    // 1. A finger takes the list: the person decides, we detach without waiting for the position.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) following = ScrollFollow.afterTouch()
        }
    }
    // 2. At rest: we resume following if the end of the thread is within the anchor margin; a gesture in
    //    progress never re-attaches (see `ScrollFollow.atRest`).
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to withinAnchor }
            .collect { (scrolling, anchored) ->
                following = ScrollFollow.atRest(scrolling, anchored, following)
            }
    }
    // 3. Content that grows — AND **every turn state transition** (waiting, start of
    //    reasoning, start of response, first token) — only goes to the bottom if we follow. The turn step
    //    (`state.live.step`) is a key in its own right: a step change that does not yet
    //    change the text (the move from waiting to reasoning, the first token of an empty reasoning)
    //    must also bring the view back, otherwise the new state is painted out of the view.
    //
    //    **A single observer for the whole turn, instead of an effect relaunched per token.**
    //
    //    The effect's keys carried `lastLength`, the live text length: it changes on
    //    **every** publication (thirty-three per second), so the effect was cancelled then relaunched
    //    thirty-three times per second, and each one issued a `scrollToItem` — a suspended coroutine
    //    that asks again for a complete measurement of the list.
    //
    //    `snapshotFlow` observes the same thing without relaunching anyone, and `requestScrollToItem` places
    //    the request **for the next measurement pass**: two tokens arriving in the same frame
    //    cost only one move, and the composition no longer waits for a `suspend`.
    LaunchedEffect(listState, latestItemIndex, stateFlow) {
        snapshotFlow {
            val ui = stateFlow.value
            FollowShape(
                lastLength = ui.messages.lastOrNull()
                    ?.let { it.text.length + it.reasoning.length + it.tools.size } ?: 0,
                isStreaming = ui.isStreaming,
                queued = ui.queued.size,
                step = ui.live?.step,
                following = following,
            )
        }.collect { shape ->
            if (ScrollFollow.followsNewContent(shape.following) && stateFlow.value.messages.isNotEmpty())
                listState.requestScrollToItem(latestItemIndex, BottomScrollOffset)
        }
    }
    // A question comes from wherever it is written: it brings the view back to the bottom (`jumpToEnd` of the reference).
    var seenUserTurns by remember { mutableStateOf(0) }
    // The key is the **count** of messages: while a response is being written, the list changes on every
    // token but its size does not move — counting turns at that rate was pointless.
    val userTurns = state.userTurnCount
    LaunchedEffect(userTurns) {
        if (userTurns > seenUserTurns) {
            following = ScrollFollow.jumpToLatest()
            listState.scrollToItem(latestItemIndex, BottomScrollOffset)
        }
        seenUserTurns = userTurns
    }

    // "Jump to latest": offered as soon as the follow is detached and the end is no longer on screen
    // (`!following && !withinAnchor`, like the reference) — not only when another row is
    // at the top: a long response covers several screens, the end can be out of view while we
    // are still reading it.
    val offersJump = state.messagesSize > 0 && !following && !withinAnchor
    val contextUsage = state.contextUsage

    // ---------------------------------------------------------------------------------------
    // LOT live-thread: the turn state, and the reading
    // ---------------------------------------------------------------------------------------
    //
    // **The real state of the turn** (`turnFlowState`): rest, reasoning, work, waiting for a decision.
    // It is it — and nothing else — that the header and the context cards paint. No state
    // is decorative: each one comes from a step the server made known, or from an
    // approval waiting for an answer (`pendingApprovalCount`).
    val pendingApprovals = state.pendingApprovals
    // The turn step is enough to paint the state: `turnFlowState` reads only `isStreaming` and
    // `step`, never the timestamp - which changes on every token.
    // With no phase at all (the server announced none), the state is rest - that was already the
    // rule of `turnFlowState` for a null `live`, and rebuilding it here keeps it identical.
    val flow = turnFlowState(
        live = if (!state.hasLivePhase) {
            null
        } else {
            TurnPhase(
                startedAt = 0L,
                lastEventAt = 0L,
                step = state.liveStep ?: TurnStep.STARTING,
                isStreaming = state.liveStreaming,
            )
        },
        pendingApprovals = pendingApprovals,
    )
    // **"We scroll back up to read."** The bottom follow is detached while a turn runs: that is the
    // reading signal. The state then **slows down** (§ the period is multiplied, never
    // divided) instead of continuing at full rate under the eyes of the reader.
    val readingUp = state.isStreaming && !following

    val toaster = com.onyx.app.ui.common.rememberToaster()

    // The restore notice is announced once, then forgotten: it says what was undone, and
    // whether the server session could be rewound to the same point.
    LaunchedEffect(state.restoreNotice) {
        state.restoreNotice?.let { notice ->
            toaster.show(notice)
            onConsumeRestoreNotice()
        }
    }

    // An image dropped on the thread becomes an attachment (`ChatDropTarget`).
    val dropTarget = rememberDragAndDrop(onAddAttachments)

    // — The conversation tabs: Chat / Feed / Files — (Ideas and Goals removed)
    //
    // The pager lives **above** the writing bar: the bar therefore never moves when we
    // change tab, it is the one carrying the tabs underneath. The chosen tab is kept **per
    // conversation**: coming back to a conversation reopens the last tab.
    val panelKey = state.conversationId ?: state.serverSessionId ?: "main"
    val panels = remember(context) { ChatPanelStore(context) }
    var feedFeedback by remember(panelKey) { mutableStateOf(panels.feedbackFor(panelKey)) }
    // **No more "bar hidden" state.** The chevron handle that carried it has been removed from
    // the screen (owner's request: "remove the button for the option to hide").
    // The bottom plate — the field **and** the tab row — is therefore **always** visible. There
    // is no writer of this state any more, neither here nor in `ChatPanelStore` (its preference
    // `tabsHidden` disappeared with the button): nothing can leave the bar hidden after a
    // relaunch, because nothing can hide it any more. The "Ideas / Goals" state disappeared with
    // those screens (reconciliation arbitration).
    // **The two weld rectangles**: the composer's and the tab bar's,
    // measured in the bottom stack. It is from their gap that the matter bridge is born (`GlassWeld`).
    var composerWeldRect by remember { mutableStateOf<Rect?>(null) }
    var tabBarWeldRect by remember { mutableStateOf<Rect?>(null) }
    val reduceMotion = LocalReduceMotion.current

    // ---- **No fading of the bottom stack, ever.** -----------------------------------------------
    //
    // The bottom stack — the composer **and** the tab row — faded on scroll (a fade-out
    // and a short slide, triggered by a travelled distance). The owner refused it:
    // "it must NEVER disappear when I scroll". So the whole machinery was **removed**,
    // not disabled: the collapsed state (`stackVeiled`), the accumulated distance and its threshold
    // (`accumulate` / `ScrollSlopDp`), the decision (`stackVeiled`), the typing guard that
    // held it (`typingHolds`), the resulting opacity (`stackAlpha`) and the `AnimatedVisibility`
    // that painted it. There remains **no** path — neither state, nor gesture, nor keyboard, nor typing, nor
    // preference — that could change the visibility or opacity of the plate: it is drawn
    // **unconditionally**, at the same place at every scroll position. The
    // `PlatePerpetualTest` lock fails if one of these paths returns.
    val pagerState = rememberPagerState(
        initialPage = chatTabIndexOf(panels.selectedTab(panelKey)),
        pageCount = { chatTabCount },
    )
    // The tab the pager **settles** on is written right away: a short swipe that
    // comes back in place therefore changes nothing, and leaving the conversation finds its tab again.
    LaunchedEffect(pagerState, panelKey) {
        // Fallback start: the restored tab is the one where the last launch died.
        // It is set back to Chat **before** reading the saved state, otherwise it comes back at once.
        if (startOnChatTab) {
            panels.setSelectedTab(panelKey, ChatTab.CHAT.key)
        }
        val saved = chatTabIndexOf(panels.selectedTab(panelKey))
        if (pagerState.settledPage != saved) pagerState.scrollToPage(saved)
        snapshotFlow { pagerState.settledPage }.collect { page ->
            panels.setSelectedTab(panelKey, ChatTab.entries[page.coerceIn(0, chatTabCount - 1)].key)
        }
    }
    // The screen edge belongs to the system back gesture: the pager does not start a
    // tab change from the band the system reserves (`systemGestures`, zero in
    // three-button navigation). In two panes, the pane edge is not a window
    // edge: no blade is placed there.
    val singlePane = onOpenConversations != null
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val edgeGuardLeftPx = if (singlePane) {
        with(LocalDensity.current) { WindowInsets.systemGestures.getLeft(this, layoutDirection).toFloat() }
    } else {
        0f
    }
    val edgeGuardRightPx = if (singlePane) {
        with(LocalDensity.current) { WindowInsets.systemGestures.getRight(this, layoutDirection).toFloat() }
    } else {
        0f
    }
    // The active tab takes the colour of the conversation's bot.
    val tabAccent = if (threadBotUnknown) theme.accent
        else threadBot?.let { botFaceColour(it.name, it.faceCode, theme) } ?: theme.accent
    // **Nothing is recomputed on every token.** A response in progress changes on every token; the
    // Feed cards and the file list read only **finished** messages, so
    // recomputing them over the whole conversation at that rate drew nothing new and cost
    // time on every frame. The key is the same as long as the last finished message does not change.
    val settledCount = state.settledCount
    val settledLast = state.settledLastId
    val feedCards = remember(settledCount, settledLast, threadSpeaker.label) {
        ChatFeed.cards(stateFlow.value.messages.take(settledCount), threadSpeaker.label)
    }
    val exchangedFiles = remember(settledCount, settledLast) {
        ChatFiles.conversationFiles(stateFlow.value.messages.take(settledCount))
    }
    val nowMillis = remember(panelKey) { System.currentTimeMillis() }
    // What has been seen of the feed: the unread dot counts only the new ones.
    var seenCards by remember(panelKey) { mutableStateOf(panels.seenCards(panelKey)) }
    val feedBadge = feedCards.count { it.id !in seenCards }
    val tabBadges = List(chatTabCount) { index -> if (index == ChatTab.FEED.ordinal) feedBadge else 0 }
    // Opening the feed marks it as seen: the dot goes out when you look at it.
    LaunchedEffect(pagerState, panelKey, feedCards) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page == ChatTab.FEED.ordinal) {
                seenCards = panels.markSeen(panelKey, feedCards.map { it.id })
            }
        }
    }
    // Three real moves, never a mute action: the tab, the field, and the draft.
    fun openTab(index: Int) {
        val target = index.coerceIn(0, chatTabCount - 1)
        scope.launch {
            if (reduceMotion) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }
    fun openChatTab() {
        openTab(ChatTab.CHAT.ordinal)
        runCatching { composerFocus.requestFocus() }
    }
    /** "Discuss": the topic arrives in the thread's draft, which takes focus. */
    fun discussAbout(title: String) {
        onInputChange(OnyxLocalization.format("About %1\$s: ", title))
        openChatTab()
    }

    // A bare "/model": we open the model selector, as the header does. The effect replays
    // only once per request; `onModelCommand` consumes it before navigating.
    LaunchedEffect(state.modelPickerRequested) {
        if (state.modelPickerRequested) onModelCommand()
    }
    // The "/" command the model left: executed once, then consumed.
    LaunchedEffect(state.pendingSlash) {
        val slash = state.pendingSlash ?: return@LaunchedEffect
        when (slash) {
            SlashAction.OPEN_MODEL_PICKER -> onPickModel()
            SlashAction.NEW_CONVERSATION -> onNewConversation()
            SlashAction.FIND -> onFindRequested()
            SlashAction.SETTINGS -> onOpenSettings()
            SlashAction.VOICE -> onOpenVoice()
            // A skill is invoked by the send itself: nothing to do on screen.
            SlashAction.INVOKE_SKILL, SlashAction.HELP, SlashAction.GATEWAY -> Unit
        }
        onConsumeSlash()
    }

    // The two heights that make the thread pass **under** the panes: the top bar's,
    // measured on itself, and the **bottom stack**'s — composer + tab row + system
    // inset + house gap, computed by a single function
    // ([threadBottomReserve]). No hard-coded value: an unfolded foldable, a three-button system bar,
    // an open keyboard or a different system text size move the reserve, and
    // the thread follows.
    //
    // **This is the fix for the reported defect** ("part of the conversation goes under the input
    // bar"): the reserve was only `composer + tabs`, without the system inset —
    // 24 dp of gesture area, 48 dp with three buttons —, so the end of the thread ended up
    // under the card, exactly by that gap. The reserve is now **equal** to the stack, and
    // `ChatThreadReserveTest` fails if it becomes smaller again.
    val glassTopInset = with(LocalDensity.current) { topBarHeightPx.toDp() }
    val glassBottomInset = threadBottomReserve(
        composerPx = composerHeightPx,
        bottomRowPx = tabBarHeightPx,
    )

    // **The lane of the floating controls.**
    //
    // The **only** control that floats above the plate is now the "go to the last
    // message" pill: the chevron handle that shared this band has been removed from the screen,
    // on the owner's order. The band remains **reserved** — the thread never crosses it, so
    // the pill cannot land on text, which was the reported defect — but it now
    // houses only one pill: its clearance plus its height ([PlateGeometry.FloatLane], 56 dp
    // instead of the 68 that the target and the handle's gap reserved). **The 12 dp handed back to the thread
    // are measured**, and the pill still lands on nothing.
    val floatLane = PlateGeometry.FloatLane

    /**
     * The space the "/" menu has: **the whole screen minus the two panes**.
     *
     * The menu sits just above the plate (see below); its maximum height is therefore
     * the band that remains between the top bar and the composer. It is **measured** — never
     * a hard-coded value — so that a 1.3× font, an open keyboard or a short screen make
     * the list **scroll** instead of letting it pass under the plate, which was the
     * "it goes behind" defect.
     */
    val slashMenuMaxHeight = (
        LocalConfiguration.current.screenHeightDp.dp - glassTopInset - glassBottomInset -
            OnyxSpace.BlockGap - OnyxSpace.BlockGap
        )
        // **A phone's ceiling.** The screen measurement remains the safeguard (open keyboard,
        // short screen, 1.3× font); [SlashMenu.MaxHeight] is the real limit — five and a
        // half rows, enough to read the list without the thread disappearing under it.
        .coerceAtMost(com.onyx.app.ui.chat.components.MaxHeight)
        .coerceAtLeast(OnyxSpace.Base)


    // The Haze state placed at the root: it is the one the top bar, the composer and the tab
    // bar sample. `null` outside the tree that placed it (previews, tests) — the panes
    // then fall back on their veil, never on a hole.
    val glassState = LocalHazeState.current
    /** The page the lens samples (Android 13+) — `null` elsewhere. */
    val lensPage = com.onyx.app.ui.design.LocalOnyxLensPage.current

    // Back closes the **thread search** before leaving the conversation: on the home (the
    // thread), the edge gesture closed the app while an open banner was waiting to be dismissed —
    // one level at a time, like the drawer, the sheets and the menus. It does exactly what
    // the banner's cross does (`onSearch("")` then `onFindRequested()`), so the home
    // back button and the edge gesture behave the same.
    BackHandler(enabled = pagerState.currentPage != 0 && !state.searchOpen) {
        scope.launch { pagerState.scrollToPage(0) }
    }
    BackHandler(enabled = state.searchOpen) {
        onSearch("")
        onFindRequested()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // **THE PAGE MATTER IS NO LONGER PAINTED HERE: it is IN the Haze source.**
            //
            // Measured at rest on the device (`sonde2.py`, APK 991): the page above the
            // plate is `#D3DCD7` (light) / `#181818` (dark) — that is the matter, which tints and
            // darkens the page colour. The plate, for its part, was `#FCFCFD` / `#707072`, a
            // **neutral flat fill** carrying **no** trace of the page (neither the tint nor the
            // background grain). The cause: the Haze source contained only the **pager content**;
            // the matter, painted on this `Box` (hence **above** the source in the tree),
            // was not sampled, and Haze filled the void with `theme.pageColor` —
            // a white (light) or a graphite (dark) that is not what we see.
            //
            // The matter therefore goes down **into** the source (see just below), so that it is
            // what the pane samples: the plate then shows the page it covers.
            // A firm swipe to the right reveals the list, anywhere on the screen —
            // it is the gesture of the reference's `SidePanel` (`Echo/Views/SwipeToOpen`), which starts from the
            // edge as well as from the rest of the surface. The button and the gesture disappear when the
            // list is already visible on the left (two panes).
            .then(
                // The drawer gesture only takes the **Chat** tab: on the other tabs, a
                // lateral swipe is used to go back to the tabs, not to open the list.
                if (onOpenConversations == null || pagerState.currentPage != 0) {
                    Modifier
                } else {
                    Modifier.swipeRight(onChanged = {}, onEnded = { distance, _ ->
                        if (distance > 120f) onOpenConversations?.invoke()
                    })
                },
            )
            .focusRequester(rootFocus)
            .focusable()
            // Shortcuts are read BEFORE the input field: ⌘↩ sends without the keyboard
            // putting focus on the button. Same hook as the composer's Ctrl+V.
            .onPreviewKeyEvent { event ->
                // Escape first closes the bot list of an `@` word in progress: the word stays written,
                // the list stays silent until it changes (the cursor does not move).
                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    chatKeyOf(event.key) == ChatKey.ESCAPE &&
                    mentionSuggestions.isNotEmpty()
                ) {
                    mentionDismissed = mentionToken
                    return@onPreviewKeyEvent true
                }
                val shortcut = chatShortcutOf(event) ?: return@onPreviewKeyEvent false
                handleShortcut(shortcut)
            }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dropTarget,
            ),
    ) {
        // **The thread passes under the panes.** The top bar and the bottom stack are placed
        // **on top of** the five tabs, instead of being stacked next to them: this is what gives
        // the background blur something to sample. Stacked (the old column), the
        // bars sat on a flat page — a blur of a flat fill produces nothing visible and
        // still costs a layer: this is the reasoning `FrostSurface` documents, and we do not
        // contradict it — here, there really is content scrolling underneath.
        Box(modifier = Modifier.fillMaxSize()) {
            // The content of the five tabs, under the finger. The pager carries **the content alone**:
            // the writing bar and the tab bar live underneath, outside the pager, so
            // changing tab never moves the input field.
            //
            // **And it fills the whole surface**: the top bar and the bottom stack are placed
            // over it. It is also **the Haze source** — what is underneath is exactly what
            // the blur samples.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(glassState?.let { Modifier.onyxHazeSource(it) } ?: Modifier)
                    // **And the lens page** (Android 13+): the same content, registered
                    // once for all the curving surfaces (see `OnyxLensGlass`).
                    .then(lensPage?.let { page -> Modifier.onyxLensPage(page) } ?: Modifier),
            ) {
            // **THE PAGE MATTER, IN THE HAZE SOURCE.** It was on the screen's root `Box`;
            // the Haze source, placed **below** it, therefore did not capture it, and the
            // plate filled the void with an assumed page colour (measured: a flat fill
            // `#FCFCFD`/`#707072`, indifferent to the page). Placed here, it is **what the pane
            // samples**: the plate shows the page it covers, tint and grain included.
            Box(Modifier.matchParentSize().matierePage(theme))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
            when (page) {
            0 -> Column(modifier = Modifier.fillMaxSize()) {

            // What the top pane covers, here: when the thread is **alone**, it rises under the
            // bar and the blur samples real content. As soon as a banner lives above the
            // list (diagnostic, search, prefill), it is the banner that keeps its place:
            // we do not blur a banner, and a half-hidden banner is no longer clickable.
            val stacked = !state.server.isUsable || state.searchOpen || state.prefill != null ||
                state.providerStatus != null || state.steerStatus != null || state.error != null
            if (stacked || state.isEmpty) Spacer(Modifier.height(glassTopInset))

            // No server configured: the app stays usable (configuration was skipped),
            // but the thread says what is missing and where to place it.
            if (!state.server.isUsable) {
                com.onyx.app.ui.common.NoServerCard(onOpenSettings = onOpenSettingsFromError)
            }

            if (state.searchOpen) {
                SearchBar(
                    query = state.searchQuery,
                    matches = state.searchHits.size,
                    position = if (state.searchHits.isEmpty()) 0 else state.searchCurrent + 1,
                    onStep = onStepSearch,
                    onQueryChange = onSearch,
                    onClose = { onSearch(""); onFindRequested() },
                )
            }

            state.prefill?.let { prefill -> PrefillBar(prefill) }

            state.providerStatus?.let { status -> ProviderStatusBar(status) }

            state.steerStatus?.let { status -> SteerStatusBar(status) }

            if (state.error != null && state.isConnectionProblem) {
                com.onyx.app.ui.common.ConnectionProblemCard(
                    message = state.error.orEmpty(),
                    onRetry = onRetry,
                    onOpenSettings = onOpenSettingsFromError,
                )
            }

            AnimatedVisibility(visible = state.error != null && !state.isConnectionProblem) {
                ErrorBanner(
                    message = state.error.orEmpty(),
                    canRetry = state.canRetry,
                    onRetry = onRetry,
                    onDismiss = onClearError,
                )
            }

            if (state.isEmpty) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // **The empty home is no longer centred: it is placed at the top.**
                        //
                        // The reported defect: "the empty home screen has huge vertical gaps —
                        // nothing structures the page". A centred block leaves **two** equal empty
                        // bands, neither of which has a reason; placed at the top, the bottom band is
                        // the one where the conversation will come, and the emptiness reads as a reserve.
                        // [EmptyHomeTopInset] is the `Loose` step of the scale (24 dp) × 4, plus
                        // the top bar clearance: the block does not stick to the pane.
                        .padding(top = EmptyHomeTopInset),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // **No decorative surface around the empty home.**
                    //
                    // `113ccd21` ("Visible glass") placed here a "large glass card"
                    // (`LiquidGlass(GlassSurface.MATTER_CARD, matchParentSize())`) to "structure"
                    // the void; merge `0d7fb13d` kept it. The owner, on `1.0.0+846`:
                    // "what is this gross frame when I start a new
                    // conversation, I did not have it before". What he sees: a large grey panel
                    // rounded around the title, the question and the "Continue" card, **cut off
                    // sharp at the bottom** — the card takes the free space (`matchParentSize`) and the composer
                    // cuts it. It was decoration: it goes, the content stays on the page.
                    StartScreen(
                        title = threadSpeaker.label,
                        cards = startCards,
                        onOpenCard = onOpenCard,
                    )
                }
            } else {
                // The Settings text scale is set at the **root** — the activity reads it
                // (`LocalOnyxTextScale`), the theme applies it to the whole tree. `theme` therefore
                // **already** holds the chosen scale: setting it again here with `chatTextScale` would apply it
                // a second time (1.23 × 1.23 = 1.51).
                // **The thread freezes the glass during scroll, as the drawer already does.**
                //
                // Reported defect: "it lags" on a large conversation, on a powerful
                // phone. The freeze was only set by `ChatSidebar` — the thread, for its part, left
                // `LocalGlassScrollFreeze` false, so **every visible card** kept
                // pulsing (`BlocConversationFace`) and every pane kept its full-quality
                // sampling while the finger scrolled. A gesture does not watch a
                // light breathe: under the finger, we switch to performance mode.
                CompositionLocalProvider(
                    LocalOnyxTheme provides theme,
                ) {
                // **The glass freeze is set by a function of its own.** Read here, in the screen's
                // scope, `listState.isScrollInProgress` invalidated the whole conversation — twice
                // per gesture relaunch, and twice per `scrollToItem`, that is, twice
                // per token while a response is being written. See [GlassScrollFreeze].
                GlassScrollFreeze(listState) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    // The thread reserves the two panes **within its scroll**: the first and the
                    // last bubble remain readable, and everything passing between the two scrolls
                    // under the glass — that is what the glass has to show.
                    contentPadding = PaddingValues(
                        start = OnyxSpace.ScreenMargin,
                        end = OnyxSpace.ScreenMargin,
                        top = if (stacked) OnyxSpace.BlockGap else glassTopInset + OnyxSpace.BlockGap,
                        bottom = glassBottomInset + floatLane + OnyxSpace.BlockGap,
                    ),
                    // The gap between two messages: the `ThreadGap` step (16), only once for
                    // the whole thread. It used to be 14 dp — a value that exists on no scale (2 × 7)
                    // and that was found nowhere else in the app.
                    verticalArrangement = Arrangement.spacedBy(OnyxSpace.ThreadGap),
                ) {
                    // **Two reasons to scroll back, a single row.** What is already in memory
                    // (the unfolded page) is shown with one tap; what is not yet loaded is
                    // requested from the server. The row disappears when there is neither one nor the other.
                    if (hiddenCount > 0 || state.earlierAvailable) {
                        item(key = "earlier") {
                            val earlierLabel = when {
                                state.loadingEarlier -> OnyxLocalization["Loading earlier messages"]
                                hiddenCount > 0 -> OnyxLocalization.format("Show earlier messages (%1\$d)", hiddenCount)
                                else -> OnyxLocalization["Load earlier messages"]
                            }
                            EarlierRow(label = earlierLabel, enabled = !state.loadingEarlier) {
                                // First what is already there; otherwise (and only then) the previous
                                // page, from the server. `visibleFrom = 0` only makes
                                // room: the list keeps its anchor, the screen does not jump.
                                if (hiddenCount > 0) visibleFrom = maxOf(0, pageStart - PAGE) else visibleFrom = 0
                                if (hiddenCount == 0) onLoadEarlier?.invoke()
                            }
                        }
                    }

                    // **A list that changes while the list draws it.**
                    //
                    // `count` and the key were computed on `state.messages` **twice**: if the
                    // thread changes in between (we open another conversation, a turn replaces
                    // its response, a "Cancel" removes a message), `pageStart + offset` leaves
                    // the list and the `LazyColumn` throws `IndexOutOfBoundsException` — a crash
                    // that only happens with many conversations, hence never in a test.
                    items(
                        // The list reads the **live** thread: `stateFlow` is a `State`, not a
                        // value, so it is this scope (the `LazyColumn` and its rows) that
                        // recomposes on the token - not the 2,200 lines of the screen above.
                        count = (stateFlow.value.messages.size - pageStart).coerceAtLeast(0),
                        key = { offset ->
                            stateFlow.value.messages.getOrNull(pageStart + offset)?.id ?: "gone-$offset"
                        },
                        // **A content type per row shape.** Without it, a question
                        // row pushed out of the page can be "reused" for a response:
                        // the list then remeasures and recomposes a row of another shape instead of
                        // resuming the right one.
                        contentType = { offset ->
                            val message = stateFlow.value.messages.getOrNull(pageStart + offset)
                            when {
                                message == null -> "gone"
                                message.isUser -> "user"
                                else -> "reply"
                            }
                        },
                    ) { offset ->
                        val index = pageStart + offset
                        val message = stateFlow.value.messages.getOrNull(index) ?: return@items
                        val arrival = remember(message.id) {
                            val justSent = System.currentTimeMillis() - message.createdAt < 1_500
                            when {
                                !justSent -> com.onyx.app.ui.common.ArrivalKind.NONE
                                message.role == com.onyx.app.data.model.Role.USER -> com.onyx.app.ui.common.ArrivalKind.SENT
                                index == stateFlow.value.messages.lastIndex && state.isStreaming &&
                                    message.text.isEmpty() && message.reasoning.isEmpty() && message.tools.isEmpty() ->
                                    com.onyx.app.ui.common.ArrivalKind.WAITING_REPLY
                                else -> com.onyx.app.ui.common.ArrivalKind.NONE
                            }
                        }
                        val previousCreatedAt = stateFlow.value.messages.getOrNull(index - 1)?.createdAt
                        val dayLabel = remember(message.id, previousCreatedAt) {
                            dayLabelFor(message.createdAt, previousCreatedAt)
                        }
                        if (dayLabel != null) {
                            DaySeparator(label = dayLabel)
                        }
                        // **The only row that reads the turn state is the live row.**
                        //
                        // `state.live` changes on **every token** (it carries `lastEventAt`). Passed to
                        // all rows, it made their parameters different on every
                        // publication: none could be skipped, and a response being written
                        // therefore recomposed **all** the visible rows — their folds, their
                        // cards, their prose — thirty times per second. A finished row does not read
                        // this state (`WaitingPulse` only exists while the text is empty, and
                        // only for the live row): it receives `null`, equality holds, and
                        // Compose skips it.
                        val isLive = state.isStreaming && index == stateFlow.value.messages.lastIndex
                        // **The arrival animation only applies to a row that arrives.**
                        // `messageArrival` is a `Modifier.composed`: mounted on every row, it
                        // opened a subcomposition and three animations for an animation that
                        // in practice only concerns the message just sent. Archive
                        // rows receive bare `Modifier`.
                        val rowModifier = if (arrival == com.onyx.app.ui.common.ArrivalKind.NONE) {
                            Modifier
                        } else {
                            Modifier.messageArrival(arrival, leading = theme.promptPrefix != null)
                        }
                        MessageRow(
                            modifier = rowModifier,
                            message = message,
                            replyName = threadSpeaker.label,
                            isLive = isLive,
                            live = if (isLive) stateFlow.value.live else null,
                            linkPreviews = linkPreviews,
                            onOpenBrowser = onOpenBrowser,
                            onRegenerate = if (!message.isUser && !state.isStreaming) {
                                { onRetry() }
                            } else {
                                null
                            },
                            onReadAloud = onReadAloud?.let { action -> { action(message) } },
                            isReading = readingMessageId == message.id,
                            onRestore = if (!message.isUser && !state.isStreaming) {
                                { onRestore(message) }
                            } else {
                                null
                            },
                            // Branching opens the row, as in the reference. It is only
                            // offered under a finished **response**: branching under a question
                            // has no stopping point to copy.
                            onBranch = if (!message.isUser && !state.isStreaming) {
                                { onBranch(message) }
                            } else {
                                null
                            },
                            onApproval = { approvalId, choice -> onApproval(message.id, approvalId, choice) },
                            onOpenDelivered = onOpenDelivered,
                            highlighted = message.id == state.searchHits.getOrNull(state.searchCurrent),
                            onToast = { text -> toaster.show(text) },
                            onOpenPatch = onOpenPatch,
                            onSelectText = onSelectText,
                            onOpenPhotos = onOpenPhotos,
                            onOpenFile = onOpenFile,
                            onOpenSubagent = onOpenSubagent,
                            onStopSubagent = onStopSubagent,
                            onClarify = { answers -> onClarify(message.id, answers) },
                            onSudo = { password -> onSudo(message.id, password) },
                            onSecret = { value -> onSecret(message.id, value) },
                            // LOT live-thread: we scroll back up to read the thread → the cards' state dots
                            // **slow down** instead of beating at full rate under the eyes.
                            //
                            // **Only the cards of the live row read this signal.** The cards
                            // of a finished response are stopped: nothing to slow there. Passed
                            // to all, it changed on the **first finger placed** (`following` drops to
                            // `false` as soon as `DragInteraction.Start`) and therefore recomposed the whole thread at
                            // the start of each gesture — a jolt at the exact moment the finger leaves.
                            readingUp = isLive && readingUp,
                        )
                    }
                    items(
                        count = state.queuedSize,
                        key = { index -> stateFlow.value.queued.getOrNull(index)?.rowID ?: "queued-$index" },
                        contentType = { "queued" },
                    ) { index ->
                        // Same rule as the thread: the outbox can empty during composition.
                        val item = stateFlow.value.queued.getOrNull(index) ?: return@items
                        QueuedMessageRow(
                            item = item,
                            isFirst = index == 0,
                            onSend = { onSendQueued(item.id) },
                            onEdit = { onEditQueued(item.id) },
                            onDelete = { onDeleteQueued(item.id) },
                            modifier = Modifier.messageArrival(
                                com.onyx.app.ui.common.ArrivalKind.QUEUED,
                                leading = theme.promptPrefix != null,
                            ),
                        )
                    }
                        // **The browser card lives in the thread, not in the field.**
                        //
                        // Placed in the composer block, it overlapped the field: the
                        // first line of the prompt ("Guide the response…") passed underneath and was
                        // half-readable. An invitation to take over is **content of the
                        // conversation** — it is placed after the last message, scrolls with it
                        // and disappears when we scroll back up. The composer, for its part, carries only the field.
                        item(key = "browser-invite") {
                            BrowserInvite(
                                server = stateFlow.value.server,
                                // **The request belongs to that conversation.** Without this
                                // identifier, the same card appeared in every open
                                // thread: you left the one where the agent was asking for help and
                                // the invitation followed, although you had done nothing.
                                conversationId = state.conversationId
                                    ?: state.serverSessionId
                                    ?: "main",
                                // **The server session, as the thread knows it.** It is
                                // the one the request publishes: comparing it is enough to know whether
                                // the card belongs to this thread, without guessing.
                                sessionId = state.serverSessionId,
                                // The conversation whose turn is running is the one that asked for
                                // takeover: it goes ahead of the others to attach the request.
                                live = state.isStreaming,
                                onOpen = onOpenBrowser,
                                modifier = Modifier.padding(horizontal = OnyxSpace.Base),
                            )
                        }
                    } // closes the LazyColumn content
                } // closes GlassScrollFreeze (the glass freeze)
                } // closes the theme's CompositionLocalProvider
            } // closes the "thread" branch of `when (page)`
            // **The "/" menu sits ABOVE the plate, never behind it.**
            //
            // Defect found on the device: the list was the last child of the thread pane,
            // so stuck to the bottom of the screen — the composer plate covered it, and its
            // last lines were neither readable nor clickable ("it goes behind").
            // It now rises by exactly the thread's reserve: the menu starts where the
            // plate ends, and therefore never covers the field.
            val shownSlash = slashDemoItems ?: state.slashItems
            if (shownSlash.isNotEmpty()) {
                SlashMenu(
                    items = shownSlash,
                    onPick = onPickSlash,
                    maxHeight = slashMenuMaxHeight,
                    modifier = Modifier.padding(
                        start = PlateGeometry.SideMargin,
                        end = PlateGeometry.SideMargin,
                        top = OnyxSpace.Half,
                        // **The menu sits ON the plate, not in the middle of the screen.**
                        //
                        // It reserved `glassBottomInset + floatLane + BlockGap`: the lane of the
                        // floating controls (56 dp) was added to the composer's reserve, which
                        // already contains it — the menu therefore floated 68 dp above its plate,
                        // detached, in an empty band. It keeps only the composer's reserve
                        // plus half a step: it almost touches its plate, like a menu emerging from
                        // it.
                        bottom = glassBottomInset + OnyxSpace.Half,
                    ),
                )
            }

            }

            1 -> ChatFeedPane(
                cards = feedCards,
                feedback = feedFeedback,
                botName = threadSpeaker.label,
                faceName = threadSpeaker.faceName,
                // A bot conversation has **only one** author: the thread's. The resolver
                // names it when a card carries it, and the fallback keeps its face — a room's
                // card, for its part, uses the face of the bot that answered.
                face = { name -> threadSpeaker.faceCode.takeIf { name == threadSpeaker.faceName } },
                fallbackFaceCode = threadSpeaker.faceCode,
                accent = tabAccent,
                nowMillis = nowMillis,
                onFeedback = { cardId, value ->
                    feedFeedback = panels.setFeedback(panelKey, cardId, value)
                },
                onDiscuss = { card -> discussAbout(card.title) },
                onCompose = { openChatTab() },
                // **THE TAB CONTENT RESTS ON THE PAGE — the glass plate has been REMOVED.**
                // A previous batch placed a plate here (`panePlate`) behind the content; the
                // owner rejected it on a screenshot ("a transparent grey frame, I don't want
                // that"). The content is therefore **directly on the page**, with no frame behind it.
                // **The slide, for its part, stays**: it is the `HorizontalPager` that translates the
                // content under the finger — the plate goes, the gesture does not.
                modifier = Modifier.fillMaxSize()
                    .padding(top = glassTopInset, bottom = glassBottomInset + floatLane),
            )

            2 -> ChatFilesPane(
                files = exchangedFiles,
                accent = tabAccent,
                config = state.server,
                onOpen = { file, local ->
                    // The reader of the family, with the type announced by the name when the
                    // attachment does not carry one.
                    onOpenLocalFile(
                        android.net.Uri.fromFile(local),
                        file.name,
                        file.mime.ifBlank { FileViewers.mimeFor(file.name) },
                    )
                },
                // The Files tab content rests on the page too: no plate.
                modifier = Modifier.fillMaxSize()
                    .padding(top = glassTopInset, bottom = glassBottomInset + floatLane),
            )

            }
            }
            // The screen edge stays with the system back gesture: these two blades absorb a
            // horizontal swipe started from the edge, and nothing else — taps pass through.
            TabSwipeEdgeGuards(
                leftPx = edgeGuardLeftPx,
                rightPx = edgeGuardRightPx,
                modifier = Modifier.matchParentSize(),
            )
            }
            // **The bar is drawn AFTER the thread**, hence above it — on screen as for
            // the finger. The three merge defects were only one, and came from the reverse
            // order (bar placed **before** the pager in this `Box`):
            // - taps landed on the pager, which fills the whole surface: the bar was
            //   visible but **inert** — none of its buttons responded, and the
            //   accessibility tree did not even publish it;
            // - Haze can only sample content **already drawn**: the bar's pane
            //   had nothing to blur and fell back on its fallback veil, hence the "lost glass";
            // - a bar under the content has no edge: its border read against black.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height },
            ) {
            OnyxTopBar(
                // The title follows the name given to the conversation (`Conversation.name` of the reference, which takes precedence
                // over the first message); otherwise the first question, as before.
                title = (state.conversationName?.takeIf { it.isNotBlank() }
                    ?: state.firstUserQuestion)
                    ?.let(::chatHeaderTitle)
                    ?.takeIf { it.isNotBlank() }
                    ?: "New conversation",
                titleIsPlaceholder = state.conversationName.isNullOrBlank() &&
                    !state.hasUserTurn,
                conversationKey = state.firstMessageId.orEmpty(),
                // The second line of the title, like `ContentView:448`: the bot's **handle**
                // preceded by "@" (a plain conversation keeps the Settings agent name),
                // then the model. Without the "@" nor the model, the line repeated the title — it was
                // the "writer / writer" duplicate seen on the Magic V3.
                // The second line names **the model in force** — the one that will actually be sent
                // with the next message, the **same value** (`ChatUiState.modelInForce`: conversation →
                // bot → server → default, the gateway alias ruled out). No label recomputed
                // separately: this is what makes what is written be what leaves. The bot's name is
                // already above, and the "@handle" was of no use here (owner's request).
                subtitle = state.modelInForce
                    ?: OnyxLocalization["default model"],
                // And **the profile actually in force**, next to it: the bot → profile binding is read on
                // screen as it leaves with the turn (`ChatUiState.profileInForce`, the same decision
                // as `ChatUiState.botProfile`). The app can therefore no longer announce one profile and
                // serve another — "coder" reads here when "coder" is served.
                //
                // **But not twice.** The bar only writes this profile when it does not repeat the
                // title already above ([headerProfileLabel]): a bot conversation carries the name
                // of its bot (`botChatToOpen` titles it that way), so the "@writer" there was the
                // duplicate seen by the owner — and the place it took was missing from the model.
                // When the two differ (bot titled "Writer", profile `writer`), the line
                // stays, and that is where it informs. Routing, for its part, does not depend on this
                // label: it carries `botProfile` (`ProfileInForce.appliedTo`), and the bot's identity
                // — opened by the face, `BotEditorScreen` — always writes its `@handle`.
                profileLabel = if (threadBotUnknown) null else state.profileInForce,
                // The **declared** fallback, when the gateway switched: the model in force
                // (the fallback) is named by the pill above, and this line says **who** it
                // replaces and **where** it comes from — the "configured announced" lie is closed.
                fallbackLine = state.fallback?.declaredLine(),
                onOpenConversations = onOpenConversations,
                sidebarToggle = sidebarToggle,
                onPickModel = onPickModel,
                onNewConversation = onNewConversation,
                onMenu = { showMoreMenu = true },
                // The sub-agent capsule only appears when there are some: it says how many
                // are working, live, and opens the tasks screen.
                // **The number of tasks IN PROGRESS**, never the conversation's total.
                //
                // The capsule counted everything the thread had seen (`it.isLive`, and "launched without
                // follow-up" stayed live forever): it showed 40 on a conversation
                // that had nothing running any more. Only `RUNNING` really works — a closed
                // task no longer counts.
                subagentRuns = (subagentAgents ?: state.subagents).count { it.status == SubagentStatus.RUNNING },
                onOpenSubagents = if ((subagentAgents ?: state.subagents).isEmpty()) null else onOpenSubagents,
                contextUsed = contextUsage?.contextUsed,
                contextWindow = contextUsage?.contextMax ?: state.server.contextWindow.takeIf { it > 0 },
                // **The face is always there**, that of the bot whose conversation it is and, for
                // a plain conversation, that of Onyx ([ThreadSpeaker.faceName] gives it in
                // both cases). It already was in the reference; the condition
                // "only if `botProfile != null`" removed it from **every** new
                // conversation, that is, from the screen the person sees first.
                avatarName = if (threadBotUnknown) null else threadSpeaker.faceName,
                avatarFaceCode = if (threadBotUnknown) null else threadSpeaker.faceCode,
                // **The header's face opens the bot's identity**, like the face marker of
                // the bottom bar: the same gesture, at the same place in the drawing, gives the same
                // result. The wiring comes from the parent (`onOpenBotIdentity`); without it, the
                // face stays decorative.
                onOpenFace = onOpenBotIdentity,
                // LOT thread-organisation-skills: the header status dot. It only
                // sits during a turn, and only says what we really know: the number of
                // working sub-agents, never an invented "step 3 of 5" (the turn's plan
                // is not carried by the gateway on this thread).
                status = threadStatus(
                    working = state.isStreaming,
                    subagents = (subagentAgents ?: state.subagents).count { it.status == SubagentStatus.RUNNING },
                    flow = flow,
                ),
                // LOT live-thread: the real turn state and the reading signal. The dot
                // **breathes**, **pulses** or **stops with a glow** according to it, and
                // **slows down** as soon as the person scrolls back up to read the thread.
                flow = flow,
                readingUp = readingUp,
                )
            }

            AnimatedVisibility(
                visible = !state.isStreaming && state.followUps.isNotEmpty(),
                enter = fadeIn(animationSpec = OnyxMotion.Sites.MessageFold),
                exit = fadeOut(animationSpec = OnyxMotion.Sites.MessageFold),
            ) {
                FollowUpChips(
                    suggestions = state.followUps,
                    onSend = { text ->
                        onInputChange(text)
                        onSend()
                    },
                )
            }

            // The bots an `@` word in progress can name, just above the field
            // (`ComposerView.mentionMenu`): touching a row finishes the word in the draft and
            // leaves the cursor after it, ready to write the rest.
            if (mentionSuggestions.isNotEmpty()) {
                MentionSuggestions(
                    bots = mentionSuggestions,
                    onPick = { bot ->
                        mentionDismissed = null
                        onInputChange(BotMentions.complete(state.input, bot))
                        runCatching { composerFocus.requestFocus() }
                    },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }

            CompactComposerDock(
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
            Box(
                modifier = Modifier.onSizeChanged { composerHeightPx = it.height }
                    .onGloballyPositioned { composerWeldRect = it.boundsInParent() },
            ) {
                OnyxComposer(
                draft = state.input,
                onDraftChange = onInputChange,
                isStreaming = state.isStreaming,
                title = threadSpeaker.label,
                returnSends = returnSends,
                queued = state.queuedSize,
                attachments = state.attachments,
                onPasteImages = { uris -> onAddAttachments(uris) },
                onRemoveAttachment = onRemoveAttachment,
                onOpenAttachment = onOpenPending,
                onSend = onSend,
                onStop = onStop,
                onTalk = onOpenVoice,
                dictating = dictating,
                onDictate = ::toggleDictation,
                problem = state.attachmentProblem ?: dictationProblem,
                onExpand = onExpandDraft,
                onAddAttachment = { showAddMenu = true },
                // The **model chip** of the card: the model **in force** and its reasoning
                // level, and the chevron that opens the sheet (feature 2). It is only drawn
                // if a screen gives it something to open. The **same value** as the top pill
                // and as the send request — a gateway alias is already ruled out upstream.
                modelLabel = state.modelInForce
                    ?: OnyxLocalization["default model"],
                // A **local** fallback does not offer the configured model's notches: we do not write
                // a notch that drives nothing — the label is cleared upstream (`reasoningLabel`).
                reflectionLabel = reasoningLabel,
                onOpenModelPicker = onOpenModelSheet,
                // A physical keyboard writes in the field: ⌘L focuses it, and during a
                // response the group offers "steer" or "file", like `ComposerView`.
                focusRequester = composerFocus,
                canSteer = state.canSteer,
                onSteer = { onSteer(state.input) },
                // The bottom stack takes the system margin **only once** (see above): the
                // field does not take it again on its side, the two no longer add up.
                applyBottomInsets = false,
                // **The unreachable server is a state of the field.** It makes the invite **yield**: the
                // queue ("N messages waiting") already carries the state, and the field does not
                // start writing two texts at once (see `composerFieldInvite`).
                offline = state.isConnectionProblem,
                // **The composer is placed on the single plate** (see the bottom stack):
                // it no longer paints its own card, the plate takes care of it.
                plate = true,
                compact = true,
            )
            }

            // Conversation destinations remain in the header overflow; no bottom navigation here.
            }
        }

        // **Where the button sits, and why here.** It is placed above the **thread reserve**
        // ([threadBottomReserve] — the measured stack: composer, tab bar, system inset,
        // house gap) plus a clearance ([ComposerStacking.PillClearanceDp]). The old
        // margin was a second estimate (`composerHeight + tabBarHeight + 8`) **plus** a
        // `navigationBarsPadding()`: the system inset was counted twice there, and the button
        // sank into the composer card — measured 7.6 dp from the edge here, and up to 48 dp
        // **inside** where the root does not consume that inset (three-button bar, task bar
        // of a foldable). And non-empty queue = the waiting messages occupy the bottom of the thread:
        // the button withdraws, rather than covering them. The `@` mention list and the
        // `/` command list occupy the same place: the button fades in front of them.
        //
        // It is now placed in the **floating lane** ([floatLane]): it is this that guarantees
        // it **never sits on text** — the band is reserved for the thread, which does not
        // cross it. The chevron handle that shared this band is gone; the lane was
        // brought back to the strict height of the pill, and the thread recovered the difference.
        val jumpClearance = glassBottomInset + ComposerStacking.PillClearanceDp.dp

        // **THE MEASURED ANCHOR OF THE FLOATING ELEMENTS — and the guard that was missing.**
        //
        // `jumpClearance` (just above) is computed from `composerHeightPx` and
        // `tabBarHeightPx`: two **measured** states (`onSizeChanged`) that are **zero** until the
        // first layout pass. A position computed from zero is not an approximate position,
        // it is a **wrong** position — and it is the state-restoration defect reported
        // by the owner: "it creates bugs… it is inconsistent". On **every** app resume
        // (cold start, restore after process death, activity recreation by
        // rotation, theme, language or 1.3× font, return from a sheet, keyboard opened then
        // closed, insets changed), the floating elements therefore drew **before the measurement**, at
        // a position nothing supported, then jumped at once as soon as the measurement arrived:
        // the first frame of the resume, the one we see.
        //
        // The rule is held here, **once for both**: `threadFloatAnchor` returns the anchor
        // when the plate has delivered its heights, and `null` otherwise — and `null` means **we do not draw
        // it**. A position that is zero on the first pass is never painted: the element
        // appears at the right place, never in the header band nor under the status bar.
        // The drawn value remains `jumpClearance`: the same source as the thread padding, so
        // no second computation can diverge.
        val floatAnchor = threadFloatAnchor(
            composerPx = composerHeightPx,
            bottomRowPx = tabBarHeightPx,
            measured = jumpClearance,
        )

        // **The chevron handle has been removed** (owner's request: "remove the button
        // for the option to hide"). The bottom plate therefore can no longer be hidden, and there is
        // no return door left to keep: it is **the only** floating element less. The
        // "go to the last message" pill remains, under the same measurement guard (`floatAnchor`).
        AnimatedVisibility(
            visible = offersJump && pagerState.currentPage == 0 &&
                state.queuedSize == 0 && mentionSuggestions.isEmpty() && state.slashItems.isEmpty() &&
                // **The measurement guard**: the pill is not drawn before the plate's measurement.
                floatAnchor != null,
            // It **appears at once**: a short fade, frozen under "Reduce motion"
            // (`ComposerStacking.fadeSpec` — a single motion decision for the whole stack).
            enter = fadeIn(animationSpec = ComposerStacking.fadeSpec(reduceMotion)),
            exit = fadeOut(animationSpec = ComposerStacking.fadeSpec(reduceMotion)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Above the thread **and** the stack: on screen as for the finger.
                .zIndex(2f)
                // A margin on each side: the button no longer touches the pane edge.
                .padding(start = 16.dp, end = 16.dp, bottom = jumpClearance),
        ) {
            JumpToLatestPill(live = state.isStreaming, onClick = {
                // Re-attach the follow, then stick to the bottom: content that keeps growing
                // (the response being written) stays followed after the tap.
                following = ScrollFollow.jumpToLatest()
                scope.launch { listState.scrollToItem(latestItemIndex, BottomScrollOffset) }
            })
        }

        ToastHost(toaster = toaster)

        // Restore asks for **confirmation**: nothing is undone as long as the house
        // surface has not answered. It is **the** house confirmation ([OnyxConfirm]) — the panes'
        // glass, the family's radius 28, the light edge, the title and the socle body,
        // and two full-width pills **16 dp from the edge**, separated by **16 dp** — and not the
        // system box (grey plate, internal lines, bare text edge to edge) that the
        // owner pointed out: "not consistent with the rest of the theme, the buttons stick too
        // close to the edge".
        state.restorePrompt?.let { prompt ->
            OnyxConfirm(
                title = OnyxLocalization["Restore to this message?"],
                message = restorePromptMessage(prompt),
                destructiveLabel = OnyxLocalization["Restore"],
                cancelLabel = OnyxLocalization["Cancel"],
                onDestructive = onConfirmRestore,
                onCancel = onCancelRestore,
                localizeMessage = false,
            )
        }

        // Branching also asks for **confirmation**, and for the same reason: nothing is
        // copied until it is given. The box says what it does, what it leaves in
        // place, when nothing starts — and what the copy really carries.
        state.branchPrompt?.let { prompt ->
            BranchConfirmAlert(
                prompt = prompt,
                onCancel = onCancelBranch,
                onConfirm = onConfirmBranch,
            )
        }

        if (showMoreMenu && !showProjectMenu) {
            val items = buildList {
                ChatTab.paged.forEachIndexed { index, tab ->
                    val label = if (tab == ChatTab.FEED && feedBadge > 0) {
                        "${OnyxLocalization[tab.label]} · $feedBadge"
                    } else tab.label
                    add(MenuItem(label, tab.symbol) {
                        showMoreMenu = false
                        scope.launch { pagerState.scrollToPage(index) }
                    })
                }
                onOpenBotIdentity?.let { open ->
                    add(MenuItem(ChatTab.FACE_LABEL, Symbols.Ellipsis) {
                        showMoreMenu = false
                        open()
                    })
                }
                menuItems.forEach { item ->
                    add(item.copy(onSelect = {
                        showMoreMenu = false
                        item.onSelect()
                    }))
                    if (item.label == "Export as Markdown" && projectDirectory != null) {
                        add(
                            MenuItem(
                                if (projectDirectory.currentName == null) "Add to Project" else "Move to Project",
                                Symbols.Folder,
                            ) {
                                showProjectMenu = true
                            },
                        )
                    }
                }
            }
            // The action sheet rises from the bottom, in place of the floating list: the **same**
            // content (no entry lost, the project row included), plus the server's three lists
            // and the identifier copy. The title says what is being driven.
            ChatActionSheet(
                title = state.conversationName?.takeIf { it.isNotBlank() } ?: threadSpeaker.label,
                items = items,
                server = state.server,
                sessionId = state.serverSessionId,
                onDismiss = { showMoreMenu = false },
                // "Usage and cost" says when the turn was served locally: a rate per
                // million tokens then makes no sense there.
                fallback = state.fallback,
                // And it NAMES the **in-force** model — the header's, the same source
                // (`ModelInForce`): without that it named the frozen model of `session.usage`, which does not
                // follow a model change (the reported defect).
                modelInForce = state.modelInForce,
                // A bot's session lives in **its** profile: that is where the
                // recorded usage is read when the session is no longer live.
                profile = state.botProfile,
            )
        }
        projectDirectory?.let { directory ->
            if (showProjectMenu) {
                ChatProjectMenu(
                    directory = directory,
                    onMove = { project ->
                        showProjectMenu = false
                        showMoreMenu = false
                        scope.launch {
                            try {
                                directory.move(project)
                            } catch (error: Exception) {
                                projectProblem = OnyxLocalization.format(
                                    "Couldn't move it to %1\$s: %2\$s",
                                    project.label,
                                    projectProblemReason(error),
                                )
                            }
                        }
                    },
                    onNewProject = {
                        showProjectMenu = false
                        showMoreMenu = false
                        namingProject = true
                    },
                    // A return from the submenu reopens the main menu, like the Android back.
                    onDismiss = { showProjectMenu = false },
                )
            }
            ChatProjectPrompt(
                naming = namingProject,
                problem = projectProblem,
                onNamingChange = { namingProject = it },
                onProblemChange = { projectProblem = it },
                onCreate = { name ->
                    scope.launch {
                        try {
                            directory.create(name)
                        } catch (error: Exception) {
                            projectProblem = projectProblemReason(error)
                        }
                    }
                },
            )
        }

        if (showAddMenu) {
            // The sheet that rises from the bottom (the reference): a title, Onyx's **real** limits
            // as subtitle, and one row per entry with its icon dot. The order is that of
            // the reference; "Paste" is added to it, the reference does not have it.
            AttachmentSheet(
                title = "Add to message",
                subtitle = AttachmentLimits.subtitle(),
                // `listOfNotNull`: the "Teach a task" row is **optional**
                // (server without Dashboard credentials), and a typed list cannot carry a
                // null element.
                actions = listOfNotNull(
                    AttachmentAction("Photo Library", Lucide.Image) {
                        showAddMenu = false
                        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ))
                    },
                    AttachmentAction("Camera", Lucide.Camera) {
                        showAddMenu = false
                        onOpenCamera()
                    },
                    AttachmentAction("Files", Lucide.Paperclip) {
                        showAddMenu = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                    AttachmentAction("Paste", Lucide.Clipboard) {
                        showAddMenu = false
                        onAddAttachments(clipboardImages(context))
                    },
                    // **"Teach a task"**, like "Teach a task" in the reference: the
                    // last row of the sheet. It closes the sheet and opens the connected
                    // computer, where the demonstration is recorded — the gesture lives there (the clicks,
                    // the screen, the server lease), the row is only the path.
                    onTeachTask?.let { teach ->
                        AttachmentAction("Teach a task", Lucide.Sparkles) {
                            showAddMenu = false
                            teach()
                        }
                    },
                ),
                onDismiss = { showAddMenu = false },
            )
        }
    }
}

/**
 * Where the camera writes its shot: **the app's durable private storage**, never the cache.
 *
 * `cacheDir` is the directory the system empties when it wants: the photo disappeared from it after
 * the app closed, and the thread bubble showed "Unreadable" (it decodes from that URI).
 * `filesDir` survives closing, the phone restarting and cache cleanup.
 */
private fun newCameraUri(context: android.content.Context): android.net.Uri? = runCatching {
    val folder = java.io.File(context.filesDir, "photos").apply { mkdirs() }
    val file = java.io.File(folder, "photo-${System.currentTimeMillis()}.jpg")
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()

/**
 * "Go to the last message", placed above the bottom stack when the end is no longer on screen.
 *
 * **The target is 44 dp** ([OnyxSpace.ThumbnailSize], the house thumb target): the pill
 * was only 18 dp high — measured in the accessibility tree —, below the finger target, hence
 * "I can't manage to touch it". The glass stretches over the target, the content stays centred.
 */
@Composable
private fun JumpToLatestPill(live: Boolean, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    GlassPill(
        // **Solid**, and not merely thin: it floats above the thread, so thin glass
        // would let the messages be read **inside** the pill (reported defect: "the pill
        // blends into the text"). The 16 margin and the placement above the plate are set by
        // the caller (`ChatScreen`'s `AnimatedVisibility`): the pill does not move here.
        solid = true,
        modifier = Modifier
            .sizeIn(minWidth = OnyxSpace.ThumbnailSize, minHeight = OnyxSpace.ThumbnailSize)
            .onyxClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxBase.Tight),
        ) {
            OnyxIcon(Symbols.ArrowDown, tint = theme.secondaryText, size = 12.dp)
            BasicText(
                text = "Jump to latest",
                style = theme.subheadline.copy(color = theme.textColor),
            )
            if (live) {
                NewBelowDot()
            }
        }
    }
}

/** `NewBelowDot`: the 8 pt dot pulses in `easeInOut` every 0.6 s during live streaming. */
@Composable
private fun NewBelowDot() {
    val theme = LocalOnyxTheme.current
    val transition = rememberInfiniteTransition(label = "newBelow")
    // `NewBelowDot` : `guard live, !reduceMotion else { dim = false; return }`
    // (`TranscriptView:528-529`) — the dot stays solid under Reduce Motion.
    val alpha = ambientLoop(1f) {
        1f - 0.75f * transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(OnyxMotion.MS_600, easing = OnyxMotion.EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "newBelowAlpha",
        ).value
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(theme.accent.copy(alpha = alpha), RoundedCornerShape(50)),
    )
}

/** The prefill progress, when the server reports it. */
@Composable
private fun PrefillBar(prefill: PrefillProgress) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(theme.separator),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(prefill.fraction)
                .height(2.dp)
                .background(theme.accent),
        )
    }
}

/** Gateway status during the turn (provider retry, unavailability): honest instead of a blank. */
@Composable
private fun ProviderStatusBar(status: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxBase.Tight),
    ) {
        BasicText(
            text = "⏳ $status",
            localize = false,
            style = theme.footnote.copy(color = theme.secondaryText),
        )
    }
}

/**
 * The status line of a steering note (`Conversation.statusLine`): what the server
 * answered — "steer queued" or "steer rejected". It fades at the end of the turn.
 */
@Composable
private fun SteerStatusBar(status: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxBase.Tight),
    ) {
        OnyxIcon(Symbols.ArrowTurnDownRight, tint = theme.secondaryText, size = 12.dp)
        BasicText(
            text = status,
            localize = false,
            style = theme.footnote.copy(color = theme.secondaryText),
        )
    }
}

/** The error banner, with "Retry" when the turn can be replayed. */
@Composable
private fun ErrorBanner(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .frostedBar(com.onyx.app.ui.design.FrostSurface.BANNER)
            .background(theme.error.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.error, size = 16.dp)
        BasicText(
            text = message,
            localize = false,
            style = theme.footnote.copy(color = theme.error),
            modifier = Modifier.weight(1f),
        )
        if (canRetry) {
            BasicText(
                text = "Retry",
                style = theme.footnote.copy(color = theme.error, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.onyxClickable(onClick = onRetry),
            )
        }
        BasicText(
            text = "Dismiss",
            style = theme.footnote.copy(color = theme.secondaryText),
            modifier = Modifier.onyxClickable(onClick = onDismiss),
        )
    }
}

/** The in-conversation search (`showSearch` of ContentView): the field, and the count. */
@Composable
private fun SearchBar(
    query: String,
    matches: Int,
    position: Int,
    onStep: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .frostedBar(com.onyx.app.ui.design.FrostSurface.SEARCH_FIELD)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxBase.Tight),
    ) {
        OnyxIcon(Symbols.MagnifyingGlass, tint = theme.secondaryText, size = 15.dp)
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                BasicText(
                    text = "Find in conversation",
                    style = theme.body.copy(color = theme.tertiaryText),
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = theme.body.copy(color = theme.textColor),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            BasicText(
                text = if (matches == 0) "None" else "$position of $matches",
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

/** One thread page: what the list renders at once (`TranscriptPage`). */
private const val PAGE = 30

/**
 * How many pixels we ask to overshoot the last row to reach the true bottom of the thread:
 * `scrollToItem(index, offset)` clamps the offset to the content bottom, so a large overshoot sticks
 * to the bottom — and not to the top of the last row, which may cover several screens.
 */
private const val BottomScrollOffset = 100_000

/** "Today" / "Yesterday" / "Monday, 3 March", where the day changes. */
@Composable
private fun DaySeparator(label: String) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = theme.caption.copy(color = theme.tertiaryText),
        )
    }
}

/**
 * The previous page, with one tap: "Show the 30 previous messages" when the page is already
 * in memory, "Load the previous messages" when it must be requested from the server.
 */
@Composable
private fun EarlierRow(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = theme.subheadline.copy(color = if (enabled) theme.accent else theme.secondaryText),
            modifier = Modifier
                .background(theme.fill, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .onyxClickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** The day label at the first message of a day, or `null`. */
private fun dayLabelFor(createdAt: Long, previousCreatedAt: Long?): String? {
    val current = java.util.Calendar.getInstance().apply { timeInMillis = createdAt }
    val today = java.util.Calendar.getInstance()

    fun sameDay(a: java.util.Calendar, b: java.util.Calendar): Boolean =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

    if (previousCreatedAt != null) {
        val previous = java.util.Calendar.getInstance().apply { timeInMillis = previousCreatedAt }
        if (sameDay(previous, current)) return null
    } else if (sameDay(today, current)) {
        return null
    }

    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(today, current) -> "Today"
        sameDay(yesterday, current) -> "Yesterday"
        else -> dayFormat().format(java.util.Date(createdAt))
    }
}

/**
 * The day formatter, **kept once**.
 *
 * `SimpleDateFormat` is expensive to construct (locale, calendar, symbol loading) and
 * is not reusable concurrently — but composition is single-threaded, and recreating it for
 * each visible row, on each recomposition, was a pure cost in the scroll of a large
 * conversation.
 */
private val dayFormatter: ThreadLocal<java.text.SimpleDateFormat> =
    ThreadLocal.withInitial { java.text.SimpleDateFormat("EEEE, d MMMM", OnyxLanguage.preferred) }

private fun dayFormat(): java.text.SimpleDateFormat = dayFormatter.get()!!

/** What the thread accepts as a drop: images, like the composer. */
@Composable
private fun rememberDragAndDrop(onAddAttachments: (List<android.net.Uri>) -> Unit): DragAndDropTarget {
    val context = LocalContext.current
    return remember(onAddAttachments) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val items = event.toAndroidDragEvent().clipData ?: return false
                val uris = buildList {
                    for (index in 0 until items.itemCount) {
                        val uri = items.getItemAt(index).uri ?: continue
                        val mime = context.contentResolver.getType(uri).orEmpty()
                        if (mime.startsWith("image/") || mime.startsWith("text/")) add(uri)
                    }
                }
                if (uris.isEmpty()) return false
                onAddAttachments(uris)
                return true
            }
        }
    }
}
