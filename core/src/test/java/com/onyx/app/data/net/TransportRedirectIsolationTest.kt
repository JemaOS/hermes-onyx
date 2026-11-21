package com.onyx.app.data.net

import com.onyx.app.data.model.CustomHeader
import com.onyx.app.data.model.ServerConfig
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class TransportRedirectIsolationTest {
    private fun config(server: MockWebServer) = ServerConfig(
        id = "redirect-test",
        baseUrl = server.url("/").toString(),
        apiKey = "gateway-secret",
        hermesProfile = "redirect-test",
        accessID = "access-id",
        accessSecret = "access-secret",
        customHeaders = listOf(CustomHeader("X-Private-Token", "custom-secret")),
    )

    @Test fun gatewayHeadersAreScopedOnEveryRedirectHop() {
        MockWebServer().use { gateway ->
            MockWebServer().use { foreign ->
                gateway.start()
                foreign.start()
                val config = config(gateway)
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location", gateway.url("/same")))
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location", foreign.url("/external")))
                foreign.enqueue(MockResponse().setResponseCode(302).addHeader("Location", gateway.url("/return")))
                gateway.enqueue(MockResponse().setBody("done"))
                OnyxHttp.clientFor(config).newCall(Request.Builder().url(gateway.url("/start"))
                    .header("Authorization", "Bearer caller-secret").build()).execute().use {
                    assertEquals("done", it.body!!.string())
                }
                repeat(3) { index ->
                    val request = gateway.takeRequest(2, TimeUnit.SECONDS)!!
                    assertEquals("access-id", request.getHeader("CF-Access-Client-Id"))
                    assertEquals("access-secret", request.getHeader("CF-Access-Client-Secret"))
                    assertEquals("custom-secret", request.getHeader("X-Private-Token"))
                    assertEquals("redirect-test", request.getHeader(OnyxHttp.PROFILE_HEADER))
                    if (index < 2) assertEquals("Bearer caller-secret", request.getHeader("Authorization"))
                }
                val request = foreign.takeRequest(2, TimeUnit.SECONDS)!!
                for (name in listOf("Authorization", "CF-Access-Client-Id", "CF-Access-Client-Secret",
                    "X-Private-Token", OnyxHttp.PROFILE_HEADER)) assertNull(name, request.getHeader(name))
            }
        }
    }

    @Test fun foreign401DoesNotDisableGatewayProfile() {
        MockWebServer().use { gateway ->
            MockWebServer().use { foreign ->
                gateway.start()
                foreign.start()
                val client = OnyxHttp.clientFor(config(gateway))
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location", foreign.url("/")))
                foreign.enqueue(MockResponse().setResponseCode(401))
                client.newCall(Request.Builder().url(gateway.url("/")).build()).execute().use { assertEquals(401, it.code) }
                gateway.enqueue(MockResponse().setBody("ok"))
                client.newCall(Request.Builder().url(gateway.url("/next")).build()).execute().close()
                assertEquals(2, gateway.requestCount)
                gateway.takeRequest()
                assertEquals("redirect-test", gateway.takeRequest().getHeader(OnyxHttp.PROFILE_HEADER))
            }
        }
    }

    @Test fun serveKeyIsPreservedLocallyAndStrippedOnForeignRedirect() {
        MockWebServer().use { gateway ->
            MockWebServer().use { foreign ->
                gateway.start()
                foreign.start()
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location", gateway.url("/same")))
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location", foreign.url("/external")))
                foreign.enqueue(MockResponse().setBody("ok"))
                ServeClient.clientFor(config(gateway)).newCall(Request.Builder().url(gateway.url("/start"))
                    .header("CF-Access-Client-Id", "access-id")
                    .header("CF-Access-Client-Secret", "access-secret")
                    .header("X-Private-Token", "custom-secret")
                    .header(OnyxHttp.PROFILE_HEADER, "redirect-test").build()).execute().close()
                repeat(2) {
                    val request = gateway.takeRequest(2, TimeUnit.SECONDS)!!
                    assertEquals("Bearer gateway-secret", request.getHeader("Authorization"))
                    assertEquals("access-id", request.getHeader("CF-Access-Client-Id"))
                    assertEquals("access-secret", request.getHeader("CF-Access-Client-Secret"))
                    assertEquals("redirect-test", request.getHeader(OnyxHttp.PROFILE_HEADER))
                    assertEquals("custom-secret", request.getHeader("X-Private-Token"))
                }
                val request = foreign.takeRequest(2, TimeUnit.SECONDS)!!
                for (name in listOf("Authorization", "CF-Access-Client-Id", "CF-Access-Client-Secret",
                    "X-Private-Token", OnyxHttp.PROFILE_HEADER)) assertNull(name, request.getHeader(name))
            }
        }
    }

    @Test fun directForeignRequestsDoNotReceiveAutomaticGatewayHeaders() {
        MockWebServer().use { gateway ->
            MockWebServer().use { foreign ->
                gateway.start()
                foreign.start()
                foreign.enqueue(MockResponse().setBody("ok"))
                OnyxHttp.clientFor(config(gateway)).newCall(Request.Builder().url(foreign.url("/"))
                    .header("X-Private-Token", "caller-copy")
                    .header(OnyxHttp.PROFILE_HEADER, "caller-profile").build()).execute().close()
                val request = foreign.takeRequest(2, TimeUnit.SECONDS)!!
                for (name in listOf("CF-Access-Client-Id", "CF-Access-Client-Secret",
                    "X-Private-Token", OnyxHttp.PROFILE_HEADER)) assertNull(name, request.getHeader(name))
                assertEquals(0, gateway.requestCount)
            }
        }
    }

    @Test fun customProfileHeaderDoesNotUndo401Fallback() {
        MockWebServer().use { gateway ->
            gateway.start()
            val config = config(gateway).copy(customHeaders = listOf(
                CustomHeader(OnyxHttp.PROFILE_HEADER, "custom-profile"),
                CustomHeader("X-Private-Token", "custom-secret"),
            ))
            gateway.enqueue(MockResponse().setResponseCode(401))
            gateway.enqueue(MockResponse().setBody("ok"))
            OnyxHttp.clientFor(config).newCall(Request.Builder().url(gateway.url("/"))
                .build()).execute().use { assertEquals(200, it.code) }
            assertEquals("redirect-test", gateway.takeRequest().getHeader(OnyxHttp.PROFILE_HEADER))
            val retry = gateway.takeRequest()
            assertNull(retry.getHeader(OnyxHttp.PROFILE_HEADER))
            assertEquals("access-secret", retry.getHeader("CF-Access-Client-Secret"))
            assertEquals("custom-secret", retry.getHeader("X-Private-Token"))
        }
    }

    @Test fun clientsDoNotAutomaticallyChangeScheme() {
        MockWebServer().use { gateway ->
            gateway.start()
            val config = config(gateway)
            val clients = listOf(OnyxHttp.clientFor(config), OnyxHttp.shortClient(), ServeClient.clientFor(config))
            for (client in clients) {
                assertFalse(client.followSslRedirects)
                assertTrue(client.followRedirects)
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://localhost:${gateway.port}/secure"))
                client.newCall(Request.Builder().url(gateway.url("/plain")).build()).execute().use {
                    assertEquals(302, it.code)
                }
            }
            assertEquals(3, gateway.requestCount)
        }
    }

    @Test fun cookiesArePartitionedBySchemeHostAndPort() {
        ServeCookieJar.forgetAll()
        try {
            val origin = "http://localhost:18432/".toHttpUrl()
            val cookie = Cookie.parse(origin, "session=secret; Path=/")!!
            ServeCookieJar.saveFromResponse(origin, listOf(cookie))
            assertEquals(listOf(cookie), ServeCookieJar.loadForRequest(origin))
            for (other in listOf("https://localhost:18432/", "http://localhost:18433/", "http://127.0.0.1:18432/")) {
                assertTrue(other, ServeCookieJar.loadForRequest(other.toHttpUrl()).isEmpty())
            }
            val secureOrigin = "https://localhost:18432/".toHttpUrl()
            val secureCookie = Cookie.parse(secureOrigin, "session=tls-only; Path=/")!!
            ServeCookieJar.saveFromResponse(secureOrigin, listOf(secureCookie))
            assertEquals(listOf(cookie), ServeCookieJar.loadForRequest(origin))
            assertEquals(listOf(secureCookie), ServeCookieJar.loadForRequest(secureOrigin))
        } finally {
            ServeCookieJar.forgetAll()
        }
    }
}
