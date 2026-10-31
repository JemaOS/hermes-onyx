package com.onyx.app.ui.setup

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ConnectionProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les sorties de l'écran de configuration, mesurées.
 *
 * Le défaut signalé : « Ignorer » et « Terminé » ne faisaient rien quand aucune adresse n'était
 * saisie — l'écran se réaffichait à l'identique. Les règles sont donc dans des fonctions pures, et
 * c'est ici qu'elles se vérifient.
 */
class SetupExitTest {

    private fun server(
        id: String,
        baseUrl: String = "",
        serveUrl: String = "",
        transport: Transport = Transport.CHAT_COMPLETIONS,
    ) = ServerConfig(id = id, baseUrl = baseUrl, serveBaseUrl = serveUrl, transport = transport)

    private fun probe(ok: Boolean, message: String) = ConnectionProbe(ok, message, 5L, emptyList())

    // --- « Ignorer » ---

    @Test
    fun `ignorer ne sauvegarde pas un brouillon vide`() {
        // Un brouillon vide enregistré écraserait le serveur actif : l'écran ne doit pas y toucher.
        assertFalse(skipSavesDraft(server("a")))
        // Une adresse saisie, elle, se garde — même sans test concluant.
        assertTrue(skipSavesDraft(server("a", baseUrl = "http://pc:8080")))
        assertTrue(skipSavesDraft(server("a", serveUrl = "http://pc:9119", transport = Transport.HERMES_SERVE)))
    }

    @Test
    fun `ignorer revient sur un serveur enregistre et utilisable`() {
        val blank = server("blank")
        val working = server("work", baseUrl = "http://pc:8080")
        assertSame(working, serverAfterSkip(blank, listOf(blank, working)))
    }

    @Test
    fun `ignorer ne change rien quand la configuration suffit`() {
        val working = server("work", baseUrl = "http://pc:8080")
        val other = server("other", baseUrl = "http://autre:8080")
        // L'actif est utilisable : aucun besoin d'en changer.
        assertNull(serverAfterSkip(working, listOf(working, other)))
        // Aucun autre serveur utilisable : on reste « non configuré », l'app reste utilisable.
        assertNull(serverAfterSkip(blank("a"), listOf(blank("a"), blank("b"))))
        // Un serveur enregistré mais sans adresse n'est pas « la configuration existante ».
        assertNull(serverAfterSkip(blank("a"), listOf(blank("a"), server("c", serveUrl = ""))))
    }

    private fun blank(id: String) = server(id)

    // --- « Terminé » ---

    @Test
    fun `termine sans adresse dit que l adresse manque`() {
        assertEquals(
            "Add the server's address first.",
            doneRefusal(server("a"), testing = false, probe = null),
        )
        // L'adresse du Dashboard compte aussi : un transport n'a pas la même adresse qu'un autre.
        assertEquals(
            "Add the server's address first.",
            doneRefusal(server("a", transport = Transport.HERMES_SERVE), testing = false, probe = null),
        )
        assertEquals(
            "Add the server's address first.",
            doneRefusal(server("a", baseUrl = "http://pc:8080", transport = Transport.HERMES_SERVE), testing = false, probe = null),
        )
        assertNull(
            doneRefusal(server("a", serveUrl = "http://pc:9119", transport = Transport.HERMES_SERVE), testing = false, probe = null),
        )
    }

    @Test
    fun `termine dit qu un test est en cours`() {
        assertEquals(
            "A connection test is running — one moment.",
            doneRefusal(server("a", baseUrl = "http://pc:8080"), testing = true, probe = null),
        )
    }

    @Test
    fun `termine reprend le message du test echoue`() {
        val failed = doneRefusal(
            server("a", baseUrl = "http://pc:8080"),
            testing = false,
            probe = probe(false, "No server answered at pc."),
        )
        assertEquals("The test failed: No server answered at pc.", failed)
    }

    @Test
    fun `termine conclut quand le test a reussi ou n a pas eu lieu`() {
        assertNull(doneRefusal(server("a", baseUrl = "http://pc:8080"), testing = false, probe = null))
        assertNull(
            doneRefusal(server("a", baseUrl = "http://pc:8080"), testing = false, probe = probe(true, "Connected.")),
        )
    }

    @Test
    fun `l adresse exigee suit le transport`() {
        assertEquals("http://a:1", transportAddress(server("i", baseUrl = "http://a:1")))
        assertEquals("http://a:1", transportAddress(server("i", baseUrl = "http://a:1", transport = Transport.HERMES_SESSIONS)))
        assertEquals("http://d:9", transportAddress(server("i", baseUrl = "http://a:1", serveUrl = "http://d:9", transport = Transport.HERMES_SERVE)))
    }
}
