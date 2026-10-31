package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le lien « tout en un » : serveur, clé, modèle, voix, pont (+ son jeton) et langue d'un seul
 * collage — et un ancien lien qui continue de marcher.
 */
class SetupCodeAllInOneTest {

    @Test
    fun `un lien tout en un porte voix pont et langue puis les rend au scan`() {
        val original = SetupCode(
            name = "Maison",
            apiURL = "https://gateway.example",
            apiKey = "install-key",
            model = "hermes-agent",
            use = Transport.HERMES_SESSIONS,
            voicesURL = "https://voices.example:8880",
            bridgeURL = "https://pc.example",
            bridgeToken = "0123456789abcdefghij",
            language = "fr",
        )

        val offer = SetupCodeOffer.fromText(original.link())

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        val code = offer?.code
        assertNotNull(code)
        assertEquals("https://gateway.example", code?.apiURL)
        assertEquals("install-key", code?.apiKey)
        assertEquals("hermes-agent", code?.model)
        assertEquals("https://voices.example:8880", code?.voicesURL)
        assertEquals("https://pc.example", code?.bridgeURL)
        assertEquals("0123456789abcdefghij", code?.bridgeToken)
        assertEquals("fr", code?.language)
        assertEquals(Transport.HERMES_SESSIONS, code?.transport)
        assertTrue("le récapitulatif doit savoir qu'il y a plus que le serveur", code?.hasExtras == true)
        assertTrue(code?.hasVoices == true)
        assertTrue(code?.hasBridge == true)
        assertTrue(code?.hasLanguage == true)
    }

    @Test
    fun `un ancien lien qui na que api et key marche toujours`() {
        val offer = SetupCodeOffer.fromText(
            "onyx-legacy://connect?api=https%3A%2F%2Fgateway.example&key=old-key&use=api&v=1",
        )

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("https://gateway.example", offer?.code?.apiURL)
        assertEquals("old-key", offer?.code?.apiKey)
        assertEquals("", offer?.code?.voicesURL)
        assertEquals("", offer?.code?.bridgeURL)
        assertEquals("", offer?.code?.bridgeToken)
        assertEquals("", offer?.code?.language)
        assertFalse(offer?.code?.hasExtras ?: true)
    }

    @Test
    fun `un ancien lien dashboard user et mot de passe marche toujours`() {
        val offer = SetupCodeOffer.fromText(
            "onyx-legacy://connect?dashboard=https%3A%2F%2Fhermes.example&user=alice&password=s3cret&use=dashboard",
        )

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("https://hermes.example", offer?.code?.dashboardURL)
        assertEquals("alice", offer?.code?.dashboardUser)
        assertEquals(Transport.HERMES_SERVE, offer?.code?.transport)
        assertFalse(offer?.code?.hasExtras ?: true)
    }

    @Test
    fun `une adresse de voix illisible ne tue pas le reste du lien`() {
        val offer = SetupCodeOffer.fromText(
            "onyx-legacy://connect?api=https%3A%2F%2Fgateway.example&key=k&voices=ftp%3A%2F%2Fbad" +
                "&bridge=https%3A%2F%2Fpc.example&lang=fr",
        )

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("", offer?.code?.voicesURL)
        assertEquals("https://pc.example", offer?.code?.bridgeURL)
        assertEquals("fr", offer?.code?.language)
    }

    @Test
    fun `le jeton du pont compte parmi les secrets du code`() {
        val withoutToken = SetupCode(apiURL = "https://gateway.example", apiKey = "k")
        val withToken = withoutToken.copy(bridgeToken = "0123456789abcdefghij")

        assertFalse(withoutToken.bridgeToken.isNotEmpty())
        assertTrue(withToken.hasSecrets)
        assertEquals("", withoutToken.bridgeToken)
    }

    @Test
    fun `un code sans adresse reste refuse`() {
        val offer = SetupCodeOffer.fromText("onyx-legacy://connect?voices=https%3A%2F%2Fvoices.example&lang=fr")
        assertNull(offer?.code)
        assertEquals(SetupCodeParseError.NO_ADDRESS, offer?.error)
    }

    @Test
    fun `le lien emis par le serveur onyx est lu lui aussi`() {
        // La forme exacte du `/connect` du serveur : `onyx://`, `server`, `bridgeToken`.
        val link = "onyx://connect?v=1&server=https%3A%2F%2Fgw.example&key=install-key" +
            "&model=hermes-agent&voices=https%3A%2F%2Fvoices.example" +
            "&bridge=https%3A%2F%2Fpc.example&bridgeToken=ABC12345&lang=fr" +
            "&capabilities=chat%2Cvoices"

        val offer = SetupCodeOffer.fromText("Le lien : $link")

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("https://gw.example", offer?.code?.apiURL)
        assertEquals("install-key", offer?.code?.apiKey)
        assertEquals("hermes-agent", offer?.code?.model)
        assertEquals("https://voices.example", offer?.code?.voicesURL)
        assertEquals("https://pc.example", offer?.code?.bridgeURL)
        assertEquals("ABC12345", offer?.code?.bridgeToken)
        assertEquals("fr", offer?.code?.language)
        assertEquals(Transport.HERMES_SESSIONS, offer?.code?.transport)
    }
}
