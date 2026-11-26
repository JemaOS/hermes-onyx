package com.onyx.app.ui

import androidx.compose.runtime.Immutable
import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.ui.chat.ChatUiState
import com.onyx.app.ui.design.FallbackDeclaration
import com.onyx.app.ui.chat.SettledScans
import com.onyx.app.ui.voice.VoiceTurn
import com.onyx.app.notifications.PendingQuestion
import com.onyx.app.notifications.pendingQuestionsOf
import com.onyx.app.data.model.ServerConfig

/**
 * **What the app shell reads from the chat state — and that does not change from one token to another.**
 *
 * `OnyxApp()` is the root composable: 5,000 lines holding the navigation, the sidebar, the
 * sheets, the notifications and the five routes. It read `ChatUiState` **whole** (`val state by
 * …collectAsStateWithLifecycle()`), and the chat state is republished on every text publication —
 * thirty-three times per second during an answer. Every token therefore replayed the whole root,
 * and above all the three scans it does of **the entire** feed:
 *
 *  - `state.subagents`, a `flatMap` on each message, a `distinctBy`, a merge with the roster;
 *  - `state.messages.flatMap { it.approvals }`, a second full scan;
 *  - `pendingQuestionsOf(state.messages)`, a third.
 *
 * These three do **not** depend on the text being written: an approval, an agent question or
 * a subagent task do not move because a word just arrived. This projection therefore computes them
 * once per **shape** of the feed, and its structural equality means the root no
 * longer recomposes on each token: only the fields it really carries (the server, the conversation,
 * the turn state) make it change.
 *
 * **What stays live.** The text of the messages is **not** here: it changes on every token, and
 * a value that changes on every token must only be read by the line that displays it. The shell
 * only keeps its **size** (`messagesSize`), which is enough for its effects (starting the departure
 * cards, publishing to the widget, re-reading the roster); where it needs the text itself, it reads
 * the state at the moment of the effect (`chatState.value`), not during composition.
 */
@Immutable
data class ShellState(
    val server: ServerConfig,
    val conversationId: String?,
    val serverSessionId: String?,
    val conversationName: String?,
    val input: String,
    val isStreaming: Boolean,
    val modelInForce: String?,
    val fallback: FallbackDeclaration?,
    /** One row per subagent of the conversation, roster included (computed once). */
    val subagents: List<SubagentActivity>,
    /** The shape of the feed: its size, not its content. */
    val messagesSize: Int,
    /** The approvals awaiting a decision, across all conversations of the feed. */
    val pendingApprovals: List<ApprovalRequest>,
    /** The agent questions (question, sudo, secret) awaiting an answer. */
    val pendingQuestions: List<PendingQuestion>,
    /** The first question of the conversation — the voice fallback title. */
    val firstUserQuestion: String?,
    /**
     * The history of the voice screen: the **finished** turns.
     *
     * The line being written only enters it at the end of its turn ([ChatMessage.isStreaming] false):
     * otherwise the list would be recomputed on every token for a screen that only shows finished
     * turns.
     */
    val voiceTurns: List<VoiceTurn>,
) {
    companion object {

        /** Projects the chat state into what the shell reads from it. Pure, therefore measurable without a device. */
        fun of(ui: ChatUiState): ShellState {
            val messages = ui.messages
            // The boundary of **finished** messages: during a turn, the last line is being written and
            // does not yet enter a history.
            val settledCount = if (ui.isStreaming) (messages.size - 1).coerceAtLeast(0) else messages.size

            // The pending approvals, in **one** pass with no intermediate allocation. They are not
            // hidden: an approval can be born **during** the turn, on the line being
            // written, and a card awaiting a decision must never wait for one more token.
            val approvals = ArrayList<ApprovalRequest>()
            for (index in messages.indices) {
                val pending = messages[index].approvals
                if (pending.isEmpty()) continue
                for (approval in pending) if (approval.isPending) approvals.add(approval)
            }

            // What depends only on the finished feed (first question, voice history) is **computed
            // once per shape of the feed**, not on every token: that is what grew with
            // the history. See [SettledScans].
            val settled = SettledScans.of(messages, settledCount)

            return ShellState(
                server = ui.server,
                conversationId = ui.conversationId,
                serverSessionId = ui.serverSessionId,
                conversationName = ui.conversationName,
                input = ui.input,
                isStreaming = ui.isStreaming,
                modelInForce = ui.modelInForce,
                fallback = ui.fallback,
                subagents = ui.subagents,
                messagesSize = messages.size,
                pendingApprovals = approvals,
                pendingQuestions = pendingQuestionsOf(messages),
                firstUserQuestion = settled.firstUserQuestion,
                voiceTurns = settled.voiceTurns,
            )
        }

    }
}
