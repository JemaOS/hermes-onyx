package com.onyx.app.notifications

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role

/**
 * Une question de l'agent qui attend encore sa réponse, prête à être notifiée.
 *
 * Ce que la notification a besoin de savoir, et rien de plus: de quoi l'identifier, de quoi la
 * reformuler, et de quoi répondre par le bon appel du gateway.
 */
data class PendingQuestion(
    /** L'identifiant Android de la notification: stable tant que la question attend. */
    val notificationId: String,
    val kind: NotificationCopy.QuestionKind,
    /** Le texte montré: la question, la consigne sudo, ou le libellé du secret. */
    val prompt: String,
    /** L'identifiant que le gateway attend dans la réponse. */
    val requestId: String,
    /** La question précise d'une série (`clarify.lock`), ou `null` pour une question seule. */
    val questionId: String?,
    /** Le nombre de questions de la série: au-delà d'une, on répond depuis l'app. */
    val questionCount: Int,
)

/**
 * Les questions en attente d'une conversation, dans l'ordre où elles se sont posées.
 *
 * Comme ce que la carte montre déjà: une question (`clarify`), un mot de passe sudo
 * (`sudoRequestId`), ou un secret que l'agent réclame (`secret` sans valeur fournie). Une
 * interruption déjà répondue n'y figure pas — le champ est remis à `null` dès la réponse.
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
