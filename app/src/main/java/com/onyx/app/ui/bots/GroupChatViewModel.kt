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
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.voice.SpeechOutput
import com.onyx.app.ui.chat.PendingAttachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Un tour du chat de groupe: la question, ou la réponse d'un bot. */
data class GroupMessage(
    val id: String,
    val bot: BotProfile?,
    val text: String,
    /**
     * Son numéro dans le fil local, posé à sa naissance: c'est ce qui ordonne la lecture à voix
     * haute d'une salle neuve, comme le `seq` du journal d'une salle du serveur
     * (`GroupVoiceReplies`). Posé une fois, il ne bouge plus, même si un message est retiré.
     */
    val seq: Int = 0,
    val isStreaming: Boolean = false,
    val failed: Boolean = false,
    /** Noms affichés pendant l'upload; les notes du texte donnent ensuite les vraies chips. */
    val pendingFiles: List<String> = emptyList(),
    val waiting: Boolean = false,
)

data class GroupUiState(
    val bots: List<BotProfile> = emptyList(),
    /** Les bots qui répondent, par nom de profil. */
    val selected: Set<String> = emptySet(),
    val messages: List<GroupMessage> = emptyList(),
    val asking: Boolean = false,
    val readAloud: Boolean = true,
    val error: String? = null,
    val loading: Boolean = true,
)

/**
 * Seul le gateway Hermes sait appliquer le profil et le provider associé au modèle brut d'un bot.
 * Sur un endpoint OpenAI-compatible non scopé, la configuration de la connexion (notamment un
 * alias `model_routes`) reste donc autoritaire: la remplacer par `BotProfile.model` changerait
 * de backend sans pouvoir changer de provider et peut mener à un serveur local inexistant.
 */
internal fun groupBotConfig(connection: ServerConfig, bot: BotProfile): ServerConfig {
    if (connection.transport != Transport.HERMES_SESSIONS) return connection
    return connection.copy(
        hermesProfile = if (bot.isDefault) "" else bot.name,
        model = bot.model ?: connection.model,
    )
}

/**
 * L'API OpenAI d'Hermes considère le dernier message comme le nouveau prompt. Les réponses déjà
 * données dans ce round appartiennent donc au contexte, et la question courante est répétée à la
 * fin. Les erreurs de transport et les brouillons ne sont jamais des paroles à faire relire à un
 * autre bot. Les réponses d'un autre bot gardent son nom et un rôle `user`; les nôtres sont des
 * réponses `assistant` dans l'historique de ce bot.
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
 * Le chat de groupe: plusieurs bots répondent à tour de rôle.
 *
 * Comme `GroupChatView` / `GroupComposer` / `GroupVoice`: on choisit qui répond,
 * chacun parle dans son profil (donc avec son modèle et sa personnalité), et les réponses
 * s'écrivent l'une après l'autre — lues à voix haute si on le veut.
 */
class GroupChatViewModel(
    application: Application,
    private val config: ServerConfig,
    /** Les profils d'une salle du serveur ouverte depuis la liste: cochés d'avance. */
    private val preselected: List<String>? = null,
) : AndroidViewModel(application), GroupVoiceThread {

    private val profiles = ServeProfilesApi(ServeClient(config))
    private val faceStore = BotFaceStore(application)
    private val attachments = ServerAttachments(ServeFilesApi(ServeClient(config)))
    private val speech = SpeechOutput(application)
    private var sendJob: Job? = null
    private var roundGeneration = 0L

    /**
     * Le numéro du dernier message du fil local. Il ne recule jamais: c'est lui qui ordonne la
     * lecture d'une salle neuve (`GroupVoiceLine.seq`).
     */
    private var messageSequence = 0

    private val _state = MutableStateFlow(GroupUiState())
    val state: StateFlow<GroupUiState> = _state.asStateFlow()

    // — Ce qu'une scène vocale lit dans ce compositeur local (`GroupVoiceThread`) —

    override val server: ServerConfig get() = config

    override val roomName: String get() = OnyxLocalization["Group chat"]

    /** Le fil local, comme les lignes d'une salle du serveur: la question, puis chaque réponse. */
    override val lines: StateFlow<List<GroupVoiceLine>> =
        state.map { ui ->
            ui.messages.map { message ->
                GroupVoiceLine(
                    id = "voice-" + message.id,
                    // Le profil du bot: c'est lui qui choisit sa voix (`BotVoices`) et son visage.
                    profile = message.bot?.name,
                    memberID = message.bot?.name,
                    name = message.bot?.label() ?: "",
                    faceCode = message.bot?.faceCode,
                    text = message.text,
                    live = message.isStreaming,
                    failed = message.failed,
                    // Le numéro du message ordonne la lecture, comme le `seq` d'une salle du
                    // serveur; tant qu'il s'écrit, c'est un brouillon — il n'est pas lu à moitié.
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

    /** Ce transport local ne connaît pas d'approbation: rien n'attend la personne. */
    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    /** Une question reconnue remplace un round encore actif, que ce transport ne peut chevaucher. */
    override fun ask(question: String) {
        if (_state.value.asking) stop()
        send(question)
    }

    override fun haltTalking() = stop()

    /** La lecture à voix haute passe à la scène vocale tant qu'elle est ouverte. */
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
        viewModelScope.launch {
            try {
                val source = profiles.visible(config.displayName)
                val local = source.associate { it.name to faceStore.faceOf(it.name) }
                val bots = source.withOnyxFaces(local)
                // Par défaut, tous ceux qui sont réglés comme bots; sinon les deux premiers. Une
                // salle ouverte depuis la liste part de ses propres membres.
                val chosen = preselected?.filter { name -> bots.any { it.name == name } }
                    ?.takeIf { it.isNotEmpty() }
                    ?: bots.filter { it.setUp }.map { it.name }.ifEmpty { bots.take(2).map { it.name } }
                _state.update { it.copy(bots = bots, selected = chosen.toSet(), loading = false) }
                // Un choix fait avant que la liste n'arrive (« New Group Chat ») prend sa place.
                applyPendingSelection()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = (error as? TransportError)?.userMessage() ?: error.message)
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
     * Les membres d'une salle neuve, choisis dans `NewGroupChatView`: exactement ceux-là, et rien
     * d'autre. La liste des bots peut n'être pas encore chargée — le choix est alors gardé et posé
     * dès qu'elle arrive.
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

    /** Pose la question à chaque bot choisi, après dépôt des pièces jointes sur le serveur. */
    fun send(question: String, pendingAttachments: List<PendingAttachment> = emptyList()) {
        val clean = question.trim()
        val initial = _state.value
        if ((clean.isEmpty() && pendingAttachments.isEmpty())) return
        val asked = initial.bots.filter { it.name in initial.selected }
        if (asked.isEmpty()) return
        // Le transport local est séquentiel: une nouvelle question arrête proprement la ronde
        // précédente au lieu de laisser le bouton « Send after this reply » sans effet.
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
                        val reason = (error as? TransportError)?.userMessage() ?: error.message ?: OnyxLocalization["No answer."]
                        updateMessage(messageId) { it.copy(isStreaming = false, failed = true, text = reason) }
                    }

                    if (_state.value.readAloud) {
                        val said = builder.toString()
                        if (said.isNotBlank()) speech.speak(said)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val reason = (error as? TransportError)?.userMessage() ?: error.message ?: OnyxLocalization["unknown error"]
                _state.update {
                    it.copy(
                        messages = it.messages.filterNot { message -> message.id == userId },
                        error = OnyxLocalization.format("Couldn't send: %1\$s", reason),
                    )
                }
            } finally {
                // Un round annulé ne doit pas marquer le round suivant comme terminé.
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
