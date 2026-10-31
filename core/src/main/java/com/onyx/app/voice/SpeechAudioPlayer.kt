package com.onyx.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Lit le PCM de Kokoro et les MP3/WAV renvoyés par Hermes, avec un seul son à la fois.
 * Les callbacks reviennent sur le thread principal ; `release` coupe aussi un téléchargement déjà joué.
 *
 * Le PCM passe par un `AudioTrack` de flux, et c'est là que tout se joue : `write` **copie** les
 * octets dans le tampon du mélangeur, il ne les fait pas sonner. Deux conséquences, apprises à la
 * dure :
 * - le tampon doit être un **tampon de flux** (quelques centaines de millisecondes), pas la phrase
 *   entière : taillé sur la phrase, il l'avale d'un coup, `write` ne bloque jamais et tout part en
 *   quelques millisecondes ;
 * - le lecteur doit ensuite **attendre que la tête de lecture ait atteint le dernier échantillon**
 *   avant de couper la piste. Couper juste après la boucle d'écriture jette ce qui n'a pas encore
 *   sonné : la voix ne sort jamais, alors que le bouton affiche « en lecture ».
 */
class SpeechAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var generation = 0L
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Où sort le son : un média (`USAGE_MEDIA`), ou un appel
     * (`USAGE_VOICE_COMMUNICATION`) quand la réponse doit se jouer dans l'écouteur du téléphone
     * levé à l'oreille. Les attributs sont figés à la création du lecteur : c'est donc au tour
     * suivant qu'un changement en cours de phrase s'entend.
     */
    @Volatile
    var usage: Int = AudioAttributes.USAGE_MEDIA
    private var audioTrack: AudioTrack? = null
    private var temporaryFile: File? = null

    /**
     * Le niveau du son qui sort, quand il passe en PCM (les voix de Kokoro et de Hermes) : la
     * **vraie** amplitude des échantillons écrits, de 0 à 1. C'est ce qui fait battre la bouche du
     * visage de la scène vocale avec la voix. Rien pour un format compressé, dont on ne voit pas
     * les échantillons : l'appelant sait alors qu'aucune mesure n'est disponible.
     */
    var onLevel: ((Float) -> Unit)? = null

    fun play(
        audio: SpeechAudio,
        onFinished: () -> Unit,
        onError: (Throwable) -> Unit = {},
    ) {
        stop()
        val token = synchronized(lock) { ++generation }
        if (audio.isRawPcm) playPcm(audio.bytes, token, onFinished, onError)
        else playEncoded(audio, token, onFinished, onError)
    }

    fun pause(): Boolean {
        synchronized(lock) {
            mediaPlayer?.let { player ->
                if (runCatching { player.isPlaying }.getOrDefault(false)) {
                    return runCatching { player.pause(); true }.getOrDefault(false)
                }
            }
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    return runCatching { track.pause(); true }.getOrDefault(false)
                }
            }
        }
        return false
    }

    fun resume(): Boolean {
        synchronized(lock) {
            mediaPlayer?.let { player ->
                runCatching { player.start() }.getOrElse { return false }
                return true
            }
            audioTrack?.let { track ->
                runCatching { track.play() }.getOrElse { return false }
                return true
            }
        }
        return false
    }

    fun stop() {
        val player: MediaPlayer?
        val track: AudioTrack?
        val file: File?
        synchronized(lock) {
            generation += 1
            player = mediaPlayer
            track = audioTrack
            file = temporaryFile
            mediaPlayer = null
            audioTrack = null
            temporaryFile = null
        }
        player?.let {
            runCatching { it.stop() }
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        file?.delete()
        // Plus rien ne sort : le niveau retombe, la bouche du visage avec lui.
        main.post { onLevel?.invoke(0f) }
    }

    fun release() = stop()

    private fun playEncoded(
        audio: SpeechAudio,
        token: Long,
        onFinished: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        try {
            val file = File.createTempFile("onyx-speech-", extensionFor(audio), appContext.cacheDir)
            file.writeBytes(audio.bytes)
            val player = MediaPlayer()
            synchronized(lock) {
                if (generation != token) {
                    file.delete()
                    return
                }
                mediaPlayer = player
                temporaryFile = file
            }
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { prepared ->
                if (isCurrent(token, prepared)) {
                    try {
                        prepared.start()
                    } catch (error: Throwable) {
                        cleanupMedia(prepared)
                        onError(error)
                    }
                }
            }
            player.setOnCompletionListener { completed ->
                if (isCurrent(token, completed)) {
                    cleanupMedia(completed)
                    onFinished()
                }
            }
            player.setOnErrorListener { failed, what, extra ->
                if (isCurrent(token, failed)) {
                    cleanupMedia(failed)
                    onError(IllegalStateException("Android audio error $what/$extra"))
                }
                true
            }
            player.prepareAsync()
        } catch (error: Throwable) {
            if (isCurrent(token)) {
                cleanupMedia(mediaPlayer)
                main.post { if (isCurrent(token)) onError(error) }
            }
        }
    }

    private fun playPcm(
        bytes: ByteArray,
        token: Long,
        onFinished: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        try {
            val minimum = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            // Un tampon de flux, jamais la phrase entière : voir l'en-tête de la classe.
            val buffer = (minimum.coerceAtLeast(MIN_BUFFER_BYTES) * STREAM_BUFFERS)
                .coerceAtMost(BUFFER_CAP)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(buffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
            val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            Log.i(
                TAG,
                "pcm ${bytes.size}o minBuffer=$minimum buffer=$buffer usage=$usage " +
                    "state=${track.state} rate=${track.sampleRate} channels=${track.channelCount} " +
                    "session=${track.audioSessionId} volume=" +
                    "${manager.getStreamVolume(AudioManager.STREAM_MUSIC)}/" +
                    "${manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}",
            )
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                throw IllegalStateException("Android refused the PCM output (state ${track.state})")
            }
            if (Log.isLoggable(TAG, Log.VERBOSE)) dumpReceived(bytes)
            synchronized(lock) {
                if (generation != token) {
                    track.release()
                    return
                }
                audioTrack = track
            }
            Thread({
                var failure: Throwable? = null
                var written = 0
                var writes = 0
                val started = SystemClock.elapsedRealtime()
                try {
                    track.play()
                    var offset = 0
                    while (offset < bytes.size && isCurrent(token, track)) {
                        val count = minOf(WRITE_CHUNK, bytes.size - offset)
                        val accepted = track.write(bytes, offset, count)
                        if (accepted <= 0) throw IllegalStateException("Android PCM write error $accepted")
                        written += accepted
                        writes += 1
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(
                                TAG,
                                "write=$accepted offset=$offset head=${track.playbackHeadPosition} " +
                                    "state=${track.playState} t=${SystemClock.elapsedRealtime() - started}ms",
                            )
                        }
                        // Le niveau est mesuré sur les échantillons réellement écrits : c'est la
                        // voix qui sort, pas une estimation.
                        val level = pcmLevel(bytes, offset, accepted)
                        main.post { if (isCurrent(token, track)) onLevel?.invoke(level) }
                        offset += accepted
                    }
                    // `write` a copié les octets dans le tampon : le son n'est pas encore sorti.
                    // On attend la tête de lecture, sinon la coupe jette la fin de la phrase.
                    val totalFrames = written / BYTES_PER_FRAME
                    val deadline = started + clipMillis(written) + DRAIN_GRACE_MS
                    while (isCurrent(token, track) &&
                        pcmStillPlaying(
                            headFrames = track.playbackHeadPosition,
                            totalFrames = totalFrames,
                            playing = track.playState == AudioTrack.PLAYSTATE_PLAYING,
                            nowMs = SystemClock.elapsedRealtime(),
                            deadlineMs = deadline,
                        )
                    ) {
                        Thread.sleep(DRAIN_POLL_MS)
                    }
                    val head = track.playbackHeadPosition
                    val elapsed = SystemClock.elapsedRealtime() - started
                    Log.i(
                        TAG,
                        "played ${written}o writes=$writes in ${elapsed}ms " +
                            "head=$head/$totalFrames frames state=${track.playState} " +
                            "musicActive=${manager.isMusicActive}",
                    )
                    if (head < totalFrames && isCurrent(token, track)) {
                        // Le lecteur n'a pas vidé le tampon : l'appareil ne sort donc rien de ce
                        // qu'on lui a donné. On le dit, au lieu d'un bouton « en lecture » muet.
                        Log.w(
                            TAG,
                            "l'audio n'est pas sorti : tête=$head/$totalFrames en ${elapsed}ms, " +
                                "état=${track.playState}",
                        )
                    }
                } catch (error: Throwable) {
                    if (isCurrent(token, track)) failure = error
                } finally {
                    val current = isCurrent(token, track)
                    if (current) {
                        synchronized(lock) { if (audioTrack === track) audioTrack = null }
                    }
                    runCatching { if (current) track.stop() }
                    runCatching { track.release() }
                    if (current) {
                        main.post {
                            if (isCurrent(token)) {
                                val error = failure
                                if (error == null) onFinished() else onError(error)
                            }
                        }
                    }
                }
            }, "onyx-pcm-preview").start()
        } catch (error: Throwable) {
            main.post { if (isCurrent(token)) onError(error) }
        }
    }

    /** La durée du PCM reçu, en millisecondes (24 kHz, 16 bits, mono). */
    private fun clipMillis(byteCount: Int): Long =
        byteCount.toLong() * 1000L / (SAMPLE_RATE.toLong() * BYTES_PER_FRAME)

    /**
     * Écrit ce que l'app a **reçu** dans un WAV de son dossier : la preuve à écouter, quand le son
     * de l'appareil ne suffit pas à trancher entre « rien reçu » et « rien sorti ».
     * Activé à la demande (`adb shell setprop log.tag.$TAG VERBOSE`), jamais en marche normale.
     */
    private fun dumpReceived(bytes: ByteArray) {
        try {
            val dir = File(appContext.filesDir, "voice-audio").apply { mkdirs() }
            File(dir, "received.pcm").writeBytes(bytes)
            val header = ByteArray(44)
            fun put(at: Int, value: Int) {
                header[at] = (value and 0xFF).toByte()
                header[at + 1] = ((value shr 8) and 0xFF).toByte()
                header[at + 2] = ((value shr 16) and 0xFF).toByte()
                header[at + 3] = ((value shr 24) and 0xFF).toByte()
            }
            "RIFF".toByteArray().copyInto(header, 0)
            put(4, 36 + bytes.size)
            "WAVE".toByteArray().copyInto(header, 8)
            "fmt ".toByteArray().copyInto(header, 12)
            put(16, 16)
            header[20] = 1
            header[22] = 1
            put(24, SAMPLE_RATE)
            put(28, SAMPLE_RATE * BYTES_PER_FRAME)
            header[32] = BYTES_PER_FRAME.toByte()
            header[34] = 16
            "data".toByteArray().copyInto(header, 36)
            put(40, bytes.size)
            val wav = File(dir, "received.wav")
            wav.outputStream().use { out ->
                out.write(header)
                out.write(bytes)
            }
            Log.i(TAG, "received.wav écrit (${bytes.size} octets) : ${wav.absolutePath}")
        } catch (error: Throwable) {
            Log.w(TAG, "received.wav non écrit", error)
        }
    }

    /**
     * L'amplitude d'un morceau de PCM 16 bits mono, de 0 à 1 : sa valeur efficace, ramenée à
     * l'échelle d'un niveau de voix (la parole utile tient dans les premiers dixièmes).
     */
    private fun pcmLevel(samples: ByteArray, from: Int, count: Int): Float {
        var sum = 0.0
        var measured = 0
        var index = from
        val end = from + count - 1
        while (index < end) {
            val raw = ((samples[index + 1].toInt() shl 8) or (samples[index].toInt() and 0xFF)).toShort()
            val value = raw.toDouble() / 32_768.0
            sum += value * value
            measured += 1
            index += 2
        }
        if (measured == 0) return 0f
        return (kotlin.math.sqrt(sum / measured) * 4.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun cleanupMedia(player: MediaPlayer?) {
        val file = synchronized(lock) {
            if (player != null && mediaPlayer !== player) return
            mediaPlayer = null
            temporaryFile.also { temporaryFile = null }
        }
        runCatching { player?.release() }
        file?.delete()
    }

    private fun isCurrent(token: Long, player: MediaPlayer? = null): Boolean = synchronized(lock) {
        generation == token && (player == null || mediaPlayer === player)
    }

    private fun isCurrent(token: Long, track: AudioTrack): Boolean = synchronized(lock) {
        generation == token && audioTrack === track
    }

    private fun extensionFor(audio: SpeechAudio): String {
        val mime = audio.mimeType.lowercase()
        return when {
            hasRiffHeader(audio.bytes) || "wav" in mime -> ".wav"
            "ogg" in mime -> ".ogg"
            "flac" in mime -> ".flac"
            "mp4" in mime || "aac" in mime -> ".m4a"
            else -> ".mp3"
        }
    }

    private companion object {
        const val TAG = "OnyxVoiceAudio"
        const val SAMPLE_RATE = 24_000
        const val BYTES_PER_FRAME = 2

        /** Le plancher d'un tampon de flux, quand l'appareil ne sait pas dire son minimum. */
        const val MIN_BUFFER_BYTES = 4_800

        /** Combien de tampons minimaux on demande : de quoi écrire sans aller-retour par échantillon. */
        const val STREAM_BUFFERS = 2

        /** Plafond de sécurité du tampon. */
        const val BUFFER_CAP = 96_000

        /** Taille d'un morceau écrit (200 ms), multiple de la trame de 2 octets. */
        const val WRITE_CHUNK = 9_600

        /** On regarde la tête de lecture toutes les 20 ms : inaudible, et précis. */
        const val DRAIN_POLL_MS = 20L

        /** Marge au-delà de la durée de la phrase avant d'abandonner l'attente. */
        const val DRAIN_GRACE_MS = 2_000L
    }
}

/**
 * Faut-il attendre encore avant de couper la piste PCM ?
 *
 * Vrai tant que la tête de lecture n'a pas rattrapé le dernier échantillon **écrit** (`write` les a
 * copiés dans le tampon du mélangeur, il ne les a pas joués), que la piste joue encore, et que la
 * marge n'est pas épuisée. Une piste arrêtée ou en pause ne rattrapera jamais rien : attendre serait
 * alors une boucle sans fin, et c'est exactement ce que faisait l'ancien code en coupant tout de
 * suite — la phrase était jetée avant d'avoir sonné.
 */
internal fun pcmStillPlaying(
    headFrames: Int,
    totalFrames: Int,
    playing: Boolean,
    nowMs: Long,
    deadlineMs: Long,
): Boolean = playing && headFrames < totalFrames && nowMs < deadlineMs

