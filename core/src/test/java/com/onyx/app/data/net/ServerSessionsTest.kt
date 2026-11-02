package com.onyx.app.data.net

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les conversations du **serveur** vues par le registre local.
 *
 * Le défaut observé : le volet des conversations ne montrait que ce que l'app avait **écrit sur
 * ce téléphone**. Les sessions du serveur — 53 d'entre elles, dont celles de la CLI et des
 * autres appareils — restaient invisibles, et le volet paraissait vide alors que le serveur
 * répondait parfaitement.
 *
 * Ces tests tiennent la règle : une session du serveur devient une conversation **distante**
 * (une projection, pas une copie), jamais deux fois, et l'identifiant local est stable.
 */
class ServerSessionsTest {

    private fun server(id: String = "srv-1") = ServerConfig(
        id = id,
        name = "Onyx",
        baseUrl = "https://example.test",
    )

    private fun summary(
        id: String,
        title: String? = "Un titre",
        startedAt: Double? = 1_700_000_000.0,
        lastActive: Double? = null,
    ) = SessionSummary(
        id = id,
        title = title,
        startedAt = startedAt,
        lastActive = lastActive,
    )

    @Test
    fun `une session du serveur devient une conversation distante`() {
        val made = ServerSessions.conversation(server(), summary("s1"))

        assertEquals("s1", made.serverSessionId)
        assertEquals("srv-1", made.serverId)
        assertTrue("la conversation vient du serveur", made.remote)
        assertEquals("Un titre", made.title)
    }

    @Test
    fun `une session deja connue n est pas dupliquee`() {
        val known = listOf(
            Conversation(
                id = "local-1",
                createdAt = 0L,
                updatedAt = 0L,
                serverId = "srv-1",
                serverSessionId = "s1",
            ),
        )

        val made = ServerSessions.conversations(server(), listOf(summary("s1"), summary("s2")), known)

        assertEquals(1, made.size)
        assertEquals("s2", made.first().serverSessionId)
    }

    @Test
    fun `les conversations distantes sont triees de la plus recente a la plus ancienne`() {
        val summaries = listOf(
            summary("vieux", startedAt = 1_000.0),
            summary("recent", startedAt = 9_000.0),
            summary("milieu", startedAt = 5_000.0),
        )

        val made = ServerSessions.conversations(server(), summaries, emptyList())

        assertEquals(listOf("recent", "milieu", "vieux"), made.map { it.serverSessionId })
    }

    @Test
    fun `l identifiant local est stable et propre`() {
        val first = ServerSessions.localId("srv-1", "abc")
        val second = ServerSessions.localId("srv-1", "abc")

        assertEquals(first, second)
        assertTrue("l identifiant ne doit pas contenir d espace : $first", !first.contains(' '))
    }

    @Test
    fun `une session sans titre garde le titre par defaut`() {
        val made = ServerSessions.conversation(server(), summary("s1", title = null))

        assertEquals(Conversation.DefaultTitle, made.title)
    }

    @Test
    fun `une conversation locale n est pas distante`() {
        val local = Conversation(id = "c", createdAt = 0L, updatedAt = 0L)

        assertFalse(local.remote)
    }
}
