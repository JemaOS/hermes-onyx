package com.onyx.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class OnyxThemeDefaultsTest {
    @Test
    fun `les identifiants suivent les raw values la référence et le repli est Messages`() {
        assertEquals(
            listOf("githubDark", "standard", "paper", "slate", "terminal", "amber", "claudeCode"),
            OnyxTheme.entries.map { it.id },
        )
        OnyxTheme.entries.forEach { theme ->
            assertSame(theme, OnyxTheme.fromId(theme.id))
        }

        assertSame(OnyxTheme.STANDARD, OnyxTheme.fromId(null))
        assertSame(OnyxTheme.STANDARD, OnyxTheme.fromId(""))
        assertSame(OnyxTheme.STANDARD, OnyxTheme.fromId("STANDARD"))
        assertSame(OnyxTheme.STANDARD, OnyxTheme.fromId("not-a-theme"))
    }

    @Test
    fun `le repli du CompositionLocal est Messages en apparence claire`() {
        val fallback = ResolvedTheme.fallback()

        assertSame(OnyxTheme.STANDARD, fallback.theme)
        assertFalse(fallback.dark)
    }
}
