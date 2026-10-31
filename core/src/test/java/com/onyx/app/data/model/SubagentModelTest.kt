package com.onyx.app.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les sous-agents : lecture des trames du gateway, du roster, et des arguments d'un
 * `delegate_task`.
 *
 * Les charges de test sont celles que le gateway émet vraiment (contrat
 * `tui_gateway/contracts/events.py`, `_subagent_payload`) : c'est un rejeu des vraies frames, pas
 * une forme inventée pour l'occasion.
 */
class SubagentModelTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `les trames subagent du gateway se lisent comme le modele`() {
        val start = json(
            """{"goal":"research pricing","task_count":3,"task_index":1,"subagent_id":"sa-1",""" +
                """"child_session_id":"c1","depth":0,"tool_count":0,"model":"gpt-4.1"}""",
        )
        val update = SubagentUpdate.parse("subagent.start", start)!!
        assertEquals("sa-1", update.id)
        assertEquals("research pricing", update.goal)
        assertEquals(1, update.taskIndex)
        assertEquals(3, update.taskCount)
        assertEquals("c1", update.childSessionId)
        assertEquals("gpt-4.1", update.model)
        assertEquals(SubagentUpdate.Phase.Started, update.phase)

        val done = json(
            """{"goal":"research pricing","subagent_id":"sa-1","status":"success",""" +
                """"summary":"Three tiers.","duration_seconds":12.5,"tool_count":7}""",
        )
        val finished = SubagentUpdate.parse("subagent.complete", done)!!
        assertEquals(
            SubagentUpdate.Phase.Completed(succeeded = true, summary = "Three tiers.", duration = 12.5),
            finished.phase,
        )
        assertEquals(7, finished.toolCount)

        val tool = SubagentUpdate.parse(
            "subagent.tool",
            json("""{"subagent_id":"sa-1","tool_name":"read_file","text":"contract.pdf"}"""),
        )!!
        assertEquals(SubagentUpdate.Phase.Tool("read_file"), tool.phase)

        val progress = SubagentUpdate.parse(
            "subagent.progress",
            json("""{"subagent_id":"sa-1","text":"7 plans"}"""),
        )!!
        assertEquals(SubagentUpdate.Phase.Progress("7 plans"), progress.phase)
    }

    @Test
    fun `une trame sans identifiant est ignoree plutot qu inventee`() {
        // Sans `subagent_id`, rien ne pourrait être piloté ni arrêté : pas de ligne du tout.
        assertNull(SubagentUpdate.parse("subagent.start", json("""{"goal":"orphan"}""")))
        assertNull(SubagentUpdate.parse("subagent.unknown", json("""{"subagent_id":"sa-1"}""")))
    }

    @Test
    fun `un echec est lu comme tel`() {
        val failed = SubagentUpdate.parse(
            "subagent.complete",
            json("""{"subagent_id":"sa-2","status":"timeout","text":"gave up"}"""),
        )!!
        assertEquals(
            SubagentUpdate.Phase.Completed(succeeded = false, summary = "gave up", duration = null),
            failed.phase,
        )
    }

    @Test
    fun `les buts d un delegate_task se lisent dans ses arguments`() {
        // Miroir de `synthesizesGoalsFromDelegateArgs` (EchoTests/SubagentTests:22) : c'est le
        // SEUL vestige d'un sous-agent sur l'API de conversation.
        val rows = SubagentUpdate.delegatedGoals("""{"tasks":[{"goal":"a"},{"goal":"b"}]}""", "r1")
        assertEquals(listOf("a", "b"), rows.map { it.goal })
        assertEquals(listOf("delegate:r1:0", "delegate:r1:1"), rows.map { it.id })
        assertTrue(rows.all { it.taskCount == 2 })
        assertTrue(rows.all { it.phase == SubagentUpdate.Phase.Dispatched })

        val single = SubagentUpdate.delegatedGoals("""{"goal":"only one"}""", "r2")
        assertEquals(listOf("only one"), single.map { it.goal })
        assertEquals(1, single.first().taskCount)

        // Un appel de contrôle (`action: list/steer/stop`) ne délègue rien.
        assertTrue(SubagentUpdate.delegatedGoals("""{"action":"list"}""", "r3").isEmpty())
        assertTrue(SubagentUpdate.delegatedGoals(null, "r3").isEmpty())
        assertTrue(SubagentUpdate.delegatedGoals("not json", "r3").isEmpty())
    }

    @Test
    fun `le roster du gateway donne le depart et le dernier outil`() {
        val row = SubagentUpdate.fromRoster(
            json(
                """{"subagent_id":"sa-9","parent_id":"rt-1","depth":1,"goal":"tidy the notes",""" +
                    """"model":"gpt-4.1","started_at":1700000000.0,"status":"running","tool_count":5,""" +
                    """"last_tool":"patch","accepting_steer":true,"child_session_id":"c9"}""",
            ),
        )!!
        assertEquals("sa-9", row.id)
        assertEquals(1, row.depth)
        assertEquals(5, row.toolCount)
        assertEquals("patch", row.lastTool)
        assertTrue(row.acceptingSteer)
        assertEquals(1_700_000_000_000L, row.startedAt)
        assertEquals(SubagentStatus.RUNNING, row.status)
        // Un roster sans identifiant ne donne pas de ligne.
        assertNull(SubagentUpdate.fromRoster(json("""{"goal":"no id"}""")))
    }

    @Test
    fun `une ligne se cree puis se met a jour sur place`() {
        val now = 1_700_000_000_000L
        var row = SubagentActivity(id = "sa-1", startedAt = now)
        row = row.applying(
            SubagentUpdate(
                id = "sa-1", goal = "price it", taskIndex = 0, taskCount = 2, toolCount = 0,
                childSessionId = "c1", phase = SubagentUpdate.Phase.Started,
            ),
            now,
        )
        assertEquals(SubagentStatus.RUNNING, row.status)
        assertEquals("price it", row.goal)
        assertEquals("c1", row.childSessionId)
        assertEquals(0.0, row.elapsedSeconds(now), 0.001)
        assertEquals(42.0, row.elapsedSeconds(now + 42_000), 0.001)

        row = row.applying(
            SubagentUpdate(id = "sa-1", toolCount = 7, phase = SubagentUpdate.Phase.Tool("read_file")),
            now,
        )
        assertEquals(7, row.toolCount)
        assertEquals("read_file", row.lastTool)

        row = row.applying(
            SubagentUpdate(
                id = "sa-1", toolCount = 7,
                phase = SubagentUpdate.Phase.Completed(true, "done", 12.5),
            ),
            now,
        )
        assertEquals(SubagentStatus.COMPLETED, row.status)
        assertEquals("done", row.summary)
        // La durée du serveur prime sur l'horloge de l'app.
        assertEquals(12.5, row.elapsedSeconds(now + 900_000), 0.001)
    }

    @Test
    fun `une ligne close ne se rouvre pas`() {
        val now = 1L
        val closed = SubagentActivity(id = "sa-1", status = SubagentStatus.COMPLETED, startedAt = now)
        val reopened = closed.applying(
            SubagentUpdate(id = "sa-1", phase = SubagentUpdate.Phase.Tool("web_search")),
            now,
        )
        // Un `subagent.start`/`.tool` rejoué ne doit pas faire repasser « terminé » à « en cours » :
        // la liste mentirait sur une tâche finie.
        assertEquals(SubagentStatus.COMPLETED, reopened.status)
    }

    @Test
    fun `une ligne lancee a l aveugle reste lancee jusqua sa fin`() {
        val now = 1L
        val dispatched = SubagentActivity(
            id = "delegate:r1:0",
            goal = "sweep the competitors",
            status = SubagentStatus.DISPATCHED,
            startedAt = now,
        )
        assertTrue(dispatched.isLive)
        // Une progression ne la fait pas passer pour un enfant suivi de près.
        val progressed = dispatched.applying(
            SubagentUpdate(id = dispatched.id, phase = SubagentUpdate.Phase.Progress("working")),
            now,
        )
        assertEquals(SubagentStatus.DISPATCHED, progressed.status)
        // Sans session d'enfant, il n'y a rien à ouvrir : l'écran le dit.
        assertFalse(dispatched.hasTranscript)
    }

    @Test
    fun `les types du contrat sont ceux du gateway`() {
        assertTrue(SubagentUpdate.isSubagentEvent("subagent.spawn_requested"))
        assertTrue(SubagentUpdate.isSubagentEvent("subagent.start"))
        assertTrue(SubagentUpdate.isSubagentEvent("subagent.progress"))
        assertTrue(SubagentUpdate.isSubagentEvent("subagent.thinking"))
        assertTrue(SubagentUpdate.isSubagentEvent("subagent.tool"))
        assertTrue(SubagentUpdate.isSubagentEvent("subagent.complete"))
        assertFalse(SubagentUpdate.isSubagentEvent("tool.start"))
        assertFalse(SubagentUpdate.isSubagentEvent("subagent"))
    }
}
