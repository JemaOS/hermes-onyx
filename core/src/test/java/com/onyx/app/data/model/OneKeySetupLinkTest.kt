package com.onyx.app.data.model

import com.onyx.app.data.net.ServeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le lien « une clé » d'Onyx One, lu de bout en bout.
 *
 * Le lien que le tunnel produit est court : `onyx://connect?v=1&server=<api>&key=<clé>`.
 * Il ne porte **ni** `dashboard`, **ni** `api`, **ni** `model-url` — seulement `server` et
 * `key`. Or c'est de là que tout se décide :
 *
 *  - `apiURL` tombe sur `server`, le transport devient HERMES_SESSIONS, et le serveur
 *    enregistré pointe sur le bon tunnel ;
 *  - `serveBaseUrl` — l'adresse du Dashboard, celle qui porte le WebSocket du RPC — doit
 *    **tomber sur `apiURL`**, faute de quoi l'app n'essaie jamais d'ouvrir le socket et le
 *    roster des bots reste vide alors que le REST répond.
 *
 * Le défaut observé sur le terrain : le lien collé dans l'app « a l'air de fonctionner » (le
 * test de connexion passe) mais la liste des bots et des conversations reste vide.
 */
class OneKeySetupLinkTest {

    private val server = "https://example.trycloudflare.com"
    private val key = "install-key"

    private fun link(vararg extra: String): String {
        val base = "onyx://connect?v=1&server=${server.replace(":", "%3A").replace("/", "%2F")}&key=$key"
        return if (extra.isEmpty()) base else base + "&" + extra.joinToString("&")
    }

    @Test
    fun `le lien one key donne une adresse api et une cle`() {
        val code = SetupCode.parse(link())

        assertNotNull("le lien doit etre lu", code)
        assertEquals(server, code!!.apiURL)
        assertEquals(key, code.apiKey)
    }

    @Test
    fun `le lien one key porte le transport des sessions`() {
        val code = SetupCode.parse(link())

        assertEquals(Transport.HERMES_SESSIONS, code!!.requestedTransport)
    }

    @Test
    fun `le lien one key donne au dashboard l adresse du serveur`() {
        // C'est ce point qui manquait : sans `serveBaseUrl`, l'app ne tente pas le WebSocket
        // du Dashboard, et le roster des bots reste vide.
        val code = SetupCode.parse(link())!!
        val config = code.toServerConfig("srv-test")

        assertEquals(server, config.serveBaseUrl)
        assertEquals(server, config.baseUrl)
    }

    @Test
    fun `le lien one key donne une connexion utilisable`() {
        val code = SetupCode.parse(link())!!
        val config = code.toServerConfig("srv-test")

        assertTrue("le serveur doit avoir des identifiants : $config", config.hasServeCredentials)
        assertTrue("le serveur doit etre joignable par la cle : $config", config.servesWithKey)
        assertEquals(key, config.effectiveApiKey)
    }

    @Test
    fun `le lien du watchdog avec model et bridge reste lisible`() {
        // Le lien que le watchdog regenere en plus du lien officiel : les champs de la voix,
        // du pont et de la langue ne doivent ni casser la lecture ni changer l'adresse.
        val code = SetupCode.parse(
            link("model=hermes-agent", "voices=https%3A%2F%2Fvoix.test", "bridge=https%3A%2F%2Fpont.test", "lang=fr"),
        )

        assertNotNull(code)
        assertEquals(server, code!!.apiURL)
        assertEquals(key, code.apiKey)
        assertEquals("hermes-agent", code.model)
        assertEquals("https://voix.test", code.voicesURL)
        assertEquals("https://pont.test", code.bridgeURL)
        assertEquals(Transport.HERMES_SESSIONS, code.requestedTransport)
        assertEquals(server, code.toServerConfig("srv-test").serveBaseUrl)
    }

    @Test
    fun `le client de la config one key vise l adresse du serveur`() {
        val config = SetupCode.parse(link())!!.toServerConfig("srv-test")

        // `ServeClient` lit `serveBase` : c'est lui qui porte l'adresse pour toutes les
        // requetes REST (`/api/profiles`, `/api/sessions`).
        assertEquals(server, config.serveBase)
        assertTrue(ServeClient(config).isConfigured)
    }
}
