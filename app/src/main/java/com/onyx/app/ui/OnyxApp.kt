package com.onyx.app.ui
import com.onyx.app.data.net.humanFailure
import com.onyx.app.ui.design.LocalBackdrop
import com.onyx.app.ui.design.LocalOverlayHost
import com.onyx.app.ui.design.OverlayHost
import com.onyx.app.ui.design.recordBackdrop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.derivedStateOf
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
import com.onyx.app.ui.browser.BrowserScreen
import com.onyx.app.ui.browser.BrowserScreenViewModel
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
import com.onyx.app.ui.bots.withFaces
import com.onyx.app.ui.bots.BotsScreen
import com.onyx.app.ui.bots.BotsViewModel
import com.onyx.app.ui.bots.BotRosterStandIn
import com.onyx.app.ui.bots.botChatToOpen
import com.onyx.app.ui.bots.BotMenuHandlers
import com.onyx.app.ui.bots.BotTemplateImportOutcome
import com.onyx.app.ui.bots.BotTemplateImportSheet
import com.onyx.app.ui.bots.BotTemplateOfferOutcome
import com.onyx.app.ui.bots.BotTemplateShareSheet
import com.onyx.app.ui.bots.botRecentConversation
import com.onyx.app.ui.bots.botOfConversation
import com.onyx.app.ui.bots.botOfConversationOrRemembered
import com.onyx.app.ui.bots.conversationProfile
import com.onyx.app.ui.bots.freshConversationForPencil
import com.onyx.app.ui.bots.BotEditorViewModel
import com.onyx.app.ui.bots.BotVoicePickerScreen
import com.onyx.app.ui.bots.GroupChatScreen
import com.onyx.app.ui.bots.GroupChatViewModel
import com.onyx.app.ui.bots.GroupRoomScreen
import com.onyx.app.ui.bots.GroupRoomViewModel
import com.onyx.app.ui.bots.roomChat
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
import com.onyx.app.data.repo.SettingsRepository
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
import com.onyx.app.ui.capabilities.SkillEditorSeed
import com.onyx.app.ui.vault.VaultScreen
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

/** The app's screens; navigation holds in a single state, like a `NavigationStack`. */
private enum class Route {
    CHAT, SETTINGS, APPEARANCE, MODELS, VOICE,
    GATEWAY, LOGS, FILES, FILE_EDITOR, CONTEXT, MEMORY, CONTEXT_EDITOR, VOICE_SETTINGS, NOTIFICATIONS, KOKORO_VOICES, CRON, KANBAN, CODE,
    COMPOSER_EDITOR, PHONE_TOOLS, CALLS, LOCK, CONNECT, BOTS, BOT_EDITOR, BOT_FACE, BOT_SCHEDULES, BOT_VOICE, GROUP_CHAT, GROUP_VOICE, ACKNOWLEDGEMENTS, SPEECH_LANGUAGE, REPLY_LANGUAGE,
    COMPUTER,
    STOP_PHRASES, SERVERS, SETUP, SPOKEN_PREFIXES, SPOKEN_PREFIX_EDITOR, PROFILES, WHATS_NEW, PATCH, SELECT_TEXT, PHOTO, PDF, VIDEO, SKILL_EDITOR, TOOLS, API_BOT, CAMERA, SUBAGENT, SUBAGENTS, TERMINAL, TERMINAL_SETTINGS,
    // Les destinations de l'onglet **Fichiers** qui n'avaient pas de lecteur : un son (le lecteur
    // de la maison), un document texte, et le contenu d'une archive.
    AUDIO, DOCUMENT, ARCHIVE,
    // "Quick capture": a passing note, filed by the agent.
    QUICK_CAPTURE,
    // The four destinations of the reference that were missing: `ConnectionDetailsView`, `HiddenBotsView`,
    // `SessionUsageView` et `ProjectSessionsView`.
    CONNECTION_DETAILS, HIDDEN_BOTS, SESSION_USAGE, PROJECT_SESSIONS,
    // The two screens of this batch: "Capabilities" (skills, tools, MCP servers) and
    // "Insights" (cost, sessions, tokens, cache, tokens per day, models). They are the two
    // destinations of the sidebar's "Capabilities" and "Insights" tiles.
    CAPABILITIES, INSIGHTS,
    // **"The Vault"** — a profile's secrets: listing them, writing one, revoking one. The
    // route is only opened by the "Secrets ›" row of "Capabilities", itself drawn
    // only when the probe has proven that the server serves the `onyx-vault` plugin.
    VAULT,
    /** The agent's browser, live, and its onboarding. */
    BROWSER_SCREEN,
    // **"Archives"** — the destination of the reference's *Archived* tile, the one that was
    // missing: the archived conversations of this device (their state already existed). The sidebar
    // carries it like its other tiles (see `SidebarDestinations.referenceTiles`).
    ARCHIVED,
    // **"Agent ledger"**: what the agent did, with what, for how long, and what that
    // cost — for the open conversation, or for a bot in aggregate. Opened by the thread's
    // "…" menu (see the "Agent ledger" entry of the conversation menu).
    LEDGER,
}

/** The sidebar's **tiles**: the app's screens, two per row. Each label opens a
 * screen that **really** exists here — the correspondence with the reference's layout
 * (Scheduled, Skills, Tools, Settings) is noted in the batch report.
 * None of this serves outside a debug APK.
 */
private const val EXTRA_VOICE_DEMO = "onyx.voiceDemo"

/**
 * The stand-in of a room's voice scene (`-e onyx.groupVoiceDemo speaking`): the line that
 * is read is **injected**, without server or microphone, like the reference's flag. Debug only.
 */
private const val EXTRA_GROUP_VOICE_DEMO = "onyx.groupVoiceDemo"

/**
 * The QA stand-in of the bot roster (`am start -e onyx.demoBots true`): the Android counterpart of the
 * reference's flag. It only sets the list of `@` mentions (and the open
 * conversation's identity), never a setting: without the extra, the roster is the server's.
 */
private const val EXTRA_DEMO_BOTS = "onyx.demoBots"

/** The stand-in words: the reference hook's default sentence, word for word. */
private const val VOICE_DEMO_WORDS = "Okay, prune anything older than thirty days and try the media share again"

/** The conversation title, truncated at 170 points like `soloHeader` does. */
private const val VOICE_DEMO_TITLE = "What's on my calendar tomorrow?"

/**
 * The **pose** of the voice scene for a capture (`-e onyx.voicePhase thinking`): an emulator has
 * neither microphone nor recognition engine, so the error states and the phases cannot be
 * produced for real. The flag poses them, as `onyx.voiceDemo` already poses listening — and it
 * changes **nothing** when it is absent. Debug only, like the others.
 */
private const val EXTRA_VOICE_PHASE = "onyx.voicePhase"

/** An engine refusal banner, set for a capture (`-e onyx.voiceProblem "…"`). Debug. */
private const val EXTRA_VOICE_PROBLEM = "onyx.voiceProblem"

/**
 * The "nothing is heard" alert set for a capture (`--ez onyx.voiceNoSound true`): an
 * emulator has no microphone, so the state cannot be produced for real. The flag
 * changes **nothing** when it is absent. Debug only, like the others.
 */
private const val EXTRA_VOICE_NO_SOUND = "onyx.voiceNoSound"

/**
 * The voice pipeline replayed without a microphone** (`am start -e onyx.voiceScenario phrase`).
 *
 * The emulator has no microphone: the full loop (opening listening, detecting the end of
 * speech, sending the question, thinking, reply, resuming listening, stops, failure cap)
 * thus could not be proven — the only thing seen there was an engine starting.
 * The flag sets a **French-WAV-fed engine** ([VoiceScenarioDemo]) and a test
 * player, and wires the reply **locally** (no server on an emulator).
 *
 * Three scenarios: `phrase` (the full journey), `silencieux` (the engine runs without hearing
 * anything — the honesty layer must say so) and `echec` (failure cap, message, retry).
 * Debug only**; without the flag, none of this is instantiated.
 */
private const val EXTRA_VOICE_SCENARIO = "onyx.voiceScenario"

/**
 * The QA stand-in for subagents (`am start --ez onyx.subagentDemo true`): an **injected**
 * thread and roster (a task working, one starting, one launched blind, one
 * finished, one failed), for lack of Dashboard and agent on an emulator. Debug only; without the
 * flag, nothing is injected.
 */
private const val EXTRA_SUBAGENT_DEMO = "onyx.subagentDemo"

/**
 * The QA stand-in for the **"/" menu** (`am start --ez onyx.slashDemo true`): a list of
 * commands **set**, for lack of gateway on an emulator — without a server, `complete.slash` returns
 * nothing and the menu is neither visible nor measurable. The entries follow the real shape: app
 * commands (short name, short detail) and server skills (long name, detail that
 * runs over one line). Debug only; without the flag, the menu is the server's.
 */
private const val EXTRA_SLASH_DEMO = "onyx.slashDemo"

/**
 * The QA stand-in for **scrolling** (`am start --ez onyx.threadDemo true`): a long, reproducible
 * thread (Markdown prose, lists, code, six tool steps per reply) to read frame
 * times instead of an impression — `dumpsys gfxinfo`. Debug only.
 */
private const val EXTRA_THREAD_DEMO = "onyx.threadDemo"

/**
 * The QA stand-in for the **agent ledger** (`am start -e onyx.ledgerDemo chargee`): a thread, a
 * session registry and a price table **set**, for lack of Dashboard and agent on an
 * emulator. Four named cases — `fraiche` (new conversation), `chargee` (loaded conversation),
 * `sans-prix` (a bot whose provider publishes no price) and `manquante` (what the transport
 * did not keep). The set values are those observed on the real server: the stand-in only
 * serves to show them without a server. Without the flag, nothing is set and the screen reads the server.
 */
private const val EXTRA_LEDGER_DEMO = "onyx.ledgerDemo"

/**
 * The **device's voice journal, copied into logcat** (`am start --ez onyx.voiceJournal true`).
 *
 * The app's Logs page is only reachable with a reachable Dashboard; on an emulator that
 * has none, the voice path's lines (question sent, bot taking the floor, turn finished,
 * send failure) thus remain invisible — and it is they that say what breaks. The flag
 * copies them into logcat, line by line. **Debug only**: in a release APK,
 * `BuildConfig.DEBUG` is false, nothing is copied, and the flag changes nothing.
 */
private const val EXTRA_VOICE_JOURNAL = "onyx.voiceJournal"

/**
 * The voice pipeline stand-in wired to a **real room** (`--ez onyx.voiceScenarioRoom true`).
 *
 * Without this flag, `onyx.voiceScenario` opens the **solo** scene and answers itself: that is what
 * the solo loop needs, and precisely what prevented exercising a room — the
 * question was asked, but to nobody. With the flag, the stand-in **opens nothing**: the person
 * navigates, opens their room and taps "Talk" ("Parler"), and it is the room's thread that receives the recognized
 * question (`groups.send`). Debug only, and without it nothing changes.
 */
private const val EXTRA_VOICE_SCENARIO_ROOM = "onyx.voiceScenarioRoom"

/** The line of the "TALKING" ("PARLE") pose: the owner's capture sentence, word for word. */
private const val VOICE_DEMO_REPLY = "Ça va bien, merci. Prêt à bosser. Toi, tu veux qu'on attaque quoi ?"

/** The phase requested by [EXTRA_VOICE_PHASE], or `null` if the flag is absent. */
private fun posedVoicePhase(raw: String?): VoicePhase? = when (raw?.trim()?.lowercase()) {
    "idle", "pret", "prêt", "ready" -> VoicePhase.IDLE
    "listening", "ecoute", "écoute" -> VoicePhase.LISTENING
    "thinking", "reflechit", "réfléchit" -> VoicePhase.THINKING
    "speaking", "parle" -> VoicePhase.SPEAKING
    else -> null
}

/** The Activity behind a composition `Context`: its `Intent`s carry the shop stand-in. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

/** Routes presented by `.sheet` / `.fullScreenCover` in the corresponding reference views. */
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
// (voice), the question refused by the room, the room that went mute without
// saying anything, a message still in transit. Never a mute screen.
private val TwoPaneRoutes = setOf(Route.CHAT, Route.CRON, Route.KANBAN, Route.CODE, Route.BOTS)

/** Presentation transitions; the native springs of sheets/segments remain approximated. */
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
        // **A section change no longer slides the page.** The sidebar opens the requested
        // screen: the page appears in place, with a short fade. The lateral slide read
        // as "the page that moves" on every tap of a tile or a shortcut.
        return fadeIn(animationSpec = OnyxMotion.Sites.TabSelection)
            .togetherWith(fadeOut(animationSpec = OnyxMotion.Sites.TabSelection))
    }
    return EnterTransition.None.togetherWith(ExitTransition.None)
}

/**
 * The root: the user's theme, then the conversation and the screens it opens.
 *
 * The theme and appearance are read here, so that a change in Settings → Appearance
 * applies to the whole app immediately.
 */
@Composable
fun OnyxApp() {
    val context = LocalContext.current
    val servers = AppContainer.servers
    val settingsRepo = AppContainer.settings
    val conversationRepo = AppContainer.conversations

    // **The host of the main window's layers.** The anchored menu lives there: it is thus drawn
    // in the **main window** — where the screen's blur source exists — and not in a
    // `Dialog`, which can sample nothing. That is what makes the **glass** menu panel
    // (the real content underneath, blurred and curved) and not a wash.
    val overlayHost = remember { OverlayHost() }

    // **A conversation's identity, PER CONVERSATION**: the **profile name of its bot** — the
    // household's unique key for resolving a face (`BotLook`). It is `conversationProfile`
    // (conversation → bot → server profile → `default`) that renders it, **the same computation as
    // the header and the tabs**. The previous version resolved it **per server** (a table
    // server id → profile), so that three conversations of different bots on the same server
    // showed the **same** face drawn from the name — the owner's "bad sync" defect.
    // (Wiring read below, in `chatSidebar`: `identityOf = { conversation -> … }`.)

    val theme by settingsRepo.theme.collectAsStateWithLifecycle()
    val appearance by settingsRepo.appearance.collectAsStateWithLifecycle()
    val themeColors by settingsRepo.themeColors.collectAsStateWithLifecycle()
    val displayName by settingsRepo.displayName.collectAsStateWithLifecycle()
    val autoCards by settingsRepo.autoCardLook.collectAsStateWithLifecycle()
    val autoVoice by settingsRepo.autoVoiceLook.collectAsStateWithLifecycle()
    // // and the voice orb (all carried by `Settings.shared` on the reference side).
    // and the voice orb (all carried by `Settings.shared` on the reference side).
    val botFaces by settingsRepo.botFaces.collectAsStateWithLifecycle()
    val botPictures by settingsRepo.botPictures.collectAsStateWithLifecycle()
    val botColors by settingsRepo.botColors.collectAsStateWithLifecycle()
    val linkPreviews by settingsRepo.linkPreviews.collectAsStateWithLifecycle()
    val chatTextSize by settingsRepo.chatTextSize.collectAsStateWithLifecycle()
    // "Pure black" ("Noir pur") and "Halo": the two display settings carried by the Appearance card.
    val pureBlack by settingsRepo.pureBlack.collectAsStateWithLifecycle()
    val halo by settingsRepo.halo.collectAsStateWithLifecycle()
    // "Fluid": the animated cover of conversation cards (variant B), true by default.
    val fluideCover by settingsRepo.fluideCover.collectAsStateWithLifecycle()
    // **The cover choice**: variant, seed, tint — written to preferences (filaments,
    // graine 3417634283, teinte bleu) et relus ici. C'est lui qui porte la graine du dessin.
    val fluideChoix by settingsRepo.fluideChoix.collectAsStateWithLifecycle()
    val voiceOrb by settingsRepo.voiceOrb.collectAsStateWithLifecycle()
    val orbLight by settingsRepo.orbLight.collectAsStateWithLifecycle()
    // // here like the other interface settings (Appearance, Voice…).
    // here like the other interface settings (Appearance, Voice…).
    val sidebarCollapsed by settingsRepo.sidebarCollapsed.collectAsStateWithLifecycle()
    // "Halo": does the glass react to the finger? A single switch turns off the **non-luminous** press —
    // // The Settings screen that shows it is carried by another batch; here we **read** the key and
    // The Settings screen that shows it is carried by another batch; here we **read** the key and
    // set it, nothing more.
    val glassHalo by settingsRepo.glassHalo.collectAsStateWithLifecycle()
    // // phone overlay and the tablet's docked panel each have their own, so that
    // // opening one does not leave the other open.
    // opening one does not leave the other open.
    val sidebarState = com.onyx.app.ui.sidebar.rememberOnyxSidebarState()
    // The app's language — the same storage as the System / Français / English setting. An
    // "all-in-one" link can set it as early as configuration. It is set **before drawing**, and the whole
    // tree is remounted under `key(appLanguage)`: a language change is seen right away.
    val appLanguage by settingsRepo.language.collectAsStateWithLifecycle()

    // Bot pictures arrive once the roster is read: the state is held here so that the
    // faces of the whole app see it, wherever they are drawn.
    var botPicturesByName by remember { mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }

    CompositionLocalProvider(
        com.onyx.app.ui.design.LocalBotFaces provides botFaces,
        com.onyx.app.ui.design.LocalBotPictures provides if (botPictures) botPicturesByName else emptyMap(),
        // The "Halo" setting: set **above** the whole tree, so read by every pane. Switched off,
        // the surface is inert to the finger — no light, no press (see `GlassTouch.kt`).
        com.onyx.app.ui.design.LocalGlassHalo provides glassHalo,
        // "Fluid": set above the whole tree, so read by every conversation card.
        // Switched off, the card falls back to the old deterministic cover.
        com.onyx.app.ui.design.LocalFluideEnabled provides fluideCover,
        // **The cover choice**: variant, seed and tint, set above the whole
        // tree — that is what makes a card draw the chosen material (filaments, seed from
        // the board, blue tint) and no longer a tint drawn from the conversation.
        com.onyx.app.ui.design.LocalFluideChoix provides fluideChoix,
    ) {
    androidx.compose.runtime.key(appLanguage) {
    OnyxAppTheme(theme = theme, appearance = appearance, colors = themeColors[theme.id], pureBlack = pureBlack, halo = halo) {
        // The lock covers everything: nothing of the app is seen before authenticating.
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

        // //
        // // `state` was the **entire** state, and it was republished on every text publication - 33
        // // times per second during a reply. The root (5,000 lines) thus replayed on every
        // // token, with three full sweeps of the thread (subagents, approvals, agent
        // // questions) that the text being written does not change. `ShellState` (see its
        // // file) carries only what the shell actually reads, and its structural equality is
        // // stable from one token to the next: **the root no longer recomposes during a stream.**
        // stable d'un jeton a l'autre : **la racine ne recompose plus pendant un flux.**
        val chatState = chatViewModel.state.collectAsStateWithLifecycle()
        val state by remember { derivedStateOf { ShellState.of(chatState.value) } }
        val history by chatViewModel.history.collectAsStateWithLifecycle()
        // La raison d'un import de conversations qui n'a pas abouti, quand il y en a une.
        val serverImportNote by chatViewModel.importNote.collectAsStateWithLifecycle()
        // The registered servers, read back for the unified list ("All Servers").
        val serverList by servers.servers.collectAsStateWithLifecycle()

        // This phone's network addresses, read once: what the "Simple" mode offers
        // // recomposition would serve no purpose and would be costly.
        // would serve no purpose and would be costly.
        // //
        // // `localAddresses()` walks the phone's network interfaces: a cost paid at **every**
        // // launch, on the main thread, for a suggestion that only the connection screens
        // launch, on the main thread, for a suggestion that only the connection screens
        // show. The value is thus computed on the first opening of one of them, then kept.
        val setupLocalAddresses = remember {
            lazy(LazyThreadSafetyMode.NONE) { com.onyx.app.ui.setup.SetupSuggestions.localAddresses() }
        }

        // // name, the placeholder) and the notifications, which name the bot in its thread.
        // name, the placeholder) and the notifications, which name the bot in its thread.
        val botsViewModel: BotsViewModel = viewModel(
            key = "bots-" + state.server.id + state.server.configSignature,
            factory = viewModelFactory {
                initializer {
                    BotsViewModel(
                        config = state.server,
                        faces = BotFaceStore(context.applicationContext),
                        activity = BotActivityStore(context.applicationContext),
                        groupRooms = com.onyx.app.data.local.GroupRoomStore(context.applicationContext),
                        // The last successful roster: it holds the first frame, before the network.
                        roster = com.onyx.app.data.local.BotRosterCache(context.applicationContext),
                    )
                }
            },
        )
        val botsState by botsViewModel.state.collectAsStateWithLifecycle()
        // QA stand-in (`am start -e onyx.demoBots true`): the emulator that captures has no
        // Dashboard, thus no roster to read, and the list of `@` mentions needs bots to
        // show itself. The reference has the same hook (`-echo.demoBots`), and the stand-in only takes the
        // server's place on this explicit request.
        val demoBotsRun = remember {
            context.findActivity()?.intent?.getBooleanExtra(EXTRA_DEMO_BOTS, false) == true
        }
        val bots = if (demoBotsRun) BotRosterStandIn.bots() else botsState.bots
        // // hidden bots set aside, none when the main one is alone.
        // hidden bots set aside, none when the main one is alone.
        val mentionRoster = remember(bots, state.server.displayName) {
            BotMentions.rosterForMentions(bots, state.server.displayName.ifBlank { "Onyx" })
        }
        // The face of a profile, resolved **once** and read by the list as by a room: the
        // roster table (the phone's choice then `ui_meta.onyx.face`, gathered by
        // `withSwiftFaces`) and the faces the bots already carry — a demonstration stand-in
        // included. A room that read the local choice alone showed a different face
        // from the list for the same bot.
        val faceResolver = remember(bots, botsState.faces) {
            com.onyx.app.ui.bots.BotFaceResolver.of(
                resolved = bots.mapNotNull { bot -> bot.faceCode?.let { code -> bot.name to code } }.toMap() +
                    botsState.faces,
            )
        }
        // The name of who speaks in the open conversation: the bot's label when it is one,
        // otherwise the agent name from Settings (or the server's when it is empty).
        val openConversation = history.firstOrNull { it.id == state.conversationId }
        val openConversationBot = openConversation?.let { conversation ->
            botOfConversation(conversation, bots, state.server.displayName)
        }
        val threadName = openConversationBot?.label(state.server.displayName)
            ?: state.server.displayName.ifBlank { "Onyx" }

        // The loop breaker: the "restoration in progress" flag is set here, before the
        // route, tab and draft are restored, and cleared as soon as the first drawing has
        // passed. A startup that finds it already set is a **fallback** startup: Chat,
        // draft without its attachment, forgotten tab — thus never the same crash twice.
        val startupGuard = remember { com.onyx.app.ui.StartupGuard.with(context.applicationContext) }
        val startPlan = remember { startupGuard.begin() }

        var route by rememberSaveable { mutableStateOf(Route.CHAT.name) }
    // Navigation stack: the Android back gesture (screen edge) and the button must
    // go back up to the previous screen, like the reference's swipe-back — and not close the app. `route`
    // remains the current value for all existing code; assignments go through `navTo`.
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
     * Leaves a screen whose state is empty. The empty route **does not enter** the stack: returning to it
     * n'aurait aucun sens (sa charge utile n'existe plus), et l'y laisser avalait le retour — voir
     * [navigateLeavingEmpty]. À appeler **hors de la composition** (`LaunchedEffect`) : un `navTo`
     * written during composition is a state write during composition.
     */
    fun navFromEmpty(target: String) {
        route = navigateLeavingEmpty(route, target, backStack)
    }

    /**
     * The startup itself: as a fallback (the previous one did not reach its first drawing), we
     * return to Chat and set aside the draft attachment — then clear the flag as soon as
     * le premier dessin, pour que le lancement suivant soit normal.
     */
    LaunchedEffect(Unit) {
        if (startPlan.dropDraftAttachments) {
            // We **fall back** onto Chat without stacking it: going back from this Chat must
            // not return to the screen that killed the previous launch.
            route = navigateLeavingEmpty(route, Route.CHAT.name, backStack)
            chatViewModel.discardDraftAttachments()
        }
        // A successful drawing, then a short margin: an image refused by the rendering engine
        // raises at the first or second drawing, and the flag must still be there so that the
        // next launch knows it must go back.
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
        // The page we come from, so that "back" returns to the right place.
        var contextReturn by rememberSaveable { mutableStateOf(Route.CONTEXT.name) }
        var selectedBot by remember { mutableStateOf<BotProfile?>(null) }
        // **The thread whose face is being changed.** Is the bot identity opened **from a
        // // alone changes. From the "Bots" tab, it is the bot's face — the one followed by its
        // alone changes. From the "Bots" tab, it is the bot's face — the one followed by its
        // fils qui n'ont pas le leur. `null` = on est venu de la liste des bots.
        var faceConversation by remember { mutableStateOf<Conversation?>(null) }
        // The bot **being created**: its name and its face. "Change Face" on the
        // "New Bot" screen had nowhere to put its choice — the face screen opens on a bot
        // // nothing, and the template's face remained the only possible one.
        // nothing, and the template's face remained the only possible one.
        var newBotName by rememberSaveable { mutableStateOf<String?>(null) }
        var newBotFaceCode by rememberSaveable { mutableStateOf<String?>(null) }
        // The room chosen in the bot list: it tints its row and pre-fills the group
        // chat with its members (a server room's thread is not yet carried).
        var openedRoom by remember { mutableStateOf<GroupChat?>(null) }
        var botCronProfile by rememberSaveable { mutableStateOf<String?>(null) }
        // The bot whose voice is being set (`BotVoicePicker`), openable from its editor as from
        // its tile menu: the profile is kept so that a recreated process finds the right one.
        var botVoiceProfile by rememberSaveable { mutableStateOf<String?>(null) }
        var renaming by remember { mutableStateOf(false) }
        // "Let bots message each other": the question first, like the reference's confirmationDialog.
        var askingLetBotsMessage by remember { mutableStateOf(false) }
        // What the "let bots talk to each other" setting gave: without it, the press showed
        // nothing at all, neither success nor failure ("it doesn't work").
        var letBotsOutcome by remember { mutableStateOf<String?>(null) }
        var editingPrefix by remember { mutableStateOf<Int?>(null) }
        var patchText by remember { mutableStateOf<String?>(null) }
        var selectedText by remember { mutableStateOf<String?>(null) }
        var skillName by remember { mutableStateOf<String?>(null) }
        // A demonstration's draft, when "Make it a skill" opens the editor: the proposed
        // name and the converted Markdown. Null, the editor is today's, identically.
        var skillSeed by remember { mutableStateOf<SkillEditorSeed?>(null) }
        // **The routine proposed by a skill** ("Make it a routine"): set here, it
        // is consumed by the schedules screen, which opens its draft already filled. Null, nothing
        // changes for the other paths.
        var routineSeed by remember { mutableStateOf<com.onyx.app.data.skill.SkillRoutine.Seed?>(null) }
        // **A demonstration requested from the thread** ("Teach a skill" from the "+"): the
        // request survives navigation, the computer screen consumes it on arrival.
        var teachRequested by remember { mutableStateOf(false) }
        var openingRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var subagentAgent by remember { mutableStateOf<com.onyx.app.data.model.SubagentActivity?>(null) }
        // QA stand-in for subagents (`onyx.subagentDemo`): the injected roster and thread,
        // for lack of Dashboard and agent on an emulator. Without the flag, the list stays empty and
        // nothing is injected.
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
        // The **scrolling** stand-in: a long thread set once, to measure frames.
        val threadDemo = remember {
            val intent = context.findActivity()?.intent
            if (intent?.getBooleanExtra(EXTRA_THREAD_DEMO, false) == true) {
                // The tool-step count and presence are **adjustable**: that is what
                // allows comparing two threads on the same device (`--ei onyx.threadDemoCount 40`,
                // `--ez onyx.threadDemoPlain true`).
                intent.getIntExtra("onyx.threadDemoCount", com.onyx.app.ui.chat.ThreadStandIn.DefaultCount) to
                    !intent.getBooleanExtra("onyx.threadDemoPlain", false)
            } else {
                null
            }
        }
        LaunchedEffect(threadDemo) {
            val (count, withTools) = threadDemo ?: return@LaunchedEffect
            chatViewModel.seedSubagentDemo(com.onyx.app.ui.chat.ThreadStandIn.messages(count, withTools))
        }
        val shownSubagents = if (subagentDemoRun && demoSubagents.isNotEmpty()) demoSubagents else state.subagents
        // The **"/" menu** stand-in (`onyx.slashDemo`): the list the gateway would return,
        // set so it can be seen and measured on an emulator that has none.
        val slashDemoItems = remember {
            if (context.findActivity()?.intent?.getBooleanExtra(EXTRA_SLASH_DEMO, false) == true) {
                com.onyx.app.ui.chat.SlashStandIn.items
            } else {
                null
            }
        }
        // The agent ledger stand-in (`onyx.ledgerDemo <case>`): it brings the world the
        // screen normally reads on the server — the thread, the roster, the registry and the price
        // table. Absent, the screen reads the server; nothing else changes.
        val ledgerStandIn = remember {
            com.onyx.app.ui.ledger.LedgerStandIn.of(
                context.findActivity()?.intent?.getStringExtra(EXTRA_LEDGER_DEMO).orEmpty(),
            )
        }
        // The stand-in **opens its screen**: without Dashboard or agent, there is nothing else to set,
        // and the capture must be able to target the ledger without crossing the thread sheet (which,
        // for its part, is reached by the "…" menu of the header — that is the real path, and it stays whole).
        LaunchedEffect(ledgerStandIn) {
            if (ledgerStandIn != null) navTo(Route.LEDGER.name)
        }
        var botModels by remember(state.server.id) { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(state.server.id) {
            botModels = runCatching { com.onyx.app.data.net.ChatCompletionsClient(state.server).testConnection() }
                .getOrNull()?.models.orEmpty()
        }

        // ---- The model picker (feature 2): the sheet, its models, its preferences ----
        // The reasoning level already lives in Settings; the sheet reads it back as the
        // models screen does, a single state for both.
        val currentReasoning by settingsRepo.reasoningEffort.collectAsStateWithLifecycle()
        var showModelSheet by remember { mutableStateOf(false) }
        /**
         * The bot editor's models sheet.** It lives here, with the other sheets,
         * for two reasons: the catalogue is read in the effect that watches `showModelSheet` and
         * `roomModelBot` (adding this flag there is enough), and the sheet must be drawn
         * above** the identity screen, not inside it.
         */
        var editorModelSheet by remember { mutableStateOf(false) }
        // The sheet is open **from a room**: the name of the target bot. A room has no
        // conversation to a bot, so that without this name the sheet would talk about the server instead of
        // this bot; the choice is written on its profile (`profiles.configure`), and that is the model
        // qu'Hermes lui donne **dans la salle** comme dans ses propres conversations.
        var roomModelBot by remember { mutableStateOf<String?>(null) }
        var modelChoices by remember(state.server.id) { mutableStateOf<List<com.onyx.app.ui.design.ModelChoice>>(emptyList()) }
        var modelChoicesLoading by remember(state.server.id) { mutableStateOf(false) }
        // The Hermes profiles and their list state — the `default` profile's local ones, the
        // `kilo` profile's remote ones, and an unreachable profile marked without erasing the others.
        var modelProfiles by remember(state.server.id) { mutableStateOf<List<com.onyx.app.ui.design.ProfileView>>(emptyList()) }
        // Device favourites and recents: what belongs only to this phone, per server.
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
        // // transports; otherwise the sheet does not draw it (see `rememberModelFastMode`).
        // transports; otherwise the sheet does not draw it (see `rememberModelFastMode`).
        val modelFastMode = com.onyx.app.ui.models.rememberModelFastMode(
            state.server,
            state.serverSessionId ?: state.conversationId,
        )
        // // It is read **per profile** (the registry, then each profile's models): the sheet
        // // thus shows all profiles, not the gateway's single alias.
        // montre ainsi tous les profils, pas le seul alias de la passerelle.
        LaunchedEffect(showModelSheet, roomModelBot, editorModelSheet, state.server.id) {
            if (!showModelSheet && roomModelBot == null && !editorModelSheet) return@LaunchedEffect
            modelChoicesLoading = true
            val catalog = runCatching { com.onyx.app.data.net.ModelCatalog.fetchAll(state.server) }
                .getOrDefault(emptyList())
            modelChoices = catalog.flatMap { it.models }
            modelProfiles = catalog.map { com.onyx.app.ui.design.ProfileView(it.profile, it.state) }
            modelChoicesLoading = false
        }
        var photoSet by remember { mutableStateOf<List<com.onyx.app.data.model.Attachment>>(emptyList()) }
        var photoIndex by remember { mutableStateOf(0) }
        // The PDF shown in the app's reader: its address and its name.
        var pdfSource by remember { mutableStateOf<com.onyx.app.ui.chat.PdfSource?>(null) }
        // The video shown in the app's reader: its address and its name.
        var videoSource by remember { mutableStateOf<com.onyx.app.ui.chat.VideoSource?>(null) }
        // Le son, le document texte et l'archive ouverts depuis l'onglet Fichiers.
        var soundSource by remember { mutableStateOf<com.onyx.app.ui.chat.SoundSource?>(null) }
        var documentSource by remember { mutableStateOf<com.onyx.app.ui.chat.DocumentSource?>(null) }
        var archiveSource by remember { mutableStateOf<com.onyx.app.ui.chat.DocumentSource?>(null) }
        var prefixDraft by remember { mutableStateOf<SpokenPrefix?>(null) }
        // // The draft lives here so it is not lost when the camera takes the whole surface.
        // The draft lives here so it is not lost when the camera takes the whole surface.
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
        // The language requested by an "all-in-one" link, set at the **end** of configuration:
        // changing it while the fields are being filled would remount the screen and erase them.
        var pendingLanguage by remember { mutableStateOf("") }
        val spokenPrefixes by settingsRepo.spokenPrefixes.collectAsStateWithLifecycle()
        var renameText by remember { mutableStateOf("") }

        // The start cards follow the history without re-reading it on every recomposition.
        LaunchedEffect(history.size, state.messagesSize) {
            if (state.messagesSize == 0) {
                startCards = chatViewModel.startCards()
            }
        }

        val scope = rememberCoroutineScope()

        // "Start fresh": its confirmation's state, and the action's body itself —
        // written **once**, for the header's pencil, the "New Conversation" command and
        // the menu entry.
        var confirmingFresh by remember { mutableStateOf(false) }

        /** The **sidebar's account sheet**: the initial's circle at the foot, or the name and address
         * of the head, open it. It tells the **actually measured** state of the gateway and carries the
         * five connection gestures; it is a sheet of the existing family, set above
         * the sidebar (which stays open behind).
* the sidebar (which stays open behind).
         */
        var accountSheetOpen by remember { mutableStateOf(false) }

        /** Opens a new conversation with **the same bot**: the context starts anew, and
         * the old one stays in the history, whole. An empty conversation never served is
         * taken back instead of stacking a second one.
* taken back instead of stacking a second one.
         */
        fun startFreshConversation() {
            val next = freshConversationForPencil(
                current = openConversation,
                bot = openConversationBot,
                conversations = history,
                serverId = state.server.id,
                mainName = state.server.displayName,
            )
            // // under its name, and what the header reads back for its face.
            // under its name, and what the header reads back for its face.
            if (openConversationBot != null && history.none { it.id == next.id }) {
                scope.launch { conversationRepo.save(next) }
            }
            chatViewModel.open(next)
        }

        /** Opens an attached file with the reader that suits it: the app's PDF reader,
         * the image viewer, or the system app that can read the rest.
* the image viewer, or the system app that can read the rest.
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
                    // The video is played in the app, like the PDF and the image: its first frame serves
                    // as a thumbnail in the thread, and the tap opens the player.
                    videoSource = com.onyx.app.ui.chat.VideoSource(
                        uri = uri,
                        name = name.ifEmpty { uri.lastPathSegment?.substringAfterLast('/') ?: "Video" },
                    )
                    navTo(Route.VIDEO.name)
                }

                com.onyx.app.ui.chat.FileViewerKind.EXTERNAL -> {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            // // `file://`**: since Android 7, a `file://` handed to another app
                            // `file://`**: since Android 7, a `file://` handed to another app
                            // raises `FileUriExposedException` and the press does nothing. Attachments
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
                        chatViewModel.showProblem(humanFailure(error, OnyxLocalization["Couldn't open it."]))
                    }
                }
            }
        }

        /** A file of the **Files** tab, already on the phone, opened according to its **family**.
* A file of the **Files** tab, already on the phone, opened according to its **family**.
         * It is the only place that answers "what does a press do?" for this tab: an
         * image, a video, a sound, a PDF and a text each have their household reader, an
         * archive alignment shows its content, and all the rest — like an unknown
         * type — goes to the system app, which is warned. **No branch does
         * nothing**: that is what the wiring test verifies, family by family.
* nothing**: that is what the wiring test verifies, family by family.
         */
        fun openConversationFile(uri: android.net.Uri, name: String, mime: String) {
            when (com.onyx.app.data.model.fileOpenRoute(name, mime)) {
                // Readers already in place: the image, the video, the PDF and the system.
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
* What an "all-in-one" link applies **beyond** the server: the voices' address, the
* "Computer" bridge (address + token) and the app's language. An old link has none of
         * cela : la fonction ne fait alors rien.
         */
        suspend fun applySetupExtras(code: com.onyx.app.data.model.SetupCode) {
            if (code.voicesURL.isNotEmpty()) settingsRepo.setKokoroUrl(code.voicesURL)
            if (code.bridgeURL.isNotEmpty()) AppContainer.computer.address = code.bridgeURL
            if (code.bridgeToken.isNotEmpty()) AppContainer.computer.token = code.bridgeToken
            if (code.language.isNotEmpty()) pendingLanguage = code.language
        }

        /**
* What "Simple" mode applies after its discovery. The bridge token that the server
* announces is **the same secret** as the gateway's: the user only remembers one.
         *
* The language, for its part, is set aside: changing it right away would remount the
         * configuration sous `key(appLanguage)` et **effacerait les deux champs en cours**. Elle
* is thus applied at the end of configuration — at first launch, as the contract
         * demande.
         */
        suspend fun applyDiscovery(found: com.onyx.app.data.net.DiscoveredSetup) {
            if (found.voices.isNotEmpty()) settingsRepo.setKokoroUrl(found.voices)
            if (found.bridge.isNotEmpty()) AppContainer.computer.address = found.bridge
            if (found.bridgeToken.isNotEmpty()) AppContainer.computer.token = found.bridgeToken
            if (found.language.isNotEmpty()) pendingLanguage = found.language
        }

/** The language requested by a link, set when configuration is done. */
        fun applyPendingLanguage() {
            val language = pendingLanguage
            if (language.isEmpty()) return
            pendingLanguage = ""
            scope.launch { settingsRepo.setLanguage(language) }
        }

        // The microphone language and the hands-free mode come from Settings.
        // The language **given to the engine**: the person's choice, otherwise the interface's.
        val speechLocale by settingsRepo.speechLocaleResolved.collectAsStateWithLifecycle()
        val handsFreeByDefault by settingsRepo.handsFreeByDefault.collectAsStateWithLifecycle()
// "Recognize offline": false by default, and the **only** path to offline.
        val offlineRecognition by settingsRepo.offlineRecognition.collectAsStateWithLifecycle()
        val returnSends by settingsRepo.returnSends.collectAsStateWithLifecycle()

        val voiceSpeed by settingsRepo.voiceSpeed.collectAsStateWithLifecycle()
        val replyVoice by settingsRepo.replyVoice.collectAsStateWithLifecycle()
        val kokoroUrl by settingsRepo.kokoroUrl.collectAsStateWithLifecycle()
        val kokoroToken by settingsRepo.kokoroToken.collectAsStateWithLifecycle()
        val kokoroVoice by settingsRepo.kokoroVoice.collectAsStateWithLifecycle()
        val talkOver by settingsRepo.talkOver.collectAsStateWithLifecycle()
        val earpieceAtEar by settingsRepo.earpieceAtEar.collectAsStateWithLifecycle()

        // // audio file** takes the recognizer's place, and a test player takes the voice's
        // place — that is what makes the full loop replayable on an emulator without a microphone.
        // place — that is what makes the full loop replayable on an emulator without a microphone.
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
                // thinking → reply → resume), not an agent reply.
                // // thinking → reply → resume), not an agent's reply.
                // thinking → reply → resume), not an agent's reply.
                { _ ->
                    scope.launch {
                        delay(700)
                        voice.finishTurn(VoiceScenarioDemo.REPLY, speaker = openConversationBot?.name)
                    }
                }
            }
            // a reason to continue (the reference's `conversation.cancel()`).
            // a reason to continue (the reference's `conversation.cancel()`).
            voice.onStoppedByWord = { chatViewModel.stop() }
            onDispose { voice.release() }
        }

        // Playback speed and interruption follow Settings, without recreating the session.
        val stopPhrases by settingsRepo.stopPhrases.collectAsStateWithLifecycle()
        // // instruction sent to the model. Without this link, "Reply in French" on an English app
        // was read with an English voice — the reply and its reading no longer spoke to each other.
        // was read with an English voice — the reply and its reading no longer spoke to each other.
        val replyLanguage by settingsRepo.replyLanguage.collectAsStateWithLifecycle()
        // same room from reading in the same voice.
// same room from reading in the same voice.
        val botVoicesHere by settingsRepo.botVoices.collectAsStateWithLifecycle()

        LaunchedEffect(
            voice,
            voiceSpeed,
            talkOver,
            earpieceAtEar,
            stopPhrases,
            replyLanguage,
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
                // of the effect is the map itself, so a setting restarts playback.
                // key is the map itself, so a setting restarts playback.
                botVoices = settingsRepo.botVoicesHere(state.server.id),
            )
            voice.talkOver = talkOver
            // played there like a call (proximity sensor → earpiece).
            // played there like a call (proximity sensor → earpiece).
            voice.earRoutingEnabled = earpieceAtEar
            // `EXTRA_PREFER_OFFLINE` — it is **online** recognition that is the default, and
            // offline that is a wish. On, listening tries the device alone and switches
            // online by itself if the engine does not have the language.
            // online by itself if the engine does not have the language.
            voice.offlineRecognition = offlineRecognition
            voice.stopPhrases = stopPhrases
            // microphone language — thus the interface's — to decide: what is said and what
            // is read then speak the same language.
            // is read then speak the same language.
            voice.replyLanguageTag = SettingsRepository.replyLanguageTag(replyLanguage) ?: speechLocale
            // only for the QA stand-in: in a real conversation, every reply gave the
            // hand back ("READY"), and one had to **touch the bubble on every sentence**. Listening thus
            // resumes by itself — the stop phrases ("stop listening", "that's all") and the
            // red button remain the only exits, and `listenAfterSpeech` keeps deciding
            // case by case (a dictated reply, a turn with no follow-up, do not open the microphone).
            // case by case (a dictated reply, a turn with no follow-up, do not open the microphone).
            voice.autoRelisten = true
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
        // when the engine delivers none (`VoiceSession.listenLevel`). Without it, a mute engine
        // left seven perfectly flat dots under "LISTENING".
        // left seven perfectly flat dots under "LISTENING".
        val listenLevel by voice.listenLevel.collectAsStateWithLifecycle()

        // **definitive** failure (listening is then already closed). Both are shown on
        // screen with an action — never a screen that claims to listen without saying anything.
        // screen with an action — never a screen that claims to listen without saying anything.
        val noSound by voice.noSound.collectAsStateWithLifecycle()
        val engineFailure by voice.failure.collectAsStateWithLifecycle()
        // frames without a word. That is what keeps the screen from staying mute on "LISTENING"
        // while nothing comes in — including when the microphone level itself was moving.
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

        // // by default**, but Android lets nothing publish without it: without the permission, the
        // foreground service cannot hold the process, and a bot's work dies
        // when Onyx goes to sleep. It is thus requested at first launch — the only moment it
        // is requested without a user gesture.
        // se demande sans geste de l'utilisateur.
        val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                com.onyx.app.voice.VoiceDiagnostics.note("notifications: permission refusee (POST_NOTIFICATIONS)")
            }
        }

        // the call is thus harmless if the person said no, and she can return via
        // Settings → Notifications.
        // Settings → Notifications.
        LaunchedEffect(Unit) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
         * The microphone permission is missing and nothing will be fixed from the app: the banner's
         * action opens the system settings for Onyx, never a button that retries in the void.
         */
        fun openVoiceAppSettings() {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}"),
            )
            runCatching { context.startActivity(intent) }
        }

/** "Try again": a fresh listening if the microphone is permitted, the request otherwise. */
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

        // The turn is over: the agent reads its reply, then goes back to listening in hands-free.
        LaunchedEffect(state.isStreaming, route) {
            if (route != Route.GROUP_VOICE.name && !state.isStreaming && voice.phase.value == VoicePhase.THINKING) {
                // Read **at the moment of the effect**: the text must not enter the shell's
                // projection, which must not change at every token.
                val reply = chatState.value.messages.lastOrNull { it.role == Role.ASSISTANT }?.text
                // template's `readAloud(_, speaker: conversation.bot?.profile)`. Without this profile, the
                // bot's "Voice" setting was ignored in voice mode.
                // bot's "Voice" setting was ignored in voice mode.
                voice.finishTurn(reply, speaker = openConversationBot?.name)
            }
        }

        // process scope (`AppContainer.work`), wired to the turn store. The screen only tells it
        // what it has in view, a little further down — otherwise a turn finished without a screen (activity
        // destroyed, another app in the foreground) would have nobody left to announce it.
        // destroyed, another app in the foreground) would have nobody left to announce it.

        // An agent page that asked a question receives the answer through this bus.
        LaunchedEffect(state.messagesSize, state.isStreaming) {
            if (!state.isStreaming) {
                chatState.value.messages.lastOrNull { it.role == com.onyx.app.data.model.Role.ASSISTANT }
                    ?.text
                    ?.let { com.onyx.app.ui.rich.PageReplyBus.publish(it) }
            }
        }

        // The home-screen widget follows the latest reply and the context fill.
        LaunchedEffect(state.messagesSize, state.isStreaming) {
            if (!state.isStreaming) {
                val used = chatState.value.messages.lastOrNull { it.usage?.contextUsed != null }?.usage?.contextUsed
                AppContainer.publishContext(used, state.server.contextWindow.takeIf { it > 0 })
                runCatching { OnyxWidget().updateAll(context) }
            }
        }
        // Les approbations qui arrivent pendant que l'app n'est pas au premier plan se notifient.
        val pendingApprovals = state.pendingApprovals
        val notifyInBackground by settingsRepo.notifyInBackground.collectAsStateWithLifecycle()
        val notificationSound by settingsRepo.notificationSound.collectAsStateWithLifecycle()
        // "Calls for an answer": a turn awaiting a decision rings the phone.
        val phoneRings by settingsRepo.phoneRings.collectAsStateWithLifecycle()
        val phoneRingsTalk by settingsRepo.phoneRingsTalk.collectAsStateWithLifecycle()
        // LifecycleCurrentStateInComposition: the value would never be recomposed). We thus follow
        // lifecycle events.
        // lifecycle events.
        val lifecycleOwner = LocalLifecycleOwner.current
        var inForeground by remember { mutableStateOf(true) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    // that passes in front of the app — a dialog, the notification shade, a permission
                    // request. The foreground service has nothing to do there, and the person
                    // asked for nothing.
                    // asked for nothing.
                    Lifecycle.Event.ON_START -> inForeground = true
                    Lifecycle.Event.ON_STOP -> inForeground = false
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // // tracking itself lives in the process scope — here we only inform it.
        // tracking itself lives in the process scope — here we only inform it.
        LaunchedEffect(state.conversationId, route, inForeground) {
            if (AppContainer.isInstalled) {
                AppContainer.work.watching(
                    conversationId = state.conversationId.takeIf { route == Route.CHAT.name },
                    foreground = inForeground,
                )
            }
        }
        DisposableEffect(Unit) {
            // The activity leaves: nothing is in view anymore, tracking must be able to notify.
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
                    // except at night. Refused or unanswered, the usual notification takes its place.
                    // except at night. Refused or unanswered, the usual notification takes its place.
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

        // // a "Reply" action when inline input is enough.
        // a "Reply" action when inline input is enough.
        val pendingQuestions = state.pendingQuestions
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

        // What comes from outside: a share, an assistant question, a shortcut.
        val pendingIntake by ShareInbox.pending.collectAsStateWithLifecycle()
        val pendingShare by ShareInbox.pendingFile.collectAsStateWithLifecycle()
        val voiceRequested by ShareInbox.voiceRequested.collectAsStateWithLifecycle()
        // "Quick capture" requested from outside (the illustration's shortcut): the app
        // opens on the note, **without touching the open conversation**.
        val captureRequested by ShareInbox.captureRequested.collectAsStateWithLifecycle()
        // **"Secrets vault"** shortcut (icon menu): the app opens on the vault,
        // without touching the open conversation.
        val vaultRequested by ShareInbox.vaultRequested.collectAsStateWithLifecycle()

        // it does not enter the composer, it opens the template preview — and the addition
        // only happens after seeing it.
        // only happens after seeing it.
        val pendingTemplate by ShareInbox.pendingTemplate.collectAsStateWithLifecycle()
        var importLink by remember { mutableStateOf<String?>(null) }
        var importBusy by remember { mutableStateOf(false) }
        var importProblem by remember { mutableStateOf<String?>(null) }
        // The bot whose share sheet is open, and what the preparation returned.
        var sharingBot by remember { mutableStateOf<BotProfile?>(null) }
        var templateShare by remember { mutableStateOf<BotTemplateOfferOutcome?>(null) }

        // "The app reappears": pending notes retry their filing, like the
        // `CaptureInbox.file()` called when the app returns to the foreground on the reference.
        // Without it, a note left pending would only have left again on the next press.
        LaunchedEffect(Unit) { com.onyx.app.data.capture.QuickCapture.fileNow() }

        LaunchedEffect(
            pendingIntake,
            pendingShare,
            voiceRequested,
            captureRequested,
            vaultRequested,
            pendingTemplate,
        ) {
            val question = pendingIntake
            val shared = pendingShare
            when {
                // A template link: the preview, never the composer. Nothing leaves before "Add".
                pendingTemplate != null -> {
                    importProblem = null
                    importBusy = false
                    importLink = pendingTemplate
                    ShareInbox.consume()
                }

                // A shared file is attached to the message: the share's text, if there is one,
                // leaves with it, as the composer's send button does.
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

                // the composer, the open conversation stays intact.
                // dans le composeur, la conversation ouverte reste intacte.
                vaultRequested -> {
                    ShareInbox.consume()
                    navTo(Route.VAULT.name)
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
                    // `upsert` activates the server by default; importing a code may want to
                    // keep the old one active — it is the one that decides.
                    servers.upsert(config, activate = activate)
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

        // Shop stand-in. The reference has `-echo.voiceDemo` for the same reason: the simulator has no
        // microphone, and neither does the emulator. It is a **debug-only** hook —
        // `BuildConfig.DEBUG` is a false constant in the release APK, where this path
        // thus does not exist. `am start -n com.onyx.app/.MainActivity -e onyx.voiceDemo true`.
        val voicePose = remember {
            if (!com.onyx.app.BuildConfig.DEBUG) null
            else posedVoicePhase(context.findActivity()?.intent?.getStringExtra(EXTRA_VOICE_PHASE))
        }
        val posedProblem = remember {
            if (!com.onyx.app.BuildConfig.DEBUG) null
            else context.findActivity()?.intent?.getStringExtra(EXTRA_VOICE_PROBLEM)
        }
        // as the real one (the banner and its action), so that the capture shows the real rendering.
        // as the real one (the banner and its action), so that the capture shows the real rendering.
        val posedNoSound = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                context.findActivity()?.intent?.getBooleanExtra(EXTRA_VOICE_NO_SOUND, false) == true
        }

        //  - microphone permission refused → open the system settings (nothing is fixed
        //    from the app);
        //  - "nothing is heard" after five seconds, or the engine's definitive failure → "Try again".
        // A banner, never a technical code alone; and never a screen that claims to listen without
        // saying anything.
        // rien dire.
        val voiceNoSound = noSound || posedNoSound
        val listenProblem = posedProblem
            ?: voiceProblem
            // never started" or "the engine receives frames and returns nothing", which the level
            // alone cannot distinguish — and that is what left the screen mute.
            // level alone cannot distinguish — and that is what left the screen mute.
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
        // The same for a room's voice scene (`-e onyx.groupVoiceDemo speaking`): a
        // scripted room, without server or microphone, to show the speech scene and exercise it.
        // owner's capture showed, seven identical dots. The pose thus injects **simulated
        // speech** (syllables, silences) so that the wave is verifiable. Declared as
        // **injected** state in the report; without the flag, it is never read and nothing changes.
        val demoVoiceLevel = rememberVoiceDemoLevel(voiceDemo)
        // The stand-in wired to a **real room**: it does not take navigation, it is the
        // person who opens their room and taps "Talk" (`GroupVoice.ask` -> `groups.send`).
        val groupVoiceDemoOn = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                context.findActivity()?.intent?.getStringExtra(EXTRA_GROUP_VOICE_DEMO) != null
        }
        // La maquette branchee sur une **vraie salle** : elle ne prend pas la navigation, c'est la
        // person who opens their room and taps "Talk" (`GroupVoice.ask` -> `groups.send`).
        val scenarioRoomWanted = remember {
            com.onyx.app.BuildConfig.DEBUG &&
                context.findActivity()?.intent?.getBooleanExtra(EXTRA_VOICE_SCENARIO_ROOM, false) == true
        }
        LaunchedEffect(voiceDemo, groupVoiceDemoOn, voiceScenario) {
            if (groupVoiceDemoOn) {
                voiceProblem = null
                navTo(Route.GROUP_VOICE.name)
            } else if (voiceScenario != null && !scenarioRoomWanted) {
                // must survive (that is exactly what the capture shows).
                // free: that is what starts the loop (listen → question → reply →
                // reprise) et ce qui affiche la phrase d'aide.
                voiceProblem = posedProblem
                if (!voice.handsFree.value) voice.toggleHandsFree()
                navTo(Route.VOICE.name)
            } else if (voiceDemo) {
                // device without an address stayed stuck on it: "Skip" and "Done" saved
                // an empty draft, the screen reappeared, and both buttons seemed dead.
                voiceProblem = posedProblem
                navTo(Route.VOICE.name)
            }
        }

        // No usable server: the connection screen, as on the reference's first launch — but
        // only as long as it has not been crossed. Without this flag (`Settings.setupDone`), a
        // device without an address stayed stuck on it: "Skip" and "Done" ("Terminé") saved
        // an empty draft, the screen reappeared, and both buttons seemed dead.
        // The shop stand-in overrides it: it talks to no server, it sets the voice
        // screen.
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
                // "Skip" **always** exits, right away, without a test: it is the way
                // out. It only saves a draft if it carries an address (an empty
                // draft would overwrite the existing configuration) and, when an already registered server
                // is usable, it returns to it — the app stays usable without being configured.
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
                    // "A key": when the server itself announces the bots, its Dashboard lives at
                    // the **same address** as the chat — and under the same key. "Simple" mode thus has
                    // **What "Simple" mode offers**: the already known addresses, in order of
                    // relevance. The current server, the phone's network, the previous gateway,
                    if (found.declaresDashboard) {
                        setupDraft = setupDraft.copy(serveBaseUrl = address.trim())
                    }
                    found
                },
                onApplyDiscovery = { found -> scope.launch { applyDiscovery(found) } },
                // **What "Simple" mode offers**: the already known addresses, in order of
                // relevance. The current server, the phone's network, the previous gateway,
                // the other registered servers.
                suggestions = com.onyx.app.ui.setup.SetupSuggestions.known(
                    current = com.onyx.app.ui.setup.setupAddressOf(state.server),
                    local = setupLocalAddresses.value,
                    lastGateway = serverList
                        .firstOrNull { it.id != state.server.id }
                        ?.let(::setupAddressOf)
                        .orEmpty(),
                    saved = serverList
                        .filter { it.id != state.server.id }
                        .drop(1)
                        .map(::setupAddressOf),
                ),
                // The draft's configuration link, **without its secrets**: the title bar's
                // "Copy". Nothing to copy as long as no address is entered.
                setupLink = if (setupDraft.isUsable) {
                    setupLinkFor(setupDraft)
                } else {
                    ""
                },
            )
            return@OnyxAppTheme
        }

        // A server change (URL included) restarts reading the roster and the
        // // The open thread's memory, server by server: the reference's `ServerSwitcher` records the
        // that was being read there, not only that server's most recent one.
        LaunchedEffect(state.server.id, state.server.configSignature) {
            botsViewModel.rebind(state.server)
            botsViewModel.refresh()
            conversationRepo.load()
        }

        // conversation being left before switching. Returning to a server thus reopens the thread
        // that was being read there, not only that server's most recent one.
        // that was being read there, not only that server's most recent one.
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

        // The conversations' read marks: what this phone has seen of each, like
        // the reference's `ChatActivity.seen` — there is no server-side read registry here. "Mark as
        // Unread" writes 0, "Mark as Read" the current time, and the row reads them.
        val conversationMarks = remember { BotActivityStore(context) }
        var unreadMarks by remember { mutableStateOf(emptyMap<String, Double?>()) }
        LaunchedEffect(history.size, state.conversationId, state.server.configSignature) {
            unreadMarks = history.associate { conversation ->
                conversation.id to conversationMarks.seen(state.server.configSignature, "conversation:" + conversation.id)
            }
        }

        // A bot tile's tap: its **chat** (the reference's `openBot`), never its editor. Its
        // conversation is the one its `canonical_session` designates (`BotProfile.chatId`), kept
        // to find it again; a bot without a named chat opens a new one, which the first turn ties to the
        // server — the port having no session transport, it cannot do it there.
        val openBotChat: (BotProfile) -> Unit = { bot ->
            val chat = botChatToOpen(
                bot = bot,
                conversations = history,
                serverId = state.server.id,
                mainName = state.server.displayName,
            )
            if (history.none { it.id == chat.id }) scope.launch { conversationRepo.save(chat) }
            chatViewModel.open(chat)
            // The bot's chat is open: what it said is seen, like the reference's `activity.botSeen`.
            botsViewModel.markBotSeen(bot)
            navTo(Route.CHAT.name)
        }

        // "Mark as Read" / "Mark as Unread" for a conversation, like the reference's `setUnread`:
        // the mark is written here and the list reads it back right away.
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

        // (`onyx_bot_faces`), its seen activity and its place in the order (`onyx_bot_activity`),
        LaunchedEffect(botPictures, botsState.bots) {
            if (botPictures) botsViewModel.loadPictures()
        }
        LaunchedEffect(botsViewModel) {
            botsViewModel.pictures.collect { botPicturesByName = it }
        }
        // (`onyx_bot_faces`), its seen activity and its place in the order (`onyx_bot_activity`),
        // and the attachment of its conversations by their title (`conversations.json`). After a
        // server-side rename (e.g. `ancien-nom` → `nom-courant`), these entries are under
        // the old name: the migration moves them to the current name, **once only** — the
        // rename journal makes it idempotent (re-running it neither replays nor overwrites anything).
        // rename journal makes it idempotent (re-running it neither replays nor overwrites anything).
        val profileMigration = remember(context) { profileStateMigration(context) }
        LaunchedEffect(botsState.bots, botsState.hidden, history.size, state.server.configSignature) {
            val roster = (botsState.bots + botsState.hidden).distinctBy { it.name }
            if (roster.isEmpty()) return@LaunchedEffect
            val outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                profileMigration.migrate(roster, state.server.configSignature, history)
            }
            outcome.conversations.forEach { updated ->
                val before = history.firstOrNull { it.id == updated.id }
                if (before != null && before.title != updated.title) conversationRepo.save(updated)
            }
            // The roster was read **under the old name**: if a transfer occurred, it is read again so that
            // // Creating, renaming and removing a project: the sheets live **here**, once, for
            if (outcome.applied.isNotEmpty()) botsViewModel.refresh()
        }
        // The server's projects (work folders), read by the Dashboard RPC — the source
        // of the "Projects" section and of `ProjectSessionsView`.
        var serverProjects by remember(state.server.id, state.server.configSignature) {
            mutableStateOf<List<com.onyx.app.data.net.ServerProject>>(emptyList())
        }
        // Creating, renaming and removing a project: the sheets live **here**, once, for
        // the list as for a project's screen. `projectRevision` forces re-reading the tree.
        var projectCreating by remember { mutableStateOf(false) }
        var projectRenaming by remember {
            mutableStateOf<com.onyx.app.data.net.ServerProject?>(null)
        }
        var projectRemoving by remember {
            mutableStateOf<com.onyx.app.data.net.ServerProject?>(null)
        }
        var projectProblem by remember { mutableStateOf<String?>(null) }
        var projectRevision by remember { mutableStateOf(0) }

        /** A gesture on a project: the call, then re-reading the tree; failure is reported. */
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
        // // The **in-force** model of the conversation whose usage is being viewed: the same source as
        var selectedProject by remember { mutableStateOf<com.onyx.app.data.net.ServerProject?>(null) }
        var usageSummary by remember { mutableStateOf<com.onyx.app.data.net.SessionSummary?>(null) }
        var usageFetchId by remember { mutableStateOf<String?>(null) }
        // // The bots chosen in "New Group Chat", set on the room when it opens.
        // The bots chosen in "New Group Chat", set on the room when it opens.
        // `default` database and does not find a bot's row there (the reported defect).
        var usageProfile by remember { mutableStateOf<String?>(null) }
        // // System back: the edge gesture, the button and the predictive back declared in the manifest
        // are handled in the layout (see the `BackHandler` of the `BoxWithConstraints`), which knows
        // whether the screen is in two panes and which route is presented.
        var usageModel by remember { mutableStateOf<String?>(null) }
        // The bots chosen in "New Group Chat", set on the room when it opens.
        var groupPicked by remember { mutableStateOf<Set<String>?>(null) }
        /** The name given to the room in the sheet (`NewGroupChatView`), or that of a room kept here. */
        var groupTitle by remember { mutableStateOf<String?>(null) }

        /**
         * Ouvre une salle depuis la liste. Une salle du serveur montre son journal (`GroupRoomScreen`,
         * `groups.log`); a room **kept here**, for lack of Dashboard, opens its local thread with its
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

        // the bots kept their old model. We read the provider of the entry actually
        // shown in the sheet, and only fall back on the prefix if it is missing.
        // whether the screen is in two panes and which route is presented.


        // The provider comes from the CATALOGUE, not from the model prefix.** `providerOf("deepseek/…")`
        // returned "deepseek", whereas this model is served by **kilocode**: the gateway
        // thus did not write the choice (the model/provider pair matched nothing), and
        // the bots kept their old model. We read the provider of the entry actually
        // // Is the open conversation a bot's? (`BotProfile.chatId`, the server's
        fun providerForChosen(model: String?): String? {
            if (model.isNullOrBlank()) return null
            return modelChoices.firstOrNull { it.id == model }?.provider
                ?: com.onyx.app.ui.design.ModelChoice.providerOf(model)
        }

        val chatPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { showListButton, sidebarToggle ->
            // the roster arrives over the network, after the screen — what the conversation retained
            // of its bot holds the top bar meanwhile, instead of Onyx's blue face.
            // face and its name, and — under "Bot colors in their chats" — its bubbles and its
            // buttons take the color of its face, like the reference's `ContentView`
            // (`BotFace.chatColors`); groups keep the theme's.
            val openedConversation = history.firstOrNull { it.id == state.conversationId }
            val openedConversationBot = openedConversation?.let { conversation ->
                // the send carry **exactly** that name (the order: conversation → bot → server → default).
                // `null` for a plain conversation: the "bot" stage then hands over to the server.
                // // **The values read here, not in the effect.** The `LaunchedEffect` key was the bot's only
                botOfConversationOrRemembered(conversation, bots, state.server.displayName)
            }
            // ("it's not synced in real time"). The model and the profile are now read
            // **before** the effect and carried by its keys: every choice propagates to the next turn.
            // bot — the one the model sheet just wrote to `FaceKeys.model` — and what makes
            // the send carry **exactly** that name (the order: conversation → bot → server → default).
            // `null` for a plain conversation: the "bot" stage then hands over to the server.
            // **The values read here, not in the effect.** The `LaunchedEffect` key was the bot's only
            // *name*: changing its model did not change it, so the effect never replayed
            // and the header kept the old model until the screen was reopened
            // ("it's not synced in real time"). The model and the profile are now read
            // `null` (plain conversation) lets the server profile decide; `""` (the main
            val botModelInForce = openedConversationBot?.let(botsViewModel::modelOf)
            val botProfileInForce =
                openedConversationBot?.let { if (it.isDefault) "" else it.name }
            LaunchedEffect(openedConversationBot?.name, botModelInForce, botProfileInForce) {
                chatViewModel.setBotModel(botModelInForce)
                // marker, the header's) designates the conversation's bot; a plain
                // conversation has no profile of its own — it is the main agent, then, whose profile
                // is the one carrying `isDefault`. If neither exists, nothing is provided:
                // the marker keeps its old behavior instead of looking like a dead control.
                chatViewModel.setBotProfile(botProfileInForce)
            }
            // **The bot whose identity is being opened.** The thread's face (the bottom bar's
            // marker, the header's) designates the conversation's bot; a plain
            // conversation has no profile of its own — it is the main agent, then, whose profile
            // est celui qui porte `isDefault`. Si aucun des deux n'existe, on ne fournit rien :
            // the marker keeps its old behavior instead of looking like a dead control.
            val openBotIdentity: (() -> Unit)? =
                (openedConversationBot ?: bots.firstOrNull { it.isDefault })?.let { profile ->
                    {
                        selectedBot = profile
                        // The identity opens **from this thread**: the chosen face will be its own.
                        faceConversation = openedConversation
                        navTo(Route.BOT_EDITOR.name)
                    }
                }
            val botChatColors = if (botColors) {
                openedConversationBot?.let { bot ->
                    val faceCode = openedConversation?.faceCode ?: bot.faceCode
                    (com.onyx.app.ui.design.BotFace.parse(faceCode)
                        ?: com.onyx.app.ui.design.BotFace.forName(if (bot.isDefault) "default" else bot.name))
                        .chatColors()
                }
            } else {
                null
            }
            CompositionLocalProvider(LocalOnyxTheme provides LocalOnyxTheme.current.withColors(botChatColors)) {
            ChatScreen(
                stateFlow = chatState,
                startCards = startCards,
                // A "/" menu line chosen: the text enters the field and the menu closes
                // again, instead of staying open on the command just taken.
                startOnChatTab = startPlan.chatTab,
                // "Shown in the header and the message box": the name from Settings → Appearance.
                assistantName = displayName.trim(),
                onInputChange = chatViewModel::onInputChange,
                // requested from the server (the thread opens on its last page, not on 500 messages).
                // referme, au lieu de rester ouvert sur la commande qu'on vient de prendre.
                onPickSlash = chatViewModel::pickSlash,
                onSend = chatViewModel::send,
                onSteer = chatViewModel::steer,
                // "Earlier messages" beyond what is loaded: the previous page is
                // requested from the server (the thread opens on its last page, not on 500 messages).
                onLoadEarlier = chatViewModel::loadEarlier,
                onConsumeSlash = chatViewModel::consumeSlash,
                onStop = chatViewModel::stop,
                onRetry = chatViewModel::retry,
                onClearError = chatViewModel::clearError,
                onNewConversation = { startFreshConversation() },
                onOpenConversations = if (showListButton) {
                    // the list is not a destination, it is a panel.
                    // du bord, comme le contrôle de la barre, l'ouvre donc au lieu d'ouvrir la
                    // route that carried it — that route has been removed.
                    { sidebarState.open() }
                } else {
                    // // **screen** (`Route.MODELS`): one gesture, two renderings, and the owner landed
                    // on the other one. It is the same picker as the card's chip (`onOpenModelSheet`):
                    null
                },
                // **The top pill opens the picker sheet.**
                // The title's second line — the model name, with its chevron — opened the models
                // **screen** (`Route.MODELS`): one gesture, two renderings, and the owner landed
                // on the other one. It is the same picker as the card's chip (`onOpenModelSheet`):
                // one gesture → one sheet.
                // **The top pill opens the model picker.**
                // The second line of the title — the model name, with its chevron — opened the
                // models **screen** (`Route.MODELS`): one gesture, two renderings, and the owner
                // landed on the other one. On the phone it is this sheet that is in force:
                // the model sheet keeps its own door (`onOpenModelSheet`), and the models
                // screen keeps its two others (Settings → Models, and the capabilities page).
                //
                onPickModel = { showModelSheet = true },
                onModelCommand = {
                    // // **The face is a door.** The fourth marker of the bottom bar and the
                    // **The face is a door.** The fourth marker of the bottom bar and the
                    chatViewModel.consumeModelPickerRequest()
                    showModelSheet = true
                },
                onOpenSettings = { navTo(Route.SETTINGS.name) },
                // the lightbulb) without changing its **routing**, which still opened the
                // "Ideas" page.
                // name, voice, what it does, personality, model, tools, skills —, the same
                // page as "Edit" in a tile's menu. That was the reported defect: the plate
                // batch had changed the fourth marker's **drawing** (the face instead of
                // de l'ampoule) sans changer son **routage**, qui ouvrait encore la page
                // "Ideas" page.
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
                        // Without this profile, "Read aloud" read a bot's reply with
                        // Onyx's voice: the bot's "Voice" setting changed nothing.
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
                    // "Start fresh": the same as the pencil, but the question first
                    // — l'ancienne reste dans l'historique, et c'est la confirmation qui le dit.
                    add(MenuItem("Start fresh", Symbols.ArrowCounterclockwise) { confirmingFresh = true })
                    // that answers "what did the agent do, and how much did it cost?" has its
                    // place. It replaces no existing entry.
                    add(MenuItem("Take a note", Symbols.SquareAndPencil) { navTo(Route.QUICK_CAPTURE.name) })
                    add(MenuItem("Computer", Symbols.DesktopComputer) { navTo(Route.COMPUTER.name) })
                    add(MenuItem("Terminal", Symbols.Terminal) { navTo(Route.TERMINAL.name) })
                    // a curve: `DocOnClipboard`, from the household family.
                    // that answers "what did the agent do, and how much did it cost?" has its
                    // place. It replaces no existing entry.
                    // **Its pictogram, not its neighbour's.** It carried `ChartBar`, the same
                    // icon as "Usage & cost" — two destinations, a single glyph in
                    // the same sheet. A ledger is a **written piece** (a logged report), not
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
                // LOT branch: "Branch from here" follows **exactly** the restore path
                // (a targeted message, a confirmation, an effect) — three lines here,
                // the mechanics living in the ViewModel and the repository.
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
                // `subagent.interrupt` can succeed. The stop is resolved **locally**,
                // exactly as the tasks screen already does for this flag — and the
                // report says that it is a simulation. Without the flag, it is the real
                onStopSubagent = { agent ->
                    if (subagentDemoRun) {
                        // QA stand-in: the emulator has neither Dashboard nor agent, so no
                        // `subagent.interrupt` can succeed. The stop is resolved **locally**,
                        // exactly as the tasks screen already does for this flag — and the
                        // rapport dit que c'est une simulation. Sans le drapeau, c'est la vraie
                        // route that is called.
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
                slashDemoItems = slashDemoItems,
                // **"Teach a skill"** — the "+" line, like the reference's "Teach a task".
                // It draws nothing here: the demonstration is captured on
                // the connected computer, so it leads there and the screen opens its sheet. The line
                // n'existe que si ce serveur porte des identifiants Dashboard — sans eux, aucune
                // route `onyx-record` n'existe et un bouton ne serait qu'un mensonge.
                onTeachTask = if (state.server.hasServeCredentials) {
                    {
                        teachRequested = true
                        navTo(Route.COMPUTER.name)
                    }
                } else {
                    null
                },
                // **La carte de prise en main du navigateur.** Elle ouvre le panneau depuis le
                // thread: that is the gesture the agent cannot do in the person's place.
                onOpenBrowser = if (state.server.hasServeCredentials) {
                    { navTo(Route.BROWSER_SCREEN.name) }
                } else {
                    null
                },
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
                    // A file attached from THIS device (gallery, share, picker) is not a
                    // server file: asking the Dashboard for it made it answer
                    // `400 Path must be absolute` et affichait un code nu. Elle s'ouvre sur place.
                    // Otherwise a delivered file opens according to its **family**: the household
                    // readers (image, video, sound, PDF, text, archive) when there is one,
                    // the system app otherwise. It first goes through the phone's cache.
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
                // // The chip's second segment: the reasoning level. A **local** fallback does not
                // offer the configured model's steps — so we do not write a step that drives
                // nothing (the in-force model name, for its part, stays named by the chip).
                // bubbles and the bottom marker show what the list's row shows.
                threadBot = openedConversationBot?.let { profile ->
                    openedConversation?.faceCode?.let { code -> profile.copy(faceCode = code) } ?: profile
                },
                // **Nothing fake on screen during synchronization.** If the conversation is
                // open, the roster has not answered yet and nothing (neither the roster nor the
                // conversation's memory) says which bot it is, the header stays **bare**:
                // neither Onyx's blue face, nor "@default" — until the answer arrives.
                threadBotUnknown = openedConversation != null &&
                    openedConversationBot == null && bots.isEmpty() && botsState.loading,
                // // for all its conversations and routines**. It is set above the
                // // conversation; the choice is remembered (per bot on the server, here for the plain
                mentionBots = mentionRoster,
                sidebarToggle = sidebarToggle,
                // The card's **model chip** opens the model picker sheet
                // (feature 1 → 2). The chip's second segment is the reasoning level.
                onOpenModelSheet = { showModelSheet = true },
                // conversation; the choice is remembered (per bot on the server, here for the plain
                // conversation), and it survives a restart.
                // nothing (the in-force model name, for its part, stays named by the chip).
                reasoningLabel = com.onyx.app.ui.design.ModelFallback.reflectionLabel(
                    com.onyx.app.ui.design.ReasoningLevels
                        .label(currentReasoning)
                        .takeIf { currentReasoning.isNotBlank() }
                        .orEmpty(),
                    state.fallback,
                ),
            )
            }
            // // kept locally — that is what makes it survive a force stop.
            // for all its conversations and routines**. It is set above the
            // on its side the choice is indeed per conversation — here, writing it would be false.
            // // A plain conversation: the active server's model, as the models
            if (showModelSheet) {
                com.onyx.app.ui.models.ModelSheet(
                    models = modelChoices,
                    // The **in-force** model carries the check mark — under fallback, it is the entry actually
                    // served (local) that is checked, never the configured model.
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
                            // // applies to **all** its conversations, that of a plain conversation is
                            // // the server's. The reference writes "For this conversation" because
                            botsViewModel.setModel(bot, model, providerForChosen(model))
                        } else {
                            // A plain conversation: the active server's model, as the models
                            // screen does.
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
                    // // routines, start fresh, edit, duplicate, delete) then the household's.
                    // // A single table for both places that show this menu — the sidebar and the "Bots"
                    // // page —, so that neither can forget one of them.
                    // on its side the choice is indeed per conversation — here, writing it would be false.
                    scope = if (openedConversationBot != null) {
                        com.onyx.app.ui.models.ModelScope.BOT
                    } else {
                        com.onyx.app.ui.models.ModelScope.SERVER
                    },
                    // The **"Fast mode" line**: `null` when the server does not expose it — the
                    // feuille ne peint alors pas la ligne (voir `rememberModelFastMode`).
                    fastMode = modelFastMode,
                    onDismiss = { showModelSheet = false },
                )
            }
        }

        // ————— Hunks: a bot card's menu ("a bot's menu" batch) —————
        // The table of gestures of a bot card's menu: **the reference's nine entries**
        // // "New conversation with <the name>": a **fresh** conversation of the bot (the
        // // thread's pencil makes the same choice), kept right away so that it is in the
        // // list under its name.
        // "Bots" page —, so that neither can forget one of them.
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
                // // The bot's chat is **archived** (it stays in the history, whole): a
                // // new conversation opens, without the history.
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
                    // The bot's chat is **archived** (it stays in the history, whole): a
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
                // Came from the bot list: the face set here is the **bot's**.
                faceConversation = null
                navTo(Route.BOT_EDITOR.name)
            },
            onDuplicate = { bot -> scope.launch { botsViewModel.duplicate(bot) } },
            onDelete = { bot -> scope.launch { botsViewModel.deleteBot(bot) } },
            // "Share as template": the sheet opens right away; the link is read on the
            // server (`SOUL.md`, tools, skills) and arrives in the sheet when it is ready.
            onShareTemplate = { bot ->
                sharingBot = bot
                templateShare = null
            },
            onMarkSeen = { bot -> botsViewModel.markBotSeen(bot) },
            onMarkUnread = { bot -> botsViewModel.markBotUnread(bot) },
            onVoice = { bot ->
                selectedBot = bot
                botVoiceProfile = bot.name
                navTo(Route.BOT_VOICE.name)
            },
            onChangeFace = { bot ->
                selectedBot = bot
                faceConversation = null
                navTo(Route.BOT_FACE.name)
            },
            onRemoveFromOnyx = { bot -> botsViewModel.removeAPIBot(bot) },
            hasRecent = { bot -> botRecentConversation(bot, history, botsState.bots, state.server.displayName) != null },
        )

        // ————— "Share as template": the link sheet, and the arrival of a link —————
        // The link is read once per opening: its `SOUL.md`, its tool sets, its skills.
        // As long as it is not there, the sheet waits — it never shows a partial link.
        LaunchedEffect(sharingBot) {
            val bot = sharingBot ?: return@LaunchedEffect
            templateShare = botsViewModel.templateOffer(bot)
        }
        sharingBot?.let { bot ->
            BotTemplateShareSheet(
                link = (templateShare as? BotTemplateOfferOutcome.Ready)?.offer?.link,
                withoutPrompt = (templateShare as? BotTemplateOfferOutcome.Ready)?.offer?.withoutPrompt == true,
                problem = (templateShare as? BotTemplateOfferOutcome.Refused)?.reason,
                onDismiss = {
                    sharingBot = null
                    templateShare = null
                },
            )
        }
        // The preview of a received link: mandatory before adding, and adding writes a real profile.
        importLink?.let { link ->
            BotTemplateImportSheet(
                link = link,
                busy = importBusy,
                problem = importProblem,
                onAdd = { share ->
                    scope.launch {
                        importBusy = true
                        importProblem = null
                        when (val outcome = botsViewModel.importTemplate(share)) {
                            is BotTemplateImportOutcome.Added -> {
                                importBusy = false
                                importLink = null
                                // Un ajout partiel se dit sur la liste des bots : la feuille se
                                // the write, and the failure is reported instead of staying mute.
                                botsViewModel.reportPartialImport(outcome)
                            }

                            is BotTemplateImportOutcome.Refused -> {
                                importBusy = false
                                importProblem = outcome.reason
                            }
                        }
                    }
                },
                onDismiss = {
                    if (!importBusy) {
                        importLink = null
                        importProblem = null
                    }
                },
            )
        }

        // the write, and the failure is reported instead of staying mute.
        // the write, and the failure is reported instead of staying mute.
        // // "Schedules…" from a tile's menu: the bot's schedules, like `scheduling = bot`.
        // bar stays to its left, like the detail of a `NavigationSplitView` on the tablet — the
        // back arrow then has no object there, sessions being chosen in the column.
        // side bar stays to its left, like the detail of a `NavigationSplitView` on the tablet — the
        val botsPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { wideDetail, sidebarControl ->
            BotsScreen(
                viewModel = botsViewModel,
                onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.SETTINGS.name) }),
                // A tile's tap opens its chat (the reference's `openBot`); the editor is an entry of the
                // menu, reached on long press.
                onOpenBotChat = openBotChat,
                onEditBot = { bot ->
                    selectedBot = bot
                    navTo(Route.BOT_EDITOR.name)
                },
                // // Opened from the list: the room takes its members back, with no imposed name.
                onChangeFace = { bot ->
                    selectedBot = bot
                    navTo(Route.BOT_FACE.name)
                },
                // // left, instead of opening a new conversation: the same defect as the
                onSchedules = { bot ->
                    selectedBot = bot
                    botCronProfile = bot.name
                    navTo(Route.BOT_SCHEDULES.name)
                },
                // // list's "Bots" section: same source, thus same badges.
                onVoice = { bot ->
                    selectedBot = bot
                    botVoiceProfile = bot.name
                    navTo(Route.BOT_VOICE.name)
                },
                // The card's menu: the reference's nine entries then the household's.
                botMenu = botMenuTable,
                onGroupChat = {
                    // // Dashboard there remains the API path, a profile and its own key (`Add Bot`).
                    groupPicked = null
                    groupTitle = null
                    // **And the previously opened room is forgotten.** Without that, the `GROUP_CHAT`
                    // route found `openedRoom` again and redisplayed the room just
                    // left, instead of opening a new conversation: the same defect as the
                    // crayon, par une autre porte.
                    openedRoom = null
                    navTo(Route.GROUP_CHAT.name)
                },
                // The bot grid reads the activity of already loaded conversations, like the
                // list's "Bots" section: same source, thus same badges.
                conversations = history,
                groups = botsState.groups,
                hasDashboard = state.server.canAdminister,
                openedChatId = history.firstOrNull { it.id == state.conversationId }?.serverSessionId,
                mainName = state.server.displayName,
                onOpenGroup = { room -> openGroup(room) },
                onRoomSeen = { room -> botsViewModel.markRoomSeen(room) },
                onNewBot = {
                    // "New Bot" makes a profile on its server (`BotEditor(.new)`); without
                    // Dashboard there remains the API path, a profile and its own key (`Add Bot`).
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

        /** The back of a screen **carried by the sidebar**: it pops the sidebar, one level at a time.
         * The back of a screen **carried by the sidebar**: it pops the sidebar, one level at a time.
         * The same screen (`Cron`, `Kanban`, `Code`) serves both presentations: in the sidebar, its
         * back arrow returns to the list; in the app, it goes back up the route stack as
         * before. It is `sidebarState.isNavigating` that decides — never a second duplicated screen.
         * before. It is `sidebarState.isNavigating` that decides — never a second duplicated screen.
         */
        val paneBack: () -> Unit = { sidebarState.popDestination() }

        val cronPane: @Composable (Boolean, (@Composable () -> Unit)?) -> Unit = { wideDetail, sidebarControl ->
            val cronViewModel: CronViewModel = viewModel(
                key = "cron-" + state.server.id + state.server.configSignature,
                factory = viewModelFactory { initializer { CronViewModel(state.server) } },
            )
            CronScreen(
                viewModel = cronViewModel,
                // The draft coming from a skill.** The skill editor saved the
                // // The reference's `topRow`: in two panes, the board carries the list's section picker
                // // above its capsules (Chat / Cron / Kanban / Code), like `KanbanView(topRow:)`.
                seed = routineSeed,
                onSeedUsed = { routineSeed = null },
                // An empty stack (the tab opened from the list, or from Settings) falls back
                // on the **conversation list** — never on Settings, from which one has
                // nevertheless just entered (`onOpenCron` from Settings). Carried by the sidebar, this same
                // back **pops the sidebar**: the arrow returns to the list. Outside the sidebar, the
                // fallback is the thread — the conversation list is no longer a route.
                onBack = if (wideDetail) null else {
                    val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })
                    // The arrow **and** the gesture pop the same stack: the sidebar's only
                    // when it is **this** screen that it carries. `isNavigating` said "some
                    // screen is open in there" — even closed, even invisible: the arrow
                    // then popped a sidebar one did not see, and the first press had no
                    // visible effect (measured).
                    if (sidebarState.destination == Route.CRON.name) paneBack else inApp
                },
                sidebarControl = sidebarControl,
            )
        }

        // The reference's `topRow`: in two panes, the board carries the list's section picker
        // above its capsules (Chat / Cron / Kanban / Code), like `KanbanView(topRow:)`.
        // The active section is Kanban: this row is only drawn on the board's route.
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

        // The Code screen: its filters, its sessions and its composer. The repository, the working copy
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
                            // Each message starts its own session: a **real** Hermes
                            // session is opened, and the status follows the real turn it launches.
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
                // // of the list, not from Settings — sending back there lost the previous page. Carried
                // // by the sidebar, it **pops the sidebar**; outside the sidebar, the fallback is the thread.
                onBack = if (wideDetail) null else {
                    val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })
                    // // Same rule as the other sections: the arrow only pops the sidebar if it
                    // by the sidebar, it **pops the sidebar**; outside the sidebar, the fallback is the thread.
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
                // Back returns **where one comes from**: the board opens from the tabs
                // of the list, not from Settings — sending back there lost the previous page. Carried
                // by the sidebar, it **pops the sidebar**; outside the sidebar, the fallback is the thread.
                onBack = if (wideDetail) null else {
                    val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })
                    // Same rule as the other sections: the arrow only pops the sidebar if it
                    // carries **this** screen (see Scheduled).
                    if (sidebarState.destination == Route.KANBAN.name) paneBack else inApp
                },
                topRow = topRow,
                sidebarControl = sidebarControl,
                onAskOnyx = { question ->
                    // "Ask Onyx" opens a new conversation with the question.
                    chatViewModel.newConversation()
                    chatViewModel.sendText(question, clearInput = false)
                    // Two panes: the conversation is next to the board, we stay there. Compact:
                    // it opens above, like the phone's panel. Carried by the sidebar,
                    // la conversation prend la main et le volet repart de sa liste.
                    if (!wideDetail) {
                        sidebarState.clearDestinations()
                        navTo(Route.CHAT.name)
                    }
                },
            )
        }

        /**
         * The sidebar's **tiles**: the app's screens, two per row. Each label opens a
         * screen that **really** exists here — the correspondence with the reference's layout
         * (Scheduled, Skills, Tools, Settings) is noted in the batch report.
         */
        // // The order and the selection come from the **local** setting (`SettingsRepository.sidebarTiles`,
        // // key `sidebar_tiles`), and no longer from the frozen list: a removed tile **stays** in
        // // the catalogue (the edit mode's library returns it in one press), and an added tile
        // // opens **exactly** the screen it opened — `paneScreen` below has not moved.
        // "Settings", for its part, has been **removed**: the gear in the sidebar's head opens the same
        // screen, in the same panel, and two adjacent entries for one screen are not two
        // chemins. Un test mesure les deux recouvrements (`SidebarDestinationsTest`) :
        // reintroducing a tile that duplicates a tab, or Settings, makes it fail.
        val sidebarTiles = com.onyx.app.ui.sidebar.SidebarDestinations.tiles

        // // content, the sidebar's foot ("New conversation" and the initial circle) intact. The
        // // sidebar no longer closes — it is the exact opposite of the previous choice, which closed it on
        // the opening of a tile and forced one to reopen it to see where one had just arrived.
        // //
        // // The screen carried **is the same screen as before** (`CronScreen`, `CapabilitiesScreen`,
        val tileIds by settingsRepo.sidebarTiles.collectAsStateWithLifecycle()
        val shownTiles = com.onyx.app.ui.sidebar.SidebarTileLayout.resolve(tileIds)
        val tileLibrary = com.onyx.app.ui.sidebar.SidebarTileLayout.library(tileIds)

        // // page, and the sidebar starts again from its list so as not to stay set on a stale screen.
        // // What a sidebar entry leaves **behind it**: the page left — the screen the
        // // sidebar carried — enters the **route stack** before the destination. It is the rule
        // // "back returns the original page" applied to leaving the sidebar: without it, the
        // twenty Settings rows of the sidebar threw away the page left (it entered **neither** of
        // the two stacks) and back returned the thread, never Settings.
        // // The sidebar, for its part, starts again from its list and **closes**: as an overlay it covers the whole
        // // window, so staying open set a stale screen above the one just
        val panePush: (String) -> Unit = { id -> sidebarState.pushDestination(id) }
        // A destination the sidebar does not carry — a deeper app screen, like
        // "Appearance" from Settings — takes the previous path: it opens full
        // page, and the sidebar starts again from its list so as not to stay set on a stale screen.
        // What a sidebar entry leaves **behind it**: the page left — the screen the
        // sidebar carried — enters the **route stack** before the destination. It is the rule
        // "back returns the original page" applied to leaving the sidebar: without it, the
        // twenty Settings rows of the sidebar threw away the page left (it entered **neither** of
        // the two stacks) and back returned the thread, never Settings.
        // // The model lives on the server: we rewrite it there, and the repository
        // // persists it. The screen has done its job, the sidebar returns to its list.
        // opening — measured on the device: "I come back out into the sidebar".
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
                            // The model lives on the server: we rewrite it there, and the repository
                            // persists it. The screen has done its job, the sidebar returns to its list.
                            settingsViewModel.open(state.server)
                            settingsViewModel.update { it.copy(model = model) }
                            settingsViewModel.saveAndClose()
                            paneBack()
                        },
                        onBack = paneBack,
                        serverSessionId = state.serverSessionId,
                        // The **in-force** model of the open conversation: the right entry is
                        // checked (and named) even when a fallback serves it outside the exposed list.
                        inForceModel = state.modelInForce,
                    )
                }

                // The "Skills" tile has been **removed**: "Capabilities" — the reference's *Capabilities*
                // tile — already carries the skills family (grouped list, count,
                // search, switch, reading, catalogue). The editor, the only gesture that did not live there,
                // is **carried** there (`onEditSkill`): it thus remains reachable from the screen.
                Route.SKILL_EDITOR.name -> SkillEditorScreen(
                    server = state.server,
                    existing = skillName,
                    scope = scope,
                    onSaved = paneBack,
                    onBack = paneBack,
                    seed = skillSeed,
                    onMakeRoutine = { name ->
                        routineSeed = com.onyx.app.data.skill.SkillRoutine.from(name)
                        skillSeed = null
                        panePush(Route.CRON.name)
                    },
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
                        // No path: there is nothing to show. We do **not** navigate during
                        // composition (an empty route does not enter the stack) — the sidebar returns to
                        // its list, exactly like the empty-state fallback of the other routes.
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

                // **"The Vault"** in the sidebar: stacked by its Settings row. Back pops
                // the sidebar and thus returns Settings, the page left.
                Route.VAULT.name -> VaultScreen(
                    server = state.server,
                    onBack = paneBack,
                )

                Route.INSIGHTS.name -> {
                    val period by settingsRepo.insightsPeriod.collectAsStateWithLifecycle()
                    InsightsScreen(
                        server = state.server,
                        period = period,
                        onSelectPeriod = { chosen -> scope.launch { settingsRepo.setInsightsPeriod(chosen) } },
                        onBack = paneBack,
                    )
                }

                // **"Capabilities"** — the reference's *Capabilities* tile: what the agent can
                // use in new conversations (skills, tool sets, MCP). It is
                // the **same** screen as the full-page route; here it is drawn **in** the sidebar and
                // its back **pops the sidebar** ([paneBack]), like the other tiles.
                Route.CAPABILITIES.name -> CapabilitiesScreen(
                    server = state.server,
                    onBack = paneBack,
                    // The editor now lives here: "New skill" and "Edit" stack
                    // its page **in the sidebar**, one level further — back returns "Capabilities".
                    onEditSkill = { name ->
                        skillName = name
                        skillSeed = null
                        panePush(Route.SKILL_EDITOR.name)
                    },
                )

                // **"Configuration"** — the reference's *Config* tile. On its side, the gear of
                // the head opens the app's Settings and this tile the **gateway**
                // configuration: two different screens. Onyx keeps the same separation — the gear
                // remains the only access to Settings, this tile opens the gateway page.
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

                // // carried here; its row only exists if the probe proves the capability.
                // archived conversations of this device, with what is needed to bring them back. A row
                // ouvre la conversation dans le fil (le volet repart de sa racine, comme les autres
                // // The pages the sidebar also carries (Models, Files, Scheduled, Kanban,
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
                    // **The Vault**: its page is stacked in the sidebar, like the other tiles
                    // carried here; its row only exists if the probe proves the capability.
                    onOpenVault = { panePush(Route.VAULT.name) },
                    onOpenBrowser = { paneLeave(Route.BROWSER_SCREEN.name) },
                    onDone = paneBack,
                    // The pages the sidebar also carries (Models, Files, Scheduled, Kanban,
                    // Skills, Tools, Insights) open **in** the sidebar; the others are
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

                // A tile without a screen here should not exist (`SidebarDestinations.paneScreens`
                // is measured by a test); we draw nothing rather than invent a screen.
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
         * The sidebar itself, as it is drawn in both widths: the head with the
         * gateway and its indicator, the search, the *Chats / Bots* selector, the tiles, the
         * projets, les filtres et la liste. Les **bots** ne sont pas dans la liste : ils vivent
         * dans la position *Bots*, avec les salons.
         */
        val chatSidebar: @Composable () -> Unit = {
            // **The sidebar's two resolvers, computed once per bots state.**
            //
            // The keys of the `remember` that builds the rows (`ChatSidebarHost`):
            // recreated on every recomposition of this screen, they made all the rows be rebuilt
            // — and all their panes — every time, which was felt when scrolling the list.
            // They only depend on the bots, the server and the displayed name.
            val knownBots = remember(botsState.bots, botsState.hidden) {
                (botsState.bots + botsState.hidden).distinctBy { it.name }
            }
            val sidebarMainName = displayName.trim().ifEmpty { "Onyx" }
            val identityOf: (com.onyx.app.data.model.Conversation) -> String = remember(
                knownBots, state.server, sidebarMainName,
            ) {
                { conversation ->
                    conversationProfile(
                        conversation = conversation,
                        bots = knownBots,
                        server = state.server,
                        mainName = sidebarMainName,
                    )
                }
            }
            // **The app update**: the check is **free** (GitHub's public API,
            // without account or key) and **occasional** — once at launch, once at
            // the sidebar's opening. A `null` (no network, GitHub unavailable) says nothing
            // // The face of a sidebar row, by **the single resolution** — the same instance as
            val updateGate = remember { com.onyx.app.ui.sidebar.UpdateGate(context) }
            val updateOffer by updateGate.offer.collectAsStateWithLifecycle()
            LaunchedEffect(updateGate) { updateGate.refresh() }
            // The face of a sidebar row, by **the single resolution** — the same instance as
            // the thread, the room and the voice scene. That was not the case: this lambda redid the
            // computation (`knownBots.firstOrNull { … }?.faceCode ?: botsState.faces[name]`), a
            // second writing of the rule that could diverge from `BotFaceResolver`.
            val faceCodeOf: (String) -> String? = remember(faceResolver) {
                faceResolver::codeOf
            }
            ChatSidebarHost(
                serverNote = serverImportNote,
                onRetryServer = chatViewModel::retryServerConversations,
                serverName = state.server.displayName,
                serverAddress = state.server.serveBaseUrl.ifBlank { state.server.baseUrl },
                // // "Not known yet": neither one — the app has not queried yet.
                // "Not known yet": neither one — the app has not queried yet.
                reachable = when {
                    botsState.problem != null -> false
                    state.server.canAdminister && !botsState.loading -> true
                    else -> null
                },
                agentName = displayName.trim().ifEmpty { "Onyx" },
                // A conversation's **mark and face** resolve on the **profile name of
                // its bot** — the same computation as the header and the tabs (`conversationProfile`):
                // per conversation, never per server.
                // // **Memoized.** These two resolvers are the **keys** of the `remember` that builds the
                // **Memoized.** These two resolvers are the **keys** of the `remember` that builds the
                // sidebar's rows: recreated on every recomposition of the screen, they made
                // all the rows — and all their panes — be rebuilt every time, which was
                // felt when scrolling. They only depend on the bots, the server and the displayed name.
                identityOf = identityOf,
                conversations = history,
                liveKinds = botsState.liveKinds,
                seenMarks = unreadMarks,
                bots = botsState.bots,
                mainName = displayName.trim().ifEmpty { "Onyx" },
                groups = botsState.groups,
                roomMarks = botsState.seenMarks,
                roomKinds = botsState.roomKinds,
                projects = serverProjects,
                faceCodeOf = faceCodeOf,
                // launcher rune on its tinted disk. It carried the gateway's initial
                // ("O"), for lack of better: an initial is not a sign.
                // ("O"), for lack of better: an initial is not a sign.
                tiles = shownTiles,
                tileLibrary = tileLibrary,
                // killed mid-composition finds the grid it was showing, and the order.
                // // A tile **stacks its screen in the sidebar** — it no longer closes it. That is the
                onSetTileLayout = { ids -> scope.launch { settingsRepo.setSidebarTiles(ids) } },
                // requested fix: "when I click for example on Insights, we stay in the
                // sidebar". The previous choice did the opposite (`navTo` then `close()`), and the
                // owner had to reopen the sidebar to see where he had just arrived.
                // owner had to reopen the sidebar to see where he had just arrived.
                onOpenTile = { id ->
                    sidebarState.pushDestination(id)
                },
                // tile: it opens in the sidebar, like the others.
                // "Settings" tile: it opens in the sidebar, like the others.
                onOpenSettings = {
                    sidebarState.pushDestination(Route.SETTINGS.name)
                },
                // its gestures live. Settings keeps its two paths (the gear
                updateOffer = updateOffer,
                onDownloadUpdate = {
                    updateOffer?.let(updateGate::download)
                },
                onDismissUpdate = {
                    updateGate.dismiss()
                },
                onOpenAccount = {
                    // The initial circle — and the gateway's name in the sidebar's head —
                    // open the account sheet (no. 8): that is where the connection's state and
                    // its gestures live. Settings keeps its two paths (the gear
                    // of the head, and the sheet's first row).
                    accountSheetOpen = true
                },
                // conversation or a page, so the sidebar's screen stack starts again from its root.
                // The row becomes the thread: the opening cover starts from its bounds,
                onOpenConversation = { conversation, rect ->
                    // The row becomes the thread: the opening cover starts from its bounds,
                    // as the list's row did.
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
                // held in the sidebar.
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
                // Onyx server has no route to fork a session, the branch thus lives
                // on this phone — it is told to the caller, never promised to the server.
                // on this phone — it is told to the caller, never promised to the server.
                onCreateThreadConversation = { conversation ->
                    scope.launch {
                        conversationRepo.fork(conversation.id)?.let { branch ->
                            chatViewModel.open(branch)
                            navTo(Route.CHAT.name)
                            sidebarState.close()
                        }
                    }
                },
                // restoration undoes the deletion **mark** — without which the repository would refuse
                // to rewrite it (a deletion is final for any other path).
                // // The deletion **reaches the source of truth**: the conversation leaves the
                onRestoreConversation = { conversation -> scope.launch { conversationRepo.restore(conversation) } },
                // server (`session.delete` route, that of a project's screen), then the phone.
                // Without that the registry kept the session and the conversation came back — it was the
                // reported defect: "I find it again". A server failure does not make it mute: the
                // local deletion did take place.
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
                    // route), like the menu of a row of the removed screen.
                    // `SESSION_USAGE` route), like the menu of a row of the removed screen.
                    usageSummary = com.onyx.app.data.net.SessionSummary.of(conversation)
                    usageFetchId = conversation.serverSessionId
                    // Le profil **du bot** de cette conversation : c'est lui qui porte sa ligne au
                    // the registry. `conversationProfile` reads the single decision (`ProfileInForce`).
                    usageProfile = com.onyx.app.ui.bots.conversationProfile(
                        conversation,
                        bots,
                        state.server,
                        state.server.displayName,
                    )
                    // source for the displayed name, the looked-up price and the counters.
                    // bot's, otherwise the server's — the same decision as the header. A single
                    // source for the displayed name, the looked-up price and the counters.
                    usageModel = com.onyx.app.ui.design.ModelInForce.inForce(
                        null,
                        botOfConversation(conversation, bots, state.server.displayName)?.model,
                        state.server.effectiveModel,
                    )
                    navTo(Route.SESSION_USAGE.name)
                    sidebarState.close()
                },
                onArchiveConversations = { ids -> scope.launch { conversationRepo.archiveAll(ids) } },
                onDeleteConversations = { ids ->
                    // The batch starts from the server, then from the phone: the targeted conversations only have a
                    // server session for those that have spoken — the others have nothing to remove.
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
                // conversation list are read again, like the removed screen's problem card.
                // conversation list are read again, like the removed screen's problem card.
                onRetry = {
                    botsViewModel.refresh()
                    scope.launch { conversationRepo.load() }
                },
                onOpenBot = { openBotChat(it); sidebarState.clearDestinations(); sidebarState.close() },
                // // What failed (duplicate, start fresh, delete): the model's word, shown
                botMenu = botMenuTable,
                // What failed (duplicate, start fresh, delete): the model's word, shown
                // in the *Bots* position — an action that fails does not stay mute.
                botNotice = botsState.problem,
                onDismissBotNotice = botsViewModel::dismissProblem,
                onNewBot = {
                    sidebarState.clearDestinations()
                    navTo(if (state.server.canAdminister) Route.BOTS.name else Route.API_BOT.name)
                    sidebarState.close()
                },
                onOpenRoom = { room -> openGroup(room); sidebarState.clearDestinations(); sidebarState.close() },
                // // screen: without that it stayed open above the bot list, the only case of all
                // // the sidebar's choices.
                // screen: without that it stayed open above the bot list, the only case of all
                // les choix du volet.
                onNewRoom = { navTo(Route.BOTS.name); sidebarState.close() },
                onNewProject = { projectCreating = true },
                onOpenProject = { project ->
                    selectedProject = project
                    // The sidebar closes **also** here: it was the second entry that left it
                    // open on the open screen.
                    navTo(Route.PROJECT_SESSIONS.name)
                    sidebarState.close()
                },
                // a back to pop a screen one does not see.
                // shows its root — and system back no longer belongs to it, it thus does not consume
                // a back to pop a screen one does not see.
                destination = sidebarState.destination.takeIf { sidebarState.isOpen },
                destinationContent = paneScreenFor.takeIf { sidebarState.isOpen },
                onPaneBack = paneBack,
                // // in the sidebar, `ChatSidebarHost`).
                // retour de quitter la recherche avant de refermer le volet (le gestionnaire est
                // dans le volet, `ChatSidebarHost`).
                sidebarState = sidebarState,
            )
        }

        // // shift by it a second time. Without that, a low field of a long form
        // // ("Identifier" of the configuration) stayed under the keyboard: the scrolling area
        // // kept the whole screen height, so Compose had nothing to lift.
        // ("Identifier" of the configuration) stayed under the keyboard: the scrolling area
        // kept the whole screen height, so Compose had nothing to lift.
        CompositionLocalProvider(LocalOverlayHost provides overlayHost) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .consumeWindowInsets(WindowInsets.ime),
        ) {
            // // together — a single pane. The collapsed sidebar leaves the detail taking everything.
            // // The sidebar's **mode** is decided by the width, right here: narrow, it covers the thread
            // // (overlay); wide, it is set beside. Each mode has **its** memory, so that
            // // a sidebar opened on a tablet does not invite itself as an overlay on a phone.
            // (overlay); wide, it is set beside. Each mode has **its** memory, so that
            // a sidebar opened on a tablet does not invite itself as an overlay on a phone.
            val sidebarMode = sidebarModeFor(maxWidth, androidx.compose.ui.platform.LocalDensity.current.fontScale)
            LaunchedEffect(sidebarMode) { sidebarState.updateMode(sidebarMode) }
            // What the kept setting was: we write it again so that a following launch
            // finds the same layout on a large screen.
            LaunchedEffect(sidebarMode, sidebarState.isOpen) {
                if (sidebarMode == SidebarMode.DOCKED) {
                    settingsRepo.setSidebarCollapsed(!sidebarState.isOpen)
                }
            }
            // // screen**, it takes a screen's width (`content`): the list column
            // // would crush it (see `sidebarContentWidthFor`).
            // screen**, it takes a screen's width (`content`): the list column
            // would crush it (see `sidebarContentWidthFor`).
            val layout = listDetailLayout(
                maxWidth,
                fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale,
                collapsed = sidebarMode == SidebarMode.DOCKED && !sidebarState.isOpen,
                content = sidebarState.isNavigating,
            )
            // // two gestures — the icon does not change, only the label says what the next press will do.
            // closes what is open. That is what makes the sidebar reversible without ever asking
            // two gestures — the icon does not change, only the label says what the next press will do.
            val sidebarToggleControl: @Composable () -> Unit = {
                SidebarToggleControl(
                    open = sidebarState.isOpen,
                    onClick = { sidebarState.toggle() },
                )
            }
            // // expands ("Expand the list"), whether the sidebar is docked or not, and the same state as the
            // // overlay. Two distinct controls previously forced one to aim at the one that
            // // was drawn: that was the second gesture to remove.
            // the overlay. Two distinct controls previously forced one to aim at the one that
            // was drawn: that was the second gesture to remove.
            val expandControl: (@Composable () -> Unit)? = if (layout.twoPanes) sidebarToggleControl else null
            val collapseControl: (@Composable () -> Unit)? = expandControl
            // // the same way, and that hardware back closes them all.
            // the **same** wrapper serves them all, so that the bar's control opens them all
            // the same way, and that hardware back closes them all.
            val withSidebar: @Composable (inner: @Composable () -> Unit) -> Unit = { inner ->
                SidebarScaffold(state = sidebarState, sidebar = { chatSidebar() }, content = { inner() })
            }
            // // partly: Android caps the exclusion at 200 dp per edge, measured on this device).
            // // The left-edge gesture — that of the reference's `SidePanel` — *thus reveals the conversation
            // The left-edge gesture — that of the reference's `SidePanel` — *thus reveals the conversation
            // list* through back itself, instead of closing the app as it did
            // on the five geometries surveyed: at the foot of the stack it opens the list, the list
            // open it closes it (back thus stays whole when the panel is open), and
            // lower in the stack it goes back as before. Two panes: the list is already there,
            // back only has to go back up the stack — never a closing of the app.
            // // pop, we go back to the screen that makes sense. Before, on Settings in two panes,
            // // the stack was empty, `navBack()` returned false and the gesture did **nothing** — "I
            // // can't even go back with the screen edge".
            // // Two panes: the **root** is the thread — the detail paints it as well for Chat as
            // // for the conversation list, which lives on the left. At the foot of the stack, back is
            // // thus no longer consumed: it hands control back to the system, as on a phone. The rule is
            // // shared and measured ([routeBackHandled]).
            // shared and measured ([routeBackHandled]).
            val backHandled = routeBackHandled(
                currentRoute = route,
                backStack = backStack,
                twoPanes = layout.twoPanes,
                chatRoute = Route.CHAT.name,
            )
            // **The fallback of an empty-stack back — a single rule for the three gestures.**
            // // **The fallback of an empty-stack back — a single rule for the three gestures.**
            // // The header's arrow (in the page), the system back button and the edge
            // // gesture follow the **same** descent: we pop the stack first (`navBack`), and the
            // // fallback is only used **when** it is empty. It comes here from the **back table**
            // // ([TableDesRetours]) — never from a hard-coded target. The pages' arrows
            // // write this same fallback at their call site (`if (!navBack()) navTo(<fallback>)`): the
            // // two targets can no longer diverge, and `ArrowInnerLockTest` compares one to
            // // the other, naming any discrepancy. (The context page is the only one whose fallback is
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

            // Each route keeps its saved state (scroll position included): the route
            // that leaves composition leaves it here, and back finds it **exactly where it
            // was** — before, returning from a sub-screen brought back to the top of the previous page.
            val routeStates = rememberSaveableStateHolder()

            AnimatedContent(
                targetState = Route.valueOf(route),
                transitionSpec = {
                    if (layout.twoPanes && initialState in TwoPaneRoutes && targetState in TwoPaneRoutes) {
                        // The column does not slide with the detail: like NavigationSplitView.
                        EnterTransition.None.togetherWith(ExitTransition.None)
                    } else {
                        onyxRouteTransition(initialState, targetState)
                    }
                },
                // // The route's content is recorded in the glass layer: a pane that
                // // covers it (menu panel, floating button) sees what passes behind it.
                modifier = Modifier
                    .fillMaxSize()
                    .then(LocalBackdrop.current?.let { Modifier.recordBackdrop(it) } ?: Modifier),
                label = "onyxRoute",
            ) { presentedRoute ->
              routeStates.SaveableStateProvider(presentedRoute.name) {
                if (layout.twoPanes && presentedRoute in TwoPaneRoutes) {
                    // // panes "in power" — the geometry keeps its bounds, and the detail
                    // // carries the control that reopens the list.
                    // porte le contrôle qui rouvre la liste.
                    if (presentedRoute == Route.KANBAN) {
                        // // board holds the left pane and carries the section picker
                        // // (the reference's `topRow`); the conversation the right one — "ask
                        // // Onyx" fills it without leaving the board. The sidebar follows the same
                        // // bounded geometry as the other routes, not a frozen 58/42 split.
                        // bounded geometry as the other routes, not a frozen 58/42 split.
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
                                    // // The bots keep the column to their left (D6).
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
                // // only shows finished turns, and rebuilding them here would re-walk the whole
                // // thread on every recomposition of the root.
                // fil a chaque recomposition de la racine.
                history = state.voiceTurns,
                // // listening, the orb lives on the same wave, and nothing is a lie: it is a pose.
                // listening, the orb lives on the same wave, and nothing is a lie: it is a pose.
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
                // // conversation, otherwise its first question — never the server's name.
                // conversation, otherwise its first question — never the server's name.
                conversationTitle = if (voiceDemo) {
                    VOICE_DEMO_TITLE
                } else {
                    (state.conversationName?.takeIf { it.isNotBlank() }
                        ?: state.firstUserQuestion)
                        ?.let(::chatHeaderTitle)
                        ?.takeIf { it.isNotBlank() }
                        ?: state.server.displayName
                },
                problem = listenProblem,
                alertAction = listenAlert,
                // The help sentence names the phrases actually listened to (those set by the
                // person, otherwise the base ones): no more English words above French
                // settings.
                stopPhrases = stopPhrases,
                onToggleHandsFree = voice::toggleHandsFree,
                // "Speak during replies": the three choices of the voice panel write the
                // setting (the same as Settings → Voice), which decides whether the microphone opens behind
                // a reply.
                talkOverReplies = TalkOverChoice.of(talkOver),
                onTalkOverChange = { choice -> scope.launch { settingsRepo.setTalkOver(choice.setting) } },
                onRestart = {
                    voiceProblem = null
                    if (phase == VoicePhase.IDLE) {
                        chatState.value.messages.lastOrNull {
                            it.role == com.onyx.app.data.model.Role.ASSISTANT && it.text.isNotBlank()
                        }?.let {
                            // Replay comes from the open conversation bot's voice —
                            // the model's `replayLastReply()` passes `conversation.bot?.profile`.
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
                // Settings → Appearance: the orb and its light are those of the setting, everywhere.
                // The reference's `VoiceOrb.shown`: "Bot faces" off replaces Onyx's face with the
                // Bubble, so that no face appears there.
                orb = if (voiceOrb == VoiceOrbChoice.ONYX && !botFaces) VoiceOrbChoice.BUBBLE else voiceOrb,
                onOrbChange = { orb -> scope.launch { settingsRepo.setVoiceOrb(orb) } },
                orbLight = orbLight,
                onOrbLightChange = { light -> scope.launch { settingsRepo.setOrbLight(light) } },
                // The orb is a button, like `VoiceView:123`: its action is that of the
                // current phase (`VoiceSession.primaryAction`).
                onOrbClick = {
                    when (phase) {
                        VoicePhase.IDLE, VoicePhase.SPEAKING, VoicePhase.THINKING -> {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) voice.startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        // Nothing more is said mid-word: the device sends the question as soon as
                        // the sentence ends; the orb thus returns to rest, without lying.
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
                onOpenVault = { navTo(Route.VAULT.name) },
                onOpenBrowser = { navTo(Route.BROWSER_SCREEN.name) },
                // "Done" closes the sheet: we return **where one came from** (the thread, the
                // list, or a bot tile), and not systematically to the thread — opening
                // Settings from the conversation list and ending up in the thread was
                // a false back.
                onDone = { if (!navBack()) navTo(Route.CHAT.name) },
                onOpenAppearance = { navTo(Route.APPEARANCE.name) },
                onOpenModels = { navTo(Route.MODELS.name) },
                onOpenVoice = { navTo(Route.VOICE_SETTINGS.name) },
                onOpenGateway = { navTo(Route.GATEWAY.name) },
                // Logs: the Settings row is **never** closed (the voice journal is
                // local to the device), whereas the Gateway requires a Dashboard session.
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
                onOpenLock = { navTo(Route.LOCK.name) },
                onOpenConnect = { navTo(Route.CONNECT.name) },
                onOpenTerminal = { navTo(Route.TERMINAL_SETTINGS.name) },
                onOpenAcknowledgements = { navTo(Route.ACKNOWLEDGEMENTS.name) },
                onOpenContext = { navTo(Route.CONTEXT.name) },
                onOpenMemory = { navTo(Route.MEMORY.name) },
                onOpenNotifications = { navTo(Route.NOTIFICATIONS.name) },
                onOpenConnectionDetails = { navTo(Route.CONNECTION_DETAILS.name) },
                onSetContextWindow = settingsViewModel::setContextWindow,
                // Settings presents itself above the two panes: their bar keeps the
                // sidebar's control, like the other routes of a large screen.
                sidebarControl = expandControl ?: collapseControl,
                onEraseEverything = {
                    // the reference erases then closes the sheet; here erasing also empties the
                    // open conversation, so we start again on a new one.
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
                    // A recreated process without the chosen bot must not open the `default` store.
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

            // "Voice…": the bot's voice, chosen for it alone (`BotVoicePicker`). The setting is
            // written right away (`setBotVoice`); the screen reads back what the app kept, so the
            // check mark survives a restart.
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
                // Back pops **the stack** first: this screen also opens from the **sidebar**
                // ("New bot" of a server without Dashboard credentials, whose
                // previous page is the thread), and `navTo` left an entry behind it — the
                // back of that time did not return the page left, and the next replayed it.
                onBack = { if (!navBack()) navTo(Route.BOTS.name) },
            )

            Route.BOTS -> withSidebar { botsPane(false, sidebarToggleControl) }

            Route.BOT_EDITOR -> {
                val bot = selectedBot
                // The bot's tool sets and skills: read from the server when the
                // editor opens, and written on the gesture (see `BotEditorViewModel`).
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
                    // The preview shows the face that will apply: the **thread's** when one
                    // came from the conversation, the bot's otherwise.
                    faceCode = bot?.let { faceConversation?.faceCode ?: botsViewModel.faceFor(it) },
                    // The face chosen on the dedicated screen, for a bot being created: it returns here
                    // through the same state, and the preview shows it.
                    chosenFaceCode = if (bot == null) newBotFaceCode else null,
                    mainName = state.server.displayName,
                    onFaceChosen = {
                        if (bot != null) navTo(Route.BOT_FACE.name)
                    },
                    onFaceChosenNamed = { _, name ->
                        // A bot being created does not exist yet on the server: the face
                        // screen opens on it by its name, and the choice returns to the editor.
                        if (bot == null) {
                            newBotName = name
                            newBotFaceCode = null
                        }
                        navTo(Route.BOT_FACE.name)
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
                        // "Voice…" of the editor: the same sheet as from a tile's menu.
                        botVoiceProfile = bot?.name
                        navTo(Route.BOT_VOICE.name)
                    },
                    voiceSummary = BotVoicePicker.summary(botVoicesHere[state.server.id]?.get(bot?.name)),
                    models = botModels,
                    onPickModel = { model ->
                        selectedBot?.let { botsViewModel.setModel(it, model, providerForChosen(model)) }
                    },
                    // **"Model" opens the real list.** The screen's two lines
                    // ("hermes-agent", a raw identifier) say nothing to the person who
                    // sets her bot; the thread sheet shows the available models, per profile,
                    // with search, price and favourites.
                    onOpenModelSheet = { editorModelSheet = true },
                    toolsets = editorState.toolsets,
                    toolsSelection = editorState.toolsSelection,
                    mainToolsets = editorState.mainToolsets,
                    onToolsSelectionChange = editorViewModel::setToolsSelection,
                    skills = editorState.skills,
                    alwaysLoadedSkills = editorState.alwaysLoaded,
                    onToggleSkill = editorViewModel::toggleSkill,
                    onPinSkill = editorViewModel::pinSkill,
                    // **No "Ideas" door on the identity page**: the reconciliation
                    // arbitration refuses the row the face batch had added. The
                    // deletion of Ideas/Goals (owner's request) removed the screen, its
                    // route, its destination, its dictionary key and this emergency door.
                    // See `DeletionIdeasGoalsTest` and `FaceGateIdentityTest`.
                    problem = deleteProblem ?: editorState.problem,
                    onSave = { title, about, model, shown ->
                        // **A rename propagates.** A bot conversation's title is the
                        // bot's name: without this report, the list kept "coder" and the
                        // conversation detached from its bot (it lost its face).
                        bot?.let { botsViewModel.saveEdit(it, title, about, model, shown) }
                        val nouveauLibelle = title.trim()
                        // Without a change condition: `retitleBot` only touches the
                        // conversations whose title is not already the new one.
                        // // **And only those of THIS bot.** The canonical session is what the
                        // **And only those of THIS bot.** The canonical session is what the
                        // server gives to the profile: without it, the old rule also renamed
                        // the threads of other bots that carried the same label, and renaming one
                        // bot changed the name of the others.
                        if (bot != null && nouveauLibelle.isNotBlank()) {
                            scope.launch {
                                conversationRepo.retitleBot(
                                    profile = bot.name,
                                    // The old label, the one the list showed before this gesture:
                                    // it no longer serves to recognize the bot (it is the session that
                                    // says it), but to know which titles were **only** its name —
                                    // those follow the rename, the others do not.
                                    fromLabel = bot.label(state.server.displayName),
                                    toLabel = nouveauLibelle,
                                    canonicalSessionId = bot.chatId,
                                )
                            }
                        }
                        // **We stay on the page.** It was `navTo(Route.BOTS.name)` that was here:
                        // "Save" sent back to the bot list — which the owner reads
                        // as "the bot creation page" — whereas he only asked to
                        // keep his settings. `saveEdit` writes the profile (server + local copy)
                        // and reads the roster again; the screen has no reason left to leave.
                    },
                    onCreate = { title, about, soul, shown, faceCode ->
                        // The profile is born on the server with its template's face: the screen does not
                        // close until it has taken, and a refusal is read here.
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
                            // The deletion goes to the server: the screen only closes once
                            // it has taken, and a refusal is read here, without erasing anything.
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
                // // thread and the room; what the choice changes is written under its title
                // **The models sheet, above the bot's identity.** Same component as the
                // thread and the room; what the choice changes is written under its title
                // (`ModelScope.BOT`: "For all this bot's conversations"), and the choice is
                if (editorModelSheet) {
                    com.onyx.app.ui.models.ModelSheet(
                        models = modelChoices,
                        currentModel = bot?.model ?: state.modelInForce,
                        reasoning = currentReasoning,
                        favourites = modelFavourites,
                        recents = modelRecents,
                        loading = modelChoicesLoading,
                        profiles = modelProfiles,
                        scope = com.onyx.app.ui.models.ModelScope.BOT,
                        fallback = state.fallback,
                        // No "Fast mode": this setting belongs to a conversation
                        // session, and a bot's identity has none.
                        fastMode = null,
                        onPickModel = { model ->
                            bot?.let { botsViewModel.setModel(it, model, providerForChosen(model)) }
                            // The roster is read again so that the "Model" row shows the chosen
                            // one, and the model enters the server's recents.
                            botsViewModel.refresh()
                            modelPrefs.noteRecent(state.server.id, model)
                            modelRecents = modelPrefs.recents(state.server.id)
                            editorModelSheet = false
                        },
                        onPickReasoning = { value -> scope.launch { settingsRepo.setReasoningEffort(value) } },
                        onToggleFavourite = { id, favourite ->
                            modelPrefs.setFavourite(state.server.id, id, favourite)
                            modelFavourites = modelPrefs.favourites(state.server.id)
                        },
                        onDismiss = { editorModelSheet = false },
                    )
                }
            }

            Route.BOT_FACE -> {
                val bot = selectedBot
                // A bot **being created** does not exist yet on the server: the face screen
                // still opens on it, and the choice returns to the editor that creates it.
                val pendingName = newBotName
                if (bot == null && pendingName == null) {
                    LaunchedEffect(Unit) { if (!navBack()) navTo(Route.BOTS.name) }
                } else if (bot == null) {
                    BotFacePickerScreen(
                        botName = pendingName.orEmpty(),
                        displayName = pendingName.orEmpty(),
                        current = newBotFaceCode,
                        isDefault = false,
                        hasPickedFace = newBotFaceCode != null,
                        // A bot that does not exist yet has nothing to write on the server: the
                        // face is set **with it**, at creation.
                        serverBacked = false,
                        keptWithBot = true,
                        onSave = { code ->
                            newBotFaceCode = code
                            if (!navBack()) navTo(Route.BOT_EDITOR.name)
                        },
                        onCancel = { if (!navBack()) navTo(Route.BOT_EDITOR.name) },
                    )
                } else {
                    // **A thread's face or a bot's?** Came from the conversation, the choice
                    // only writes on **this thread** (`Conversation.faceCode`): the other conversations
                    // of the same bot keep theirs — that was the whole reported defect ("it changed
                    // all the coder ones"). Came from the "Bots" tab, it writes on the bot, as before.
                    val thread = faceConversation
                    BotFacePickerScreen(
                        botName = bot.name,
                        displayName = bot.label(state.server.displayName),
                        current = thread?.faceCode ?: botsViewModel.faceFor(bot),
                        isDefault = bot.isDefault || bot.name == "default",
                        hasPickedFace = thread != null || botsViewModel.hasPickedFace(bot),
                        serverBacked = state.server.hasServeCredentials,
                        onSave = { code ->
                            if (thread != null) {
                                // The thread, and it alone: the conversation is saved with its
                                // face, and the list reads this file back (a single source).
                                scope.launch { conversationRepo.save(thread.copy(faceCode = code)) }
                            } else {
                                botsViewModel.setFace(bot, code)
                            }
                            if (!navBack()) navTo(Route.BOT_EDITOR.name)
                        },
                        onCancel = { if (!navBack()) navTo(Route.BOT_EDITOR.name) },
                    )
                }
            }

            // **The room is wrapped by the sidebar**, like the thread: without this wrapper, the
            // sidebar's control changed its state without anything opening — "the button does
            // nothing". The same `SidebarScaffold` thus serves the thread and the room.
            Route.GROUP_CHAT -> withSidebar {
                val room = openedRoom
                if (room != null) {
                    // A server room opened from the list: its thread comes from Hermes itself
                    // (`groups.log` / `groups.state`), and what is written there leaves by `groups.send`.
                    // The local turn-taking composer (GroupChatScreen) is now only used by a
                    // new room ("New Group Chat"), whose members are not yet on Hermes's side.
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
                        // **The sidebar**, as in a bot thread: the same badge, the same gesture.
                        sidebarToggle = sidebarToggleControl,
                        faceResolver = faceResolver,
                        // The room opens from the conversation list as from the Bots
                        // tab: back thus returns where one comes from, and no longer jumps
                        // systematically to Bots.
                        onBack = { if (!navBack()) navTo(Route.BOTS.name) },
                        // The pencil: a **new conversation of this room** — its members and its
                        // name, a new journal on Hermes's side (`groups.create`) — opened **in the
                        // room**: the same screen, thus the same plate, the same tabs and the
                        // same faces. It no longer leaves the route: the old path emptied
                        // `openedRoom` and fell back on the local composer of `GroupChatScreen`,
                        // which appeared above the plate during the transition ("a second
                        // empty composer in blur, shifted to the right").
                        onNewConversation = {
                            // A still-blank room is taken as is, so as not to stack
                            // two empty conversations: the conversation is already new there.
                            if (roomStartsAnotherConversation(roomViewModel.state.value.rows)) {
                                roomViewModel.startNewConversation { made ->
                                    // The new room opens **here**: the route does not change, and
                                    // its new identifier makes it compose its own thread (blank
                                    // state) under the same plate. The list is then read again, so
                                    // that the new conversation takes its place there — as after
                                    // any made room.
                                    openedRoom = roomChat(made, bots)
                                    botsViewModel.refresh()
                                }
                            }
                        },
                        // The room's Talk button opens the voice scene, which reads this thread
                        // (`GroupRoomFeed`): the server's replies and who is speaking.
                        onOpenVoice = { navTo(Route.GROUP_VOICE.name) },
                        // A file from the room's Files tab opens like that of a
                        // conversation: the same route, the same family reader.
                        onOpenFile = { uri, name, mime -> openConversationFile(uri, name, mime) },
                        // A member's AI model, chosen **from the room**: the room's menu
                        // lists its bots, and the sheet opens for the one touched. The choice
                        // is written on its profile, so it also applies to its replies in the room.
                        onPickBotModel = { name -> roomModelBot = name },
                        onEnded = {
                            // // ---- The AI model **of a bot**, set from the room ----
                            openedRoom = null
                            botsViewModel.refresh()
                            navTo(Route.BOTS.name)
                        },
                        speechLocale = speechLocale,
                        focusComposer = groupComposerFocus,
                        onComposerFocused = { groupComposerFocus = false },
                        chatTextScale = com.onyx.app.ui.theme.ResolvedTheme.chatTextScale(chatTextSize),
                    )
                    // ---- The AI model **of a bot**, set from the room ----
                    // A room has no conversation with a bot: the sheet is thus drawn
                    // au-dessus d'elle, pour le membre choisi dans son menu. Elle lit le `model`
                    // `model` — the one Hermes serves to this bot **in the room** — and writes it via
                    // `profiles.configure` (`BotsViewModel.setModel`), comme la feuille d'un chat.
                    val roomModelBotProfile = roomModelBot?.let { name ->
                        bots.firstOrNull { it.name == name } ?: BotProfile(name = name)
                    }
                    if (roomModelBotProfile != null) {
                        com.onyx.app.ui.models.ModelSheet(
                            models = modelChoices,
                            currentModel = roomModelBotProfile.model ?: state.modelInForce,
                            reasoning = currentReasoning,
                            favourites = modelFavourites,
                            recents = modelRecents,
                            loading = modelChoicesLoading,
                            profiles = modelProfiles,
                            scope = com.onyx.app.ui.models.ModelScope.BOT,
                            fallback = state.fallback,
                            // No "Fast mode": this setting belongs to a conversation
                            // session, and the room has none — the line is not drawn.
                            fastMode = null,
                            onPickModel = { model ->
                                botsViewModel.setModel(roomModelBotProfile, model, providerForChosen(model))
                                // **The roster is read again; the choice is pushed into the room itself.**
                                // `setModel` writes `profiles.configure` on the server side and the local
                                // profile: without re-reading the roster, the sheet reopened the old model
                                // and the room saw nothing change — "the change is not
                                // taken in real time". The local merge gives the new model to
                                // the screen immediately, the re-read confirms what the server
                                // retained.
                                botsViewModel.refresh()
                                modelPrefs.noteRecent(state.server.id, model)
                                modelRecents = modelPrefs.recents(state.server.id)
                                roomModelBot = null
                            },
                            onPickReasoning = { value -> scope.launch { settingsRepo.setReasoningEffort(value) } },
                            onToggleFavourite = { id, favourite ->
                                modelPrefs.setFavourite(state.server.id, id, favourite)
                                modelFavourites = modelPrefs.favourites(state.server.id)
                            },
                            onDismiss = { roomModelBot = null },
                        )
                    }
                } else {
                    // The room chosen in the list: its members are pre-checked, like the
                    // reference's `NewGroupChatView` starts from the room's bots.
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
                    // that reply, like the reference's `createGroup(named:bots:mainName:)`.
                    // that reply, like the reference's `createGroup(named:bots:mainName:)`.
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
                        // The pencil: a new conversation of **this** group — the thread starts again
                        // blank, the chosen bots stay (same members, same room). An already
                        // empty thread is taken as is.
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
                // Shop stand-in: a scripted room whose read line is **injected**,
                // to exercise the speech scene where there is neither server nor microphone (debug only).
                // // And its variant that **plays the loop**: when the microphone-less engine is set
                // And its variant that **plays the loop**: when the microphone-less engine is set
                // (`onyx.voiceScenario`), the scripted room **answers** and it is the real session
                // that runs — listening, question, reply queue, resume. That is what makes the
                // conversation of a room provable on an emulator.
                val liveDemo = remember(groupVoiceDemoOn, voiceScenario) {
                    if (groupVoiceDemoOn && voiceScenario != null) GroupVoiceDemo.live(scope) else null
                }
                // On an emulator without a server, the app's Logs page stays out of reach:
                // the stand-in thus copies the **device's voice journal** into logcat, line by
                // line, so that a room's steps can be read. The `onyx.voiceJournal` flag
                // does the same copy outside the stand-in — it is what makes a room of a
                // **real** server readable, whose Logs page remains, here, unreachable. Debug only.
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
                // Two threads can feed the voice scene: that of a server room when one
                // is open (`openedRoom` — that is where the voice reads the server's replies), and
                // that of the local composer of a new room, whose members are not yet
                // on Hermes's side. Both ViewModels are taken with the **same key** as GROUP_CHAT: the
                // voice reads and advances the thread already opened by the screen, not a second room.
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

                // The room's faces, for the header and the orb: the **same** table as the thread
                // (`faceResolver`) — otherwise a bot shows the room's face then a creature
                // inferred from its name in the voice scene.
                val bots = ((liveDemo ?: demo)?.bots ?: if (roomViewModel != null) {
                    val roomState by roomViewModel.state.collectAsStateWithLifecycle()
                    roomState.room.composerBots(roomViewModel.face)
                } else {
                    val groupState by groupViewModel!!.state.collectAsStateWithLifecycle()
                    groupState.bots.filter { it.name in groupState.selected }
                }).withFaces(faceResolver::codeOf)

                // The room's names: it is they that tell the reading which voice goes to whom,
                // so that two bots do not read in the same voice (`BotVoices`).
                val roster = bots.map { it.name }
                LaunchedEffect(roster) { voice.output?.setBotRoster(roster) }

                // The room's thread is followed as long as the voice scene is there, like the
                // reference's `.task(id: scenePhase == .active || voice.isOn)`: the bots' replies
                // keep arriving while they are being read.
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
                // The room went mute **without answering anything**: the scene says so, instead of letting
                // "the bots are chatting" spin in a vacuum.
                val groupStalled by groupVoice.stalled.collectAsStateWithLifecycle()
                // What the room refused (a question that did not leave): without that, a bubble
                // disappeared without a word in the voice scene.
                val roomProblem by thread.problem.collectAsStateWithLifecycle()
                // The room went mute without saying anything: the banner says it, in French.
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
                // The queue: the replies waiting to be read, and the message in transit.
                val queued by groupVoice.queued.collectAsStateWithLifecycle()
                val voiceProblemShown by groupVoice.problem.collectAsStateWithLifecycle()
                val sending by thread.pendingSends.collectAsStateWithLifecycle()
                // The level of the read voice: it is what makes the big face's mouth beat.
                val replyLevel by voice.replyLevel.collectAsStateWithLifecycle()
                // The stand-in sets the scene it announces (speech or listening); otherwise, the real
                // phase and the bot the coordinator is currently reading.
                val shownPhase = demo?.phase ?: phase
                val shownSpeaker = demo?.speakerId ?: liveSpeakerId
                // The stand-in's line is simulated: its level beats like a voice, so that
                // the big face's mouth really moves on a capture.
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
                // The reference's `solIsWorking`: something is running — the voice, or a room that is speaking.
                val groupWorking = shownPhase == VoicePhase.LISTENING || shownPhase == VoicePhase.SPEAKING ||
                    (isTalking && !isHeld) || sending || queued > 0

                LaunchedEffect(groupVoice, demo) {
                    // The stand-in opens neither microphone nor playback: its line is injected.
                    if (demo != null) return@LaunchedEffect
                    voice.onQuestion = groupVoice::ask
                    voiceProblem = null
                    groupVoice.start()
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) groupVoice.listen() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
                DisposableEffect(groupVoice, demo) {
                    // **The stop order cuts the room's turn**, not the thread's chat: saying
                    // "stop listening" in a room must stop the bots, otherwise the turn
                    // starts again and the microphone reopens — the order was never obeyed.
                    val soloStop = voice.onStoppedByWord
                    if (demo == null) voice.onStoppedByWord = { groupVoice.halt() }
                    onDispose {
                        if (demo == null) {
                            groupVoice.stop()
                            voice.onQuestion = onSoloVoiceQuestion
                            voice.onStoppedByWord = soloStop
                        }
                    }
                }

                VoiceScreen(
                    phase = shownPhase,
                    heard = heard,
                    spoken = spoken,
                    level = speech.level,
                    // The level of the read line: the speaking face's mouth follows it.
                    speakingLevel = soundingLevel,
                    // A room is always in hands-free: each round hands the microphone back, and
                    // the room's header thus draws no switch for it.
                    handsFree = true,
                    conversationTitle = thread.roomName,
                    // What prevented a reply is read **in the scene**: the microphone or the engine
                    // (voice), the question refused by the room, the room that went mute without
                    // saying anything, a message still in transit. Never a mute screen.
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

            // "Connection details": `ConnectionDetailsView`, opened from Settings.
            Route.CONNECTION_DETAILS -> ConnectionDetailsScreen(
                server = state.server,
                onSave = { settingsViewModel.saveConnection(it) },
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
                // The probe is redone when the config signature changes: it is another
                // machine (or other credentials) in front, thus other capabilities.
                probe = remember(state.server.configSignature) {
                    com.onyx.app.data.net.ServerProbe(state.server)
                },
            )

            // "Hidden Bots": `HiddenBotsView`, opened by the grid's "Hidden" tile.
            Route.HIDDEN_BOTS -> HiddenBotsScreen(
                viewModel = botsViewModel,
                mainName = state.server.displayName,
                onBack = { if (!navBack()) navTo(Route.BOTS.name) },
            )

            // "Usage & cost": `SessionUsageView`.
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
                        model = usageModel,
                        onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                    )
                }
            }

            // "A project's sessions": `ProjectSessionsView`.
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
                            // leftover of the previous sheet.
                            // for its bot, so the in-force model is the server's — never a
                            // leftover of the previous sheet.
                            usageModel = com.onyx.app.ui.design.ModelInForce.inForce(
                                null,
                                null,
                                state.server.effectiveModel,
                            )
                            navTo(Route.SESSION_USAGE.name)
                        },
                        onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                        // Where to move a conversation: the projects that are a **folder**, without
                        // this one (the catch-all and the personal folder receive nothing).
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
                // The thread menu opens the computer: in two panes, the page left is the
                // **list** (which paints the same thread), so we pop before falling back on the thread.
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                // "Show once": the probe says whether this server serves the `onyx-record`
                // companion, and the skill editor receives the draft read back.
                server = state.server,
                probe = remember(state.server.configSignature) {
                    com.onyx.app.data.net.ServerProbe(state.server)
                },
                // The demonstration requested from the "+": the sheet opens here, once.
                teachRequested = teachRequested,
                onTeachRequestConsumed = { teachRequested = false },
                onDraftSkill = { name, content ->
                    skillName = null
                    skillSeed = SkillEditorSeed(name = name, content = content)
                    navTo(Route.SKILL_EDITOR.name)
                },
            )

            Route.LOCK -> LockSettingsScreen(onBack = { if (!navBack()) navTo(Route.SETTINGS.name) })

            Route.ACKNOWLEDGEMENTS -> AcknowledgementsScreen(onBack = { if (!navBack()) navTo(Route.SETTINGS.name) })

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
                    // "all-in-one" link: the same code also carries the voices, the
                    // "Computer" bridge and the language — a single paste, a single secret.
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
                // The Voice page opens from Settings **and** from the voice scene: back
                // returns where one comes from (the screen edge already returns there).
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
                // The freshest row: the thread (or the roster) updates it while one
                // watches, and the screen follows — an effective stop must show right away.
                val agent = subagentAgent?.let { picked -> shownSubagents.firstOrNull { it.id == picked.id } ?: picked }
                if (agent == null) {
                    // Outside composition: the empty route must not stay in the stack either.
                    LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }
                } else {
                    // transport does not carry it, and the screen then says so instead of waiting.
                    // transport does not carry it, and the screen then says so instead of waiting.
                    val controllable = agent.hasTranscript && !subagentDemoRun
                    // locally (it is a simulation, and the report says so), and without a
                    // transport carrying them they are not offered at all.
                    // transport carrying them they are not offered at all.
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
                                // // to serve it. The report says so.
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
                // // in progress, it is the only source of elapsed time and of the last tool.
                // in progress, it is the only source of elapsed time and of the last tool.
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
                    // // `navTo` written during composition leaves the back gesture without effect
                    // // when the empty state reproduces (the entry pushed back on every pass).
                    // when the empty state reproduces (the entry pushed back on every pass).
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
                        // goes to the system app, which may know how to open it.
                        // goes to the system app, which may know how to open it.
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

            // also taken from the illustration's shortcut, without going through the thread.
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
                        // The thread sheet opens the selection: the page left is popped first.
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
                        // Like the text selection: we go back up the page left.
                        onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                    )
                }
            }

            Route.TOOLS -> ToolsScreen(
                server = state.server,
                scope = scope,
                // Sidebar tile as Settings row: back returns the page left
                // (the thread), and does not jump to Settings when one comes from elsewhere.
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            // The "Skills" tile has been **removed** ("Capabilities" replaces it): its route has
            // left the enum with it. The **editor**, for its part, stays — "Capabilities" opens it from the
            // skills family, and its back returns "Capabilities", the page left.
            Route.SKILL_EDITOR -> SkillEditorScreen(
                server = state.server,
                existing = skillName,
                scope = scope,
                onSaved = { navTo(Route.CAPABILITIES.name) },
                onBack = { if (!navBack()) navTo(Route.CAPABILITIES.name) },
                // A demonstration draft arrives here, read back on the Computer screen: the name
                // and the Markdown are already there, the user only has to read and save.
                seed = skillSeed,
                // **"Make it a routine"**: the editor saves the skill then returns its
                // name; the schedules screen opens with the draft already filled (`SkillRoutine`).
                onMakeRoutine = { name ->
                    routineSeed = com.onyx.app.data.skill.SkillRoutine.from(name)
                    skillSeed = null
                    navTo(Route.CRON.name)
                },
            )

            // "Capabilities": the profile's skills, tool sets and MCP servers,
            // each with its switch (the catalogue is a page of this screen). Since the
            // removal of the "Skills" tile, it also carries the **writing** of a skill.
            Route.CAPABILITIES -> CapabilitiesScreen(
                server = state.server,
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
                onEditSkill = { name ->
                    skillName = name
                    skillSeed = null
                    navTo(Route.SKILL_EDITOR.name)
                },
            )

            // **"The Vault"** — the profile's secrets, and **never** a value on screen. It
            // opens by its Settings row ("Secrets" section) and by the icon's shortcut;
            // both only exist if the probe has proven the capability. Its back returns
            // the page left, and Settings by default.
            // **The agent's browser** — its screen, full page. It opens by its row in
            // Settings ("Browser" section), which only exists if the plugin answers.
            Route.BROWSER_SCREEN -> BrowserScreen(
                viewModel = viewModel(
                    key = "browser-screen-" + state.server.id + state.server.configSignature,
                    factory = viewModelFactory {
                        initializer { BrowserScreenViewModel(context.applicationContext as android.app.Application, state.server) }
                    },
                ),
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            Route.VAULT -> VaultScreen(
                server = state.server,
                onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
            )

            // "Archives" full page: the **same** list as the one the sidebar carries. It only
            // opens by the tile (which stacks in the sidebar); this branch is the fallback of
            // the caller outside the sidebar, and keeps the route alive like its neighbours.
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

            // "Agent ledger": the open conversation (its thread, plus its row in the session
            // registry), or a bot in aggregate — the same rows, at the scale of what this
            // phone attaches to it. Prices come from the server, and nothing is computed without them.
            Route.LEDGER -> AgentLedgerScreen(
                server = state.server,
                conversations = history,
                bots = bots,
                mainName = state.server.displayName.ifBlank { "Onyx" },
                openConversation = history.firstOrNull { it.id == state.conversationId },
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                standIn = ledgerStandIn,
                // The "Model" row and the looked-up price follow the **in-force** model of the
                // open conversation — the same source as the header and the usage sheet.
                openModelInForce = state.modelInForce,
            )

            // "Insights": what this profile consumed over 7, 30 or 90 days. The period is
            // remembered by the device, so reading it again after a restart finds the same choice.
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
                    // Opened from Settings **or** at the end of configuration (`SETUP`): back
                    // thus pops the stack before falling back on Settings.
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
                    // "Skip" here too exits without saving anything: opening the editor from
                    // Settings must not be able to empty a registered server.
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
                        // "A key": the Dashboard lives at the same address as the chat — see the
                        // note of the same gesture above.
                        if (found.declaresDashboard) {
                            setupDraft = setupDraft.copy(serveBaseUrl = address.trim())
                        }
                        found
                    },
                    onApplyDiscovery = { found -> scope.launch { applyDiscovery(found) } },
                    // **What "Simple" mode offers**: the already known addresses, in
                    // order of relevance (current server, phone's network, previous
                    // gateway, other registered servers).
                    suggestions = com.onyx.app.ui.setup.SetupSuggestions.known(
                        current = com.onyx.app.ui.setup.setupAddressOf(state.server),
                        local = setupLocalAddresses.value,
                        lastGateway = serverList
                            .firstOrNull { it.id != state.server.id }
                                ?.let(::setupAddressOf)
                            .orEmpty(),
                        saved = serverList
                            .filter { it.id != state.server.id }
                            .drop(1)
                            .map(::setupAddressOf),
                    ),
                    // The draft's configuration link, **without its secrets**: the title bar's
                    // "Copy".
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
                        // **This branch's payload is not saveable** (`contextEditor`
                        // is a `remember`: a `Triple` does not go into a `Bundle`). After a process
                        // death, we thus return to this route **without** its editor — and the
                        // fallback returned **Settings**, whereas the page left is the one
                        // `contextReturn` carries (the bot's editor). Back must not land
                        // anywhere other than where one was: we return the page left, like the other
                        // empty-state screens (`navFromEmpty`, the empty route does not enter the stack).
                        LaunchedEffect(Unit) { navFromEmpty(contextReturn) }

                    else -> {
                        // A bot's personality keeps the generic editor's profile route.
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
                            // **One level at a time, like the file branch just above.** Back
                            // was written `navTo(contextReturn)`: it did not pop the stack
                            // first, and it only popped `contextReturn` as long as it was
                            // **at the top**. As soon as the screen is reached from elsewhere (or after
                            // a restoration), it **stacks** the page left instead of returning it —
                            // the defect that `ReturnCoherenceTest` has already removed from five other screens.
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
                    // Sidebar tile ("Server files") as Settings row: back
                    // returns the page left (the thread) — it does not jump to Settings.
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
                            // Without a reachable Dashboard, the page opens on the **voice** journal,
                            // which is local to the device — the server's three journals, for their part,
                            // require the Gateway.
                            LogsViewModel(
                                config = state.server,
                                localOnly = !state.server.canReachGateway,
                            )
                        }
                    },
                )
                LogsScreen(
                    viewModel = logsViewModel,
                    // The logical parent is **where one comes from**: the Gateway when one was there,
                    // Settings otherwise (the "Logs" row is there, without a padlock).
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
                    // The model lives on the server: we rewrite it there, and the repository persists it.
                    settingsViewModel.open(state.server)
                    settingsViewModel.update { it.copy(model = model) }
                    settingsViewModel.saveAndClose()
                    navTo(Route.CHAT.name)
                },
                onBack = { if (!navBack()) navTo(Route.CHAT.name) },
                serverSessionId = state.serverSessionId,
                // The **in-force** model of the open conversation, to check the right entry
                // (and not the configured model) when a fallback is at work.
                inForceModel = state.modelInForce,
                )
            }
                    }
                }

              }}
            // Same-window glass stays outside the animated, recorded route subtree.
            OverlayLayer(overlayHost)
        }

        if (openingRect != null) {
            com.onyx.app.ui.design.OpeningCover(
                source = openingRect,
                title = "",
                // Nothing to exchange underneath: the chat is already open. The surface withdraws at the
                // end of its fade (the `clearing` of the reference's `OpeningCover.run`) — erasing it
                // at the exchange cut the fade and let the cover disappear at once.
                onFinished = { openingRect = null },
            )
        }

        // **The sidebar's account sheet**: the gateway's **measured** state and the five
        // connection gestures. It is set above the sidebar, which stays open behind; a press
        // beside it, a swipe down or hardware back close it, and back closes
        // **the sheet** before anything else (it lives in its `Dialog`).
        if (accountSheetOpen) {
            AccountSheet(
                address = state.server.serveBaseUrl.ifBlank { state.server.baseUrl },
                server = state.server,
                onDismiss = { accountSheetOpen = false },
                onOpenSettings = { navTo(Route.SETTINGS.name) },
                onRefreshConversations = {
                    // // report is the repository's **after** the re-read, never the one before.
                    // report is the repository's **after** the re-read, never the one before.
                    botsViewModel.refresh()
                    conversationRepo.load()
                    conversationRepo.conversations.value.size
                },
                // // same destination as "Configure Onyx". Nothing is deleted from the server.
                // same destination as "Configure Onyx". Nothing is deleted from the server.
                onDisconnect = { navTo(Route.SETUP.name) },
                // // and the addition.
                // and the addition.
                onChangeGateway = { navTo(Route.SERVERS.name) },
            )
        }

        // // `confirmationDialog` as the reference, with its two outcomes.
        // `confirmationDialog` as the reference, with its two outcomes.
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

        // // that **the old one stays in the history** — that is exactly what distinguishes it from a
        // // deletion, and the sentence says so.
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

        // // for a project's screen: the sheets live here, and the call leaves from here.
        // for a project's screen: the sheets live here, and the call leaves from here.
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
                // The name changes: the open screen, if it is that one, keeps its title just.
                if (selectedProject?.id == project.id) selectedProject = project.copy(label = name)
                runProjectAction {
                    com.onyx.app.ui.conversations.ProjectManage.rename(state.server, project.id, name)
                }
            },
            onRemove = { project ->
                // Removing the **open** project returns to the list; the folder and the conversations,
                // for their part, stay on the server.
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
}


/** The main window's layer** — the anchored menu lives there.
 * **The main window's layer** — the anchored menu lives there.
 * It is drawn **above the route**, in the **main window**: that is what lets it
 * read the screen's blur source (set in this window) and thus paint **glass**
 * rather than a veil. A `Dialog` could not — it is another window, and the
 * panel then read as a translucent gray rectangle set on the screen.
 * panel then read as a translucent gray rectangle set on the screen.
 */
@Composable
private fun OverlayLayer(host: OverlayHost) {
    val layer = host.layer ?: return
    Box(modifier = Modifier.fillMaxSize()) { layer() }
}
/**
 * The emulator has no microphone: without this level, the "listening" pose would show a wave at
 * rest — seven dots at the same height — and nothing would prove it follows the voice. The signal
 * follows **syllables** (sharp rise, gentle fall) interspersed with breaths, in the manner
 * of a sentence. It is only read under the debug flag: outside the stand-in, the wave takes the
 * engine's real level (`speech.level`).
 * d'une phrase. Il n'est lu que sous le drapeau de debug : hors de la maquette, la vague prend le
 * engine's real level (`speech.level`).
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
