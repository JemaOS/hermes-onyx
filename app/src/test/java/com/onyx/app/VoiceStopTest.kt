package com.onyx.app

import com.onyx.app.ui.voice.soloVoiceStop
import com.onyx.app.voice.VoicePhase
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceStopTest {
    @Test
    fun `End voice mode depuis READY arrete puis ferme la voix`() {
        val actions = mutableListOf<String>()

        soloVoiceStop(
            phase = VoicePhase.IDLE,
            stop = { actions += "stop" },
            end = { actions += "end" },
        )

        assertEquals(listOf("stop", "end"), actions)
    }

    @Test
    fun `Stop pendant une activite arrete sans fermer la voix`() {
        VoicePhase.entries.filter { it != VoicePhase.IDLE }.forEach { phase ->
            val actions = mutableListOf<String>()

            soloVoiceStop(
                phase = phase,
                stop = { actions += "stop" },
                end = { actions += "end" },
            )

            assertEquals("phase $phase", listOf("stop"), actions)
        }
    }
}
