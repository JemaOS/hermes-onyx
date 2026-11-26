package com.onyx.app.ui.bots

import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.serve.GroupApproval
import com.onyx.app.data.model.serve.GroupDraft
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupState
import com.onyx.app.data.net.GroupRoomApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.net.strOrNull
import com.onyx.app.data.room.RoomHandoffLine
import com.onyx.app.data.room.roomHandoffs
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.voice.VoiceDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A message from the person on its way to the room: what was typed, and the files that
 * accompany it, by their name.
 */
data class GroupSending(
    /** Its event identifier: it is also the server's `event_id`, hence its uniqueness key. */
    val id: String,
    val text: String,
    val files: List<String> = emptyList(),
    /** What goes into the room: the text, and one note per file once on the server. */
    val said: String? = null,
    /** How many attempts failed: the message is **still** on its way, it is not lost. */
    val attempts: Int = 0,
    /** The send definitively failed: the message stays on screen, marked, with its reason. */
    val failed: Boolean = false,
    val problem: String? = null,
) {
    /** What is really said: the sent text, otherwise the one that was typed. */
    val delivered: String get() = said ?: text
}

/** A room followed on screen: what was said there, what it waits for, and what is being written. */
data class GroupRoomFeedState(
    val room: GroupRoom,
    val events: List<GroupEvent> = emptyList(),
    val status: GroupState = GroupState(),
    /**
     * The lead that the **room** announces (`groups.state` → `context.lead_member_id`, `lead`
     * routing). `null` when the room has none — or when this server does not know the routing.
     */
    val leadMemberId: String? = null,
    /** The sent messages, not yet in the journal. */
    val sending: List<GroupSending> = emptyList(),
    /** The replies being written, each until the room has it. */
    val drafts: List<GroupDraft> = emptyList(),
    /** The approvals and retries answered here and not yet gone from the state: their card hides. */
    val answering: Set<String> = emptySet(),
    /** What the server refused, said as is: never a message that disappears silently. */
    val problem: String? = null,
    /** The last event read from the journal. */
    val cursor: Int = 0,
    /** True once the journal has been read a first time. */
    val loaded: Boolean = false,
) {
    /**
     * The bots are talking: a message from the person does not yet have an end of discussion
     * after it. (A Desktop room's copy says nothing about this.)
     */
    val discussing: Boolean
        get() {
            if (room.inDesktop) return false
            val lastAsk = events.lastOrNull { it.kind == "message.user" }?.seq ?: return sending.isNotEmpty()
            return sending.isNotEmpty() || events.none { it.seq > lastAsk && it.endsDiscussion }
        }

    /** Something is running: a discussion is in progress, or the room is working. */
    val busy: Boolean get() = discussing || status.working

    /** The last event seen here, as `GroupRoom.activityMark` counts it. */
    val seenMark: Double?
        get() = if (room.inDesktop) room.updatedAtMillis?.let { it / 1000.0 } else cursor.takeIf { it > 0 }?.toDouble()

    /** The reply being written at this instant, if there is one. */
    val writing: GroupDraft? get() = drafts.lastOrNull { it.text.isNotEmpty() && it.finishedAtMillis == null }

    /**
     * The drafts being drawn: those that have started writing, filtered like the reference view
     * (`feed.drafts.filter { !$0.text.isEmpty() }`) — an open session from which nothing has yet
     * come adds no row.
     */
    val shownDrafts: List<GroupDraft> get() = drafts.filter { it.text.isNotEmpty() }

    /**
     * **The journal's handoffs** — one per change of recipient, in place of the reply that
     * carries it (`roomHandoffs`). Read from the journal, they are never invented: two reads of
     * the same journal return the same lines, so none can be displayed twice.
     *
     * The last one tells whom the room writes to by default from then on.
     */
    val handoffs: List<RoomHandoffLine> get() = roomHandoffs(events, room.members)

    /**
     * The journal rows that are drawn: the messages, the notes a turn is worth, and the handoff
     * **right after** the reply that carries it — same list, hence same order: a handoff row never
     * reorders existing rows.
     */
    val rows: List<GroupEvent>
        get() {
            val kept = events.filter { it.isMessage || it.note(room) != null }
            val handoffs = handoffs
            if (handoffs.isEmpty()) return kept
            val after = handoffs.associateBy { it.seq }
            return kept.flatMap { event ->
                val handoff = after[event.seq]
                if (handoff == null) listOf(event) else listOf(event, handoff.asEvent())
            }
        }
}

/**
 * The thread as the voice scene reads it (`GroupVoiceThread.lines`): the person's question —
 * the one leaving, and those of the journal —, the bots' replies, those being written
 * (`drafts`) and those on the way (`sending`), in the order the thread draws them.
 *
 * This is the substance of the reference's `VoiceView.groupExchange`: journal events become the
 * lines of a reply (`message.member`) or of a question (`message.user`), a draft a live line
 * under its bot, and a message on the way the bubble of the person still waiting.
 */
internal fun GroupRoomFeedState.voiceLines(face: (String) -> String? = { null }): List<GroupVoiceLine> {
    val said = events.filter { it.isMessage }.map { event ->
        val member = room.member(event.actorID)
        val text = AttachedFileNote.split(event.text)
        GroupVoiceLine(
            id = "voice-seq-" + event.seq,
            // The bot's profile (`room.member(id)?.profile`), not the member identifier: it is
            // what chooses its voice (`BotVoices`) and its face in the orb. The identifier, for
            // its part, is kept apart — the thread draws it and the protocol names it.
            profile = if (event.kind == "message.user") null else member?.profile ?: event.actorID,
            memberID = if (event.kind == "message.user") null else event.actorID,
            name = member?.label ?: OnyxLocalization["A bot"],
            faceCode = face(member?.profile.orEmpty()),
            text = text.text,
            paths = if (event.kind == "message.user") text.paths else emptyList(),
            atMillis = event.createdAtMillis,
            seq = event.seq,
            after = event.seq - 1,
        )
    }
    val pending = sending.map { message ->
        val text = AttachedFileNote.split(message.text)
        GroupVoiceLine(
            id = "voice-sending-" + message.id,
            profile = null,
            name = "",
            text = text.text,
            paths = text.paths,
            files = message.files,
            // What did not arrive in the room is **visible**: the bubble stays, marked by its
            // failure if it is definitive. A message that disappears silently is a lost message.
            waiting = !message.failed,
            failed = message.failed,
        )
    }
    val writing = shownDrafts.map { draft ->
        val member = room.member(draft.memberID)
        GroupVoiceLine(
            id = "voice-draft-" + draft.id,
            profile = member?.profile ?: draft.memberID,
            memberID = draft.memberID,
            name = member?.label ?: OnyxLocalization["A bot"],
            faceCode = face(member?.profile.orEmpty()),
            text = draft.text,
            live = draft.finishedAtMillis == null,
            atMillis = draft.beganMillis,
            draftId = draft.id,
            after = draft.after,
        )
    }
    return said + pending + writing
}

/**
 * The thread of a bots' room: what was said there, read from its journal, and what the person
 * says there.
 *
 * Mirror of `GroupFeed` (Echo/Services/Transport/ServerGroups:647): the journal is read after a
 * cursor (`groups.log`), the room state at each turn (`groups.state`), and the bots' replies where
 * Hermes writes them first — in the hidden session "Group: <room>" of each bot
 * (`session.events.since`). A sent message stays on screen as long as the room has not received
 * it, and a server refusal is read instead of making what was written disappear.
 *
 * A room's thread never touches the app's local thread (`GroupChatViewModel`): what is written
 * here goes into the room, and what is displayed comes from the room.
 */
class GroupRoomFeed(
    initialRoom: GroupRoom,
    private val api: GroupRoomApi,
    /** The time, injectable to test what a finished reply becomes. */
    private val clock: () -> Long = System::currentTimeMillis,
    /** The members of a Desktop room called as the roster calls them (`GroupRoom.naming`). */
    private val naming: (GroupRoom) -> GroupRoom = { it },
    /** The pauses between two send attempts — shortened in a test. */
    private val sendBackoffMs: List<Long> = GroupSendOutbox.SEND_BACKOFF_MS,
    /** The pause itself, replaceable so as not to wait in a test. */
    private val waitSend: suspend (Long) -> Unit = { delay(it) },
) {
    private val _state = MutableStateFlow(GroupRoomFeedState(room = initialRoom))
    val state: StateFlow<GroupRoomFeedState> = _state.asStateFlow()

    /**
     * The room's send queue: what has **not yet** arrived at the server. The same identifier
     * enters it only once (`event_id`), and nothing leaves it without the server having taken it
     * or without the person having been told.
     */
    private val outbox = GroupSendOutbox(backoffMs = sendBackoffMs)

    /** Sends go **in order**: a retry never doubles the next message. */
    private val sendLock = Mutex()

    private val _pendingSends = MutableStateFlow(false)

    /** A message from the person is still on its way: a voice scene's mic stays closed. */
    val pendingSends: StateFlow<Boolean> = _pendingSends.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)

    /** What the room refused, as it reads (already in the app's language). */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    /** Each live session and its last event read. */
    private val draftSeen = mutableMapOf<String, Int>()

    /** This room's bot sessions, by kept identifier: whose a live session is. */
    private val memberSessions = mutableMapOf<String, String>()
    private var lookedForMembers: Long? = null

    /** The number of each Desktop room message here, by its key: the same message keeps its row. */
    private val desktopSeqs = mutableMapOf<String, Int>()

    /** Follows the room until the task is cancelled (the screen goes away). */
    suspend fun follow() {
        var tick = 0
        while (currentCoroutineContext().isActive) {
            val busy = state.value.busy
            // While the bots are talking: the journal once per second, the replies twice.
            if (!busy || tick % 2 == 0) look()
            lookAtDrafts()
            tick += 1
            delay(if (busy) BUSY_TICK else IDLE_TICK)
        }
    }

    /** Reads the room's journal and its state (`groups.log`, `groups.state`). */
    suspend fun look() {
        if (state.value.room.inDesktop) return lookInDesktop()
        try {
            // **The journal and the state are requested together.** They are two independent
            // reads of the same channel; in series, the tick paid two round-trips
            // (the tunnel adds ~60 ms) whereas in parallel it pays one.
            val room = state.value.room.id
            val cursor = state.value.cursor
            val (fresh, roomStatus) = coroutineScope {
                val freshCall = async { api.log(room, cursor) }
                val stateCall = async { api.roomState(room) }
                freshCall.await() to stateCall.await()
            }
            if (fresh.isNotEmpty()) {
                _state.update { current ->
                    val renamed = fresh.lastOrNull { it.kind == "room.renamed" }?.payload?.strOrNull("name")
                    current.copy(
                        // **The displayed journal is bounded.** The room re-reads its journal
                        // twice per second when it works, and `events + fresh` recopied
                        // everything: a long bots session made O(n) allocations per tick, hence
                        // O(n²) over time. Five hundred lines largely cover what the screen
                        // draws (handoffs and the lead only read the last ones).
                        events = (current.events + fresh).takeLast(MAX_EVENTS),
                        cursor = fresh.last().seq,
                        // A message from the person now in the journal no longer waits (Hermes
                        // trims what it is given: it is the said text that makes them join).
                        sending = current.sending.filterNot { pending ->
                            fresh.any { it.kind == "message.user" && it.text.trim() == pending.delivered.trim() }
                        },
                        room = renamed?.let { name -> current.room.copy(name = name) } ?: current.room,
                    )
                }
            }
            val (current, status) = roomStatus
            _state.update { state ->
                val members = current?.members?.takeIf { it.isNotEmpty() && it != state.room.members }
                state.copy(
                    room = if (members != null) state.room.copy(members = members) else state.room,
                    status = status,
                    leadMemberId = roomStatus.leadMemberId,
                    problem = null,
                    loaded = true,
                )
            }
        } catch (error: Exception) {
            fail(error)
        }
    }

    /** The bots' replies as they are written (`GroupDraft`), as long as the room works. */
    suspend fun lookAtDrafts() {
        if (state.value.room.inDesktop) return
        val current = state.value
        val busy = current.busy
        // A reply leaves once the room has it; a finished one that the room never wrote (a bot
        // that passes), after a moment; one being written, when the room stops.
        val kept = current.drafts.filter { draft ->
            val landed = current.events.any { event ->
                event.kind == "message.member" && event.seq > draft.after &&
                    (draft.memberID == null || event.actorID == draft.memberID)
            }
            when {
                landed -> false
                draft.finishedAtMillis != null -> clock() - draft.finishedAtMillis!! < KEEP_FINISHED
                else -> busy
            }
        }
        if (kept != current.drafts) _state.update { it.copy(drafts = kept) }
        if (!busy) return
        val live = try {
            api.liveSessions(current.room.id)
        } catch (_: Exception) {
            return
        }
        var next = _state.value.drafts
        // Attaching sessions to members is resolved **once**, before the reads:
        // looking up each member's session at each bot, at each tick, was one extra trip
        // per member.
        if (live.any { memberSessions[it.key] == null }) findMemberSessions()
        // **Live sessions are read in parallel.** One by one, each bot added a full
        // round-trip (the tunnel adds ~60 ms) before the next one left: with four bots, the
        // fourth's reply waited for three trips. In parallel, all the drafts arrive together.
        val slices = coroutineScope {
            live.map { session ->
                async {
                    val index = next.indexOfFirst { it.id == session.id }
                    // A session at work, or one whose reply is still followed.
                    val following = index >= 0 && next[index].finishedAtMillis == null
                    if (session.status !in BUSY_STATUSES && !following) return@async null
                    val since = draftSeen[session.id] ?: 0
                    val slice = try {
                        api.sessionEvents(session.id, since)
                    } catch (_: Exception) {
                        null
                    }
                    session to slice
                }
            }.mapNotNull { it.await() }
        }
        for ((session, slice) in slices) {
            if (slice == null) continue
            val index = next.indexOfFirst { it.id == session.id }
            val first = draftSeen[session.id] == null
            draftSeen[session.id] = slice.latest
            var draft = if (index >= 0) next[index] else GroupDraft(id = session.id, after = _state.value.cursor)
            draft = draft.copy(memberID = memberSessions[session.key])
            val read = draft.read(slice.events)
            draft = read.draft
            // Read first after its beginning was released (Hermes keeps the last 512): the rest.
            if (!read.started && first && slice.truncated && draft.text.isNotEmpty()) {
                draft = draft.copy(text = "…" + draft.text)
            }
            if (index >= 0) {
                next = next.toMutableList().also { it[index] = draft }
            } else if (draft.finishedAtMillis == null || draft.text.isNotEmpty()) {
                next = next + draft
            }
        }
        if (next != _state.value.drafts) _state.update { it.copy(drafts = next) }
    }

    /** Whose each of the room's live sessions is, by each bot's title at Hermes. */
    private suspend fun findMemberSessions() {
        val last = lookedForMembers
        if (last != null && clock() - last < MEMBER_LOOK) return
        lookedForMembers = clock()
        val room = state.value.room.id
        val members = state.value.room.members
        // **Members are looked up in parallel.** One by one, each member added a
        // round-trip before the next one left; in parallel, a single wait.
        val found = coroutineScope {
            members.map { member ->
                async {
                    val key = try {
                        api.memberSession(room, member.profile)
                    } catch (_: Exception) {
                        null
                    }
                    member to key
                }
            }.mapNotNull { it.await() }
        }
        for ((member, key) in found) {
            if (key != null) memberSessions[key] = member.id
        }
    }

    /** A room held by Hermes Desktop: its copy is re-read (Desktop writes it an instant later). */
    private suspend fun lookInDesktop() {
        try {
            val current = api.desktopRooms().firstOrNull { it.id == state.value.room.id }
            if (current == null) {
                _state.update {
                    it.copy(problem = OnyxLocalization["This group chat was ended in Hermes Desktop."])
                }
                return
            }
            val named = naming(current)
            val fresh = desktopEvents(named, desktopSeqs)
            _state.update { state ->
                state.copy(room = named, events = fresh, loaded = true, problem = null)
            }
        } catch (error: Exception) {
            fail(error)
        }
    }

    /**
     * Adds a message from the person, on its way to the room: it is displayed right away, and
     * stays there as long as the journal does not have it. Returns its event identifier.
     *
     * The identifier can be **imposed**: a question recognized by voice arrives with its own
     * (`GroupAskLedger`), and it is the `event_id` — a question replayed by the screen cannot
     * therefore enter the room twice.
     */
    fun beginSend(
        text: String,
        files: List<String> = emptyList(),
        id: String = java.util.UUID.randomUUID().toString(),
    ): String {
        if (_state.value.sending.any { it.id == id }) return id
        _state.update { it.copy(sending = it.sending + GroupSending(id = id, text = text, files = files)) }
        outbox.offer(id, text, clock())
        _pendingSends.value = outbox.isNotEmpty
        return id
    }

    /**
     * **Names the room's lead on the server side** (`groups.lead`), or removes it (`memberId = null`).
     *
     * Returns `true` when the server accepted — it then writes a `room.lead_changed` in the journal,
     * and the room queries this member first when nobody is named. Returns `false` when the
     * server does not know the route: the setting stays on the phone, and the journal has nothing
     * to re-read.
     */
    suspend fun nameLead(memberId: String?): Boolean {
        // Three server ages, from newest to oldest:
        //   1. `groups.context` + `routing: "lead"` — the upstream form (the lead distributes);
        //   2. `groups.lead` — the batch 5 route (the lead replies first);
        //   3. neither — the setting stays on this phone.
        val named = api.leadRouting(state.value.room.id, memberId) ||
            api.nameLead(state.value.room.id, memberId)
        if (named) look()
        return named
    }

    /**
     * **Records a handoff in the room** (`groups.handoff`): the lead who leaves has named its
     * successor, the room notes it, and the other devices see it.
     *
     * Returns `false` when this server does not know the route — the local journal line then
     * remains the only trace, exactly as before.
     */
    suspend fun handOver(from: String, to: String): Boolean {
        if (from == to) return false
        val done = api.handoff(state.value.room.id, from, to)
        if (done) look()
        return done
    }

    /**
     * Says the message in the room (`groups.send`) and re-reads the journal.
     *
     * **A message is not lost.** A send that fails for a transient reason (network, 5xx,
     * timeout, internal server error) **stays queued** and goes again with increasing pauses —
     * always under the **same** `event_id`, so a server that had already taken the message keeps
     * it only once. Once exhausted, the failure is **stated**: the bubble stays on screen,
     * marked, and the problem is displayed in the app's language.
     *
     * Refused for good by the server (a question that no longer waits), the message leaves
     * the screen and the refusal is read — never a message that disappears silently.
     */
    suspend fun deliver(id: String, said: String) {
        _state.update { state ->
            state.copy(sending = state.sending.map { if (it.id == id) it.copy(said = said) else it })
        }
        sendLock.withLock {
            var attempt = 0
            while (true) {
                try {
                    api.send(state.value.room.id, said, id)
                    outbox.sent(id)
                    _pendingSends.value = outbox.isNotEmpty
                    look()
                    return
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (!sendIsWorthRetrying(error) || attempt >= sendBackoffMs.size) {
                        failSend(id, error)
                        return
                    }
                    attempt += 1
                    val wait = sendBackoffMs[(attempt - 1).coerceAtMost(sendBackoffMs.lastIndex)]
                    VoiceDiagnostics.note(
                        "salle (${state.value.room.name}): envoi en échec ($attempt/${sendBackoffMs.size}), " +
                            "reprise dans ${wait} ms — $id",
                    )
                    // The message stays in the queue **and** on screen: it has not disappeared.
                    _state.update { current ->
                        current.copy(
                            sending = current.sending.map { pending ->
                                if (pending.id == id) pending.copy(attempts = attempt) else pending
                            },
                        )
                    }
                    waitSend(wait)
                }
            }
        }
    }

    /**
     * A message that could not leave (attachment refused, dead network, server refusal).
     *
     * A **definitive** refusal keeps the message on screen, marked: it does not evaporate. A
     * "question that no longer waits" refusal (5118/5119) removes it, like the chat thread does,
     * because the room has already handled it elsewhere.
     */
    fun failSend(id: String, error: Exception) {
        val reason = reason(error)
        val stale = (error as? TransportError.Rpc)?.code in GroupSendOutbox.STALE_CODES
        outbox.giveUp(id)
        _pendingSends.value = outbox.isNotEmpty
        val message = if (stale) {
            OnyxLocalization.format("Couldn't send: %1\$s", reason)
        } else {
            OnyxLocalization.format("Your message did not reach the room: %1\$s", reason)
        }
        _problem.value = message
        _state.update { state ->
            state.copy(
                sending = if (stale) {
                    state.sending.filterNot { it.id == id }
                } else {
                    state.sending.map { pending ->
                        if (pending.id == id) pending.copy(failed = true, problem = reason) else pending
                    }
                },
                problem = message,
            )
        }
        VoiceDiagnostics.note("salle (${state.value.room.name}): envoi perdu — $reason")
    }

    /** What the server refused, on screen, as is (a rename, a room deletion). */
    fun report(error: Exception) = fail(error)

    /**
     * A recognized question enters the room, **only once**.
     *
     * This is the voice path: `askId` is the identifier given to it by `GroupAskLedger`, and it
     * serves as the `event_id`. A replayed question (the screen recomposes, the phase restarts,
     * the engine returns its transcript one more time) carries the same identifier and therefore
     * does not enter twice: the send queue refuses it, while logging it.
     */
    suspend fun ask(question: String, askId: String) {
        val clean = question.trim()
        if (clean.isEmpty()) return
        if (outbox.knows(askId) || _state.value.sending.any { it.id == askId }) {
            VoiceDiagnostics.note("salle (${state.value.room.name}): question déjà posée — elle ne repart pas ($askId)")
            return
        }
        beginSend(clean, emptyList(), askId)
        deliver(askId, clean)
    }

    /** Stops the room's bots (`groups.stop`). */
    suspend fun stop() {
        try {
            api.stop(state.value.room.id)
            look()
        } catch (error: Exception) {
            fail(error)
        }
    }

    /** Restarts a reply stopped halfway (`groups.retry`). */
    suspend fun retry(task: String) {
        settle(task) { api.retry(state.value.room.id, task) }
    }

    /** Answers a bot's command ("once" or "deny"). */
    suspend fun answer(approval: GroupApproval, choice: String) {
        settle(approval.id) { api.approve(state.value.room.id, approval, choice) }
    }

    /**
     * Answers one of the room's questions, its card hidden meanwhile. Refused as no longer
     * pending (answered elsewhere — 5119 —, or expired), the room is re-read and the card goes away.
     */
    private suspend fun settle(id: String, call: suspend () -> Unit) {
        if (state.value.answering.contains(id)) return
        _state.update { it.copy(answering = it.answering + id) }
        try {
            call()
            look()
        } catch (error: TransportError) {
            if (error is TransportError.Rpc && error.code in STALE_CODES) look() else fail(error)
        } catch (error: Exception) {
            fail(error)
        }
        _state.update { it.copy(answering = it.answering - id) }
    }

    /** Renames the room on the server (`groups.rename`). */
    suspend fun rename(to: String) {
        api.rename(state.value.room.id, to)
        _state.update { it.copy(room = it.room.copy(name = to)) }
        look()
    }

    /** The bot whose task this is, from the room's journal (the end of a turn names it). */
    fun memberForTask(task: String): String? =
        state.value.events.lastOrNull { it.payload["task_id"] != null && it.actorID != null }
            ?.let { state.value.room.member(it.actorID)?.label }

    private fun fail(error: Exception) {
        _state.update { it.copy(problem = reason(error), loaded = true) }
    }

    private fun reason(error: Exception): String =
        humanFailure(error, OnyxLocalization["unknown error"])

    companion object {
        const val BUSY_TICK = 500L
        const val IDLE_TICK = 5_000L
        const val KEEP_FINISHED = 20_000L

        /** The number of journal lines kept in memory for display. */
        const val MAX_EVENTS = 500
        const val MEMBER_LOOK = 5_000L

        /** A session that is writing, or that has just started. */
        val BUSY_STATUSES = setOf("working", "starting")

        /** The codes of a refusal that is not a failure: the question no longer awaits an answer. */
        val STALE_CODES = setOf(5118, 5119)

        /** A Desktop room's journal, like the entries the screen draws. */
        fun desktopEvents(room: GroupRoom, seqs: MutableMap<String, Int>): List<GroupEvent> =
            room.desktopMessages.map { message ->
                val seq = seqs[message.key] ?: ((seqs.values.maxOrNull() ?: 0) + 1)
                seqs[message.key] = seq
                GroupEvent(
                    seq = seq,
                    kind = if (message.fromPerson) "message.user" else "message.member",
                    actorKind = if (message.fromPerson) "user" else "member",
                    actorID = message.profile,
                    payload = buildJsonObject { put("text", message.text) },
                    createdAtMillis = message.atMillis,
                )
            }
    }
}
