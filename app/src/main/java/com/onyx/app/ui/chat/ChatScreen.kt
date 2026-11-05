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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.MenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.onyx.app.ui.design.onyxPage
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
import com.onyx.app.ui.design.ChatTabBar
import com.onyx.app.ui.design.OnyxBase
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.socleShadow
import androidx.compose.foundation.border
import com.onyx.app.ui.design.ComposerStacking
import com.onyx.app.ui.design.TabBarGeometry
import com.onyx.app.ui.design.LocalHazeState
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.composerBottomInsets
import com.onyx.app.ui.design.threadBottomReserve
import com.onyx.app.ui.design.threadFloatAnchor
import com.onyx.app.ui.design.TabSwipeEdgeGuards
import com.onyx.app.ui.design.botFaceColour
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.PlateGeometry
import com.onyx.app.ui.design.onyxLensPage
import com.onyx.app.ui.design.chatTabCount
import com.onyx.app.ui.design.chatTabIndexOf
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.liquidGlass
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import com.onyx.app.ui.design.glassWeldBehind
import com.onyx.app.ui.design.onyxHazeSource
import kotlinx.coroutines.launch

/**
 * L'écran de conversation.
 *
 * La barre de titre, le fil et le composer sont tous faits à la main — `OnyxTopBar`,
 * `MessageRow`, `OnyxComposer` — pour que rien de Material ne soit reconnaissable à l'écran,
 * comme l'exige la parité avec l'app la référence.
 */
@Composable
fun ChatScreen(
    /**
     * L'etat du chat, **tenu par reference**.
     *
     * L'ecran le lisait par valeur : chaque publication de texte (33 par seconde pendant une
     * reponse) remplacait l'etat, donc le corps de l'ecran - 2 200 lignes - etait rejoue a
     * chaque jeton. Il le recoit desormais **par reference** et n'en projette que la partie
     * stable ([ChatScreenState]) : le texte vivant n'est lu que par la liste et la ligne qui
     * s'ecrit.
     */
    stateFlow: androidx.compose.runtime.State<ChatUiState>,
    startCards: List<StartCard>,
    onInputChange: (String) -> Unit,
    /**
     * Une ligne du menu « / » a été touchée : le texte entre dans le brouillon **et le menu se
     * referme** ([ChatViewModel.pickSlash]). Par défaut, le même appel qu'une frappe — un écran
     * qui n'a pas de menu ne change donc rien.
     */
    onPickSlash: (String) -> Unit = onInputChange,
    onSend: () -> Unit,
    /** Une note de pilotage écrite pendant une réponse : elle rejoint le tour en cours. */
    onSteer: (String) -> Unit = { _ -> },
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversations: (() -> Unit)?,
    onPickModel: () -> Unit,
    /** Un `/model` nu : même ouverture du sélecteur que par l'en-tête, mais il faut aussi baisser
     *  la demande (l'écran peut être recréé). Par défaut, le même appel que l'en-tête. */
    onModelCommand: () -> Unit = onPickModel,
    /**
     * Ouvre la **feuille du sélecteur de modèle** (la fonction 2) depuis la **puce de la carte**.
     * `null` : la puce n'est pas dessinée — l'écran n'a pas de modèle à choisir.
     */
    onOpenModelSheet: (() -> Unit)? = null,
    /** Le niveau de réflexion courant : le second segment de la puce (« Modèle | Réflexion »). */
    reasoningLabel: String = "",
    onOpenSettings: () -> Unit,
    /**
     * **Le visage de la barre du bas est une porte, pas un onglet.**
     *
     * Le quatrième repère d'une conversation est *dessiné* comme le **visage du bot** ; l'appuyer
     * ouvre donc l'**identité de ce bot** — son visage, son nom, sa voix, ce qu'il fait, sa
     * personnalité, son modèle, ses outils, ses compétences —, c'est-à-dire exactement la page que
     * « Modifier » ouvre depuis le menu d'une tuile de bot ([com.onyx.app.ui.bots.BotEditorScreen]).
     * C'était le défaut signalé : l'apparence du repère avait changé, pas ce que l'appui déclenche.
     *
     * `null` : l'écran n'a pas d'identité à ouvrir ; le repère retombe alors sur son comportement
     * **sûr** — aucun défilement hors page —, au lieu d'avoir l'air d'un contrôle mort.
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
     * Un fichier de l'onglet **Fichiers** déjà sur le téléphone, ouvert par le lecteur de sa
     * famille. C'est ce que fait un appui sur une ligne : sans ce branchement, l'onglet aurait des
     * lignes sans effet — le défaut signalé.
     */
    onOpenLocalFile: (android.net.Uri, String, String) -> Unit,
    menuItems: List<MenuItem> = emptyList(),
    // « Find in conversation » : un champ, le compte des résultats et les flèches qui passent de
    // l'un à l'autre (`TranscriptView.searchBar`) — pas de liste de résultats à ouvrir, l'la référence
    // n'en a pas. Les deux paramètres qui l'annonçaient (`searchMatches`, `onOpenSearchResult`)
    // n'étaient ni lus ni fournis : retirés plutôt que de dessiner un contrôle sans modèle.
    onSearch: (String) -> Unit = {},
    onFindRequested: () -> Unit = {},
    onStepSearch: (Int) -> Unit = {},
    onOpenSettingsFromError: () -> Unit = {},
    onOpenPatch: (String) -> Unit = {},
    onSelectText: (String) -> Unit = {},
    /**
     * Restaurer la conversation **à** une réponse : tout ce qui suit est défait. La confirmation
     * est demandée avant de retirer quoi que ce soit.
     */
    onRestore: (com.onyx.app.data.model.ChatMessage) -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    /**
     * Brancher **à partir d'ici** : copier ce fil jusqu'à cette réponse dans une nouvelle
     * conversation. Comme la restauration, rien n'est copié avant la confirmation.
     */
    onBranch: (com.onyx.app.data.model.ChatMessage) -> Unit = {},
    onConfirmBranch: () -> Unit = {},
    onCancelBranch: () -> Unit = {},
    /** L'avis de restauration a été affiché : il ne se rejoue pas. */
    onConsumeRestoreNotice: () -> Unit = {},
    onOpenPhotos: (List<com.onyx.app.data.model.Attachment>, Int) -> Unit = { _, _ -> },
    /** Un fichier joint déjà envoyé, ouvert au tap (le PDF dans le lecteur de l'app). */
    onOpenFile: (com.onyx.app.data.model.Attachment) -> Unit = {},
    /** Une pièce jointe encore dans le champ, ouverte au tap. */
    onOpenPending: (com.onyx.app.ui.chat.PendingAttachment) -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onOpenSubagent: (com.onyx.app.data.model.SubagentActivity) -> Unit = {},
    /**
     * LOT organisation-fil-competences : arrête **un** sous-agent encore vivant
     * (`subagent.interrupt`). Le même point que l'écran des tâches ; sans lui, aucune carte ne
     * dessine de bouton d'arrêt.
     */
    onStopSubagent: (com.onyx.app.data.model.SubagentActivity) -> Unit = {},
    /** Ouvre l'écran des tâches lancées (sous-agents) depuis la conversation. */
    onOpenSubagents: () -> Unit = {},
    /**
     * Le roster de sous-agents à montrer, ou `null` pour celui de la conversation. La doublure de
     * QA (`onyx.subagentDemo`) le remplit ; sans elle, le fil fait foi.
     */
    subagentAgents: List<com.onyx.app.data.model.SubagentActivity>? = null,
    /**
     * **« Enseigner une tâche »** — la ligne de la feuille du « + », comme « Teach a task » de
     * la référence.
     *
     * Elle ne fait pas la démonstration ici : la capture a besoin de l'**ordinateur connecté** (les
     * gestes, l'écran, le bail côté serveur). La ligne ouvre donc cet écran et y lance la feuille
     * d'enregistrement — c'est le chemin le plus court pour la personne, qui ne devine plus qu'il
     * faut aller chercher « Show me once » dans un menu ailleurs.
     *
     * `null` : pas de ligne (serveur sans identifiants Dashboard, ou aperçu). Jamais un bouton mort.
     */
    onTeachTask: (() -> Unit)? = null,
    /**
     * Ouvre le panneau du navigateur de l'agent depuis le fil.
     *
     * C'est ce qui rend la **carte de prise en main** cliquable : l'agent demande, la carte tombe
     * dans la conversation, la personne appuie et le panneau s'ouvre sur la page déjà chargée.
     * `null` : pas de carte, plutôt qu'une carte sans issue.
     */
    onOpenBrowser: (() -> Unit)? = null,
    /**
     * Doublure de QA du menu « / » (`am start --ez onyx.slashDemo true`) : l'émulateur n'a pas de
     * passerelle, donc `complete.slash` ne rend rien et le menu ne peut être vu ni mesuré. Le
     * drapeau injecte une liste fixe qui **ressemble à la vraie** (des noms courts de l'app, des
     * noms de compétences du serveur et leurs détails longs), pour vérifier la hauteur des rangées,
     * le plafond et la pose au-dessus de la plaque. `null` : le vrai menu, toujours.
     */
    slashDemoItems: List<SlashItem>? = null,
    onClarify: (messageId: String, answers: Map<String, String>) -> Unit = { _, _ -> },
    onSudo: (messageId: String, password: String) -> Unit = { _, _ -> },
    onSecret: (messageId: String, value: String) -> Unit = { _, _ -> },
    onSendQueued: (String) -> Unit = {},
    onEditQueued: (String) -> Unit = {},
    onDeleteQueued: (String) -> Unit = {},
    /** La commande « / » de [ChatUiState.pendingSlash] est exécutée : elle ne se rejoue pas. */
    onConsumeSlash: () -> Unit = {},
    returnSends: Boolean = true,
    /**
     * Démarrage de secours : le lancement précédent est mort pendant qu'il restaurait son état.
     * L'onglet mémorisé est celui du plantage — on revient sur **Chat** au lieu de le rouvrir,
     * sinon la même image refusée par le moteur de rendu tue l'app au même endroit, en boucle.
     */
    startOnChatTab: Boolean = false,
    /** Locale du micro pour la dictée dans le champ (`Réglages → Voix → Langue d'écoute`). */
    speechLocale: String = "",
    /**
     * Ce que l'en-tête et la zone de saisie appellent l'assistant : le nom des Réglages →
     * Apparence, ou le nom du serveur quand il est vide (`Settings.headerTitle` d'la référence).
     */
    assistantName: String = "",
    /**
     * L'échelle du texte de la conversation (`Settings.chatTextSize`), appliquée au fil seulement :
     * le composeur et les barres autour gardent la taille du système, comme le `chatTextSize()` du
     * la référence posé sur le `ScrollView` du fil.
     */
    chatTextScale: Float = 1f,
    /** Réglages → Apparence → « Aperçus des liens » : la fiche du premier lien sous un message. */
    linkPreviews: Boolean = false,
    /**
     * Le bot dont c'est la conversation ([botOfConversation] : le chat serveur du bot
     * — `BotProfile.chatId`, le `canonical_session` — sinon le titre dont `botChatToOpen` a nommé son
     * chat) ; `null` pour une conversation simple, où l'en-tête montre alors Onyx — son nom d'agent
     * et son visage de bulle.
     */
    threadBot: com.onyx.app.data.model.serve.BotProfile? = null,
    /**
     * **Le bot de cette conversation n'est pas encore connu.**
     *
     * Vrai seulement entre la première frame et la réponse du roster, quand ni le roster ni la
     * mémoire de la conversation ne disent de qui il s'agit. L'en-tête n'affiche alors **ni visage
     * ni « @profil »** : mieux vaut un en-tête nu un instant qu'un en-tête faux — c'est ce qui
     * montrait le bleu d'Onyx puis sautait sur le vrai bot.
     */
    threadBotUnknown: Boolean = false,
    /**
     * Les bots qu'un mot `@` du brouillon peut nommer (`BotMentions.rosterForMentions`) : la liste
     * au-dessus du champ, comme `ComposerView.mentionMenu` — le fil d'un bot comme celui de
     * l'agent principal, et jamais une salle de groupe, qui a la sienne (`GroupComposer`). Vide,
     * aucune liste ne s'ouvre.
     */
    mentionBots: List<com.onyx.app.data.model.serve.BotProfile> = emptyList(),
    /**
     * Le contrôle du volet (`sidebar.left`) quand le fil est le **détail** des deux panneaux et
     * que le volet est replié : il prend la place du bouton « Conversations » de la barre, et son
     * libellé d'accessibilité annonce « Déplier la liste ».
     */
    sidebarToggle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // **Le corps de l'écran ne lit plus l'état vivant.** Voir [ChatScreenState] : il ne porte que
    // ce qui ne bouge pas d'un jeton à l'autre, si bien que la publication du texte (33 fois par
    // seconde) ne recompose plus que le fil, et non les 2 200 lignes de cet écran.
    val state by remember { derivedStateOf { ChatScreenState.of(stateFlow.value) } }
    // « Shown in the header and the message box » : un seul nom, partout.
    val agentName = assistantName.ifBlank { state.server.displayName }
    // L'en-tête du fil est un ajout volontaire (l'app la référence n'en a pas) : le visage et le nom de qui
    // parle, en tête des messages — le bot de la conversation, sinon Onyx.
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
    // Une longue conversation ne rend que la dernière page ; « N earlier messages » remonte.
    var visibleFrom by remember(state.firstMessageId) { mutableIntStateOf(0) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // La hauteur du composeur (pièces jointes, bandeau « N messages en attente », champ) : le
    // « Jump to latest » s'y accroche pour rester **au-dessus** de lui, jamais dessus.
    var composerHeightPx by remember { mutableIntStateOf(0) }
    /** La hauteur de la barre d'onglets : le « Aller au dernier message » se pose au-dessus d'elle. */
    var tabBarHeightPx by remember { mutableIntStateOf(0) }
    /**
     * La hauteur **mesurée** de la barre du haut. Le fil s'en sert pour passer dessous : c'est ce
     * qui donne au verre un contenu à échantillonner, au lieu de poser la vitre sur un aplat de page
     * (le cas que le lot précédent avait mesuré et écarté — ici, la mesure est refaite).
     */
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    /**
     * Le mot `@` qu'Échap a fait taire : la liste des bots reste fermée tant que le mot en cours
     * est celui-là, et revient dès qu'il change (une lettre de plus, un « @ » effacé puis retapé).
     */
    var mentionDismissed by remember { mutableStateOf<String?>(null) }

    // Les bots offerts pendant qu'un mot `@` s'écrit (`BotMentions.menu`) : au plus six, ceux que
    // ce qui suit le « @ » commence. Le bot dont c'est la conversation ne se propose pas lui-même,
    // et la liste ne s'ouvre pas dans un brouillon de plusieurs lignes.
    //
    // **Mémorisé par ses seules entrées.** L'écran se recompose à chaque jeton pendant qu'une
    // réponse s'écrit ; sans clé, le menu était redécoupé à ce rythme alors que ni le brouillon ni
    // le roster n'avaient bougé. Les clés sont exactement les arguments de `menu`.
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
    // Plus aucun mot en cours : ce qu'Échap avait fait taire est oublié, et la liste pourra rouvrir.
    LaunchedEffect(mentionToken) { if (mentionToken == null) mentionDismissed = null }

    // Raccourcis clavier : le clavier physique d'Android n'a aucune des `keyboardShortcut` de
    // la référence, donc l'écran les reçoit lui-même. La racine a le focus, ce qui donne à ⌘↩ le même
    // sens qu'à l'écran : envoyer sans quitter le champ.
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
            // ⌘E exporte le fil en Markdown : le raccourci double l'entrée du menu « … ».
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

    // Le micro du champ est une dictée indépendante : ce qui est dit rejoint le brouillon, rien
    // n'est envoyé et l'écran vocal ne s'ouvre pas — même comportement que le composeur de groupe
    // et que `DictateButton` d'la référence.
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
        maxOf(visibleFrom, state.messagesSize - PAGE)
    }
    val hiddenCount = pageStart
    val latestItemIndex = (if (hiddenCount > 0) 1 else 0) +
        (state.messagesSize - pageStart - 1).coerceAtLeast(0) + state.queuedSize
    // **On ouvre sur le dernier message.** La liste naît déjà posée sur la fin du fil, au lieu
    // d'apparaître en haut puis de sauter en bas au premier passage de l'effet de suivi. L'index
    // n'est lu qu'à la **première** composition (c'est un état initial) ; ensuite, c'est le suivi
    // qui décide où regarde la vue.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = latestItemIndex)
    // La longueur du texte vivant n'est lue **que** par l'observateur du suivi du bas : elle
    // change a chaque publication, et la lire ici recomposerait l'ecran a chaque jeton.
    val lastIndex = state.messagesSize - 1
    val currentHit = state.searchHits.getOrNull(state.searchCurrent)
    LaunchedEffect(currentHit) {
        val index = stateFlow.value.messages.indexOfFirst { it.id == currentHit }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    // Le fil suit la fin **tant que la personne ne remonte pas** : c'est elle qui décide. La
    // décision vit dans `ScrollFollow` (pure, testable sur la JVM) ; l'écran ne fait que la
    // brancher. Trois gestes, ceux de la référence :
    //  1. un doigt posé sur la liste détache le suivi **tout de suite**, même si la vue est encore
    //     collée en bas ;
    //  2. au repos, on reprend la suite si la fin du fil est à l'écran **dans la marge d'ancrage**
    //     (`ScrollFollow.AnchorMarginDp` : une hauteur de rangée) ;
    //  3. le contenu qui grandit (la réflexion, une ligne d'outil, « Utilisés : N outils », le
    //     compteur de durée, le texte qui s'écrit) ne va en bas que si le suivi est attaché.
    //
    // **La mesure du bas, refaite pour ce lot.** La distance se compte de la fin de la dernière
    // ligne au bord du viewport **moins la réserve que la liste pose sous elle**
    // (`afterContentPadding` : compositeur, barre d'onglets, encart du système, écart de la
    // maison). Sans ce terme, la réserve entrait dans la marge : 330 dp mesurés à l'appareil au
    // lieu d'une rangée, donc un tiers d'écran remonté retombait au bas dès la première ligne
    // reçue — « quand je défile doucement pour tout lire, ça force au dernier message ».
    val anchorMarginPx = with(LocalDensity.current) { ScrollFollow.AnchorMarginDp.dp.toPx() }
    val distanceToBottomPx by remember(anchorMarginPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf 0
            // La dernière ligne n'est pas dans la vue : on est forcément remonté.
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
    // **Un état, pas une valeur recalculée** : le `snapshotFlow` plus bas ne suit que les lectures
    // d'état. Une valeur calculée dans la composition y restait **figée** à la première passe — le
    // suivi se ré-attachait donc à chaque relâchement, et la vue retombait au bas. C'est ce que la
    // trace de `logcat` a montré (« suivi=attaché … dans la marge=false »), et c'est la raison
    // d'être de cette ligne.
    val withinAnchor by remember(anchorMarginPx) {
        derivedStateOf { ScrollFollow.withinAnchor(distanceToBottomPx, anchorMarginPx.toInt()) }
    }
    var following by remember { mutableStateOf(true) }
    // La **trace de mesure**, une ligne par changement d'état : une décision de géométrie se lit
    // dans `logcat` sous son étiquette, sinon elle n'est vérifiable que par ses effets (même
    // principe que `OnyxThreadReserve`, pour la réserve du bas).
    LaunchedEffect(withinAnchor, following) {
        println(
            "$ScrollFollowLogTag suivi=${if (following) "attaché" else "détaché"} " +
                "distance au bas=${distanceToBottomPx}px marge=${anchorMarginPx.toInt()}px " +
                "dans la marge=$withinAnchor " +
                // La géométrie qui décide de la visibilité du contenu neuf : la hauteur du fil et
                // le rembourrage que la liste pose sous sa dernière ligne. Un rembourrage plus
                // grand que le fil vide l'écran au bas du défilement — cette trace le montre.
                "fil=${
                    listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                }px rembourrage bas=${listState.layoutInfo.afterContentPadding}px",
        )
    }
    // 1. Un doigt prend la liste : la personne décide, on se détache sans attendre la position.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) following = ScrollFollow.afterTouch()
        }
    }
    // 2. Au repos : on reprend la suite si la fin du fil est dans la marge d'ancrage ; un geste en
    //    cours ne ré-attache jamais (voir `ScrollFollow.atRest`).
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to withinAnchor }
            .collect { (scrolling, anchored) ->
                following = ScrollFollow.atRest(scrolling, anchored, following)
            }
    }
    // 3. Le contenu qui grandit — ET **chaque transition d'état du tour** (attente, début de
    //    réflexion, début de réponse, premier jeton) — ne va en bas que si on suit. L'étape du tour
    //    (`state.live.step`) est une clé à part entière : un changement d'étape qui ne change pas
    //    encore le texte (le passage de l'attente à la réflexion, le premier jeton d'un raisonnement
    //    vide) doit lui aussi ramener la vue, sinon l'état neuf se peint hors de la vue.
    //
    //    **Un seul observateur pour tout le tour, au lieu d'un effet relancé par jeton.**
    //
    //    Les clés de l'effet portaient `lastLength`, la longueur du texte vivant : elle change à
    //    **chaque** publication (trente-trois par seconde), donc l'effet était annulé puis relancé
    //    trente-trois fois par seconde, et chacun posait un `scrollToItem` — une coroutine suspendue
    //    qui redemande une mesure complète de la liste.
    //
    //    `snapshotFlow` observe la même chose sans relancer personne, et `requestScrollToItem` pose
    //    la demande **pour la prochaine passe de mesure** : deux jetons arrivés dans la même trame ne
    //    coûtent plus qu'un déplacement, et la composition n'attend plus un `suspend`.
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
    // Une question part d'où qu'elle soit écrite : elle ramène en bas (`jumpToEnd` de la référence).
    var seenUserTurns by remember { mutableStateOf(0) }
    // La clé est le **nombre** de messages : pendant qu'une réponse s'écrit, la liste change à chaque
    // jeton mais sa taille ne bouge pas — compter les tours à ce rythme ne servait à rien.
    val userTurns = state.userTurnCount
    LaunchedEffect(userTurns) {
        if (userTurns > seenUserTurns) {
            following = ScrollFollow.jumpToLatest()
            listState.scrollToItem(latestItemIndex, BottomScrollOffset)
        }
        seenUserTurns = userTurns
    }

    // « Jump to latest » : offert dès que le suivi est détaché et que la fin n'est plus à l'écran
    // (`!following && !withinAnchor`, comme la référence) — pas seulement quand une autre ligne est
    // en tête : une longue réponse couvre plusieurs écrans, la fin peut être hors de vue alors qu'on
    // est en train de la lire.
    val offersJump = state.messagesSize > 0 && !following && !withinAnchor
    val contextUsage = state.contextUsage

    // ---------------------------------------------------------------------------------------
    // LOT fil-vivant : l'état du tour, et la lecture
    // ---------------------------------------------------------------------------------------
    //
    // **L'état réel du tour** (`turnFlowState`) : repos, réflexion, travail, attente de décision.
    // C'est lui — et rien d'autre — que l'en-tête et les cartes de contexte peignent. Aucun état
    // n'est décoratif : chacun vient d'une étape que le serveur a fait connaître, ou d'une
    // approbation qui attend une réponse (`pendingApprovalCount`).
    val pendingApprovals = state.pendingApprovals
    // L'etape du tour suffit a peindre l'etat : `turnFlowState` ne lit que `isStreaming` et
    // `step`, jamais l'horodatage - qui, lui, change a chaque jeton.
    // Sans phase du tout (le serveur n'en a pas annonce), l'etat est le repos - c'etait deja la
    // regle de `turnFlowState` pour un `live` nul, et la reconstituer ici la garde identique.
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
    // **« On remonte lire. »** Le suivi du bas est détaché pendant qu'un tour tourne : c'est le
    // signal de la lecture. L'état **ralentit** alors (§ la période est multipliée, jamais
    // divisée) au lieu de continuer à pleine cadence sous les yeux de qui lit.
    val readingUp = state.isStreaming && !following

    val toaster = com.onyx.app.ui.common.rememberToaster()

    // L'avis d'une restauration s'annonce une fois, puis s'oublie : il dit ce qui a été défait, et
    // si la session du serveur a pu être rembobinée au même point.
    LaunchedEffect(state.restoreNotice) {
        state.restoreNotice?.let { notice ->
            toaster.show(notice)
            onConsumeRestoreNotice()
        }
    }

    // Une image déposée sur le fil devient une pièce jointe (`ChatDropTarget`).
    val dropTarget = rememberDragAndDrop(onAddAttachments)

    // — Les onglets de la conversation : Chat / Flux / Fichiers — (Idées et Objectifs supprimés)
    //
    // Le pageur vit **au-dessus** de la barre d'écriture : la barre ne bouge donc jamais quand on
    // change d'onglet, c'est elle qui porte les onglets en dessous. L'onglet choisi est gardé **par
    // conversation** : revenir dans une conversation rouvre le dernier onglet.
    val panelKey = state.conversationId ?: state.serverSessionId ?: "main"
    val panels = remember(context) { ChatPanelStore(context) }
    var feedFeedback by remember(panelKey) { mutableStateOf(panels.feedbackFor(panelKey)) }
    // **Plus d'état « barre masquée ».** La poignée à chevron qui le portait a été retirée de
    // l'écran (demande du propriétaire : « supprime le bouton pour la possibilité de cacher »).
    // La plaque du bas — le champ **et** la rangée d'onglets — est donc **toujours** visible. Il
    // n'existe plus d'écrivain de cet état, ni ici, ni dans `ChatPanelStore` (sa préférence
    // `tabsHidden` a disparu avec le bouton) : rien ne peut plus laisser la barre masquée après une
    // relance, parce que rien ne peut plus la masquer. L'état « Idées / Objectifs » a disparu avec
    // ces écrans (arbitrage de la réconciliation).
    // **Les deux rectangles de la soudure** : celui du compositeur et celui de la barre d'onglets,
    // mesurés dans la pile du bas. C'est de leur écart que naît le pont de matière (`GlassWeld`).
    var composerWeldRect by remember { mutableStateOf<Rect?>(null) }
    var tabBarWeldRect by remember { mutableStateOf<Rect?>(null) }
    val reduceMotion = LocalReduceMotion.current

    // ---- **Aucun effacement de la pile du bas, jamais.** ---------------------------------------
    //
    // La pile du bas — le compositeur **et** la rangée d'onglets — s'effaçait au défilement (un fondu
    // et un glissement court, déclenchés par une distance parcourue). Le propriétaire l'a refusé :
    // « ça ne doit JAMAIS disparaître quand je défile ». Toute la machinerie a donc été **retirée**,
    // pas désactivée : l'état replié (`stackVeiled`), la distance accumulée et son seuil
    // (`accumulate` / `ScrollSlopDp`), la décision (`stackVeiled`), la garde de frappe qui la
    // retenait (`typingHolds`), l'opacité qui en découlait (`stackAlpha`) et l'`AnimatedVisibility`
    // qui la peignait. Il ne reste **aucun** chemin — ni état, ni geste, ni clavier, ni frappe, ni
    // préférence — qui puisse changer la visibilité ou l'opacité de la plaque : elle est dessinée
    // **inconditionnellement**, à la même place à chaque position de défilement. Le verrou
    // `PlatePerpetualTest` échoue si l'un de ces chemins revient.
    val pagerState = rememberPagerState(
        initialPage = chatTabIndexOf(panels.selectedTab(panelKey)),
        pageCount = { chatTabCount },
    )
    // L'onglet sur lequel le pageur se **pose** est écrit tout de suite : un glissement court qui
    // revient en place ne change donc rien, et une sortie de la conversation retrouve son onglet.
    LaunchedEffect(pagerState, panelKey) {
        // Démarrage en secours : l'onglet restauré est celui où le dernier lancement est mort.
        // On le remet à Chat **avant** de lire l'état sauvé, sinon il revient aussitôt.
        if (startOnChatTab) {
            panels.setSelectedTab(panelKey, ChatTab.CHAT.key)
        }
        val saved = chatTabIndexOf(panels.selectedTab(panelKey))
        if (pagerState.settledPage != saved) pagerState.scrollToPage(saved)
        snapshotFlow { pagerState.settledPage }.collect { page ->
            panels.setSelectedTab(panelKey, ChatTab.entries[page.coerceIn(0, chatTabCount - 1)].key)
        }
    }
    // Le bord de l'écran appartient au geste de retour du système : le pageur ne démarre pas un
    // changement d'onglet depuis la bande que le système réserve (`systemGestures`, nulle en
    // navigation à trois boutons). En deux panneaux, le bord du panneau n'est pas un bord de
    // fenêtre : aucune lame n'y est posée.
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
    // L'onglet actif prend la couleur du bot de la conversation.
    val tabAccent = if (threadBotUnknown) theme.accent
        else threadBot?.let { botFaceColour(it.name, it.faceCode, theme) } ?: theme.accent
    // **Rien ne se recalcule à chaque jeton.** Une réponse en cours change à chaque token ; les
    // cartes du Flux et la liste des fichiers ne lisent que des messages **finis**, donc les
    // recalculer sur toute la conversation à ce rythme ne dessinait rien de neuf et coûtait du
    // temps à chaque image. La clé est la même tant que le dernier message fini ne change pas.
    val settledCount = state.settledCount
    val settledLast = state.settledLastId
    val feedCards = remember(settledCount, settledLast, threadSpeaker.label) {
        ChatFeed.cards(stateFlow.value.messages.take(settledCount), threadSpeaker.label)
    }
    val exchangedFiles = remember(settledCount, settledLast) {
        ChatFiles.conversationFiles(stateFlow.value.messages.take(settledCount))
    }
    val nowMillis = remember(panelKey) { System.currentTimeMillis() }
    // Ce qui a été vu du flux : la pastille de non-lu ne compte que le neuf.
    var seenCards by remember(panelKey) { mutableStateOf(panels.seenCards(panelKey)) }
    val feedBadge = feedCards.count { it.id !in seenCards }
    val tabBadges = List(chatTabCount) { index -> if (index == ChatTab.FEED.ordinal) feedBadge else 0 }
    // Ouvrir le flux le marque comme vu : la pastille s'éteint quand on le regarde.
    LaunchedEffect(pagerState, panelKey, feedCards) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page == ChatTab.FEED.ordinal) {
                seenCards = panels.markSeen(panelKey, feedCards.map { it.id })
            }
        }
    }
    // Trois déplacements réels, jamais une action muette : l'onglet, le champ, et le brouillon.
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
    /** « Discuter » : le sujet arrive dans le brouillon du fil, qui prend le focus. */
    fun discussAbout(title: String) {
        onInputChange(OnyxLocalization.format("About %1\$s: ", title))
        openChatTab()
    }

    // « /model » nu : on ouvre le sélecteur de modèle, comme le fait l'en-tête. L'effet se rejoue
    // une seule fois par demande ; `onModelCommand` la baisse avant de naviguer.
    LaunchedEffect(state.modelPickerRequested) {
        if (state.modelPickerRequested) onModelCommand()
    }
    // La commande « / » que le modèle a laissée : exécutée une fois, puis consommée.
    LaunchedEffect(state.pendingSlash) {
        val slash = state.pendingSlash ?: return@LaunchedEffect
        when (slash) {
            SlashAction.OPEN_MODEL_PICKER -> onPickModel()
            SlashAction.NEW_CONVERSATION -> onNewConversation()
            SlashAction.FIND -> onFindRequested()
            SlashAction.SETTINGS -> onOpenSettings()
            SlashAction.VOICE -> onOpenVoice()
            // Une compétence s'invoque par l'envoi lui-même : rien à faire à l'écran.
            SlashAction.INVOKE_SKILL, SlashAction.HELP, SlashAction.GATEWAY -> Unit
        }
        onConsumeSlash()
    }

    // Les deux hauteurs qui font passer le fil **sous** les vitres : celle de la barre du haut,
    // mesurée sur elle, et celle de la **pile du bas** — compositeur + rangée d'onglets + encart du
    // système + vide de la maison, calculée par une seule fonction
    // ([threadBottomReserve]). Aucune valeur en dur : un pliable déplié, une barre système à trois
    // boutons, un clavier ouvert ou une taille de texte système différente déplacent la réserve, et
    // le fil suit.
    //
    // **C'est la correction du défaut signalé** (« une partie de la conversation passe sous la barre
    // de saisie ») : la réserve ne valait que `compositeur + onglets`, sans l'encart du système —
    // 24 dp de zone de gestes, 48 dp à trois boutons —, si bien que la fin du fil se retrouvait
    // sous la carte, exactement de cet écart. La réserve est désormais **égale** à la pile, et
    // `ChatThreadReserveTest` échoue si elle redevient plus petite.
    val glassTopInset = with(LocalDensity.current) { topBarHeightPx.toDp() }
    val glassBottomInset = threadBottomReserve(
        composerPx = composerHeightPx,
        bottomRowPx = tabBarHeightPx,
    )

    // **La voie des commandes flottantes.**
    //
    // La **seule** commande qui flotte au-dessus de la plaque est désormais la pastille « aller au
    // dernier message » : la poignée à chevron qui partageait cette bande a été retirée de l'écran,
    // sur l'ordre du propriétaire. La bande reste **réservée** — le fil ne la traverse jamais, donc
    // la pastille ne peut pas se poser sur du texte, ce qui était le défaut relevé — mais elle ne
    // loge plus qu'une pastille : son dégagement plus sa hauteur ([PlateGeometry.FloatLane], 56 dp
    // au lieu des 68 que la cible et l'écart de la poignée réservaient). **Les 12 dp rendus au fil
    // sont mesurés**, et la pastille ne se pose toujours sur rien.
    val floatLane = PlateGeometry.FloatLane

    /**
     * L'espace dont le menu « / » dispose : **tout l'écran moins les deux vitres**.
     *
     * Le menu se pose juste au-dessus de la plaque (voir plus bas) ; sa hauteur au plus vaut donc
     * la bande qui reste entre la barre du haut et le compositeur. Elle est **mesurée** — jamais
     * une valeur en dur — pour qu'une police à 1,3×, un clavier ouvert ou un écran court fassent
     * **défiler** la liste au lieu de la faire passer sous la plaque, ce qui était le défaut
     * « ça passe derrière ».
     */
    val slashMenuMaxHeight = (
        LocalConfiguration.current.screenHeightDp.dp - glassTopInset - glassBottomInset -
            OnyxSpace.BlockGap - OnyxSpace.BlockGap
        )
        // **Le plafond d'un téléphone.** La mesure de l'écran reste le garde-fou (clavier ouvert,
        // écran court, police à 1,3×) ; [SlashMenu.MaxHeight] est la vraie limite — cinq rangées et
        // demie, de quoi lire la liste sans que le fil disparaisse sous elle.
        .coerceAtMost(com.onyx.app.ui.chat.components.MaxHeight)
        .coerceAtLeast(OnyxSpace.Base)


    // L'état Haze posé à la racine : c'est lui que la barre du haut, le compositeur et la barre
    // d'onglets échantillonnent. `null` hors de l'arbre qui l'a posé (aperçus, tests) — les vitres
    // retombent alors sur leur voile, jamais sur un trou.
    val glassState = LocalHazeState.current
    /** La page que la lentille échantillonne (Android 13+) — `null` ailleurs. */
    val lensPage = com.onyx.app.ui.design.LocalOnyxLensPage.current

    // Le retour ferme la **recherche du fil** avant de quitter la conversation : sur l'accueil (le
    // fil), le geste du bord fermait l'app alors qu'un bandeau ouvert attendait qu'on le referme —
    // un seul niveau à la fois, comme le volet, les feuilles et les menus. Il fait exactement ce
    // que fait la croix du bandeau (`onSearch("")` puis `onFindRequested()`), si bien que le bouton
    // retour maison et le geste du bord se comportent pareil.
    BackHandler(enabled = state.searchOpen) {
        onSearch("")
        onFindRequested()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // **LA MATIÈRE DE LA PAGE N'EST PLUS PEINTE ICI : elle est DANS la source Haze.**
            //
            // Mesuré au repos sur l'appareil (`sonde2.py`, APK 991) : la page au-dessus de la
            // plaque vaut `#D3DCD7` (clair) / `#181818` (sombre) — c'est la matière, qui teinte et
            // assombrit la couleur de page. La plaque, elle, valait `#FCFCFD` / `#707072`, un
            // **aplat neutre** qui ne portait **aucune** trace de la page (ni la teinte, ni le
            // grain du fond). La cause : la source Haze ne contenait que le **contenu du pageur** ;
            // la matière, peinte sur cette `Box` (donc **au-dessus** de la source dans l'arbre),
            // n'était pas échantillonnée, et Haze remplissait le vide avec `theme.pageColor` —
            // un blanc (clair) ou un graphite (sombre) qui n'est pas ce qu'on voit.
            //
            // La matière descend donc **dans** la source (voir juste plus bas), pour qu'elle soit
            // ce que la vitre échantillonne : la plaque montre alors la page qu'elle recouvre.
            // Un glissement franc vers la droite révèle la liste, n'importe où sur l'écran —
            // c'est le geste du `SidePanel` d'la référence (`Echo/Views/SwipeToOpen`), qui part du
            // bord comme du reste de la surface. Le bouton et le geste disparaissent quand la
            // liste est déjà visible à gauche (deux panneaux).
            .then(
                // Le geste du volet ne prend que l'onglet **Chat** : sur les autres onglets, un
                // glissement latéral sert à revenir aux onglets, pas à ouvrir la liste.
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
            // Les raccourcis se lisent AVANT le champ de saisie : ⌘↩ envoie sans que le clavier
            // pose le focus sur le bouton. Même crochet que le Ctrl+V du composeur.
            .onPreviewKeyEvent { event ->
                // Échap ferme d'abord la liste des bots d'un mot `@` en cours : le mot reste écrit,
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
        // **Le fil passe sous les vitres.** La barre du haut et la pile du bas sont posées
        // **par-dessus** les cinq onglets, au lieu d'être empilées à côté d'eux : c'est ce qui donne
        // au flou d'arrière-plan quelque chose à échantillonner. Empilées (l'ancienne colonne), les
        // barres se posaient sur un aplat de page — un flou d'aplat ne produit rien de visible et
        // coûte quand même un calque : c'est le raisonnement que `FrostSurface` documente, et on ne
        // le contredit pas — ici, il y a bien du contenu qui défile dessous.
        Box(modifier = Modifier.fillMaxSize()) {
            // Le contenu des cinq onglets, sous le doigt. Le pageur porte **le contenu seul** :
            // la barre d'écriture et la barre d'onglets vivent en dessous, hors du pageur, donc
            // changer d'onglet ne déplace jamais le champ de saisie.
            //
            // **Et il occupe toute la surface** : la barre du haut et la pile du bas sont posées
            // par-dessus lui. C'est aussi **la source Haze** — ce qui est dessous est exactement ce
            // que le flou échantillonne.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(glassState?.let { Modifier.onyxHazeSource(it) } ?: Modifier)
                    // **Et la page de la lentille** (Android 13+) : le même contenu, enregistré
                    // une fois pour toutes les surfaces qui courbent (voir `OnyxLensGlass`).
                    .then(lensPage?.let { page -> Modifier.onyxLensPage(page) } ?: Modifier),
            ) {
            // **LA MATIÈRE DE LA PAGE, DANS LA SOURCE HAZE.** Elle était sur la `Box` racine de
            // l'écran ; la source Haze, posée **sous** elle, ne la capturait donc pas, et la
            // plaque remplissait le vide d'une couleur de page supposée (mesuré : un aplat
            // `#FCFCFD`/`#707072`, indifférent à la page). Posée ici, elle est **ce que la vitre
            // échantillonne** : la plaque montre la page qu'elle recouvre, teinte et grain compris.
            Box(Modifier.matchParentSize().matierePage(theme))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
            when (page) {
            0 -> Column(modifier = Modifier.fillMaxSize()) {

            // Ce que la vitre du haut recouvre, ici : quand le fil est **seul**, il monte sous la
            // barre et le flou échantillonne du vrai contenu. Dès qu'un bandeau vit au-dessus de la
            // liste (diagnostic, recherche, pré-remplissage), c'est le bandeau qui garde sa place :
            // on ne floute pas un bandeau, et un bandeau à moitié caché n'est plus cliquable.
            val stacked = !state.server.isUsable || state.searchOpen || state.prefill != null ||
                state.providerStatus != null || state.steerStatus != null || state.error != null
            if (stacked || state.isEmpty) Spacer(Modifier.height(glassTopInset))

            // Aucun serveur configuré : l'app reste utilisable (la configuration a été passée),
            // mais le fil dit ce qui manque et où le poser.
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
                        // **L'accueil vide n'est plus centré : il est posé en haut.**
                        //
                        // Le défaut relevé : « l'écran d'accueil vide a d'énormes vides verticaux —
                        // rien ne structure la page ». Un bloc centré laisse **deux** bandes vides
                        // égales, dont aucune n'a de raison ; posé en haut, la bande du bas est
                        // celle où la conversation va venir, et le vide se lit comme une réserve.
                        // [EmptyHomeTopInset] est la marche `Loose` de l'échelle (24 dp) × 4, plus
                        // le dégagement de la barre du haut : le bloc ne colle pas la vitre.
                        .padding(top = EmptyHomeTopInset),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // **Aucune surface décorative autour de l'accueil vide.**
                    //
                    // `113ccd21` (« Verre visible ») a posé ici une « grande carte de verre »
                    // (`LiquidGlass(GlassSurface.MATTER_CARD, matchParentSize())`) pour « structurer »
                    // le vide ; la fusion `0d7fb13d` l'a gardée. Le propriétaire, sur `1.0.0+846` :
                    // « c'est quoi cet encadré dégueulasse quand je commence une nouvelle
                    // conversation, avant je ne l'avais pas ». Ce qu'il voit : un grand panneau gris
                    // arrondi autour du titre, de la question et de la carte « Continuer », **tranché
                    // net en bas** — la carte prend la place libre (`matchParentSize`) et le compositeur
                    // la coupe. C'était de la décoration : elle part, le contenu reste sur la page.
                    StartScreen(
                        title = threadSpeaker.label,
                        cards = startCards,
                        onOpenCard = onOpenCard,
                    )
                }
            } else {
                // L'échelle de texte des Réglages est posée à la **racine** — l'activité la lit
                // (`LocalOnyxTextScale`), le thème l'applique à tout l'arbre. `theme` vaut donc
                // **déjà** l'échelle choisie : la reposer ici avec `chatTextScale` l'appliquerait
                // une seconde fois (1,23 × 1,23 = 1,51).
                // **Le fil gèle le verre pendant le défilement, comme le volet le fait déjà.**
                //
                // Défaut relevé : « ça lag » sur une grosse conversation, sur un téléphone
                // puissant. Le gel n'était posé que par `ChatSidebar` — le fil, lui, laissait
                // `LocalGlassScrollFreeze` à faux, donc **chaque carte visible** continuait à
                // pulsar (`BlocConversationFace`) et chaque vitre gardait son échantillonnage
                // pleine qualité pendant que le doigt défilait. Un geste ne regarde pas une
                // lumière respirer : sous le doigt, on passe en mode performance.
                CompositionLocalProvider(
                    LocalOnyxTheme provides theme,
                ) {
                // **Le gel du verre est posé par une fonction à lui.** Lu ici, dans la portée de
                // l'écran, `listState.isScrollInProgress` invalidait toute la conversation — deux
                // fois par relance de geste, et deux fois par `scrollToItem`, c'est-à-dire deux
                // fois par jeton pendant qu'une réponse s'écrit. Voir [GlassScrollFreeze].
                GlassScrollFreeze(listState) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    // Le fil réserve les deux vitres **dans son défilement** : la première et la
                    // dernière bulle restent lisibles, et tout ce qui passe entre les deux défile
                    // sous le verre — c'est ce que le verre a à montrer.
                    contentPadding = PaddingValues(
                        start = OnyxSpace.ScreenMargin,
                        end = OnyxSpace.ScreenMargin,
                        top = if (stacked) OnyxSpace.BlockGap else glassTopInset + OnyxSpace.BlockGap,
                        bottom = glassBottomInset + floatLane + OnyxSpace.BlockGap,
                    ),
                    // L'écart entre deux messages : la marche `ThreadGap` (16), une seule fois pour
                    // tout le fil. C'était 14 dp — une valeur qui n'existe sur aucune échelle (2 × 7)
                    // et qui ne se retrouvait nulle part ailleurs dans l'app.
                    verticalArrangement = Arrangement.spacedBy(OnyxSpace.ThreadGap),
                ) {
                    if (hiddenCount > 0) {
                        item(key = "earlier") {
                            EarlierRow(count = hiddenCount) {
                                visibleFrom = maxOf(0, pageStart - PAGE)
                            }
                        }
                    }

                    // **Une liste qui change pendant que la liste la dessine.**
                    //
                    // `count` et la clé étaient calculés sur `state.messages` **deux fois** : si le
                    // fil change entre les deux (on ouvre une autre conversation, un tour remplace
                    // sa réponse, un « Annuler » retire un message), `pageStart + offset` sort de
                    // la liste et la `LazyColumn` jette `IndexOutOfBoundsException` — un plantage
                    // qui n'arrive qu'avec beaucoup de conversations, donc jamais en test.
                    items(
                        // La liste lit le fil **vivant** : `stateFlow` est un `State`, pas une
                        // valeur, donc c'est cette portee (la `LazyColumn` et ses lignes) qui
                        // recompose au jeton - pas les 2 200 lignes de l'ecran au-dessus.
                        count = (stateFlow.value.messages.size - pageStart).coerceAtLeast(0),
                        key = { offset ->
                            stateFlow.value.messages.getOrNull(pageStart + offset)?.id ?: "gone-$offset"
                        },
                        // **Un type de contenu par forme de rangée.** Sans lui, une ligne de
                        // question sortie de la page peut être « réutilisée » pour une réponse :
                        // la liste remesure et recompose alors une rangée d'une autre forme au lieu
                        // de reprendre la bonne.
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
                        // **La seule ligne qui lit l'état du tour est la ligne vivante.**
                        //
                        // `state.live` change à **chaque jeton** (il porte `lastEventAt`). Passé à
                        // toutes les rangées, il rendait leurs paramètres différents à chaque
                        // publication : aucune ne pouvait être sautée, et une réponse qui s'écrit
                        // recomposait donc **toutes** les lignes visibles — leurs replis, leurs
                        // cartes, leur prose — trente fois par seconde. Une rangée finie ne lit pas
                        // cet état (`WaitingPulse` n'existe que tant que le texte est vide, et
                        // seulement pour la ligne vivante) : elle reçoit `null`, l'égalité tient, et
                        // Compose la saute.
                        val isLive = state.isStreaming && index == stateFlow.value.messages.lastIndex
                        // **La pose d'arrivée ne s'applique qu'à une rangée qui arrive.**
                        // `messageArrival` est un `Modifier.composed` : monté sur chaque rangée, il
                        // ouvrait une sous-composition et trois animations pour une animation qui
                        // ne concerne, en pratique, que le message qu'on vient d'envoyer. Les
                        // rangées d'archive reçoivent `Modifier` nu.
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
                            // La bifurcation ouvre la rangée, comme dans la référence. Elle ne
                            // s'offre que sous une **réponse** finie : brancher sous une question
                            // n'a pas de point d'arrêt à copier.
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
                            // LOT fil-vivant : on remonte lire le fil → les puces d'état des cartes
                            // **ralentissent** au lieu de battre à pleine cadence sous les yeux.
                            //
                            // **Seules les cartes de la ligne vivante lisent ce signal.** Les cartes
                            // d'une réponse finie sont à l'arrêt : rien à ralentir chez elles. Passé
                            // à toutes, il changeait au **premier doigt posé** (`following` tombe à
                            // `false` dès `DragInteraction.Start`) et recomposait donc tout le fil au
                            // début de chaque geste — un à-coup au moment précis où le doigt part.
                            readingUp = isLive && readingUp,
                        )
                    }
                    items(
                        count = state.queuedSize,
                        key = { index -> stateFlow.value.queued.getOrNull(index)?.rowID ?: "queued-$index" },
                        contentType = { "queued" },
                    ) { index ->
                        // Même règle que le fil : l'outbox peut se vider pendant la composition.
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
                    } // ferme le contenu de la LazyColumn
                } // ferme GlassScrollFreeze (le gel du verre)
                } // ferme la CompositionLocalProvider du thème
            } // ferme la branche « fil » de `when (page)`
            // **Le menu « / » se pose AU-DESSUS de la plaque, jamais derrière elle.**
            //
            // Défaut relevé sur l'appareil : la liste était le dernier enfant du pane du fil,
            // donc collée au bas de l'écran — la plaque du compositeur la recouvrait, et ses
            // dernières lignes n'étaient ni lisibles ni cliquables (« ça passe derrière »).
            // Elle remonte maintenant d'exactement la réserve du fil : le menu commence là où la
            // plaque finit, et ne recouvre donc jamais le champ.
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
                        // **Le menu se pose SUR la plaque, pas au milieu de l'écran.**
                        //
                        // Il réservait `glassBottomInset + floatLane + BlockGap` : la voie des
                        // commandes flottantes (56 dp) s'ajoutait à la réserve du compositeur, qui
                        // la contient déjà — le menu flottait donc 68 dp au-dessus de sa plaque,
                        // détaché, dans une bande vide. Il ne garde que la réserve du compositeur
                        // plus un demi-pas : il touche presque sa plaque, comme un menu qui en
                        // sort.
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
                // Une conversation à un bot n'a **qu'un** auteur : celui du fil. Le résolveur le
                // nomme quand une carte le porte, et le repli garde son visage — la carte d'une
                // salle, elle, y met celui du bot qui a répondu.
                face = { name -> threadSpeaker.faceCode.takeIf { name == threadSpeaker.faceName } },
                fallbackFaceCode = threadSpeaker.faceCode,
                accent = tabAccent,
                nowMillis = nowMillis,
                onFeedback = { cardId, value ->
                    feedFeedback = panels.setFeedback(panelKey, cardId, value)
                },
                onDiscuss = { card -> discussAbout(card.title) },
                onCompose = { openChatTab() },
                // **LE CONTENU DE L'ONGLET REPOSE SUR LA PAGE — la plaque de verre a été RETIRÉE.**
                // Un lot précédent posait ici une plaque (`panePlate`) derrière le contenu ; le
                // propriétaire l'a refusée sur capture (« un encadré gris transparent, je veux pas
                // ça »). Le contenu est donc **directement sur la page**, sans encadré derrière lui.
                // **Le glissement, lui, reste** : c'est le `HorizontalPager` qui translate le
                // contenu sous le doigt — la plaque s'en va, le geste non.
                modifier = Modifier.fillMaxSize()
                    .padding(top = glassTopInset, bottom = glassBottomInset + floatLane),
            )

            2 -> ChatFilesPane(
                files = exchangedFiles,
                accent = tabAccent,
                config = state.server,
                onOpen = { file, local ->
                    // Le lecteur de la famille, avec le type annoncé par le nom quand la pièce
                    // jointe n'en porte pas.
                    onOpenLocalFile(
                        android.net.Uri.fromFile(local),
                        file.name,
                        file.mime.ifBlank { FileViewers.mimeFor(file.name) },
                    )
                },
                // Le contenu de l'onglet Fichiers repose sur la page, lui aussi : aucune plaque.
                modifier = Modifier.fillMaxSize()
                    .padding(top = glassTopInset, bottom = glassBottomInset + floatLane),
            )

            }
            }
            // Le bord de l'écran reste au geste de retour du système : ces deux lames absorbent un
            // glissement horizontal parti du bord, et rien d'autre — les appuis passent.
            TabSwipeEdgeGuards(
                leftPx = edgeGuardLeftPx,
                rightPx = edgeGuardRightPx,
                modifier = Modifier.matchParentSize(),
            )
            }
            // **La barre est dessinée APRÈS le fil**, donc au-dessus de lui — à l'écran comme pour
            // le doigt. Les trois défauts de la fusion n'en faisaient qu'un, et venaient de l'ordre
            // inverse (barre posée **avant** le pageur dans ce `Box`) :
            // - les appuis tombaient sur le pageur, qui occupe toute la surface : la barre était
            //   visible mais **inerte** — aucun de ses boutons ne répondait, et l'arbre
            //   d'accessibilité ne la publiait même pas ;
            // - Haze ne peut échantillonner qu'un contenu **déjà dessiné** : la vitre de la barre
            //   n'avait rien à flouter et retombait sur son voile de repli, d'où le « verre perdu » ;
            // - une barre sous le contenu n'a pas d'arête : son liseré se lisait sur du noir.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height },
            ) {
            OnyxTopBar(
                // Le titre suit le nom donné à la conversation (`Conversation.name` d'la référence, qui prime
                // sur le premier message) ; sinon la première question, comme avant.
                title = (state.conversationName?.takeIf { it.isNotBlank() }
                    ?: state.firstUserQuestion)
                    ?.let(::chatHeaderTitle)
                    ?.takeIf { it.isNotBlank() }
                    ?: "New conversation",
                titleIsPlaceholder = state.conversationName.isNullOrBlank() &&
                    !state.hasUserTurn,
                conversationKey = state.firstMessageId.orEmpty(),
                // La seconde ligne du titre, comme `ContentView:448` : le **handle** du bot
                // précédé de « @ » (une conversation simple garde le nom d'agent des Réglages),
                // puis le modèle. Sans le « @ » ni le modèle, la ligne répétait le titre — c'est
                // le doublon « writer / writer » vu sur le Magic V3.
                // La seconde ligne nomme **le modèle en vigueur** — celui qui sera réellement envoyé
                // au prochain message, la **même valeur** (`ChatUiState.modelInForce` : conversation →
                // bot → serveur → défaut, l'alias de passerelle écarté). Aucun libellé recalculé à
                // part : c'est ce qui fait que ce qui est écrit est ce qui part. Le nom du bot est
                // déjà au-dessus, et le « @handle » ne servait à rien ici (demande du propriétaire).
                subtitle = state.modelInForce
                    ?: OnyxLocalization["default model"],
                // Et **le profil réellement en force**, à côté : la liaison bot → profil se lit à
                // l'écran comme elle part au tour (`ChatUiState.profileInForce`, la même décision
                // que `ChatUiState.botProfile`). L'app ne peut donc plus annoncer un profil et en
                // servir un autre — « coder » se lit ici quand « coder » sert.
                //
                // **Mais pas deux fois.** La barre n'écrit ce profil que quand il ne répète pas le
                // titre déjà au-dessus ([headerProfileLabel]) : une conversation de bot porte le nom
                // de son bot (`botChatToOpen` la titre ainsi), donc le « @writer » y était le
                // doublon vu par le propriétaire — et la place qu'il prenait manquait au modèle.
                // Quand les deux diffèrent (bot titré « Rédacteur », profil `writer`), la ligne
                // reste, et c'est là qu'elle renseigne. Le routage, lui, ne dépend pas de ce
                // libellé : il porte `botProfile` (`ProfileInForce.appliedTo`), et l'identité du
                // bot — ouverte par le visage, `BotEditorScreen` — écrit toujours son `@handle`.
                profileLabel = if (threadBotUnknown) null else state.profileInForce,
                // Le repli **déclaré**, quand la passerelle a basculé : le modèle en vigueur
                // (le repli) est nommé par la pilule ci-dessus, et cette ligne dit **qui** il
                // remplace et **d'où** il vient — le mensonge « configuré annoncé » est fermé.
                fallbackLine = state.fallback?.declaredLine(),
                onOpenConversations = onOpenConversations,
                sidebarToggle = sidebarToggle,
                onPickModel = onPickModel,
                onNewConversation = onNewConversation,
                onMenu = { showMoreMenu = true },
                // La capsule des sous-agents n'apparaît que quand il y en a : elle dit combien
                // travaillent, en direct, et ouvre l'écran des tâches.
                // **Le nombre de tâches EN COURS**, jamais le cumul de la conversation.
                //
                // La capsule comptait tout ce que le fil avait vu (`it.isLive`, et « lancé sans
                // suite » restait vivant pour toujours) : elle affichait 40 sur une conversation
                // qui n'avait plus rien en route. Seul `RUNNING` travaille vraiment — une tâche
                // close ne compte plus.
                subagentRuns = (subagentAgents ?: state.subagents).count { it.status == SubagentStatus.RUNNING },
                onOpenSubagents = if ((subagentAgents ?: state.subagents).isEmpty()) null else onOpenSubagents,
                contextUsed = contextUsage?.contextUsed,
                contextWindow = contextUsage?.contextMax ?: state.server.contextWindow.takeIf { it > 0 },
                // **Le visage est toujours là**, celui du bot dont c'est la conversation et, pour
                // une conversation simple, celui d'Onyx ([ThreadSpeaker.faceName] le donne dans
                // les deux cas). Il l'était déjà dans la référence ; la condition
                // « seulement si `botProfile != null` » le retirait de **toute** nouvelle
                // conversation, c'est-à-dire de l'écran que la personne voit en premier.
                avatarName = if (threadBotUnknown) null else threadSpeaker.faceName,
                avatarFaceCode = if (threadBotUnknown) null else threadSpeaker.faceCode,
                // **Le visage de l'en-tête ouvre l'identité du bot**, comme le repère-visage de
                // la barre du bas : le même geste, au même endroit du dessin, donne le même
                // résultat. Le branchement vient du parent (`onOpenBotIdentity`) ; sans lui, le
                // visage reste décoratif.
                onOpenFace = onOpenBotIdentity,
                // LOT organisation-fil-competences : la pastille d'état de l'en-tête. Elle ne se
                // pose que pendant un tour, et ne dit que ce qu'on sait vraiment : le nombre de
                // sous-agents qui travaillent, jamais une « étape 3 sur 5 » inventée (le plan du
                // tour n'est pas transporté par le gateway sur ce fil).
                status = threadStatus(
                    working = state.isStreaming,
                    subagents = (subagentAgents ?: state.subagents).count { it.status == SubagentStatus.RUNNING },
                    flow = flow,
                ),
                // LOT fil-vivant : l'état réel du tour et le signal de lecture. La pastille
                // **respire**, **pulse** ou **s'immobilise avec une lueur** selon lui, et
                // **ralentit** dès que la personne remonte lire le fil.
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

            // Les bots qu'un mot `@` en cours peut nommer, juste au-dessus du champ
            // (`ComposerView.mentionMenu`) : toucher une ligne finit le mot dans le brouillon et
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

            // **La pile du bas** : le compositeur, puis la barre d'onglets. Une **seule** marge
            // système pour les deux (`composerBottomInsets` : le clavier d'abord, puis la zone de
            // navigation **sans** le clavier) — les deux marges ne s'additionnent plus, et la
            // barre d'onglets ne peut plus être poussée sous le bord de la fenêtre, derrière le
            // clavier, pendant que le compositeur occuperait sa place.
            // La pile du bas, **par-dessus** le fil : le compositeur et les cinq onglets portent
            // donc des vitres sur du contenu qui défile.
            //
            // **Elle ne s'efface plus, jamais.** L'`AnimatedVisibility` qui la fondait au défilement
            // a été retirée : le `Column` ci-dessous est dessiné **inconditionnellement**, à la même
            // place, à chaque position de défilement. La hauteur mesurée des deux morceaux
            // (`composerHeightPx`, `tabBarHeightPx`) reste la même, donc la réserve du fil ne bouge
            // pas — et rien ne peut plus faire disparaître la plaque.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // **LA PLAQUE UNIQUE — compositeur et barre d'onglets dans UNE SEULE surface.**
                    // Le point le plus visible de la planche (`hlib.py:491` `slab` : `ask` 64 px +
                    // `navrow` 76 px = 140 px, largeur 379, rayon 28, marge 16, arête claire et
                    // filet de 1 px — `hcss.py:226`). Les deux formes ne sont plus soudées par un
                    // pont de matière : elles tiennent dans une seule plaque, et il n'y a donc plus
                    // de soudure à faire.
                    //
                    // **Et elle est TRANSLUCIDE.** Elle peignait un aplat (`OnyxSurfaces.surfaceHigh`)
                    // *sous* sa vitre : le verre n'avait plus rien à montrer, et la plaque se lisait
                    // « un aplat gris qui ne se détache pas du bord » — c'était le premier des sept
                    // écarts. L'aplat est retiré : la vitre peint désormais **seule** sa teinte
                    // (mesurée à son plancher de lisibilité), et le fil qui défile passe au travers.
                    // Le filet de la maison
                    // ([OnyxSurfaces.hairline]) et l'arête claire du verre ([drawGlassFinish])
                    // restent : ce sont eux qui détachent la plaque du bord, pas un remplissage.
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
                    // **Et elle MASQUE ce qu'elle recouvre.** Le verre est mince par nature : au
                    // plancher de lisibilité d'une encre dérivée (48 % en clair), le texte du fil
                    // restait **lisible au travers** de la plaque (défaut relevé : « le texte passe
                    // à travers et autour »). `masking = true` remonte le voile au **plancher de
                    // masquage** ([glassMaskingFloor]) — l'opacité à partir de laquelle le contraste
                    // du fil retombe sous le seuil de lisibilité — sans toucher au flou ni à la
                    // finition du verre, qui restent peints **au-dessus**.
                    .liquidGlass(GlassSurface.TAB_BAR, RoundedCornerShape(PlateGeometry.Radius), masking = true),
            ) {
            // **LA CARTE D'ACTIVITÉ A ÉTÉ RETIRÉE** — ordre du propriétaire : « supprime la barre de
            // réflexion, encadrée en rouge, ça fait doublon ».
            //
            // Elle réaffichait au-dessus du champ ce que le fil dit déjà : la réflexion et sa durée
            // (la rangée « Réflexion » dépliable, en tête de la réponse) **et** un second bouton
            // d'arrêt pour le même geste que le carré du compositeur. Le fil est la **seule** source
            // de la réflexion, et le compositeur le **seul** arrêt du tour.
            //
            // La pile du bas ne contient donc plus que le champ : sa réserve (`composerHeightPx`)
            // mesure le champ seul, et la plaque garde exactement sa géométrie. La réserve du fil
            // au-dessus du compositeur diminue d'autant (voir la mesure dans le rapport).
            // **La prise en main se voit aussi sans le fil.** La carte du message ne peut apparaître
            // que si l'agent écrit son appel dans *cette* session ; cette bande interroge l'état du
            // plugin et couvre le cas où l'agent travaille ailleurs. Elle se retire toute seule.
            BrowserInvite(server = state.server, onOpen = onOpenBrowser)
            Box(
                modifier = Modifier
                    .onSizeChanged { composerHeightPx = it.height }
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
                // La **puce du modèle** de la carte : le modèle **en vigueur** et son niveau de
                // réflexion, et le chevron qui ouvre la feuille (fonction 2). Elle n'est dessinée
                // que si un écran lui donne de quoi ouvrir. La **même valeur** que la pilule du haut
                // et que la requête d'envoi — un alias de passerelle est déjà écarté en amont.
                modelLabel = state.modelInForce
                    ?: OnyxLocalization["default model"],
                // Un repli **local** ne propose pas les crans du modèle configuré : on n'écrit pas
                // un cran qui ne pilote rien — le libellé est vidé en amont (`reasoningLabel`).
                reflectionLabel = reasoningLabel,
                onOpenModelPicker = onOpenModelSheet,
                // Un clavier physique écrit dans le champ : ⌘L le met au focus, et pendant une
                // réponse le groupe propose « steer » ou « file », comme `ComposerView`.
                focusRequester = composerFocus,
                canSteer = state.canSteer,
                onSteer = { onSteer(state.input) },
                // La pile du bas prend la marge système **une seule fois** (voir plus haut) : le
                // champ ne la reprend pas de son côté, les deux ne s'additionnent plus.
                applyBottomInsets = false,
                // **Le serveur injoignable est un état du champ.** Il fait **céder** l'invite : la
                // file (« N messages waiting ») porte déjà l'état, et le champ ne se met pas à
                // écrire deux textes à la fois (voir `composerFieldInvite`).
                offline = state.isConnectionProblem,
                // **Le compositeur est posé sur la plaque unique** (voir la pile du bas) :
                // il ne peint plus sa propre carte, la plaque s'en charge.
                plate = true,
            )
            }

            // **La barre des onglets, toujours là.** L'ordre est **compositeur puis barre** : le
            // champ reste collé sous le fil qu'on lit, et la barre occupe le bas de la pile, au plus
            // près du clavier — exactement là où elle a toujours été. Rien n'est donc inséré entre
            // la conversation et la zone d'écriture, et le champ ne bouge pas d'un onglet à l'autre.
            //
            // **Aucun effacement, d'aucune sorte, ne subsiste.** La poignée à chevron qui la masquait
            // a été retirée de l'écran sur l'ordre du propriétaire, et sa préférence par conversation
            // (`ChatPanelStore.tabsHidden`) avec elle ; l'effacement **au défilement** de la pile
            // entière — champ **et** barre — a été retiré à son tour (« ça ne doit JAMAIS disparaître
            // quand je défile »). Ni le clavier, ni un réglage, ni un geste, ni une position de
            // défilement ne l'éteignent : la plaque est dessinée **inconditionnellement**.
            //
            // **La pilule détachée du bas.** `navigationBarsPadding` la pose au-dessus de la zone
            // système (barre de navigation, ou zone du geste d'accueil : c'est cette zone qui passait
            // par-dessus les libellés) ; [TabBarGeometry.BottomMargin] ajoute le dégagement de la
            // maison — la marche `Half` de l'échelle — pour qu'elle ne paraisse jamais collée au bord.
            // C'est **cette** hauteur que la réserve du fil reprend, marge comprise : la géométrie de
            // la pilule et la place que le fil lui laisse ne peuvent donc pas diverger.
            // **La rangée des onglets, à hauteur fixe** — 76 dp, la hauteur de `.navrow` dans la
            // planche. C'est la seconde moitié des 140 dp : avec la rangée du champ (64 dp, dans
            // `OnyxComposer`), la plaque vaut exactement la cible, et elle se **mesure** au lieu de
            // dépendre de trois rangées qui s'additionnaient (166 dp relevés sur la capture).
            //
            // **Le filet de séparation interne est peint ici**, sur la frontière haute de la rangée :
            // c'est `.ask{border-bottom:1px solid var(--line)}` de la planche — le trait qui sépare
            // le champ des onglets. Il manquait complètement : sans lui, les deux rangées se lisaient
            // comme une seule bande de contrôles posés dans un aplat. Il est **peint** et non
            // mesuré : il ne prend pas un dp à la rangée, donc la somme reste 64 + 76 = 140.
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
                    }
                    // La marge système du bas et le dégagement de la maison sont portés par **la
                    // plaque** (voir la pile du bas) : la rangée n'a plus à les reprendre, sinon
                    // l'encart du système serait compté deux fois — le défaut d'origine.
                    .onSizeChanged { tabBarHeightPx = it.height }
                    .onGloballyPositioned { tabBarWeldRect = it.boundsInParent() },
                // **La barre est CENTRÉE dans ses 76 dp.** Le défaut relevé : la pastille de
                // l'onglet actif était posée au **haut** de la rangée — collée au filet de
                // séparation, avec tout le blanc sous elle (« c'est même pas centré »). La barre
                // a maintenant ses 8 dp de dégagement au-dessus **et** au-dessous, comme `.navi`
                // dans `.navrow` (`hcss.py:227-229`).
                verticalArrangement = Arrangement.Center,
            ) {
                // **Le repère-visage : la position de la barre, une autre porte.**
                //
                // Son dessin (le visage dans son cercle à l'accent) occupait la place d'« Idées ».
                // « Idées » et « Objectifs » sont supprimés (demande du propriétaire) : le repère
                // ne fait donc plus défiler le pageur jusqu'à une page « Idées » qui n'existe
                // plus — il ouvre l'identité du bot, ou ne fait rien de sûr. On branche le
                // **geste** sur ce que le dessin annonce.
                //
                // **La plaque du bas est toujours visible.** La poignée à chevron qui pouvait la
                // masquer a été retirée (demande du propriétaire) : plus d'`AnimatedVisibility`,
                // plus d'état « barre masquée » — un écrivain de moins, aucune fonction de plus.
                val faceIndex = ChatTab.drawn.lastIndex
                ChatTabBar(
                    selectedIndex = pagerState.currentPage,
                    // **Quatre repères : trois onglets de contenu, puis le visage du bot.**
                    // Le cinquième (« Objectifs ») tombait à 4,2 dp de large : ni icône ni
                    // libellé. Il est **supprimé** avec « Idées » — ce n'est plus une page du
                    // pageur, c'est un écran qui n'existe plus.
                    tabs = ChatTab.drawn,
                    accent = tabAccent,
                    badges = tabBadges,
                    // **La barre est posée sur la plaque unique** : elle ne peint plus sa propre
                    // capsule, la plaque porte la forme, le fond, le filet et l'ombre.
                    plate = true,
                    // **Le quatrième repère est le visage**, dans un cercle à l'accent, avec son
                    // libellé « Onyx » — la planche n'y met pas une ampoule.
                    faceIndex = faceIndex,
                    faceLabel = ChatTab.FACE_LABEL,
                    face = {
                        BotLook(
                            name = threadSpeaker.faceName,
                            faceCode = threadSpeaker.faceCode,
                            size = PlateGeometry.TabCell,
                            // Le repère ne cligne pas : quatre repères de barre ne bougent
                            // pas, et un visage qui cligne dans une barre attire l'œil pour
                            // rien.
                            blinks = false,
                        )
                    },
                    onSelect = { index ->
                        when {
                            // Le repère-visage ouvre l'identité du bot.
                            index == faceIndex && onOpenBotIdentity != null -> onOpenBotIdentity()
                            // Les onglets de contenu font défiler le pageur.
                            index in 0 until chatTabCount -> scope.launch {
                                if (reduceMotion) {
                                    pagerState.scrollToPage(index)
                                } else {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                            // Le repère-visage sans porte : rien, jamais un défilement hors page.
                            else -> Unit
                        }
                    },
                )
            }
            }
        }

        // **Où se pose le bouton, et pourquoi ici.** Il se range au-dessus de la **réserve du fil**
        // ([threadBottomReserve] — la pile mesurée : compositeur, barre d'onglets, encart du système,
        // écart de la maison) plus un dégagement ([ComposerStacking.PillClearanceDp]). L'ancienne
        // marge était une seconde estimation (`composerHeight + tabBarHeight + 8`) **plus** un
        // `navigationBarsPadding()` : l'encart du système y était compté deux fois, et le bouton
        // descendait dans la carte du compositeur — mesuré 7,6 dp du bord ici, et jusqu'à 48 dp
        // **dedans** là où la racine ne consomme pas cet encart (barre à trois boutons, barre de
        // tâches d'un pliable). Et file non vide = les messages en attente occupent le bas du fil :
        // le bouton se retire, plutôt que de les recouvrir. La liste des mentions `@` et celle des
        // commandes `/` occupent la même place : le bouton s'efface devant elles.
        //
        // Il se range désormais dans la **voie flottante** ([floatLane]) : c'est elle qui garantit
        // qu'il ne se pose **jamais sur du texte** — la bande est réservée au fil, qui ne la
        // traverse pas. La poignée à chevron qui partageait cette bande est partie ; la voie a été
        // ramenée à la stricte hauteur de la pastille, et le fil en a récupéré la différence.
        val jumpClearance = glassBottomInset + ComposerStacking.PillClearanceDp.dp

        // **L'ANCRE MESURÉE DES ÉLÉMENTS FLOTTANTS — et la garde qui manquait.**
        //
        // `jumpClearance` (juste au-dessus) est calculé depuis `composerHeightPx` et
        // `tabBarHeightPx` : deux états **mesurés** (`onSizeChanged`) qui valent **zéro** jusqu'au
        // premier passage de mise en page. Une position calculée depuis zéro n'est pas une position
        // approchée, c'est une position **fausse** — et c'est le défaut de restitution d'état relevé
        // par le propriétaire : « ça crée des bugs… c'est incohérent ». À **chaque** reprise de l'app
        // (démarrage à froid, restauration après mort de processus, recréation de l'activité par
        // rotation, thème, langue ou police 1,3×, retour depuis une feuille, clavier ouvert puis
        // fermé, insets changés), les éléments flottants se dessinaient donc **avant la mesure**, à
        // une position que rien ne soutenait, puis sautaient d'un coup dès que la mesure arrivait :
        // la première image de la reprise, celle qu'on voit.
        //
        // La règle est tenue ici, **une fois pour les deux** : `threadFloatAnchor` rend l'ancre
        // quand la plaque a livré ses hauteurs, et `null` sinon — et `null` veut dire **on ne dessine
        // pas**. Une position qui vaut zéro au premier passage n'est jamais peinte : l'élément
        // paraît au bon endroit, jamais dans la bande de l'en-tête ni sous la barre d'état.
        // La valeur dessinée reste `jumpClearance` : la même source que le rembourrage du fil, donc
        // aucun second calcul à faire diverger.
        val floatAnchor = threadFloatAnchor(
            composerPx = composerHeightPx,
            bottomRowPx = tabBarHeightPx,
            measured = jumpClearance,
        )

        // **La poignée à chevron a été retirée** (demande du propriétaire : « supprime le bouton
        // pour la possibilité de cacher »). La plaque du bas ne peut donc plus être masquée, et il
        // n'y a plus de porte de retour à garder : c'est le **seul** élément flottant en moins. La
        // pastille « aller au dernier message » reste, sous la même garde de mesure (`floatAnchor`).
        AnimatedVisibility(
            visible = offersJump && pagerState.currentPage == 0 &&
                state.queuedSize == 0 && mentionSuggestions.isEmpty() && state.slashItems.isEmpty() &&
                // **La garde de mesure** : la pastille ne se dessine pas avant la mesure de la plaque.
                floatAnchor != null,
            // Il **paraît d'un coup** : un fondu court, figé sous « Réduire les animations »
            // (`ComposerStacking.fadeSpec` — une seule décision de mouvement pour toute la pile).
            enter = fadeIn(animationSpec = ComposerStacking.fadeSpec(reduceMotion)),
            exit = fadeOut(animationSpec = ComposerStacking.fadeSpec(reduceMotion)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Au-dessus du fil **et** de la pile : à l'écran comme pour le doigt.
                .zIndex(2f)
                // Une marge de chaque côté : le bouton ne touche plus le bord du panneau.
                .padding(start = 16.dp, end = 16.dp, bottom = jumpClearance),
        ) {
            JumpToLatestPill(live = state.isStreaming, onClick = {
                // Re-attacher le suivi, puis coller au bas : le contenu qui continue de grandir
                // (la réponse s'écrit) reste suivi après l'appui.
                following = ScrollFollow.jumpToLatest()
                scope.launch { listState.scrollToItem(latestItemIndex, BottomScrollOffset) }
            })
        }

        ToastHost(toaster = toaster)

        // La restauration demande **confirmation** : rien n'est défait tant que la surface de la
        // maison n'a pas répondu. C'est **la** confirmation de la maison ([OnyxConfirm]) — le verre
        // des panneaux, le rayon 28 de la famille, l'arête claire, le titre et le corps du socle,
        // et deux pastilles pleine largeur à **16 dp du bord**, séparées de **16 dp** — et non la
        // boîte du système (plaque grise, lignes internes, texte nu bord à bord) que le
        // propriétaire a pointée : « pas cohérente avec le reste du thème, les boutons collent trop
        // au bord ».
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

        // Le branchement demande **confirmation** lui aussi, et pour la même raison : rien n'est
        // copié tant qu'elle n'est pas donnée. La boîte dit ce qu'il fait, ce qu'il laisse en
        // place, quand rien ne démarre — et ce que la copie emporte vraiment.
        state.branchPrompt?.let { prompt ->
            BranchConfirmAlert(
                prompt = prompt,
                onCancel = onCancelBranch,
                onConfirm = onConfirmBranch,
            )
        }

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
            // La feuille des actions monte du bas, à la place de la liste flottante : le **même**
            // contenu (aucune entrée perdue, la ligne de projet comprise), plus les trois listes du
            // serveur et la copie de l'identifiant. Le titre dit ce qu'on pilote.
            ChatActionSheet(
                title = state.conversationName?.takeIf { it.isNotBlank() } ?: threadSpeaker.label,
                items = items,
                server = state.server,
                sessionId = state.serverSessionId,
                onDismiss = { showMoreMenu = false },
                // « Consommation et coût » dit quand le tour a été servi en local : un tarif par
                // million de jetons n'y a alors aucun sens.
                fallback = state.fallback,
                // Et il NOMME le modèle **en vigueur** — celui de l'en-tête, la même source
                // (`ModelInForce`) : sans ça il nommait le modèle figé de `session.usage`, qui ne
                // suit pas un changement de modèle (le défaut signalé).
                modelInForce = state.modelInForce,
                // La session d'un bot vit dans **son** profil : c'est là que se lit la
                // consommation consignée quand la session n'est plus vivante.
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
            // La feuille qui monte du bas (la référence) : un titre, les **vraies** limites d'Onyx
            // en sous-titre, et une ligne par entrée avec sa pastille d'icône. L'ordre est celui de
            // la référence ; « Coller » s'y ajoute, la référence ne l'a pas.
            AttachmentSheet(
                title = "Add to message",
                subtitle = AttachmentLimits.subtitle(),
                // `listOfNotNull` : la ligne « Enseigner une tâche » est **facultative**
                // (serveur sans identifiants Dashboard), et une liste typée ne peut pas porter un
                // élément nul.
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
                    // **« Enseigner une tâche »**, comme « Teach a task » de la référence : la
                    // dernière ligne de la feuille. Elle ferme la feuille et ouvre l'ordinateur
                    // connecté, où la démonstration s'enregistre — le geste vit là-bas (les clics,
                    // l'écran, le bail du serveur), la ligne n'est que le chemin.
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
 * Où l'appareil photo écrit sa prise : **le stockage privé durable de l'app**, jamais le cache.
 *
 * `cacheDir` est le répertoire que le système vide quand il veut : la photo y disparaissait après
 * fermeture de l'app, et la bulle du fil affichait « Illisible » (elle se décode depuis cette URI).
 * `filesDir` survit à la fermeture, au redémarrage du téléphone et au nettoyage du cache.
 */
private fun newCameraUri(context: android.content.Context): android.net.Uri? = runCatching {
    val folder = java.io.File(context.filesDir, "photos").apply { mkdirs() }
    val file = java.io.File(folder, "photo-${System.currentTimeMillis()}.jpg")
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()

/**
 * « Aller au dernier message », posé au-dessus de la pile du bas quand la fin n'est plus à l'écran.
 *
 * **La cible fait 44 dp** ([OnyxSpace.ThumbnailSize], la cible du pouce de la maison) : la pastille
 * n'en faisait que 18 dp de haut — mesuré à l'arbre d'accessibilité —, sous la cible du doigt, d'où
 * « je n'arrive pas à le toucher ». Le verre s'étire sur la cible, le contenu reste centré.
 */
@Composable
private fun JumpToLatestPill(live: Boolean, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    GlassPill(
        // **Pleine**, et pas seulement mince : elle flotte au-dessus du fil, donc le verre mince
        // laisserait lire les messages **dans** la pastille (défaut relevé : « la pastille se
        // mélange au texte »). La marge de 16 et le rangement au-dessus de la plaque sont posés par
        // l'appelant (`AnimatedVisibility` de `ChatScreen`) : la pastille ne bouge pas ici.
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

/** `NewBelowDot` : le point de 8 pt pulse en `easeInOut` toutes les 0,6 s pendant le direct. */
@Composable
private fun NewBelowDot() {
    val theme = LocalOnyxTheme.current
    val transition = rememberInfiniteTransition(label = "newBelow")
    // `NewBelowDot` : `guard live, !reduceMotion else { dim = false; return }`
    // (`TranscriptView:528-529`) — le point reste plein sous Reduce Motion.
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

/** Statut du gateway pendant le tour (réessai provider, indisponibilité) : honnête au lieu d'un vide. */
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
 * La ligne de statut d'une note de pilotage (`Conversation.statusLine`) : ce que le serveur a
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

/** La recherche dans la conversation (`showSearch` de ContentView) : le champ, et le compte. */
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

/** Une page de fil : ce que la liste rend d'un coup (`TranscriptPage`). */
private const val PAGE = 30

/**
 * De combien de pixels on demande à dépasser la dernière ligne pour toucher le vrai bas du fil :
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

/** « 12 earlier messages » : la page précédente, d'un tap. */
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
 * Le formateur de jour, **gardé une fois**.
 *
 * `SimpleDateFormat` est cher à construire (chargement de locale, de calendrier, de symboles) et
 * n'est pas réutilisable en concurrence — mais la composition est mono-thread, et le recréer à
 * chaque rangée visible, à chaque recomposition, était un coût pur dans le défilement d'une grosse
 * conversation.
 */
private val dayFormatter: ThreadLocal<java.text.SimpleDateFormat> =
    ThreadLocal.withInitial { java.text.SimpleDateFormat("EEEE, d MMMM", OnyxLanguage.preferred) }

private fun dayFormat(): java.text.SimpleDateFormat = dayFormatter.get()!!

/** Ce que le fil accepte en dépôt : les images, comme le composeur. */
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
