package com.onyx.app.data.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lecture du format brut des tâches `hermes serve`, utilisé par les planifications de bots. */
class ServeCronApiTest {
    @Test
    fun `une routine brute du dashboard est normalisee`() {
        val payload = Json.parseToJsonElement(
            """
            {"jobs":[{
              "id":"a1",
              "name":"[bot:scout] What to wear",
              "prompt":"Check the weather",
              "schedule_display":"0 7 * * 1-5",
              "deliver":"bot-chat:scout",
              "profile":"scout",
              "enabled":false,
              "state":"scheduled",
              "next_run_at":1700000000,
              "last_run_at":1699990000,
              "last_status":"error"
            }]}
            """.trimIndent(),
        )

        val job = parseCronJobs(payload).single()

        assertEquals("[bot:scout] What to wear", job.title)
        assertEquals("What to wear", job.untaggedTitle)
        assertEquals("What to wear", job.displayTitle)
        assertEquals("0 7 * * 1-5", job.schedule)
        assertEquals("bot-chat:scout", job.deliverTo)
        assertEquals("scout", job.botName)
        assertEquals("paused", job.state)
        assertTrue(job.paused)
        assertTrue(job.isBotRoutine)
        assertEquals(1_700_000_000_000L, job.nextRunAt)
        assertEquals(1_699_990_000_000L, job.lastRunAt)
        assertEquals("error", job.lastStatus)
    }

    @Test
    fun `le profil est conserve dans la query des actions`() {
        assertEquals(mapOf("profile" to "scout"), cronProfileQuery("scout"))
        assertEquals(
            mapOf("limit" to "20", "profile" to "scout"),
            cronProfileQuery("scout", mapOf("limit" to "20")),
        )
        assertEquals(emptyMap<String, String>(), cronProfileQuery(null))
    }
}
