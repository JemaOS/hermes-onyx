package com.onyx.app.ui.bots

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.voice.VoicePhase
import com.onyx.app.voice.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Une ligne du round vocal: la question de la personne, ou la réponse d'un bot telle qu'elle
 * s'écrit.
 *
 * Les deux fils qui peuvent nourrir une scène vocale — celui d'une salle du serveur
 * (`GroupRoomFeed`) et celui d'une salle neuve encore tenue par le compositeur local
 * (`GroupChatViewModel`) — la produisent, et l'écran la dessine comme le fil de la salle, ce que
 * fait `VoiceView.groupExchange` en reprenant le `MessageRow` du chat.
 */
data class GroupVoiceLine(
    val id: String,
    /**
     * Le **profil** du bot qui a parlé: c'est le nom que le roster, `BotVoices` et la scène
     * connaissent. `null` quand c'est la personne qui a parlé.
     */
    val profile: String?,
    val name: String,
    val faceCode: String? = null,
    /**
     * Son **identifiant dans la salle** (`actorID` du journal, `GroupMember.id`): ce n'est pas
     * toujours son profil — la salle les distingue (`{"kind":"member","id":…,"profile":…}`).
     * C'est lui que le fil dessine et que le protocole nomme; le profil, lui, choisit la voix.
     */
    val memberID: String? = null,
    val text: String,
    /** La réponse s'écrit encore: elle se dessine, mais n'est pas encore lue. */
    val live: Boolean = false,
    val failed: Boolean = false,
    /** Ce que la personne a joint: les chemins sur le serveur, ou les noms tant qu'ils partent. */
    val paths: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    /** Le message de la personne est en route vers la salle, pas encore dans son journal. */
    val waiting: Boolean = false,
    val atMillis: Long = 0L,
    /** Son message dans le journal de la salle (`seq`), `null` tant que la salle ne l'a pas. */
    val seq: Int? = null,
    /** La session vivante qui l'écrit (`GroupDraft.id`), quand elle a été vue s'écrire. */
    val draftId: String? = null,
    /** Le dernier événement du journal quand elle a commencé: l'ordre du round (`GroupReplies`). */
    val after: Int = 0,
) {
    /** La personne: sa question se dessine dans une bulle, à droite. */
    val isMine: Boolean get() = profile == null
}

/** Le dernier round de la salle, comme `VoiceView.groupExchange`: la question et ce qui suit. */
internal fun groupVoiceRound(lines: List<GroupVoiceLine>): List<GroupVoiceLine> {
    val lastAsk = lines.indexOfLast { it.isMine }
    return if (lastAsk < 0) emptyList() else lines.drop(lastAsk)
}

/** Le `phaseTitle` de `VoiceView.group`, avec le membre courant plutôt que le nom de la salle. */
internal fun groupVoicePhaseTitle(
    phase: VoicePhase,
    speaker: String?,
    writer: String?,
    isTalking: Boolean,
): String = when {
    phase == VoicePhase.SPEAKING -> "${speaker ?: "A bot"} is speaking"
    phase == VoicePhase.IDLE && isTalking -> writer?.let { "$it is writing" } ?: "The bots are talking"
    phase == VoicePhase.IDLE -> "Ready"
    phase == VoicePhase.LISTENING -> "Listening"
    phase == VoicePhase.THINKING -> writer?.let { "$it is writing" } ?: "The bots are talking"
    else -> "Ready"
}

/**
 * Le fil qu'une scène vocale lit: la salle d'un serveur (`GroupRoomFeed`), ou la salle neuve d'un
 * compositeur local (`GroupChatViewModel`).
 *
 * C'est le `GroupVoiceScene { voice, feed }`: la scène vocale ne connaît que ce fil, et
 * c'est lui qui dit ce qui se dessine, si les bots travaillent, et où va la question reconnue.
 */
interface GroupVoiceThread {
    /** Le nom de la salle, en tête de l'écran. */
    val roomName: String
    val server: ServerConfig
    /** Ce qui se dessine: la dernière question et ce qui suit, brouillons compris. */
    val lines: StateFlow<List<GroupVoiceLine>>
    /** Les bots travaillent: une discussion en cours, ou la salle au travail. */
    val isTalking: StateFlow<Boolean>
    /** Quelque chose n'attend que la personne: le micro reste fermé (`waitsOnPerson`). */
    val waitsOnPerson: StateFlow<Boolean>
    /** La question reconnue part dans la salle. */
    fun ask(question: String)
    /** Arrête les bots de la salle. */
    fun haltTalking()
    /** La scène vocale s'ouvre: ce fil cesse de lire les réponses lui-même. */
    fun onVoiceStart() {}
    /** La scène vocale se ferme: le fil reprend ce qu'il faisait. */
    fun onVoiceEnd() {}
}

/** La salle passée à `VoiceScreen`, équivalent du `GroupVoiceScene` de `VoiceView.group`. */
data class GroupVoiceScene(
    val roomName: String,
    val server: ServerConfig,
    val bots: List<BotProfile>,
    /** Le round: la dernière question et ce qui suit. */
    val lines: List<GroupVoiceLine>,
    val speakerId: String?,
    val isTalking: Boolean,
    val isHeld: Boolean,
    /** Un bot attend une réponse de la personne (une commande à approuver). */
    val waitsOnPerson: Boolean = false,
) {
    /** Le bot dont la réponse est lue. */
    val speaker: BotProfile? get() = bots.firstOrNull { it.name == speakerId }

    /** Le bot dont la réponse s'écrit à cet instant. */
    val writer: BotProfile? get() = lines.lastOrNull { it.live && !it.isMine && it.text.isNotEmpty() }
        ?.let { line -> bots.firstOrNull { it.name == line.profile } }

    /** La dernière réponse achevée, pour « Replay the last answer ». */
    val lastAnswer: GroupVoiceLine? get() = lines.lastOrNull {
        !it.isMine && !it.live && !it.failed && it.text.isNotBlank()
    }

    /** Ce qui se dessine sous l'orbe: le round, du dernier mot de la personne à la fin. */
    val round: List<GroupVoiceLine> get() = groupVoiceRound(lines)
}

/**
 * Les réponses d'une salle que la voix lit, dans leur ordre de commencement (`GroupReplies`
 * ): du journal de la salle (ses messages) et des réponses qui s'écrivent (`GroupDraft`),
 * chacune lue une fois — un brouillon dont le message arrive ensuite n'est pas relu, et un bot qui
 * passe (« pass ») n'est jamais lu.
 *
 * Une seule limite par rapport au , et elle est dans la lecture, pas dans la file:
 * `VoiceSession` dit un texte d'un bloc (`speak`), là où enchaîne les morceaux au fil de
 * l'écriture (`beginReading`, `continueReading`, `endReading`). Une réponse n'est donc lue qu'une
 * fois entière — la salle l'a écrite, ou le bot a fini de l'écrire — tandis que la file qui suit
 * (ordre de commencement, doublons, passages, brouillons abandonnés) est celle.
 */
internal class GroupVoiceReplies(lines: List<GroupVoiceLine> = emptyList()) {
    /** Le numéro du dernier événement de la salle quand la voix a commencé: ce qui suit est lu. */
    private val since: Int = lines.mapNotNull { it.seq }.maxOrNull() ?: 0

    /** Ce qui attend d'être lu, dans l'ordre où cela a commencé. */
    private val queue = mutableListOf<GroupVoiceLine>()

    /** Les réponses de brouillons déjà lues dont le message n'est pas venu: pas relues ensuite. */
    private val awaiting = mutableListOf<GroupVoiceLine>()

    private var reading: GroupVoiceLine? = null
    private val seenMessages = mutableSetOf<Int>()
    private val seenDrafts = mutableSetOf<String>()

    /**
     * Les brouillons lâchés avant d'être entiers (la personne a parlé, ou Stop): ils ne sont pas
     * lus plus tard, quand ils finissent d'écrire.
     */
    private val letGo = mutableSetOf<String>()

    /** Ce qui attend d'être lu. */
    val pending: List<GroupVoiceLine> get() = queue.toList()

    /** La réponse en cours de lecture. */
    val current: GroupVoiceLine? get() = reading

    /** La salle telle qu'elle est maintenant: les nouvelles réponses entrent dans la file. */
    fun update(lines: List<GroupVoiceLine>, held: Boolean) {
        val messages = lines.filter {
            !it.isMine && it.seq != null && it.seq > since && it.text.isNotEmpty() && it.profile != null
        }
        for (message in messages) {
            val seq = message.seq ?: continue
            if (!seenMessages.add(seq)) continue
            if (!attach(message)) enqueue(message)
        }
        val drafts = lines.filter { it.draftId != null }
        for (draft in drafts) {
            val member = draft.profile ?: continue
            val id = draft.draftId ?: continue
            val waiting = queue.indexOfFirst { it.draftId == id }
            when {
                reading?.draftId == id -> reading = reading?.take(draft)
                waiting >= 0 -> queue[waiting] = queue[waiting].take(draft)
                // Déjà vu — et pas encore entier alors: c'est maintenant qu'il peut l'être.
                id in seenDrafts -> {
                    if (whole(draft) && id !in letGo && awaiting.none { it.draftId == id }) enqueue(draft)
                }
                mayBePass(draft.text) -> Unit
                // Son message est déjà dans le journal: c'est lui qui est lu, pas le brouillon.
                messages.any { it.profile == member && (it.seq ?: 0) > draft.after } ->
                    seenDrafts.add(id)
                else -> {
                    seenDrafts.add(id)
                    if (whole(draft)) enqueue(draft)
                }
            }
        }
        // Un brouillon parti sans message: ce qui en a été écrit est tout ce qu'il y aura.
        val live = drafts.mapNotNull { it.draftId }.toSet()
        queue.removeAll { gone(it, live) }
        awaiting.removeAll { gone(it, live) }
        if (held) dropAll()
    }

    /** La réponse à lire maintenant, s'il y en a une. */
    fun takeNext(): GroupVoiceLine? {
        if (reading != null || queue.isEmpty()) return null
        reading = queue.removeFirst()
        return reading
    }

    /** La réponse lue est finie: entendue jusqu'au bout, ou coupée. */
    fun finishReading() {
        val reply = reading
        if (reply != null && reply.seq == null && reply.draftId != null) awaiting.add(reply)
        reading = null
    }

    /** Tout ce qui est connu est lâché sans être lu (la personne a parlé, ou Stop). */
    fun dropAll() {
        finishReading()
        awaiting.addAll(queue.filter { it.seq == null && it.draftId != null })
        // Ce qui était connu et pas encore entier est lâché aussi: il ne sera pas lu plus tard.
        letGo.addAll(seenDrafts)
        queue.clear()
    }

    /** `line` est une réponse déjà connue par son brouillon: elle est entière maintenant. */
    private fun attach(line: GroupVoiceLine): Boolean {
        fun matches(reply: GroupVoiceLine): Boolean =
            reply.draftId != null && reply.seq == null && reply.profile == line.profile &&
                reply.after < (line.seq ?: 0)

        val speaking = reading
        if (speaking != null && matches(speaking)) {
            reading = speaking.copy(seq = line.seq, text = line.text)
            return true
        }
        val index = queue.indexOfFirst { matches(it) }
        if (index >= 0) {
            queue[index] = queue[index].copy(seq = line.seq, text = line.text)
            return true
        }
        val waiting = awaiting.indexOfFirst { matches(it) }
        if (waiting >= 0) {
            awaiting.removeAt(waiting)
            return true
        }
        return false
    }

    /** Tout est connu: la salle l'a écrit, ou le bot a fini de l'écrire (et ce n'est pas un passage). */
    private fun whole(line: GroupVoiceLine): Boolean =
        line.seq != null || (!line.live && !mayBePass(line.text))

    /**
     * Un brouillon parti de la salle sans que son message ne l'ait remplacé: ce qui en a été écrit
     * est tout ce qu'il y aura. Un brouillon jamais entier (pas encore commencé) n'est pas lu.
     */
    private fun gone(reply: GroupVoiceLine, live: Set<String>): Boolean =
        reply.draftId != null && reply.draftId !in live && reply.seq == null && !whole(reply)

    private fun enqueue(reply: GroupVoiceLine) {
        val at = queue.indexOfFirst { it.after > reply.after }
        queue.add(if (at < 0) queue.size else at, reply)
    }

    /** Le brouillon dit ce qu'il en est maintenant: `seq` posé, le texte ne bouge plus. */
    private fun GroupVoiceLine.take(draft: GroupVoiceLine): GroupVoiceLine =
        if (seq != null) this else copy(text = draft.text, live = draft.live)

    companion object {
        /** Un texte qu'un bot qui passe écrit: « pass », « (pass) », « Pass. ». */
        private val PASS = Regex("^\\(?\\s*p(a(s(s)?)?)?\\s*\\)?\\.?$", RegexOption.IGNORE_CASE)

        internal fun mayBePass(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.isEmpty() || PASS.matches(trimmed)
        }
    }
}

/**
 * Coordinateur de: micro → question dans la salle → lecture des réponses une
 * par une → micro. La scène Compose ne fait que le rendre, et le fil ([GroupVoiceThread]) est
 * celui de la salle: ce sont les réponses du serveur qui sont lues, et la question reconnue part
 * par `groups.send`.
 */
internal class GroupVoice(
    private val thread: GroupVoiceThread,
    private val session: VoiceSession,
    private val scope: CoroutineScope,
) {
    private var replies = GroupVoiceReplies(thread.lines.value)
    private var jobs = mutableListOf<Job>()
    private var current: GroupVoiceLine? = null
    private var wantsListening = false

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _isHeld = MutableStateFlow(false)
    val isHeld: StateFlow<Boolean> = _isHeld.asStateFlow()

    private val _speakerId = MutableStateFlow<String?>(null)
    val speakerId: StateFlow<String?> = _speakerId.asStateFlow()

    fun start() {
        if (_isOn.value) return
        _isOn.value = true
        _isHeld.value = false
        _speakerId.value = null
        wantsListening = false
        current = null
        // Ce que la salle a déjà n'est pas relu; ce qui suit l'est, et ce qui s'écrit maintenant.
        replies = GroupVoiceReplies(thread.lines.value)
        // La lecture appartient à la scène vocale tant qu'elle est ouverte.
        thread.onVoiceStart()
        session.stop()

        jobs += scope.launch {
            combine(thread.lines, thread.isTalking) { lines, talking -> lines to talking }
                .collect { (lines, _) ->
                    replies.update(lines, held = _isHeld.value)
                    next()
                }
        }
        jobs += scope.launch {
            session.phase.collect { phase ->
                when (phase) {
                    VoicePhase.LISTENING -> {
                        // Une nouvelle question l'emporte sur les réponses qui attendaient.
                        // Le micro rouvert par la demande de permission suit le même chemin que
                        // `listen`: la salle reste mains-libres après la réponse.
                        wantsListening = true
                        replies.dropAll()
                        current = null
                        _speakerId.value = null
                        _isHeld.value = false
                    }
                    VoicePhase.IDLE -> {
                        if (current != null) {
                            replies.finishReading()
                            current = null
                            _speakerId.value = null
                        }
                        next()
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Ouvre le micro, ou interrompt la voix courante pour répondre à la salle. */
    fun listen() {
        if (!_isOn.value) return
        _isHeld.value = false
        wantsListening = false
        replies.dropAll()
        current = null
        _speakerId.value = null
        session.stop()
        wantsListening = true
        session.startListening()
    }

    /** Une question reconnue part dans la salle (pour une salle du serveur: `groups.send`). */
    fun ask(question: String) {
        if (!_isOn.value) return
        replies.dropAll()
        thread.ask(question)
    }

    /**
     * Stop rouge: plus de lecture ni de micro, et le round courant est arrêté (`groups.stop` pour
     * une salle du serveur), comme le Stop du chat. Rien n'est relu tant que le micro n'est pas
     * rouvert.
     */
    fun halt() {
        if (!_isOn.value) return
        _isHeld.value = true
        wantsListening = false
        current = null
        _speakerId.value = null
        replies.dropAll()
        if (thread.isTalking.value) thread.haltTalking()
        session.stop()
    }

    /** Quitter la scène ne supprime pas le fil; le round texte peut continuer dans le chat. */
    fun stop() {
        if (!_isOn.value) return
        jobs.forEach { it.cancel() }
        jobs.clear()
        _isOn.value = false
        _isHeld.value = false
        _speakerId.value = null
        wantsListening = false
        current = null
        replies.dropAll()
        session.stop()
        thread.onVoiceEnd()
    }

    fun pauseOrResume() {
        if (session.isPaused.value) session.resumeSpeaking() else session.pauseSpeaking()
    }

    /** Rejoue la dernière réponse achevée, sous le visage de son bot. */
    fun replayLast() {
        if (!_isOn.value || _isHeld.value || current != null || thread.waitsOnPerson.value) return
        if (session.phase.value != VoicePhase.IDLE || replies.pending.isNotEmpty()) return
        val last = thread.lines.value.lastOrNull {
            !it.isMine && !it.live && !it.failed && it.text.isNotBlank()
        } ?: return
        current = last
        _speakerId.value = last.profile
        // La réponse est relue **de la voix de son bot**, comme la première fois.
        session.speak(last.text, speaker = last.profile)
    }

    private fun next() {
        if (!_isOn.value || _isHeld.value || current != null) return
        val phase = session.phase.value
        if (phase != VoicePhase.IDLE && phase != VoicePhase.THINKING) return

        val reply = replies.takeNext()
        if (reply != null) {
            current = reply
            _speakerId.value = reply.profile
            // Chacun parle **avec sa propre voix**: c'est le profil du bot qui la choisit
            // (`BotVoices`), jamais une hauteur inventée sur la voix d'un autre.
            session.speak(reply.text, speaker = reply.profile)
            return
        }

        _speakerId.value = null
        // Un bot attend la personne (une commande à approuver): le micro reste fermé.
        if (thread.waitsOnPerson.value) return
        if (phase == VoicePhase.THINKING) {
            session.finishTurn(null)
        } else if (wantsListening) {
            session.startListening()
        }
    }
}
