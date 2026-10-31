package com.onyx.app

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupDraft
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.ui.bots.GroupSending
import com.onyx.app.ui.bots.GroupVoiceLine
import com.onyx.app.ui.bots.GroupVoiceReplies
import com.onyx.app.ui.bots.GroupVoiceScene
import com.onyx.app.ui.bots.GroupRoomFeedState
import com.onyx.app.ui.bots.groupVoicePhaseTitle
import com.onyx.app.ui.bots.groupVoiceRound
import com.onyx.app.ui.bots.voiceLines
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.voice.VoicePhase
import java.util.Locale
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupVoiceTest {
    private val scout = BotProfile(name = "scout", title = "Scout")
    private val sage = BotProfile(name = "sage", title = "Sage")
    private val room = GroupRoom(
        id = "room-1",
        name = "Weekend hike",
        members = listOf(
            GroupMember(id = "default", profile = "default", handle = "hermes", displayName = "Onyx"),
            GroupMember(id = "scout", profile = "scout", handle = "scout", displayName = "Scout"),
            GroupMember(id = "sage", profile = "sage", handle = "sage", displayName = "Sage"),
        ),
    )

    @Test
    fun `le round vocal commence au dernier message utilisateur et garde les brouillons`() {
        val lines = listOf(
            mine("old", "Ancienne question"),
            from("old-answer", "scout", "Ancienne réponse", seq = 2),
            mine("ask", "Nouvelle question", seq = 3),
            from("draft", "scout", "En cours", live = true),
            from("answer", "sage", "Réponse", seq = 5),
        )

        assertEquals(listOf("ask", "draft", "answer"), groupVoiceRound(lines).map { it.id })
    }

    @Test
    fun `le titre de phase nomme le bot qui parle ou celui qui ecrit`() {
        assertEquals(
            "Scout is speaking",
            groupVoicePhaseTitle(VoicePhase.SPEAKING, speaker = "Scout", writer = "Sage", isTalking = true),
        )
        assertEquals(
            "Sage is writing",
            groupVoicePhaseTitle(VoicePhase.IDLE, speaker = null, writer = "Sage", isTalking = true),
        )
        assertEquals(
            "The bots are talking",
            groupVoicePhaseTitle(VoicePhase.IDLE, speaker = null, writer = null, isTalking = true),
        )
    }

    /**
     * Le défaut rapporté : la scène affichait « LES BOTS DISCUTENT » — une légende **générique au
     * pluriel** — alors qu'un bot parlait. La légende doit nommer le bot qui parle, et la forme
     * générique ne sert **que** pendant une attente réelle (la salle travaille, personne ne parle,
     * personne n'écrit). Une salle qui s'est tue sans rien dire ne la garde pas non plus : elle dit
     * que personne ne répond.
     */
    @Test
    fun `la legende generique ne remplace jamais un bot qui parle`() {
        // Un bot qui parle est nommé, quoi qu'il arrive autour — même si la salle se dit au travail.
        for (phase in listOf(VoicePhase.SPEAKING)) {
            for (talking in listOf(true, false)) {
                for (writer in listOf(null, "Sage")) {
                    assertEquals(
                        "le bot qui parle est nommé",
                        "Quill is speaking",
                        groupVoicePhaseTitle(phase, speaker = "Quill", writer = writer, isTalking = talking),
                    )
                }
            }
        }
        // La forme générique : seulement pendant une attente réelle.
        assertEquals(
            "The bots are talking",
            groupVoicePhaseTitle(VoicePhase.THINKING, speaker = null, writer = null, isTalking = true),
        )
        assertEquals(
            "The bots are talking",
            groupVoicePhaseTitle(VoicePhase.IDLE, speaker = null, writer = null, isTalking = true),
        )
        // Une salle qui s'est tue sans répondre : plus de générique du tout.
        for (phase in listOf(VoicePhase.IDLE, VoicePhase.THINKING)) {
            assertEquals(
                "personne ne répond",
                "Nobody is answering",
                groupVoicePhaseTitle(phase, speaker = null, writer = null, isTalking = true, stalled = true),
            )
            assertEquals(
                "même salle au repos déclarée",
                "Nobody is answering",
                groupVoicePhaseTitle(phase, speaker = null, writer = null, isTalking = false, stalled = true),
            )
        }
        // Et un bot qui écrit garde son nom, même après un abandon : le verdict « personne ne
        // répond » ne recouvre jamais un bot qui a un nom.
        assertEquals(
            "Sage is writing",
            groupVoicePhaseTitle(VoicePhase.THINKING, speaker = null, writer = "Sage", isTalking = true),
        )
        for (phase in listOf(VoicePhase.IDLE, VoicePhase.THINKING)) {
            assertEquals(
                "le bot qui écrit passe avant l'abandon",
                "Sage is writing",
                groupVoicePhaseTitle(
                    phase, speaker = null, writer = "Sage", isTalking = true, stalled = true,
                ),
            )
        }
    }

    /**
     * Un bot qui **écrit** fait bouger la salle : c'est ce mouvement qui tient le tour ouvert, et
     * sans lui le garde-fou « la salle travaille sans rien produire » rendait le micro au milieu de
     * l'écriture d'un bot — et la reprise d'écoute jetait la réponse qui suivait.
     */
    @Test
    fun `un bot qui ecrit fait bouger la salle`() {
        val replies = GroupVoiceReplies(emptyList())
        val writing = from("draft-1", "sage", "Il écrit", live = true, draftId = "draft-1", after = 1)

        assertTrue("le premier mot du brouillon est un mouvement", replies.update(listOf(writing), held = false))
        assertFalse("le même texte revu n'en est plus un", replies.update(listOf(writing), held = false))

        val longer = writing.copy(text = "Il écrit encore")
        assertTrue("un mot de plus en est un", replies.update(listOf(longer), held = false))
    }

    /**
     * **L'état ne ment pas.** « PRÊT » ne s'écrit que lorsque le tour est vraiment fini : une file
     * vocale non vide, ou un message encore en route vers la salle, et l'écran dit que ça discute.
     * C'est le défaut de la capture : « PRÊT » alors que les réponses étaient là.
     */
    @Test
    fun `l etat ne dit pas pret avec une file non vide`() {
        assertEquals(
            "The bots are talking",
            groupVoicePhaseTitle(VoicePhase.IDLE, speaker = null, writer = null, isTalking = false, pending = 2),
        )
        assertEquals(
            "The bots are talking",
            groupVoicePhaseTitle(VoicePhase.IDLE, speaker = null, writer = null, isTalking = false, sending = true),
        )
        assertEquals(
            "Sage is writing",
            groupVoicePhaseTitle(
                VoicePhase.IDLE, speaker = null, writer = "Sage", isTalking = false, pending = 1,
            ),
        )
        // Le tour fini, et rien dans la file : là seulement, PRÊT.
        assertEquals(
            "Ready",
            groupVoicePhaseTitle(VoicePhase.IDLE, speaker = null, writer = null, isTalking = false),
        )
        // Et en français, mis en capitales par l'écran : c'est ce que la personne lit.
        assertEquals("Prêt", OnyxLocalization.translate("Prêt", Locale.FRENCH))
    }

    @Test
    fun `la file attend la fin du brouillon et ne met jamais deux fois la reponse`() {
        val old = from("old", "scout", "Déjà lue", seq = 1)
        // La voix commence après ce que la salle a déjà : `old` n'est pas relu.
        val replies = GroupVoiceReplies(listOf(old))
        val draft = from("new", "sage", "En cours", live = true, draftId = "draft-1", after = 1)
        replies.update(listOf(old, draft), held = false)
        assertEquals(emptyList<GroupVoiceLine>(), replies.pending)

        val finished = draft.copy(text = "Réponse complète", live = false)
        replies.update(listOf(old, finished), held = false)

        assertEquals(listOf("new"), replies.pending.map { it.id })
        assertEquals("Réponse complète", replies.pending.single().text)

        // Le même brouillon revu ne rentre pas deux fois, et la lecture le retire de la file.
        replies.update(listOf(old, finished), held = false)
        assertEquals(1, replies.pending.size)
        assertEquals("new", replies.takeNext()?.id)
        assertNull(replies.takeNext())
    }

    @Test
    fun `le message d un brouillon deja lu ne le rejoue pas`() {
        val replies = GroupVoiceReplies(emptyList())
        val draft = from("draft-1", "sage", "Bonjour", live = false, draftId = "draft-1", after = 4)
        replies.update(listOf(draft), held = false)
        assertEquals(listOf("draft-1"), replies.pending.map { it.id })

        // La salle écrit le message de cette réponse : c'est le même mot, lu une fois.
        val message = from("seq-5", "sage", "Bonjour", seq = 5)
        replies.update(listOf(message), held = false)
        assertEquals(listOf("draft-1"), replies.pending.map { it.id })
    }

    @Test
    fun `un bot qui passe n est jamais lu`() {
        val replies = GroupVoiceReplies(emptyList())
        val passing = from("pass-1", "sage", " pass ", live = false, draftId = "pass-1", after = 1)
        val blank = from("pass-2", "scout", "  ", live = false, draftId = "pass-2", after = 1)

        replies.update(listOf(passing, blank), held = false)

        assertEquals(emptyList<GroupVoiceLine>(), replies.pending)
    }

    @Test
    fun `la file suit l ordre ou les reponses ont commence`() {
        val replies = GroupVoiceReplies(emptyList())
        val second = from("b", "sage", "Deuxième", live = false, draftId = "b", after = 9)
        val first = from("a", "scout", "Première", live = false, draftId = "a", after = 3)

        // Arrivées dans le désordre : celle qui a commencé le plus tôt est lue d'abord.
        replies.update(listOf(second, first), held = false)

        assertEquals(listOf("a", "b"), replies.pending.map { it.id })
    }

    @Test
    fun `un brouillon lache avant d etre entier n est pas lu ensuite`() {
        val replies = GroupVoiceReplies(emptyList())
        val draft = from("draft-1", "sage", "Il écrit", live = true, draftId = "draft-1", after = 1)
        replies.update(listOf(draft), held = false)
        replies.dropAll()

        // Il finit d'écrire après que la personne a repris la parole : il n'est pas lu.
        replies.update(listOf(draft.copy(text = "Voilà", live = false)), held = false)

        assertEquals(emptyList<GroupVoiceLine>(), replies.pending)
    }

    @Test
    fun `un rien tenu ne lit plus rien`() {
        val replies = GroupVoiceReplies(emptyList())
        val line = from("answer", "scout", "Une réponse", seq = 2)
        replies.update(listOf(line), held = false)
        assertEquals(1, replies.pending.size)

        replies.update(listOf(line), held = true)

        assertEquals(emptyList<GroupVoiceLine>(), replies.pending)
    }

    @Test
    fun `les lignes d une salle reprennent le journal, ce qui part et ce qui s ecrit`() {
        val state = GroupRoomFeedState(
            room = room,
            events = listOf(
                user("Bonjour à tous", seq = 1),
                member("On it.", seq = 2, who = "default"),
                member("Le sentier est ouvert.", seq = 3, who = "scout"),
            ),
            sending = listOf(GroupSending(id = "send-1", text = "Et le parking ?")),
            drafts = listOf(GroupDraft(id = "draft-1", memberID = "sage", text = "Je regarde", after = 3)),
        )

        val lines = state.voiceLines()

        assertEquals(
            listOf("voice-seq-1", "voice-seq-2", "voice-seq-3", "voice-sending-send-1", "voice-draft-draft-1"),
            lines.map { it.id },
        )
        val mine = lines.first()
        assertTrue(mine.isMine)
        assertEquals("Bonjour à tous", mine.text)
        val onyx = lines[1]
        assertEquals("Onyx", onyx.name)
        assertEquals("default", onyx.profile)
        assertEquals(2, onyx.seq ?: -1)
        val scout = lines[2]
        assertEquals("Scout", scout.name)
        assertTrue(lines[3].waiting)
        assertEquals("Et le parking ?", lines[3].text)
        val draft = lines[4]
        assertTrue(draft.live)
        assertEquals("sage", draft.profile)
        assertEquals("draft-1", draft.draftId)
        assertEquals(3, draft.after)
    }

    @Test
    fun `l orbe ne montre qu un visage, celui qui parle, sinon le dernier qui a parle`() {
        val lines = listOf(
            mine("ask", "Question", seq = 1),
            from("a1", "scout", "Réponse A", seq = 2),
            from("a2", "sage", "Réponse B", seq = 3),
        )
        val scene = GroupVoiceScene(
            roomName = room.name,
            server = ServerConfig(id = "s"),
            bots = listOf(scout, sage),
            lines = lines,
            speakerId = null,
            isTalking = false,
            isHeld = false,
        )

        // Au repos : personne ne parle, et l'orbe montre **le dernier** qui a parlé — un seul visage.
        assertNull(scene.speaker)
        assertFalse(scene.orbFaceIsSpeaking)
        assertEquals("l'orbe montre le dernier qui a parlé", sage, scene.orbFace)

        // Pendant la lecture : c'est le bot qui parle, et lui seul.
        val speaking = scene.copy(speakerId = "scout")
        assertTrue(speaking.orbFaceIsSpeaking)
        assertEquals("le grand visage est celui qui parle", scout, speaking.orbFace)

        // Une salle qui n'a encore rien dit : le premier de la salle, jamais personne d'autre.
        assertEquals(scout, scene.copy(lines = listOf(mine("ask", "Question", seq = 1))).orbFace)
    }

    @Test
    fun `la scene nomme celui qui parle et celui qui ecrit`() {
        val lines = listOf(
            mine("ask", "Question", seq = 1),
            from("draft", "sage", "En cours", live = true),
        )
        val scene = GroupVoiceScene(
            roomName = room.name,
            server = ServerConfig(id = "s"),
            bots = listOf(scout, sage),
            lines = lines,
            speakerId = "sage",
            isTalking = true,
            isHeld = false,
        )

        assertEquals(sage, scene.speaker)
        assertEquals(sage, scene.writer)
        assertNull(scene.lastAnswer)
        assertEquals(listOf("ask", "draft"), scene.round.map { it.id })
        assertFalse(scene.lastAnswer != null)
    }

    private fun mine(id: String, text: String, seq: Int? = null) =
        GroupVoiceLine(id = id, profile = null, name = "", text = text, seq = seq)

    private fun from(
        id: String,
        who: String,
        text: String,
        seq: Int? = null,
        live: Boolean = false,
        draftId: String? = null,
        after: Int = 0,
    ) = GroupVoiceLine(
        id = id,
        profile = who,
        name = who,
        text = text,
        live = live,
        seq = seq,
        draftId = draftId,
        after = after,
    )

    private fun user(text: String, seq: Int) = GroupEvent(
        seq = seq,
        kind = "message.user",
        actorKind = "user",
        actorID = null,
        payload = buildJsonObject { put("text", text) },
        createdAtMillis = 1_000L * seq,
    )

    private fun member(text: String, seq: Int, who: String) = GroupEvent(
        seq = seq,
        kind = "message.member",
        actorKind = "member",
        actorID = who,
        payload = buildJsonObject { put("text", text) },
        createdAtMillis = 1_000L * seq,
    )
}
