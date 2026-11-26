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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les appels que l'écran « Capacités » porte : le **catalogue** du hub, la bascule des jeux d'outils
 * et celle des serveurs MCP, avec leur test de connexion.
 *
 * Ces trois familles sont celles du sélecteur de l'écran. Le catalogue est nouveau
 * (`GET api/skills/hub/official`, `POST api/skills/hub/install`, `POST api/skills/hub/uninstall`) ;
 * les deux bascules existaient déjà pour les Réglages, et ce test tient la route et le corps que
 * l'écran leur donne — une bascule d'écran qui partirait sur une autre route laisserait croire à un
 * réglage posé qui n'a rien changé.
 */
class CapabilitiesActionsTest {

    @Test
    fun `le catalogue officiel est lu avec les familles et la marque installee`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeSkillsApi(
            ServeClient(
                config(),
                client(calls) { request ->
                    if (request.url.encodedPath == "/api/skills/hub/official") {
                        """{"skills":[
                             {"identifier":"official/research/arxiv","name":"arxiv",
                              "description":"Papiers","category":"research","installed":true,
                              "trust_level":"builtin"},
                             {"identifier":"official/web/search","name":"search","category":"web"}]}"""
                    } else {
                        "{}"
                    }
                },
            ),
        )

        val catalog = api.catalog()

        assertEquals(2, catalog.size)
        assertEquals("arxiv", catalog.first().name)
        assertEquals("research", catalog.first().category)
        assertTrue(catalog.first().installed)
        assertEquals("builtin", catalog.first().trustLevel)
        assertFalse("une entrée sans marque n'est pas installée", catalog.last().installed)
        assertEquals("web", catalog.last().category)
        assertEquals("/api/skills/hub/official", calls.first { it.path == "/api/skills/hub/official" }.path)
    }

    @Test
    fun `installer et retirer du catalogue passent par les routes du hub`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeSkillsApi(ServeClient(config(), client(calls) { """{"ok":true}""" }))

        api.installFromCatalog("official/research/arxiv")
        api.uninstallFromCatalog("arxiv")

        val install = calls.single { it.path == "/api/skills/hub/install" }
        assertEquals("POST", install.method)
        assertEquals("""{"identifier":"official/research/arxiv"}""", install.body)
        val uninstall = calls.single { it.path == "/api/skills/hub/uninstall" }
        assertEquals("POST", uninstall.method)
        assertEquals("""{"name":"arxiv"}""", uninstall.body)
    }

    @Test
    fun `la bascule d'un jeu d'outils est un PUT portant le nom du jeu`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeToolsetsApi(ServeClient(config(), client(calls) { "{}" }))

        api.setEnabled("web", true)

        val call = calls.single { it.method == "PUT" }
        assertEquals("/api/tools/toolsets/web", call.path)
        assertEquals("""{"enabled":true}""", call.body)
    }

    @Test
    fun `les serveurs MCP se listent, se basculent et se testent`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val gateway = ServeGatewayApi(
            ServeClient(
                config(),
                client(calls) { request ->
                    when {
                        request.url.encodedPath == "/api/mcp/servers" ->
                            """{"servers":[{"name":"fs","command":"mcp-fs","enabled":true},
                                          {"name":"plug","url":"http://x","plugin":"kanban","enabled":true}]}"""
                        // La forme réelle de la route : une liste d'outils, pas un compte.
                        request.url.encodedPath.endsWith("/test") ->
                            """{"ok":true,"tools":[{"name":"read","description":""},
                                                  {"name":"write","description":""}]}"""
                        else -> "{}"
                    }
                },
            ),
        )

        val servers = gateway.mcpServers()
        assertEquals(listOf("fs", "plug"), servers.map { it.name })
        assertEquals("mcp-fs", servers.first().target)
        assertEquals("kanban", servers.last().plugin)

        gateway.setMcpEnabled("fs", false)
        val toggle = calls.single { it.path == "/api/mcp/servers/fs/enabled" }
        assertEquals("PUT", toggle.method)
        assertEquals("""{"enabled":false}""", toggle.body)

        val probe = gateway.testMcp("fs")
        assertTrue(probe.ok)
        assertEquals("Connected · 2 tools", probe.summary)
        assertEquals("/api/mcp/servers/fs/test", calls.first { it.path.endsWith("/test") }.path)
    }

    @Test
    fun `un test MCP qui echoue est rendu comme un echec, jamais comme une reussite`() = runBlocking {
        val gateway = ServeGatewayApi(
            ServeClient(
                config(),
                client(mutableListOf()) { """{"ok":false,"error":"handshake refused"}""" },
            ),
        )

        val probe = gateway.testMcp("fs")

        assertFalse(probe.ok)
        assertEquals("handshake refused", probe.error)
    }

    private data class Recorded(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val body: String,
    )

    private fun client(calls: MutableList<Recorded>, body: (Request) -> String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            if (path.endsWith("/auth/password-login") || path == "/api/sessions") {
                return@addInterceptor request.respond(200, """{"ok":true}""")
            }
            calls += Recorded(
                method = request.method,
                path = path,
                query = request.url.queryParameterNames.associateWith { request.url.queryParameter(it).orEmpty() },
                body = request.text(),
            )
            request.respond(200, body(request))
        }.build()

    private fun config(): ServerConfig = ServerConfig(
        id = "test",
        serveBaseUrl = "https://hermes.test",
        serveUsername = "user",
        servePassword = "x",
    )

    private fun Request.text(): String =
        body?.let { payload -> Buffer().also { payload.writeTo(it) }.readUtf8() } ?: ""

    private fun Request.respond(code: Int, body: String): Response = Response.Builder()
        .request(this)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
        .build()
}
