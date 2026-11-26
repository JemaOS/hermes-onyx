package com.onyx.app.voice

import com.onyx.app.data.net.TransportError
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFallbackTest {

    /** HTTP 400/403/5xx, délai, injoignable, flux coupé, réponse illisible : tous replient. */
    private val serverFailures = listOf(
        TransportError.Http(400, "{\"detail\":\"TTS chunk 1\"}"),
        TransportError.Http(403, "forbidden"),
        TransportError.Http(500, "boom"),
        TransportError.Http(503, "unavailable"),
        TransportError.Timeout("read timed out"),
        TransportError.Unreachable("no route to host"),
        TransportError.StreamLost("connection reset"),
        TransportError.Malformed("Hermes sent no audio"),
    )

    @Test
    fun `une erreur serveur declenche le repli sur la voix de l appareil`() {
        serverFailures.forEach { error ->
            val name = error.javaClass.simpleName
            assertTrue(name, shouldFallBackToDevice(error))
            val outcome = voiceTrialOnFailure(error, deviceAvailable = true, locale = Locale.ENGLISH)
            assertTrue(name, outcome is VoiceTrialOutcome.Device)
        }
    }

    @Test
    fun `sans moteur local le repli echoue proprement et garde le detail du serveur`() {
        val error = TransportError.Http(400, "{\"detail\":\"TTS chunk 1\"}")
        val outcome = voiceTrialOnFailure(error, deviceAvailable = false, locale = Locale.FRANCE)

        assertTrue(outcome is VoiceTrialOutcome.Silent)
        val footnote = voiceTrialFootnote(outcome, Locale.FRANCE)
        assertTrue(footnote!!.contains("TTS chunk 1"))
        assertTrue(footnote.contains("aucune voix"))
        assertTrue(voiceTrialFootnoteIsError(outcome))
        assertEquals(VoiceTrialValue.FAILED, voiceTrialValue(outcome))
    }

    @Test
    fun `l etat court de la rangee est d une ligne et ne porte jamais le detail`() {
        val detail = "The server returned HTTP error 400. {\"detail\":\"TTS chunk 1\"}"
        val device = VoiceTrialOutcome.Device(detail)
        val silent = VoiceTrialOutcome.Silent(detail)

        assertEquals(VoiceTrialValue.SPOKEN, voiceTrialValue(device))
        assertEquals(VoiceTrialValue.FAILED, voiceTrialValue(silent))
        assertEquals(VoiceTrialValue.NONE, voiceTrialValue(null))
        assertFalse(voiceTrialFootnoteIsError(device))

        assertEquals("✓", voiceTrialShortLabel(VoiceTrialValue.SPOKEN, Locale.FRANCE))
        assertEquals("✓", voiceTrialShortLabel(VoiceTrialValue.SPOKEN, Locale.ENGLISH))
        assertEquals("Échec", voiceTrialShortLabel(VoiceTrialValue.FAILED, Locale.FRANCE))
        assertEquals("Failed", voiceTrialShortLabel(VoiceTrialValue.FAILED, Locale.ENGLISH))
        assertEquals("En cours…", voiceTrialShortLabel(VoiceTrialValue.WORKING, Locale.FRANCE))
        assertEquals("Working…", voiceTrialShortLabel(VoiceTrialValue.WORKING, Locale.ENGLISH))
        assertEquals("", voiceTrialShortLabel(VoiceTrialValue.NONE, Locale.FRANCE))

        VoiceTrialValue.entries.forEach { value ->
            val (french, english) = voiceTrialShortLabel(value, Locale.FRANCE) to
                voiceTrialShortLabel(value, Locale.ENGLISH)
            assertFalse(value.name, french.contains("HTTP"))
            assertFalse(value.name, english.contains("TTS chunk"))
            assertTrue(value.name, french.length <= 10)
            assertTrue(value.name, english.length <= 10)
        }
    }

    @Test
    fun `le detail du serveur part au pied de section, pas dans la valeur`() {
        val error = TransportError.Http(400, "{\"detail\":\"TTS chunk 1\"}")
        val outcome = voiceTrialOnFailure(error, deviceAvailable = true, locale = Locale.FRANCE)
        val footnote = voiceTrialFootnote(outcome, Locale.FRANCE)

        assertTrue(footnote!!.contains("HTTP 400"))
        assertTrue(footnote.contains("TTS chunk 1"))
        assertTrue(footnote.contains("appareil"))
        assertEquals("✓", voiceTrialShortLabel(voiceTrialValue(outcome), Locale.FRANCE))
    }

    @Test
    fun `un succes nomme le provider au pied et pose une coche dans la rangee`() {
        val outcome = VoiceTrialOutcome.Server("edge")

        assertEquals(VoiceTrialValue.SPOKEN, voiceTrialValue(outcome))
        assertEquals("✓", voiceTrialShortLabel(voiceTrialValue(outcome), Locale.FRANCE))
        assertEquals("Lu par Edge, la voix en ligne de Microsoft", voiceTrialFootnote(outcome, Locale.FRANCE))
        assertEquals("Spoken by Edge, Microsoft's online voice", voiceTrialFootnote(outcome, Locale.ENGLISH))
        assertNull(voiceTrialFootnote(VoiceTrialOutcome.Server(null), Locale.FRANCE))
        assertNull(voiceTrialFootnote(VoiceTrialOutcome.Server(" "), Locale.FRANCE))
        assertNull(voiceTrialFootnote(null, Locale.FRANCE))
        assertNull(voiceTrialFootnote(null, Locale.ENGLISH))
    }

    @Test
    fun `une annulation volontaire ne declenche pas le repli`() {
        assertFalse(shouldFallBackToDevice(CancellationException("cancelled")))
    }
}
