package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.data.net.ServerProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les règles du volet : le ton d'une ligne, les filtres, la recherche, les puces de projets et
 * l'âge. Pures, donc mesurables — et c'est ici que se tient la **séparation** : les bots ne sont pas
 * des conversations.
 */
class ChatSidebarModelTest {

    private fun conversation(
        id: String,
        title: String = "Chat $id",
        updatedAt: Long = 1_000_000L,
        pinned: Boolean = false,
        sessionId: String? = null,
        messages: List<ChatMessage> = emptyList(),
    ) = Conversation(
        id = id,
        title = title,
        createdAt = 0L,
        updatedAt = updatedAt,
        serverSessionId = sessionId,
        pinned = pinned,
        messages = messages,
    )

    private fun reply(atMillis: Long) = ChatMessage(id = "m$atMillis", role = Role.ASSISTANT, text = "done", createdAt = atMillis)
    private fun question(atMillis: Long) = ChatMessage(id = "q$atMillis", role = Role.USER, text = "hi", createdAt = atMillis)

    @Test
    fun `le ton suit le serveur d abord, la lecture ensuite`() {
        val chat = conversation("a", messages = listOf(question(1_000), reply(10_000)))

        // Un tour en cours prime sur tout : on ne lit rien avant qu'il finisse.
        assertEquals(SidebarRowTone.RUNNING, sidebarToneOf(chat, ActivityKind.WORKING, seenMark = 50.0))
        // Une question de l'agent prime sur le simple non-lu : elle attend un geste.
        assertEquals(SidebarRowTone.WAITING, sidebarToneOf(chat, ActivityKind.NEEDS_YOU, seenMark = 50.0))
        // Rien de signalé : c'est la réponse de l'agent, non vue, qui parle.
        assertEquals(SidebarRowTone.UNREAD, sidebarToneOf(chat, null, seenMark = 5.0))
        // Vue : la ligne est au repos.
        assertEquals(SidebarRowTone.IDLE, sidebarToneOf(chat, null, seenMark = 20.0))
        // Aucune marque connue : rien n'est « non lu » par défaut.
        assertEquals(SidebarRowTone.IDLE, sidebarToneOf(chat, null, seenMark = null))
    }

    @Test
    fun `la marque de lecture se compare en secondes, pas en millisecondes`() {
        // Le défaut d'origine : la date du message (millisecondes) comparée à la marque (secondes)
        // disait toute ligne non lue. Ici, une réponse à 10 000 ms vue à 20 s ne l'est pas.
        val chat = conversation("a", messages = listOf(reply(10_000)))
        assertFalse(sidebarHasUnreadReply(chat, 20.0))
        assertTrue(sidebarHasUnreadReply(chat, 5.0))
    }

    @Test
    fun `marquee non lue a la main, la marque zero rend le dernier mot neuf`() {
        val chat = conversation("a", messages = listOf(question(1_000), reply(2_000)))
        assertTrue(sidebarHasUnreadReply(chat, 0.0))
        // Une conversation où l'agent n'a jamais parlé n'est jamais « non lue ».
        assertFalse(sidebarHasUnreadReply(conversation("b", messages = listOf(question(1_000))), 0.0))
    }

    @Test
    fun `les filtres retiennent ce qu ils annoncent`() {
        assertTrue(sidebarFilterKeeps(SidebarFilter.ALL, SidebarRowTone.IDLE))
        assertTrue(sidebarFilterKeeps(SidebarFilter.RUNNING, SidebarRowTone.RUNNING))
        assertFalse(sidebarFilterKeeps(SidebarFilter.RUNNING, SidebarRowTone.UNREAD))
        assertTrue(sidebarFilterKeeps(SidebarFilter.WAITING, SidebarRowTone.WAITING))
        assertFalse(sidebarFilterKeeps(SidebarFilter.WAITING, SidebarRowTone.RUNNING))
    }

    @Test
    fun `les compteurs des filtres sont ceux des lignes`() {
        val counts = sidebarFilterCounts(
            listOf(
                SidebarRowTone.RUNNING,
                SidebarRowTone.WAITING,
                SidebarRowTone.UNREAD,
                SidebarRowTone.IDLE,
                SidebarRowTone.WAITING,
            ),
        )
        assertEquals(5, counts[SidebarFilter.ALL])
        assertEquals(1, counts[SidebarFilter.RUNNING])
        assertEquals(2, counts[SidebarFilter.WAITING])
    }

    @Test
    fun `la liste se filtre, se trie, et met les epinglees en tete`() {
        val running = conversation("run", sessionId = "s1", updatedAt = 3_000_000L)
        val pinned = conversation("pin", pinned = true, updatedAt = 1_000_000L)
        val plain = conversation("plain", updatedAt = 2_000_000L)

        val rows = sidebarRows(
            conversations = listOf(plain, pinned, running),
            liveKinds = mapOf("s1" to ActivityKind.WORKING),
            seenMarks = emptyMap(),
            now = 3_600.0,
        )
        assertEquals(listOf("pin", "run", "plain"), rows.map { it.id })

        val onlyRunning = sidebarRows(
            conversations = listOf(plain, pinned, running),
            liveKinds = mapOf("s1" to ActivityKind.WORKING),
            seenMarks = emptyMap(),
            now = 3_600.0,
            filter = SidebarFilter.RUNNING,
        )
        assertEquals(listOf("run"), onlyRunning.map { it.id })
    }

    @Test
    fun `la recherche regarde le titre, puis les messages quand la portee le dit`() {
        val chat = conversation("a", title = "Deploy notes", messages = listOf(question(1_000), reply(2_000)))

        assertTrue(sidebarMatches(chat, "", includeMessages = false))
        assertTrue(sidebarMatches(chat, "deploy", includeMessages = false))
        assertFalse(sidebarMatches(chat, "hi", includeMessages = false))
        assertTrue("le message de l'utilisateur est dans la portée", sidebarMatches(chat, "hi", includeMessages = true))
        assertFalse(sidebarMatches(chat, "nowhere", includeMessages = true))
    }

    @Test
    fun `les puces de projets portent le compte, et « All projects » n en porte pas`() {
        val projects = listOf(
            ServerProject(id = "__no_project__", label = "Home", path = null, isNoProject = true, sessionCount = 99, lastActive = 5_000L),
            ServerProject(id = "p1", label = "Onyx", path = "/srv/onyx", isAuto = false, sessionCount = 4, lastActive = 9_000L),
            ServerProject(id = "p2", label = "Trouvé", path = "/srv/x", isAuto = true, sessionCount = 0, lastActive = 1L),
        )
        val chips = sidebarProjectChips(projects)

        assertEquals(ALL_PROJECTS_ID, chips.first().id)
        assertNull("« All projects » ne porte pas de nombre", chips.first().count)
        assertTrue(chips.any { it.label == "Onyx" && it.count == 4 })
        assertFalse("un projet trouvé sans conversation n'est pas listé", chips.any { it.id == "p2" })
        // Le **groupe par défaut** : son nom (celui du serveur), et son compte — qui est le nombre de
        // conversations qu'il liste. Sa puce suit « All projects » et précède les projets.
        assertEquals("__no_project__", chips[1].id)
        assertEquals("Home", chips[1].label)
        assertEquals(99, chips[1].count)
    }

    @Test
    fun `l age d une ligne est court, comme le volet le montre`() {
        assertEquals("", sidebarAgeLabel(0.0))
        assertEquals("", sidebarAgeLabel(-5.0))
        assertEquals("now", sidebarAgeLabel(30.0))
        assertEquals("1m", sidebarAgeLabel(60.0))
        assertEquals("58m", sidebarAgeLabel(3_500.0))
        assertEquals("1h", sidebarAgeLabel(3_600.0))
        assertEquals("1d", sidebarAgeLabel(86_400.0))
        assertEquals("6d", sidebarAgeLabel(6.5 * 86_400.0))
        assertEquals("1w", sidebarAgeLabel(7.0 * 86_400.0))
        assertEquals("2w", sidebarAgeLabel(15.0 * 86_400.0))
    }

    @Test
    fun `la seconde ligne ne dit que ce qui est su`() {
        val chat = conversation("a")
        assertEquals("Working", sidebarSubtitle(chat, ActivityKind.WORKING, "Onyx"))
        assertEquals("Waiting for you", sidebarSubtitle(chat, ActivityKind.NEEDS_YOU, "Onyx"))
        assertNull("rien à dire : la ligne reste à une seule ligne", sidebarSubtitle(chat, null, "Onyx"))
    }

    @Test
    fun `les cartes de bots passent a une colonne quand la place ou la police manque`() {
        assertEquals(2, botGridColumns(360f, 1f))
        assertEquals("une police à 1,3× ne laisse pas deux colonnes lisibles", 1, botGridColumns(360f, 1.3f))
        assertEquals("un volet étroit passe à une colonne", 1, botGridColumns(280f, 1f))
        assertEquals(2, botGridColumns(660f, 1f))
    }

    @Test
    fun `les salons se rangent du plus recent au plus ancien`() {
        val rows = sidebarRoomRows(
            rooms = listOf(
                SidebarRoomSource(id = "old", name = "Ops", members = listOf("a", "b"), updatedAt = 10.0),
                SidebarRoomSource(id = "new", name = "Scribe", members = listOf("c"), updatedAt = 90.0, ageSeconds = 3_600.0),
            ),
            unreadRoomIds = setOf("new"),
        )
        assertEquals(listOf("new", "old"), rows.map { it.id })
        assertEquals("1h", rows.first().ageLabel)
        assertTrue(rows.first().unread)
        assertNull(rows.last().ageLabel)
    }
}
