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
 * One line of the voice round: the person's question, or a bot's answer as it is
 * being written.
 *
 * The two threads that can feed a voice scene — the one of a server room
 * (`GroupRoomFeed`) and the one of a fresh room still held by the local composer
 * (`GroupChatViewModel`) — produce it, and the screen draws it like the room's thread, which is what
 * `VoiceView.groupExchange` does by reusing the chat's `MessageRow`.
 */
data class GroupVoiceLine(
    val id: String,
    /**
     * The **profile** of the bot that spoke: it is the name that the roster, `BotVoices` and the scene
     * know. `null` when it is the person who spoke.
     */
    val profile: String?,
    val name: String,
    val faceCode: String? = null,
    /**
     * Its **identifier in the room** (the log's `actorID`, `GroupMember.id`): it is not
     * always its profile — the room distinguishes them (`{"kind":"member","id":…,"profile":…}`).
     * It is the one the thread draws and the protocol names; the profile is the one that chooses the voice.
     */
    val memberID: String? = null,
    val text: String,
    /** The answer is still being written: it is drawn, but not yet read out. */
    val live: Boolean = false,
    val failed: Boolean = false,
    /** What the person attached: the paths on the server, or the names while they are being sent. */
    val paths: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    /** The person's message is on its way to the room, not yet in its log. */
    val waiting: Boolean = false,
    val atMillis: Long = 0L,
    /** Its message in the room's log (`seq`), `null` as long as the room does not have it. */
    val seq: Int? = null,
    /** The live session that is writing it (`GroupDraft.id`), when it was seen being written. */
    val draftId: String? = null,
    /** The last event of the log when it started: the order of the round (`GroupReplies`). */
    val after: Int = 0,
) {
    /** The person: their question is drawn in a bubble, on the right. */
    val isMine: Boolean get() = profile == null
}

/**
 * The bots of a voice scene, each carrying the face that **the screen** resolves for it.
 *
 * A room draws its faces with a single table (`BotFaceResolver`: the phone's choice
 * combined with `ui_meta.onyx.face`). The voice scene, for its part, read only the local choice: a member whose
 * face comes from the server arrived there **without a code**, and the orb then showed `BotLook`'s
 * fallback creature, derived from the name — a different face than the room's. Resolving here, with the screen's
 * table, closes the gap: header, orb and caption read the same code.
 */
internal fun List<BotProfile>.withFaces(resolve: (String) -> String?): List<BotProfile> =
    map { bot ->
        val code = resolve(bot.name)
        if (code == null || code == bot.faceCode) bot else bot.copy(faceCode = code)
    }

/** The room's last round, like `VoiceView.groupExchange`: the question and what follows. */
internal fun groupVoiceRound(lines: List<GroupVoiceLine>): List<GroupVoiceLine> {
    val lastAsk = lines.indexOfLast { it.isMine }
    return if (lastAsk < 0) emptyList() else lines.drop(lastAsk)
}

/**
 * The `phaseTitle` of `VoiceView.group`, with the current member rather than the room's name.
 *
 * **The state does not lie**: "READY" ("PRÊT") is only said once the turn is really over. As long as an
 * answer waits to be read ([pending]), a message is still on its way to the room
 * ([sending]) or the room is working ([isTalking]), the screen says what is happening — the name of the bot
 * that speaks, of the one that writes, or "THE BOTS ARE TALKING" ("LES BOTS DISCUTENT"). The reported defect: "READY" was shown
 * while the answers were there and the voice queue was not empty.
 */
internal fun groupVoicePhaseTitle(
    phase: VoicePhase,
    speaker: String?,
    writer: String?,
    isTalking: Boolean,
    /**
     * The room was **announced as working** and produced nothing at all: the wait is over
     * (`GroupVoice.stalled`). The generic caption "the bots are talking" must then no longer
     * spin idly — it says that nobody is answering, which is the truth.
     */
    stalled: Boolean = false,
    pending: Int = 0,
    sending: Boolean = false,
): String {
    val waiting = isTalking || pending > 0 || sending || phase == VoicePhase.THINKING
    return when {
        phase == VoicePhase.SPEAKING -> "${speaker ?: "A bot"} is speaking"
        phase == VoicePhase.LISTENING -> "Listening"
        // A named bot **always** takes precedence over a generic caption: the one that writes, then the one
        // that speaks. "Nobody is answering" only takes the place of a void — and never that of a
        // bot that has a name.
        waiting -> writer?.let { "$it is writing" }
            ?: if (stalled) "Nobody is answering" else "The bots are talking"
        stalled -> "Nobody is answering"
        else -> "Ready"
    }
}

/**
 * The thread that a voice scene reads: a server's room (`GroupRoomFeed`), or the fresh room of a
 * local composer (`GroupChatViewModel`).
 *
 * It is the `GroupVoiceScene { voice, feed }` of the reference: the voice scene knows only this thread, and
 * it is the one that says what is drawn, whether the bots are working, and where the recognized question goes.
 */
interface GroupVoiceThread {
    /** The room's name, at the top of the screen. */
    val roomName: String
    val server: ServerConfig
    /** What is drawn: the last question and what follows, drafts included. */
    val lines: StateFlow<List<GroupVoiceLine>>
    /** The bots are working: a discussion in progress, or the room working. */
    val isTalking: StateFlow<Boolean>
    /** Something waits only on the person: the microphone stays closed (`waitsOnPerson`). */
    val waitsOnPerson: StateFlow<Boolean>
    /**
     * The recognized question goes into the room, **under its identifier**: a question enters
     * only once, even replayed by the screen or by the recognition engine.
     */
    fun ask(question: String, askId: String)
    /** Stops the room's bots. */
    fun haltTalking()
    /**
     * What the room has **refused**, as is (a question that did not leave, a read that
     * failed): the voice scene shows it, as the chat's thread shows it. A room that refuses a
     * send says so — never a bubble that disappears without a word.
     */
    val problem: StateFlow<String?> get() = NO_PROBLEM
    /**
     * A message from the person is still on its way to the room: the send queue is not empty
     * (an attempt in progress, or a spaced retry). The microphone stays closed as long as one remains.
     */
    val pendingSends: StateFlow<Boolean> get() = NO_SENDS
    /** The voice scene opens: this thread stops reading the answers itself. */
    fun onVoiceStart() {}
    /** The voice scene closes: the thread resumes what it was doing. */
    fun onVoiceEnd() {}

    companion object {
        /** Threads with no send queue (a scripted room) never hold the microphone. */
        private val NO_SENDS = MutableStateFlow(false)
        private val NO_PROBLEM = MutableStateFlow<String?>(null)
    }
}

/** The room passed to `VoiceScreen`, equivalent of the `GroupVoiceScene` of `VoiceView.group`. */
data class GroupVoiceScene(
    val roomName: String,
    val server: ServerConfig,
    val bots: List<BotProfile>,
    /** The round: the last question and what follows. */
    val lines: List<GroupVoiceLine>,
    val speakerId: String?,
    val isTalking: Boolean,
    val isHeld: Boolean,
    /** A bot waits for an answer from the person (a command to approve). */
    val waitsOnPerson: Boolean = false,
    /**
     * The room was announced as working and answered **nothing**: the wait is over, and the
     * caption says so ("nobody is answering") rather than letting "the bots are talking" spin
     * idly.
     */
    val stalled: Boolean = false,
    /** The answers already there that wait to be read: the voice queue is not empty. */
    val pending: Int = 0,
    /** A message from the person is still on its way to the room (retry included). */
    val sending: Boolean = false,
) {
    /** The bot whose answer is being read. */
    val speaker: BotProfile? get() = bots.firstOrNull { it.name == speakerId }

    /** The bot whose answer is being written at this instant. */
    val writer: BotProfile? get() = lines.lastOrNull { it.live && !it.isMine && it.text.isNotEmpty() }
        ?.let { line -> bots.firstOrNull { it.name == line.profile } }

    /**
     * The **single** face of the orb: that of the bot **who speaks**, and — at rest — the last one who
     * spoke, otherwise the first of the room. A room takes turns: the orb therefore **never** shows
     * **two faces**; the participants only come together in the header, as
     * small pills.
     */
    val orbFace: BotProfile? get() = speaker
        ?: lastAnswer?.let { line -> bots.firstOrNull { it.name == line.profile } }
        ?: bots.firstOrNull()

    /** Is the orb's face speaking at this instant? (It is the one that carries the aura and the caption.) */
    val orbFaceIsSpeaking: Boolean get() = speaker != null

    /** The last completed answer, for "Replay the last answer". */
    val lastAnswer: GroupVoiceLine? get() = lines.lastOrNull {
        !it.isMine && !it.live && !it.failed && it.text.isNotBlank()
    }

    /** What is drawn under the orb: the round, from the person's last word to the end. */
    val round: List<GroupVoiceLine> get() = groupVoiceRound(lines)
}

/**
 * The answers of a room that the voice reads, in their order of start (`GroupReplies`
 * of the reference): from the room's log (its messages) and from the answers being written (`GroupDraft`),
 * each read once — a draft whose message arrives afterwards is not read again, and a bot that
 * passes ("pass") is never read.
 *
 * A single limitation compared with the reference, and it lies in the reading, not in the queue:
 * `VoiceSession` speaks a text in one go (`speak`), where the reference chains the pieces as
 * they are written (`beginReading`, `continueReading`, `endReading`). An answer is therefore read only
 * once whole — the room wrote it, or the bot finished writing it — while the queue that follows
 * (order of start, duplicates, passes, abandoned drafts) is that of the reference.
 */
internal class GroupVoiceReplies(lines: List<GroupVoiceLine> = emptyList()) {
    /** The number of the room's last event when the voice started: what follows is read. */
    private val since: Int = lines.mapNotNull { it.seq }.maxOrNull() ?: 0

    /** What waits to be read, in the order in which it started. */
    private val queue = mutableListOf<GroupVoiceLine>()

    /** Draft answers already read whose message has not come: not read again afterwards. */
    private val awaiting = mutableListOf<GroupVoiceLine>()

    private var reading: GroupVoiceLine? = null
    private val seenMessages = mutableSetOf<Int>()
    private val seenDrafts = mutableSetOf<String>()

    /**
     * The drafts let go before being whole (the person spoke, or Stop): they are not
     * read later, when they finish writing.
     */
    private val letGo = mutableSetOf<String>()

    /** The last text seen of each live draft: it is the one that says that a bot **writes**. */
    private val draftText = mutableMapOf<String, String>()

    /** What waits to be read. */
    val pending: List<GroupVoiceLine> get() = queue.toList()

    /** The answer currently being read. */
    val current: GroupVoiceLine? get() = reading

    /** The room as it is now: the new answers enter the queue. */
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
            // **A bot that writes makes the room move.** Without this, the turn's clock only restarted
            // at the *end* of an answer: a bot writing for thirty seconds (a real
            // model takes its time) let the "the room produces nothing" guard return the
            // microphone in the middle of its writing — and open listening then throws away what waits.
            val written = draftText[id]
            if (draft.text.isNotEmpty() && draft.text != written) {
                draftText[id] = draft.text
                fresh = true
            }
            val waiting = queue.indexOfFirst { it.draftId == id }
            when {
                reading?.draftId == id -> reading = reading?.take(draft)
                waiting >= 0 -> queue[waiting] = queue[waiting].take(draft)
                // Already seen — and not yet whole then: it is now that it can be.
                id in seenDrafts -> {
                    if (whole(draft) && id !in letGo && awaiting.none { it.draftId == id }) {
                        fresh = true
                        enqueue(draft)
                    }
                }
                mayBePass(draft.text) -> Unit
                // Its message is already in the log: it is the one that is read, not the draft.
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
        // A draft gone without a message: what was written of it is all there will be.
        val live = drafts.mapNotNull { it.draftId }.toSet()
        queue.removeAll { gone(it, live) }
        awaiting.removeAll { gone(it, live) }
        if (held) dropAll()
        return fresh
    }

    /** The answer to read now, if there is one. */
    fun takeNext(): GroupVoiceLine? {
        if (reading != null || queue.isEmpty()) return null
        reading = queue.removeFirst()
        return reading
    }

    /** The read answer is over: heard to the end, or cut off. */
    fun finishReading() {
        val reply = reading
        if (reply != null && reply.seq == null && reply.draftId != null) awaiting.add(reply)
        reading = null
    }

    /** Everything known is let go without being read (the person spoke, or Stop). */
    fun dropAll() {
        finishReading()
        awaiting.addAll(queue.filter { it.seq == null && it.draftId != null })
        // What was known and not yet whole is let go too: it will not be read later.
        letGo.addAll(seenDrafts)
        queue.clear()
    }

    /** `line` is an answer already known by its draft: it is whole now. */
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

    /** Everything is known: the room wrote it, or the bot finished writing it (and it is not a pass). */
    private fun whole(line: GroupVoiceLine): Boolean =
        line.seq != null || (!line.live && !mayBePass(line.text))

    /**
     * A draft that left the room without its message replacing it: what was written of it
     * is all there will be. A draft never whole (not yet started) is not read.
     */
    private fun gone(reply: GroupVoiceLine, live: Set<String>): Boolean =
        reply.draftId != null && reply.draftId !in live && reply.seq == null && !whole(reply)

    private fun enqueue(reply: GroupVoiceLine) {
        val at = queue.indexOfFirst { it.after > reply.after }
        queue.add(if (at < 0) queue.size else at, reply)
    }

    /** The draft says what it is now: `seq` set, the text no longer moves. */
    private fun GroupVoiceLine.take(draft: GroupVoiceLine): GroupVoiceLine =
        if (seq != null) this else copy(text = draft.text, live = draft.live)

    companion object {
        /** A text that a passing bot writes: "pass", "(pass)", "Pass.". */
        private val PASS = Regex("^\\(?\\s*p(a(s(s)?)?)?\\s*\\)?\\.?$", RegexOption.IGNORE_CASE)

        internal fun mayBePass(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.isEmpty() || PASS.matches(trimmed)
        }
    }
}

/**
 * Coordinator of `GroupVoice`: microphone → question into the room → reading of the answers one
 * by one → microphone. The Compose scene only renders it, and the thread ([GroupVoiceThread]) is
 * the room's: it is the server's answers that are read, and the recognized question leaves
 * through `groups.send`.
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
     * The question gateway: a recognized question enters the room **only once**, even
     * if the engine returns its transcription several times (it grows, it comes back identical).
     * This is the fix for the reported defect: "Your 'are you there' messages come back in a loop".
     */
    private val ledger = GroupAskLedger()

    /** The retry of a question that has no answer yet (the server says nothing). */
    private var silenceJob: Job? = null

    /**
     * A question has left, and **no answer has arrived yet**.
     *
     * This is what keeps the microphone closed between the send and the bot's first line. Without this lock,
     * the room can declare itself at rest for a fraction of a second (a `combine` that renders its old
     * thread with its new "not working" state), the turn was declared over, listening
     * reopened — and the answer that arrived right after was **dropped**: the bot never spoke.
     * Verified consequence: questions left without any answer being read.
     */
    private var awaitingAnswer = false

    /**
     * The instant of the room's last movement (a line, or its working state).
     *
     * It is the clock of the "bots' turn": while they write, listening stays closed, and the
     * thread reopens it as soon as they fall silent. It also serves as a guard — if the room stays
     * announced as working without producing anything more, listening returns after [GROUP_ROUND_QUIET_MS]
     * so that the person is never locked in.
     */
    private var roomChangedAt: Long = 0L

    /** The listening resumption postponed: cancelled as soon as the room moves. */
    private var roomWaitJob: Job? = null

    /** The line "the bots' turn is not over" is written only once per turn. */
    private var notedRoundWait = false

    /** The line "the room isn't answering" is written only once per turn too. */
    private var notedStall = false

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _isHeld = MutableStateFlow(false)
    val isHeld: StateFlow<Boolean> = _isHeld.asStateFlow()

    private val _speakerId = MutableStateFlow<String?>(null)
    val speakerId: StateFlow<String?> = _speakerId.asStateFlow()

    /**
     * The room is announced as working and produced **nothing**: the wait is over without an answer.
     *
     * This is the owner's case — the question leaves, the room says it is working, and nothing
     * comes. The screen must not let "the bots are talking" spin indefinitely: the caption
     * and the banner say so, in French.
     */
    private val _stalled = MutableStateFlow(false)
    val stalled: StateFlow<Boolean> = _stalled.asStateFlow()

    /** Has the room answered anything since the last question? */
    private var answered = false

    /** The turn's clock: the last thing that moved, **question included**. */
    private fun roomMoved() {
        roomChangedAt = System.currentTimeMillis()
    }
    /** The answers that wait to be read: the turn is not over. */
    private val _queued = MutableStateFlow(0)
    val queued: StateFlow<Int> = _queued.asStateFlow()

    /** What could not be done, said on screen (a room that doesn't answer, a lost send). */
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
        // What the room already has is not read again; what follows is, and what is being written now.
        replies = GroupVoiceReplies(thread.lines.value)
        // Reading belongs to the voice scene as long as it is open.
        thread.onVoiceStart()
        session.stop()

        jobs += scope.launch {
            combine(thread.lines, thread.isTalking) { lines, talking -> lines to talking }
                .collect { (lines, _) ->
                    if (replies.update(lines, held = _isHeld.value)) {
                        roomMoved()
                        // The room **produces** something (a line, a bot writing): the
                        // "nothing came" verdict is void, and the bot's name takes the caption back.
                        _stalled.value = false
                        roomChangedAt = System.currentTimeMillis()
                        // An answer has arrived: the room answered, we no longer announce silence.
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
                        // A new question wins over the answers that were waiting.
                        // The microphone reopened by the permission request follows the same path as
                        // `listen()`: the room stays hands-free after the answer.
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

    /** Opens the microphone, or interrupts the current voice to answer the room. */
    fun listen() {
        if (!_isOn.value) return
        // The person takes the floor again: the current reply is **cut off** (the safety net of the microphone
        // reopened at once) and the rest of the queue is let go — this is the reference's
        // behavior, and the log says so.
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

    /** A recognized question goes into the room (for a server room: `groups.send`). */
    fun ask(question: String) {
        if (!_isOn.value) return
        when (val decision = ledger.admit(question)) {
            is GroupAskDecision.Send -> {
                replies.dropAll()
                queued()
                notedRoundWait = false
                _problem.value = null
                // The question also resets the silence verdict (the turn restarts from here).
                _stalled.value = false
                notedStall = false
                answered = false
                roomMoved()
                // The turn starts **now**: the settle time counts from here, and no answer has
                // arrived yet. This is what keeps the microphone closed until the first line.
                roomChangedAt = System.currentTimeMillis()
                awaitingAnswer = true
                // The log **never** keeps the recognized words: the line says what is happening,
                // not what was said. The identifier is the proof of a single send.
                VoiceDiagnostics.note("voice (salle): question reconnue envoyée dans la salle (${decision.id})")
                thread.ask(decision.text, decision.id)
                watchForSilence()
            }

            // **The question does not leave again.** This is the defect seen by the bots: the engine returns the
            // same sentence one more time (or its transcription grows by a word), and each
            // variant left again. A question asked once is asked only once.
            is GroupAskDecision.Again -> {
                VoiceDiagnostics.note(
                    "voice (salle): question déjà posée (${decision.why}, ${decision.id}) — elle ne repart pas",
                )
                // The session went into waiting for this question: since it does not leave,
                // it is given listening back instead of being left waiting for an answer that will not
                // come (the microphone would stay closed for nothing). If the room is still working, we
                // touch nothing: the current turn has its answers to give.
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
     * Red Stop: no more reading or microphone, and the current round is stopped (`groups.stop` for
     * a server room), like the chat's Stop. Nothing is read again as long as the microphone is not
     * reopened.
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

    /** Leaving the scene does not delete the thread; the text round can continue in the chat. */
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

    /** Replays the last completed answer, under its bot's face. */
    fun replayLast() {
        if (!_isOn.value || _isHeld.value || current != null || thread.waitsOnPerson.value) return
        if (session.phase.value != VoicePhase.IDLE || replies.pending.isNotEmpty()) return
        val last = thread.lines.value.lastOrNull {
            !it.isMine && !it.live && !it.failed && it.text.isNotBlank()
        } ?: return
        current = last
        _speakerId.value = last.profile
        // The answer is read again **in its bot's voice**, as the first time.
        session.speak(last.text, speaker = last.profile)
    }

    private fun next() {
        if (!_isOn.value || _isHeld.value || current != null) return
        val phase = session.phase.value
        if (phase != VoicePhase.IDLE && phase != VoicePhase.THINKING) return

        // **The room at the present instant.** A line written just before the settle task woke
        // up had not yet gone through the collector: the turn was then declared over with
        // an empty queue, listening reopened, and the answer that followed was dropped — the bot
        // skipped (noted in the log: `écrit quill (seq=4)` and `le tour est fini` in the same
        // second). The lines are therefore re-read **here**, before judging the turn.
        if (replies.update(thread.lines.value, held = _isHeld.value)) {
            roomChangedAt = System.currentTimeMillis()
            // The room produced something: the question has an answer on its way.
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
            // Each one speaks **with its own voice**: it is the bot's profile that chooses it
            // (`BotVoices`), never a pitch invented on another's voice.
            session.speak(reply.text, speaker = reply.profile)
            return
        }

        _speakerId.value = null
        val quietForMs = System.currentTimeMillis() - roomChangedAt
        val roomTalking = thread.isTalking.value
        queued()
        // **The bots' turn is not over: listening does not reopen.** This is the reported
        // defect — the room announced its answers, but the session reopened the microphone in
        // the instant, and the next resumption threw away what waited to be read (`dropAll` on
        // `LISTENING`): the turn never passed from one bot to another and the screen stayed on
        // "LISTENING" ("À L'ÉCOUTE").
        if (!groupRoundIsOver(
                isTalking = roomTalking,
                waitsOnPerson = thread.waitsOnPerson.value,
                quietForMs = quietForMs,
                pending = replies.pending.size,
                sending = thread.pendingSends.value,
                // **The nascent-question lock does not mask the time guard.**
                // `awaitingAnswer` keeps the microphone closed between the question and the first line — it
                // protects against a room that declares itself at rest for a fraction of a second. But a
                // room that **says it is working** without producing anything for [GROUP_ROUND_QUIET_MS]
                // (twelve seconds) must return the microphone and say so: the lock therefore no longer counts
                // once that delay has passed. The two guards cooperate instead of cancelling each other.
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
        // **The room was working and said nothing.** The guard returns the microphone (the person is
        // never locked in), but this is not an ordinary turn: the app **says** it, instead of
        // letting "the bots are talking" spin idly.
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

    /** The listening resumption postponed: it is retried in small steps, as long as the turn lasts. */
    private fun waitForRoom() {
        if (roomWaitJob?.isActive == true) return
        roomWaitJob = scope.launch {
            while (true) {
                delay(GROUP_ROUND_SETTLE_MS)
                if (!_isOn.value) return@launch
                // A reading has resumed (or listening is open): it is no longer up to this task to act.
                if (session.phase.value != VoicePhase.IDLE && session.phase.value != VoicePhase.THINKING) {
                    return@launch
                }
                next()
                // `next()` has reopened listening (it cancels this task): nothing left to watch.
                if (!isActive) return@launch
            }
        }
    }

    private fun cancelRoomWait() {
        roomWaitJob?.cancel()
        roomWaitJob = null
    }

    /** The voice queue as it is: what remains to be read. */
    private fun queued() {
        _queued.value = replies.pending.size
    }

    /**
     * **The server says nothing.** A question has left, the room declares itself neither working nor
     * writing, and no answer arrives: after [GROUP_REPLY_TIMEOUT_MS] the screen
     * **says** it (in French) and returns the microphone, instead of leaving a mute wait that never
     * ends — it was "THE BOTS ARE TALKING" ("LES BOTS DISCUTENT") for minutes in front of a dead room.
     */
    private fun watchForSilence() {
        cancelSilenceWatch()
        val askedId = ledger.lastAskedId
        silenceJob = scope.launch {
            delay(GROUP_REPLY_TIMEOUT_MS)
            if (!_isOn.value || _isHeld.value) return@launch
            // An answer has arrived, or a reading has started: there is no silence any more.
            if (current != null || replies.pending.isNotEmpty()) return@launch
            if (askedId != null && ledger.lastAskedId != askedId) return@launch
            // The room is still working (it is writing): it has the right to take its time.
            if (thread.isTalking.value) return@launch
            VoiceDiagnostics.note("voice (salle): aucune réponse après ${GROUP_REPLY_TIMEOUT_MS} ms — on le dit")
            // The turn is closed: it is not an answer, it is the silence that is announced.
            awaitingAnswer = false
            _problem.value = OnyxLocalization["The room isn't answering. Check the connection and try again."]
            // **The two silence verdicts become one.** The room is declared "nobody is
            // answering" (the caption and the banner say so), and the turn closes through the **common
            // path**: it writes "the room isn't answering" then "the turn is over", and returns the
            // microphone — a dead room never locks the person in.
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
 * Is the bots' turn over — can the microphone go back to the person?
 *
 * A room takes **turns**: the bots answer **one by one**, and listening only reopens
 * once the queue is emptied and the room has fallen back to rest. As long as the room is working, reopening the
 * microphone would let the next answer come in during listening — and resuming listening then throws away what
 * waited to be read, so that speech never passed from one bot to another.
 *
 * The person waiting for a command to approve keeps control: the microphone stays closed as long as they
 * have not answered in the thread. And if the room stays announced as working without producing anything more
 * since [GROUP_ROUND_QUIET_MS], listening returns anyway: a room that falls silent must never
 * lock the person in.
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
 * The **settle** time before the microphone comes back: the room has just written its last line, and
 * an answer can still arrive in the follow-through (a server that trims, a queue that empties one
 * second after its state). Reopening listening at that instant would **drop** it — open listening
 * throws away what waits to be read — and a bot would stay mute: this is the skipped turn seen once
 * on a slow device. Short: the person does not hear it.
 */
internal const val GROUP_ROUND_SETTLE_MS = 500L

/** How long a room can stay announced as working without producing anything before the microphone is returned. */
internal const val GROUP_ROUND_QUIET_MS = 12_000L

/**
 * How long a question can stay without **any** answer before it is said.
 *
 * It is not the turn's guard (the room can work for a long time): it is total silence —
 * nothing in the log, no room "working", no writing. Past this delay, the screen
 * displays in French that the room isn't answering, and the microphone comes back. A mute wait that never
 * ends is one more lie.
 */
internal const val GROUP_REPLY_TIMEOUT_MS = 20_000L
