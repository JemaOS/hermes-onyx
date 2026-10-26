package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `Conversation.customName` décide si l'en-tête du chat montre le nom donné par « Renommer » ou
 * la première question — le comportement de `Conversation.headerTitle`.
 */
class ConversationCustomNameTest {
    private fun user(text: String) = ChatMessage(id = "u", role = Role.USER, text = text, createdAt = 1L)
    private fun assistant(text: String) = ChatMessage(id = "a", role = Role.ASSISTANT, text = text, createdAt = 2L)

    @Test
    fun `une conversation neuve n'a pas de nom`() {
        assertNull(Conversation.start(id = "c").customName())
    }

    @Test
    fun `le titre derive de la premiere question n'est pas un nom`() {
        val first = "Reply with exactly QA130-OK and nothing else."
        val conversation = Conversation.start(id = "c").withMessage(user(first), now = 2L)
            .withMessage(assistant("QA130-OK"), now = 3L)
        assertEquals(Conversation.titleFrom(first), conversation.title)
        assertNull(conversation.customName())
    }

    @Test
    fun `un titre choisi par Renommer est le nom de l'en-tete`() {
        val conversation = Conversation(
            id = "c", title = "Projet Onyx", createdAt = 1L, updatedAt = 2L,
            messages = listOf(user("Bonjour"), assistant("Salut")),
        )
        assertEquals("Projet Onyx", conversation.customName())
    }

    @Test
    fun `une nouvelle conversation renommee montre aussi son nom`() {
        val conversation = Conversation(
            id = "c", title = "Brouillon", createdAt = 1L, updatedAt = 2L, messages = emptyList(),
        )
        assertEquals("Brouillon", conversation.customName())
    }
}
