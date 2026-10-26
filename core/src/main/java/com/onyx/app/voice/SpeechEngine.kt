package com.onyx.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ce que le micro entend, et où il en est. */
data class SpeechState(
    val isListening: Boolean = false,
    /** Ce qui a été reconnu jusqu'ici ; le partiel est réécrit, jamais concaténé. */
    val transcript: String = "",
    /** Vrai quand la phrase est terminée et que le texte ne bougera plus. */
    val isFinal: Boolean = false,
    /** Niveau du micro, de 0 à 1 — l'orbe en dépend. */
    val level: Float = 0f,
    /**
     * Le même niveau, en dBFS : `onRmsChanged` le donne tel quel. C'est ce que mesure
     * [BargeInDetector] pour savoir si quelqu'un parle par-dessus la réponse.
     */
    val rmsDb: Float = SILENT_DB,
    /** Un problème à montrer, ou `null`. */
    val problem: String? = null,
    /**
     * Le moteur a **refusé** la session : ce n'est pas une phrase terminée, et rien de ce qui a été
     * entendu ne doit partir comme un tour. (Vrai aussi pour un silence reconnu comme une erreur :
     * le partiel de la dictée est jeté, l'écoute reprend simplement.)
     */
    val failed: Boolean = false,
) {
    companion object {
        /** Ce que vaut un silence : assez bas pour ne jamais franchir un seuil de voix. */
        const val SILENT_DB = -120f
    }
}

/**
 * La reconnaissance vocale, en local.
 *
 * `EXTRA_PREFER_OFFLINE` demande au moteur de l'appareil de travailler sans réseau : la voix ne
 * quitte pas le téléphone, même si le moteur accepte une reconnaissance en ligne.
 * La langue est celle des réglages (`fr-FR` par défaut).
 *
 * Un `SpeechRecognizer` ne tourne pas indéfiniment : il rend la main après une phrase. Le mode
 * mains-libres le relance donc à chaque fin, ce que fait [VoiceSession].
 */
class SpeechEngine(
    private val context: Context,
    private val localeTag: String = DEFAULT_LOCALE,
) {
    private var recognizer: SpeechRecognizer? = null

    /** Le `SpeechRecognizer` ne vit que sur le looper principal : tout passe par ce Handler. */
    private val main = Handler(Looper.getMainLooper())

    /**
     * Une seule session à la fois. Relancer la reconnaissance pendant que la précédente se
     * ferme est **la** cause de `ERROR_CLIENT` (code 5) : le moteur refuse le second départ.
     */
    private var sessionActive = false

    /**
     * Le hors-ligne est un souhait (`EXTRA_PREFER_OFFLINE`), pas une garantie : si le moteur du
     * téléphone ne sait pas le faire pour cette langue, il échoue au premier essai. On retente
     * alors une fois en ligne au lieu de laisser l'utilisateur devant un code d'erreur.
     */
    private var preferOffline = true

    /** Vrai quand la dernière session s'est mal terminée : on repart alors d'un moteur neuf. */
    private var needsFreshRecognizer = false

    /** Refus consécutifs du moteur : un incident isolé est réessayé en silence. */
    private var consecutiveErrors = 0

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** Appelé quand le moteur a fini une phrase (ou s'est arrêté), pour relancer si besoin. */
    var onFinished: (() -> Unit)? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Une reprise automatique a-t-elle encore un sens ? Vrai tant que le moteur n'a pas refusé
     * [MAX_ENGINE_ATTEMPTS] fois d'affilée : au-delà, [VoiceSession] s'arrête et le bandeau dit
     * pourquoi (le repli de la référence). Une session réussie remet le compteur à zéro.
     */
    val canRetry: Boolean get() = shouldRetryEngine(consecutiveErrors)

    /**
     * Démarre l'écoute. [fresh] : une demande neuve (un appui, une phrase terminée) — le compteur de
     * refus est remis à zéro. La reprise de [VoiceSession] passe `fresh = false`, sinon un moteur
     * durablement cassé se relancerait sans fin (c'est exactement la boucle signalée).
     */
    fun start(fresh: Boolean = true) = main.post { startNow(fresh) }

    private fun startNow(fresh: Boolean = true) {
        if (sessionActive) return
        if (fresh) consecutiveErrors = 0
        if (!isAvailable) {
            _state.update { it.copy(problem = "This device has no speech recogniser.") }
            return
        }
        // On garde le même moteur d'une phrase à l'autre : en recréer un à chaque fois ajoutait
        // de la latence et des refus. Un moteur neuf seulement si la session précédente a échoué.
        val engine = recognizer?.takeUnless { needsFreshRecognizer }
            ?: run {
                recognizer?.let { runCatching { it.destroy() } }
                createRecognizer().also { recognizer = it }
            }
        needsFreshRecognizer = false
        sessionActive = true
        _state.update { it.copy(isListening = true, isFinal = false, transcript = "", problem = null) }
        runCatching { engine.startListening(intent()) }
            .onFailure { error ->
                sessionActive = false
                needsFreshRecognizer = true
                _state.update { it.copy(problem = error.message, isListening = false) }
            }
    }

    /** Arrête d'écouter sans jeter ce qui a été entendu. */
    fun stop() = main.post {
        runCatching { recognizer?.stopListening() }
        _state.update { it.copy(isListening = false, level = 0f) }
        // Si la session ne rend pas la main (silence, refus du moteur), on libère la place.
        main.postDelayed({ sessionActive = false }, RESTART_DELAY_MS * 2)
    }

    /** Arrête, jette la phrase en cours et repart à zéro. */
    fun cancel() = main.post {
        runCatching { recognizer?.cancel() }
        _state.update { SpeechState() }
        main.postDelayed({ sessionActive = false }, RESTART_DELAY_MS * 2)
    }

    fun release() = main.post {
        runCatching { recognizer?.destroy() }
        recognizer = null
        sessionActive = false
        _state.update { SpeechState() }
    }

    /**
     * Rend la main au mode mains-libres **après** la fermeture réelle de la session.
     *
     * Relancer dans le callback lui-même produit `ERROR_CLIENT` (code 5) : le moteur n'a pas
     * encore libéré le micro. Un court délai suffit, et il n'est pas perceptible en usage.
     */
    private fun finishLater() {
        main.postDelayed({ onFinished?.invoke() }, RESTART_DELAY_MS)
    }

    private fun createRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.update { it.copy(isListening = true, problem = null) }
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    // `onRmsChanged` arrive vers -2…12 dB : ramené à 0…1 pour l'orbe.
                    val level = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
                    _state.update { it.copy(level = level, rmsDb = rmsdB) }
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _state.update { it.copy(isListening = false, level = 0f) }
                }

                override fun onError(error: Int) {
                    // Le moteur hors-ligne peut refuser la langue : l'essai suivant se fera en ligne.
                    if (error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_SERVER ||
                        error == SpeechRecognizer.ERROR_NETWORK
                    ) {
                        preferOffline = false
                    }
                    val problem = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> null // Un silence n'est pas une erreur : on relance simplement.
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "The microphone is not allowed for this app."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        -> "The speech recogniser needs a network for this language."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy."
                        SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                        -> "The speech service disconnected or failed. Onyx will retry."
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                            "The speech service is receiving too many requests. Onyx will retry."
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "Speech recognition is not available in this language on this device."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            "This language is not supported by the speech recogniser."
                        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
                            "Onyx cannot check speech recognition support on this device."
                        SpeechRecognizer.ERROR_CLIENT ->
                            engineFailureMessage(SpeechRecognizer.ERROR_CLIENT)
                        else -> "The recogniser stopped (code $error)."
                    }
                    // Un silence n'est pas un échec : il ne compte pas, et l'écoute reprend.
                    val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    consecutiveErrors = if (silence) 0 else consecutiveErrors + 1
                    sessionActive = false
                    // Une session qui vient d'échouer repartira d'un moteur neuf.
                    needsFreshRecognizer = !silence
                    // Le nombre de départs d'une même demande d'écoute est borné : au-delà, c'est
                    // [VoiceSession] qui s'arrête et montre [engineFailureMessage]. Un refus
                    // passager, lui, se couvre d'une reprise espacée — sans bandeau, comme avant.
                    val retrying = !silence && isTransientEngineFailure(error) && shouldRetryEngine(consecutiveErrors)
                    _state.update {
                        it.copy(
                            isListening = false,
                            level = 0f,
                            // **Rien de ce qui a été reconnu ne part.** Une session refusée n'est pas
                            // une phrase terminée : `isFinal` restait vrai avec la dictée partielle
                            // encore là, et chaque refus la renvoyait comme un tour — de plus en plus
                            // long au fil de la dictée. C'était la « même question posée plusieurs
                            // fois » du signalement.
                            transcript = "",
                            isFinal = silence,
                            failed = !silence,
                            // Le bandeau ne s'allume pas pour un incident isolé (il se rétablissait
                            // seul) : il dit ce qui reste à faire quand plus rien ne réessaie. Et il
                            // ne promet jamais une reprise qui n'aura pas lieu.
                            problem = when {
                                retrying -> null
                                isTransientEngineFailure(error) -> engineFailureMessage(error)
                                else -> problem
                            },
                        )
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text = results.firstSentence()
                    sessionActive = false
                    consecutiveErrors = 0
                    needsFreshRecognizer = false
                    _state.update {
                        it.copy(
                            transcript = text.ifEmpty { it.transcript },
                            isListening = false,
                            isFinal = text.isNotEmpty(),
                            level = 0f,
                        )
                    }
                    finishLater()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults.firstSentence()
                    if (text.isNotEmpty()) {
                        _state.update { it.copy(transcript = text, isFinal = false) }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // La voix reste sur l'appareil quand le moteur sait le faire ; sinon l'essai suivant
        // repart en ligne (voir `preferOffline`), plutôt que d'échouer en boucle.
        if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun Bundle?.firstSentence(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    companion object {
        const val DEFAULT_LOCALE = "fr-FR"

        /**
         * Délai avant de rendre la main au mode mains-libres : la session précédente doit être
         * réellement fermée, sinon le moteur répond `ERROR_CLIENT` (code 5). Court, sinon la
         * conversation paraît hachée.
         */
        const val RESTART_DELAY_MS = 250L
    }
}
