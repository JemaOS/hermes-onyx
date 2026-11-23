package com.onyx.app.notifications

/**
 * The text of Onyx notifications, in plain terms, so it can be tested without Android.
 *
 * The strings returned are the English keys that [com.onyx.app.localization.OnyxLocalization]
 * translates (identity in English, templates for the variable parts). It is the same contract as the
 * rest of the app: no label hard-coded in a screen or in a notification.
 */
object NotificationCopy {

    // —— Approvals ————————————————————————————————————————————————

    /** The title of an approval request (`ApprovalNotifier`). */
    const val APPROVAL_TITLE = "Approval needed"

    /** The two answers, word for word like the gateway's buttons. */
    const val APPROVE = "Approve"
    const val DENY = "Deny"

    /** What the notification says once the answer has been sent. */
    fun resolvedTitle(approved: Boolean): String = if (approved) "Approved" else "Denied"

    /** An answer that could not be sent: the command is still waiting. */
    const val UNANSWERED_TITLE = "Couldn't answer"
    const val UNANSWERED_TEXT = "Open Onyx to answer from the conversation."

    /** What the notification of a question says once the answer has been sent. */
    const val ANSWERED_TITLE = "Answered"

    /** The notification text after an answer: the command, or the failure and what to do next. */
    fun answeredText(command: String, failed: Boolean): String =
        if (failed) UNANSWERED_TEXT else command.take(120)

    // —— Agent questions ————————————————————————————————————————

    /** The three things that stop an agent: a question, sudo, a secret. */
    enum class QuestionKind { CLARIFY, SUDO, SECRET }

    /** The "Reply" action ("Répondre"), shared by all three. */
    const val REPLY = "Reply"

    /** The action that opens the app when inline input is not possible. */
    const val OPEN = "Open"

    /**
     * The title of a question, depending on what the agent asks.
     *
     * [name] is the name of whoever asks the question: the bot in its thread, otherwise "Onyx". The title
     * then names the bot — "writer has a question" — which French renders with its own pattern.
     */
    fun questionTitle(kind: QuestionKind, name: String? = null): String = when (kind) {
        QuestionKind.CLARIFY -> name?.takeIf { it.isNotBlank() && it != "Onyx" }?.let { "$it has a question" }
            ?: "Onyx has a question"
        QuestionKind.SUDO -> "A password is needed"
        QuestionKind.SECRET -> "A secret is needed"
    }

    /** The body of a question: the question itself, or the secret's instruction. */
    fun questionText(prompt: String): String = prompt.take(160)

    /**
     * Is an inline answer possible?
     *
     * `sudo` and `secret` are addressed to the open session: without its identifier, the answer cannot
     * be sent from the notification, and the action opens the app. A series of questions
     * (a `batch` of more than one question) cannot fit in a single input field either.
     */
    fun canAnswerInline(kind: QuestionKind, hasSession: Boolean, questionCount: Int): Boolean = when (kind) {
        QuestionKind.CLARIFY -> questionCount <= 1
        QuestionKind.SUDO, QuestionKind.SECRET -> hasSession
    }

    // —— Turn in progress (Live Activity) ————————————————————————————————

    /** The turn's sentence, with the current tool and the elapsed time. */
    fun turnPhase(prefillPercent: Int?, toolName: String?, elapsedSeconds: Long?): String {
        val base = when {
            !toolName.isNullOrBlank() -> "Running $toolName"
            prefillPercent != null -> "$prefillPercent% read"
            else -> "Answering…"
        }
        val seconds = elapsedSeconds?.takeIf { it > 0 } ?: return base
        return "$base · ${elapsed(seconds)}"
    }

    /** A short, readable duration: "12s", "1m 05s", "1h 02m". */
    fun elapsed(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return when {
            safe < 60 -> "${safe}s"
            safe < 3600 -> "${safe / 60}m ${(safe % 60).toString().padStart(2, '0')}s"
            else -> "${safe / 3600}h ${((safe % 3600) / 60).toString().padStart(2, '0')}m"
        }
    }

    // —— End of a bot's work ————————————————————————————————————

    /** The title of the finished notification: the result, in one word. */
    const val FINISHED_TITLE = "Finished"
    const val FAILED_TITLE = "Failed"

    /** The title a progress notification carries when the thread has no name yet. */
    const val ONYX = "Onyx"

    /** How much of the answer the excerpt keeps: enough to recognize the answer without reading it whole. */
    const val EXCERPT_CHARS = 200

    /** The title of the finished notification, depending on the turn's outcome. */
    fun finishedTitle(failed: Boolean): String = if (failed) FAILED_TITLE else FINISHED_TITLE

    /**
     * The excerpt of the answer: the first words, on a single line.
     *
     * A notification does not unfold a paragraph — it gives enough to recognize the answer,
     * and the conversation carries it whole. Whitespace is collapsed so that no line break
     * from the server splits the notification into three.
     */
    fun excerpt(reply: String, limit: Int = EXCERPT_CHARS): String =
        reply.trim().replace(WHITESPACE, " ").take(limit).trim()

    private val WHITESPACE = Regex("\\s+")
}
