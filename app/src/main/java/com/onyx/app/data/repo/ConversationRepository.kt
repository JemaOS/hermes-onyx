package com.onyx.app.data.repo

import androidx.datastore.core.DataStore
import com.onyx.app.data.local.DeletedConversations
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The conversation history, on disk.
 *
 * Mirrors `ConversationStore` on the reference side: the conversations are sorted from the most
 * recent to the oldest, and the chat screen saves the thread at the end of each turn.
 *
 * **A deletion is final.** The file is not enough to guarantee it: other paths
 * rewrite a conversation without knowing it was deleted (the end of a turn that saves
 * its thread, a bot binding, a migration). So each deletion places a **mark**
 * ([DeletedConversations]) — durable, outside the file — and [save] refuses to write a marked
 * identifier: a deleted conversation can no longer come back by any path, nor on restart.
 * Only [restore] (the banner's “Annuler” — undo) removes the mark.
 */
class ConversationRepository(
    private val store: DataStore<List<Conversation>>,
    private val deleted: DeletedConversations = DeletedConversations.InMemory,
) {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** The file's write lock: a single writer at a time. */
    private val writeLock = Mutex()
    /**
     * The write requests, **merged**: twenty publications during a turn make only one
     * pending entry, and it is the freshest state that goes out.
     */
    private val writeRequests = Channel<Unit>(Channel.CONFLATED)
    /** True when the file does not yet carry the current state. */
    private val dirty = AtomicBoolean(false)
    /**
     * The single writer's scope. Internal and living as long as the repository (an app
     * singleton): no write needs to be awaited by the caller, and a burst can no longer
     * block the thread that produces it.
     */
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        writer.launch {
            // **The single writer.** Each request is followed by a short window that lets the
            // burst settle; the channel being conflated, everything arriving during that time makes
            // a single entry — and the write takes the **current** state, not the request's.
            // **The window follows the cost of the write.** The store holds all the conversations
            // in a single JSON: a save reserializes and rewrites **everything**. Measured
            // (`ConversationWriteCostTest`): a conversation of 60 tools weighs 256 KiB and costs
            // 7 ms; twenty-five of these conversations weigh 6.4 MiB and cost 33 ms. With a fixed window
            // (250 ms), a turn launching 60 tools triggered four per second — 130 ms of CPU
            // and 26 MiB of disk per second, while the person reads. The window therefore lengthens
            // with the number of messages to write: one saves less often when saving
            // costs more.
            for (request in writeRequests) {
                delay(writeSettleMillis(_conversations.value.sumOf { it.messages.size }))
                writeLock.withLock { writeFile() }
            }
        }
    }

    /** The conversations, most recent first. */
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    /** Reads the file at startup. */
    suspend fun load() {
        val list = withContext(Dispatchers.IO) {
            runCatching { store.data.first() }.getOrDefault(emptyList())
        }
        // A conversation marked deleted is **also** filtered out on read: a file written
        // by an earlier version (or by a sync) does not bring it back either.
        _conversations.value = list.filterNot { it.id in deleted.marked() }.sortedByDescending { it.updatedAt }
    }

    fun find(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    /** Pins or unpins a conversation, and rewrites the file. */
    suspend fun setPinned(id: String, pinned: Boolean) {
        val existing = find(id) ?: return
        save(existing.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
    }

    /** Files a conversation into the archive, or takes it out. */
    suspend fun setArchived(id: String, archived: Boolean) {
        val existing = find(id) ?: return
        save(existing.copy(archived = archived, updatedAt = System.currentTimeMillis()))
    }

    /** Renames a conversation. */
    suspend fun rename(id: String, title: String) {
        val existing = find(id) ?: return
        save(existing.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    /**
     * “Créer un fil” (create a thread): a **branch** of this conversation — a new thread carrying the **copy**
     * of the transcript, and opening separately. The copy lives **on this phone**: the Onyx server has
     * no route to fork a session (`POST api/sessions/{id}/fork` does not exist on the
     * server side), so there is nothing to ask it — the branch is local, like the rest of the thread.
     *
     * Returns the created conversation (the caller opens it), or `null` if the original is gone.
     */
    suspend fun fork(id: String, now: Long = System.currentTimeMillis()): Conversation? {
        val existing = find(id) ?: return null
        val branch = forkedConversation(existing, java.util.UUID.randomUUID().toString(), now)
        save(branch)
        return branch
    }

    /**
     * “Brancher à partir d'ici” (branch from here): a **branch** of [conversation] keeping only its messages
     * **up to [messageId] inclusive** — the target message and everything before it, in order.
     *
     * It is the **same** cut rule as the restore (`messagesThroughRestore`); what
     * changes is the direction: the restore **removes** from the conversation, the branch **copies**
     * elsewhere and leaves the original intact. The rule itself lives in [branchedConversation],
     * pure, a little further down.
     *
     * Saves the branch and returns it (the caller opens it); nothing is sent anywhere, and
     * the original is neither modified nor rewritten. Returns `null` when the target message is gone.
     */
    suspend fun branchFrom(conversation: Conversation, messageId: String, now: Long = System.currentTimeMillis()): Conversation? {
        val branch = branchedConversation(conversation, java.util.UUID.randomUUID().toString(), messageId, now) ?: return null
        save(branch)
        return branch
    }

    /**
     * Pins several conversations at once (the reference's selection).
     *
     * **One write, not N.** Each `save` rewrites the **whole** file — all the
     * conversations, all the messages. Pinning twelve conversations rewrote the same
     * file twelve times, for an action with only one result: the list changes, then the disk
     * receives it once.
     */
    suspend fun pinAll(ids: Set<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        val touched = _conversations.value.map { conversation ->
            if (conversation.id in ids) conversation.copy(pinned = pinned) else conversation
        }
        if (touched == _conversations.value) return
        _conversations.value = touched
        persist()
    }

    /** Files several conversations into the archive — same rule: a single write. */
    suspend fun archiveAll(ids: Set<String>) {
        if (ids.isEmpty()) return
        val touched = _conversations.value.map { conversation ->
            if (conversation.id in ids) conversation.copy(archived = true) else conversation
        }
        if (touched == _conversations.value) return
        _conversations.value = touched
        persist()
    }

    /**
     * **Renaming a bot renames its conversations.**
     *
     * A bot conversation's title **is** the bot's name: it is what binds it to it
     * when `canonical_session` says nothing (`botOfConversation` reads `botName()`). Renaming the
     * bot without retitling its threads left them carrying the old name in the list and, worse,
     * **detached** them from the bot — which then lost its face and its subtitle.
     *
     * [profile] is the profile name (the identity, immutable); [fromLabel] the old label, as
     * the list showed it; [canonicalSessionId] the session the server gives this bot — the
     * only thing that binds a conversation written before `botProfile` existed.
     *
     * **The title alone is not enough to recognize a bot.** The old rule renamed every
     * conversation without `botProfile` whose title equaled the old name: two bots called the
     * same way — or a bot and another's threads, created under the same label — were
     * renamed together. Renaming a bot changed the others' name, which is not
     * consistent: only the targeted bot and what truly belongs to it are touched here.
     *
     * **And only titles that were nothing but its name.** A thread the owner named
     * themselves (“Refonte du parser” — parser rewrite) keeps its name: the bot rename does not rewrite what
     * they wrote. Only silent threads or those still at the old label follow — those whose title
     * was nothing other than the bot's name.
     *
     * A single write, like the other bulk actions.
     */
    suspend fun retitleBot(
        profile: String,
        fromLabel: String,
        toLabel: String,
        canonicalSessionId: String? = null,
    ) {
        if (toLabel.isBlank() || profile.isBlank()) return
        val old = fromLabel.trim()
        val canonique = canonicalSessionId?.takeIf { it.isNotBlank() }
        val touched = _conversations.value.map { conversation ->
            // **A bot's thread is recognized by two things, and nothing else.**
            //
            // 1. its canonical session — what the server gives the profile, and which is not confused
            //    with any other bot;
            // 2. its profile **and** a title that was nothing but its name.
            //
            // The profile alone is not enough: two bots can share it (same default
            // label, profile copied at import), and renaming one then renamed the threads of
            // the other — “j'ai changé le nom de ce bot, ça change tous les bots” (I renamed this bot, it changes all bots). And a title
            // written by someone never follows a rename: it no longer belongs to the bot.
            val parSession = canonique != null && conversation.serverSessionId == canonique
            // **A foreign session decides: this thread is not this bot's.**
            // A thread carrying another bot's session is never renamed by its title,
            // even if its profile says “coder” — that is how renaming a bot renamed the
            // other's threads.
            val sessionEtrangere = !conversation.serverSessionId.isNullOrBlank() && !parSession
            val parNom = !sessionEtrangere &&
                conversation.botProfile == profile &&
                conversation.title.trim() == old
            val mine = parSession || parNom
            when {
                !mine -> conversation
                conversation.title.trim().isEmpty() -> conversation.copy(title = toLabel)
                conversation.title.trim() == toLabel -> conversation
                // A title that was **only** the old name follows the bot; any other is kept.
                parNom || conversation.title.trim() == old -> conversation.copy(title = toLabel)
                else -> conversation
            }
        }
        if (touched == _conversations.value) return
        _conversations.value = touched
        persist()
    }

    suspend fun deleteAll(ids: Set<String>) {
        ids.forEach { deleted.mark(it) }
        _conversations.update { current -> current.filterNot { it.id in ids } }
        // **A deletion awaits its write.** The thread's publications can settle, a
        // deletion does not: if the process dies within the settling window, the conversation
        // would come back on next start. It is the only path where the wait is due to the disk.
        flush()
    }

    /**
     * Erases **all** conversations on this phone, like the reference's `Conversation.eraseAll()`.
     * Nothing is touched on the server — but each erased conversation is **marked**: what has
     * been erased does not come back, even through a path that does not read the file.
     */
    suspend fun eraseAll() {
        _conversations.value.forEach { deleted.mark(it.id) }
        _conversations.update { emptyList() }
        flush()
    }

    /**
     * Saves (or replaces) a conversation and rewrites the file.
     *
     * Refuses an identifier **marked deleted**: it is the guarantee that no path brings back a
     * deleted conversation (see the class note). The “Annuler” (undo) goes through [restore], which undoes
     * the mark first.
     */
    suspend fun save(conversation: Conversation) {
        if (conversation.id in deleted.marked()) return
        // **Nothing changed: nothing to rewrite.** A turn often re-emits the same shape (a card
        // answered, a secret given) and each write rewrites *all* the conversations in
        // the file: with many conversations, it is the whole list that goes back to disk
        // for an identical field.
        // **The read-write must be atomic.** `_conversations.value = (value - it + it)`
        // reads then rewrites: two simultaneous `save()` (the viewModel publishes, a turn callback
        // publishes) each started from the same list and the second **erased the other's
        // conversation** — twelve concurrent saves left only one on disk. It is a
        // data-loss defect, not a performance one, found by the coalescing test.
        var changed = false
        _conversations.update { current ->
            val previous = current.firstOrNull { it.id == conversation.id }
            // **The common case first, without comparing the text.** `previous == conversation` is a
            // `data class` equality: it compares **all** the messages, all their text and all
            // their tool outputs — megabytes, on every save. When the list is the
            // **same instance** (the viewModel no longer copies it if there is nothing to strip) and
            // the surface fields are equal, the answer is known without reading a single character.
            if (previous === conversation) return@update current
            if (previous != null && previous.messages === conversation.messages &&
                previous.copy(messages = emptyList()) == conversation.copy(messages = emptyList())
            ) {
                return@update current
            }
            if (previous == conversation) return@update current
            changed = true
            (current.filterNot { it.id == conversation.id } + conversation).sortedByDescending { it.updatedAt }
        }
        if (!changed) return
        persist()
    }

    /** The banner's “Annuler” (undo): undoes the deletion mark, then re-saves the conversation. */
    suspend fun restore(conversation: Conversation) {
        deleted.unmark(conversation.id)
        save(conversation)
    }

    /**
     * **Remembers a conversation's bot** (`Conversation.botProfile`) so that the screen
     * knows it from restart, without waiting for the roster.
     *
     * Same value as the one the send carries on the turn: `""` for the main profile, otherwise the
     * profile's name. Touches neither the date nor the title: the list does not move because a bot was
     * recognized.
     */
    suspend fun bindBot(conversationId: String, profile: String?) {
        val existing = find(conversationId) ?: return
        if (existing.botProfile == profile || existing.remote) return
        save(existing.copy(botProfile = profile))
    }

    /** Adds a message to the saved thread, creating the conversation if needed. */
    suspend fun append(conversationId: String, serverId: String, message: ChatMessage, now: Long = System.currentTimeMillis()) {
        val existing = find(conversationId)
        val updated = (existing ?: Conversation.start(id = conversationId, now = now).copy(serverId = serverId))
            .withMessage(message, now)
        save(updated)
    }

    /**
     * Writes (or replaces) **one** message of the saved thread.
     *
     * It is what makes a turn's response survive when its screen has gone: the turn
     * store writes it as it progresses, with no one to call `save` from a screen.
     */
    suspend fun upsertMessage(conversationId: String, message: ChatMessage) {
        val existing = find(conversationId) ?: return
        val messages = (existing.messages.filterNot { it.id == message.id } + message)
            .sortedBy { it.createdAt }
        save(
            existing.copy(
                messages = messages,
                updatedAt = maxOf(existing.updatedAt, message.createdAt),
                title = if (existing.title == Conversation.DefaultTitle && message.role == com.onyx.app.data.model.Role.USER) {
                    Conversation.titleFrom(message.text)
                } else {
                    existing.title
                },
            ),
        )
    }

    /**
     * Deletes a conversation: it leaves the file, and its identifier is **marked** — it
     * can no longer come back through a `save` (the save of a turn that was ending, a
     * bot binding, a profile migration), nor on restart.
     */
    suspend fun delete(id: String) {
        deleted.mark(id)
        _conversations.update { current -> current.filterNot { it.id == id } }
        // See [deleteAll]: a deletion awaits its write, it does not settle.
        flush()
    }

    /** The most recent conversation one can return to from the start screen. */
    fun mostRecent(excluding: String? = null): Conversation? =
        _conversations.value.firstOrNull { it.id != excluding && it.turnCount > 0 }

    private suspend fun persist() {
        // **Request, never write oneself.** `store.updateData` rewrites the **whole** file
        // (all local conversations, with their full history) and `persistCurrent()` is
        // called in sixteen places, several times per turn: it was a full rewrite per
        // tool step. The request is now *merged* (one pending entry, never N) and
        // the single writer answers it with the current state after a short window.
        dirty.set(true)
        writeRequests.trySend(Unit)
    }

    /** The write itself: the current state, without the server's conversations. */
    private suspend fun writeFile() {
        // The **server** conversations are not persisted: they are only a
        // projection of the server registry, reread on each connection. Writing them here would
        // turn them into local copies — and a deleted conversation would come back.
        val snapshot = _conversations.value.filterNot { it.remote }
        withContext(Dispatchers.IO) { store.updateData { snapshot } }
        // The file carries the state read **before** the write; a request that arrived meanwhile set
        // the flag back to true and will be served by the writer's loop.
        dirty.set(false)
    }

    /**
     * Waits for the file to be up to date. Rendering does not need it; a path that **must** know
     * the write is laid down (before handing back to the system) uses it.
     */
    suspend fun flush() {
        // Taking the lock means waiting for the current writer. If a write is still pending
        // (the file does not carry the current state), one writes it oneself; otherwise nothing is touched —
        // a `flush` on an already-written state must not cost a rewrite of the whole file.
        writeLock.withLock { if (dirty.get()) writeFile() }
    }
}
/**
 * The window during which write requests melt into one.
 *
 * Short enough that an app close right after a send finds the turn written, long
 * enough that a burst of publications (one turn, ten tool steps) costs only one write.
 */
internal const val WRITE_SETTLE_MILLIS = 250L

/**
 * **How long write requests melt, depending on what there is to write.**
 *
 * The store has only one file: a save reserializes and rewrites **all** the
 * conversations. Its cost therefore follows the number of messages, not the size of the change. With a
 * fixed window, a large installation paid the same rhythm as a small one — four writes per second
 * during a turn launching sixty tools.
 *
 * The window starts at [WRITE_SETTLE_MILLIS] and lengthens by 4 ms per message, capped at
 * [WRITE_SETTLE_CEILING_MILLIS]: on a file of 300 messages (6.4 MiB, 33 ms of writing), two
 * writes per second at most. The turn ends with `flush()`, and so does going to background —
 * nothing that must be on disk waits for this window.
 */
internal fun writeSettleMillis(messageCount: Int): Long =
    (WRITE_SETTLE_MILLIS + messageCount.coerceAtLeast(0) * 4L).coerceAtMost(WRITE_SETTLE_CEILING_MILLIS)

/** The window's ceiling: beyond it, one no longer waits, one writes. */
internal const val WRITE_SETTLE_CEILING_MILLIS = 1_200L

/** A branch's title: that of its original plus “(fork)”, like the reference. */
internal fun forkTitle(title: String): String = "$title${Conversation.ForkSuffix}"

/**
 * The conversation of a **branch from here**: the copy of the messages up to [messageId]
 * **inclusive**, under a branch title, in a new thread.
 *
 * What the copy carries, and what it does not — everything is decided here:
 *
 *  - the **messages** as they are held in memory: text, reasoning, tool steps with
 *    their inputs and outputs, **attachments** (their references to this phone's files),
 *    cards and measurements;
 *  - **not** the **outbox**: a message still unsent in the original must never leave on its
 *    own from the branch. That is what guarantees a branch triggers no turn;
 *  - **not** the server session identifier: the branch has no session at the gateway, so
 *    nothing sent there later will be written into the original. The server has no route
 *    to fork a session; the copy lives on this phone, and it is the **first send** in
 *    the branch that will open its session (the copied thread then goes as history, as always).
 *
 * The title follows the “Créer un fil” (create a thread) convention ([forkTitle]); a title still at the default takes
 * that of the first question, otherwise the branch of a conversation never renamed
 * would be called “New Conversation (fork)”.
 *
 * **Pure**, therefore verifiable without storage or device; `null` when the message is not in
 * the original (in which case there is nothing to branch).
 */
internal fun branchedConversation(
    existing: Conversation,
    id: String,
    messageId: String,
    now: Long,
): Conversation? {
    val index = existing.messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    val titled = if (existing.title == Conversation.DefaultTitle) {
        existing.messages.firstOrNull { it.role == com.onyx.app.data.model.Role.USER }
            ?.let { existing.copy(title = Conversation.titleFrom(it.text)) }
            ?: existing
    } else {
        existing
    }
    return forkedConversation(titled, id, now).copy(
        messages = existing.messages.take(index + 1),
        outbox = emptyList(),
        serverSessionId = null,
    )
}

/**
 * The conversation of a branch: the **copy** of the transcript under a new title, which inherits
 * neither the pin nor the archive of its original (a new branch arrives in Chats, at
 * the top, never in the archives). Pure, therefore verifiable without storage.
 */
internal fun forkedConversation(existing: Conversation, id: String, now: Long): Conversation =
    existing.copy(
        id = id,
        title = forkTitle(existing.title),
        createdAt = now,
        updatedAt = now,
        pinned = false,
        archived = false,
    )
