package com.onyx.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.MenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.PrefillProgress
import com.onyx.app.data.model.serve.BotMentions
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.components.FollowUpChips
import com.onyx.app.ui.chat.components.MentionSuggestions
import com.onyx.app.ui.chat.components.MessageRow
import com.onyx.app.ui.common.ToastHost
import com.onyx.app.ui.common.messageArrival
import com.onyx.app.ui.chat.components.OnyxComposer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.onyx.app.voice.SpeechEngine
import com.onyx.app.ui.bots.dictationJoined
import com.onyx.app.ui.chat.components.OnyxTopBar
import com.onyx.app.ui.chat.components.threadHeaderSpeaker
import com.onyx.app.ui.chat.components.chatHeaderTitle
import com.onyx.app.ui.chat.components.StartCard
import com.onyx.app.ui.chat.components.StartScreen
import com.onyx.app.ui.chat.components.clipboardImages
import com.onyx.app.ui.design.GlassPill
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.ambientLoop
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.swipeRight
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch

/**
 * L'écran de conversation.
 *
 * La barre de titre, le fil et le composer sont tous faits à la main — `OnyxTopBar`,
 * `MessageRow`, `OnyxComposer` — pour que rien de Material ne soit reconnaissable à l'écran,
 * comme l'exige la cohérence avec l'app.
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    startCards: List<StartCard>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    /** Une note de pilotage écrite pendant une réponse: elle rejoint le tour en cours. */
    onSteer: (String) -> Unit = { _ -> },
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversations: (() -> Unit)?,
    onPickModel: () -> Unit,
    /** Un `/model` nu: même ouverture du sélecteur que par l'en-tête, mais il faut aussi baisser
     * la demande (l'écran peut être recréé). Par défaut, le même appel que l'en-tête. */
    onModelCommand: () -> Unit = onPickModel,
    onOpenSettings: () -> Unit,
    onOpenVoice: () -> Unit,
    onReadAloud: ((com.onyx.app.data.model.ChatMessage) -> Unit)? = null,
    readingMessageId: String? = null,
    onExpandDraft: () -> Unit,
    onOpenCard: (StartCard) -> Unit,
    onAddAttachments: (List<android.net.Uri>) -> Unit,
    onApproval: (messageId: String, approvalId: String, choice: String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenDelivered: (com.onyx.app.data.model.serve.DeliveredFile) -> Unit,
    menuItems: List<MenuItem> = emptyList(),
    // « Find in conversation »: un champ, le compte des résultats et les flèches qui passent de
    // l'un à l'autre (`TranscriptView.searchBar`) — pas de liste de résultats à ouvrir, 
    // n'en a pas. Les deux paramètres qui l'annonçaient (`searchMatches`, `onOpenSearchResult`)
    // n'étaient ni lus ni fournis: retirés plutôt que de dessiner un contrôle sans modèle.
    onSearch: (String) -> Unit = {},
    onFindRequested: () -> Unit = {},
    onStepSearch: (Int) -> Unit = {},
    onOpenSettingsFromError: () -> Unit = {},
    onOpenPatch: (String) -> Unit = {},
    onSelectText: (String) -> Unit = {},
    onOpenPhotos: (List<com.onyx.app.data.model.Attachment>, Int) -> Unit = { _, _ -> },
    /** Un fichier joint déjà envoyé, ouvert au tap (le PDF dans le lecteur de l'app). */
    onOpenFile: (com.onyx.app.data.model.Attachment) -> Unit = {},
    /** Une pièce jointe encore dans le champ, ouverte au tap. */
    onOpenPending: (com.onyx.app.ui.chat.PendingAttachment) -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onOpenSubagent: (com.onyx.app.data.model.SubagentActivity) -> Unit = {},
    /** Ouvre l'écran des tâches lancées (sous-agents) depuis la conversation. */
    onOpenSubagents: () -> Unit = {},
    /**
     * Le roster de sous-agents à montrer, ou `null` pour celui de la conversation. La doublure de
     * QA (`onyx.subagentDemo`) le remplit; sans elle, le fil fait foi.
     */
    subagentAgents: List<com.onyx.app.data.model.SubagentActivity>? = null,
    onClarify: (messageId: String, answers: Map<String, String>) -> Unit = { _, _ -> },
    onSudo: (messageId: String, password: String) -> Unit = { _, _ -> },
    onSecret: (messageId: String, value: String) -> Unit = { _, _ -> },
    onSendQueued: (String) -> Unit = {},
    onEditQueued: (String) -> Unit = {},
    onDeleteQueued: (String) -> Unit = {},
    /** La commande « / » de [ChatUiState.pendingSlash] est exécutée: elle ne se rejoue pas. */
    onConsumeSlash: () -> Unit = {},
    returnSends: Boolean = true,
    /** Locale du micro pour la dictée dans le champ (`Réglages → Voix → Langue d'écoute`). */
    speechLocale: String = "",
    /**
     * Ce que l'en-tête et la zone de saisie appellent l'assistant: le nom des Réglages →
     * Apparence, ou le nom du serveur quand il est vide (`Settings.headerTitle` ).
     */
    assistantName: String = "",
    /**
     * L'échelle du texte de la conversation (`Settings.chatTextSize`), appliquée au fil seulement:
     * le composeur et les barres autour gardent la taille du système, comme le `chatTextSize` du
     * posé sur le `ScrollView` du fil.
     */
    chatTextScale: Float = 1f,
    /** Réglages → Apparence → « Aperçus des liens »: la fiche du premier lien sous un message. */
    linkPreviews: Boolean = false,
    /**
     * Le bot dont c'est la conversation ([botOfConversation]: le chat serveur du bot
     * — `BotProfile.chatId`, le `canonical_session` — sinon le titre dont `botChatToOpen` a nommé son
     * chat); `null` pour une conversation simple, où l'en-tête montre alors Onyx — son nom d'agent
     * et son visage de bulle.
     */
    threadBot: com.onyx.app.data.model.serve.BotProfile? = null,
    /**
     * Les bots qu'un mot `@` du brouillon peut nommer (`BotMentions.rosterForMentions`): la liste
     * au-dessus du champ, comme `ComposerView.mentionMenu` — le fil d'un bot comme celui de
     * l'agent principal, et jamais une salle de groupe, qui a la sienne (`GroupComposer`). Vide,
     * aucune liste ne s'ouvre.
     */
    mentionBots: List<com.onyx.app.data.model.serve.BotProfile> = emptyList(),
    /**
     * Le contrôle du volet (`sidebar.left`) quand le fil est le **détail** des deux panneaux et
     * que le volet est replié: il prend la place du bouton « Conversations » de la barre, et son
     * libellé d'accessibilité annonce « Déplier la liste ».
     */
    sidebarToggle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // « Shown in the header and the message box »: un seul nom, partout.
    val agentName = assistantName.ifBlank { state.server.displayName }
    // L'en-tête du fil est un ajout volontaire (l'app n'en a pas): le visage et le nom de qui
    // parle, en tête des messages — le bot de la conversation, sinon Onyx.
    val threadSpeaker = remember(threadBot, agentName) { threadHeaderSpeaker(threadBot, agentName) }
    val theme = LocalOnyxTheme.current
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var namingProject by remember { mutableStateOf(false) }
    var projectProblem by remember { mutableStateOf<String?>(null) }
    val projectDirectory = rememberChatProjectDirectory(state.server, state.serverSessionId)
    val scope = rememberCoroutineScope()
    // Une longue conversation ne rend que la dernière page; « N earlier messages » remonte.
    var visibleFrom by remember(state.messages.firstOrNull()?.id) { mutableIntStateOf(0) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // La hauteur du composeur (pièces jointes, bandeau « N messages en attente », champ): le
    // « Jump to latest » s'y accroche pour rester **au-dessus** de lui, jamais dessus.
    var composerHeightPx by remember { mutableIntStateOf(0) }
    /**
     * Le mot `@` qu'Échap a fait taire: la liste des bots reste fermée tant que le mot en cours
     * est celui-là, et revient dès qu'il change (une lettre de plus, un « @ » effacé puis retapé).
     */
    var mentionDismissed by remember { mutableStateOf<String?>(null) }

    // Les bots offerts pendant qu'un mot `@` s'écrit (`BotMentions.menu`): au plus six, ceux que
    // ce qui suit le « @ » commence. Le bot dont c'est la conversation ne se propose pas lui-même,
    // et la liste ne s'ouvre pas dans un brouillon de plusieurs lignes.
    val mentionToken = BotMentions.typing(state.input)
    val mentionSuggestions = BotMentions.menu(
        draft = state.input,
        roster = mentionBots,
        speaking = threadBot?.name,
        mainName = agentName,
        dismissed = mentionDismissed,
    )
    // Plus aucun mot en cours: ce qu'Échap avait fait taire est oublié, et la liste pourra rouvrir.
    LaunchedEffect(mentionToken) { if (mentionToken == null) mentionDismissed = null }

    // Raccourcis clavier: le clavier physique d'Android n'a aucune des `keyboardShortcut` de
    // , donc l'écran les reçoit lui-même. La racine a le focus, ce qui donne à ⌘↩ le même
    // sens qu'à l'écran: envoyer sans quitter le champ.
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
                hasMessages = state.messages.isNotEmpty(),
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
            // ⌘E exporte le fil en Markdown: le raccourci double l'entrée du menu « … ».
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

    // Le micro du champ est une dictée indépendante: ce qui est dit rejoint le brouillon, rien
    // n'est envoyé et l'écran vocal ne s'ouvre pas — même comportement que le composeur de groupe
    // et que `DictateButton`.
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
        speechEngine.start()
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

    val pageStart = remember(state.messages.size, visibleFrom) {
        maxOf(visibleFrom, state.messages.size - PAGE)
    }
    val hiddenCount = pageStart
    val latestItemIndex = (if (hiddenCount > 0) 1 else 0) +
        (state.messages.size - pageStart - 1).coerceAtLeast(0) + state.queued.size
    val lastIndex = state.messages.lastIndex
    val lastLength = state.messages.lastOrNull()?.let { it.text.length + it.reasoning.length + it.tools.size } ?: 0
    val currentHit = state.searchHits.getOrNull(state.searchCurrent)
    LaunchedEffect(currentHit) {
        val index = state.messages.indexOfFirst { it.id == currentHit }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    // Le fil suit la fin **tant que la personne ne remonte pas**: c'est elle qui décide. La
    // décision vit dans `ScrollFollow` (pure, testable sur la JVM); l'écran ne fait que la
    // brancher. Trois gestes, ceux du modèle:
    // 1. un doigt posé sur la liste détache le suivi **tout de suite**, même si la vue est encore
    // collée en bas;
    // 2. au repos, on reprend la suite si le bas est à l'écran;
    // 3. le contenu qui grandit (la réflexion, une ligne d'outil, « Utilisés: N outils », le
    // compteur de durée) ne va en bas que si le suivi est attaché.
    // Avant, le détachement attendait que la **position** quitte le bas: or chaque morceau de
    // texte reçu (`lastLength` change à chaque jeton) ramenait la vue en bas avant qu'on ait pu
    // s'en éloigner — l'effet se relançait et écrasait le geste, donc impossible de remonter lire
    // pendant que l'agent réfléchissait.
    val reachPx = with(LocalDensity.current) { ScrollFollow.Reach.dp.toPx() }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + reachPx
        }
    }
    var following by remember { mutableStateOf(true) }
    // 1. Un doigt prend la liste: la personne décide, on se détache sans attendre la position.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) following = ScrollFollow.afterTouch()
        }
    }
    // 2. Au repos: on reprend la suite si le bas est à l'écran; un geste en cours ne ré-attache
    // jamais (voir `ScrollFollow.atRest`).
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to atBottom }
            .collect { (scrolling, bottom) ->
                following = ScrollFollow.atRest(scrolling, bottom, following)
            }
    }
    // 3. Le contenu qui grandit ne va en bas que si on suit.
    LaunchedEffect(lastIndex, lastLength, state.isStreaming, state.queued.size, following) {
        if (following && state.messages.isNotEmpty())
            listState.scrollToItem(latestItemIndex, BottomScrollOffset)
    }
    // Une question part d'où qu'elle soit écrite: elle ramène en bas (`jumpToEnd` du modèle).
    var seenUserTurns by remember { mutableStateOf(0) }
    val userTurns = remember(state.messages) {
        state.messages.count { it.role == com.onyx.app.data.model.Role.USER }
    }
    LaunchedEffect(userTurns) {
        if (userTurns > seenUserTurns) {
            following = ScrollFollow.jumpToLatest()
            listState.scrollToItem(latestItemIndex, BottomScrollOffset)
        }
        seenUserTurns = userTurns
    }

    // « Jump to latest »: offert dès que le suivi est détaché et que la fin n'est plus à l'écran
    // (`!following && !atBottom`, comme le modèle) — pas seulement quand une autre ligne est en
    // tête: une longue réponse couvre plusieurs écrans, la fin peut être hors de vue alors qu'on
    // est en train de la lire.
    val offersJump = state.messages.isNotEmpty() && !following && !atBottom
    val contextUsage = state.messages.lastOrNull { it.usage?.contextUsed != null }?.usage

    val toaster = com.onyx.app.ui.common.rememberToaster()

    // Une image déposée sur le fil devient une pièce jointe (`ChatDropTarget`).
    val dropTarget = rememberDragAndDrop(onAddAttachments)

    // « /model » nu: on ouvre le sélecteur de modèle, comme le fait l'en-tête. L'effet se rejoue
    // une seule fois par demande; `onModelCommand` la baisse avant de naviguer.
    LaunchedEffect(state.modelPickerRequested) {
        if (state.modelPickerRequested) onModelCommand()
    }
    // La commande « / » que le modèle a laissée: exécutée une fois, puis consommée.
    LaunchedEffect(state.pendingSlash) {
        val slash = state.pendingSlash ?: return@LaunchedEffect
        when (slash) {
            SlashAction.OPEN_MODEL_PICKER -> onPickModel()
            SlashAction.NEW_CONVERSATION -> onNewConversation()
            SlashAction.FIND -> onFindRequested()
            SlashAction.SETTINGS -> onOpenSettings()
            SlashAction.VOICE -> onOpenVoice()
            SlashAction.HELP, SlashAction.GATEWAY -> Unit
        }
        onConsumeSlash()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor)
            // Un glissement franc vers la droite révèle la liste, n'importe où sur l'écran —
            // c'est le geste du `SidePanel` , qui part du
            // bord comme du reste de la surface. Le bouton et le geste disparaissent quand la
            // liste est déjà visible à gauche (deux panneaux).
            .then(
                if (onOpenConversations == null) {
                    Modifier
                } else {
                    Modifier.swipeRight(onChanged = {}, onEnded = { distance, _ ->
                        if (distance > 120f) onOpenConversations?.invoke()
                    })
                },
            )
            .focusRequester(rootFocus)
            .focusable()
            // Les raccourcis se lisent AVANT le champ de saisie: ⌘↩ envoie sans que le clavier
            // pose le focus sur le bouton. Même crochet que le Ctrl+V du composeur.
            .onPreviewKeyEvent { event ->
                // Échap ferme d'abord la liste des bots d'un mot `@` en cours: le mot reste écrit,
                // la liste se tait jusqu'à ce qu'il change (le curseur ne bouge pas).
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
        Column(modifier = Modifier.fillMaxSize()) {
            OnyxTopBar(
                // Le titre suit le nom donné à la conversation (`Conversation.name` , qui prime
                // sur le premier message); sinon la première question, comme avant.
                title = (state.conversationName?.takeIf { it.isNotBlank() }
                    ?: state.messages.firstOrNull { it.role == com.onyx.app.data.model.Role.USER }?.text)
                    ?.let(::chatHeaderTitle)
                    ?.takeIf { it.isNotBlank() }
                    ?: "New conversation",
                titleIsPlaceholder = state.conversationName.isNullOrBlank() &&
                    state.messages.none { it.role == com.onyx.app.data.model.Role.USER },
                conversationKey = state.messages.firstOrNull()?.id.orEmpty(),
                // La seconde ligne du titre, comme: le **handle** du bot
                // précédé de « @ » (une conversation simple garde le nom d'agent des Réglages),
                // puis le modèle. Sans le « @ » ni le modèle, la ligne répétait le titre — c'est
                // le doublon « writer / writer » vu sur le Magic V3.
                // La seconde ligne nomme **le modèle qui répond**, et rien d'autre: le nom du bot
                // est déjà au-dessus, et le « @handle » ne servait à rien ici (demande du
                // propriétaire). Le modèle vient du serveur quand il le dit, donc il suit un
                // changement de modèle en cours de conversation.
                subtitle = state.modelInUse
                    ?: state.server.effectiveModel
                    ?: OnyxLocalization["default model"],
                onOpenConversations = onOpenConversations,
                sidebarToggle = sidebarToggle,
                onPickModel = onPickModel,
                onNewConversation = onNewConversation,
                onMenu = { showMoreMenu = true },
                // La capsule des sous-agents n'apparaît que quand il y en a: elle dit combien
                // travaillent, en direct, et ouvre l'écran des tâches.
                subagentRuns = (subagentAgents ?: state.subagents).count { it.isLive },
                onOpenSubagents = if ((subagentAgents ?: state.subagents).isEmpty()) null else onOpenSubagents,
                contextUsed = contextUsage?.contextUsed,
                contextWindow = contextUsage?.contextMax ?: state.server.contextWindow.takeIf { it > 0 },
                // Le visage du bot de la conversation, fixe dans la barre (l'en-tête du fil a été
                // retiré): `threadSpeaker` dit qui parle, sans son nom écrit sous l'avatar. La
                // référence ne pose ce visage que pour le fil d'un bot (`conversation.bot`): une
                // conversation simple n'en porte aucun, la barre ne montre que son titre.
                avatarName = threadSpeaker.faceName.takeIf { threadSpeaker.botProfile != null },
                avatarFaceCode = threadSpeaker.faceCode.takeIf { threadSpeaker.botProfile != null },
            )

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
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    StartScreen(
                        title = threadSpeaker.label,
                        cards = startCards,
                        onOpenCard = onOpenCard,
                    )
                }
            } else {
                // « Chat text size »: tout le fil se met à l'échelle ensemble; le composeur et
                // les barres autour gardent la taille du système, comme `.chatTextSize` 
                // posé sur le fil seulement.
                CompositionLocalProvider(LocalOnyxTheme provides theme.withFontScale(chatTextScale)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (hiddenCount > 0) {
                        item(key = "earlier") {
                            EarlierRow(count = hiddenCount) {
                                visibleFrom = maxOf(0, pageStart - PAGE)
                            }
                        }
                    }

                    items(
                        count = state.messages.size - pageStart,
                        key = { offset -> state.messages[pageStart + offset].id },
                    ) { offset ->
                        val index = pageStart + offset
                        val message = state.messages[index]
                        val arrival = remember(message.id) {
                            val justSent = System.currentTimeMillis() - message.createdAt < 1_500
                            when {
                                !justSent -> com.onyx.app.ui.common.ArrivalKind.NONE
                                message.role == com.onyx.app.data.model.Role.USER -> com.onyx.app.ui.common.ArrivalKind.SENT
                                index == state.messages.lastIndex && state.isStreaming &&
                                    message.text.isEmpty() && message.reasoning.isEmpty() && message.tools.isEmpty() ->
                                    com.onyx.app.ui.common.ArrivalKind.WAITING_REPLY
                                else -> com.onyx.app.ui.common.ArrivalKind.NONE
                            }
                        }
                        val dayLabel = dayLabelFor(state.messages, index)
                        if (dayLabel != null) {
                            DaySeparator(label = dayLabel)
                        }
                        MessageRow(
                            modifier = Modifier.messageArrival(arrival, leading = theme.promptPrefix != null),
                            message = message,
                            replyName = threadSpeaker.label,
                            isLive = state.isStreaming && index == state.messages.lastIndex,
                            live = state.live,
                            linkPreviews = linkPreviews,
                            onRegenerate = if (!message.isUser && !state.isStreaming) {
                                { onRetry() }
                            } else {
                                null
                            },
                            onReadAloud = onReadAloud?.let { action -> { action(message) } },
                            isReading = readingMessageId == message.id,
                            onApproval = { approvalId, choice -> onApproval(message.id, approvalId, choice) },
                            onOpenDelivered = onOpenDelivered,
                            highlighted = message.id == state.searchHits.getOrNull(state.searchCurrent),
                            onToast = { text -> toaster.show(text) },
                            onOpenPatch = onOpenPatch,
                            onSelectText = onSelectText,
                            onOpenPhotos = onOpenPhotos,
                            onOpenFile = onOpenFile,
                            onOpenSubagent = onOpenSubagent,
                            onClarify = { answers -> onClarify(message.id, answers) },
                            onSudo = { password -> onSudo(message.id, password) },
                            onSecret = { value -> onSecret(message.id, value) },
                        )
                    }
                    items(
                        count = state.queued.size,
                        key = { index -> state.queued[index].rowID },
                    ) { index ->
                        val item = state.queued[index]
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
                }
            }

            if (state.slashItems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(theme.surfaceColor, RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
                ) {
                    state.slashItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onyxClickable { onInputChange(item.text) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OnyxIcon(Symbols.Terminal, tint = theme.accent, size = 14.dp)
                            BasicText(
                                text = item.display,
                                localize = false,
                                style = theme.body.copy(color = theme.textColor),
                                maxLines = 1,
                            )
                            if (item.detail.isNotEmpty()) {
                                BasicText(
                                    text = item.detail,
                                    localize = false,
                                    style = theme.caption.copy(color = theme.secondaryText),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                }
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

            // Les bots qu'un mot `@` en cours peut nommer, juste au-dessus du champ
            // (`ComposerView.mentionMenu`): toucher une ligne finit le mot dans le brouillon et
            // laisse le curseur après, prêt à écrire la suite.
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

            Box(modifier = Modifier.onSizeChanged { composerHeightPx = it.height }) {
                OnyxComposer(
                draft = state.input,
                onDraftChange = onInputChange,
                isStreaming = state.isStreaming,
                title = threadSpeaker.label,
                returnSends = returnSends,
                queued = state.queued.size,
                attachments = state.attachments,
                onPasteImages = { uris -> onAddAttachments(uris) },
                onRemoveAttachment = onRemoveAttachment,
                onOpenAttachment = onOpenPending,
                onSend = onSend,
                onStop = onStop,
                onTalk = onOpenVoice,
                dictating = dictating,
                onDictate = ::toggleDictation,
                problem = dictationProblem,
                onExpand = onExpandDraft,
                onAddAttachment = { showAddMenu = true },
                // Un clavier physique écrit dans le champ: ⌘L le met au focus, et pendant une
                // réponse le groupe propose « steer » ou « file », comme `ComposerView`.
                focusRequester = composerFocus,
                canSteer = state.canSteer,
                onSteer = { onSteer(state.input) },
            )
            }
        }

        val jumpOffset = with(LocalDensity.current) { 12.dp.roundToPx() }
        // Le composeur mesuré (bandeau « N messages en attente » compris): le bouton se pose juste
        // au-dessus, jamais sur le bandeau ni sur le champ. Et file non vide = les messages en
        // attente occupent le bas du fil: le bouton se retire, plutôt que de les recouvrir. La
        // liste des mentions `@` occupe la même place: le bouton s'efface devant elle.
        val composerHeight = with(LocalDensity.current) { composerHeightPx.toDp() }
        AnimatedVisibility(
            visible = offersJump && state.queued.isEmpty() && mentionSuggestions.isEmpty(),
            enter = slideInVertically(animationSpec = OnyxMotion.Sites.JumpToLatestOffset) { jumpOffset } +
                scaleIn(animationSpec = OnyxMotion.Sites.JumpToLatest, initialScale = 0.9f) +
                fadeIn(animationSpec = OnyxMotion.Sites.JumpToLatest),
            exit = slideOutVertically(animationSpec = OnyxMotion.Sites.JumpToLatestOffset) { jumpOffset } +
                scaleOut(animationSpec = OnyxMotion.Sites.JumpToLatest, targetScale = 0.9f) +
                fadeOut(animationSpec = OnyxMotion.Sites.JumpToLatest),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Une marge de chaque côté: le bouton ne touche plus le bord du panneau.
                .padding(start = 16.dp, end = 16.dp, bottom = composerHeight + 8.dp),
        ) {
            JumpToLatestPill(live = state.isStreaming, onClick = {
                // Re-attacher le suivi, puis coller au bas: le contenu qui continue de grandir
                // (la réponse s'écrit) reste suivi après l'appui.
                following = ScrollFollow.jumpToLatest()
                scope.launch { listState.scrollToItem(latestItemIndex, BottomScrollOffset) }
            })
        }

        ToastHost(toaster = toaster)

        if (showMoreMenu && !showProjectMenu) {
            val items = buildList {
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
            OnyxMenu(
                items = items,
                onDismiss = { showMoreMenu = false },
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
                    // Un retour du sous-menu rouvre le menu principal, comme le back Android.
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
            OnyxMenu(
                items = listOf(
                    MenuItem("Files", Symbols.Folder) {
                        showAddMenu = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                    MenuItem("Photo Library", Symbols.PhotoOnRectangle) {
                        showAddMenu = false
                        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ))
                    },
                    MenuItem("Camera", Symbols.Camera) {
                        showAddMenu = false
                        onOpenCamera()
                    },
                    MenuItem("Paste", Symbols.DocOnDoc) {
                        showAddMenu = false
                        onAddAttachments(clipboardImages(context))
                    },
                ),
                onDismiss = { showAddMenu = false },
            )
        }
    }
}

/** Un fichier de cache où l'appareil photo écrit, partagé par le FileProvider. */
private fun newCameraUri(context: android.content.Context): android.net.Uri? = runCatching {
    val folder = java.io.File(context.cacheDir, "photos").apply { mkdirs() }
    val file = java.io.File(folder, "photo-${System.currentTimeMillis()}.jpg")
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()

/** « Jump to latest », posé sur le bas du fil quand la fin n'est plus à l'écran. */
@Composable
private fun JumpToLatestPill(live: Boolean, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    GlassPill(modifier = Modifier.onyxClickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            OnyxIcon(Symbols.ArrowDown, tint = theme.accent, size = 12.dp)
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

/** `NewBelowDot`: le point de 8 pt pulse en `easeInOut` toutes les 0,6 s pendant le direct. */
@Composable
private fun NewBelowDot() {
    val theme = LocalOnyxTheme.current
    val transition = rememberInfiniteTransition(label = "newBelow")
    // `NewBelowDot`: `guard live, !reduceMotion else { dim = false; return }`
    // — le point reste plein sous Reduce Motion.
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

/** La progression du préremplissage, quand le serveur la remonte. */
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

/** Statut du gateway pendant le tour (réessai provider, indisponibilité): honnête au lieu d'un vide. */
@Composable
private fun ProviderStatusBar(status: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = "⏳ $status",
            localize = false,
            style = theme.footnote.copy(color = theme.secondaryText),
        )
    }
}

/**
 * La ligne de statut d'une note de pilotage (`Conversation.statusLine`): ce que le serveur a
 * répondu — « steer queued » ou « steer rejected ». Elle s'efface à la fin du tour.
 */
@Composable
private fun SteerStatusBar(status: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(Symbols.ArrowTurnDownRight, tint = theme.secondaryText, size = 12.dp)
        BasicText(
            text = status,
            localize = false,
            style = theme.footnote.copy(color = theme.secondaryText),
        )
    }
}

/** Le bandeau d'erreur, avec « Retry » quand le tour peut être rejoué. */
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

/** La recherche dans la conversation (`showSearch` de ContentView): le champ, et le compte. */
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
            .background(theme.surfaceColor)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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

/** Une page de fil: ce que la liste rend d'un coup (`TranscriptPage`). */
private const val PAGE = 30

/**
 * De combien de pixels on demande à dépasser la dernière ligne pour toucher le vrai bas du fil:
 * `scrollToItem(index, offset)` borne l'offset au bas du contenu, donc un grand dépassement colle
 * au bas — et non au haut de la dernière ligne, qui peut couvrir plusieurs écrans.
 */
private const val BottomScrollOffset = 100_000

/** « Today » / « Yesterday » / « Monday, 3 March », là où le jour change. */
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

/** « 12 earlier messages »: la page précédente, d'un tap. */
@Composable
private fun EarlierRow(count: Int, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = if (count == 1) "1 earlier message" else "$count earlier messages",
            style = theme.subheadline.copy(color = theme.accent),
            modifier = Modifier
                .background(theme.fill, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .onyxClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** Le libellé du jour au premier message d'une journée, ou `null`. */
private fun dayLabelFor(messages: List<com.onyx.app.data.model.ChatMessage>, index: Int): String? {
    val current = java.util.Calendar.getInstance().apply { timeInMillis = messages[index].createdAt }
    val today = java.util.Calendar.getInstance()

    fun sameDay(a: java.util.Calendar, b: java.util.Calendar): Boolean =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

    if (index > 0) {
        val previous = java.util.Calendar.getInstance().apply { timeInMillis = messages[index - 1].createdAt }
        if (sameDay(previous, current)) return null
    } else if (sameDay(today, current)) {
        return null
    }

    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(today, current) -> "Today"
        sameDay(yesterday, current) -> "Yesterday"
        else -> java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
            .format(java.util.Date(messages[index].createdAt))
    }
}

/** Ce que le fil accepte en dépôt: les images, comme le composeur. */
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
