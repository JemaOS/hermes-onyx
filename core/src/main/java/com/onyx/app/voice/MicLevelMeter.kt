package com.onyx.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * **Le micro que l'app ouvre elle-même**, uniquement pour son niveau.
 *
 * C'est le « signal que l'app contrôle » : quand le reconnaisseur livre des mesures plates ou
 * aucune ([LevelFusion]), c'est ce niveau-là qui fait bouger les points. Il ne sert **jamais** à
 * écrire des mots — la reconnaissance reste le métier de [SpeechEngine] ; ici on ne lit que
 * l'amplitude, et on n'en garde rien.
 *
 * Il n'est ouvert **que** sur décision de [LevelFusion] : un second micro ouvert en permanence
 * pendant que le reconnaisseur travaille risquerait de lui voler le son — on ne prend donc ce
 * chemin que lorsque le moteur a déjà montré qu'il ne donnait rien.
 *
 * Derrière une interface pour que le reste de la chaîne se teste sans matériel ([SilentMicLevel]).
 */
interface MicLevel {

    /** Le niveau 0…1 : ce que les points affichent. */
    val level: StateFlow<Float>

    /** Le même niveau en dBFS, pour les seuils qui raisonnent en décibels. */
    val rmsDb: StateFlow<Float>

    /** Le nombre de mesures livrées — les « trames » du micro de l'app. */
    val frames: Int

    /** Vrai quand un micro a pu être ouvert. */
    val isAvailable: Boolean

    fun start()
    fun stop()
    fun release()
}

/** Un micro qui n'existe pas : la valeur par défaut hors Android, et l'éprouvette des tests. */
class SilentMicLevel : MicLevel {
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    override val rmsDb: StateFlow<Float> = MutableStateFlow(SpeechState.SILENT_DB).asStateFlow()
    override val frames: Int get() = 0
    override val isAvailable: Boolean get() = false
    override fun start() = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

/**
 * Le micro d'Android, lu en PCM brut par `AudioRecord` et réduit à un niveau RMS.
 *
 * Une seule boucle de lecture, sur son propre fil ; `stop()` la referme et libère l'entrée. Aucun
 * échantillon ne sort d'ici : le tampon est écrasé à chaque tour.
 */
class AndroidMicLevelMeter(
    /** Le gain appliqué avant l'affichage, pour qu'une voix normale atteigne le haut des points. */
    private val fullScaleRms: Float = 0.12f,
) : MicLevel {

    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()

    private val _rmsDb = MutableStateFlow(SpeechState.SILENT_DB)
    override val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    @Volatile
    private var count: Int = 0
    override val frames: Int get() = count

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    private var opened: Boolean = false
    override val isAvailable: Boolean get() = opened

    @SuppressLint("MissingPermission")
    override fun start() {
        if (running) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            opened = false
            VoiceDiagnostics.note("voice: micro de niveau indisponible (getMinBufferSize=$minBuffer)")
            return
        }
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
        }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record?.release() }
            opened = false
            VoiceDiagnostics.note("voice: micro de niveau indisponible (AudioRecord non initialisé)")
            return
        }
        opened = true
        running = true
        VoiceDiagnostics.note("voice: micro de niveau ouvert (le moteur ne livrait rien d'exploitable)")
        thread = Thread({
            val buffer = ShortArray(minBuffer)
            while (running) {
                val read = runCatching { record.read(buffer, 0, buffer.size) }.getOrDefault(-1)
                if (read <= 0) continue
                var sum = 0.0
                for (i in 0 until read) sum += (buffer[i].toDouble() * buffer[i].toDouble())
                val rms = (sqrt(sum / read) / 32768.0).toFloat()
                count += 1
                _level.value = (rms / fullScaleRms).coerceIn(0f, 1f)
                _rmsDb.value = if (rms <= 0.0001f) SpeechState.SILENT_DB else 20f * kotlin.math.log10(rms)
            }
        }, "onyx-mic-level").apply { isDaemon = true; start() }
    }

    override fun stop() {
        running = false
        thread?.join(200)
        thread = null
        _level.value = 0f
        _rmsDb.value = SpeechState.SILENT_DB
    }

    override fun release() = stop()

    private companion object {
        /** 16 kHz mono : ce que le reconnaisseur consomme, et de quoi mesurer une voix. */
        const val SAMPLE_RATE = 16_000
    }
}
