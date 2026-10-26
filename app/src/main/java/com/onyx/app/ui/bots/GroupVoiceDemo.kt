package com.onyx.app.ui.bots

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.voice.VoicePhase
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * La maquette de boutique de la scène vocale d'une salle, comme `-echo.groupVoice`: le
 * lancement la demande (`-e onyx.groupVoiceDemo speaking`, ou `listening`), et la scène se pose sur
 * une salle scriptée — **sans serveur, sans salle ouverte et sans micro**.
 *
 * C'est ce qui permet d'éprouver la scène de parole sur un émulateur qui n'a ni microphone ni
 * moteur de reconnaissance: la réplique, son bot et son niveau sont **injectés**, pas devinés.
 * Rien de tout ceci ne sert hors d'un APK de debug.
 */
internal class GroupVoiceDemo(
    val thread: GroupVoiceThread,
    val bots: List<BotProfile>,
    /** Le bot dont la réplique est lue; `null` quand c'est le tour de quelqu'un d'autre. */
    val speakerId: String?,
    val phase: VoicePhase,
    /** Le niveau de la voix lue, celui sur lequel la bouche du grand visage bat. */
    val level: Float,
) {
    companion object {
        /** La sienne des trois: c'est elle qui reçoit l'aura de son visage. */
        private const val QUILL = "1:blob:happy:smile:blush:4:0:0"

        /** Les trois faces de la salle, pour que l'en-tête et l'orbe aient des visages distincts. */
        private val bots = listOf(
            BotProfile(name = "default", title = "Onyx", isDefault = true, faceCode = "1:bubble:happy:smile:none:13:0:0"),
            BotProfile(name = "scout", title = "Scout", faceCode = "1:egg:happy:smile:freckles:3:0:0"),
            BotProfile(name = "quill", title = "Quill", faceCode = QUILL),
        )

        /** Le round du modèle, mot pour mot: la question, puis une réponse par bot. */
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
         * Le niveau de la maquette à la seconde `t`, comme `OrbFace.demoLevel`: deux ondes
         * qui battent, jamais tout à fait muettes. La réplique est simulée — c'est dit — et la
         * bouche du grand visage **bouge** donc d'une image à l'autre, comme elle bougerait avec
         * une vraie voix.
         */
        fun levelAt(t: Float): Float = 0.3f + 0.45f * kotlin.math.abs(sin(t * 5.1f) * sin(t * 1.7f + 0.8f))

        /**
         * La scène demandée: `speaking` (le défaut) pose **Quill en train de parler**, sa réplique
         * étant lue; `listening` pose le micro ouvert, tous les visages dans l'orbe.
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
    }

    /** Le fil de la maquette: une salle scriptée qui ne parle à personne. */
    private class DemoThread(
        lines: MutableStateFlow<List<GroupVoiceLine>>,
        talking: MutableStateFlow<Boolean>,
    ) : GroupVoiceThread {
        override val roomName: String = "Weekend hike"
        override val server: ServerConfig = ServerConfig(id = "demo")
        override val lines: StateFlow<List<GroupVoiceLine>> = lines
        override val isTalking: StateFlow<Boolean> = talking
        override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)
        override fun ask(question: String) = Unit
        override fun haltTalking() = Unit
    }
}
