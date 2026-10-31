package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le pilotage (steer) : `Conversation.canSteer` et `Conversation.steer` côté référence.
 *
 * Une note écrite pendant une réponse se place AVANT la réponse qui streame, pour que la bulle
 * vivante reste la dernière du fil — et n'est pas une question, donc elle ne nomme pas la
 * conversation et ne compte pas comme un tour.
 */
class SteerNoteTest {
    private fun message(id: String, role: Role, steer: Boolean = false, text: String = id) =
        ChatMessage(id = id, role = role, text = text, createdAt = 0L, isSteer = steer)

    @Test
    fun `la note se pose juste avant la reponse en cours`() {
        val messages = listOf(
            message("u1", Role.USER, text = "Question"),
            message("a1", Role.ASSISTANT, text = "Réponse terminée"),
            message("u2", Role.USER, text = "Question suivante"),
            message("a2", Role.ASSISTANT, text = "Réponse en cours"),
        )

        assertEquals(3, steerInsertIndex(messages))
    }

    @Test
    fun `sans reponse la note va a la fin`() {
        assertEquals(0, steerInsertIndex(emptyList()))
        assertEquals(1, steerInsertIndex(listOf(message("u1", Role.USER))))
    }

    @Test
    fun `une note nest pas une question`() {
        assertTrue(message("s1", Role.USER, steer = true).isSteer)
        assertFalse(message("s1", Role.USER, steer = true).isQuestion)
        assertTrue(message("u1", Role.USER).isQuestion)
    }

    @Test
    fun `pilotage possible seulement pendant un tour sur un transport a registre`() {
        val server = ServerConfig(id = "s", baseUrl = "https://example.test", transport = Transport.HERMES_SERVE)
        val base = ChatUiState(server = server)

        assertFalse("hors tour, rien à piloter", base.canSteer)
        assertTrue("un tour sur serve se pilote", base.copy(isStreaming = true).canSteer)
        assertFalse(
            "chatCompletions n'a pas de registre",
            base.copy(isStreaming = true, server = server.copy(transport = Transport.CHAT_COMPLETIONS)).canSteer,
        )
    }
}
