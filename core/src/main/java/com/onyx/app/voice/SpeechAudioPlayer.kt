package com.onyx.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Lit le PCM de Kokoro et les MP3/WAV renvoyés par Hermes, avec un seul son à la fois.
 * Les callbacks reviennent sur le thread principal ; `release` coupe aussi un téléchargement déjà joué.
 *
 * Le PCM passe par un `AudioTrack` de flux, et c'est là que tout se joue : `write` **copie** les
 * octets dans le tampon du mélangeur, il ne les fait pas sonner. Quatre conséquences, apprises à la
 * dure :
 * - le tampon ne doit pas faire la **phrase entière** : taillé sur la phrase, il l'avale d'un coup,
 *   `write` ne bloque jamais et tout part en quelques millisecondes avant qu'un seul échantillon
 *   n'ait sonné ;
 * - il ne doit pas non plus être **trop court**. Un tampon de deux minimums fait ~200 ms : le temps
 *   de deux écritures, donc aucune marge. Le moindre retard du fil qui alimente le mélangeur — une
 *   planification, un ramasse-miettes, un appareil chargé — vide le tampon et **s'entend** : c'est
 *   la voix **hachée par moments**. Le tampon vaut donc au moins le **pré-tampon** ([PRE_ROLL_MS]) ;
 * - la piste se **remplit avant de démarrer** : appeler `play()` sur une piste vide met l'horloge en
 *   marche alors que rien n'est encore copié, les premiers cycles du mélangeur consomment du vide, et
 *   le **début de la phrase** est la première chose mangée. On écrit donc [preRollBytes] octets, puis
 *   on démarre — les octets sont déjà en mémoire, donc cela ne retarde rien ;
 * - le lecteur doit ensuite **attendre que la tête de lecture ait atteint le dernier échantillon**
 *   avant de couper la piste. Couper juste après la boucle d'écriture jette ce qui n'a pas encore
 *   sonné : la voix ne sort jamais, alors que le bouton affiche « en lecture ». Et un **trou** — piste
 *   mise en pause par le système, ou écriture refusée un instant — n'est pas une fin de phrase : on
 *   **patiente sans jamais faire glisser l'offset**, sinon la fin du texte est perdue (les mots
 *   mangés).
 *
 * Chaque lecture laisse une ligne de mesure (`$TAG`, préfixe `MESURE`) : sous-alimentations de la
 * piste (`AudioTrack.getUnderrunCount`), temps jusqu'au premier échantillon, temps de pré-tampon et
 * facteur temps réel. Une lecture se juge sur ces compteurs, pas à l'oreille — et l'émulateur, lui,
 * n'a souvent aucun son.
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
            // Un tampon de flux tenant le pré-tampon, jamais la phrase entière : voir l'en-tête.
            val buffer = streamBufferBytes(minimum, PRE_ROLL_MS, BUFFER_CAP)
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
                // Le fil qui alimente le mélangeur ne doit pas être désordonnancé plus longtemps que
                // le tampon ne tient : c'est exactement ce qui fait les hachures.
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
                var failure: Throwable? = null
                var written = 0
                var writes = 0
                var stalls = 0
                var firstSampleMs = -1L
                var primedMs = 0L
                var feedMs = 0L
                var underrunsBefore = 0
                var pausedCount = 0
                val started = SystemClock.elapsedRealtime()
                try {
                    // ---- 1) le pré-tampon : on remplit la piste AVANT de la faire tourner. Les
                    // octets sont déjà en mémoire, donc remplir ne retarde pas le démarrage : cela
                    // remplace un démarrage à vide (dont le début de phrase faisait les frais) par
                    // une réserve de [PRE_ROLL_MS] que le fil peut perdre sans qu'on l'entende.
                    val priming = SystemClock.elapsedRealtime()
                    var offset = 0
                    val preRoll = preRollBytes(bytes.size, buffer, PRE_ROLL_MS)
                    while (offset < preRoll && isCurrent(token, track)) {
                        val count = minOf(WRITE_CHUNK, preRoll - offset, bytes.size - offset)
                        val accepted = pcmWrite(
                            track = track,
                            bytes = bytes,
                            offset = offset,
                            count = count,
                            token = token,
                            onWait = { stalls += 1 },
                        )
                        if (accepted <= 0) throw IllegalStateException("Android PCM write error $accepted")
                        written += accepted
                        writes += 1
                        offset += accepted
                        reportLevel(token, track, bytes, offset - accepted, accepted)
                    }
                    primedMs = SystemClock.elapsedRealtime() - priming
                    underrunsBefore = track.underrunCount
                    track.play()
                    val playing = SystemClock.elapsedRealtime()
                    // ---- 2) le temps jusqu'au premier échantillon : c'est là que se lit un
                    // démarrage à vide. Il est observé par un **fil à part**, qui regarde la tête de
                    // lecture toutes les 5 ms : l'observer depuis ce fil-ci suspendrait l'écriture,
                    // et la mesure fabriquerait le trou qu'elle prétend mesurer.
                    val firstSample = startFirstSampleWatch(token, track)
                    // ---- 3) le reste de la phrase
                    while (offset < bytes.size && isCurrent(token, track)) {
                        val count = minOf(WRITE_CHUNK, bytes.size - offset)
                        val accepted = pcmWrite(
                            track = track,
                            bytes = bytes,
                            offset = offset,
                            count = count,
                            token = token,
                            onWait = { stalls += 1 },
                        )
                        if (accepted <= 0) throw IllegalStateException("Android PCM write error $accepted")
                        written += accepted
                        writes += 1
                        offset += accepted
                        reportLevel(token, track, bytes, offset - accepted, accepted)
                    }
                    feedMs = SystemClock.elapsedRealtime() - playing
                    firstSampleMs = firstSample.firstElapsedMs()
                    // ---- 4) `write` a copié les octets dans le tampon : le son n'est pas encore
                    // sorti. On attend la tête de lecture, sans qu'un trou passe pour une fin.
                    val totalFrames = written / BYTES_PER_FRAME
                    val deadline = playing + clipMillis(written) + DRAIN_GRACE_MS
                    var pausedMs = 0L
                    var pausedAt = 0L
                    while (isCurrent(token, track)) {
                        val head = track.playbackHeadPosition
                        val state = track.playState
                        val now = SystemClock.elapsedRealtime()
                        if (state == AudioTrack.PLAYSTATE_PAUSED) {
                            if (pausedAt == 0L) {
                                pausedAt = now
                                pausedCount += 1
                                Log.w(TAG, "lecture en pause à $head/$totalFrames trames : on patiente")
                            }
                            if (now - pausedAt > PATIENCE_MS) break
                        } else if (pausedAt != 0L) {
                            // Le trou est fini : ses secondes ne comptent pas dans la marge.
                            pausedMs += now - pausedAt
                            pausedAt = 0L
                            Log.i(TAG, "lecture reprise à $head/$totalFrames trames")
                        }
                        if (!pcmStillPlaying(
                                headFrames = head,
                                totalFrames = totalFrames,
                                playState = state,
                                pausedMs = pausedMs,
                                nowMs = now,
                                deadlineMs = deadline,
                            )
                        ) {
                            break
                        }
                        runCatching { Thread.sleep(DRAIN_POLL_MS) }
                    }
                    val head = track.playbackHeadPosition
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val underruns = track.underrunCount - underrunsBefore
                    Log.i(
                        TAG,
                        "played ${written}o writes=$writes in ${elapsed}ms " +
                            "head=$head/$totalFrames frames state=${track.playState} " +
                            "musicActive=${manager.isMusicActive}",
                    )
                    // La ligne qui juge une lecture : elle se compare avant/après un correctif.
                    Log.i(
                        TAG,
                        "MESURE underruns=$underruns firstSampleMs=$firstSampleMs " +
                            "sampleWatchMs=$FIRST_SAMPLE_WATCH_MS primedMs=$primedMs " +
                            "buffer=$buffer preRoll=$preRoll " +
                            "stalls=$stalls recovers=$pausedCount feedRtf=" +
                            String.format(Locale.ROOT, "%.2f", realTimeFactor(written, feedMs)) +
                            " head=$head/$totalFrames",
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

    /**
     * Écrit un morceau dans la piste en **patientant** si l'appareil le refuse un instant : l'offset
     * ne bouge pas d'un octet, donc rien ne glisse et aucun mot n'est sauté. Un refus qui dure
     * ([WRITE_STALL_LIMIT] attentes) est une vraie panne, et remonte à l'appelant.
     */
    private fun pcmWrite(
        track: AudioTrack,
        bytes: ByteArray,
        offset: Int,
        count: Int,
        token: Long,
        onWait: (Int) -> Unit = {},
    ): Int {
        var waited = 0
        while (true) {
            if (!isCurrent(token, track)) return 0
            val accepted = track.write(bytes, offset, count)
            if (accepted > 0) {
                if (waited > 0) Log.i(TAG, "écriture acceptée après $waited attente(s) : la phrase continue")
                return accepted
            }
            waited += 1
            onWait(waited)
            if (waited > WRITE_STALL_LIMIT) return accepted
            runCatching { Thread.sleep(WRITE_STALL_WAIT_MS) }
        }
    }

    /**
     * Observe le **premier échantillon** de la piste : le temps, en millisecondes, entre le démarrage
     * de la piste et le moment où la tête de lecture bouge. Un démarrage sur une piste vide met
     * l'horloge en marche alors que rien n'est encore copié — ce chiffre le dit, et il est relevé par
     * un fil à part pour que l'observation ne suspende pas l'écriture (une mesure qui fabrique le
     * trou qu'elle mesure ne vaut rien).
     */
    private fun startFirstSampleWatch(token: Long, track: AudioTrack): FirstSample {
        val watch = FirstSample()
        Thread({
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start <= FIRST_SAMPLE_WATCH_MS) {
                if (!isCurrent(token, track)) return@Thread
                if (track.playbackHeadPosition > 0) {
                    watch.set(SystemClock.elapsedRealtime() - start)
                    return@Thread
                }
                runCatching { Thread.sleep(FIRST_SAMPLE_POLL_MS) }
            }
        }, "onyx-pcm-first-sample").start()
        return watch
    }

    /** Le niveau des échantillons réellement écrits : c'est lui qui fait battre la bouche du visage. */
    private fun reportLevel(token: Long, track: AudioTrack, bytes: ByteArray, from: Int, count: Int) {
        val level = pcmLevel(bytes, from, count)
        main.post { if (isCurrent(token, track)) onLevel?.invoke(level) }
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

        /**
         * Ce qu'on met en tampon **avant** de démarrer la piste (1 s) : la marge du lecteur contre un
         * retard du fil qui alimente le mélangeur. Les octets étant déjà en mémoire, remplir ne
         * retarde pas le démarrage.
         */
        const val PRE_ROLL_MS = 1_000L

        /** Plafond de sécurité du tampon (4 s) : au-delà, la mémoire n'achète plus rien. */
        const val BUFFER_CAP = 192_000

        /** Taille d'un morceau écrit (200 ms), multiple de la trame de 2 octets. */
        const val WRITE_CHUNK = 9_600

        /** On regarde la tête de lecture toutes les 20 ms : inaudible, et précis. */
        const val DRAIN_POLL_MS = 20L

        /** Pas d'observation du premier échantillon : la tête bouge en un cycle de mélangeur. */
        const val FIRST_SAMPLE_POLL_MS = 5L

        /** Au-delà de deux secondes sans premier échantillon, la piste ne sort rien du tout. */
        const val FIRST_SAMPLE_WATCH_MS = 2_000L

        /** Marge au-delà de la durée de la phrase avant d'abandonner l'attente. */
        const val DRAIN_GRACE_MS = 2_000L

        /** Combien de temps un trou peut durer avant qu'on renonce (piste en pause). */
        const val PATIENCE_MS = 30_000L

        /** Combien d'attentes on accorde à une écriture refusée avant de la déclarer en panne. */
        const val WRITE_STALL_LIMIT = 100

        /** Le pas d'une attente d'écriture (10 ms). */
        const val WRITE_STALL_WAIT_MS = 10L
    }
}

/** Le nombre d'octets d'une durée de PCM 24 kHz mono 16 bits. */
internal fun bytesForMillis(millis: Long): Int = (millis * 24_000L).toInt() / 1_000 * 2

/**
 * Le temps écoulé avant le **premier échantillon** de la piste, en millisecondes (‑1 : aucun dans la
 * fenêtre d'observation). Rempli par le fil d'observation, lu par le fil de lecture.
 */
internal class FirstSample {
    private val value = java.util.concurrent.atomic.AtomicLong(-1L)

    fun set(millis: Long) {
        if (value.get() < 0L) value.set(millis)
    }

    fun firstElapsedMs(): Long = value.get()
}

/**
 * La taille du tampon d'un flux PCM : le plus grand entre « deux tampons minimaux » et le
 * **pré-tampon**, borné par le plafond.
 *
 * Deux tampons minimaux font ~200 ms sur la plupart des appareils : le temps de deux écritures,
 * donc aucune marge contre un retard du fil qui alimente le mélangeur — et c'est ce retard qui
 * s'entend. Le tampon doit donc tenir le pré-tampon, sinon le pré-tampon ne tient pas dans la piste.
 */
internal fun streamBufferBytes(
    minimumBytes: Int,
    preRollMillis: Long = 1_000L,
    capBytes: Int = 192_000,
): Int {
    val floor = minimumBytes.coerceAtLeast(4_800) * 2
    return maxOf(floor, bytesForMillis(preRollMillis)).coerceAtMost(capBytes)
}

/**
 * Ce qu'on écrit avant de démarrer la piste : tout ce qui tient dans le tampon, jamais plus que la
 * phrase elle-même (une phrase de 300 ms ne se pré-tamponne pas plus de 300 ms) ni que le pré-tampon
 * demandé. Un pré-tampon nul rend l'ancien démarrage (piste vide) : c'est le témoin de la mesure.
 */
internal fun preRollBytes(
    clipBytes: Int,
    bufferBytes: Int,
    preRollMillis: Long = 1_000L,
): Int {
    if (preRollMillis <= 0L) return 0
    return minOf(clipBytes, bufferBytes, bytesForMillis(preRollMillis))
}

/**
 * Le facteur temps réel d'une alimentation : secondes d'audio copiées par seconde d'horloge. En
 * dessous de 1, l'appareil consomme plus vite qu'on ne le nourrit : c'est la fabrique des hachures.
 */
internal fun realTimeFactor(bytesWritten: Int, wallMs: Long): Double {
    if (wallMs <= 0L) return 0.0
    val audioMs = bytesWritten.toDouble() * 1_000.0 / (24_000.0 * 2.0)
    return audioMs / wallMs.toDouble()
}

/**
 * Faut-il attendre encore avant de couper la piste PCM ?
 *
 * Vrai tant que la tête de lecture n'a pas rattrapé le dernier échantillon **écrit** (`write` les a
 * copiés dans le tampon du mélangeur, il ne les a pas joués), que la piste n'est pas **arrêtée**
 * (arrêtée ou libérée, elle ne rattrapera jamais rien : attendre serait une boucle sans fin), et que
 * la marge d'abandon n'est pas épuisée.
 *
 * Une piste **en pause** n'est pas une fin de phrase : c'est un trou, et un trou se **patiente** —
 * sinon la fin du texte est jetée, et c'est un mot mangé. Les secondes passées en pause ne comptent
 * pas dans la marge, sinon un trou consommerait la patience destinée à la lecture elle-même.
 */
internal fun pcmStillPlaying(
    headFrames: Int,
    totalFrames: Int,
    playState: Int,
    pausedMs: Long = 0L,
    nowMs: Long,
    deadlineMs: Long,
): Boolean {
    if (headFrames >= totalFrames) return false
    if (playState == AudioTrack.PLAYSTATE_STOPPED) return false
    if (playState == AudioTrack.PLAYSTATE_PAUSED) return true
    return nowMs - pausedMs < deadlineMs
}
