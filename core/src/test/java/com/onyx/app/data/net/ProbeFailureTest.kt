package com.onyx.app.data.net

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le message d'un échec de sonde nomme l'adresse tentée et montre ce que le serveur a répondu.**
 *
 * Le défaut observé : le serveur du propriétaire répondait `HTTP 401` avec un corps explicite
 * (« clé d'installation absente ou invalide »), et l'application affichait « Adresse introuvable.
 * Vérifiez l'adresse dans les réglages. » — elle envoyait corriger une adresse **qui était juste**.
 * Ce fichier verrouille les deux moitiés du correctif : l'adresse réellement tentée, et le corps du
 * serveur repris **verbatim**.
 */
class ProbeFailureTest {

    private fun file(path: String): File =
        listOf(File(path), File("core/$path")).firstOrNull { it.exists() }
            ?: error("introuvable : $path")

    /** Le corps exact que le serveur a renvoyé au propriétaire. */
    private val unauthorized =
        "{\"error\":\"unauthorized\",\"detail\":\"clé d'installation absente ou invalide\",\"reason\":\"missing_or_invalid\"}"

    private val gateway = "https://amp-withdrawal-mary-van.trycloudflare.com"

    @Test
    fun `l echec nomme l adresse REELLEMENT tentee`() {
        val message = ProbeFailure.message(gateway, "Address not found.")

        assertTrue("l'adresse testée doit être nommée", message.contains(gateway))
        assertTrue("la raison doit suivre", message.contains("Address not found."))
        assertEquals("$gateway — Address not found.", message)
    }

    @Test
    fun `le corps du serveur est repris VERBATIM`() {
        val message = ProbeFailure.message(gateway, "HTTP 401.", unauthorized)

        assertTrue("le corps du serveur doit apparaître tel quel", message.contains(unauthorized))
        assertTrue("le détail utile est lisible", message.contains("clé d'installation absente ou invalide"))
        // Le corps vient APRÈS l'adresse et la raison : on lit d'abord où l'on a frappé.
        assertTrue(message.indexOf(gateway) < message.indexOf("\"error\""))
    }

    @Test
    fun `un corps vide ne laisse pas de separateur qui traine`() {
        assertEquals("$gateway — HTTP 401.", ProbeFailure.message(gateway, "HTTP 401."))
        assertEquals("HTTP 401.", ProbeFailure.message("", "HTTP 401."))
        assertEquals("HTTP 401. — corps", ProbeFailure.message("  ", "HTTP 401.", "corps"))
    }

    @Test
    fun `le corps est ramene sur une ligne et borne`() {
        assertEquals("a b c", ProbeFailure.body("  a\n\tb   c  "))
        assertEquals(300, ProbeFailure.body("x".repeat(4000)).length)
        assertEquals("", ProbeFailure.body("   \n  "))
    }

    @Test
    fun `les deux sondes nomment l adresse tentee et montrent le corps`() {
        // L'API Hermes : l'adresse du transport retenu, puis le corps de la réponse.
        val chat = file("src/main/java/com/onyx/app/data/net/ChatCompletionsClient.kt").readText()
        assertTrue("la sonde API passe par ProbeFailure", chat.contains("ProbeFailure.message("))
        assertTrue("elle nomme l'adresse tentée", chat.contains("address = config.apiBaseUrl"))
        assertTrue("elle passe le corps du serveur", chat.contains("rawBody = body"))

        // Le Dashboard : son hôte, et le corps de sa réponse.
        val tester = file("src/main/java/com/onyx/app/data/net/ConnectionTester.kt").readText()
        assertTrue("la sonde Dashboard passe par ProbeFailure", tester.contains("ProbeFailure.message("))
        assertTrue("elle nomme l'hôte tenté", tester.contains("address = base.host"))
        assertTrue("elle lit le corps AVANT de conclure", tester.contains("rawBody = refusal"))

        // Et le générique seul ne revient pas : « vérifiez l'adresse » sans l'adresse est le défaut.
        assertFalse(chat.contains("response.code, detail)"))
    }
}
