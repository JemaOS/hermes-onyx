package com.onyx.app.data.net

import com.onyx.app.data.model.CustomHeader
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionTesterTest {
    private lateinit var server: TinyHttpServer

    @Before
    fun startServer() {
        server = TinyHttpServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun `un Dashboard sans login est reellement interroge avant le succes`() = runBlocking {
        server.respond("/api/status", 200, """{"version":"2026.10","auth_required":false}""")
        val config = ServerConfig(
            id = "test",
            transport = Transport.HERMES_SERVE,
            serveBaseUrl = server.baseUrl,
        )

        val result = ConnectionTester.current(config)

        assertTrue(result.ok)
        assertTrue(result.message.contains("2026.10"))
    }

    @Test
    fun `un Dashboard qui refuse le login ne produit pas un faux succes`() = runBlocking {
        server.respond("/api/status", 200, """{"version":"2026.10","auth_required":true}""")
        server.respond("/api/sessions", 401, "")
        server.respond("/auth/password-login", 401, "")
        val config = ServerConfig(
            id = "test",
            transport = Transport.HERMES_SERVE,
            serveBaseUrl = server.baseUrl,
            serveUsername = "alice",
            servePassword = "wrong",
        )

        val result = ConnectionTester.current(config)

        assertFalse(result.ok)
        assertTrue(result.message.contains("login failed"))
    }

    @Test
    fun `les en-tetes Access et personnalises partent avec la sonde`() = runBlocking {
        server.respond("/api/status", 200, """{"version":"2026.10","auth_required":false}""")
        val config = ServerConfig(
            id = "test",
            transport = Transport.HERMES_SERVE,
            serveBaseUrl = server.baseUrl,
            accessID = "client-id",
            accessSecret = "access-secret",
            customHeaders = listOf(CustomHeader("X-Tenant", "alpha")),
        )

        val result = ConnectionTester.current(config)

        assertTrue(result.ok)
        assertEquals("client-id", server.lastHeaders["cf-access-client-id"])
        assertEquals("access-secret", server.lastHeaders["cf-access-client-secret"])
        assertEquals("alpha", server.lastHeaders["x-tenant"])
    }
}

/** Serveur HTTP de test sans dépendance supplémentaire, sur loopback uniquement. */
private class TinyHttpServer : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val responses = mutableMapOf<String, Pair<Int, String>>()
    @Volatile private var running = true
    private var thread: Thread? = null
    @Volatile var lastHeaders: Map<String, String> = emptyMap()
    val baseUrl: String get() = "http://127.0.0.1:${socket.localPort}"

    fun start() {
        thread = Thread({
            while (running) {
                runCatching {
                    socket.accept().use { client ->
                        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.ISO_8859_1))
                        val request = reader.readLine().orEmpty().split(' ')
                        val path = request.getOrNull(1).orEmpty().substringBefore('?')
                        var contentLength = 0
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            val name = header.substringBefore(':').lowercase()
                            headers[name] = header.substringAfter(':').trim()
                            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                            }
                        }
                        lastHeaders = headers
                        if (contentLength > 0) {
                            val body = CharArray(contentLength)
                            reader.read(body, 0, contentLength)
                        }
                        val (status, text) = responses[path] ?: (404 to "")
                        val bytes = text.toByteArray()
                        val reason = if (status == 200) "OK" else "ERROR"
                        client.getOutputStream().apply {
                            write(
                                (
                                    "HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                                    ).toByteArray(Charsets.ISO_8859_1),
                            )
                            write(bytes)
                            flush()
                        }
                    }
                }
            }
        }, "tiny-http-server").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun respond(path: String, status: Int, body: String) {
        responses[path] = status to body
    }

    override fun close() {
        running = false
        runCatching { socket.close() }
        thread?.join(1_000)
    }
}
