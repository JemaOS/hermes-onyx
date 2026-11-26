package com.onyx.app

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.ClarifyQuestion
import com.onyx.app.data.model.ClarifyRequest
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.SecretRequest
import com.onyx.app.notifications.NotificationCopy
import com.onyx.app.notifications.pendingQuestionsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce qui fait qu'une question devient une notification : une question ouverte, un sudo en attente,
 * un secret sans valeur. Une interruption déjà répondue n'y figure pas.
 */
class PendingQuestionsTest {

    @Test
    fun `une question ouverte devient une notification repondable en ligne`() {
        val message = ChatMessage(
            id = "m1",
            role = Role.ASSISTANT,
            createdAt = 0L,
            clarify = ClarifyRequest(
                id = "c1",
                questions = listOf(ClarifyQuestion(id = "q1", question = "Which branch?")),
            ),
        )

        val pending = pendingQuestionsOf(listOf(message))

        assertEquals(1, pending.size)
        assertEquals(NotificationCopy.QuestionKind.CLARIFY, pending[0].kind)
        assertEquals("Which branch?", pending[0].prompt)
        assertEquals("c1", pending[0].requestId)
        assertEquals("onyx.question.c1", pending[0].notificationId)
        assertTrue(NotificationCopy.canAnswerInline(pending[0].kind, hasSession = false, questionCount = pending[0].questionCount))
    }

    @Test
    fun `une serie de questions porte le question_id et nest pas repondable en ligne`() {
        val message = ChatMessage(
            id = "m1",
            role = Role.ASSISTANT,
            createdAt = 0L,
            clarify = ClarifyRequest(
                id = "batch",
                isBatch = true,
                questions = listOf(
                    ClarifyQuestion(id = "q1", question = "First?"),
                    ClarifyQuestion(id = "q2", question = "Second?"),
                ),
            ),
        )

        val pending = pendingQuestionsOf(listOf(message))

        assertEquals(1, pending.size)
        assertEquals("q1", pending[0].questionId)
        assertEquals(2, pending[0].questionCount)
        // Trop de questions pour un seul champ : la notification ouvre l'app.
        assertTrue(!NotificationCopy.canAnswerInline(pending[0].kind, hasSession = true, questionCount = pending[0].questionCount))
    }

    @Test
    fun `un sudo en attente et un secret reclame sont deux notifications`() {
        val message = ChatMessage(
            id = "m1",
            role = Role.ASSISTANT,
            createdAt = 0L,
            sudoRequestId = "sudo-7",
            secret = SecretRequest(id = "sec-9", prompt = "Paste your API key"),
        )

        val pending = pendingQuestionsOf(listOf(message))

        assertEquals(2, pending.size)
        assertEquals(NotificationCopy.QuestionKind.SUDO, pending[0].kind)
        assertEquals("sudo-7", pending[0].requestId)
        assertEquals(NotificationCopy.QuestionKind.SECRET, pending[1].kind)
        assertEquals("Paste your API key", pending[1].prompt)
    }

    @Test
    fun `un secret deja fourni ne se notifie plus`() {
        val message = ChatMessage(
            id = "m1",
            role = Role.ASSISTANT,
            createdAt = 0L,
            secret = SecretRequest(id = "sec-9", prompt = "Paste your API key", provided = "sk-…"),
        )

        assertEquals(0, pendingQuestionsOf(listOf(message)).size)
    }

    @Test
    fun `un message utilisateur nest jamais une question de l'agent`() {
        val message = ChatMessage(
            id = "m1",
            role = Role.USER,
            text = "hello",
            createdAt = 0L,
            sudoRequestId = "sudo-7",
        )

        assertEquals(0, pendingQuestionsOf(listOf(message)).size)
    }
}
