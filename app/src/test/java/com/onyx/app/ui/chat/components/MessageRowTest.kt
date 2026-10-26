package com.onyx.app.ui.chat.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRowTest {
    @Test
    fun `les metriques utilisent caption2 et des chiffres tabulaires`() {
        val theme = ResolvedTheme.fallback()
        val style = replyMetricsTextStyle(theme)

        assertEquals(11.sp, style.fontSize)
        assertEquals("tnum", style.fontFeatureSettings)
        assertEquals(theme.caption2.fontFamily, style.fontFamily)
        assertEquals(theme.tertiaryText, style.color)
    }

    @Test
    fun `la rangee d actions garde le retrait et la taille de glyphe `() {
        assertEquals((-7).dp, ReplyActionRowLeading)
        assertEquals(15.dp, ReplyActionGlyphSize)
    }

    @Test
    fun `le retrait negatif utilise offset car padding le refuse`() {
        val source = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
        assertTrue(source.contains("Modifier.offset(x = ReplyActionRowLeading)"))
        assertFalse(source.contains("Modifier.padding(start = ReplyActionRowLeading)"))
    }

    @Test
    fun `seul le prefixe du prompt est en gras`() {
        val prompt = promptLineText("❯ ", "pwd")
        val bold = prompt.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }

        assertEquals("❯ pwd", prompt.text)
        assertEquals(1, bold.size)
        assertEquals(0, bold.single().start)
        assertEquals(2, bold.single().end)
    }
}
