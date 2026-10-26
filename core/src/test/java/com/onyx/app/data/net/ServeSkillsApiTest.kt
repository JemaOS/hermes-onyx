package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les compétences d'un bot: on/off et « always loaded », écrits au serveur pour ce profil-là.
 *
 * Les routes sont celles (`HermesServeClient.toggleSkill`, `setAlwaysLoadedSkills`,
 * `dashboardSkills`): `PUT api/skills/toggle`, `PUT api/config`, `GET api/skills`, chacune avec
 * son `?profile=`.
 */
class ServeSkillsApiTest {

    @Test
    fun `allumer une competence est un PUT de api skills toggle pour ce profil`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        ServeSkillsApi(ServeClient(config(), client(calls) { "{}" })).toggle("weather", false, "scout")

        val call = calls.single { it.method == "PUT" && it.path == "/api/skills/toggle" }
        assertEquals("scout", call.query["profile"])
        assertEquals("""{"name":"weather","enabled":false}""", call.body)
    }

    @Test
    fun `lire les competences d'un bot nomme son profil`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeSkillsApi(
            ServeClient(
                config(),
                client(calls) { request ->
                    if (request.url.encodedPath == "/api/skills") {
                        """[{"name":"weather","enabled":false,"category":"home"}]"""
                    } else {
                        "{}"
                    }
                },
            ),
        )

        val skills = api.skills("scout")

        assertEquals(1, skills.size)
        assertEquals("weather", skills.single().name)
        assertEquals("home", skills.single().category)
        assertEquals(false, skills.single().enabled)
        assertEquals("scout", calls.first { it.path == "/api/skills" }.query["profile"])
    }

    @Test
    fun `les competences toujours lues se lisent dans la config du profil`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeSkillsApi(
            ServeClient(config(), client(calls) { """{"skills":{"auto_load":["weather","notes"]}}""" }),
        )

        val loaded = api.alwaysLoaded("scout")

        assertEquals(listOf("weather", "notes"), loaded)
        val call = calls.single { it.path == "/api/config" }
        assertEquals("false", call.query["include_defaults"])
        assertEquals("scout", call.query["profile"])
    }

    @Test
    fun `epingler une competence ecrit toute la liste auto_load`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        ServeSkillsApi(ServeClient(config(), client(calls) { "{}" })).setAlwaysLoaded(listOf("notes", "weather"), "scout")

        val call = calls.single { it.method == "PUT" && it.path == "/api/config" }
        assertEquals("scout", call.query["profile"])
        assertEquals("""{"config":{"skills":{"auto_load":["notes","weather"]}}}""", call.body)
    }

    @Test
    fun `un refus du serveur remonte au lieu de passer pour un reglage pose`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        // Seule la bascule est refusée: la connexion au Dashboard, elle, répond.
        val api = ServeSkillsApi(
            ServeClient(
                config(),
                client(calls, status = { request -> if (request.url.encodedPath == "/api/skills/toggle") 500 else 200 }) {
                    """{"detail":"no such skill"}"""
                },
            ),
        )

        val error = runCatching { api.toggle("weather", true, "scout") }.exceptionOrNull()

        assertTrue("un refus doit remonter", error is TransportError.Http)
        assertEquals("scout", calls.single { it.path == "/api/skills/toggle" }.query["profile"])
    }

    // MARK: - Outils

    private fun config() = ServerConfig(
        id = "test",
        serveBaseUrl = "https://hermes.test",
        serveUsername = "user",
        servePassword = "x",
    )

    /** Un client qui note chaque appel: sa méthode, son chemin, son corps et sa requête. */
    private fun client(
        calls: MutableList<Recorded>,
        status: (Request) -> Int = { 200 },
        body: (Request) -> String,
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val sent = request.body?.let { payload -> Buffer().also { payload.writeTo(it) }.readUtf8() }
        val query = request.url.queryParameterNames.associateWith { key -> request.url.queryParameter(key).orEmpty() }
        calls += Recorded(request.method, request.url.encodedPath, sent, query)
        val code = status(request)
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "test" else "refused")
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

/** La lecture de `skills.auto_load`: celle de l'éditeur d'un bot, quand rien n'est épinglé. */
class ServeSkillsAutoLoadTest {
    @Test
    fun `une config sans auto_load donne une liste vide`() {
        assertEquals(emptyList<String>(), autoLoadFrom(null))
        assertEquals(emptyList<String>(), autoLoadFrom(JsonNull))
    }

    @Test
    fun `les entrees non textuelles sont ignorees`() {
        val payload = Json.parseToJsonElement("""{"skills":{"auto_load":["weather",3,null]}}""")
        assertEquals(listOf("weather"), autoLoadFrom(payload))
    }
}
