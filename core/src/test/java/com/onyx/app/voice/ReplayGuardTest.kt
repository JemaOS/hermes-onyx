package com.onyx.app.voice

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le mode mains libres ne doit pas se réentendre indéfiniment.
 *
 * Le moteur de reconnaissance peut rejouer une finale déjà livrée; chaque rejeu relançait un tour,
 * et la conversation ne s'arrêtait plus (« ça s'auto-écoute en boucle… ça s'arrête jamais »).
 * La garde est double: une phrase déjà posée dans les 60 s est ignorée, et trois reprises
 * d'affilée arrêtent le mode mains libres au lieu de boucler.
 */
class ReplayGuardTest {

    private val session = File("src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()

    @Test
    fun `une phrase deja posee ne repart pas`() {
        assertTrue("trace des phrases posées", session.contains("private val recentAsked = ArrayDeque<Pair<String, Long>>()"))
        assertTrue("fenêtre de 60 s", session.contains("nowMs - recentAsked.first().second > 60_000L"))
        assertTrue("comparaison normalisée", session.contains("recentAsked.any { it.first == normalised }"))
    }

    @Test
    fun `trois reprises d affilee arretent le mode mains libres`() {
        assertTrue("compteur de reprises", session.contains("private var replays = 0"))
        assertTrue("coupe-circuit", session.contains("if (replays >= 3)"))
        assertTrue("on quitte le mains libres", session.contains("_handsFree.value = false"))
    }

    @Test
    fun `une phrase acceptee est enregistree`() {
        assertTrue(
            "la phrase posée entre dans la trace",
            session.contains("recentAsked.addLast(it to System.currentTimeMillis())"),
        )
    }
}
