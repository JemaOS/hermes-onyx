package com.onyx.app.data.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La lecture des balises d'une page: Open Graph d'abord, puis les replis de Messages. */
class LinkPreviewMetaTest {

    @Test
    fun `les balises open graph priment sur le titre de la page`() {
        val meta = LinkPreviewMeta.parse("""<title>Doc</title><meta property="og:title" content="OG Title">""")
        assertEquals("OG Title", meta.title)
    }

    @Test
    fun `le titre de la page sert de repli, entites decodees`() {
        val meta = LinkPreviewMeta.parse("<title>Tom &amp; Jerry</title>")
        assertEquals("Tom & Jerry", meta.title)
    }

    @Test
    fun `l image open graph prime sur l icone`() {
        val meta = LinkPreviewMeta.parse(
            """<meta property="og:image" content="/og.png"><link rel="icon" href="/favicon.png">""",
        )
        assertEquals("/og.png", meta.imageUrl)
    }

    @Test
    fun `l icone sert de repli a l image`() {
        val meta = LinkPreviewMeta.parse("""<link rel="apple-touch-icon" href="/icon.png">""")
        assertEquals("/icon.png", meta.imageUrl)
    }

    @Test
    fun `un titre vide ne donne pas de titre`() {
        assertNull(LinkPreviewMeta.parse("<title>   </title>").title)
    }

    @Test
    fun `les attributs se lisent quel que soit leur ordre ou leurs guillemets`() {
        val single = LinkPreviewMeta.parse("""<meta content='Single' name='og:title'>""")
        assertEquals("Single", single.title)
    }
}
