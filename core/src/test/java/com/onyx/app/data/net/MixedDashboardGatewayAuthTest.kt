package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.net.URI

class MixedDashboardGatewayAuthTest {
    private fun config(dashboard: MockWebServer, api: MockWebServer = dashboard) = ServerConfig(
        id = "mixed-auth", baseUrl = api.url("/v1").toString(),
        serveBaseUrl = dashboard.url("/").toString(), apiKey = "gateway-key",
        serveUsername = "dashboard-user", servePassword = "dashboard-password",
    )

    @Test fun passwordLoginAndGatewayConversationsWorkTogether() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/auth/password-login") {
                        assertNull(request.getHeader("Authorization"))
                        assertTrue(request.body.readUtf8().contains("dashboard-password"))
                        return MockResponse().setBody("{}").addHeader("Set-Cookie", "session=active; Path=/")
                    }
                    assertEquals("session=active", request.getHeader("Cookie"))
                    return if (request.path!!.startsWith("/api/sessions")) {
                        if (request.getHeader("Authorization") == "Bearer gateway-key")
                            MockResponse().setBody("{\"sessions\":[]}")
                        else MockResponse().setResponseCode(401)
                    } else {
                        assertNull(request.getHeader("Authorization"))
                        MockResponse().setBody("{\"profiles\":[]}")
                    }
                }
            }
            val client = ServeClient(config(server))
            client.request("api/profiles")
            assertEquals(Json.parseToJsonElement("{\"sessions\":[]}"), client.request("api/sessions", query = mapOf("limit" to "40")))
            client.request("api/sessions/conversation/messages")
            client.delete("api/sessions/conversation")
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun bearerIsLimitedToExactGatewaySessionRouteAndDescendants() {
        MockWebServer().use { server ->
            val client = ServeClient.clientFor(config(server))
            val routes = mapOf(
                "/api/sessions" to true, "/api/sessions?limit=1" to true,
                "/api/sessions/" to true, "/api/sessions/id/messages?limit=2" to true,
                "/api/sessions-evil" to false, "/api/sessionsOther/id" to false,
                "/api/profiles?next=/api/sessions" to false, "/auth/password-login" to false,
                "/v1/chat/completions" to false, "/other/api/sessions" to false,
                "/api/sessions%2Fevil" to false,
            )
            routes.forEach { (path, gateway) ->
                server.enqueue(MockResponse().setBody("{}"))
                client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
                assertEquals(path, if (gateway) "Bearer gateway-key" else null,
                    server.takeRequest().getHeader("Authorization"))
            }
        }
    }

    @Test fun distinctDashboardOriginDoesNotReceiveGatewayKeyAndForeignHostGetsNoCredentials() = runBlocking {
        MockWebServer().use { dashboard -> MockWebServer().use { gateway -> MockWebServer().use { foreign ->
            val cfg = config(dashboard, gateway)
            dashboard.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "session=active; Path=/"))
            dashboard.enqueue(MockResponse().setBody("{}"))
            ServeClient(cfg).request("api/sessions", query = mapOf("limit" to "1"))
            assertNull(dashboard.takeRequest().getHeader("Authorization"))
            val session = dashboard.takeRequest()
            assertNull(session.getHeader("Authorization"))
            assertEquals("session=active", session.getHeader("Cookie"))
            foreign.enqueue(MockResponse().setBody("{}"))
            ServeClient.clientFor(cfg).newCall(Request.Builder().url(foreign.url("/api/sessions")).build()).execute().close()
            val foreignRequest = foreign.takeRequest()
            assertNull(foreignRequest.getHeader("Authorization"))
            assertNull(foreignRequest.getHeader("Cookie"))
            assertEquals(0, gateway.requestCount)
        } } }
    }

    @Test fun distinctGatewayMessageAndStopCallersStillUseGatewayNotDashboard() = runBlocking {
        MockWebServer().use { dashboard -> MockWebServer().use { gateway ->
            val cfg = config(dashboard, gateway)
            gateway.enqueue(MockResponse().setBody("{\"messages\":[{\"role\":\"assistant\",\"content\":\"gateway reply\"}]}"))
            assertEquals("gateway reply", ServeSessionMessages(cfg).rows("conversation").single().text)
            gateway.enqueue(MockResponse().setBody("{}"))
            assertTrue(ServeRunStop(cfg).stop("run"))
            listOf("/api/sessions/conversation/messages", "/v1/runs/run/stop").forEach { path ->
                val request = gateway.takeRequest()
                assertEquals(path, URI(request.path!!).path)
                assertEquals("Bearer gateway-key", request.getHeader("Authorization"))
                assertNull(request.getHeader("Cookie"))
            }
            assertEquals(0, dashboard.requestCount)
        } }
    }

    @Test fun passwordWithoutGatewayKeyNeverAddsBearer() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            ServeClient.clientFor(config(server).copy(apiKey = "")).newCall(
                Request.Builder().url(server.url("/api/sessions")).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun missingApiBaseDoesNotAssumeDashboardIsGateway() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            val cfg = config(server).copy(baseUrl = "")
            ServeClient.clientFor(cfg).newCall(Request.Builder().url(server.url("/api/sessions")).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun gatewayRedirectDoesNotCarryBearerToDashboardOrCredentialsToForeignOrigin() = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { foreign ->
            val cfg = config(server)
            server.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "session=active; Path=/"))
            ServeClient(cfg).login()
            assertNull(server.takeRequest().getHeader("Authorization"))

            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/api/profiles")))
            server.enqueue(MockResponse().setBody("{}"))
            ServeClient.clientFor(cfg).newCall(Request.Builder().url(server.url("/api/sessions/id")).build())
                .execute().use { assertEquals(200, it.code) }
            assertEquals("Bearer gateway-key", server.takeRequest().getHeader("Authorization"))
            val redirected = server.takeRequest()
            assertNull(redirected.getHeader("Authorization"))
            assertEquals("session=active", redirected.getHeader("Cookie"))

            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", foreign.url("/api/sessions")))
            foreign.enqueue(MockResponse().setBody("{}"))
            ServeClient.clientFor(cfg).newCall(Request.Builder().url(server.url("/api/sessions/id")).build())
                .execute().use { assertEquals(200, it.code) }
            assertEquals("Bearer gateway-key", server.takeRequest().getHeader("Authorization"))
            val external = foreign.takeRequest(1, TimeUnit.SECONDS)!!
            assertNull(external.getHeader("Authorization"))
            assertNull(external.getHeader("Cookie"))
        } }
    }
}
