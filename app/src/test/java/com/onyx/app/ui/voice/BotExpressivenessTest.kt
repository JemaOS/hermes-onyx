package com.onyx.app.ui.voice

import com.onyx.app.voice.VoicePhase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'expressivité dans la scène vocale: les micro-expressions de chaque phase (sourcils, paupières,
 * respiration), leur bornage, et le câblage des cinq états dans les écrans.
 *
 * Le rendu, lui, se juge sur la capture: ces vérifications portent sur la règle et sur la source.
 */
class BotExpressivenessTest {

    private val face = File("src/main/java/com/onyx/app/ui/voice/VoiceFace.kt").readText()

    @Test
    fun `chaque phase a ses micro-expressions`() {
        // Sourcils hauts à l'écoute, rapprochés en réflexion.
        assertTrue("écoute : sourcils hauts", voicePose(0f, VoicePhase.LISTENING, 0f).brow > 0.2f)
        assertTrue("réflexion : sourcils rapprochés", voicePose(0f, VoicePhase.THINKING, 0f).brow < -0.3f)
        // Pendant la parole, les sourcils vivent d'une image à l'autre.
        val speaking1 = voicePose(0f, VoicePhase.SPEAKING, 0f).brow
        val speaking2 = voicePose(0.5f, VoicePhase.SPEAKING, 0f).brow
        assertTrue("pendant la parole, les sourcils remuent", speaking1 != speaking2)
    }

    @Test
    fun `les micro-expressions restent douces et bornees`() {
        listOf(0f, 0.4f, 1.1f, 2.7f, 4.2f, 13.6f).forEach { t ->
            listOf(VoicePhase.IDLE, VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING).forEach { phase ->
                val pose = voicePose(t, phase, 0.5f)
                assertTrue("brow borné à $t/$phase", pose.brow in -1f..1f)
                assertTrue("squint borné à $t/$phase", pose.squint in -1f..1f)
                assertTrue("breath borné à $t/$phase", pose.breath in -1f..1f)
                // Petite amplitude: jamais un visage qui saute.
                assertTrue("sourcils discrets à $t/$phase", Math.abs(pose.brow) <= 0.6f)
                assertTrue("paupières discrètes à $t/$phase", Math.abs(pose.squint) <= 0.4f)
            }
        }
    }

    @Test
    fun `la reflection baisse les paupieres et le corps respire lentement`() {
        assertTrue("réflexion : paupières basses", voicePose(0f, VoicePhase.THINKING, 0f).squint > 0.2f)
        // La respiration fait un cycle d'environ quatre secondes: deux instants opposés le montrent.
        val a = voicePose(0.5f, VoicePhase.IDLE, 0f).breath
        val b = voicePose(2.6f, VoicePhase.IDLE, 0f).breath
        assertTrue("le corps respire", a * b < 0f)
        assertTrue("un souffle, pas un saut", Math.abs(a) <= 0.5f)
    }

    @Test
    fun `les formes d avant ne bougent pas`() {
        // Les micro-expressions s'ajoutent: le clignement, la bouche, le regard et l'inclinaison
        // gardent exactement leurs formules — les tests du implémentation restent vrais.
        assertEquals(1f, voicePose(0.08f, VoicePhase.SPEAKING, level = 0.5f).blink, 0.0001f)
        assertEquals(0.9f, voicePose(1f, VoicePhase.SPEAKING, level = 0.5f).mouth!!, 0.0001f)
        assertEquals(0f, voicePose(0f, VoicePhase.LISTENING, 0f).look!!.x, 0.0001f)
    }

    @Test
    fun `la source porte les micro-expressions de chaque phase`() {
        assertTrue(face.contains("VoicePhase.LISTENING -> 0.35f"))
        assertTrue(face.contains("VoicePhase.THINKING -> -0.45f"))
        assertTrue(face.contains("val breath = 0.5f * sin(seconds * 1.5f)"))
    }

    @Test
    fun `les ecrans passent les cinq etats au visage`() {
        val conv = File("src/main/java/com/onyx/app/ui/conversations/ConversationsScreen.kt").readText()
        val bots = File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText()
        val chat = File("src/main/java/com/onyx/app/ui/bots/GroupChatScreen.kt").readText()
        val voice = File("src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt").readText()
        // Liste des bots: réflexion pendant qu'il compose, attente sinon.
        assertTrue(conv.contains("activity.working -> BotMood.THINKING"))
        assertTrue(conv.contains("activity.needsYou -> BotMood.WAITING"))
        // Tuiles: même règle.
        assertTrue(bots.contains("activity.working -> BotMood.THINKING"))
        // Fil d'une salle: succès à la fin, erreur quand c'est raté.
        assertTrue(chat.contains("else if (message.failed) BotMood.ERROR"))
        assertTrue(chat.contains("else BotMood.PLEASED"))
        // Scène vocale: écoute et réflexion sur les visages de l'orbe.
        assertTrue(voice.contains("VoicePhase.LISTENING -> BotMood.LISTENING"))
        assertTrue(voice.contains("VoicePhase.THINKING -> BotMood.THINKING"))
    }

    @Test
    fun `la planche des etats est une maquette de debug`() {
        val board = File("src/debug/java/com/onyx/app/debug/BotExpressionsActivity.kt")
        assertTrue("la planche vit dans le jeu de sources debug", board.isFile)
        val text = board.readText()
        assertTrue(text.contains("BotMood.ERROR"))
        assertTrue("PAROLE passe par la vraie scène vocale", text.contains("VoiceFace("))
    }

    @Test
    fun `aucun texte visible ne nomme la plateforme d origine`() {
        val literal = Regex("\"([^\"\\\\]|\\\\.)*\"")
        val sources = listOf(
            "VoiceFace.kt" to face,
            "VoiceScreen.kt" to File("src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt").readText(),
            "BotExpressionsActivity.kt" to File("src/debug/java/com/onyx/app/debug/BotExpressionsActivity.kt").readText(),
        )
        sources.forEach { (name, source) ->
            val texts = literal.findAll(source).map { it.value }
            listOf("iOS", "iPhone", "iPad", "Apple", "Siri").forEach { word ->
                assertTrue("« $word » ne doit apparaître dans aucun texte visible de $name", texts.none { it.contains(word) })
            }
        }
    }
}
