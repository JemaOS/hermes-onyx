package com.onyx.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que les onglets d'une conversation gardent **sur le téléphone** : l'onglet choisi, la barre
 * masquée, le retour sur une carte, et ce qui a été vu du flux.
 *
 * Les **listes locales Idées et Objectifs** ont été supprimées (demande du propriétaire) : leurs
 * règles ne sont plus testées ici, parce que le magasin ne les porte plus.
 *
 * Le magasin est éprouvé sur un support en mémoire : c'est la même classe, la même sérialisation,
 * et un second magasin posé sur le même support simule une relance de l'app.
 */
class ChatPanelStoreTest {

    private class MemoryText : TextStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private val text = MemoryText()
    private val store = ChatPanelStore(text)

    @Test
    fun `l'onglet est garde par conversation`() {
        assertNull(store.selectedTab("conv-a"))
        store.setSelectedTab("conv-a", "Feed")
        store.setSelectedTab("conv-b", "Files")
        assertEquals("Feed", store.selectedTab("conv-a"))
        assertEquals("Files", store.selectedTab("conv-b"))
        // Une autre conversation n'hérite de rien.
        assertNull(store.selectedTab("conv-c"))
        // Et l'onglet survit à une relance : un magasin neuf sur le même support.
        val after = ChatPanelStore(text)
        assertEquals("Feed", after.selectedTab("conv-a"))
        // Le changer remplace, ne s'empile pas.
        store.setSelectedTab("conv-a", "Chat")
        assertEquals("Chat", store.selectedTab("conv-a"))
    }

    @Test
    fun `le retour d'une carte est garde par conversation et par carte`() {
        assertTrue(store.feedbackFor("conv-a").isEmpty())
        assertEquals(mapOf("m1" to "up"), store.setFeedback("conv-a", "m1", "up"))
        store.setFeedback("conv-a", "m2", "down")
        store.setFeedback("conv-b", "m1", "down")
        assertEquals("up", store.feedback("conv-a", "m1"))
        assertEquals("down", store.feedback("conv-a", "m2"))
        assertEquals("down", store.feedback("conv-b", "m1"))
        // Le même pouce repose : il s'efface, comme un interrupteur.
        assertEquals(mapOf("m2" to "down"), store.setFeedback("conv-a", "m1", "up"))
        assertNull(store.feedback("conv-a", "m1"))
        // L'autre pouce remplace : un seul choix par carte.
        assertEquals("up", store.setFeedback("conv-a", "m2", "up")["m2"])
        // Rien ne fuit d'une conversation à l'autre.
        assertEquals(setOf("m1"), store.feedbackFor("conv-b").keys)
    }

    @Test
    fun `ce qui a ete vu se marque et ne se perd pas`() {
        assertTrue(store.seenCards("conv-a").isEmpty())
        assertEquals(setOf("m1", "m2"), store.markSeen("conv-a", listOf("m1", "m2")))
        assertEquals(setOf("m1", "m2"), ChatPanelStore(text).seenCards("conv-a"))
        // Marquer deux fois n'ajoute rien, et une carte neuve s'ajoute aux anciennes.
        assertEquals(setOf("m1", "m2", "m3"), store.markSeen("conv-a", listOf("m2", "m3")))
        assertTrue(store.seenCards("conv-b").isEmpty())
    }

    @Test
    fun `le document survit a un contenu abime et se remet a zero au besoin`() {
        // Un document illisible ne fait pas tomber l'écran : on repart de rien.
        text.put("panels", "{ce n'est pas du json")
        val fresh = ChatPanelStore(text)
        assertNull(fresh.selectedTab("conv-a"))
        assertTrue(fresh.feedbackFor("conv-a").isEmpty())
        assertTrue(fresh.seenCards("conv-a").isEmpty())
        // Et il reste utilisable.
        fresh.setSelectedTab("conv-a", "Feed")
        assertEquals("Feed", fresh.selectedTab("conv-a"))
        // « Effacer tout » ne laisse rien.
        fresh.clearAll()
        assertNull(fresh.selectedTab("conv-a"))
    }
}
