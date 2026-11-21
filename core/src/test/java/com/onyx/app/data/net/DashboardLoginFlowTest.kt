package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Transport-boundary contracts: coordination, failure fan-out, identity and stale 401 recovery.
 * Existing interceptor tests cannot observe cookie persistence or concurrent HTTP logins.
 */
class DashboardLoginFlowTest {
    private fun config(server: MockWebServer, password: String = "test-password") = ServerConfig(
        id = "test-dashboard", baseUrl = server.url("/").toString(), serveBaseUrl = server.url("/").toString(),
        serveUsername = "test-user", servePassword = password,
    )

    private fun ok() = MockResponse().setBody("{\"ok\":true}")
    private fun login(cookie: String = "session=active; Path=/") = ok().addHeader("Set-Cookie", cookie)

    @Test fun concurrentInstancesLoginOnceAndNeverProbeGateway() = runBlocking {
        MockWebServer().use { server ->
            val logins = AtomicInteger()
            val probes = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/auth/password-login" -> { logins.incrementAndGet(); login().setBodyDelay(100, TimeUnit.MILLISECONDS) }
                    "/api/sessions?limit=1" -> { probes.incrementAndGet(); MockResponse().setResponseCode(401) }
                    else -> if (request.getHeader("Cookie") == "session=active") ok() else MockResponse().setResponseCode(401)
                }
            }
            val clients = List(16) { ServeClient(config(server).copy(name = "client-$it")) }
            clients.map { async { it.request("api/profiles") } }.awaitAll()
            assertEquals(1, logins.get())
            assertEquals(0, probes.get())
        }
    }

    @Test fun rejectedCredentialsAreSharedAcrossConcurrentAndLaterClients() = runBlocking {
        MockWebServer().use { server ->
            val logins = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/auth/password-login") logins.incrementAndGet()
                    return MockResponse().setResponseCode(401).setBody("invalid credentials")
                }
            }
            val errors = List(12) { async { runCatching { ServeClient(config(server)).request("api/profiles") }.exceptionOrNull() } }.awaitAll()
            val later = runCatching { ServeClient(config(server)).login(force = true) }.exceptionOrNull()
            (errors + later).forEach { assertEquals(401, (it as TransportError.Http).status) }
            assertEquals(1, logins.get())
        }
    }

    @Test fun missingAndMalformedRetryAfterUseAtLeastSixtySecondCooldown() = runBlocking {
        listOf(null, "not-a-date", "-1").forEach { header ->
            MockWebServer().use { server ->
                val logins = AtomicInteger()
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.path != "/auth/password-login") return MockResponse().setResponseCode(401)
                        logins.incrementAndGet()
                        return MockResponse().setResponseCode(429).apply { if (header != null) addHeader("Retry-After", header) }
                    }
                }
                repeat(3) {
                    val error = runCatching { ServeClient(config(server)).login() }.exceptionOrNull()
                    assertEquals(429, (error as TransportError.Http).status)
                }
                assertEquals(1, logins.get())
            }
        }
    }

    private fun retryAfterRecovers(header: () -> String, waitMillis: Long) = runBlocking {
        MockWebServer().use { server ->
            val logins = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path != "/auth/password-login") return ok()
                    return if (logins.incrementAndGet() == 1) MockResponse().setResponseCode(429).addHeader("Retry-After", header()) else login()
                }
            }
            repeat(2) {
                val error = runCatching { ServeClient(config(server)).request("api/profiles") }.exceptionOrNull()
                assertEquals(429, (error as TransportError.Http).status)
            }
            assertEquals(1, logins.get())
            Thread.sleep(waitMillis)
            ServeClient(config(server)).request("api/profiles")
            assertEquals(2, logins.get())
        }
    }

    @Test fun retryAfterSecondsIsHonored() = retryAfterRecovers({ "1" }, 1_150)

    @Test fun retryAfterHttpDateIsHonored() = retryAfterRecovers({
        ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(2).format(DateTimeFormatter.RFC_1123_DATE_TIME)
    }, 2_150)

    @Test fun credentialsAndOriginsDoNotShareSessionsOrCooldown() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            val firstLogins = AtomicInteger()
            val secondLogins = AtomicInteger()
            val wrongCookies = AtomicInteger()
            fun dispatcher(count: AtomicInteger) = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/auth/password-login") {
                        count.incrementAndGet()
                        if (request.getHeader("Cookie") != null) wrongCookies.incrementAndGet()
                        return if (request.body.readUtf8().contains("rejected-password")) MockResponse().setResponseCode(429) else login()
                    }
                    return ok()
                }
            }
            first.dispatcher = dispatcher(firstLogins)
            second.dispatcher = dispatcher(secondLogins)
            ServeClient(config(first)).request("api/profiles")
            val rejected = runCatching { ServeClient(config(first, "rejected-password")).request("api/profiles") }.exceptionOrNull()
            assertEquals(429, (rejected as TransportError.Http).status)
            ServeClient(config(first)).request("api/profiles")
            ServeClient(config(second)).request("api/profiles")
            assertEquals(2, firstLogins.get())
            assertEquals(1, secondLogins.get())
            assertEquals(0, wrongCookies.get())
        } }
    }

    @Test fun lateUnauthorizedResponseDoesNotInvalidateNewerSession() = runBlocking {
        MockWebServer().use { server ->
            val logins = AtomicInteger()
            val delayedStarted = CountDownLatch(1)
            val renewed = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/auth/password-login") {
                        val n = logins.incrementAndGet()
                        if (n == 2) renewed.countDown()
                        return login("session=$n; Path=/")
                    }
                    if (request.getHeader("Cookie") == "session=1") {
                        if (request.path == "/api/delayed") {
                            delayedStarted.countDown()
                            check(renewed.await(10, TimeUnit.SECONDS))
                            return MockResponse().setResponseCode(401).setHeadersDelay(150, TimeUnit.MILLISECONDS)
                        }
                        check(delayedStarted.await(10, TimeUnit.SECONDS))
                        return MockResponse().setResponseCode(401)
                    }
                    return ok()
                }
            }
            val cfg = config(server)
            ServeClient(cfg).login()
            listOf(async { ServeClient(cfg).request("api/delayed") }, async { ServeClient(cfg).delete("api/expired") }).awaitAll()
            assertEquals(2, logins.get())
        }
    }

    @Test fun keyOnlyNeverLogsInEvenWhenRequestIsUnauthorized() = runBlocking {
        MockWebServer().use { server ->
            val requests = AtomicInteger()
            val logins = AtomicInteger()
            val missingKeys = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests.incrementAndGet()
                    if (request.path == "/auth/password-login") logins.incrementAndGet()
                    if (request.getHeader("Authorization") != "Bearer test-install-key") missingKeys.incrementAndGet()
                    return MockResponse().setResponseCode(401)
                }
            }
            val cfg = config(server).copy(serveUsername = "", servePassword = "", apiKey = "test-install-key")
            val error = runCatching { ServeClient(cfg).request("api/profiles") }.exceptionOrNull()
            assertEquals(401, (error as TransportError.Http).status)
            assertEquals(0, logins.get())
            assertEquals(0, missingKeys.get())
            assertEquals(2, requests.get())
        }
    }

    @Test fun repeatedUnauthorizedIsBoundedForRequestAndDelete() = runBlocking {
        listOf(false, true).forEach { delete ->
            MockWebServer().use { server ->
                val logins = AtomicInteger()
                val requests = AtomicInteger()
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.path == "/auth/password-login") { logins.incrementAndGet(); return login() }
                        requests.incrementAndGet()
                        return MockResponse().setResponseCode(401)
                    }
                }
                val error = runCatching {
                    val client = ServeClient(config(server))
                    if (delete) client.delete("api/profiles/x") else client.request("api/profiles")
                }.exceptionOrNull()
                assertEquals(401, (error as TransportError.Http).status)
                assertEquals(2, logins.get())
                assertEquals(2, requests.get())
            }
        }
    }
}
