package com.onyx.app.ui.rich

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownBlocksTest {
    @Test
    fun `table GFM est decoupee avec alignements et cellules`() {
        val blocks = splitMarkdown(
            """
            | Nom | Qté | Prix |
            |:----|:---:|-----:|
            | Thé \| vert | 2 | 3,50 |
            """.trimIndent(),
        )

        val table = blocks.single().table!!
        assertEquals(listOf("Nom", "Qté", "Prix"), table.header)
        assertEquals(listOf(listOf("Thé | vert", "2", "3,50")), table.rows)
        assertEquals(
            listOf(TableAlignment.LEADING, TableAlignment.CENTER, TableAlignment.TRAILING),
            table.alignments,
        )
        assertEquals(MarkdownRenderer.TABLE, blocks.single().renderer(trailing = false))
    }

    @Test
    fun `citation conserve son Markdown pour un rendu recursif`() {
        val blocks = splitMarkdown("> **Important**\n>\n> - [x] fait")

        assertEquals("**Important**\n\n- [x] fait", blocks.single().quote)
        assertEquals(MarkdownRenderer.QUOTE, blocks.single().renderer(trailing = false))
    }

    @Test
    fun `cases a cocher restent dans le bloc de prose Markwon`() {
        val blocks = splitMarkdown("- [ ] ouvert\n- [x] terminé\n- [X] majuscule")

        assertEquals("- [ ] ouvert\n- [x] terminé\n- [X] majuscule", blocks.single().prose)
        assertEquals(MarkdownRenderer.PROSE, blocks.single().renderer(trailing = false))
    }

    @Test
    fun `formules dollar et crochets sont routees vers KaTeX`() {
        val blocks = splitMarkdown("\$\$E = mc^2\$\$\n\n\\[ a^2 + b^2 = c^2 \\]")

        assertEquals(listOf("E = mc^2", "a^2 + b^2 = c^2"), blocks.map { it.math })
        assertEquals(
            listOf(MarkdownRenderer.MATH, MarkdownRenderer.MATH),
            blocks.map { it.renderer(trailing = false) },
        )
    }

    @Test
    fun `diagramme Mermaid et code colore ont leurs rendus dedies`() {
        val blocks = splitMarkdown(
            """
            ```mermaid
            graph TD
              A[Départ] --> B{OK ?}
            ```

            ```kotlin
            fun answer(): Int = 42
            ```
            """.trimIndent(),
        )

        assertEquals(true, blocks[0].isMermaid)
        assertEquals("kotlin", blocks[1].language)
        assertEquals(MarkdownRenderer.MERMAID, blocks[0].renderer(trailing = false))
        assertEquals(MarkdownRenderer.CODE, blocks[1].renderer(trailing = false))
        assertNull(blocks[1].math)
    }

    @Test
    fun `bloc riche en cours reste du code jusqu'a la fin du streaming`() {
        val mermaid = splitMarkdown("```mermaid\ngraph TD; A-->B;").single()
        val math = splitMarkdown("$$\n\\int_0^1 x\\,dx").single()
        val html = splitMarkdown("```html\n<title>Page</title>").single()

        assertEquals(MarkdownRenderer.LIVE_CODE, mermaid.renderer(trailing = true))
        assertEquals(MarkdownRenderer.LIVE_CODE, math.renderer(trailing = true))
        assertEquals(MarkdownRenderer.LIVE_CODE, html.renderer(trailing = true))
        assertEquals(MarkdownRenderer.MERMAID, mermaid.renderer(trailing = false))
        assertEquals(MarkdownRenderer.MATH, math.renderer(trailing = false))
        assertEquals(MarkdownRenderer.AGENT_PAGE, html.renderer(trailing = false))
    }
}
