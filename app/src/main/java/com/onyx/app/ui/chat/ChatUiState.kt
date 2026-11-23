package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.PrefillProgress
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus

/**
 * Everything the chat screen must render.
 *
 * Immutable: the ViewModel publishes a new value on every stream event.
 */
data class ChatUiState(
    val server: ServerConfig,
    /** Local conversation displayed: the wide list highlights exactly this row. */
    val conversationId: String? = null,
    /** Server session identifier used by the conversation projects and settings. */
    val serverSessionId: String? = null,
    /** The name given to the conversation by "Rename" (`Conversation.name` of the reference); `null`
     *  as long as no name has been given, in which case the header shows the first question. */
    val conversationName: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    /**
     * The open thread carried only its **last page** and the server keeps others: "older messages"
     * has something to ask for. See
     * [com.onyx.app.data.net.ServeSessionMessages.THREAD_PAGE].
     */
    val earlierAvailable: Boolean = false,
    /** The older page is in flight: the top line says "loading" instead of lying. */
    val loadingEarlier: Boolean = false,
    val input: String = "",
    val isStreaming: Boolean = false,
    /**
     * The honest state of the current turn of this conversation: the known step and the timings.
     * The thread uses it to say what is happening (the server does not always send enough to show
     * it), and it is the same after returning to the conversation.
     */
    val live: TurnPhase? = null,
    /** Prefill progress, when the server reports it (llama.cpp). */
    val prefill: PrefillProgress? = null,
    /** Gateway/provider status during the turn (e.g. "provider unavailable, retrying"). */
    val providerStatus: String? = null,
    /**
     * What the server answered to a steering note ("steer queued", "steer rejected…").
     * The reference's status line (`Conversation.statusLine`); it clears at the end of the turn.
     */
    val steerStatus: String? = null,
    /** Last error to show in a banner, or `null`. */
    val error: String? = null,
    /** True if the error can be replayed with the same message. */
    val canRetry: Boolean = false,
    /** Server unreachable: useful to offer the settings. */
    val isConnectionProblem: Boolean = false,
    /** Messages written during a reply or held back by a dropout, with their attachments. */
    val queued: List<com.onyx.app.data.model.OutboxItem> = emptyList(),
    /** What is attached to the message being written. */
    val attachments: List<PendingAttachment> = emptyList(),
    /**
     * What was refused when adding an attachment, and why (the weight and the cap, in figures) —
     * shown under the field, in place of the dictation problem. `null` when nothing was refused.
     */
    val attachmentProblem: String? = null,
    /** The in-thread search is open, and what it found. */
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val followUps: List<String> = emptyList(),
    /** The composer's "/" menu, when the draft starts with a slash. */
    val slashItems: List<SlashItem> = emptyList(),
    /** A bare `/model` was sent: the screen opens the model picker (only once). */
    val modelPickerRequested: Boolean = false,
    /**
     * The model actually used by the last send, once resolved against the server: it is the one the
     * second line of the top bar names, next to the bot's "@handle" (`ContentView`:
     * `modelChipLabel`).
     */
    val modelInUse: String? = null,
    /**
     * The model set for the **bot** whose conversation this is, or `null` for a plain
     * conversation. It is **the same memory** as the one written by the model sheet
     * ([com.onyx.app.ui.bots.BotsViewModel.setModel] → `FaceKeys.model`): the top pill therefore
     * immediately names what the person has just chosen, and the send carries that same model.
     */
    val botModel: String? = null,
    /**
     * The **bot profile** whose conversation this is, or `null` for a plain conversation.
     *
     * It is what decides the profile actually **in force** ([profileInForce]) and the `profile`
     * carried on the turn: the bot → profile binding. Without it, a bot's conversation ran in the
     * server profile (often `default`) while the bot was announced on its own profile. `""` (the
     * main profile) and `null` (plain conversation) are not the same thing.
     */
    val botProfile: String? = null,
    /**
     * The turn's **declared fallback**: the gateway switched from the configured model to another
     * (often the **local** model) because the first one failed, and it said so on the stream. It is
     * what makes [modelInForce] name the model **in force** and the header, the picker and the
     * usage **say** the fallback instead of announcing the configured one.
     */
    val fallback: com.onyx.app.ui.design.FallbackDeclaration? = null,
    /**
     * The "/" command the app must still execute (open the model picker, a new conversation…).
     * The screen consumes it then calls [ChatViewModel.consumeSlash]: a command must not replay on
     * the next recomposition.
     */
    val pendingSlash: SlashAction? = null,
    val searchHits: List<String> = emptyList(),
    val searchCurrent: Int = 0,
    /**
     * The roster of live subagents, as `subagent.list` returns it (Dashboard only).
     *
     * It fills in what the thread does not know: the **start date** and the **last tool** of a
     * child launched before the app looked, or during a reconnection. The thread's lines remain
     * the live source; the roster completes them.
     */
    val subagentRoster: List<SubagentActivity> = emptyList(),

    /**
     * **The roster has been read at least once.** Until it has been, an empty list says nothing —
     * it may be "not asked yet". Once read, it **is authoritative**: a task the server no longer
     * lists is no longer running, even if a message still believes it is alive.
     */
    val subagentRosterRead: Boolean = false,
    /**
     * The restore waiting for **confirmation**: the target message and what would be undone.
     * Nothing is removed as long as this field is set — it is the requested confirmation.
     */
    val restorePrompt: RestorePrompt? = null,
    /** What a restore has just undone, to announce once then forget. */
    val restoreNotice: String? = null,
    /**
     * The **branch** waiting for confirmation: the target message and what would be copied.
     * Nothing exists as long as this field is set — it is the confirmation the reference asks for
     * too.
     */
    val branchPrompt: BranchPrompt? = null,
) {
    val isEmpty: Boolean get() = messages.isEmpty() && queued.isEmpty()
    val canSend: Boolean get() = (input.isNotBlank() || attachments.isNotEmpty()) && !isStreaming

    /**
     * All the conversation's subagents: those of the replies, completed by the roster.
     *
     * A single line per `subagent_id` — a child announced by `subagent.start` then re-read by
     * `subagent.list` must not appear twice.
     */
    val subagents: List<SubagentActivity>
        get() {
            // **A single line per `subagent_id`**, including across two messages: a task
            // replayed by the thread (or resumed in a following reply) carried the same
            // identifier, and the list drew it twice — that duplicate is what crashed the tasks
            // screen (`Key … was already used` in the `LazyColumn`). The **last** known version of
            // each task is kept: it is the freshest.
            val fromMessages = messages.flatMap { message ->
                message.subagents.map { row ->
                    // **The roster is the only authority on what is still running.** A task the
                    // server no longer lists no longer works, whatever a message says: without
                    // this rule, a task launched then interrupted (dropout, resume, turn stopped)
                    // stayed "In progress" forever, with a counter climbing up to 238 min — the
                    // task list never emptied.
                    val onServer = subagentRoster.firstOrNull { it.id == row.id }
                    when {
                        !subagentRosterRead -> row
                        message.isStreaming -> row
                        !row.status.isLive -> row
                        onServer == null -> row.copy(status = SubagentStatus.UNKNOWN)
                        // The server has the last word on the state: it is what sees it working.
                        else -> row.copy(status = onServer.status)
                    }
                }
            }
                .asReversed()
                .distinctBy { it.id }
                .asReversed()
            if (subagentRoster.isEmpty()) return fromMessages
            val known = fromMessages.map { it.id }.toSet()
            val merged = fromMessages.map { row ->
                val server = subagentRoster.firstOrNull { it.id == row.id } ?: return@map row
                row.copy(
                    startedAt = row.startedAt.takeIf { it > 0 } ?: server.startedAt,
                    lastTool = row.lastTool ?: server.lastTool,
                    toolCount = row.toolCount.takeIf { it > 0 } ?: server.toolCount,
                    childSessionId = row.childSessionId ?: server.childSessionId,
                    model = row.model ?: server.model,
                    acceptingSteer = row.acceptingSteer && server.acceptingSteer,
                )
            }
            // The server roster can also repeat a line: the list is **bounded once**, on the way
            // out, so that `subagent_id` stays unique whatever happens — it is the invariant the
            // `LazyColumn` key depends on.
            return (merged + subagentRoster.filter { it.id !in known }).distinctBy { it.id }
        }

    /**
     * True when a message written now can steer the ongoing reply rather than wait.
     * Mirror of `Conversation.canSteer`: a turn must be running and the transport must keep a
     * server-side ledger (`hasLedger`: Hermes or serve sessions).
     */
    val canSteer: Boolean get() = isStreaming && server.transport.isStateful

    /**
     * **The model in force** — the single source of truth for "the model that will serve", **name
     * to paint and model to send, the same value**.
     *
     * The top pill, the chip, the model sheet and the send all read it: **what is named is what
     * leaves**. The order and the alias rule are in
     * [com.onyx.app.ui.design.ModelInForce.inForce] (conversation → bot → server → default, the
     * gateway alias set aside).
     */
    val modelInForce: String?
        get() = com.onyx.app.ui.design.ModelFallback.displayName(
            fallback,
            com.onyx.app.ui.design.ModelInForce.inForce(modelInUse, botModel, server.effectiveModel),
        )

    /**
     * **The profile in force** — the same value the send carries as `profile` on the turn.
     *
     * The conversation bot's profile decides, otherwise the server's, otherwise `default`
     * ([com.onyx.app.ui.design.ProfileInForce.inForce]). The header writes this value **as is**
     * and the send carries **exactly** [botProfile]: what is shown is what serves.
     */
    val profileInForce: String
        get() = com.onyx.app.ui.design.ProfileInForce.inForce(botProfile, server.effectiveProfile)
}

/** Switches the thread presented without leaking the previous thread's ephemeral controls. */
internal fun ChatUiState.switchToConversation(
    conversationId: String,
    serverSessionId: String?,
    messages: List<ChatMessage>,
    input: String,
    queued: List<OutboxItem>,
    conversationName: String? = null,
): ChatUiState = copy(
    conversationId = conversationId,
    serverSessionId = serverSessionId,
    conversationName = conversationName,
    messages = messages,
    // Pagination belongs to the thread we are leaving: the new conversation starts from its
    // last page, and `loadRemoteThread` will say again if older messages remain.
    earlierAvailable = false,
    loadingEarlier = false,
    input = input,
    isStreaming = false,
    live = null,
    // The served model belongs to the conversation we are leaving: the new one's arrives
    // with its first turn (`follow`). Without this, the header subtitle still announced the
    // old conversation's model after a creation.
    modelInUse = null,
    // And the **bot** model of the old conversation: the new one's arrives with its
    // own conversation (the bot that claims it), never inherited from the previous one.
    botModel = null,
    // And its **profile**: a new conversation does not inherit the profile of the bot we are
    // leaving — the screen sets it again for the conversation it opens (`setBotProfile`), or
    // `null` (plain conversation). Without this reset, the next conversation would keep the
    // previous one's profile, and the bot → profile binding would be wrong.
    botProfile = null,
    // The declared fallback belongs to the conversation we are leaving: it does not follow.
    fallback = null,
    prefill = null,
    providerStatus = null,
    steerStatus = null,
    error = null,
    canRetry = false,
    isConnectionProblem = false,
    queued = queued,
    attachments = emptyList(),
    searchOpen = false,
    searchQuery = "",
    followUps = emptyList(),
    slashItems = emptyList(),
    modelPickerRequested = false,
    pendingSlash = null,
    searchHits = emptyList(),
    searchCurrent = 0,
    restorePrompt = null,
    restoreNotice = null,
    // A confirmation that targeted the other thread no longer has an object here (the branch
    // especially: it would open the copy of a conversation we have just left).
    branchPrompt = null,
)

/**
 * A restore waiting for confirmation.
 *
 * [removedMessages]: the number of thread messages that will be removed (the target message
 * stays); [removedTurns]: the number of **user turns** that must be rewound on the gateway side
 * (`session.undo` counts turns, not messages).
 */
data class RestorePrompt(
    val messageId: String,
    val removedMessages: Int,
    val removedTurns: Int,
)

/**
 * "1 message" / "3 messages": the count, in the app's language.
 *
 * **Zero is not spoken.** A sentence announcing "removes 0 messages" is absurd: it describes an
 * action that does none. The case therefore has **its own sentence** ([restoreZeroPhrase]), and
 * [restorePromptMessage] switches to it as soon as the count is zero.
 */
internal fun restoreCountPhrase(count: Int): String =
    if (count == 1) OnyxLocalization["1 message"] else OnyxLocalization["$count messages"]

/**
 * The sentence when there is **nothing** to remove — the target message is the last of the thread.
 *
 * We do not say "0 messages": we say what is true, and what explains itself — the thread stops on
 * that message, so the restore would remove nothing.
 */
internal fun restoreZeroPhrase(): String =
    OnyxLocalization["Nothing follows this message: restoring would remove nothing."]

/**
 * What a restore **at** [messageId] would remove — `null` if the message is not in the thread.
 *
 * **Pure** function (hence testable): it is the rule that decides the count of messages and user
 * turns to rewind. The target message **stays** (it is the point we return to); everything that
 * follows it is removed. Nothing is cut here: [RestorePrompt] is used to **ask for confirmation**.
 */
internal fun restorePlan(messages: List<ChatMessage>, messageId: String): RestorePrompt? {
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    val removed = messages.drop(index + 1)
    return RestorePrompt(
        messageId = messageId,
        removedMessages = removed.size,
        removedTurns = removed.count { it.isQuestion },
    )
}

/** The thread brought back **to** [messageId]: the target message, and everything before it. */
internal fun messagesThroughRestore(messages: List<ChatMessage>, messageId: String): List<ChatMessage> {
    val index = messages.indexOfFirst { it.id == messageId }
    return if (index < 0) messages else messages.take(index + 1)
}

/**
 * A branch waiting for confirmation.
 *
 * [copiedMessages]: the number of messages the new conversation will receive — the target message
 * **and** everything before it. It is this count that the confirmation sentence gives, and it is
 * this one that a test can oppose to the thread actually created.
 */
data class BranchPrompt(
    val messageId: String,
    val copiedMessages: Int,
)

/**
 * What a branch **from** [messageId] would copy — `null` if the message is not in the thread.
 *
 * **Pure** function (hence testable): the target message is **included** (it is the starting point
 * of the new conversation), and everything after it is left to the original. Nothing is created
 * here: [BranchPrompt] is used to **ask for confirmation**.
 */
internal fun branchPlan(messages: List<ChatMessage>, messageId: String): BranchPrompt? {
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    return BranchPrompt(messageId = messageId, copiedMessages = index + 1)
}

/**
 * The **four lines** of a branch confirmation, in the reference's order: what the branch does,
 * what it does not do, when nothing starts — then, in addition, what the copy really carries on our
 * side.
 *
 * The last line is ours and it is **true**: the copy lives on this phone and the attachments follow,
 * because the messages are copied with their attachments. The count is that of the target thread,
 * in the app's language.
 */
internal fun branchPromptLines(prompt: BranchPrompt): List<String> = listOf(
    OnyxLocalization["Copies this conversation up to this message into a new conversation, and opens it."],
    OnyxLocalization["This conversation stays as it is."],
    OnyxLocalization["No task starts until you send something in the new conversation."],
    String.format(
        OnyxLocalization["The copy lives on this phone: %1\$s, attachments included."],
        restoreCountPhrase(prompt.copiedMessages),
    ),
)

/**
 * The confirmation sentence of a restore: what will be undone, plainly.
 *
 * **The real number of removed messages** — that of [restorePlan], computed on the real thread — in
 * the singular when it is 1, in the plural above. And **never "0"**: when nothing follows the
 * target message, the sentence changes ([restoreZeroPhrase]) instead of announcing a null count.
 */
internal fun restorePromptMessage(prompt: RestorePrompt): String =
    if (prompt.removedMessages == 0) {
        restoreZeroPhrase()
    } else {
        String.format(
            OnyxLocalization["This removes %1\$s and everything after it, and rewinds the server to the same point. It can't be undone."],
            restoreCountPhrase(prompt.removedMessages),
        )
    }

/** An entry of the `/` menu: what is written into the draft, and what is displayed. */
data class SlashItem(val text: String, val display: String, val detail: String = "")
