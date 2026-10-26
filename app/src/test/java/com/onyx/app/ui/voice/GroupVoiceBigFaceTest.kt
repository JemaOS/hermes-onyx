package com.onyx.app.ui.voice

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.GroupRoomFeedState
import com.onyx.app.ui.bots.GroupVoiceLine
import com.onyx.app.ui.bots.GroupVoiceReplies
import com.onyx.app.ui.bots.GroupVoiceScene
import com.onyx.app.ui.bots.groupVoicePhaseTitle
import com.onyx.app.ui.bots.voiceLines
import com.onyx.app.voice.VoicePhase
import java.io.File
import java.util.Locale
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Pendant la lecture d'un bot dans une salle, le grand visage affiché est celui du bot qui parle,
 * et la légende le nomme. »
 *
 * C'est la propriété que le modèle demande (`VoiceView.groupSpeaker` → `OrbFace.replyFace`,
 * `phaseTitle`) et que le implémentation ratait: **le grand visage ne venait jamais**, l'orbe gardait tous
 * les visages côte à côte et la légende restait générique. Trois raisons, une par épreuve:
 *
 * 1. le fil d'une **salle du serveur** donnait à `GroupVoiceLine.profile` l'**identifiant du
 * membre** (`actorID`) alors que toute la scène y lit un **profil** de bot (`GroupVoiceScene`,
 * `BotVoices`). Dès que la salle distingue les deux — `{"kind":"member","id":…,"profile":…}`,
 * ce que la passerelle envoie — la scène ne trouvait aucun bot: la légende restait
 * « A bot is speaking » et les visages restaient côte à côte;
 * 2. le fil d'une **salle neuve** (créée sur le téléphone) ne portait ni `seq` ni `draftId`, si
 * bien que `GroupVoiceReplies` n'en lisait **rien**: aucun bot ne parlait, donc aucun visage;
 * 3. le bouton du milieu était la relance, **grise quand rien ne se lisait** — là où le modèle
 * met le ⏸ de la pause.
 */
class GroupVoiceBigFaceTest {

    private val scout = BotProfile(name = "scout", title = "Scout")
    private val quill = BotProfile(name = "quill", title = "Quill")
    private val bots = listOf(scout, quill)

    /** La question de la personne, telle que la salle l'a retenue. */
    private fun ask(seq: Int) = GroupVoiceLine(
        id = "voice-ask-$seq",
        profile = null,
        name = "",
        text = "We're doing Falls Loop on Saturday. Who's on what?",
        seq = seq,
        after = seq - 1,
    )

    /** Une réponse de bot, numérotée comme le fil la numérote. */
    private fun answer(seq: Int, bot: BotProfile, text: String, id: String = "voice-$seq") =
        GroupVoiceLine(
            id = id,
            profile = bot.name,
            name = bot.label(),
            text = text,
            seq = seq,
            after = seq - 1,
        )

    @Test
    fun `une salle du serveur pose le visage du bot qui parle, et la legende le nomme`() {
        // Ce que la passerelle envoie: `id` est l'identifiant du membre, `profile` le bot.
        val room = GroupRoom(
            id = "room-1",
            name = "Weekend hike",
            members = listOf(
                GroupMember(id = "m-7", profile = "scout", handle = "scout", displayName = "Scout"),
                GroupMember(id = "m-9", profile = "quill", handle = "quill", displayName = "Quill"),
            ),
        )
        val lines = GroupRoomFeedState(
            room = room,
            events = listOf(
                GroupEvent(
                    seq = 1,
                    kind = "message.user",
                    actorKind = "user",
                    actorID = null,
                    payload = buildJsonObject { put("text", "Who's on what?") },
                    createdAtMillis = 1_000L,
                ),
                GroupEvent(
                    seq = 2,
                    kind = "message.member",
                    actorKind = "member",
                    actorID = "m-7",
                    payload = buildJsonObject { put("text", "Boots, not sneakers.") },
                    createdAtMillis = 2_000L,
                ),
            ),
        ).voiceLines()

        val said = lines.first { !it.isMine }
        // Sa voix et son visage se lisent sur le **profil** du bot, pas sur l'identifiant du membre:
        // `BotVoices` et le roster de la salle ne connaissent que celui-là.
        assertEquals("le fil porte le profil du bot", "scout", said.profile)

        // Ce que le coordinateur annonce à la scène (`GroupVoice.next`: `reply.profile`).
        val scene = GroupVoiceScene(
            roomName = room.name,
            server = ServerConfig(id = "s"),
            bots = bots,
            lines = lines,
            speakerId = said.profile,
            isTalking = true,
            isHeld = false,
        )
        assertEquals("le grand visage est celui du bot qui parle", scout, scene.speaker)
        assertEquals(
            "Scout parle",
            OnyxLocalization.translate(
                groupVoicePhaseTitle(VoicePhase.SPEAKING, scene.speaker?.label(), null, true),
                Locale.FRENCH,
            ),
        )
    }

    @Test
    fun `pendant la lecture d un bot dans une salle, le grand visage est celui qui parle`() {
        val already = listOf(ask(1))
        val replies = GroupVoiceReplies(already)

        // Le bot répond pendant que la voix est ouverte: c'est cette réponse qui doit être lue.
        val said = answer(2, scout, "Boots, not sneakers.")
        replies.update(already + said, held = false)

        val reading = replies.takeNext()
        assertEquals("la réponse du bot est lue, donc il parle", "scout", reading?.profile)

        val scene = GroupVoiceScene(
            roomName = "Weekend hike",
            server = ServerConfig(id = "s"),
            bots = bots,
            lines = already + said,
            speakerId = reading?.profile,
            isTalking = false,
            isHeld = false,
        )
        assertEquals(scout, scene.speaker)
        assertEquals(
            "Scout parle",
            OnyxLocalization.translate(
                groupVoicePhaseTitle(VoicePhase.SPEAKING, scene.speaker?.label(), scene.writer?.label(), false),
                Locale.FRENCH,
            ),
        )
    }

    @Test
    fun `une reponse encore ecrite n est pas lue a moitie, et l est une fois finie`() {
        val already = listOf(ask(1))
        val replies = GroupVoiceReplies(already)

        // Ce qu'un fil local montre pendant l'écriture: un brouillon, sans numéro.
        val writing = GroupVoiceLine(
            id = "voice-writing",
            profile = "quill",
            name = "Quill",
            text = "And here's your inv",
            live = true,
            draftId = "local-writing",
            after = 1,
        )
        replies.update(already + writing, held = false)
        assertNull("rien n'est lu tant que la réponse s'écrit", replies.takeNext())

        // Finie: elle prend son numéro et c'est elle qui passe.
        val done = writing.copy(text = "And here's your invite.", live = false, seq = 2, draftId = null)
        replies.update(already + done, held = false)
        assertEquals("quill", replies.takeNext()?.profile)
    }

    @Test
    fun `le fil d une salle neuve porte de quoi etre lu par la voix`() {
        val viewModel = File("src/main/java/com/onyx/app/ui/bots/GroupChatViewModel.kt").readText()
        // La salle neuve (compositeur local) donne à ses lignes le vocabulaire d'une salle du
        // serveur: un numéro par message, un brouillon tant qu'il s'écrit, le profil de son bot.
        assertTrue("un numéro par question", viewModel.contains("val userSeq = ++messageSequence"))
        assertTrue("et par réponse", viewModel.contains("val replySeq = ++messageSequence"))
        assertTrue(
            "la réponse n'est numérotée qu'une fois finie",
            viewModel.contains("seq = message.seq.takeIf { it > 0 && !message.isStreaming }"),
        )
        assertTrue(
            "ce qui s'écrit est un brouillon",
            viewModel.contains("draftId = if (message.isStreaming) \"local-\" + message.id else null"),
        )
        assertTrue("et le fil porte le profil du bot", viewModel.contains("profile = message.bot?.name"))
    }

    @Test
    fun `les trois boutons de la reference, et la pause qui met vraiment en pause`() {
        val screen = File("src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt").readText()
        val controls = screen.substringAfter("// `VoiceView.controls`")
        assertTrue(
            "une salle met le ⏸ de la référence au milieu",
            controls.contains("if (group != null || phase == VoicePhase.SPEAKING)"),
        )
        assertTrue(controls.contains("symbol = if (isPaused) Symbols.Play else Symbols.Pause"))
        assertTrue(
            "le gris de la relance n'est plus à cette place dans une salle",
            controls.contains("// Le gris de la relance n'y figure plus"),
        )
        // Et il met vraiment la lecture en pause (`SpeechOutput.pause` / `VoiceSession`).
        assertTrue(screen.contains("onClick = onPauseResume"))
        val session = File("../core/src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
        assertTrue(session.contains("fun pauseSpeaking()"))
        assertTrue(session.contains("fun resumeSpeaking()"))
        val output = File("../core/src/main/java/com/onyx/app/voice/SpeechOutput.kt").readText()
        assertTrue("la pause arrête bien la voix", output.contains("fun pause() {"))
        assertTrue("et le texte se reprend où il s'est arrêté", output.contains("speechRemainder(text, currentOffset)"))
    }
}
