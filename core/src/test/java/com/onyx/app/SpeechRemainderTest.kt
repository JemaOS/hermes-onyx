package com.onyx.app

import com.onyx.app.voice.speechRemainder
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRemainderTest {
    @Test
    fun `la reprise garde la partie non lue de la reponse`() {
        val text = "Le premier bot parle. Puis le second répond."

        assertEquals(text, speechRemainder(text, 0))
        assertEquals("Puis le second répond.", speechRemainder(text, 22))
        assertEquals("", speechRemainder(text, text.length + 20))
    }
}
