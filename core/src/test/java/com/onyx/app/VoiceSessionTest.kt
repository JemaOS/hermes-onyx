package com.onyx.app

import com.onyx.app.voice.VoicePhase
import com.onyx.app.voice.shouldReopenMicForSpeech
import com.onyx.app.voice.shouldRestartHandsFreeAfterSpeech
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le demi-duplex, vu du branchement de la session : le micro ne se rouvre pendant la lecture que si
 * la personne a demandé l'interruption (« Talk over replies ») **et** que la route du moment s'y
 * prête, et jamais pour une lecture demandée depuis le fil.
 */
class VoiceSessionTest {
    @Test
    fun `le micro ne se rouvre pendant la lecture que si le barge-in est demande`() {
        assertFalse(
            "demi-duplex par défaut : rien ne rouvre le micro pendant la lecture",
            shouldReopenMicForSpeech(VoicePhase.SPEAKING, isListening = false, micBehindReply = false),
        )
        assertTrue(
            "barge-in demandé : le micro peut rouvrir derrière la réponse",
            shouldReopenMicForSpeech(VoicePhase.SPEAKING, isListening = false, micBehindReply = true),
        )
        assertFalse(
            "déjà à l'écoute : rien à rouvrir",
            shouldReopenMicForSpeech(VoicePhase.SPEAKING, isListening = true, micBehindReply = true),
        )
        assertFalse(shouldReopenMicForSpeech(VoicePhase.IDLE, isListening = false, micBehindReply = true))
        assertFalse(shouldReopenMicForSpeech(VoicePhase.LISTENING, isListening = false, micBehindReply = true))
    }

    @Test
    fun `la lecture simple depuis le chat ne rouvre jamais le micro`() {
        assertFalse(
            shouldReopenMicForSpeech(
                VoicePhase.SPEAKING,
                isListening = false,
                listenAfterSpeech = false,
                micBehindReply = true,
            ),
        )
        assertFalse(shouldRestartHandsFreeAfterSpeech(handsFree = true, listenAfterSpeech = false))
        assertTrue(shouldRestartHandsFreeAfterSpeech(handsFree = true, listenAfterSpeech = true))
    }
}
