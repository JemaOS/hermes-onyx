package com.onyx.app.data.model.serve

import com.onyx.app.data.net.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Une salle que l'on demande: son identifiant, son nom et ses membres, exactement le `wire` que
 * `groups.create` prend (`GroupMember.wire` du modèle), et la règle qui empêche deux fois le
 * même nom de faire deux salles.
 */
class GroupRoomDraftTest {

    private val bots = listOf(
        BotProfile(name = "default", isDefault = true),
        BotProfile(name = "scout", title = "Scout"),
    )

    @Test
    fun `une salle demandee porte ses membres comme groups create les prend`() {
        val draft = GroupRoomDraft.of(name = "Weekend hike", bots = bots, mainName = "Onyx", id = "onyx-x", now = 42L)

        assertEquals("onyx-x", draft.id)
        assertEquals("Weekend hike", draft.name)
        assertEquals(42L, draft.createdAtMillis)
        assertEquals(listOf("default", "scout"), draft.members.map { it.profile })
        // Le membre: son id dans la salle, son profil, sa poignée, son nom de salle.
        assertEquals(setOf("member_id", "profile", "handle", "display_name"), draft.members.first().wire().keys)
        assertEquals("default", draft.members.first().wire().str("member_id"))
        assertEquals("hermes", draft.members.first().wire().str("handle"))
        assertEquals("Onyx", draft.members.first().wire().str("display_name"))
        assertEquals("hermes", draft.members.first().handle)
        assertEquals("Onyx", draft.members.first().label)
        assertEquals("Scout", draft.members.last().label)
    }

    @Test
    fun `un membre sans nom de salle ne porte pas de display_name`() {
        val bare = GroupMember(id = "scout", profile = "scout", handle = "scout")
        assertEquals(setOf("member_id", "profile", "handle"), bare.wire().keys)
    }

    @Test
    fun `l identifiant d une salle est unique et se reconnait`() {
        val first = GroupRoomDraft.newID()
        assertTrue("l'identifiant que la référence tire", first.startsWith("onyx-"))
        assertNotEquals(first, GroupRoomDraft.newID())
    }

    @Test
    fun `le meme nom ne trouve qu une seule salle`() {
        val rooms = listOf(
            GroupRoom(id = "a", name = "Garage"),
            GroupRoom(id = "b", name = "Trips"),
        )

        assertEquals("a", GroupRoom.named(rooms, "  garage ")?.id)
        assertEquals("b", GroupRoom.named(rooms, "Trips")?.id)
        assertNull(GroupRoom.named(rooms, "Atelier"))
        assertNull("un nom vide ne trouve rien", GroupRoom.named(rooms, "   "))
    }

    @Test
    fun `deux a six membres`() {
        assertEquals(2, GroupRoom.MIN_MEMBERS)
        assertEquals(6, GroupRoom.MAX_MEMBERS)
    }
}
