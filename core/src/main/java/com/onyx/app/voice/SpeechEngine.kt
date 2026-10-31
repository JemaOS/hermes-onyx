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
    /**
     * Ce que la session doit **faire** après cet arrêt ([engineStopDecision]), ou `null` quand il
     * n'y a rien à décider. `failed` dit qu'il y a eu arrêt ; celle-ci dit la suite : rouvrir,
     * reprendre, **basculer en ligne**, ou s'arrêter en le disant.
     */
    val stopDecision: EngineStopDecision? = null,
) {
    companion object {
        /** Ce que vaut un silence : assez bas pour ne jamais franchir un seuil de voix. */
        const val SILENT_DB = -120f
    }
}

/**
 * La reconnaissance vocale, sur l'appareil.
 *
 * La langue est celle des réglages (`fr-FR` par défaut). Le mode **hors ligne** est un **souhait**
 * de la personne (Réglages → Voix → « Reconnaître hors ligne »), **éteint par défaut** : quand il
 * est éteint, l'intention ne porte **pas** `EXTRA_PREFER_OFFLINE` et le moteur travaille **en
 * ligne**. L'imposer était la cause du code 13 (`language-unavailable`) : sur un téléphone sans
 * pack hors ligne pour la langue, le moteur démarrait puis s'arrêtait tout seul.
 *
 * Quand le hors ligne est demandé et que le moteur avoue ne pas savoir le faire pour cette langue
 * (codes 13, 12, 11, 5, 4, 2, 1), [SpeechEngine] **bascule en ligne de lui-même** — une fois, sans
 * rien redemander. La décision, code par code, vit dans [engineStopDecision] ; ici, on la suit.
 *
 * Un `SpeechRecognizer` ne tourne pas indéfiniment : il rend la main après une phrase. Une **fin
 * de session normale** (silence, durée maximale) n'est pas une panne : l'écoute se rouvre, ce que
 * fait [VoiceSession].
 */
class SpeechEngine(
    private val context: Context,
    private val localeTag: String = DEFAULT_LOCALE,
) : SpeechInput {
    private var recognizer: SpeechRecognizer? = null

    /** Le `SpeechRecognizer` ne vit que sur le looper principal : tout passe par ce Handler. */
    private val main = Handler(Looper.getMainLooper())

    /**
     * Une seule session à la fois. Relancer la reconnaissance pendant que la précédente se
     * ferme est **la** cause de `ERROR_CLIENT` (code 5) : le moteur refuse le second départ.
     */
    private var sessionActive = false

    /**
     * Le souhait de la personne : « Reconnaître hors ligne » (Réglages → Voix).
     *
     * **Faux par défaut** — la reconnaissance se fait **en ligne**, comme le moteur de l'appareil
     * sait le faire. L'imposer (`EXTRA_PREFER_OFFLINE` systématique) était la cause du `code=13
     * language-unavailable` du journal : le moteur démarrait, ne trouvait pas sa langue hors ligne,
     * puis s'arrêtait — et l'app coupait l'écoute.
     */
    override var offlineWanted: Boolean = false
        set(value) {
            field = value
            // Le souhait rallumé relève la bascule : le réglage reste celui de la personne.
            if (!value) fellBackOnline = false
        }

    /**
     * Le moteur **hors ligne** a échoué pour cette écoute : on ne le retente plus, et l'essai
     * suivant se fait **en ligne**. Définitif pour l'écoute en cours — jamais de va-et-vient.
     */
    private var fellBackOnline = false

    /**
     * Le souhait **effectif** du prochain départ : le hors ligne de la personne, moins la bascule
     * (`language-unavailable` notamment). C'est la seule chose que l'intention lit.
     */
    private val preferOffline: Boolean get() = offlineWanted && !fellBackOnline

    /** Vrai quand la dernière session s'est mal terminée : on repart alors d'un moteur neuf. */
    private var needsFreshRecognizer = false

    /**
     * Le moteur a-t-il **répondu** ? Vrai dès qu'un rappel du moteur arrive (`onReadyForSpeech`,
     * une mesure de niveau, un résultat, une erreur). C'est la seule preuve que
     * « À L'ÉCOUTE » n'est pas un mensonge : [SpeechState.isListening] est posé au départ, avant
     * que le moteur n'ait dit quoi que ce soit, et ne prouve donc rien.
     *
     * Il **survit aux reprises automatiques** (une session qui s'ouvre ne remet pas le doute) et
     * n'est remis à zéro que sur une demande **neuve** ([start] avec `fresh = true`) — sinon un
     * moteur qui redémarre sa session au silence serait signalé comme absent.
     */
    override var answered = false
        private set

    /** Refus consécutifs du moteur : un incident isolé est réessayé en silence. */
    private var consecutiveErrors = 0

    /**
     * Les mesures de niveau (`onRmsChanged`) reçues depuis le dernier départ : les « trames » du
     * micro. C'est ce compteur que la page Journaux montre pour distinguer « le moteur tourne mais
     * le micro ne donne rien » d'« aucune session n'a démarré ».
     */
    override var frameCount: Int = 0
        private set

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** Appelé quand le moteur a fini une phrase (ou s'est arrêté), pour relancer si besoin. */
    override var onFinished: (() -> Unit)? = null

    override val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Ce que le journal dit d'un moteur qui ne répond pas : le souhait **effectif** hors ligne
     * (celui de la personne, moins une bascule déjà faite), tel quel.
     */
    override val prefersOffline: Boolean get() = preferOffline

    /** La langue demandée au moteur — un moteur muet peut être une affaire de langue. */
    override val requestedLocale: String get() = localeTag

    /**
     * Une reprise automatique a-t-elle encore un sens ? Vrai tant que le moteur n'a pas refusé
     * [MAX_ENGINE_ATTEMPTS] fois d'affilée : au-delà, [VoiceSession] s'arrête et le bandeau dit
     * pourquoi (le repli de la référence). Une session réussie remet le compteur à zéro.
     */
    override val canRetry: Boolean get() = shouldRetryEngine(consecutiveErrors)

    /**
     * Les refus consécutifs du moteur. C'est ce compteur qui **espace** les reprises
     * ([engineRetryDelayMs]) : plus le moteur refuse, plus on lui laisse de temps.
     */
    override val attempts: Int get() = consecutiveErrors

    /**
     * Démarre l'écoute. [fresh] : une demande neuve (un appui, une phrase terminée) — le compteur de
     * refus est remis à zéro. La reprise de [VoiceSession] passe `fresh = false`, sinon un moteur
     * durablement cassé se relancerait sans fin (c'est exactement la boucle signalée).
     */
    override fun start(fresh: Boolean) {
        main.post { startNow(fresh) }
    }

    private fun startNow(fresh: Boolean = true) {
        // Une demande d'écoute qui tombe sur une session déjà ouverte ne peut pas être ignorée en
        // silence : c'était l'autre chemin du « rien ne se passe » — l'écran disait « À L'ÉCOUTE »
        // et le journal n'en disait pas un mot. On le note, sans mentir sur la suite.
        if (sessionActive) {
            VoiceDiagnostics.note(
                "voice: départ ignoré — une session est déjà ouverte " +
                    "(trames reçues=$frameCount, moteur a répondu=$answered)",
            )
            return
        }
        if (fresh) {
            consecutiveErrors = 0
            // Une demande neuve rouvre le doute : c'est la seule fois où « pas de réponse » se
            // juge sur cette écoute-là.
            answered = false
        }
        if (!isAvailable) {
            VoiceDiagnostics.note("voice: aucun moteur de reconnaissance sur cet appareil (isRecognitionAvailable=false)")
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
        frameCount = 0
        VoiceDiagnostics.note(
            "voice: moteur démarré (langue=$localeTag, " +
                (if (preferOffline) "hors ligne" else "en ligne") + ")",
        )
        _state.update {
            it.copy(
                isListening = true,
                isFinal = false,
                transcript = "",
                problem = null,
                failed = false,
                // Une décision d'arrêt appartient à la session qui vient de mourir : un départ
                // neuf n'en traîne aucune.
                stopDecision = null,
            )
        }
        runCatching { engine.startListening(intent()) }
            .onFailure { error ->
                sessionActive = false
                needsFreshRecognizer = true
                VoiceDiagnostics.note("voice: départ refusé (${error.javaClass.simpleName}: ${error.message})")
                _state.update { it.copy(problem = error.message, isListening = false) }
            }
    }

    /** Arrête d'écouter sans jeter ce qui a été entendu. */
    override fun stop() {
        main.post {
            runCatching { recognizer?.stopListening() }
            _state.update { it.copy(isListening = false, level = 0f) }
            // Si la session ne rend pas la main (silence, refus du moteur), on libère la place.
            main.postDelayed({ sessionActive = false }, RESTART_DELAY_MS * 2)
        }
    }

    /** Arrête, jette la phrase en cours et repart à zéro. */
    override fun cancel() {
        main.post {
            runCatching { recognizer?.cancel() }
            _state.update { SpeechState() }
            main.postDelayed({ sessionActive = false }, RESTART_DELAY_MS * 2)
        }
    }

    override fun release() {
        main.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
            sessionActive = false
            _state.update { SpeechState() }
        }
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
                    // Le premier rappel : le moteur a bien pris la session. C'est la preuve que
                    // « À L'ÉCOUTE » n'est pas un mensonge.
                    answered = true
                    _state.update { it.copy(isListening = true, problem = null) }
                }

                override fun onBeginningOfSpeech() {
                    answered = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // `onRmsChanged` arrive vers -2…12 dB : ramené à 0…1 pour l'orbe.
                    val level = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
                    answered = true
                    frameCount += 1
                    _state.update { it.copy(level = level, rmsDb = rmsdB) }
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    answered = true
                    _state.update { it.copy(isListening = false, level = 0f) }
                }

                override fun onError(error: Int) {
                    answered = true
                    // Un silence (fin de session normale) n'est pas un échec : il ne compte pas.
                    val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    consecutiveErrors = if (silence) 0 else consecutiveErrors + 1
                    // **La décision, code par code** (`engineStopDecision`) : reprendre, basculer
                    // en ligne, ou s'arrêter en le disant. Elle est prise ici, une fois, et la
                    // session ne fait que la suivre.
                    val decision = engineStopDecision(
                        error = error,
                        preferOffline = preferOffline,
                        attempts = consecutiveErrors,
                    )
                    if (decision == EngineStopDecision.GO_ONLINE) {
                        // Le moteur hors ligne ne sait pas cette langue. La bascule n'est **pas**
                        // un échec : le compteur repart à zéro pour que l'essai en ligne ait tout
                        // son budget, et rien n'est redemandé à la personne. La ligne dit la
                        // bascule en clair — c'est celle qu'on lui demande de nous transmettre.
                        fellBackOnline = true
                        consecutiveErrors = 0
                        VoiceDiagnostics.note(
                            "voice: bascule hors ligne → en ligne (code=$error ${
                                errorName(error)
                            }) : le moteur hors ligne ne sert pas la langue=$localeTag, " +
                                "l'essai suivant se fait en ligne",
                        )
                    }
                    VoiceDiagnostics.note(
                        "voice: erreur du moteur code=$error (${
                            errorName(error)
                        }), trames reçues=$frameCount, décision=${decision.name}",
                    )
                    sessionActive = false
                    // Une session qui vient d'échouer repartira d'un moteur neuf.
                    needsFreshRecognizer = !silence
                    // **Le message suit la décision** (`engineMessage`) : rien à dire pour une fin
                    // de session normale, une reprise espacée ou une bascule en ligne — elles
                    // agissent ; un arrêt définitif porte la cause en toutes lettres, sans jamais
                    // promettre une reprise qui n'aura pas lieu.
                    val message = engineMessage(error, decision)
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
                            stopDecision = decision,
                            problem = message,
                        )
                    }
                }

                override fun onResults(results: Bundle?) {
                    answered = true
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
                            stopDecision = null,
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

    /**
     * Le nom de l'erreur du moteur, pour le journal : un code seul (« 5 ») n'aide pas la personne à
     * nous renseigner, un nom si. Les mots reconnus, eux, ne sont **jamais** journalisés.
     */
    private fun errorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "no-match"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech-timeout"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "server-disconnected"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network-timeout"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recogniser-busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient-permissions"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "too-many-requests"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language-not-supported"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language-unavailable"
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "cannot-check-support"
        else -> "other"
    }

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
