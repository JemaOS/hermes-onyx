package com.onyx.app.ui.settings.voice

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroLayoutTest {
    @Test
    fun `le bouton decoute garde un glyphe title3 dans sa cible tactile`() {
        val source = File("src/main/java/com/onyx/app/ui/settings/voice/VoicePreviewComponents.kt").readText()
        assertTrue(source.contains("VoicePreviewGlyphSize = 20.dp"))
        assertTrue(source.contains("VoicePreviewTouchSize = 36.dp"))
        assertTrue(source.contains(".size(VoicePreviewTouchSize)"))
        assertTrue(source.contains("Canvas(modifier = Modifier.size(VoicePreviewGlyphSize))"))
        assertTrue(source.contains("size.minDimension / 2f - stroke / 2f"))
    }

    @Test
    fun `la ligne garde les 4 points verticaux autour de la cible`() {
        val source = File("src/main/java/com/onyx/app/ui/settings/voice/KokoroVoicesView.kt").readText()
        assertTrue(source.contains(".padding(horizontal = 16.dp, vertical = 4.dp)"))
    }
}
