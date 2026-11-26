package com.onyx.app.ui.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerPasteTest {
    @Test
    fun `une image sans texte est acceptee mais pas la miniature d un lien`() {
        assertTrue(isPictureClipboardItem("image/png", hasText = false))
        assertFalse(isPictureClipboardItem("image/png", hasText = true))
        assertFalse(isPictureClipboardItem("text/plain", hasText = false))
    }
}
