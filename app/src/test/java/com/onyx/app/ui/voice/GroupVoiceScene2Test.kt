package com.onyx.app.ui.voice

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La scène vocale d'une salle au design du modèle 2.1: **un seul grand visage** — celui qui
 * parle — avec l'aura de sa couleur, la légende « <NOM> PARLE » sous l'orbe, le fil avec visage et
 * nom par réponse, et **la voix propre à chaque bot**.
 *
 * Vérifications de source: elles portent sur le câblage, pas sur le rendu — c'est la capture
 * d'émulateur qui juge le rendu.
 */
class GroupVoiceScene2Test {

    private val screen = File("src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt").readText()
    private val face = File("src/main/java/com/onyx/app/ui/voice/VoiceFace.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val groupVoice = File("src/main/java/com/onyx/app/ui/bots/GroupVoice.kt").readText()
    private val room = File("src/main/java/com/onyx/app/ui/bots/GroupRoomScreen.kt").readText()
    private val session = File("../core/src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
    private val output = File("../core/src/main/java/com/onyx/app/voice/SpeechOutput.kt").readText()

    @Test
    fun `le grand visage de l orbe est celui du bot qui parle, et il bouge`() {
        val orb = screen.substringAfter("private fun GroupVoiceOrbDisplay(")
            .substringBefore("private fun GroupVoiceFaces(")
        // Un seul visage: celui du bot dont la réponse est lue (`group.speaker`), en grand.
        assertTrue(orb.contains("val speaker = group.speaker.takeIf { phase == VoicePhase.SPEAKING }"))
        assertTrue("le grand visage est vivant", orb.contains("VoiceFace("))
        assertTrue("à la taille de `OnyxVoiceFace.size`", orb.contains("size = 196.dp"))
        assertTrue("il bat avec le niveau de la voix lue", orb.contains("level = { speakingLevel }"))
        // Les autres ne sont plus dessinés à côté: ils s'effacent (`GroupVoiceFaces` au repos).
        assertTrue(orb.contains("GroupVoiceFaces("))
    }

    @Test
    fun `l aura prend la couleur du visage qui parle`() {
        val orb = screen.substringAfter("private fun GroupVoiceOrbDisplay(")
            .substringBefore("private fun GroupVoiceFaces(")
        assertTrue(orb.contains("val aura = speaker?.let { botFaceAuraColour(it.name, it.faceCode, theme) } ?: tint"))
        // C'est bien l'aura et la lueur du visage qui la prennent; les anneaux gardent la phase.
        assertTrue(orb.contains("aura.copy(alpha = if (phase == VoicePhase.LISTENING || speaker != null) 0.45f else 0.16f)"))
        assertTrue(orb.contains("0.68f to aura.copy("))
        assertTrue(orb.contains("VoiceHalos(\n            color = tint,"))
    }

    @Test
    fun `la teinte de l aura suit la page, jamais le bot`() {
        // Une seule teinte pour les deux pages laisse, dans l'un des deux thèmes, un orbe délavé:
        // une couleur profonde sur du noir n'est qu'un halo éteint, une claire sur du blanc un
        // blanc bleuté. L'aura prend donc le clair du dégradé sur une page sombre, le profond sur
        // une page claire — c'est le visage qui donne la couleur, la page qui donne la teinte.
        val look = File("../core/src/main/java/com/onyx/app/ui/design/BotLook.kt").readText()
        val aura = look.substringAfter("fun botFaceAuraColour(").substringBefore("/** Le visage qu'un nom")
        assertTrue("la teinte est choisie par la page", aura.contains("if (theme.dark) light else deep"))
        assertTrue("la couleur reste celle du visage", aura.contains("BotFace.gradient(face.colour, theme)"))
        // Le refrain: la légende, elle, garde la couleur de la phase.
        assertTrue(screen.contains("color = statusColor,"))
    }

    @Test
    fun `la legende est centree sous l orbe, comme la reference`() {
        // Le noeud occupe toute la largeur (le nouveau mot pousse l'ancien d'un bloc): sans
        // `textAlign`, le texte se collait à la marge de gauche, à hauteur du fil, là où la
        // référence le pose au milieu sous le visage. Mesuré sur le dump: `x[63-1017]` avant.
        val title = screen.substringAfter("AnimatedContent(\n                targetState = OnyxLocalization[statusTitle],")
            .substringBefore("// Le chronomètre d'écoute")
        assertTrue(title.contains("textAlign = TextAlign.Center,"))
        assertTrue("le noeud occupe la largeur, donc l'alignement est dans le style", title.contains(".fillMaxWidth()"))
    }

    @Test
    fun `la legende est le nom du bot qui parle, en capitales et de la couleur de la phase`() {
        // `phaseTitle`: « <NOM> is speaking », mis en capitales et espacé par l'écran.
        assertTrue(screen.contains("groupVoicePhaseTitle(phase, it.speaker?.label(), it.writer?.label(), it.isTalking)"))
        assertTrue(screen.contains("text = title.uppercase()"))
        assertTrue(screen.contains("letterSpacing = 1.2.sp"))
        assertTrue(screen.contains("color = statusColor"))
        // Et le mot lui-même se dit en français quand l'app est en français.
        assertEquals("Quill parle", OnyxLocalization.translate("Quill is speaking", Locale.FRENCH))
        assertEquals("Quill is speaking", OnyxLocalization.translate("Quill is speaking", Locale.ENGLISH))
    }

    @Test
    fun `le fil porte un visage et un nom par reponse, et le tour en cours se distingue`() {
        val exchange = screen.substringAfter("private fun GroupVoiceExchange(")
            .substringBefore("private fun GroupVoiceLineRow(")
        assertTrue(exchange.contains("speaking = line.profile != null && line.profile == group.speakerId"))
        assertTrue(screen.contains("private fun GroupVoiceLineRow(line: GroupVoiceLine, server: ServerConfig, speaking: Boolean = false)"))
        assertTrue(screen.contains("speaking = speaking,"))
        // Le visage et le nom: `RoomMemberRow` les dessine déjà comme le fil de la salle.
        val member = room.substringAfter("internal fun RoomMemberRow(").substringBefore("/** Une réponse en train")
        assertTrue(member.contains("BotLook("))
        assertTrue(member.contains("speaking: Boolean = false"))
        assertTrue("le tour lu porte un haut-parleur", member.contains("Symbols.SpeakerWave"))
    }

    @Test
    fun `chaque replique est lue avec la voix de son bot`() {
        // Le coordinateur passe le profil du bot à la lecture, à la première lecture comme au rappel.
        assertTrue(groupVoice.contains("session.speak(reply.text, speaker = reply.profile)"))
        assertTrue(groupVoice.contains("session.speak(last.text, speaker = last.profile)"))
        assertTrue(session.contains("fun speak(text: String, listenAfterSpeech: Boolean = true, speaker: String? = null)"))
        assertTrue(session.contains("output.speak(clean, replyLanguageTag, speaker)"))
        // Et c'est le réglage du bot qui décide: voix du synthétiseur, voix Kokoro, ou celle d'Onyx.
        assertTrue(output.contains("BotVoices.onDevice("))
        assertTrue(output.contains("BotVoices.kokoro(speakingAs, botVoices, kokoroVoice, kokoroVoices, botNames)"))
        assertTrue(output.contains("BotVoices.speed(speaker, botVoices, rate)"))
        assertTrue(output.contains("applyOnDeviceVoice(speaker)"))
        // Les bots de la salle sont annoncés à la lecture, pour que deux n'aient pas la même voix.
        assertTrue(app.contains("voice.output.setBotRoster(roster)"))
        assertFalse("aucune hauteur n'est inventée", output.contains("setPitch"))
    }

    @Test
    fun `la bouche bat avec la voix qui sort, pas avec un niveau invente`() {
        // Le niveau vient de la vraie amplitude quand la voix est de l'audio, sinon d'un niveau
        // tenu — dit comme tel — parce que le synthétiseur n'expose aucune mesure.
        assertTrue(output.contains("val replyLevel: StateFlow<Float>"))
        assertTrue(output.contains("audioPlayer.onLevel = { level -> _replyLevel.value = level }"))
        assertTrue(output.contains("private const val SYNTHESISED_SPEAKING_LEVEL"))
        assertTrue(session.contains("val replyLevel: StateFlow<Float> get() = output.replyLevel"))
        assertTrue(app.contains("speakingLevel = soundingLevel"))
        // Et le visage suit ce niveau, une image sur trente.
        assertTrue(face.contains("level = level()"))
        assertTrue(face.contains("voicePose(seconds = seconds, phase = phase, level = level()"))
    }

    @Test
    fun `Reduce Motion fige le visage`() {
        assertTrue(face.contains("val reduceMotion = LocalReduceMotion.current"))
        assertTrue(face.contains("if (reduceMotion) {\n            seconds = 0f\n            return@LaunchedEffect\n        }"))
        assertTrue("un visage figé garde ses yeux, sa bouche et son regard", face.contains("BotPose()"))
    }

    @Test
    fun `la scene se laisse eprouver par des etats injectes, sans micro`() {
        val demo = File("src/main/java/com/onyx/app/ui/bots/GroupVoiceDemo.kt").readText()
        assertTrue(app.contains("private const val EXTRA_GROUP_VOICE_DEMO = \"onyx.groupVoiceDemo\""))
        assertTrue(app.contains("GroupVoiceDemo.from("))
        assertTrue("la maquette n'ouvre ni micro ni lecture", app.contains("if (demo != null) return@LaunchedEffect"))
        assertTrue(demo.contains("speakerId = if (speaking) \"quill\" else null"))
        assertTrue(demo.contains("phase = if (speaking) VoicePhase.SPEAKING else VoicePhase.LISTENING"))
        assertTrue("la réplique est injectée, avec son niveau", demo.contains("level = levelAt(0f)"))
        assertTrue("et le niveau bat comme une voix", demo.contains("fun levelAt(t: Float): Float"))
    }

    @Test
    fun `aucun texte visible ne nomme la plateforme d origine`() {
        val literal = Regex("\"([^\"\\\\]|\\\\.)*\"")
        val sources = listOf("VoiceScreen.kt" to screen, "VoiceFace.kt" to face, "OnyxApp.kt" to app)
        sources.forEach { (name, source) ->
            // Seuls les textes visibles comptent: les commentaires, eux, citent qui sert
            // de spécification (« Comme `VoiceView` »).
            val texts = literal.findAll(source).map { it.value }
            listOf("iOS", "iPhone", "iPad", "Apple", "Siri").forEach { word ->
                assertFalse(
                    "« $word » ne doit apparaître dans aucun texte visible de $name",
                    texts.any { it.contains(word) },
                )
            }
        }
    }
}
