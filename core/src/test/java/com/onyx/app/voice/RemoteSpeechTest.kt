package com.onyx.app.voice

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSpeechTest {
    @Test
    fun `les voix Kokoro sont groupees et triees comme sur la référence`() {
        val groups = groupKokoroVoices(listOf("bm_george", "af_heart", "am_onyx", "bf_dora", "custom_voice"))

        assertEquals(listOf("af", "am", "bf", "bm", "cu"), groups.map { it.id })
        assertEquals("American English", groups[0].language)
        assertEquals("female", groups[0].gender)
        assertEquals("male", groups[1].gender)
        assertEquals("British English", groups[2].language)
        assertEquals("CU", groups[4].language)
        assertEquals("Heart", kokoroVoiceDisplayName("af_heart"))
        assertEquals("Onyx", kokoroVoiceDisplayName("am_onyx"))
        assertEquals("blend", kokoroVoiceDisplayName("blend"))
    }

    @Test
    fun `l adresse Kokoro conserve le chemin et supprime seulement le slash final`() {
        assertEquals("http://localhost:8880", normalizedKokoroBase(" http://localhost:8880/ "))
        assertEquals("https://tts.example.test/base", normalizedKokoroBase("tts.example.test/base/"))
        assertNull(normalizedKokoroBase(" / "))
    }

    @Test
    fun `le data URL Hermes conserve audio mime et provider`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val url = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(bytes)

        val audio = decodeSpeechDataUrl(url, provider = "elevenlabs")

        assertEquals("audio/mpeg", audio?.mimeType)
        assertEquals("elevenlabs", audio?.provider)
        assertEquals(bytes.toList(), audio?.bytes?.toList())
        assertFalse(audio?.isRawPcm == true)
        assertEquals("audio/mpeg", decodeSpeechDataUrl(url.replace(";base64", ";BASE64"))?.mimeType)
        assertNull(decodeSpeechDataUrl("https://example.test/audio.mp3"))
        assertNull(decodeSpeechDataUrl("data:audio/mpeg;base64,not base64"))
    }

    @Test
    fun `le PCM brut est distingue d un WAV renvoye par Kokoro`() {
        val pcm = SpeechAudio(byteArrayOf(0, 0, 1, 0), "audio/pcm")
        val wav = SpeechAudio(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), 0, 0, 0, 0, 'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()), "audio/pcm")

        assertTrue(pcm.isRawPcm)
        assertFalse(wav.isRawPcm)
    }

    @Test
    fun `les providers Hermes gardent les noms publics de la référence`() {
        assertEquals("your Hermes", hermesProviderName(""))
        assertEquals("Edge, Microsoft's online voice", hermesProviderName("EDGE"))
        assertEquals("ElevenLabs", hermesProviderName("elevenlabs"))
        assertEquals("Piper, on your server", hermesProviderName("piper"))
        assertEquals("custom-provider", hermesProviderName("custom-provider"))
    }

    @Test
    fun `les ids de voix conserves sont compatibles avec Settings ReplyVoice`() {
        assertEquals(ReplyVoice.ON_DEVICE, ReplyVoice.fromId(null))
        assertEquals(ReplyVoice.ON_DEVICE, ReplyVoice.fromId("unknown"))
        assertEquals(ReplyVoice.KOKORO, ReplyVoice.fromId("kokoro"))
        assertEquals(ReplyVoice.HERMES, ReplyVoice.fromId("hermes"))
        assertEquals("onDevice", ReplyVoice.ON_DEVICE.id)
    }
}
