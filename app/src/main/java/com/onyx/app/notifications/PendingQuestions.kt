package com.onyx.app.notifications

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role

/**
 * An agent question still awaiting its answer, ready to be notified.
 *
 * What the notification needs to know, and nothing more: what to identify it by, what to
 * rephrase, and what to answer with through the right gateway call.
 */
data class PendingQuestion(
    /** The notification's Android identifier: stable as long as the question is waiting. */
    val notificationId: String,
    val kind: NotificationCopy.QuestionKind,
    /** The text shown: the question, the sudo instruction, or the secret's label. */
    val prompt: String,
    /** The identifier the gateway expects in the answer. */
    val requestId: String,
    /** The specific question of a series (`clarify.lock`), or `null` for a single question. */
    val questionId: String?,
    /** The number of questions in the series: beyond one, you answer from the app. */
    val questionCount: Int,
)

/**
 * The pending questions of a conversation, in the order they were asked.
 *
 * Android mirror of what the card already shows: a question (`clarify`), a sudo password
 * (`sudoRequestId`), or a secret the agent requests (`secret` with no value provided). An
 * interrupt already answered does not appear there — the field is reset to `null` as soon as it is answered.
 */
fun pendingQuestionsOf(messages: List<ChatMessage>): List<PendingQuestion> = buildList {
    messages.forEach { message ->
        if (message.role != Role.ASSISTANT) return@forEach
        message.clarify?.let { request ->
            val first = request.questions.firstOrNull()
            add(
                PendingQuestion(
                    notificationId = "onyx.question.${request.id}",
                    kind = NotificationCopy.QuestionKind.CLARIFY,
                    prompt = first?.question ?: request.questions.joinToString(" ") { it.question },
                    requestId = request.id,
                    questionId = if (request.isBatch) first?.id else null,
                    questionCount = request.questions.size,
                ),
            )
        }
        message.sudoRequestId?.let { requestId ->
            add(
                PendingQuestion(
                    notificationId = "onyx.sudo.$requestId",
                    kind = NotificationCopy.QuestionKind.SUDO,
                    prompt = "The agent needs your password for a command on your machine.",
                    requestId = requestId,
                    questionId = null,
                    questionCount = 1,
                ),
            )
        }
        message.secret?.takeIf { it.provided == null }?.let { request ->
            add(
                PendingQuestion(
                    notificationId = "onyx.secret.${request.id}",
                    kind = NotificationCopy.QuestionKind.SECRET,
                    prompt = request.prompt,
                    requestId = request.id,
                    questionId = null,
                    questionCount = 1,
                ),
            )
        }
    }
}
