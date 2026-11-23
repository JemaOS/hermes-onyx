package com.onyx.app.ui.bots

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.chat.ScrollFollow
import com.onyx.app.ui.chat.components.MarkdownText
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.ComposerCircleButton
import com.onyx.app.ui.design.GlassCircleButton
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.SafeBarSpacing
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.voice.SpeechEngine

/**
 * The group chat.
 *
 * Port of `GroupChatView`: the bot row (you choose who answers), the thread where each reply
 * carries its bot's face and name, and the composer. "Read aloud" reads each reply.
 *
 * This file also gathers the port of `GroupVoice` (the group's voice controls), the room banner,
 * the per-bot turn indicator and a bot's menu. The ViewModel (`GroupChatViewModel`) is not touched:
 * any state that does not live there (dictation, the menu) is held here by `remember`.
 */
@Composable
fun GroupChatScreen(
    viewModel: GroupChatViewModel,
    server: ServerConfig,
    onBack: () -> Unit,
    /** The group's pencil: a **new conversation for this group** (same members). */
    onNewConversation: (() -> Unit)? = null,
    /** The `GroupComposer` Talk button opens the room's voice scene. */
    onOpenVoice: () -> Unit,
    /** Language of the dictation in the field, distinct from the hands-free scene. */
    speechLocale: String = SpeechEngine.DEFAULT_LOCALE,
    /** `Switch to typing`: give focus and keyboard back to the composer once back. */
    focusComposer: Boolean = false,
    onComposerFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** The room title. Failing that, the chosen bots provide it (`formatted(.list(type: .and))`). */
    title: String? = null,
    /** Long press on a bot: open its editor. `null` hides the menu entry. */
    onEditBot: ((BotProfile) -> Unit)? = null,
    /** "Chat text size": the thread's scale, as in the solo chat. */
    chatTextScale: Float = 1f,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val composerFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusComposer) {
        if (focusComposer) {
            composerFocusRequester.requestFocus()
            keyboard?.show()
            onComposerFocused()
        }
    }

    // The chosen bots, in roster order: the banner and the empty state name them.
    val chosenBots = remember(state.bots, state.selected) {
        state.bots.filter { it.name in state.selected }
    }

    // The current turn: the person's last message opens it and what follows, bot by bot,
    // says who is still answering, who has finished, and which bot is writing right now.
    val turn = remember(state.messages) { groupTurn(state.messages) }

    // A room's thread follows the end **as long as the person does not scroll up**, like the solo
    // thread: the decision lives in `ScrollFollow`. A finger on the list detaches following at once;
    // at rest, coming back within the anchor margin re-attaches it; content that grows moves the
    // view only if we are following. (Before, `LaunchedEffect(lastIndex, lastLength)` *animated* to
    // the bottom on each piece of text received: you could not scroll up to read while the bots
    // were working.)
    //
    // The bottom measure is the solo thread's, with one reservation: here the list applies
    // `PaddingValues(vertical = 12.dp)` — that value, not the viewport edge, is the thread's real
    // bottom. `ScrollFollow` carries the rule; the screen only plugs it in.
    val anchorMarginPx = with(LocalDensity.current) { ScrollFollow.AnchorMarginDp.dp.toPx() }
    val threadBottomPaddingPx = with(LocalDensity.current) { GroupThreadBottomPadding.roundToPx() }
    val distanceToBottomPx by remember(anchorMarginPx, threadBottomPaddingPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf 0
            if (last.index < info.totalItemsCount - 1) {
                Int.MAX_VALUE
            } else {
                ScrollFollow.distanceToBottom(
                    itemEndPx = last.offset + last.size,
                    viewportEndPx = info.viewportEndOffset,
                    bottomPaddingPx = threadBottomPaddingPx,
                )
            }
        }
    }
    // A **state**: the `snapshotFlow` below only follows state reads (see `ChatScreen`,
    // same rule and same reason).
    val withinAnchor by remember(anchorMarginPx, threadBottomPaddingPx) {
        derivedStateOf { ScrollFollow.withinAnchor(distanceToBottomPx, anchorMarginPx.toInt()) }
    }
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) following = ScrollFollow.afterTouch()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to withinAnchor }
            .collect { (scrolling, anchored) ->
                following = ScrollFollow.atRest(scrolling, anchored, following)
            }
    }
    val lastIndex = state.messages.lastIndex
    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(lastIndex, lastLength, following) {
        if (following && lastIndex >= 0) listState.scrollToItem(lastIndex, BottomScrollOffset)
    }
    // A question leaves from wherever it is written: it brings us back to the bottom
    // (`bot == null` is the person, as in `GroupMessageRow`).
    var seenUserTurns by remember { mutableStateOf(0) }
    val userTurns = remember(state.messages) { state.messages.count { it.bot == null } }
    LaunchedEffect(userTurns) {
        if (userTurns > seenUserTurns) {
            following = ScrollFollow.jumpToLatest()
            listState.scrollToItem(lastIndex.coerceAtLeast(0), BottomScrollOffset)
        }
        seenUserTurns = userTurns
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme)
            // The system area first, then the house's visual space: the room's writing bar can no
            // longer be buried under the navigation bar, nor appear stuck to the edge.
            .navigationBarsPadding()
            .padding(bottom = SafeBarSpacing.GapDp.dp),
    ) {
        // `GroupChatView`: list, room header (faces + name), thread, then GroupComposer.
        GroupChatHeader(
            title = title.orEmpty(),
            bots = chosenBots,
            onBack = onBack,
            onNewConversation = onNewConversation,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                GroupOpening(loading = state.loading, bots = chosenBots)
            } else {
                // The Settings text scale is applied at the **root** — the activity reads it
                // (`LocalOnyxTextScale`), the theme applies it to the whole tree. `theme` therefore
                // **already** holds the chosen scale: applying it again here would apply it
                // twice (1.23 x 1.23 = 1.51). The `chatTextScale` parameter is deliberately
                // left unused here; scaling belongs to the root.
                CompositionLocalProvider(LocalOnyxTheme provides theme) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = GroupThreadBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        GroupMessageRow(message = message, server = server)
                    }
                }
                }
            }
        }

        GroupComposer(
            bots = state.bots,
            isStreaming = state.asking,
            onSend = { question, files -> viewModel.send(question, files) },
            onStop = { viewModel.stop() },
            onTalk = onOpenVoice,
            serverError = state.error,
            speechLocale = speechLocale,
            focusRequester = composerFocusRequester,
        )
    }

    // A long press on a bot opens no menu: the reference's `GroupChatView` has none for its bots
    // (only the room has its own — Find in Chat, Rename, Mute, End Group Chat, not ported yet).
    // The "Edit bot…" / "Open its chat" menu that lived here could never open: its state was
    // never set, and its two callbacks were supplied by no one.
}

/** `GroupChatView` header: Conversations, FaceStack, the room name, and the pencil. */
@Composable
private fun GroupChatHeader(
    title: String,
    bots: List<BotProfile>,
    onBack: () -> Unit,
    onNewConversation: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassCircleButton(
            symbol = Symbols.ListBullet,
            label = "Conversations",
            size = 36.dp,
            iconSize = 18.dp,
            tint = theme.secondaryText,
            onClick = onBack,
        )
        if (bots.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                bots.take(3).forEach { bot ->
                    BotLook(name = bot.name, size = 22.dp, faceCode = bot.faceCode)
                }
            }
        }
        BasicText(
            text = title,
            localize = title.isEmpty(),
            style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onNewConversation != null) {
            // The pencil, as in a bot thread: a conversation for **this** group, same
            // members, same room.
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

/**
 * How many pixels we ask to exceed the last line by to reach the thread's real bottom
 * (see `ChatScreen`: `scrollToItem(index, offset)` clamps the offset to the content's bottom).
 */
private const val BottomScrollOffset = 100_000

/**
 * **The reserve a room's thread applies below its last line**, in dp: it is the list padding's
 * `vertical`. It is named because it is also the thread's **real bottom** for the following
 * measurement ([ScrollFollow.distanceToBottom]): without this term, the padding counted as
 * scrolled-up thread, and following re-attached while we were reading two lines further
 * up.
 */
private val GroupThreadBottomPadding = 12.dp

/** What a thread turn says about a bot: nothing, it is writing, or it has finished. */
private enum class TurnMark { NONE, WORKING, DONE }

/** The state of the current turn: who is writing, how many are answering, and the turn number. */
private data class GroupTurn(
    /** Each bot's state during this turn, by profile name. */
    val marks: Map<String, TurnMark>,
    /** How many bots are answering right now. */
    val answering: Int,
    /** The number of turns: one per person message (`Turn N`). */
    val turns: Int,
    /** The bot writing now, if any. */
    val writing: BotProfile?,
)

/**
 * Reads the thread for the current turn: since the person's last message, each bot is
 * "writing" if one of its replies is streaming, "done" if it has at least one posted reply,
 * and "nothing" otherwise. The turn number is the count of person messages.
 */
private fun groupTurn(messages: List<GroupMessage>): GroupTurn {
    val lastAsk = messages.indexOfLast { it.bot == null }
    val rows = messages.withIndex().filter { it.index > lastAsk && it.value.bot != null }
    val marks = rows.groupBy { it.value.bot!!.name }.mapValues { (_, group) ->
        when {
            group.any { it.value.isStreaming } -> TurnMark.WORKING
            group.any { !it.value.failed && it.value.text.isNotBlank() } -> TurnMark.DONE
            else -> TurnMark.NONE
        }
    }
    return GroupTurn(
        marks = marks,
        answering = marks.values.count { it == TurnMark.WORKING },
        turns = messages.count { it.bot == null },
        writing = messages.lastOrNull { it.isStreaming }?.bot,
    )
}

/**
 * The room banner: the chosen bots' faces, the title, the number of chosen bots and the
 * turn number, then the turn state ("N of M answering") and the writing bot's line.
 */
@Composable
private fun GroupBanner(
    bots: List<BotProfile>,
    chosen: Int,
    total: Int,
    answering: Int,
    turns: Int,
    writing: BotProfile?,
    asking: Boolean,
    title: String?,
) {
    val theme = LocalOnyxTheme.current
    val roomTitle = title?.takeIf { it.isNotBlank() } ?: joinLabels(bots.map { it.label() })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FacesRow(bots = bots)
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = roomTitle.ifEmpty { "Group chat" },
                    localize = roomTitle.isEmpty(),
                    style = theme.headline.copy(color = theme.textColor),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = "$chosen of $total chosen" + if (turns > 0) " · Turn $turns" else "",
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 1,
                )
            }
        }
        // "N of M answering": the turn state, while it runs.
        if (asking || answering > 0) {
            TurnStatusLine(text = "$answering of $chosen answering")
        }
        // The writing bot's line, like the live reply `GroupChatView` draws.
        if (writing != null) {
            TurnStatusLine(text = "${writing.label()} · Replying…")
        }
    }
}

/** A turn status line: the live dot, then the text. */
@Composable
private fun TurnStatusLine(text: String) {
    val theme = LocalOnyxTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LiveMark(color = theme.accent)
        BasicText(
            text = text,
            style = theme.footnote.copy(color = theme.secondaryText),
        )
    }
}

/** The faces of a few overlapping bots (`FaceStack`), each on the page background. */
@Composable
private fun FacesRow(bots: List<BotProfile>, size: Dp = 24.dp) {
    val theme = LocalOnyxTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(-(size * 0.28f))) {
        bots.take(4).forEach { bot ->
            Box(
                modifier = Modifier
                    .background(theme.pageColor, CircleShape)
                    .padding(1.dp),
            ) {
                BotLook(name = bot.name, size = size, faceCode = bot.faceCode)
            }
        }
    }
}

/**
 * The bot row: each one turns on or off with a tap, and carries its turn indicator —
 * a live dot while it answers, a discreet check once it has finished.
 *
 * A tap, nothing more: the reference's `GroupChatView` gives no menu to a room bot (the
 * room's own — Find in Chat, Rename, Mute, End Group Chat — concerns the room).
 */
@Composable
private fun RosterRow(
    bots: List<BotProfile>,
    selected: Set<String>,
    marks: Map<String, TurnMark>,
    onToggle: (BotProfile) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bots.forEach { bot ->
            val active = bot.name in selected
            val mark = marks[bot.name] ?: TurnMark.NONE
            Row(
                modifier = Modifier
                    .background(
                        color = if (active) theme.accent.copy(alpha = 0.16f) else theme.surfaceColor,
                        shape = RoundedCornerShape(50),
                    )
                    .onyxClickable { onToggle(bot) }
                    .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BotLook(name = bot.name, size = 22.dp, faceCode = bot.faceCode)
                BasicText(
                    text = bot.label(),
                    localize = false,
                    style = theme.caption.copy(
                        color = if (active) theme.textColor else theme.secondaryText,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // The bot's turn indicator, after its name.
                when (mark) {
                    TurnMark.WORKING -> LiveMark(color = theme.accent)
                    TurnMark.DONE -> OnyxIcon(
                        symbol = Symbols.Checkmark,
                        tint = theme.tertiaryText,
                        size = 12.dp,
                        contentDescription = "Done",
                    )

                    TurnMark.NONE -> Unit
                }
            }
        }
    }
}

/**
 * The thread's empty state: the bots' faces, "Ask them together.", the reference's help sentence and
 * the reminder of the group's `@` mentions.
 */
@Composable
private fun GroupOpening(loading: Boolean, bots: List<BotProfile>) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bots.isNotEmpty()) {
            FacesRow(bots = bots, size = 44.dp)
            Spacer(Modifier.height(16.dp))
        }
        BasicText(
            text = if (loading) "Loading from Onyx…" else "Ask them together.",
            style = theme.title3.copy(color = theme.textColor, textAlign = TextAlign.Center),
        )
        if (!loading) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "Say something to all of them, or start with @ and a name to ask one. " +
                    "A bot that has nothing to add stays quiet.",
                style = theme.subheadline.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
            )
            if (bots.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                // The mention reminder: the group's `@` handles, ready to type.
                BasicText(
                    text = bots.joinToString("   ") { "@" + it.handle },
                    localize = false,
                    style = theme.footnote.copy(color = theme.accent, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/**
 * The group's voice controls (port of `GroupVoice`): "Talk to the group" opens the mic
 * (waveform symbol), the "Listening" state says so, and "Stop reading" cuts the current reading
 * (the reference's `halt`: the reading, the listening, and the round if it runs).
 */
@Composable
private fun GroupVoiceBar(
    listening: Boolean,
    reading: Boolean,
    onTalk: () -> Unit,
    onStopListening: () -> Unit,
    onStopReading: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // "Talk to the group": the same kind of round button as the composer, with the waveform symbol.
        ComposerCircleButton(
            symbol = Symbols.Waveform,
            label = if (listening) "Listening" else "Talk to the group",
            tint = theme.accent,
            size = 42.dp,
            onClick = onTalk,
        )
        Column(modifier = Modifier.weight(1f)) {
            when {
                listening -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LiveMark(color = theme.success)
                        BasicText(
                            text = "Listening",
                            style = theme.subheadline.copy(color = theme.success, fontWeight = FontWeight.SemiBold),
                        )
                    }
                    BasicText(
                        text = "What you say goes to the group.",
                        style = theme.caption.copy(color = theme.secondaryText),
                    )
                }

                reading -> {
                    BasicText(
                        text = "Reading the replies",
                        style = theme.subheadline.copy(color = theme.textColor),
                    )
                    BasicText(
                        text = "Tap Talk to the group to answer by voice.",
                        style = theme.caption.copy(color = theme.secondaryText),
                    )
                }

                else -> {
                    BasicText(
                        text = "Talk to the group",
                        style = theme.subheadline.copy(color = theme.textColor),
                    )
                    BasicText(
                        text = "The bots' replies are read to you.",
                        style = theme.caption.copy(color = theme.secondaryText),
                    )
                }
            }
        }
        // Close the mic ("Stop" of `GroupVoice`), without cutting the reading.
        if (listening) {
            ComposerCircleButton(
                symbol = Symbols.Xmark,
                label = "Stop listening",
                tint = theme.error,
                size = 42.dp,
                onClick = onStopListening,
            )
        }
        // "Stop reading": cuts the current reading, like the reference's speaker button.
        if (reading) {
            ComposerCircleButton(
                symbol = Symbols.Stop,
                label = "Stop reading",
                tint = theme.warning,
                size = 42.dp,
                onClick = onStopReading,
            )
        }
    }
}

@Composable
internal fun GroupMessageRow(message: GroupMessage, server: ServerConfig) {
    val theme = LocalOnyxTheme.current
    val bot = message.bot

    if (bot == null) {
        // The question and its files, on the right; technical notes stay outside the bubble.
        val said = AttachedFileNote.split(message.text)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (message.waiting) 0.6f else 1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (said.text.isNotEmpty()) {
                com.onyx.app.ui.chat.components.MessageRow(
                    message = ChatMessage(id = message.id, role = Role.USER, text = said.text, createdAt = 0L),
                    replyName = "",
                    isLive = false,
                    showActions = false,
                )
            }
            if (said.paths.isNotEmpty() || message.pendingFiles.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    said.paths.forEach { path -> GroupFileChip(path = path, server = server) }
                    message.pendingFiles.forEach { name -> PendingGroupFileChip(name = name) }
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotLook(
                name = bot.name,
                size = 20.dp,
                faceCode = bot.faceCode,
                mood = if (message.isStreaming) BotMood.THINKING
                else if (message.failed) BotMood.ERROR
                else BotMood.PLEASED,
            )
            if (message.isStreaming) LiveMark(color = theme.accent)
            BasicText(
                text = bot.label(),
                localize = false,
                style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            )
        }
        if (message.text.isNotEmpty()) {
            if (message.failed) {
                BasicText(
                    text = message.text,
                    localize = false,
                    style = theme.footnote.copy(color = theme.error),
                    modifier = Modifier.padding(start = 26.dp),
                )
            } else {
                MarkdownText(
                    markdown = message.text,
                    isStreaming = message.isStreaming,
                    modifier = Modifier.padding(start = 26.dp),
                )
            }
        } else if (message.isStreaming) {
            // The bot is writing: the reference's "Replying…" line, under its face.
            BasicText(
                text = "Replying…",
                style = theme.body.copy(color = theme.tertiaryText),
                modifier = Modifier.padding(start = 26.dp),
            )
        }
    }
}

/**
 * The names joined like `formatted(.list(type: .and))`: "A", "A and B", "A, B and C".
 * They provide the default title of a room whose name was not supplied.
 */
private fun joinLabels(labels: List<String>): String {
    val and = OnyxLocalization["and"]
    return when (labels.size) {
        0 -> ""
        1 -> labels[0]
        2 -> "${labels[0]} $and ${labels[1]}"
        else -> labels.dropLast(1).joinToString(", ") + " $and " + labels.last()
    }
}
