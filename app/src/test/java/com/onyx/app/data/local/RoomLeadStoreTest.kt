package com.onyx.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Le meneur d'une salle, gardé sur ce téléphone.**
 *
 * Le contrat est court : un meneur par salle, il **survit à une relance** (le magasin neuf sur le
 * même disque joue les préférences d'Android), « No lead » l'enlève pour de bon, et une salle sans
 * entrée n'a pas de meneur — jamais un mot vide qui se lirait comme un nom.
 */
class RoomLeadStoreTest {

    private class MemoryText : TextStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private val disk = MemoryText()

    @Test
    fun `le meneur choisi se retrouve apres une relance`() {
        RoomLeadStore(disk).set("onyx-1", "Coder")

        // Une relance : un magasin neuf sur le même disque.
        assertEquals("Coder", RoomLeadStore(disk).lead("onyx-1"))
    }

    @Test
    fun `chaque salle a son meneur`() {
        val store = RoomLeadStore(disk)
        store.set("onyx-1", "Coder")
        store.set("onyx-2", "Planner")

        assertEquals("Coder", store.lead("onyx-1"))
        assertEquals("Planner", store.lead("onyx-2"))
        assertNull(store.lead("onyx-3"))
    }

    @Test
    fun `no lead enleve le meneur, et le retrait survit aussi`() {
        val store = RoomLeadStore(disk)
        store.set("onyx-1", "Coder")
        store.set("onyx-1", null)

        assertNull(store.lead("onyx-1"))
        assertNull("le retrait est écrit, pas oublié", RoomLeadStore(disk).lead("onyx-1"))
    }

    @Test
    fun `un choix vide n est pas un meneur`() {
        val store = RoomLeadStore(disk)
        store.set("onyx-1", "Coder")
        store.set("onyx-1", "   ")

        assertNull(store.lead("onyx-1"))
    }

    @Test
    fun `erase everything n en garde aucun`() {
        val store = RoomLeadStore(disk)
        store.set("onyx-1", "Coder")
        store.set("onyx-2", "Planner")
        store.clearAll()

        assertNull(store.lead("onyx-1"))
        assertNull(store.lead("onyx-2"))
    }
}
