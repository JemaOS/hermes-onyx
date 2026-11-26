package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashboardDownloadIsolationTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun config(server: MockWebServer) = ServerConfig(
        id = "isolation", baseUrl = server.url("/").toString(),
        serveBaseUrl = server.url("/").toString(),
        serveUsername = "user", servePassword = "password",
    )

    @Test fun arbitraryExternalDownloadNeverLogsInRetriesOrSendsDashboardBearer() = runBlocking {
        MockWebServer().use { dashboard ->
            MockWebServer().use { external ->
                external.enqueue(MockResponse().setResponseCode(401))
                val config = config(dashboard).copy(apiKey = "dashboard-key", serveUsername = "", servePassword = "")
                try {
                    ServeFileDownload(config).toUrl(external.url("/file").toString(), temporary.newFile())
                    fail("expected external rejection")
                } catch (error: TransportError.Http) {
                    assertEquals(401, error.status)
                }
                val request = external.takeRequest()
                assertNull(request.getHeader("Authorization"))
                assertNull(request.getHeader("Cookie"))
                assertEquals(OnyxHttp.USER_AGENT, request.getHeader("User-Agent"))
                assertEquals(1, external.requestCount)
                assertEquals(0, dashboard.requestCount)
            }
        }
    }

    @Test fun explicitAuthorizationIsPreservedAndDoesNotTriggerPasswordRecovery() = runBlocking {
        MockWebServer().use { dashboard ->
            dashboard.enqueue(MockResponse().setResponseCode(401))
            val request = Request.Builder().url(dashboard.url("/api/files/download"))
                .header("Authorization", "Bearer explicit-token")
                .header("User-Agent", "explicit-agent").build()
            ServeClient(config(dashboard)).executeAuthenticated(request).use {
                assertEquals(401, it.code)
            }
            val received = dashboard.takeRequest()
            assertEquals("Bearer explicit-token", received.getHeader("Authorization"))
            assertEquals("explicit-agent", received.getHeader("User-Agent"))
            assertEquals(1, dashboard.requestCount)
        }
    }

    @Test fun foreignOriginCannotTriggerPasswordRecovery() = runBlocking {
        MockWebServer().use { dashboard ->
            MockWebServer().use { external ->
                external.enqueue(MockResponse().setResponseCode(401))
                val request = Request.Builder().url(external.url("/file")).build()
                ServeClient(config(dashboard)).executeAuthenticated(request).use {
                    assertEquals(401, it.code)
                }
                assertNull(external.takeRequest().getHeader("Authorization"))
                assertEquals(1, external.requestCount)
                assertEquals(0, dashboard.requestCount)
            }
        }
    }
}
