package com.onyx.app.ui.voice

import android.content.Context
import com.onyx.app.voice.EngineStopDecision
import com.onyx.app.voice.HeardFrame
import com.onyx.app.voice.ScenarioSpeechInput
import com.onyx.app.voice.SpeechReply
import com.onyx.app.voice.scenarioFromWav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * **La chaîne vocale rejouée sans micro** : la maquette de QA du mode vocal.
 *
 * L'émulateur n'a pas de micro. Tant que tout le parcours dépendait d'un vrai signal, la seule
 * chose qu'on pouvait y prouver était que le moteur **démarre** — jamais qu'une phrase est entendue,
 * qu'une question part, qu'une réponse se lit et que l'écoute reprend. Ce fichier pose les deux
 * pièces qui manquaient (voir `core/.../voice/AudioScenario.kt` pour le moteur) :
 *
 *  - un moteur d'écoute **alimenté par un WAV français** livré dans les ressources — la phrase
 *    donne son enveloppe RMS, donc **les points bougent vraiment** ;
 *  - un lecteur de **test**, qui « dit » la réplique pendant une durée fixe sans dépendre d'un
 *    `TextToSpeech` que l'émulateur peut ne pas avoir.
 *
 * Le drapeau de débogage `onyx.voiceScenario` choisit le scénario ; **sans lui, rien de tout ceci
 * n'est instancié** et l'app se comporte exactement comme avant.
 */
class VoiceScenario(val engine: ScenarioSpeechInput, val reply: SpeechReply)

object VoiceScenarioDemo {

    /** Le drapeau de lancement (`am start -e onyx.voiceScenario phrase`). */
    const val EXTRA = "onyx.voiceScenario"

    /** La phrase française du WAV livré avec l'app. */
    const val ASSET = "voice/phrase-fr.wav"

    /** La question que le scénario fait « entendre ». */
    const val QUESTION = "Quel temps fait-il demain à Lyon ?"

    /** La réponse que la maquette se fait à elle-même — aucun serveur n'existe sur l'émulateur. */
    const val REPLY = "Demain à Lyon, ciel dégagé et dix-huit degrés l'après-midi."

    /** Le scénario par défaut : silence → phrase → silence, puis la boucle complète. */
    const val PHRASE = "phrase"

    /**
     * Les questions d'une **conversation** rejouée : une par tour, jamais deux fois la même.
     *
     * Sans cela, la garde anti-rejeu de la session prendrait la deuxième question pour le rejeu de
     * la première, et le tour suivant ne partirait jamais — la salle comme le solo s'arrêteraient
     * au premier tour.
     */
    val CONVERSATION = listOf(
        QUESTION,
        "Et à Paris, mardi matin ?",
        "Et le vent, il souffle ?",
    )

    /**
     * Le moteur qui **tourne sans rien entendre** : des trames plates à zéro, aucun mot. C'est le
     * scénario qui doit faire parler la couche d'honnêteté (« on n'entend rien ») — le défaut
     * rapporté, reproduit exprès.
     */
    const val SILENT = "silencieux"

    /** Le moteur qui **refuse** : trois sessions échouées d'affilée, jusqu'au plafond et au message. */
    const val FAILING = "echec"

    /**
     * Le moteur qui **a répondu puis ne reçoit plus rien** : zéro trame, aucun mot. C'est le cas
     * exact de la capture rapportée (sept points plats, « À L'ÉCOUTE ») — et c'est ce scénario qui
     * produit la ligne de journal « on n'entend rien ».
     */
    const val MUTE = "muet"

    /** Construit le scénario demandé, ou `null` quand le drapeau est absent. */
    fun from(name: String?, context: Context, scope: CoroutineScope): VoiceScenario? {
        val scenario = name?.trim()?.lowercase() ?: return null
        val frames = when {
            scenario == SILENT || scenario.startsWith("silenc") -> silentFrames()
            scenario == MUTE || scenario.startsWith("muet") -> emptyList()
            scenario == FAILING || scenario.startsWith("echec") || scenario.startsWith("échec") ->
                failingFrames()
            else -> phraseFrames(context)
        }
        // Une phrase rejouée **en conversation** : chaque écoute neuve dit une phrase différente,
        // sinon la session prendrait la deuxième pour le rejeu de la première.
        val conversation = scenario == PHRASE || scenario.startsWith("phrase") ||
            scenario.startsWith("convers")
        return VoiceScenario(
            engine = ScenarioSpeechInput(
                frames = frames,
                scope = scope,
                // « muet » : le moteur a répondu (la session est ouverte), il ne reçoit rien.
                answersOnStart = scenario == MUTE || scenario.startsWith("muet"),
                following = if (conversation) conversationFrames(context) else emptyList(),
            ),
            reply = ScenarioSpeechReply(scope),
        )
    }

    /** Silence → phrase → silence, l'enveloppe prise dans le WAV, la transcription qui grandit. */
    private fun phraseFrames(context: Context): List<HeardFrame> = runCatching {
        val bytes = context.assets.open(ASSET).readBytes()
        scenarioFromWav(bytes, transcript = QUESTION)
    }.getOrElse { emptyList() }

    /** Les phrases **suivantes** de la conversation (la première étant [phraseFrames]). */
    private fun conversationFrames(context: Context): List<List<HeardFrame>> = runCatching {
        val bytes = context.assets.open(ASSET).readBytes()
        CONVERSATION.drop(1).map { question -> scenarioFromWav(bytes, transcript = question) }
    }.getOrElse { emptyList() }

    /**
     * Le moteur qui tourne et ne reçoit **rien** : cent trames strictement plates, aucun mot. La
     * phrase n'est jamais écrite, le niveau ne bouge pas — exactement la capture rapportée.
     */
    private fun silentFrames(): List<HeardFrame> =
        (0 until 200).map { HeardFrame(atMs = it * 60L, level = 0f) }

    /**
     * Le moteur qui refuse : la première session avoue un refus passager (code 5), et chaque
     * reprise en avoue un de plus — jusqu'au plafond de `MAX_ENGINE_ATTEMPTS`, où l'app doit
     * s'arrêter **en le disant**.
     */
    private fun failingFrames(): List<HeardFrame> = listOf(
        HeardFrame(
            atMs = 0L,
            level = 0f,
            failed = true,
            stopDecision = EngineStopDecision.RETRY,
            problem = "Le moteur de reconnaissance est occupé. Touchez l’orbe et réessayez.",
        ),
    )
}

/**
 * Le lecteur de la maquette : il « dit » la réplique pendant une durée fixe, en faisant vivre le
 * niveau de la bouche du visage qui parle, puis rend la main.
 *
 * Aucun `TextToSpeech` n'est sollicité : c'est ce qui rend la boucle « réponse → reprise de
 * l'écoute » prouvable sur un émulateur qui n'a pas de voix de synthèse.
 */
class ScenarioSpeechReply(
    private val scope: CoroutineScope,
    private val durationMs: Long = 1_400L,
) : SpeechReply {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _replyLevel = MutableStateFlow(0f)
    override val replyLevel: StateFlow<Float> = _replyLevel.asStateFlow()

    override var onFinished: (() -> Unit)? = null

    /** La sortie de la maquette : un champ, et le geste [setEarRouting] qui l'écrit. */
    private var atEar: Boolean = false
    override val earRouting: Boolean get() = atEar

    private var job: Job? = null

    override fun setLanguage(tag: String) = Unit

    override fun setEarRouting(atEar: Boolean) {
        this.atEar = atEar
    }

    override fun speak(text: String, languageTag: String?, speaker: String?) {
        job?.cancel()
        _isSpeaking.value = true
        job = scope.launch {
            val steps = (durationMs / STEP_MS).toInt().coerceAtLeast(1)
            repeat(steps) { i ->
                // Une bouche qui « parle » : le niveau ondule, comme une vraie lecture.
                _replyLevel.value = 0.2f + 0.45f * abs(sin(i * 0.7f))
                delay(STEP_MS)
            }
            _replyLevel.value = 0f
            _isSpeaking.value = false
            onFinished?.invoke()
        }
    }

    override fun pause() {
        // La maquette n'a pas de position de lecture : une pause n'a rien à tenir.
    }

    override fun resume(): Boolean = _isSpeaking.value

    override fun stop() {
        job?.cancel()
        job = null
        _replyLevel.value = 0f
        _isSpeaking.value = false
    }

    override fun shutdown() = stop()

    private companion object {
        /** Le pas du niveau de la bouche : 60 ms, comme la sonde d'écoute. */
        const val STEP_MS = 60L
    }
}
