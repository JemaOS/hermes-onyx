package com.onyx.app.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * La chaîne vocale **sans micro** : un moteur de test alimenté par un **fichier audio**.
 *
 * Pourquoi ce fichier existe : l'émulateur n'a pas de micro, et le téléphone de la personne en a un
 * qui ne donne rien au moteur. Dans les deux cas, la boucle complète (ouvrir l'écoute, détecter la
 * fin de parole, envoyer la question, réfléchir, répondre, reprendre l'écoute, s'arrêter sur un mot
 * ou sur le bouton ■, plafonner les échecs) **ne pouvait pas être rejouée**. Elle l'est maintenant :
 *
 *  - [WavReader] lit un WAV PCM sans dépendre d'Android (donc éprouvable sur JVM) ;
 *  - [rmsLevels] en tire l'**enveloppe de niveau** (RMS par pas de temps) — c'est elle qui fait
 *    **bouger les points**, avec de vraies variations ;
 *  - [scenarioFromWav] compose un scénario **silence → phrase → silence** où la transcription
 *    grandit comme celle d'un vrai moteur (des partiels, puis une finale) ;
 *  - [ScenarioSpeechInput] rejoue ce scénario derrière [SpeechInput], exactement comme le moteur
 *    réel : mêmes états, mêmes compteurs, mêmes gestes.
 *
 * Le nom et la nature sont explicites — un **moteur de test**. Il n'est jamais choisi tout seul :
 * la maquette de QA le pose par un drapeau de débogage, et les tests l'instancient à la main.
 */

/** Le plancher de silence d'un moteur : assez bas pour ne jamais franchir un seuil de voix. */
private const val SILENT_RMS = 0.0f

/**
 * La valeur RMS qui vaut « niveau 1 » pour l'affichage : un RMS de 0,25 sur un signal 16 bits
 * (± 8192) est déjà une voix forte. Les mesures d'espeak/TTS tournant autour de 0,10–0,20, une
 * phrase réelle donne donc un niveau visible (≈ 0,4–0,8) et jamais un plateau.
 */
private const val FULL_SCALE_RMS = 0.25f

/** Le pas par défaut d'un scénario rejoué, en millisecondes. */
const val SCENARIO_STEP_MS = 60L

/**
 * Un WAV PCM, lu sans Android : en-tête RIFF, bloc `fmt `, bloc `data`.
 *
 * Volontairement minimal — ce que le moteur de test a besoin de lire, et rien d'autre : PCM 8 ou
 * 16 bits, un ou plusieurs canaux (**le premier canal est pris**, ce qui suffit pour une phrase
 * mono ou stéréo). Un fichier qui n'est pas un WAV PCM lève une [IllegalArgumentException] : mieux
 * vaut un test qui casse qu'un moteur qui rejoue du silence en croyant avoir du son.
 */
object WavReader {

    /** Les échantillons du premier canal, ramenés dans [-1, 1]. */
    fun samples(bytes: ByteArray): FloatArray {
        require(bytes.size > 44 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) {
            "ce n'est pas un WAV (en-tête RIFF absent)"
        }
        var format = 0
        var channels = 1
        var bits = 16
        var dataAt = -1
        var dataSize = 0
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4, Charsets.US_ASCII)
            val size = le32(bytes, at + 4)
            val body = at + 8
            when (id) {
                "fmt " -> {
                    format = le16(bytes, body)
                    channels = le16(bytes, body + 2).coerceAtLeast(1)
                    bits = le16(bytes, body + 14)
                }
                "data" -> {
                    dataAt = body
                    dataSize = minOf(size, bytes.size - body)
                }
            }
            at = body + size + (size and 1)
        }
        require(format == 1) { "WAV non PCM (format=$format) : illisible pour le moteur de test" }
        require(dataAt >= 0 && dataSize > 0) { "aucun bloc data dans ce WAV" }
        val frameBytes = (bits / 8) * channels
        require(frameBytes > 0) { "profondeur invalide ($bits bits)" }
        val count = dataSize / frameBytes
        val out = FloatArray(count)
        for (i in 0 until count) {
            val p = dataAt + i * frameBytes
            out[i] = when (bits) {
                8 -> (bytes[p].toInt() and 0xFF) / 128f - 1f
                16 -> le16s(bytes, p) / 32768f
                32 -> le32s(bytes, p) / 2147483648f
                else -> throw IllegalArgumentException("profondeur non gérée : $bits bits")
            }
        }
        return out
    }

    private fun le16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le16s(b: ByteArray, at: Int): Short = le16(b, at).toShort()

    private fun le32(b: ByteArray, at: Int): Int =
        le16(b, at) or (le16(b, at + 2) shl 16)

    private fun le32s(b: ByteArray, at: Int): Int = le32(b, at)
}

/**
 * L'enveloppe de niveau d'un signal : un RMS par pas de [stepMs], ramené dans 0…1.
 *
 * C'est **la** source des points qui bougent quand le moteur de reconnaissance ne livre rien.
 */
fun rmsLevels(
    samples: FloatArray,
    sampleRate: Int,
    stepMs: Long = SCENARIO_STEP_MS,
    fullScale: Float = FULL_SCALE_RMS,
): List<Float> {
    require(sampleRate > 0) { "fréquence d'échantillonnage invalide" }
    val window = ((sampleRate * stepMs) / 1000L).toInt().coerceAtLeast(1)
    if (samples.isEmpty()) return emptyList()
    val out = ArrayList<Float>(samples.size / window + 1)
    var i = 0
    while (i < samples.size) {
        val end = minOf(i + window, samples.size)
        var sum = 0.0
        for (j in i until end) sum += (samples[j] * samples[j]).toDouble()
        val rms = sqrt(sum / (end - i)).toFloat()
        out.add((rms / fullScale).coerceIn(0f, 1f))
        i = end
    }
    return out
}

/** Le niveau 0…1 ramené en dBFS, comme `onRmsChanged` du moteur réel. */
fun levelToDb(level: Float): Float =
    if (level <= 0.0001f) SpeechState.SILENT_DB else 20f * log10(level.coerceIn(0.0001f, 1f))

/**
 * Une trame d'un scénario rejoué : ce que le moteur « entend » à cet instant.
 *
 * [level] fait bouger les points, [transcript] grandit comme une reconnaissance réelle (des
 * partiels, puis une finale), et [failed] + [stopDecision] permettent de rejouer les échecs du
 * moteur (plafond, message, « Réessayer »).
 */
data class HeardFrame(
    val atMs: Long,
    val level: Float,
    val transcript: String = "",
    val isFinal: Boolean = false,
    val failed: Boolean = false,
    val stopDecision: EngineStopDecision? = null,
    val problem: String? = null,
)

/**
 * Compose un scénario **silence → phrase → silence** à partir d'un WAV.
 *
 * Le silence de tête laisse le temps au moteur de démarrer (l'alerte « le moteur n'a jamais
 * répondu » ne doit pas se déclencher) ; le silence de queue est **assez long pour que
 * l'endpointing conclue tout seul** — c'est ce qui prouve « détection de fin de parole » sans
 * qu'aucun test ne la simule.
 *
 * [partials] : la transcription est écrite **par morceaux** pendant la phrase, comme un vrai
 * moteur livre des partiels — c'est ce que la ligne « Ce que le micro a entendu » réécrit.
 */
fun scenarioFromWav(
    bytes: ByteArray,
    transcript: String,
    leadInMs: Long = 400L,
    tailMs: Long = 1_600L,
    stepMs: Long = SCENARIO_STEP_MS,
): List<HeardFrame> {
    val samples = WavReader.samples(bytes)
    val levels = rmsLevels(samples, sampleRate = SAMPLE_RATE_HINT, stepMs = stepMs)
    val frames = ArrayList<HeardFrame>(levels.size + 8)
    var at = 0L
    while (at < leadInMs) {
        frames += HeardFrame(atMs = at, level = SILENT_RMS)
        at += stepMs
    }
    val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    levels.forEachIndexed { index, level ->
        // La transcription grandit d'un mot toutes les quelques trames, comme un moteur qui hésite.
        val seen = if (words.isEmpty()) "" else {
            val take = (((index + 1).toFloat() / levels.size) * words.size).toInt()
            words.take(take.coerceIn(1, words.size)).joinToString(" ")
        }
        frames += HeardFrame(atMs = at, level = level, transcript = seen)
        at += stepMs
    }
    // La fin de parole : la phrase est posée, la pièce se tait. Le niveau retombe **vraiment** à
    // zéro — c'est le silence que l'endpointing attend.
    var tail = 0L
    while (tail < tailMs) {
        frames += HeardFrame(atMs = at, level = SILENT_RMS, transcript = transcript)
        at += stepMs
        tail += stepMs
    }
    frames += HeardFrame(atMs = at, level = SILENT_RMS, transcript = transcript, isFinal = true)
    return frames
}

/** La fréquence que nos WAV de scénario utilisent (16 kHz, comme un micro de téléphone). */
const val SAMPLE_RATE_HINT = 16_000

/**
 * Le **moteur d'écoute de test** : il rejoue [frames] derrière [SpeechInput].
 *
 * Il se comporte comme le moteur réel — il faut `start()` pour qu'il parle, `stop()` rend la
 * finale, `cancel()` jette tout — mais aucun micro n'est ouvert : c'est un **fichier** qui parle.
 * C'est ce qui rend la chaîne vocale reproductible sur l'émulateur et sur JVM.
 */
class ScenarioSpeechInput(
    private val frames: List<HeardFrame>,
    private val scope: CoroutineScope,
    private val stepMs: Long = SCENARIO_STEP_MS,
    private val localeTag: String = SpeechEngine.DEFAULT_LOCALE,
    override var offlineWanted: Boolean = false,
    /**
     * Le moteur **a répondu** dès le départ, même sans aucune trame.
     *
     * C'est le cas du moteur réel qui ouvre sa session puis ne reçoit plus rien du micro : il a
     * parlé (donc « À L'ÉCOUTE » n'est pas un mensonge), mais **zéro trame** n'arrive. Ce scénario
     * sert à produire la ligne « on n'entend rien » — celle qu'on demande au propriétaire de
     * transmettre.
     */
    private val answersOnStart: Boolean = false,
    /**
     * Les phrases **suivantes** : à chaque écoute neuve, le moteur rejoue la suivante (et revient à
     * la première après la dernière).
     *
     * C'est ce qui permet de rejouer une **conversation** et non une phrase répétée : la session
     * prend une phrase identique pour le **rejeu** de la précédente (sa garde anti-rejeu), donc la
     * deuxième question ne partirait jamais.
     */
    private val following: List<List<HeardFrame>> = emptyList(),
) : SpeechInput {

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    override val isAvailable: Boolean = frames.isNotEmpty() || answersOnStart
    override val prefersOffline: Boolean get() = offlineWanted
    override val requestedLocale: String get() = localeTag

    override var answered: Boolean = false
        private set

    override var frameCount: Int = 0
        private set

    /** Les trames d'échec déjà jouées : ce qui alimente le plafond de reprises. */
    private var errors: Int = 0

    override val attempts: Int get() = errors

    override val canRetry: Boolean get() = shouldRetryEngine(errors)

    override var onFinished: (() -> Unit)? = null

    private var job: Job? = null
    private var cursor: Int = 0

    /** Le tour rejoué : `-1` avant la première écoute, puis 0 = [frames], 1 = [following][0]… */
    private var round: Int = -1

    private fun playing(): List<HeardFrame> = if (round <= 0) frames else following[round - 1]

    override fun start(fresh: Boolean) {
        job?.cancel()
        if (fresh) {
            errors = 0
            answered = answersOnStart
            // La phrase du tour : la suivante à chaque écoute neuve, et retour à la première au bout.
            round = (round + 1) % (following.size + 1)
        }
        cursor = 0
        // `answered` est posé au premier pas : comme le vrai moteur, l'écoute n'est pas déclarée
        // vivante avant que le moteur n'ait dit quelque chose (ou, si [answersOnStart], dès
        // l'ouverture de session — le moteur a répondu, puis plus rien).
        _state.value = SpeechState(isListening = true)
        val frames = playing()
        job = scope.launch {
            while (cursor < frames.size) {
                val frame = frames[cursor]
                answered = true
                frameCount += 1
                if (frame.failed) errors += 1
                _state.update {
                    it.copy(
                        isListening = !frame.isFinal || frame.failed,
                        isFinal = frame.isFinal,
                        transcript = frame.transcript,
                        level = frame.level,
                        rmsDb = levelToDb(frame.level),
                        failed = frame.failed,
                        problem = frame.problem,
                        stopDecision = frame.stopDecision,
                    )
                }
                cursor += 1
                delay(stepMs)
            }
            _state.update { it.copy(isListening = false, level = 0f) }
            onFinished?.invoke()
        }
    }

    /**
     * Comme le moteur réel : arrêter fait **rendre la finale**. C'est par ce chemin que la phrase
     * part — l'endpointing décide, `stop()` rend ce qui a été entendu.
     */
    override fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(isListening = false, level = 0f, isFinal = true, failed = false) }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        cursor = 0
        _state.value = SpeechState()
    }

    override fun release() {
        job?.cancel()
        job = null
        cursor = 0
        _state.value = SpeechState()
    }
}
