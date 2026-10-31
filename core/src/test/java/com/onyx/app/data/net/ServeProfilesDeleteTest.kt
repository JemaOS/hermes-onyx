package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La suppression d'un bot : `DELETE api/profiles/{name}`, sans corps, et jamais le profil
 * principal.
 *
 * Miroir de `HermesServeClient.deleteProfile` / `deleteBot` de la référence : Hermes n'a aucun appel socket
 * pour supprimer un profil, la route REST fait le travail, et un refus doit remonter — sans quoi
 * l'app dirait « supprimé » d'un bot qui vit toujours.
 */
class ServeProfilesDeleteTest {

    @Test
    fun `supprimer un bot est un DELETE de son profil, sans corps`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        ServeProfilesApi(ServeClient(config(), client(calls))).deleteProfile("scout")

        val call = calls.single { it.method == "DELETE" }
        assertEquals("/api/profiles/scout", call.path)
        assertNull("aucun corps, comme le `rest(\"DELETE\", …)` de la référence", call.body)
        assertNull("la route ne prend aucun profil en plus du nom du chemin", call.query["profile"])
    }

    @Test
    fun `le nom est encode dans le chemin`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        ServeProfilesApi(ServeClient(config(), client(calls))).deleteProfile("scout two")

        assertEquals("/api/profiles/scout%20two", calls.single { it.method == "DELETE" }.path)
    }

    @Test
    fun `le profil principal n'est jamais supprimable`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeProfilesApi(ServeClient(config(), client(calls)))

        val error = runCatching { api.deleteProfile("default") }.exceptionOrNull()

        assertTrue("le principal doit être refusé", error is TransportError)
        assertEquals("et rien ne doit partir au serveur", emptyList<Recorded>(), calls.filter { it.method == "DELETE" })
    }

    @Test
    fun `un refus du serveur remonte`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeProfilesApi(ServeClient(config(), client(calls, status = 409) { """{"detail":"in use"}""" }))

        val error = runCatching { api.deleteProfile("scout") }.exceptionOrNull()

        assertTrue(error is TransportError.Http)
        assertEquals(409, (error as TransportError.Http).status)
    }

    // MARK: - Outils

    private fun config() = ServerConfig(
        id = "test",
        serveBaseUrl = "https://hermes.test",
        serveUsername = "user",
        servePassword = "x",
    )

    private fun client(
        calls: MutableList<Recorded>,
        status: Int = 200,
        body: (Request) -> String = { "{}" },
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val sent = request.body?.let { payload -> Buffer().also { payload.writeTo(it) }.readUtf8() }
        val query = request.url.queryParameterNames.associateWith { key -> request.url.queryParameter(key).orEmpty() }
        calls += Recorded(request.method, request.url.encodedPath, sent, query)
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message(if (status == 200) "test" else "refused")
            .body(body(request).toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }.build()

    private data class Recorded(
        val method: String,
        val path: String,
        val body: String?,
        val query: Map<String, String>,
    )
}
