package com.onyx.app.di

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.onyx.app.data.local.DraftStore
import com.onyx.app.data.local.SecretStore
import com.onyx.app.data.local.SharedPreferencesDeletedConversations
import com.onyx.app.data.local.SettingsKeys
import com.onyx.app.data.local.conversationsDataStore
import com.onyx.app.data.repo.ConversationRepository
import com.onyx.app.data.repo.ServerRepository
import com.onyx.app.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The application singletons — no injection framework, just a container installed
 * by [com.onyx.app.OnyxApplication].
 *
 * Order matters: repositories fill from disk at startup, and screens
 * observe already-loaded `StateFlow`s.
 */
object AppContainer {

    private lateinit var applicationContext: Context

    /** Process scope: startup disk reads belong to no screen. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The process scope, for what must outlive a screen (the call bridge). */
    val processScope: CoroutineScope get() = scope

    lateinit var servers: ServerRepository
        private set

    lateinit var conversations: ConversationRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    lateinit var drafts: DraftStore
        private set

    /** Hand-added faces and profiles: "Erase everything" wipes them too. */
    lateinit var botFaces: com.onyx.app.ui.bots.BotFaceStore
        private set

    /**
     * The conversation turns in progress, **above the screens**.
     *
     * Its scope is the process scope: leaving the conversation, destroying the activity or turning
     * off the screen no longer interrupts the work, and the thread finds it again on return.
     */
    lateinit var turns: com.onyx.app.ui.chat.TurnStore
        private set

    /**
     * Bot work tracking: the progress notification and the completion notification.
     *
     * Held by the process, like the turns it watches — that is what lets it announce
     * a reply that arrived while no screen was waiting for it.
     */
    lateinit var work: com.onyx.app.notifications.TurnWorkNotifier
        private set

    /**
     * The bridge for the **agent calling the phone** (`phone_call_me`).
     *
     * Held by the process, like work tracking: a call that arrives while no screen
     * is waiting must still ring, and its outcome must be reported back to the agent.
     */
    lateinit var agentCalls: com.onyx.app.calls.AgentCallBridge
        private set

    /** The group rooms kept on this phone (a "New Group Chat" without a Dashboard). */
    lateinit var groupRooms: com.onyx.app.data.local.GroupRoomStore
        private set

    /** Where to reach the "Computer" bridge and its token: the address here, the token in the Keystore. */
    lateinit var computer: com.onyx.app.data.local.ComputerBridgeStore
        private set

    /**
     * The "Computer" session, held by the process and not by the screen: leaving the preview and
     * coming back finds the same connection, the same image and the same hand.
     */
    lateinit var computerSession: com.onyx.app.ui.computer.ComputerSession
        private set

    /** True once [install] has run: the widget uses it to wait for the repository. */
    val isInstalled: Boolean get() = ::servers.isInitialized

    /**
     * The application context, once [install] has run.
     *
     * What services without a screen read: the icon shortcuts
     * ([com.onyx.app.shortcuts.RosterShortcuts]) are republished from the bots section, a
     * `ViewModel` — and a `ViewModel` has no context.
     */
    val appContext: Context get() = applicationContext

    /** To be called once, from `Application.onCreate`. */
    fun install(context: Context) {
        if (isInstalled) return
        applicationContext = context.applicationContext
        val preferences = applicationContext.onyxStore
        val secrets = SecretStore(applicationContext)
        settings = SettingsRepository(preferences)
        // The chosen language is applied **before** the first composition: otherwise the UI
        // would show for a fraction of a second in the Android language before following the choice.
        // The read is synchronous (a small file) and only delays process startup.
        //
        // **Theme and appearance take the same path, and must take it**: `load()` is
        // asynchronous, so without this read the first frame would be rendered with the
        // `AppearanceMode.SYSTEM` default. On a light phone, a user who chose
        // "Dark" would reopen the app in light mode until `load()` finished.
        kotlinx.coroutines.runBlocking {
            settings.applyStoredLanguage()
            settings.applyStoredTheme()
        }
        servers = ServerRepository(preferences, secrets)
        conversations = ConversationRepository(
            conversationsDataStore(applicationContext),
            // The deleted-conversations marker: in the phone preferences, so that
            // a deletion comes back neither on restart nor through a path that rewrites the thread.
            SharedPreferencesDeletedConversations(applicationContext),
        )
        com.onyx.app.ui.rich.WebBlockHeights.install(applicationContext)
        com.onyx.app.data.link.LinkPreviews.install(applicationContext)
        drafts = DraftStore(applicationContext)
        botFaces = com.onyx.app.ui.bots.BotFaceStore(applicationContext)
        groupRooms = com.onyx.app.data.local.GroupRoomStore(applicationContext)
        // Turns run in the process scope: their reply is written to disk even
        // when no screen is watching it (the app is in the background, the activity is destroyed).
        turns = com.onyx.app.ui.chat.TurnStore(
            scope = scope,
            journal = object : com.onyx.app.ui.chat.TurnJournal {
                override fun noteAssistant(conversationId: String, message: com.onyx.app.data.model.ChatMessage) {
                    scope.launch { conversations.upsertMessage(conversationId, message) }
                }

                override fun turnEnded(conversationId: String, session: com.onyx.app.ui.chat.TurnSession) {
                    scope.launch { conversations.upsertMessage(conversationId, session.message) }
                }
            },
        )
        // Work tracking: the progress and completion notifications, held by the
        // process and wired to the turn store. The thread name comes from the conversations.
        work = com.onyx.app.notifications.TurnWorkNotifier(
            context = applicationContext,
            turns = turns,
            settings = settings,
            titleOf = { conversationId ->
                runCatching { conversations.find(conversationId)?.title }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: com.onyx.app.notifications.NotificationCopy.ONYX
            },
        )
        work.start(scope)
        // The agent-call bridge: the loop that waits for a `phone_call_me` and makes it
        // ring. Its scope is the process scope (like work tracking); it starts when
        // "Calls for a reply" is on, and stops when it is off.
        agentCalls = com.onyx.app.calls.AgentCallBridge(
            context = applicationContext,
            settings = settings,
            servers = servers,
            titleOf = { conversationId ->
                runCatching { conversations.find(conversationId)?.title }.getOrNull()?.takeIf { it.isNotBlank() }
            },
        )
        scope.launch {
            settings.phoneRings.collect { on ->
                if (on) {
                    agentCalls.start(scope)
                    // "The line stays open": the foreground service keeps the process
                    // alive so that a call arriving with the screen off still rings.
                    runCatching { com.onyx.app.notifications.AgentCallService.start(applicationContext) }
                } else {
                    agentCalls.stop()
                    runCatching { com.onyx.app.notifications.AgentCallService.stop(applicationContext) }
                }
            }
        }
        // The Computer screen: the companion bridge lives on the desktop, the app attaches to it by its address.
        computer = com.onyx.app.data.local.ComputerBridgeStore(applicationContext)
        computerSession = com.onyx.app.ui.computer.ComputerSession(computer)
        scope.launch {
            settings.load()
            servers.load()
            conversations.load()
        }
    }

    /**
     * "Erase everything", the `eraseEverything()` of `ResetSettings` (SettingsSections:707):
     * the conversations and their drafts, the servers with their keys and credentials, the
     * bot faces, and the settings restored to their original values.
     *
     * Nothing on the servers is touched: their conversations and files stay there, as
     * the reference footer says.
     */
    suspend fun eraseEverything() {
        if (!isInstalled) return
        conversations.eraseAll()
        drafts.clearAll()
        botFaces.clearAll()
        // The cached roster is part of what the phone knows about bots: it is erased with
        // them, otherwise an erased server would leave the old faces still displayed.
        com.onyx.app.data.local.BotRosterCache(applicationContext).clearAll()
        com.onyx.app.data.local.BotPresenceIndex(applicationContext).clear()
        groupRooms.clearAll()
        com.onyx.app.data.link.LinkPreviews.shared.clear()
        // Settings first: they share the same preferences file, and clearing them
        // would erase the factory server just written there.
        settings.reset()
        servers.eraseAll()
        applicationContext.onyxStore.edit { preferences ->
            preferences.remove(SettingsKeys.WIDGET_CONTEXT_USED)
            preferences.remove(SettingsKeys.WIDGET_CONTEXT_WINDOW)
        }
        reload()
    }

    /** Keeps the context fill for the widget, which does not read the conversation. */
    fun publishContext(used: Int?, window: Int?) {
        if (!isInstalled) return
        if (used == null || window == null || window <= 0) return
        scope.launch {
            applicationContext.onyxStore.edit {
                it[SettingsKeys.WIDGET_CONTEXT_USED] = used
                it[SettingsKeys.WIDGET_CONTEXT_WINDOW] = window
            }
        }
    }

    /** Re-reads everything from disk — after a restore, or for the widget. */
    fun reload() {
        if (!isInstalled) return
        scope.launch {
            settings.load()
            servers.load()
            conversations.load()
        }
    }

    /** The raw preferences, for what has no repository (the widget reads two integers). */
    val preferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
        get() = applicationContext.onyxStore

    private val Context.onyxStore by preferencesDataStore(name = "onyx_settings")

    /** Settings keys, exposed for screens that read them directly. */
    val settingsKeys = SettingsKeys
}
