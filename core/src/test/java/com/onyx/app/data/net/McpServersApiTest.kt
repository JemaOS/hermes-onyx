package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.McpServerDraft
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les routes réelles des serveurs MCP, code par code : **lire** (`GET api/mcp/servers`), **ajouter**
 * (`POST api/mcp/servers`), **retirer** (`DELETE api/mcp/servers/{nom}`), **basculer**
 * (`PUT api/mcp/servers/{nom}/enabled`) et **tester** (`POST api/mcp/servers/{nom}/test`).
 *
 * Les corps de réponse de ce test sont ceux du Dashboard Hermes, relevés sur la route
 * (`_mcp_server_summary`, et `{"ok":false,"error":"Connection closed","tools":[]}` d'une vraie sonde
 * qui échoue) : ce qu'on vérifie est la **forme lue**, pas une forme inventée.
 */
class McpServersApiTest {

    @Test
    fun `la liste MCP rend la forme reelle de chaque serveur`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeGatewayApi(
            ServeClient(
                config(),
                client(calls) {
                    """{"servers":[
                        {"name":"probe-stdio","transport":"stdio","url":null,"command":"python3",
                         "args":["-c","import sys"],"env":{"TOKEN":"•••"},"auth":null,
                         "enabled":true,"tools":["read","write"],"source":"config","plugin":null},
                        {"name":"probe-http","transport":"http","url":"http://127.0.0.1:1/mcp",
                         "command":null,"args":[],"env":{},"auth":"oauth","enabled":false,
                         "tools":null,"source":"config","plugin":null},
                        {"name":"plugged","transport":"http","url":"http://x/mcp","enabled":true,
                         "plugin":"kanban"}]}"""
                },
            ),
        )

        val servers = api.mcpServers()

        assertEquals(listOf("probe-stdio", "probe-http", "plugged"), servers.map { it.name })
        val stdio = servers.first()
        assertEquals("stdio", stdio.transport)
        assertEquals("python3", stdio.command)
        assertEquals(listOf("-c", "import sys"), stdio.args)
        assertEquals("python3 -c import sys", stdio.commandLine)
        assertEquals(mapOf("TOKEN" to "•••"), stdio.env)
        assertEquals(listOf("read", "write"), stdio.toolSelection)
        assertEquals("config", stdio.source)
        assertTrue(stdio.isLocalCommand)

        val http = servers[1]
        assertEquals("http", http.transport)
        assertEquals("http://127.0.0.1:1/mcp", http.url)
        assertEquals("http://127.0.0.1:1/mcp", http.target)
        assertEquals("oauth", http.auth)
        assertFalse(http.enabled)
        assertNull("`tools: null` veut dire « tous », pas une liste vide", http.toolSelection)
        assertFalse(http.isLocalCommand)

        // Un serveur d'une version qui ne dit pas son transport se lit quand même de ses champs.
        assertEquals("kanban", servers[2].plugin)
        assertNull(servers[2].toolSelection)
        assertEquals("/api/mcp/servers", calls.first().path)
    }

    @Test
    fun `ajouter un serveur a commande locale envoie la commande, les arguments et l'environnement`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeGatewayApi(
            ServeClient(
                config(),
                client(calls) {
                    """{"name":"probe-stdio","transport":"stdio","command":"npx",
                        "args":["-y","@scope/server"],"env":{"API_KEY":"k"},"enabled":true,
                        "tools":null,"source":"config","plugin":null}"""
                },
            ),
        )

        val added = api.addMcpServer(
            McpServerDraft(
                name = " probe-stdio ",
                transport = "stdio",
                command = "npx",
                args = "-y  @scope/server",
                env = "API_KEY=k",
            ),
        )

        val call = calls.single { it.method == "POST" && it.path == "/api/mcp/servers" }
        assertEquals(
            """{"name":"probe-stdio","command":"npx","args":["-y","@scope/server"],"env":{"API_KEY":"k"}}""",
            call.body,
        )
        assertEquals("probe-stdio", added.name)
        assertEquals("stdio", added.transport)
    }

    @Test
    fun `ajouter un serveur par adresse envoie l'adresse et le mode d'authentification, jamais de jeton en clair`() =
        runBlocking {
            val calls = mutableListOf<Recorded>()
            val api = ServeGatewayApi(
                ServeClient(config(), client(calls) { """{"name":"remote","transport":"http"}""" }),
            )

            api.addMcpServer(
                McpServerDraft(name = "remote", transport = "http", url = "https://x/mcp", auth = "oauth"),
            )
            api.addMcpServer(
                McpServerDraft(
                    name = "header",
                    transport = "http",
                    url = "https://y/mcp",
                    auth = "header",
                    bearerToken = "secret",
                ),
            )

            val posts = calls.filter { it.method == "POST" && it.path == "/api/mcp/servers" }
            assertEquals("""{"name":"remote","url":"https://x/mcp","auth":"oauth"}""", posts[0].body)
            assertEquals(
                """{"name":"header","url":"https://y/mcp","auth":"header","bearer_token":"secret"}""",
                posts[1].body,
            )
        }

    @Test
    fun `retirer un serveur est un DELETE sans corps, et la bascule un PUT d'enabled`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeGatewayApi(ServeClient(config(), client(calls) { """{"ok":true}""" }))

        api.removeMcpServer("probe-stdio")
        api.setMcpEnabled("probe-stdio", false)

        val delete = calls.single { it.method == "DELETE" }
        assertEquals("/api/mcp/servers/probe-stdio", delete.path)
        assertEquals("", delete.body)
        val toggle = calls.single { it.method == "PUT" }
        assertEquals("/api/mcp/servers/probe-stdio/enabled", toggle.path)
        assertEquals("""{"enabled":false}""", toggle.body)
    }

    @Test
    fun `un test reussi rend les outils et la latence mesuree`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeGatewayApi(
            ServeClient(
                config(),
                client(calls) {
                    """{"ok":true,"tools":[{"name":"read","description":"Read a file"},
                        {"name":"write","description":""}],"prompts":2,"resources":1}"""
                },
            ),
        )

        val probe = api.testMcp("probe-stdio")

        assertTrue(probe.ok)
        assertEquals("Connected · 2 tools", probe.summary)
        assertEquals(listOf("read", "write"), probe.tools.map { it.name })
        assertEquals("Read a file", probe.tools.first().description)
        assertEquals(2, probe.prompts)
        assertEquals(1, probe.resources)
        assertTrue("la latence est mesurée ici", (probe.latencyMillis ?: -1) >= 0)
        assertEquals("/api/mcp/servers/probe-stdio/test", calls.single().path)
    }

    @Test
    fun `un test qui echoue remonte l'erreur reelle et aucun outil`() = runBlocking {
        // La réponse réelle d'une sonde qui n'arrive pas à se connecter : un 200, `ok: false`.
        val api = ServeGatewayApi(
            ServeClient(config(), client(mutableListOf()) { """{"ok":false,"error":"Connection closed","tools":[]}""" }),
        )

        val probe = api.testMcp("probe-stdio")

        assertFalse("un échec n'est jamais une réussite", probe.ok)
        assertEquals("Connection closed", probe.error)
        assertTrue("aucun outil n'est inventé pour un test raté", probe.tools.isEmpty())
    }

    @Test
    fun `un test sur un serveur inconnu remonte le 404 du serveur`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val api = ServeGatewayApi(
            ServeClient(
                config(),
                client(calls, status = 404) { """{"detail":"Server 'inexistant' not found"}""" },
            ),
        )

        val probe = api.testMcp("inexistant")

        assertFalse(probe.ok)
        assertTrue("le 404 est dit, pas transformé en succès", probe.error.orEmpty().contains("404"))
    }

    private data class Recorded(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val body: String,
    )

    private fun client(
        calls: MutableList<Recorded>,
        status: Int = 200,
        body: (Request) -> String,
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
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
        request.respond(status, body(request))
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
