package com.onyx.app.data.net

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La fiche d'usage d'une session (`SessionUsageView`) tire ses chiffres du registre du gateway
 * (`GET api/sessions`), et ses formats sont ceux: 999, 8.2k, 1.20M, « — », « $0 », et
 * quatre décimales au-delà.
 */
class SessionUsageTest {

    @Test
    fun `les nombres se replient comme le compact d `() {
        assertEquals("0", SessionUsage.compact(0))
        assertEquals("999", SessionUsage.compact(999))
        assertEquals("8.2k", SessionUsage.compact(8_200))
        assertEquals("1.20M", SessionUsage.compact(1_200_000))
    }

    @Test
    fun `le prix suit money `() {
        assertNull(null as Double?)
        assertEquals("—", SessionUsage.money(null))
        assertEquals("$0", SessionUsage.money(0.0))
        assertEquals("$0.0123", SessionUsage.money(0.0123))
    }

    @Test
    fun `un compteur absent se lit zero`() {
        assertEquals("0", SessionUsage.count(null))
        assertEquals("12", SessionUsage.count(12))
    }

    @Test
    fun `une ligne de session se lit champ par champ`() {
        val row = Json.parseToJsonElement(
            """
            {"id":"s1","title":"Transcript scrolling","source":"cli","model":"claude","cwd":"/home/onyx/echo",
             "message_count":12,"tool_call_count":3,"api_call_count":5,
             "input_tokens":8200,"output_tokens":1100,"reasoning_tokens":40,
             "cache_read_tokens":600,"cache_write_tokens":20,
             "estimated_cost_usd":0.0123,"actual_cost_usd":0.01,"last_active":1773000000}
            """.trimIndent(),
        ) as JsonObject

        val summary = sessionSummaryFromRow(row)!!
        assertEquals("s1", summary.id)
        assertEquals(9300, summary.totalTokens)
        assertEquals(0.01, summary.cost!!, 0.0000001)
        assertEquals("CLI", summary.sourceLabel)
        assertEquals("Transcript scrolling", summary.displayTitle)
        assertEquals(1_773_000_000_000L, summary.lastActiveMillis)
        assertEquals(12, summary.messageCount)
        assertEquals(40, summary.reasoningTokens)
        assertEquals(600, summary.cacheReadTokens)
        assertEquals(20, summary.cacheWriteTokens)
        assertEquals("\u25B2 8.2k \u25BC 1.1k · $0.010", summary.usageLine)
    }

    @Test
    fun `un compteur rendu en flottant reste lisible`() {
        // `intOrNull` refuse « 12.0 »: sans la lecture tolérante, le compte disparaîtrait.
        val row = Json.parseToJsonElement("""{"id":"s2","message_count":12.0,"input_tokens":3.0}""") as JsonObject
        val summary = sessionSummaryFromRow(row)!!
        assertEquals(12, summary.messageCount)
        assertEquals(3, summary.inputTokens)
    }

    @Test
    fun `une session sans identifiant est ecartee`() {
        val row = Json.parseToJsonElement("""{"title":"sans id"}""") as JsonObject
        assertNull(sessionSummaryFromRow(row))
    }

    @Test
    fun `une conversation locale donne sa consommation, sans inventer le reste`() {
        val conversation = Conversation(
            id = "c1",
            createdAt = 0,
            updatedAt = 1000,
            serverSessionId = "s9",
            messages = listOf(
                ChatMessage(id = "m1", role = Role.USER, text = "hello", createdAt = 1),
                ChatMessage(
                    id = "m2",
                    role = Role.ASSISTANT,
                    text = "hi",
                    createdAt = 2,
                    usage = TokenUsage(input = 100, output = 20),
                ),
            ),
        )

        val summary = SessionSummary.of(conversation)
        assertEquals("s9", summary.id)
        assertEquals(100, summary.inputTokens)
        assertEquals(20, summary.outputTokens)
        assertEquals(120, summary.totalTokens)
        assertEquals(2, summary.messageCount)
        // Aucun outil dans ce fil: le compte est zéro, pas absent.
        assertEquals(0, summary.toolCallCount)
        // Le flux ne remonte ni le raisonnement ni le coût: ils restent absents, et la ligne
        // d'usage s'arrête alors aux jetons.
        assertNull(summary.reasoningTokens)
        assertNull(summary.cost)
        assertEquals("\u25B2 100 \u25BC 20", summary.usageLine)
    }

    @Test
    fun `les enveloppes de api sessions se lisent toutes les deux`() {
        val sessions = Json.parseToJsonElement("""{"sessions":[{"id":"a"},{"id":"b"}]}""") as JsonObject
        assertEquals(listOf("a", "b"), (sessions.array("sessions")).mapNotNull { it.asObject()?.firstString("id") })
        val data = Json.parseToJsonElement("""{"data":[{"id":"c"}]}""") as JsonObject
        assertEquals(listOf("c"), (data.array("data")).mapNotNull { it.asObject()?.firstString("id") })
    }

    @Test
    fun `un projet et ses voies se lisent comme `() {
        val row = Json.parseToJsonElement(
            """
            {"id":"/home/onyx/echo","label":"Echo","path":"/home/onyx/echo","is_home":false,"is_no_project":false,
             "session_count":2,"last_active":1773000000,
             "repos":[{"id":"r","label":"Echo","groups":[{"id":"lane1","label":"main","sessions":[
               {"id":"demo-1","title":"Transcript scrolling","source":"cli","last_active":1773000000},
               {"id":"demo-2","title":"Calendar MCP guards","source":"desktop"}]}]}]}
            """.trimIndent(),
        ) as JsonObject

        val project = serverProjectFromRow(row)!!
        assertEquals("/home/onyx/echo", project.id)
        assertEquals("Echo", project.label)
        assertTrue(project.canStart)
        assertEquals(2, project.sessionCount)
        assertEquals(1_773_000_000_000L, project.lastActive)
        assertEquals(1, project.lanes.size)
        val lane = project.lanes.first()
        assertEquals("main", lane.label)
        assertEquals("Echo", lane.repoLabel)
        assertEquals(listOf("demo-1", "demo-2"), lane.sessions.map { it.id })
    }

    @Test
    fun `le fourre-tout n est pas un dossier ou demarrer`() {
        val row = Json.parseToJsonElement("""{"id":"__no_project__","path":"/home","is_no_project":true}""") as JsonObject
        val project = serverProjectFromRow(row)!!
        assertEquals(ServerProject.NO_PROJECT_LABEL, project.label)
        assertNull(project.path)
        assertTrue(!project.canStart)
    }
}
