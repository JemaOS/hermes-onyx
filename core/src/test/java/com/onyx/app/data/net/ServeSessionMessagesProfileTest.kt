package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * **Le sondage des messages dit de quel profil il parle.**
 *
 * Journal du 2026-11-05, 21:06 : `GET /api/sessions/20261105_195844_06443a/messages` toutes les
 * 5 s, `404 session_not_found`, alors que la session vit dans `profiles/coder`. L'en-tête
 * `X-Hermes-Profile` ne suffisait pas — il dépendait du profil de l'**écran**, pas de la
 * conversation, et un relais pouvait le laisser tomber. Le profil part donc aussi dans l'URL,
 * celle que le serveur lit déjà pour ses listes (`api/model/options?profile=<nom>`).
 */
class ServeSessionMessagesProfileTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(hermesProfile: String = "") = ServerConfig(
        id = "s",
        baseUrl = server.url("/").toString(),
        apiKey = "cle-du-gateway-0123456789",
        hermesProfile = hermesProfile,
    )

    @Test
    fun `le profil de la conversation part dans l URL`() = runBlocking {
        ServeSessionMessages(config()).rows("abc", "coder")
        assertEquals("/api/sessions/abc/messages?profile=coder", server.takeRequest().path)
    }

    @Test
    fun `a defaut, le profil du serveur fait foi`() = runBlocking {
        ServeSessionMessages(config(hermesProfile = "kilo")).rows("abc")
        assertEquals("/api/sessions/abc/messages?profile=kilo", server.takeRequest().path)
    }

    @Test
    fun `sans aucun profil, l URL reste nue`() = runBlocking {
        ServeSessionMessages(config()).rows("abc")
        val path = server.takeRequest().path.orEmpty()
        assertEquals("/api/sessions/abc/messages", path)
        assertNull("aucune query parasite", path.substringAfter("?", "").takeIf { it.isNotEmpty() })
    }
}
