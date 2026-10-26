package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionStateTest {
    private val server = ServerConfig(
        id = "server",
        baseUrl = "https://example.test",
        model = "model",
        transport = Transport.CHAT_COMPLETIONS,
    )
    private val oldMessage = ChatMessage(id = "old", role = Role.USER, text = "Ancien fil", createdAt = 1L)
    private val oldState = ChatUiState(
        server = server,
        conversationId = "old-id",
        serverSessionId = "old-session",
        messages = listOf(oldMessage),
        input = "old draft",
        error = "old error",
        canRetry = true,
        isConnectionProblem = true,
        followUps = listOf("old follow-up"),
        slashItems = listOf(SlashItem(text = "/old", display = "old")),
        searchOpen = true,
        searchQuery = "old",
        searchHits = listOf("old"),
        searchCurrent = 1,
    )

    @Test
    fun `la selection remplace l identite et le contenu du fil affiche`() {
        val selectedMessage = ChatMessage(id = "selected", role = Role.USER, text = "Fil choisi", createdAt = 2L)
        val queued = OutboxItem(id = "queued", text = "À reprendre", queuedAt = 3L)

        val state = oldState.switchToConversation(
            conversationId = "selected-id",
            serverSessionId = "selected-session",
            messages = listOf(selectedMessage),
            input = "selected draft",
            queued = listOf(queued),
        )

        assertEquals("selected-id", state.conversationId)
        assertEquals("selected-session", state.serverSessionId)
        assertEquals(listOf(selectedMessage), state.messages)
        assertEquals("selected draft", state.input)
        assertEquals(listOf(queued), state.queued)
        assertEphemeralStateCleared(state)
    }

    @Test
    fun `une nouvelle conversation ne conserve aucun contenu de l ancienne`() {
        val state = oldState.switchToConversation(
            conversationId = "new-id",
            serverSessionId = null,
            messages = emptyList(),
            input = "",
            queued = emptyList(),
        )

        assertEquals("new-id", state.conversationId)
        assertNull(state.serverSessionId)
        assertTrue(state.messages.isEmpty())
        assertTrue(state.queued.isEmpty())
        assertTrue(state.input.isEmpty())
        assertTrue(state.isEmpty)
        assertEphemeralStateCleared(state)
    }

    private fun assertEphemeralStateCleared(state: ChatUiState) {
        assertNull(state.error)
        assertFalse(state.canRetry)
        assertFalse(state.isConnectionProblem)
        assertTrue(state.followUps.isEmpty())
        assertTrue(state.slashItems.isEmpty())
        assertFalse(state.searchOpen)
        assertTrue(state.searchQuery.isEmpty())
        assertTrue(state.searchHits.isEmpty())
        assertEquals(0, state.searchCurrent)
    }
}
