package com.onyx.app.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * **La ligne que le serveur rend vraiment se décode.**
 *
 * Copiée mot pour mot de la réponse du gateway (`GET api/sessions`) telle que la sonde de l'app
 * l'a affichée : l'import recevait cette ligne et n'en tirait **rien** — la lecture répondait 200,
 * le décodeur rendait `null`, et la liste restait vide sans une erreur.
 */
class SessionRowDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun row(text: String): JsonObject = json.parseToJsonElement(text) as JsonObject

    @Test
    fun `la ligne reelle du registre se decode`() {
        val summary = sessionSummaryFromRow(
            row(
                """{"id":"api-9ff07d177ac60f52","source":"api_server","user_id":"api",
                   "model":"deepseek/deepseek-v4.1-flash-1017","title":"Continue previous task",
                   "started_at":1793844644.792967,"ended_at":null,"end_reason":null,
                   "message_count":1310,"tool_call_count":752,"input_tokens":1488560,
                   "output_tokens":5203,"cwd":"/home/john/Bureau/onyx"}""",
            ),
        )
        assertNotNull("la ligne doit se décoder", summary)
        assertEquals("api-9ff07d177ac60f52", summary?.id)
        assertEquals("Continue previous task", summary?.title)
        assertEquals(1310, summary?.messageCount)
        assertEquals(1488560, summary?.inputTokens)
    }

    @Test
    fun `un horodatage flottant ne fait pas perdre la ligne`() {
        val summary = sessionSummaryFromRow(row("""{"id":"api-1","started_at":1793844644.792967}"""))
        assertNotNull(summary)
        assertEquals(1793844644.792967, summary?.startedAt ?: 0.0, 0.0001)
    }

    @Test
    fun `une ligne sans identifiant est la seule a etre rejetee`() {
        assertEquals(null, sessionSummaryFromRow(row("""{"title":"sans identifiant"}""")))
    }
}
