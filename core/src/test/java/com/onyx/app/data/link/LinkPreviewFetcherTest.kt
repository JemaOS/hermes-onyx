package com.onyx.app.data.link

import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La lecture d'une page par le téléphone (`LinkPreviews.fromSite`), éprouvée sur la JVM: une page
 * sans métadonnées, une image absente, une page lente, un contenu trop gros, et le suivi d'une
 * redirection.
 */
class LinkPreviewFetcherTest {

    @After
    fun restore() {
        LinkPreviewFetcher.client = LinkPreviewFetcher.defaultClient(LinkPreviewFetcher.TIMEOUT_SECONDS)
        LinkPreviewFetcher.compress = { AndroidImageThumbnail.jpeg(it, LinkPreviewFetcher.MAX_IMAGE_SIDE) }
    }

    private fun page(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .setBody(body)

    @Test
    fun `une page sans metadonnees donne une fiche qui nomme le site`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(page("<html><body>Hello</body></html>"))
        server.start()
        try {
            val preview = requireNotNull(LinkPreviewFetcher.fromSite(server.url("/page").toString()))
            assertNull(preview.title)
            assertNull(preview.image)
            assertEquals("localhost", preview.site)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `une page sans image donne une fiche sans image`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(page("""<meta property="og:title" content="Falls Loop trail">"""))
        server.start()
        try {
            val preview = requireNotNull(LinkPreviewFetcher.fromSite(server.url("/trail").toString()))
            assertEquals("Falls Loop trail", preview.title)
            assertNull(preview.image)
            assertEquals("localhost", preview.site)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `l image de la page est telechargee, ramenee en vignette et encodee`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(page("""<meta property="og:title" content="Trail"><meta property="og:image" content="/pic.png">"""))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(byteArrayOf(1, 2, 3, 4))),
        )
        server.start()
        try {
            // `android.graphics` n'existe pas sur la JVM: la mise à l'échelle est injectée.
            LinkPreviewFetcher.compress = { byteArrayOf(9, 9) }
            val preview = requireNotNull(LinkPreviewFetcher.fromSite(server.url("/page").toString()))
            assertEquals("Trail", preview.title)
            assertArrayEquals(byteArrayOf(9, 9), requireNotNull(preview.imageBytes))
            assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(9, 9)), preview.image)
            server.takeRequest()
            assertEquals("/pic.png", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `une page lente ne donne pas de fiche`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(page("<meta property=\"og:title\" content=\"Late\">").setBodyDelay(3, TimeUnit.SECONDS))
        server.start()
        try {
            LinkPreviewFetcher.client = LinkPreviewFetcher.defaultClient(1)
            assertNull(LinkPreviewFetcher.fromSite(server.url("/slow").toString()))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `un contenu trop gros n est pas lu`() = runBlocking {
        val server = MockWebServer()
        val huge = "<html><body><meta property=\"og:title\" content=\"Big\">" +
            "x".repeat(LinkPreviewFetcher.MAX_HTML_BYTES) + "</body></html>"
        server.enqueue(page(huge))
        server.start()
        try {
            assertNull(LinkPreviewFetcher.fromSite(server.url("/big").toString()))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `une redirection est suivie et la page finale est lue`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/final"))
        server.enqueue(page("""<meta property="og:title" content="After redirect">"""))
        server.start()
        try {
            val preview = requireNotNull(LinkPreviewFetcher.fromSite(server.url("/start").toString()))
            assertEquals("After redirect", preview.title)
        } finally {
            server.shutdown()
        }
    }
}
