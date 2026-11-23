package com.onyx.app.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.local.BotRosterCache
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Attachment
import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.OutboxState
import com.onyx.app.data.model.ReplyCards
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.local.DraftStore
import com.onyx.app.data.model.Transport
import com.onyx.app.data.model.TurnMetrics
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.GatewayApprovalClient
import com.onyx.app.data.net.HermesServeChat
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeSessionsApi
import com.onyx.app.data.net.ServeSessionMessages
import com.onyx.app.data.net.ServeSessionMessages.Companion.THREAD_PAGE
import com.onyx.app.data.net.ServeSkillsApi
import com.onyx.app.data.net.ServerSessions
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.data.net.asChatChannel
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.array
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.net.firstString
import com.onyx.app.data.repo.ConversationRepository
import com.onyx.app.data.repo.ServerRepository
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.shortcuts.BotShortcutInbox
import com.onyx.app.shortcuts.BotShortcutRequest
import com.onyx.app.ui.bots.botChatToOpen
import com.onyx.app.ui.bots.botOfConversation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Drives a conversation: sends the turn to the server, applies stream events, exposes the
 * state to the screen and records the thread.
 *
 * Partial mirror of the reference `Conversation`: `startTurn` becomes [send], `apply(_:to:)`
 * becomes [applyEvent], and `ConversationStore` is here [ConversationRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
/** The calm before querying the server for the "/" menu (ms). */
private const val SlashCompleteDebounceMs = 120L

/** The longest wait of a turn before a "/" command gives up (10 minutes). */
private const val SlashWaitTimeoutMs = 10 * 60 * 1000L

/**
 * The two timings of the import retry after a reinstall.
 *
 * Fast first attempt (the server often answers from the second frame), then doubling up to
 * one minute: at worst one read per minute while it fails, and none at all once it is done.
 * Without this ceiling, an unreachable server would spin a tight loop.
 */
private const val IMPORT_RETRY_FIRST_MS = 3_000L
private const val IMPORT_RETRY_CEILING_MS = 60_000L

/**
 * How many Dashboard channels stay open, one per bot profile.
 *
 * Three: the bot being left, the one being opened and the next one often overlap (back-and-forth
 * between two bots, a third one in the conversation). Beyond that, the oldest closes — a
 * forgotten socket costs as much as a failed handshake.
 */
private const val ServeChannelLimit = 3

class ChatViewModel(
    application: Application,
    private val servers: ServerRepository,
    private val conversations: ConversationRepository,
    private val settings: SettingsRepository,
    private val drafts: DraftStore,
    /**
     * The process's in-flight turns. The turn lives there, **above the screen**: leaving the
     * conversation or destroying the activity does not stop it, and the thread finds it on return.
     */
    private val turns: TurnStore,
) : AndroidViewModel(application) {

    private var conversationId: String = UUID.randomUUID().toString()

    /**
     * The roster kept on this phone, read by the server session import to know
     * which sessions are **a bot's chat** — and not conversations to import.
     */
    private val roster = BotRosterCache(getApplication<Application>())

    private val _state = MutableStateFlow(
        ChatUiState(server = servers.activeServer.value, conversationId = conversationId),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** The list shown by the conversations screen, most recent first. */
    val history: StateFlow<List<Conversation>> = conversations.conversations

    /**
     * **What the server conversation import produced, said on screen.**
     *
     * Three successive fixes were not enough because this import failed **silently**:
     * a read that throws, a server answering 401, a profile that does not exist — everything ended
     * in a `runCatching` and the screen stayed empty without a word. That is not diagnosed from
     * a screenshot, and the owner should not have to play the guesser.
     *
     * `null` = all is well. Otherwise: the reason, in plain terms, shown above the list.
     */
    private val _importNote = MutableStateFlow<String?>(null)
    val importNote: StateFlow<String?> = _importNote.asStateFlow()

    private var lastRequest: ChatRequest? = null
    private val runControls = RunChatControlsStore(application)

    /** The turns whose outcome has already been settled (each only once). */
    private val settledTurns = mutableSetOf<String>()

    /** Server-side session identifier, when the transport holds one. */
    private var serverSessionId: String? = null

    /** The name given to the conversation ("Rename" ("Renommer")), or `null` while it only has its question. */
    private var conversationName: String? = null

    /** The messages written during a response: they leave one after another at the end. */
    private val queued = OutboxQueue()
    private var retryJob: Job? = null
    private val serverImportGate = Mutex()
    /** The catch-up of a turn finished without us (see [resumeDetachedTurn]): only one at a time. */
    private var recoveryJob: Job? = null
    /** The silence watcher: it re-reads the session when the stream sends nothing more. */
    private var rescueJob: Job? = null
    /** The older page of the open thread (see [loadEarlier]): only one fetch at a time. */
    private var earlierJob: Job? = null
    private var retryAttempt = 0
    private val retryDelaysMs = longArrayOf(2_000, 5_000, 15_000, 30_000, 60_000)
    /** Initial age of the turns taken out of the queue, per reply bubble. */
    private val heldSince = mutableMapOf<String, Long>()

    /**
     * **The Dashboard channels, kept open — one per (server, profile).**
     *
     * A `ServeRpc` freezes its profile; that is why a single field was not enough. Throwing the channel
     * away on every bot change repaid a login, a ticket and a WebSocket handshake **on every
     * switch** — and the return trip cost as much as the way there. One channel per profile thus keeps
     * every bot's conversation ready: changing bots means taking a socket already
     * open, and coming back to it costs nothing.
     *
     * Three channels at most ([ServeChannelLimit]): beyond that, the oldest closes. The key is
     * [serveChannelKey] — the address and the credentials are in it, the model is not.
     */
    private val serveChannels =
        object : LinkedHashMap<String, com.onyx.app.data.net.ServeRpc>(4, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, com.onyx.app.data.net.ServeRpc>,
            ): Boolean {
                if (size <= ServeChannelLimit) return false
                // The evicted channel has nobody left: close it rather than leave a
                // socket without an owner (which is what `close()` already protects elsewhere).
                runCatching { eldest.value.close() }
                return true
            }
        }

    /** The Dashboard channel of **this** server and of **this** profile, open or to be opened. */
    private fun serveChannel(server: ServerConfig): com.onyx.app.data.net.ServeRpc {
        val key = com.onyx.app.data.net.serveChannelKey(server)
        serveChannels[key]?.let { return it }
        return com.onyx.app.data.net.ServeRpc(server).also { serveChannels[key] = it }
    }

    /**
     * Keeps only one server's channels: the others talk to an address or with
     * credentials that are no longer the app's.
     */
    private fun retainServeChannels(key: String?) {
        val doomed = serveChannels.entries.filter { it.key != key }
        doomed.forEach { (k, channel) ->
            serveChannels.remove(k)
            runCatching { channel.close() }
        }
    }

    /**
     * The lock of a send: attachments are encoded off the main thread, so two
     * close sends can cross — without it, a large photo could let
     * the second message leave before the first.
     */
    private val sendGate = Mutex()

    /**
     * The Dashboard channel, **preheated**.
     *
     * `ServeRpc.connect()` does the login then requests a WebSocket ticket: two round trips
     * that, paid at the moment of the first send, made every first message wait. We pay them
     * here, as soon as the server (or the bot profile) is known; `connect()` does nothing if
     * the channel is already open, and the turn finds it ready.
     */
    private fun preheatServe(server: ServerConfig) {
        if (server.transport != Transport.HERMES_SERVE) return
        val channel = serveChannel(server)
        viewModelScope.launch { runCatching { channel.connect() } }
    }

    /**
     * The **runtime** identifier of the session opened on the Dashboard, the one under which the
     * gateway registers it. `subagent.list` / `.tail` / `.steer` / `.interrupt` look for it;
     * the identifier kept on the app side (`serverSessionId`) is only a resume key.
     */
    private var runtimeSessionId: String? = null
    private var slashJob: kotlinx.coroutines.Job? = null

    /**
     * **The list that the menu and the execution read**, and nothing else: the six fixed commands,
     * then the profile's skills ([refreshSlashCommands]).
     *
     * It starts without a skill: before the first read, the app behaves exactly as
     * before this batch.
     */
    private var slashCommands: List<SlashCommand> = SlashCommands.commands()

    /** The skills read in progress (`api/skills`). */
    private var skillsJob: Job? = null

    /** When `api/skills` was last read: at most one re-read per minute. */
    private var skillsLoadedAt = 0L

    /**
     * The skills read, **per profile**, with the time of the read.
     *
     * Changing bots changes profile, hence list: without this cache, returning to a bot just
     * left re-requested `api/skills` — one round trip per switch. The window is
     * that of [SKILLS_TTL_MS], and the cache is emptied when the server changes.
     */
    private val skillsByProfile = mutableMapOf<String, Pair<Long, List<SlashCommand>>>()

    /**
     * A thread's already resolved cards, per conversation: the source list and its result.
     *
     * Resolving the cards re-reads the tool outputs of **every** message, JSON included: on a
     * long thread, that was a full pass on every opening, on the main thread — and a
     * bot change opens a thread. The cache keeps the **source** list (the repository's,
     * immutable): returning to an untouched thread no longer re-reads anything.
     */
    private val cardsCache =
        object : LinkedHashMap<String, Pair<List<ChatMessage>, List<ChatMessage>>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<List<ChatMessage>, List<ChatMessage>>>) =
                size > 4
        }

    /**
     * True once the most recent conversation is restored (or a new one started).
     *
     * Without this lock, the empty thread of a new conversation would look like the app's
     * startup, and the repository's first save would reopen the one just left.
     */
    private var restoredInitial = false

    init {
        // A server change (Settings) empties the thread: history no longer makes sense.
        // An EDIT of the configuration (same id), however, must be adopted: otherwise
        // "Done" on the connection screen does save the server but the state keeps
        // the old config, the app still believes it has no server and shows the form again
        // (symptom: the Done button seems to do nothing).
        viewModelScope.launch {
            servers.activeServer.collect { server ->
                val current = _state.value.server
                if (server.id != current.id) {
                    persistCurrent()
                    // The old server's turns have nobody left to answer to: they are
                    // stopped (the host is notified), not abandoned.
                    turns.stopAll()
                    // The old server's channels talk to an address (or with
                    // credentials) that are no longer the app's: only they go away. Those of the
                    // new server stay, including those of the other bot profiles.
                    retainServeChannels(com.onyx.app.data.net.serveChannelKey(server))
                    settledTurns.clear()
                    queued.clear()
                    retryJob?.cancel()
                    retryJob = null
                    heldSince.clear()
                    serverSessionId = null
                    conversationId = UUID.randomUUID().toString()
                    conversationName = null
                    restoredInitial = false
                    _state.update {
                        it.copy(
                            server = server,
                            conversationId = conversationId,
                            serverSessionId = null,
                            conversationName = null,
                            messages = emptyList(),
                            queued = emptyList(),
                            attachments = emptyList(),
                            error = null,
                            canRetry = false,
                        )
                    }
                } else if (server != current) {
                    // An edit of the **same** server's configuration is adopted. When it is the
                    // **model** that changed (the model sheet writes `ServerConfig.model` for a
                    // plain conversation), the server echo ([ChatUiState.modelInUse]) becomes
                    // stale: we lower it so the new choice takes effect on display **and** on
                    // send. Without that, the "conversation" stage kept the old served model and the
                    // choice applied nowhere.
                    val modelChanged = server.model != current.model
                    _state.update { it.copy(server = server, modelInUse = if (modelChanged) null else it.modelInUse) }
                }
                // Skills live in the profile: another server, another profile or
                // other credentials change the "/" command list — and nothing of what
                // was read on the other server holds here.
                skillsByProfile.clear()
                refreshSlashCommands(force = true)
                // The Dashboard channel is preheated for **this** server (no effect on the
                // other transports): the first send no longer pays the login or the ticket.
                preheatServe(server)
            }
        }
        // The most recent conversation for this server, only once, if there is one.
        viewModelScope.launch {
            conversations.conversations.collect { list ->
                // NEVER restore during an in-flight turn: saving the
                // conversation (triggered by the send) makes this list emit, and the
                // restoration would overwrite the assistant message being streamed — the
                // server's reply would have no bubble to write into (mute chat: the
                // server answers in 1.6 s but nothing shows).
                if (restoredInitial || _state.value.isStreaming || _state.value.messages.isNotEmpty()) {
                    return@collect
                }
                val latest = list.firstOrNull { it.serverId == _state.value.server.id && !it.isEmpty }
                    ?: return@collect
                restoredInitial = true
                open(latest)
            }
        }
        // The turn is written in the store, not in this screen: we attach to it. A turn started
        // before the screen was recreated (return to the app) is therefore found whole, in flight.
        viewModelScope.launch {
            _state.map { it.conversationId }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(null) else turns.live(id) }
                .collect { live -> follow(live) }
        }
        // The **server's** conversations: the local registry only shows what the app has
        // written on this phone. The server's sessions — other devices, the CLI, the
        // old ones — are imported here, once per server: without that, the pane stayed empty
        // while the server had dozens.
        viewModelScope.launch {
            // **One import per server, not per emission.** `activeServer` re-emits on every setting
            // touched (theme, font, profile, model): the import then restarted a read of all
            // profiles and a full write of the history **per change**, for an identical
            // result. The key is the config signature: it changes when the
            // server changes, not when a display setting moves.
            var importedFor: String? = null
            servers.activeServer.collect { server ->
                if (!server.hasServeCredentials) return@collect
                if (importedFor == server.configSignature) return@collect
                // **An import that fails is not marked as done.**
                //
                // That was the reinstall's flaw: the mark was set even when the
                // read had failed — server not yet reachable, tunnel starting, network
                // not ready on the first frames. The "already imported for this server" condition
                // was therefore true forever, and the app stayed empty **without ever retrying**.
                // That is exactly what the owner saw: "I reinstalled, my
                // conversations only come back in Home".
                //
                // We therefore retry until it passes, at growing intervals, capped:
                // one read per minute at worst, and none once it is done.
                var attente = IMPORT_RETRY_FIRST_MS
                while (importedFor != server.configSignature) {
                    if (importServerSessions(server)) {
                        importedFor = server.configSignature
                    } else {
                        kotlinx.coroutines.delay(attente)
                        attente = (attente * 2).coerceAtMost(IMPORT_RETRY_CEILING_MS)
                    }
                }
            }
        }
        // The menu that opens on a long press on the icon: a **bot** shortcut opens that
        // bot's conversation. The request was posted by the activity ([BotShortcutInbox]);
        // it opens here, once, then is consumed — taking the same shortcut twice
        // opens twice, but returning to the app reopens nothing.
        viewModelScope.launch {
            BotShortcutInbox.requested.collect { request ->
                if (request == null) return@collect
                BotShortcutInbox.consume()
                openBotShortcut(request)
            }
        }
    }

    /**
     * Imports the server's sessions into the local registry, so the pane shows them.
     *
     * The local registry only knows the conversations **written on this phone**. The
     * server's sessions — those of other devices, those of the CLI, the old ones —
     * are not there: the pane then appeared empty while the server had
     * dozens. Each session becomes a conversation without messages: the thread is read on
     * opening, never in advance.
     *
     * A read failure breaks nothing: the local registry stays, and the app continues.
     */
    fun retryServerConversations() {
        viewModelScope.launch {
            val server = servers.activeServer.value
            if (server.hasServeCredentials) importServerSessions(server)
        }
    }

    private suspend fun importServerSessions(server: ServerConfig): Boolean = serverImportGate.withLock {
        importServerSessionsLocked(server)
    }

    private suspend fun importServerSessionsLocked(server: ServerConfig): Boolean {
        if (conversations.conversations.value.isEmpty()) conversations.load()
        // **The profiles to query**: those of the bots kept on this phone, plus the
        // server's. That is what gives each session the profile that carries it — without it, a
        // conversation reimported after a reinstall loses its bot and takes on Onyx's face.
        val cached = runCatching { roster.load(server.rosterScope) }.getOrNull()
        // **At least one read, the default profile's.**
        //
        // `effectiveProfile` returns `null` when the server is on "default": the list of profiles
        // to query thus became **empty** as soon as the kept roster also was — that is,
        // exactly after a reinstall. `summariesByProfile` looped over nothing, read
        // nothing, and the import reported success: the list stayed empty, the import marked done, and
        // no further attempt. That is the "always empty" that was observed.
        //
        // The default profile has no name on this route (`named()` strips "default" and the
        // empty) : we therefore query **with the empty string**, which means "the server's profile".
        val profiles = (listOf(server.hermesProfile) + cached?.bots.orEmpty().map { it.name })
            .map { it.trim() }
            .distinct()
        val api = ServeSessionsApi(ServeClient(server))
        _importNote.value = OnyxLocalization["Refreshing server conversations. Saved/local conversations remain available; counts are not yet refreshed."]
        var tagged = runCatching { api.summariesByProfile(profiles) }.getOrElse { error ->
            // **Said, not swallowed.** This is the reason missing from the three screenshots.
            _importNote.value = OnyxLocalization.format(
                "The server's conversations did not load: %1\$s",
                humanFailure(error),
            ) + " " + OnyxLocalization["Showing saved/local conversations; counts are not a refreshed server total. Check authentication in Settings or retry a temporary connection failure."]
            return false
        }
        // **The fallback that has already proved itself.**
        //
        // The profile-by-profile read returned zero rows while the server answered fifteen
        // rows at the same instant (raw probe: `{"object":"list","data":[{"id":"api-9ff07d…`).
        // The Home screen, for its part, shows them all — it reads via `allSummaries()`, without a list of
        // profiles. A read that fails **must not leave the list empty**: we redo the
        // simple read, the one that works, even at the cost of losing the profile tag (the face
        // is restored afterwards by the bot registry).
        var lues = tagged
        // Three empty reads in a row mean "there is nothing"; below that, we retry.
        var vides = 0
        if (lues.isEmpty()) {
            val simple = runCatching { api.allSummaries() }.getOrDefault(emptyList())
            if (simple.isNotEmpty()) {
                val etiquette = server.hermesProfile.trim()
                lues = simple.map { summary -> etiquette to summary }
            }
        }
        // A read that answered "no session" is a success: there is nothing to restore.
        // But it is **said**: it is the silence of this case that cost four versions.
        if (lues.isEmpty()) {
            _importNote.value = OnyxLocalization.format("%1\$d sessions read, %2\$d imported", 0, 0)
            vides += 1
            return vides >= 3
        }
        tagged = lues
        val summaries = tagged.map { (_, summary) -> summary }
        val profileById = tagged.associate { (profile, summary) -> summary.id to profile }
        val known = conversations.conversations.value
        // **The roster bots' chats are not conversations.**
        //
        // The roster kept on this phone (`BotRosterCache`) names each bot's `canonical_session`.
        // Without this list, the import took them for ordinary conversations: an **empty**
        // row per bot, titled with its name and with no preview, appeared all by itself in the list
        // as soon as a conversation was created with one of them ("I have 3 bots, I should only have the
        // bot I chose"). A bot chat opens from its tile — it has nothing to do here.
        val botChats = cached?.bots.orEmpty().mapNotNull { it.chatId }.toSet()

        // **And rows already written by the old import are erased.** Until now each bot's chat
        // was imported as a conversation: the empty rows are in the phone's
        // registry, and a fix that only blocked the following ones would leave them
        // there forever. We only touch **remote** conversations (`remote`) whose
        // session is a bot chat — never a conversation written here, which may legitimately
        // be a bot's chat.
        if (botChats.isNotEmpty()) {
            val stale = ServerSessions.staleBotChats(known, botChats)
            if (stale.isNotEmpty()) conversations.deleteAll(stale.toSet())
        }

        // **Already imported conversations get their profile back.** Those written by a previous
        // version have no `botProfile`: without this catch-up, the fix would
        // only hold for the following imports, and the list would stay on Onyx's face until the
        // next restart.
        known.filter { it.remote && it.botProfile == null }.forEach { conversation ->
            val profile = profileById[conversation.serverSessionId] ?: return@forEach
            conversations.save(conversation.copy(botProfile = profile))
        }

        val imported = ServerSessions.conversations(
            server = server,
            summaries = summaries,
            known = known,
            botChats = botChats,
            profileOf = { summary -> profileById[summary.id] },
        )
        imported.forEach { conversation -> conversations.save(conversation) }
        // **What the read actually found, said on screen.**
        //
        // A silent read is indistinguishable from an empty read: the pane is the same. This
        // line short-circuits remote diagnosis — "15 read, 15 imported" on an empty list
        // no longer blames the network but the display, and "0 read" blames the route.
        // **Silent only when all is normal.** Sessions read and imported: nothing to
        // say. Zero rows read, or rows read and nothing imported: that is an anomaly, and it
        // is said — it is that silence that cost four versions.
        _importNote.value = if (imported.isEmpty()) {
            OnyxLocalization.format("%1\$d sessions read, %2\$d imported", summaries.size, imported.size)
        } else {
            null
        }
        // True as soon as the read succeeded — even with nothing new to write: it is the **read**
        // that must be marked as done, not the write.
        return true
    }

    /**
     * Opens the conversation of the bot requested by its shortcut.
     *
     * This is the path of a **bot tile** ("`openBot`" of the reference): the conversation that
     * the roster names for this bot (`BotProfile.chatId`, its `canonical_session`) is found in
     * history; failing that, a new conversation is opened under its name, and that title is what
     * attaches it to the bot. History is re-read **before** resolving, otherwise an already
     * kept but not yet loaded conversation would be duplicated on the first press.
     */
    private suspend fun openBotShortcut(request: BotShortcutRequest) {
        val server = _state.value.server
        if (conversations.conversations.value.isEmpty()) conversations.load()
        val known = conversations.conversations.value
        val bot = BotProfile(
            name = request.bot,
            title = request.label,
            isDefault = request.isDefault,
            chatId = request.chatId,
        )
        // Without a `canonical_session` (no Dashboard to name it), `botChatToOpen` opens a
        // **new** conversation on every press: the same shortcut taken twice would leave two
        // empty threads of the same bot. We therefore take back the one that is **empty** and already
        // this bot's, like the composer's pencil (`freshConversationForPencil`).
        val empty = known.firstOrNull { conversation ->
            conversation.isEmpty && botOfConversation(conversation, listOf(bot), server.displayName) != null
        }
        val chat = empty ?: botChatToOpen(
            bot = bot,
            conversations = known,
            serverId = server.id,
            mainName = server.displayName,
        )
        if (known.none { it.id == chat.id }) conversations.save(chat)
        open(chat)
    }

    /**
     * The thread follows this conversation's in-flight turn: the reply being written, the known stage
     * and the outcome. It is the only place that settles the end of a turn.
     */
    private fun follow(live: TurnLive?) {
        val state = _state.value
        if (live == null) {
            if (state.isStreaming || state.live != null) {
                _state.update { it.copy(isStreaming = false, prefill = null, providerStatus = null, live = null) }
            }
            return
        }
        val phase = live.phase
        if (live.serverSessionId != null && live.serverSessionId != serverSessionId) {
            serverSessionId = live.serverSessionId
            // Call-agent BATCH: the gateway's live session, so the call bridge can
            // talk to the plugin (`command.dispatch` requires an open session).
            if (com.onyx.app.di.AppContainer.isInstalled) {
                com.onyx.app.di.AppContainer.agentCalls.sessionId = live.serverSessionId
            }
        }
        _state.update {
            it.copy(
                messages = upsertMessage(it.messages, live.message),
                isStreaming = phase.isStreaming,
                prefill = phase.prefill,
                providerStatus = phase.providerStatus,
                serverSessionId = live.serverSessionId ?: it.serverSessionId,
                modelInUse = live.modelInUse ?: it.modelInUse,
                fallback = live.fallback ?: it.fallback,
                live = phase,
            )
        }
        if (!phase.isStreaming) settle(live)
    }

    /** Puts the message of a turn in progress in its place (or adds it, when coming back into the
     *  conversation): the screen never writes into the reply, it reads it. */
    private fun upsertMessage(messages: List<ChatMessage>, message: ChatMessage): List<ChatMessage> =
        if (messages.any { it.id == message.id }) {
            messages.map { if (it.id == message.id) message else it }
        } else {
            messages + message
        }

    /**
     * A turn that has just finished. Successful: it is saved and the queue resumes. Failed: a turn
     * cut off by the connection returns to the queue instead of being lost, as before.
     */
    private fun settle(live: TurnLive) {
        if (!settledTurns.add(live.assistantId)) return
        val error = turns.session(live.conversationId)?.failure
        heldSince.remove(live.assistantId)
        if (error == null) {
            retryAttempt = 0
            persistCurrent()
            retryOutbox()
            if (settings.suggestFollowUps.value) requestFollowUps()
            return
        }

        val snapshot = _state.value
        val assistantIndex = snapshot.messages.indexOfFirst { it.id == live.assistantId }
        val user = snapshot.messages.take(assistantIndex.coerceAtLeast(0)).lastOrNull { it.role == Role.USER }
        val assistant = snapshot.messages.firstOrNull { it.id == live.assistantId }
        val blankReply = assistant != null && assistant.text.isEmpty() && assistant.reasoning.isEmpty() &&
            assistant.tools.isEmpty() && assistant.approvals.isEmpty() && assistant.clarify == null &&
            assistant.secret == null && assistant.sudoRequestId == null

        // No reply arrived: the current turn returns to the outbox instead of being lost.
        if (error.isConnectivity && user != null && blankReply) {
            val held = OutboxItem(
                id = user.id,
                text = user.text,
                attachments = user.attachments,
                queuedAt = heldSince.remove(live.assistantId) ?: now(),
                state = OutboxState.WAITING_FOR_CONNECTION,
            )
            queued.hold(held)
            _state.update {
                it.copy(
                    isStreaming = false,
                    prefill = null,
                    steerStatus = null,
                    messages = it.messages.filterNot { row -> row.id == user.id || row.id == live.assistantId },
                    queued = queued.items,
                    error = null,
                    canRetry = false,
                    isConnectionProblem = false,
                    live = null,
                )
            }
            persistCurrent()
            scheduleRetry()
            return
        }

        queued.pause()
        _state.update {
            it.copy(
                isStreaming = false,
                prefill = null,
                providerStatus = null,
                queued = queued.items,
                error = error.userMessage(),
                canRetry = lastRequest != null,
                isConnectionProblem = error.isConnectivity,
                live = null,
            )
        }
        persistCurrent()
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
        updateSlashMenu(value)
        // The draft follows the conversation: it is found again when reopening it.
        drafts.put(conversationId, value)
    }

    /**
     * A row of the « / » menu has been **tapped**: its text enters the draft, and the menu
     * closes.
     *
     * Without this close, `onInputChange` relaunched `updateSlashMenu` on the chosen text: the
     * menu redrew itself with that single row, above the field just used — and a skill, which
     * must be able to be completed with an instruction then sent, stayed under a panel. That was
     * the second side of the "skills are unusable" defect.
     */
    fun pickSlash(text: String) {
        slashJob?.cancel()
        _state.update { it.copy(input = text, slashItems = emptyList()) }
        drafts.put(conversationId, text)
    }

    /** Attaches the chosen files: images or texts, depending on their type. */
    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            // Limits are checked **before** adding (off the main thread: the durable copy reads
            // the file, then a size is read): whatever exceeds them is refused here, with its
            // weight and the cap, and never goes to the server.
            val admission = withContext(Dispatchers.IO) {
                // The persistable permission first — when the provider grants one —, the copy
                // next: the copy is the guarantee, the first is only convenience.
                takeDurablePermissions(app, uris)
                admitAttachments(app, _state.value.attachments, uris)
            }
            _state.update {
                it.copy(
                    attachments = it.attachments + admission.accepted,
                    attachmentProblem = admission.problem,
                )
            }
        }
    }

    /**
     * Discards the attachments of the **draft** — a fallback startup does not replay the one
     * that may have killed the previous launch. The thread keeps its own: nothing is deleted from
     * a message already sent.
     */
    fun discardDraftAttachments() {
        if (_state.value.attachments.isEmpty()) return
        _state.update { it.copy(attachments = emptyList()) }
    }

    fun removeAttachment(id: String) {
        _state.update { state ->
            state.copy(
                attachments = state.attachments.filterNot { it.id == id },
                // The refusal fades with the attachment being removed: it was about the addition.
                attachmentProblem = null,
            )
        }
    }

    /** Answers a question from the agent, over the Dashboard channel. */
    fun respondClarify(messageId: String, answers: Map<String, String>) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val request = message.clarify ?: return
        updateMessage(messageId) { it.copy(clarify = null) }

        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(_state.value.server, _state.value.botProfile)
        if (!server.hasServeCredentials) return
        viewModelScope.launch {
            runCatching {
                // The bot channel is already up (or is coming up in the background): answering a
                // question no longer pays for a login and a ticket. It is not closed here — it
                // belongs to the cache, which keeps it for the next turn.
                val rpc = serveChannel(server)
                val client = com.onyx.app.data.net.ServeInterruptRpc(rpc)
                if (!request.isBatch) {
                    client.clarify(request.id, null, answers.values.firstOrNull().orEmpty())
                } else {
                    answers.forEach { (questionId, answer) -> client.clarify(request.id, questionId, answer) }
                }
            }.onFailure { error ->
                _state.update { it.copy(error = humanFailure(error)) }
            }
        }
    }

    /** Gives the requested sudo password. */
    fun respondSudo(messageId: String, password: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val requestId = message.sudoRequestId ?: return
        updateMessage(messageId) { it.copy(sudoRequestId = null) }

        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(_state.value.server, _state.value.botProfile)
        if (!server.hasServeCredentials) return
        viewModelScope.launch {
            runCatching {
                // Same channel as the turn: nothing to bring up to give a sudo password.
                serveChannel(server)
                    .let { com.onyx.app.data.net.ServeInterruptRpc(it) }
                    .sudo(serverSessionId.orEmpty(), requestId, password)
            }.onFailure { error ->
                _state.update { it.copy(error = humanFailure(error)) }
            }
        }
    }

    /** Gives the requested secret (an empty value passes it). */
    fun respondSecret(messageId: String, value: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val request = message.secret ?: return
        updateMessage(messageId) { it.copy(secret = null) }

        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(_state.value.server, _state.value.botProfile)
        if (!server.hasServeCredentials) return
        viewModelScope.launch {
            runCatching {
                // Same channel as the turn, for the same reason: the secret leaves without a handshake.
                serveChannel(server)
                    .let { com.onyx.app.data.net.ServeInterruptRpc(it) }
                    .secret(serverSessionId.orEmpty(), request.id, value)
            }.onFailure { error ->
                _state.update { it.copy(error = humanFailure(error)) }
            }
        }
    }

    /**
     * Steers a running subagent (`subagent.steer`), from its transcript.
     *
     * `false` when the gateway does not accept the instruction for now, which the screen says.
     *
     * The channel is the conversation's, **not** a fresh socket: the gateway only grants steering
     * to the transport attached to the session, and refuses a second socket with `4001` even with
     * the right identifier.
     */
    suspend fun steerSubagent(id: String, text: String): Boolean {
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(_state.value.server, _state.value.botProfile)
        if (!server.hasServeCredentials) return false
        return runCatching {
            // The conversation channel — the bot's, not a fresh socket: the gateway only grants
            // steering to the transport attached to the session.
            val channel = serveChannel(server)
            com.onyx.app.data.net.ServeSubagentRpc(channel).steer(parentSessionId(), id, text)
        }.getOrDefault(false)
    }

    /** Stops a running subagent (`subagent.interrupt`); `false` if it had already finished. */
    suspend fun stopSubagent(id: String): Boolean {
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(_state.value.server, _state.value.botProfile)
        if (!server.hasServeCredentials) return false
        return runCatching {
            val channel = serveChannel(server)
            com.onyx.app.data.net.ServeSubagentRpc(channel).interrupt(parentSessionId(), id)
        }.getOrDefault(false)
    }

    /**
     * Re-reads the subagent roster (`subagent.list`) and keeps it for the tasks screen.
     *
     * Without a turn in progress, it is the only source: `subagent.*` frames only come during a
     * turn. Rendered empty (never an exception) when the transport is not the Dashboard — the
     * list then lives on what the thread has already seen.
     */
    suspend fun refreshSubagentRoster() {
        val server = _state.value.server
        if (server.transport != com.onyx.app.data.model.Transport.HERMES_SERVE) return
        if (!server.hasServeCredentials) return
        runCatching {
            val channel = serveChannel(server)
            com.onyx.app.data.net.ServeSubagentRpc(channel).list(parentSessionId())
        }.getOrNull()?.let { rows ->
            // Read, even empty: that is what lets the screen close the tasks the server no longer
            // lists (otherwise an empty list would not distinguish "nothing is running" from "not
            // requested yet").
            _state.update { it.copy(subagentRoster = rows, subagentRosterRead = true) }
        }
    }

    /**
     * The live transcript of a subagent (`subagent.tail`), or `null` when the transport does not
     * carry it — the screen then says so instead of waiting indefinitely.
     */
    suspend fun subagentTranscript(agent: com.onyx.app.data.model.SubagentActivity): SubagentTailView? {
        val server = _state.value.server
        if (server.transport != com.onyx.app.data.model.Transport.HERMES_SERVE) return null
        if (!server.hasServeCredentials) return null
        val result = runCatching {
            val channel = serveChannel(server)
            com.onyx.app.data.net.ServeSubagentRpc(channel).tail(parentSessionId(), agent.id)
        }.getOrNull() ?: return null
        return SubagentTailView(
            text = result.text,
            available = result.available,
            truncated = result.truncated,
        )
    }

    /** The session to name to the gateway: the runtime one when it has been seen, otherwise the stored one. */
    private fun parentSessionId(): String = runtimeSessionId ?: serverSessionId.orEmpty()

    /**
     * Sets a **seeded** demo thread (flag `onyx.subagentDemo`), for screenshots: the emulator has
     * neither Dashboard nor agent, so no real subagent can be born.
     *
     * Writes the displayed state only — no conversation is opened, nothing is persisted under an
     * identifier: without the flag, this function is never called.
     */
    fun seedSubagentDemo(messages: List<com.onyx.app.data.model.ChatMessage>) {
        _state.update { it.copy(messages = messages, isStreaming = false, error = null) }
    }

    /** Renames the conversation; an empty title falls back to the first question. */
    fun rename(title: String) {
        val clean = title.trim()
        val id = conversationId
        val messages = _state.value.messages
        // The header follows the name immediately, without reopening the conversation.
        conversationName = clean.ifEmpty { null }
        _state.update { it.copy(conversationName = conversationName) }
        viewModelScope.launch {
            val existing = conversations.find(id) ?: return@launch
            conversations.save(
                existing.copy(
                    title = clean.ifEmpty {
                        messages.firstOrNull { it.isQuestion }?.let { Conversation.titleFrom(it.text) }
                            ?: Conversation.DefaultTitle
                    },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** The thread as Markdown — what « Export as Markdown » shares (`TranscriptExporter`). */
    fun exportMarkdown(assistantName: String = _state.value.server.displayName): String {
        val state = _state.value
        val title = conversations.find(conversationId)?.title ?: Conversation.DefaultTitle
        return buildString {
            appendLine("# $title")
            appendLine()
            state.messages.forEach { message ->
                if (message.text.isBlank() && message.tools.isEmpty()) return@forEach
                appendLine(if (message.isUser) "## You" else "## $assistantName")
                appendLine()
                message.reasoning.takeIf { it.isNotBlank() }?.let {
                    appendLine("<details><summary>Reasoning</summary>")
                    appendLine()
                    appendLine(it)
                    appendLine()
                    appendLine("</details>")
                    appendLine()
                }
                message.tools.forEach { tool ->
                    appendLine("> **${tool.name}**${tool.preview?.let { preview -> " · $preview" }.orEmpty()}")
                    appendLine()
                }
                if (message.text.isNotBlank()) {
                    appendLine(message.text)
                    appendLine()
                }
            }
        }
    }

    /** The identifiers of the messages containing the searched text (`Find in conversation`). */
    fun search(query: String): List<String> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return _state.value.messages
            .filter { message ->
                message.text.contains(needle, ignoreCase = true) ||
                    message.reasoning.contains(needle, ignoreCase = true) ||
                    message.tools.any { tool ->
                        tool.name.contains(needle, ignoreCase = true) ||
                            tool.output.orEmpty().contains(needle, ignoreCase = true)
                    }
            }
            .map { it.id }
    }

    /**
     * Asks the gateway for the commands starting with what is typed after « / ».
     * The menu only exists on a Dashboard connection, as in the reference.
     */
    private fun updateSlashMenu(value: String) {
        slashJob?.cancel()
        if (!value.startsWith("/") || value.contains("\n") || _state.value.isStreaming) {
            _state.update { it.copy(slashItems = emptyList()) }
            return
        }
        // The profile's skills may have changed (an install in « Skills » (« Compétences »),
        // another bot): they are re-read, at most once per minute.
        if (now() - skillsLoadedAt > SKILLS_TTL_MS) refreshSlashCommands()
        // Without a Dashboard connection, there is no `complete.slash`: the menu shows the
        // commands the app knows how to run itself, rather than nothing.
        if (!_state.value.server.hasServeCredentials) {
            val local = SlashCommands.matches(value, slashCommands).map { command ->
                SlashItem(text = command.name, display = command.display, detail = command.shownDetail)
            }
            _state.update { it.copy(slashItems = local) }
            return
        }
        slashJob = viewModelScope.launch {
            // **One keystroke, one request — at most.** Each character typed after « / » launched a
            // `complete.slash`: eighty calls for a twenty-letter word, none of which was useful
            // since the next one cancelled the previous one. The small delay adds nothing to the
            // feel (the menu appears when the finger stops) and removes the burst.
            delay(SlashCompleteDebounceMs)
            val server = _state.value.server
            val channel = serveChannel(server)
            val result = runCatching {
                channel.call(
                    method = "complete.slash",
                    params = kotlinx.serialization.json.buildJsonObject {
                        put("text", value)
                    },
                    timeoutMillis = 4_000,
                )
            }.getOrNull()
            val items = result?.asObject()?.array("items").orEmpty().mapNotNull { element ->
                val obj = element.asObject() ?: return@mapNotNull null
                val text = obj.firstString("text") ?: return@mapNotNull null
                SlashItem(
                    text = text,
                    display = obj.firstString("display") ?: text,
                    // The reply carries `meta` for what is displayed to the right of the name.
                    detail = obj.firstString("meta", "detail", "description").orEmpty(),
                )
            }.take(8)
            // The gateway menu, completed with what the app already knows how to run — `/model` and
            // the profile's skills (`withLocalCommands`): the same list as execution.
            val merged = withModelCommand(items, value)
            _state.update { it.copy(slashItems = withLocalCommands(merged, value, slashCommands)) }
        }
    }

    /** Opens or closes « Find in conversation ». */
    fun toggleSearch() {
        _state.update {
            it.copy(searchOpen = !it.searchOpen, searchQuery = "", searchHits = emptyList(), searchCurrent = 0)
        }
    }

    fun setSearchQuery(query: String) {
        val hits = search(query)
        _state.update { it.copy(searchQuery = query, searchHits = hits, searchCurrent = 0) }
    }

    /** Moves to the next (or previous) search result. */
    fun stepSearch(delta: Int) {
        val state = _state.value
        if (state.searchHits.isEmpty()) return
        val next = Math.floorMod(state.searchCurrent + delta, state.searchHits.size)
        _state.update { it.copy(searchCurrent = next) }
    }

    fun clearError() {
        _state.update { it.copy(error = null, canRetry = false, isConnectionProblem = false) }
    }

    /**
     * An attached file that cannot be opened says so in the thread's error band, in words: the
     * server's or the system's reason, never a silence that would suggest success.
     */
    fun showProblem(text: String) {
        _state.update { it.copy(error = text, canRetry = false, isConnectionProblem = false) }
    }

    /**
     * New conversation: clears the thread and the server session identifier.
     *
     * The turn of the conversation being left is **not** stopped: it lives in the store, it keeps
     * going, and its reply is there when it is reopened.
     */
    fun newConversation() {
        persistCurrent()
        serverSessionId = null
        conversationName = null
        conversationId = UUID.randomUUID().toString()
        restoredInitial = true
        queued.clear()
        retryJob?.cancel()
        retryJob = null
        heldSince.clear()
        _state.update {
            it.switchToConversation(
                conversationId = conversationId,
                serverSessionId = null,
                messages = emptyList(),
                input = drafts.get(conversationId),
                queued = emptyList(),
                conversationName = null,
            )
        }
    }

    /**
     * Reopens a saved conversation. A turn still in progress in another conversation keeps
     * writing: it is found whole on return.
     */
    fun open(conversation: Conversation) {
        conversationId = conversation.id
        serverSessionId = conversation.serverSessionId
        // A title that is not the one the first question would have given is a chosen name:
        // the header must show it rather than the question.
        conversationName = conversation.customName()
        queued.replace(conversation.outbox)
        heldSince.clear()
        restoredInitial = true
        _state.update {
            it.switchToConversation(
                conversationId = conversation.id,
                serverSessionId = conversation.serverSessionId,
                // The cards of a re-read reply are recovered from its tool calls when they were
                // not saved (`ReplyCards.resolve`, as in the reference).
                messages = cardsFor(conversation),
                input = drafts.get(conversation.id),
                queued = queued.items,
                conversationName = conversationName,
            )
        }
        scheduleRetry()
        resumeDetachedTurn(conversation)
        loadRemoteThread(conversation)
    }

    /**
     * The messages of a thread ready for the screen: tool cards resolved, and no bubble left
     * marked "typing".
     *
     * Two passes formerly, on **every** opening: `ReplyCards.resolve` re-read the tool outputs of
     * all messages (JSON included), then a `map { copy }` recopied the whole list. Switching bots
     * opens a thread, so that was the cost of switching bots.
     *
     * Here the resolution is keyed by the **source list** (the repository's, immutable): reopening
     * an untouched thread re-reads nothing. The second pass happens **only** if a bubble still
     * carries the flag: a `map` over five hundred messages always returned a new list, even when
     * none was typing — and a new list is no longer recognized as "unchanged" by the repository,
     * which then rewrote the thread's file for nothing.
     */
    private fun cardsFor(conversation: Conversation): List<ChatMessage> {
        val source = conversation.messages
        cardsCache[conversation.id]?.let { (cachedSource, resolved) ->
            if (cachedSource === source) return resolved
        }
        val resolved = ReplyCards.resolve(source)
        // No message is streaming: the resolution's list **is** the answer, without a copy.
        val clean = if (resolved.none { it.isStreaming }) resolved
        else resolved.map { message -> if (message.isStreaming) message.copy(isStreaming = false) else message }
        cardsCache[conversation.id] = source to clean
        return clean
    }

    /**
     * The thread of a **server** conversation: the projection in the registry has only its title,
     * not its messages. The thread is read here, on opening — the server keeps the source, and
     * nothing is written on this phone.
     *
     * An already loaded thread (a local conversation that also has a session) is not re-read:
     * what we have here is fresher than what the server knows about it.
     */
    private fun loadRemoteThread(conversation: com.onyx.app.data.model.Conversation) {
        if (!conversation.remote) return
        if (conversation.messages.isNotEmpty()) return
        val sessionId = conversation.serverSessionId?.takeIf { it.isNotBlank() } ?: return
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(_state.value.server, _state.value.botProfile)
        viewModelScope.launch {
            val rows = runCatching {
                com.onyx.app.data.net.ServeSessionMessages(server)
                    .rows(sessionId, conversation.botProfile, limit = THREAD_PAGE)
            }.getOrElse { return@launch }
            if (rows.isEmpty()) return@launch
            if (conversationId != conversation.id) return@launch
            _state.update { state ->
                state.copy(
                    messages = state.messages + rows.mapIndexed { index, row -> row.toMessage(conversation.createdAt, index) },
                    // A full page suggests there is older content: that is said, and the next tap
                    // checks it with the server (an empty page turns the row off).
                    earlierAvailable = rows.size >= THREAD_PAGE,
                )
            }
        }
    }

    /**
     * **The previous page of the thread.**
     *
     * The thread opens on its last page ([THREAD_PAGE]): older messages are requested here, by
     * `offset`, and are **placed at the top** — the thread's chronological order does not move.
     * An empty page turns `earlierAvailable` off: the row disappears instead of inviting a tap
     * that would return nothing.
     */
    fun loadEarlier() {
        val state = _state.value
        if (state.loadingEarlier || !state.earlierAvailable) return
        val conversation = conversations.find(state.conversationId ?: return) ?: return
        val sessionId = state.serverSessionId?.takeIf { it.isNotBlank() } ?: return
        val offset = state.messages.size
        _state.update { it.copy(loadingEarlier = true) }
        earlierJob?.cancel()
        earlierJob = viewModelScope.launch {
            val rows = runCatching {
                com.onyx.app.data.net.ServeSessionMessages(state.server)
                    .rows(sessionId, conversation.botProfile, limit = THREAD_PAGE, offset = offset)
            }.getOrElse { null }
            if (conversationId != conversation.id) return@launch
            _state.update { current ->
                when {
                    rows == null -> current.copy(loadingEarlier = false)
                    rows.isEmpty() -> current.copy(loadingEarlier = false, earlierAvailable = false)
                    else -> current.copy(
                        messages = rows.mapIndexed { index, row -> row.toMessage(conversation.createdAt, index) } +
                            current.messages,
                        loadingEarlier = false,
                        earlierAvailable = rows.size >= THREAD_PAGE,
                    )
                }
            }
        }
    }

    /** A server row → a thread message (the same mapping as on opening). */
    private fun com.onyx.app.data.net.ServeSessionMessages.Row.toMessage(
        conversationCreatedAt: Long,
        index: Int,
    ): com.onyx.app.data.model.ChatMessage = com.onyx.app.data.model.ChatMessage(
        id = java.util.UUID.randomUUID().toString(),
        role = if (role == "user") com.onyx.app.data.model.Role.USER
        else com.onyx.app.data.model.Role.ASSISTANT,
        text = text,
        createdAt = conversationCreatedAt + index,
    )

    /**
     * **The turn the host finished without us.**
     *
     * Closing the app while a bot is working no longer stops it: the server finishes the run and
     * writes the reply into the session (the client announces `X-Hermes-Keep-Running`). On return
     * to the conversation, the bubble left pending therefore fills with what the host wrote —
     * while the app was closed, or while it was still arriving.
     *
     * Three explicit refusals, in this order:
     *  - a turn **is still alive** in the process: it is the one writing, we do not double it;
     *  - the transport holds **no** server-side session: there is nothing to re-read;
     *  - no stored session identifier: the same reason.
     *
     * And one underlying rule: without an assistant reply **after** the last user message,
     * nothing is touched (`ServeSessionMessages.trailingReply`) — an old reply is never the new
     * one.
     */
    private fun resumeDetachedTurn(conversation: com.onyx.app.data.model.Conversation) {
        recoveryJob?.cancel()
        recoveryJob = null
        val pending = conversation.messages.lastOrNull()?.takeIf { message ->
            message.role == Role.ASSISTANT && (message.isStreaming || message.text.isBlank())
        } ?: return
        if (turns.session(conversation.id) != null) return
        val state = _state.value
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(state.server, state.botProfile)
        if (server.transport == Transport.CHAT_COMPLETIONS && server.apiBaseUrl.isBlank()) return
        if (server.transport != Transport.CHAT_COMPLETIONS && server.transport != Transport.HERMES_SESSIONS) return
        val sessionId = conversation.serverSessionId?.takeIf { it.isNotBlank() } ?: return
        recoveryJob = viewModelScope.launch {
            val client = com.onyx.app.data.net.ServeSessionMessages(server)
            repeat(RECOVERY_ATTEMPTS) { attempt ->
                val fromHost = runCatching { client.reply(sessionId, conversation.botProfile) }.getOrNull()
                val merged = com.onyx.app.data.net.ServeSessionMessages.merge(pending.text, fromHost)
                if (merged != null) {
                    updateMessage(pending.id) { message -> message.copy(text = merged, isStreaming = false) }
                    persistCurrent()
                    return@launch
                }
                // The run may still be going on at the host: it is asked again, without asserting anything in between.
                if (attempt < RECOVERY_ATTEMPTS - 1) delay(RECOVERY_DELAY_MS)
            }
        }
    }

    fun delete(conversation: Conversation) {
        // A deleted conversation no longer has a running turn: it is stopped at the host,
        // never left in the background (mirror of the reference's `Conversation.delete`).
        turns.forget(conversation.id)
        settledTurns.removeAll { turns.session(conversation.id)?.assistantId == it }
        viewModelScope.launch {
            conversations.delete(conversation.id)
            if (conversation.id == conversationId) newConversation()
        }
    }

    /** The startup screen cards: resume the most recent conversation. */
    fun startCards(): List<com.onyx.app.ui.chat.components.StartCard> {
        val recent = conversations.mostRecent(excluding = conversationId) ?: return emptyList()
        return listOf(
            com.onyx.app.ui.chat.components.StartCard(
                id = recent.id,
                symbol = com.onyx.app.ui.design.Symbols.Bubbles,
                title = OnyxLocalization["Continue: "] + recent.title,
                subtitle = OnyxLocalization["Opens your most recent conversation"],
                isContinue = true,
            ),
        )
    }

    /** Sends the composer's content. */
    fun send() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() && current.attachments.isEmpty()) return
        // A bare « /model » opens the model picker (same screen and effect as via the header),
        // instead of going to the server — like the reference's `ComposerView.send`.
        if (current.attachments.isEmpty() && isModelCommand(text)) {
            _state.update { it.copy(input = "", modelPickerRequested = true, slashItems = emptyList()) }
            drafts.put(conversationId, "")
            return
        }
        sendText(text, allowEmpty = current.attachments.isNotEmpty())
    }

    /** The model screen has opened: the request is lowered (the effect does not replay). */
    fun consumeModelPickerRequest() {
        _state.update { it.copy(modelPickerRequested = false) }
    }

    /**
     * The **bot's** model for the open conversation — the same memory as the one written by the
     * model sheet ([com.onyx.app.ui.bots.BotsViewModel.setModel] → `FaceKeys.model`).
     *
     * The screen pushes it as soon as the conversation (or the bot's roster) changes: that is what
     * makes the top pill name **immediately** the chosen model, and makes the send carry it
     * ([ChatUiState.modelInForce]: conversation → bot → server → default). `null` for a plain
     * conversation.
     *
     * A new choice **invalidates the server's echo** ([ChatUiState.modelInUse]): without that,
     * the "conversation" floor would keep the old served model and the bot's model would not take
     * effect — the app would display and send the previous one, ignoring the choice just made.
     */
    fun setBotModel(model: String?) {
        val clean = model?.trim()?.takeIf { it.isNotEmpty() }
        _state.update { if (it.botModel == clean) it else it.copy(botModel = clean, modelInUse = null) }
    }

    /**
     * The **bot's profile** for the open conversation — the bot → profile binding.
     *
     * The screen pushes it as soon as the conversation (or the bot's roster) changes, as
     * [setBotModel] does for the model. That is what makes the next turn leave **in that bot's
     * profile** (`profile` on `session.create`, address `/p/<profile>`), instead of the server's
     * global profile — the defect the owner saw ("coder" announced, `default` served).
     *
     * `null` for a plain conversation (the server's profile decides); `""` for the main profile
     * (the `isDefault` bot, which leaves the gateway on its default profile).
     */
    fun setBotProfile(profile: String?) {
        val clean = profile?.trim()
        val changed = _state.value.botProfile != clean
        _state.update { if (it.botProfile == clean) it else it.copy(botProfile = clean) }
        // Skills are **the profile's**: switching bots changes the list of « / » commands (one
        // profile's are not another's).
        if (changed) {
            // **The previous bot's channel stays open.** It cannot serve this turn — a `ServeRpc`
            // freezes its profile —, but it will serve the next one: returning to a bot just left
            // takes an already mounted socket instead of repaying the login, the ticket and the
            // upgrade. The chosen profile's is therefore preheated, without closing the others.
            preheatServe(com.onyx.app.ui.design.ProfileInForce.appliedTo(_state.value.server, clean))
            refreshSlashCommands()
        }
        // **The bot is held with the conversation.** Without this line, the app no longer knows,
        // on restart, that this conversation is « coder »'s: the roster arrives after the screen,
        // and the header showed Onyx — blue face, « @default » — for the time of two frames
        // before jumping to the real bot.
        val id = _state.value.conversationId?.takeIf { it.isNotBlank() }
        if (id != null) viewModelScope.launch { conversations.bindBot(id, clean) }
    }

    /**
     * Sends a text coming from somewhere other than the composer — dictation, the voice screen,
     * a watch tile, an incoming share.
     */
    fun sendText(
        text: String,
        clearInput: Boolean = true,
        modelOverride: String? = null,
        allowEmpty: Boolean = false,
    ) {
        val current = _state.value
        val clean = text.trim()
        if (clean.isEmpty() && !(allowEmpty && current.attachments.isNotEmpty())) return

        // A "/" command never leaves as a thread message: a bare `/model` opens the
        // picker, the app's commands act, and the rest goes to the gateway (`slash.exec`).
        if (clean.startsWith("/") && !clean.contains('\n') && current.attachments.isEmpty()) {
            submitSlash(clean, clearInput)
            return
        }

        // During a reply, or behind an already-queued message, we keep text and attachments.
        if (current.isStreaming || queued.items.isNotEmpty()) {
            val item = OutboxItem(
                id = UUID.randomUUID().toString(),
                text = clean,
                attachments = current.attachments.map { it.toAttachment() },
                queuedAt = now(),
            )
            queued.enqueue(item, waiting = false)
            drafts.put(conversationId, "")
            _state.update {
                it.copy(
                    input = if (clearInput) "" else it.input,
                    attachments = emptyList(),
                    queued = queued.items,
                )
            }
            persistCurrent()
            if (!current.isStreaming) retryOutbox()
            return
        }

        val history = current.messages
            .filter { it.error == null && it.text.isNotBlank() }
            .map { ChatTurn(role = it.role, text = it.text) }
        appendTurn(text = clean, history = history, clearInput = clearInput, modelOverride = modelOverride)
    }

    /**
     * A "/" command: what the app executes, it does; the rest goes to the gateway.
     *
     * Mirrors `ComposerView.send()` (the bare `/model`) and `HermesServeTransport.runSlash`
     * (`slash.exec`, then `command.dispatch` on error 4018).
     */
    private fun submitSlash(text: String, clearInput: Boolean) {
        if (clearInput) clearDraft()
        when (SlashCommands.action(text, slashCommands)) {
            SlashAction.OPEN_MODEL_PICKER,
            SlashAction.NEW_CONVERSATION,
            SlashAction.FIND,
            SlashAction.SETTINGS,
            SlashAction.VOICE,
            -> _state.update {
                it.copy(pendingSlash = SlashCommands.action(text, slashCommands), slashItems = emptyList())
            }

            SlashAction.HELP -> {
                _state.update { it.copy(slashItems = emptyList()) }
                appendLocalReply(SlashCommands.helpText(slashCommands))
            }

            // A profile skill is not a worker command: `slash.exec` rejects it
            // on purpose ("skill command: use command.dispatch").
            SlashAction.INVOKE_SKILL -> invokeSkill(text)

            SlashAction.GATEWAY -> runSlashOnGateway(text)
        }
    }

    /**
     * Invokes a profile skill, and **sends** the message that loads it.
     *
     * `command.dispatch` returns the message meant for the model (`type: skill`): that is what
     * leaves as the next turn, exactly the reference contract (`gateway_chat_commands.forward`
     * calls `view.submit(reply["message"])`). The thread, for its part, keeps the typed line — the
     * skill body is not a user utterance.
     */
    private fun invokeSkill(text: String) {
        val server = _state.value.server
        if (!server.hasServeCredentials) {
            appendLocalReply(OnyxLocalization["Slash commands need a connection to the Dashboard."])
            return
        }
        val line = text.trim().removePrefix("/")
        val name = line.substringBefore(' ').trim()
        val argument = line.substringAfter(' ', "").trim()
        _state.update { it.copy(slashItems = emptyList()) }
        viewModelScope.launch {
            // A turn is already running: the skill waits its turn instead of opening a second
            // stream on the same conversation.
            //
            // **We wait for the change, we do not poll it.** The loop woke the process
            // five times per second without limit; a stream lasting ten minutes is three thousand
            // wakes for nothing — and each republishes a state read in an already loaded turn.
            // `first { }` wakes on change, and `withTimeout` yields rather than
            // waiting indefinitely for a turn that will never end.
            runCatching {
                withTimeoutOrNull(SlashWaitTimeoutMs) {
                    state.first { !it.isStreaming }
                }
            }
            val channel = serveChannel(server)
            val result = runCatching {
                channel.call(
                    method = "command.dispatch",
                    params = buildJsonObject {
                        // The live session, when it exists: `command.dispatch` uses it to
                        // name the skill task, and accepts an empty value.
                        put("session_id", parentSessionId())
                        put("name", name)
                        put("arg", argument)
                    },
                    timeoutMillis = 30_000,
                )
            }
            val payload = result.getOrNull()?.asObject()
            val message = payload?.firstString("message")
            if (message != null) {
                val history = _state.value.messages
                    .filter { it.error == null && it.text.isNotBlank() }
                    .map { ChatTurn(role = it.role, text = it.text) }
                appendTurn(text = message, history = history, displayText = text.trim())
                return@launch
            }
            val error = result.exceptionOrNull()
            val reason = (error as? TransportError)?.userMessage() ?: error?.message.orEmpty()
            appendLocalReply(
                OnyxLocalization["The command did not run."] + " " + reason,
            )
        }
    }

    /**
     * Reloads the profile skills: `api/skills`, the route the "Compétences" (Skills) screen already
     * uses. It is **the** source of the list that the menu and the execution share.
     *
     * Without a Dashboard connection, there is no route: the list remains that of the six fixed
     * commands. A failing read breaks nothing either — a profile with no skill yields
     * exactly the previous list.
     */
    private fun refreshSlashCommands(force: Boolean = false) {
        skillsJob?.cancel()
        val server = _state.value.server
        // Skills are the bot profile's when the conversation carries one; otherwise
        // the server profile's (`ServeClient` handles it when nobody is named).
        val profile = _state.value.botProfile?.takeIf { it.isNotBlank() }
        val key = profile.orEmpty()
        val stamp = now()
        // **A profile's list does not change from one second to the next.** Without this cache, every
        // return to a bot relaunched `api/skills` — one round trip per visit, while the
        // answer is the same. The cache is per profile, and it expires like the current read.
        if (!force) {
            skillsByProfile[key]?.takeIf { stamp - it.first <= SKILLS_TTL_MS }?.let { (at, cached) ->
                skillsLoadedAt = at
                slashCommands = cached
                return
            }
        }
        skillsLoadedAt = stamp
        if (!server.hasServeCredentials) {
            slashCommands = SlashCommands.commands()
            return
        }
        skillsJob = viewModelScope.launch {
            val skills = runCatching { ServeSkillsApi(ServeClient(server)).skills(profile) }
                .getOrDefault(emptyList())
            val commands = SlashCommands.commands(skills)
            skillsByProfile[key] = now() to commands
            slashCommands = commands
            // The already-open menu is reread with the fresh list: skills enter it.
            val draft = _state.value.input
            if (draft.startsWith("/")) updateSlashMenu(draft)
        }
    }

    /** The screen executed the command: it must not replay on the next recomposition. */
    fun consumeSlash() {
        _state.update { it.copy(pendingSlash = null) }
    }

    /**
     * Sends the command to the gateway: `slash.exec` first, `command.dispatch` if the gateway
     * answers 4018 ("this is not a worker command"). Without a Dashboard connection, there is
     * nobody to execute it: the reply says so instead of sending the line as a message.
     */
    private fun runSlashOnGateway(text: String) {
        val server = _state.value.server
        if (!server.hasServeCredentials) {
            appendLocalReply(OnyxLocalization["Slash commands need a connection to the Dashboard."])
            return
        }
        val command = text.trim().removePrefix("/").trim()
        viewModelScope.launch {
            val channel = serveChannel(server)
            val result = runCatching {
                channel.call(
                    method = "slash.exec",
                    params = buildJsonObject {
                        // `session_id` is only useful to an open session; the worker accepts empty.
                        put("session_id", serverSessionId.orEmpty())
                        put("command", command)
                    },
                    timeoutMillis = 30_000,
                )
            }
            val output = result.getOrNull()?.asObject()?.firstString("output")
            if (output != null) {
                appendLocalReply(output.ifBlank { "(no output)" })
                return@launch
            }
            val error = result.exceptionOrNull()
            if (error is TransportError.Rpc && error.code == SLASH_NOT_A_COMMAND) {
                dispatchCustomCommand(channel, command)
                return@launch
            }
            appendLocalReply(
                OnyxLocalization["The command did not run."] + " " +
                    ((error as? TransportError)?.userMessage() ?: error?.message.orEmpty()),
            )
        }
    }

    /** `command.dispatch`: the user's own commands, those `slash.exec` ignores. */
    private suspend fun dispatchCustomCommand(
        channel: com.onyx.app.data.net.ServeRpc,
        command: String,
    ) {
        val name = command.substringBefore(' ').trim()
        val argument = command.substringAfter(' ', "").trim()
        val result = runCatching {
            channel.call(
                method = "command.dispatch",
                params = buildJsonObject {
                    put("session_id", serverSessionId.orEmpty())
                    put("name", name)
                    put("arg", argument)
                },
                timeoutMillis = 30_000,
            )
        }
        val reply = result.getOrNull()?.asObject()
        // A skill (or a command that composes a turn, `/plan`…): the gateway returns the
        // message **meant for the model**, and that is what leaves — never in a bubble. It is the
        // fallback for a skill that the known list did not carry yet.
        val message = reply?.firstString("message")
        val type = reply?.firstString("type")
        if (message != null && (type == "skill" || type == "send")) {
            val history = _state.value.messages
                .filter { it.error == null && it.text.isNotBlank() }
                .map { ChatTurn(role = it.role, text = it.text) }
            val typed = "/" + name + if (argument.isEmpty()) "" else " " + argument
            appendTurn(text = message, history = history, displayText = typed)
            return
        }
        val shown = reply
            ?.let { it.firstString("display") ?: it.firstString("output") }
            ?: humanFailure(result.exceptionOrNull(), "(done)")
        appendLocalReply(shown)
    }

    /** What a command answered: an agent bubble, like `TurnEvent.textDelta` in the reference. */
    private fun appendLocalReply(text: String) {
        val reply = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.ASSISTANT,
            text = text,
            createdAt = now(),
        )
        _state.update { it.copy(messages = it.messages + reply) }
        persistCurrent()
    }

    /**
     * Steers the current reply without interrupting it (`Conversation.steer`).
     *
     * The note is shown as a user message, placed before the streaming reply
     * so that it remains the last line of the thread. Only a ledger transport (`hasLedger`)
     * accepts steering: elsewhere the composer only offers the queue.
     */
    fun steer(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val snapshot = _state.value
        if (!snapshot.canSteer) return
        val note = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            text = clean,
            createdAt = now(),
            isSteer = true,
        )
        _state.update { state ->
            val index = steerInsertIndex(state.messages)
            state.copy(
                messages = state.messages.toMutableList().apply { add(index, note) },
                input = "",
                slashItems = emptyList(),
            )
        }
        clearDraft()
        persistCurrent()
        deliverSteer(clean)
    }

    /** The channel that carries `session.steer`: the Dashboard's, the only one that can steer. */
    private fun deliverSteer(text: String) {
        val state = _state.value
        val session = serverSessionId
        if (state.server.transport != Transport.HERMES_SERVE ||
            !state.server.hasServeCredentials ||
            session.isNullOrEmpty()
        ) {
            // No channel to steer with: the reference yields with "steer rejected" and keeps the
            // note in the thread. We do not pretend the turn was steered.
            _state.update { it.copy(steerStatus = OnyxLocalization["steer rejected (turn already finishing)"]) }
            return
        }
        viewModelScope.launch {
            val channel = serveChannel(state.server)
            val result = runCatching {
                channel.call(
                    method = "session.steer",
                    params = buildJsonObject {
                        put("session_id", session)
                        put("text", text)
                    },
                    timeoutMillis = 8_000,
                )
            }
            val status = result.getOrNull()?.asObject()?.firstString("status")
            val queued = status == "queued"
            _state.update {
                it.copy(
                    steerStatus = if (queued) {
                        OnyxLocalization["steer queued"]
                    } else {
                        OnyxLocalization["steer rejected (turn already finishing)"]
                    },
                )
            }
        }
    }

    private fun clearDraft() {
        drafts.put(conversationId, "")
        _state.update { it.copy(input = "", slashItems = emptyList()) }
    }

    /** Replays the last turn (the "Retry" or "Regenerate" button). */
    fun retry() {
        val current = _state.value
        if (current.isStreaming) return
        val lastUser = current.messages.lastOrNull { it.role == Role.USER } ?: return
        val history = current.messages
            .filter { it.error == null && it.text.isNotBlank() && it.id != lastUser.id }
            .map { ChatTurn(role = it.role, text = it.text) }
        // The faulty message and the empty reply are removed before replaying the turn.
        _state.update { it.copy(messages = it.messages.filterNot { m -> m.error != null || m.isStreaming }) }
        appendTurn(text = lastUser.text, history = history, clearInput = false)
    }

    /**
     * Requests the restoration of a reply **to this message**: nothing is removed before the
     * confirmation. We only gather what would be undone.
     */
    fun requestRestore(messageId: String) {
        val current = _state.value
        if (current.isStreaming) return
        // Nothing is removed here: we only gather what **would** be undone, and we submit it.
        val plan = restorePlan(current.messages, messageId) ?: return
        _state.update { it.copy(restorePrompt = plan) }
    }

    /** The person refuses: **nothing** is undone. */
    fun cancelRestore() {
        _state.update { it.copy(restorePrompt = null) }
    }

    /**
     * The person confirms: the thread is brought back **to** this message, and the gateway session is
     * rewound by the same number of turns when the transport can do it (`session.undo`, the
     * Dashboard). On the shared API, which has no undo, only our copy of the thread is
     * cut: the notice says so plainly.
     */
    fun confirmRestore() {
        val prompt = _state.value.restorePrompt ?: return
        _state.update { it.copy(restorePrompt = null) }
        val current = _state.value
        if (current.messages.none { it.id == prompt.messageId }) return
        val kept = messagesThroughRestore(current.messages, prompt.messageId)

        turns.stop(conversationId)
        retryJob?.cancel()
        retryJob = null
        queued.clear()
        settledTurns.clear()
        _state.update {
            it.copy(
                messages = kept,
                queued = emptyList(),
                isStreaming = false,
                live = null,
                prefill = null,
                providerStatus = null,
                steerStatus = null,
                error = null,
                canRetry = false,
                followUps = emptyList(),
            )
        }
        persistCurrent()

        val session = serverSessionId
        val server = _state.value.server
        val undoable = prompt.removedTurns > 0 && !session.isNullOrEmpty() &&
            server.transport == Transport.HERMES_SERVE && server.hasServeCredentials
        viewModelScope.launch {
            val rewound = if (undoable) rewindGateway(session.orEmpty(), prompt.removedTurns) else null
            _state.update { it.copy(restoreNotice = restoreNotice(prompt, rewound)) }
        }
    }

    /** The restore notice has been shown: it does not replay. */
    fun consumeRestoreNotice() {
        if (_state.value.restoreNotice != null) _state.update { it.copy(restoreNotice = null) }
    }

    /**
     * "Branch from here": asks for confirmation **before** creating anything.
     *
     * Like restoration, the request touches nothing: it only gathers what **would** be
     * copied (the target message and everything before it) and submits it to the confirmation.
     */
    fun requestBranch(messageId: String) {
        val current = _state.value
        if (current.isStreaming) return
        val plan = branchPlan(current.messages, messageId) ?: return
        _state.update { it.copy(branchPrompt = plan) }
    }

    /** The person refuses: **nothing** is created, and the thread does not budge an inch. */
    fun cancelBranch() {
        _state.update { it.copy(branchPrompt = null) }
    }

    /**
     * The person confirms: a **new conversation** that carries the copy of the thread up to and
     * including this message, and it **opens**. The original is neither rewritten nor truncated: it keeps
     * its messages, its title and its identifier.
     *
     * **No turn starts here, and nothing is sent to the server**: branching is a local
     * copy (`ConversationRepository.branchFrom`) followed by an open. The branch is born without a
     * server session and without a queue, so no send can trigger on it by itself;
     * it is the first message written in the new conversation that will open its session.
     *
     * The server, for its part, cannot be branched: the gateway exposes no route to fork a
     * session (`POST api/sessions/{id}/branch` does not exist). The copy therefore lives on this phone —
     * and that is stated in the confirmation, in so many words.
     */
    fun confirmBranch() {
        val prompt = _state.value.branchPrompt ?: return
        _state.update { it.copy(branchPrompt = null) }
        val current = _state.value
        if (current.messages.none { it.id == prompt.messageId }) return
        // The original as it is **now**: its messages in memory (the live thread),
        // its title and its original session. None of that is modified by what follows.
        val source = (conversations.find(conversationId) ?: Conversation.start(id = conversationId)).copy(
            serverId = current.server.id,
            serverSessionId = serverSessionId,
            messages = current.messages.map { it.copy(isStreaming = false) },
        )
        viewModelScope.launch {
            conversations.branchFrom(source, prompt.messageId)?.let { branch ->
                // The new conversation opens right here; the chat screen follows its state.
                open(branch)
            }
        }
    }

    /**
     * `session.undo` removes **the last turn**; to go back to a higher message, it is called
     * once per removed user turn (that is what the reference does). Returns `true` when all
     * turns have passed, `false` as soon as a call fails or the server refuses one.
     */
    private suspend fun rewindGateway(session: String, turns: Int): Boolean {
        val channel = serveChannel(_state.value.server)
        repeat(turns) {
            val result = runCatching {
                channel.call(
                    method = "session.undo",
                    params = buildJsonObject { put("session_id", session) },
                    timeoutMillis = 15_000,
                )
            }.getOrNull()?.asObject() ?: return false
            if ((result.firstString("removed")?.toIntOrNull() ?: 0) <= 0) return false
        }
        return true
    }

    /**
     * The end-of-restore notice: what was undone, and what the server could follow.
     *
     * The **real** count goes in here as in the confirmation, and zero is phrased differently: "nothing
     * was removed" rather than "removed: 0 messages".
     */
    private fun restoreNotice(prompt: RestorePrompt, rewound: Boolean?): String {
        val base = if (prompt.removedMessages == 0) {
            OnyxLocalization["Restored here — nothing was removed."]
        } else {
            String.format(
                OnyxLocalization["Restored here — %1\$s removed."],
                restoreCountPhrase(prompt.removedMessages),
            )
        }
        return when (rewound) {
            true -> base + " " + OnyxLocalization["The session was rewound to the same point."]
            false -> base + " " + OnyxLocalization["The server could not rewind its own copy; it still holds them."]
            null -> base
        }
    }

    /**
     * The stop button: the current turn is **really** stopped, on the host too.
     * The text already received stays in the thread.
     */
    fun stop() {
        // The order goes out **before** the local cut: now that the turn survives closing the
        // app, cancelling the listener is no longer enough — without `POST /v1/runs/{id}/stop`, the bot
        // would keep working behind the user's back.
        val stateBefore = _state.value
        val runId = turns.session(conversationId)?.live?.value?.runId
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(
            stateBefore.server, stateBefore.botProfile,
        )
        if (!runId.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching { com.onyx.app.data.net.ServeRunStop(server).stop(runId) }
            }
        }
        turns.stop(conversationId)
        retryJob?.cancel()
        retryJob = null
        queued.pause()
        _state.update { state ->
            state.copy(
                isStreaming = false,
                prefill = null,
                steerStatus = null,
                live = null,
                queued = queued.items,
                messages = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
            )
        }
        persistCurrent()
    }

    /** Answers an approval request, in the gateway's wording (`once`… `deny`). */
    fun respondApproval(messageId: String, approvalId: String, choice: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val approval = message.approvals.firstOrNull { it.id == approvalId } ?: return
        if (!approval.isPending) return

        updateMessage(messageId) { current ->
            current.copy(
                approvals = current.approvals.map {
                    if (it.id == approvalId) it.copy(decision = choice) else it
                },
            )
        }

        val runId = approval.runId
        if (runId.isNullOrEmpty() || _state.value.server.transport != Transport.HERMES_SESSIONS) return

        viewModelScope.launch {
            try {
                GatewayApprovalClient(_state.value.server).respond(runId, approvalId, choice)
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                _state.update {
                    it.copy(error = OnyxLocalization["The approval did not reach the server."] + " " + error.userMessage())
                }
            }
        }
    }

    private fun appendTurn(
        text: String,
        history: List<ChatTurn>,
        clearInput: Boolean = true,
        modelOverride: String? = null,
        outgoing: OutboxItem? = null,
        /**
         * What the thread shows as the user's words, when the text **sent** is not theirs:
         * invoking a skill sends the skill's body, and the bubble keeps the typed line
         * (`/resume this thread`).
         */
        displayText: String? = null,
    ) {
        // The state **and** the server: the model in force is read from the state — the **same** resolution
        // as the top pill, so that what is named is exactly what goes out. And the
        // **profile**: a bot's conversation carries **its** profile (address, session `profile`,
        // model and provider of the profile), not the server's — the bot → profile link.
        val state = _state.value
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(state.server, state.botProfile)
        val attachments = outgoing?.attachments?.map { it.toPendingAttachment() } ?: _state.value.attachments
        val userMessage = ChatMessage(
            id = outgoing?.id ?: UUID.randomUUID().toString(),
            role = Role.USER,
            text = displayText ?: text,
            createdAt = now(),
            attachments = attachments.map { it.toAttachment() },
        )
        val assistantId = UUID.randomUUID().toString()
        val assistantMessage = ChatMessage(
            id = assistantId,
            role = Role.ASSISTANT,
            createdAt = now(),
            metrics = TurnMetrics(startedAt = now()),
            isStreaming = true,
        )
        if (outgoing != null) heldSince[assistantId] = outgoing.queuedAt

        drafts.clear(conversationId)
        _state.update { state ->
            state.copy(
                messages = state.messages + userMessage + assistantMessage,
                input = if (clearInput) "" else state.input,
                isStreaming = true,
                prefill = null,
                error = null,
                canRetry = false,
                isConnectionProblem = false,
            )
        }

        // Immediate save of the turn and the outbox: a close mid-stream cannot
        // resurrect the message removed from the queue next to its already-sent bubble.
        restoredInitial = true
        persistCurrent()

        // Images go out as data URLs; attached text is added to the message, like the note
        // the reference app writes for a file.
        if (outgoing == null) _state.update { it.copy(attachments = emptyList()) }

        // **The disk does not make the screen wait.** Downscaling a photo and encoding it as a data URL,
        // or reading an attached file, is disk and CPU work (up to [MAX_INLINE_IMAGE] before
        // downscaling): done here, that work delayed the turn's departure by as much, while the
        // bubble is already on screen and the person has seen their message leave. So the turn departs
        // from a coroutine; the lock keeps the order of two close-together sends.
        viewModelScope.launch {
            sendGate.withLock {
                val parts = withContext(Dispatchers.IO) {
                    attachments.filter { it.isImage }.mapNotNull { encodeImage(it.uri) } to
                        attachments.filterNot { it.isImage }.mapNotNull { readTextAttachment(it.uri, it.name) }
                }
                val images = parts.first
                val fileNotes = parts.second
                val fullText = if (fileNotes.isEmpty()) text else text + "\n\n" + fileNotes.joinToString("\n\n")

                val request = ChatRequest(
                    history = history + ChatTurn(role = Role.USER, text = fullText, images = images),
                    instructions = SettingsRepository.replyInstruction(settings.replyLanguage.value),
                    model = modelOverride ?: state.modelInForce,
                    provider = server.effectiveProvider,
                    reasoningEffort = settings.reasoningEffort.value.takeIf { it.isNotEmpty() },
                    fast = server.transport == Transport.HERMES_SESSIONS &&
                        serverSessionId?.let { runControls.isFast(server.configSignature, it) } == true,
                    // An already-known session is resumed by its identifier: the gateway re-reads its
                    // history (state.db) instead of trusting the thread sent by the app, and the
                    // turn becomes "wakeable" again on the server side. Reserved for the Hermes transport — an
                    // arbitrary OpenAI-compatible server does not have this header.
                    sessionId = serverSessionId
                        ?.takeIf { it.isNotBlank() }
                        ?.takeIf { server.transport == Transport.HERMES_SESSIONS },
                    // The thread key: stable for this conversation, so the gateway finds the
                    // same session from one turn to the next instead of creating one per request.
                    conversationKey = conversationId,
                )
                lastRequest = request
                startStream(server, request, assistantId, prompt = fullText)
            }
        }
    }

    /**
     * **A reply that arrives without the stream is not lost.**
     *
     * The server stays silent while a tool works — sometimes several minutes, and Telegram
     * shows "typing" the whole time without losing anything, because it re-reads the
     * session. The stream itself can end up being cut by a relay, a network change or
     * a sleep: the reply was then written on the host and never shown.
     *
     * This watcher re-reads the session as soon as the stream is quiet for more than [RESCUE_AFTER_SECONDS]: if
     * the host wrote the reply, it is placed in the turn's message and the turn closes
     * cleanly. It touches nothing while the stream is talking, and never guesses: without an assistant
     * message after the prompt, it simply waits for the next turn.
     */
    private fun watchForRescue(
        conversation: String,
        assistantId: String,
        server: ServerConfig,
    ) {
        rescueJob?.cancel()
        rescueJob = viewModelScope.launch {
            var essais = 0
            while (essais < RESCUE_ATTEMPTS) {
                delay(RESCUE_POLL_MS)
                val session = turns.session(conversation) ?: return@launch
                val phase = session.phase
                // **Two cases to catch up on, and only one before.**
                //
                //  1. the turn is still alive but the stream has gone quiet (agent thinking, slow tool):
                //     we wait for the silence, then re-read on the host;
                //  2. the stream was **cut** (`TransportError.Truncated`): the turn is already
                //     marked as failed, and the old guard `if (!phase.isStreaming) return`
                //     exited right here — the bubble stayed truncated with an error, while
                //     the host had finished writing the reply. This is precisely the
                //     disconnect: the reply exists, and the app does not go fetch it.
                val cut = !phase.isStreaming && session.failure is TransportError.Truncated
                if (!phase.isStreaming && !cut) return@launch
                if (!cut && phase.quietSeconds(System.currentTimeMillis()) < RESCUE_AFTER_SECONDS) continue
                val id = session.serverSessionId ?: serverSessionId ?: continue
                essais += 1
                val fromHost = runCatching {
                    com.onyx.app.data.net.ServeSessionMessages(server).reply(id)
                }.getOrNull() ?: continue
                val courant = _state.value.messages.firstOrNull { it.id == assistantId }
                val fusion = com.onyx.app.data.net.ServeSessionMessages
                    .merge(courant?.text.orEmpty(), fromHost) ?: continue
                // A reply recovered from the host **erases** the failure: it is complete, and
                // leaving the error shown above a full text would suggest a
                // problem that no longer exists.
                session.edit { message -> message.copy(text = fusion, isStreaming = false, error = null) }
                session.succeed()
                return@launch
            }
        }
    }

    private fun startStream(
        server: ServerConfig,
        request: ChatRequest,
        assistantId: String,
        prompt: String,
    ) {
        val conversation = conversationId
        // The turn departs into the process store: the screen that launched it can disappear
        // (back, another conversation, destroyed activity) without stopping it.
        turns.start(
            conversationId = conversation,
            assistantId = assistantId,
            userText = prompt,
            serverSessionId = serverSessionId,
        ) { departure ->
            // **The request carries exactly the model in force** — the same name the top pill
            // writes (`ChatUiState.modelInForce`). No substitution here: `withResolvedModel`
            // rewrote the model when the field was empty or unknown (`available.first()`, often
            // `sage-a1.5`) while the pill said "Default model" ("Modèle par défaut") — the app named one thing
            // and the request carried another. If nothing is in force, **nothing is sent**: the
            // server chooses, and that is exactly what "Default model" ("Modèle par défaut") announces.
            lastRequest = request
            // **The stream is not the only source of the reply.** See `watchForRescue`.
            watchForRescue(conversation, assistantId, server)
            val effectiveRequest = lastRequest ?: request
            // What really goes out: the top bar shows it (it is the same value).
            effectiveRequest.model?.trim()?.takeIf { it.isNotEmpty() }?.let { resolved ->
                turns.noteModel(conversation, assistantId, resolved)
            }
            when (server.transport) {
                // The turn goes through the Dashboard's JSON-RPC channel (WebSocket), as a reference — and
                // through the **same socket** as the sub-agent commands: the gateway only grants
                // control to the transport attached to the session.
                Transport.HERMES_SERVE -> {
                    val channel = serveChannel(server)
                    HermesServeChat(server, channel.asChatChannel(), ownsLink = false)
                        .stream(effectiveRequest, serverSessionId, departure) { runtimeSessionId = it }
                }
                else -> ChatCompletionsClient(server).stream(effectiveRequest)
            }
        }
    }

    /**
     * Asks the model for three follow-up questions, as `FollowUpChips` receives them on the reference side.
     * A small separate turn, once the reply is finished.
     */
    private fun requestFollowUps() {
        val state = _state.value
        val server = state.server
        if (state.messages.count { it.role == Role.USER } < 1) return
        val history = state.messages
            .filter { it.text.isNotBlank() }
            .takeLast(6)
            .map { ChatTurn(role = it.role, text = it.text) }
        viewModelScope.launch {
            val builder = StringBuilder()
            runCatching {
                ChatCompletionsClient(server).stream(
                    ChatRequest(
                        history = history + ChatTurn(
                            role = Role.USER,
                            text = "Suggest three short follow-up questions I might ask next. " +
                                "One per line, no numbering, no quotes, at most 60 characters each.",
                        ),
                        model = state.modelInForce,
                    ),
                ).collect { event ->
                    when (event) {
                        is TurnEvent.TextDelta -> builder.append(event.text)
                        is TurnEvent.TextFinal -> {
                            builder.clear()
                            builder.append(event.text)
                        }
                        else -> Unit
                    }
                }
                val lines = builder.toString()
                    .lineSequence()
                    .map { it.trim().trimStart('-', '*', '•').trim().trim('"') }
                    .filter { it.isNotEmpty() && it.length <= 80 }
                    .take(3)
                    .toList()
                _state.update { it.copy(followUps = lines) }
            }
        }
    }

    /** The reader confirms the head of the queue; the following ones then resume in the same order. */
    fun sendQueuedNow(id: String) {
        if (!queued.confirmFirst(id, now())) return
        retryAttempt = 0
        _state.update { it.copy(queued = queued.items) }
        retryOutbox()
    }

    /** « Edit » retire le message de la file et le rend au composer avec ses pièces jointes. */
    fun editQueued(id: String) {
        val item = queued.remove(id) ?: return
        drafts.put(conversationId, item.text)
        _state.update {
            it.copy(
                input = item.text,
                attachments = item.attachments.map { attachment -> attachment.toPendingAttachment() },
                queued = queued.items,
            )
        }
        persistCurrent()
    }

    fun deleteQueued(id: String) {
        queued.remove(id) ?: return
        if (queued.items.isEmpty()) {
            retryJob?.cancel()
            retryJob = null
        }
        _state.update { it.copy(queued = queued.items) }
        persistCurrent()
    }

    /** Envoie la tête si elle est autorisée à partir, jamais un message en pause ou périmé. */
    private fun retryOutbox() {
        if (_state.value.isStreaming) return
        val next = queued.takeNext(now())
        if (next == null) {
            _state.update { it.copy(queued = queued.items) }
            persistCurrent()
            return
        }
        _state.update { it.copy(queued = queued.items) }
        val history = _state.value.messages
            .filter { it.error == null && it.text.isNotBlank() }
            .map { ChatTurn(role = it.role, text = it.text) }
        appendTurn(text = next.text, history = history, clearInput = false, outgoing = next)
    }

    private fun scheduleRetry() {
        val first = queued.items.firstOrNull()
        if (_state.value.isStreaming || first?.state != OutboxState.WAITING_FOR_CONNECTION) return
        retryJob?.cancel()
        val delayMs = retryDelaysMs[minOf(retryAttempt, retryDelaysMs.lastIndex)]
        retryAttempt += 1
        retryJob = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            retryOutbox()
            scheduleRetry()
        }
    }

    /** Enregistre le fil tel qu'il est : appelé à la fin d'un tour et au changement de serveur. */
    private fun persistCurrent() {
        val state = _state.value
        if (state.messages.isEmpty() && queued.items.isEmpty()) return
        val id = conversationId
        val serverId = state.server.id
        // **La liste n'est copiée que si elle en a besoin.** `map { it.copy(...) }` allouait un objet
        // par message à **chaque** appel (seize sites, plusieurs par tour) : un fil de 500 messages
        // payait 500 allocations pour retirer un drapeau qui, le plus souvent, n'était posé nulle
        // part. Et la copie empêchait le contrôle d'égalité du dépôt de reconnaître « rien n'a
        // changé » (liste neuve = jamais égale), donc le fichier repartait pour rien.
        val streaming = state.messages.any { it.isStreaming }
        val messages = if (streaming) state.messages.map { it.copy(isStreaming = false) } else state.messages
        val session = serverSessionId
        viewModelScope.launch {
            val existing = conversations.find(id)
            val base = existing ?: Conversation.start(id = id)
            conversations.save(
                base.copy(
                    serverId = serverId,
                    serverSessionId = session,
                    messages = messages,
                    outbox = queued.items,
                    updatedAt = maxOf(
                        base.updatedAt,
                        messages.maxOfOrNull { it.createdAt } ?: base.updatedAt,
                        queued.items.maxOfOrNull { it.queuedAt } ?: base.updatedAt,
                    ),
                    title = if (base.title == Conversation.DefaultTitle) {
                        messages.firstOrNull { it.isQuestion }?.let { Conversation.titleFrom(it.text) }
                            ?: base.title
                    } else {
                        base.title
                    },
                ),
            )
        }
    }

    /**
     * Une image en data URL — l'API n'accepte pas d'URI locale.
     *
     * Deux chemins, et une borne sur les deux : une image ordinaire part telle quelle, mais une
     * image plus lourde que [MAX_INLINE_IMAGE] est **réduite** avant l'envoi. Lire entière une
     * photo de 40 Mo pour la convertir en base64 tenait son poids plusieurs fois en mémoire (les
     * octets, la chaîne, sa copie), et une image de 8000×6000 décodée en pleine résolution pèse
     * 192 Mo — de quoi tuer le processus au pire moment. Le décodage passe par [BoundedImages] :
     * borné en pixels et en octets, et `null` plutôt qu'une exception.
     */
    private fun encodeImage(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val step = input.read(chunk)
                    if (step <= 0) break
                    buffer.write(chunk, 0, step)
                    if (buffer.size() > MAX_INLINE_IMAGE) break
                }
                buffer.toByteArray()
            } ?: return null
            if (bytes.size <= MAX_INLINE_IMAGE) {
                return "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
            // Trop lourde pour partir telle quelle : on relit la source **réduite** (le flux est
            // rouvert, les octets tronqués ne servent pas à décoder), et le type envoyé est celui
            // du ré-encodage.
            val bitmap = BoundedImages.decodeUri(getApplication(), uri, SEND_PX, SEND_BYTES) ?: return null
            val reduced = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, reduced)
            bitmap.recycle()
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(reduced.toByteArray(), android.util.Base64.NO_WRAP)
        }.getOrNull()
    }

    /** Le texte d'un fichier joint, borné, avec son nom — la note de la référence pour un fichier. */
    private fun readTextAttachment(uri: Uri, name: String): String? {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.size > MAX_TEXT_ATTACHMENT) return null
            val text = bytes.decodeToString()
            "[Attached file: $name]\n$text"
        }.getOrNull()
    }

    /**
     * La pièce jointe du message porte **la copie durable** ([PendingAttachment.uri]), son nom, sa
     * taille et son type : c'est ce qui reste dans le fil après la fermeture de l'app.
     */
    private fun PendingAttachment.toAttachment(): Attachment =
        Attachment(id = id, mimeType = mimeType, uri = uri.toString(), name = name, size = size)

    private fun Attachment.toPendingAttachment(): PendingAttachment = PendingAttachment(
        id = id,
        uri = Uri.parse(uri),
        name = name ?: uri.substringAfterLast('/'),
        mimeType = mimeType,
        isImage = mimeType.startsWith("image/"),
        size = size.takeIf { it > 0L } ?: durableFileLength(Uri.parse(uri)) ?: 0L,
    )

    /**
     * Modifie un message du fil. La réponse d'un tour en cours appartient au magasin : c'est lui
     * qui l'écrit, sinon la modification serait écrasée par le fragment suivant du serveur.
     */
    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        if (turns.update(conversationId, id, transform)) return
        _state.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        /** Silence toléré avant la première relecture de la session : en deçà, un tour écrit. */
        const val RESCUE_AFTER_SECONDS = 25.0
        /** Une relecture toutes les dix secondes — le rythme d'un humain qui vérifie. */
        const val RESCUE_POLL_MS = 10_000L
        /** Plafond : cinq minutes de relectures, puis on laisse le tour tel qu'il est. */
        const val RESCUE_ATTEMPTS = 30

        /**
         * Effort de raisonnement demandé aux modèles qui le supportent.
         * Aucune consigne système par défaut, comme sur la référence : `instructions` reste vide tant que
         * l'utilisateur n'a pas réglé de langue de réponse.
         */
        const val REASONING_EFFORT = "medium"

        /** Un fichier joint plus gros n'est pas ajouté au message. */
        const val MAX_TEXT_ATTACHMENT = AttachmentLimits.MAX_TEXT_BYTES

        /**
         * Le poids au-delà duquel une image est réduite avant l'envoi : une photo ordinaire
         * (2 à 6 Mo) part intacte, une image « pour analyse » de plusieurs dizaines de mégaoctets
         * est ramenée à [SEND_PX] sur son plus grand côté.
         */
        const val MAX_INLINE_IMAGE = 16 * 1024 * 1024

        /** Le plus grand côté d'une image réduite pour l'envoi : ce qu'un modèle regarde encore. */
        const val SEND_PX = 2048

        /** Le budget mémoire de cette réduction, sous le plafond de dessin. */
        const val SEND_BYTES = 32 * 1024 * 1024

        /** Le gateway rend 4018 quand la ligne n'est pas une commande de son worker. */
        const val SLASH_NOT_A_COMMAND = 4018

        /**
         * Combien de temps la liste des compétences reste fraîche : `api/skills` est relu au plus
         * une fois par minute, quand le menu « / » s'ouvre.
         */
        const val SKILLS_TTL_MS = 60_000L

        /**
         * Le rattrapage d'un tour fini sans client : combien de relectures, et à quel rythme.
         *
         * Douze relectures espacées de cinq secondes couvrent une minute — le temps qu'un run
         * laissé tourner chez l'hôte finisse d'écrire. Au-delà, la bulle reste telle quelle : rien
         * n'est inventé, et le prochain retour sur la conversation retentera.
         */
        const val RECOVERY_ATTEMPTS = 12
        const val RECOVERY_DELAY_MS = 5_000L
    }
}

/**
 * Où poser une note de pilotage : juste avant la réponse en cours, pour qu'elle reste la dernière
 * ligne du fil et que la réponse vivante ne cesse pas de l'être.
 *
 * Miroir de `Conversation.steer` : `messages.lastIndex(where: { $0.role == .assistant })`,
 * la fin du fil quand aucune réponse n'existe encore.
 */
internal fun steerInsertIndex(messages: List<ChatMessage>): Int {
    val last = messages.indexOfLast { it.role == Role.ASSISTANT }
    return if (last < 0) messages.size else last
}
