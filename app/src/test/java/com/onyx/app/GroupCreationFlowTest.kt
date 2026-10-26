package com.onyx.app

import com.onyx.app.data.local.GroupRoomStore
import com.onyx.app.data.local.TextStore
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupRoomDraft
import com.onyx.app.data.net.TransportError
import com.onyx.app.ui.bots.createGroupRoom
import com.onyx.app.ui.bots.groupTitle
import com.onyx.app.ui.conversations.GroupCreation
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « New Group Chat » de bout en bout: la salle est **gardée** quand la connexion n'a pas de
 * Dashboard (et se retrouve après une relance), **ouverte sur le serveur** quand elle en a un, et
 * un échec se dit sans laisser de salle à moitié faite.
 *
 * Le même nom ne fait jamais deux salles — ni ici, ni sur le serveur.
 */
class GroupCreationFlowTest {

    private class MemoryText : TextStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private val disk = MemoryText()
    private val store = GroupRoomStore(disk)
    private val bots = listOf(
        BotProfile(name = "default", isDefault = true),
        BotProfile(name = "scout", title = "Scout"),
        BotProfile(name = "quill", title = "Quill"),
        BotProfile(name = "kilo", title = "Kilo"),
    )

    /** Une connexion sans Dashboard: la salle ne peut vivre que sur ce téléphone. */
    private val noDashboard = ServerConfig(id = "s")

    /** Une connexion dont le Dashboard est configuré: il tient la salle lui-même. */
    private val dashboard = ServerConfig(
        id = "s",
        serveBaseUrl = "https://dash.test",
        serveUsername = "u",
        servePassword = "p",
    )

    private suspend fun create(
        name: String,
        members: Set<String> = setOf("default", "scout"),
        config: ServerConfig = noDashboard,
        known: List<GroupRoom> = emptyList(),
        server: (suspend (GroupRoomDraft) -> GroupRoom?)? = null,
    ) = createGroupRoom(
        config = config,
        bots = bots,
        known = known,
        store = store,
        serverKey = "server-a",
        name = name,
        members = members,
        mainName = "Onyx",
        server = server,
    )

    @Test
    fun `sans Dashboard la salle est gardee et se retrouve apres une relance`() = runBlocking {
        val made = create("Weekend hike") as GroupCreation.Made

        assertEquals("Weekend hike", made.room.name)
        assertTrue("la salle est gardée ici", made.room.local)
        assertEquals(listOf("Onyx", "Scout"), made.room.members.map { it.label() })
        assertEquals(listOf("default", "scout"), made.room.members.map { it.name })

        // Une relance: un magasin neuf sur le même disque.
        assertEquals(listOf("Weekend hike"), GroupRoomStore(disk).rooms("server-a").map { it.name })
    }

    @Test
    fun `sans nom, les membres nomment la salle`() = runBlocking {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Onyx, Scout, and Quill", groupTitle("   ", listOf(bots[0], bots[1], bots[2]), "Onyx"))

            val made = create("", members = setOf("default", "scout", "quill")) as GroupCreation.Made
            assertEquals("Onyx, Scout, and Quill", made.room.name)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `le meme nom deux fois ne fait qu une salle`() = runBlocking {
        val first = create("Weekend hike") as GroupCreation.Made
        val second = create("  WEEKEND HIKE ") as GroupCreation.Made

        assertEquals("c'est la même salle", first.room.id, second.room.id)
        assertEquals(1, GroupRoomStore(disk).rooms("server-a").size)
    }

    @Test
    fun `un serveur qui tient la salle ne la garde pas ici`() = runBlocking {
        var asked: GroupRoomDraft? = null
        val made = create(
            "Weekend hike",
            config = dashboard,
            server = { draft ->
                asked = draft
                GroupRoom(id = draft.id, name = draft.name, members = draft.members)
            },
        ) as GroupCreation.Made

        assertEquals("Weekend hike", made.room.name)
        assertFalse("la salle vit sur le serveur", made.room.local)
        assertEquals("le nom et les membres partent au serveur", "Weekend hike", asked?.name)
        assertEquals(listOf("default", "scout"), asked?.members?.map { it.profile })
        assertTrue("rien n'est gardé ici", GroupRoomStore(disk).rooms("server-a").isEmpty())
    }

    @Test
    fun `un serveur en echec le dit et ne garde rien`() = runBlocking {
        val made = create(
            "Weekend hike",
            config = dashboard,
            server = { throw TransportError.Rpc(-32000, "room worker is not running") },
        )

        val problem = (made as GroupCreation.Failed).problem
        assertTrue("le refus se lit tel quel", problem.contains("room worker is not running"))
        assertTrue("aucune salle gardée à moitié", GroupRoomStore(disk).rooms("server-a").isEmpty())
    }

    @Test
    fun `un serveur qui ne dit rien de la salle n en a pas fait`() = runBlocking {
        val made = create("Weekend hike", config = dashboard, server = { null })

        assertTrue(made is GroupCreation.Failed)
        assertTrue(GroupRoomStore(disk).rooms("server-a").isEmpty())
    }

    @Test
    fun `un nom deja pris revient au lieu d en ouvrir une seconde`() = runBlocking {
        val already = GroupRoom(id = "onyx-old", name = "Garage")

        val made = create(
            "Garage",
            config = dashboard,
            known = listOf(already),
            server = { throw AssertionError("le serveur ne doit pas être appelé pour un nom déjà pris") },
        ) as GroupCreation.Made

        assertEquals("onyx-old", made.room.id)
        assertTrue("rien de neuf n'est gardé ici non plus", GroupRoomStore(disk).rooms("server-a").isEmpty())
    }

    @Test
    fun `moins de deux bots ne fait pas de salle`() = runBlocking {
        val made = create("Solo", members = setOf("default"))

        assertTrue(made is GroupCreation.Failed)
        assertTrue(GroupRoomStore(disk).rooms("server-a").isEmpty())
    }
}
