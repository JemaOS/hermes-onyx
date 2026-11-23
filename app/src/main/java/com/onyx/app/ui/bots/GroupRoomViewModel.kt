package com.onyx.app.ui.bots

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.local.RoomLeadStore
import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupApproval
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupRoomDraft
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.ServeGroupsRpc
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServerAttachment
import com.onyx.app.data.net.ServerAttachments
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.room.RoomHandoffLine
import com.onyx.app.data.room.roomAddressed
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.ui.conversations.GroupChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * An open server room: its feed (`GroupRoomFeed`), wired to the connection's Dashboard.
 *
 * Mirror of the reference's `GroupChatView` + `GroupFeed` (Echo/Views/GroupChatView:8): the room is
 * followed as long as the screen is there, what is written leaves via `groups.send`, and what is
 * displayed comes from the room's journal — never from the app's local feed.
 */
class GroupRoomViewModel(
    application: Application,
    private val config: ServerConfig,
    initialRoom: GroupRoom,
    /** The name Onyx gives the main bot: the members of a Desktop room inherit it. */
    private val mainName: String = "",
) : AndroidViewModel(application), GroupVoiceThread {

    private val rpc = ServeGroupsRpc(ServeRpc(config))
    private val attachments = ServerAttachments(ServeFilesApi(ServeClient(config)))

    /**
     * The choice kept on this phone, **behind the single resolution**.
     *
     * The room no longer re-reads the store to draw: it reads what [BotFaceResolver] returns.
     * When the screen has not yet set its table, the fallback remains the local choice — but it is
     * served by the resolver's `local` hook, the same function as everywhere else, instead of
     * a second resolution path that could yield another face.
     */
    private val faceStore: BotFaceResolver =
        BotFaceResolver.of(emptyMap(), BotFaceStore(application)::faceOf)

    /** The name the room gives to a profile's face: what the single resolution returns. */
    private fun BotFaceResolver.faceOf(profile: String): String? = codeOf(profile)

    /**
     * **This room's lead.** It lives first here (`RoomLeadStore`), like an open conversation tab
     * — that is what keeps it from one session to the next, even offline. When the server knows
     * `groups.lead`, [setLead] writes it **also into the room**: the lead then becomes a fact of
     * the room, which the bots read and which the room consults first.
     */
    private val leadStore = RoomLeadStore(application)

    /**
     * The room's default recipient: the member it writes to when the person names no one.
     * `null` — "No lead" — and the room behaves exactly as before.
     */
    private val _lead = MutableStateFlow(leadStore.lead(initialRoom.id))
    val lead: StateFlow<String?> = _lead.asStateFlow()

    /**
     * How far the journal has been read for the lead: a handoff changes the recipient **only
     * once**. Set to the last event the list knew at opening (so yesterday's journal does not
     * undo today's choice); `null` when the list knew nothing — the first journal read then
     * serves as the starting point.
     */
    private var handoffThrough: Int? = initialRoom.latestSeq

    /**
     * The **screen's** face table (`BotFaceResolver`), set by the room: it brings together the
     * phone's choice and `ui_meta.onyx.face`. Without it, the voice scene read only the local
     * choice — a member whose face comes from the server appeared there without a code, hence
     * with `BotLook`'s fallback creature, inferred from its name: two faces for one bot.
     */
    private val faces = MutableStateFlow<((String) -> String?)?>(null)

    /** The room sets its face resolution; the feed and the voice scene follow it. */
    fun useFaces(resolve: (String) -> String?) {
        faces.value = resolve
    }

    val face: (String) -> String? = { name -> faces.value?.invoke(name) ?: faceStore.faceOf(name) }

    val feed = GroupRoomFeed(
        initialRoom = initialRoom,
        api = rpc,
        naming = { room -> room.naming(roster = null, mainName = mainName) },
    )
    val state = feed.state

    init {
        // **A handoff is read from the journal**, on every response read: the first journal to
        // come is the room's history (it does not undo today's choice), and the **next** handoffs
        // change the default recipient afterwards — once each.
        viewModelScope.launch {
            state.collect { current ->
                adoptRoomLead(current)
                applyHandoffs(current)
            }
        }
    }

    /**
     * **The lead the room announces** (`groups.state` → `context.lead_member_id`), adopted once at
     * opening.
     *
     * Without this, a lead chosen from another device stayed invisible here: the menu showed the
     * phone's choice, and the screen could point to another bot than the one the room routed to.
     * The room is authoritative when it speaks; the local setting remains the fallback when it
     * stays silent.
     */
    private fun adoptRoomLead(current: GroupRoomFeedState) {
        if (leadAdopted) return
        val named = current.leadMemberId ?: return
        leadAdopted = true
        val profile = roomLeadProfile(named, current.room.members) ?: return
        if (profile == _lead.value) return
        _lead.value = profile
        leadStore.set(current.room.id, profile)
    }

    /** The room's state was read only once for the lead: afterwards, the local one lives on. */
    private var leadAdopted = false

    // — What a voice scene reads in this room (`GroupVoiceThread`) —

    override val server: ServerConfig get() = config

    override val roomName: String get() = state.value.room.name

    /** The round, as the voice scene reads it: the journal, what leaves, what is being written. */
    private val _lines = combine(state, faces) { room, _ -> room.voiceLines(face) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines

    override val isTalking: StateFlow<Boolean> =
        state.map { it.busy }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A bot waiting for an answer from the person: a command to approve, or a nudge. */
    override val waitsOnPerson: StateFlow<Boolean> =
        state.map { it.status.waitsOnPerson }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A message from the person is still on its way (attempt in progress, or spaced retry). */
    override val pendingSends: StateFlow<Boolean> = feed.pendingSends

    /**
     * What the room refused, as is: a question that did not leave, an unreadable journal,
     * a send that did not reach the room. The voice scene shows it — without that, a refused
     * question vanished without a word. What the **send queue** says takes precedence (a message
     * that has not arrived yet); the room takes over (the unreadable journal, the ended room).
     */
    override val problem: StateFlow<String?> =
        combine(feed.problem, state.map { it.problem }) { fromFeed, fromRoom -> fromFeed ?: fromRoom }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The recognized question leaves into the room (`groups.send`), **only once**: its identifier
     * is the `event_id`, and the send queue refuses to take it again.
     */
    override fun ask(question: String, askId: String) {
        viewModelScope.launch { feed.ask(question, askId) }
    }

    /** The red stop stops the room's bots (`groups.stop`). */
    override fun haltTalking() = stop()

    // The feed is followed by the screen, as long as it is on screen and the app is in front
    // (`GroupRoomScreen`), like the reference's `task(id: scenePhase == .active)`.

    /**
     * Says something in the room. A room only takes text: what is attached leaves first to the
     * server ("Onyx attachments") and the message names them, as the reference's `GroupFeed.send`
     * does.
     *
     * **The recipient is a parameter, not a protocol.** The room has a lead? The message leaves
     * **to it by default** (`roomAddressed`): it is the same mention the person would have typed.
     * The composer's `@X` mention always takes precedence, and without a lead nothing changes.
     * [to] names a recipient for this send only; absent, it is the room's lead.
     */
    fun send(
        question: String,
        pendingAttachments: List<PendingAttachment> = emptyList(),
        to: String? = null,
    ) {
        val clean = question.trim()
        if (clean.isEmpty() && pendingAttachments.isEmpty()) return
        // The recipient is chosen **here**, once: the room's lead, or the one named.
        val recipient = to ?: _lead.value
        val id = feed.beginSend(clean, pendingAttachments.map { it.name })
        viewModelScope.launch {
            try {
                var said = roomAddressed(clean, recipient, state.value.room.members)
                if (pendingAttachments.isNotEmpty()) {
                    val outgoing = withContext(Dispatchers.IO) {
                        pendingAttachments.map { attachment -> readServerAttachment(attachment) }
                    }
                    val placed = attachments.place(outgoing)
                    said = placed.fold(clean) { text, file ->
                        text + if (file.source.isImage) {
                            AttachedFileNote.picture(file.path)
                        } else {
                            AttachedFileNote.note(file.path)
                        }
                    }
                }
                feed.deliver(id, said)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                feed.failSend(id, error)
            }
        }
    }

    fun stop() {
        viewModelScope.launch { feed.stop() }
    }

    /**
     * **Chooses the room's lead** — or removes it (`null`, "No lead").
     *
     * Two places, in this order: the phone (`RoomLeadStore`, always), then the room itself when
     * this server knows `groups.lead`. Writing to the room is what makes the choice **visible to
     * the bots**: they read the journal's `room.lead_changed`, and the room consults the lead first
     * when no one is named. A server without this route simply leaves the setting here — and
     * [leadIsRooms] tells the screen so.
     */
    fun setLead(profile: String?, memberId: String? = profile) {
        val clean = profile?.takeIf { it.isNotBlank() }
        _lead.value = clean
        leadStore.set(state.value.room.id, clean)
        // The room first when it can receive it: that is what makes the lead readable to the bots
        // (a `room.lead_changed` in the journal) and makes this member consulted first. A server
        // without the route returns `false` — the local setting remains, exactly as before.
        viewModelScope.launch { feed.nameLead(memberId?.takeIf { it.isNotBlank() }) }
    }

    /**
     * What a journal **has just said** about the lead: the last handoff more recent than
     * [handoffThrough] wins, only once, and is kept for afterwards.
     *
     * The first journal to come does not count: it is the room's history, and it must not
     * undo the choice the person made today.
     */
    private fun applyHandoffs(current: GroupRoomFeedState) {
        val through = handoffThrough
        if (through == null) {
            if (current.loaded) handoffThrough = current.handoffs.lastOrNull()?.seq ?: 0
            return
        }
        val read = roomLeadRead(_lead.value, current.handoffs, through)
        handoffThrough = read.through
        if (read.lead != _lead.value) {
            val from = _lead.value
            _lead.value = read.lead
            leadStore.set(current.room.id, read.lead)
            // **The handoff becomes a fact of the room.** The lead wrote "@Coder takes over":
            // the room records it (`groups.handoff`), the other devices see it, and the departing
            // lead's turn is stopped. A server without the route returns `false` — the local
            // journal line remains, exactly as before.
            if (from != null && read.lead != null) {
                val memberOf = { profile: String ->
                    current.room.members.firstOrNull { it.profile == profile }?.id ?: profile
                }
                viewModelScope.launch {
                    feed.handOver(memberOf(from), memberOf(read.lead))
                }
            }
        }
    }

    fun retry(task: String) {
        viewModelScope.launch { feed.retry(task) }
    }

    fun answer(approval: GroupApproval, choice: String) {
        viewModelScope.launch { feed.answer(approval, choice) }
    }

    fun rename(name: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                feed.rename(name)
                onDone(null)
            } catch (error: Exception) {
                onDone(humanFailure(error))
            }
        }
    }

    /**
     * The room's pencil: a **fresh conversation of the same room**.
     *
     * A Hermes room **is** a conversation: its fresh one is therefore requested from the server
     * (`groups.create`), with the same members and the same name, under a new identifier. The
     * journal starts empty, and yesterday's room stays in the list with its feed — nothing is
     * erased, nothing is rewritten.
     *
     * `onOpened` receives the room as the server made it: the caller opens it **in the same
     * screen** (`GroupRoomScreen`), hence with the same header, the same tabs and the same
     * faces. A refusal is read in the room and the screen does not move.
     */
    fun startNewConversation(onOpened: (GroupRoom) -> Unit) {
        val room = state.value.room
        viewModelScope.launch {
            try {
                // The members pass **as is**: same profiles, same handles, same room
                // names — the fresh conversation is this room's, not another one.
                val draft = GroupRoomDraft(
                    id = GroupRoomDraft.newID(),
                    name = room.name,
                    members = room.members,
                    createdAtMillis = System.currentTimeMillis(),
                )
                val made = rpc.create(draft.id, draft.name, draft.members)
                    ?: throw IOException(OnyxLocalization["The server made no group."])
                onOpened(made)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                feed.report(error)
            }
        }
    }

    /**
     * Ends the room for good (`groups.disband`): the bots stop and the room goes away, on the
     * server too. `onEnded` is called only once the server has taken it.
     */
    fun disband(onEnded: () -> Unit) {
        viewModelScope.launch {
            try {
                rpc.disband(state.value.room.id)
                onEnded()
            } catch (error: Exception) {
                // The refusal is read on screen: the room does not disappear as long as the server
                // has not taken it, and nothing is erased here.
                feed.report(error)
            }
        }
    }

    private fun readServerAttachment(attachment: PendingAttachment): ServerAttachment {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(attachment.uri)?.use { it.readBytes() }
            ?: throw IOException("Couldn't read ${attachment.name}")
        return ServerAttachment(name = attachment.name, bytes = bytes, isImage = attachment.isImage)
    }
}

/**
 * **What a journal does to the lead**: the last handoff more recent than [through] wins, and
 * [through] advances up to it.
 *
 * This is what makes a handoff act **only once**: re-reading the journal (every second, and on
 * every reconnection) does not change the recipient twice, and does not rewrite the setting.
 */
internal data class RoomLeadRead(val lead: String?, val through: Int)

/**
 * **The profile of a lead announced by the room** (`context.lead_member_id`), resolved in the roster.
 *
 * The room names a **member** (`coder`), the screen checks a **profile**: the roster makes the
 * bridge. An identifier that matches no member is returned as is — it comes from the server, and
 * the screen prefers a name it does not know to a silently erased lead.
 */
internal fun roomLeadProfile(memberId: String, members: List<GroupMember>): String? {
    val id = memberId.trim().takeIf { it.isNotEmpty() } ?: return null
    return members.firstOrNull { it.id == id }?.profile ?: id
}

internal fun roomLeadRead(lead: String?, lines: List<RoomHandoffLine>, through: Int): RoomLeadRead {
    val fresh = lines.lastOrNull { it.seq > through } ?: return RoomLeadRead(lead, through)
    return RoomLeadRead(fresh.to, fresh.seq)
}

/** A room's members, as the bots the composer knows (handle `@`, name, face). */
internal fun GroupRoom.composerBots(face: (String) -> String? = { null }): List<BotProfile> =
    members.map { member ->
        BotProfile(
            name = member.profile,
            title = member.displayName,
            isDefault = member.profile == "default",
            faceCode = face(member.profile),
        )
    }

/**
 * The server room as the list gave it (`GroupChat` → `GroupRoom`): the feed starts from there,
 * then updates with what Hermes says about it (`groups.state`, and its copy for a room that
 * Hermes Desktop holds itself).
 */
internal fun GroupChat.toRoom(): GroupRoom = GroupRoom(
    id = id,
    name = name,
    members = members.map { bot ->
        GroupMember(id = bot.name, profile = bot.name, handle = bot.handle, displayName = bot.label())
    },
    updatedAtMillis = updatedAt,
    inDesktop = inDesktop,
)
