package com.onyx.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Le niveau tenu pendant qu'un synthétiseur parle: Android n'expose aucune amplitude de la voix
 * qu'il fabrique, et une bouche fermée pendant qu'on entend une voix serait un mensonge. Quand la
 * voix est de l'audio (Kokoro, Hermes), c'est la **vraie** amplitude des échantillons qui décide.
 */
private const val SYNTHESISED_SPEAKING_LEVEL = 0.62f

/**
 * La lecture des réponses: voix Android, serveur Kokoro ou voix du Hermes configuré.
 * Les deux voix distantes retombent sur le synthétiseur Android si le serveur ne répond pas.
 */
class SpeechOutput(context: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Appelé quand une lecture se termine d'elle-même (pas après [stop] ou [pause]). */
    var onFinished: (() -> Unit)? = null

    private var pendingLanguage: String? = null
    private var rate: Float = 1.0f

    /** La vitesse de la réplique en cours: celle de son bot, sinon celle d'Onyx. */
    private var speakingRate: Float = 1.0f

    /** Le bot dont la réplique se lit, pour qu'elle sorte de **sa** voix. */
    private var speakingAs: String? = null
    private var currentText: String? = null
    private var currentOffset = 0
    private var playbackBase = 0
    private var paused = false
    private var utteranceSequence = 0L
    private var activeUtterance: String? = null

    private val audioPlayer = SpeechAudioPlayer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var remoteJob: Job? = null
    private var remoteSequence = 0L
    private var remoteActive = false

    /**
     * Vrai quand la réponse se joue dans l'écouteur (téléphone levé à l'oreille): le
     * synthétiseur prend alors les attributs d'un appel, comme la lecture distante.
     */
    var earRouting: Boolean = false
        private set

    private var replyVoice = ReplyVoice.ON_DEVICE
    private var kokoroBaseUrl = ""
    private var kokoroVoice = "am_onyx(2)+bm_george(1)"
    private var hermesServer: ServerConfig? = null

    /**
     * La voix réglée pour chaque bot (`Settings.botVoices`) et les noms de la salle: c'est ce qui
     * fait qu'un bot lit **de sa voix**, et que deux bots ne lisent pas de la même.
     */
    private var botVoices: Map<String, BotVoice> = emptyMap()
    private var botNames: List<String> = emptyList()

    /** Les voix que le serveur Kokoro a données: ce parmi quoi un bot reçoit la sienne. */
    private var kokoroVoices: List<String> = emptyList()

    /**
     * Le niveau de la voix qui se lit, de 0 à 1: la vraie amplitude des échantillons quand la voix
     * est de l'audio (Kokoro, Hermes), un niveau tenu tant que le synthétiseur parle sinon — il
     * n'expose aucune mesure. C'est ce qui fait battre la bouche du visage de la scène vocale.
     */
    private val _replyLevel = MutableStateFlow(0f)
    val replyLevel: StateFlow<Float> = _replyLevel.asStateFlow()

    init {
        audioPlayer.onLevel = { level -> _replyLevel.value = level }
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (isActive(utteranceId)) _isSpeaking.value = true
                    }

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        if (isActive(utteranceId)) {
                            currentOffset = (playbackBase + start).coerceIn(0, currentText?.length ?: 0)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        finishUtterance(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finishUtterance(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        finishUtterance(utteranceId)
                    }
                })
                pendingLanguage?.let { applyLanguage(it) }
            }
        }
    }

    val isReady: Boolean get() = ready

    /** Applique les réglages sans recréer le moteur ni interrompre une phrase déjà en cours. */
    fun configure(
        voice: ReplyVoice,
        kokoroUrl: String,
        kokoroVoice: String,
        hermesServer: ServerConfig?,
        /** La voix réglée pour chaque bot, et les voix que le serveur Kokoro a données. */
        botVoices: Map<String, BotVoice> = emptyMap(),
        kokoroVoices: List<String> = emptyList(),
    ) {
        replyVoice = voice
        kokoroBaseUrl = kokoroUrl
        this.kokoroVoice = kokoroVoice
        this.hermesServer = hermesServer
        this.botVoices = botVoices
        this.kokoroVoices = kokoroVoices
    }

    /**
     * Les bots de la salle: leurs noms décident de quelle voix chacun reçoit
     * ([com.onyx.app.voice.BotVoices]), pour que deux bots ne lisent pas de la même.
     */
    fun setBotRoster(names: List<String>) {
        botNames = names
    }

    /** Les voix réglées pour les bots (`Settings.botVoices`). */
    fun setBotVoices(picks: Map<String, BotVoice>) {
        botVoices = picks
    }

    /** Change la langue de lecture (`fr-FR` par défaut). */
    fun setLanguage(tag: String) {
        pendingLanguage = tag
        if (ready) applyLanguage(tag)
    }

    /**
     * La réponse se joue dans l'écouteur (capteur de proximité) ou revient au haut-parleur.
     *
     * `TextToSpeech` ne se réachemine pas en cours d'énoncé: le nouvel attribut s'applique à
     * l'énoncé suivant, et c'est `VoiceSession` qui réémet le reste de la phrase s'il faut.
     */
    fun setEarRouting(atEar: Boolean) {
        if (earRouting == atEar) return
        earRouting = atEar
        audioPlayer.usage = if (atEar) {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        } else {
            AudioAttributes.USAGE_MEDIA
        }
        applyEarAttributes()
    }

    /** La vitesse Android, ou le champ `speed` envoyé à Kokoro. Hermes garde son propre réglage. */
    fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 2.0f)
        engine?.setSpeechRate(rate)
    }

    /**
     * Les voix installées du synthétiseur, réduites à ce qui décide ([SpokenVoice]): c'est parmi
     * elles qu'un bot reçoit une voix de son cru.
     */
    fun installedVoices(): List<SpokenVoice> {
        val device = engine ?: return emptyList()
        val voices = runCatching { device.voices }.getOrNull() ?: return emptyList()
        return voices.mapNotNull { voice ->
            val id = voice.name ?: return@mapNotNull null
            // Une voix que le moteur annonce mais qui n'est pas installée ne peut pas lire.
            if (runCatching { voice.features }.getOrNull()?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) {
                return@mapNotNull null
            }
            SpokenVoice(
                id = id,
                language = runCatching { voice.locale?.toLanguageTag() }.getOrNull().orEmpty(),
                // `Voice.QUALITY_HIGH` et au-dessus sont les voix « enhanced » d'Android: elles
                // passent avant les autres dans la répartition.
                quality = if (voice.quality >= android.speech.tts.Voice.QUALITY_HIGH) 2 else 1,
            )
        }
    }

    /**
     * La voix que le synthétiseur donne à une réplique sans bot (`defaultVoice` d'Android): c'est
     * la voix d'Onyx, celle que la répartition laisse à un bot qui n'a pas la sienne.
     */
    fun defaultVoiceId(): String? =
        runCatching { engine?.defaultVoice?.name }.getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * La voix avec laquelle le synthétiseur lit à partir de maintenant: **celle du bot**
     * (`BotVoices`), ou la voix d'Onyx quand il n'en a pas.
     *
     * C'est ce qui fait que deux bots ne lisent pas de la même voix, et qu'aucune réplique n'est
     * jamais lue avec une hauteur inventée: le réglage du bot décide, lui seul.
     */
    private fun applyOnDeviceVoice(speaker: String?) {
        val device = engine ?: return
        val main = runCatching { device.defaultVoice?.name }.getOrNull()
        val chosen = BotVoices.onDevice(
            forProfile = speaker,
            picked = botVoices,
            language = voiceLanguage(),
            main = main,
            installed = installedVoices(),
            names = botNames,
        )
        val voice = chosen?.let { id -> runCatching { device.voices?.firstOrNull { it.name == id } }.getOrNull() }
        runCatching {
            if (voice != null) {
                device.voice = voice
            } else {
                device.defaultVoice?.let { device.voice = it }
            }
        }
    }

    /** La langue de base dans laquelle on lit (« fr »): celle demandée, sinon celle du moteur. */
    private fun voiceLanguage(): String {
        val tag = pendingLanguage
            ?: runCatching { engine?.voice?.locale?.toLanguageTag() }.getOrNull()
            ?: return ""
        return BotVoices.base(tag)
    }

    /**
     * Lit un texte; la lecture précédente est interrompue.
     *
     * `speaker`: de qui sont ces mots — le profil d'un bot, lu **de sa voix** (`BotVoices`); rien
     * pour Onyx, qui lit de la sienne. Une réplique de bot n'est jamais lue de la voix d'Onyx avec
     * une hauteur inventée: c'est sa voix qui change.
     */
    fun speak(text: String, languageTag: String? = null, speaker: String? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        languageTag?.let { applyLanguage(it) }
        cancelRemote()
        currentText = clean
        currentOffset = 0
        playbackBase = 0
        paused = false
        speakingAs = speaker
        // La vitesse du bot, quand il en a une: sinon celle d'Onyx, telle quelle.
        speakingRate = BotVoices.speed(speaker, botVoices, rate)
        if (replyVoice == ReplyVoice.ON_DEVICE) {
            applyOnDeviceVoice(speaker)
            if (ready) startUtterance(clean)
        } else {
            startRemote(clean)
        }
    }

    /** Met la lecture en pause sans perdre le texte; un téléchargement pas encore joué recommencera. */
    fun pause() {
        if (currentText == null || paused) return
        paused = true
        _isSpeaking.value = false
        _replyLevel.value = 0f
        if (remoteActive) {
            remoteJob?.cancel()
            remoteJob = null
            if (!audioPlayer.pause()) audioPlayer.stop()
        } else {
            engine?.stop()
        }
    }

    /** Reprend au dernier fragment Android, ou relance la voix distante si son audio n'était pas prêt. */
    fun resume(): Boolean {
        val text = currentText ?: return false
        if (!paused) return true
        if (remoteActive) {
            paused = false
            if (audioPlayer.resume()) {
                _isSpeaking.value = true
            } else {
                startRemote(text)
            }
            return true
        }
        val remaining = speechRemainder(text, currentOffset)
        if (remaining.isBlank()) {
            currentText = null
            activeUtterance = null
            return false
        }
        playbackBase = currentOffset
        paused = false
        startUtterance(remaining)
        return true
    }

    /** Coupe la lecture — c'est ce que fait l'interruption (« barge-in »). */
    fun stop() {
        cancelRemote()
        // Invalider d'abord: certains moteurs envoient encore `onDone` depuis `stop`.
        activeUtterance = null
        currentText = null
        currentOffset = 0
        playbackBase = 0
        paused = false
        _isSpeaking.value = false
        _replyLevel.value = 0f
        engine?.stop()
    }

    fun shutdown() {
        stop()
        scope.cancel()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun startRemote(text: String) {
        remoteJob?.cancel()
        audioPlayer.stop()
        engine?.stop()
        activeUtterance = null
        remoteActive = true
        paused = false
        val token = ++remoteSequence
        _isSpeaking.value = true
        // Tant que rien n'est mesuré (une voix encodée n'expose pas ses échantillons, ou le
        // premier morceau n'est pas encore écrit), le niveau tient: la bouche du visage ne reste
        // pas fermée pendant qu'une voix se fait entendre.
        _replyLevel.value = SYNTHESISED_SPEAKING_LEVEL
        // La voix Kokoro de ce bot: la sienne, sinon celle d'Onyx. Hermes a la sienne, une pour
        // tous — son réglage est celui du serveur.
        val voice = if (replyVoice == ReplyVoice.KOKORO) {
            BotVoices.kokoro(speakingAs, botVoices, kokoroVoice, kokoroVoices, botNames) ?: kokoroVoice
        } else {
            kokoroVoice
        }
        remoteJob = scope.launch {
            try {
                val audio = when (replyVoice) {
                    ReplyVoice.KOKORO -> {
                        if (normalizedKokoroBase(kokoroBaseUrl) == null || voice.isBlank()) {
                            throw IllegalStateException("Kokoro is not configured")
                        }
                        KokoroSpeechClient().speak(text, kokoroBaseUrl, voice, speakingRate)
                    }
                    ReplyVoice.HERMES -> {
                        val server = hermesServer ?: throw IllegalStateException("Hermes is not configured")
                        val client = HermesSpeechClient(server)
                        if (!client.isAvailable) throw IllegalStateException("Hermes is not configured")
                        client.speak(text)
                    }
                    ReplyVoice.ON_DEVICE -> {
                        fallbackToDevice(token, text)
                        return@launch
                    }
                }
                if (token != remoteSequence || !remoteActive || paused) return@launch
                audioPlayer.play(
                    audio = audio,
                    onFinished = { finishRemote(token) },
                    onError = { fallbackToDevice(token, text) },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                fallbackToDevice(token, text)
            }
        }
    }

    private fun fallbackToDevice(token: Long, text: String) {
        if (token != remoteSequence || !remoteActive || paused) return
        remoteJob = null
        remoteActive = false
        audioPlayer.stop()
        if (ready) {
            startUtterance(text)
        } else {
            currentText = null
            _isSpeaking.value = false
            _replyLevel.value = 0f
            onFinished?.invoke()
        }
    }

    private fun finishRemote(token: Long) {
        if (token != remoteSequence || !remoteActive || paused) return
        remoteJob = null
        remoteActive = false
        currentText = null
        currentOffset = 0
        playbackBase = 0
        _isSpeaking.value = false
        _replyLevel.value = 0f
        onFinished?.invoke()
    }

    private fun cancelRemote() {
        remoteSequence += 1
        remoteJob?.cancel()
        remoteJob = null
        remoteActive = false
        audioPlayer.stop()
    }

    private fun applyEarAttributes() {
        val attributes = AudioAttributes.Builder()
            .setUsage(
                if (earRouting) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA,
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        runCatching { engine?.setAudioAttributes(attributes) }
    }

    private fun startUtterance(text: String) {
        utteranceSequence += 1
        val id = "onyx.reply.$utteranceSequence"
        activeUtterance = id
        engine?.setSpeechRate(speakingRate)
        // Le synthétiseur n'expose aucune amplitude: la bouche du visage bat sur ce niveau tenu.
        _replyLevel.value = SYNTHESISED_SPEAKING_LEVEL
        // Les attributs suivent la sortie du moment: média, ou appel dans l'écouteur.
        applyEarAttributes()
        _isSpeaking.value = true
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun isActive(utteranceId: String?): Boolean =
        utteranceId != null && utteranceId == activeUtterance

    private fun finishUtterance(utteranceId: String?) {
        if (!isActive(utteranceId)) return
        _isSpeaking.value = false
        _replyLevel.value = 0f
        if (paused) return
        activeUtterance = null
        currentText = null
        currentOffset = 0
        playbackBase = 0
        onFinished?.invoke()
    }

    private fun applyLanguage(tag: String) {
        val locale = runCatching { Locale.forLanguageTag(tag) }.getOrNull() ?: return
        val result = engine?.setLanguage(locale) ?: return
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine?.setLanguage(Locale.getDefault())
        }
    }
}

/** Ce qu'il reste à lire après une pause, borné aux limites du texte. */
internal fun speechRemainder(text: String, offset: Int): String =
    text.substring(offset.coerceIn(0, text.length))
