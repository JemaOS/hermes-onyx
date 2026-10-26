package com.onyx.app.ui.conversations

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Tous vos serveurs dans une seule liste » (`AllServers`): les conversations de chaque serveur
 * réunies, chacune marquée du sien, triées comme , un serveur absent nommé plutôt qu'un
 * vide, et la clé composite qui distingue deux conversations de même identifiant d'un serveur à
 * l'autre — le piège que traite aussi.
 */
class AllServersTest {

    private fun conversation(
        id: String,
        serverId: String,
        updatedAt: Long,
        title: String = id,
        pinned: Boolean = false,
        archived: Boolean = false,
    ): Conversation = Conversation(
        id = id,
        title = title,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        serverId = serverId,
        pinned = pinned,
        archived = archived,
    )

    @Test
    fun `les notres ne portent pas d etiquette, celles des autres la portent`() {
        val cabin = ServerTag("srv-cabin", "Cabin")
        val rows = AllServers.merge(
            own = listOf(conversation("a", "default", 10)),
            ownServerId = "default",
            others = listOf(cabin to listOf(conversation("b", "srv-cabin", 20))),
        )
        assertEquals(listOf("b", "a"), rows.map { it.conversation.id })
        val mine = rows.first { it.conversation.id == "a" }
        val theirs = rows.first { it.conversation.id == "b" }
        assertFalse("la nôtre n'est jamais marquée", mine.remote)
        assertNull(mine.serverName)
        assertTrue("celle d'un autre serveur est marquée", theirs.remote)
        assertEquals("Cabin", theirs.serverName)
        assertEquals("srv-cabin", theirs.serverId)
    }

    @Test
    fun `le tri est celui , de la plus recente a la plus ancienne`() {
        val rows = AllServers.merge(
            own = listOf(conversation("a", "default", 100), conversation("c", "default", 300)),
            ownServerId = "default",
            others = listOf(ServerTag("srv-x", "Workshop") to listOf(conversation("b", "srv-x", 200))),
        )
        assertEquals(listOf(300L, 200L, 100L), rows.map { it.conversation.updatedAt })
        assertEquals(listOf("c", "b", "a"), rows.map { it.conversation.id })
    }

    @Test
    fun `une conversation deja listee ne l est qu une fois`() {
        // Le même Hermes enregistré deux fois (deux serveurs, la même session): une seule rangée.
        val rows = AllServers.merge(
            own = listOf(conversation("shared", "default", 10)),
            ownServerId = "default",
            others = listOf(
                ServerTag("srv-api", "Lab API") to listOf(conversation("shared", "srv-api", 10)),
                ServerTag("srv-dash", "Lab Dashboard") to listOf(conversation("other", "srv-dash", 5)),
            ),
        )
        assertEquals(listOf("shared", "other"), rows.map { it.conversation.id })
        assertFalse("la première gagne, la nôtre", rows.first().remote)
    }

    @Test
    fun `les autres serveurs sont ceux enregistres, jamais le serveur actif ni une conversation sans serveur`() {
        val conversations = listOf(
            conversation("mine", "default", 1),
            conversation("legacy", "", 2),
            conversation("cabin", "srv-cabin", 3),
            conversation("workshop", "srv-workshop", 4),
            conversation("gone", "srv-retire", 5),
        )
        val servers = listOf(ServerTag("default", "Onyx"), ServerTag("srv-cabin", "Cabin"), ServerTag("srv-workshop", "Workshop"))
        val groups = AllServers.others(conversations, "default", servers)
        assertEquals(listOf("Cabin", "Workshop"), groups.map { it.first.name })
        assertEquals(listOf("cabin"), groups[0].second.map { it.id })
        assertEquals(listOf("workshop"), groups[1].second.map { it.id })
        // Ni la notre, ni la sans-serveur, ni celle d'un serveur retire (absent de la liste).
        assertFalse(groups.any { (_, list) -> list.any { it.id == "mine" || it.id == "legacy" || it.id == "gone" } })
    }

    @Test
    fun `la cle est composite, deux serveurs donnent deux rangees distinctes pour un meme identifiant`() {
        val mine = AllServerRow(conversation("s1", "default", 1), "default")
        val theirs = AllServerRow(conversation("s1", "srv-cabin", 1), "srv-cabin", "Cabin")
        assertNotEquals("l'identifiant seul ne suffit pas", mine.key, theirs.key)
        assertEquals(AllServers.key("default", "s1"), mine.key)
        assertEquals(AllServers.key("srv-cabin", "s1"), theirs.key)
        // Le même serveur et le même identifiant donnent bien la même clé.
        assertEquals(mine.key, AllServerRow(conversation("s1", "default", 9), "default").key)
    }

    @Test
    fun `un serveur qui n a pas repondu est nomme, singulier et pluriel`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertNull("tous répondent, rien à dire", AllServers.unreachableLine(emptyList()))
            assertNull(AllServers.unreachableLine(listOf("  ")))
            assertEquals(
                "Cabin didn't answer, so its chats aren't here.",
                AllServers.unreachableLine(listOf("Cabin")),
            )
            assertEquals(
                "Cabin and Workshop didn't answer, so their chats aren't here.",
                AllServers.unreachableLine(listOf("Workshop", "Cabin")),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `la ligne des serveurs absents est dite en francais`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals(
                "Cabin n’a pas répondu, donc ses conversations ne sont pas ici.",
                AllServers.unreachableLine(listOf("Cabin")),
            )
            assertEquals(
                "Cabin et Workshop n’ont pas répondu, donc leurs conversations ne sont pas ici.",
                AllServers.unreachableLine(listOf("Cabin", "Workshop")),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `les libelles de la liste unifiee sont dits en francais`() {
        assertEquals("Tous les serveurs", OnyxLocalization.translate("All Servers", Locale.FRENCH))
        assertEquals("Serveur", OnyxLocalization.translate("Server", Locale.FRENCH))
        assertEquals("Ouvrir sur Cabin", OnyxLocalization.format("Open on %1\$s", "Cabin", locale = Locale.FRENCH))
        assertEquals(
            "· nouvelles conversations sur Onyx",
            OnyxLocalization.format("· new chats on %1\$s", "Onyx", locale = Locale.FRENCH),
        )
        // En anglais, les libellés restent ceux; les noms de serveurs ne se traduisent pas.
        assertEquals("All Servers", OnyxLocalization.translate("All Servers", Locale.ENGLISH))
        assertEquals("Open on Cabin", OnyxLocalization.format("Open on %1\$s", "Cabin", locale = Locale.ENGLISH))
    }

    @Test
    fun `une conversation locale porte le titre et le dernier mot pour l apercu`() {
        val message = ChatMessage(id = "m1", role = Role.ASSISTANT, text = "The sensor reads closed again.", createdAt = 5)
        val row = AllServerRow(conversation("s1", "srv-cabin", 5, title = "Garage door sensor").copy(messages = listOf(message)), "srv-cabin", "Workshop")
        assertEquals("Garage door sensor", row.conversation.title)
        assertEquals("The sensor reads closed again.", row.conversation.messages.last().text)
        assertTrue(row.remote)
    }
}
