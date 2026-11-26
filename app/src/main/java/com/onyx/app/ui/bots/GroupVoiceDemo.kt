package com.onyx.app.ui.bots

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.voice.VoiceDiagnostics
import com.onyx.app.voice.VoicePhase
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The shop-demo fixture of a room's voice scene, like the corresponding flag of the reference: the
 * launch requests it (`-e onyx.groupVoiceDemo speaking`, or `listening`), and the scene sits on
 * a scripted room — **no server, no open room and no microphone**.
 *
 * This is what makes it possible to test the speech scene on an emulator that has neither microphone nor
 * recognition engine: the reply, its bot and its level are **injected**, not guessed.
 * None of this is of any use outside a debug APK.
 */
internal class GroupVoiceDemo(
    val thread: GroupVoiceThread,
    val bots: List<BotProfile>,
    /** The bot whose reply is being read; `null` when it is someone else's turn. */
    val speakerId: String?,
    val phase: VoicePhase,
    /** The level of the voice being read, the one on which the big face's mouth beats. */
    val level: Float,
    /**
     * True for the fixture that **plays the loop** ([live]): the scene then follows the **real**
     * phase and speaker (`GroupVoice`), the fixture providing only the room. False for the
     * frozen scene, where everything is injected.
     */
    val live: Boolean = false,
) {
    companion object {
        /** Its own of the three: it is the one that receives its face's aura. */
        private const val QUILL = "1:blob:happy:smile:blush:4:0:0"

        /** The room's three faces, so that the header and the orb have distinct faces. */
        private val bots = listOf(
            BotProfile(name = "default", title = "Onyx", isDefault = true, faceCode = "1:bubble:happy:smile:none:13:0:0"),
            BotProfile(name = "scout", title = "Scout", faceCode = "1:egg:happy:smile:freckles:3:0:0"),
            BotProfile(name = "quill", title = "Quill", faceCode = QUILL),
        )

        /** The reference's round, word for word: the question, then one answer per bot. */
        private val round = listOf(
            GroupVoiceLine(
                id = "demo-seq-1",
                profile = null,
                name = "",
                text = "We're doing Falls Loop on Saturday. Who's on what?",
                seq = 1,
            ),
            GroupVoiceLine(
                id = "demo-seq-2",
                profile = "default",
                name = "Onyx",
                faceCode = bots[0].faceCode,
                text = "On it. Saturday at 8 is on your calendar, with an alarm at 6:45.",
                seq = 2,
            ),
            GroupVoiceLine(
                id = "demo-seq-3",
                profile = "scout",
                name = "Scout",
                faceCode = bots[1].faceCode,
                text = "The trail's open, but it's muddy past the second footbridge. Boots, not sneakers.",
                seq = 3,
            ),
            GroupVoiceLine(
                id = "demo-seq-4",
                profile = "quill",
                name = "Quill",
                faceCode = QUILL,
                text = "And here's your invite: Falls Loop, Saturday at 8. Bring layers. Coffee's on me after.",
                seq = 4,
            ),
        )

        /**
         * The fixture's level at second `t`, like the reference's fixture level: two waves
         * that beat, never quite silent. The reply is simulated — it is said so — and the
         * big face's mouth therefore **moves** from one frame to the next, as it would move with
         * a real voice.
         */
        fun levelAt(t: Float): Float = 0.3f + 0.45f * kotlin.math.abs(sin(t * 5.1f) * sin(t * 1.7f + 0.8f))

        /**
         * The requested scene: `speaking` (the default) sets up **Quill speaking**, its reply
         * being read; `listening` sets up the open microphone, all the faces in the orb.
         */
        fun from(variant: String?): GroupVoiceDemo? {
            val name = variant?.trim()?.lowercase() ?: return null
            val speaking = name != "listening"
            return GroupVoiceDemo(
                thread = DemoThread(
                    lines = MutableStateFlow(round),
                    talking = MutableStateFlow(!speaking),
                ),
                bots = bots,
                speakerId = if (speaking) "quill" else null,
                phase = if (speaking) VoicePhase.SPEAKING else VoicePhase.LISTENING,
                level = levelAt(0f),
            )
        }

        /**
         * The fixture that **plays the loop**: a scripted room that **answers**, bot after bot.
         *
         * It is set up with the scene flag (`-e onyx.groupVoiceDemo file`) **and** the engine
         * without microphone (`-e onyx.voiceScenario phrase`): listening, the question, the queue of answers
         * — one per bot, each read with its profile — and the resumption of listening then run
         * **for real**, with no server, no open room and no microphone. This is what makes a
         * room's conversation provable on an emulator.
         */
        fun live(scope: CoroutineScope): GroupVoiceDemo = GroupVoiceDemo(
            thread = ScriptedGroupRoom(scope),
            bots = bots,
            speakerId = null,
            phase = VoicePhase.LISTENING,
            level = 0f,
            live = true,
        )
    }
}

/** Delay between two answers of the scripted room: the time to read the one before. */
private const val SCRIPTED_REPLY_GAP_MS = 2_200L

/**
 * The **scripted room that answers**: the counterpart, for the loop, of the fixture's frozen room.
 *
 * It receives the question (`ask`) and writes the answers **one by one** — one bot, then the next —
 * keeping `isTalking` true throughout the turn: this is what replays the "one at a time"
 * rule (listening does not reopen as long as the queue is not finished), with no server and no open
 * room. None of this is of any use outside a debug APK.
 */
private class ScriptedGroupRoom(private val scope: CoroutineScope) : GroupVoiceThread {
    override val roomName: String = "Weekend hike"
    override val server: ServerConfig = ServerConfig(id = "demo")

    private val _lines = MutableStateFlow<List<GroupVoiceLine>>(emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines.asStateFlow()

    private val _talking = MutableStateFlow(false)
    override val isTalking: StateFlow<Boolean> = _talking.asStateFlow()

    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    private var seq = 0

    override fun ask(question: String, askId: String) {
        VoiceDiagnostics.note("voice (démo): la salle reçoit la question ($askId)")
        seq += 1
        val asked = seq
        add(profile = null, name = "", text = question, seq = asked)
        _talking.value = true
        scope.launch {
            for ((who, label, text) in scriptedReplies) {
                delay(SCRIPTED_REPLY_GAP_MS)
                seq += 1
                add(profile = who, name = label, text = text, seq = seq)
            }
            _talking.value = false
        }
    }

    private fun add(profile: String?, name: String, text: String, seq: Int) {
        VoiceDiagnostics.note("voice (démo): écrit ${profile ?: "la question"} (seq=$seq)")
        _lines.value = _lines.value + GroupVoiceLine(
            id = "demo-$seq",
            profile = profile,
            name = name,
            text = text,
            seq = seq,
            after = seq - 1,
        )
    }

    override fun haltTalking() {
        _talking.value = false
    }

    private companion object {
        /** Three bots, three answers: enough to show that the turn does pass from one to the other. */
        val scriptedReplies = listOf(
            Triple("default", "Onyx", "J'ai noté la sortie de samedi à 8 h, avec un réveil à 6 h 45."),
            Triple(
                "scout",
                "Scout",
                "Le sentier est ouvert, mais boueux après la deuxième passerelle : des chaussures.",
            ),
            Triple(
                "quill",
                "Quill",
                "Et voilà l'invitation : samedi à 8 h, prévoyez des couches. Le café est pour moi.",
            ),
        )
    }
}

/** The fixture's thread: a scripted room that talks to nobody. */
private class DemoThread(
    lines: MutableStateFlow<List<GroupVoiceLine>>,
    talking: MutableStateFlow<Boolean>,
) : GroupVoiceThread {
        override val roomName: String = "Weekend hike"
        override val server: ServerConfig = ServerConfig(id = "demo")
        override val lines: StateFlow<List<GroupVoiceLine>> = lines
        override val isTalking: StateFlow<Boolean> = talking
        override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)
        override fun ask(question: String, askId: String) = Unit
        override fun haltTalking() = Unit
    }
