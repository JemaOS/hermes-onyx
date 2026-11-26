package com.onyx.app.calls

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le protocole de l'appel de l'agent, **tel que la référence le définit** — sans Android.
 *
 * Ce qui est éprouvé : la lecture d'une réponse `phone pending`, l'écart des appels périmés, la
 * décision de silence (heures de tranquillité) **avec l'heure où l'on pourra resonner**, et le
 * corps exact des trois commandes (`phone tools`, `phone pending`, `phone answer`).
 */
class AgentCallProtocolTest {

    private fun okReply(vararg requests: String): String =
        """{"ok":true,"requests":[${requests.joinToString(",")}],"now":1000}"""

    private fun request(
        id: String = "a".repeat(32),
        tool: String = "phone_call_me",
        reason: String = "Besoin d'un accord",
        details: String? = "Le disque est plein",
        session: String = "sess-1",
        expires: Long = 5000,
    ): String = buildString {
        append("{")
        append(""""id":"$id","tool":"$tool","session":"$session","expires":$expires,""")
        append(""""args":{"reason":"$reason"""")
        if (details != null) append(""","details":"$details"""")
        append("}}")
    }

    // ---- lecture de `phone pending` ----------------------------------------------------------

    @Test
    fun `une reponse lisible donne la demande avec sa raison et ses details`() {
        val requests = AgentCallProtocol.parsePending(okReply(request()), now = 1000)
        assertEquals(1, requests.size)
        val one = requests.single()
        assertEquals("a".repeat(32), one.id)
        assertEquals(AgentCallTool.CALL_ME, one.tool)
        assertEquals("Besoin d'un accord", one.reason)
        assertEquals("Le disque est plein", one.details)
        assertEquals("sess-1", one.session)
        assertEquals(5000, one.expires)
    }

    @Test
    fun `un appel peimre ne sonne pas`() {
        // expires 900 < now 1000 : l'agent n'attend plus.
        val requests = AgentCallProtocol.parsePending(okReply(request(expires = 900)), now = 1000)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `une reponse refusee ou illisible ne donne rien, sans lever`() {
        assertTrue(AgentCallProtocol.parsePending("""{"ok":false,"requests":[]}""").isEmpty())
        assertTrue(AgentCallProtocol.parsePending("pas du json").isEmpty())
        assertTrue(AgentCallProtocol.parsePending("").isEmpty())
        assertTrue(AgentCallProtocol.parsePending("""{"ok":true}""").isEmpty())
    }

    @Test
    fun `une demande sans raison est ignoree`() {
        val broken = """{"id":"${"b".repeat(32)}","tool":"phone_call_me","args":{}}"""
        assertTrue(AgentCallProtocol.parsePending(okReply(broken)).isEmpty())
    }

    // ---- la décision de silence --------------------------------------------------------------

    @Test
    fun `heures de tranquillite rendent quiet et l'heure ou resonner`() {
        val call = AgentCallProtocol.parsePending(okReply(request())).single()
        // 07:00 = 420 minutes.
        val answer = AgentCallProtocol.silentAnswer(call, quietNow = true, quietUntilMinute = 420)
        assertEquals(CallOutcome.QUIET, answer?.status)
        assertEquals("07:00", answer?.until)
    }

    @Test
    fun `hors heures de tranquillite on sonne, donc pas de reponse silencieuse`() {
        val call = AgentCallProtocol.parsePending(okReply(request())).single()
        assertNull(AgentCallProtocol.silentAnswer(call, quietNow = false, quietUntilMinute = 420))
    }

    @Test
    fun `un autre outil est occupe, il ne sonne pas`() {
        val other = AgentCallRequest(id = "c".repeat(32), tool = "phone_battery", reason = "r")
        assertEquals(CallOutcome.BUSY, AgentCallProtocol.silentAnswer(other, false, 0)?.status)
    }

    // ---- les trois commandes ------------------------------------------------------------------

    @Test
    fun `phone tools declare l'outil aupres du plugin`() {
        val body = Json.parseToJsonElement(
            AgentCallProtocol.toolsReport("dev", "sek", "Onyx", listOf(AgentCallTool.CALL_ME)),
        ).jsonObject
        assertEquals("dev", body["device_id"]?.jsonPrimitive?.content)
        assertEquals("sek", body["key"]?.jsonPrimitive?.content)
        assertEquals("Onyx", body["name"]?.jsonPrimitive?.content)
        val tools = body["tools"] as kotlinx.serialization.json.JsonArray
        assertEquals(listOf("phone_call_me"), tools.map { it.jsonPrimitive.content })
    }

    @Test
    fun `phone pending porte l'identite du telephone`() {
        val body = Json.parseToJsonElement(AgentCallProtocol.pendingQuery("dev", "sek")).jsonObject
        assertEquals("dev", body["device_id"]?.jsonPrimitive?.content)
        assertEquals("sek", body["key"]?.jsonPrimitive?.content)
    }

    @Test
    fun `phone answer rend le statut, et until quand il y en a un`() {
        val answered = Json.parseToJsonElement(
            AgentCallProtocol.answerBody("dev", "sek", "id-1", AgentCallAnswer(CallOutcome.ANSWERED)),
        ).jsonObject
        assertEquals("id-1", answered["id"]?.jsonPrimitive?.content)
        assertEquals("answered", answered["result"]?.jsonObject?.get("status")?.jsonPrimitive?.content)
        assertNull(answered["result"]?.jsonObject?.get("until"))

        val quiet = Json.parseToJsonElement(
            AgentCallProtocol.answerBody("dev", "sek", "id-2", AgentCallAnswer(CallOutcome.QUIET, until = "07:00")),
        ).jsonObject
        assertEquals("quiet", quiet["result"]?.jsonObject?.get("status")?.jsonPrimitive?.content)
        assertEquals("07:00", quiet["result"]?.jsonObject?.get("until")?.jsonPrimitive?.content)
    }

    @Test
    fun `phone answer d'echec porte l'erreur`() {
        val body = Json.parseToJsonElement(
            AgentCallProtocol.errorBody("dev", "sek", "id-3", "Le téléphone ne peut pas sonner."),
        ).jsonObject
        assertEquals("Le téléphone ne peut pas sonner.", body["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `un identifiant bien forme est trente-deux hexadecimales`() {
        assertTrue(AgentCallProtocol.isWellFormedId("0123456789abcdef0123456789abcdef"))
        assertFalse(AgentCallProtocol.isWellFormedId("ID"))
        assertFalse(AgentCallProtocol.isWellFormedId("z".repeat(32)))
    }
}
