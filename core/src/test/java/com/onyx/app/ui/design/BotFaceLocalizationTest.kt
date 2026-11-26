package com.onyx.app.ui.design

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class BotFaceLocalizationTest {
    @Test
    fun `la description accessible du visage suit la langue`() {
        val face = BotFace()
        assertEquals("Visage bleu, yeux ronds, sourire", face.describe(Locale.FRANCE))
        assertEquals("Blue face, round eyes, smile", face.describe(Locale.ENGLISH))
    }
}
