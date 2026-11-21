package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.TimeUnit

class ConnectionProbeOriginTest {
    @Test fun dashboardProbeUsesExplicitDashboardOriginNotChatOrigin() = runBlocking {
        MockWebServer().use { chat ->
            MockWebServer().use { dashboard ->
                chat.start()
                dashboard.start()
                dashboard.enqueue(MockResponse().setBody("""{"auth_required":false}"""))
                val config = ServerConfig(
                    id = "probe-origin", transport = Transport.HERMES_SERVE,
                    baseUrl = chat.url("/").toString(),
                    serveBaseUrl = dashboard.url("/").toString(), accessID = "dashboard-id",
                )
                assertEquals(true, ConnectionTester.current(config).ok)
                assertEquals("dashboard-id", dashboard.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("CF-Access-Client-Id"))
                assertEquals(0, chat.requestCount)
            }
        }
    }

    @Test fun dashboardProbeDoesNotFollowForeignRedirect() = runBlocking {
        MockWebServer().use { origin ->
            MockWebServer().use { external ->
                origin.start()
                external.start()
                origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", external.url("/status")))
                val config = ServerConfig(
                    id = "probe-redirect", transport = Transport.HERMES_SERVE,
                    serveBaseUrl = origin.url("/").toString(), accessID = "private-id",
                )
                assertFalse(ConnectionTester.current(config).ok)
                assertEquals("private-id", origin.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("CF-Access-Client-Id"))
                assertEquals(0, external.requestCount)
            }
        }
    }
}
