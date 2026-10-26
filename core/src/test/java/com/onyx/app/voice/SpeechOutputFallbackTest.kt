package com.onyx.app.voice

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lecture des réponses retombait déjà sur la voix de l'appareil: ce test fixe ce comportement
 * pour qu'une évolution du repli ne laisse pas un serveur muet sans secours.
 */
class SpeechOutputFallbackTest {

    private val source = listOf(
        File("src/main/java/com/onyx/app/voice/SpeechOutput.kt"),
        File("core/src/main/java/com/onyx/app/voice/SpeechOutput.kt"),
    ).firstOrNull { it.exists() }!!.readText()

    @Test
    fun `les deux voix distantes retombent sur le synthetiseur de l appareil`() {
        assertTrue(source.contains("ReplyVoice.KOKORO -> {"))
        assertTrue(source.contains("ReplyVoice.HERMES -> {"))
        assertTrue(source.contains("private fun fallbackToDevice(token: Long, text: String)"))
        assertTrue(source.contains("if (ready) {\n            startUtterance(text)"))
    }

    @Test
    fun `un echec de lecture ou de telechargement declenche le repli, pas une annulation`() {
        assertTrue(source.contains("onError = { fallbackToDevice(token, text) }"))
        assertTrue(source.contains("} catch (error: CancellationException) {\n                throw error"))
        assertTrue(source.contains("} catch (_: Throwable) {\n                fallbackToDevice(token, text)"))
    }
}
