package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.SpeechEngine

/**
 * Le fil d'une salle de bots du serveur.
 *
 * Comme `GroupChatView`: l'en-tête (Conversations, les
 * visages des membres, le nom, le menu de la salle), ce qui a été dit lu du journal de la salle
 * (chaque réponse sous le visage et le nom de son bot), les messages de la personne, les réponses
 * que les bots écrivent à cet instant, l'état de la salle (« The bots are talking… », ce qui attend
 * la personne), et le composer — celui d'une salle Desktop remplacé par la note qui dit où elle se
 * parle.
 *
 * Rien ici ne touche au fil local de l'application (`GroupChatViewModel`): ce qui est écrit part
 * dans la salle (`groups.send`), et ce qui s'affiche vient de son journal.
 */
@Composable
fun GroupRoomScreen(
    viewModel: GroupRoomViewModel,
    server: ServerConfig,
    onBack: () -> Unit,
    /**
     * Le bouton Talk du composer ouvre la scène vocale de la salle: c'est ce fil
     * (`GroupRoomFeed`) qu'elle lit, donc les réponses du serveur et celui qui parle; la question
     * reconnue part par `groups.send` et le Stop rouge par `groups.stop`. Sans cette lambda, aucun
     * bouton Talk n'est dessiné.
     */
    onOpenVoice: (() -> Unit)? = null,
    /** La salle a été terminée ici: la liste la laisse partir. */
    onEnded: () -> Unit,
    speechLocale: String = SpeechEngine.DEFAULT_LOCALE,
    focusComposer: Boolean = false,
    onComposerFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    chatTextScale: Float = 1f,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val room = state.room
    val listState = rememberLazyListState()
    val composerFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val mutes = remember(context) { Mutes(context) }
    val faceStore = remember(context) { BotFaceStore(context) }
    val face: (String) -> String? = remember(faceStore) { { name -> faceStore.faceOf(name) } }

    var showingMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var confirmingEnd by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var findIndex by remember { mutableStateOf(0) }
    /** Le premier message montré, par son numéro dans le journal (`shownFrom` ). */
    var shownFrom by remember { mutableStateOf<Int?>(null) }

    val rows = state.rows
    val page = remember(rows, shownFrom) { roomPage(rows, shownFrom) }
    val matches = remember(rows, findText, findOpen) { roomMatches(rows, findOpen, findText) }
    val current = matches.getOrNull(findIndex.coerceIn(0, (matches.size - 1).coerceAtLeast(0)))

    // Le fil est suivi tant que l'écran est ouvert et que l'application est devant, comme le
    // `task(id: scenePhase == .active)`: le journal et les réponses en cours sont relus
    // une fois par seconde pendant que les bots parlent, et toutes les cinq secondes sinon.
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

    // Le premier journal venu ouvre la page sur ses derniers messages (`settleShown`).
    LaunchedEffect(rows) {
        if (shownFrom == null && rows.isNotEmpty()) shownFrom = rows[(rows.size - PAGE).coerceAtLeast(0)].seq
    }

    // Une nouveauté est suivie depuis le bas seulement: relire plus haut n'est pas tiré en arrière.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        RoomHeader(
            room = room,
            face = face,
            onBack = onBack,
            onMenu = { showingMenu = true },
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

        Box(modifier = Modifier.weight(1f)) {
            if (rows.isEmpty() && state.sending.isEmpty() && state.shownDrafts.isEmpty() && !state.loaded) {
                RoomOpening(room = room, face = face)
            } else {
                CompositionLocalProvider(LocalOnyxTheme provides theme.withFontScale(chatTextScale)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                        items(page.shown, key = { "seq-" + it.seq }) { event ->
                            RoomEventRow(
                                event = event,
                                room = room,
                                server = server,
                                face = face,
                                highlighted = current == event.seq,
                            )
                        }
                        // Les messages de la personne pas encore dans le journal.
                        items(state.sending, key = { "sending-" + it.id }) { pending ->
                            MineRow(
                                text = AttachedFileNote.split(pending.text).text,
                                paths = AttachedFileNote.split(pending.text).paths,
                                files = pending.files,
                                server = server,
                                waiting = true,
                            )
                        }
                        // Les réponses en train d'être écrites, sous leur bot, jusqu'à ce que la salle les ait.
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
                                    // Ce que le serveur a refusé passe tel quel, jamais traduit.
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

        if (room.inDesktop) {
            DesktopNote()
        } else {
            GroupComposer(
                bots = room.composerBots(face),
                isStreaming = state.busy,
                onSend = { text, files -> viewModel.send(text, files) },
                onStop = { viewModel.stop() },
                onTalk = onOpenVoice,
                serverError = state.problem,
                speechLocale = speechLocale,
                focusRequester = composerFocusRequester,
            )
        }
    }

    if (showingMenu) {
        OnyxMenu(
            items = buildList {
                add(MenuItem(label = "Find in Chat", symbol = Symbols.MagnifyingGlass) {
                    showingMenu = false
                    findOpen = true
                })
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

/** Combien de messages une page tient (`GroupChatView.pageSize`). */
private const val PAGE = 30

/** Une page du journal: les lignes montrées, combien sont avant, et où la page commence. */
internal data class RoomPage(val shown: List<GroupEvent>, val hidden: Int, val start: Int)

/** Les lignes du journal à partir de `from`, ou les plus récentes tant qu'il n'est pas posé. */
internal fun roomPage(rows: List<GroupEvent>, from: Int?): RoomPage {
    val start = from?.let { seq -> rows.indexOfFirst { it.seq >= seq }.takeIf { it >= 0 } ?: rows.size }
        ?: (rows.size - PAGE).coerceAtLeast(0)
    return RoomPage(shown = if (start < rows.size) rows.subList(start, rows.size) else emptyList(), hidden = start, start = start)
}

/** Les messages où les mots cherchés se trouvent, par leur numéro, du plus ancien au plus récent. */
internal fun roomMatches(rows: List<GroupEvent>, open: Boolean, query: String): List<Int> {
    val words = query.trim()
    if (!open || words.isEmpty()) return emptyList()
    return rows.filter { it.isMessage && AttachedFileNote.strip(it.text).contains(words, ignoreCase = true) }.map { it.seq }
}

/**
 * Combien de lignes la liste de la salle dessine: ce que l'écran empile, dans l'ordre — les lignes
 * du journal montrées, les messages en route, les réponses qui s'écrivent, la ligne de la salle, les
 * cartes à répondre, et ce que le serveur a refusé.
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

/** Va à un message trouvé: quand il est avant la page montrée, la page s'ouvre sur lui. */
internal fun findGoTo(seq: Int, page: RoomPage, shownFrom: Int?): Int? =
    if (page.shown.any { it.seq == seq }) null else seq

/** L'en-tête: Conversations, les visages des membres, le nom de la salle, et le menu « … ». */
@Composable
private fun RoomHeader(room: GroupRoom, face: (String) -> String?, onBack: () -> Unit, onMenu: () -> Unit) {
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
        RoomFaceStack(members = room.members, face = face, size = 22.dp)
        BasicText(
            text = room.name,
            localize = false,
            style = theme.headline.copy(color = theme.textColor),
            maxLines = 1,
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
    }
}

/** Les visages de quelques membres qui se chevauchent (`FaceStack`), chacun sur le fond de la page. */
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

/** L'état vide: les visages des membres, leurs noms joints, et la phrase d'aide. */
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

/** Les noms des membres joints comme `formatted(.list(type: .and))`. */
internal fun roomMembersLabel(labels: List<String>): String {
    return when (labels.size) {
        0 -> ""
        1 -> labels[0]
        2 -> "${labels[0]} ${OnyxLocalization["and"]} ${labels[1]}"
        // Trois et plus: la queue dit la langue — « , and » en anglais (la virgule d'Oxford),
        // « et » en français, où la virgule ne la précède pas.
        else -> labels.dropLast(1).joinToString(", ") + OnyxLocalization[", and"] + " " + labels.last()
    }
}

/** Un événement du journal: ce que la personne a dit, la réponse d'un bot, ou une note de tour. */
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
 * Une réponse d'un bot: son visage, son nom, puis ce qu'il a dit — **à plat**, sous le visage,
 * comme le `MessageRow` quand le thème imprime les réponses sur la page
 * (`palette.flatReplies`): le texte est au bord du fil, pas en retrait sous le nom, et la ligne
 * du nom est à 8 pt de ce qu'elle annonce.
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
    /** La voix de ce bot sort à cet instant: sa ligne se distingue des autres. */
    speaking: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotLook(
                name = profile.ifEmpty { "bot" },
                faceCode = faceCode,
                size = 20.dp,
                mood = if (live) BotMood.THINKING else BotMood.PLEASED,
            )
            if (live) LiveMark(color = theme.accent)
            // Ce qui est lu maintenant: un haut-parleur de la couleur de l'accent, et le nom
            // dans cette couleur — la réponse dont la voix sort se voit dans le fil.
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

/** Une réponse en train d'être écrite (`GroupDraft`), dessinée comme la réponse vivante du chat. */
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
 * Ce que la personne a dit, comme le chat dessine un de ses messages, avec les fichiers qu'il
 * nomme par leurs chemins sur le serveur, ou leurs noms tant qu'il est en route (`waiting`).
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

/** Ce que la salle attend: les bots parlent, elle attend la personne, elle s'arrête, ou se remet. */
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

/** Une réponse arrêtée à mi-chemin: Hermes ne sait pas jusqu'où elle est allée, on la relance si on veut. */
@Composable
private fun RetryCard(task: String, who: String, onRetry: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.warning.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
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
 * En place du composer, pour une salle qu'Hermes Desktop tient: c'est Desktop qui répond à ses
 * messages, et il n'en partage qu'une copie des derniers, alors elle se lit ici et se parle là-bas.
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

/** La recherche dans la salle (`Find in Chat`): les mots, le compte, et les flèches. */
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
