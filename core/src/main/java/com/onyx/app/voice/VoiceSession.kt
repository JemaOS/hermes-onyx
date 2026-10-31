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
 * Le message d'un échec du moteur **sans code connu** : la clé que le dictionnaire traduit déjà
 * (« Le moteur de reconnaissance s'est arrêté et l'écoute est coupée. »). Jamais un code brut seul.
 */
private const val ENGINE_STOPPED_KEY =
    "The recogniser stopped and listening is off. Tap the orb to listen again."

/**
 * Délai avant de rouvrir le micro pendant que l'app parle : la sortie audio doit d'abord prendre
 * le focus, sinon le moteur vocal refuse de s'ouvrir (`ERROR_CLIENT`, code 5).
 */
private const val MIC_REOPEN_DELAY_MS = 900L

/**
 * Délai avant de rouvrir le micro **après la fin** d'une lecture (le mode mains-libres qui
 * reprend). Plus long que le délai de libération du moteur (500 ms) : la queue de la voix doit
 * s'éteindre, sinon ses derniers mots rentrent par le micro.
 */
// Le micro se rouvre juste après la fin de la lecture : 550 ms se sentaient comme un trou, et
// on perdait le début de la phrase suivante (« pas fluide »).
private const val MIC_AFTER_REPLY_DELAY_MS = 180L

/**
 * Délai avant de rouvrir l'écoute après une **bascule hors ligne → en ligne** : le moteur vient de
 * fermer sa session refusée et doit rendre le micro. Court à dessein — la personne a parlé, rien ne
 * doit se perdre, et cette bascule ne lui est pas redemandée.
 */
private const val OFFLINE_FALLBACK_DELAY_MS = 250L

/**
 * Combien de temps le moteur a pour sortir des mots après qu'une voix a été entendue par-dessus la
 * réponse. Port de `VoiceSession.confirmWindow` (1,5 s) : c'est le temps de reconnaître ce qui a
 * été dit, pas le temps de le dire.
 */
private const val CONFIRM_WINDOW_MS = 1500L

/** Le pas de la surveillance du silence (endpointing). */
private const val ENDPOINT_POLL_MS = 120L

/**
 * La durée que représente une mesure de niveau : `onRmsChanged` en livre une dizaine par seconde,
 * c'est ce pas que `BargeInDetector.feed` additionne.
 */
private const val LEVEL_STEP_SECONDS = 0.1


/** Où en est le mode vocal : au repos, à l'écoute, en train de réfléchir, ou en train de parler. */
enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * Le mode vocal : on parle, l'agent répond, et si le mode mains-libres est allumé il se remet à
 * écouter tout seul.
 *
 * Miroir de la session vocale de la référence, en plus simple : l'écoute passe par un reconnaisseur
 * local ([SpeechInput]), la lecture par la voix du système ([SpeechReply]), et l'interruption
 * (« barge-in ») consiste à couper la lecture dès que le micro entend à nouveau quelque chose.
 *
 * **Les quatre dépendances dures sont désormais derrière des interfaces** (`SpeechInput`,
 * `SpeechReply`, `EarRouteController`, `FaceDownSensing`). C'est ce découpage qui rend la boucle
 * complète rejouable **sans micro** — moteur de test alimenté par un WAV, lecteur de test, sortie
 * factice — donc éprouvable sur JVM et pilotable depuis un émulateur. Sur un vrai téléphone, les
 * quatre implémentations sont celles d'Android et rien ne change.
 */
class VoiceSession(
    /** L'écoute : le reconnaisseur de l'appareil, ou un moteur rejoué (maquette, tests). */
    val engine: SpeechInput,
    private val reply: SpeechReply,
    private val audioRoute: EarRouteController,
    private val faceDown: FaceDownSensing,
    earRouter: ProximityEarRouter?,
    private val scope: CoroutineScope,
    localeTag: String = SpeechEngine.DEFAULT_LOCALE,
    /**
     * Le contexte Android, quand il y en a un : il ne sert qu'à ouvrir le capteur de proximité.
     * `null` sur le chemin sans Android (tests JVM, banc de QA).
     */
    private val androidContext: Context? = null,
    /** Le micro que l'app ouvre **elle-même** pour son niveau, quand le moteur n'en livre aucun. */
    private val meter: MicLevel = SilentMicLevel(),
) {

    /**
     * Le chemin Android : le reconnaisseur de l'appareil, la voix du téléphone, `AudioManager` et
     * l'accéléromètre. `speechInput`/`speechReply` permettent de **rejouer** la chaîne (moteur de
     * test) sans changer une ligne du comportement réel.
     */
    constructor(
        context: Context,
        scope: CoroutineScope,
        localeTag: String = SpeechEngine.DEFAULT_LOCALE,
        earRouter: ProximityEarRouter? = null,
        /**
         * Le moteur d'écoute : `null` prend le reconnaisseur de l'appareil. La maquette de QA y pose
         * un [ScenarioSpeechInput] — un moteur **alimenté par un fichier audio** — pour que la boucle
         * complète tourne sur un émulateur qui n'a pas de micro.
         */
        speechInput: SpeechInput? = null,
        /** Le lecteur de la réponse : `null` prend la voix du téléphone. */
        speechReply: SpeechReply? = null,
    ) : this(
        engine = speechInput ?: SpeechEngine(context, localeTag),
        reply = speechReply ?: AndroidSpeechReply(SpeechOutput(context)),
        audioRoute = AudioManagerEarRoute(context),
        faceDown = AccelerometerFaceDownDetector(context),
        earRouter = earRouter,
        scope = scope,
        localeTag = localeTag,
        androidContext = context,
        meter = AndroidMicLevelMeter(),
    )

    /**
     * Le lecteur Android réel, quand il y en a un : l'écran y règle la voix (vitesse, serveurs,
     * roster des bots). `null` quand la session tourne avec un lecteur de test — l'écran n'a alors
     * rien à régler, et s'en aperçoit.
     */
    val output: SpeechOutput? get() = when (val current = reply) {
        is AndroidSpeechReply -> current.delegate
        // Pas d'autre branche : le compilateur **prouve** que `SpeechReply` ne peut pas être un
        // `SpeechOutput` (la seconde est une classe finale qui n'implémente pas la première), et
        // depuis Kotlin 2.4 ce test-là est une **erreur** de compilation, pas un avertissement. Il
        // était là par prudence ; le lecteur de test rend donc `null`, comme avant.
        else -> null
    }

    /**
     * « Écouteur quand on lève le téléphone à l'oreille » (Réglages → Voix → `earpieceAtEar`).
     * Éteint, le capteur n'est jamais ouvert et la réponse reste au haut-parleur.
     */
    var earRoutingEnabled: Boolean = true

    /**
     * « Reconnaître hors ligne » (Réglages → Voix) : un **souhait**, **éteint par défaut** — la
     * reconnaissance se fait **en ligne**, comme l'appareil sait le faire. Allumé, le moteur tente
     * l'appareil seul ; s'il n'a pas la langue (code 13 `language-unavailable`), l'écoute bascule
     * d'elle-même **en ligne**, sans rien redemander à la personne.
     */
    var offlineRecognition: Boolean
        get() = engine.offlineWanted
        set(value) {
            engine.offlineWanted = value
        }

    /**
     * Le routeur à l'oreille : celui qu'on a passé, ou la vraie plomberie Android.
     *
     * Construit **paresseusement** : il se référence lui-même (le capteur rend la mesure par un
     * rappel, et le routeur n'existe pas encore quand on l'abonne) et il lit [earRoutingEnabled],
     * déclaré après lui.
     */
    val ear: ProximityEarRouter by lazy {
        earRouter ?: ProximityEarRouter(
            watcher = SensorProximityWatcher(
                requireNotNull(androidContext) { "une session sans Android reçoit son routeur" },
            ) { near -> ear.proximityChanged(near) },
            route = audioRoute,
            externalRoute = { audioRoute.hasExternalRoute() },
            enabled = { earRoutingEnabled },
            faceDown = { faceDown.isFaceDown() },
        )
    }

    /**
     * **Le niveau affiché**, celui qui fait bouger les points : le plus fort du niveau du moteur et
     * de celui du micro de l'app ([displayedLevel]).
     *
     * C'est la correction du défaut rapporté — « sept points parfaitement plats » : un moteur qui ne
     * livre rien (ou des mesures plates) n'efface plus le son que l'app, elle, contrôle.
     */
    private val _listenLevel = MutableStateFlow(0f)
    val listenLevel: StateFlow<Float> = _listenLevel.asStateFlow()

    /** La décision « le moteur ne livre rien d'exploitable : prenons notre propre micro ». */
    private val levelFusion = LevelFusion()

    /**
     * Le micro de niveau a-t-il **déjà** été tenté pour cette écoute ?
     *
     * Sans ce verrou, une ouverture ratée (`AudioRecord` non initialisé, un émulateur sans micro)
     * était retentée à **chaque** mesure : le journal se remplissait de la même ligne huit fois par
     * seconde et le plafond de 200 lignes tournait en une vingtaine de secondes — exactement ce qu'il
     * ne faut pas quand on demande à la personne de lire la ligne qui explique la panne.
     */
    private var meterAttempted = false
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

    private val _noSound = MutableStateFlow(false)
    /**
     * Vrai quand on écoute **sans rien recevoir** depuis [NO_SOUND_MS] (le niveau est resté sous le
     * plancher de bruit) : l'écran doit le dire au lieu de rester sur « À L'ÉCOUTE » sans un mot —
     * c'est le symptôme rapporté (« rien ne se passe quand je parle »). Retombe de lui-même dès
     * qu'un son est entendu.
     */
    val noSound: StateFlow<Boolean> = _noSound.asStateFlow()

    private val _failure = MutableStateFlow<String?>(null)
    /**
     * Le message du dernier échec **définitif** du moteur (après la reprise unique), ou `null`.
     * L'écoute, elle, est déjà close : l'écran montre ce message avec « Réessayer », jamais un
     * écran qui prétend écouter après la mort du moteur.
     */
    val failure: StateFlow<String?> = _failure.asStateFlow()

    private val _trouble = MutableStateFlow(ListenTrouble.NONE)
    /**
     * Ce que l'écoute doit dire d'elle-même quand rien n'y arrive : le moteur n'a jamais répondu,
     * aucune trame du micro, ou des trames sans un mot. Décidé sur les **faits** du moteur
     * ([listenTrouble]), jamais sur le seul niveau du micro — c'est ce qui laissait l'écran muet
     * sur « À L'ÉCOUTE » alors que rien ne rentrait. Retombe à [ListenTrouble.NONE] dès qu'un mot
     * arrive, ou dès qu'on quitte l'écoute.
     */
    val trouble: StateFlow<ListenTrouble> = _trouble.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    /** Vrai quand la réponse est mise en pause, la phase restant [VoicePhase.SPEAKING]. */
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    /** Appelé avec une phrase terminée : c'est à l'écran de l'envoyer à l'agent. */
    var onQuestion: ((String) -> Unit)? = null

    /**
     * Appelé quand « stop » est dit par-dessus la réponse : le tour en cours côté serveur n'a plus
     * de raison de continuer à s'écrire (`conversation.cancel()` de la référence).
     */
    var onStoppedByWord: (() -> Unit)? = null

    /** La langue de lecture : celle des réglages, ou celle de la réponse quand on la connaît. */
    var replyLanguageTag: String = localeTag

    /** Peut-on couper la voix en reprenant la parole ? (Réglages → Voix → « Parler pendant les réponses ».) */
    var talkOver: TalkOver = TalkOver.DEFAULT

    /**
     * Le micro peut-il s'ouvrir **derrière la réponse** ? Décidé au départ de chaque lecture, à
     * partir du réglage et de la route du moment — comme la référence : jamais quand c'est éteint,
     * avec un casque seulement quand c'est demandé, partout quand c'est demandé partout. Sur le
     * haut-parleur d'un téléphone et sans le réglage, la réponse ne peut donc pas revenir par le
     * micro (c'était le chemin de l'écho).
     */
    private var micBehindReply: Boolean = false

    /** Les phrases qui terminent une conversation en mains-libres, en plus de celles de base. */
    var stopPhrases: List<String> = emptyList()

    /**
     * Faux tant qu'aucune écoute n'a reçu de son ; vrai une fois l'alerte affichée. Sert à ne pas
     * écrire la ligne de journal chaque fois que la boucle repasse.
     */
    private var lastFrameNoteAt: Long = 0L

    /**
     * L'instant du dernier son entendu (niveau au-dessus du plancher bas, ou un mot reconnu).
     *
     * Il **survit aux reprises automatiques** : un moteur Android qui termine sa session au silence
     * rouvre l'écoute, et cette reprise ne doit pas remettre à zéro le compteur d'« on n'entend
     * rien » — sinon l'alerte ne s'afficherait jamais sur un micro muet, précisément le cas signalé.
     * Il ne repart de zéro que sur un geste de la personne (ouvrir la voix, toucher l'orbe).
     */
    private var lastSoundAt: Long = 0L

    /**
     * L'instant du début de l'écoute **de la personne** : c'est l'horloge qui juge le moteur
     * (« jamais démarré », « aucune trame », « aucun mot »). Comme [lastSoundAt], elle **survit aux
     * reprises automatiques** — sinon un moteur qui termine sa session au silence toutes les
     * quelques secondes remettrait le compteur à zéro à chaque reprise, et l'écran resterait muet
     * exactement comme signalé. Elle ne repart que sur un geste (ouvrir la voix, toucher l'orbe).
     */
    private var listenStartedAt: Long = 0L

    /**
     * La dernière panne d'écoute déjà écrite au journal : le journal ne se remplit pas de la même
     * ligne toutes les 120 ms.
     */
    private var notedTrouble: ListenTrouble = ListenTrouble.NONE

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
     * Le détecteur de prise de parole : il ne lit que le **niveau** du micro pendant la réponse,
     * jamais les mots que le moteur pourrait écrire en entendant Onyx lui-même.
     */
    private var bargeIn = BargeInDetector.echoCancelled()

    /**
     * Le silence qui termine une écoute, en millisecondes (Réglages → Voix). Réglable, comme
     * demandé ; 1,4 s par défaut.
     */
    var endpointSilenceMs: Long = DEFAULT_ENDPOINT_SILENCE_MS
        set(value) {
            field = value
            endpointWatcher = EndpointWatcher(silenceMs = value)
        }

    /**
     * Le mains-libres se relance-t-il tout seul après chaque réponse ?
     *
     * **Non par défaut** : c'est ce relancement automatique qui, avec la voix de synthèse revenue
     * par le micro, enchaînait écoute → réponse → écoute sans fin. Après une réponse, Onyx se pose
     * donc à l'arrêt ([VoicePhase.IDLE]) : on retape l'orbe pour reprendre la parole. (La garde
     * anti-écho empêche la boucle même quand ceci est rallumé.)
     */
    var autoRelisten: Boolean = false

    /** La dernière réponse lue à voix haute : c'est elle qu'on ne veut pas voir revenir du micro. */
    private var lastReplyText: String = ""

    /** La dernière transcription vue, et quand elle a bougé — pour l'endpointing. */
    private var lastTranscript: String = ""
    private var lastTranscriptAt: Long = 0L
    private var firstSpeechAt: Long = 0L
    private var endpointJob: Job? = null

    /** L'endpointing en cours : recréé à chaque écoute (son plancher de bruit suit la pièce). */
    private var endpointWatcher = EndpointWatcher()

    /** Vrai pendant qu'une voix entendue par-dessus la réponse est en train d'être examinée. */
    private var holding: Boolean = false

    /** La fenêtre d'examen : passée sans mots, la réponse repart. */
    private var holdJob: Job? = null
    /** Faux pour une lecture demandée depuis le fil, qui ne doit jamais ouvrir le micro. */
    private var listenAfterSpeech = true

    init {
        engine.onFinished = { /* l'état suffit : tout est observé ci-dessous */ }
        reply.onFinished = { if (_phase.value == VoicePhase.SPEAKING) finishSpeaking() }
        reply.setLanguage(replyLanguageTag)
        // Le téléphone levé à l'oreille : la lecture prend les attributs d'un appel et sort par
        // l'écouteur ; reposé, elle revient au haut-parleur.
        ear.onAtEarChanged = { atEar -> reroute(atEar) }

        watchJob = scope.launch {
            engine.state.collect { speech ->
                when (_phase.value) {
                    VoicePhase.LISTENING -> {
                        if (speech.failed) {
                            // Le moteur a refusé la session (code 5 notamment). Ce n'est pas une
                            // phrase : **rien ne part**. Ce que la session va faire ensuite est
                            // **décidé par la politique** (`speech.stopDecision`, une décision par
                            // code — `engineStopDecision`) :
                            //  - `GO_ONLINE` : le moteur **hors ligne** n'a pas su servir la langue
                            //    (code 13 `language-unavailable` notamment) — on repart **en ligne**
                            //    tout de suite, sans rien redemander (le moteur a remis son
                            //    compteur à zéro) ;
                            //  - `RETRY` : une reprise espacée, tant qu'il en reste une ;
                            //  - `STOP` : on ferme l'écoute **tout de suite** — un code qui ne se
                            //    répare pas (permission, langue absente de l'appareil, plafond
                            //    atteint) ne se réessaie pas, et l'écran le dit au lieu d'une
                            //    éternelle « nouvelle tentative ».
                            // Le brouillon (la dictée en cours) ne survit pas à l'échec : une
                            // session refusée ne laisse rien d'affiché — c'était le « brouillon
                            // partiel resté là » des captures.
                            _heard.value = ""
                            if (speech.stopDecision == EngineStopDecision.GO_ONLINE) {
                                relistenAfterOfflineFallback()
                                return@collect
                            }
                            if (speech.stopDecision == EngineStopDecision.RETRY) {
                                if (engine.canRetry) {
                                    retryListening()
                                } else {
                                    closeAfterEngineFailure(speech)
                                }
                            } else {
                                closeAfterEngineFailure(speech)
                            }
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
                            // Une phrase identique à la précédente vient d'un état qui traîne : on
                            // ne la repose pas (c'était la boucle en mains-libres).
                            if (speech.transcript == lastAsked) return@collect
                            // Et une phrase **déjà posée** dans les 60 dernières secondes non plus :
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
                                    // Trois reprises : ce n'est plus une conversation, c'est le
                                    // moteur qui se réentend. On arrête, au lieu de boucler.
                                    replays = 0
                                    _handsFree.value = false
                                    _phase.value = VoicePhase.IDLE
                                }
                                engine.cancel()
                                if (_handsFree.value) restartListeningIfHandsFree() else _phase.value = VoicePhase.IDLE
                                return@collect
                            }
                            // **La garde anti-écho**, la même que par-dessus la lecture : la
                            // réponse qu'on vient de lire est revenue par le micro. Rien ne part,
                            // et l'écoute reprend au lieu de boucler. Le point de décision est
                            // unique (`shouldSendHeardTranscript`) : la garde vaut donc sur la
                            // transcription finale **et** sur la parole par-dessus, sans divergence.
                            if (!shouldSendHeardTranscript(speech.transcript, lastReplyText, alreadySent = false)) {
                                engine.cancel()
                                if (_handsFree.value) restartListeningIfHandsFree() else _phase.value = VoicePhase.IDLE
                                return@collect
                            }
                            ask(speech.transcript)
                        } else if (!speech.isListening && speech.isFinal) {
                            // Un silence : en mains-libres on se remet à écouter.
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

    /** La dernière phrase envoyée : sert de garde contre une relance de la même question. */
    private var lastAsked: String = ""

    /**
     * Les phrases déjà posées récemment (texte normalisé + instant). Le moteur de reconnaissance
     * peut **rejouer** une finale déjà livrée — et en mains libres, chaque rejeu reposait la même
     * question : la conversation partait en boucle sans jamais s'arrêter. Une phrase reposée dans
     * les 60 s est ignorée ; trois reprises d'affilée arrêtent le mode mains libres.
     */
    private val recentAsked = ArrayDeque<Pair<String, Long>>()
    private var replays = 0

    /** Se met à écouter, sur un geste de la personne (ouvrir la voix, toucher l'orbe). */
    fun startListening(fresh: Boolean = true) = beginListening(fresh = fresh, resetSilence = true)

    /**
     * Le vrai départ d'écoute. [resetSilence] : le compteur d'« on n'entend rien » repart de zéro —
     * vrai sur un geste de la personne, **faux** sur une reprise automatique (le moteur a terminé sa
     * session au silence : ce n'est pas une raison pour effacer une alerte que le micro est muet).
     */
    private fun beginListening(fresh: Boolean, resetSilence: Boolean) {
        _heard.value = ""
        lastAsked = ""
        // Le jugement « le moteur a-t-il un niveau exploitable ? » se rouvre à chaque écoute, et le
        // micro de l'app se referme : on ne le rouvrira que si le moteur retombe muet.
        levelFusion.reset()
        meter.stop()
        meterAttempted = false
        _listenLevel.value = 0f
        _isPaused.value = false
        _phase.value = VoicePhase.LISTENING
        // Un échec d'hier ne survit pas à une écoute neuve.
        _failure.value = null
        if (resetSilence) {
            _noSound.value = false
            lastSoundAt = System.currentTimeMillis()
            // Le geste rouvre aussi le jugement sur le moteur : « jamais démarré / aucune trame /
            // aucun mot » se compte à partir d'ici, et la panne déjà annoncée tombe.
            listenStartedAt = lastSoundAt
            _trouble.value = ListenTrouble.NONE
            notedTrouble = ListenTrouble.NONE
        }
        lastTranscript = ""
        firstSpeechAt = 0L
        lastTranscriptAt = System.currentTimeMillis()
        lastFrameNoteAt = 0L
        // Une écoute qui n'est pas partie d'un geste (reprise) garde son horloge ; mais la toute
        // première doit toujours en avoir une, sinon « depuis 1,7e12 ms » dirait n'importe quoi.
        if (listenStartedAt == 0L) listenStartedAt = System.currentTimeMillis()
        engine.start(fresh)
        VoiceDiagnostics.note("voice: écoute demandée (${if (fresh) "neuve" else "reprise"})")
        refreshEndpointWatch()
    }

    /**
     * La **seule** reprise après un refus du moteur : une, espacée, puis on s'arrête.
     *
     * Elle passe `fresh = false` pour que le compteur de refus du moteur ne reparte pas de zéro —
     * sinon un moteur durablement cassé se relancerait indéfiniment (le défaut signalé : l'app
     * « redémarre le moteur en boucle »).
     */
    private fun retryListening() {
        retryJob?.cancel()
        retryJob = scope.launch {
            // L'attente **s'allonge** avec les refus (1,5 s, puis 3 s) : un moteur qui refuse en
            // rafale a besoin qu'on lui rende le micro avant de le reprendre.
            delay(engineRetryDelayMs(engine.attempts))
            if (_phase.value == VoicePhase.LISTENING) startListening(fresh = false)
        }
    }

    /**
     * Ferme l'écoute sur un échec **définitif** du moteur : la phase repasse au repos et le
     * message est porté à l'écran, avec « Réessayer » — un écran qui prétend écouter après la mort
     * du moteur est exactement le « rien ne se passe » signalé. C'est la fin de la politique de
     * [engineStopDecision] (`STOP`), et le seul chemin qui coupe l'écoute sans un geste de la
     * personne.
     */
    private fun closeAfterEngineFailure(speech: SpeechState) {
        _failure.value = speech.problem ?: ENGINE_STOPPED_KEY
        VoiceDiagnostics.note(
            "voice: écoute close après échec définitif du moteur " +
                "(${speech.problem ?: ENGINE_STOPPED_KEY})",
        )
        _phase.value = VoicePhase.IDLE
    }

    /**
     * La **bascule hors ligne → en ligne** : le moteur hors ligne a avoué ne pas servir la langue
     * (code 13 `language-unavailable`, 12, 11, 5…). L'écoute repart **en ligne** dans la foulée,
     * sans rien redemander à la personne — c'est la règle : la reconnaissance en ligne est le
     * défaut, le hors ligne un souhait explicite.
     *
     * Elle passe `fresh = false` (l'horloge de l'écoute et l'alerte « on n'entend rien » ne
     * repartent pas : la personne, elle, n'a rien fait), et le moteur a déjà remis son compteur de
     * refus à zéro pour que l'essai en ligne ait tout son budget. Un échec **en ligne** ensuite
     * suit le chemin ordinaire — reprise espacée bornée, puis arrêt dit.
     */
    private fun relistenAfterOfflineFallback() {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(OFFLINE_FALLBACK_DELAY_MS)
            if (_phase.value == VoicePhase.LISTENING) startListening(fresh = false)
        }
    }

    /**
     * L'endpointing : l'écoute se termine d'elle-même quand la transcription est stable **et** que
     * la pièce est calme ([EndpointWatcher]) — puis la phrase part, une fois. Sans lui, une écoute
     * ne se fermait qu'au silence du moteur, et le tour s'enchaînait sans temps d'arrêt ; avec un
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
                // Le niveau du moment : c'est lui qui dit que la personne parle encore, même quand
                // le moteur n'écrit plus rien.
                val engineSpeech = engine.state.value
                // Le moteur livre-t-il un niveau exploitable ? Sinon — et **seulement** alors —
                // l'app ouvre son propre micro : c'est le signal qu'elle contrôle, et c'est lui
                // qui fait bouger les points quand le moteur n'en donne aucun.
                levelFusion.observe(engineSpeech.level, engine.frameCount)
                if (levelFusion.wantsMeter && !meterAttempted && !meter.isAvailable) {
                    // Une seule tentative par écoute : un échec ne se répète pas.
                    meterAttempted = true
                    meter.start()
                }
                val level = displayedLevel(engineSpeech.level, meter.level.value)
                _listenLevel.value = level
                // Les trames « entendues » : celles du moteur **plus** celles du micro de l'app.
                // C'est sur ce total que se juge « le moteur tourne sans rien recevoir » — sans
                // lui, un moteur muet gardait l'alerte éteinte.
                val frames = engine.frameCount + meter.frames
                endpointWatcher.level(level, ENDPOINT_POLL_MS / 1000.0, now)
                // Un son est « entendu » dès que le niveau franchit le plancher bas, ou dès qu'un
                // mot est reconnu. Ce repère **survit aux reprises automatiques** : c'est ce qui
                // permet de dire « on n'entend rien » même quand le moteur rouvre sa session au
                // silence toutes les quelques secondes (le cas d'un micro muet).
                if (level > ENDPOINT_MINIMUM_LEVEL || _heard.value.isNotBlank()) lastSoundAt = now
                // « On n'entend rien » : aucun son depuis NO_SOUND_MS. L'alerte est posée **une
                // fois**, et elle retombe d'elle-même dès qu'un son est entendu — jamais un bandeau
                // collé.
                val noSound = noSoundWhileListening(_phase.value, now - lastSoundAt, _heard.value)
                if (noSound != _noSound.value) {
                    _noSound.value = noSound
                    if (noSound) {
                        VoiceDiagnostics.note(silentListeningLine(frames, engine.answered))
                    }
                }
                // Les trames reçues : un relevé par seconde, jamais les mots reconnus.
                if (now - lastFrameNoteAt >= 1_000L) {
                    lastFrameNoteAt = now
                    VoiceDiagnostics.note(
                        "voice: écoute — $frames trames " +
                            "(moteur=${engine.frameCount}, micro=${meter.frames}), niveau $level",
                    )
                }
                // **La panne d'écoute, jugée sur les faits du moteur** : a-t-il seulement répondu,
                // a-t-il reçu des trames, a-t-il rendu un mot ? Le niveau du micro ne suffit pas —
                // un moteur qui livre des niveaux sans jamais rendre un mot gardait l'alerte
                // éteinte, et l'écran restait muet sur « À L'ÉCOUTE ».
                val trouble = listenTrouble(
                    phase = _phase.value,
                    heard = _heard.value,
                    frames = frames,
                    engineStarted = engine.answered,
                    listeningForMs = now - listenStartedAt,
                )
                when (trouble) {
                    // Aucune trame : le message déjà connu de l'alerte « on n'entend rien » s'en
                    // charge (le niveau est resté au plancher) — rien à doubler ici.
                    ListenTrouble.NO_FRAMES -> if (_trouble.value != ListenTrouble.NONE) {
                        _trouble.value = ListenTrouble.NONE
                    }
                    else -> if (_trouble.value != trouble) _trouble.value = trouble
                }
                if (trouble != notedTrouble) {
                    notedTrouble = trouble
                    listenTroubleLogLine(
                        trouble = trouble,
                        seconds = (now - listenStartedAt) / 1000,
                        frames = frames,
                        started = engine.answered,
                    )?.let { line ->
                        VoiceDiagnostics.note(
                            if (trouble == ListenTrouble.ENGINE_NEVER_STARTED) {
                                "$line — ${engineStartReason(
                                    recognitionAvailable = engine.isAvailable,
                                    preferOffline = engine.prefersOffline,
                                    localeTag = engine.requestedLocale,
                                    frames = engine.frameCount,
                                )}"
                            } else {
                                line
                            },
                        )
                    }
                }
                // Le plafond de 45 s ne compte **qu'à partir du premier mot** : tant qu'aucune
                // transcription n'est arrivée, l'horloge vaut zéro — sinon « depuis 1,7e12 ms »
                // terminait l'écoute 120 ms après son ouverture (la personne n'avait pas le temps
                // de parler : c'est le « il ne capte pas ma voix »).
                val sinceFirstSpeech = if (firstSpeechAt == 0L) 0L else now - firstSpeechAt
                if (endpointWatcher.shouldEnd(_heard.value, now - lastTranscriptAt, sinceFirstSpeech, now)) {
                    // Le moteur rend la phrase entendue ; la branche LISTENING l'enverra une fois.
                    engine.stop()
                    lastTranscriptAt = now
                    return@launch
                }
            }
        }
    }

    /** Arrête tout : le micro, la lecture, et l'état. */
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
        meter.stop()
        reply.stop()
        _phase.value = VoicePhase.IDLE
        _isPaused.value = false
        _heard.value = ""
        _spoken.value = ""
        _listenLevel.value = 0f
        _noSound.value = false
        _trouble.value = ListenTrouble.NONE
    }

    /** La voix qui se lit, de 0 à 1 : ce qui fait battre la bouche du visage qui parle. */
    val replyLevel: StateFlow<Float> get() = reply.replyLevel

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
        _listenLevel.value = 0f
        _noSound.value = false
        _trouble.value = ListenTrouble.NONE
        holding = false
        holdJob?.cancel()
        holdJob = null
        // Le micro d'un casque entend la pièce à son vrai niveau, celui du téléphone est filtré
        // par l'annulation d'écho : les deux seuils de la référence, choisis selon la route du moment.
        // Le seuil d'interruption est celui qu'on demande : BARGE_IN_HOLD_MS de parole tenue, pas
        // un dixième de seconde de bruit.
        val holdSeconds = BARGE_IN_HOLD_MS / 1000.0
        bargeIn = if (audioRoute.hasExternalRoute()) {
            BargeInDetector.headset(hold = holdSeconds)
        } else {
            BargeInDetector.echoCancelled(hold = holdSeconds)
        }
        lastReplyText = clean
        // Le micro derrière **cette** réponse : le réglage « Parler pendant les réponses », confronté
        // à la route du moment (un casque ne s'entend pas lui-même, un haut-parleur si).
        micBehindReply = talkOverAllowsMic(talkOver, audioRoute.hasExternalRoute())
        // **Le micro est fermé avant que la voix ne démarre**, et il le reste pendant toute la
        // lecture : c'est le demi-duplex. Sans cette fermeture explicite, la session d'écoute
        // encore ouverte capturait les premiers mots de la réponse — le texte lu arrivait au
        // moteur avant que le micro ne se referme. `cancel()` jette aussi ce qui traînait, pour
        // qu'aucune transcription périmée ne puisse être envoyée au tour suivant.
        engine.cancel()
        // Le téléphone levé à l'oreille : la réponse s'y joue comme un appel (`setEarRouting`).
        // L'accéléromètre ne tourne que le temps d'une lecture : il sert au « posé face contre la
        // table », qui n'est pas une oreille.
        faceDown.start()
        ear.setWanted(true)
        reply.setLanguage(replyLanguageTag)
        // `speaker` : la réplique d'un bot sort de **sa** voix (`BotVoices`), pas de celle d'Onyx.
        reply.speak(clean, replyLanguageTag, speaker)
        // Le micro reste ouvert pendant la lecture : c'est ce qui rend l'interruption possible.
        // Mais pas dans l'instant où la voix démarre — la sortie audio prend le focus et le
        // moteur vocal refuse alors de s'ouvrir (code 5). D'où ce court décalage.
        refreshEndpointWatch()
        reopenMicJob = scope.launch {
            delay(MIC_REOPEN_DELAY_MS)
            // **Demi-duplex par défaut** : le micro ne se rouvre pendant la lecture que si le
            // barge-in est demandé (Réglages → Voix). Sinon la voix de synthèse ne peut pas
            // revenir par le micro, et c'est ce qui casse la boucle.
            if (shouldReopenMicForSpeech(_phase.value, engine.state.value.isListening, listenAfterSpeech, micBehindReply)) {
                engine.start(true)
            }
        }
    }

    /** Pause/Play de `VoiceView.controls`, sans changer de phase ni perdre le bot qui parle. */
    fun pauseSpeaking() {
        if (_phase.value != VoicePhase.SPEAKING || _isPaused.value) return
        _isPaused.value = true
        reply.pause()
    }

    fun resumeSpeaking() {
        if (_phase.value != VoicePhase.SPEAKING || !_isPaused.value) return
        _isPaused.value = false
        if (!reply.resume()) finishSpeaking()
    }

    /**
     * Appelé par l'écran quand le tour est fini : on parle, puis on réécoute.
     *
     * `speaker` — le **profil du bot de la conversation ouverte** : la réponse se lit de **sa** voix
     * (`BotVoices`), comme la relance du modèle (`replayLastReply()`, qui passe
     * `conversation.bot?.profile`). Sans lui, une réponse de bot se lirait de la voix d'Onyx — le
     * réglage « Voix » d'un bot n'aurait alors aucun effet en mode vocal.
     */
    fun finishTurn(replyText: String?, speaker: String? = null) {
        if (replyText.isNullOrBlank()) {
            finishSpeaking()
        } else {
            speak(replyText, speaker = speaker)
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
        meter.release()
        engine.release()
        reply.shutdown()
    }

    /**
     * Pendant la lecture : le **niveau** du micro dit qu'une voix s'élève (`BargeInDetector`), la
     * réponse est alors suspendue le temps que le moteur dise si c'était des mots — la
     * `confirmWindow` de la référence — et ce qu'il en sort décide de la suite.
     *
     * Le niveau d'abord, exprès : le moteur qui entend Onyx parler n'a pas à écrire ses propres
     * mots, et un texte reconnu tant que rien n'a été entendu par-dessus ne compte donc pas.
     */
    private fun handleSpeaking(speech: SpeechState) {
        // Sans micro derrière la réponse (demi-duplex : réglage éteint, ou haut-parleur quand le
        // réglage ne vaut que pour un casque), il n'y a rien à surveiller : le niveau du micro ne
        // peut pas être celui de quelqu'un qui parle par-dessus.
        if (!micBehindReply) return
        val text = speech.transcript
        // Garde anti-boucle : la phrase déjà envoyée ne repart jamais telle quelle.
        val fresh = text.isNotBlank() && text != lastAsked

        if (holding) {
            if (!fresh) return
            // (« Okay » peut encore devenir « okay, stop » : le bruit d'écoute attend la fenêtre.)
            when (bargeInOutcomeFor(text, stopPhrases)) {
                BargeInOutcome.NONE, BargeInOutcome.BACKCHANNEL -> Unit
                BargeInOutcome.STOP -> stoppedByWord()
                // La garde anti-écho vaut aussi ici — le même point de décision que la
                // transcription finale (`shouldSendHeardTranscript`) : ce que le micro rend de la
                // réponse en cours ne coupe pas la réponse.
                BargeInOutcome.WORDS ->
                    if (shouldSendHeardTranscript(text, _spoken.value, alreadySent = false)) {
                        interruptedBy(text, speech.isFinal)
                    }
            }
            return
        }

        if (fresh && bargeInOutcomeFor(text, stopPhrases) == BargeInOutcome.STOP) {
            // Un ordre d'arrêt n'attend pas : le niveau, lui, a besoin de quelques mesures.
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
     * Le synthétiseur Android ne se réachemine pas au milieu d'un énoncé : la phrase reprend donc
     * là où elle en est, avec la sortie du moment. Un lecteur distant, lui, repart du début de son
     * audio — le seul moyen qu'offre un `MediaPlayer`.
     */
    private fun reroute(atEar: Boolean) {
        val changed = reply.earRouting != atEar
        reply.setEarRouting(atEar)
        if (!changed) return
        if (_phase.value != VoicePhase.SPEAKING || _isPaused.value || holding) return
        if (_spoken.value.isEmpty() || !reply.isSpeaking.value) return
        reply.pause()
        reply.resume()
    }

    /** Une voix au-dessus de la réponse : on suspend la lecture et on écoute s'il y a des mots. */
    private fun holdToHearWords() {
        holding = true
        reply.pause()
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(CONFIRM_WINDOW_MS)
            if (holding) carryOn()
        }
    }

    /** C'était des mots : la réponse s'arrête ici, comme si on avait tapé l'orbe. */
    private fun interruptedBy(text: String, isFinal: Boolean) {
        holdJob?.cancel()
        holdJob = null
        holding = false
        reply.stop()
        _heard.value = text
        if (isFinal) {
            ask(text)
        } else {
            // Le moteur écoute déjà : la fin de la phrase passera par la branche LISTENING.
            _phase.value = VoicePhase.LISTENING
        }
    }

    /** Ni mots ni bruit d'écoute (une toux, une porte) : la réponse repart où elle s'est arrêtée. */
    private fun carryOn() {
        holdJob = null
        holding = false
        if (_phase.value != VoicePhase.SPEAKING) return
        bargeIn.rearm()
        if (!_isPaused.value) reply.resume()
    }

    /** Un ordre d'arrêt par-dessus la réponse : elle se tait, et le mains-libres avec elle. */
    private fun stoppedByWord() {
        holdJob?.cancel()
        holdJob = null
        holding = false
        _handsFree.value = false
        stop()
        onStoppedByWord?.invoke()
    }

    private fun ask(question: String) {
        // `cancel()` et non `stop()` : la phrase reconnue doit être **effacée** de l'état du
        // moteur. Avec `stop()`, `isFinal` restait vrai et le transcript intact ; pendant la
        // réponse, la branche barge-in relisait cet état périmé et reposait la **même** question,
        // en boucle, à chaque tour. `cancel()` vide l'état et ferme le micro (il se rouvrira
        // pendant la lecture, ce qui suffit au barge-in).
        engine.cancel()
        // On quitte l'écoute : l'alerte « on n'entend rien » n'a plus lieu d'être affichée, et le
        // micro de niveau n'a plus rien à mesurer.
        meter.stop()
        _listenLevel.value = 0f
        _noSound.value = false
        _trouble.value = ListenTrouble.NONE
        // Le tour est en cours **tout de suite** : sans ça, le moteur rendait une finale avec
        // `isListening = false` juste après l'envoi, et la boucle en mains libres rouvrait le micro
        // pendant que l'agent réfléchissait — il s'auto-écoutait (« ça s'écoute même quand elle
        // répond »).
        _phase.value = VoicePhase.THINKING
        lastAsked = question
        // On garde la trace des phrases posées : une finale rejouée par le moteur ne relance pas
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
        // Retour à PRÊT : le brouillon d'écoute n'a plus lieu d'être affiché. Ce qui a été dit est
        // dans le fil ; ce qui n'a été qu'entendu à moitié ne doit pas rester sous les yeux.
        _heard.value = ""
        _listenLevel.value = 0f
        endpointJob?.cancel()
        endpointJob = null
        // Plus rien ne se lit : le capteur se referme et la sortie redevient celle du téléphone.
        ear.setWanted(false)
        faceDown.stop()
        if (shouldRestartHandsFreeAfterSpeech(_handsFree.value, listenAfterSpeech, autoRelisten)) {
            // Après une réponse, l'écoute qui repart est neuve : l'alerte « on n'entend rien »
            // repart de zéro (la personne vient d'entendre l'agent, elle va peut-être parler).
            restartListeningIfHandsFree(resetSilence = true)
        }
    }

    private fun restartListeningIfHandsFree(resetSilence: Boolean = false) {
        if (!shouldReopenListeningAfterSessionEnd(_handsFree.value, _phase.value)) {
            // Hors mains libres **et au repos**, une fin de session rend la main au repos, sans
            // message (fin d'une lecture : c'est [finishSpeaking] qui décide de rouvrir).
            if (!_handsFree.value) _phase.value = VoicePhase.IDLE
            return
        }
        scope.launch {
            // La réouverture après la fin de la lecture attend un délai : le moteur se libère
            // (`ERROR_CLIENT` sinon), et la queue de la voix de synthèse s'éteint — rouvrir dans
            // l'instant ferait rentrer l'écho des derniers mots par le micro.
            delay(MIC_AFTER_REPLY_DELAY_MS)
            if (shouldReopenListeningAfterSessionEnd(_handsFree.value, _phase.value)) {
                beginListening(fresh = false, resetSilence = resetSilence)
            }
        }
    }
}

/**
 * Faut-il rouvrir l'écoute tout seul après une fin de session ?
 *
 * Un moteur de reconnaissance Android termine sa session **tout seul** — au silence, ou à la durée
 * maximale — sans que ce soit une panne : ce n'est pas une raison pour couper l'écoute.
 *
 *  - **Pendant l'écoute** ([VoicePhase.LISTENING]) : l'écoute rouvre **toujours**, en mains libres
 *    ou non. L'écoute ne se coupe que sur un geste (■), sur les mots d'invocation d'arrêt, ou sur
 *    un échec **définitif** après plafond — jamais parce que le moteur a fini sa session.
 *  - **Au repos** ([VoicePhase.IDLE], fin d'une lecture) : seulement en mains libres — sinon
 *    l'agent s'écouterait lui-même (`shouldRestartHandsFreeAfterSpeech`).
 *
 * Pendant la réflexion ou la lecture, rien ne rouvre : le micro est tenu par le tour.
 */
internal fun shouldReopenListeningAfterSessionEnd(
    handsFree: Boolean,
    phase: VoicePhase,
): Boolean = phase == VoicePhase.LISTENING || (handsFree && phase == VoicePhase.IDLE)

/**
 * Ce qu'une prise de parole par-dessus la réponse est : rien, un bruit d'écoute, un ordre d'arrêt,
 * ou des mots.
 *
 * Port de `Backchannel` + `StopWord` de la référence, tels que la session de la référence s'en sert — et
 * décision pure : aucun micro, aucune horloge, donc éprouvable sur JVM.
 */
enum class BargeInOutcome { NONE, BACKCHANNEL, STOP, WORDS }

/**
 * Classe [text] dit par-dessus la réponse.
 *
 * Un silence ne décide rien ; « mm-hm », « okay », « d'accord » ne coupent pas la réponse (c'était
 * le défaut : n'importe quel mot reconnu l'arrêtait) ; une phrase d'arrêt — « stop », ou celles de
 * la personne — arrête tout ; le reste est une vraie prise de parole.
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
 * `watchIfSpeaking` de la référence : **seulement** pendant que la réponse se lit, pas en
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

/** La phrase telle qu'elle est entendue : minuscules, sans politesses autour. */
fun normalizeStopPhrase(phrase: String): String {
    // Les apostrophes typographiques (« c’est tout ») comptent comme celles du clavier : sans elles,
    // une phrase française par défaut ne se reconnaîtrait que si le moteur écrivait le même
    // caractère — ce qui n'est jamais garanti.
    val clean = phrase.lowercase().replace(Regex("[\\p{Punct}’‘´`«»]"), " ")
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

/** Ce qui arrête toujours : les phrases **françaises par défaut**, et celles de la référence. */
val BUILT_IN_STOP_PHRASES: Set<String> = listOf(
    // — français : les phrases que l'app cite à l'écran, et celles qu'une personne dira vraiment —
    "arrête", "arrête d'écouter", "c'est tout", "ça suffit", "au revoir", "bonne nuit",
    "termine la conversation", "laisse tomber", "annule", "merci",
    // — anglais : la référence, pour qui parle anglais à Onyx —
    "stop", "stop listening", "that's all", "that's enough", "goodbye", "good night",
    "end conversation", "we're done", "never mind", "cancel", "thank you",
)
    // La comparaison se fait sur ce que le **moteur a écrit** ([normalizeStopPhrase]), ponctuation
    // retirée : sans cette forme comparable, « c'est tout » ou « that's all » n'auraient jamais pu
    // être reconnus (l'apostrophe disparaît de la transcription). On garde les deux formes : la
    // brute (rien ne change pour ce qui marchait) et la comparable (ce qui ne marchait pas).
    .flatMap { listOf(it, comparableStopPhrase(it)) }
    .filter { it.isNotEmpty() }
    .toSet()

/** La forme comparable d'une phrase : minuscules, ponctuation retirée, espaces resserrés. */
fun comparableStopPhrase(phrase: String): String =
    phrase.lowercase().replace(Regex("[\\p{Punct}’‘´`«»]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
