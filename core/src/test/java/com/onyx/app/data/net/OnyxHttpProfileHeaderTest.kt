package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * **Le profil voyage en en-tête, jamais dans l'adresse.**
 *
 * Journal du 2026-11-05 : l'app sondait `GET /api/sessions/20261105_193955_640040/messages` toutes
 * les 10 s et recevait `404 session_not_found`, parce que la session vit dans le profil `coder`
 * alors qu'une requête sans profil est servie par le profil **par défaut** du serveur. L'adresse ne
 * peut pas porter le profil (`/p/<profil>` part au tableau de bord, qui répond 405), donc il part
 * en en-tête — et un serveur qui n'a pas de clé pour ce profil reçoit une seconde chance sans lui.
 */
class OnyxHttpProfileHeaderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(profile: String, profileKey: String = "cle-du-profil-0123456789") = ServerConfig(
        id = "s",
        name = "maison",
        baseUrl = server.url("/").toString(),
        apiKey = "cle-du-gateway-0123456789",
        hermesProfile = profile,
        profileKey = profileKey,
    )

    private fun call(cfg: ServerConfig) {
        val client = OnyxHttp.clientFor(cfg)
        client.newCall(
            Request.Builder().url(server.url("/api/sessions/abc/messages")).build()
        ).execute().close()
    }

    @Test
    fun `le profil effectif part en en-tete`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))

        call(config("coder"))

        assertEquals("coder", server.takeRequest().getHeader(OnyxHttp.PROFILE_HEADER))
    }

    @Test
    fun `le profil par defaut ne pose aucun en-tete`() {
        // « default » et « » veulent dire « le profil par défaut du serveur » : rien à annoncer.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        call(config("default"))
        assertNull(server.takeRequest().getHeader(OnyxHttp.PROFILE_HEADER))

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        call(config(""))
        assertNull(server.takeRequest().getHeader(OnyxHttp.PROFILE_HEADER))
    }

    @Test
    fun `un 401 fait reessayer sans l en-tete plutot que de casser la connexion`() {
        // Ce serveur n'a pas de clé pour « coder » : on retombe sur son profil par défaut, comme
        // avant, au lieu de laisser l'app muette sur un 401.
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"gateway_auth_failed"}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))

        call(config("coder"))

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("coder", first.getHeader(OnyxHttp.PROFILE_HEADER))
        assertNull("le second essai ne porte plus le profil", second.getHeader(OnyxHttp.PROFILE_HEADER))
    }

    @Test
    fun `le refus est retenu pour la session`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        call(config("coder"))

        // Appel suivant : plus d'en-tête du tout, donc plus d'aller-retour perdu.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        call(config("coder"))

        server.takeRequest() // 401
        server.takeRequest() // le retry sans en-tête
        assertNull(server.takeRequest().getHeader(OnyxHttp.PROFILE_HEADER))
    }
}
