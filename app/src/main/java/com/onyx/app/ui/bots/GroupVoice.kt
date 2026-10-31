package com.onyx.app.ui.bots

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.voice.VoiceDiagnostics
import com.onyx.app.voice.VoicePhase
import com.onyx.app.voice.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Une ligne du round vocal : la question de la personne, ou la réponse d'un bot telle qu'elle
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
     * Le **profil** du bot qui a parlé : c'est le nom que le roster, `BotVoices` et la scène
     * connaissent. `null` quand c'est la personne qui a parlé.
     */
    val profile: String?,
    val name: String,
    val faceCode: String? = null,
    /**
     * Son **identifiant dans la salle** (`actorID` du journal, `GroupMember.id`) : ce n'est pas
     * toujours son profil — la salle les distingue (`{"kind":"member","id":…,"profile":…}`).
     * C'est lui que le fil dessine et que le protocole nomme ; le profil, lui, choisit la voix.
     */
    val memberID: String? = null,
    val text: String,
    /** La réponse s'écrit encore : elle se dessine, mais n'est pas encore lue. */
    val live: Boolean = false,
    val failed: Boolean = false,
    /** Ce que la personne a joint : les chemins sur le serveur, ou les noms tant qu'ils partent. */
    val paths: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    /** Le message de la personne est en route vers la salle, pas encore dans son journal. */
    val waiting: Boolean = false,
    val atMillis: Long = 0L,
    /** Son message dans le journal de la salle (`seq`), `null` tant que la salle ne l'a pas. */
    val seq: Int? = null,
    /** La session vivante qui l'écrit (`GroupDraft.id`), quand elle a été vue s'écrire. */
    val draftId: String? = null,
    /** Le dernier événement du journal quand elle a commencé : l'ordre du round (`GroupReplies`). */
    val after: Int = 0,
) {
    /** La personne : sa question se dessine dans une bulle, à droite. */
    val isMine: Boolean get() = profile == null
}

/** Le dernier round de la salle, comme `VoiceView.groupExchange` : la question et ce qui suit. */
internal fun groupVoiceRound(lines: List<GroupVoiceLine>): List<GroupVoiceLine> {
    val lastAsk = lines.indexOfLast { it.isMine }
    return if (lastAsk < 0) emptyList() else lines.drop(lastAsk)
}

/**
 * Le `phaseTitle` de `VoiceView.group`, avec le membre courant plutôt que le nom de la salle.
 *
 * **L'état ne ment pas** : « PRÊT » ne se dit que lorsque le tour est vraiment fini. Tant qu'une
 * réponse attend d'être lue ([pending]), qu'un message est encore en route vers la salle
 * ([sending]) ou que la salle travaille ([isTalking]), l'écran dit ce qui se passe — le nom du bot
 * qui parle, celui qui écrit, ou « LES BOTS DISCUTENT ». Le défaut rapporté : « PRÊT » s'affichait
 * alors que les réponses étaient là et que la file vocale n'était pas vide.
 */
internal fun groupVoicePhaseTitle(
    phase: VoicePhase,
    speaker: String?,
    writer: String?,
    isTalking: Boolean,
    /**
     * La salle a été **annoncée au travail** et n'a rien produit du tout : l'attente est finie
     * (`GroupVoice.stalled`). La légende générique « les bots discutent » ne doit alors plus
     * tourner dans le vide — elle dit que personne ne répond, ce qui est la vérité.
     */
    stalled: Boolean = false,
    pending: Int = 0,
    sending: Boolean = false,
): String {
    val waiting = isTalking || pending > 0 || sending || phase == VoicePhase.THINKING
    return when {
        phase == VoicePhase.SPEAKING -> "${speaker ?: "A bot"} is speaking"
        phase == VoicePhase.LISTENING -> "Listening"
        // Un bot nommé passe **toujours** avant une légende générique : celui qui écrit, puis celui
        // qui parle. « Personne ne répond » ne prend la place que d'un vide — et jamais celle d'un
        // bot qui a un nom.
        waiting -> writer?.let { "$it is writing" }
            ?: if (stalled) "Nobody is answering" else "The bots are talking"
        stalled -> "Nobody is answering"
        else -> "Ready"
    }
}

/**
 * Le fil qu'une scène vocale lit : la salle d'un serveur (`GroupRoomFeed`), ou la salle neuve d'un
 * compositeur local (`GroupChatViewModel`).
 *
 * C'est le le `GroupVoiceScene { voice, feed }` de la référence : la scène vocale ne connaît que ce fil, et
 * c'est lui qui dit ce qui se dessine, si les bots travaillent, et où va la question reconnue.
 */
interface GroupVoiceThread {
    /** Le nom de la salle, en tête de l'écran. */
    val roomName: String
    val server: ServerConfig
    /** Ce qui se dessine : la dernière question et ce qui suit, brouillons compris. */
    val lines: StateFlow<List<GroupVoiceLine>>
    /** Les bots travaillent : une discussion en cours, ou la salle au travail. */
    val isTalking: StateFlow<Boolean>
    /** Quelque chose n'attend que la personne : le micro reste fermé (`waitsOnPerson`). */
    val waitsOnPerson: StateFlow<Boolean>
    /**
     * La question reconnue part dans la salle, **sous son identifiant** : une question n'entre
     * qu'une fois, même rejouée par l'écran ou par le moteur de reconnaissance.
     */
    fun ask(question: String, askId: String)
    /** Arrête les bots de la salle. */
    fun haltTalking()
    /**
     * Ce que la salle a **refusé**, tel quel (une question qui n'est pas partie, une lecture qui a
     * échoué) : la scène vocale le montre, comme le fil du chat le montre. Une salle qui refuse un
     * envoi le dit — jamais une bulle qui disparaît sans un mot.
     */
    val problem: StateFlow<String?> get() = NO_PROBLEM
    /**
     * Un message de la personne est encore en route vers la salle : la file d'envoi n'est pas vide
     * (un essai en cours, ou une reprise espacée). Le micro reste fermé tant qu'il en reste un.
     */
    val pendingSends: StateFlow<Boolean> get() = NO_SENDS
    /** La scène vocale s'ouvre : ce fil cesse de lire les réponses lui-même. */
    fun onVoiceStart() {}
    /** La scène vocale se ferme : le fil reprend ce qu'il faisait. */
    fun onVoiceEnd() {}

    companion object {
        /** Les fils qui n'ont pas de file d'envoi (une salle scriptée) ne retiennent jamais le micro. */
        private val NO_SENDS = MutableStateFlow(false)
        private val NO_PROBLEM = MutableStateFlow<String?>(null)
    }
}

/** La salle passée à `VoiceScreen`, équivalent du `GroupVoiceScene` de `VoiceView.group`. */
data class GroupVoiceScene(
    val roomName: String,
    val server: ServerConfig,
    val bots: List<BotProfile>,
    /** Le round : la dernière question et ce qui suit. */
    val lines: List<GroupVoiceLine>,
    val speakerId: String?,
    val isTalking: Boolean,
    val isHeld: Boolean,
    /** Un bot attend une réponse de la personne (une commande à approuver). */
    val waitsOnPerson: Boolean = false,
    /**
     * La salle a été annoncée au travail et n'a **rien** répondu : l'attente est finie, et la
     * légende le dit (« personne ne répond ») plutôt que de laisser « les bots discutent » tourner
     * dans le vide.
     */
    val stalled: Boolean = false,
    /** Les réponses déjà là qui attendent d'être lues : la file vocale n'est pas vide. */
    val pending: Int = 0,
    /** Un message de la personne est encore en route vers la salle (reprise comprise). */
    val sending: Boolean = false,
) {
    /** Le bot dont la réponse est lue. */
    val speaker: BotProfile? get() = bots.firstOrNull { it.name == speakerId }

    /** Le bot dont la réponse s'écrit à cet instant. */
    val writer: BotProfile? get() = lines.lastOrNull { it.live && !it.isMine && it.text.isNotEmpty() }
        ?.let { line -> bots.firstOrNull { it.name == line.profile } }

    /**
     * Le **seul** visage de l'orbe : celui du bot **qui parle**, et — au repos — le dernier qui a
     * parlé, sinon le premier de la salle. Une salle fait chacun son tour : l'orbe ne montre donc
     * **jamais deux visages** ; les participants ne se retrouvent ensemble que dans l'en-tête, en
     * petites pastilles.
     */
    val orbFace: BotProfile? get() = speaker
        ?: lastAnswer?.let { line -> bots.firstOrNull { it.name == line.profile } }
        ?: bots.firstOrNull()

    /** Le visage de l'orbe parle-t-il à cet instant ? (C'est lui qui porte l'aura et la légende.) */
    val orbFaceIsSpeaking: Boolean get() = speaker != null

    /** La dernière réponse achevée, pour « Replay the last answer ». */
    val lastAnswer: GroupVoiceLine? get() = lines.lastOrNull {
        !it.isMine && !it.live && !it.failed && it.text.isNotBlank()
    }

    /** Ce qui se dessine sous l'orbe : le round, du dernier mot de la personne à la fin. */
    val round: List<GroupVoiceLine> get() = groupVoiceRound(lines)
}

/**
 * Les réponses d'une salle que la voix lit, dans leur ordre de commencement (`GroupReplies`
 * de la référence) : du journal de la salle (ses messages) et des réponses qui s'écrivent (`GroupDraft`),
 * chacune lue une fois — un brouillon dont le message arrive ensuite n'est pas relu, et un bot qui
 * passe (« pass ») n'est jamais lu.
 *
 * Une seule limite par rapport à la référence, et elle est dans la lecture, pas dans la file :
 * `VoiceSession` dit un texte d'un bloc (`speak`), là où la référence enchaîne les morceaux au fil de
 * l'écriture (`beginReading`, `continueReading`, `endReading`). Une réponse n'est donc lue qu'une
 * fois entière — la salle l'a écrite, ou le bot a fini de l'écrire — tandis que la file qui suit
 * (ordre de commencement, doublons, passages, brouillons abandonnés) est celle de la référence.
 */
internal class GroupVoiceReplies(lines: List<GroupVoiceLine> = emptyList()) {
    /** Le numéro du dernier événement de la salle quand la voix a commencé : ce qui suit est lu. */
    private val since: Int = lines.mapNotNull { it.seq }.maxOrNull() ?: 0

    /** Ce qui attend d'être lu, dans l'ordre où cela a commencé. */
    private val queue = mutableListOf<GroupVoiceLine>()

    /** Les réponses de brouillons déjà lues dont le message n'est pas venu : pas relues ensuite. */
    private val awaiting = mutableListOf<GroupVoiceLine>()

    private var reading: GroupVoiceLine? = null
    private val seenMessages = mutableSetOf<Int>()
    private val seenDrafts = mutableSetOf<String>()

    /**
     * Les brouillons lâchés avant d'être entiers (la personne a parlé, ou Stop) : ils ne sont pas
     * lus plus tard, quand ils finissent d'écrire.
     */
    private val letGo = mutableSetOf<String>()

    /** Le dernier texte vu de chaque brouillon vivant : c'est lui qui dit qu'un bot **écrit**. */
    private val draftText = mutableMapOf<String, String>()

    /** Ce qui attend d'être lu. */
    val pending: List<GroupVoiceLine> get() = queue.toList()

    /** La réponse en cours de lecture. */
    val current: GroupVoiceLine? get() = reading

    /** La salle telle qu'elle est maintenant : les nouvelles réponses entrent dans la file. */
    fun update(lines: List<GroupVoiceLine>, held: Boolean): Boolean {
        var fresh = false
        val messages = lines.filter {
            !it.isMine && it.seq != null && it.seq > since && it.text.isNotEmpty() && it.profile != null
        }
        for (message in messages) {
            val seq = message.seq ?: continue
            if (!seenMessages.add(seq)) continue
            fresh = true
            if (!attach(message)) enqueue(message)
        }
        val drafts = lines.filter { it.draftId != null }
        for (draft in drafts) {
            val member = draft.profile ?: continue
            val id = draft.draftId ?: continue
            // **Un bot qui écrit fait bouger la salle.** Sans cela, l'horloge du tour ne repartait
            // qu'à la *fin* d'une réponse : un bot qui écrit pendant trente secondes (un vrai
            // modèle prend son temps) laissait le garde-fou « la salle ne produit rien » rendre le
            // micro au milieu de son écriture — et l'écoute ouverte jette alors ce qui attend.
            val written = draftText[id]
            if (draft.text.isNotEmpty() && draft.text != written) {
                draftText[id] = draft.text
                fresh = true
            }
            val waiting = queue.indexOfFirst { it.draftId == id }
            when {
                reading?.draftId == id -> reading = reading?.take(draft)
                waiting >= 0 -> queue[waiting] = queue[waiting].take(draft)
                // Déjà vu — et pas encore entier alors : c'est maintenant qu'il peut l'être.
                id in seenDrafts -> {
                    if (whole(draft) && id !in letGo && awaiting.none { it.draftId == id }) {
                        fresh = true
                        enqueue(draft)
                    }
                }
                mayBePass(draft.text) -> Unit
                // Son message est déjà dans le journal : c'est lui qui est lu, pas le brouillon.
                messages.any { it.profile == member && (it.seq ?: 0) > draft.after } ->
                    seenDrafts.add(id)
                else -> {
                    seenDrafts.add(id)
                    if (whole(draft)) {
                        fresh = true
                        enqueue(draft)
                    }
                }
            }
        }
        // Un brouillon parti sans message : ce qui en a été écrit est tout ce qu'il y aura.
        val live = drafts.mapNotNull { it.draftId }.toSet()
        queue.removeAll { gone(it, live) }
        awaiting.removeAll { gone(it, live) }
        if (held) dropAll()
        return fresh
    }

    /** La réponse à lire maintenant, s'il y en a une. */
    fun takeNext(): GroupVoiceLine? {
        if (reading != null || queue.isEmpty()) return null
        reading = queue.removeFirst()
        return reading
    }

    /** La réponse lue est finie : entendue jusqu'au bout, ou coupée. */
    fun finishReading() {
        val reply = reading
        if (reply != null && reply.seq == null && reply.draftId != null) awaiting.add(reply)
        reading = null
    }

    /** Tout ce qui est connu est lâché sans être lu (la personne a parlé, ou Stop). */
    fun dropAll() {
        finishReading()
        awaiting.addAll(queue.filter { it.seq == null && it.draftId != null })
        // Ce qui était connu et pas encore entier est lâché aussi : il ne sera pas lu plus tard.
        letGo.addAll(seenDrafts)
        queue.clear()
    }

    /** `line` est une réponse déjà connue par son brouillon : elle est entière maintenant. */
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

    /** Tout est connu : la salle l'a écrit, ou le bot a fini de l'écrire (et ce n'est pas un passage). */
    private fun whole(line: GroupVoiceLine): Boolean =
        line.seq != null || (!line.live && !mayBePass(line.text))

    /**
     * Un brouillon parti de la salle sans que son message ne l'ait remplacé : ce qui en a été écrit
     * est tout ce qu'il y aura. Un brouillon jamais entier (pas encore commencé) n'est pas lu.
     */
    private fun gone(reply: GroupVoiceLine, live: Set<String>): Boolean =
        reply.draftId != null && reply.draftId !in live && reply.seq == null && !whole(reply)

    private fun enqueue(reply: GroupVoiceLine) {
        val at = queue.indexOfFirst { it.after > reply.after }
        queue.add(if (at < 0) queue.size else at, reply)
    }

    /** Le brouillon dit ce qu'il en est maintenant : `seq` posé, le texte ne bouge plus. */
    private fun GroupVoiceLine.take(draft: GroupVoiceLine): GroupVoiceLine =
        if (seq != null) this else copy(text = draft.text, live = draft.live)

    companion object {
        /** Un texte qu'un bot qui passe écrit : « pass », « (pass) », « Pass. ». */
        private val PASS = Regex("^\\(?\\s*p(a(s(s)?)?)?\\s*\\)?\\.?$", RegexOption.IGNORE_CASE)

        internal fun mayBePass(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.isEmpty() || PASS.matches(trimmed)
        }
    }
}

/**
 * Coordinateur de `GroupVoice` : micro → question dans la salle → lecture des réponses une
 * par une → micro. La scène Compose ne fait que le rendre, et le fil ([GroupVoiceThread]) est
 * celui de la salle : ce sont les réponses du serveur qui sont lues, et la question reconnue part
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

    /**
     * Le guichet des questions : une question reconnue n'entre dans la salle **qu'une fois**, même
     * si le moteur rend sa transcription plusieurs fois (elle grandit, elle revient à l'identique).
     * C'est la réponse au défaut rapporté : « Tes messages “vous êtes là” reviennent en boucle ».
     */
    private val ledger = GroupAskLedger()

    /** La relance d'une question qui n'a pas encore de réponse (le serveur ne dit rien). */
    private var silenceJob: Job? = null

    /**
     * Une question est partie, et **aucune réponse n'est encore arrivée**.
     *
     * C'est ce qui tient le micro fermé entre l'envoi et la première ligne du bot. Sans ce verrou,
     * la salle peut se déclarer au repos une fraction de seconde (un `combine` qui rend son ancien
     * fil avec son nouvel état « pas au travail »), le tour était déclaré fini, l'écoute se
     * rouvrait — et la réponse qui arrivait juste après était **lâchée** : le bot ne parlait jamais.
     * Conséquence vérifiée : des questions partaient sans qu'aucune réponse ne soit lue.
     */
    private var awaitingAnswer = false

    /**
     * L'instant du dernier mouvement de la salle (une ligne, ou son état de travail).
     *
     * C'est l'horloge du « tour des bots » : pendant qu'ils écrivent, l'écoute reste fermée, et le
     * fil la rouvre dès qu'ils se taisent. Elle sert aussi de garde-fou — si la salle reste
     * annoncée au travail sans plus rien produire, l'écoute revient après [GROUP_ROUND_QUIET_MS]
     * pour que la personne ne soit jamais enfermée.
     */
    private var roomChangedAt: Long = 0L

    /** La reprise d'écoute remise à plus tard : annulée dès que la salle bouge. */
    private var roomWaitJob: Job? = null

    /** La ligne « le tour des bots n'est pas fini » n'est écrite qu'une fois par tour. */
    private var notedRoundWait = false

    /** La ligne « la salle ne répond pas » n'est écrite qu'une fois par tour, elle aussi. */
    private var notedStall = false

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _isHeld = MutableStateFlow(false)
    val isHeld: StateFlow<Boolean> = _isHeld.asStateFlow()

    private val _speakerId = MutableStateFlow<String?>(null)
    val speakerId: StateFlow<String?> = _speakerId.asStateFlow()

    /**
     * La salle est annoncée au travail et n'a **rien** produit : l'attente est finie sans réponse.
     *
     * C'est le cas du propriétaire — la question part, la salle dit qu'elle travaille, et rien ne
     * vient. L'écran ne doit pas laisser « les bots discutent » tourner indéfiniment : la légende
     * et le bandeau le disent, en français.
     */
    private val _stalled = MutableStateFlow(false)
    val stalled: StateFlow<Boolean> = _stalled.asStateFlow()

    /** La salle a-t-elle répondu quelque chose depuis la dernière question ? */
    private var answered = false

    /** L'horloge du tour : la dernière chose qui a bougé, **question comprise**. */
    private fun roomMoved() {
        roomChangedAt = System.currentTimeMillis()
    }
    /** Les réponses qui attendent d'être lues : le tour n'est pas fini. */
    private val _queued = MutableStateFlow(0)
    val queued: StateFlow<Int> = _queued.asStateFlow()

    /** Ce qui n'a pas pu être fait, dit à l'écran (une salle qui ne répond pas, un envoi perdu). */
    private val _problem = MutableStateFlow<String?>(null)
    val problem: StateFlow<String?> = _problem.asStateFlow()

    fun start() {
        if (_isOn.value) return
        _isOn.value = true
        _isHeld.value = false
        _speakerId.value = null
        _stalled.value = false
        answered = false
        _queued.value = 0
        _problem.value = null
        awaitingAnswer = false
        wantsListening = false
        current = null
        roomMoved()
        // Ce que la salle a déjà n'est pas relu ; ce qui suit l'est, et ce qui s'écrit maintenant.
        replies = GroupVoiceReplies(thread.lines.value)
        // La lecture appartient à la scène vocale tant qu'elle est ouverte.
        thread.onVoiceStart()
        session.stop()

        jobs += scope.launch {
            combine(thread.lines, thread.isTalking) { lines, talking -> lines to talking }
                .collect { (lines, _) ->
                    if (replies.update(lines, held = _isHeld.value)) {
                        roomMoved()
                        // La salle **produit** quelque chose (une ligne, un bot qui écrit) : le
                        // verdict « rien n'est venu » est caduc, et le nom du bot reprend la légende.
                        _stalled.value = false
                        roomChangedAt = System.currentTimeMillis()
                        // Une réponse est arrivée : la salle a répondu, on n'annonce plus le silence.
                        awaitingAnswer = false
                        _problem.value = null
                        cancelSilenceWatch()
                    }
                    queued()
                    next()
                }
        }
        jobs += scope.launch {
            session.phase.collect { phase ->
                when (phase) {
                    VoicePhase.LISTENING -> {
                        // Une nouvelle question l'emporte sur les réponses qui attendaient.
                        // Le micro rouvert par la demande de permission suit le même chemin que
                        // `listen()` : la salle reste mains-libres après la réponse.
                        wantsListening = true
                        replies.dropAll()
                        current = null
                        _speakerId.value = null
                        _isHeld.value = false
                        queued()
                        cancelSilenceWatch()
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
        // La personne reprend la parole : la réplique en cours est **coupée** (le filet du micro
        // rouvert tout de suite) et la suite de la file est lâchée — c'est le comportement de la
        // référence, et le journal le dit.
        if (current != null) {
            VoiceDiagnostics.note("voice (salle): la personne reprend la parole — la réplique en cours est coupée")
        }
        notedRoundWait = false
        notedStall = false
        awaitingAnswer = false
        cancelRoomWait()
        cancelSilenceWatch()
        _isHeld.value = false
        _stalled.value = false
        answered = false
        wantsListening = false
        replies.dropAll()
        current = null
        _speakerId.value = null
        roomMoved()
        session.stop()
        wantsListening = true
        session.startListening()
    }

    /** Une question reconnue part dans la salle (pour une salle du serveur : `groups.send`). */
    fun ask(question: String) {
        if (!_isOn.value) return
        when (val decision = ledger.admit(question)) {
            is GroupAskDecision.Send -> {
                replies.dropAll()
                queued()
                notedRoundWait = false
                _problem.value = null
                // La question remet aussi le verdict de silence à zéro (le tour repart d'ici).
                _stalled.value = false
                notedStall = false
                answered = false
                roomMoved()
                // Le tour commence **maintenant** : la pose se compte d'ici, et aucune réponse n'est
                // encore arrivée. C'est ce qui garde le micro fermé jusqu'à la première ligne.
                roomChangedAt = System.currentTimeMillis()
                awaitingAnswer = true
                // Le journal ne garde **jamais** les mots reconnus : la ligne dit ce qui se passe,
                // pas ce qui a été dit. L'identifiant, lui, est la preuve d'un envoi unique.
                VoiceDiagnostics.note("voice (salle): question reconnue envoyée dans la salle (${decision.id})")
                thread.ask(decision.text, decision.id)
                watchForSilence()
            }

            // **La question ne repart pas.** C'est le défaut vu par les bots : le moteur rend la
            // même phrase une fois de plus (ou sa transcription grandit d'un mot), et chaque
            // variante repartait. Une question posée une fois n'est posée qu'une fois.
            is GroupAskDecision.Again -> {
                VoiceDiagnostics.note(
                    "voice (salle): question déjà posée (${decision.why}, ${decision.id}) — elle ne repart pas",
                )
                // La session s'est mise en attente pour cette question : puisqu'elle ne part pas,
                // on lui rend l'écoute au lieu de la laisser attendre une réponse qui ne viendra
                // pas (le micro resterait fermé pour rien). Si la salle travaille encore, on ne
                // touche à rien : le tour en cours a ses réponses à donner.
                if (session.phase.value == VoicePhase.THINKING && !thread.isTalking.value &&
                    replies.pending.isEmpty() && !thread.pendingSends.value
                ) {
                    session.finishTurn(null)
                }
            }

            GroupAskDecision.Blank -> Unit
        }
    }

    /**
     * Stop rouge : plus de lecture ni de micro, et le round courant est arrêté (`groups.stop` pour
     * une salle du serveur), comme le Stop du chat. Rien n'est relu tant que le micro n'est pas
     * rouvert.
     */
    fun halt() {
        if (!_isOn.value) return
        cancelRoomWait()
        cancelSilenceWatch()
        if (current != null) {
            VoiceDiagnostics.note("voice (salle): arrêt — la file est coupée")
        }
        _isHeld.value = true
        awaitingAnswer = false
        wantsListening = false
        current = null
        _speakerId.value = null
        replies.dropAll()
        if (thread.isTalking.value) thread.haltTalking()
        session.stop()
    }

    /** Quitter la scène ne supprime pas le fil ; le round texte peut continuer dans le chat. */
    fun stop() {
        if (!_isOn.value) return
        cancelRoomWait()
        cancelSilenceWatch()
        jobs.forEach { it.cancel() }
        jobs.clear()
        _isOn.value = false
        _isHeld.value = false
        _speakerId.value = null
        _queued.value = 0
        _problem.value = null
        awaitingAnswer = false
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

        // **La salle à l'instant présent.** Une ligne écrite juste avant le réveil de la tâche de
        // pose n'était pas encore passée par le collecteur : le tour était alors déclaré fini avec
        // une file vide, l'écoute se rouvrait, et la réponse qui suivait était lâchée — le bot
        // sauté (relevé au journal : `écrit quill (seq=4)` et `le tour est fini` dans la même
        // seconde). On relit donc les lignes **ici**, avant de juger le tour.
        if (replies.update(thread.lines.value, held = _isHeld.value)) {
            roomChangedAt = System.currentTimeMillis()
            // La salle a produit quelque chose : la question a une réponse en route.
            awaitingAnswer = false
        }

        val reply = replies.takeNext()
        if (reply != null) {
            awaitingAnswer = false
            cancelRoomWait()
            cancelSilenceWatch()
            current = reply
            answered = true
            _stalled.value = false
            _speakerId.value = reply.profile
            queued()
            VoiceDiagnostics.note(
                "voice (salle): ${reply.profile ?: "un bot"} prend la parole " +
                    "(${replies.pending.size} en attente)",
            )
            // Chacun parle **avec sa propre voix** : c'est le profil du bot qui la choisit
            // (`BotVoices`), jamais une hauteur inventée sur la voix d'un autre.
            session.speak(reply.text, speaker = reply.profile)
            return
        }

        _speakerId.value = null
        val quietForMs = System.currentTimeMillis() - roomChangedAt
        val roomTalking = thread.isTalking.value
        queued()
        // **Le tour des bots n'est pas fini : l'écoute ne se rouvre pas.** C'est le défaut
        // rapporté — la salle annonçait ses réponses, mais la session rouvrait le micro dans
        // l'instant, et la reprise suivante jetait ce qui attendait d'être lu (`dropAll` sur
        // `LISTENING`) : le tour ne passait jamais d'un bot à l'autre et l'écran restait sur
        // « À L'ÉCOUTE ».
        if (!groupRoundIsOver(
                isTalking = roomTalking,
                waitsOnPerson = thread.waitsOnPerson.value,
                quietForMs = quietForMs,
                pending = replies.pending.size,
                sending = thread.pendingSends.value,
                // **Le verrou de la question naissante ne masque pas le garde-fou du temps.**
                // `awaitingAnswer` tient le micro fermé entre la question et la première ligne — il
                // protège d'une salle qui se déclare au repos une fraction de seconde. Mais une
                // salle qui **se dit au travail** sans rien produire pendant [GROUP_ROUND_QUIET_MS]
                // (douze secondes) doit rendre le micro et le dire : le verrou ne compte donc plus
                // une fois ce délai passé. Les deux garde-fous coopèrent au lieu de s'annuler.
                awaiting = awaitingAnswer && !(roomTalking && quietForMs >= GROUP_ROUND_QUIET_MS),
            )
        ) {
            if (!notedRoundWait) {
                notedRoundWait = true
                VoiceDiagnostics.note("voice (salle): le tour des bots n'est pas fini — l'écoute reste fermée")
            }
            waitForRoom()
            return
        }
        cancelRoomWait()
        // **La salle travaillait et n'a rien dit.** Le garde-fou rend le micro (la personne n'est
        // jamais enfermée), mais ce n'est pas un tour ordinaire : l'app le **dit**, au lieu de
        // laisser « les bots discutent » tourner dans le vide.
        if (roomTalking && quietForMs >= GROUP_ROUND_QUIET_MS && !answered) {
            _stalled.value = true
            if (!notedStall) {
                notedStall = true
                VoiceDiagnostics.note(
                    "voice (salle): rien n'est venu de la salle en " + (quietForMs / 1000) +
                        " s — la salle ne répond pas, le micro est rendu",
                )
            }
        }
        if (notedRoundWait) {
            notedRoundWait = false
            VoiceDiagnostics.note(
                "voice (salle): le tour est fini — l'écoute reprend (salle au travail=" + roomTalking +
                    ", rien produit depuis " + (quietForMs / 1000) + " s, " + replies.pending.size +
                    " en attente, réponses lues=" + answered + ")",
            )
        }
        if (phase == VoicePhase.THINKING) {
            session.finishTurn(null)
        } else if (wantsListening) {
            session.startListening()
        }
    }

    /** La reprise d'écoute remise à plus tard : on la retente à petits pas, tant que le tour dure. */
    private fun waitForRoom() {
        if (roomWaitJob?.isActive == true) return
        roomWaitJob = scope.launch {
            while (true) {
                delay(GROUP_ROUND_SETTLE_MS)
                if (!_isOn.value) return@launch
                // Une lecture a repris (ou l'écoute est ouverte) : ce n'est plus à cette tâche d'agir.
                if (session.phase.value != VoicePhase.IDLE && session.phase.value != VoicePhase.THINKING) {
                    return@launch
                }
                next()
                // `next()` a rouvert l'écoute (il annule cette tâche) : plus rien à surveiller.
                if (!isActive) return@launch
            }
        }
    }

    private fun cancelRoomWait() {
        roomWaitJob?.cancel()
        roomWaitJob = null
    }

    /** La file vocale telle qu'elle est : ce qui reste à lire. */
    private fun queued() {
        _queued.value = replies.pending.size
    }

    /**
     * **Le serveur ne dit rien.** Une question est partie, la salle ne se déclare ni au travail ni
     * en train d'écrire, et aucune réponse n'arrive : au bout de [GROUP_REPLY_TIMEOUT_MS] l'écran
     * le **dit** (en français) et rend le micro, au lieu de laisser une attente muette qui ne finit
     * jamais — c'était « LES BOTS DISCUTENT » pendant des minutes devant une salle morte.
     */
    private fun watchForSilence() {
        cancelSilenceWatch()
        val askedId = ledger.lastAskedId
        silenceJob = scope.launch {
            delay(GROUP_REPLY_TIMEOUT_MS)
            if (!_isOn.value || _isHeld.value) return@launch
            // Une réponse est arrivée, ou une lecture a commencé : il n'y a plus de silence.
            if (current != null || replies.pending.isNotEmpty()) return@launch
            if (askedId != null && ledger.lastAskedId != askedId) return@launch
            // La salle travaille encore (elle écrit) : elle a le droit de prendre son temps.
            if (thread.isTalking.value) return@launch
            VoiceDiagnostics.note("voice (salle): aucune réponse après ${GROUP_REPLY_TIMEOUT_MS} ms — on le dit")
            // Le tour est clos : ce n'est pas une réponse, c'est le silence qu'on annonce.
            awaitingAnswer = false
            _problem.value = OnyxLocalization["The room isn't answering. Check the connection and try again."]
            // **Les deux verdicts de silence n'en font qu'un.** La salle est déclarée « personne ne
            // répond » (la légende et le bandeau le disent), et le tour se ferme par le **chemin
            // commun** : il écrit « la salle ne répond pas » puis « le tour est fini », et rend le
            // micro — une salle morte n'enferme jamais la personne.
            _stalled.value = true
            if (!notedStall) {
                notedStall = true
                VoiceDiagnostics.note(
                    "voice (salle): rien n'est venu de la salle en " +
                        ((System.currentTimeMillis() - roomChangedAt) / 1000) +
                        " s — la salle ne répond pas, le micro est rendu",
                )
            }
            next()
            if (wantsListening && session.phase.value == VoicePhase.IDLE) session.startListening()
        }
    }

    private fun cancelSilenceWatch() {
        silenceJob?.cancel()
        silenceJob = null
    }
}

/**
 * Le tour des bots est-il fini — le micro peut-il revenir à la personne ?
 *
 * Une salle fait **chacun son tour** : les bots répondent **un par un**, et l'écoute ne se rouvre
 * qu'une fois la file vidée et la salle retombée au repos. Tant que la salle travaille, rouvrir le
 * micro ferait entrer la réponse suivante pendant l'écoute — et la reprise d'écoute jette alors ce
 * qui attendait d'être lu, si bien que la parole ne passait jamais d'un bot à l'autre.
 *
 * La personne qui attend une commande à approuver garde la main : le micro reste fermé tant qu'elle
 * n'a pas répondu dans le fil. Et si la salle reste annoncée au travail sans plus rien produire
 * depuis [GROUP_ROUND_QUIET_MS], l'écoute revient quand même : une salle qui se tait ne doit jamais
 * enfermer la personne.
 */
internal fun groupRoundIsOver(
    isTalking: Boolean,
    waitsOnPerson: Boolean,
    quietForMs: Long,
    pending: Int = 0,
    sending: Boolean = false,
    awaiting: Boolean = false,
    quietLimitMs: Long = GROUP_ROUND_QUIET_MS,
    settleMs: Long = GROUP_ROUND_SETTLE_MS,
): Boolean = !waitsOnPerson && pending == 0 && !sending && !awaiting &&
    quietForMs >= settleMs && (!isTalking || quietForMs >= quietLimitMs)

/**
 * Le temps de **pose** avant que le micro revienne : la salle vient d'écrire sa dernière ligne, et
 * une réponse peut encore arriver dans la foulée (un serveur qui rogne, une file qui se vide une
 * seconde après son état). Rouvrir l'écoute dans cet instant la **lâcherait** — l'écoute ouverte
 * jette ce qui attend d'être lu — et un bot resterait muet : c'est le tour sauté qu'on a vu une fois
 * sur un appareil lent. Court : la personne ne l'entend pas.
 */
internal const val GROUP_ROUND_SETTLE_MS = 500L

/** Combien de temps une salle peut rester annoncée au travail sans rien produire avant qu'on rende le micro. */
internal const val GROUP_ROUND_QUIET_MS = 12_000L

/**
 * Combien de temps une question peut rester sans **aucune** réponse avant qu'on le dise.
 *
 * Ce n'est pas le garde-fou du tour (la salle peut travailler longtemps) : c'est le silence total —
 * rien dans le journal, aucune salle « au travail », aucune écriture. Passé ce délai, l'écran
 * affiche en français que la salle ne répond pas, et le micro revient. Une attente muette qui ne
 * finit jamais est un mensonge de plus.
 */
internal const val GROUP_REPLY_TIMEOUT_MS = 20_000L
