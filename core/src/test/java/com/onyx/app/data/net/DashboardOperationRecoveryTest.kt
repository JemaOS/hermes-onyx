package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Exercise the real ticket/upgrade and streaming callers, not just the auth coordinator. */
@RunWith(Parameterized::class)
class DashboardOperationRecoveryTest(private val operation: String) {
    @get:Rule val temporary = TemporaryFolder()

    private fun config(server: MockWebServer) = ServerConfig(
        id = "recovery", baseUrl = server.url("/").toString(),
        serveBaseUrl = server.url("/").toString(),
        serveUsername = "user", servePassword = "password",
    )

    private suspend fun perform(config: ServerConfig): Boolean {
        if (operation == "download") {
            val destination = temporary.newFile()
            return try {
                ServeFileDownload(config).toFile("report.txt", destination)
                assertEquals("report contents", destination.readText())
                assertFalse(destination.resolveSibling(destination.name + ".part").exists())
                true
            } catch (error: TransportError.Http) {
                assertEquals(401, error.status)
                false
            }
        }
        val rpc = ServeRpc(config)
        return try {
            rpc.connect()
            rpc.isReady
        } catch (error: TransportError.Http) {
            assertEquals(401, error.status)
            false
        } finally {
            rpc.close()
        }
    }

    private fun success(request: RecordedRequest): MockResponse = when {
        request.path == "/api/auth/ws-ticket" -> MockResponse().setBody("{\"ticket\":\"fresh-ticket\"}")
        request.path == "/api/ws?ticket=fresh-ticket" ->
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }
            })
        request.path == "/api/files/download?path=report.txt" -> MockResponse().setBody("report contents")
        else -> MockResponse().setResponseCode(404)
    }

    @Test fun expiredSessionRefreshesOnceAndCompletesOperation() = runBlocking {
        MockWebServer().use { server ->
            val logins = AtomicInteger()
            val stale = AtomicInteger()
            val fresh = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    assertEquals(OnyxHttp.USER_AGENT, request.getHeader("User-Agent"))
                    assertNull(request.getHeader("Authorization"))
                    if (request.path == "/auth/password-login") {
                        val generation = logins.incrementAndGet()
                        return MockResponse().addHeader("Set-Cookie", "session=$generation; Path=/")
                    }
                    if (request.getHeader("Cookie") == "session=1") {
                        stale.incrementAndGet()
                        return MockResponse().setResponseCode(401)
                    }
                    if (request.path != "/api/ws?ticket=fresh-ticket") {
                        assertEquals("session=2", request.getHeader("Cookie"))
                        fresh.incrementAndGet()
                    }
                    return success(request)
                }
            }
            val config = config(server)
            ServeClient(config).login()
            assertTrue(perform(config))
            assertEquals(2, logins.get())
            assertEquals(1, stale.get())
            assertEquals(1, fresh.get())
        }
    }

    @Test fun repeatedUnauthorizedIsBoundedToOneRefreshAndOneReplay() = runBlocking {
        MockWebServer().use { server ->
            val logins = AtomicInteger()
            val attempts = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/auth/password-login") {
                        logins.incrementAndGet()
                        return MockResponse()
                    }
                    attempts.incrementAndGet()
                    return MockResponse().setResponseCode(401)
                }
            }
            val config = config(server)
            ServeClient(config).login()
            assertFalse(perform(config))
            assertEquals(2, logins.get())
            assertEquals(2, attempts.get())
        }
    }

    @Test fun concurrentStaleFailuresShareOneRefresh() = runBlocking {
        MockWebServer().use { server ->
            val count = 6
            val staleRequests = CountDownLatch(count)
            val logins = AtomicInteger()
            val fresh = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/auth/password-login") {
                        val generation = logins.incrementAndGet()
                        return MockResponse().addHeader("Set-Cookie", "session=$generation; Path=/")
                    }
                    if (request.getHeader("Cookie") == "session=1") {
                        staleRequests.countDown()
                        check(staleRequests.await(10, TimeUnit.SECONDS)) { "callers did not overlap" }
                        return MockResponse().setResponseCode(401)
                    }
                    if (request.path != "/api/ws?ticket=fresh-ticket") {
                        assertEquals("session=2", request.getHeader("Cookie"))
                        fresh.incrementAndGet()
                    }
                    return success(request)
                }
            }
            val config = config(server)
            ServeClient(config).login()
            val results = List(count) { async(Dispatchers.IO) { perform(config) } }.awaitAll()
            assertTrue(results.all { it })
            assertEquals(0L, staleRequests.count)
            assertEquals(2, logins.get())
            assertEquals(count, fresh.get())
        }
    }

    @Test fun rejectedRefreshSharesCooldownAcrossOperations() = runBlocking {
        MockWebServer().use { server ->
            val logins = AtomicInteger()
            val attempts = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/auth/password-login") {
                        return if (logins.incrementAndGet() == 1) MockResponse()
                        else MockResponse().setResponseCode(401).addHeader("Retry-After", "60")
                    }
                    attempts.incrementAndGet()
                    return MockResponse().setResponseCode(401)
                }
            }
            val config = config(server)
            ServeClient(config).login()
            assertFalse(perform(config))
            assertFalse(perform(config))
            assertEquals(2, logins.get())
            assertEquals(1, attempts.get())
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun operations() = listOf(arrayOf("rpc"), arrayOf("download"))
    }
}
