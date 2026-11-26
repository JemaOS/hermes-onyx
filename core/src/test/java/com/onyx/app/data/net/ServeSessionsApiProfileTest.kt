package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **L'attribution par bot du registre des sessions.**
 *
 * Une session est écrite dans la base **de son profil** : `~/.hermes/state.db` pour `default`,
 * `~/.hermes/profiles/<bot>/state.db` pour un bot (vérifié : la ligne de la session `writer`
 * « Messages 4 » vit dans `profiles/writer/state.db`, pas dans la base `default`). Un registre lu
 * **sans profil** lit donc la base `default`, où la ligne du bot n'est pas — et les deux écrans
 * (fiche d'usage, relevé de l'agent) concluaient « le registre n'a pas répondu » sur une session
 * parfaitement enregistrée.
 *
 * Ces verrous échouent si une lecture du registre part sans le profil qui porte la session.
 */
class ServeSessionsApiProfileTest {

    /**
     * Un registre simulé qui rend **une base par profil** : `api/sessions` sans `profile` est la
     * base `default`, `?profile=writer` est la base du bot. C'est la forme réelle de la route.
     */
    private fun registryApi(
        asked: MutableList<String?>,
        rows: Map<String?, List<String>>,
    ): ServeSessionsApi {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.encodedPath.endsWith("/api/sessions")) {
                request.respond("""{"ok":true}""")
            } else {
                val profile = request.url.queryParameter("profile")
                asked += profile
                val ids = rows[profile].orEmpty()
                val body = ids.joinToString(",") { id -> """{"id":"$id","input_tokens":1,"output_tokens":2}""" }
                request.respond("""{"sessions":[$body],"total":${ids.size},"limit":100,"offset":0}""")
            }
        }.build()
        val config = ServerConfig(
            id = "test",
            serveBaseUrl = "https://hermes.test",
            apiKey = "installation-key",
        )
        return ServeSessionsApi(ServeClient(config, http))
    }

    @Test
    fun `un profil nomme part sur la route`() = runBlocking {
        val asked = mutableListOf<String?>()
        val api = registryApi(asked, mapOf("writer" to listOf("w1")))

        api.allSummaries(profile = "writer")

        assertEquals(listOf("writer"), asked)
    }

    @Test
    fun `le profil par defaut ne se dit pas sur la route`() = runBlocking {
        val asked = mutableListOf<String?>()
        val api = registryApi(asked, mapOf(null to listOf("d1")))

        // `default` est la base du processus : la route le réserve aux profils nommés.
        api.allSummaries(profile = "default")
        api.allSummaries(profile = "  ")
        api.allSummaries(profile = null)

        assertEquals(listOf(null, null, null), asked)
    }

    @Test
    fun `la ligne d un bot ne se trouve pas dans la base par defaut`() = runBlocking {
        val asked = mutableListOf<String?>()
        val api = registryApi(asked, mapOf(null to listOf("d1"), "writer" to listOf("w1")))

        // C'est le défaut signalé : lire le registre sans profil ne rend pas la ligne du bot…
        assertNull("la base default ne porte pas la session du bot", api.summary("w1"))
        // …et la lire **sous le profil du bot** la rend.
        assertNotNull("le profil du bot porte sa session", api.summary("w1", "writer"))
    }

    @Test
    fun `une session se cherche dans les profils qui peuvent la porter`() = runBlocking {
        val api = registryApi(mutableListOf(), mapOf(null to listOf("d1"), "writer" to listOf("w1")))

        val found = api.summaryIn("w1", listOf("default", "writer"))

        assertNotNull(found)
        assertEquals("w1", found!!.id)
        assertEquals(1, found.inputTokens)
        assertEquals(2, found.outputTokens)
    }

    @Test
    fun `plusieurs profils se lisent tous, et leurs lignes se reunissent`() = runBlocking {
        val asked = mutableListOf<String?>()
        val api = registryApi(asked, mapOf(null to listOf("d1"), "writer" to listOf("w1"), "coder" to listOf("c1")))

        val all = api.summariesIn(listOf("default", "writer", "coder"))

        assertEquals(setOf("d1", "w1", "c1"), all.map { it.id }.toSet())
        // Chaque profil est demandé une fois, sous son nom (ou sans nom pour `default`).
        assertEquals(listOf(null, "writer", "coder"), asked)
    }

    @Test
    fun `un profil repete ne se demande pas deux fois`() = runBlocking {
        val asked = mutableListOf<String?>()
        val api = registryApi(asked, mapOf("writer" to listOf("w1")))

        api.summariesIn(listOf("writer", " writer ", "writer"))

        assertEquals(listOf("writer"), asked)
        assertTrue(asked.isNotEmpty())
    }

    @Test
    fun `les parametres de page portent le profil nomme, jamais default`() {
        assertEquals(
            "writer",
            ServeSessionsApi.pageQuery(limit = 100, offset = 0, order = "created", profile = "writer")["profile"],
        )
        assertTrue(
            !ServeSessionsApi.pageQuery(limit = 100, offset = 0, order = "created", profile = "default")
                .containsKey("profile"),
        )
        assertNull(ServeSessionsApi.named("DEFAULT"))
        assertNull(ServeSessionsApi.named(""))
        assertEquals("coder", ServeSessionsApi.named(" coder "))
    }
}

private fun Request.respond(body: String): Response = Response.Builder()
    .request(this)
    .protocol(Protocol.HTTP_1_1)
    .code(200)
    .message("test")
    .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
    .build()
