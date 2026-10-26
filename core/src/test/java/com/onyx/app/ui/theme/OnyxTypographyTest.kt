package com.onyx.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OnyxTypographyTest {
    @Test
    fun `les familles systeme et embarquees gardent les roles de Theme swift`() {
        val fonts = distinctBundledFonts()

        assertEquals(FontFamily.SansSerif, fonts.familyFor(FontDesign.DEFAULT))
        assertEquals(FontFamily.Monospace, fonts.familyFor(FontDesign.MONOSPACED))
        assertSame(fonts.inter, fonts.familyFor(FontDesign.DEFAULT, OnyxFonts.INTER_REGULAR))
        assertSame(fonts.sourceCode, fonts.familyFor(FontDesign.MONOSPACED, OnyxFonts.SOURCE_CODE_MEDIUM))

        val standard = ResolvedTheme.resolve(OnyxTheme.STANDARD, false, fonts)
        assertEquals(FontFamily.SansSerif, standard.messageFont.fontFamily)
        assertEquals(FontFamily.Monospace, standard.codeFont.fontFamily)
        assertEquals(FontWeight.SemiBold, standard.headingFont(2).fontWeight)

        val hermes = ResolvedTheme.resolve(OnyxTheme.GITHUB_DARK, true, fonts)
        assertSame(fonts.inter, hermes.messageFont.fontFamily)
        assertEquals(FontFamily.Monospace, hermes.codeFont.fontFamily)

        val terminal = ResolvedTheme.resolve(OnyxTheme.TERMINAL, false, fonts)
        assertSame(fonts.sourceCode, terminal.messageFont.fontFamily)
        assertSame(fonts.sourceCode, terminal.codeFont.fontFamily)

        val code = ResolvedTheme.resolve(OnyxTheme.CLAUDE_CODE, false, fonts)
        assertEquals(FontFamily.SansSerif, code.userFont.fontFamily)
        assertEquals(FontFamily.SansSerif, code.title2.fontFamily)

        val paper = ResolvedTheme.resolve(OnyxTheme.PAPER, false, fonts)
        assertEquals(FontFamily.SansSerif, paper.title2.fontFamily)
        assertEquals(FontFamily.SansSerif, paper.tableFont.fontFamily)
        assertEquals(FontFamily.SansSerif, code.tableFont.fontFamily)
        assertSame(fonts.inter, hermes.tableFont.fontFamily)
        assertSame(fonts.sourceCode, terminal.tableFont.fontFamily)

        assertEquals(17.sp, standard.messageFont.fontSize)
        assertEquals(13.sp, standard.codeFont.fontSize)
        assertEquals(13.sp, standard.tableFont.fontSize)
    }

    private fun distinctBundledFonts(): OnyxFonts {
        val constructor = OnyxFonts::class.java.declaredConstructors.single { it.parameterCount == 1 }.apply { isAccessible = true }
        val fonts = constructor.newInstance(*arrayOfNulls<Any?>(1)) as OnyxFonts
        OnyxFonts::class.java.getDeclaredField("inter").apply {
            isAccessible = true
            set(fonts, FontFamily.Serif)
        }
        OnyxFonts::class.java.getDeclaredField("sourceCode").apply {
            isAccessible = true
            set(fonts, FontFamily.Cursive)
        }
        return fonts
    }
}
