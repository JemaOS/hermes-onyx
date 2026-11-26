package com.onyx.app.ui.chat.components

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextRingTest {
    @Test
    fun `la phrase donne les tokens et le pourcentage arrondi`() {
        assertEquals(
            "54,000 of 128,000 tokens (42%)",
            contextSentence(used = 54_000, window = 128_000, locale = Locale.US),
        )
        assertEquals(
            "1,000 of 0 tokens (0%)",
            contextSentence(used = 1_000, window = 0, locale = Locale.US),
        )
        assertEquals(1f, contextShare(used = 200, window = 100), 0f)
    }

    @Test
    fun `la couleur devient ambre a 75 pour cent et rouge a 90`() {
        assertEquals(ContextTint.ACCENT, contextTint(used = 74, window = 100))
        assertEquals(ContextTint.WARNING, contextTint(used = 75, window = 100))
        assertEquals(ContextTint.WARNING, contextTint(used = 89, window = 100))
        assertEquals(ContextTint.ERROR, contextTint(used = 90, window = 100))
    }
}
