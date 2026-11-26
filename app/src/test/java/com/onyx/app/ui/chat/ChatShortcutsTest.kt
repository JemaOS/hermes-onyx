package com.onyx.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les raccourcis clavier du chat (`ContentView:540` pour référence).
 *
 * La table est une fonction pure, donc la vérifier ne demande ni clavier ni émulateur : seule
 * la traduction `Key` → [ChatKey] vit dans l'écran.
 */
class ChatShortcutsTest {
    @Test
    fun `les raccourcis de commande couvrent ceux d référence`() {
        assertEquals(ChatShortcut.NEW_CONVERSATION, chatShortcutFor(ChatKey.N, ctrl = true))
        assertEquals(ChatShortcut.FIND, chatShortcutFor(ChatKey.K, ctrl = true))
        assertEquals(ChatShortcut.FIND, chatShortcutFor(ChatKey.F, ctrl = true))
        assertEquals(ChatShortcut.SEND, chatShortcutFor(ChatKey.ENTER, ctrl = true))
        assertEquals(ChatShortcut.STOP, chatShortcutFor(ChatKey.PERIOD, ctrl = true))
        assertEquals(ChatShortcut.FOCUS_COMPOSER, chatShortcutFor(ChatKey.L, ctrl = true))
        assertEquals(ChatShortcut.EXPORT, chatShortcutFor(ChatKey.E, ctrl = true))
    }

    @Test
    fun `echap ferme sans touche de commande`() {
        assertEquals(ChatShortcut.CLOSE, chatShortcutFor(ChatKey.ESCAPE, ctrl = false))
        assertEquals(ChatShortcut.CLOSE, chatShortcutFor(ChatKey.ESCAPE, ctrl = true))
    }

    @Test
    fun `sans touche de commande rien dautre nagit`() {
        assertNull(chatShortcutFor(ChatKey.N, ctrl = false))
        assertNull(chatShortcutFor(ChatKey.K, ctrl = false))
        assertNull(chatShortcutFor(ChatKey.ENTER, ctrl = false))
        assertNull(chatShortcutFor(ChatKey.PERIOD, ctrl = false))
    }

    @Test
    fun `une touche inconnue ne fait rien meme avec commande`() {
        assertNull(chatShortcutFor(ChatKey.OTHER, ctrl = true))
        assertNull(chatShortcutFor(ChatKey.OTHER, ctrl = false))
    }

    @Test
    fun `envoyer attend un brouillon et un tour au repos`() {
        assertTrue(allows(ChatShortcut.SEND, hasDraft = true))
        assertFalse(allows(ChatShortcut.SEND, hasDraft = false))
        assertFalse(allows(ChatShortcut.SEND, hasDraft = true, isStreaming = true))
    }

    @Test
    fun `stop nexiste que pendant une reponse`() {
        assertTrue(allows(ChatShortcut.STOP, isStreaming = true))
        assertFalse(allows(ChatShortcut.STOP, isStreaming = false))
    }

    @Test
    fun `rechercher et exporter exigent des messages`() {
        assertTrue(allows(ChatShortcut.FIND, hasMessages = true))
        assertFalse(allows(ChatShortcut.FIND, hasMessages = false))
        assertTrue(allows(ChatShortcut.EXPORT, hasMessages = true))
        assertFalse(allows(ChatShortcut.EXPORT, hasMessages = false))
    }

    @Test
    fun `fermer ne fait quelque chose que si quelque chose est ouvert`() {
        assertTrue(allows(ChatShortcut.CLOSE, searchOpen = true))
        assertFalse(allows(ChatShortcut.CLOSE, searchOpen = false))
    }

    @Test
    fun `une nouvelle conversation reste toujours possible`() {
        assertTrue(allows(ChatShortcut.NEW_CONVERSATION, isStreaming = true))
        assertTrue(allows(ChatShortcut.NEW_CONVERSATION))
    }

    private fun allows(
        shortcut: ChatShortcut,
        isStreaming: Boolean = false,
        hasDraft: Boolean = false,
        hasMessages: Boolean = false,
        searchOpen: Boolean = false,
    ) = allowsChatShortcut(
        shortcut = shortcut,
        isStreaming = isStreaming,
        hasDraft = hasDraft,
        hasMessages = hasMessages,
        searchOpen = searchOpen,
    )
}
