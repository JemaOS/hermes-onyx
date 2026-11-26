package com.onyx.app.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **premier** démarrage de la voix doit marcher, sans bandeau rouge.
 *
 * Le symptôme : à chaque première entrée dans la scène vocale — bot seul ou salle — le moteur
 * de reconnaissance s'arrêtait, l'app affichait « le moteur s'est arrêté », et il fallait
 * toucher « Réessayer » pour que ça marche. Les entrées suivantes, elles, allaient bien.
 *
 * La cause : à chaque échec, l'app **jetait le moteur** et en créait un neuf. Or un moteur neuf
 * doit se lier au service de reconnaissance — et c'est justement ce lien qui manque au premier
 * démarrage. La reprise automatique détruisait donc la trace du lien et reproduisait l'échec
 * qu'elle était censée réparer.
 *
 * Ce test verrouille la distinction : un moteur **mort** (la session avait commencé) se
 * remplace ; un service **pas encore lié** (la session n'a jamais commencé) se garde, et on
 * rejoue dessus.
 */
class EngineFirstStartupTest {

    @Test
    fun `un moteur dont la session n a jamais commence est garde`() {
        // Le service n'était pas encore lié : le moteur n'est pas cassé, et le jeter
        // relancerait la même course.
        assertFalse(
            "un moteur qui n'a jamais pris la session doit être gardé",
            needsFreshRecognizerAfter(SpeechRecognizer.ERROR_CLIENT, readyForSpeech = false),
        )
        assertFalse(
            "idem pour un service occupationné",
            needsFreshRecognizerAfter(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, readyForSpeech = false),
        )
        assertFalse(
            "idem pour un réseau mort",
            needsFreshRecognizerAfter(SpeechRecognizer.ERROR_NETWORK, readyForSpeech = false),
        )
    }

    @Test
    fun `un moteur dont la session avait commence est remplace`() {
        // La session tournait : le moteur peut être mort, on en fait un neuf.
        assertTrue(
            "un moteur qui a pris la session puis est mort doit être remplacé",
            needsFreshRecognizerAfter(SpeechRecognizer.ERROR_CLIENT, readyForSpeech = true),
        )
        assertTrue(
            needsFreshRecognizerAfter(SpeechRecognizer.ERROR_SERVER, readyForSpeech = true),
        )
    }

    @Test
    fun `une fin de session normale ne remplace jamais le moteur`() {
        assertFalse(needsFreshRecognizerAfter(SpeechRecognizer.ERROR_NO_MATCH, readyForSpeech = true))
        assertFalse(needsFreshRecognizerAfter(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, readyForSpeech = true))
        assertFalse(needsFreshRecognizerAfter(SpeechRecognizer.ERROR_NO_MATCH, readyForSpeech = false))
    }

    @Test
    fun `un premier demarrage a un budget plus large`() {
        // Le service a le temps de se lier : cinq essais espacés.
        assertTrue(
            "le premier démarrage doit avoir plus de budget",
            engineMaxAttempts(sessionStarted = false) > engineMaxAttempts(sessionStarted = true),
        )
        assertTrue(
            "et ce budget doit rester borne",
            engineMaxAttempts(sessionStarted = false) <= 6,
        )
    }

    @Test
    fun `la reprise reste espacee et bornee`() {
        // Une reprise qui ne marche pas ne boucle pas sans fin.
        assertTrue(shouldRetryEngine(attempts = 1, maxAttempts = engineMaxAttempts(false)))
        assertTrue(shouldRetryEngine(attempts = 4, maxAttempts = engineMaxAttempts(false)))
        assertFalse(shouldRetryEngine(attempts = 5, maxAttempts = engineMaxAttempts(false)))
        assertFalse(shouldRetryEngine(attempts = 3, maxAttempts = engineMaxAttempts(true)))
        // Et le délai s'allonge avec les refus.
        assertTrue(engineRetryDelayMs(2) > engineRetryDelayMs(1))
    }

    @Test
    fun `l app ne prend pas le micro que le moteur tient`() {
        // Le micro est une ressource partagée : ouvrir le sien par-dessus une session de
        // reconnaissance, c'est se faire évincer — et s'attirer le refus qui suit.
        assertFalse(
            "le micro de l'app ne s'ouvre pas pendant que le moteur écoute",
            mayOpenAppMic(
                wantsMeter = true,
                alreadyAttempted = false,
                meterAvailable = false,
                engineListening = true,
            ),
        )
        assertTrue(
            "mais il s'ouvre quand le moteur ne tient pas le micro",
            mayOpenAppMic(
                wantsMeter = true,
                alreadyAttempted = false,
                meterAvailable = false,
                engineListening = false,
            ),
        )
    }

    @Test
    fun `le micro de l app ne s ouvre qu une fois par ecoute`() {
        assertFalse(
            "une tentative suffit : un échec ne se répète pas",
            mayOpenAppMic(
                wantsMeter = true,
                alreadyAttempted = true,
                meterAvailable = false,
                engineListening = false,
            ),
        )
        assertFalse(
            "et pas quand il est déjà ouvert",
            mayOpenAppMic(
                wantsMeter = true,
                alreadyAttempted = false,
                meterAvailable = true,
                engineListening = false,
            ),
        )
    }
}
