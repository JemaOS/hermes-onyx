package com.onyx.app.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Délai avant de rouvrir le micro pendant que l'app parle: la sortie audio doit d'abord prendre
 * le focus, sinon le moteur vocal refuse de s'ouvrir (`ERROR_CLIENT`, code 5).
 */
private const val MIC_REOPEN_DELAY_MS = 900L

/**
 * Délai avant de rouvrir le micro **après la fin** d'une lecture (le mode mains-libres qui
 * reprend). Plus long que le délai de libération du moteur (500 ms): la queue de la voix doit
 * s'éteindre, sinon ses derniers mots rentrent par le micro.
 */
// Le micro se rouvre juste après la fin de la lecture: 550 ms se sentaient comme un trou, et
// on perdait le début de la phrase suivante (« pas fluide »).
private const val MIC_AFTER_REPLY_DELAY_MS = 180L

/**
 * Combien de temps le moteur a pour sortir des mots après qu'une voix a été entendue par-dessus la
 * réponse. Comme `VoiceSession.confirmWindow` (1,5 s): c'est le temps de reconnaître ce qui a
 * été dit, pas le temps de le dire.
 */
private const val CONFIRM_WINDOW_MS = 1500L

/** Le pas de la surveillance du silence (endpointing). */
private const val ENDPOINT_POLL_MS = 120L

/**
 * La durée que représente une mesure de niveau: `onRmsChanged` en livre une dizaine par seconde,
 * c'est ce pas que `BargeInDetector.feed` additionne.
 */
private const val LEVEL_STEP_SECONDS = 0.1


/** Où en est le mode vocal: au repos, à l'écoute, en train de réfléchir, ou en train de parler. */
enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * Le mode vocal: on parle, l'agent répond, et si le mode mains-libres est allumé il se remet à
 * écouter tout seul.
 *
 * Comme , en plus simple: l'écoute passe par un
 * `SpeechRecognizer` local, la lecture par le TTS du système, et l'interruption (« barge-in »)
 * consiste à couper la lecture dès que le micro entend à nouveau quelque chose.
 */
class VoiceSession(
    private val context: Context,
    private val scope: CoroutineScope,
    localeTag: String = SpeechEngine.DEFAULT_LOCALE,
    /**
     * Le chemin « téléphone à l'oreille ». `null`: la vraie plomberie Android (capteur de
     * proximité + `AudioManager`). Un éprouvette en passe un faux, ce qui permet d'exercer le
     * routeur de bout en bout sans capteur — ce n'est donc pas un contrôle inerte.
     */
    earRouter: ProximityEarRouter? = null,
) {
    val engine = SpeechEngine(context, localeTag)
    val output = SpeechOutput(context)

    /** La sortie audio sous Android (écouteur / haut-parleur), et si un casque porte déjà le son. */
    private val audioRoute = AudioManagerEarRoute(context)

    /** Le téléphone posé face contre la table lit « près » sans être à une oreille. */
    private val faceDown = AccelerometerFaceDownDetector(context)

    /**
     * « Écouteur quand on lève le téléphone à l'oreille » (Réglages → Voix → `earpieceAtEar`).
     * Éteint, le capteur n'est jamais ouvert et la réponse reste au haut-parleur.
     */
    var earRoutingEnabled: Boolean = true

    /** Le routeur à l'oreille: celui qu'on a passé, ou la vraie plomberie Android. */
    val ear: ProximityEarRouter = earRouter ?: run {
        // Le capteur rend la mesure par un rappel: le routeur n'existe pas encore quand on
        // l'abonne, d'où ce porte-voix.
        val holder = arrayOfNulls<ProximityEarRouter>(1)
        val router = ProximityEarRouter(
            watcher = SensorProximityWatcher(context) { near -> holder[0]?.proximityChanged(near) },
            route = audioRoute,
            externalRoute = { audioRoute.hasExternalRoute() },
            enabled = { earRoutingEnabled },
            faceDown = { faceDown.isFaceDown() },
        )
        holder[0] = router
        router
    }

    private val _phase = MutableStateFlow(VoicePhase.IDLE)
    val phase: StateFlow<VoicePhase> = _phase.asStateFlow()

    /** Ce que le micro a entendu, réécrit au fil de la phrase. */
    private val _heard = MutableStateFlow("")
    val heard: StateFlow<String> = _heard.asStateFlow()

    private val _handsFree = MutableStateFlow(false)
    val handsFree: StateFlow<Boolean> = _handsFree.asStateFlow()

    private val _spoken = MutableStateFlow("")
    /** Ce que l'agent dit, pendant qu'il le dit. */
    val spoken: StateFlow<String> = _spoken.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    /** Vrai quand la réponse est mise en pause, la phase restant [VoicePhase.SPEAKING]. */
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    /** Appelé avec une phrase terminée: c'est à l'écran de l'envoyer à l'agent. */
    var onQuestion: ((String) -> Unit)? = null

    /**
     * Appelé quand « stop » est dit par-dessus la réponse: le tour en cours côté serveur n'a plus
     * de raison de continuer à s'écrire (`conversation.cancel` ).
     */
    var onStoppedByWord: (() -> Unit)? = null

    /** La langue de lecture: celle des réglages, ou celle de la réponse quand on la connaît. */
    var replyLanguageTag: String = localeTag

    /** Peut-on couper la voix en reprenant la parole ? (Réglages → Voix → « Parler pendant les réponses ».) */
    var talkOver: TalkOver = TalkOver.DEFAULT

    /**
     * Le micro peut-il s'ouvrir **derrière la réponse** ? Décidé au départ de chaque lecture, à
     * partir du réglage et de la route du moment — comme le modèle: jamais quand c'est éteint,
     * avec un casque seulement quand c'est demandé, partout quand c'est demandé partout. Sur le
     * haut-parleur d'un téléphone et sans le réglage, la réponse ne peut donc pas revenir par le
     * micro (c'était le chemin de l'écho).
     */
    private var micBehindReply: Boolean = false

    /** Les phrases qui terminent une conversation en mains-libres, en plus de celles de base. */
    var stopPhrases: List<String> = emptyList()

    /** Vrai si la phrase entendue est un ordre d'arrêt (`StopPhrase`). */
    private fun isStopPhrase(text: String): Boolean {
        val heard = normalizeStopPhrase(text)
        if (heard.isEmpty()) return false
        return heard in BUILT_IN_STOP_PHRASES || stopPhrases.any { normalizeStopPhrase(it) == heard }
    }

    private var watchJob: Job? = null
    private var reopenMicJob: Job? = null

    /** La reprise espacée après un refus du moteur (une seule). */
    private var retryJob: Job? = null

    /**
     * Le détecteur de prise de parole: il ne lit que le **niveau** du micro pendant la réponse,
     * jamais les mots que le moteur pourrait écrire en entendant Onyx lui-même.
     */
    private var bargeIn = BargeInDetector.echoCancelled()

    /**
     * Le silence qui termine une écoute, en millisecondes (Réglages → Voix). Réglable, comme
     * demandé; 1,4 s par défaut.
     */
    var endpointSilenceMs: Long = DEFAULT_ENDPOINT_SILENCE_MS
        set(value) {
            field = value
            endpointWatcher = EndpointWatcher(silenceMs = value)
        }

    /**
     * Le mains-libres se relance-t-il tout seul après chaque réponse ?
     *
     * **Non par défaut**: c'est ce relancement automatique qui, avec la voix de synthèse revenue
     * par le micro, enchaînait écoute → réponse → écoute sans fin. Après une réponse, Onyx se pose
     * donc à l'arrêt ([VoicePhase.IDLE]): on retape l'orbe pour reprendre la parole. (La garde
     * anti-écho empêche la boucle même quand ceci est rallumé.)
     */
    var autoRelisten: Boolean = false

    /** La dernière réponse lue à voix haute: c'est elle qu'on ne veut pas voir revenir du micro. */
    private var lastReplyText: String = ""

    /** La dernière transcription vue, et quand elle a bougé — pour l'endpointing. */
    private var lastTranscript: String = ""
    private var lastTranscriptAt: Long = 0L
    private var firstSpeechAt: Long = 0L
    private var endpointJob: Job? = null

    /** L'endpointing en cours: recréé à chaque écoute (son plancher de bruit suit la pièce). */
    private var endpointWatcher = EndpointWatcher()

    /** Vrai pendant qu'une voix entendue par-dessus la réponse est en train d'être examinée. */
    private var holding: Boolean = false

    /** La fenêtre d'examen: passée sans mots, la réponse repart. */
    private var holdJob: Job? = null
    /** Faux pour une lecture demandée depuis le fil, qui ne doit jamais ouvrir le micro. */
    private var listenAfterSpeech = true

    init {
        engine.onFinished = { /* l'état suffit : tout est observé ci-dessous */ }
        output.onFinished = { if (_phase.value == VoicePhase.SPEAKING) finishSpeaking() }
        output.setLanguage(replyLanguageTag)
        // Le téléphone levé à l'oreille: la lecture prend les attributs d'un appel et sort par
        // l'écouteur; reposé, elle revient au haut-parleur.
        ear.onAtEarChanged = { atEar -> reroute(atEar) }

        watchJob = scope.launch {
            engine.state.collect { speech ->
                when (_phase.value) {
                    VoicePhase.LISTENING -> {
                        if (speech.failed) {
                            // Le moteur a refusé la session (code 5 notamment). Ce n'est pas une
                            // phrase: **rien ne part**. Une reprise espacée, une seule; si elle
                            // échoue aussi, on s'arrête au lieu de relancer toutes les secondes —
                            // c'est le repli du modèle, et le bandeau du moteur dit alors
                            // pourquoi (plus de « nouvelle tentative » qui n'aura pas lieu).
                            // Le brouillon (la dictée en cours) ne survit pas à l'échec: une
                            // session refusée ne laisse rien d'affiché — c'était le « brouillon
                            // partiel resté là » des captures.
                            _heard.value = ""
                            if (engine.canRetry) retryListening() else _phase.value = VoicePhase.IDLE
                            return@collect
                        }
                        if (speech.transcript.isNotEmpty() && speech.transcript != lastTranscript) {
                            lastTranscript = speech.transcript
                            lastTranscriptAt = System.currentTimeMillis()
                            if (firstSpeechAt == 0L) firstSpeechAt = lastTranscriptAt
                            _heard.value = speech.transcript
                        }
                        if (speech.isFinal && speech.transcript.isNotBlank()) {
                            if (isStopPhrase(speech.transcript)) {
                                stop()
                                return@collect
                            }
                            // Une phrase identique à la précédente vient d'un état qui traîne: on
                            // ne la repose pas (c'était la boucle en mains-libres).
                            if (speech.transcript == lastAsked) return@collect
                            // Et une phrase **déjà posée** dans les 60 dernières secondes non plus:
                            // le moteur peut rejouer ses finales, et chaque rejeu relançait un tour,
                            // sans fin.
                            val nowMs = System.currentTimeMillis()
                            while (recentAsked.isNotEmpty() && nowMs - recentAsked.first().second > 60_000L) {
                                recentAsked.removeFirst()
                            }
                            val normalised = normalizeForEcho(speech.transcript)
                            if (normalised.isNotEmpty() && recentAsked.any { it.first == normalised }) {
                                replays += 1
                                if (replays >= 3) {
                                    // Trois reprises: ce n'est plus une conversation, c'est le
                                    // moteur qui se réentend. On arrête, au lieu de boucler.
                                    replays = 0
                                    _handsFree.value = false
                                    _phase.value = VoicePhase.IDLE
                                }
                                engine.cancel()
                                if (_handsFree.value) restartListeningIfHandsFree() else _phase.value = VoicePhase.IDLE
                                return@collect
                            }
                            // **La garde anti-écho**, la même que par-dessus la lecture: la
                            // réponse qu'on vient de lire est revenue par le micro. Rien ne part,
                            // et l'écoute reprend au lieu de boucler. Le point de décision est
                            // unique (`shouldSendHeardTranscript`): la garde vaut donc sur la
                            // transcription finale **et** sur la parole par-dessus, sans divergence.
                            if (!shouldSendHeardTranscript(speech.transcript, lastReplyText, alreadySent = false)) {
                                engine.cancel()
                                if (_handsFree.value) restartListeningIfHandsFree() else _phase.value = VoicePhase.IDLE
                                return@collect
                            }
                            ask(speech.transcript)
                        } else if (!speech.isListening && speech.isFinal) {
                            // Un silence: en mains-libres on se remet à écouter.
                            restartListeningIfHandsFree()
                        }
                    }

                    VoicePhase.SPEAKING -> handleSpeaking(speech)

                    else -> Unit
                }
            }
        }
    }

    fun toggleHandsFree() {
        _handsFree.value = !_handsFree.value
        if (_handsFree.value && _phase.value == VoicePhase.IDLE) startListening()
    }

    /** La dernière phrase envoyée: sert de garde contre une relance de la même question. */
    private var lastAsked: String = ""

    /**
     * Les phrases déjà posées récemment (texte normalisé + instant). Le moteur de reconnaissance
     * peut **rejouer** une finale déjà livrée — et en mains libres, chaque rejeu reposait la même
     * question: la conversation partait en boucle sans jamais s'arrêter. Une phrase reposée dans
     * les 60 s est ignorée; trois reprises d'affilée arrêtent le mode mains libres.
     */
    private val recentAsked = ArrayDeque<Pair<String, Long>>()
    private var replays = 0

    /** Se met à écouter. En mains-libres, reste à l'écoute après chaque phrase. */
    fun startListening(fresh: Boolean = true) {
        _heard.value = ""
        lastAsked = ""
        _isPaused.value = false
        _phase.value = VoicePhase.LISTENING
        lastTranscript = ""
        firstSpeechAt = 0L
        lastTranscriptAt = System.currentTimeMillis()
        engine.start(fresh)
        refreshEndpointWatch()
    }

    /**
     * La **seule** reprise après un refus du moteur: une, espacée, puis on s'arrête.
     *
     * Elle passe `fresh = false` pour que le compteur de refus du moteur ne reparte pas de zéro —
     * sinon un moteur durablement cassé se relancerait indéfiniment (le défaut signalé: l'app
     * « redémarre le moteur en boucle »).
     */
    private fun retryListening() {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(ENGINE_RETRY_DELAY_MS)
            if (_phase.value == VoicePhase.LISTENING) startListening(fresh = false)
        }
    }

    /**
     * L'endpointing: l'écoute se termine d'elle-même quand la transcription est stable **et** que
     * la pièce est calme ([EndpointWatcher]) — puis la phrase part, une fois. Sans lui, une écoute
     * ne se fermait qu'au silence du moteur, et le tour s'enchaînait sans temps d'arrêt; avec un
     * minuteur sur la seule transcription, une hésitation coupait la phrase au milieu.
     */
    private fun refreshEndpointWatch() {
        endpointJob?.cancel()
        endpointJob = scope.launch {
            endpointWatcher = EndpointWatcher()
            endpointWatcher.start(System.currentTimeMillis())
            while (true) {
                delay(ENDPOINT_POLL_MS)
                if (_phase.value != VoicePhase.LISTENING) return@launch
                val now = System.currentTimeMillis()
                // Le niveau du moment: c'est lui qui dit que la personne parle encore, même quand
                // le moteur n'écrit plus rien.
                endpointWatcher.level(engine.state.value.level, ENDPOINT_POLL_MS / 1000.0, now)
                if (endpointWatcher.shouldEnd(_heard.value, now - lastTranscriptAt, now - firstSpeechAt, now)) {
                    // Le moteur rend la phrase entendue; la branche LISTENING l'enverra une fois.
                    engine.stop()
                    lastTranscriptAt = now
                    return@launch
                }
            }
        }
    }

    /** Arrête tout: le micro, la lecture, et l'état. */
    fun stop() {
        reopenMicJob?.cancel()
        reopenMicJob = null
        retryJob?.cancel()
        retryJob = null
        holdJob?.cancel()
        holdJob = null
        endpointJob?.cancel()
        endpointJob = null
        holding = false
        ear.setWanted(false)
        faceDown.stop()
        engine.stop()
        output.stop()
        _phase.value = VoicePhase.IDLE
        _isPaused.value = false
        _heard.value = ""
        _spoken.value = ""
    }

    /** La voix qui se lit, de 0 à 1: ce qui fait battre la bouche du visage qui parle. */
    val replyLevel: StateFlow<Float> get() = output.replyLevel

    /** Lit une réponse. En mode vocal seulement, le micro peut ensuite rouvrir pour l'interruption. */
    fun speak(text: String, listenAfterSpeech: Boolean = true, speaker: String? = null) {
        reopenMicJob?.cancel()
        this.listenAfterSpeech = listenAfterSpeech
        val clean = text.trim()
        if (clean.isEmpty()) {
            finishSpeaking()
            return
        }
        _spoken.value = clean
        _phase.value = VoicePhase.SPEAKING
        _isPaused.value = false
        holding = false
        holdJob?.cancel()
        holdJob = null
        // Le micro d'un casque entend la pièce à son vrai niveau, celui du téléphone est filtré
        // par l'annulation d'écho: les deux seuils , choisis selon la route du moment.
        // Le seuil d'interruption est celui qu'on demande: BARGE_IN_HOLD_MS de parole tenue, pas
        // un dixième de seconde de bruit.
        val holdSeconds = BARGE_IN_HOLD_MS / 1000.0
        bargeIn = if (audioRoute.hasExternalRoute()) {
            BargeInDetector.headset(hold = holdSeconds)
        } else {
            BargeInDetector.echoCancelled(hold = holdSeconds)
        }
        lastReplyText = clean
        // Le micro derrière **cette** réponse: le réglage « Parler pendant les réponses », confronté
        // à la route du moment (un casque ne s'entend pas lui-même, un haut-parleur si).
        micBehindReply = talkOverAllowsMic(talkOver, audioRoute.hasExternalRoute())
        // **Le micro est fermé avant que la voix ne démarre**, et il le reste pendant toute la
        // lecture: c'est le demi-duplex. Sans cette fermeture explicite, la session d'écoute
        // encore ouverte capturait les premiers mots de la réponse — le texte lu arrivait au
        // moteur avant que le micro ne se referme. `cancel` jette aussi ce qui traînait, pour
        // qu'aucune transcription périmée ne puisse être envoyée au tour suivant.
        engine.cancel()
        // Le téléphone levé à l'oreille: la réponse s'y joue comme un appel (`setEarRouting`).
        // L'accéléromètre ne tourne que le temps d'une lecture: il sert au « posé face contre la
        // table », qui n'est pas une oreille.
        faceDown.start()
        ear.setWanted(true)
        output.setLanguage(replyLanguageTag)
        // `speaker`: la réplique d'un bot sort de **sa** voix (`BotVoices`), pas de celle d'Onyx.
        output.speak(clean, replyLanguageTag, speaker)
        // Le micro reste ouvert pendant la lecture: c'est ce qui rend l'interruption possible.
        // Mais pas dans l'instant où la voix démarre — la sortie audio prend le focus et le
        // moteur vocal refuse alors de s'ouvrir (code 5). D'où ce court décalage.
        refreshEndpointWatch()
        reopenMicJob = scope.launch {
            delay(MIC_REOPEN_DELAY_MS)
            // **Demi-duplex par défaut**: le micro ne se rouvre pendant la lecture que si le
            // barge-in est demandé (Réglages → Voix). Sinon la voix de synthèse ne peut pas
            // revenir par le micro, et c'est ce qui casse la boucle.
            if (shouldReopenMicForSpeech(_phase.value, engine.state.value.isListening, listenAfterSpeech, micBehindReply)) {
                engine.start()
            }
        }
    }

    /** Pause/Play de `VoiceView.controls`, sans changer de phase ni perdre le bot qui parle. */
    fun pauseSpeaking() {
        if (_phase.value != VoicePhase.SPEAKING || _isPaused.value) return
        _isPaused.value = true
        output.pause()
    }

    fun resumeSpeaking() {
        if (_phase.value != VoicePhase.SPEAKING || !_isPaused.value) return
        _isPaused.value = false
        if (!output.resume()) finishSpeaking()
    }

    /** Appelé par l'écran quand le tour est fini: on parle, puis on réécoute. */
    fun finishTurn(replyText: String?) {
        if (replyText.isNullOrBlank()) {
            finishSpeaking()
        } else {
            speak(replyText)
        }
    }

    fun release() {
        watchJob?.cancel()
        reopenMicJob?.cancel()
        retryJob?.cancel()
        holdJob?.cancel()
        endpointJob?.cancel()
        holding = false
        ear.release()
        audioRoute.release()
        faceDown.stop()
        engine.release()
        output.shutdown()
    }

    /**
     * Pendant la lecture: le **niveau** du micro dit qu'une voix s'élève (`BargeInDetector`), la
     * réponse est alors suspendue le temps que le moteur dise si c'était des mots — la
     * `confirmWindow` — et ce qu'il en sort décide de la suite.
     *
     * Le niveau d'abord, exprès: le moteur qui entend Onyx parler n'a pas à écrire ses propres
     * mots, et un texte reconnu tant que rien n'a été entendu par-dessus ne compte donc pas.
     */
    private fun handleSpeaking(speech: SpeechState) {
        // Sans micro derrière la réponse (demi-duplex: réglage éteint, ou haut-parleur quand le
        // réglage ne vaut que pour un casque), il n'y a rien à surveiller: le niveau du micro ne
        // peut pas être celui de quelqu'un qui parle par-dessus.
        if (!micBehindReply) return
        val text = speech.transcript
        // Garde anti-boucle: la phrase déjà envoyée ne repart jamais telle quelle.
        val fresh = text.isNotBlank() && text != lastAsked

        if (holding) {
            if (!fresh) return
            // (« Okay » peut encore devenir « okay, stop »: le bruit d'écoute attend la fenêtre.)
            when (bargeInOutcomeFor(text, stopPhrases)) {
                BargeInOutcome.NONE, BargeInOutcome.BACKCHANNEL -> Unit
                BargeInOutcome.STOP -> stoppedByWord()
                // La garde anti-écho vaut aussi ici — le même point de décision que la
                // transcription finale (`shouldSendHeardTranscript`): ce que le micro rend de la
                // réponse en cours ne coupe pas la réponse.
                BargeInOutcome.WORDS ->
                    if (shouldSendHeardTranscript(text, _spoken.value, alreadySent = false)) {
                        interruptedBy(text, speech.isFinal)
                    }
            }
            return
        }

        if (fresh && bargeInOutcomeFor(text, stopPhrases) == BargeInOutcome.STOP) {
            // Un ordre d'arrêt n'attend pas: le niveau, lui, a besoin de quelques mesures.
            stoppedByWord()
            return
        }
        if (shouldWatchForBargeIn(_phase.value, speech.isListening, _isPaused.value, holding, micBehindReply) &&
            bargeIn.feed(speech.rmsDb, LEVEL_STEP_SECONDS)
        ) {
            holdToHearWords()
        }
    }

    /**
     * La sortie a changé en pleine réponse (le téléphone vient d'être levé, ou reposé).
     *
     * Le synthétiseur Android ne se réachemine pas au milieu d'un énoncé: la phrase reprend donc
     * là où elle en est, avec la sortie du moment. Un lecteur distant, lui, repart du début de son
     * audio — le seul moyen qu'offre un `MediaPlayer`.
     */
    private fun reroute(atEar: Boolean) {
        val changed = output.earRouting != atEar
        output.setEarRouting(atEar)
        if (!changed) return
        if (_phase.value != VoicePhase.SPEAKING || _isPaused.value || holding) return
        if (_spoken.value.isEmpty() || !output.isSpeaking.value) return
        output.pause()
        output.resume()
    }

    /** Une voix au-dessus de la réponse: on suspend la lecture et on écoute s'il y a des mots. */
    private fun holdToHearWords() {
        holding = true
        output.pause()
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(CONFIRM_WINDOW_MS)
            if (holding) carryOn()
        }
    }

    /** C'était des mots: la réponse s'arrête ici, comme si on avait tapé l'orbe. */
    private fun interruptedBy(text: String, isFinal: Boolean) {
        holdJob?.cancel()
        holdJob = null
        holding = false
        output.stop()
        _heard.value = text
        if (isFinal) {
            ask(text)
        } else {
            // Le moteur écoute déjà: la fin de la phrase passera par la branche LISTENING.
            _phase.value = VoicePhase.LISTENING
        }
    }

    /** Ni mots ni bruit d'écoute (une toux, une porte): la réponse repart où elle s'est arrêtée. */
    private fun carryOn() {
        holdJob = null
        holding = false
        if (_phase.value != VoicePhase.SPEAKING) return
        bargeIn.rearm()
        if (!_isPaused.value) output.resume()
    }

    /** Un ordre d'arrêt par-dessus la réponse: elle se tait, et le mains-libres avec elle. */
    private fun stoppedByWord() {
        holdJob?.cancel()
        holdJob = null
        holding = false
        _handsFree.value = false
        stop()
        onStoppedByWord?.invoke()
    }

    private fun ask(question: String) {
        // `cancel` et non `stop`: la phrase reconnue doit être **effacée** de l'état du
        // moteur. Avec `stop`, `isFinal` restait vrai et le transcript intact; pendant la
        // réponse, la branche barge-in relisait cet état périmé et reposait la **même** question,
        // en boucle, à chaque tour. `cancel` vide l'état et ferme le micro (il se rouvrira
        // pendant la lecture, ce qui suffit au barge-in).
        engine.cancel()
        // Le tour est en cours **tout de suite**: sans ça, le moteur rendait une finale avec
        // `isListening = false` juste après l'envoi, et la boucle en mains libres rouvrait le micro
        // pendant que l'agent réfléchissait — il s'auto-écoutait (« ça s'écoute même quand elle
        // répond »).
        _phase.value = VoicePhase.THINKING
        lastAsked = question
        // On garde la trace des phrases posées: une finale rejouée par le moteur ne relance pas
        // un tour (c'était la boucle sans fin du mode mains libres).
        normalizeForEcho(question).takeIf { it.isNotEmpty() }?.let { recentAsked.addLast(it to System.currentTimeMillis()) }
        replays = 0
        _heard.value = question
        _spoken.value = ""
        _isPaused.value = false
        _phase.value = VoicePhase.THINKING
        onQuestion?.invoke(question)
    }

    private fun finishSpeaking() {
        _phase.value = VoicePhase.IDLE
        _isPaused.value = false
        holding = false
        // Retour à PRÊT: le brouillon d'écoute n'a plus lieu d'être affiché. Ce qui a été dit est
        // dans le fil; ce qui n'a été qu'entendu à moitié ne doit pas rester sous les yeux.
        _heard.value = ""
        endpointJob?.cancel()
        endpointJob = null
        // Plus rien ne se lit: le capteur se referme et la sortie redevient celle du téléphone.
        ear.setWanted(false)
        faceDown.stop()
        if (shouldRestartHandsFreeAfterSpeech(_handsFree.value, listenAfterSpeech, autoRelisten)) {
            restartListeningIfHandsFree()
        }
    }

    private fun restartListeningIfHandsFree() {
        if (!_handsFree.value) {
            _phase.value = VoicePhase.IDLE
            return
        }
        scope.launch {
            // La réouverture après la fin de la lecture attend un délai: le moteur se libère
            // (`ERROR_CLIENT` sinon), et la queue de la voix de synthèse s'éteint — rouvrir dans
            // l'instant ferait rentrer l'écho des derniers mots par le micro.
            delay(MIC_AFTER_REPLY_DELAY_MS)
            if (_phase.value == VoicePhase.IDLE) startListening()
        }
    }
}

/**
 * Ce qu'une prise de parole par-dessus la réponse est: rien, un bruit d'écoute, un ordre d'arrêt,
 * ou des mots.
 *
 * Comme `Backchannel` + `StopWord` tels que s'en sert — et
 * décision pure: aucun micro, aucune horloge, donc éprouvable sur JVM.
 */
enum class BargeInOutcome { NONE, BACKCHANNEL, STOP, WORDS }

/**
 * Classe [text] dit par-dessus la réponse.
 *
 * Un silence ne décide rien; « mm-hm », « okay », « d'accord » ne coupent pas la réponse (c'était
 * le défaut: n'importe quel mot reconnu l'arrêtait); une phrase d'arrêt — « stop », ou celles de
 * la personne — arrête tout; le reste est une vraie prise de parole.
 */
fun bargeInOutcomeFor(text: String, own: List<String> = emptyList()): BargeInOutcome {
    if (text.isBlank()) return BargeInOutcome.NONE
    if (StopWord.heardIn(text, own)) return BargeInOutcome.STOP
    if (Backchannel.isOnly(text)) return BargeInOutcome.BACKCHANNEL
    return BargeInOutcome.WORDS
}

/**
 * Faut-il surveiller le niveau du micro à cet instant ?
 *
 * `VoiceSession.watchIfSpeaking`: **seulement** pendant que la réponse se lit, pas en
 * pause, pas quand une voix est déjà en train d'être examinée, et pas quand la personne a éteint
 * le fait de parler par-dessus.
 */
internal fun shouldWatchForBargeIn(
    phase: VoicePhase,
    isListening: Boolean,
    isPaused: Boolean,
    holding: Boolean,
    enabled: Boolean,
): Boolean = enabled && !holding && phase == VoicePhase.SPEAKING && isListening && !isPaused

/** Le mains-libres ne reprend qu'après une lecture du mode vocal, pas après une lecture du fil. */
internal fun shouldRestartHandsFreeAfterSpeech(
    handsFree: Boolean,
    listenAfterSpeech: Boolean,
    autoRelisten: Boolean = true,
): Boolean = handsFree && listenAfterSpeech && autoRelisten

/** La phrase telle qu'elle est entendue: minuscules, sans politesses autour. */
fun normalizeStopPhrase(phrase: String): String {
    val clean = phrase.lowercase().replace(Regex("[\\p{Punct}]"), " ")
    val tokens = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
    val leading = setOf("okay", "ok", "hey", "onyx", "please")
    val trailing = listOf(listOf("thank", "you"), listOf("thanks"), listOf("please"))

    while (tokens.isNotEmpty() && tokens.first() in leading) tokens.removeAt(0)
    var trimmed = true
    while (trimmed && tokens.isNotEmpty()) {
        trimmed = false
        for (words in trailing) {
            if (tokens.size >= words.size && tokens.takeLast(words.size) == words) {
                repeat(words.size) { tokens.removeAt(tokens.lastIndex) }
                trimmed = true
            }
        }
    }
    return tokens.joinToString(" ")
}

/** Ce qui arrête toujours, comme `StopPhrase.phrases`. */
val BUILT_IN_STOP_PHRASES = setOf(
    "stop", "stop listening", "that's all", "that's enough", "goodbye", "good night",
    "end conversation", "we're done", "never mind", "cancel", "thank you",
)
