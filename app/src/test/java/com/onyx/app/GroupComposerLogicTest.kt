package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupComposerLogicTest {
    private val bots = listOf(
        BotProfile(name = "scout", title = "Scout"),
        BotProfile(name = "sage", title = "Sage"),
        BotProfile(name = "kilo", title = "Research"),
    )

    @Test
    fun `la dictee rejoint le brouillon sans ajouter d espace en trop`() {
        assertEquals("Bonjour", dictationJoined("", "  Bonjour \n"))
        assertEquals("Bonjour tout le monde", dictationJoined("Bonjour", "tout le monde"))
        assertEquals("Bonjour \ntout le monde", dictationJoined("Bonjour \n", "tout le monde"))
        assertEquals("Brouillon", dictationJoined("Brouillon", "  "))
    }

    @Test
    fun `les suggestions cherchent dans la poignee et le nom`() {
        assertEquals(listOf("kilo"), groupTagSuggestions("Dis @ki", bots).map { it.name })
        assertEquals(listOf("kilo"), groupTagSuggestions("Dis @res", bots).map { it.name })
        assertEquals(3, groupTagSuggestions("@", bots).size)
        assertEquals(0, groupTagSuggestions("sans mention", bots).size)
    }

    @Test
    fun `la completion utilise la poignee et preserve le blanc precedent`() {
        assertEquals("Dis à @kilo ", replaceTagToken("Dis à @ki", "kilo"))
        assertEquals("Bonjour\n@scout ", replaceTagToken("Bonjour\n@sc", "scout"))
        assertEquals("texte", replaceTagToken("texte", "kilo"))
    }

    // hasDraft: texte qui n'est pas que du blanc, ou au moins une pièce jointe.
    @Test
    fun `le champ a un brouillon avec du texte ou une piece jointe`() {
        assertFalse(groupComposerHasDraft("", 0))
        assertFalse(groupComposerHasDraft("   \n ", 0))
        assertTrue(groupComposerHasDraft("salut", 0))
        assertTrue(groupComposerHasDraft("", 1))
        assertTrue(groupComposerHasDraft("salut", 2))
    }

    // Le bouton rond suit exactement la cascade: Stop, Talk, sinon Send.
    @Test
    fun `le bouton rond arrete, parle ou envoie selon l etat`() {
        assertEquals(GroupComposerAction.STOP, groupComposerAction(hasDraft = false, isStreaming = true, canTalk = true))
        assertEquals(GroupComposerAction.STOP, groupComposerAction(hasDraft = false, isStreaming = true, canTalk = false))
        // Un brouillon pendant le tour: le groupe envoie (pas de file d'attente).
        assertEquals(GroupComposerAction.SEND, groupComposerAction(hasDraft = true, isStreaming = true, canTalk = true))
        // Champ vide, rien ne coule: Talk quand la scène vocale est offerte…
        assertEquals(GroupComposerAction.TALK, groupComposerAction(hasDraft = false, isStreaming = false, canTalk = true))
        // …et sinon Send, désactivé (comme `Send.disabled(!hasDraft)`).
        assertEquals(GroupComposerAction.SEND, groupComposerAction(hasDraft = false, isStreaming = false, canTalk = false))
        assertEquals(GroupComposerAction.SEND, groupComposerAction(hasDraft = true, isStreaming = false, canTalk = true))
    }

    @Test
    fun `l invite du champ dit la dictee quand elle ecoute`() {
        assertEquals("Listening…", groupComposerPlaceholder(dictating = true))
        assertEquals("Message the group", groupComposerPlaceholder(dictating = false))
    }
}
