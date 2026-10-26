package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le implémentation de , et , et la décision
 * qui en sort pour `VoiceSession`: sans mots, sans micro, sans horloge — donc éprouvable ici.
 */
class BargeInTest {

    // MARK: - Le niveau

    @Test
    fun `une voix au-dessus du bruit de la pièce déclenche`() {
        val detector = BargeInDetector.echoCancelled()
        // Le réglage initial est la fenêtre d'annulation d'écho: rien ne compte encore.
        repeat(12) { assertFalse("pendant l'échauffement", detector.feed(SILENCE, STEP)) }
        // La pièce se met à son niveau (95 dBFS), puis une voix arrive à -20.
        detector.feed(SILENCE, STEP)
        var tripped = false
        repeat(20) { if (detector.feed(VOICE, STEP)) tripped = true }
        assertTrue("une voix tenue 2 s doit déclencher", tripped)
    }

    @Test
    fun `le bruit de la pièce seul ne déclenche pas`() {
        val detector = BargeInDetector.echoCancelled()
        repeat(60) { assertFalse(detector.feed(-60f, STEP)) }
    }

    @Test
    fun `une voix plus courte que la tenue ne déclenche pas`() {
        val detector = BargeInDetector.echoCancelled()
        detector.feed(SILENCE, STEP)
        // Un dixième de seconde: sous les 0,16 s.
        assertFalse(detector.feed(VOICE, STEP))
    }

    @Test
    fun `un creux entre syllabes ne reprend que ce qu il dure`() {
        // Sans fenêtre d'échauffement: c'est la tenue qu'on éprouve ici, pas l'annulation d'écho.
        val detector = BargeInDetector(minimum = -28f, settle = 0.0)
        // La pièce se déclare d'abord: le plancher de bruit tombe, et c'est lui qui décide du seuil.
        assertFalse(detector.feed(SILENCE, 0.05))
        assertFalse(detector.feed(VOICE, 0.1))          // 0,10 s de voix
        assertFalse(detector.feed(SILENCE, 0.05))       // un creux de 0,05 s
        assertTrue(detector.feed(VOICE, 0.15))          // 0,10 - 0,05 + 0,15 = 0,20 ≥ 0,16
    }

    @Test
    fun `le détacheur se remet à zéro comme au début d une réponse`() {
        val detector = BargeInDetector.echoCancelled()
        detector.feed(SILENCE, STEP)
        repeat(20) { detector.feed(VOICE, STEP) }
        detector.rearm()
        // Tout de suite après, la fenêtre d'annulation d'écho est de nouveau ouverte.
        assertFalse(detector.feed(VOICE, STEP))
    }

    @Test
    fun `un micro de casque a un seuil plus bas que celui du téléphone`() {
        val phone = BargeInDetector.echoCancelled()
        val headset = BargeInDetector.headset()
        // -32 dBFS: sous le seuil du téléphone (28), au-dessus de celui du casque (38).
        val quietRoom = -70f
        listOf(phone to false, headset to true).forEach { (detector, expected) ->
            detector.feed(quietRoom, STEP)
            var tripped = false
            repeat(20) { if (detector.feed(-32f, STEP)) tripped = true }
            assertEquals("seuil ${detector.minimum}", expected, tripped)
        }
    }

    // MARK: - Les bruits d'écoute

    @Test
    fun `les petits sons d un auditeur ne comptent pas comme une prise de parole`() {
        listOf("Mm-hm.", "Okay", "Okay, right", "I see", "Yeah", "Right, okay", "Got it", "D'accord")
            .forEach { assertTrue(it, Backchannel.isOnly(it)) }
    }

    @Test
    fun `un mot qui n est pas un bruit d écoute n en est pas un`() {
        listOf("okay, stop", "Mm-hm, what's the weather", "", "   ", "yes please delete the file", "mm hm hm hm")
            .forEach { assertFalse(it, Backchannel.isOnly(it)) }
    }

    // MARK: - L'ordre d'arrêt

    @Test
    fun `stop comme mot à part entière arrête`() {
        listOf("stop", "Stop.", "stop listening", "please stop", "okay stop", "Stop reading it")
            .forEach { assertTrue(it, StopWord.heardIn(it)) }
    }

    @Test
    fun `don't stop ne veut pas dire stop`() {
        assertFalse(StopWord.heardIn("don't stop"))
        assertFalse(StopWord.heardIn("do not stop"))
    }

    @Test
    fun `une phrase propre à la personne compte comme un arrêt`() {
        assertTrue(StopWord.heardIn("cut it out", own = listOf("cut it out")))
        assertFalse(StopWord.heardIn("cut it out"))
    }

    // MARK: - La décision de VoiceSession

    @Test
    fun `ce qui est dit par-dessus la réponse est classé`() {
        assertEquals(BargeInOutcome.NONE, bargeInOutcomeFor(""))
        assertEquals(BargeInOutcome.NONE, bargeInOutcomeFor("   "))
        assertEquals(BargeInOutcome.BACKCHANNEL, bargeInOutcomeFor("Mm-hm."))
        assertEquals(BargeInOutcome.STOP, bargeInOutcomeFor("stop"))
        assertEquals(BargeInOutcome.STOP, bargeInOutcomeFor("okay, stop", own = listOf("arrête")))
        assertEquals(BargeInOutcome.WORDS, bargeInOutcomeFor("what's on my calendar"))
    }

    @Test
    fun `le niveau n est surveillé que pendant la réponse`() {
        assertTrue(
            shouldWatchForBargeIn(
                VoicePhase.SPEAKING, isListening = true, isPaused = false, holding = false, enabled = true,
            ),
        )
        assertFalse("au repos", shouldWatchForBargeIn(VoicePhase.IDLE, true, false, false, true))
        assertFalse("à l'écoute", shouldWatchForBargeIn(VoicePhase.LISTENING, true, false, false, true))
        assertFalse("en pause", shouldWatchForBargeIn(VoicePhase.SPEAKING, true, true, false, true))
        assertFalse("micro fermé", shouldWatchForBargeIn(VoicePhase.SPEAKING, false, false, false, true))
        assertFalse(
            "une voix est déjà examinée",
            shouldWatchForBargeIn(VoicePhase.SPEAKING, true, false, holding = true, enabled = true),
        )
        assertFalse(
            "« Talk over replies » éteint",
            shouldWatchForBargeIn(VoicePhase.SPEAKING, true, false, false, enabled = false),
        )
    }

    private companion object {
        /** Un pas de mesure: `onRmsChanged` en livre une dizaine par seconde. */
        const val STEP = 0.1
        /** Le silence après annulation d'écho, ce que le détecteur voit d'une pièce calme. */
        const val SILENCE = -95f
        /** Une voix à bout de bras, la fourchette basse mesurée par. */
        const val VOICE = -20f
    }
}
