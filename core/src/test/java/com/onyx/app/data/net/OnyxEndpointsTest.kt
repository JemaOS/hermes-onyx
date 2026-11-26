package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le manifeste `GET /api/onyx/endpoints` : lecture pure, tolérance au 404, et l'assemblage avec
 * `/v1/models` qui fait tenir le mode « Simple ».
 */
class OnyxEndpointsTest {

    private val contract = """
        {"version":1,"server":"https://gateway.example","model":"hermes-agent",
         "voices":"https://voices.example:8880","bridge":"https://pc.example",
         "bridgeToken":"0123456789abcdefghij","language":"fr",
         "capabilities":["chat","bots","groups","voices","computer"]}
    """.trimIndent()

    @Test
    fun `le manifeste du contrat est lu entier`() {
        val endpoints = OnyxEndpoints.parse(contract)

        assertEquals(1, endpoints?.version)
        assertEquals("https://gateway.example", endpoints?.server)
        assertEquals("hermes-agent", endpoints?.model)
        assertEquals("https://voices.example:8880", endpoints?.voices)
        assertEquals("https://pc.example", endpoints?.bridge)
        assertEquals("0123456789abcdefghij", endpoints?.bridgeToken)
        assertEquals("fr", endpoints?.language)
        assertEquals(listOf("chat", "bots", "groups", "voices", "computer"), endpoints?.capabilities)
        assertTrue(endpoints?.isUsable == true)
    }

    @Test
    fun `un manifeste illisible nest pas une panne`() {
        assertNull(OnyxEndpoints.parse(""))
        assertNull(OnyxEndpoints.parse("pas du json"))
        assertNull(OnyxEndpoints.parse("<html>404</html>"))
    }

    @Test
    fun `une adresse non http est ignoree sans casser le reste`() {
        val endpoints = OnyxEndpoints.parse(
            """{"model":"m","voices":"ftp://bad","bridge":"https://pc.example","language":"en"}""",
        )

        assertEquals("m", endpoints?.model)
        assertEquals("", endpoints?.voices)
        assertEquals("https://pc.example", endpoints?.bridge)
        assertEquals("en", endpoints?.language)
    }

    @Test
    fun `le manifeste fait foi et v1 models prend le relais pour le modele`() {
        val endpoints = OnyxEndpoints.parse(contract)
        val probe = ConnectionProbe(true, "Connected.", 12L, listOf("from-models"))

        val merged = DiscoveredSetup.merge(endpoints, probe, probeReachable = true)

        assertEquals("hermes-agent", merged.model)
        assertEquals("https://voices.example:8880", merged.voices)
        assertEquals("https://pc.example", merged.bridge)
        assertEquals("0123456789abcdefghij", merged.bridgeToken)
        assertEquals("fr", merged.language)
        assertTrue(merged.reachable)
        assertTrue(merged.hasExtras)
    }

    @Test
    fun `un serveur sans manifeste retombe sur v1 models et reste joignable`() {
        val probe = ConnectionProbe(true, "Connected.", 8L, listOf("m1", "m2"))

        val merged = DiscoveredSetup.merge(endpoints = null, probe = probe, probeReachable = probe.ok)

        assertEquals("m1", merged.model)
        assertEquals("", merged.voices)
        assertEquals("", merged.bridge)
        assertEquals("", merged.bridgeToken)
        assertEquals("", merged.language)
        assertEquals(listOf("m1", "m2"), merged.models)
        assertTrue("un serveur plus ancien reste joignable", merged.reachable)
        assertFalse("rien de plus à remplir, et ce n'est pas une erreur", merged.hasExtras)
    }

    @Test
    fun `un serveur muet sans manifeste nest pas joignable`() {
        val merged = DiscoveredSetup.merge(endpoints = null, probe = null, probeReachable = false)

        assertFalse(merged.reachable)
        assertFalse(merged.hasExtras)
        assertEquals("", merged.model)
    }

    @Test
    fun `un serveur qui annonce les bots annonce aussi le Dashboard`() {
        val merged = DiscoveredSetup.merge(OnyxEndpoints.parse(contract), null, probeReachable = true)

        assertTrue(merged.declaresDashboard)
        assertEquals(listOf("chat", "bots", "groups", "voices", "computer"), merged.capabilities)
    }

    @Test
    fun `un serveur sans manifeste n'annonce pas de Dashboard`() {
        val merged = DiscoveredSetup.merge(endpoints = null, probe = null, probeReachable = false)

        assertFalse(merged.declaresDashboard)
    }
}
