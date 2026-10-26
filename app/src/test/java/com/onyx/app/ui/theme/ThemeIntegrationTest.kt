package com.onyx.app.ui.theme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeIntegrationTest {
    @Test
    fun `la commande d une approbation utilise le fond tertiaire systeme`() {
        val source = source("ui/chat/components/ApprovalCard.kt")

        assertTrue(source.contains(".background(theme.tertiarySurfaceColor, RoundedCornerShape(8.dp))"))
        assertFalse(source.contains(".background(theme.surfaceColor, RoundedCornerShape(8.dp))"))
    }

    @Test
    fun `le widget retombe sur Messages quand les reglages sont indisponibles`() {
        val source = source("widget/OnyxWidget.kt")

        assertTrue(source.contains("theme = OnyxTheme.STANDARD"))
        assertTrue(source.contains("?: OnyxTheme.STANDARD"))
        assertFalse(source.contains("OnyxTheme.GITHUB_DARK"))
    }

    @Test
    fun `la prose Markwon garde la sans systeme sans police nommee`() {
        val source = source("ui/chat/components/MarkdownText.kt")

        assertTrue(source.contains("else -> Typeface.SANS_SERIF"))
        assertFalse(source.contains("else -> runCatching { Typeface.createFromAsset(context.assets, OnyxFonts.INTER_REGULAR) }"))
    }

    @Test
    fun `le chat de groupe reutilise les bulles et prompts de MessageRow`() {
        val source = source("ui/bots/GroupChatScreen.kt")

        assertTrue(source.contains("MessageRow("))
        assertFalse(source.contains(".background(theme.userBubbleColor, RoundedCornerShape(16.dp))"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/onyx/app", path).readText()
}
