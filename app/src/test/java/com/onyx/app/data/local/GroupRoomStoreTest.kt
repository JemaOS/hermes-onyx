package com.onyx.app.data.local

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoomDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les salles gardées sur ce téléphone: écrites à la création, retrouvées après une relance, et une
 * seule par nom.
 *
 * « Une relance » = un magasin neuf sur le même disque: [MemoryText] joue les préférences
 * d'Android, qui ne disparaissent pas quand l'application se ferme.
 */
class GroupRoomStoreTest {

    private class MemoryText : TextStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private val disk = MemoryText()

    private fun draft(
        name: String,
        id: String = "onyx-1",
        members: List<BotProfile> = listOf(
            BotProfile(name = "default", isDefault = true),
            BotProfile(name = "scout", title = "Scout"),
        ),
    ) = GroupRoomDraft.of(
        name = name,
        bots = members,
        mainName = "Onyx",
        id = id,
        now = 1_761_324_000_000L,
    )

    @Test
    fun `une salle gardee se retrouve apres une relance`() {
        GroupRoomStore(disk).save("server-a", draft("Weekend hike"))

        val room = GroupRoomStore(disk).rooms("server-a").single()

        assertEquals("onyx-1", room.id)
        assertEquals("Weekend hike", room.name)
        assertTrue("la salle est gardée ici", room.local)
        assertEquals(1_761_324_000_000L, room.updatedAtMillis)
        assertEquals(listOf("default", "scout"), room.members.map { it.profile })
        assertEquals("hermes", room.members.first().handle)
        assertEquals(listOf("Onyx", "Scout"), room.members.map { it.label })
    }

    @Test
    fun `le meme nom ne garde qu une salle`() {
        val store = GroupRoomStore(disk)
        val first = store.save("server-a", draft("Weekend hike", id = "onyx-1"))
        val again = store.save("server-a", draft("  weekend hike ", id = "onyx-2"))

        assertEquals("onyx-1", first.id)
        assertEquals("la salle déjà gardée revient telle quelle", "onyx-1", again.id)
        assertEquals(1, store.rooms("server-a").size)
        assertEquals("et sur le disque aussi", 1, GroupRoomStore(disk).rooms("server-a").size)
    }

    @Test
    fun `les salles de deux connexions ne se melangent pas`() {
        val store = GroupRoomStore(disk)
        store.save("server-a", draft("Garage", id = "a"))
        store.save("server-b", draft("Atelier", id = "b"))

        assertEquals(listOf("Garage"), store.rooms("server-a").map { it.name })
        assertEquals(listOf("Atelier"), store.rooms("server-b").map { it.name })
        assertTrue("une connexion sans salle n'en montre aucune", store.rooms("server-c").isEmpty())
    }

    @Test
    fun `une salle retiree s en va`() {
        val store = GroupRoomStore(disk)
        store.save("server-a", draft("Garage", id = "a"))
        store.save("server-a", draft("Atelier", id = "b"))

        store.remove("server-a", "a")

        assertEquals(listOf("Atelier"), GroupRoomStore(disk).rooms("server-a").map { it.name })
    }

    @Test
    fun `erase everything ne laisse aucune salle`() {
        val store = GroupRoomStore(disk)
        store.save("server-a", draft("Garage"))

        store.clearAll()

        assertTrue(store.rooms("server-a").isEmpty())
        assertTrue(GroupRoomStore(disk).rooms("server-a").isEmpty())
    }

    @Test
    fun `un disque vide ou illisible ne rend aucune salle`() {
        assertTrue(GroupRoomStore(disk).rooms("server-a").isEmpty())

        val broken = MemoryText().apply { put("rooms", "pas du JSON") }
        assertTrue(GroupRoomStore(broken).rooms("server-a").isEmpty())
    }
}
