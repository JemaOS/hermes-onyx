package com.onyx.app.ui.chat

import androidx.compose.runtime.Immutable
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.TokenUsage

/**
 * **What the thread screen reads from the chat state — without the text being written.**
 *
 * `ChatScreen` used to read the **whole** `ChatUiState`: the screen body is 2,200 lines (top bar,
 * tab pager, composer, menus, sheets), and every text publication — 33 per second during a reply —
 * replayed it from end to end. Yet the live text is only read by **one** line: the one being
 * written.
 *
 * This projection is the lock: it carries only what does **not** move from one token to the next,
 * its structural equality holds throughout the stream, and `derivedStateOf` therefore means the
 * body no longer recomposes when a word arrives. The thread, for its part, reads the live list
 * separately (`stateFlow.value.messages`) in its own scope: it is the only one that must follow the
 * text.
 *
 * What stays live and is **read by the body without making it recompose**:
 *  - `messagesSize` (the shape of the thread, not its content);
 *  - `liveStep` (the turn step: it only changes at transitions);
 *  - `settledCount` / `settledLastId` (the boundary of finished messages: the Stream and the Files
 *    read only those);
 *  - `pendingApprovals` / `contextUsage` (two scans that do not depend on the text).
 */
@Immutable
data class ChatScreenState(
    val server: ServerConfig,
    val conversationId: String?,
    val serverSessionId: String?,
    val conversationName: String?,
    val input: String,
    val isStreaming: Boolean,
    val canSteer: Boolean,
    val canRetry: Boolean,
    val isEmpty: Boolean,
    val modelInForce: String?,
    val profileInForce: String,
    val botProfile: String?,
    val fallback: com.onyx.app.ui.design.FallbackDeclaration?,
    val error: String?,
    val isConnectionProblem: Boolean,
    val providerStatus: String?,
    val steerStatus: String?,
    val prefill: com.onyx.app.data.model.PrefillProgress?,
    val attachments: List<PendingAttachment>,
    val attachmentProblem: String?,
    val searchOpen: Boolean,
    val searchQuery: String,
    val searchHits: List<String>,
    val searchCurrent: Int,
    val followUps: List<String>,
    val slashItems: List<SlashItem>,
    val modelPickerRequested: Boolean,
    val pendingSlash: SlashAction?,
    val restorePrompt: RestorePrompt?,
    val restoreNotice: String?,
    val branchPrompt: BranchPrompt?,
    val subagents: List<SubagentActivity>,

    // ---- The shape of the thread, never its live content --------------------------------------
    /** The number of messages — the list key, stable while a reply is being written. */
    val messagesSize: Int,
    /** The number of messages waiting to be sent. */
    val queuedSize: Int,
    /** The identifier of the first message: the resume page resets when it changes. */
    val firstMessageId: String?,
    /**
     * Does the server keep messages **older** than those loaded here?
     *
     * The thread opens on its last page ([com.onyx.app.data.net.ServeSessionMessages.THREAD_PAGE])
     * instead of the whole thread: 500 messages weighed 1.17 MB on every open, in a database that
     * never stops growing. "Older messages" therefore goes up page by page, and this value says
     * whether anything is left to ask for.
     */
    val earlierAvailable: Boolean = false,
    /** An older page is on its way (the top line says so, without lying). */
    val loadingEarlier: Boolean = false,
    /** Has the turn announced a phase? (`ChatUiState.live` non-null) */
    val hasLivePhase: Boolean,
    /**
     * Does the phase announce an **ongoing** turn? (`TurnPhase.isStreaming`)
     *
     * Distinct from [isStreaming] in a single case: the instant the server stops the turn. The
     * phase says it before the state, and it is the phase that drives the thread state
     * (`turnFlowState`) — the projection therefore keeps **its** value, so as not to paint
     * "work in progress" on a turn the server has just closed.
     */
    val liveStreaming: Boolean,
    /** The current turn step — a step change is a real screen change. */
    val liveStep: TurnStep?,
    /** The first question of the thread (the fallback title, the empty state). */
    val firstUserQuestion: String?,
    /** Has there been at least one question? (the welcome only shows without a turn) */
    val hasUserTurn: Boolean,
    /** The number of the person's turns: it is what brings the view back to the bottom. */
    val userTurnCount: Int,
    /** The number of **finished** messages: the Stream and the Files stop there. */
    val settledCount: Int,
    /** The identifier of the last finished message — the key of their caches. */
    val settledLastId: String?,
    /** The approvals waiting for a decision (a scan, not a text). */
    val pendingApprovals: Int,
    /** The last known context fill, for the top chip. */
    val contextUsage: TokenUsage?,
) {
    companion object {

        fun of(ui: ChatUiState): ChatScreenState {
            val messages = ui.messages
            val settled = if (ui.isStreaming) (messages.size - 1).coerceAtLeast(0) else messages.size
            // Approvals alone are scanned on every publication: a card waiting for a decision can
            // be born **during** the turn, on the line being written, and must appear without
            // waiting for a shape change. The question count, for its part, does not move from one
            // token to the next: it comes from the finished-thread projection ([SettledScans]), not
            // from a second scan of the whole history on every text publication.
            var approvals = 0
            for (message in messages) {
                val pending = message.approvals
                if (pending.isEmpty()) continue
                for (approval in pending) if (approval.isPending) approvals++
            }
            val scans = com.onyx.app.ui.chat.SettledScans.of(messages, settled)
            val users = scans.userTurns + if (ui.isStreaming && messages.lastOrNull()
                    ?.role == com.onyx.app.data.model.Role.USER) 1 else 0
            return ChatScreenState(
                server = ui.server,
                conversationId = ui.conversationId,
                serverSessionId = ui.serverSessionId,
                conversationName = ui.conversationName,
                input = ui.input,
                isStreaming = ui.isStreaming,
                canSteer = ui.canSteer,
                canRetry = ui.canRetry,
                isEmpty = ui.isEmpty,
                modelInForce = ui.modelInForce,
                profileInForce = ui.profileInForce,
                botProfile = ui.botProfile,
                fallback = ui.fallback,
                error = ui.error,
                isConnectionProblem = ui.isConnectionProblem,
                providerStatus = ui.providerStatus,
                steerStatus = ui.steerStatus,
                prefill = ui.prefill,
                attachments = ui.attachments,
                attachmentProblem = ui.attachmentProblem,
                searchOpen = ui.searchOpen,
                searchQuery = ui.searchQuery,
                searchHits = ui.searchHits,
                searchCurrent = ui.searchCurrent,
                followUps = ui.followUps,
                slashItems = ui.slashItems,
                modelPickerRequested = ui.modelPickerRequested,
                pendingSlash = ui.pendingSlash,
                restorePrompt = ui.restorePrompt,
                restoreNotice = ui.restoreNotice,
                branchPrompt = ui.branchPrompt,
                subagents = ui.subagents,
                messagesSize = messages.size,
                queuedSize = ui.queued.size,
                firstMessageId = messages.firstOrNull()?.id,
                earlierAvailable = ui.earlierAvailable,
                loadingEarlier = ui.loadingEarlier,
                hasLivePhase = ui.live != null,
                liveStreaming = ui.live?.isStreaming == true,
                liveStep = ui.live?.step,
                firstUserQuestion = scans.firstUserQuestion,
                hasUserTurn = users > 0,
                userTurnCount = users,
                settledCount = settled,
                settledLastId = messages.getOrNull(settled - 1)?.id,
                pendingApprovals = approvals,
                contextUsage = messages.lastOrNull { it.usage?.contextUsed != null }?.usage,
            )
        }
    }
}
