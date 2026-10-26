package com.onyx.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Une session que le moteur a refusée n'est pas une phrase: **rien ne part**.
 *
 * C'était la cause des tours renvoyés plusieurs fois. `onError` passait `isFinal` à vrai sans
 * toucher à la dictée en cours; la branche d'écoute voyait « finale, texte non vide » et reposait
 * le partiel comme un tour. En parlant plus longtemps, chaque refus renvoyait un partiel un peu
 * plus long — « peux-tu m'aider » puis « est-ce que tu peux m'aider aujourd'hui » — et l'agent
 * répondait à chacune.
 *
 * L'écoute passe par un `SpeechRecognizer` Android: ces deux fichiers ne se testent pas sur JVM.
 * Ce que l'on vérifie ici, c'est donc l'articulation exacte qui portait le défaut.
 */
class FailedSessionTest {

    private val engine = File("src/main/java/com/onyx/app/voice/SpeechEngine.kt").readText()
    private val session = File("src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
    private val onError = engine.substringAfter("override fun onError(error: Int)")
        .substringBefore("override fun onResults")

    @Test
    fun `un echec du moteur jette la dictee partielle`() {
        // L'état d'un échec n'est pas celui d'une phrase terminée: le partiel est jeté.
        assertTrue("le partiel ne survit pas à un refus", onError.contains("transcript = \"\""))
        assertTrue("un échec n'est pas une finale", onError.contains("isFinal = silence"))
        assertTrue("l'échec est signalé comme tel", onError.contains("failed = !silence"))
        // Le silence, lui, termine bien l'écoute: c'est ce qui la relance.
        assertTrue(onError.contains("val silence = error == SpeechRecognizer.ERROR_NO_MATCH"))
    }

    @Test
    fun `un echec ne relance pas l ecoute dans la foulee`() {
        // L'ancien chemin: l'échec sortait par la branche du silence et relançait aussitôt.
        assertFalse("plus de relance par la branche du silence", onError.contains("finishLater()"))
        // Et la reprise est unique et espacée.
        assertTrue(session.contains("if (engine.canRetry) retryListening() else _phase.value = VoicePhase.IDLE"))
        assertTrue(session.contains("delay(ENGINE_RETRY_DELAY_MS)"))
        assertTrue("la reprise ne remet pas le compteur à zéro", session.contains("startListening(fresh = false)"))
    }

    private fun listening(): String = session.substringAfter("VoicePhase.LISTENING -> {")
        .substringBefore("VoicePhase.SPEAKING -> handleSpeaking")

    @Test
    fun `l ecoute traite l echec avant d envoyer quoi que ce soit`() {
        val listening = listening()
        val failed = listening.indexOf("if (speech.failed)")
        val send = listening.indexOf("ask(speech.transcript)")
        assertTrue("la branche d'échec existe", failed >= 0)
        assertTrue("elle passe avant l'envoi", failed < send)
        // Un échec n'est plus pris pour une phrase: plus de bandeau posé par cette branche.
        assertFalse(listening.contains("speech.isFinal && speech.problem != null"))
    }

    @Test
    fun `le brouillon est efface quand la phase repasse a pret`() {
        // Le brouillon (la dictée en cours) n'a de sens que pendant l'écoute: ce qui n'a été
        // qu'entendu à moitié ne doit pas rester sous les yeux au retour à PRÊT — c'était le
        // « brouillon partiel resté affiché » des captures.
        val finish = session.substringAfter("private fun finishSpeaking()")
            .substringBefore("private fun restartListeningIfHandsFree")
        assertTrue("retour à PRÊT : plus de brouillon", finish.contains("_heard.value = \"\""))
        // Et un échec du moteur jette aussi le partiel.
        assertTrue("échec : plus de brouillon", onError.contains("transcript = \"\""))
        assertTrue(listening().contains("_heard.value = \"\""))
    }

}
