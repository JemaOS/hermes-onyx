package com.onyx.app.data.net

import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import kotlin.math.sqrt

/**
 * La session avec le pont « Ordinateur » : une connexion, des images, la main, des entrées.
 *
 * Elle vit **hors de l'écran**, dans le conteneur de l'application : quitter l'onglet et y revenir
 * ne coupe rien, ne rend pas la main et ne redemande pas l'image — on retrouve la session telle
 * qu'elle était, avec la dernière image déjà là. Seul un `close()` explicite (« Fermer la
 * session ») arrête tout, et le pont rend alors la main de lui-même.
 *
 * Le jeton ne sort jamais d'ici : il part dans un en-tête `Authorization`, il n'est ni journalisé,
 * ni rendu, ni affiché.
 *
 * **Le battement de cœur est applicatif, pas un ping WebSocket.** Le client envoyait auparavant un
 * ping de contrôle toutes les 20 s : quand le pong manquait, la bibliothèque coupait la session et
 * affichait son texte interne, en anglais et en millisecondes (« sent ping but didn't receive pong
 * within 20000ms (after 0 successful ping/pongs) »). Un pong n'est pas une preuve de vie fiable au
 * travers d'un tunnel — il fait la queue derrière des kilo-octets d'image — et tuer une session
 * saine sur 20 s de retard est une panne inventée. On envoie donc `{"t":"ping"}` (le pont répond
 * `{"t":"pong"}`) et on ne conclut à la mort du lien qu'après [silenceDeadlineMs] sans **aucune**
 * trame reçue.
 *
 * **Le pong ne prouve pas que l'image arrive.** C'était le second visage du défaut : la capture du
 * pont pouvait mourir (ffmpeg sorti) et le pont continuait de répondre aux pings — le lien était
 * donc « vivant » et l'écran restait figé pour toujours, sans un mot. Un pong dit que le pont est
 * là ; seul un **octet d'image** dit que l'écran bouge. Le battement regarde donc aussi l'âge de la
 * dernière image ([imageDeadlineMs]) : au-delà, on ne reste pas sur une image morte — on reconnecte
 * et on le dit.
 *
 * **Le refus du pont est lu là où il arrive.** Un serveur qui refuse tient d'abord la poignée de
 * main — il a besoin de lire l'en-tête — puis ferme avec son code (`4401` jeton, `4404` chemin,
 * `4429` trop de tentatives). OkHttp annonce cette fermeture par `onClosing`, et c'est **là** que
 * le code est lu : la cause est dite tout de suite, et rien de ce que le même socket dirait ensuite
 * ne compte. Une cause qui demande une action (recopier le jeton, corriger l'adresse) n'est
 * **jamais** rejouée toute seule.
 */
class ComputerBridgeClient(
    private var address: String,
    private var token: String,
    private val httpClient: OkHttpClient = sharedClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val heartbeatMs: Long = 15_000,
    private val silenceDeadlineMs: Long = 45_000,
    private val imageDeadlineMs: Long = 6_000,
) {

    enum class Phase {
        IDLE,
        CONNECTING,
        LIVE,

        /** La session était ouverte : on repart tout seul, l'image reste affichée, grisée. */
        RECONNECTING,
        FAILED,
    }

    /** Une image reçue, avec son rang — deux images identiques restent deux images. */
    data class Frame(val bytes: ByteArray, val seq: Long) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private val ids = AtomicInteger(1)

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _hello = MutableStateFlow<ComputerHello?>(null)
    val hello: StateFlow<ComputerHello?> = _hello.asStateFlow()

    private val _state = MutableStateFlow(ComputerState())
    val state: StateFlow<ComputerState> = _state.asStateFlow()

    private val _frame = MutableStateFlow<Frame?>(null)
    val frame: StateFlow<Frame?> = _frame.asStateFlow()

    /** Le dernier refus ou la dernière panne, en clair — `null` quand tout va bien. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** La même panne en pièces détachées : l'écran peut décider quoi proposer (recopier, réessayer). */
    private val _problem = MutableStateFlow<ComputerBridgeError?>(null)
    val problem: StateFlow<ComputerBridgeError?> = _problem.asStateFlow()

    /** La sortie de la dernière commande approuvée, quand il y en a une. */
    private val _runOutput = MutableStateFlow<String?>(null)
    val runOutput: StateFlow<String?> = _runOutput.asStateFlow()

    /** Les mesures du flux — celles qui décident de la qualité, et rien d'autre. */
    private val _stats = MutableStateFlow(ComputerStreamStats())
    val stats: StateFlow<ComputerStreamStats> = _stats.asStateFlow()

    /** Le cran de l'échelle actif, et si la liaison économe est forcée. */
    private val _ladder = MutableStateFlow(ComputerLadder(step = ComputerQuality.HEAVIEST))
    val ladder: StateFlow<ComputerLadder> = _ladder.asStateFlow()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var wanted = false

    @Volatile
    private var lastSeenAt = 0L

    /** L'âge de la dernière **image** — la seule preuve que l'écran bouge encore. */
    @Volatile
    private var lastFrameAt = 0L

    /** Le numéro par tentative : la réponse d'une socket périmée ne touche plus l'état. */
    private var generation = 0

    /** La tentative dont la fermeture a déjà été expliquée — la cause n'est dite qu'une fois. */
    private var handledGen = -1

    private var attempt = 0
    private var beat: Job? = null
    private var meter: Job? = null
    private var frames = 0L

    /** Ce que le décodeur a réellement affiché : `reçues - décodées` = jetées (en retard). */
    private var decoded = 0L

    /** Les mesures brutes, entre deux battements de la jauge. */
    private var windowFrames = 0
    private var windowBytes = 0L
    private var liveSinceNanos = 0L
    private var lastMeterAt = 0L

    /** Les derniers aller-retours, pour la latence et la **gigue**. */
    private val pings = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val rtts = ArrayDeque<Long>()

    /** Le gouverneur de qualité : un seul décideur, nourri par les mesures ci-dessus. */
    private val governor = ComputerQualityGovernor()

    /** La largeur **voulue** par l'écran (son volet), en pixels réels. */
    @Volatile
    private var paneWidthPx = 0

    /** Ouvre la session si besoin ; ne fait rien si elle est déjà ouverte. */
    fun connect() {
        wanted = true
        if (_phase.value == Phase.LIVE || _phase.value == Phase.CONNECTING) return
        // Un lancement à la main repart à zéro : la temporisation d'une panne ancienne ne doit pas
        // retarder l'essai que l'utilisateur vient de demander.
        attempt = 0
        open()
    }

    /**
     * Réessaie tout de suite, sans attendre la temporisation : le geste de l'utilisateur après
     * avoir corrigé quelque chose (le jeton, l'adresse).
     */
    fun retry() {
        wanted = true
        attempt = 0
        if (_phase.value == Phase.LIVE || _phase.value == Phase.CONNECTING) return
        open()
    }

    private fun open() {
        val url = ComputerAddress.websocketUrl(address)
        if (url == null) {
            fail(ComputerBridgeError.BadAddress, retryLater = false)
            return
        }
        val credential = ComputerToken.clean(token)
        if (credential.isEmpty()) {
            // Le texte montré vient de la cause décrite côté écran ; l'objet structuré, lui, dit la
            // même chose en pièces détachées.
            fail(
                ComputerBridgeError.TokenMissing,
                retryLater = false,
                notice = OnyxLocalization["The bridge token is missing."],
            )
            return
        }
        if (ComputerToken.looksMalformed(credential)) {
            fail(ComputerBridgeError.TokenMalformed, retryLater = false)
            return
        }
        if (ComputerToken.obviouslyTooShort(credential)) {
            fail(ComputerBridgeError.TokenCut(credential.length, 0), retryLater = false)
            return
        }
        _phase.value = if (_frame.value != null) Phase.RECONNECTING else Phase.CONNECTING
        generation += 1
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $credential")
            .build()
        socket = httpClient.newWebSocket(request, Listener(generation))
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {

        private fun current(): Boolean = gen == generation

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current()) return
            attempt = 0
            val now = System.nanoTime()
            lastSeenAt = now
            // **Une image fraîche est due.** On repart de zéro pour l'âge de l'image : la nouvelle
            // session n'a rien envoyé encore, et l'ancienne image ne compte pas comme preuve.
            lastFrameAt = now
            liveSinceNanos = now
            lastMeterAt = now
            windowFrames = 0
            windowBytes = 0L
            _phase.value = Phase.LIVE
            _problem.value = null
            _notice.value = null
            startHeartbeat(webSocket)
            startMeter()
            // **Redemander le flux, même la même taille.** C'est le cœur du défaut : après une
            // coupure, l'app se reconnectait sans rien redemander, et le pont s'abonnait donc à un
            // flux mort — image figée, pour toujours. On réaffirme donc la demande à **chaque**
            // ouverture ; le pont, de son côté, relance sa capture si elle est morte.
            reassertStream()
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            if (!current()) return
            val data = bytes.toByteArray()
            if (data.isEmpty()) return
            val now = System.nanoTime()
            lastSeenAt = now
            lastFrameAt = now
            frames += 1
            windowFrames += 1
            windowBytes += data.size
            _frame.value = Frame(data, frames)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!current()) return
            lastSeenAt = System.nanoTime()
            when (val event = ComputerWire.parse(text)) {
                is ComputerEvent.Hello -> {
                    _hello.value = event.hello
                    _notice.value = null
                    _problem.value = null
                }
                is ComputerEvent.State -> _state.value = event.state
                is ComputerEvent.Ack -> {
                    // Un accusé vaut pour toutes les demandes : la demande de confort n'est plus en
                    // attente, et la dernière panne affichée n'a plus lieu d'être.
                    event.id?.let { optional.remove(it) }
                    _notice.value = null
                }
                is ComputerEvent.Denied -> {
                    // Un pont plus ancien ne connaît pas la taille d'image ni la cadence : il refuse
                    // ces deux demandes-là. Ce n'est pas une panne de l'écran, donc on l'avale — le
                    // reste (un appui, une frappe) garde son refus, lui, affiché.
                    val comfort = event.id != null && optional.remove(event.id)
                    if (!comfort) _notice.value = ComputerWire.denialMessage(event.reason)
                }
                is ComputerEvent.RunResult -> {
                    _runOutput.value = event.output
                    if (!event.ok) _notice.value = OnyxLocalization["The command didn't finish."]
                }
                ComputerEvent.Pong -> notePong(text)
                ComputerEvent.Ignored -> Unit
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (!current()) return
            // Le pont annonce **pourquoi** il ferme : c'est la cause exacte, dite tout de suite.
            reportClose(code, reason)
            // **On referme de notre côté.** OkHttp n'annonce `onClosed` — et donc la reprise —
            // que quand *les deux* pairs ont signalé la fermeture : sans cet écho, une coupure
            // venue du pont (redémarrage, capture relancée, tunnel qui lâche) ne rendait jamais
            // la main à la boucle de reconnexion, et la session restait morte en silence.
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!current()) return
            reportClose(code, reason)
            beat?.cancel()
            if (!wanted) return
            val error = ComputerBridgeError.fromClose(code, reason)
            // Une cause qui demande une action ne se rejoue pas : cela épuiserait les essais du pont.
            if (error != null && error.needsUser) return
            itIsTransient()
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!current()) return
            // Une fermeture a déjà dit la cause : un échec de transport ne la remplace pas.
            if (handledGen == gen) return
            beat?.cancel()
            val error = classify(t, response)
            fail(error, retryLater = !error.needsUser, notice = ComputerBridgeError.fromFailure(t, response?.code))
        }
    }

    /** La latence d'un aller-retour : l'heure d'envoi du ping, retrouvée par son écho. */
    private fun notePong(text: String) {
        val id = Regex("\"id\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return
        val sentAt = pings.remove(id) ?: return
        val rtt = (System.nanoTime() - sentAt) / 1_000_000
        synchronized(rtts) {
            rtts.addLast(rtt)
            while (rtts.size > RTT_WINDOW) rtts.removeFirst()
        }
    }

    /** Note la fermeture **une seule fois** — `onClosing` puis `onClosed` la décrivent en deux temps. */
    private fun reportClose(code: Int, reason: String) {
        if (handledGen == generation) return
        handledGen = generation
        beat?.cancel()
        val error = ComputerBridgeError.fromClose(code, reason) ?: return
        _phase.value = Phase.FAILED
        _problem.value = error
        _notice.value = error.message()
    }

    /**
     * Le battement de cœur, en deux temps : le silence du **pont**, et celui de l'**image**.
     *
     * Le second est celui qui manquait. Le pont répond aux pings même quand sa capture est morte :
     * la session paraissait donc vivante et l'écran restait figé sans un mot. Une image qui ne
     * vient plus est une panne, pong ou pas.
     */
    private fun startHeartbeat(webSocket: WebSocket) {
        beat?.cancel()
        beat = scope.launch {
            while (true) {
                delay(heartbeatMs)
                val silentMs = (System.nanoTime() - lastSeenAt) / 1_000_000
                if (silentMs > silenceDeadlineMs) {
                    giveUpOn(webSocket, silentMs)
                    return@launch
                }
                val frameAgeMs = (System.nanoTime() - lastFrameAt) / 1_000_000
                if (frameAgeMs > imageDeadlineMs && _phase.value == Phase.LIVE) {
                    frozen(webSocket, frameAgeMs)
                    return@launch
                }
                val id = nextId()
                pings[id] = System.nanoTime()
                if (!webSocket.send(ComputerWire.ping(id))) {
                    giveUpOn(webSocket, silentMs)
                    return@launch
                }
            }
        }
    }

    /** La jauge : une fois par seconde, les chiffres — et la décision de qualité qui va avec. */
    private fun startMeter() {
        meter?.cancel()
        meter = scope.launch {
            while (true) {
                delay(METER_MS)
                val now = System.nanoTime()
                val windowMs = ((now - lastMeterAt) / 1_000_000).coerceAtLeast(1)
                val received = windowFrames
                val bytes = windowBytes
                lastMeterAt = now
                windowFrames = 0
                windowBytes = 0L
                val elapsedMs = ((now - liveSinceNanos) / 1_000_000).coerceAtLeast(1)
                val fps = received * 1000f / windowMs
                val droppedNow = (frames - decoded).coerceAtLeast(0L).toInt()
                val expected = (_hello.value?.fps ?: 0) * elapsedMs / 1000.0
                val missed = (expected - frames).coerceAtLeast(0.0).toInt()
                val missRatio = if (expected > 1.0) ((expected - frames) / expected).toFloat().coerceIn(0f, 1f) else 0f
                val next = ComputerStreamStats(
                    rttMs = medianRtt(),
                    jitterMs = jitter(),
                    fps = fps,
                    dropped = droppedNow,
                    missed = missed,
                    width = wantedWidth,
                    quality = wantedQuality,
                    kbps = (bytes * 8 / windowMs).toInt(),
                )
                _stats.value = next
                // La **même** mesure nourrit l'échelle : ce que l'écran montre sous « Détails du
                // flux » est exactement ce qui fait descendre ou remonter la qualité.
                val decided = governor.observe(next, missRatio, droppedNow - lastDropped)
                lastDropped = droppedNow
                if (decided != null) applyLadder()
            }
        }
    }

    private var lastDropped = 0

    private fun medianRtt(): Int = synchronized(rtts) {
        if (rtts.isEmpty()) return 0
        val sorted = rtts.sorted()
        sorted[sorted.size / 2].toInt()
    }

    /** La **gigue** : l'écart-type des derniers aller-retours, en millisecondes. */
    private fun jitter(): Int = synchronized(rtts) {
        if (rtts.size < 2) return 0
        val mean = rtts.average()
        val variance = rtts.sumOf { (it - mean) * (it - mean) } / rtts.size
        sqrt(variance).toInt()
    }

    /** Le décodeur dit combien d'images il a réellement affichées. */
    fun noteDecoded(total: Int) {
        decoded = total.toLong()
    }

    private fun giveUpOn(webSocket: WebSocket, silentMs: Long) {
        beat?.cancel()
        generation += 1
        webSocket.cancel()
        socket = null
        fail(ComputerBridgeError.Silent((silentMs / 1000).toInt().coerceAtLeast(1)), retryLater = true)
    }

    /**
     * L'image ne vient plus alors que la session est ouverte : on reconnecte, et on le dit.
     *
     * Rien n'est jeté au passage — ni le jeton, ni l'adresse, ni la dernière image. On repart sur
     * la même session, en montrant l'écran qu'on avait.
     */
    private fun frozen(webSocket: WebSocket, frameAgeMs: Long) {
        beat?.cancel()
        generation += 1
        webSocket.cancel()
        socket = null
        _phase.value = Phase.RECONNECTING
        _notice.value = OnyxLocalization["The screen stopped updating. Reconnecting…"]
        _problem.value = ComputerBridgeError.Silent((frameAgeMs / 1000).toInt().coerceAtLeast(1))
        scheduleReconnect()
    }

    /**
     * Range la panne, l'affiche, et ne relance que si cela peut servir.
     *
     * La phrase montrée est celle de la cause ; [notice] la remplace quand la panne vient du
     * transport, où c'est [ComputerBridgeError.fromFailure] qui nomme ce qu'on peut corriger.
     */
    private fun fail(error: ComputerBridgeError, retryLater: Boolean, notice: String? = null) {
        beat?.cancel()
        // Une panne passagère n'est une **reconnexion** que si la session avait déjà donné une
        // image : on garde alors l'écran, on le grise, et on repart tout seul. Une toute première
        // connexion qui échoue, elle, est un échec — il n'y a rien à garder à l'écran.
        if (retryLater && wanted && !error.needsUser && _frame.value != null) {
            _phase.value = Phase.RECONNECTING
            _problem.value = error
            _notice.value = notice ?: OnyxLocalization["The connection dropped. Reconnecting…"]
        } else {
            _phase.value = Phase.FAILED
            _problem.value = error
            _notice.value = notice ?: error.message()
        }
        if (!retryLater || !wanted) return
        val wait = minOf(2L shl minOf(attempt, 3), 15L)
        attempt += 1
        val gen = generation
        scope.launch {
            delay(wait * 1000L)
            if (wanted && gen == generation && _phase.value != Phase.LIVE) open()
        }
    }

    /** La phase d'une session qu'on va reprendre : on garde l'image, on grise, on redit où on en est. */
    private fun itIsTransient() {
        if (_phase.value != Phase.FAILED) _phase.value = Phase.RECONNECTING
    }

    /** La temporisation de reprise, pour une fermeture polie (le pont a rendu la main). */
    private fun scheduleReconnect() {
        if (!wanted) return
        if (_phase.value != Phase.FAILED) _phase.value = Phase.RECONNECTING
        val wait = minOf(2L shl minOf(attempt, 3), 15L)
        attempt += 1
        val gen = generation
        scope.launch {
            delay(wait * 1000L)
            if (wanted && gen == generation && _phase.value != Phase.LIVE) open()
        }
    }

    /** Ferme pour de bon : la main repart à l'agent et l'image cesse d'arriver. */
    fun close() {
        wanted = false
        attempt = 0
        generation += 1
        beat?.cancel()
        meter?.cancel()
        socket?.close(1000, "app closed the session")
        socket = null
        wantedWidth = 0
        wantedQuality = 0
        wantedFps = 0
        moving = false
        optional.clear()
        pings.clear()
        synchronized(rtts) { rtts.clear() }
        _phase.value = Phase.IDLE
        _hello.value = null
        _frame.value = null
        _state.value = ComputerState()
        _notice.value = null
        _problem.value = null
        _runOutput.value = null
        _stats.value = ComputerStreamStats()
    }

    /**
     * L'adresse ou le jeton ont changé : on ferme la session en cours et on repart avec les
     * nouveaux — **sans remplacer cet objet**.
     *
     * Un client neuf laisserait l'écran branché sur les flux de l'ancien : plus rien ne bougerait
     * à l'affichage, et le refus du pont ne serait jamais montré. Or c'est exactement ce qu'on fait
     * après un « jeton refusé » — on recolle le bon, et on veut le voir repartir.
     *
     * `close()` incrémente la génération : la fermeture de l'ancien socket arrive alors que la
     * nouvelle session est déjà en place, et elle ne peut plus se lire comme une panne de celle-ci.
     */
    fun reconfigure(address: String, token: String) {
        this.address = address
        this.token = token
        close()
    }

    private fun send(payload: String) {
        val live = socket
        if (live == null) {
            // Rien à envoyer : on redit la dernière panne connue plutôt qu'un « pas connecté »
            // qui ne dit pas pourquoi.
            _notice.value = _notice.value ?: OnyxLocalization["The bridge isn't connected."]
            return
        }
        live.send(payload)
    }

    private fun nextId(): Int = ids.getAndIncrement()

    // ---- la main -----------------------------------------------------------------

    fun take() = send(ComputerWire.take(nextId()))

    fun release() = send(ComputerWire.release(nextId()))

    // ---- les entrées --------------------------------------------------------------

    fun tap(x: Int, y: Int, button: Int = 1) = send(ComputerWire.tap(nextId(), x, y, button))

    /**
     * Le pointeur suit le doigt sans cliquer.
     *
     * Le message part comme les demandes de confort (`optional`) : un pont plus ancien le refuse,
     * et ce refus ne s'affiche pas — c'est une commodité, pas une action. Le curseur dessiné par
     * l'app, lui, bouge de toute façon.
     */
    fun motion(x: Int, y: Int) {
        val id = nextId()
        optional.add(id)
        send(ComputerWire.motion(id, x, y))
    }

    fun drag(fromX: Int, fromY: Int, toX: Int, toY: Int) = send(ComputerWire.drag(nextId(), fromX, fromY, toX, toY))

    fun scroll(x: Int, y: Int, amount: Int) = send(ComputerWire.scroll(nextId(), x, y, amount))

    fun typeText(text: String) = send(ComputerWire.typeText(nextId(), text))

    /**
     * La taille d'image voulue — la largeur du volet de l'aperçu, en pixels réels.
     *
     * L'app la connaît de sa propre mise en page : elle n'a aucune raison de décoder, à chaque
     * image, des pixels que l'écran ne montrera jamais. Redemander exactement la même chose ne
     * repart pas : le pont redémarrerait sa capture pour rien.
     */
    @Volatile
    private var wantedWidth = 0

    @Volatile
    private var wantedQuality = 0

    @Volatile
    private var wantedFps = 0

    /**
     * Les demandes **de confort** — la taille de l'image et la cadence d'un geste.
     *
     * Elles ne touchent pas au PC : elles disent seulement comment le regarder. Un pont plus ancien
     * ne les connaît pas et les refuse (`unknown_action:stream`) ; ce refus ne dit rien de l'écran,
     * donc on l'avale au lieu d'afficher une panne qui n'en est pas une. Un appui, une frappe ou une
     * commande, eux, gardent leur refus affiché.
     */
    private val optional = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    /**
     * L'écran dit la largeur de son volet : c'est la seule chose que lui seul connaît.
     *
     * La taille réellement demandée est le cran courant de l'échelle, borné par ce volet et par le
     * plancher de lisibilité ([ComputerQuality.widthFor]).
     */
    fun requestStream(widthPx: Int) {
        paneWidthPx = widthPx
        val step = ComputerQuality.LADDER[_ladder.value.step]
        val width = ComputerQuality.widthFor(step, widthPx)
        if (width == wantedWidth && step.quality == wantedQuality && step.fps == wantedFps) return
        wantedWidth = width
        wantedQuality = step.quality
        wantedFps = step.fps
        val id = nextId()
        optional.add(id)
        send(ComputerWire.stream(id, width, step.quality, step.fps))
    }

    /** Réaffirme la demande complète — appelée à chaque ouverture, y compris la même taille. */
    private fun reassertStream() {
        wantedWidth = 0
        wantedQuality = 0
        wantedFps = 0
        if (paneWidthPx > 0) requestStream(paneWidthPx)
    }

    /** Applique le cran de l'échelle : la taille demandée peut changer, le pont suit. */
    private fun applyLadder() {
        val snapshot = _ladder.value
        _ladder.value = snapshot.copy(step = governor.step, economical = governor.economical)
        if (paneWidthPx > 0) requestStream(paneWidthPx)
    }

    /** Force la « liaison économe » : le cran le plus léger, tant que l'utilisateur le veut. */
    fun setEconomical(economical: Boolean) {
        if (economical) governor.forceEconomy() else governor.automatic()
        _ladder.value = ComputerLadder(step = governor.step, economical = governor.economical)
        if (paneWidthPx > 0) requestStream(paneWidthPx)
    }

    /**
     * Le taux d'images : haut le temps d'un geste, au repos autrement.
     *
     * On ne monte en cadence que pendant qu'on se déplace — le reste du temps, l'aperçu est une
     * image qu'on lit, et chaque cran de fréquence se paie en kilo-octets.
     */
    @Volatile
    private var moving = false

    fun setMoving(movingNow: Boolean) {
        if (movingNow == moving) return
        moving = movingNow
        val id = nextId()
        optional.add(id)
        send(ComputerWire.rate(id, movingNow))
    }

    fun key(keys: String, approved: Boolean = false) = send(ComputerWire.key(nextId(), keys, approved))

    /** N'part qu'avec `approved = true` : c'est l'app qui a demandé, l'utilisateur qui a dit oui. */
    fun run(command: String, approved: Boolean) = send(ComputerWire.run(nextId(), command, approved))

    companion object {
        /** La fenêtre de latence : assez de mesures pour une gigue honnête, pas plus. */
        const val RTT_WINDOW = 8

        /** La cadence de la jauge : une fois par seconde, comme à l'écran. */
        const val METER_MS = 1_000L

        /**
         * Un client dédié : pas de délai de lecture (l'image arrive en continu) et **pas de ping de
         * contrôle** — c'est le battement applicatif qui décide de la vie du lien, pas une trame
         * dont le retard ferait tomber une session saine.
         */
        fun sharedClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(0, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /**
         * Traduit une panne de transport en cause utile.
         *
         * L'ordre compte : un certificat refusé et un nom introuvable sont des `IOException` comme
         * les autres ; les présenter comme un « délai » enverrait l'utilisateur chercher au mauvais
         * endroit.
         */
        fun classify(t: Throwable, response: Response?): ComputerBridgeError {
            response?.code?.let { code ->
                if (code == 401 || code == 403) return ComputerBridgeError.TokenRefused
                if (code == 404) return ComputerBridgeError.WrongPath
                if (code == 429) return ComputerBridgeError.TooManyAttempts(300)
            }
            val message = (t.message ?: "").lowercase()
            return when {
                t is UnknownHostException -> ComputerBridgeError.UnknownHost
                t is SSLException || t.cause is SSLException -> ComputerBridgeError.TlsRefused
                t is SocketTimeoutException || t is InterruptedIOException -> ComputerBridgeError.Timeout
                t is ConnectException -> ComputerBridgeError.Unreachable
                message.contains("didn't receive pong") || message.contains("keepalive") ->
                    ComputerBridgeError.Silent(0)
                message.contains("timeout") || message.contains("timed out") ->
                    ComputerBridgeError.Timeout
                message.contains("refused") -> ComputerBridgeError.Unreachable
                message.contains("unable to resolve host") || message.contains("unknown host") ->
                    ComputerBridgeError.UnknownHost
                else -> ComputerBridgeError.Lost(shortDetail(t))
            }
        }

        /** Le détail d'une panne inconnue, court et sans URL : une ligne d'écran, pas une pile. */
        private fun shortDetail(t: Throwable): String {
            val text = (t.message ?: t::class.java.simpleName).replace('\n', ' ').trim()
            return if (text.length <= 60) text else text.take(57) + "…"
        }
    }
}

/**
 * Le cran de l'échelle en cours, et si la liaison économe est forcée.
 *
 * Publié séparément de la phase : l'écran doit pouvoir dire « automatique, cran 3 » ou « liaison
 * économe » sans reconstruire la session.
 */
data class ComputerLadder(val step: Int, val economical: Boolean = false) {
    val total: Int get() = ComputerQuality.LADDER.size
}
