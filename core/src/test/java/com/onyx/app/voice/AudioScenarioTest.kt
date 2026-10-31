package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le moteur de test alimenté par un fichier audio.**
 *
 * Ce que ces vérifications tiennent : le WAV français livré dans les ressources est **lu** (PCM,
 * sans Android), son **enveloppe de niveau bouge** (c'est elle qui fait bouger les points — le
 * défaut rapporté était sept points parfaitement plats), et le scénario composé est bien
 * **silence → phrase → silence**, avec une transcription qui grandit comme celle d'un vrai moteur.
 */
class AudioScenarioTest {

    private val wav: ByteArray = requireNotNull(
        AudioScenarioTest::class.java.getResourceAsStream("/voice/phrase-fr.wav"),
    ) { "le WAV de scénario doit être dans les ressources de test" }.readBytes()

    @Test
    fun `le WAV de scenario se lit sans Android`() {
        val samples = WavReader.samples(wav)
        assertTrue("des échantillons", samples.size > 16_000)
        assertTrue("dans [-1, 1]", samples.all { it >= -1f && it <= 1f })
        assertTrue("du signal, pas du silence", samples.any { kotlin.math.abs(it) > 0.05f })
    }

    @Test
    fun `un fichier qui n est pas un WAV est refuse, jamais rejoue en silence`() {
        var refused = false
        try {
            WavReader.samples("pas un wav du tout".toByteArray())
        } catch (expected: IllegalArgumentException) {
            refused = true
        }
        assertTrue("un WAV illisible doit lever, pas rendre du vide", refused)
    }

    @Test
    fun `l enveloppe du niveau bouge vraiment`() {
        val levels = rmsLevels(WavReader.samples(wav), sampleRate = SAMPLE_RATE_HINT, stepMs = 60)
        assertTrue("assez de mesures pour une phrase", levels.size >= 30)
        val highest = levels.max()
        val lowest = levels.min()
        assertTrue("une phrase atteint un niveau visible (max=$highest)", highest > 0.35f)
        assertTrue(
            "l'écart entre le plus bas et le plus haut est franc (min=$lowest, max=$highest) : " +
                "c'est ce qui fait bouger les points",
            highest - lowest > 0.25f,
        )
        // Et ce n'est pas un plateau : au moins un quart des mesures sont distinctes.
        assertTrue("le niveau n'est pas plat", levels.distinct().size > levels.size / 4)
    }

    @Test
    fun `le scenario est silence, phrase, silence`() {
        val frames = scenarioFromWav(
            wav,
            transcript = "Quel temps fait-il demain à Lyon ?",
            leadInMs = 300,
            tailMs = 1_700,
            stepMs = 40,
        )
        assertTrue("des trames", frames.size > 30)
        val lead = frames.takeWhile { it.atMs < 300 }
        assertTrue("le silence de tête ne bouge pas", lead.all { it.level == 0f && it.transcript.isEmpty() })

        val speaking = frames.filter { it.transcript.isNotEmpty() && !it.isFinal }
        assertTrue("une phrase au milieu", speaking.isNotEmpty())
        assertTrue("la phrase a du niveau", speaking.maxOf { it.level } > 0.35f)

        // Le silence de queue : la phrase posée, la pièce calme — c'est ce que l'endpointing attend.
        // Les 20 dernières trames sont ce silence (le scénario en pose bien plus), et elles portent
        // la phrase complète : c'est ce que la session enverra.
        val queue = frames.takeLast(21)
        assertTrue("un silence de queue assez long pour l'endpointing", queue.all { it.level == 0f })
        assertTrue(
            "et ce silence porte la phrase posée",
            queue.all { it.transcript == "Quel temps fait-il demain à Lyon ?" },
        )

        assertTrue("la dernière trame rend la finale", frames.last().isFinal)
        assertEquals("chronologie croissante", frames.map { it.atMs }, frames.map { it.atMs }.sorted())
    }

    @Test
    fun `le niveau 0 se dit en dBFS comme un silence de moteur`() {
        assertEquals(SpeechState.SILENT_DB, levelToDb(0f), 0.001f)
        assertTrue("un niveau franc se dit en dB négatifs", levelToDb(1f) > -1f)
        assertFalse("et jamais au-dessus de 0", levelToDb(1f) > 0.001f)
    }
}
