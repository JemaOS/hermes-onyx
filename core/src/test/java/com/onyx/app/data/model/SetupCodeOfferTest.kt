package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCodeOfferTest {
    @Test
    fun `un lien Onyx dans le texte devient une offre a confirmer`() {
        val offer = SetupCodeOffer.fromText(
            "Voici le lien : onyx://connect?name=Maison&dashboard=https%3A%2F%2Fhermes.example%2F&user=alice&use=dashboard&v=1",
        )

        assertNotNull(offer)
        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("Maison", offer?.code?.name)
        assertEquals("https://hermes.example/", offer?.code?.dashboardURL)
        assertEquals(Transport.HERMES_SERVE, offer?.code?.transport)
    }

    @Test
    fun `un QR qui ne contient pas de lien Onyx nest pas une offre`() {
        assertNull(SetupCodeOffer.fromText("https://example.com/onyx://connect"))
        assertNull(SetupCodeOffer.fromText("bonjour"))
    }

    @Test
    fun `un lien Onyx dune version future reste une offre avec erreur visible`() {
        val offer = SetupCodeOffer.fromText("onyx://connect?dashboard=https%3A%2F%2Fhermes.example&v=99")

        assertNotNull(offer)
        assertNull(offer?.code)
        assertEquals(SetupCodeParseError.NEWER_VERSION, offer?.error)
        assertTrue(offer?.message.orEmpty().contains("newer version"))
    }

    @Test
    fun `le lien universel lit le fragment et non la requete envoyee au site`() {
        val offer = SetupCodeOffer.fromText(
            "https://onyx.example.org/connect?ignored=yes#api=https%3A%2F%2Fgateway.example&key=a%2Bb&use=api",
        )

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("https://gateway.example", offer?.code?.apiURL)
        assertEquals("a+b", offer?.code?.apiKey)
        assertEquals(Transport.HERMES_SESSIONS, offer?.code?.transport)
        assertNull(SetupCodeOffer.fromText("https://onyx.example.org/connect"))
    }

    @Test
    fun `une adresse non HTTP est refusee avant toute installation`() {
        val offer = SetupCodeOffer.fromText("onyx://connect?dashboard=ftp%3A%2F%2Fhermes.example")

        assertEquals(SetupCodeParseError.BAD_ADDRESS, offer?.error)
        assertNull(offer?.code)
        assertTrue(offer?.message.orEmpty().contains("ftp://hermes.example"))
    }

    @Test
    fun `un code sans adresse ou trop long explique son refus`() {
        assertEquals(
            SetupCodeParseError.NO_ADDRESS,
            SetupCodeOffer.fromText("onyx://connect?name=Maison")?.error,
        )
        val longLink = "onyx://connect?name=" + "x".repeat(2_600) +
            "&dashboard=https%3A%2F%2Fhermes.example"
        assertEquals(SetupCodeParseError.TOO_LONG, SetupCodeOffer.fromText(longLink)?.error)
    }

    @Test
    fun `le lien partage conserve espaces plus et transport au scan`() {
        val original = SetupCode(
            name = "Maison + été",
            apiURL = "https://gateway.example",
            apiKey = "a+b c",
            use = Transport.HERMES_SESSIONS,
        )

        val offer = SetupCodeOffer.fromText(original.link())

        assertEquals(original.name, offer?.code?.name)
        assertEquals(original.apiKey, offer?.code?.apiKey)
        assertEquals(Transport.HERMES_SESSIONS, offer?.code?.transport)
    }

    @Test
    fun `un lien diffuse sous le premier nom de l app reste accepte`() {
        val app = SetupCodeOffer.fromText(
            "${SetupCode.LEGACY_SCHEME}://connect?dashboard=https%3A%2F%2Fhermes.example&use=dashboard&v=1",
        )
        assertEquals(SetupCodeParseError.NONE, app?.error)
        assertEquals("https://hermes.example", app?.code?.dashboardURL)

        val web = SetupCodeOffer.fromText(
            "https://${SetupCode.LEGACY_WEB_HOST}/connect#api=https%3A%2F%2Fgateway.example&use=api&v=1",
        )
        assertEquals(SetupCodeParseError.NONE, web?.error)
        assertEquals("https://gateway.example", web?.code?.apiURL)
    }

    @Test
    fun `le lien partage porte le schema actuel`() {
        assertTrue(SetupCode(apiURL = "https://gateway.example").link().startsWith("onyx://connect?"))
    }

    @Test
    fun `les secrets de profil Access et les en-tetes restent dans le code`() {
        val offer = SetupCodeOffer.fromText(
            "onyx://connect?api=https%3A%2F%2Fgateway.example&profile=work&profile-key=profile-secret" +
                "&access-id=client-id&access-secret=access-secret&header=X-Tenant%3A%20alpha" +
                "&header=X-Token%3A%20beta&use=api",
        )

        assertEquals("profile-secret", offer?.code?.profileKey)
        assertEquals("client-id", offer?.code?.accessID)
        assertEquals("access-secret", offer?.code?.accessSecret)
        assertEquals(listOf("X-Tenant", "X-Token"), offer?.code?.headers?.map { it.name })
        assertEquals(listOf("alpha", "beta"), offer?.code?.headers?.map { it.value })
        assertTrue(offer?.code?.hasSecrets == true)
    }
}
