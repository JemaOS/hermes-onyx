package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le repli quand le moteur de reconnaissance refuse de démarrer (code 5).
 *
 * Le défaut signalé : « le moteur n'a pas pu démarrer (code 5) — nouvelle tentative » revenait sans
 * fin, et le mode vocal « buguait tout le temps ». La règle éprouvée ici est celle de la référence :
 * **une** reprise espacée, puis on s'arrête avec un message qui dit ce qui s'est passé et quoi
 * faire — jamais la promesse d'une reprise qui n'aura pas lieu.
 */
class EngineFailureTest {

    @Test
    fun `des reprises espacees, puis on s arrete`() {
        // Un refus passager se reprend ; c'est la **répétition** qui fait le défaut, et c'est elle
        // qui arrête le mode — pas la première panne.
        assertTrue(shouldRetryEngine(attempts = 1))
        assertTrue(shouldRetryEngine(attempts = 2))
        assertFalse(shouldRetryEngine(attempts = 3))
        // Avant tout refus, rien à décider.
        assertTrue(shouldRetryEngine(attempts = 0))
    }

    @Test
    fun `le nombre de departs est borne et l attente s allonge`() {
        assertEquals(3, MAX_ENGINE_ATTEMPTS)
        // Un compteur qui repart de zéro ne doit jamais autoriser un départ de plus que le plafond.
        assertFalse(shouldRetryEngine(attempts = MAX_ENGINE_ATTEMPTS))
        // La reprise est espacée : plus long que la relance ordinaire (250 ms), sinon le moteur
        // refuse encore parce qu'il tient le micro.
        assertTrue(ENGINE_RETRY_DELAY_MS > SpeechEngine.RESTART_DELAY_MS)
        // Et l'attente **s'allonge** avec les refus (espacement croissant).
        assertEquals(ENGINE_RETRY_DELAY_MS, engineRetryDelayMs(1))
        assertEquals(ENGINE_RETRY_DELAY_MS * 2, engineRetryDelayMs(2))
        assertEquals(ENGINE_RETRY_DELAY_MS * 3, engineRetryDelayMs(3))
        assertTrue(engineRetryDelayMs(2) > engineRetryDelayMs(1))
        assertTrue(engineRetryDelayMs(3) > engineRetryDelayMs(2))
    }

    @Test
    fun `le message d un echec definitif ne promet aucune reprise`() {
        val code5 = engineFailureMessage(android.speech.SpeechRecognizer.ERROR_CLIENT)
        // Le code est gardé : c'est ce que le propriétaire a lu à l'écran.
        assertTrue(code5.contains("code 5"))
        // Et il dit quoi faire, au lieu d'annoncer une tentative qui n'aura pas lieu.
        assertFalse(code5.contains("retrying"))
        assertTrue(code5.contains("Tap the orb"))

        val autre = engineFailureMessage(android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertFalse(autre.contains("retrying"))
        assertTrue(autre.contains("Tap the orb"))
    }

    @Test
    fun `un refus passager se reprend, les autres non`() {
        // Le code 5, le réseau, le moteur occupé : ça vaut une reprise espacée.
        assertTrue(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_CLIENT))
        assertTrue(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_NETWORK))
        assertTrue(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertTrue(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertTrue(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_SERVER))
        assertTrue(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_TOO_MANY_REQUESTS))
        // Ce qui ne se répare pas en réessayant : montré tout de suite, sans reprise.
        assertFalse(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertFalse(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertFalse(isTransientEngineFailure(android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
    }

    // MARK: - « Parler pendant les réponses »

    @Test
    fun `le micro reste ferme quand parler par dessus n est pas demande pour cette route`() {
        // Le test de la référence, cas par cas : « the mic stays shut where talking over is not on ».
        assertFalse(talkOverAllowsMic(TalkOver.OFF, externalRoute = true))
        assertFalse(talkOverAllowsMic(TalkOver.OFF, externalRoute = false))
        // Casque demandé, mais le son sort par le haut-parleur du téléphone : fermé.
        assertFalse(talkOverAllowsMic(TalkOver.HEADPHONES, externalRoute = false))
        // Casque demandé et un casque est branché : ouvert.
        assertTrue(talkOverAllowsMic(TalkOver.HEADPHONES, externalRoute = true))
        // Partout : ouvert même sur le haut-parleur (annulation d'écho).
        assertTrue(talkOverAllowsMic(TalkOver.EVERYWHERE, externalRoute = false))
        assertTrue(talkOverAllowsMic(TalkOver.EVERYWHERE, externalRoute = true))
    }

    @Test
    fun `le defaut est celui de la reference, et l ancien reglage est relu`() {
        // `.headphones` : le micro ne s'ouvre derrière la réponse qu'avec un casque, donc jamais sur
        // le haut-parleur d'un téléphone — c'est ce qui ferme le chemin de l'écho.
        assertEquals(TalkOver.HEADPHONES, TalkOver.DEFAULT)
        assertFalse(talkOverAllowsMic(TalkOver.DEFAULT, externalRoute = false))
        assertTrue(talkOverAllowsMic(TalkOver.DEFAULT, externalRoute = true))
        // L'ancien booléen : vrai valait « partout », faux « jamais ».
        assertEquals(TalkOver.EVERYWHERE, TalkOver.fromLegacy(true))
        assertEquals(TalkOver.OFF, TalkOver.fromLegacy(false))
        // Le mode enregistré se relit ; une valeur inconnue retombe sur le défaut.
        assertEquals(TalkOver.EVERYWHERE, TalkOver.fromId("EVERYWHERE"))
        assertEquals(TalkOver.OFF, TalkOver.fromId("off"))
        assertEquals(TalkOver.DEFAULT, TalkOver.fromId(null))
        assertEquals(TalkOver.DEFAULT, TalkOver.fromId("n'importe quoi"))
    }

    @Test
    fun `le micro ne se rouvre que pendant la lecture, et seulement si c est demande`() {
        // Pendant qu'on réfléchit : jamais (l'agent s'auto-écoutait là).
        assertFalse(
            shouldReopenMicForSpeech(
                phase = VoicePhase.THINKING,
                isListening = false,
                listenAfterSpeech = true,
                micBehindReply = true,
            ),
        )
        // Pendant la lecture, sans le réglage : jamais non plus (demi-duplex).
        assertFalse(
            shouldReopenMicForSpeech(
                phase = VoicePhase.SPEAKING,
                isListening = false,
                listenAfterSpeech = true,
                micBehindReply = false,
            ),
        )
        // Pendant la lecture, avec le réglage et la route qui vont bien : oui.
        assertTrue(
            shouldReopenMicForSpeech(
                phase = VoicePhase.SPEAKING,
                isListening = false,
                listenAfterSpeech = true,
                micBehindReply = true,
            ),
        )
        // Une lecture demandée depuis le fil ne rouvre jamais le micro.
        assertFalse(
            shouldReopenMicForSpeech(
                phase = VoicePhase.SPEAKING,
                isListening = false,
                listenAfterSpeech = false,
                micBehindReply = true,
            ),
        )
        // Et pas deux fois : si l'écoute tourne déjà, il n'y a rien à rouvrir.
        assertFalse(
            shouldReopenMicForSpeech(
                phase = VoicePhase.SPEAKING,
                isListening = true,
                listenAfterSpeech = true,
                micBehindReply = true,
            ),
        )
    }
}
