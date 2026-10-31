package com.onyx.app.ui
import com.onyx.app.ui.design.LocalBackdrop
import com.onyx.app.ui.design.recordBackdrop

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
import androidx.compose.runtime.State
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
import androidx.compose.runtime.withFrameNanos
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
import com.onyx.app.data.local.profileStateMigration
import com.onyx.app.data.local.ChatPanelStore
import com.onyx.app.ui.design.ChatTab
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SetupCodeOffer
import com.onyx.app.di.AppContainer
import com.onyx.app.intake.ShareInbox
import com.onyx.app.notifications.ApprovalNotifier
import com.onyx.app.notifications.CallRinger
import com.onyx.app.notifications.NotificationCopy
import com.onyx.app.notifications.QuestionNotifier
import com.onyx.app.notifications.TurnService
import com.onyx.app.notifications.pendingQuestionsOf
import com.onyx.app.ui.chat.ComposerEditorScreen
import com.onyx.app.ui.chat.ChatScreen
import com.onyx.app.ui.chat.ChatViewModel
import com.onyx.app.ui.chat.components.StartCard
import com.onyx.app.ui.chat.components.chatHeaderTitle
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
import com.onyx.app.ui.bots.BotMenuHandlers
import com.onyx.app.ui.bots.botRecentConversation
import com.onyx.app.ui.bots.botOfConversation
import com.onyx.app.ui.bots.freshConversationForPencil
import com.onyx.app.ui.bots.BotEditorViewModel
import com.onyx.app.ui.bots.BotVoicePickerScreen
import com.onyx.app.ui.bots.GroupChatScreen
import com.onyx.app.ui.bots.GroupChatViewModel
import com.onyx.app.ui.bots.GroupRoomScreen
import com.onyx.app.ui.bots.GroupRoomViewModel
import com.onyx.app.ui.bots.roomStartsAnotherConversation
import com.onyx.app.ui.bots.toRoom
import com.onyx.app.ui.bots.GroupVoice
import com.onyx.app.ui.bots.GroupVoiceDemo
import com.onyx.app.ui.bots.GroupVoiceScene
import com.onyx.app.ui.bots.GroupVoiceThread
import com.onyx.app.ui.bots.HiddenBotsScreen
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.sidebar.AccountSheet
import com.onyx.app.ui.sidebar.ArchivedScreen
import com.onyx.app.ui.sidebar.ChatSidebarHost
import com.onyx.app.ui.sidebar.SidebarMode
import com.onyx.app.ui.sidebar.SidebarScaffold
import com.onyx.app.ui.sidebar.SidebarTile
import com.onyx.app.ui.sidebar.SidebarToggleControl
import com.onyx.app.ui.sidebar.sidebarModeFor
import com.onyx.app.data.model.serve.BotMentions
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.kanban.KanbanScreen
import com.onyx.app.ui.kanban.KanbanViewModel
import com.onyx.app.ui.code.CodeScreen
import com.onyx.app.ui.code.CodeViewModel
import com.onyx.app.ui.code.PreferencesCodeSessionStorage
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
import com.onyx.app.ui.chat.shareableUri
import com.onyx.app.ui.chat.PhotoViewerScreen
import com.onyx.app.ui.chat.ViewerPhoto
import com.onyx.app.ui.chat.SelectableTextScreen
import com.onyx.app.ui.tools.ToolsScreen
import com.onyx.app.ui.capabilities.CapabilitiesScreen
import com.onyx.app.ui.capabilities.SkillEditorScreen
import com.onyx.app.ui.insights.InsightsScreen
import com.onyx.app.ui.ledger.AgentLedgerScreen
import com.onyx.app.ui.servers.ProfilePickerScreen
import com.onyx.app.ui.common.WhatsNewScreen
import com.onyx.app.ui.common.CURRENT_RELEASE_ITEMS
import com.onyx.app.ui.servers.ServersScreen
import com.onyx.app.ui.setup.SetupScreen
import com.onyx.app.ui.setup.SetupSuggestions
import com.onyx.app.ui.setup.setupAddressOf
import com.onyx.app.ui.setup.setupLinkFor
import com.onyx.app.ui.setup.SetupCodeFinish
import com.onyx.app.ui.setup.SetupCodeScanner
import com.onyx.app.ui.setup.SetupCodeSheet
import com.onyx.app.ui.setup.serverAfterSkip
import com.onyx.app.ui.setup.skipSavesDraft
import com.onyx.app.ui.settings.LockSettingsScreen
import com.onyx.app.ui.settings.PhoneToolsScreen
import com.onyx.app.ui.settings.CallsScreen
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
import com.onyx.app.ui.voice.VoiceAlertAction
import com.onyx.app.ui.voice.VoiceOrbChoice
import com.onyx.app.ui.voice.VoiceScreen
import com.onyx.app.ui.voice.VoiceScenarioDemo
import com.onyx.app.ui.voice.soloVoiceStop
import com.onyx.app.voice.BotVoicePicker
import com.onyx.app.voice.VoicePhase
import com.onyx.app.voice.VoiceSession
import com.onyx.app.widget.OnyxWidget
import androidx.glance.appwidget.updateAll

/** Les écrans de l'app ; la navigation tient dans un seul état, comme un `NavigationStack`. */
private enum class Route {
    CHAT, SETTINGS, APPEARANCE, MODELS, VOICE,
    GATEWAY, LOGS, FILES, FILE_EDITOR, CONTEXT, MEMORY, CONTEXT_EDITOR, VOICE_SETTINGS, NOTIFICATIONS, KOKORO_VOICES, CRON, KANBAN, CODE,
    COMPOSER_EDITOR, PHONE_TOOLS, CALLS, LOCK, CONNECT, BOTS, BOT_EDITOR, BOT_FACE, BOT_SCHEDULES, BOT_VOICE, GROUP_CHAT, GROUP_VOICE, ACKNOWLEDGEMENTS, SPEECH_LANGUAGE, REPLY_LANGUAGE,
    COMPUTER,
    STOP_PHRASES, SERVERS, SETUP, SPOKEN_PREFIXES, SPOKEN_PREFIX_EDITOR, PROFILES, WHATS_NEW, PATCH, SELECT_TEXT, PHOTO, PDF, VIDEO, SKILL_EDITOR, TOOLS, API_BOT, CAMERA, TIP_JAR, SUBAGENT, SUBAGENTS, TERMINAL, TERMINAL_SETTINGS,
    // Les destinations de l'onglet **Fichiers** qui n'avaient pas de lecteur : un son (le lecteur
    // de la maison), un document texte, et le contenu d'une archive.
    AUDIO, DOCUMENT, ARCHIVE,
    // « Capture rapide » : une note prise en passant, classée par l'agent.
    QUICK_CAPTURE,
    // Les quatre destinations d'la référence qui manquaient : `ConnectionDetailsView`, `HiddenBotsView`,
    // `SessionUsageView` et `ProjectSessionsView`.
    CONNECTION_DETAILS, HIDDEN_BOTS, SESSION_USAGE, PROJECT_SESSIONS,
    // Les deux écrans de ce lot : « Capacités » (compétences, outils, serveurs MCP) et
    // « Aperçus » (coût, sessions, jetons, cache, jetons par jour, modèles). Ce sont les deux
    // destinations des tuiles « Capacités » et « Aperçus » du volet.
    CAPABILITIES, INSIGHTS,
    // **« Archives »** — la destination de la tuile *Archived* de la référence, celle qui
    // manquait : les conversations rangées de cet appareil (leur état existait déjà). Le volet la
    // porte comme ses autres tuiles (voir `SidebarDestinations.referenceTiles`).
    ARCHIVED,
    // **« Relevé de l'agent »** : ce que l'agent a fait, avec quoi, combien de temps, et ce que cela
    // a coûté — pour la conversation ouverte, ou pour un bot en cumul. Ouvert par le menu « … » du
    // fil (voir l'entrée « Agent ledger » du menu de conversation).
    LEDGER,
}

/**
 * La maquette de boutique du mode vocal, comme `-echo.voiceDemo` d'la référence : le lancement la demande
 * (`-e onyx.voiceDemo true`), et l'écran vocal se pose en écoute sans qu'aucun micro n'existe.
 * Rien de tout ceci ne sert hors d'un APK de debug.
 */
private const val EXTRA_VOICE_DEMO = "onyx.voiceDemo"

/**
 * La maquette de la scène vocale d'une salle (`-e onyx.groupVoiceDemo speaking`) : la réplique qui
 * se lit est **injectée**, sans serveur ni micro, comme le drapeau de la référence. Debug seulement.
 */
private const val EXTRA_GROUP_VOICE_DEMO = "onyx.groupVoiceDemo"

/**
 * La doublure de QA du roster des bots (`am start -e onyx.demoBots true`) : le pendant Android du
 * drapeau de la référence. Elle ne pose que la liste des mentions `@` (et l'identité de la
 * conversation ouverte), jamais un réglage : sans l'extra, le roster est celui du serveur.
 */
private const val EXTRA_DEMO_BOTS = "onyx.demoBots"

/** Les mots de la maquette : la phrase par défaut du crochet d'la référence, mot pour mot. */
private const val VOICE_DEMO_WORDS = "Okay, prune anything older than thirty days and try the media share again"

/** Le titre de la conversation, tronqué à 170 points comme `soloHeader` le fait. */
private const val VOICE_DEMO_TITLE = "What's on my calendar tomorrow?"

/**
 * La **pose** de la scène vocale pour une capture (`-e onyx.voicePhase thinking`) : un émulateur n'a
 * ni micro ni moteur de reconnaissance, donc les états d'erreur et les phases ne peuvent pas être
 * produits pour de vrai. Le drapeau les pose, comme `onyx.voiceDemo` pose déjà l'écoute — et il ne
 * change **rien** quand il est absent. Debug seulement, comme les autres.
 */
private const val EXTRA_VOICE_PHASE = "onyx.voicePhase"

/** Le bandeau d'un refus du moteur, posé pour une capture (`-e onyx.voiceProblem "…"`). Debug. */
private const val EXTRA_VOICE_PROBLEM = "onyx.voiceProblem"

/**
 * L'alerte « on n'entend rien » posée pour une capture (`--ez onyx.voiceNoSound true`) : un
 * émulateur n'a pas de micro, donc l'état ne peut pas être produit pour de vrai. Le drapeau ne
 * change **rien** quand il est absent. Debug seulement, comme les autres.
 */
private const val EXTRA_VOICE_NO_SOUND = "onyx.voiceNoSound"

/**
 * **La chaîne vocale rejouée sans micro** (`am start -e onyx.voiceScenario phrase`).
 *
 * L'émulateur n'a pas de micro : la boucle complète (ouverture de l'écoute, détection de fin de
 * parole, envoi de la question, réflexion, réponse, reprise de l'écoute, arrêts, plafond d'échecs)
 * ne pouvait donc pas être prouvée — la seule chose qu'on y voyait était un moteur qui démarre.
 * Le drapeau pose un **moteur alimenté par un WAV français** ([VoiceScenarioDemo]) et un lecteur de
 * test, et câble la réponse **localement** (aucun serveur sur un émulateur).
 *
 * Trois scénarios : `phrase` (le parcours complet), `silencieux` (le moteur tourne sans rien
 * entendre — la couche d'honnêteté doit le dire) et `echec` (plafond d'échecs, message, réessai).
 * **Debug seulement** ; sans le drapeau, rien de tout ceci n'est instancié.
 */
private const val EXTRA_VOICE_SCENARIO = "onyx.voiceScenario"

/**
 * La doublure de QA des sous-agents (`am start --ez onyx.subagentDemo true`) : un fil et un
 * roster **injectés** (une tâche qui travaille, une qui démarre, une lancée à l'aveugle, une
 * finie, une en échec), faute de Dashboard et d'agent sur un émulateur. Debug seulement ; sans le
 * drapeau, rien n'est injecté.
 */
private const val EXTRA_SUBAGENT_DEMO = "onyx.subagentDemo"

/**
 * La doublure de QA du **relevé d'agent** (`am start -e onyx.ledgerDemo chargee`) : un fil, un
 * registre de sessions et une table de prix **posés**, faute de Dashboard et d'agent sur un
 * émulateur. Quatre cas nommés — `fraiche` (conversation neuve), `chargee` (conversation chargée),
 * `sans-prix` (un bot dont le fournisseur ne publie aucun prix) et `manquante` (ce que le transport
 * n'a pas conservé). Les valeurs posées sont celles relevées sur le serveur réel : la doublure ne
 * sert qu'à les montrer sans serveur. Sans le drapeau, rien n'est posé et l'écran lit le serveur.
 */
private const val EXTRA_LEDGER_DEMO = "onyx.ledgerDemo"

/**
 * Le **journal vocal de l'appareil, recopié dans logcat** (`am start --ez onyx.voiceJournal true`).
 *
 * La page Journaux de l'app n'est atteignable qu'avec un Dashboard joignable ; sur un émulateur qui
 * n'en a pas, les lignes du chemin vocal (question envoyée, bot qui prend la parole, tour fini,
 * échec d'envoi) restent donc invisibles — et ce sont elles qui disent ce qui casse. Le drapeau les
 * recopie dans logcat, ligne par ligne. **Debug seulement** : dans un APK release,
 * `BuildConfig.DEBUG` est faux, rien n'est recopié, et le drapeau ne change rien.
 */
private const val EXTRA_VOICE_JOURNAL = "onyx.voiceJournal"

/**
 * La chaine vocale de maquette branchee sur une **vraie salle** (`--ez onyx.voiceScenarioRoom true`).
 *
 * Sans ce drapeau, `onyx.voiceScenario` ouvre la scene **solo** et se repond a elle-meme : c'est ce
 * qu'il faut pour la boucle du solo, et c'est justement ce qui empechait d'eprouver une salle — la
 * question etait posee, mais a personne. Avec le drapeau, la maquette **n'ouvre rien** : la personne
 * navigue, ouvre sa salle et touche « Parler », et c'est le fil de la salle qui recoit la question
 * reconnue (`groups.send`). Debug seulement, et sans lui rien ne change.
 */
private const val EXTRA_VOICE_SCENARIO_ROOM = "onyx.voiceScenarioRoom"

/** La réplique de la pose « PARLE » : la phrase de la capture du propriétaire, mot pour mot. */
private const val VOICE_DEMO_REPLY = "Ça va bien, merci. Prêt à bosser. Toi, tu veux qu'on attaque quoi ?"

/** La phase demandée par [EXTRA_VOICE_PHASE], ou `null` si le drapeau est absent. */
private fun posedVoicePhase(raw: String?): VoicePhase? = when (raw?.trim()?.lowercase()) {
    "idle", "pret", "prêt", "ready" -> VoicePhase.IDLE
    "listening", "ecoute", "écoute" -> VoicePhase.LISTENING
    "thinking", "reflechit", "réfléchit" -> VoicePhase.THINKING
    "speaking", "parle" -> VoicePhase.SPEAKING
    else -> null
}

/** L'Activity derrière un `Context` de composition : ses `Intent` portent la maquette de boutique. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

/** Routes présentées par `.sheet` / `.fullScreenCover` dans les vues la référence correspondantes. */
private val PresentedRoutes = setOf(
    Route.SETTINGS, Route.MODELS, Route.VOICE, Route.COMPOSER_EDITOR, Route.SELECT_TEXT,
    Route.PHOTO, Route.PDF, Route.VIDEO, Route.AUDIO, Route.DOCUMENT, Route.ARCHIVE, Route.CAMERA, Route.TERMINAL, Route.SETUP, Route.CONNECT, Route.WHATS_NEW,
    Route.QUICK_CAPTURE,
    Route.BOT_EDITOR, Route.BOT_FACE, Route.API_BOT, Route.BOT_SCHEDULES, Route.BOT_VOICE, Route.SKILL_EDITOR, Route.SUBAGENT, Route.SUBAGENTS,
    Route.PATCH, Route.HIDDEN_BOTS, Route.SESSION_USAGE,
)
private val SheetDismissDestinations = setOf(
    Route.CHAT, Route.BOTS, Route.SERVERS, Route.SETTINGS, Route.HIDDEN_BOTS,
)
private val SectionRoutes = listOf(Route.CRON, Route.KANBAN, Route.CODE)
// Bots s'y ajoute : la liste des bots est une destination de la colonne, que le détail présente
// sans faire disparaître la barre latérale (la tablette : le détail du `NavigationSplitView`).
private val TwoPaneRoutes = setOf(Route.CHAT, Route.CRON, Route.KANBAN, Route.CODE, Route.BOTS)

/** Transitions de présentation ; les ressorts natifs des sheets/segments restent approximés. */
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
 * La racine : le thème de l'utilisateur, puis la conversation et les écrans qu'elle ouvre.
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

    // **L'identité d'une conversation, par identifiant de serveur** : le **nom de profil** de son
    // bot — la clé unique de la maison pour résoudre un visage et sa marque (`BotLook`). Un serveur
    // sans profil nommé est le bot principal (`default`), jamais un libellé : c'est le libellé qui
    // faisait « deux visages pour le même bot » (le profil s'appelle `default`, le libellé « Onyx »).
    val serverList by servers.servers.collectAsStateWithLifecycle()
    val identityByServer = remember(serverList) {
        serverList.associate { server -> server.id to (server.effectiveProfile ?: "default") }
    }

    val theme by settingsRepo.theme.collectAsStateWithLifecycle()
    val appearance by settingsRepo.appearance.collectAsStateWithLifecycle()
    val themeColors by settingsRepo.themeColors.collectAsStateWithLifecycle()
    val displayName by settingsRepo.displayName.collectAsStateWithLifecycle()
    val autoCards by settingsRepo.autoCardLook.collectAsStateWithLifecycle()
    val autoVoice by settingsRepo.autoVoiceLook.collectAsStateWithLifecycle()
    // Réglages → Apparence : les visages de bots, leurs photos, leurs couleurs, le texte du chat
    // et l'orbe vocal (tous portés par `Settings.shared` côté la référence).
    val botFaces by settingsRepo.botFaces.collectAsStateWithLifecycle()
    val botPictures by settingsRepo.botPictures.collectAsStateWithLifecycle()
    val botColors by settingsRepo.botColors.collectAsStateWithLifecycle()
    val linkPreviews by settingsRepo.linkPreviews.collectAsStateWithLifecycle()
    val chatTextSize by settingsRepo.chatTextSize.collectAsStateWithLifecycle()
    // « Noir pur » et « Halo » : les deux réglages d'affichage portés par la carte Apparence.
    val pureBlack by settingsRepo.pureBlack.collectAsStateWithLifecycle()
    val halo by settingsRepo.halo.collectAsStateWithLifecycle()
    // « Fluide » : la couverture animée des cartes de conversation (variante B), vrai par défaut.
    val fluideCover by settingsRepo.fluideCover.collectAsStateWithLifecycle()
    // **Le choix de la couverture** : variante, graine, teinte — écrits en préférences (filaments,
    // graine 3417634283, teinte bleu) et relus ici. C'est lui qui porte la graine du dessin.
    val fluideChoix by settingsRepo.fluideChoix.collectAsStateWithLifecycle()
    val voiceOrb by settingsRepo.voiceOrb.collectAsStateWithLifecycle()
    val orbLight by settingsRepo.orbLight.collectAsStateWithLifecycle()
    // Le volet de gauche des deux panneaux, replié ou non : un réglage de l'appareil, donc relu
    // ici comme les autres réglages d'interface (Apparence, Voix…).
    val sidebarCollapsed by settingsRepo.sidebarCollapsed.collectAsStateWithLifecycle()
    // « Halo » : le verre réagit-il au doigt ? Un seul interrupteur éteint l'appui **non lumineux** —
    // l'enfoncement des petites surfaces et l'assombrissement des grandes — voir `GlassTouch.kt`.
    // L'écran des Réglages qui l'affiche est porté par un autre lot ; ici on **lit** la clé et on la
    // pose, rien de plus.
    val glassHalo by settingsRepo.glassHalo.collectAsStateWithLifecycle()
    // Le volet du Chat — sa mémoire est **par largeur** (voir `OnyxSidebarState`) : la
    // surimpression du téléphone et le panneau posé de la tablette ont chacun la leur, si bien
    // qu'ouvrir l'un ne laisse pas l'autre ouvert.
    val sidebarState = com.onyx.app.ui.sidebar.rememberOnyxSidebarState()
    // La langue de l'app — le même stockage que le réglage Système / Français / English. Un lien
    // « tout en un » peut la poser dès la configuration. On la pose **avant de dessiner**, et tout
    // l'arbre est remonté sous `key(appLanguage)` : un changement de langue se voit tout de suite.
    val appLanguage by settingsRepo.language.collectAsStateWithLifecycle()

    // Les photos des bots arrivent une fois le roster lu : l'état est tenu ici pour que les
    // visages de toute l'app le voient, où qu'ils soient dessinés.
    var botPicturesByName by remember { mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }

    CompositionLocalProvider(
        com.onyx.app.ui.design.LocalBotFaces provides botFaces,
        com.onyx.app.ui.design.LocalBotPictures provides if (botPictures) botPicturesByName else emptyMap(),
        // Le réglage « Halo » : posé **au-dessus** de tout l'arbre, donc lu par chaque vitre. Éteint,
        // la surface est inerte au doigt — aucune lumière, aucun appui (voir `GlassTouch.kt`).
        com.onyx.app.ui.design.LocalGlassHalo provides glassHalo,
        // « Fluide » : posé au-dessus de tout l'arbre, donc lu par chaque carte de conversation.
        // Éteint, la carte reprend l'ancienne couverture déterministe.
        com.onyx.app.ui.design.LocalFluideEnabled provides fluideCover,
        // **Le choix de la couverture** : variante, graine et teinte, posés au-dessus de tout
        // l'arbre — c'est ce qui fait qu'une carte dessine le matériau retenu (filaments, graine de
        // la planche, teinte bleu) et non plus une teinte tirée de la conversation.
        com.onyx.app.ui.design.LocalFluideChoix provides fluideChoix,
    ) {
    androidx.compose.runtime.key(appLanguage) {
    OnyxAppTheme(theme = theme, appearance = appearance, colors = themeColors[theme.id], pureBlack = pureBlack, halo = halo) {
        // Le verrou couvre tout : on ne voit rien de l'app avant de s'être authentifié.
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

        // Les adresses du réseau de ce téléphone, lues une fois : ce que le mode « Simple » propose
        // en plus des serveurs déjà enregistrés. Relire les interfaces à chaque recomposition ne
        // servirait à rien et coûterait cher.
        val setupLocalAddresses = remember { com.onyx.app.ui.setup.SetupSuggestions.localAddresses() }

        // Le roster est lu tôt : la conversation ouverte le dit deux fois — son fil (le visage, le
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
        // Doublure de QA (`am start -e onyx.demoBots true`) : l'émulateur qui capture n'a aucun
        // Dashboard, donc aucun roster à lire, et la liste des mentions `@` a besoin de bots pour
        // se montrer. Le la référence a le même crochet (`-echo.demoBots`), et la doublure ne prend la
        // place du serveur que sur cette demande explicite.
        val demoBotsRun = remember {
            context.findActivity()?.intent?.getBooleanExtra(EXTRA_DEMO_BOTS, false) == true
        }
        val bots = if (demoBotsRun) BotRosterStandIn.bots() else botsState.bots
        // Ce que la liste des mentions `@` propose : `BotRoster.listed` — le principal d'abord,
        // les bots cachés écartés, aucun quand le principal est seul.
        val mentionRoster = remember(bots, state.server.displayName) {
            BotMentions.rosterForMentions(bots, state.server.displayName.ifBlank { "Onyx" })
        }
        // Le visage d'un profil, résolu **une fois** et lu par la liste comme par une salle : la
        // table du roster (le choix du téléphone puis `ui_meta.onyx.face`, réunis par
        // `withSwiftFaces`) et les visages que les bots portent déjà — une doublure de
        // démonstration comprise. Une salle qui lisait le choix local seul montrait un autre visage
        // que la liste pour le même bot.
        val faceResolver = remember(bots, botsState.faces) {
            com.onyx.app.ui.bots.BotFaceResolver.of(
                resolved = bots.mapNotNull { bot -> bot.faceCode?.let { code -> bot.name to code } }.toMap() +
                    botsState.faces,
            )
        }
        // Le nom de qui parle dans la conversation ouverte : le libellé du bot quand c'en est un,
        // sinon le nom d'agent des Réglages (ou celui du serveur quand il est vide).
        val openConversation = history.firstOrNull { it.id == state.conversationId }
        val openConversationBot = openConversation?.let { conversation ->
            botOfConversation(conversation, bots, state.server.displayName)
        }
        val threadName = openConversationBot?.label(state.server.displayName)
            ?: state.server.displayName.ifBlank { "Onyx" }

        // Le briseur de boucle : le drapeau « restauration en cours » est posé ici, avant que la
        // route, l'onglet et le brouillon ne soient restaurés, et effacé dès le premier dessin
        // passé. Un démarrage qui le retrouve déjà posé est un démarrage **de secours** : Chat,
        // brouillon sans sa pièce jointe, onglet oublié — donc jamais deux fois le même plantage.
        val startupGuard = remember { com.onyx.app.ui.StartupGuard.with(context.applicationContext) }
        val startPlan = remember { startupGuard.begin() }

        var route by rememberSaveable { mutableStateOf(Route.CHAT.name) }
    // Pile de navigation : le geste de retour Android (bord d'écran) et le bouton doivent
    // remonter à l'écran précédent, comme le swipe-back d'la référence — et non fermer l'app. `route`
    // reste la valeur courante pour tout le code existant ; les affectations passent par `navTo`.
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
     * Quitte un écran dont l'état est vide. La route vide **n'entre pas** dans la pile : y revenir
     * n'aurait aucun sens (sa charge utile n'existe plus), et l'y laisser avalait le retour — voir
     * [navigateLeavingEmpty]. À appeler **hors de la composition** (`LaunchedEffect`) : un `navTo`
     * écrit pendant la composition est une écriture d'état pendant la composition.
     */
    fun navFromEmpty(target: String) {
        route = navigateLeavingEmpty(route, target, backStack)
    }

    /**
     * Le démarrage lui-même : en secours (le précédent n'a pas atteint son premier dessin), on
     * revient sur Chat et on écarte la pièce jointe du brouillon — puis on efface le drapeau dès
     * le premier dessin, pour que le lancement suivant soit normal.
     */
    LaunchedEffect(Unit) {
        if (startPlan.dropDraftAttachments) {
            // On **retombe** sur Chat sans l'empiler : revenir en arrière depuis ce Chat ne doit
            // pas ramener sur l'écran qui a tué le lancement précédent.
            route = navigateLeavingEmpty(route, Route.CHAT.name, backStack)
            chatViewModel.discardDraftAttachments()
        }
        // Un dessin réussi, puis une courte marge : une image refusée par le moteur de rendu
        // lève au premier ou au deuxième dessin, et le drapeau doit encore être là pour que le
        // lancement suivant sache qu'il faut revenir en arrière.
        withFrameNanos { }
        kotlinx.coroutines.delay(900)
        startupGuard.settled()
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
        // La salle choisie dans la liste des bots : elle teinte sa rangée et pré-remplit le chat
        // de groupe avec ses membres (le fil d'une salle du serveur n'est pas encore porté).
        var openedRoom by remember { mutableStateOf<GroupChat?>(null) }
        var botCronProfile by rememberSaveable { mutableStateOf<String?>(null) }
        // Le bot dont la voix se règle (`BotVoicePicker`), ouvrable depuis son éditeur comme de
        // son menu de tuile : le profil est gardé pour qu'un processus recréé retrouve le bon.
        var botVoiceProfile by rememberSaveable { mutableStateOf<String?>(null) }
        var renaming by remember { mutableStateOf(false) }
        // « Let bots message each other » : la question d'abord, comme le confirmationDialog la référence.
        var askingLetBotsMessage by remember { mutableStateOf(false) }
        // Ce que le réglage « laisser les bots se parler » a donné : sans ça, l'appui ne montrait
        // rien du tout, ni réussite ni échec (« ça fonctionne pas »).
        var letBotsOutcome by remember { mutableStateOf<String?>(null) }
        var editingPrefix by remember { mutableStateOf<Int?>(null) }
        var patchText by remember { mutableStateOf<String?>(null) }
        var selectedText by remember { mutableStateOf<String?>(null) }
        var skillName by remember { mutableStateOf<String?>(null) }
        var openingRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var subagentAgent by remember { mutableStateOf<com.onyx.app.data.model.SubagentActivity?>(null) }
        // Doublure de QA des sous-agents (`onyx.subagentDemo`) : le roster et le fil injectés,
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
        // La doublure du relevé d'agent (`onyx.ledgerDemo <cas>`) : elle apporte le monde que
        // l'écran lit d'ordinaire sur le serveur — le fil, le roster, le registre et la table de
        // prix. Absente, l'écran lit le serveur ; rien d'autre ne change.
        val ledgerStandIn = remember {
            com.onyx.app.ui.ledger.LedgerStandIn.of(
                context.findActivity()?.intent?.getStringExtra(EXTRA_LEDGER_DEMO).orEmpty(),
            )
        }
        // La doublure **ouvre son écran** : sans Dashboard ni agent, il n'y a rien d'autre à poser,
        // et la capture doit pouvoir viser le relevé sans traverser la feuille du fil (qui, elle,
        // est atteinte par le menu « … » de l'en-tête — c'est le chemin réel, et il reste entier).
        LaunchedEffect(ledgerStandIn) {
            if (ledgerStandIn != null) navTo(Route.LEDGER.name)
        }
        var botModels by remember(state.server.id) { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(state.server.id) {
            botModels = runCatching { com.onyx.app.data.net.ChatCompletionsClient(state.server).testConnection() }
                .getOrNull()?.models.orEmpty()
        }

        // ---- Le sélecteur de modèle (fonction 2) : la feuille, ses modèles, ses préférences ----
        // Le niveau de réflexion vit déjà dans les Réglages ; la feuille le relit comme le fait
        // l'écran des modèles, un seul état pour les deux.
        val currentReasoning by settingsRepo.reasoningEffort.collectAsStateWithLifecycle()
        var showModelSheet by remember { mutableStateOf(false) }
        var modelChoices by remember(state.server.id) { mutableStateOf<List<com.onyx.app.ui.design.ModelChoice>>(emptyList()) }
        var modelChoicesLoading by remember(state.server.id) { mutableStateOf(false) }
        // Les profils Hermes et l'état de leur liste — le local du profil `default`, les distants du
        // profil `kilo`, et un profil injoignable marqué sans effacer les autres.
        var modelProfiles by remember(state.server.id) { mutableStateOf<List<com.onyx.app.ui.design.ProfileView>>(emptyList()) }
        // Favoris et récents de l'appareil : ce qui n'appartient qu'à ce téléphone, par serveur.
        val modelPrefs = remember(context) {
            com.onyx.app.data.local.ModelPreferenceStore(
                com.onyx.app.data.local.PrefsRenameStore(
                    context.applicationContext.getSharedPreferences(
                        com.onyx.app.data.local.ModelKeys.FILE,
                        android.content.Context.MODE_PRIVATE,
                    ),
                ),
            )
        }
        var modelFavourites by remember(state.server.id) { mutableStateOf(modelPrefs.favourites(state.server.id)) }
        var modelRecents by remember(state.server.id) { mutableStateOf(modelPrefs.recents(state.server.id)) }
        // La ligne **« Mode rapide »** de la feuille : le serveur l'expose-t-il ? Les transports à
        // registre seulement ; sinon la feuille ne la dessine pas (voir `rememberModelFastMode`).
        val modelFastMode = com.onyx.app.ui.models.rememberModelFastMode(
            state.server,
            state.serverSessionId ?: state.conversationId,
        )
        // Le catalogue n'est lu **que** quand la feuille s'ouvre : une requête en moins au démarrage.
        // Il est lu **par profil** (le registre, puis les modèles de chaque profil) : la feuille
        // montre ainsi tous les profils, pas le seul alias de la passerelle.
        LaunchedEffect(showModelSheet, state.server.id) {
            if (!showModelSheet) return@LaunchedEffect
            modelChoicesLoading = true
            val catalog = runCatching { com.onyx.app.data.net.ModelCatalog.fetchAll(state.server) }
                .getOrDefault(emptyList())
            modelChoices = catalog.flatMap { it.models }
            modelProfiles = catalog.map { com.onyx.app.ui.design.ProfileView(it.profile, it.state) }
            modelChoicesLoading = false
        }
        var photoSet by remember { mutableStateOf<List<com.onyx.app.data.model.Attachment>>(emptyList()) }
        var photoIndex by remember { mutableStateOf(0) }
        // Le PDF montré dans le lecteur de l'app : son adresse et son nom.
        var pdfSource by remember { mutableStateOf<com.onyx.app.ui.chat.PdfSource?>(null) }
        // La vidéo montrée dans le lecteur de l'app : son adresse et son nom.
        var videoSource by remember { mutableStateOf<com.onyx.app.ui.chat.VideoSource?>(null) }
        // Le son, le document texte et l'archive ouverts depuis l'onglet Fichiers.
        var soundSource by remember { mutableStateOf<com.onyx.app.ui.chat.SoundSource?>(null) }
        var documentSource by remember { mutableStateOf<com.onyx.app.ui.chat.DocumentSource?>(null) }
        var archiveSource by remember { mutableStateOf<com.onyx.app.ui.chat.DocumentSource?>(null) }
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
        // La langue demandée par un lien « tout en un », posée à la **fin** de la configuration :
        // la changer pendant que les champs se remplissent remonterait l'écran et les effacerait.
        var pendingLanguage by remember { mutableStateOf("") }
        val spokenPrefixes by settingsRepo.spokenPrefixes.collectAsStateWithLifecycle()
        var renameText by remember { mutableStateOf("") }

        // Les cartes de départ suivent l'historique sans le relire à chaque recomposition.
        LaunchedEffect(history, state.messages.size) {
            startCards = chatViewModel.startCards()
        }

        val scope = rememberCoroutineScope()

        // « Repartir de zéro » : l'état de sa confirmation, et le corps de l'action lui-même —
        // écrit **une fois**, pour le crayon de l'en-tête, la commande « Nouvelle conversation » et
        // l'entrée du menu.
        var confirmingFresh by remember { mutableStateOf(false) }

        /**
         * La **feuille de compte du volet** : le rond de l'initiale du pied, ou le nom et l'adresse
         * de la tête, l'ouvrent. Elle dit l'état **réellement mesuré** de la passerelle et porte les
         * cinq gestes de la connexion ; c'est une feuille de la famille existante, posée par-dessus
         * le volet (qui reste ouvert derrière).
         */
        var accountSheetOpen by remember { mutableStateOf(false) }

        /**
         * Ouvre une conversation neuve avec **le même bot** : le contexte repart à neuf, et
         * l'ancienne reste dans l'historique, entière. Une conversation vide et jamais servie est
         * reprise au lieu d'en empiler une deuxième.
         */
        fun startFreshConversation() {
            val next = freshConversationForPencil(
                current = openConversation,
                bot = openConversationBot,
                conversations = history,
                serverId = state.server.id,
                mainName = state.server.displayName,
            )
            // Une conversation de bot se garde tout de suite : c'est ce qui la met dans la liste
            // sous son nom, et ce que l'en-tête relit pour son visage.
            if (openConversationBot != null && history.none { it.id == next.id }) {
                scope.launch { conversationRepo.save(next) }
            }
            chatViewModel.open(next)
        }

        /**
         * Ouvre un fichier joint avec le lecteur qui lui convient : le lecteur de PDF de l'app,
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
                    // La vidéo se lit dans l'app, comme le PDF et l'image : sa première image sert
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
                            // **Une copie durable se partage par le FileProvider, jamais en
                            // `file://`** : depuis Android 7, une `file://` confiée à une autre app
                            // lève `FileUriExposedException` et l'appui ne fait rien. Les pièces
                            // jointes vivent maintenant sous `filesDir`, donc cette conversion
                            // n'est pas un confort — c'est ce qui les rend ouvrables.
                            shareableUri(context, uri),
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

        /**
         * Un fichier de l'onglet **Fichiers**, déjà sur le téléphone, ouvert selon sa **famille**.
         *
         * C'est le seul endroit qui répond à « qu'est-ce qu'un appui fait ? » pour cet onglet : une
         * image, une vidéo, un son, un PDF et un texte ont chacun leur lecteur de la maison, une
         * archive d'alignement de fichiers montre son contenu, et tout le reste — comme un type
         * inconnu — part à l'application du système, qui est prévenue. **Aucune branche ne fait
         * rien** : c'est ce que le test de câblage vérifie, famille par famille.
         */
        fun openConversationFile(uri: android.net.Uri, name: String, mime: String) {
            when (com.onyx.app.data.model.fileOpenRoute(name, mime)) {
                // Les lecteurs déjà en place : l'image, la vidéo, le PDF et le système.
                com.onyx.app.data.model.FileOpenRoute.PHOTO,
                com.onyx.app.data.model.FileOpenRoute.VIDEO,
                com.onyx.app.data.model.FileOpenRoute.PDF,
                com.onyx.app.data.model.FileOpenRoute.EXTERNAL,
                -> openFile(uri = uri, name = name, mime = mime)

                com.onyx.app.data.model.FileOpenRoute.AUDIO -> {
                    soundSource = com.onyx.app.ui.chat.SoundSource(
                        uri = uri,
                        name = name.ifEmpty { "Audio" },
                    )
                    navTo(Route.AUDIO.name)
                }

                com.onyx.app.data.model.FileOpenRoute.TEXT -> {
                    documentSource = com.onyx.app.ui.chat.DocumentSource(
                        uri = uri,
                        name = name.ifEmpty { "Document" },
                    )
                    navTo(Route.DOCUMENT.name)
                }

                com.onyx.app.data.model.FileOpenRoute.ARCHIVE -> {
                    archiveSource = com.onyx.app.ui.chat.DocumentSource(
                        uri = uri,
                        name = name.ifEmpty { "Archive" },
                    )
                    navTo(Route.ARCHIVE.name)
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

        /**
         * Ce qu'un lien « tout en un » applique **au-delà** du serveur : l'adresse des voix, le pont
         * « Ordinateur » (adresse + jeton) et la langue de l'app. Un ancien lien n'a rien de tout
         * cela : la fonction ne fait alors rien.
         */
        suspend fun applySetupExtras(code: com.onyx.app.data.model.SetupCode) {
            if (code.voicesURL.isNotEmpty()) settingsRepo.setKokoroUrl(code.voicesURL)
            if (code.bridgeURL.isNotEmpty()) AppContainer.computer.address = code.bridgeURL
            if (code.bridgeToken.isNotEmpty()) AppContainer.computer.token = code.bridgeToken
            if (code.language.isNotEmpty()) pendingLanguage = code.language
        }

        /**
         * Ce que le mode « Simple » applique après sa découverte. Le jeton du pont que le serveur
         * annonce est **le même secret** que celui de la passerelle : l'utilisateur n'en retient qu'un.
         *
         * La langue, elle, est mise de côté : la changer tout de suite remonterait l'écran de
         * configuration sous `key(appLanguage)` et **effacerait les deux champs en cours**. Elle
         * s'applique donc à la fin de la configuration — au premier lancement, comme le contrat le
         * demande.
         */
        suspend fun applyDiscovery(found: com.onyx.app.data.net.DiscoveredSetup) {
            if (found.voices.isNotEmpty()) settingsRepo.setKokoroUrl(found.voices)
            if (found.bridge.isNotEmpty()) AppContainer.computer.address = found.bridge
            if (found.bridgeToken.isNotEmpty()) AppContainer.computer.token = found.bridgeToken
            if (found.language.isNotEmpty()) pendingLanguage = found.language
        }

        /** La langue demandée par un lien, posée quand la configuration est finie. */
        fun applyPendingLanguage() {
            val language = pendingLanguage
            if (language.isEmpty()) return
            pendingLanguage = ""
            scope.launch { settingsRepo.setLanguage(language) }
        }

        // La langue du micro et le mode mains-libres viennent des Réglages.
        val speechLocale by settingsRepo.speechLocale.collectAsStateWithLifecycle()
        val handsFreeByDefault by settingsRepo.handsFreeByDefault.collectAsStateWithLifecycle()
        // « Reconnaître hors ligne » : faux par défaut, et le **seul** chemin vers le hors ligne.
        val offlineRecognition by settingsRepo.offlineRecognition.collectAsStateWithLifecycle()
        val returnSends by settingsRepo.returnSends.collectAsStateWithLifecycle()

        val voiceSpeed by settingsRepo.voiceSpeed.collectAsStateWithLifecycle()
        val replyVoice by settingsRepo.replyVoice.collectAsStateWithLifecycle()
        val kokoroUrl by settingsRepo.kokoroUrl.collectAsStateWithLifecycle()
        val kokoroToken by settingsRepo.kokoroToken.collectAsStateWithLifecycle()
        val kokoroVoice by settingsRepo.kokoroVoice.collectAsStateWithLifecycle()
        val talkOver by settingsRepo.talkOver.collectAsStateWithLifecycle()
        val earpieceAtEar by settingsRepo.earpieceAtEar.collectAsStateWithLifecycle()

        // La maquette de la chaîne vocale (`onyx.voiceScenario`) : un moteur **alimenté par un
        // fichier audio** prend la place du reconnaisseur, et un lecteur de test la place de la
        // voix — c'est ce qui rend la boucle complète rejouable sur un émulateur sans micro.
        val voiceScenario = remember {
            if (!com.onyx.app.BuildConfig.DEBUG) null
            else VoiceScenarioDemo.from(
                context.findActivity()?.intent?.getStringExtra(EXTRA_VOICE_SCENARIO),
                context,
                scope,
            )
        }
        val voice = remember(speechLocale, voiceScenario) {
            VoiceSession(
                context,
                scope,
                speechLocale,
                earRouter = null,
                speechInput = voiceScenario?.engine,
                speechReply = voiceScenario?.reply,
            )
        }

        val onSoloVoiceQuestion = remember(chatViewModel, settingsRepo) {
            { question: String ->
                val routed = routeVoiceText(question, settingsRepo.spokenPrefixes.value)
                chatViewModel.sendText(routed.text, clearInput = false, modelOverride = routed.model)
            }
        }
        DisposableEffect(voice, voiceScenario) {
            voice.onQuestion = if (voiceScenario == null) {
                onSoloVoiceQuestion
            } else {
                // La maquette se répond **à elle-même** : sur un émulateur il n'y a ni serveur ni
                // agent, et c'est bien la **boucle** qu'on veut voir (écoute → question →
                // réflexion → réponse → reprise), pas la réponse d'un agent.
                { _ ->
                    scope.launch {
                        delay(700)
                        voice.finishTurn(VoiceScenarioDemo.REPLY, speaker = openConversationBot?.name)
                    }
                }
            }
            // « Stop » dit par-dessus la réponse : le tour qui s'écrit côté serveur n'a plus de
            // raison de continuer (`conversation.cancel()` d'la référence).
            voice.onStoppedByWord = { chatViewModel.stop() }
            onDispose { voice.release() }
        }

        // La vitesse de lecture et l'interruption suivent les Réglages, sans recréer la session.
        val stopPhrases by settingsRepo.stopPhrases.collectAsStateWithLifecycle()
        // La voix de chaque bot (`Settings.botVoices`) : c'est elle qui fait que deux bots de la
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
            kokoroToken,
            kokoroVoice,
            state.server.configSignature,
            botVoicesHere,
            offlineRecognition,
            voiceScenario,
        ) {
            voice.output?.setRate(voiceSpeed)
            voice.output?.configure(
                voice = replyVoice,
                kokoroUrl = kokoroUrl,
                kokoroToken = kokoroToken,
                kokoroVoice = kokoroVoice,
                hermesServer = state.server,
                // Les voix réglées pour les bots de ce serveur (`Settings.botVoicesHere`) : la clé
                // de l'effet est la carte elle-même, donc un réglage relance la lecture.
                botVoices = settingsRepo.botVoicesHere(state.server.id),
            )
            voice.talkOver = talkOver
            // « Earpiece when raised to your ear » : téléphone levé à l'oreille, la réponse s'y
            // joue comme un appel (capteur de proximité → écouteur).
            voice.earRoutingEnabled = earpieceAtEar
            // « Reconnaître hors ligne » : éteint (le défaut), l'intention ne porte **pas**
            // `EXTRA_PREFER_OFFLINE` — c'est la reconnaissance **en ligne** qui est le défaut, et
            // le hors ligne qui est un souhait. Allumé, l'écoute tente l'appareil seul et bascule
            // en ligne d'elle-même si le moteur n'a pas la langue.
            voice.offlineRecognition = offlineRecognition
            voice.stopPhrases = stopPhrases
            // La maquette de QA se relance après chaque réponse : c'est la **reprise de l'écoute**
            // qu'on veut prouver sur l'émulateur. Hors maquette, le réglage de la personne décide.
            voice.autoRelisten = voiceScenario != null
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
        // Le niveau des points : celui du moteur **et** celui du micro que l'app ouvre elle-même
        // quand le moteur n'en livre aucun (`VoiceSession.listenLevel`). Sans lui, un moteur muet
        // laissait sept points parfaitement plats sous « À L'ÉCOUTE ».
        val listenLevel by voice.listenLevel.collectAsStateWithLifecycle()

        // Le diagnostic d'écoute : « on n'entend rien » depuis cinq secondes, et le dernier échec
        // **définitif** du moteur (l'écoute est alors déjà close). L'un et l'autre se voient à
        // l'écran avec une action — jamais un écran qui prétend écouter sans rien dire.
        val noSound by voice.noSound.collectAsStateWithLifecycle()
        val engineFailure by voice.failure.collectAsStateWithLifecycle()
        // **La panne d'écoute jugée sur les faits du moteur** : jamais démarré, aucune trame,
        // trames sans un mot. C'est ce qui empêche l'écran de rester muet sur « À L'ÉCOUTE »
        // alors que rien ne rentre — y compris quand le niveau du micro, lui, bougeait.
        val voiceTrouble by voice.trouble.collectAsStateWithLifecycle()

        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                voiceProblem = null
                com.onyx.app.voice.VoiceDiagnostics.note("voice: permission du micro accordée")
                voice.startListening()
            } else {
                com.onyx.app.voice.VoiceDiagnostics.note("voice: permission du micro refusée (RECORD_AUDIO)")
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

        /**
         * La permission du micro manque et rien ne se réparera depuis l'app : l'action du bandeau
         * ouvre les réglages du système pour Onyx, jamais un bouton qui réessaie dans le vide.
         */
        fun openVoiceAppSettings() {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}"),
            )
            runCatching { context.startActivity(intent) }
        }

        /** « Réessayer » : une écoute neuve si le micro est permis, la demande sinon. */
        fun retryVoiceListening() {
            voiceProblem = null
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                com.onyx.app.voice.VoiceDiagnostics.note("voice: réécoute demandée d'un appui")
                voice.startListening()
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Le tour est fini : l'agent lit sa réponse, puis se remet à écouter en mains-libres.
        LaunchedEffect(state.isStreaming, route) {
            if (route != Route.GROUP_VOICE.name && !state.isStreaming && voice.phase.value == VoicePhase.THINKING) {
                val reply = state.messages.lastOrNull { it.role == Role.ASSISTANT }?.text
                // La réponse se lit **de la voix du bot de la conversation ouverte** : c'est le
                // `readAloud(_, speaker: conversation.bot?.profile)` du modèle. Sans ce profil, le
                // réglage « Voix » du bot était ignoré en mode vocal.
                voice.finishTurn(reply, speaker = openConversationBot?.name)
            }
        }

        // Le suivi du tour dans la barre d'état ne vit plus ici : il est tenu par la portée du
        // processus (`AppContainer.work`), branchée sur le magasin de tours. L'écran ne lui dit
        // que ce qu'il a sous les yeux, un peu plus bas — sinon un tour fini sans écran (activité
        // détruite, autre app au premier plan) n'aurait plus personne pour l'annoncer.

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
        // « Appels pour une réponse » : un tour en attente de décision fait sonner le téléphone.
        val phoneRings by settingsRepo.phoneRings.collectAsStateWithLifecycle()
        val phoneRingsTalk by settingsRepo.phoneRingsTalk.collectAsStateWithLifecycle()
        // `lifecycle.currentState` ne doit pas être lu pendant la composition (lint
        // LifecycleCurrentStateInComposition : la valeur ne serait jamais recomposée). On suit
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

        // Ce que l'utilisateur a sous les yeux : le suivi du travail ne le lui notifie pas. Le
        // suivi lui-même vit dans la portée du processus — ici on ne fait que le renseigner.
        LaunchedEffect(state.conversationId, route, inForeground) {
            if (AppContainer.isInstalled) {
                AppContainer.work.watching(
                    conversationId = state.conversationId.takeIf { route == Route.CHAT.name },
                    foreground = inForeground,
                )
            }
        }
        DisposableEffect(Unit) {
            // L'activité s'en va : plus rien n'est sous les yeux, le suivi doit pouvoir notifier.
            onDispose { if (AppContainer.isInstalled) AppContainer.work.watching(null, false) }
        }

        LaunchedEffect(pendingApprovals.size, inForeground, notifyInBackground, notificationSound, phoneRings, phoneRingsTalk) {
            if (inForeground) {
                pendingApprovals.forEach {
                    ApprovalNotifier.clear(context, it.id)
                    // L'app reparaît : un appel qui sonnait pour cette approbation se retire sans repli.
                    CallRinger.answered(context, it.id)
                }
            } else {
                pendingApprovals.forEach {
                    val missed = {
                        if (notifyInBackground) {
                            ApprovalNotifier.notifyApproval(
                                context = context,
                                id = it.id,
                                command = it.command,
                                title = threadName,
                                runId = it.runId,
                                serverId = state.server.id,
                                enabled = true,
                                sound = notificationSound,
                            )
                        }
                    }
                    // Le tour attend une décision : le téléphone sonne (Réglages → Phone → Calls),
                    // sauf la nuit. Refusé ou sans réponse, la notification habituelle prend sa place.
                    val rings = phoneRings && !settingsRepo.quietNow() &&
                        CallRinger.ring(
                            context,
                            CallRinger.Ringing(
                                id = it.id,
                                title = threadName,
                                why = OnyxLocalization["Needs your approval"],
                                talk = phoneRingsTalk,
                                conversationId = state.conversationId,
                                onMiss = missed,
                            ),
                        )
                    if (!rings) missed()
                }
            }
        }

        // Les questions de l'agent — question, sudo, secret — se répondent de la même façon, avec
        // une action « Reply » quand la saisie en ligne suffit.
        val pendingQuestions = remember(state.messages) { pendingQuestionsOf(state.messages) }
        LaunchedEffect(pendingQuestions.map { it.notificationId }, inForeground, notifyInBackground, notificationSound, phoneRings, phoneRingsTalk) {
            if (inForeground) {
                pendingQuestions.forEach {
                    QuestionNotifier.clear(context, it.notificationId)
                    CallRinger.answered(context, it.notificationId)
                }
            } else {
                pendingQuestions.forEach { question ->
                    val missed = {
                        if (notifyInBackground) {
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
                                enabled = true,
                                sound = notificationSound,
                            )
                        }
                    }
                    val rings = phoneRings && !settingsRepo.quietNow() &&
                        CallRinger.ring(
                            context,
                            CallRinger.Ringing(
                                id = question.notificationId,
                                title = threadName,
                                why = OnyxLocalization["Has a question for you"],
                                talk = phoneRingsTalk,
                                conversationId = state.conversationId,
                                onMiss = missed,
                            ),
                        )
                    if (!rings) missed()
                }
            }
        }

        // Ce qui vient de l'extérieur : un partage, une question de l'assistant, un raccourci.
        val pendingIntake by ShareInbox.pending.collectAsStateWithLifecycle()
        val pendingShare by ShareInbox.pendingFile.collectAsStateWithLifecycle()
        val voiceRequested by ShareInbox.voiceRequested.collectAsStateWithLifecycle()
        // « Capture rapide » demandée depuis l'extérieur (raccourci de l'illustration) : l'app
        // s'ouvre sur la note, **sans toucher à la conversation ouverte**.
        val captureRequested by ShareInbox.captureRequested.collectAsStateWithLifecycle()

        // « L'app reparaît » : les notes en attente retentent leur remise, comme le
        // `CaptureInbox.file()` appelé quand l'app revient au premier plan dans la référence.
        // Sans ça, une note restée en attente n'aurait reparti qu'au prochain appui.
        LaunchedEffect(Unit) { com.onyx.app.data.capture.QuickCapture.fileNow() }

        LaunchedEffect(pendingIntake, pendingShare, voiceRequested, captureRequested) {
            val question = pendingIntake
            val shared = pendingShare
            when {
                // Un fichier partagé se joint au message : le texte du partage, s'il y en a un,
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
                    applyPendingLanguage()
                    if (finish is SetupCodeFinish.Saved && setupOfferFirstRun) {
                        forceFirstRunSetup = !finish.connected
                        if (finish.connected) navTo(Route.CHAT.name)
                    }
                },
                onApplyExtras = { code -> applySetupExtras(code) },
            )
            return@OnyxAppTheme
        }

        // Maquette de boutique. la référence a `-echo.voiceDemo` pour la même raison : le simulateur n'a
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
        // L'alerte « on n'entend rien » posée pour une capture : elle passe par le **même** chemin
        // que la vraie (le bandeau et son action), pour que la capture montre le rendu réel.
        val posedNoSound = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                context.findActivity()?.intent?.getBooleanExtra(EXTRA_VOICE_NO_SOUND, false) == true
        }

        // Ce que l'écran vocal dit quand l'écoute n'aboutit pas, et **l'action** qui va avec :
        //  - la permission du micro refusée → ouvrir les réglages du système (rien ne se répare
        //    depuis l'app) ;
        //  - « on n'entend rien » après cinq secondes, ou l'échec définitif du moteur → « Réessayer ».
        // Un bandeau, jamais un code technique seul ; et jamais un écran qui prétend écouter sans
        // rien dire.
        val voiceNoSound = noSound || posedNoSound
        val listenProblem = posedProblem
            ?: voiceProblem
            // La panne d'écoute vient **avant** le niveau du micro : elle sait dire « le moteur
            // n'a jamais démarré » ou « le moteur reçoit des trames et ne rend rien », que le seul
            // niveau ne peut pas distinguer — et c'est ce qui laissait l'écran muet.
            ?: voiceTrouble.message
            ?: if (voiceNoSound) {
                "I can't hear anything — check your microphone."
            } else {
                engineFailure ?: speech.problem
            }
        val listenAlert = listenProblem?.let {
            val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (micGranted) {
                VoiceAlertAction(label = "Try again", onClick = { retryVoiceListening() })
            } else {
                VoiceAlertAction(label = "Open Android settings", onClick = { openVoiceAppSettings() })
            }
        }
        val voiceDemo = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                (voicePose != null ||
                    context.findActivity()?.intent?.getBooleanExtra(EXTRA_VOICE_DEMO, false) == true)
        }
        // La maquette vocale : l'émulateur n'a **pas de micro**, donc le niveau du moteur reste à
        // zéro et la vague ne bougerait pas d'un pixel — c'est justement ce que la capture d'un
        // propriétaire montrait, sept points identiques. La pose injecte donc une **parole
        // simulée** (des syllabes, des silences) pour que la vague soit vérifiable. Déclaré comme
        // état **injecté** dans le rapport ; sans le drapeau, il n'est jamais lu et rien ne change.
        val demoVoiceLevel = rememberVoiceDemoLevel(voiceDemo)
        // La même chose pour la scène vocale d'une salle (`-e onyx.groupVoiceDemo speaking`) : une
        // salle scriptée, sans serveur ni micro, pour montrer la scène de parole et l'éprouver.
        val groupVoiceDemoOn = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                context.findActivity()?.intent?.getStringExtra(EXTRA_GROUP_VOICE_DEMO) != null
        }
        // La maquette branchee sur une **vraie salle** : elle ne prend pas la navigation, c'est la
        // personne qui ouvre sa salle et touche « Parler » (`GroupVoice.ask` -> `groups.send`).
        val scenarioRoomWanted = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                context.findActivity()?.intent?.getBooleanExtra(EXTRA_VOICE_SCENARIO_ROOM, false) == true
        }
        LaunchedEffect(voiceDemo, groupVoiceDemoOn, voiceScenario) {
            if (groupVoiceDemoOn) {
                voiceProblem = null
                navTo(Route.GROUP_VOICE.name)
            } else if (voiceScenario != null && !scenarioRoomWanted) {
                // La maquette de la chaîne vocale ouvre l'écran vocal et se met **en mains
                // libres** : c'est ce qui fait entrer la boucle (écoute → question → réponse →
                // reprise) et ce qui affiche la phrase d'aide.
                voiceProblem = posedProblem
                if (!voice.handsFree.value) voice.toggleHandsFree()
                navTo(Route.VOICE.name)
            } else if (voiceDemo) {
                // La pose d'une capture ne pose que l'écran : le drapeau du problème, s'il est là,
                // doit survivre (c'est justement ce que la capture montre).
                voiceProblem = posedProblem
                navTo(Route.VOICE.name)
            }
        }

        // Aucun serveur utilisable : l'écran de connexion, comme au premier lancement d'la référence — mais
        // seulement tant qu'il n'a pas été traversé. Sans ce drapeau (`Settings.setupDone`), un
        // appareil sans adresse restait bloqué dessus : « Ignorer » et « Terminé » enregistraient
        // un brouillon vide, l'écran se réaffichait, et les deux boutons paraissaient morts.
        // La maquette de boutique passe outre : elle ne parle à aucun serveur, elle pose l'écran
        // vocal.
        val setupDone by settingsViewModel.setupDone.collectAsStateWithLifecycle()
        val firstRunSetup = (!state.server.isUsable && !setupDone) || forceFirstRunSetup
        if (firstRunSetup && !voiceDemo && !groupVoiceDemoOn && voiceScenario == null) {
            SetupScreen(
                config = setupDraft,
                onChange = { transform -> setupDraft = transform(setupDraft) },
                onDone = {
                    settingsViewModel.open(setupDraft)
                    settingsViewModel.saveAndClose()
                    settingsViewModel.setSetupDone(true)
                    forceFirstRunSetup = false
                },
                // « Ignorer » sort **toujours**, tout de suite, sans test : c'est la porte de
                // sortie. Il ne sauvegarde un brouillon que s'il porte une adresse (un brouillon
                // vide écraserait la configuration existante) et, quand un serveur déjà enregistré
                // est utilisable, il y revient — l'app reste utilisable sans être configurée.
                onSkip = {
                    if (skipSavesDraft(setupDraft)) {
                        settingsViewModel.open(setupDraft)
                        settingsViewModel.saveAndClose()
                    }
                    serverAfterSkip(state.server, serverList)?.let { server ->
                        settingsViewModel.setActive(server.id)
                    }
                    settingsViewModel.setSetupDone(true)
                    forceFirstRunSetup = false
                    applyPendingLanguage()
                },
                scope = scope,
                onScanCode = { setupScannerOpen = true },
                onPasteCode = { text ->
                    SetupCodeOffer.fromText(text)?.let { offer ->
                        presentSetupOffer(offer)
                        true
                    } == true
                },
                onDiscover = { address, key ->
                    val found = com.onyx.app.data.net.OnyxDiscovery.discover(address, key)
                    // « Une clé » : quand le serveur annonce lui-même les bots, son Dashboard vit à
                    // la **même adresse** que le chat — et sous la même clé. Le mode « Simple » n'a
                    // donc rien de plus à demander : ni second serveur, ni identifiant, ni mot de
                    // passe. Rien à faire pour un serveur qui n'annonce pas le Dashboard.
                    if (found.declaresDashboard) {
                        setupDraft = setupDraft.copy(serveBaseUrl = address.trim())
                    }
                    found
                },
                onApplyDiscovery = { found -> scope.launch { applyDiscovery(found) } },
                // **Ce que le mode « Simple » propose** : les adresses déjà connues, dans l'ordre de
                // pertinence. Le serveur en cours, le réseau du téléphone, la passerelle précédente,
                // les autres serveurs enregistrés.
                suggestions = com.onyx.app.ui.setup.SetupSuggestions.known(
                    current = com.onyx.app.ui.setup.setupAddressOf(state.server),
                    local = setupLocalAddresses,
                    lastGateway = serverList
                        .firstOrNull { it.id != state.server.id }
                        ?.let(::setupAddressOf)
                        .orEmpty(),
                    saved = serverList
                        .filter { it.id != state.server.id }
                        .drop(1)
                        .map(::setupAddressOf),
                ),
                // Le lien de configuration du brouillon, **sans ses secrets** : le « Copier » de la
                // barre de titre. Rien à copier tant qu'aucune adresse n'est saisie.
                setupLink = if (setupDraft.isUsable) {
                    setupLinkFor(setupDraft)
                } else {
                    ""
                },
            )
            return@OnyxAppTheme
        }

        // Un changement de serveur (URL comprise) relance la lecture du roster et des
        // conversations : sinon l'ancienne erreur — « Unable to resolve host … » — restait
        // affichée alors que la nouvelle adresse répondait déjà, et « Réessayer » la rejouait.
        LaunchedEffect(state.server.id, state.server.configSignature) {
            botsViewModel.rebind(state.server)
            botsViewModel.refresh()
            conversationRepo.load()
        }

        // La mémoire du fil ouvert, serveur par serveur : `ServerSwitcher` d'la référence enregistre la
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

        // Les marques de lecture des conversations : ce que ce téléphone a vu de chacune, comme
        // `ChatActivity.seen` d'la référence — il n'y a pas de registre serveur de lecture ici. « Mark as
        // Unread » écrit 0, « Mark as Read » l'heure courante, et la rangée les lit.
        val conversationMarks = remember { BotActivityStore(context) }
        var unreadMarks by remember { mutableStateOf(emptyMap<String, Double?>()) }
        LaunchedEffect(history, state.server.configSignature) {
            unreadMarks = history.associate { conversation ->
                conversation.id to conversationMarks.seen(state.server.configSignature, "conversation:" + conversation.id)
            }
        }

        // Le tap d'une tuile de bot : son **chat** (le `openBot` d'la référence), jamais son éditeur. Sa
        // conversation est celle que son `canonical_session` désigne (`BotProfile.chatId`), gardée
        // pour le retrouver ; un bot sans chat nommé en ouvre une neuve, que le premier tour lie au
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
            // Le chat du bot est ouvert : ce qu'il a dit est vu, comme `activity.botSeen` d'la référence.
            botsViewModel.markBotSeen(bot)
            navTo(Route.CHAT.name)
        }

        // « Mark as Read » / « Mark as Unread » d'une conversation, comme le `setUnread` d'la référence :
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

        // « Pictures from Hermes » : les photos du roster, une fois par lancement et par profil.
        LaunchedEffect(botPictures, botsState.bots) {
            if (botPictures) botsViewModel.loadPictures()
        }
        LaunchedEffect(botsViewModel) {
            botsViewModel.pictures.collect { botPicturesByName = it }
        }
        // L'état local de l'app est indexé par **nom de profil** : le visage et la visibilité d'un
        // bot (`onyx_bot_faces`), son activité vue et sa place dans l'ordre (`onyx_bot_activity`),
        // et le rattachement de ses conversations par leur titre (`conversations.json`). Après un
        // renommage côté serveur (par ex. `ancien-nom` → `nom-courant`), ces entrées sont sous
        // l'ancien nom : la migration les reporte sur le nom courant, **une seule fois** — le
        // journal des renommages la rend idempotente (la relancer ne rejoue ni n'écrase rien).
        val profileMigration = remember(context) { profileStateMigration(context) }
        LaunchedEffect(botsState.bots, botsState.hidden, history, botsState.problem) {
            val roster = (botsState.bots + botsState.hidden).distinctBy { it.name }
            if (roster.isEmpty()) return@LaunchedEffect
            val outcome = profileMigration.migrate(roster, state.server.configSignature, history)
            outcome.conversations.forEach { updated ->
                val before = history.firstOrNull { it.id == updated.id }
                if (before != null && before.title != updated.title) conversationRepo.save(updated)
            }
            // Le roster a été lu **sous l'ancien nom** : s'il y a eu report, on le relit pour que
            // le visage migré se montre tout de suite (et pas seulement au prochain rafraîchissement).
            if (outcome.applied.isNotEmpty()) botsViewModel.refresh()
        }
        // Les projets du serveur (dossiers de travail), lus par le RPC du Dashboard — la source
        // de la section « Projects » et de `ProjectSessionsView`.
        var serverProjects by remember(state.server.id, state.server.configSignature) {
            mutableStateOf<List<com.onyx.app.data.net.ServerProject>>(emptyList())
        }
        // Créer, renommer et retirer un projet : les feuilles vivent **ici**, une seule fois, pour
        // la liste comme pour l'écran d'un projet. `projectRevision` force la relecture de l'arbre.
        var projectCreating by remember { mutableStateOf(false) }
        var projectRenaming by remember {
            mutableStateOf<com.onyx.app.data.net.ServerProject?>(null)
        }
        var projectRemoving by remember {
            mutableStateOf<com.onyx.app.data.net.ServerProject?>(null)
        }
        var projectProblem by remember { mutableStateOf<String?>(null) }
        var projectRevision by remember { mutableStateOf(0) }

        /** Un geste sur un projet : l'appel, puis la relecture de l'arbre ; l'échec se dit. */
        fun runProjectAction(work: suspend () -> Unit) {
            scope.launch {
                try {
                    work()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    projectProblem = com.onyx.app.ui.conversations.ProjectManage.reason(error)
                }
                projectRevision++
            }
        }

        LaunchedEffect(state.server.id, state.server.configSignature, projectRevision) {
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
        // Le profil Hermes qui porte la session : celui du bot de la conversation, sinon celui du
        // serveur. Les sessions s'écrivent par profil ; sans lui, la fiche d'usage interroge la
        // base `default` et n'y trouve pas la ligne d'un bot (le défaut signalé).
        var usageProfile by remember { mutableStateOf<String?>(null) }
        // Les bots choisis dans « New Group Chat », posés sur la salle à son ouverture.
        var groupPicked by remember { mutableStateOf<Set<String>?>(null) }
        /** Le nom donné à la salle dans la feuille (`NewGroupChatView`), ou celui d'une salle gardée ici. */
        var groupTitle by remember { mutableStateOf<String?>(null) }

        /**
         * Ouvre une salle depuis la liste. Une salle du serveur montre son journal (`GroupRoomScreen`,
         * `groups.log`) ; une salle **gardée ici**, faute de Dashboard, ouvre son fil local avec ses
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

        // Retour système : le geste du bord, le bouton et le retour prédictif déclaré au manifeste
        // sont traités dans la mise en page (voir le `BackHandler` du `BoxWithConstraints`), qui sait
        // si l'écran est en deux panneaux et quelle route est présentée.

        val chatPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { showListButton, sidebarToggle ->
            // La conversation ouverte est-elle celle d'un bot ? (`BotProfile.chatId`, le
            // `canonical_session` du serveur.) Elle le dit deux fois : l'en-tête du fil montre son
            // visage et son nom, et — sous « Bot colors in their chats » — ses bulles et ses
            // boutons prennent la couleur de son visage, comme `ContentView` côté la référence
            // (`BotFace.chatColors`) ; les groupes gardent celle du thème.
            val openedConversation = history.firstOrNull { it.id == state.conversationId }
            val openedConversationBot = openedConversation?.let { conversation ->
                botOfConversation(conversation, bots, state.server.displayName)
            }
            // **Le modèle du bot de la conversation** alimente la source unique du modèle en vigueur
            // (`ChatUiState.modelInForce`) : c'est ce qui fait que la pilule du haut nomme le modèle du
            // bot — celui que la feuille de modèle vient d'écrire dans `FaceKeys.model` — et que
            // l'envoi porte **exactement** ce nom (l'ordre : conversation → bot → serveur → défaut).
            // `null` pour une conversation simple : l'étage « bot » passe alors la main au serveur.
            LaunchedEffect(openedConversationBot?.name) {
                chatViewModel.setBotModel(openedConversationBot?.let(botsViewModel::modelOf))
                // **La liaison bot → profil** : la conversation d'un bot tourne dans **son** profil.
                // `null` (conversation simple) laisse le profil du serveur décider ; `""` (le profil
                // principal, `isDefault`) laisse la passerelle sur son profil par défaut. C'est ce
                // que l'envoi porte au tour (`ChatUiState.botProfile` → `profile` de session).
                chatViewModel.setBotProfile(
                    openedConversationBot?.let { if (it.isDefault) "" else it.name },
                )
            }
            // **Le bot dont on ouvre l'identité.** Le visage du fil (le repère de la barre du
            // bas, celui de l'en-tête) désigne le bot de la conversation ; une conversation
            // simple n'a pas de profil à elle — c'est l'agent principal, alors, dont le profil
            // est celui qui porte `isDefault`. Si aucun des deux n'existe, on ne fournit rien :
            // le repère garde son ancien comportement au lieu d'avoir l'air d'un contrôle mort.
            val openBotIdentity: (() -> Unit)? =
                (openedConversationBot ?: bots.firstOrNull { it.isDefault })?.let { profile ->
                    {
                        selectedBot = profile
                        navTo(Route.BOT_EDITOR.name)
                    }
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
                // Démarrage de secours : le lancement précédent est mort en restaurant cet
                // onglet — on rouvre sur Chat plutôt que de refaire le même dessin.
                startOnChatTab = startPlan.chatTab,
                // « Shown in the header and the message box » : le nom des Réglages → Apparence.
                assistantName = displayName.trim(),
                onInputChange = chatViewModel::onInputChange,
                onSend = chatViewModel::send,
                onSteer = chatViewModel::steer,
                onConsumeSlash = chatViewModel::consumeSlash,
                onStop = chatViewModel::stop,
                onRetry = chatViewModel::retry,
                onClearError = chatViewModel::clearError,
                onNewConversation = { startFreshConversation() },
                onOpenConversations = if (showListButton) {
                    // La liste des conversations n'est plus un **écran** : c'est le volet. Le geste
                    // du bord, comme le contrôle de la barre, l'ouvre donc au lieu d'ouvrir la
                    // route qui le portait — celle-ci a été retirée.
                    { sidebarState.open() }
                } else {
                    // Replié, c'est le contrôle de la barre (`sidebar.left`) qui rouvre le volet :
                    // la liste n'est pas une destination, c'est un panneau.
                    null
                },
                // **La pilule du haut ouvre la feuille du sélecteur.**
                // La seconde ligne du titre — le nom du modèle, avec son chevron — ouvrait l'**écran**
                // des modèles (`Route.MODELS`) : un geste, deux dessins, et le propriétaire tombait
                // sur l'autre. C'est le même sélecteur que la puce de la carte (`onOpenModelSheet`) :
                // un seul geste → une seule feuille.
                // **La pilule du haut ouvre la feuille du sélecteur de modèle.**
                // La seconde ligne du titre — le nom du modèle, avec son chevron — ouvrait l'**écran**
                // des modèles (`Route.MODELS`) : un geste, deux dessins, et le propriétaire tombait sur
                // l'autre. La feuille de modèle n'avait plus **aucune** porte sur le téléphone (la puce
                // du compositeur n'est dessinée que sur une carte non-plaque) : elle est donc
                // inatteignable sans ce branchement. L'écran des modèles garde ses deux autres portes
                // (Réglages → Modèles, et la page des capacités).
                onPickModel = { showModelSheet = true },
                onModelCommand = {
                    // « /model » : on baisse la demande puis on ouvre le sélecteur, exactement
                    // comme la pilule du haut.
                    chatViewModel.consumeModelPickerRequest()
                    showModelSheet = true
                },
                onOpenSettings = { navTo(Route.SETTINGS.name) },
                // **Le visage est une porte.** Le quatrième repère de la barre du bas et le
                // visage de l'en-tête ouvrent l'**identité du bot de la conversation** — visage,
                // nom, voix, ce qu'il fait, personnalité, modèle, outils, compétences —, la même
                // page que « Modifier » du menu d'une tuile. C'était le défaut signalé : le lot
                // de la plaque avait changé le **dessin** du quatrième repère (le visage au lieu
                // de l'ampoule) sans changer son **routage**, qui ouvrait encore la page
                // « Idées ».
                onOpenBotIdentity = openBotIdentity,
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
                        // **La voix du bot de la conversation**, comme `TranscriptView.readAloud`
                        // (`voiceSession.readAloud(message.text, speaker: conversation.bot?.profile)`).
                        // Sans ce profil, « Lire à voix haute » lisait une réponse de bot avec la
                        // voix d'Onyx : le réglage « Voix » du bot ne changeait rien.
                        voice.speak(
                            message.text,
                            listenAfterSpeech = false,
                            speaker = openedConversationBot?.name,
                        )
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
                    // « Repartir de zéro » : la même chose que le crayon, mais la question d'abord
                    // — l'ancienne reste dans l'historique, et c'est la confirmation qui le dit.
                    add(MenuItem("Start fresh", Symbols.ArrowCounterclockwise) { confirmingFresh = true })
                    // « Capture rapide » : une note en passant, depuis le fil, sans quitter ce
                    // qu'on regarde (la note part dans sa propre conversation, « Notes »).
                    add(MenuItem("Take a note", Symbols.SquareAndPencil) { navTo(Route.QUICK_CAPTURE.name) })
                    add(MenuItem("Computer", Symbols.DesktopComputer) { navTo(Route.COMPUTER.name) })
                    add(MenuItem("Terminal", Symbols.Terminal) { navTo(Route.TERMINAL.name) })
                    // « Relevé de l'agent » : c'est **le** menu du fil, donc c'est ici que l'écran
                    // qui répond à « qu'est-ce que l'agent a fait, et combien ça a coûté ? » a sa
                    // place. Il ne remplace aucune entrée existante.
                    // **Son pictogramme, pas celui du voisin.** Il portait `ChartBar`, la même
                    // icône que « Consommation et coût » — deux destinations, un seul glyphe dans
                    // la même feuille. Un relevé est une **pièce écrite** (un rapport consigné), pas
                    // une courbe : `DocOnClipboard`, de la famille de la maison.
                    add(MenuItem("Agent ledger", Symbols.DocOnClipboard) { navTo(Route.LEDGER.name) })
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
                onRestore = chatViewModel::requestRestore.let { request -> { message -> request(message.id) } },
                onConfirmRestore = chatViewModel::confirmRestore,
                onCancelRestore = chatViewModel::cancelRestore,
                // LOT branche : « Brancher à partir d'ici » suit **exactement** le chemin de la
                // restauration (un message visé, une confirmation, un effet) — trois lignes ici,
                // la mécanique vivant dans le ViewModel et le dépôt.
                onBranch = chatViewModel::requestBranch.let { request -> { message -> request(message.id) } },
                onConfirmBranch = chatViewModel::confirmBranch,
                onCancelBranch = chatViewModel::cancelBranch,
                onConsumeRestoreNotice = chatViewModel::consumeRestoreNotice,
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
                // LOT organisation-fil-competences : l'arrêt d'un sous-agent depuis sa carte du
                // fil — le **vrai** `subagent.interrupt` du Dashboard (le même appel que l'écran
                // des tâches), jamais un bouton dessiné pour rien.
                onStopSubagent = { agent ->
                    if (subagentDemoRun) {
                        // Doublure de QA : l'émulateur n'a ni Dashboard ni agent, donc aucun
                        // `subagent.interrupt` ne peut aboutir. L'arrêt se résout **en local**,
                        // exactement comme l'écran des tâches le fait déjà pour ce drapeau — et le
                        // rapport dit que c'est une simulation. Sans le drapeau, c'est la vraie
                        // route qui est appelée.
                        demoSubagents = demoSubagents.map { row ->
                            if (row.id == agent.id) {
                                row.copy(
                                    status = com.onyx.app.data.model.SubagentStatus.COMPLETED,
                                    summary = "Stopped from Onyx before it finished.",
                                )
                            } else {
                                row
                            }
                        }
                    } else {
                        scope.launch { chatViewModel.stopSubagent(agent.id) }
                    }
                },
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
                onOpenLocalFile = { uri, name, mime -> openConversationFile(uri, name, mime) },
                onOpenDelivered = { file ->
                    // Une pièce jointe de CET appareil (galerie, partage, sélecteur) n'est pas un
                    // fichier du serveur : la demander au Dashboard faisait répondre
                    // `400 Path must be absolute` et affichait un code nu. Elle s'ouvre sur place.
                    // Sinon un fichier livré s'ouvre selon sa **famille** : les lecteurs de la
                    // maison (image, vidéo, son, PDF, texte, archive) quand il y en a un,
                    // l'application du système sinon. Il passe d'abord par le cache du téléphone.
                    val external = com.onyx.app.data.model.fileOpenRoute(
                        file.name,
                        com.onyx.app.ui.chat.FileViewers.mimeFor(file.name),
                    ) == com.onyx.app.data.model.FileOpenRoute.EXTERNAL
                    if (com.onyx.app.ui.chat.DeliveredFiles.isLocalPath(file.path)) {
                        openFile(
                            uri = android.net.Uri.parse(file.path),
                            name = file.name,
                            mime = com.onyx.app.ui.chat.FileViewers.mimeFor(file.name),
                        )
                    } else if (external) {
                        scope.launch {
                            com.onyx.app.ui.chat.DeliveredFiles.open(context, state.server, file)
                                .onFailure { error ->
                                    chatViewModel.showProblem(
                                        com.onyx.app.ui.chat.DeliveredFiles.failureText(error, file),
                                    )
                                }
                        }
                    } else {
                        scope.launch {
                            com.onyx.app.ui.chat.DeliveredFiles.local(context, state.server, file)
                                .onSuccess { local ->
                                    openConversationFile(
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
                // L'en-tête du fil : le visage et le nom du bot dont c'est la conversation, sinon
                // ceux de Onyx (ajout volontaire côté Android).
                threadBot = openedConversationBot,
                // La liste des mentions `@` du composeur : les bots du roster, comme la section
                // « Bots » les montre.
                mentionBots = mentionRoster,
                sidebarToggle = sidebarToggle,
                // La **puce du modèle** de la carte ouvre la feuille du sélecteur de modèle
                // (fonction 1 → 2). Le second segment de la puce est le niveau de réflexion.
                onOpenModelSheet = { showModelSheet = true },
                // Le second segment de la puce : le niveau de réflexion. Un repli **local** ne
                // propose pas les crans du modèle configuré — on n'écrit donc pas un cran qui ne
                // pilote rien (le nom du modèle en force, lui, reste nommé par la puce).
                reasoningLabel = com.onyx.app.ui.design.ModelFallback.reflectionLabel(
                    com.onyx.app.ui.design.ReasoningLevels
                        .label(currentReasoning)
                        .takeIf { currentReasoning.isNotBlank() }
                        .orEmpty(),
                    state.fallback,
                ),
            )
            }
            // La **feuille du sélecteur de modèle** : le modèle choisi devient **celui de ce bot,
            // pour toutes ses conversations et ses routines**. Elle est posée par-dessus la
            // conversation ; le choix se mémorise (par bot sur le serveur, ici pour la conversation
            // simple), et il survit à une relance.
            if (showModelSheet) {
                com.onyx.app.ui.models.ModelSheet(
                    models = modelChoices,
                    // Le modèle **en force** porte la coche — sous repli, c'est l'entrée réellement
                    // servie (locale) qui est cochée, jamais le modèle configuré.
                    currentModel = state.modelInForce,
                    reasoning = currentReasoning,
                    favourites = modelFavourites,
                    recents = modelRecents,
                    loading = modelChoicesLoading,
                    profiles = modelProfiles,
                    fallback = state.fallback,
                    onPickModel = { model ->
                        val bot = openedConversationBot ?: selectedBot
                        if (bot != null) {
                            // Le modèle d'un bot : écrit sur le serveur (`profiles.configure`) et
                            // gardé localement — c'est ce qui le fait survivre à un arrêt forcé.
                            botsViewModel.setModel(bot, model)
                        } else {
                            // Une conversation simple : le modèle du serveur actif, comme l'écran
                            // des modèles le fait.
                            settingsViewModel.open(state.server)
                            settingsViewModel.update { it.copy(model = model) }
                            settingsViewModel.saveAndClose()
                        }
                        modelPrefs.noteRecent(state.server.id, model)
                        modelRecents = modelPrefs.recents(state.server.id)
                        showModelSheet = false
                    },
                    onPickReasoning = { value -> scope.launch { settingsRepo.setReasoningEffort(value) } },
                    onToggleFavourite = { id, favourite ->
                        modelPrefs.setFavourite(state.server.id, id, favourite)
                        modelFavourites = modelPrefs.favourites(state.server.id)
                    },
                    // La **portée réelle** du choix, dite à droite du titre : le modèle d'un bot
                    // vaut pour **toutes** ses conversations, celui d'une conversation simple est
                    // celui du serveur. La référence écrit « Pour cette conversation » parce que
                    // chez elle le choix est bien par conversation — ici, l'écrire serait faux.
                    scope = if (openedConversationBot != null) {
                        com.onyx.app.ui.models.ModelScope.BOT
                    } else {
                        com.onyx.app.ui.models.ModelScope.SERVER
                    },
                    // La **ligne « Mode rapide »** : `null` quand le serveur ne l'expose pas — la
                    // feuille ne peint alors pas la ligne (voir `rememberModelFastMode`).
                    fastMode = modelFastMode,
                    onDismiss = { showModelSheet = false },
                )
            }
        }

        // ————— Hunks : le menu d'une carte de bot (lot « menu d'un bot ») —————
        // La table des gestes du menu d'une carte de bot : **les neuf entrées de la référence**
        // (épingler, masquer, ouvrir la dernière session, une conversation neuve avec le bot, ses
        // routines, repartir à zéro, modifier, dupliquer, supprimer) puis celles de la maison.
        // Une seule table pour les deux endroits qui montrent ce menu — le volet et la page
        // « Bots » —, si bien qu'aucune des deux ne peut en oublier une.
        val openBotConversation: (BotProfile, Conversation) -> Unit = { bot, conversation ->
            openingRect = null
            chatViewModel.open(conversation)
            botsViewModel.markBotSeen(bot)
            sidebarState.clearDestinations()
            sidebarState.close()
            navTo(Route.CHAT.name)
        }
        val botMenuTable = BotMenuHandlers(
            onSetPinned = { bot, pinned -> botsViewModel.setPinned(bot, pinned) },
            onHide = { bot -> botsViewModel.hideBot(bot) },
            onOpenRecent = { bot ->
                botRecentConversation(bot, history, botsState.bots, state.server.displayName)
                    ?.let { recent -> openBotConversation(bot, recent) }
            },
            onNewChat = { bot ->
                // « Nouvelle conversation avec <le nom> » : une conversation **neuve** du bot (le
                // crayon du fil fait le même choix), gardée tout de suite pour qu'elle soit dans la
                // liste sous son nom.
                val next = freshConversationForPencil(
                    current = null,
                    bot = bot,
                    conversations = history,
                    serverId = state.server.id,
                    mainName = state.server.displayName,
                )
                if (history.none { it.id == next.id }) scope.launch { conversationRepo.save(next) }
                openBotConversation(bot, next)
            },
            onRoutines = { bot ->
                selectedBot = bot
                botCronProfile = bot.name
                navTo(Route.BOT_SCHEDULES.name)
            },
            onStartFresh = { bot ->
                scope.launch {
                    // Le chat du bot est **archivé** (il reste dans l'historique, entier) : une
                    // conversation neuve s'ouvre, sans l'historique.
                    if (botsViewModel.archiveChat(bot) == null) {
                        val fresh = freshConversationForPencil(
                            current = null,
                            bot = bot,
                            conversations = history,
                            serverId = state.server.id,
                            mainName = state.server.displayName,
                        )
                        if (history.none { it.id == fresh.id }) conversationRepo.save(fresh)
                        openBotConversation(bot, fresh)
                    }
                }
            },
            onEdit = { bot ->
                selectedBot = bot
                navTo(Route.BOT_EDITOR.name)
            },
            onDuplicate = { bot -> scope.launch { botsViewModel.duplicate(bot) } },
            onDelete = { bot -> scope.launch { botsViewModel.deleteBot(bot) } },
            onMarkSeen = { bot -> botsViewModel.markBotSeen(bot) },
            onMarkUnread = { bot -> botsViewModel.markBotUnread(bot) },
            onVoice = { bot ->
                selectedBot = bot
                botVoiceProfile = bot.name
                navTo(Route.BOT_VOICE.name)
            },
            onChangeFace = { bot ->
                selectedBot = bot
                navTo(Route.BOT_FACE.name)
            },
            onRemoveFromOnyx = { bot -> botsViewModel.removeAPIBot(bot) },
            hasRecent = { bot -> botRecentConversation(bot, history, botsState.bots, state.server.displayName) != null },
        )

        // « Let bots message each other » installe le réglage serveur : la question d'abord, puis
        // l'écriture, et l'échec se dit au lieu de rester muet.
        //
        // La route Bots se présente, en largeur étendue, dans le panneau de détail : la barre
        // latérale reste à sa gauche, comme le détail d'un `NavigationSplitView` sur la tablette — la
        // flèche de retour n'y a alors pas d'objet, les sessions se choisissant dans la colonne.
        val botsPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { wideDetail, sidebarControl ->
            BotsScreen(
                viewModel = botsViewModel,
                onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.SETTINGS.name) }),
                // Le tap d'une tuile ouvre son chat (`openBot` d'la référence) ; l'éditeur est une entrée du
                // menu, atteinte à l'appui long.
                onOpenBotChat = openBotChat,
                onEditBot = { bot ->
                    selectedBot = bot
                    navTo(Route.BOT_EDITOR.name)
                },
                // « Change Face… » du menu d'une tuile : le sélecteur de visage du bot.
                onChangeFace = { bot ->
                    selectedBot = bot
                    navTo(Route.BOT_FACE.name)
                },
                // « Schedules… » du menu d'une tuile : les horaires du bot, comme `scheduling = bot`.
                onSchedules = { bot ->
                    selectedBot = bot
                    botCronProfile = bot.name
                    navTo(Route.BOT_SCHEDULES.name)
                },
                // « Voice… » du menu d'une tuile : la voix du bot, qui se règle pour lui seul.
                onVoice = { bot ->
                    selectedBot = bot
                    botVoiceProfile = bot.name
                    navTo(Route.BOT_VOICE.name)
                },
                // Le menu de la carte : les neuf entrées de la référence puis celles de la maison.
                botMenu = botMenuTable,
                onGroupChat = {
                    // Ouverte depuis la liste : la salle reprend ses membres, sans nom imposé.
                    groupPicked = null
                    groupTitle = null
                    navTo(Route.GROUP_CHAT.name)
                },
                // La grille des bots lit l'activité des conversations déjà chargées, comme la
                // section « Bots » de la liste : même source, donc mêmes pastilles.
                conversations = history,
                groups = botsState.groups,
                hasDashboard = state.server.canAdminister,
                openedChatId = history.firstOrNull { it.id == state.conversationId }?.serverSessionId,
                mainName = state.server.displayName,
                onOpenGroup = { room -> openGroup(room) },
                onRoomSeen = { room -> botsViewModel.markRoomSeen(room) },
                onNewBot = {
                    // « New Bot » fait un profil sur son serveur (`BotEditor(.new)`) ; sans
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

        /**
         * Le retour d'un écran **porté par le volet** : il dépile le volet, un niveau à la fois.
         *
         * Le même écran (`Cron`, `Kanban`, `Code`) sert les deux présentations : dans le volet, sa
         * flèche de retour ramène à la liste ; dans l'app, elle remonte la pile de routes comme
         * avant. C'est `sidebarState.isNavigating` qui tranche — jamais un second écran dupliqué.
         */
        val paneBack: () -> Unit = { sidebarState.popDestination() }

        val cronPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { wideDetail, sidebarControl ->
            val cronViewModel: CronViewModel = viewModel(
                key = "cron-" + state.server.id + state.server.configSignature,
                factory = viewModelFactory { initializer { CronViewModel(state.server) } },
            )
            CronScreen(
                viewModel = cronViewModel,
                // Une pile vide (l'onglet ouvert depuis la liste, ou depuis les Réglages) retombe
                // sur la **liste des conversations** — jamais sur les Réglages, d'où l'on vient
                // pourtant d'entrer (`onOpenCron` des Réglages). Porté par le volet, ce même
                // retour **dépile le volet** : la flèche revient à la liste. Hors du volet, le
                // repli est le fil — la liste des conversations n'est plus une route.
                onBack = if (wideDetail) null else {
                    val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })
                    // La flèche **et** le geste dépilent la même pile : celle du volet seulement
                    // quand c'est **cet** écran qu'il porte. `isNavigating` disait « un écran
                    // quelconque est ouvert là-dedans » — même fermé, même invisible : la flèche
                    // dépilait alors un volet qu'on ne voyait pas, et le premier appui n'avait
                    // aucun effet visible (mesuré).
                    if (sidebarState.destination == Route.CRON.name) paneBack else inApp
                },
                sidebarControl = sidebarControl,
            )
        }

        // Le `topRow` d'la référence : en deux panneaux, le tableau porte le sélecteur de section de la
        // liste au-dessus de ses capsules (Chat / Cron / Kanban / Code), comme `KanbanView(topRow:)`.
        // La section active est Kanban : cette rangée ne se dessine que sur la route du tableau.
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
                            ListSection.CODE -> navTo(Route.CODE.name)
                        }
                    },
                )
            }
        }

        // L'écran Code : ses filtres, ses sessions et son compositeur. Le dépôt, la copie de travail
        // et la branche viennent du serveur ; la fiche d'une session est tenue ici et le dit.
        val codePane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { wideDetail, sidebarControl ->
            val codeViewModel: CodeViewModel = viewModel(
                key = "code-" + state.server.id + state.server.configSignature,
                factory = viewModelFactory {
                    initializer {
                        CodeViewModel(
                            config = state.server,
                            storage = PreferencesCodeSessionStorage(context),
                            turns = AppContainer.turns,
                            // Chaque message démarre sa propre session : une **vraie** session
                            // Hermes est ouverte, et le statut suit le tour réel qu'elle lance.
                            starter = { text, _ ->
                                chatViewModel.newConversation()
                                chatViewModel.sendText(text, clearInput = false)
                                chatViewModel.state.value.conversationId
                            },
                        ).also { it.refresh() }
                    }
                },
            )
            CodeScreen(
                viewModel = codeViewModel,
                // Le retour revient **là d'où l'on vient** : la liste des conversations, pas l'onglet.
                // Porté par le volet, il **dépile le volet** ; hors du volet, le repli est le fil.
                onBack = if (wideDetail) null else {
                    val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })
                    // Même règle que les autres sections : la flèche ne dépile le volet que s'il
                    // porte **cet** écran (voir Planifiés).
                    if (sidebarState.destination == Route.CODE.name) paneBack else inApp
                },
                sidebarControl = sidebarControl,
            )
        }

        val kanbanPane: @Composable (Boolean, (@Composable () -> Unit)?, (@Composable () -> Unit)?) -> Unit =
            { wideDetail, topRow, sidebarControl ->
            val kanbanViewModel: KanbanViewModel = viewModel(
                key = "kanban-" + state.server.id + state.server.configSignature,
                factory = viewModelFactory { initializer { KanbanViewModel(state.server) } },
            )
            KanbanScreen(
                viewModel = kanbanViewModel,
                // Le retour revient **là d'où l'on vient** : le tableau s'ouvre depuis les onglets
                // de la liste, pas depuis Réglages — y renvoyer perdait la page précédente. Porté
                // par le volet, il **dépile le volet** ; hors du volet, le repli est le fil.
                onBack = if (wideDetail) null else {
                    val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })
                    // Même règle que les autres sections : la flèche ne dépile le volet que s'il
                    // porte **cet** écran (voir Planifiés).
                    if (sidebarState.destination == Route.KANBAN.name) paneBack else inApp
                },
                topRow = topRow,
                sidebarControl = sidebarControl,
                onAskOnyx = { question ->
                    // « Demander à Onyx » ouvre une conversation neuve avec la question.
                    chatViewModel.newConversation()
                    chatViewModel.sendText(question, clearInput = false)
                    // Deux panneaux : la conversation est à côté du tableau, on y reste. Compact :
                    // elle s'ouvre par-dessus, comme le panneau du téléphone. Porté par le volet,
                    // la conversation prend la main et le volet repart de sa liste.
                    if (!wideDetail) {
                        sidebarState.clearDestinations()
                        navTo(Route.CHAT.name)
                    }
                },
            )
        }

        /**
         * Les **tuiles** du volet : les écrans de l'app, deux par rangée. Chaque intitulé ouvre un
         * écran qui existe **vraiment** ici — la correspondance avec la disposition de la référence
         * (Planifiés, Compétences, Outils, Réglages) est relevée dans le rapport de lot.
         */
        // **Une destination, un seul point d'entrée.** La liste est celle de
        // [com.onyx.app.ui.sidebar.SidebarDestinations], et elle ne contient **que** les écrans que
        // la barre du bas ne donne pas : l'onglet « Fichiers » montre les pièces du fil, la tuile
        // « Fichiers du serveur » ouvre le navigateur du serveur — deux écrans, deux mots. La tuile
        // « Réglages », elle, a été **retirée** : la roue dentée de la tête du volet ouvre le même
        // écran, dans le même panneau, et deux entrées adjacentes pour un écran ne sont pas deux
        // chemins. Un test mesure les deux recouvrements (`SidebarDestinationsTest`) :
        // réintroduire une tuile qui double un onglet, ou les Réglages, le fait échouer.
        val sidebarTiles = com.onyx.app.ui.sidebar.SidebarDestinations.tiles

        // ---- **La grille de raccourcis, telle que la personne l'a composée** ------------------
        // L'ordre et la sélection viennent du réglage **local** (`SettingsRepository.sidebarTiles`,
        // clé `sidebar_tiles`), et non plus de la liste figée : une tuile retirée **reste** dans le
        // catalogue (la bibliothèque du mode édition la rend en un appui), et une tuile ajoutée
        // ouvre **exactement** l'écran qu'elle ouvrait — `paneScreen` ci-dessous n'a pas bougé.
        val tileIds by settingsRepo.sidebarTiles.collectAsStateWithLifecycle()
        val shownTiles = com.onyx.app.ui.sidebar.SidebarTileLayout.resolve(tileIds)
        val tileLibrary = com.onyx.app.ui.sidebar.SidebarTileLayout.library(tileIds)

        // ---- Le volet, **conteneur de navigation** --------------------------------------------
        // Une tuile ouvre son écran **dans le volet** : flèche de retour, titre, sous-titre et
        // contenu, le pied du volet (« Nouvelle conversation » et le rond d'initiale) intact. Le
        // volet ne se referme plus — c'est l'inverse exact du choix précédent, qui le fermait à
        // l'ouverture d'une tuile et obligeait à le rouvrir pour voir où l'on venait d'arriver.
        //
        // L'écran porté **est le même écran qu'avant** (`CronScreen`, `CapabilitiesScreen`,
        // `InsightsScreen`…), avec ses fonctions : on change seulement **où** il s'affiche.
        val panePush: (String) -> Unit = { id -> sidebarState.pushDestination(id) }
        // Une destination que le volet ne porte pas — un écran de l'app plus profond, comme
        // « Apparence » depuis les Réglages — reprend le chemin d'avant : elle s'ouvre en pleine
        // page, et le volet repart de sa liste pour ne pas rester posé sur un écran périmé.
        // Ce qu'une entrée du volet laisse **derrière elle** : la page quittée — l'écran que le
        // volet portait — entre dans la **pile de routes** avant la destination. C'est la règle
        // « le retour rend la page d'origine » appliquée à la sortie du volet : sans elle, les
        // vingt rangées des Réglages du volet jetaient la page quittée (elle n'entrait dans
        // **aucune** des deux piles) et le retour rendait le fil, jamais les Réglages.
        // Le volet, lui, repart de sa liste et **se referme** : en surimpression il couvre toute la
        // fenêtre, donc rester ouvert posait un écran périmé par-dessus celui qu'on venait
        // d'ouvrir — mesuré sur l'appareil : « je ressors dans le volet ».
        val paneLeave: (String) -> Unit = { id ->
            val quittee = sidebarState.destination?.takeIf { it != id }
            if (quittee != null) navTo(quittee)
            sidebarState.clearDestinations()
            sidebarState.close()
            navTo(id)
        }
        val paneScreen: @Composable (String) -> Unit = { id ->
            when (id) {
                Route.CRON.name -> cronPane(false, null)
                Route.KANBAN.name -> kanbanPane(false, null, null)
                Route.CODE.name -> codePane(false, null)

                Route.MODELS.name -> {
                    val effort by settingsRepo.reasoningEffort.collectAsStateWithLifecycle()
                    ModelsScreen(
                        server = state.server,
                        reasoningEffort = effort,
                        onPickReasoning = { value -> scope.launch { settingsRepo.setReasoningEffort(value) } },
                        onPick = { model ->
                            // Le modèle vit sur le serveur : on l'y réécrit, et le dépôt le
                            // persiste. L'écran a fini son office, le volet revient à sa liste.
                            settingsViewModel.open(state.server)
                            settingsViewModel.update { it.copy(model = model) }
                            settingsViewModel.saveAndClose()
                            paneBack()
                        },
                        onBack = paneBack,
                        serverSessionId = state.serverSessionId,
                        // Le modèle **en force** de la conversation ouverte : la bonne entrée est
                        // cochée (et nommée) même quand un repli la sert hors de la liste exposée.
                        inForceModel = state.modelInForce,
                    )
                }

                // La tuile « Compétences » a été **retirée** : « Capacités » — la tuile *Capabilities*
                // de la référence — porte déjà la famille des compétences (liste groupée, compte,
                // recherche, interrupteur, lecture, catalogue). L'éditeur, seul geste qui n'y vivait
                // pas, y est **porté** (`onEditSkill`) : il reste donc atteignable depuis l'écran.
                Route.SKILL_EDITOR.name -> SkillEditorScreen(
                    server = state.server,
                    existing = skillName,
                    scope = scope,
                    onSaved = paneBack,
                    onBack = paneBack,
                )

                Route.TOOLS.name -> ToolsScreen(server = state.server, scope = scope, onBack = paneBack)

                Route.FILES.name -> {
                    val paneFilesViewModel: FilesViewModel = viewModel(
                        key = "files-" + state.server.id + state.server.configSignature,
                        factory = viewModelFactory { initializer { FilesViewModel(state.server) } },
                    )
                    FilesScreen(
                        viewModel = paneFilesViewModel,
                        onBack = paneBack,
                        onOpenFile = { entry ->
                            editorPath = entry.path
                            panePush(Route.FILE_EDITOR.name)
                        },
                    )
                }

                Route.FILE_EDITOR.name -> {
                    val path = editorPath
                    if (path == null) {
                        // Pas de chemin : il n'y a rien à montrer. On ne navigue **pas** pendant la
                        // composition (la route vide n'entre pas dans la pile) — le volet revient à
                        // sa liste, exactement comme le repli d'état vide des autres routes.
                        LaunchedEffect(Unit) { paneBack() }
                    } else {
                        val paneEditorViewModel: FileEditorViewModel = viewModel(
                            key = "editor-" + path,
                            factory = viewModelFactory { initializer { FileEditorViewModel(state.server, path) } },
                        )
                        FileEditorScreen(
                            viewModel = paneEditorViewModel,
                            onBack = { paneLeave(Route.FILES.name) },
                        )
                    }
                }

                Route.INSIGHTS.name -> {
                    val period by settingsRepo.insightsPeriod.collectAsStateWithLifecycle()
                    InsightsScreen(
                        server = state.server,
                        period = period,
                        onSelectPeriod = { chosen -> scope.launch { settingsRepo.setInsightsPeriod(chosen) } },
                        onBack = paneBack,
                    )
                }

                // **« Capacités »** — la tuile *Capabilities* de la référence : ce que l'agent peut
                // utiliser dans les nouvelles conversations (compétences, jeux d'outils, MCP). C'est
                // le **même** écran que la route pleine page ; ici il se dessine **dans** le volet et
                // son retour **dépile le volet** ([paneBack]), comme les autres tuiles.
                Route.CAPABILITIES.name -> CapabilitiesScreen(
                    server = state.server,
                    onBack = paneBack,
                    // L'éditeur vit désormais ici : « Nouvelle compétence » et « Modifier » empilent
                    // sa page **dans le volet**, un niveau plus loin — le retour rend « Capacités ».
                    onEditSkill = { name ->
                        skillName = name
                        panePush(Route.SKILL_EDITOR.name)
                    },
                )

                // **« Configuration »** — la tuile *Config* de la référence. Chez elle, la roue de
                // la tête ouvre les Réglages de l'app et cette tuile la configuration de la
                // **passerelle** : deux écrans différents. Onyx garde la même séparation — la roue
                // reste le seul accès aux Réglages, cette tuile ouvre la page de la passerelle.
                Route.GATEWAY.name -> {
                    val paneGatewayViewModel: GatewayViewModel = viewModel(
                        key = "gateway-" + state.server.id + state.server.configSignature,
                        factory = viewModelFactory { initializer { GatewayViewModel(state.server) } },
                    )
                    GatewayScreen(
                        viewModel = paneGatewayViewModel,
                        onBack = paneBack,
                        onOpenLogs = { paneLeave(Route.LOGS.name) },
                    )
                }

                // **« Archives »** — la tuile *Archived* de la référence, créée ici : la liste des
                // conversations rangées de cet appareil, avec de quoi les ressortir. Une rangée
                // ouvre la conversation dans le fil (le volet repart de sa racine, comme les autres
                // gestes de la liste qui **sortent** du volet).
                Route.ARCHIVED.name -> ArchivedScreen(
                    conversations = history,
                    onOpen = { conversation ->
                        openingRect = null
                        chatViewModel.open(conversation)
                        paneLeave(Route.CHAT.name)
                        sidebarState.close()
                    },
                    onUnarchive = { conversation ->
                        scope.launch { conversationRepo.setArchived(conversation.id, false) }
                    },
                    onBack = paneBack,
                )

                Route.SETTINGS.name -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onDone = paneBack,
                    // Les pages que le volet porte aussi (Modèles, Fichiers, Planifiés, Kanban,
                    // Compétences, Outils, Aperçus) s'ouvrent **dans** le volet ; les autres sont
                    // des pages de l'app et la prennent, comme avant.
                    onOpenModels = { panePush(Route.MODELS.name) },
                    onOpenFiles = { panePush(Route.FILES.name) },
                    onOpenCron = { botCronProfile = null; panePush(Route.CRON.name) },
                    onOpenKanban = { panePush(Route.KANBAN.name) },
                    onOpenTools = { panePush(Route.TOOLS.name) },
                    onOpenInsights = { panePush(Route.INSIGHTS.name) },
                    onOpenAppearance = { paneLeave(Route.APPEARANCE.name) },
                    onOpenVoice = { paneLeave(Route.VOICE_SETTINGS.name) },
                    onOpenGateway = { paneLeave(Route.GATEWAY.name) },
                    onOpenLogs = { paneLeave(Route.LOGS.name) },
                    onOpenPhoneTools = { paneLeave(Route.PHONE_TOOLS.name) },
                    onOpenCalls = { paneLeave(Route.CALLS.name) },
                    onOpenBots = { paneLeave(Route.BOTS.name) },
                    onOpenServers = { paneLeave(Route.SERVERS.name) },
                    onOpenProfile = { paneLeave(Route.PROFILES.name) },
                    onOpenCapabilities = { paneLeave(Route.CAPABILITIES.name) },
                    onOpenWhatsNew = { paneLeave(Route.WHATS_NEW.name) },
                    onOpenTipJar = { paneLeave(Route.TIP_JAR.name) },
                    onOpenLock = { paneLeave(Route.LOCK.name) },
                    onOpenConnect = { paneLeave(Route.CONNECT.name) },
                    onOpenTerminal = { paneLeave(Route.TERMINAL_SETTINGS.name) },
                    onOpenAcknowledgements = { paneLeave(Route.ACKNOWLEDGEMENTS.name) },
                    onOpenContext = { paneLeave(Route.CONTEXT.name) },
                    onOpenMemory = { paneLeave(Route.MEMORY.name) },
                    onOpenNotifications = { paneLeave(Route.NOTIFICATIONS.name) },
                    onOpenConnectionDetails = { paneLeave(Route.CONNECTION_DETAILS.name) },
                    onSetContextWindow = settingsViewModel::setContextWindow,
                    onEraseEverything = {
                        scope.launch {
                            AppContainer.eraseEverything()
                            chatViewModel.newConversation()
                        }
                        sidebarState.clearDestinations()
                        navTo(Route.CHAT.name)
                    },
                )

                // Une tuile sans écran ici ne devrait pas exister (`SidebarDestinations.paneScreens`
                // est mesuré par un test) ; on ne dessine rien plutôt que d'inventer un écran.
                else -> Unit
            }
        }
        // Le contenu du volet : `null` quand il montre sa racine (la liste).
        val paneDestination = sidebarState.destination
        val paneScreenFor: (@Composable () -> Unit)? = paneDestination?.let { id ->
            val content: @Composable () -> Unit = { paneScreen(id) }
            content
        }

        /**
         * Le volet lui-même, tel qu'il se dessine dans les deux largeurs : la tête avec la
         * passerelle et son voyant, la recherche, le sélecteur *Chats / Bots*, les tuiles, les
         * projets, les filtres et la liste. Les **bots** ne sont pas dans la liste : ils vivent
         * dans la position *Bots*, avec les salons.
         */
        val chatSidebar: @Composable () -> Unit = {
            ChatSidebarHost(
                serverName = state.server.displayName,
                serverAddress = state.server.serveBaseUrl.ifBlank { state.server.baseUrl },
                // Joignable : le Dashboard répond. Non joignable : la dernière lecture a échoué.
                // « Pas encore su » : ni l'un ni l'autre — l'app n'a pas encore interrogé.
                reachable = when {
                    botsState.problem != null -> false
                    state.server.canAdminister && !botsState.loading -> true
                    else -> null
                },
                agentName = displayName.trim().ifEmpty { "Onyx" },
                // La marque d'identité d'une ligne se résout sur le **nom de profil de son bot** :
                // la même clé que la liste des bots, la fiche, l'en-tête et le volet.
                identityOf = { id -> identityByServer[id] ?: "default" },
                conversations = history,
                liveKinds = botsState.liveKinds,
                seenMarks = unreadMarks,
                bots = botsState.bots,
                mainName = displayName.trim().ifEmpty { "Onyx" },
                groups = botsState.groups,
                roomMarks = botsState.seenMarks,
                roomKinds = botsState.roomKinds,
                projects = serverProjects,
                faceCodeOf = { name ->
                    botsState.bots.firstOrNull { it.name == name }?.faceCode
                        ?: botsState.hidden.firstOrNull { it.name == name }?.faceCode
                        ?: botsState.faces[name]
                },
                // Onyx n'a pas de compte : le rond du pied porte **le signe de la maison** — la
                // rune du lanceur sur son disque teinté. Il portait l'initiale de la passerelle
                // (« O »), faute de mieux : une initiale n'est pas un signe.
                tiles = shownTiles,
                tileLibrary = tileLibrary,
                // **Le réglage est écrit tout de suite** (clé dédiée `sidebar_tiles`) : un appareil
                // tué au milieu de la composition retrouve la grille qu'il montrait, et l'ordre.
                onSetTileLayout = { ids -> scope.launch { settingsRepo.setSidebarTiles(ids) } },
                // Une tuile **empile son écran dans le volet** — elle ne le ferme plus. C'est la
                // correction demandée : « quand je clique par exemple sur Aperçus, on reste dans le
                // volet ». Le choix précédent faisait l'inverse (`navTo` puis `close()`), et le
                // propriétaire devait rouvrir le volet pour voir où il venait d'arriver.
                onOpenTile = { id ->
                    sidebarState.pushDestination(id)
                },
                // La roue dentée et le rond d'initiale sont la destination de la tuile
                // « Réglages » : elle s'ouvre dans le volet, comme les autres.
                onOpenSettings = {
                    sidebarState.pushDestination(Route.SETTINGS.name)
                },
                onOpenAccount = {
                    // Le rond de l'initiale — et le nom de la passerelle dans la tête du volet —
                    // ouvrent la feuille de compte (n° 8) : c'est là que vivent l'état de la
                    // connexion et ses gestes. Les Réglages gardent leurs deux chemins (la roue
                    // dentée de la tête, et la première rangée de la feuille).
                    accountSheetOpen = true
                },
                // Les gestes de la liste et du pied **sortent** du volet : ils ouvrent une
                // conversation ou une page, donc la pile d'écrans du volet repart de sa racine.
                onOpenConversation = { conversation, rect ->
                    // La rangée devient le fil : la couverture d'ouverture part de ses limites,
                    // comme la rangée de la liste le faisait.
                    openingRect = rect
                    chatViewModel.open(conversation)
                    paneLeave(Route.CHAT.name)
                    sidebarState.close()
                },
                onNewConversation = {
                    chatViewModel.newConversation()
                    paneLeave(Route.CHAT.name)
                    sidebarState.close()
                },
                // Le menu d'une rangée et la barre de sélection : les gestes de l'écran retiré,
                // tenus dans le volet.
                onSetConversationUnread = setConversationUnread,
                onPinConversation = { conversation, pinned ->
                    scope.launch { conversationRepo.setPinned(conversation.id, pinned) }
                },
                onSetConversationArchived = { conversation, archived ->
                    scope.launch { conversationRepo.setArchived(conversation.id, archived) }
                },
                onRenameConversation = { conversation, title ->
                    scope.launch { conversationRepo.rename(conversation.id, title) }
                },
                // « Créer un fil » : une branche locale (copie du transcript) ouverte à part. Le
                // serveur d'Onyx n'a pas de route pour bifurquer une session, la branche vit donc
                // sur ce téléphone — c'est dit à l'appelant, jamais promis au serveur.
                onCreateThreadConversation = { conversation ->
                    scope.launch {
                        conversationRepo.fork(conversation.id)?.let { branch ->
                            chatViewModel.open(branch)
                            navTo(Route.CHAT.name)
                            sidebarState.close()
                        }
                    }
                },
                // Le retour en arrière du bandeau : une conversation supprimée est rétablie. Le
                // rétablissement défait la **marque** de suppression — sans quoi le dépôt refuserait
                // de la réécrire (une suppression est définitive pour tout autre chemin).
                onRestoreConversation = { conversation -> scope.launch { conversationRepo.restore(conversation) } },
                // La suppression **porte jusqu'à la source de vérité** : la conversation quitte le
                // serveur (route `session.delete`, celle de l'écran d'un projet), puis le téléphone.
                // Sans cela le registre gardait la session et la conversation revenait — c'était le
                // défaut signalé : « je la retrouve ». Un échec serveur ne rend pas muet : la
                // suppression locale, elle, a eu lieu.
                onDeleteConversation = { conversation ->
                    chatViewModel.delete(conversation)
                    scope.launch {
                        runCatching {
                            com.onyx.app.ui.conversations.ProjectManage.delete(
                                state.server,
                                com.onyx.app.ui.conversations.ProjectManage.serverSessionIds(listOf(conversation)),
                            )
                        }
                    }
                },
                onOpenUsageConversation = { conversation ->
                    // « Usage & cost » : la fiche d'usage de cette conversation (la route
                    // `SESSION_USAGE`), comme le menu d'une rangée de l'écran retiré.
                    usageSummary = com.onyx.app.data.net.SessionSummary.of(conversation)
                    usageFetchId = conversation.serverSessionId
                    // Le profil **du bot** de cette conversation : c'est lui qui porte sa ligne au
                    // registre. `conversationProfile` lit la décision unique (`ProfileInForce`).
                    usageProfile = com.onyx.app.ui.bots.conversationProfile(
                        conversation,
                        bots,
                        state.server,
                        state.server.displayName,
                    )
                    navTo(Route.SESSION_USAGE.name)
                    sidebarState.close()
                },
                onArchiveConversations = { ids -> scope.launch { conversationRepo.archiveAll(ids) } },
                onDeleteConversations = { ids ->
                    // Le lot part du serveur, puis du téléphone : les conversations visées n'ont de
                    // session serveur que celles qui ont parlé — les autres n'ont rien à retirer.
                    val going = history.filter { it.id in ids }
                    scope.launch {
                        runCatching {
                            com.onyx.app.ui.conversations.ProjectManage.delete(
                                state.server,
                                com.onyx.app.ui.conversations.ProjectManage.serverSessionIds(going),
                            )
                        }
                    }
                    scope.launch { conversationRepo.deleteAll(ids) }
                },
                // « Réessayer » de la tête du volet : la liste des bots et celle des
                // conversations sont relues, comme la carte de problème de l'écran retiré.
                onRetry = {
                    botsViewModel.refresh()
                    scope.launch { conversationRepo.load() }
                },
                onOpenBot = { openBotChat(it); sidebarState.clearDestinations(); sidebarState.close() },
                // Le menu d'une carte de bot dans le volet : la même table que la page « Bots ».
                botMenu = botMenuTable,
                // Ce qui a échoué (dupliquer, repartir à zéro, supprimer) : le mot du modèle, montré
                // dans la position *Bots* — une action qui échoue ne reste pas muette.
                botNotice = botsState.problem,
                onDismissBotNotice = botsViewModel::dismissProblem,
                onNewBot = {
                    sidebarState.clearDestinations()
                    navTo(if (state.server.canAdminister) Route.BOTS.name else Route.API_BOT.name)
                    sidebarState.close()
                },
                onOpenRoom = { room -> openGroup(room); sidebarState.clearDestinations(); sidebarState.close() },
                // La création d'une salle vit dans l'écran des bots : le « + » y mène en un geste —
                // et, comme les autres entrées du volet, il **referme le volet** en ouvrant son
                // écran : sans ça il restait ouvert par-dessus la liste des bots, seul cas de tous
                // les choix du volet.
                onNewRoom = { navTo(Route.BOTS.name); sidebarState.close() },
                onNewProject = { projectCreating = true },
                onOpenProject = { project ->
                    selectedProject = project
                    // Le volet se referme **aussi** ici : c'était la seconde entrée qui le laissait
                    // ouvert sur l'écran ouvert.
                    navTo(Route.PROJECT_SESSIONS.name)
                    sidebarState.close()
                },
                // Ce que le volet porte : `null` quand il montre sa liste. **Replié**, le volet
                // montre sa racine — et le retour système ne lui appartient plus, il ne mange donc
                // pas un retour pour dépiler un écran qu'on ne voit pas.
                destination = sidebarState.destination.takeIf { sidebarState.isOpen },
                destinationContent = paneScreenFor.takeIf { sidebarState.isOpen },
                onPaneBack = paneBack,
                // L'état du volet porte le **mode recherche** (n° 7) : c'est lui qui permet au
                // retour de quitter la recherche avant de refermer le volet (le gestionnaire est
                // dans le volet, `ChatSidebarHost`).
                sidebarState = sidebarState,
            )
        }

        // Le clavier est traité **ici, une seule fois** : la zone de l'app se réduit au-dessus du
        // clavier (`imePadding`), puis l'inset est consommé pour que les écrans internes ne
        // s'en décalent pas une seconde fois. Sans ça, un champ bas d'un formulaire long
        // (« Identifiant » de la configuration) restait sous le clavier : la zone défilante
        // gardait toute la hauteur de l'écran, donc Compose n'avait rien à faire remonter.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .consumeWindowInsets(WindowInsets.ime),
        ) {
            // La géométrie du `NavigationSplitView` : volet borné (320…400 dp, jamais plus de
            // 40 % de la fenêtre), détail qui garde 360 dp, et — quand les deux minima ne tiennent
            // pas ensemble — un seul panneau. Le volet replié laisse le détail prendre tout.
            // Le **mode** du volet est décidé par la largeur, ici même : étroit, il recouvre le fil
            // (surimpression) ; large, il est posé à côté. Chaque mode a **sa** mémoire, si bien
            // qu'un volet ouvert sur une tablette ne s'invite pas en surimpression sur un téléphone.
            val sidebarMode = sidebarModeFor(maxWidth)
            LaunchedEffect(sidebarMode) { sidebarState.updateMode(sidebarMode) }
            // Ce que le réglage gardé était : on l'écrit de nouveau pour qu'un lancement suivant
            // retrouve la même disposition sur grand écran.
            LaunchedEffect(sidebarMode, sidebarState.isOpen) {
                if (sidebarMode == SidebarMode.DOCKED) {
                    settingsRepo.setSidebarCollapsed(!sidebarState.isOpen)
                }
            }
            // La géométrie des deux panneaux : replié, le détail prend tout. L'ouverture vient de
            // l'état du volet — le même que celui de la surimpression. Quand le volet **porte un
            // écran**, il prend la largeur d'un écran (`content`) : la colonne de la liste
            // l'écraserait (voir `sidebarContentWidthFor`).
            val layout = listDetailLayout(
                maxWidth,
                collapsed = sidebarMode == SidebarMode.DOCKED && !sidebarState.isOpen,
                content = sidebarState.isNavigating,
            )
            // Le **même** contrôle dans les deux largeurs : un seul appui ouvre ce qui est fermé,
            // referme ce qui est ouvert. C'est ce qui rend le volet réversible sans jamais demander
            // deux gestes — l'icône ne change pas, seul le libellé dit ce que le prochain appui fera.
            val sidebarToggleControl: @Composable () -> Unit = {
                SidebarToggleControl(
                    open = sidebarState.isOpen,
                    onClick = { sidebarState.toggle() },
                )
            }
            // Le contrôle de la barre : `sidebar.left`, **dans les deux états**. C'est ce qui rend
            // le retour au volet un seul appui — la même pastille replie (« Replier la liste ») et
            // déplie (« Déplier la liste »), que le volet soit posé ou non, et le même état que la
            // surimpression. Deux contrôles distincts obligeaient auparavant à viser celui qui
            // était dessiné : c'était le second geste à supprimer.
            val expandControl: (@Composable () -> Unit)? = if (layout.twoPanes) sidebarToggleControl else null
            val collapseControl: (@Composable () -> Unit)? = expandControl
            // En surimpression (téléphone étroit, paysage, pliable replié), l'écran porte le volet :
            // la **même** enveloppe les sert tous, si bien que le contrôle de la barre les ouvre tous
            // de la même façon, et que le retour matériel les referme tous.
            val withSidebar: @Composable (inner: @Composable () -> Unit) -> Unit = { inner ->
                SidebarScaffold(state = sidebarState, sidebar = { chatSidebar() }, content = { inner() })
            }
            // Le bord de l'écran est au retour système (une app ne peut s'en soustraire qu'en
            // partie : Android plafonne l'exclusion à 200 dp par bord, mesuré sur cet appareil).
            // Le geste du bord gauche — celui du `SidePanel` d'la référence — *révèle donc la liste des
            // conversations* par le retour lui-même, au lieu de fermer l'app comme il le faisait
            // sur les cinq géométries relevées : au pied de la pile il ouvre la liste, la liste
            // ouverte il la referme (le retour reste donc entier quand le panneau est ouvert), et
            // plus bas dans la pile il remonte comme avant. Deux panneaux : la liste est déjà là,
            // le retour n'a plus qu'à remonter la pile — jamais de fermeture de l'app.
            // Le retour (bouton ou geste du bord) ne doit **jamais** être avalé : s'il n'y a rien à
            // dépiler, on remonte à l'écran qui a du sens. Avant, sur Réglages en deux panneaux,
            // la pile était vide, `navBack()` rendait faux et le geste ne faisait **rien** — « je
            // peux même pas faire retour avec le bord de l'écran ».
            // Deux panneaux : la **racine** est le fil — le détail le peint aussi bien pour Chat que
            // pour la liste des conversations, qui vit à gauche. Au pied de la pile, le retour n'est
            // donc plus consommé : il rend la main au système, comme sur un téléphone. La règle est
            // partagée et mesurée ([routeBackHandled]).
            val backHandled = routeBackHandled(
                currentRoute = route,
                backStack = backStack,
                twoPanes = layout.twoPanes,
                chatRoute = Route.CHAT.name,
            )
            // **Le repli d'un retour à pile vide — une seule règle pour les trois gestes.**
            // La flèche de l'en-tête (dans la page), le bouton retour du système et le geste du
            // bord descendent la **même** descente : on remonte la pile d'abord (`navBack`), et le
            // repli ne sert **que** quand elle est vide. Il vient ici de la **table des retours**
            // ([TableDesRetours]) — jamais d'une cible écrite en dur. Les flèches des pages
            // écrivent ce même repli à leur site d'appel (`if (!navBack()) navTo(<repli>)`) : les
            // deux cibles ne peuvent plus diverger, et `FlecheInterneVerrouTest` compare l'une à
            // l'autre, en nommant tout écart. (La page de contexte est la seule dont le repli est
            // un **état**, `contextReturn` — sa ligne de table le dit.)
            fun repliDeRetour(): String =
                if (route == Route.CONTEXT_EDITOR.name) {
                    contextReturn
                } else {
                    TableDesRetours.de(route)?.repli
                        ?: rootRouteBehind(currentRoute = route, chatRoute = Route.CHAT.name)
                }
            BackHandler(enabled = backHandled) {
                if (!navBack()) {
                    navTo(repliDeRetour())
                }
            }

            // Chaque route garde son état sauvegardé (position de défilement comprise) : la route
            // qui sort de la composition le laisse ici, et le retour la retrouve **pile où elle
            // était** — avant, revenir d'un sous-écran ramenait en haut de la page précédente.
            val routeStates = rememberSaveableStateHolder()

            AnimatedContent(
                targetState = Route.valueOf(route),
                transitionSpec = {
                    if (layout.twoPanes && initialState in TwoPaneRoutes && targetState in TwoPaneRoutes) {
                        // La colonne ne glisse pas avec le détail : comme NavigationSplitView.
                        EnterTransition.None.togetherWith(ExitTransition.None)
                    } else {
                        onyxRouteTransition(initialState, targetState)
                    }
                },
                // Le contenu de la route est enregistré dans le calque du verre : une vitre qui
                // le recouvre (panneau de menu, bouton flottant) y voit ce qui passe derrière.
                modifier = Modifier
                    .fillMaxSize()
                    .then(LocalBackdrop.current?.let { Modifier.recordBackdrop(it) } ?: Modifier),
                label = "onyxRoute",
            ) { presentedRoute ->
              routeStates.SaveableStateProvider(presentedRoute.name) {
                if (layout.twoPanes && presentedRoute in TwoPaneRoutes) {
                    // Le volet est replié : un seul panneau est peint, mais il reste deux
                    // panneaux « en puissance » — la géométrie garde ses bornes, et le détail
                    // porte le contrôle qui rouvre la liste.
                    if (presentedRoute == Route.KANBAN) {
                        // Capture Kanban 2.0 : le tableau et la conversation côte à côte. Le
                        // tableau tient le panneau de gauche et porte le sélecteur de section
                        // (le `topRow` d'la référence) ; la conversation celui de droite — « demander à
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
                            sidebar = { chatSidebar() },
                            detail = {
                                when (presentedRoute) {
                                    Route.CHAT ->
                                        key(state.conversationId) { chatPane(false, expandControl) }

                                    Route.CRON -> cronPane(true, expandControl)
                                    Route.KANBAN -> kanbanPane(true, null, expandControl)
                                    Route.CODE -> codePane(true, expandControl)
                                    // Les bots gardent la colonne à leur gauche (D6).
                                    Route.BOTS -> botsPane(true, expandControl)
                                    else -> Unit
                                }
                            },
                        )
                    }
                } else {
                    when (presentedRoute) {
            Route.CHAT -> withSidebar { chatPane(true, sidebarToggleControl) }

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
                // `VoiceView`: `Self.demo ? .listening : session.phase` — la maquette montre
                // l'écoute, l'orbe vit de la même onde, et rien n'est un mensonge : c'est une pose.
                phase = voicePose ?: if (voiceDemo) VoicePhase.LISTENING else phase,
                heard = if (voiceDemo && (voicePose == null || voicePose == VoicePhase.LISTENING)) {
                    VOICE_DEMO_WORDS
                } else {
                    heard
                },
                spoken = if (voicePose == VoicePhase.SPEAKING) VOICE_DEMO_REPLY else spoken,
                level = if (voiceDemo) demoVoiceLevel.value else listenLevel,
                streaming = state.isStreaming,
                handsFree = if (voiceDemo) true else handsFree,
                // `VoiceView.soloHeader` montre `conversation.title` : le nom donné à la
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
                problem = listenProblem,
                alertAction = listenAlert,
                // La phrase d'aide nomme les phrases réellement écoutées (celles réglées par la
                // personne, sinon celles de base) : plus de mots anglais au-dessus de réglages
                // français.
                stopPhrases = stopPhrases,
                onToggleHandsFree = voice::toggleHandsFree,
                // « Parler pendant les réponses » : les trois choix du panneau vocal écrivent le
                // réglage (le même que Réglages → Voix), qui décide si le micro s'ouvre derrière
                // une réponse.
                talkOverReplies = TalkOverChoice.of(talkOver),
                onTalkOverChange = { choice -> scope.launch { settingsRepo.setTalkOver(choice.setting) } },
                onRestart = {
                    voiceProblem = null
                    if (phase == VoicePhase.IDLE) {
                        state.messages.lastOrNull {
                            it.role == com.onyx.app.data.model.Role.ASSISTANT && it.text.isNotBlank()
                        }?.let {
                            // La relance sort de la voix du bot de la conversation ouverte —
                            // `replayLastReply()` du modèle passe `conversation.bot?.profile`.
                            voice.speak(it.text, speaker = openConversationBot?.name)
                        }
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
                    if (!navBack()) navTo(Route.CHAT.name)
                },
                onOpenSettings = { navTo(Route.VOICE_SETTINGS.name) },
                isPaused = voice.isPaused.collectAsStateWithLifecycle().value,
                onPauseResume = { if (voice.isPaused.value) voice.resumeSpeaking() else voice.pauseSpeaking() },
                // Réglages → Apparence : l'orbe et sa lumière sont ceux du réglage, partout.
                // `VoiceOrb.shown` d'la référence : « Bot faces » éteint remplace la face de Onyx par la
                // Bubble, pour qu'aucun visage n'apparaisse là.
                orb = if (voiceOrb == VoiceOrbChoice.ONYX && !botFaces) VoiceOrbChoice.BUBBLE else voiceOrb,
                onOrbChange = { orb -> scope.launch { settingsRepo.setVoiceOrb(orb) } },
                orbLight = orbLight,
                onOrbLightChange = { light -> scope.launch { settingsRepo.setOrbLight(light) } },
                // L'orbe est un bouton, comme `VoiceView:123` : son action est celle de la
                // phase en cours (`VoiceSession.primaryAction`).
                onOrbClick = {
                    when (phase) {
                        VoicePhase.IDLE, VoicePhase.SPEAKING, VoicePhase.THINKING -> {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) voice.startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        // Rien ne se dit de plus à mi-mots : l'appareil envoie la question dès que
                        // la phrase se termine ; l'orbe rend donc le repos, sans mentir.
                        VoicePhase.LISTENING -> voice.stop()
                    }
                },
            )

            Route.COMPOSER_EDITOR -> ComposerEditorScreen(
                draft = state.input,
                onDraftChange = chatViewModel::onInputChange,
                onDone = { if (!navBack()) navTo(Route.CHAT.name) },
                onSend = {
                    navTo(Route.CHAT.name)
                    chatViewModel.send()
                },
                isStreaming = state.isStreaming,
            )

            Route.SETTINGS -> SettingsScreen(
                viewModel = settingsViewModel,
                // « Terminé » referme la feuille : on revient **d'où l'on est venu** (le fil, la
                // liste, ou une tuile de bot), et non systématiquement sur le fil — ouvrir les
                // Réglages depuis la liste des conversations et se retrouver dans le fil était
                // un retour faux.
                onDone = { if (!navBack()) navTo(Route.CHAT.name) },
                onOpenAppearance = { navTo(Route.APPEARANCE.name) },
                onOpenModels = { navTo(Route.MODELS.name) },
                onOpenVoice = { navTo(Route.VOICE_SETTINGS.name) },
                onOpenGateway = { navTo(Route.GATEWAY.name) },
                // Journaux : la ligne des Réglages n'est **jamais** fermée (le journal vocal est
                // local à l'appareil), là où la Passerelle demande une session du Dashboard.
                onOpenLogs = { navTo(Route.LOGS.name) },
                onOpenFiles = { navTo(Route.FILES.name) },
                onOpenCron = { botCronProfile = null; navTo(Route.CRON.name) },
                onOpenKanban = { navTo(Route.KANBAN.name) },
                onOpenPhoneTools = { navTo(Route.PHONE_TOOLS.name) },
                onOpenCalls = { navTo(Route.CALLS.name) },
                onOpenBots = { navTo(Route.BOTS.name) },
                onOpenServers = { navTo(Route.SERVERS.name) },
                onOpenProfile = { navTo(Route.PROFILES.name) },
                onOpenTools = { navTo(Route.TOOLS.name) },
                onOpenCapabilities = { navTo(Route.CAPABILITIES.name) },
                onOpenInsights = { navTo(Route.INSIGHTS.name) },
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
                // Les Réglages se présentent par-dessus les deux panneaux : leur barre garde le
                // contrôle du volet, comme les autres routes d'un écran large.
                sidebarControl = expandControl ?: collapseControl,
                onEraseEverything = {
                    // la référence efface puis ferme la feuille ; ici l'effacement vide aussi la
                    // conversation ouverte, donc on repart sur une nouvelle.
                    scope.launch {
                        AppContainer.eraseEverything()
                        chatViewModel.newConversation()
                    }
                    navTo(Route.CHAT.name)
                },
            )

            Route.CRON -> withSidebar { cronPane(false, sidebarToggleControl) }

            Route.CODE -> withSidebar { codePane(false, sidebarToggleControl) }

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

            // « Voice… » : la voix du bot, choisie pour lui seul (`BotVoicePicker`). Le réglage est
            // écrit tout de suite (`setBotVoice`) ; l'écran relit ce que l'app a gardé, donc la
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
                        kokoroToken = kokoroToken,
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
                // Le retour remonte **la pile** d'abord : cet écran s'ouvre aussi du **volet**
                // (« Nouveau bot » d'un serveur sans identifiants Dashboard, dont la page
                // précédente est le fil), et `navTo` y laissait une entrée derrière lui — le
                // retour d'alors ne rendait pas la page quittée, et le suivant la rejouait.
                onBack = { if (!navBack()) navTo(Route.BOTS.name) },
            )

            Route.BOTS -> withSidebar { botsPane(false, sidebarToggleControl) }

            Route.BOT_EDITOR -> {
                val bot = selectedBot
                // Les jeux d'outils et les compétences du bot : lus au serveur à l'ouverture de
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
                        // « Voice… » de l'éditeur : la même feuille que par le menu d'une tuile.
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
                    // **Pas de porte « Idées » sur la page d'identité** : l'arbitrage de la
                    // réconciliation refuse la rangée que le lot du visage avait ajoutée. La
                    // suppression d'Idées/Objectifs (demande du propriétaire) a retiré l'écran, sa
                    // route, sa destination, sa clé de dictionnaire et cette porte de secours.
                    // Voir `SuppressionIdeesObjectifsTest` et `VisagePorteIdentiteTest`.
                    problem = deleteProblem ?: editorState.problem,
                    onSave = { title, about, model, shown ->
                        bot?.let { botsViewModel.saveEdit(it, title, about, model, shown) }
                        navTo(Route.BOTS.name)
                    },
                    onCreate = { title, about, soul, shown, faceCode ->
                        // Le profil naît sur le serveur avec le visage de son modèle : l'écran ne
                        // se ferme qu'une fois qu'il a pris, et un refus se lit ici.
                        scope.launch {
                            val failure = botsViewModel.createBot(
                                title = title,
                                about = about,
                                soul = soul,
                                faceCode = faceCode,
                                hidden = !shown,
                            )
                            if (failure == null) {
                                selectedBot = null
                                navTo(Route.BOTS.name)
                            } else {
                                deleteProblem = failure
                            }
                        }
                    },
                    onDelete = {
                        val target = bot
                        if (target == null) {
                            navTo(Route.BOTS.name)
                        } else {
                            // La suppression part au serveur : l'écran ne se ferme qu'une fois
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
                    onCancel = { if (!navBack()) navTo(Route.BOTS.name) },
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
                    // Une salle du serveur ouverte depuis la liste : son fil vient d'Hermes lui-même
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
                        faceResolver = faceResolver,
                        // La salle s'ouvre depuis la liste des conversations comme depuis l'onglet
                        // Bots : le retour revient donc là d'où l'on vient, et ne saute plus
                        // systématiquement sur Bots.
                        onBack = { if (!navBack()) navTo(Route.BOTS.name) },
                        // Le crayon : une conversation de **cette** salle — ses membres et son nom,
                        // ouverts neufs. Le journal de la salle reste chez Hermes, et la liste le
                        // retrouve ; une salle encore vierge se reprend telle quelle, pour ne pas
                        // empiler deux conversations vides.
                        onNewConversation = {
                            if (roomStartsAnotherConversation(roomViewModel.state.value.rows)) {
                                openedRoom = null
                                groupPicked = room.members.map { it.name }.toSet()
                                groupTitle = room.name
                                navTo(Route.GROUP_CHAT.name)
                            }
                        },
                        // Le bouton Talk de la salle ouvre la scène vocale, qui lit ce fil
                        // (`GroupRoomFeed`) : les réponses du serveur et celui qui parle.
                        onOpenVoice = { navTo(Route.GROUP_VOICE.name) },
                        // Un fichier de l'onglet Fichiers de la salle s'ouvre comme celui d'une
                        // conversation : la même route, le même lecteur de famille.
                        onOpenFile = { uri, name, mime -> openConversationFile(uri, name, mime) },
                        onEnded = {
                            // Terminée sur le serveur : la liste la laisse partir.
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
                    // La salle choisie dans la liste : ses membres sont cochés d'avance, comme le
                    // `NewGroupChatView` d'la référence part des bots de la salle.
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
                    // `NewGroupChatView` : les bots choisis dans la feuille deviennent les membres
                    // qui répondent, comme le `createGroup(named:bots:mainName:)` de la référence.
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
                        // Le crayon : une nouvelle conversation de **ce** groupe — le fil repart à
                        // vide, les bots choisis restent (mêmes membres, même salle). Un fil déjà
                        // vide est repris tel quel.
                        onNewConversation = groupViewModel::startNewConversation,
                        onOpenVoice = { navTo(Route.GROUP_VOICE.name) },
                        speechLocale = speechLocale,
                        focusComposer = groupComposerFocus,
                        onComposerFocused = { groupComposerFocus = false },
                        chatTextScale = com.onyx.app.ui.theme.ResolvedTheme.chatTextScale(chatTextSize),
                    )
                }
            }

            Route.GROUP_VOICE -> {
                // Maquette de boutique : une salle scriptée dont la réplique lue est **injectée**,
                // pour éprouver la scène de parole là où il n'y a ni serveur ni micro (debug seul).
                //
                // Et sa variante qui **joue la boucle** : quand le moteur sans micro est posé
                // (`onyx.voiceScenario`), la salle scriptée **répond** et c'est la vraie session
                // qui tourne — écoute, question, file des réponses, reprise. C'est ce qui rend la
                // conversation d'une salle prouvable sur un émulateur.
                val liveDemo = remember(groupVoiceDemoOn, voiceScenario) {
                    if (groupVoiceDemoOn && voiceScenario != null) GroupVoiceDemo.live(scope) else null
                }
                // Sur un émulateur sans serveur, la page Journaux de l'app reste hors de portée :
                // la maquette recopie donc le **journal vocal de l'appareil** dans logcat, ligne par
                // ligne, pour que les étapes d'une salle se lisent. Le drapeau `onyx.voiceJournal`
                // fait la même recopie hors maquette — c'est lui qui rend lisible la salle d'un
                // **vrai** serveur, dont la page Journaux reste, ici, inatteignable. Debug seulement.
                val journalToLogcat = remember {
                    com.onyx.app.BuildConfig.DEBUG &&
                        context.findActivity()?.intent?.getBooleanExtra(EXTRA_VOICE_JOURNAL, false) == true
                }
                LaunchedEffect(liveDemo, journalToLogcat) {
                    if (liveDemo == null && !journalToLogcat) return@LaunchedEffect
                    var seen = 0
                    while (true) {
                        val lines = com.onyx.app.voice.VoiceDiagnostics.lines()
                        while (seen < lines.size) {
                            android.util.Log.i("OnyxVoiceDemo", lines[seen])
                            seen += 1
                        }
                        delay(300)
                    }
                }
                val demo = remember(groupVoiceDemoOn, liveDemo) {
                    if (groupVoiceDemoOn && liveDemo == null) {
                        GroupVoiceDemo.from(context.findActivity()?.intent?.getStringExtra(EXTRA_GROUP_VOICE_DEMO))
                    } else {
                        null
                    }
                }
                // Deux fils peuvent nourrir la scène vocale : celui d'une salle du serveur quand une
                // est ouverte (`openedRoom` — c'est là que la voix lit les réponses du serveur), et
                // celui du compositeur local d'une salle neuve, dont les membres ne sont pas encore
                // chez Hermes. Les deux ViewModels sont pris avec la **même clé** que GROUP_CHAT : la
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
                val thread: GroupVoiceThread = liveDemo?.thread ?: demo?.thread ?: roomViewModel ?: groupViewModel!!

                // Les visages de la salle, pour l'en-tête et l'orbe.
                val bots = (liveDemo ?: demo)?.bots ?: if (roomViewModel != null) {
                    val roomState by roomViewModel.state.collectAsStateWithLifecycle()
                    roomState.room.composerBots(roomViewModel.face)
                } else {
                    val groupState by groupViewModel!!.state.collectAsStateWithLifecycle()
                    groupState.bots.filter { it.name in groupState.selected }
                }

                // Les noms de la salle : c'est eux qui disent à la lecture quelle voix va à qui,
                // pour que deux bots ne lisent pas de la même (`BotVoices`).
                val roster = bots.map { it.name }
                LaunchedEffect(roster) { voice.output?.setBotRoster(roster) }

                // Le fil de la salle se suit tant que la scène vocale est là, comme le
                // `.task(id: scenePhase == .active || voice.isOn)` de la référence : les réponses des bots
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
                // La salle s'est tue **sans rien répondre** : la scène le dit, au lieu de laisser
                // « les bots discutent » tourner dans le vide.
                val groupStalled by groupVoice.stalled.collectAsStateWithLifecycle()
                // Ce que la salle a refusé (une question qui n'est pas partie) : sans ça, une bulle
                // disparaissait sans un mot dans la scène vocale.
                val roomProblem by thread.problem.collectAsStateWithLifecycle()
                // La salle s'est tue sans rien dire : le bandeau le dit, en français.
                val stalledProblem = if (groupStalled) {
                    OnyxLocalization[
                        "The room didn't answer: its bots are stopped, or the server sent nothing back.",
                    ]
                } else {
                    null
                }
                val isPaused by voice.isPaused.collectAsStateWithLifecycle()
                val lines by thread.lines.collectAsStateWithLifecycle()
                val isTalking by thread.isTalking.collectAsStateWithLifecycle()
                val waitsOnPerson by thread.waitsOnPerson.collectAsStateWithLifecycle()
                // La file : les réponses qui attendent d'être lues, et le message en route.
                val queued by groupVoice.queued.collectAsStateWithLifecycle()
                val voiceProblemShown by groupVoice.problem.collectAsStateWithLifecycle()
                val sending by thread.pendingSends.collectAsStateWithLifecycle()
                // Le niveau de la voix lue : c'est lui qui fait battre la bouche du grand visage.
                val replyLevel by voice.replyLevel.collectAsStateWithLifecycle()
                // La maquette pose la scène qu'elle annonce (parole ou écoute) ; sinon, la phase
                // réelle et le bot que le coordinateur est en train de lire.
                val shownPhase = demo?.phase ?: phase
                val shownSpeaker = demo?.speakerId ?: liveSpeakerId
                // La réplique de la maquette est simulée : son niveau bat comme une voix, pour que
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
                // `solIsWorking` de la référence : quelque chose tourne — la voix, ou une salle qui parle.
                val groupWorking = shownPhase == VoicePhase.LISTENING || shownPhase == VoicePhase.SPEAKING ||
                    (isTalking && !isHeld) || sending || queued > 0

                LaunchedEffect(groupVoice, demo) {
                    // La maquette n'ouvre ni micro ni lecture : sa réplique est injectée.
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
                    // Le niveau de la réplique lue : la bouche du visage qui parle le suit.
                    speakingLevel = soundingLevel,
                    // Une salle est toujours en mains-libres : chaque round rend le micro, et
                    // l'en-tête de la salle ne dessine donc aucun interrupteur pour ça.
                    handsFree = true,
                    conversationTitle = thread.roomName,
                    // Ce qui a empêché une réponse se lit **dans la scène** : le micro ou le moteur
                    // (voix), la question refusée par la salle, la salle qui s'est tue sans rien
                    // dire, un message encore en route. Jamais un écran muet.
                    problem = voiceProblem ?: roomProblem ?: voiceProblemShown ?: stalledProblem ?: speech.problem,
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
                        if (!navBack()) navTo(Route.GROUP_CHAT.name)
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
                        stalled = groupStalled,
                        pending = queued,
                        sending = sending,
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

            // « Connection details » : `ConnectionDetailsView`, ouvert depuis les Réglages.
            Route.CONNECTION_DETAILS -> ConnectionDetailsScreen(
                server = state.server,
                onSave = { settingsViewModel.saveConnection(it) },
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            // « Hidden Bots » : `HiddenBotsView`, ouvert par la tuile « Hidden » de la grille.
            Route.HIDDEN_BOTS -> HiddenBotsScreen(
                viewModel = botsViewModel,
                mainName = state.server.displayName,
                onBack = { if (!navBack()) navTo(Route.BOTS.name) },
            )

            // « Usage & cost » : `SessionUsageView`.
            Route.SESSION_USAGE -> {
                val summary = usageSummary
                if (summary == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    SessionUsageScreen(
                        initial = summary,
                        server = state.server,
                        fetchId = usageFetchId,
                        profile = usageProfile,
                        onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                    )
                }
            }

            // « Les sessions d'un projet » : `ProjectSessionsView`.
            Route.PROJECT_SESSIONS -> {
                val project = selectedProject
                if (project == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
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
                            usageProfile = null
                            navTo(Route.SESSION_USAGE.name)
                        },
                        onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                        // Où déplacer une conversation : les projets qui sont un **dossier**, sans
                        // celui-ci (le fourre-tout et le dossier personnel ne reçoivent rien).
                        moveTargets = com.onyx.app.data.net.projectFolders(serverProjects)
                            .filter { it.id != project.id },
                        onArchive = { ids ->
                            com.onyx.app.ui.conversations.ProjectManage.archive(state.server, ids)
                        },
                        onDelete = { ids ->
                            com.onyx.app.ui.conversations.ProjectManage.delete(state.server, ids)
                        },
                        onMove = { ids, target ->
                            com.onyx.app.ui.conversations.ProjectManage.move(state.server, ids, target)
                        },
                        onRequestNewProject = { projectCreating = true },
                        onRequestRenameProject = { chosen -> projectRenaming = chosen },
                        onRequestRemoveProject = { chosen -> projectRemoving = chosen },
                    )
                }
            }

            Route.PHONE_TOOLS -> PhoneToolsScreen(onBack = { if (!navBack()) navTo(Route.SETTINGS.name) })

            Route.CALLS -> CallsScreen(onBack = { if (!navBack()) navTo(Route.SETTINGS.name) })

            Route.COMPUTER -> ComputerScreen(
                session = AppContainer.computerSession,
                // Le menu du fil ouvre l'ordinateur : en deux panneaux, la page quittée est la
                // **liste** (qui peint le même fil), donc on dépile avant de se rabattre sur le fil.
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
            )

            Route.LOCK -> LockSettingsScreen(onBack = { if (!navBack()) navTo(Route.SETTINGS.name) })

            Route.ACKNOWLEDGEMENTS -> AcknowledgementsScreen(onBack = { if (!navBack()) navTo(Route.SETTINGS.name) })

            Route.TIP_JAR -> TipJarScreen(onBack = { if (!navBack()) navTo(Route.SETTINGS.name) })

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
                    // Le lien « tout en un » : le même code porte aussi les voix, le pont
                    // « Ordinateur » et la langue — un seul collage, un seul secret.
                    voicesURL = kokoroUrl,
                    bridgeURL = AppContainer.computer.address,
                    bridgeToken = AppContainer.computer.token,
                    language = if (appLanguage == com.onyx.app.localization.OnyxLanguage.SYSTEM) "" else appLanguage,
                ),
                onScan = { setupScannerOpen = true },
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            Route.KANBAN -> withSidebar { kanbanPane(false, null, sidebarToggleControl) }

            Route.VOICE_SETTINGS -> VoiceSettingsScreen(
                viewModel = settingsViewModel,
                server = state.server,
                // La page Voix s'ouvre depuis les Réglages **et** depuis la scène vocale : le
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
                token = kokoroToken,
                currentVoice = kokoroVoice,
                onVoiceChange = settingsViewModel::setKokoroVoice,
                onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) },
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
                // La ligne la plus fraîche : le fil (ou le roster) la met à jour pendant qu'on
                // regarde, et l'écran suit — un arrêt effectif doit se voir tout de suite.
                val agent = subagentAgent?.let { picked -> shownSubagents.firstOrNull { it.id == picked.id } ?: picked }
                if (agent == null) {
                    // Hors de la composition : la route vide ne doit pas non plus rester dans la pile.
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    // La transcription : lue sur le Dashboard (`subagent.tail`) ; rien à lire si le
                    // transport ne la porte pas, et l'écran le dit alors au lieu d'attendre.
                    val controllable = agent.hasTranscript && !subagentDemoRun
                    // Les deux gestes de contrôle, typés explicitement : la doublure de QA les
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
                                // Transcription **injectée** : l'émulateur n'a pas de Dashboard
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
                // Le roster du Dashboard (`subagent.list`) complète ce que le fil a vu : sans tour
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
                onClose = { if (!navBack()) navTo(Route.CHAT.name) },
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
                        onClose = { if (!navBack()) navTo(Route.CHAT.name) },
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
                        onClose = { if (!navBack()) navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.VIDEO -> {
                val source = videoSource
                if (source == null) {
                    // Le repli se pose **hors de la composition** (`LaunchedEffect`) : un
                    // `navTo` écrit pendant la composition laisse le geste de retour sans effet
                    // quand l'état vide se reproduit (l'entrée repoussée à chaque passage).
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    com.onyx.app.ui.chat.VideoPlayerScreen(
                        source = source,
                        onClose = { if (!navBack()) navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.AUDIO -> {
                val source = soundSource
                if (source == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    com.onyx.app.ui.chat.AudioPlayerScreen(
                        source = source,
                        onClose = { if (!navBack()) navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.DOCUMENT -> {
                val source = documentSource
                if (source == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    com.onyx.app.ui.chat.DocumentScreen(
                        source = source,
                        onClose = { if (!navBack()) navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.ARCHIVE -> {
                val source = archiveSource
                if (source == null) {
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    com.onyx.app.ui.chat.ArchiveScreen(
                        source = source,
                        onClose = { if (!navBack()) navTo(Route.CHAT.name) },
                        // Une archive qu'on ne sait pas lire sur place n'est pas un échec : elle
                        // part à l'application du système, qui saura peut-être l'ouvrir.
                        onOpenExternally = {
                            openFile(
                                uri = source.uri,
                                name = source.name,
                                mime = com.onyx.app.ui.chat.FileViewers.mimeFor(source.name),
                            )
                        },
                    )
                }
            }

            // « Capture rapide » : une note prise en passant, que l'agent range. Elle se prend
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
                        // La feuille du fil ouvre la sélection : la page quittée se dépile d'abord.
                        onBack = { if (!navBack()) navTo(Route.CHAT.name) },
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
                        // Comme la sélection de texte : on remonte la page quittée.
                        onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.TOOLS -> ToolsScreen(
                server = state.server,
                scope = scope,
                // Tuile du **volet** comme ligne des Réglages : le retour rend la page quittée
                // (le fil), et ne saute pas aux Réglages quand on vient d'ailleurs.
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            // La tuile « Compétences » a été **retirée** (« Capacités » la remplace) : sa route a
            // quitté l'énum avec elle. L'**éditeur**, lui, reste — « Capacités » l'ouvre depuis la
            // famille des compétences, et son retour rend « Capacités », la page quittée.
            Route.SKILL_EDITOR -> SkillEditorScreen(
                server = state.server,
                existing = skillName,
                scope = scope,
                onSaved = { navTo(Route.CAPABILITIES.name) },
                onBack = { if (!navBack()) navTo(Route.CAPABILITIES.name) },
            )

            // « Capacités » : les compétences, les jeux d'outils et les serveurs MCP du profil,
            // chacun avec son interrupteur (le catalogue est une page de cet écran). Depuis le
            // retrait de la tuile « Compétences », elle porte aussi **l'écriture** d'une compétence.
            Route.CAPABILITIES -> CapabilitiesScreen(
                server = state.server,
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
                onEditSkill = { name ->
                    skillName = name
                    navTo(Route.SKILL_EDITOR.name)
                },
            )

            // « Archives » en pleine page : la **même** liste que celle que le volet porte. Elle ne
            // s'ouvre que par la tuile (qui empile dans le volet) ; cette branche est le repli de
            // l'appelant hors du volet, et garde la route vivante comme ses voisines.
            Route.ARCHIVED -> ArchivedScreen(
                conversations = history,
                onOpen = { conversation ->
                    openingRect = null
                    chatViewModel.open(conversation)
                    navTo(Route.CHAT.name)
                },
                onUnarchive = { conversation ->
                    scope.launch { conversationRepo.setArchived(conversation.id, false) }
                },
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
            )

            // « Relevé de l'agent » : la conversation ouverte (son fil, plus sa ligne du registre
            // des sessions), ou un bot en cumul — les mêmes rangées, à l'échelle de ce que ce
            // téléphone lui rattache. Les prix viennent du serveur, et rien n'est calculé sans eux.
            Route.LEDGER -> AgentLedgerScreen(
                server = state.server,
                conversations = history,
                bots = bots,
                mainName = state.server.displayName.ifBlank { "Onyx" },
                openConversation = history.firstOrNull { it.id == state.conversationId },
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                standIn = ledgerStandIn,
            )

            // « Aperçus » : ce que ce profil a consommé sur 7, 30 ou 90 jours. La période est
            // mémorisée par l'appareil, donc la relire après une relance retrouve le même choix.
            Route.INSIGHTS -> {
                val period by settingsRepo.insightsPeriod.collectAsStateWithLifecycle()
                InsightsScreen(
                    server = state.server,
                    period = period,
                    onSelectPeriod = { chosen -> scope.launch { settingsRepo.setInsightsPeriod(chosen) } },
                    onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
                )
            }

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
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            Route.WHATS_NEW -> WhatsNewScreen(
                version = com.onyx.app.BuildConfig.VERSION_NAME,
                items = CURRENT_RELEASE_ITEMS,
                onContinue = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            Route.SERVERS -> {
                val all by servers.servers.collectAsStateWithLifecycle()
                val active by servers.activeId.collectAsStateWithLifecycle()
                ServersScreen(
                    repository = servers,
                    servers = all,
                    activeId = active,
                    scope = scope,
                    // Ouverts depuis les Réglages **ou** à la fin de la configuration (`SETUP`) : le
                    // retour remonte donc la pile avant de se rabattre sur les Réglages.
                    onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
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
                        settingsViewModel.setSetupDone(true)
                        setupCodeEditing = false
                        if (!navBack()) navTo(Route.SERVERS.name)
                    },
                    // « Ignorer » ici aussi sort sans rien enregistrer : ouvrir l'éditeur depuis les
                    // Réglages ne doit pas pouvoir vider un serveur enregistré.
                    onSkip = {
                        setupCodeEditing = false
                        if (!navBack()) navTo(Route.SERVERS.name)
                        applyPendingLanguage()
                    },
                    scope = scope,
                    onScanCode = { setupScannerOpen = true },
                    onPasteCode = { text ->
                        SetupCodeOffer.fromText(text)?.let { offer ->
                            presentSetupOffer(offer)
                            true
                        } == true
                    },
                    onDiscover = { address, key ->
                        val found = com.onyx.app.data.net.OnyxDiscovery.discover(address, key)
                        // « Une clé » : le Dashboard vit à la même adresse que le chat — voir la
                        // note du même geste plus haut.
                        if (found.declaresDashboard) {
                            setupDraft = setupDraft.copy(serveBaseUrl = address.trim())
                        }
                        found
                    },
                    onApplyDiscovery = { found -> scope.launch { applyDiscovery(found) } },
                    // **Ce que le mode « Simple » propose** : les adresses déjà connues, dans
                    // l'ordre de pertinence (serveur en cours, réseau du téléphone, passerelle
                    // précédente, autres serveurs enregistrés).
                    suggestions = com.onyx.app.ui.setup.SetupSuggestions.known(
                        current = com.onyx.app.ui.setup.setupAddressOf(state.server),
                        local = setupLocalAddresses,
                        lastGateway = serverList
                            .firstOrNull { it.id != state.server.id }
                                ?.let(::setupAddressOf)
                            .orEmpty(),
                        saved = serverList
                            .filter { it.id != state.server.id }
                            .drop(1)
                            .map(::setupAddressOf),
                    ),
                    // Le lien de configuration du brouillon, **sans ses secrets** : le « Copier »
                    // de la barre de titre.
                    setupLink = if (setupDraft.isUsable) {
                        setupLinkFor(setupDraft)
                    } else {
                        ""
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
                onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) },
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
                        onBack = { if (!navBack()) navTo(Route.SPOKEN_PREFIXES.name) },
                    )
                }
            }

            Route.STOP_PHRASES -> {
                val phrases by settingsRepo.stopPhrases.collectAsStateWithLifecycle()
                StopPhrasesScreen(
                    phrases = phrases,
                    onAdd = { phrase -> scope.launch { settingsRepo.setStopPhrases(phrases + phrase) } },
                    onRemove = { phrase -> scope.launch { settingsRepo.setStopPhrases(phrases - phrase) } },
                    onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) },
                )
            }

            Route.SPEECH_LANGUAGE -> {
                val locale by settingsRepo.speechLocale.collectAsStateWithLifecycle()
                SpeechLanguageScreen(
                    current = locale,
                    onPick = { tag -> scope.launch { settingsRepo.setSpeechLocale(tag) } },
                    onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) },
                )
            }

            Route.REPLY_LANGUAGE -> {
                val language by settingsRepo.replyLanguage.collectAsStateWithLifecycle()
                ReplyLanguageScreen(
                    current = language,
                    onPick = { code -> scope.launch { settingsRepo.setReplyLanguage(code) } },
                    onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) },
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
                    onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
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

                    editor == null ->
                        // **La charge utile de cette branche n'est pas sauvegardable** (`contextEditor`
                        // est un `remember` : un `Triple` ne va pas dans un `Bundle`). Après une mort
                        // du processus, on revient donc sur cette route **sans** son éditeur — et le
                        // repli rendait les **Réglages**, alors que la page quittée est celle que
                        // `contextReturn` porte (l'éditeur du bot). Le retour ne doit pas atterrir
                        // ailleurs qu'où l'on était : on rend la page quittée, comme les autres
                        // écrans à état vide (`navFromEmpty`, la route vide n'entre pas dans la pile).
                        LaunchedEffect(Unit) { navFromEmpty(contextReturn) }

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
                            // **Un niveau à la fois, comme la branche fichier juste au-dessus.** Le
                            // retour était écrit `navTo(contextReturn)` : il ne remontait pas la pile
                            // d'abord, et il ne dépilait `contextReturn` que tant que celle-ci se
                            // trouvait **au sommet**. Dès que l'écran est atteint d'ailleurs (ou après
                            // une restauration), il **empile** la page quittée au lieu de la rendre —
                            // le défaut que `RetourCoherenceTest` a déjà retiré de cinq autres écrans.
                            onBack = { if (!navBack()) navTo(contextReturn) },
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
                    // Tuile du **volet** (« Fichiers du serveur ») comme ligne des Réglages : le
                    // retour rend la page quittée (le fil) — il ne saute pas aux Réglages.
                    onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
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
                        onBack = { if (!navBack()) navTo(Route.FILES.name) },
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
                    onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
                    onOpenLogs = { navTo(Route.LOGS.name) },
                )
            }

            Route.LOGS -> {
                val logsViewModel: LogsViewModel = viewModel(
                    key = "logs-" + state.server.id + state.server.configSignature,
                    factory = viewModelFactory {
                        initializer {
                            // Sans Dashboard joignable, la page s'ouvre sur le journal **vocal**,
                            // qui est local à l'appareil — les trois journaux du serveur, eux,
                            // demandent la Passerelle.
                            LogsViewModel(
                                config = state.server,
                                localOnly = !state.server.canReachGateway,
                            )
                        }
                    },
                )
                LogsScreen(
                    viewModel = logsViewModel,
                    // Le parent logique est **d'où l'on vient** : la Passerelle quand on y était,
                    // les Réglages sinon (la ligne « Journaux » y est, sans cadenas).
                    onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
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
                pureBlack = pureBlack,
                halo = halo,
                fluideCover = fluideCover,
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
                onPureBlack = settingsViewModel::setPureBlack,
                onHalo = settingsViewModel::setHalo,
                onFluideCover = settingsViewModel::setFluideCover,
                onPickVoiceOrb = settingsViewModel::setVoiceOrb,
                onPickOrbLight = settingsViewModel::setOrbLight,
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            Route.MODELS -> {
                val effort by settingsRepo.reasoningEffort.collectAsStateWithLifecycle()
                ModelsScreen(
                server = state.server,
                reasoningEffort = effort,
                onPickReasoning = { value -> scope.launch { settingsRepo.setReasoningEffort(value) } },
                onPick = { model ->
                    // Le modèle vit sur le serveur : on l'y réécrit, et le dépôt le persiste.
                    settingsViewModel.open(state.server)
                    settingsViewModel.update { it.copy(model = model) }
                    settingsViewModel.saveAndClose()
                    navTo(Route.CHAT.name)
                },
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                serverSessionId = state.serverSessionId,
                // Le modèle **en force** de la conversation ouverte, pour cocher la bonne entrée
                // (et non le modèle configuré) quand un repli est à l'œuvre.
                inForceModel = state.modelInForce,
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
                // Rien à échanger dessous : le chat est déjà ouvert. La surface se retire à la
                // fin de son effacement (le `clearing` d'`OpeningCover.run` d'la référence) — l'effacer
                // dès l'échange coupait le fondu et laissait la couverture disparaître d'un coup.
                onFinished = { openingRect = null },
            )
        }

        // La **feuille de compte du volet** : l'état **mesuré** de la passerelle et les cinq gestes
        // de la connexion. Elle est posée par-dessus le volet, qui reste ouvert derrière ; un appui
        // à côté, un glissement vers le bas ou le retour matériel la referment, et le retour ferme
        // **la feuille** avant tout le reste (elle vit dans sa `Dialog`).
        if (accountSheetOpen) {
            AccountSheet(
                address = state.server.serveBaseUrl.ifBlank { state.server.baseUrl },
                server = state.server,
                onDismiss = { accountSheetOpen = false },
                onOpenSettings = { navTo(Route.SETTINGS.name) },
                onRefreshConversations = {
                    // La liste est **relue pour de vrai** — le roster et les conversations —, et le
                    // compte rendu est celui du dépôt **après** la relecture, jamais celui d'avant.
                    botsViewModel.refresh()
                    conversationRepo.load()
                    conversationRepo.conversations.value.size
                },
                // « Se déconnecter », **après** la confirmation : l'écran de configuration, la
                // même destination que « Configurer Onyx ». Rien n'est supprimé du serveur.
                onDisconnect = { navTo(Route.SETUP.name) },
                // « Changer ou ajouter une passerelle » : les serveurs enregistrés, leur bascule,
                // et l'ajout.
                onChangeGateway = { navTo(Route.SERVERS.name) },
            )
        }

        // « Let bots message each other? » : la question d'abord, et l'écriture ensuite — la même
        // `confirmationDialog` qu'la référence, avec ses deux issues.
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

        // « Repartir de zéro » : la question d'abord, parce que l'entrée ne dit pas d'elle-même
        // que **l'ancienne reste dans l'historique** — c'est justement ce qui la distingue d'une
        // suppression, et la phrase le dit.
        if (confirmingFresh) {
            OnyxAlert(
                title = "Start fresh?",
                message = "A new conversation starts with the same bot, with a fresh context. The one you are in right now stays in your history, whole.",
                actions = listOf(
                    AlertAction("Start fresh") {
                        confirmingFresh = false
                        startFreshConversation()
                    },
                    AlertAction("Cancel") { confirmingFresh = false },
                ),
                onDismiss = { confirmingFresh = false },
            )
        }

        // La gestion des projets — créer, renommer, retirer —, **une seule fois** pour la liste et
        // pour l'écran d'un projet : les feuilles vivent ici, et l'appel part d'ici.
        com.onyx.app.ui.conversations.ProjectDialogs(
            creating = projectCreating,
            onCreatingChange = { projectCreating = it },
            renaming = projectRenaming,
            onRenamingChange = { projectRenaming = it },
            removing = projectRemoving,
            onRemovingChange = { projectRemoving = it },
            problem = projectProblem,
            onProblemChange = { projectProblem = it },
            onCreate = { name ->
                runProjectAction { com.onyx.app.ui.conversations.ProjectManage.create(state.server, name) }
            },
            onRename = { project, name ->
                // Le nom change : l'écran ouvert, s'il est celui-là, garde son titre juste.
                if (selectedProject?.id == project.id) selectedProject = project.copy(label = name)
                runProjectAction {
                    com.onyx.app.ui.conversations.ProjectManage.rename(state.server, project.id, name)
                }
            },
            onRemove = { project ->
                // Retirer le projet **ouvert** ramène à la liste ; le dossier et les conversations,
                // eux, restent sur le serveur.
                if (selectedProject?.id == project.id) {
                    selectedProject = null
                    if (!navBack()) navTo(Route.CHAT.name)
                }
                runProjectAction {
                    com.onyx.app.ui.conversations.ProjectManage.remove(state.server, project.id)
                }
            },
        )

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
}

/**
 * Le niveau d'une **parole simulée** pour la maquette vocale (`onyx.voiceDemo`).
 *
 * L'émulateur n'a pas de micro : sans ce niveau, la pose « à l'écoute » montrerait une vague au
 * repos — sept points à la même hauteur — et rien ne prouverait qu'elle suit la voix. Le signal
 * suit des **syllabes** (montée vive, retombée douce) entrecoupées de respirations, à la manière
 * d'une phrase. Il n'est lu que sous le drapeau de debug : hors de la maquette, la vague prend le
 * niveau réel du moteur (`speech.level`).
 */
@Composable
private fun rememberVoiceDemoLevel(on: Boolean): State<Float> {
    val level = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(on) {
        if (!on) {
            level.floatValue = 0f
            return@LaunchedEffect
        }
        val started = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val t = (now - started) / 1_000_000_000.0
                // ~2,4 syllabes par seconde, une phrase de 3,4 s puis une respiration.
                val syllable = 0.5 + 0.5 * kotlin.math.sin(t * 2.4 * 2.0 * Math.PI)
                val phrase = if (t % 4.6 < 3.4) 1.0 else 0.10
                level.floatValue = (0.10 + 0.80 * syllable * phrase).toFloat()
            }
        }
    }
    return level
}
