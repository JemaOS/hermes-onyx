package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le serveur « une clé » : le compagnon sert le Dashboard sur la **même adresse** que le chat et
 * accepte la clé d'installation en `Bearer`. L'app ne doit donc **rien** demander d'autre —
 * ni écran de connexion, ni nom d'utilisateur, ni mot de passe.
 */
class ServeKeyIdentityTest {

    private fun keyOnly() = ServerConfig(
        id = "compagnon",
        baseUrl = "https://companion.test",
        apiKey = "install-key",
        transport = Transport.HERMES_SESSIONS,
        serveBaseUrl = "https://companion.test",
    )

    private fun withPassword() = ServerConfig(
        id = "dashboard",
        serveBaseUrl = "https://hermes.test",
        serveUsername = "alice",
        servePassword = "secret",
    )

    /** Un client qui répond du JSON enregistré et retient chaque requête partie. */
    private class Recorder(
        private val json: (String) -> String,
        private val status: (String) -> Int = { 200 },
        base: OkHttpClient = OkHttpClient(),
    ) {
        val paths = mutableListOf<String>()
        val requests = mutableListOf<Request>()

        val client: OkHttpClient = base.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            paths += path
            requests += request
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status(path))
                .message("test")
                .body(json(path).toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }.build()
    }

    @Test
    fun `une cle seule lit les profils sans jamais ouvrir de session par mot de passe`() = runBlocking {
        val recorder = Recorder(
            json = { path ->
                if (path.endsWith("/api/profiles")) {
                    """{"profiles":[{"name":"kilo"},{"name":"scout"}]}"""
                } else {
                    "{}"
                }
            },
            // Le vrai client du Dashboard : c'est lui qui pose la clé quand elle fait l'identité.
            base = ServeClient.clientFor(keyOnly()),
        )

        val bots = ServeProfilesApi(ServeClient(keyOnly(), recorder.client)).profiles()

        assertEquals(listOf("kilo", "scout"), bots.map { it.name })
        assertFalse(
            "aucun écran de connexion : pas d'appel à /auth/password-login",
            recorder.paths.any { it.endsWith("password-login") },
        )
        assertEquals(listOf("/api/profiles"), recorder.paths)
        assertEquals("Bearer install-key", recorder.requests.single().header("Authorization"))
    }

    @Test
    fun `un Dashboard a identifiants garde la session par mot de passe et pas de cle sur le REST`() = runBlocking {
        val recorder = Recorder(
            json = { path -> if (path.endsWith("/api/sessions")) "" else """{"profiles":[]}""" },
            status = { path -> if (path.endsWith("/api/sessions")) 401 else 200 },
        )

        // La sonde de session échoue (401) : login tente alors le mot de passe.
        runCatching { ServeProfilesApi(ServeClient(withPassword(), recorder.client)).profiles() }

        assertTrue(
            "un Dashboard à identifiants s'authentifie par mot de passe",
            recorder.paths.any { it.endsWith("password-login") },
        )
        assertFalse(
            "la clé n'est pas posée quand l'identité est le mot de passe",
            recorder.requests.any { it.header("Authorization") != null },
        )
    }

    @Test
    fun `le client du Dashboard pose la cle en Bearer quand elle fait l'identite`() {
        val client = ServeClient.clientFor(keyOnly()).newBuilder().addInterceptor { chain ->
            assertEquals("Bearer install-key", chain.request().header("Authorization"))
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        client.newCall(
            Request.Builder().url("https://companion.test/api/sessions").build(),
        ).execute().close()
    }

    @Test
    fun `un Dashboard a mot de passe ne recoit pas la cle en Bearer`() {
        val client = ServeClient.clientFor(withPassword()).newBuilder().addInterceptor { chain ->
            assertEquals(null, chain.request().header("Authorization"))
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        client.newCall(
            Request.Builder().url("https://hermes.test/api/sessions").build(),
        ).execute().close()
    }
}
