package com.onyx.app.ui.bots

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeProfilesApi
import com.onyx.app.data.net.ServerAttachment
import com.onyx.app.data.net.ServerAttachments
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.humanFailure
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.di.AppContainer
import com.onyx.app.voice.ReplyVoice
import com.onyx.app.voice.SpeechOutput
import com.onyx.app.voice.VoiceDiagnostics
import com.onyx.app.ui.chat.PendingAttachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** A group chat turn: the question, or a bot's reply. */
data class GroupMessage(
    val id: String,
    val bot: BotProfile?,
    val text: String,
    /**
     * Its number in the local thread, set at birth: it is what orders the read-aloud of a fresh
     * room, like the journal's `seq` of a server room
     * (`GroupVoiceReplies`). Set once, it never moves, even if a message is removed.
     */
    val seq: Int = 0,
    val isStreaming: Boolean = false,
    val failed: Boolean = false,
    /** Names shown during upload; the text notes then provide the real chips. */
    val pendingFiles: List<String> = emptyList(),
    val waiting: Boolean = false,
)

data class GroupUiState(
    val bots: List<BotProfile> = emptyList(),
    /** The bots that answer, by profile name. */
    val selected: Set<String> = emptySet(),
    val messages: List<GroupMessage> = emptyList(),
    val asking: Boolean = false,
    val readAloud: Boolean = true,
    val error: String? = null,
    val loading: Boolean = true,
)

/**
 * True when a group thread has enough to open **another** conversation: it said something,
 * a round is still writing, or it carries an error to read. A blank thread returns false — it is
 * resumed as is, otherwise the pencil would stack two empty conversations.
 */
internal fun groupStartsAnotherConversation(
    messages: List<GroupMessage>,
    asking: Boolean,
    error: String?,
): Boolean = messages.isNotEmpty() || asking || error != null

/**
 * Only the Hermes gateway knows how to apply the profile and the provider tied to a bot's raw model.
 * On an unscoped OpenAI-compatible endpoint, the connection configuration (notably a
 * `model_routes` alias) therefore remains authoritative: replacing it with `BotProfile.model` would
 * change backend without being able to change provider and may lead to a nonexistent local server.
 */
internal fun groupBotConfig(connection: ServerConfig, bot: BotProfile): ServerConfig {
    if (connection.transport != Transport.HERMES_SESSIONS) return connection
    return connection.copy(
        hermesProfile = if (bot.isDefault) "" else bot.name,
        model = bot.model ?: connection.model,
    )
}

/**
 * Hermes' OpenAI API treats the last message as the new prompt. The replies already
 * given in this round therefore belong to the context, and the current question is repeated at the
 * end. Transport errors and drafts are never words to make another bot re-read. Another bot's
 * replies keep its name and a `user` role; ours are `assistant`
 * replies in that bot's history.
 */
internal fun groupHistory(messages: List<GroupMessage>, bot: BotProfile): List<ChatTurn> {
    val latestQuestion = messages.indexOfLast { it.bot == null }
    if (latestQuestion < 0) return emptyList()
    return buildList {
        messages.forEachIndexed { index, message ->
            if (index == latestQuestion || message.failed || message.isStreaming || message.text.isBlank()) {
                return@forEachIndexed
            }
            when (val speaker = message.bot) {
                null -> add(ChatTurn(role = Role.USER, text = message.text))
                else -> add(
                    ChatTurn(
                        role = if (speaker.name == bot.name) Role.ASSISTANT else Role.USER,
                        text = "${speaker.label()}: ${message.text}",
                    ),
                )
            }
        }
        add(ChatTurn(role = Role.USER, text = messages[latestQuestion].text))
    }
}

/**
 * The voice settings **this** reader needs, gathered for `combine`: the chosen synthesis,
 * the Kokoro address and voice, its token, and the voices set for this server's bots.
 */
private data class VoiceSetup(
    val replyVoice: ReplyVoice,
    val kokoroUrl: String,
    val kokoroVoice: String,
    val kokoroToken: String,
    val botVoices: Map<String, com.onyx.app.voice.BotVoice>,
)

/**
 * The group chat: several bots answer in turn.
 *
 * Mirror of the reference's `GroupChatView` / `GroupComposer` / `GroupVoice`: you choose who answers,
 * each speaks in its profile (hence with its model and personality), and the replies
 * are written one after another — read aloud if desired.
 */
class GroupChatViewModel(
    application: Application,
    private val config: ServerConfig,
    /** The profiles of a server room opened from the list: pre-checked. */
    private val preselected: List<String>? = null,
) : AndroidViewModel(application), GroupVoiceThread {

    private val profiles = ServeProfilesApi(ServeClient(config))
    private val faceStore = BotFaceStore(application)
    private val attachments = ServerAttachments(ServeFilesApi(ServeClient(config)))
    private val speech = SpeechOutput(application)
    private var sendJob: Job? = null
    private var roundGeneration = 0L

    /**
     * The number of the last message in the local thread. It never goes backwards: it is what orders
     * the reading of a fresh room (`GroupVoiceLine.seq`).
     */
    private var messageSequence = 0

    private val _state = MutableStateFlow(GroupUiState())
    val state: StateFlow<GroupUiState> = _state.asStateFlow()

    // — What a voice scene reads in this local composer (`GroupVoiceThread`) —

    override val server: ServerConfig get() = config

    override val roomName: String get() = OnyxLocalization["Group chat"]

    /** The local thread, like a server room's lines: the question, then each reply. */
    override val lines: StateFlow<List<GroupVoiceLine>> =
        state.map { ui ->
            ui.messages.map { message ->
                GroupVoiceLine(
                    id = "voice-" + message.id,
                    // The bot's profile: it is what chooses its voice (`BotVoices`) and its face.
                    profile = message.bot?.name,
                    memberID = message.bot?.name,
                    name = message.bot?.label() ?: "",
                    faceCode = message.bot?.faceCode,
                    text = message.text,
                    live = message.isStreaming,
                    failed = message.failed,
                    // The message number orders the reading, like a server room's
                    // `seq`; while it is being written, it is a draft — it is not half-read.
                    seq = message.seq.takeIf { it > 0 && !message.isStreaming },
                    draftId = if (message.isStreaming) "local-" + message.id else null,
                    after = (message.seq - 1).coerceAtLeast(0),
                    files = message.pendingFiles,
                    waiting = message.waiting,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    override val isTalking: StateFlow<Boolean> =
        state.map { it.asking }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** This local transport knows no approval: nothing waits on the person. */
    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    /** What this thread refused (a question that did not go out): the voice scene shows it. */
    override val problem: StateFlow<String?> =
        state.map { it.error }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A recognised question replaces a still-active round, which this transport cannot overlap. */
    override fun ask(question: String, askId: String) {
        // The same question is not asked again: its identifier already said it.
        if (!askedIds.add(askId)) {
            VoiceDiagnostics.note("voice (local room): question already asked — it is not sent again ($askId)")
            return
        }
        if (_state.value.asking) stop()
        send(question)
    }

    /** The questions already asked by voice, by their identifier. */
    private val askedIds = mutableSetOf<String>()

    override fun haltTalking() = stop()

    /** Read-aloud passes to the voice scene while it is open. */
    private var readAloudBefore = true

    override fun onVoiceStart() {
        readAloudBefore = _state.value.readAloud
        setReadAloud(false)
    }

    override fun onVoiceEnd() {
        setReadAloud(readAloudBefore)
    }

    init {
        speech.setRate(1f)
        // **Each bot's voice must be heard here too.** The read-aloud of a room that is
        // being written goes through **this** reader, and it carried no setting: a bot thus read
        // in Onyx's voice, whatever its "Voice" (bot menu -> "Voice…"). The same settings as the
        // thread are applied to it (`OnyxApp` -> `SpeechOutput.configure`): without them, the
        // profile passed by `speak` would have nothing to apply to.
        viewModelScope.launch {
            val settings = AppContainer.settings
            combine(
                settings.replyVoice,
                settings.kokoroUrl,
                settings.kokoroVoice,
                settings.kokoroToken,
                settings.botVoices,
            ) { voice, kokoroUrl, kokoroVoice, kokoroToken, botVoices ->
                VoiceSetup(voice, kokoroUrl, kokoroVoice, kokoroToken, botVoices[config.id].orEmpty())
            }.collect { setup ->
                speech.configure(
                    voice = setup.replyVoice,
                    kokoroUrl = setup.kokoroUrl,
                    kokoroVoice = setup.kokoroVoice,
                    kokoroToken = setup.kokoroToken,
                    // Hermes reads with the server voice, one for all: nothing more to set.
                    hermesServer = config,
                    botVoices = setup.botVoices,
                )
            }
        }
        viewModelScope.launch {
            try {
                val source = profiles.visible(config.displayName)
                val local = source.associate { it.name to faceStore.faceOf(it.name) }
                val bots = source.withSwiftFaces(local)
                // By default, all those set up as bots; otherwise the first two. A room
                // opened from the list starts from its own members.
                val chosen = preselected?.filter { name -> bots.any { it.name == name } }
                    ?.takeIf { it.isNotEmpty() }
                    ?: bots.filter { it.setUp }.map { it.name }.ifEmpty { bots.take(2).map { it.name } }
                _state.update { it.copy(bots = bots, selected = chosen.toSet(), loading = false) }
                // A choice made before the list arrives ("New Group Chat") takes its place.
                applyPendingSelection()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = humanFailure(error))
                }
            }
        }
    }

    fun toggle(bot: BotProfile) {
        _state.update { state ->
            val selected = state.selected.toMutableSet()
            if (!selected.add(bot.name)) selected.remove(bot.name)
            state.copy(selected = selected)
        }
    }

    /**
     * The members of a fresh room, chosen in `NewGroupChatView`: exactly those, and nothing
     * else. The bot list may not be loaded yet — the choice is then kept and applied
     * as soon as it arrives.
     */
    fun setSelected(names: Set<String>) {
        pendingSelection = names
        applyPendingSelection()
    }

    private var pendingSelection: Set<String>? = null

    private fun applyPendingSelection() {
        val wanted = pendingSelection ?: return
        val bots = _state.value.bots
        if (_state.value.loading || bots.isEmpty()) return
        val known = bots.map { it.name }.toSet()
        _state.update { it.copy(selected = wanted.intersect(known)) }
        pendingSelection = null
    }

    fun setReadAloud(value: Boolean) {
        _state.update { it.copy(readAloud = value) }
        if (!value) speech.stop()
    }

    fun stop() {
        roundGeneration += 1
        sendJob?.cancel()
        sendJob = null
        speech.stop()
        _state.update {
            it.copy(
                asking = false,
                messages = it.messages.filterNot { message -> message.waiting }
                    .map { message -> message.copy(isStreaming = false) },
            )
        }
    }

    /**
     * A new conversation for this room: the thread starts empty and the chosen bots
     * **remain** — same members, same room —, and whatever was still writing is stopped cleanly.
     *
     * An **already empty** thread is resumed as is: a blank thread does not stack a second one
     * (nothing changes on screen, the conversation there is already new).
     */
    fun startNewConversation() {
        val current = _state.value
        if (!groupStartsAnotherConversation(current.messages, current.asking, current.error)) return
        stop()
        messageSequence = 0
        _state.update { it.copy(messages = emptyList(), error = null) }
    }

    /** Asks the question to each chosen bot, after uploading the attachments to the server. */
    fun send(question: String, pendingAttachments: List<PendingAttachment> = emptyList()) {
        val clean = question.trim()
        val initial = _state.value
        if ((clean.isEmpty() && pendingAttachments.isEmpty())) return
        val asked = initial.bots.filter { it.name in initial.selected }
        if (asked.isEmpty()) return
        // The local transport is sequential: a new question cleanly stops the previous
        // round instead of leaving the "Send after this reply" button without effect.
        if (initial.asking) stop()

        val userId = UUID.randomUUID().toString()
        val userSeq = ++messageSequence
        _state.update {
            it.copy(
                asking = true,
                error = null,
                messages = it.messages + GroupMessage(
                    id = userId,
                    bot = null,
                    text = clean,
                    seq = userSeq,
                    pendingFiles = pendingAttachments.map { attachment -> attachment.name },
                    waiting = pendingAttachments.isNotEmpty(),
                ),
            )
        }

        val generation = ++roundGeneration
        sendJob = viewModelScope.launch {
            try {
                if (pendingAttachments.isNotEmpty()) {
                    val outgoing = withContext(Dispatchers.IO) {
                        pendingAttachments.map { attachment -> readServerAttachment(attachment) }
                    }
                    val placed = attachments.place(outgoing)
                    val said = placed.fold(clean) { text, file ->
                        text + if (file.source.isImage) {
                            AttachedFileNote.picture(file.path)
                        } else {
                            AttachedFileNote.note(file.path)
                        }
                    }
                    updateMessage(userId) {
                        it.copy(text = said, pendingFiles = emptyList(), waiting = false)
                    }
                }

                for (bot in asked) {
                    val messageId = UUID.randomUUID().toString()
                    val replySeq = ++messageSequence
                    _state.update {
                        it.copy(
                            messages = it.messages + GroupMessage(
                                id = messageId,
                                bot = bot,
                                text = "",
                                seq = replySeq,
                                isStreaming = true,
                            ),
                        )
                    }

                    val botConfig = groupBotConfig(config, bot)
                    val builder = StringBuilder()
                    try {
                        ChatCompletionsClient(botConfig).stream(
                            ChatRequest(
                                history = groupHistory(_state.value.messages, bot),
                                model = botConfig.effectiveModel,
                            ),
                        ).collect { event ->
                            when (event) {
                                is TurnEvent.TextDelta -> {
                                    builder.append(event.text)
                                    updateMessage(messageId) { it.copy(text = builder.toString()) }
                                }

                                is TurnEvent.TextFinal -> {
                                    builder.clear()
                                    builder.append(event.text)
                                    updateMessage(messageId) { it.copy(text = event.text) }
                                }

                                else -> Unit
                            }
                        }
                        updateMessage(messageId) { it.copy(isStreaming = false) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val reason = humanFailure(error, OnyxLocalization["No answer."])
                        updateMessage(messageId) { it.copy(isStreaming = false, failed = true, text = reason) }
                    }

                    if (_state.value.readAloud) {
                        val said = builder.toString()
                        // The reply comes out in **its bot's voice** (`BotVoices`), like the server
                        // room (`GroupVoice` -> `session.speak(…, speaker = reply.profile)`).
                        if (said.isNotBlank()) speech.speak(said, speaker = bot.name)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val reason = humanFailure(error, OnyxLocalization["unknown error"])
                _state.update {
                    it.copy(
                        messages = it.messages.filterNot { message -> message.id == userId },
                        error = OnyxLocalization.format("Couldn't send: %1\$s", reason),
                    )
                }
            } finally {
                // A cancelled round must not mark the next round as finished.
                if (roundGeneration == generation) {
                    _state.update {
                        it.copy(
                            asking = false,
                            messages = it.messages.map { message -> message.copy(isStreaming = false) },
                        )
                    }
                }
            }
        }
    }

    private fun readServerAttachment(attachment: PendingAttachment): ServerAttachment {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(attachment.uri)?.use { it.readBytes() }
            ?: throw IOException("Couldn't read ${attachment.name}")
        return ServerAttachment(name = attachment.name, bytes = bytes, isImage = attachment.isImage)
    }

    private fun updateMessage(id: String, transform: (GroupMessage) -> GroupMessage) {
        _state.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    override fun onCleared() {
        sendJob?.cancel()
        speech.shutdown()
        super.onCleared()
    }
}
