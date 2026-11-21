package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class AttachmentCredentialOriginTest {
    @get:Rule val files = TemporaryFolder()

    private fun config(server: MockWebServer) = ServerConfig(
        id = "attachment-origin-test",
        baseUrl = server.url("/").toString(),
        apiKey = "test-gateway-secret",
    )

    @Test fun externalAttachmentDoesNotReceiveGatewayKey() = runBlocking {
        MockWebServer().use { gateway ->
            MockWebServer().use { external ->
                gateway.start()
                external.start()
                external.enqueue(MockResponse().setBody("external attachment"))
                val destination = files.newFile()
                ServeFileDownload(config(gateway)).toUrl(external.url("/report.pdf").toString(), destination)
                assertEquals("external attachment", destination.readText())
                assertNull(external.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            }
        }
    }

    @Test fun sameOriginAttachmentReceivesGatewayKey() = runBlocking {
        MockWebServer().use { gateway ->
            gateway.start()
            gateway.enqueue(MockResponse().setBody("private attachment"))
            val destination = files.newFile()
            ServeFileDownload(config(gateway)).toUrl(gateway.url("/report.pdf").toString(), destination)
            assertEquals("private attachment", destination.readText())
            assertEquals("Bearer test-gateway-secret", gateway.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        }
    }

    @Test fun crossOriginRedirectDoesNotReceiveGatewayKey() = runBlocking {
        MockWebServer().use { gateway ->
            MockWebServer().use { external ->
                gateway.start()
                external.start()
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location", external.url("/report.pdf")))
                external.enqueue(MockResponse().setBody("redirected attachment"))
                val destination = files.newFile()
                ServeFileDownload(config(gateway)).toUrl(gateway.url("/download").toString(), destination)
                assertEquals("redirected attachment", destination.readText())
                assertEquals("Bearer test-gateway-secret", gateway.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Authorization"))
                assertNull(external.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            }
        }
    }

    @Test fun explicitAuthorizationIsPreservedOnConfiguredOrigin() {
        MockWebServer().use { gateway ->
            gateway.start()
            gateway.enqueue(MockResponse().setBody("ok"))
            val request = Request.Builder().url(gateway.url("/api/status"))
                .header("Authorization", "Bearer explicit").build()
            ServeClient.clientFor(config(gateway)).newCall(request).execute().close()
            assertEquals("Bearer explicit", gateway.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        }
    }
}
