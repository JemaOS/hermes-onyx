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
 * La maquette de boutique de la scène vocale d'une salle, comme le drapeau correspondant de la référence : le
 * lancement la demande (`-e onyx.groupVoiceDemo speaking`, ou `listening`), et la scène se pose sur
 * une salle scriptée — **sans serveur, sans salle ouverte et sans micro**.
 *
 * C'est ce qui permet d'éprouver la scène de parole sur un émulateur qui n'a ni microphone ni
 * moteur de reconnaissance : la réplique, son bot et son niveau sont **injectés**, pas devinés.
 * Rien de tout ceci ne sert hors d'un APK de debug.
 */
internal class GroupVoiceDemo(
    val thread: GroupVoiceThread,
    val bots: List<BotProfile>,
    /** Le bot dont la réplique est lue ; `null` quand c'est le tour de quelqu'un d'autre. */
    val speakerId: String?,
    val phase: VoicePhase,
    /** Le niveau de la voix lue, celui sur lequel la bouche du grand visage bat. */
    val level: Float,
    /**
     * Vrai pour la maquette qui **joue la boucle** ([live]) : la scène suit alors la phase et le
     * locuteur **réels** (`GroupVoice`), la maquette ne fournissant que la salle. Faux pour la
     * scène figée, où tout est injecté.
     */
    val live: Boolean = false,
) {
    companion object {
        /** La sienne des trois : c'est elle qui reçoit l'aura de son visage. */
        private const val QUILL = "1:blob:happy:smile:blush:4:0:0"

        /** Les trois faces de la salle, pour que l'en-tête et l'orbe aient des visages distincts. */
        private val bots = listOf(
            BotProfile(name = "default", title = "Onyx", isDefault = true, faceCode = "1:bubble:happy:smile:none:13:0:0"),
            BotProfile(name = "scout", title = "Scout", faceCode = "1:egg:happy:smile:freckles:3:0:0"),
            BotProfile(name = "quill", title = "Quill", faceCode = QUILL),
        )

        /** Le round de la référence, mot pour mot : la question, puis une réponse par bot. */
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
         * Le niveau de la maquette à la seconde `t`, comme le niveau de maquette de la référence : deux ondes
         * qui battent, jamais tout à fait muettes. La réplique est simulée — c'est dit — et la
         * bouche du grand visage **bouge** donc d'une image à l'autre, comme elle bougerait avec
         * une vraie voix.
         */
        fun levelAt(t: Float): Float = 0.3f + 0.45f * kotlin.math.abs(sin(t * 5.1f) * sin(t * 1.7f + 0.8f))

        /**
         * La scène demandée : `speaking` (le défaut) pose **Quill en train de parler**, sa réplique
         * étant lue ; `listening` pose le micro ouvert, tous les visages dans l'orbe.
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
         * La maquette qui **joue la boucle** : une salle scriptée qui **répond**, bot après bot.
         *
         * Elle se pose avec le drapeau de scène (`-e onyx.groupVoiceDemo file`) **et** le moteur
         * sans micro (`-e onyx.voiceScenario phrase`) : l'écoute, la question, la file des réponses
         * — une par bot, chacune lue avec son profil — et la reprise de l'écoute tournent alors
         * **pour de vrai**, sans serveur, sans salle ouverte et sans micro. C'est ce qui rend la
         * conversation d'une salle prouvable sur un émulateur.
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

/** Délai entre deux réponses de la salle scriptée : le temps de lire celle qui précède. */
private const val SCRIPTED_REPLY_GAP_MS = 2_200L

/**
 * La salle **scriptée qui répond** : le pendant, pour la boucle, de la salle figée de la maquette.
 *
 * Elle reçoit la question (`ask`) et écrit les réponses **une par une** — un bot, puis le suivant —
 * en gardant `isTalking` vrai pendant tout le tour : c'est ce qui rejoue la règle « chacun son
 * tour » (l'écoute ne se rouvre pas tant que la file n'est pas finie), sans serveur ni salle
 * ouverte. Rien de tout ceci ne sert hors d'un APK de debug.
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
        /** Trois bots, trois réponses : de quoi montrer que le tour passe bien de l'un à l'autre. */
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

/** Le fil de la maquette : une salle scriptée qui ne parle à personne. */
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
