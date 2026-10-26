package com.onyx.app.data.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Quel lien reçoit une fiche: les mêmes cas que `LinkPreviewTests` 
 * — le premier lien web, jamais dans du code, jamais une image,
 * jamais autre chose qu'http ou https.
 */
class LinkPreviewLinksTest {

    @Test
    fun `le premier lien web recoit la fiche`() {
        assertEquals(
            "https://example.com/a",
            LinkPreviewLinks.first("See https://example.com/a and https://example.org/b"),
        )
        assertEquals(
            "https://example.com/kitchen",
            LinkPreviewLinks.first("Details are in the [shared plan](https://example.com/kitchen)."),
        )
        assertNull(LinkPreviewLinks.first("No link here, just words."))
    }

    @Test
    fun `ni dans le code, ni une image, ni une autre sorte d adresse`() {
        assertNull(LinkPreviewLinks.first("Run `curl https://example.com/install.sh` first"))
        assertNull(LinkPreviewLinks.first("```\nfetch('https://example.com/api')\n```"))
        assertNull(LinkPreviewLinks.first("![a chart](https://example.com/chart.png)"))
        assertNull(LinkPreviewLinks.first("Write to mailto:someone@example.com or ftp://example.com/file"))
        assertEquals(
            "https://example.com/page",
            LinkPreviewLinks.first("![chart](https://example.com/c.png) and the [page](https://example.com/page)"),
        )
    }

    @Test
    fun `le site se lit sans www`() {
        assertEquals("example.com", LinkPreviews.site("www.example.com"))
        assertEquals("trails.example.com", LinkPreviews.site("trails.example.com"))
    }

    @Test
    fun `l hote se lit sans port`() {
        assertEquals("example.com", LinkPreviewLinks.host("https://example.com:8443/a"))
        assertEquals("www.example.com", LinkPreviewLinks.host("http://www.example.com/"))
    }
}
