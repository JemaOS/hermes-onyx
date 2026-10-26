package com.onyx.app.ui.voice

import com.onyx.app.voice.VoicePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'instant d'un visage qui parle: `OnyxVoiceFace.pose`.
 *
 * Ce qui se voit dans la scène: les yeux clignent même pendant la parole, le regard suit la phase,
 * et **la bouche s'ouvre avec la voix** — c'est exactement ce que le modèle montre et ce qui
 * manquait (le visage d'avant était figé, une humeur « au travail » aux yeux grands et à la bouche
 * en trait).
 */
class VoiceFacePoseTest {

    @Test
    fun `les yeux clignent, meme pendant la parole`() {
        // Le clignement dure 0,16 s, en sinus: fermé au milieu.
        assertEquals(1f, voicePose(0.08f, VoicePhase.SPEAKING, level = 0.5f).blink, 0.0001f)
        assertEquals(
            "sin(0,35 π) : l'œil se referme",
            0.891f,
            voicePose(0.056f, VoicePhase.IDLE, 0f).blink,
            0.01f,
        )
        assertTrue("entre deux clignements, les yeux sont ouverts", voicePose(2f, VoicePhase.IDLE, 0f).blink < 0.001f)
        // Et le double, 13,8 s plus tard, décalé de 0,35 s.
        assertTrue(voicePose(13.53f, VoicePhase.IDLE, 0f).blink > 0.9f)
        assertTrue("et rien au milieu de l'attente", voicePose(9f, VoicePhase.IDLE, 0f).blink < 0.001f)
    }

    @Test
    fun `la bouche ne s ouvre qu avec la voix`() {
        val speaking = voicePose(1f, VoicePhase.SPEAKING, level = 0.5f)
        assertEquals("ouverture = niveau × 1,8", 0.9f, speaking.mouth!!, 0.0001f)
        assertEquals("une voix forte ouvre la bouche sans la déborder", 1f, voicePose(1f, VoicePhase.SPEAKING, 1.2f).mouth!!, 0.0001f)
        // Au repos, à l'écoute et en réflexion, la bouche reste celle du visage.
        assertEquals(0f, voicePose(1f, VoicePhase.IDLE, 0.8f).mouth!!, 0.0001f)
        assertEquals(0f, voicePose(1f, VoicePhase.LISTENING, 0.8f).mouth!!, 0.0001f)
        assertEquals(0f, voicePose(1f, VoicePhase.THINKING, 0.8f).mouth!!, 0.0001f)
    }

    @Test
    fun `le regard suit la phase`() {
        // À l'écoute: vers vous, un rien sous le milieu.
        val listening = voicePose(0f, VoicePhase.LISTENING, 0f).look!!
        assertEquals(0f, listening.x, 0.0001f)
        assertEquals(0.3f, listening.y, 0.0001f)
        // En réflexion: en haut et de côté.
        assertTrue(voicePose(0f, VoicePhase.THINKING, 0f).look!!.y < -0.6f)
        // Pendant la lecture: devant, un léger balancement.
        val speaking = voicePose(0f, VoicePhase.SPEAKING, 0f).look!!
        assertEquals(0.2f, speaking.y, 0.0001f)
        // Au repos: un regard tenu, puis un passage au suivant.
        assertEquals(0f, voicePose(0f, VoicePhase.IDLE, 0f).look!!.x, 0.0001f)
        assertTrue("et il change de place deux secondes et demie plus tard", voicePose(2.5f, VoicePhase.IDLE, 0f).look!! != voicePose(0f, VoicePhase.IDLE, 0f).look)
    }

    @Test
    fun `la tete penche avec le regard`() {
        val pose = voicePose(1f, VoicePhase.THINKING, 0f, tilt = 3f)
        assertEquals(3f + pose.look!!.x * 2.5f, pose.tilt!!, 0.0001f)
    }

    @Test
    fun `un visage fige n a ni clignement ni bouche qui bat`() {
        // C'est ce qu'oppose Reduce Motion: `BotPose` garde le regard, les yeux et la bouche du
        // visage, et rien ne bouge.
        val still = com.onyx.app.ui.design.BotPose()
        assertEquals(0f, still.blink, 0.0001f)
        assertNull(still.look)
        assertNull(still.mouth)
        assertNull(still.tilt)
        // Et le visage du bot, lui, reste celui qu'il a: la pose ne le remplace pas.
        assertNotNull(com.onyx.app.ui.design.botFace("quill", "1:blob:happy:smile:blush:4:0:0"))
    }
}
