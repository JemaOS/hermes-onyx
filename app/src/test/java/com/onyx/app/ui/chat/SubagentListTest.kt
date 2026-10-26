package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.ui.chat.components.subagentDetail
import com.onyx.app.ui.chat.components.subagentGoal
import com.onyx.app.ui.chat.components.subagentListDetail
import com.onyx.app.ui.chat.components.subagentStateLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La liste des sous-agents: l'ordre, ce qu'une ligne dit, et la fusion du roster avec le fil.
 */
class SubagentListTest {

    private fun agent(
        id: String,
        status: SubagentStatus,
        startedAt: Long = 1_000L,
        toolCount: Int = 0,
        lastTool: String? = null,
        summary: String? = null,
        childSessionId: String? = null,
        durationSeconds: Double? = null,
    ) = SubagentActivity(
        id = id,
        goal = "task $id",
        status = status,
        startedAt = startedAt,
        toolCount = toolCount,
        lastTool = lastTool,
        summary = summary,
        childSessionId = childSessionId,
        durationSeconds = durationSeconds,
    )

    @Test
    fun `ce qui travaille passe avant ce qui est fini, le plus recent en tete`() {
        val rows = listOf(
            agent("old-done", SubagentStatus.COMPLETED, startedAt = 10),
            agent("old-live", SubagentStatus.RUNNING, startedAt = 20),
            agent("new-done", SubagentStatus.COMPLETED, startedAt = 40),
            agent("new-live", SubagentStatus.RUNNING, startedAt = 30),
        ).sortedWith(subagentOrder)

        assertEquals(listOf("new-live", "old-live", "new-done", "old-done"), rows.map { it.id })
    }

    @Test
    fun `une tache lancee a l aveugle reste en haut, avec les vivantes`() {
        val rows = listOf(
            agent("done", SubagentStatus.COMPLETED, startedAt = 99),
            agent("blind", SubagentStatus.DISPATCHED, startedAt = 5),
        ).sortedWith(subagentOrder)
        assertEquals(listOf("blind", "done"), rows.map { it.id })
    }

    @Test
    fun `le detail d une tache vivante dit ses outils et son dernier geste`() {
        val running = agent(
            "a", SubagentStatus.RUNNING, startedAt = 0, toolCount = 7, lastTool = "read_file",
        )
        assertEquals("7 tools · read_file", subagentDetail(running, now = 0))
        // Un seul outil se dit au singulier (la clé « 1 tool » existe au dictionnaire).
        assertEquals(
            "1 tool · web_search",
            subagentDetail(agent("b", SubagentStatus.RUNNING, toolCount = 1, lastTool = "web_search"), 0),
        )
        // Rien encore: « Working… », jamais une ligne vide.
        assertEquals("Working…", subagentDetail(agent("c", SubagentStatus.RUNNING), 0))
    }

    @Test
    fun `une tache lancee a l aveugle le dit au lieu d inventer un etat`() {
        val dispatched = agent("blind", SubagentStatus.DISPATCHED)
        assertEquals(
            "Running in the background; results arrive in a later turn",
            subagentDetail(dispatched, now = 0),
        )
        assertEquals("Dispatched", subagentStateLabel(SubagentStatus.DISPATCHED))
    }

    @Test
    fun `une tache finie dit sa duree, ses outils et son resume`() {
        val done = agent(
            "a", SubagentStatus.COMPLETED, startedAt = 0, toolCount = 4, lastTool = "web_search",
            summary = "Three tiers.", durationSeconds = 23.0,
        )
        assertEquals("4 tools · 23 s · Three tiers.", subagentDetail(done, now = 100_000))
        // Sans résumé ni outil, la durée seule suffit — jamais une ligne vide.
        assertEquals(
            "12 s",
            subagentDetail(agent("b", SubagentStatus.COMPLETED, durationSeconds = 12.0), now = 0),
        )
    }

    @Test
    fun `le but vide se remplace par le mot de repli localise`() {
        assertEquals("Delegated task", subagentGoal(SubagentActivity(id = "x")))
        assertEquals("price it", subagentGoal(SubagentActivity(id = "x", goal = "price it")))
    }

    @Test
    fun `la liste reunit le fil et le roster sans doubler une ligne`() {
        val fromThread = agent(
            "sa-1", SubagentStatus.RUNNING, startedAt = 5_000, toolCount = 3,
            childSessionId = "c1",
        )
        // Le roster ne connaît pas les trames du fil en cours: il complète ce qui manque.
        val rosterOnly = agent("sa-2", SubagentStatus.RUNNING, startedAt = 9_000, lastTool = "patch")
        val sameAsThread = agent("sa-1", SubagentStatus.RUNNING, startedAt = 4_000, lastTool = "grep")

        val state = ChatUiState(
            server = ServerConfig(id = "s", name = "x"),
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    role = Role.ASSISTANT,
                    createdAt = 0,
                    subagents = listOf(fromThread),
                ),
            ),
            subagentRoster = listOf(sameAsThread, rosterOnly),
        )

        val rows = state.subagents
        assertEquals(listOf("sa-1", "sa-2"), rows.map { it.id })
        // La ligne du fil garde sa date de départ et gagne le dernier outil venu du roster.
        assertEquals(5_000L, rows[0].startedAt)
        assertEquals(3, rows[0].toolCount)
        assertEquals("grep", rows[0].lastTool)
        // Une ligne que seul le roster connaît est reprise telle quelle.
        assertEquals("patch", rows[1].lastTool)
    }

    @Test
    fun `la liste met le temps ecoule en tete, et il avance`() {
        val running = agent("a", SubagentStatus.RUNNING, startedAt = 0, toolCount = 7, lastTool = "read_file")
        assertEquals("42 s · 7 tools · read_file", subagentListDetail(running, now = 42_000))
        assertEquals("0 s · 7 tools · read_file", subagentListDetail(running, now = 0))
        // Une tâche lancée à l'aveugle dit aussi depuis quand.
        assertEquals(
            "12 s · Running in the background; results arrive in a later turn",
            subagentListDetail(agent("b", SubagentStatus.DISPATCHED, startedAt = 0), now = 12_000),
        )
        // Une tâche finie garde la durée du serveur, et **une seule**: `subagentDetail` la porte
        // déjà, la préfixer la dirait deux fois (vu en capture).
        assertEquals(
            "4 tools · 23 s · Three tiers.",
            subagentListDetail(
                agent("c", SubagentStatus.COMPLETED, toolCount = 4, summary = "Three tiers.", durationSeconds = 23.0),
                now = 900_000,
            ),
        )
    }

    @Test
    fun `une conversation sans sous-agent n en montre aucun`() {
        val state = ChatUiState(server = ServerConfig(id = "s", name = "x"))
        assertTrue(state.subagents.isEmpty())
    }
}
