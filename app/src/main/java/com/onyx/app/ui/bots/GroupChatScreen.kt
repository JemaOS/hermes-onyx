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
 * Le chat de groupe.
 *
 * Port de `GroupChatView` : la rangée des bots (on choisit qui répond), le fil où chaque réponse
 * porte le visage et le nom de son bot, et le composer. « Read aloud » lit chaque réponse.
 *
 * Ce fichier réunit aussi le port de `GroupVoice` (les contrôles vocaux du groupe), le bandeau de
 * la salle, l'indicateur de tour par bot et le menu d'un bot. Le ViewModel (`GroupChatViewModel`)
 * n'est pas touché : tout état qui n'y vit pas (la dicée, le menu) est tenu ici par `remember`.
 */
@Composable
fun GroupChatScreen(
    viewModel: GroupChatViewModel,
    server: ServerConfig,
    onBack: () -> Unit,
    /** Le crayon du groupe : une **nouvelle conversation de ce groupe** (mêmes membres). */
    onNewConversation: (() -> Unit)? = null,
    /** Le bouton Talk du `GroupComposer` ouvre la scène vocale de la salle. */
    onOpenVoice: () -> Unit,
    /** Langue de la dictée dans le champ, distincte de la scène mains-libres. */
    speechLocale: String = SpeechEngine.DEFAULT_LOCALE,
    /** `Switch to typing` : rendre le focus et le clavier au composer une fois de retour. */
    focusComposer: Boolean = false,
    onComposerFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Le titre de la salle. À défaut, les bots choisis le donnent (`formatted(.list(type: .and))`). */
    title: String? = null,
    /** Appui long sur un bot : ouvrir son éditeur. `null` cache l'entrée du menu. */
    onEditBot: ((BotProfile) -> Unit)? = null,
    /** « Chat text size » : l'échelle du fil, comme dans le chat solo. */
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

    // Les bots choisis, dans l'ordre du roster : le bandeau et l'état vide les nomment.
    val chosenBots = remember(state.bots, state.selected) {
        state.bots.filter { it.name in state.selected }
    }

    // Le tour en cours : le dernier message de la personne l'ouvre et ce qui suit, bot par bot,
    // dit qui répond encore, qui a fini, et quel bot écrit à cet instant.
    val turn = remember(state.messages) { groupTurn(state.messages) }

    // Le fil d'une salle suit la fin **tant que la personne ne remonte pas**, comme le fil solo :
    // la décision vit dans `ScrollFollow`. Un doigt sur la liste détache le suivi tout de suite ;
    // au repos, revenir dans la marge d'ancrage le ré-attache ; le contenu qui grandit ne bouge la
    // vue que si on suit. (Avant, `LaunchedEffect(lastIndex, lastLength)` *animait* vers le bas à
    // chaque morceau de texte reçu : on ne pouvait pas remonter lire pendant que les bots
    // travaillaient.)
    //
    // La mesure du bas est celle du fil solo, à la réserve près : ici la liste pose
    // `PaddingValues(vertical = 12.dp)` — c'est cette valeur, et non le bord du viewport, qui est
    // le vrai bas du fil. `ScrollFollow` porte la règle ; l'écran ne fait que la brancher.
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
    // Un **état** : le `snapshotFlow` plus bas ne suit que les lectures d'état (voir `ChatScreen`,
    // même règle et même raison).
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
    // Une question part d'où qu'elle soit écrite : elle ramène en bas (`bot == null` est la
    // personne, comme `GroupMessageRow`).
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
            // La zone du système d'abord, puis l'espace visuel de la maison : la barre
            // d'écriture de la salle ne peut plus être enterrée sous la barre de navigation,
            // ni paraître collée au bord.
            .navigationBarsPadding()
            .padding(bottom = SafeBarSpacing.GapDp.dp),
    ) {
        // `GroupChatView` : liste, en-tête de salle (visages + nom), fil, puis GroupComposer.
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
                // L'échelle de texte des Réglages est posée à la **racine** — l'activité la lit
                // (`LocalOnyxTextScale`), le thème l'applique à tout l'arbre. `theme` vaut donc
                // **déjà** l'échelle choisie : la reposer ici avec `chatTextScale` l'appliquerait
                // une seconde fois (1,23 × 1,23 = 1,51).
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

    // L'appui long sur un bot n'ouvre aucun menu : `GroupChatView` de la référence n'en a pas pour ses bots
    // (seule la salle a le sien — Find in Chat, Rename, Mute, End Group Chat, encore non porté).
    // Le menu « Edit bot… » / « Open its chat » qui vivait ici ne pouvait pas s'ouvrir : son état
    // n'était jamais posé, et ses deux rappels n'étaient fournis par personne.
}

/** En-tête de `GroupChatView` : Conversations, FaceStack, le nom de la salle, et le crayon. */
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
            // Le crayon, comme dans un fil de bot : une conversation de **ce** groupe, mêmes
            // membres, même salle.
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
 * De combien de pixels on demande à dépasser la dernière ligne pour toucher le vrai bas du fil
 * (voir `ChatScreen` : `scrollToItem(index, offset)` borne l'offset au bas du contenu).
 */
private const val BottomScrollOffset = 100_000

/**
 * **La réserve que le fil d'une salle pose sous sa dernière ligne**, en dp : c'est le `vertical`
 * du rembourrage de la liste. Elle est nommée parce qu'elle est aussi le **vrai bas** du fil pour
 * la mesure du suivi ([ScrollFollow.distanceToBottom]) : sans ce terme, le rembourrage se
 * comptait comme du fil remonté, et le suivi se ré-attachait alors qu'on lisait deux lignes plus
 * haut.
 */
private val GroupThreadBottomPadding = 12.dp

/** Ce qu'un tour du fil dit d'un bot : rien, il écrit, ou il a fini. */
private enum class TurnMark { NONE, WORKING, DONE }

/** L'état du tour en cours : qui écrit, combien répondent, et le numéro du tour. */
private data class GroupTurn(
    /** L'état de chaque bot pendant ce tour, par nom de profil. */
    val marks: Map<String, TurnMark>,
    /** Combien de bots répondent à cet instant. */
    val answering: Int,
    /** Le nombre de tours : un par message de la personne (`Turn N`). */
    val turns: Int,
    /** Le bot qui écrit maintenant, s'il y en a un. */
    val writing: BotProfile?,
)

/**
 * Lit le fil pour le tour en cours : depuis le dernier message de la personne, chaque bot est
 * « en train d'écrire » si une de ses réponses coule, « fini » s'il a au moins une réponse posée,
 * et « rien » sinon. Le numéro du tour est le nombre de messages de la personne.
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
 * Le bandeau de la salle : les visages des bots choisis, le titre, le nombre de bots choisis et le
 * numéro du tour, puis l'état du tour (« N of M answering ») et la ligne du bot qui écrit.
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
        // « N of M answering » : l'état du tour, tant qu'il court.
        if (asking || answering > 0) {
            TurnStatusLine(text = "$answering of $chosen answering")
        }
        // La ligne du bot qui écrit, comme la réponse vivante que `GroupChatView` dessine.
        if (writing != null) {
            TurnStatusLine(text = "${writing.label()} · Replying…")
        }
    }
}

/** Une ligne d'état du tour : le point vivant, puis le texte. */
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

/** Les visages de quelques bots qui se chevauchent (`FaceStack`), chacun sur le fond de la page. */
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
 * La rangée des bots : chacun s'allume ou s'éteint d'un tap, et porte l'indicateur de son tour —
 * un point vivant pendant qu'il répond, une coche discrète quand il a fini.
 *
 * Un tap, rien de plus : `GroupChatView` de la référence ne donne pas de menu à un bot de la salle (celui
 * de la salle — Find in Chat, Rename, Mute, End Group Chat — porte sur la salle).
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
                // L'indicateur de tour du bot, à la suite de son nom.
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
 * L'état vide du fil : les visages des bots, « Ask them together. », la phrase d'aide de la référence et
 * le rappel des mentions `@` du groupe.
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
                // Le rappel de la mention : les poignées `@` du groupe, prêtes à taper.
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
 * Les contrôles vocaux du groupe (port de `GroupVoice`) : « Talk to the group » ouvre le micro
 * (symbole waveform), l'état « Listening » le dit, et « Stop reading » coupe la lecture en cours
 * (le `halt` de la référence : la lecture, l'écoute, et la ronde si elle court).
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
        // « Talk to the group » : le même genre de bouton rond que le composer, au symbole waveform.
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
        // Fermer le micro (« Stop » de `GroupVoice`), sans couper la lecture.
        if (listening) {
            ComposerCircleButton(
                symbol = Symbols.Xmark,
                label = "Stop listening",
                tint = theme.error,
                size = 42.dp,
                onClick = onStopListening,
            )
        }
        // « Stop reading » : coupe la lecture en cours, comme le bouton du haut-parleur de la référence.
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
        // La question et ses fichiers, à droite ; les notes techniques restent hors de la bulle.
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
            // Le bot écrit : la ligne « Replying… » de la référence, sous son visage.
            BasicText(
                text = "Replying…",
                style = theme.body.copy(color = theme.tertiaryText),
                modifier = Modifier.padding(start = 26.dp),
            )
        }
    }
}

/**
 * Les noms joints comme `formatted(.list(type: .and))` : « A », « A and B », « A, B and C ».
 * Ils donnent le titre par défaut d'une salle dont le nom n'a pas été fourni.
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
