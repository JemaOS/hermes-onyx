package com.onyx.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateListOf
import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SetupCodeOffer
import com.onyx.app.di.AppContainer
import com.onyx.app.intake.ShareInbox
import com.onyx.app.notifications.ApprovalNotifier
import com.onyx.app.notifications.NotificationCopy
import com.onyx.app.notifications.QuestionNotifier
import com.onyx.app.notifications.TurnService
import com.onyx.app.notifications.pendingQuestionsOf
import com.onyx.app.ui.chat.ComposerEditorScreen
import com.onyx.app.ui.chat.ChatScreen
import com.onyx.app.ui.chat.ChatViewModel
import com.onyx.app.ui.chat.components.StartCard
import com.onyx.app.ui.chat.components.chatHeaderTitle
import com.onyx.app.ui.conversations.ConversationsScreen
import com.onyx.app.ui.conversations.ListSection
import com.onyx.app.ui.conversations.ProjectSessionsScreen
import com.onyx.app.ui.conversations.ProjectSessionsViewModel
import com.onyx.app.ui.conversations.SessionUsageScreen
import com.onyx.app.ui.models.ModelsScreen
import com.onyx.app.ui.settings.AppearanceScreen
import com.onyx.app.ui.settings.ConnectionDetailsScreen
import com.onyx.app.ui.settings.SettingsScreen
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.OnyxSegmented
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.chat.shareTranscript
import com.onyx.app.ui.bots.APIBotEditorScreen
import com.onyx.app.ui.bots.BotEditorScreen
import com.onyx.app.ui.bots.BotFacePickerScreen
import com.onyx.app.ui.bots.BotActivityStore
import com.onyx.app.ui.bots.BotBadges
import com.onyx.app.ui.bots.BotFaceStore
import com.onyx.app.ui.bots.composerBots
import com.onyx.app.ui.bots.BotsScreen
import com.onyx.app.ui.bots.BotsViewModel
import com.onyx.app.ui.bots.BotRosterStandIn
import com.onyx.app.ui.bots.botChatToOpen
import com.onyx.app.ui.bots.botOfConversation
import com.onyx.app.ui.bots.BotEditorViewModel
import com.onyx.app.ui.bots.BotVoicePickerScreen
import com.onyx.app.ui.bots.GroupChatScreen
import com.onyx.app.ui.bots.GroupChatViewModel
import com.onyx.app.ui.bots.GroupRoomScreen
import com.onyx.app.ui.bots.GroupRoomViewModel
import com.onyx.app.ui.bots.toRoom
import com.onyx.app.ui.bots.GroupVoice
import com.onyx.app.ui.bots.GroupVoiceDemo
import com.onyx.app.ui.bots.GroupVoiceScene
import com.onyx.app.ui.bots.GroupVoiceThread
import com.onyx.app.ui.bots.HiddenBotsScreen
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.data.model.serve.BotMentions
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.kanban.KanbanScreen
import com.onyx.app.ui.kanban.KanbanViewModel
import com.onyx.app.ui.cron.BotSchedulesScreen
import com.onyx.app.ui.cron.CronScreen
import com.onyx.app.ui.cron.CronViewModel
import com.onyx.app.ui.context.ContextFile
import com.onyx.app.ui.context.ContextFileEditorScreen
import com.onyx.app.ui.context.ContextFileEditorViewModel
import com.onyx.app.ui.context.ContextScreen
import com.onyx.app.ui.context.ContextViewModel
import com.onyx.app.ui.context.Mode
import com.onyx.app.ui.files.FileEditorScreen
import com.onyx.app.ui.files.FileEditorViewModel
import com.onyx.app.ui.files.FilesScreen
import com.onyx.app.ui.files.FilesViewModel
import com.onyx.app.security.AppLock
import com.onyx.app.ui.lock.LockScreen
import com.onyx.app.ui.settings.ConnectQrScreen
import com.onyx.app.ui.common.AcknowledgementsScreen
import com.onyx.app.ui.common.TipJarScreen
import com.onyx.app.ui.settings.voice.ReplyLanguageScreen
import com.onyx.app.ui.settings.voice.KokoroVoicesView
import com.onyx.app.ui.settings.voice.SpokenPrefixesScreen
import com.onyx.app.ui.settings.voice.SpokenPrefixEditorScreen
import com.onyx.app.ui.settings.voice.SpokenPrefix
import com.onyx.app.ui.settings.voice.routeVoiceText
import com.onyx.app.ui.settings.voice.StopPhrasesScreen
import com.onyx.app.ui.settings.voice.SpeechLanguageScreen
import com.onyx.app.ui.rich.PatchScreen
import com.onyx.app.ui.computer.ComputerScreen
import com.onyx.app.ui.terminal.TerminalScreen
import com.onyx.app.ui.terminal.TerminalSettingsView
import com.onyx.app.ui.chat.SubagentListScreen
import com.onyx.app.ui.chat.SubagentTailView
import com.onyx.app.ui.chat.SubagentTranscriptScreen
import com.onyx.app.ui.chat.CameraScreen
import com.onyx.app.ui.chat.PhotoViewerScreen
import com.onyx.app.ui.chat.ViewerPhoto
import com.onyx.app.ui.chat.SelectableTextScreen
import com.onyx.app.ui.tools.ToolsScreen
import com.onyx.app.ui.skills.SkillsScreen
import com.onyx.app.ui.skills.SkillEditorScreen
import com.onyx.app.ui.servers.ProfilePickerScreen
import com.onyx.app.ui.common.WhatsNewScreen
import com.onyx.app.ui.common.CURRENT_RELEASE_ITEMS
import com.onyx.app.ui.servers.ServersScreen
import com.onyx.app.ui.setup.SetupScreen
import com.onyx.app.ui.setup.SetupCodeFinish
import com.onyx.app.ui.setup.SetupCodeScanner
import com.onyx.app.ui.setup.SetupCodeSheet
import com.onyx.app.ui.settings.LockSettingsScreen
import com.onyx.app.ui.settings.PhoneToolsScreen
import com.onyx.app.ui.settings.PushSettingsScreen
import com.onyx.app.ui.settings.GatewayScreen
import com.onyx.app.ui.settings.GatewayViewModel
import com.onyx.app.ui.settings.LogsScreen
import com.onyx.app.ui.settings.LogsViewModel
import com.onyx.app.ui.settings.SettingsViewModel
import com.onyx.app.ui.settings.VoiceSettingsScreen
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.OnyxAppTheme
import com.onyx.app.ui.voice.OrbLight
import com.onyx.app.ui.voice.TalkOverChoice
import com.onyx.app.ui.voice.VoiceOrbChoice
import com.onyx.app.ui.voice.VoiceScreen
import com.onyx.app.ui.voice.soloVoiceStop
import com.onyx.app.voice.BotVoicePicker
import com.onyx.app.voice.VoicePhase
import com.onyx.app.voice.VoiceSession
import com.onyx.app.widget.OnyxWidget
import androidx.glance.appwidget.updateAll

/** Les écrans de l'app; la navigation tient dans un seul état, comme un `NavigationStack`. */
private enum class Route {
    CHAT, CONVERSATIONS, SETTINGS, APPEARANCE, MODELS, VOICE,
    GATEWAY, LOGS, FILES, FILE_EDITOR, CONTEXT, MEMORY, CONTEXT_EDITOR, VOICE_SETTINGS, NOTIFICATIONS, KOKORO_VOICES, CRON, KANBAN,
    COMPOSER_EDITOR, PHONE_TOOLS, LOCK, CONNECT, BOTS, BOT_EDITOR, BOT_FACE, BOT_SCHEDULES, BOT_VOICE, GROUP_CHAT, GROUP_VOICE, ACKNOWLEDGEMENTS, SPEECH_LANGUAGE, REPLY_LANGUAGE,
    COMPUTER,
    STOP_PHRASES, SERVERS, SETUP, SPOKEN_PREFIXES, SPOKEN_PREFIX_EDITOR, PROFILES, WHATS_NEW, PATCH, SELECT_TEXT, PHOTO, PDF, VIDEO, SKILLS, SKILL_EDITOR, TOOLS, API_BOT, CAMERA, TIP_JAR, SUBAGENT, SUBAGENTS, TERMINAL, TERMINAL_SETTINGS,
    // « Capture rapide »: une note prise en passant, classée par l'agent.
    QUICK_CAPTURE,
    // Les quatre destinations qui manquaient: `ConnectionDetailsView`, `HiddenBotsView`,
    // `SessionUsageView` et `ProjectSessionsView`.
    CONNECTION_DETAILS, HIDDEN_BOTS, SESSION_USAGE, PROJECT_SESSIONS,
}

/**
 * La maquette de boutique du mode vocal, comme `-echo.voiceDemo`: le lancement la demande
 * (`-e onyx.voiceDemo true`), et l'écran vocal se pose en écoute sans qu'aucun micro n'existe.
 * Rien de tout ceci ne sert hors d'un APK de debug.
 */
private const val EXTRA_VOICE_DEMO = "onyx.voiceDemo"

/**
 * La maquette de la scène vocale d'une salle (`-e onyx.groupVoiceDemo speaking`): la réplique qui
 * se lit est **injectée**, sans serveur ni micro, comme le drapeau du modèle. Debug seulement.
 */
private const val EXTRA_GROUP_VOICE_DEMO = "onyx.groupVoiceDemo"

/**
 * La doublure de QA du roster des bots (`am start -e onyx.demoBots true`): le pendant Android du
 * drapeau du modèle. Elle ne pose que la liste des mentions `@` (et l'identité de la
 * conversation ouverte), jamais un réglage: sans l'extra, le roster est celui du serveur.
 */
private const val EXTRA_DEMO_BOTS = "onyx.demoBots"

/** Les mots de la maquette: la phrase par défaut du crochet , mot pour mot. */
private const val VOICE_DEMO_WORDS = "Okay, prune anything older than thirty days and try the media share again"

/** Le titre de la conversation, tronqué à 170 points comme `soloHeader` le fait. */
private const val VOICE_DEMO_TITLE = "What's on my calendar tomorrow?"

/**
 * La **pose** de la scène vocale pour une capture (`-e onyx.voicePhase thinking`): un émulateur n'a
 * ni micro ni moteur de reconnaissance, donc les états d'erreur et les phases ne peuvent pas être
 * produits pour de vrai. Le drapeau les pose, comme `onyx.voiceDemo` pose déjà l'écoute — et il ne
 * change **rien** quand il est absent. Debug seulement, comme les autres.
 */
private const val EXTRA_VOICE_PHASE = "onyx.voicePhase"

/** Le bandeau d'un refus du moteur, posé pour une capture (`-e onyx.voiceProblem "…"`). Debug. */
private const val EXTRA_VOICE_PROBLEM = "onyx.voiceProblem"

/**
 * La doublure de QA des sous-agents (`am start --ez onyx.subagentDemo true`): un fil et un
 * roster **injectés** (une tâche qui travaille, une qui démarre, une lancée à l'aveugle, une
 * finie, une en échec), faute de Dashboard et d'agent sur un émulateur. Debug seulement; sans le
 * drapeau, rien n'est injecté.
 */
private const val EXTRA_SUBAGENT_DEMO = "onyx.subagentDemo"

/** La réplique de la pose « PARLE »: la phrase de la capture du propriétaire, mot pour mot. */
private const val VOICE_DEMO_REPLY = "Ça va bien, merci. Prêt à bosser. Toi, tu veux qu'on attaque quoi ?"

/** La phase demandée par [EXTRA_VOICE_PHASE], ou `null` si le drapeau est absent. */
private fun posedVoicePhase(raw: String?): VoicePhase? = when (raw?.trim()?.lowercase()) {
    "idle", "pret", "prêt", "ready" -> VoicePhase.IDLE
    "listening", "ecoute", "écoute" -> VoicePhase.LISTENING
    "thinking", "reflechit", "réfléchit" -> VoicePhase.THINKING
    "speaking", "parle" -> VoicePhase.SPEAKING
    else -> null
}

/** L'Activity derrière un `Context` de composition: ses `Intent` portent la maquette de boutique. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

/** Routes présentées par `.sheet` / `.fullScreenCover` dans les vues correspondantes. */
private val PresentedRoutes = setOf(
    Route.SETTINGS, Route.MODELS, Route.VOICE, Route.COMPOSER_EDITOR, Route.SELECT_TEXT,
    Route.PHOTO, Route.PDF, Route.VIDEO, Route.CAMERA, Route.TERMINAL, Route.SETUP, Route.CONNECT, Route.WHATS_NEW,
    Route.QUICK_CAPTURE,
    Route.BOT_EDITOR, Route.BOT_FACE, Route.API_BOT, Route.BOT_SCHEDULES, Route.BOT_VOICE, Route.SKILL_EDITOR, Route.SUBAGENT, Route.SUBAGENTS,
    Route.PATCH, Route.HIDDEN_BOTS, Route.SESSION_USAGE,
)
private val SheetDismissDestinations = setOf(
    Route.CHAT, Route.CONVERSATIONS, Route.BOTS, Route.SKILLS, Route.SERVERS, Route.SETTINGS, Route.HIDDEN_BOTS,
)
private val SectionRoutes = listOf(Route.CONVERSATIONS, Route.CRON, Route.KANBAN)
// Bots s'y ajoute: la liste des bots est une destination de la colonne, que le détail présente
// sans faire disparaître la barre latérale (téléphone: le détail du `NavigationSplitView`).
private val TwoPaneRoutes = setOf(Route.CHAT, Route.CONVERSATIONS, Route.CRON, Route.KANBAN, Route.BOTS)

/** Transitions de présentation; les ressorts natifs des sheets/segments restent approximés. */
private fun onyxRouteTransition(initialState: Route, targetState: Route): ContentTransform {
    if (targetState in PresentedRoutes && initialState !in PresentedRoutes) {
        return (
            slideInVertically(animationSpec = OnyxMotion.Sites.SheetOffset) { it } +
                fadeIn(animationSpec = OnyxMotion.Sites.Sheet)
            ).togetherWith(fadeOut(animationSpec = OnyxMotion.Sites.Sheet))
    }
    if (initialState in PresentedRoutes && targetState in SheetDismissDestinations) {
        return fadeIn(animationSpec = OnyxMotion.Sites.Sheet).togetherWith(
            slideOutVertically(animationSpec = OnyxMotion.Sites.SheetOffset) { it } +
                fadeOut(animationSpec = OnyxMotion.Sites.Sheet),
        )
    }
    if (initialState == Route.CHAT && targetState == Route.CONVERSATIONS) {
        return (
            slideInHorizontally(animationSpec = OnyxMotion.Sites.SidePanelOffset) { -it } +
                fadeIn(animationSpec = OnyxMotion.Sites.SidePanel)
            ).togetherWith(
            slideOutHorizontally(animationSpec = OnyxMotion.Sites.SidePanelOffset) { it } +
                fadeOut(animationSpec = OnyxMotion.Sites.SidePanel),
        )
    }
    if (initialState == Route.CONVERSATIONS && targetState == Route.CHAT) {
        return (
            slideInHorizontally(animationSpec = OnyxMotion.Sites.SidePanelOffset) { it } +
                fadeIn(animationSpec = OnyxMotion.Sites.SidePanel)
            ).togetherWith(
            slideOutHorizontally(animationSpec = OnyxMotion.Sites.SidePanelOffset) { -it } +
                fadeOut(animationSpec = OnyxMotion.Sites.SidePanel),
        )
    }
    val initialTab = SectionRoutes.indexOf(initialState)
    val targetTab = SectionRoutes.indexOf(targetState)
    if (initialTab >= 0 && targetTab >= 0 && initialTab != targetTab) {
        val forward = targetTab > initialTab
        return (
            slideInHorizontally(animationSpec = OnyxMotion.Sites.TabSelectionOffset) {
                if (forward) it else -it
            } + fadeIn(animationSpec = OnyxMotion.Sites.TabSelection)
            ).togetherWith(
            slideOutHorizontally(animationSpec = OnyxMotion.Sites.TabSelectionOffset) {
                if (forward) -it else it
            } + fadeOut(animationSpec = OnyxMotion.Sites.TabSelection),
        )
    }
    return EnterTransition.None.togetherWith(ExitTransition.None)
}

/**
 * La racine: le thème de l'utilisateur, puis la conversation et les écrans qu'elle ouvre.
 *
 * Le thème et l'apparence sont lus ici, si bien qu'un changement dans Réglages → Apparence
 * s'applique à toute l'app immédiatement.
 */
@Composable
fun OnyxApp() {
    val context = LocalContext.current
    val servers = AppContainer.servers
    val settingsRepo = AppContainer.settings
    val conversationRepo = AppContainer.conversations

    val theme by settingsRepo.theme.collectAsStateWithLifecycle()
    val appearance by settingsRepo.appearance.collectAsStateWithLifecycle()
    val themeColors by settingsRepo.themeColors.collectAsStateWithLifecycle()
    val displayName by settingsRepo.displayName.collectAsStateWithLifecycle()
    val autoCards by settingsRepo.autoCardLook.collectAsStateWithLifecycle()
    val autoVoice by settingsRepo.autoVoiceLook.collectAsStateWithLifecycle()
    // Réglages → Apparence: les visages de bots, leurs photos, leurs couleurs, le texte du chat
    // et l'orbe vocal (tous portés par `Settings.shared` ).
    val botFaces by settingsRepo.botFaces.collectAsStateWithLifecycle()
    val botPictures by settingsRepo.botPictures.collectAsStateWithLifecycle()
    val botColors by settingsRepo.botColors.collectAsStateWithLifecycle()
    val linkPreviews by settingsRepo.linkPreviews.collectAsStateWithLifecycle()
    val chatTextSize by settingsRepo.chatTextSize.collectAsStateWithLifecycle()
    val voiceOrb by settingsRepo.voiceOrb.collectAsStateWithLifecycle()
    val orbLight by settingsRepo.orbLight.collectAsStateWithLifecycle()
    // Le volet de gauche des deux panneaux, replié ou non: un réglage de l'appareil, donc relu
    // ici comme les autres réglages d'interface (Apparence, Voix…).
    val sidebarCollapsed by settingsRepo.sidebarCollapsed.collectAsStateWithLifecycle()

    // Les photos des bots arrivent une fois le roster lu: l'état est tenu ici pour que les
    // visages de toute l'app le voient, où qu'ils soient dessinés.
    var botPicturesByName by remember { mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }

    CompositionLocalProvider(
        com.onyx.app.ui.design.LocalBotFaces provides botFaces,
        com.onyx.app.ui.design.LocalBotPictures provides if (botPictures) botPicturesByName else emptyMap(),
    ) {
    OnyxAppTheme(theme = theme, appearance = appearance, colors = themeColors[theme.id]) {
        // Le verrou couvre tout: on ne voit rien de l'app avant de s'être authentifié.
        val appLock = remember { AppLock(context) }
        var locked by remember { mutableStateOf(appLock.isEnabled) }
        var leftAt by remember { mutableLongStateOf(0L) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner, appLock) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> leftAt = System.currentTimeMillis()
                    Lifecycle.Event.ON_START -> if (appLock.shouldLock(leftAt)) locked = true
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }

        if (locked) {
            LockScreen(lock = appLock, onUnlocked = { locked = false })
            return@OnyxAppTheme
        }

        val chatViewModel: ChatViewModel = viewModel(
            factory = viewModelFactory { initializer { ChatViewModel(context.applicationContext as android.app.Application, servers, conversationRepo, settingsRepo, AppContainer.drafts, AppContainer.turns) } },
        )
        val settingsViewModel: SettingsViewModel = viewModel(
            factory = viewModelFactory { initializer { SettingsViewModel(servers, settingsRepo) } },
        )

        val state by chatViewModel.state.collectAsStateWithLifecycle()
        val history by chatViewModel.history.collectAsStateWithLifecycle()
        // Les serveurs enregistrés, relus pour la liste unifiée (« All Servers »).
        val serverList by servers.servers.collectAsStateWithLifecycle()

        // Le roster est lu tôt: la conversation ouverte le dit deux fois — son fil (le visage, le
        // nom, le placeholder) et les notifications, qui nomment le bot dans son fil.
        val botsViewModel: BotsViewModel = viewModel(
            key = "bots-" + state.server.id + state.server.configSignature,
            factory = viewModelFactory {
                initializer {
                    BotsViewModel(
                        config = state.server,
                        faces = BotFaceStore(context.applicationContext),
                        activity = BotActivityStore(context.applicationContext),
                        groupRooms = com.onyx.app.data.local.GroupRoomStore(context.applicationContext),
                    )
                }
            },
        )
        val botsState by botsViewModel.state.collectAsStateWithLifecycle()
        // Doublure de QA (`am start -e onyx.demoBots true`): l'émulateur qui capture n'a aucun
        // Dashboard, donc aucun roster à lire, et la liste des mentions `@` a besoin de bots pour
        // se montrer. Le a le même crochet (`-echo.demoBots`), et la doublure ne prend la
        // place du serveur que sur cette demande explicite.
        val demoBotsRun = remember {
            context.findActivity()?.intent?.getBooleanExtra(EXTRA_DEMO_BOTS, false) == true
        }
        val bots = if (demoBotsRun) BotRosterStandIn.bots() else botsState.bots
        // Ce que la liste des mentions `@` propose: `BotRoster.listed` — le principal d'abord,
        // les bots cachés écartés, aucun quand le principal est seul.
        val mentionRoster = remember(bots, state.server.displayName) {
            BotMentions.rosterForMentions(bots, state.server.displayName.ifBlank { "Onyx" })
        }
        // Le nom de qui parle dans la conversation ouverte: le libellé du bot quand c'en est un,
        // sinon le nom d'agent des Réglages (ou celui du serveur quand il est vide).
        val openConversation = history.firstOrNull { it.id == state.conversationId }
        val openConversationBot = openConversation?.let { conversation ->
            botOfConversation(conversation, bots, state.server.displayName)
        }
        val threadName = openConversationBot?.label(state.server.displayName)
            ?: state.server.displayName.ifBlank { "Onyx" }

        var route by rememberSaveable { mutableStateOf(Route.CHAT.name) }
    // Pile de navigation: le geste de retour Android (bord d'écran) et le bouton doivent
    // remonter à l'écran précédent, comme le swipe-back — et non fermer l'app. `route`
    // reste la valeur courante pour tout le code existant; les affectations passent par `navTo`.
    val backStack = remember { mutableStateListOf<String>() }
    fun navTo(target: String) {
        route = navigateWithBackStack(route, target, backStack)
    }
    fun navBack(): Boolean {
        val previous = popRoute(backStack) ?: return false
        route = previous
        return true
    }

    /**
     * Quitte un écran dont l'état est vide. La route vide **n'entre pas** dans la pile: y revenir
     * n'aurait aucun sens (sa charge utile n'existe plus), et l'y laisser avalait le retour — voir
     * [navigateLeavingEmpty]. À appeler **hors de la composition** (`LaunchedEffect`): un `navTo`
     * écrit pendant la composition est une écriture d'état pendant la composition.
     */
    fun navFromEmpty(target: String) {
        route = navigateLeavingEmpty(route, target, backStack)
    }
        var startCards by remember { mutableStateOf<List<StartCard>>(emptyList()) }
        var voiceProblem by remember { mutableStateOf<String?>(null) }
        var groupComposerFocus by remember { mutableStateOf(false) }
        var editorPath by rememberSaveable { mutableStateOf<String?>(null) }
        var contextEditor by remember { mutableStateOf<Triple<String, String, String>?>(null) }
        var contextFileName by rememberSaveable { mutableStateOf<String?>(null) }
        // La page d'où l'on vient, pour que « retour » ramène au bon endroit.
        var contextReturn by rememberSaveable { mutableStateOf(Route.CONTEXT.name) }
        var selectedBot by remember { mutableStateOf<BotProfile?>(null) }
        // La salle choisie dans la liste des bots: elle teinte sa rangée et pré-remplit le chat
        // de groupe avec ses membres (le fil d'une salle du serveur n'est pas encore porté).
        var openedRoom by remember { mutableStateOf<GroupChat?>(null) }
        var botCronProfile by rememberSaveable { mutableStateOf<String?>(null) }
        // Le bot dont la voix se règle (`BotVoicePicker`), ouvrable depuis son éditeur comme de
        // son menu de tuile: le profil est gardé pour qu'un processus recréé retrouve le bon.
        var botVoiceProfile by rememberSaveable { mutableStateOf<String?>(null) }
        var renaming by remember { mutableStateOf(false) }
        // « Let bots message each other »: la question d'abord, comme le confirmationDialog.
        var askingLetBotsMessage by remember { mutableStateOf(false) }
        // Ce que le réglage « laisser les bots se parler » a donné: sans ça, l'appui ne montrait
        // rien du tout, ni réussite ni échec (« ça fonctionne pas »).
        var letBotsOutcome by remember { mutableStateOf<String?>(null) }
        var editingPrefix by remember { mutableStateOf<Int?>(null) }
        var patchText by remember { mutableStateOf<String?>(null) }
        var selectedText by remember { mutableStateOf<String?>(null) }
        var skillName by remember { mutableStateOf<String?>(null) }
        var openingRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var subagentAgent by remember { mutableStateOf<com.onyx.app.data.model.SubagentActivity?>(null) }
        // Doublure de QA des sous-agents (`onyx.subagentDemo`): le roster et le fil injectés,
        // faute de Dashboard et d'agent sur un émulateur. Sans le drapeau, la liste reste vide et
        // rien n'est injecté.
        val subagentDemoRun = remember {
            context.findActivity()?.intent?.getBooleanExtra(EXTRA_SUBAGENT_DEMO, false) == true
        }
        var demoSubagents by remember { mutableStateOf<List<com.onyx.app.data.model.SubagentActivity>>(emptyList()) }
        LaunchedEffect(subagentDemoRun) {
            if (subagentDemoRun) {
                val at = System.currentTimeMillis()
                demoSubagents = com.onyx.app.ui.chat.SubagentStandIn.agents(at)
                chatViewModel.seedSubagentDemo(com.onyx.app.ui.chat.SubagentStandIn.conversation(at))
            }
        }
        val shownSubagents = if (subagentDemoRun && demoSubagents.isNotEmpty()) demoSubagents else state.subagents
        var botModels by remember(state.server.id) { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(state.server.id) {
            botModels = runCatching { com.onyx.app.data.net.ChatCompletionsClient(state.server).testConnection() }
                .getOrNull()?.models.orEmpty()
        }
        var photoSet by remember { mutableStateOf<List<com.onyx.app.data.model.Attachment>>(emptyList()) }
        var photoIndex by remember { mutableStateOf(0) }
        // Le PDF montré dans le lecteur de l'app: son adresse et son nom.
        var pdfSource by remember { mutableStateOf<com.onyx.app.ui.chat.PdfSource?>(null) }
        // La vidéo montrée dans le lecteur de l'app: son adresse et son nom.
        var videoSource by remember { mutableStateOf<com.onyx.app.ui.chat.VideoSource?>(null) }
        var prefixDraft by remember { mutableStateOf<SpokenPrefix?>(null) }
        // Le scanner et sa confirmation sont des feuilles au-dessus du formulaire d'origine.
        // Le brouillon vit ici pour ne pas être perdu quand la caméra prend toute la surface.
        var setupDraft by remember(state.server.id) { mutableStateOf(state.server) }
        var setupScannerOpen by remember { mutableStateOf(false) }
        var setupOffer by remember { mutableStateOf<SetupCodeOffer?>(null) }
        var setupOfferOriginal by remember { mutableStateOf<ServerConfig?>(null) }
        var setupOfferServers by remember { mutableStateOf<List<ServerConfig>>(emptyList()) }
        var setupOfferUnused by remember { mutableStateOf(false) }
        var setupOfferFirstRun by remember { mutableStateOf(false) }
        var forceFirstRunSetup by remember { mutableStateOf(false) }
        var setupOffersEditing by remember { mutableStateOf(true) }
        var setupCodeEditing by remember { mutableStateOf(false) }
        val spokenPrefixes by settingsRepo.spokenPrefixes.collectAsStateWithLifecycle()
        var renameText by remember { mutableStateOf("") }

        // Les cartes de départ suivent l'historique sans le relire à chaque recomposition.
        LaunchedEffect(history, state.messages.size) {
            startCards = chatViewModel.startCards()
        }

        val scope = rememberCoroutineScope()

        /**
         * Ouvre un fichier joint avec le lecteur qui lui convient: le lecteur de PDF de l'app,
         * le visualiseur d'images, ou l'app du système qui sait lire le reste.
         */
        fun openFile(uri: android.net.Uri, name: String, mime: String) {
            when (com.onyx.app.ui.chat.FileViewers.kind(name, mime)) {
                com.onyx.app.ui.chat.FileViewerKind.PDF -> {
                    pdfSource = com.onyx.app.ui.chat.PdfSource(
                        uri = uri,
                        name = name.ifEmpty { "PDF" },
                    )
                    navTo(Route.PDF.name)
                }

                com.onyx.app.ui.chat.FileViewerKind.PHOTO -> {
                    photoSet = listOf(
                        com.onyx.app.data.model.Attachment(
                            id = uri.toString(),
                            mimeType = mime.ifEmpty { com.onyx.app.ui.chat.FileViewers.mimeFor(name) },
                            uri = uri.toString(),
                            name = name,
                        ),
                    )
                    photoIndex = 0
                    navTo(Route.PHOTO.name)
                }

                com.onyx.app.ui.chat.FileViewerKind.VIDEO -> {
                    // La vidéo se lit dans l'app, comme le PDF et l'image: sa première image sert
                    // de vignette dans le fil, et l'appui ouvre le lecteur.
                    videoSource = com.onyx.app.ui.chat.VideoSource(
                        uri = uri,
                        name = name.ifEmpty { uri.lastPathSegment?.substringAfterLast('/') ?: "Video" },
                    )
                    navTo(Route.VIDEO.name)
                }

                com.onyx.app.ui.chat.FileViewerKind.EXTERNAL -> {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            uri,
                            mime.ifEmpty { com.onyx.app.ui.chat.FileViewers.mimeFor(name) },
                        )
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        context.startActivity(android.content.Intent.createChooser(intent, name))
                    }.onFailure { error ->
                        chatViewModel.showProblem(error.message ?: OnyxLocalization["Couldn't open it."])
                    }
                }
            }
        }

        LaunchedEffect(state.server) {
            if (setupOffer == null && !setupCodeEditing) setupDraft = state.server
        }

        fun presentSetupOffer(offer: SetupCodeOffer) {
            val source = if (route == Route.SETUP.name || !state.server.isUsable || forceFirstRunSetup) setupDraft else state.server
            setupOfferOriginal = source
            setupOfferServers = servers.servers.value
            setupOfferUnused = source.apiKey.isEmpty() && source.profileKey.isEmpty() &&
                source.servePassword.isEmpty() && source.accessSecret.isEmpty() && source.customHeaders.isEmpty()
            setupOfferFirstRun = !state.server.isUsable || forceFirstRunSetup
            setupOffersEditing = !setupOfferFirstRun
            setupOffer = offer
            setupScannerOpen = false
        }

        // La langue du micro et le mode mains-libres viennent des Réglages.
        val speechLocale by settingsRepo.speechLocale.collectAsStateWithLifecycle()
        val handsFreeByDefault by settingsRepo.handsFreeByDefault.collectAsStateWithLifecycle()
        val returnSends by settingsRepo.returnSends.collectAsStateWithLifecycle()

        val voiceSpeed by settingsRepo.voiceSpeed.collectAsStateWithLifecycle()
        val replyVoice by settingsRepo.replyVoice.collectAsStateWithLifecycle()
        val kokoroUrl by settingsRepo.kokoroUrl.collectAsStateWithLifecycle()
        val kokoroVoice by settingsRepo.kokoroVoice.collectAsStateWithLifecycle()
        val talkOver by settingsRepo.talkOver.collectAsStateWithLifecycle()
        val earpieceAtEar by settingsRepo.earpieceAtEar.collectAsStateWithLifecycle()
        val showLiveActivity by settingsRepo.showLiveActivity.collectAsStateWithLifecycle()

        val voice = remember(speechLocale) { VoiceSession(context, scope, speechLocale) }

        val onSoloVoiceQuestion = remember(chatViewModel, settingsRepo) {
            { question: String ->
                val routed = routeVoiceText(question, settingsRepo.spokenPrefixes.value)
                chatViewModel.sendText(routed.text, clearInput = false, modelOverride = routed.model)
            }
        }
        DisposableEffect(voice) {
            voice.onQuestion = onSoloVoiceQuestion
            // « Stop » dit par-dessus la réponse: le tour qui s'écrit côté serveur n'a plus de
            // raison de continuer (`conversation.cancel` ).
            voice.onStoppedByWord = { chatViewModel.stop() }
            onDispose { voice.release() }
        }

        // La vitesse de lecture et l'interruption suivent les Réglages, sans recréer la session.
        val stopPhrases by settingsRepo.stopPhrases.collectAsStateWithLifecycle()
        // La voix de chaque bot (`Settings.botVoices`): c'est elle qui fait que deux bots de la
        // même salle ne lisent pas de la même voix.
        val botVoicesHere by settingsRepo.botVoices.collectAsStateWithLifecycle()

        LaunchedEffect(
            voice,
            voiceSpeed,
            talkOver,
            earpieceAtEar,
            stopPhrases,
            replyVoice,
            kokoroUrl,
            kokoroVoice,
            state.server.configSignature,
            botVoicesHere,
        ) {
            voice.output.setRate(voiceSpeed)
            voice.output.configure(
                voice = replyVoice,
                kokoroUrl = kokoroUrl,
                kokoroVoice = kokoroVoice,
                hermesServer = state.server,
                // Les voix réglées pour les bots de ce serveur (`Settings.botVoicesHere`): la clé
                // de l'effet est la carte elle-même, donc un réglage relance la lecture.
                botVoices = settingsRepo.botVoicesHere(state.server.id),
            )
            voice.talkOver = talkOver
            // « Earpiece when raised to your ear »: téléphone levé à l'oreille, la réponse s'y
            // joue comme un appel (capteur de proximité → écouteur).
            voice.earRoutingEnabled = earpieceAtEar
            voice.stopPhrases = stopPhrases
        }

        val phase by voice.phase.collectAsStateWithLifecycle()
        var readingMessageId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(phase) {
            if (phase != VoicePhase.SPEAKING) readingMessageId = null
        }
        val heard by voice.heard.collectAsStateWithLifecycle()
        val spoken by voice.spoken.collectAsStateWithLifecycle()
        val handsFree by voice.handsFree.collectAsStateWithLifecycle()
        val speech by voice.engine.state.collectAsStateWithLifecycle()

        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                voiceProblem = null
                voice.startListening()
            } else {
                voiceProblem = "Onyx needs the microphone to listen. Allow it in Android's Settings → Apps → Onyx."
            }
        }

        fun openVoice() {
            voiceProblem = null
            navTo(Route.VOICE.name)
            if (handsFreeByDefault && !voice.handsFree.value) voice.toggleHandsFree()
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                if (phase != VoicePhase.LISTENING) voice.startListening()
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Le tour est fini: l'agent lit sa réponse, puis se remet à écouter en mains-libres.
        LaunchedEffect(state.isStreaming, route) {
            if (route != Route.GROUP_VOICE.name && !state.isStreaming && voice.phase.value == VoicePhase.THINKING) {
                val reply = state.messages.lastOrNull { it.role == Role.ASSISTANT }?.text
                voice.finishTurn(reply)
            }
        }

        // Le tour en cours se suit dans la barre d'état, et survit à l'app mise de côté.
        // La notification s'égrène: elle dit quel outil tourne et depuis combien de temps, d'où
        // la boucle d'une seconde — sans elle, un tour long resterait figé sur « Answering… ».
        LaunchedEffect(state.isStreaming, showLiveActivity) {
            if (state.isStreaming && showLiveActivity) {
                val startedAt = System.currentTimeMillis()
                var first = true
                while (true) {
                    val now = chatViewModel.state.value
                    val tool = now.messages.lastOrNull { it.role == Role.ASSISTANT }
                        ?.tools?.lastOrNull { it.isRunning }
                    val percent = now.prefill?.let { (it.fraction * 100).toInt() }
                    val elapsed = (System.currentTimeMillis() - startedAt) / 1000
                    val phase = NotificationCopy.turnPhase(percent, tool?.name, elapsed)
                    if (first) {
                        TurnService.start(
                            context = context,
                            title = threadName,
                            phase = phase,
                            percent = percent,
                        )
                        first = false
                    } else {
                        TurnService.phase(context, phase, percent)
                    }
                    delay(1_000)
                }
            } else {
                TurnService.stop(context)
            }
        }

        // Une page d'agent qui a posé une question reçoit la réponse par ce bus.
        LaunchedEffect(state.messages.size, state.isStreaming) {
            if (!state.isStreaming) {
                state.messages.lastOrNull { it.role == com.onyx.app.data.model.Role.ASSISTANT }
                    ?.text
                    ?.let { com.onyx.app.ui.rich.PageReplyBus.publish(it) }
            }
        }

        // Le widget d'écran d'accueil suit la dernière réponse et le remplissage du contexte.
        LaunchedEffect(state.messages.size, state.isStreaming) {
            if (!state.isStreaming) {
                val used = state.messages.lastOrNull { it.usage?.contextUsed != null }?.usage?.contextUsed
                AppContainer.publishContext(used, state.server.contextWindow.takeIf { it > 0 })
                runCatching { OnyxWidget().updateAll(context) }
            }
        }
        // Les approbations qui arrivent pendant que l'app n'est pas au premier plan se notifient.
        val pendingApprovals = state.messages.flatMap { it.approvals }.filter { it.isPending }
        val notifyInBackground by settingsRepo.notifyInBackground.collectAsStateWithLifecycle()
        val notificationSound by settingsRepo.notificationSound.collectAsStateWithLifecycle()
        // `lifecycle.currentState` ne doit pas être lu pendant la composition (lint
        // LifecycleCurrentStateInComposition: la valeur ne serait jamais recomposée). On suit
        // donc les événements du cycle de vie.
        val lifecycleOwner = LocalLifecycleOwner.current
        var inForeground by remember { mutableStateOf(true) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> inForeground = true
                    Lifecycle.Event.ON_PAUSE -> inForeground = false
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(pendingApprovals.size, inForeground, notifyInBackground, notificationSound) {
            if (inForeground || !notifyInBackground) {
                pendingApprovals.forEach { ApprovalNotifier.clear(context, it.id) }
            } else {
                pendingApprovals.forEach {
                    ApprovalNotifier.notifyApproval(
                        context = context,
                        id = it.id,
                        command = it.command,
                        title = threadName,
                        runId = it.runId,
                        serverId = state.server.id,
                        enabled = notifyInBackground,
                        sound = notificationSound,
                    )
                }
            }
        }

        // Les questions de l'agent — question, sudo, secret — se répondent de la même façon, avec
        // une action « Reply » quand la saisie en ligne suffit.
        val pendingQuestions = remember(state.messages) { pendingQuestionsOf(state.messages) }
        LaunchedEffect(pendingQuestions.map { it.notificationId }, inForeground, notifyInBackground, notificationSound) {
            if (inForeground || !notifyInBackground) {
                pendingQuestions.forEach { QuestionNotifier.clear(context, it.notificationId) }
            } else {
                pendingQuestions.forEach { question ->
                    QuestionNotifier.notifyQuestion(
                        context = context,
                        id = question.notificationId,
                        prompt = question.prompt,
                        kind = question.kind,
                        requestId = question.requestId,
                        questionId = question.questionId,
                        sessionId = state.serverSessionId,
                        name = threadName,
                        canReplyInline = NotificationCopy.canAnswerInline(
                            kind = question.kind,
                            hasSession = !state.serverSessionId.isNullOrBlank(),
                            questionCount = question.questionCount,
                        ),
                        enabled = notifyInBackground,
                        sound = notificationSound,
                    )
                }
            }
        }

        // Ce qui vient de l'extérieur: un partage, une question de l'assistant, un raccourci.
        val pendingIntake by ShareInbox.pending.collectAsStateWithLifecycle()
        val pendingShare by ShareInbox.pendingFile.collectAsStateWithLifecycle()
        val voiceRequested by ShareInbox.voiceRequested.collectAsStateWithLifecycle()
        // « Capture rapide » demandée depuis l'extérieur (raccourci de l'illustration): l'app
        // s'ouvre sur la note, **sans toucher à la conversation ouverte**.
        val captureRequested by ShareInbox.captureRequested.collectAsStateWithLifecycle()

        // « L'app reparaît »: les notes en attente retentent leur remise, comme le
        // `CaptureInbox.file` appelé quand l'app revient au premier plan dans le modèle.
        // Sans ça, une note restée en attente n'aurait reparti qu'au prochain appui.
        LaunchedEffect(Unit) { com.onyx.app.data.capture.QuickCapture.fileNow() }

        LaunchedEffect(pendingIntake, pendingShare, voiceRequested, captureRequested) {
            val question = pendingIntake
            val shared = pendingShare
            when {
                // Un fichier partagé se joint au message: le texte du partage, s'il y en a un,
                // part avec lui, comme le fait le bouton d'envoi du composeur.
                shared != null -> {
                    chatViewModel.addAttachments(listOf(android.net.Uri.parse(shared.uri)))
                    question?.let { chatViewModel.sendText(it, clearInput = false) }
                    ShareInbox.consume()
                }

                question != null -> {
                    chatViewModel.sendText(question, clearInput = false)
                    ShareInbox.consume()
                }

                voiceRequested -> {
                    ShareInbox.consume()
                    navTo(Route.VOICE.name)
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) voice.startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }

                captureRequested -> {
                    ShareInbox.consume()
                    navTo(Route.QUICK_CAPTURE.name)
                }
            }
        }
        if (setupScannerOpen) {
            SetupCodeScanner(
                onCode = ::presentSetupOffer,
                onCancel = { setupScannerOpen = false },
            )
            return@OnyxAppTheme
        }

        val pendingSetupOffer = setupOffer
        val setupOrigin = setupOfferOriginal
        if (pendingSetupOffer != null && setupOrigin != null) {
            SetupCodeSheet(
                offer = pendingSetupOffer,
                activeServer = setupOrigin,
                servers = setupOfferServers,
                activeServerIsUnused = setupOfferUnused,
                offersEditing = setupOffersEditing,
                newServerId = servers::newId,
                onInstall = { config, activate ->
                    servers.upsert(config)
                    if (activate) servers.setActive(config.id)
                },
                onRemoveAdded = { added, previous ->
                    servers.setActive(previous)
                    servers.remove(added)
                },
                onEditConnection = { config ->
                    setupDraft = config
                    setupCodeEditing = true
                    setupOffer = null
                    navTo(Route.SETUP.name)
                },
                onFinish = { finish ->
                    setupOffer = null
                    setupCodeEditing = false
                    setupDraft = servers.activeServer.value
                    if (finish is SetupCodeFinish.Saved && setupOfferFirstRun) {
                        forceFirstRunSetup = !finish.connected
                        if (finish.connected) navTo(Route.CHAT.name)
                    }
                },
            )
            return@OnyxAppTheme
        }

        // Maquette de boutique. a `-echo.voiceDemo` pour la même raison: le simulateur n'a
        // pas de micro, et l'émulateur non plus. C'est un crochet **debug seulement** —
        // `BuildConfig.DEBUG` est une constante fausse dans l'APK de release, où ce chemin
        // n'existe donc pas. `am start -n com.onyx.app/.MainActivity -e onyx.voiceDemo true`.
        val voicePose = remember {
            if (!com.onyx.app.BuildConfig.DEBUG) null
            else posedVoicePhase(context.findActivity()?.intent?.getStringExtra(EXTRA_VOICE_PHASE))
        }
        val posedProblem = remember {
            if (!com.onyx.app.BuildConfig.DEBUG) null
            else context.findActivity()?.intent?.getStringExtra(EXTRA_VOICE_PROBLEM)
        }
        val voiceDemo = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                (voicePose != null ||
                    context.findActivity()?.intent?.getBooleanExtra(EXTRA_VOICE_DEMO, false) == true)
        }
        // La même chose pour la scène vocale d'une salle (`-e onyx.groupVoiceDemo speaking`): une
        // salle scriptée, sans serveur ni micro, pour montrer la scène de parole et l'éprouver.
        val groupVoiceDemoOn = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                context.findActivity()?.intent?.getStringExtra(EXTRA_GROUP_VOICE_DEMO) != null
        }
        LaunchedEffect(voiceDemo, groupVoiceDemoOn) {
            if (groupVoiceDemoOn) {
                voiceProblem = null
                navTo(Route.GROUP_VOICE.name)
            } else if (voiceDemo) {
                // La pose d'une capture ne pose que l'écran: le drapeau du problème, s'il est là,
                // doit survivre (c'est justement ce que la capture montre).
                voiceProblem = posedProblem
                navTo(Route.VOICE.name)
            }
        }

        // Aucun serveur: l'écran de connexion, comme au premier lancement. La maquette de
        // boutique passe outre: elle ne parle à aucun serveur, elle pose l'écran vocal.
        if ((!state.server.isUsable || forceFirstRunSetup) && !voiceDemo && !groupVoiceDemoOn) {
            SetupScreen(
                config = setupDraft,
                onChange = { transform -> setupDraft = transform(setupDraft) },
                onDone = {
                    settingsViewModel.open(setupDraft)
                    settingsViewModel.saveAndClose()
                    forceFirstRunSetup = false
                },
                scope = scope,
                onScanCode = { setupScannerOpen = true },
                onPasteCode = { text ->
                    SetupCodeOffer.fromText(text)?.let { offer ->
                        presentSetupOffer(offer)
                        true
                    } == true
                },
            )
            return@OnyxAppTheme
        }

        // Un changement de serveur (URL comprise) relance la lecture du roster et des
        // conversations: sinon l'ancienne erreur — « Unable to resolve host … » — restait
        // affichée alors que la nouvelle adresse répondait déjà, et « Réessayer » la rejouait.
        LaunchedEffect(state.server.id, state.server.configSignature) {
            botsViewModel.rebind(state.server)
            botsViewModel.refresh()
            conversationRepo.load()
        }

        // « All Servers »: chaque autre serveur enregistré est interrogé, tous à la fois, chacun
        // avec sa patience (`AllServers.PATIENCE_MS`); un serveur qui ne répond pas est nommé
        // au-dessus de la liste plutôt que de laisser un vide (`serversUnreachable` ). Un
        // serveur sans adresse n'est pas interrogé: on ne nomme pas ce qui n'a jamais existé.
        var unreachableServers by remember {
            mutableStateOf<List<com.onyx.app.ui.conversations.ServerTag>>(emptyList())
        }
        LaunchedEffect(state.server.id, serverList.map { it.id + it.configSignature }) {
            val others = serverList.filter { it.id != state.server.id && it.isUsable }
            if (others.isEmpty()) {
                unreachableServers = emptyList()
                return@LaunchedEffect
            }
            val answered = coroutineScope {
                others.map { server ->
                    async {
                        server to (runCatching {
                            withTimeout(com.onyx.app.ui.conversations.AllServers.PATIENCE_MS) {
                                com.onyx.app.data.net.ConnectionTester.current(server)
                            }
                        }.getOrNull()?.ok == true)
                    }
                }.awaitAll()
            }
            unreachableServers = answered.filterNot { (_, ok) -> ok }
                .map { (server, _) -> com.onyx.app.ui.conversations.ServerTag(server.id, server.displayName) }
        }

        // La mémoire du fil ouvert, serveur par serveur: `ServerSwitcher` enregistre la
        // conversation qu'on quitte avant de basculer. Revenir sur un serveur rouvre donc le fil
        // qu'on y lisait, pas seulement le plus récent de ce serveur.
        val lastThreadByServer = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
        LaunchedEffect(state.server.id, state.conversationId, history) {
            val id = state.conversationId ?: return@LaunchedEffect
            if (id.isNotBlank() && history.any { it.id == id }) lastThreadByServer[state.server.id] = id
        }
        LaunchedEffect(state.server.id) {
            val remembered = lastThreadByServer[state.server.id] ?: return@LaunchedEffect
            val thread = history.firstOrNull { it.id == remembered && it.serverId == state.server.id }
            if (thread != null && thread.id != state.conversationId) chatViewModel.open(thread)
        }

        // La conversation d'un autre serveur qu'on a demandé d'ouvrir: Onyx bascule d'abord
        // (`setActive`), puis l'ouvre ici quand le nouveau serveur est en place — sinon le
        // changement de serveur, qui vide le fil, écraserait l'ouverture (`load` ).
        var pendingRemoteOpen by remember { mutableStateOf<Conversation?>(null) }
        LaunchedEffect(state.server.id, pendingRemoteOpen) {
            val pending = pendingRemoteOpen ?: return@LaunchedEffect
            if (pending.serverId == state.server.id) {
                pendingRemoteOpen = null
                chatViewModel.open(pending)
                navTo(Route.CHAT.name)
            }
        }

        // Les marques de lecture des conversations: ce que ce téléphone a vu de chacune, comme
        // `ChatActivity.seen` — il n'y a pas de registre serveur de lecture ici. « Mark as
        // Unread » écrit 0, « Mark as Read » l'heure courante, et la rangée les lit.
        val conversationMarks = remember { BotActivityStore(context) }
        var unreadMarks by remember { mutableStateOf(emptyMap<String, Double?>()) }
        LaunchedEffect(history, state.server.configSignature) {
            unreadMarks = history.associate { conversation ->
                conversation.id to conversationMarks.seen(state.server.configSignature, "conversation:" + conversation.id)
            }
        }

        // Le tap d'une tuile de bot: son **chat** (le `openBot` ), jamais son éditeur. Sa
        // conversation est celle que son `canonical_session` désigne (`BotProfile.chatId`), gardée
        // pour le retrouver; un bot sans chat nommé en ouvre une neuve, que le premier tour lie au
        // serveur — le port n'ayant pas de transport de sessions, il ne peut pas la faire là-bas.
        val openBotChat: (BotProfile) -> Unit = { bot ->
            val chat = botChatToOpen(
                bot = bot,
                conversations = history,
                serverId = state.server.id,
                mainName = state.server.displayName,
            )
            if (history.none { it.id == chat.id }) scope.launch { conversationRepo.save(chat) }
            chatViewModel.open(chat)
            // Le chat du bot est ouvert: ce qu'il a dit est vu, comme `activity.botSeen`.
            botsViewModel.markBotSeen(bot)
            navTo(Route.CHAT.name)
        }

        // « Mark as Read » / « Mark as Unread » d'une conversation, comme le `setUnread`:
        // la marque est écrite ici et la liste la relit tout de suite.
        val setConversationUnread: (Conversation, Boolean) -> Unit = { conversation, unread ->
            val key = "conversation:" + conversation.id
            if (unread) {
                conversationMarks.markUnread(state.server.configSignature, key)
            } else {
                conversationMarks.markSeen(
                    state.server.configSignature,
                    key,
                    conversation.updatedAt / 1000.0,
                )
            }
            unreadMarks = unreadMarks + (conversation.id to conversationMarks.seen(state.server.configSignature, key))
        }

        // « Pictures from Hermes »: les photos du roster, une fois par lancement et par profil.
        LaunchedEffect(botPictures, botsState.bots) {
            if (botPictures) botsViewModel.loadPictures()
        }
        LaunchedEffect(botsViewModel) {
            botsViewModel.pictures.collect { botPicturesByName = it }
        }
        // Les projets du serveur (dossiers de travail), lus par le RPC du Dashboard — la source
                var serverProjects by remember(state.server.id, state.server.configSignature) {
            mutableStateOf<List<com.onyx.app.data.net.ServerProject>>(emptyList())
        }
        LaunchedEffect(state.server.id, state.server.configSignature) {
            if (!state.server.hasServeCredentials) {
                serverProjects = emptyList()
                return@LaunchedEffect
            }
            serverProjects = runCatching {
                val rpc = com.onyx.app.data.net.ServeRpc(state.server)
                try {
                    com.onyx.app.data.net.ChatProjectsApi(
                        rpc,
                        com.onyx.app.data.net.ServeFilesApi(com.onyx.app.data.net.ServeClient(state.server)),
                    ).tree()
                } finally {
                    rpc.close()
                }
            }.getOrDefault(emptyList())
        }
        // Le projet ouvert, et la session dont on regarde l'usage (les deux routes nouvelles).
        var selectedProject by remember { mutableStateOf<com.onyx.app.data.net.ServerProject?>(null) }
        var usageSummary by remember { mutableStateOf<com.onyx.app.data.net.SessionSummary?>(null) }
        var usageFetchId by remember { mutableStateOf<String?>(null) }
        // Les bots choisis dans « New Group Chat », posés sur la salle à son ouverture.
        var groupPicked by remember { mutableStateOf<Set<String>?>(null) }
        /** Le nom donné à la salle dans la feuille (`NewGroupChatView`), ou celui d'une salle gardée ici. */
        var groupTitle by remember { mutableStateOf<String?>(null) }

        /**
         * Ouvre une salle depuis la liste. Une salle du serveur montre son journal (`GroupRoomScreen`,
         * `groups.log`); une salle **gardée ici**, faute de Dashboard, ouvre son fil local avec ses
         * membres — le serveur ne la connaît pas.
         */
        fun openGroup(room: GroupChat) {
            botsViewModel.markRoomSeen(room)
            if (room.local) {
                openedRoom = null
                val picked = room.members.map { it.name }.toSet()
                groupPicked = picked
                groupTitle = room.name
            } else {
                openedRoom = room
                groupPicked = null
                groupTitle = null
            }
            navTo(Route.GROUP_CHAT.name)
        }

        // Retour système: le geste du bord, le bouton et le retour prédictif déclaré au manifeste
        // sont traités dans la mise en page (voir le `BackHandler` du `BoxWithConstraints`), qui sait
        // si l'écran est en deux panneaux et quelle route est présentée.

        val chatPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { showListButton, sidebarToggle ->
            // La conversation ouverte est-elle celle d'un bot ? (`BotProfile.chatId`, le
            // `canonical_session` du serveur.) Elle le dit deux fois: l'en-tête du fil montre son
            // visage et son nom, et — sous « Bot colors in their chats » — ses bulles et ses
            // boutons prennent la couleur de son visage, comme `ContentView` 
            // (`BotFace.chatColors`); les groupes gardent celle du thème.
            val openedConversation = history.firstOrNull { it.id == state.conversationId }
            val openedConversationBot = openedConversation?.let { conversation ->
                botOfConversation(conversation, bots, state.server.displayName)
            }
            val botChatColors = if (botColors) {
                openedConversationBot?.let { bot ->
                    (com.onyx.app.ui.design.BotFace.parse(bot.faceCode)
                        ?: com.onyx.app.ui.design.BotFace.forName(if (bot.isDefault) "default" else bot.name))
                        .chatColors()
                }
            } else {
                null
            }
            CompositionLocalProvider(LocalOnyxTheme provides LocalOnyxTheme.current.withColors(botChatColors)) {
            ChatScreen(
                state = state,
                startCards = startCards,
                // « Shown in the header and the message box »: le nom des Réglages → Apparence.
                assistantName = displayName.trim(),
                onInputChange = chatViewModel::onInputChange,
                onSend = chatViewModel::send,
                onSteer = chatViewModel::steer,
                onConsumeSlash = chatViewModel::consumeSlash,
                onStop = chatViewModel::stop,
                onRetry = chatViewModel::retry,
                onClearError = chatViewModel::clearError,
                onNewConversation = chatViewModel::newConversation,
                onOpenConversations = if (showListButton) {
                    { navTo(Route.CONVERSATIONS.name) }
                } else {
                    // Replié, c'est le contrôle de la barre (`sidebar.left`) qui rouvre le volet:
                    // la liste n'est pas une destination, c'est un panneau.
                    null
                },
                onPickModel = { navTo(Route.MODELS.name) },
                onModelCommand = {
                    // « /model »: on baisse la demande puis on ouvre le sélecteur, exactement
                    // comme le fait l'en-tête (`onPickModel`).
                    chatViewModel.consumeModelPickerRequest()
                    navTo(Route.MODELS.name)
                },
                onOpenSettings = { navTo(Route.SETTINGS.name) },
                onOpenVoice = {
                    readingMessageId = null
                    voice.stop()
                    openVoice()
                },
                onReadAloud = { message ->
                    if (readingMessageId == message.id && phase == VoicePhase.SPEAKING) {
                        readingMessageId = null
                        voice.stop()
                    } else {
                        voice.stop()
                        readingMessageId = message.id
                        voice.speak(message.text, listenAfterSpeech = false)
                    }
                },
                readingMessageId = readingMessageId,
                onExpandDraft = { navTo(Route.COMPOSER_EDITOR.name) },
                onOpenCard = { card ->
                    history.firstOrNull { it.id == card.id }?.let { chatViewModel.open(it) }
                },
                menuItems = buildList {
                    add(MenuItem("Find in conversation", Symbols.MagnifyingGlass) { chatViewModel.toggleSearch() })
                    add(MenuItem("Rename", Symbols.Pencil) {
                        renameText = ""
                        renaming = true
                    })
                    add(MenuItem("Export as Markdown", Symbols.Share) {
                        val title = history.firstOrNull { it.messages.isNotEmpty() }?.title
                            ?: com.onyx.app.data.model.Conversation.DefaultTitle
                        scope.launch { shareTranscript(context, chatViewModel.exportMarkdown(threadName), title) }
                    })
                    add(MenuItem("New Conversation", Symbols.SquareAndPencil) { chatViewModel.newConversation() })
                    // « Capture rapide »: une note en passant, depuis le fil, sans quitter ce
                    // qu'on regarde (la note part dans sa propre conversation, « Notes »).
                    add(MenuItem("Take a note", Symbols.SquareAndPencil) { navTo(Route.QUICK_CAPTURE.name) })
                    add(MenuItem("Computer", Symbols.DesktopComputer) { navTo(Route.COMPUTER.name) })
                    add(MenuItem("Terminal", Symbols.Terminal) { navTo(Route.TERMINAL.name) })
                    add(MenuItem("Settings", Symbols.Gearshape) { navTo(Route.SETTINGS.name) })
                },
                onSearch = chatViewModel::setSearchQuery,
                onFindRequested = chatViewModel::toggleSearch,
                onStepSearch = chatViewModel::stepSearch,
                onOpenSettingsFromError = { navTo(Route.SETTINGS.name) },
                onOpenPatch = { text ->
                    patchText = text
                    navTo(Route.PATCH.name)
                },
                onSelectText = { text ->
                    selectedText = text
                    navTo(Route.SELECT_TEXT.name)
                },
                onOpenCamera = { navTo(Route.CAMERA.name) },
                onClarify = chatViewModel::respondClarify,
                onSudo = chatViewModel::respondSudo,
                onSecret = chatViewModel::respondSecret,
                onSendQueued = chatViewModel::sendQueuedNow,
                onEditQueued = chatViewModel::editQueued,
                onDeleteQueued = chatViewModel::deleteQueued,
                onOpenSubagent = { tool ->
                    subagentAgent = tool
                    navTo(Route.SUBAGENT.name)
                },
                onOpenSubagents = { navTo(Route.SUBAGENTS.name) },
                subagentAgents = if (subagentDemoRun) demoSubagents else null,
                onOpenPhotos = { attachments, index ->
                    photoSet = attachments
                    photoIndex = index
                    navTo(Route.PHOTO.name)
                },
                onOpenFile = { attachment ->
                    openFile(
                        uri = android.net.Uri.parse(attachment.uri),
                        name = attachment.name.orEmpty(),
                        mime = attachment.mimeType,
                    )
                },
                onOpenPending = { pending ->
                    openFile(uri = pending.uri, name = pending.name, mime = pending.mimeType)
                },
                onApproval = chatViewModel::respondApproval,
                onAddAttachments = chatViewModel::addAttachments,
                onRemoveAttachment = chatViewModel::removeAttachment,
                onOpenDelivered = { file ->
                    // Un PDF ou une image livrés s'ouvrent dans Onyx; le reste part à l'app qui
                    // sait le lire. Le fichier passe d'abord par le cache du téléphone.
                    if (com.onyx.app.ui.chat.FileViewers.kind(file.name) ==
                        com.onyx.app.ui.chat.FileViewerKind.EXTERNAL
                    ) {
                        scope.launch {
                            com.onyx.app.ui.chat.DeliveredFiles.open(context, state.server, file)
                        }
                    } else {
                        scope.launch {
                            com.onyx.app.ui.chat.DeliveredFiles.local(context, state.server, file)
                                .onSuccess { local ->
                                    openFile(
                                        uri = android.net.Uri.fromFile(local),
                                        name = file.name,
                                        mime = com.onyx.app.ui.chat.FileViewers.mimeFor(file.name),
                                    )
                                }
                                .onFailure { error ->
                                    chatViewModel.showProblem(
                                        com.onyx.app.ui.chat.DeliveredFiles.failureText(error, file),
                                    )
                                }
                        }
                    }
                },
                returnSends = returnSends,
                speechLocale = speechLocale,
                chatTextScale = com.onyx.app.ui.theme.ResolvedTheme.chatTextScale(chatTextSize),
                linkPreviews = linkPreviews,
                // L'en-tête du fil: le visage et le nom du bot dont c'est la conversation, sinon
                // ceux de Onyx (ajout volontaire côté Android).
                threadBot = openedConversationBot,
                // La liste des mentions `@` du composeur: les bots du roster, comme la section
                // « Bots » les montre.
                mentionBots = mentionRoster,
                sidebarToggle = sidebarToggle,
            )
            }
        }

        val conversationsPane: @Composable (Boolean, Route, (@Composable () -> Unit)?) -> Unit =
            { wideDetail, detailRoute, sidebarControl ->
            ConversationsScreen(
                conversations = history,
                bots = bots,
                loading = botsState.loading,
                error = botsState.problem,
                currentId = if (wideDetail) state.conversationId else null,
                wideDetail = wideDetail,
                selectedSection = if (wideDetail) {
                    when (detailRoute) {
                        Route.CRON -> ListSection.CRON
                        Route.KANBAN -> ListSection.KANBAN
                        else -> ListSection.CHATS
                    }
                } else {
                    null
                },
                onOpenChats = { navTo(Route.CHAT.name) },
                // « All Servers » (`AllServers`): les serveurs enregistrés, celui où l'app est, et
                // la bascule. La conversation d'un autre serveur s'ouvre sur son serveur.
                servers = serverList.map {
                    com.onyx.app.ui.conversations.ServerTag(it.id, it.displayName)
                },
                activeServerId = state.server.id,
                unreachable = unreachableServers,
                onSwitchServer = { id -> scope.launch { servers.setActive(id) } },
                onOpenRemote = { row ->
                    openingRect = null
                    pendingRemoteOpen = row.conversation
                    scope.launch { servers.setActive(row.serverId) }
                },
                // « Try again » de la carte de problème: la liste des bots et celle des
                // conversations sont relues, comme le `load` après un échec.
                onRetry = {
                    botsViewModel.refresh()
                    scope.launch { conversationRepo.load() }
                },
                onOpen = { conversation, rect ->
                    // L'animation de rangée est propre au panneau coulissant du téléphone.
                    openingRect = if (wideDetail) null else rect
                    chatViewModel.open(conversation)
                    navTo(Route.CHAT.name)
                },
                onDelete = chatViewModel::delete,
                onPin = { conversation, pinned -> scope.launch { conversationRepo.setPinned(conversation.id, pinned) } },
                onRename = { conversation, title ->
                    scope.launch { conversationRepo.rename(conversation.id, title) }
                },
                onArchive = { conversation, archived -> scope.launch { conversationRepo.setArchived(conversation.id, archived) } },
                onBulkDelete = { ids -> scope.launch { conversationRepo.deleteAll(ids) } },
                onBulkArchive = { ids -> scope.launch { conversationRepo.archiveAll(ids) } },
                onBulkPin = { ids -> scope.launch { conversationRepo.pinAll(ids, true) } },
                onNew = {
                    chatViewModel.newConversation()
                    navTo(Route.CHAT.name)
                },
                onOpenBotChat = openBotChat,
                // « Edit Bot… » du menu d'une tuile: l'éditeur du bot.
                onEditBot = { bot ->
                    selectedBot = bot
                    navTo(Route.BOT_EDITOR.name)
                },
                onChangeFace = { bot ->
                    selectedBot = bot
                    navTo(Route.BOT_FACE.name)
                },
                onBotSchedules = { bot ->
                    selectedBot = bot
                    botCronProfile = bot.name
                    navTo(Route.BOT_SCHEDULES.name)
                },
                // « Voice… » du menu d'une tuile: la voix du bot, qui se règle pour lui seul.
                onBotVoice = { bot ->
                    selectedBot = bot
                    botVoiceProfile = bot.name
                    navTo(Route.BOT_VOICE.name)
                },
                onHideBot = { bot -> botsViewModel.hideBot(bot) },
                onRemoveAPIBot = { bot -> botsViewModel.removeAPIBot(bot) },
                onMarkBotUnread = { bot -> botsViewModel.markBotUnread(bot) },
                onMarkBotSeen = { bot -> botsViewModel.markBotSeen(bot) },
                onMarkRoomUnread = { room -> botsViewModel.markRoomUnread(room) },
                unreadMarks = unreadMarks,
                onSetUnread = setConversationUnread,
                onNewBot = {
                    // « Nouveau bot » passe par la **pile de retour** comme les autres entrées:
                    // écrire `route` en direct laissait la pile intacte, et le retour du nouvel
                    // écran dépilait l'entrée d'avant — on revenait dans le fil au lieu de la
                    // liste où l'on était (mesuré sur l'appareil).
                    navTo(if (state.server.canAdminister) Route.BOTS.name else Route.API_BOT.name)
                },
                onNewGroupChat = { picked, name ->
                    // La salle est faite ici: ouverte sur le serveur quand le Dashboard est
                    // joignable, gardée sur ce téléphone sinon. L'échec remonte à la feuille, qui
                    // le montre au lieu de fermer sur une salle qui n'existe pas.
                    botsViewModel.createGroup(
                        name = name,
                        members = picked,
                        mainName = displayName.trim().ifEmpty { "Onyx" },
                    )
                },
                onOpenUsage = { conversation ->
                    usageSummary = com.onyx.app.data.net.SessionSummary.of(conversation)
                    usageFetchId = conversation.serverSessionId
                    navTo(Route.SESSION_USAGE.name)
                },
                serverProjects = serverProjects,
                onOpenProject = { project ->
                    selectedProject = project
                    navTo(Route.PROJECT_SESSIONS.name)
                },
                // Les salles du serveur, telles que le Dashboard les donne (`groups.list` et la
                // copie d'Hermes Desktop), avec les pastilles de l'activité qu'il signale.
                groups = botsState.groups,
                badges = BotBadges(
                    liveKinds = botsState.liveKinds,
                    roomKinds = botsState.roomKinds,
                    seenMarks = botsState.seenMarks,
                ),
                arranged = botsState.arranged,
                botOrder = botsState.order,
                hiddenBotCount = botsState.hiddenBots.size,
                mainName = displayName.trim().ifEmpty { "Onyx" },
                onOpenHiddenBots = { navTo(Route.HIDDEN_BOTS.name) },
                onMoveRow = { from, to -> botsViewModel.move(from, to) },
                onSortBotsByRecent = { botsViewModel.sortByRecent() },
                onRoomSeen = { room -> botsViewModel.markRoomSeen(room) },
                onOpenGroup = { room -> openGroup(room) },
                onLetBotsMessage = { navTo(Route.BOTS.name) },
                onOpenSettings = { navTo(Route.SETTINGS.name) },
                onOpenCron = { botCronProfile = null; navTo(Route.CRON.name) },
                onOpenKanban = { navTo(Route.KANBAN.name) },
                onDone = { navTo(Route.CHAT.name) },
                sidebarControl = sidebarControl,
            )
        }

        // « Let bots message each other » installe le réglage serveur, comme
        // `ConversationsView.letBotsMessageEachOther`: la question d'abord, puis l'écriture, et
        // l'échec remonte dans la liste au lieu de rester muet.
        //
        // La route Bots se présente, en largeur étendue, dans le panneau de détail: la barre
        // latérale reste à sa gauche, comme le détail d'un `NavigationSplitView` sur téléphone — la
        // flèche de retour n'y a alors pas d'objet, les sessions se choisissant dans la colonne.
        val botsPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { wideDetail, sidebarControl ->
            BotsScreen(
                viewModel = botsViewModel,
                onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.SETTINGS.name) }),
                // Le tap d'une tuile ouvre son chat (`openBot`); l'éditeur est une entrée du
                // menu, atteinte à l'appui long.
                onOpenBotChat = openBotChat,
                onEditBot = { bot ->
                    selectedBot = bot
                    navTo(Route.BOT_EDITOR.name)
                },
                // « Change Face… » du menu d'une tuile: le sélecteur de visage du bot.
                onChangeFace = { bot ->
                    selectedBot = bot
                    navTo(Route.BOT_FACE.name)
                },
                // « Schedules… » du menu d'une tuile: les horaires du bot, comme `scheduling = bot`.
                onSchedules = { bot ->
                    selectedBot = bot
                    botCronProfile = bot.name
                    navTo(Route.BOT_SCHEDULES.name)
                },
                // « Voice… » du menu d'une tuile: la voix du bot, qui se règle pour lui seul.
                onVoice = { bot ->
                    selectedBot = bot
                    botVoiceProfile = bot.name
                    navTo(Route.BOT_VOICE.name)
                },
                onGroupChat = {
                    // Ouverte depuis la liste: la salle reprend ses membres, sans nom imposé.
                    groupPicked = null
                    groupTitle = null
                    navTo(Route.GROUP_CHAT.name)
                },
                // La grille des bots lit l'activité des conversations déjà chargées, comme la
                // section « Bots » de la liste: même source, donc mêmes pastilles.
                conversations = history,
                groups = botsState.groups,
                hasDashboard = state.server.canAdminister,
                openedChatId = history.firstOrNull { it.id == state.conversationId }?.serverSessionId,
                mainName = state.server.displayName,
                onOpenGroup = { room -> openGroup(room) },
                onRoomSeen = { room -> botsViewModel.markRoomSeen(room) },
                onNewBot = {
                    // « New Bot » fait un profil sur son serveur (`BotEditor(.new)`); sans
                    // Dashboard il reste la voie de l'API, un profil et sa propre clé (`Add Bot`).
                    if (state.server.canAdminister) {
                        selectedBot = null
                        navTo(Route.BOT_EDITOR.name)
                    } else {
                        navTo(Route.API_BOT.name)
                    }
                },
                onLetBotsMessage = { askingLetBotsMessage = true },
                hiddenCount = botsState.hidden.size,
                onOpenHiddenBots = { navTo(Route.HIDDEN_BOTS.name) },
                sidebarControl = sidebarControl,
            )
        }

        val cronPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { wideDetail, sidebarControl ->
            val cronViewModel: CronViewModel = viewModel(
                key = "cron-" + state.server.id + state.server.configSignature,
                factory = viewModelFactory { initializer { CronViewModel(state.server) } },
            )
            CronScreen(
                viewModel = cronViewModel,
                // Une pile vide (l'onglet ouvert depuis la liste, ou depuis les Réglages) retombe
                // sur la **liste des conversations** — jamais sur les Réglages, d'où l'on vient
                // pourtant d'entrer (`onOpenCron` des Réglages).
                onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.CONVERSATIONS.name) }),
                sidebarControl = sidebarControl,
            )
        }

        // Le `topRow`: en deux panneaux, le tableau porte le sélecteur de section de la
        // liste au-dessus de ses capsules (Chats / Cron / Kanban), comme `KanbanView(topRow:)`.
        // La section active est Kanban: cette rangée ne se dessine que sur la route du tableau.
        val kanbanTopRow: @Composable () -> Unit = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OnyxSegmented(
                    options = ListSection.entries.map { it.title },
                    selectedIndex = ListSection.entries.indexOf(ListSection.KANBAN),
                    onSelect = { index ->
                        when (ListSection.entries[index]) {
                            ListSection.CHATS -> navTo(Route.CHAT.name)
                            ListSection.CRON -> {
                                botCronProfile = null
                                navTo(Route.CRON.name)
                            }

                            ListSection.KANBAN -> Unit
                        }
                    },
                )
            }
        }

        val kanbanPane: @Composable (Boolean, (@Composable () -> Unit)?, (@Composable () -> Unit)?) -> Unit =
            { wideDetail, topRow, sidebarControl ->
            val kanbanViewModel: KanbanViewModel = viewModel(
                key = "kanban-" + state.server.id + state.server.configSignature,
                factory = viewModelFactory { initializer { KanbanViewModel(state.server) } },
            )
            KanbanScreen(
                viewModel = kanbanViewModel,
                // Le retour revient **là d'où l'on vient**: le tableau s'ouvre depuis les onglets
                // de la liste, pas depuis Réglages — y renvoyer perdait la page précédente.
                onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.CONVERSATIONS.name) }),
                topRow = topRow,
                sidebarControl = sidebarControl,
                onAskOnyx = { question ->
                    // « Demander à Onyx » ouvre une conversation neuve avec la question.
                    chatViewModel.newConversation()
                    chatViewModel.sendText(question, clearInput = false)
                    // Deux panneaux: la conversation est à côté du tableau, on y reste. Compact:
                    // elle s'ouvre par-dessus, comme le panneau du téléphone.
                    if (!wideDetail) navTo(Route.CHAT.name)
                },
            )
        }

        // Le clavier est traité **ici, une seule fois**: la zone de l'app se réduit au-dessus du
        // clavier (`imePadding`), puis l'inset est consommé pour que les écrans internes ne
        // s'en décalent pas une seconde fois. Sans ça, un champ bas d'un formulaire long
        // (« Identifiant » de la configuration) restait sous le clavier: la zone défilante
        // gardait toute la hauteur de l'écran, donc Compose n'avait rien à faire remonter.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .consumeWindowInsets(WindowInsets.ime),
        ) {
            // La géométrie du `NavigationSplitView`: volet borné (320…400 dp, jamais plus de
            // 40 % de la fenêtre), détail qui garde 360 dp, et — quand les deux minima ne tiennent
            // pas ensemble — un seul panneau. Le volet replié laisse le détail prendre tout.
            val layout = listDetailLayout(maxWidth, sidebarCollapsed)
            // Le contrôle de la barre: `sidebar.left`, porté par la barre du volet (« Replier la
            // liste ») et, une fois le volet replié, par celle du détail (« Déplier la liste »).
            // Même icône, même place: c'est ce qui rend le geste réversible depuis n'importe
            // quelle route en deux panneaux.
            val collapseControl: (@Composable () -> Unit)? = if (layout.twoPanes) {
                {
                    SidebarToggleButton(
                        collapsed = false,
                        onClick = { scope.launch { settingsRepo.setSidebarCollapsed(true) } },
                    )
                }
            } else {
                null
            }
            val expandControl: (@Composable () -> Unit)? = if (layout.twoPanes && sidebarCollapsed) {
                {
                    SidebarToggleButton(
                        collapsed = true,
                        onClick = { scope.launch { settingsRepo.setSidebarCollapsed(false) } },
                    )
                }
            } else {
                null
            }
            // Le bord de l'écran est au retour système (une app ne peut s'en soustraire qu'en
            // partie: Android plafonne l'exclusion à 200 dp par bord, mesuré sur cet appareil).
            // Le geste du bord gauche — celui du `SidePanel` — *révèle donc la liste des
            // conversations* par le retour lui-même, au lieu de fermer l'app comme il le faisait
            // sur les cinq géométries relevées: au pied de la pile il ouvre la liste, la liste
            // ouverte il la referme (le retour reste donc entier quand le panneau est ouvert), et
            // plus bas dans la pile il remonte comme avant. Deux panneaux: la liste est déjà là,
            // le retour n'a plus qu'à remonter la pile — jamais de fermeture de l'app.
            // Le retour (bouton ou geste du bord) ne doit **jamais** être avalé: s'il n'y a rien à
            // dépiler, on remonte à l'écran qui a du sens. Avant, sur Réglages en deux panneaux,
            // la pile était vide, `navBack` rendait faux et le geste ne faisait **rien** — « je
            // peux même pas faire retour avec le bord de l'écran ».
            val backHandled = if (layout.twoPanes) {
                backStack.isNotEmpty() || route != Route.CONVERSATIONS.name
            } else {
                backStack.isNotEmpty() || route != Route.CHAT.name
            }
            BackHandler(enabled = backHandled) {
                if (!navBack()) {
                    navTo(
                        rootRouteBehind(
                            currentRoute = route,
                            twoPanes = layout.twoPanes,
                            chatRoute = Route.CHAT.name,
                            conversationsRoute = Route.CONVERSATIONS.name,
                        ),
                    )
                }
            }

            // Une liste compacte ouverte devient le détail au passage en largeur étendue. On dépile
            // le chat correspondant pour que le tout premier retour ne soit pas un faux retour fixe.
            LaunchedEffect(layout.expanded, route) {
                if (layout.expanded) {
                    route = routeWhenExpanded(
                        currentRoute = route,
                        backStack = backStack,
                        conversationsRoute = Route.CONVERSATIONS.name,
                        chatRoute = Route.CHAT.name,
                    )
                }
            }

            // Chaque route garde son état sauvegardé (position de défilement comprise): la route
            // qui sort de la composition le laisse ici, et le retour la retrouve **pile où elle
            // était** — avant, revenir d'un sous-écran ramenait en haut de la page précédente.
            val routeStates = rememberSaveableStateHolder()

            AnimatedContent(
                targetState = Route.valueOf(route),
                transitionSpec = {
                    if (layout.twoPanes && initialState in TwoPaneRoutes && targetState in TwoPaneRoutes) {
                        // La colonne ne glisse pas avec le détail: comme NavigationSplitView.
                        EnterTransition.None.togetherWith(ExitTransition.None)
                    } else {
                        onyxRouteTransition(initialState, targetState)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "onyxRoute",
            ) { presentedRoute ->
              routeStates.SaveableStateProvider(presentedRoute.name) {
                if (layout.twoPanes && presentedRoute in TwoPaneRoutes) {
                    // Le volet est replié: un seul panneau est peint, mais il reste deux
                    // panneaux « en puissance » — la géométrie garde ses bornes, et le détail
                    // porte le contrôle qui rouvre la liste.
                    if (presentedRoute == Route.KANBAN) {
                        // Capture Kanban 2.0: le tableau et la conversation côte à côte. Le
                        // tableau tient le panneau de gauche et porte le sélecteur de section
                        // (le `topRow` ); la conversation celui de droite — « demander à
                        // Onyx » la remplit sans quitter le tableau. Le volet suit la même
                        // géométrie bornée que les autres routes, pas un partage 58/42 figé.
                        OnyxListDetail(
                            layout = kanbanPairLayout(maxWidth, sidebarCollapsed),
                            sidebar = { kanbanPane(true, kanbanTopRow, collapseControl) },
                            detail = { key(state.conversationId) { chatPane(false, expandControl) } },
                        )
                    } else {
                        OnyxListDetail(
                            layout = layout,
                            sidebar = { conversationsPane(true, presentedRoute, collapseControl) },
                            detail = {
                                when (presentedRoute) {
                                    Route.CHAT, Route.CONVERSATIONS ->
                                        key(state.conversationId) { chatPane(false, expandControl) }

                                    Route.CRON -> cronPane(true, expandControl)
                                    Route.KANBAN -> kanbanPane(true, null, expandControl)
                                    // Les bots gardent la colonne à leur gauche (D6).
                                    Route.BOTS -> botsPane(true, expandControl)
                                    else -> Unit
                                }
                            },
                        )
                    }
                } else {
                    when (presentedRoute) {
            Route.CHAT -> chatPane(true, null)

            Route.VOICE -> VoiceScreen(
                history = state.messages
                    .filter { it.role == com.onyx.app.data.model.Role.USER }
                    .mapNotNull { question ->
                        val index = state.messages.indexOf(question)
                        val answer = state.messages.drop(index + 1)
                            .firstOrNull { it.role == com.onyx.app.data.model.Role.ASSISTANT }
                            ?.text
                            ?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        com.onyx.app.ui.voice.VoiceTurn(question = question.text, answer = answer)
                    },
                // `VoiceView`: `Self.demo ? .listening: session.phase` — la maquette montre
                // l'écoute, l'orbe vit de la même onde, et rien n'est un mensonge: c'est une pose.
                phase = voicePose ?: if (voiceDemo) VoicePhase.LISTENING else phase,
                heard = if (voiceDemo && (voicePose == null || voicePose == VoicePhase.LISTENING)) {
                    VOICE_DEMO_WORDS
                } else {
                    heard
                },
                spoken = if (voicePose == VoicePhase.SPEAKING) VOICE_DEMO_REPLY else spoken,
                level = if (voiceDemo) 0.62f else speech.level,
                streaming = state.isStreaming,
                handsFree = if (voiceDemo) true else handsFree,
                // `VoiceView.soloHeader` montre `conversation.title`: le nom donné à la
                // conversation, sinon sa première question — jamais le nom du serveur.
                conversationTitle = if (voiceDemo) {
                    VOICE_DEMO_TITLE
                } else {
                    (state.conversationName?.takeIf { it.isNotBlank() }
                        ?: state.messages.firstOrNull { it.role == com.onyx.app.data.model.Role.USER }?.text)
                        ?.let(::chatHeaderTitle)
                        ?.takeIf { it.isNotBlank() }
                        ?: state.server.displayName
                },
                problem = posedProblem ?: if (voiceDemo) null else voiceProblem ?: speech.problem,
                onToggleHandsFree = voice::toggleHandsFree,
                // « Parler pendant les réponses »: les trois choix du panneau vocal écrivent le
                // réglage (le même que Réglages → Voix), qui décide si le micro s'ouvre derrière
                // une réponse.
                talkOverReplies = TalkOverChoice.of(talkOver),
                onTalkOverChange = { choice -> scope.launch { settingsRepo.setTalkOver(choice.setting) } },
                onRestart = {
                    voiceProblem = null
                    if (phase == VoicePhase.IDLE) {
                        state.messages.lastOrNull {
                            it.role == com.onyx.app.data.model.Role.ASSISTANT && it.text.isNotBlank()
                        }?.let { voice.speak(it.text) }
                    }
                },
                onStop = {
                    soloVoiceStop(
                        phase = phase,
                        allowEnd = !state.isStreaming,
                        stop = {
                            voice.stop()
                            chatViewModel.stop()
                        },
                        end = { navTo(Route.CHAT.name) },
                    )
                },
                onBackToKeyboard = {
                    voice.stop()
                    navTo(Route.CHAT.name)
                },
                onOpenSettings = { navTo(Route.VOICE_SETTINGS.name) },
                isPaused = voice.isPaused.collectAsStateWithLifecycle().value,
                onPauseResume = { if (voice.isPaused.value) voice.resumeSpeaking() else voice.pauseSpeaking() },
                // Réglages → Apparence: l'orbe et sa lumière sont ceux du réglage, partout.
                // `VoiceOrb.shown`: « Bot faces » éteint remplace la face de Onyx par la
                // Bubble, pour qu'aucun visage n'apparaisse là.
                orb = if (voiceOrb == VoiceOrbChoice.ONYX && !botFaces) VoiceOrbChoice.BUBBLE else voiceOrb,
                onOrbChange = { orb -> scope.launch { settingsRepo.setVoiceOrb(orb) } },
                orbLight = orbLight,
                onOrbLightChange = { light -> scope.launch { settingsRepo.setOrbLight(light) } },
                // L'orbe est un bouton, comme: son action est celle de la
                // phase en cours (`VoiceSession.primaryAction`).
                onOrbClick = {
                    when (phase) {
                        VoicePhase.IDLE, VoicePhase.SPEAKING, VoicePhase.THINKING -> {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) voice.startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        // Rien ne se dit de plus à mi-mots: l'appareil envoie la question dès que
                        // la phrase se termine; l'orbe rend donc le repos, sans mentir.
                        VoicePhase.LISTENING -> voice.stop()
                    }
                },
            )

            Route.COMPOSER_EDITOR -> ComposerEditorScreen(
                draft = state.input,
                onDraftChange = chatViewModel::onInputChange,
                onDone = { navTo(Route.CHAT.name) },
                onSend = {
                    navTo(Route.CHAT.name)
                    chatViewModel.send()
                },
                isStreaming = state.isStreaming,
            )

            Route.CONVERSATIONS -> conversationsPane(false, presentedRoute, null)

            Route.SETTINGS -> SettingsScreen(
                viewModel = settingsViewModel,
                // « Terminé » referme la feuille: on revient **d'où l'on est venu** (le fil, la
                // liste, ou une tuile de bot), et non systématiquement sur le fil — ouvrir les
                // Réglages depuis la liste des conversations et se retrouver dans le fil était
                // un retour faux.
                onDone = { if (!navBack()) navTo(Route.CHAT.name) },
                onOpenAppearance = { navTo(Route.APPEARANCE.name) },
                onOpenModels = { navTo(Route.MODELS.name) },
                onOpenVoice = { navTo(Route.VOICE_SETTINGS.name) },
                onOpenGateway = { navTo(Route.GATEWAY.name) },
                onOpenFiles = { navTo(Route.FILES.name) },
                onOpenCron = { botCronProfile = null; navTo(Route.CRON.name) },
                onOpenKanban = { navTo(Route.KANBAN.name) },
                onOpenPhoneTools = { navTo(Route.PHONE_TOOLS.name) },
                onOpenBots = { navTo(Route.BOTS.name) },
                onOpenServers = { navTo(Route.SERVERS.name) },
                onOpenProfile = { navTo(Route.PROFILES.name) },
                onOpenSkills = { navTo(Route.SKILLS.name) },
                onOpenTools = { navTo(Route.TOOLS.name) },
                onOpenWhatsNew = { navTo(Route.WHATS_NEW.name) },
                onOpenTipJar = { navTo(Route.TIP_JAR.name) },
                onOpenLock = { navTo(Route.LOCK.name) },
                onOpenConnect = { navTo(Route.CONNECT.name) },
                onOpenTerminal = { navTo(Route.TERMINAL_SETTINGS.name) },
                onOpenAcknowledgements = { navTo(Route.ACKNOWLEDGEMENTS.name) },
                onOpenContext = { navTo(Route.CONTEXT.name) },
                onOpenMemory = { navTo(Route.MEMORY.name) },
                onOpenNotifications = { navTo(Route.NOTIFICATIONS.name) },
                onOpenConnectionDetails = { navTo(Route.CONNECTION_DETAILS.name) },
                onSetContextWindow = settingsViewModel::setContextWindow,
                // Les Réglages se présentent par-dessus les deux panneaux: leur barre garde le
                // contrôle du volet, comme les autres routes d'un écran large.
                sidebarControl = expandControl ?: collapseControl,
                onEraseEverything = {
                    // efface puis ferme la feuille; ici l'effacement vide aussi la
                    // conversation ouverte, donc on repart sur une nouvelle.
                    scope.launch {
                        AppContainer.eraseEverything()
                        chatViewModel.newConversation()
                    }
                    navTo(Route.CHAT.name)
                },
            )

            Route.CRON -> cronPane(false, null)

            Route.BOT_SCHEDULES -> {
                val profile = botCronProfile
                val bot = selectedBot?.takeIf { it.name == profile }
                    ?: botsState.bots.firstOrNull { it.name == profile }
                    ?: profile?.let { BotProfile(name = it) }
                if (bot == null) {
                    // Un processus recréé sans le bot choisi ne doit pas ouvrir le store `default`.
                    LaunchedEffect(Unit) { navTo(Route.BOTS.name) }
                } else {
                    val cronViewModel: CronViewModel = viewModel(
                        key = "bot-schedules-" + state.server.id + state.server.configSignature + "-" + bot.name,
                        factory = viewModelFactory {
                            initializer { CronViewModel(state.server, bot.name, bot.label(state.server.displayName)) }
                        },
                    )
                    BotSchedulesScreen(
                        viewModel = cronViewModel,
                        bot = bot,
                        mainName = state.server.displayName,
                        reachable = state.server.canAdminister,
                        onBack = { if (!navBack()) navTo(Route.BOT_EDITOR.name) },
                    )
                }
            }

            // « Voice… »: la voix du bot, choisie pour lui seul (`BotVoicePicker`). Le réglage est
            // écrit tout de suite (`setBotVoice`); l'écran relit ce que l'app a gardé, donc la
            // coche survit à une relance.
            Route.BOT_VOICE -> {
                val profile = botVoiceProfile
                val bot = selectedBot?.takeIf { it.name == profile }
                    ?: botsState.bots.firstOrNull { it.name == profile }
                if (bot == null) {
                    LaunchedEffect(Unit) { if (!navBack()) navTo(Route.BOTS.name) }
                } else {
                    BotVoicePickerScreen(
                        botName = bot.name,
                        displayName = bot.label(state.server.displayName),
                        mainName = state.server.displayName,
                        picked = botVoicesHere[state.server.id].orEmpty(),
                        replyVoice = replyVoice,
                        kokoroVoice = kokoroVoice,
                        kokoroUrl = kokoroUrl,
                        voiceSpeed = voiceSpeed,
                        speechLocale = speechLocale,
                        botNames = botsState.bots.map { it.name },
                        onPick = { voice ->
                            scope.launch { settingsRepo.setBotVoice(state.server.id, bot.name, voice) }
                        },
                        onBack = {
                            if (!navBack()) navTo(if (bot.isDefault) Route.SETTINGS.name else Route.BOT_EDITOR.name)
                        },
                    )
                }
            }

            Route.API_BOT -> APIBotEditorScreen(
                onAdd = { profile, title ->
                    botsViewModel.addLocalBot(profile, title)
                    navTo(Route.BOTS.name)
                },
                onBack = { navTo(Route.BOTS.name) },
            )

            Route.BOTS -> botsPane(false, null)

            Route.BOT_EDITOR -> {
                val bot = selectedBot
                // Les jeux d'outils et les compétences du bot: lus au serveur à l'ouverture de
                // l'éditeur, et écrits au geste (voir `BotEditorViewModel`).
                val editorViewModel: BotEditorViewModel = viewModel(
                    key = "bot-editor-" + state.server.id + state.server.configSignature + "-" + bot?.name,
                    factory = viewModelFactory {
                        initializer { BotEditorViewModel(state.server, bot?.name) }
                    },
                )
                val editorState by editorViewModel.state.collectAsStateWithLifecycle()
                var deleteProblem by remember(bot?.name) { mutableStateOf<String?>(null) }
                BotEditorScreen(
                    bot = bot,
                    faceCode = bot?.let { botsViewModel.faceFor(it) },
                    mainName = state.server.displayName,
                    onFaceChosen = {
                        if (bot != null) navTo(Route.BOT_FACE.name)
                    },
                    onOpenPrompt = {
                        contextFileName = null
                        contextEditor = Triple(
                            "${bot?.label(state.server.displayName) ?: "Bot"} — Personality",
                            "Its profile's SOUL.md.",
                            bot?.name.orEmpty(),
                        )
                        contextReturn = Route.BOT_EDITOR.name
                        navTo(Route.CONTEXT_EDITOR.name)
                    },
                    onOpenSchedules = {
                        botCronProfile = bot?.name
                        navTo(Route.BOT_SCHEDULES.name)
                    },
                    onOpenVoice = {
                        // « Voice… » de l'éditeur: la même feuille que par le menu d'une tuile.
                        botVoiceProfile = bot?.name
                        navTo(Route.BOT_VOICE.name)
                    },
                    voiceSummary = BotVoicePicker.summary(botVoicesHere[state.server.id]?.get(bot?.name)),
                    models = botModels,
                    onPickModel = { model ->
                        selectedBot?.let { botsViewModel.setModel(it, model) }
                    },
                    toolsets = editorState.toolsets,
                    toolsSelection = editorState.toolsSelection,
                    mainToolsets = editorState.mainToolsets,
                    onToolsSelectionChange = editorViewModel::setToolsSelection,
                    skills = editorState.skills,
                    alwaysLoadedSkills = editorState.alwaysLoaded,
                    onToggleSkill = editorViewModel::toggleSkill,
                    onPinSkill = editorViewModel::pinSkill,
                    problem = deleteProblem ?: editorState.problem,
                    onSave = { title, about, model, shown ->
                        bot?.let { botsViewModel.saveEdit(it, title, about, model, shown) }
                        navTo(Route.BOTS.name)
                    },
                    onDelete = {
                        val target = bot
                        if (target == null) {
                            navTo(Route.BOTS.name)
                        } else {
                            // La suppression part au serveur: l'écran ne se ferme qu'une fois
                            // qu'elle a pris, et un refus se lit ici, sans rien effacer.
                            scope.launch {
                                val failure = botsViewModel.deleteBot(target)
                                if (failure == null) {
                                    selectedBot = null
                                    navTo(Route.BOTS.name)
                                } else {
                                    deleteProblem = OnyxLocalization.format("Couldn't delete it: %1\$s", failure)
                                }
                            }
                        }
                    },
                    onCancel = { navTo(Route.BOTS.name) },
                )
            }

            Route.BOT_FACE -> {
                val bot = selectedBot
                if (bot == null) {
                    LaunchedEffect(Unit) { if (!navBack()) navTo(Route.BOTS.name) }
                } else {
                    BotFacePickerScreen(
                        botName = bot.name,
                        displayName = bot.label(state.server.displayName),
                        current = botsViewModel.faceFor(bot),
                        isDefault = bot.isDefault || bot.name == "default",
                        hasPickedFace = botsViewModel.hasPickedFace(bot),
                        serverBacked = state.server.hasServeCredentials,
                        onSave = { code ->
                            botsViewModel.setFace(bot, code)
                            if (!navBack()) navTo(Route.BOT_EDITOR.name)
                        },
                        onCancel = { if (!navBack()) navTo(Route.BOT_EDITOR.name) },
                    )
                }
            }

            Route.GROUP_CHAT -> {
                val room = openedRoom
                if (room != null) {
                    // Une salle du serveur ouverte depuis la liste: son fil vient d'Hermes lui-même
                    // (`groups.log` / `groups.state`), et ce qui y est écrit part par `groups.send`.
                    // Le compositeur local à tours de parole (GroupChatScreen) ne sert plus qu'à une
                    // salle neuve (« New Group Chat »), dont les membres ne sont pas encore chez Hermes.
                    val roomViewModel: GroupRoomViewModel = viewModel(
                        key = "room-" + state.server.id + state.server.configSignature + "|" + room.id,
                        factory = viewModelFactory {
                            initializer {
                                GroupRoomViewModel(
                                    application = context.applicationContext as android.app.Application,
                                    config = state.server,
                                    initialRoom = room.toRoom(),
                                    mainName = state.server.displayName,
                                )
                            }
                        },
                    )
                    GroupRoomScreen(
                        viewModel = roomViewModel,
                        server = state.server,
                        // La salle s'ouvre depuis la liste des conversations comme depuis l'onglet
                        // Bots: le retour revient donc là d'où l'on vient, et ne saute plus
                        // systématiquement sur Bots.
                        onBack = { if (!navBack()) navTo(Route.BOTS.name) },
                        // Le bouton Talk de la salle ouvre la scène vocale, qui lit ce fil
                        // (`GroupRoomFeed`): les réponses du serveur et celui qui parle.
                        onOpenVoice = { navTo(Route.GROUP_VOICE.name) },
                        onEnded = {
                            // Terminée sur le serveur: la liste la laisse partir.
                            openedRoom = null
                            botsViewModel.refresh()
                            navTo(Route.BOTS.name)
                        },
                        speechLocale = speechLocale,
                        focusComposer = groupComposerFocus,
                        onComposerFocused = { groupComposerFocus = false },
                        chatTextScale = com.onyx.app.ui.theme.ResolvedTheme.chatTextScale(chatTextSize),
                    )
                } else {
                    // La salle choisie dans la liste: ses membres sont cochés d'avance, comme le
                    // `NewGroupChatView` part des bots de la salle.
                    val groupViewModel: GroupChatViewModel = viewModel(
                        key = "group-" + state.server.id + state.server.configSignature + "|" + (groupTitle ?: ""),
                        factory = viewModelFactory {
                            initializer {
                                GroupChatViewModel(
                                    application = context.applicationContext as android.app.Application,
                                    config = state.server,
                                    preselected = null,
                                )
                            }
                        },
                    )
                    // `NewGroupChatView`: les bots choisis dans la feuille deviennent les membres
                    // qui répondent, comme le `createGroup(named:bots:mainName:)`.
                    LaunchedEffect(groupPicked, groupViewModel) {
                        groupPicked?.let { picked ->
                            groupViewModel.setSelected(picked)
                            groupPicked = null
                        }
                    }
                    GroupChatScreen(
                        viewModel = groupViewModel,
                        server = state.server,
                        title = groupTitle,
                        onBack = {
                            groupTitle = null
                            if (!navBack()) navTo(Route.BOTS.name)
                        },
                        onOpenVoice = { navTo(Route.GROUP_VOICE.name) },
                        speechLocale = speechLocale,
                        focusComposer = groupComposerFocus,
                        onComposerFocused = { groupComposerFocus = false },
                        chatTextScale = com.onyx.app.ui.theme.ResolvedTheme.chatTextScale(chatTextSize),
                    )
                }
            }

            Route.GROUP_VOICE -> {
                // Maquette de boutique: une salle scriptée dont la réplique lue est **injectée**,
                // pour éprouver la scène de parole là où il n'y a ni serveur ni micro (debug seul).
                val demo = remember(groupVoiceDemoOn) {
                    if (groupVoiceDemoOn) {
                        GroupVoiceDemo.from(context.findActivity()?.intent?.getStringExtra(EXTRA_GROUP_VOICE_DEMO))
                    } else {
                        null
                    }
                }
                // Deux fils peuvent nourrir la scène vocale: celui d'une salle du serveur quand une
                // est ouverte (`openedRoom` — c'est là que la voix lit les réponses du serveur), et
                // celui du compositeur local d'une salle neuve, dont les membres ne sont pas encore
                // chez Hermes. Les deux ViewModels sont pris avec la **même clé** que GROUP_CHAT: la
                // voix lit et fait avancer le fil déjà ouvert par l'écran, pas une seconde salle.
                val room = openedRoom
                val roomViewModel: GroupRoomViewModel? = if (room == null) {
                    null
                } else {
                    viewModel(
                        key = "room-" + state.server.id + state.server.configSignature + "|" + room.id,
                        factory = viewModelFactory {
                            initializer {
                                GroupRoomViewModel(
                                    application = context.applicationContext as android.app.Application,
                                    config = state.server,
                                    initialRoom = room.toRoom(),
                                    mainName = state.server.displayName,
                                )
                            }
                        },
                    )
                }
                val groupViewModel: GroupChatViewModel? = if (room != null) {
                    null
                } else {
                    viewModel(
                        key = "group-" + state.server.id + state.server.configSignature + "|" + (groupTitle ?: ""),
                        factory = viewModelFactory {
                            initializer { GroupChatViewModel(context.applicationContext as android.app.Application, state.server) }
                        },
                    )
                }
                val thread: GroupVoiceThread = demo?.thread ?: roomViewModel ?: groupViewModel!!

                // Les visages de la salle, pour l'en-tête et l'orbe.
                val bots = demo?.bots ?: if (roomViewModel != null) {
                    val roomState by roomViewModel.state.collectAsStateWithLifecycle()
                    roomState.room.composerBots(roomViewModel.face)
                } else {
                    val groupState by groupViewModel!!.state.collectAsStateWithLifecycle()
                    groupState.bots.filter { it.name in groupState.selected }
                }

                // Les noms de la salle: c'est eux qui disent à la lecture quelle voix va à qui,
                // pour que deux bots ne lisent pas de la même (`BotVoices`).
                val roster = bots.map { it.name }
                LaunchedEffect(roster) { voice.output.setBotRoster(roster) }

                // Le fil de la salle se suit tant que la scène vocale est là, comme le
                // `.task(id: scenePhase == .active || voice.isOn)`: les réponses des bots
                // continuent d'arriver pendant qu'elles sont lues.
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner, room?.id) {
                    val feed = roomViewModel?.feed ?: return@LaunchedEffect
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { feed.follow() }
                }

                val groupVoice = remember(thread, voice, scope) {
                    GroupVoice(thread, voice, scope)
                }
                val liveSpeakerId by groupVoice.speakerId.collectAsStateWithLifecycle()
                val isHeld by groupVoice.isHeld.collectAsStateWithLifecycle()
                val isPaused by voice.isPaused.collectAsStateWithLifecycle()
                val lines by thread.lines.collectAsStateWithLifecycle()
                val isTalking by thread.isTalking.collectAsStateWithLifecycle()
                val waitsOnPerson by thread.waitsOnPerson.collectAsStateWithLifecycle()
                // Le niveau de la voix lue: c'est lui qui fait battre la bouche du grand visage.
                val replyLevel by voice.replyLevel.collectAsStateWithLifecycle()
                // La maquette pose la scène qu'elle annonce (parole ou écoute); sinon, la phase
                // réelle et le bot que le coordinateur est en train de lire.
                val shownPhase = demo?.phase ?: phase
                val shownSpeaker = demo?.speakerId ?: liveSpeakerId
                // La réplique de la maquette est simulée: son niveau bat comme une voix, pour que
                // la bouche du grand visage bouge vraiment sur une capture.
                var demoLevel by remember(demo) { mutableFloatStateOf(demo?.level ?: 0f) }
                LaunchedEffect(demo) {
                    if (demo == null) return@LaunchedEffect
                    val started = System.nanoTime()
                    while (true) {
                        demoLevel = GroupVoiceDemo.levelAt((System.nanoTime() - started) / 1_000_000_000f)
                        delay(50)
                    }
                }
                val soundingLevel = if (demo != null) demoLevel else replyLevel
                // `solIsWorking`: quelque chose tourne — la voix, ou une salle qui parle.
                val groupWorking = shownPhase == VoicePhase.LISTENING || shownPhase == VoicePhase.SPEAKING ||
                    (isTalking && !isHeld)

                LaunchedEffect(groupVoice, demo) {
                    // La maquette n'ouvre ni micro ni lecture: sa réplique est injectée.
                    if (demo != null) return@LaunchedEffect
                    voice.onQuestion = groupVoice::ask
                    voiceProblem = null
                    groupVoice.start()
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) groupVoice.listen() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
                DisposableEffect(groupVoice, demo) {
                    onDispose {
                        if (demo == null) {
                            groupVoice.stop()
                            voice.onQuestion = onSoloVoiceQuestion
                        }
                    }
                }

                VoiceScreen(
                    phase = shownPhase,
                    heard = heard,
                    spoken = spoken,
                    level = speech.level,
                    // Le niveau de la réplique lue: la bouche du visage qui parle le suit.
                    speakingLevel = soundingLevel,
                    // Une salle est toujours en mains-libres: chaque round rend le micro, et
                    // l'en-tête de la salle ne dessine donc aucun interrupteur pour ça.
                    handsFree = true,
                    conversationTitle = thread.roomName,
                    problem = voiceProblem ?: speech.problem,
                    onRestart = groupVoice::replayLast,
                    onStop = {
                        if (groupWorking) {
                            groupVoice.halt()
                        } else {
                            groupVoice.stop()
                            navTo(Route.GROUP_CHAT.name)
                        }
                    },
                    onBackToKeyboard = {
                        groupVoice.stop()
                        groupComposerFocus = true
                        navTo(Route.GROUP_CHAT.name)
                    },
                    group = GroupVoiceScene(
                        roomName = thread.roomName,
                        server = thread.server,
                        bots = bots,
                        lines = lines,
                        speakerId = shownSpeaker,
                        isTalking = isTalking,
                        isHeld = isHeld,
                        waitsOnPerson = waitsOnPerson,
                    ),
                    isPaused = isPaused,
                    onPauseResume = groupVoice::pauseOrResume,
                    onOrbClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) groupVoice.listen() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            }

            // « Connection details »: `ConnectionDetailsView`, ouvert depuis les Réglages.
            Route.CONNECTION_DETAILS -> ConnectionDetailsScreen(
                server = state.server,
                onSave = { settingsViewModel.saveConnection(it) },
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            // « Hidden Bots »: `HiddenBotsView`, ouvert par la tuile « Hidden » de la grille.
            Route.HIDDEN_BOTS -> HiddenBotsScreen(
                viewModel = botsViewModel,
                mainName = state.server.displayName,
                onBack = { if (!navBack()) navTo(Route.BOTS.name) },
            )

            // « Usage & cost »: `SessionUsageView`.
            Route.SESSION_USAGE -> {
                val summary = usageSummary
                if (summary == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CONVERSATIONS.name) }
                } else {
                    SessionUsageScreen(
                        initial = summary,
                        server = state.server,
                        fetchId = usageFetchId,
                        onBack = { if (!navBack()) navTo(Route.CONVERSATIONS.name) },
                    )
                }
            }

            // « Les sessions d'un projet »: `ProjectSessionsView`.
            Route.PROJECT_SESSIONS -> {
                val project = selectedProject
                if (project == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CONVERSATIONS.name) }
                } else {
                    val projectViewModel: ProjectSessionsViewModel = viewModel(
                        key = "project-sessions-" + project.id,
                        factory = viewModelFactory {
                            initializer { ProjectSessionsViewModel(state.server, project.id, project.lanes) }
                        },
                    )
                    ProjectSessionsScreen(
                        project = project,
                        viewModel = projectViewModel,
                        onOpenSession = { session ->
                            usageSummary = session
                            usageFetchId = null
                            navTo(Route.SESSION_USAGE.name)
                        },
                        onBack = { if (!navBack()) navTo(Route.CONVERSATIONS.name) },
                    )
                }
            }

            Route.PHONE_TOOLS -> PhoneToolsScreen(onBack = { navTo(Route.SETTINGS.name) })

            Route.COMPUTER -> ComputerScreen(
                session = AppContainer.computerSession,
                onBack = { navTo(Route.CHAT.name) },
            )

            Route.LOCK -> LockSettingsScreen(onBack = { navTo(Route.SETTINGS.name) })

            Route.ACKNOWLEDGEMENTS -> AcknowledgementsScreen(onBack = { navTo(Route.SETTINGS.name) })

            Route.TIP_JAR -> TipJarScreen(onBack = { navTo(Route.SETTINGS.name) })

            Route.CONNECT -> ConnectQrScreen(
                code = com.onyx.app.data.model.SetupCode(
                    name = state.server.name,
                    dashboardURL = state.server.serveBaseUrl,
                    dashboardUser = state.server.serveUsername,
                    dashboardPassword = state.server.servePassword,
                    apiURL = state.server.normalizedBaseUrl,
                    apiKey = state.server.apiKey,
                    profile = state.server.hermesProfile,
                    profileKey = state.server.profileKey,
                    accessID = state.server.accessID,
                    accessSecret = state.server.accessSecret,
                    headers = state.server.customHeaders,
                    model = state.server.model,
                    use = state.server.transport,
                ),
                onScan = { setupScannerOpen = true },
                onBack = { navTo(Route.SETTINGS.name) },
            )

            Route.KANBAN -> kanbanPane(false, null, null)

            Route.VOICE_SETTINGS -> VoiceSettingsScreen(
                viewModel = settingsViewModel,
                server = state.server,
                // La page Voix s'ouvre depuis les Réglages **et** depuis la scène vocale: le
                // retour revient là d'où l'on vient (le bord de l'écran y revient déjà).
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
                onOpenListeningLanguage = { navTo(Route.SPEECH_LANGUAGE.name) },
                onOpenReplyLanguage = { navTo(Route.REPLY_LANGUAGE.name) },
                onOpenStopPhrases = { navTo(Route.STOP_PHRASES.name) },
                onOpenSpokenPrefixes = { navTo(Route.SPOKEN_PREFIXES.name) },
                onOpenKokoroVoices = { navTo(Route.KOKORO_VOICES.name) },
            )

            Route.NOTIFICATIONS -> PushSettingsScreen(
                viewModel = settingsViewModel,
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            Route.KOKORO_VOICES -> KokoroVoicesView(
                baseUrl = kokoroUrl,
                currentVoice = kokoroVoice,
                onVoiceChange = settingsViewModel::setKokoroVoice,
                onBack = { navTo(Route.VOICE_SETTINGS.name) },
            )

            Route.TERMINAL -> TerminalScreen(
                server = state.server,
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                folderLookup = {
                    runCatching {
                        com.onyx.app.data.net.ServeFilesApi(com.onyx.app.data.net.ServeClient(state.server)).home()
                    }.getOrNull()
                },
            )

            Route.TERMINAL_SETTINGS -> TerminalSettingsView(
                server = state.server,
                onOpenTerminal = { navTo(Route.TERMINAL.name) },
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            Route.SUBAGENT -> {
                // La ligne la plus fraîche: le fil (ou le roster) la met à jour pendant qu'on
                // regarde, et l'écran suit — un arrêt effectif doit se voir tout de suite.
                val agent = subagentAgent?.let { picked -> shownSubagents.firstOrNull { it.id == picked.id } ?: picked }
                if (agent == null) {
                    // Hors de la composition: la route vide ne doit pas non plus rester dans la pile.
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    // La transcription: lue sur le Dashboard (`subagent.tail`); rien à lire si le
                    // transport ne la porte pas, et l'écran le dit alors au lieu d'attendre.
                    val controllable = agent.hasTranscript && !subagentDemoRun
                    // Les deux gestes de contrôle, typés explicitement: la doublure de QA les
                    // résout en local (c'est une simulation, et le rapport le dit), et sans
                    // transport qui les porte ils ne sont pas proposés du tout.
                    val demoSteer: suspend (String) -> Boolean = { text ->
                        demoSubagents = demoSubagents.map {
                            if (it.id == agent.id) it.copy(lastTool = text.take(80)) else it
                        }
                        true
                    }
                    val demoStop: suspend () -> Boolean = {
                        demoSubagents = demoSubagents.map {
                            if (it.id == agent.id) {
                                it.copy(
                                    status = com.onyx.app.data.model.SubagentStatus.COMPLETED,
                                    summary = "Stopped from Onyx before it finished.",
                                )
                            } else {
                                it
                            }
                        }
                        true
                    }
                    val liveSteer: suspend (String) -> Boolean = { text ->
                        chatViewModel.steerSubagent(agent.id, text)
                    }
                    val liveStop: suspend () -> Boolean = { chatViewModel.stopSubagent(agent.id) }
                    val steerAction: (suspend (String) -> Boolean)? =
                        if (subagentDemoRun) demoSteer else if (controllable) liveSteer else null
                    val stopAction: (suspend () -> Boolean)? =
                        if (subagentDemoRun) demoStop else if (controllable) liveStop else null
                    SubagentTranscriptScreen(
                        agent = agent,
                        onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                        loadTranscript = { current ->
                            if (subagentDemoRun) {
                                // Transcription **injectée**: l'émulateur n'a pas de Dashboard
                                // pour la servir. Le rapport le dit.
                                val text = com.onyx.app.ui.chat.SubagentStandIn.transcript(current)
                                SubagentTailView(text = text, available = text.isNotBlank())
                            } else {
                                chatViewModel.subagentTranscript(current)
                            }
                        },
                        onSteer = steerAction,
                        onStop = stopAction,
                    )
                }
            }

            Route.SUBAGENTS -> {
                // Le roster du Dashboard (`subagent.list`) complète ce que le fil a vu: sans tour
                // en cours, c'est la seule source du temps écoulé et du dernier outil.
                LaunchedEffect(Unit) { if (!subagentDemoRun) chatViewModel.refreshSubagentRoster() }
                SubagentListScreen(
                    subagents = shownSubagents,
                    onOpen = { current ->
                        subagentAgent = current
                        navTo(Route.SUBAGENT.name)
                    },
                    onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                )
            }

            Route.CAMERA -> CameraScreen(
                onCapture = { uri ->
                    chatViewModel.addAttachments(listOf(uri))
                    navTo(Route.CHAT.name)
                },
                onClose = { navTo(Route.CHAT.name) },
            )

            Route.PHOTO -> {
                if (photoSet.isEmpty()) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    PhotoViewerScreen(
                        photos = photoSet.map { attachment ->
                            ViewerPhoto(
                                id = attachment.id,
                                uri = android.net.Uri.parse(attachment.uri),
                                caption = attachment.name.orEmpty(),
                                name = attachment.name ?: "Photo",
                            )
                        },
                        initialIndex = photoIndex,
                        onClose = { navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.PDF -> {
                val source = pdfSource
                if (source == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    com.onyx.app.ui.chat.PdfViewerScreen(
                        source = source,
                        onClose = { navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.VIDEO -> {
                val source = videoSource
                if (source == null) {
                    // Le repli se pose **hors de la composition** (`LaunchedEffect`): un
                    // `navTo` écrit pendant la composition laisse le geste de retour sans effet
                    // quand l'état vide se reproduit (l'entrée repoussée à chaque passage).
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    com.onyx.app.ui.chat.VideoPlayerScreen(
                        source = source,
                        onClose = { navTo(Route.CHAT.name) },
                    )
                }
            }

            // « Capture rapide »: une note prise en passant, que l'agent range. Elle se prend
            // aussi depuis le raccourci de l'illustration, sans passer par le fil.
            Route.QUICK_CAPTURE -> com.onyx.app.ui.capture.QuickCaptureScreen(
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
            )

            Route.SELECT_TEXT -> {
                val text = selectedText
                if (text == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    SelectableTextScreen(
                        text = text,
                        onBack = { navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.PATCH -> {
                val text = patchText
                if (text == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    PatchScreen(
                        title = "Change",
                        patch = text,
                        onBack = { navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.TOOLS -> ToolsScreen(
                server = state.server,
                scope = scope,
                onBack = { navTo(Route.SETTINGS.name) },
            )

            Route.SKILLS -> SkillsScreen(
                server = state.server,
                onOpenSkill = { name ->
                    skillName = name
                    navTo(Route.SKILL_EDITOR.name)
                },
                onBack = { navTo(Route.SETTINGS.name) },
            )

            Route.SKILL_EDITOR -> SkillEditorScreen(
                server = state.server,
                existing = skillName,
                scope = scope,
                onSaved = { navTo(Route.SKILLS.name) },
                onBack = { navTo(Route.SKILLS.name) },
            )

            Route.PROFILES -> ProfilePickerScreen(
                server = state.server,
                onChoose = { profile ->
                    scope.launch {
                        servers.upsert(state.server.copy(hermesProfile = profile.orEmpty()))
                        servers.setActive(state.server.id)
                    }
                    chatViewModel.newConversation()
                    navTo(Route.SETTINGS.name)
                },
                onBack = { navTo(Route.SETTINGS.name) },
            )

            Route.WHATS_NEW -> WhatsNewScreen(
                version = com.onyx.app.BuildConfig.VERSION_NAME,
                items = CURRENT_RELEASE_ITEMS,
                onContinue = { navTo(Route.SETTINGS.name) },
            )

            Route.SERVERS -> {
                val all by servers.servers.collectAsStateWithLifecycle()
                val active by servers.activeId.collectAsStateWithLifecycle()
                ServersScreen(
                    repository = servers,
                    servers = all,
                    activeId = active,
                    scope = scope,
                    onBack = { navTo(Route.SETTINGS.name) },
                    onEditConnection = { navTo(Route.SETUP.name) },
                )
            }

            Route.SETUP -> {
                SetupScreen(
                    config = setupDraft,
                    onChange = { transform -> setupDraft = transform(setupDraft) },
                    onDone = {
                        settingsViewModel.open(setupDraft)
                        settingsViewModel.saveAndClose()
                        setupCodeEditing = false
                        navTo(Route.SERVERS.name)
                    },
                    scope = scope,
                    onScanCode = { setupScannerOpen = true },
                    onPasteCode = { text ->
                        SetupCodeOffer.fromText(text)?.let { offer ->
                            presentSetupOffer(offer)
                            true
                        } == true
                    },
                )
            }

            Route.SPOKEN_PREFIXES -> SpokenPrefixesScreen(
                rules = spokenPrefixes,
                bots = botsState.bots,
                onAdd = { rule ->
                    prefixDraft = rule
                    editingPrefix = spokenPrefixes.size
                    navTo(Route.SPOKEN_PREFIX_EDITOR.name)
                },
                onRemove = { index ->
                    scope.launch { settingsRepo.setSpokenPrefixes(spokenPrefixes.filterIndexed { i, _ -> i != index }) }
                },
                onOpenRule = { index ->
                    prefixDraft = spokenPrefixes.getOrNull(index)
                    editingPrefix = index
                    navTo(Route.SPOKEN_PREFIX_EDITOR.name)
                },
                onBack = { navTo(Route.VOICE_SETTINGS.name) },
            )

            Route.SPOKEN_PREFIX_EDITOR -> {
                val draft = prefixDraft
                if (draft == null) {
                    navTo(Route.SPOKEN_PREFIXES.name)
                } else {
                    var models by remember(state.server.id) { mutableStateOf<List<String>>(emptyList()) }
                    LaunchedEffect(state.server.id) {
                        models = runCatching { com.onyx.app.data.net.ChatCompletionsClient(state.server).testConnection() }
                            .getOrNull()?.models.orEmpty()
                    }
                    SpokenPrefixEditorScreen(
                        rule = draft,
                        bots = botsState.bots,
                        models = models,
                        onChange = { updated ->
                            prefixDraft = updated
                            val index = editingPrefix ?: spokenPrefixes.size
                            val next = spokenPrefixes.toMutableList()
                            if (index < next.size) next[index] = updated else next.add(updated)
                            scope.launch { settingsRepo.setSpokenPrefixes(next) }
                        },
                        onBack = { navTo(Route.SPOKEN_PREFIXES.name) },
                    )
                }
            }

            Route.STOP_PHRASES -> {
                val phrases by settingsRepo.stopPhrases.collectAsStateWithLifecycle()
                StopPhrasesScreen(
                    phrases = phrases,
                    onAdd = { phrase -> scope.launch { settingsRepo.setStopPhrases(phrases + phrase) } },
                    onRemove = { phrase -> scope.launch { settingsRepo.setStopPhrases(phrases - phrase) } },
                    onBack = { navTo(Route.VOICE_SETTINGS.name) },
                )
            }

            Route.SPEECH_LANGUAGE -> {
                val locale by settingsRepo.speechLocale.collectAsStateWithLifecycle()
                SpeechLanguageScreen(
                    current = locale,
                    onPick = { tag -> scope.launch { settingsRepo.setSpeechLocale(tag) } },
                    onBack = { navTo(Route.VOICE_SETTINGS.name) },
                )
            }

            Route.REPLY_LANGUAGE -> {
                val language by settingsRepo.replyLanguage.collectAsStateWithLifecycle()
                ReplyLanguageScreen(
                    current = language,
                    onPick = { code -> scope.launch { settingsRepo.setReplyLanguage(code) } },
                    onBack = { navTo(Route.VOICE_SETTINGS.name) },
                )
            }

            Route.CONTEXT, Route.MEMORY -> {
                val contextViewModel: ContextViewModel = viewModel(
                    key = "context-" + state.server.id + state.server.configSignature,
                    factory = viewModelFactory { initializer { ContextViewModel(state.server) } },
                )
                ContextScreen(
                    viewModel = contextViewModel,
                    mode = if (presentedRoute == Route.CONTEXT) Mode.CONTEXT else Mode.MEMORY,
                    onBack = { navTo(Route.SETTINGS.name) },
                    onOpenFile = { file: ContextFile ->
                        contextFileName = file.name
                        contextEditor = null
                        contextReturn = route
                        navTo(Route.CONTEXT_EDITOR.name)
                    },
                )
            }

            Route.CONTEXT_EDITOR -> {
                val contextFile = contextFileName?.let { name -> ContextFile.entries.firstOrNull { it.name == name } }
                val editor = contextEditor
                when {
                    contextFile != null -> {
                        val editorViewModel: ContextFileEditorViewModel = viewModel(
                            key = "context-file-editor-" + state.server.id + state.server.configSignature + "-" + contextFile.name,
                            factory = viewModelFactory {
                                initializer { ContextFileEditorViewModel(state.server, contextFile) }
                            },
                        )
                        ContextFileEditorScreen(
                            viewModel = editorViewModel,
                            file = contextFile,
                            onBack = { if (!navBack()) navTo(contextReturn) },
                        )
                    }

                    editor == null -> LaunchedEffect(Unit) { navFromEmpty(Route.SETTINGS.name) }

                    else -> {
                        // La personnalité d'un bot garde la route de profil de l'éditeur générique.
                        val (label, purpose, path) = editor
                        val soulProfile = if (contextReturn == Route.BOT_EDITOR.name) path else null
                        val editorViewModel: FileEditorViewModel = viewModel(
                            key = "context-editor-" + path,
                            factory = viewModelFactory {
                                initializer { FileEditorViewModel(state.server, path, soulProfile) }
                            },
                        )
                        FileEditorScreen(
                            viewModel = editorViewModel,
                            onBack = { navTo(contextReturn) },
                            title = label,
                            purpose = purpose,
                        )
                    }
                }
            }

            Route.FILES -> {
                val filesViewModel: FilesViewModel = viewModel(
                    key = "files-" + state.server.id + state.server.configSignature,
                    factory = viewModelFactory { initializer { FilesViewModel(state.server) } },
                )
                FilesScreen(
                    viewModel = filesViewModel,
                    onBack = { navTo(Route.SETTINGS.name) },
                    onOpenFile = { entry ->
                        editorPath = entry.path
                        navTo(Route.FILE_EDITOR.name)
                    },
                )
            }

            Route.FILE_EDITOR -> {
                val path = editorPath
                if (path == null) {
                    navTo(Route.FILES.name)
                } else {
                    val editorViewModel: FileEditorViewModel = viewModel(
                        key = "editor-" + path,
                        factory = viewModelFactory { initializer { FileEditorViewModel(state.server, path) } },
                    )
                    FileEditorScreen(
                        viewModel = editorViewModel,
                        onBack = { navTo(Route.FILES.name) },
                    )
                }
            }

            Route.GATEWAY -> {
                val gatewayViewModel: GatewayViewModel = viewModel(
                    key = "gateway-" + state.server.id + state.server.configSignature,
                    factory = viewModelFactory { initializer { GatewayViewModel(state.server) } },
                )
                GatewayScreen(
                    viewModel = gatewayViewModel,
                    onBack = { navTo(Route.SETTINGS.name) },
                    onOpenLogs = { navTo(Route.LOGS.name) },
                )
            }

            Route.LOGS -> {
                val logsViewModel: LogsViewModel = viewModel(
                    key = "logs-" + state.server.id + state.server.configSignature,
                    factory = viewModelFactory { initializer { LogsViewModel(state.server) } },
                )
                LogsScreen(
                    viewModel = logsViewModel,
                    onBack = { navTo(Route.GATEWAY.name) },
                )
            }

            Route.APPEARANCE -> AppearanceScreen(
                theme = theme,
                appearance = appearance,
                themeColors = themeColors,
                displayName = displayName,
                autoCards = autoCards,
                autoVoice = autoVoice,
                botFaces = botFaces,
                botPictures = botPictures,
                botColors = botColors,
                linkPreviews = linkPreviews,
                chatTextSize = chatTextSize,
                voiceOrb = voiceOrb,
                orbLight = orbLight,
                onPickTheme = settingsViewModel::setTheme,
                onPickAppearance = settingsViewModel::setAppearance,
                onPickThemeColors = settingsViewModel::setThemeColors,
                onDisplayName = settingsViewModel::setDisplayName,
                onPickAutoCards = settingsViewModel::setAutoCardLook,
                onPickAutoVoice = settingsViewModel::setAutoVoiceLook,
                onBotFaces = settingsViewModel::setBotFaces,
                onBotPictures = settingsViewModel::setBotPictures,
                onBotColors = settingsViewModel::setBotColors,
                onLinkPreviews = settingsViewModel::setLinkPreviews,
                onChatTextSize = settingsViewModel::setChatTextSize,
                onPickVoiceOrb = settingsViewModel::setVoiceOrb,
                onPickOrbLight = settingsViewModel::setOrbLight,
                onBack = { navTo(Route.SETTINGS.name) },
            )

            Route.MODELS -> {
                val effort by settingsRepo.reasoningEffort.collectAsStateWithLifecycle()
                ModelsScreen(
                server = state.server,
                reasoningEffort = effort,
                onPickReasoning = { value -> scope.launch { settingsRepo.setReasoningEffort(value) } },
                onPick = { model ->
                    // Le modèle vit sur le serveur: on l'y réécrit, et le dépôt le persiste.
                    settingsViewModel.open(state.server)
                    settingsViewModel.update { it.copy(model = model) }
                    settingsViewModel.saveAndClose()
                    navTo(Route.CHAT.name)
                },
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                serverSessionId = state.serverSessionId,
                )
            }
                    }
                }
            
              }}
        }

        if (openingRect != null) {
            com.onyx.app.ui.design.OpeningCover(
                source = openingRect,
                title = "",
                // Rien à échanger dessous: le chat est déjà ouvert. La surface se retire à la
                // fin de son effacement (le `clearing` d'`OpeningCover.run` ) — l'effacer
                // dès l'échange coupait le fondu et laissait la couverture disparaître d'un coup.
                onFinished = { openingRect = null },
            )
        }

        // « Let bots message each other? »: la question d'abord, et l'écriture ensuite — la même
        // `confirmationDialog` qu', avec ses deux issues.
        if (askingLetBotsMessage) {
            OnyxAlert(
                title = "Let bots message each other?",
                message = "Hermes gives each bot's chat a tool to message the other bots, so you can say “@scout, what do you think?” in one bot's chat and have it ask. Onyx turns this on the way Hermes Desktop does, by setting your main profile up as a bot on the server. Chats with your agent stay as they are.",
                actions = listOf(
                    AlertAction("Let Them") {
                        askingLetBotsMessage = false
                        scope.launch {
                            val failure = botsViewModel.letBotsMessage()
                            letBotsOutcome = failure
                                ?: OnyxLocalization["Done — tag a bot with @ in any bot chat, and it will ask it."]
                        }
                    },
                    AlertAction("Cancel") { askingLetBotsMessage = false },
                ),
                onDismiss = { askingLetBotsMessage = false },
            )
        }

        letBotsOutcome?.let { outcome ->
            OnyxAlert(
                title = OnyxLocalization["Bots can message each other"],
                message = outcome,
                actions = listOf(AlertAction("OK") { letBotsOutcome = null }),
                onDismiss = { letBotsOutcome = null },
            )
        }

        if (renaming) {
            OnyxPrompt(
                title = "Rename conversation",
                placeholder = "Name",
                value = renameText,
                onValueChange = { renameText = it },
                confirmLabel = "Save",
                onConfirm = {
                    renaming = false
                    chatViewModel.rename(renameText)
                },
                onDismiss = { renaming = false },
            )
        }
    }
    }
}
