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

/**
 * La session avec le pont « Ordinateur »: une connexion, des images, la main, des entrées.
 *
 * Elle vit **hors de l'écran**, dans le conteneur de l'application: quitter l'onglet et y revenir
 * ne coupe rien, ne rend pas la main et ne redemande pas l'image — on retrouve la session telle
 * qu'elle était, avec la dernière image déjà là. Seul un `close` explicite (« Fermer la
 * session ») arrête tout, et le pont rend alors la main de lui-même.
 *
 * Le jeton ne sort jamais d'ici: il part dans un en-tête `Authorization`, il n'est ni journalisé,
 * ni rendu, ni affiché.
 *
 * **Le battement de cœur est applicatif, pas un ping WebSocket.** Le client envoyait auparavant un
 * ping de contrôle toutes les 20 s: quand le pong manquait, la bibliothèque coupait la session et
 * affichait son texte interne, en anglais et en millisecondes (« sent ping but didn't receive pong
 * within 20000ms (after 0 successful ping/pongs) »). Un pong n'est pas une preuve de vie fiable au
 * travers d'un tunnel — il fait la queue derrière des kilo-octets d'image — et tuer une session
 * saine sur 20 s de retard est une panne inventée. On envoie donc `{"t":"ping"}` (le pont répond
 * `{"t":"pong"}`) et on ne conclut à la mort du lien qu'après [silenceDeadlineMs] sans **aucune**
 * trame reçue.
 *
 * **Le refus du pont est lu là où il arrive.** Un serveur qui refuse tient d'abord la poignée de
 * main — il a besoin de lire l'en-tête — puis ferme avec son code (`4401` jeton, `4404` chemin,
 * `4429` trop de tentatives). OkHttp annonce cette fermeture par `onClosing`, et c'est **là** que
 * le code est lu: la cause est dite tout de suite, et rien de ce que le même socket dirait ensuite
 * ne compte. Une cause qui demande une action (recopier le jeton, corriger l'adresse) n'est
 * **jamais** rejouée toute seule: insister ne ferait qu'user la serrure du pont.
 */
class ComputerBridgeClient(
    private var address: String,
    private var token: String,
    private val httpClient: OkHttpClient = sharedClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val heartbeatMs: Long = 15_000,
    private val silenceDeadlineMs: Long = 45_000,
) {

    enum class Phase { IDLE, CONNECTING, LIVE, FAILED }

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

    /** La même panne en pièces détachées: l'écran peut décider quoi proposer (recopier, réessayer). */
    private val _problem = MutableStateFlow<ComputerBridgeError?>(null)
    val problem: StateFlow<ComputerBridgeError?> = _problem.asStateFlow()

    /** La sortie de la dernière commande approuvée, quand il y en a une. */
    private val _runOutput = MutableStateFlow<String?>(null)
    val runOutput: StateFlow<String?> = _runOutput.asStateFlow()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var wanted = false

    @Volatile
    private var lastSeenAt = 0L

    /** Un numéro par tentative: la réponse d'une socket périmée ne touche plus l'état. */
    private var generation = 0

    /** La tentative dont la fermeture a déjà été expliquée — la cause n'est dite qu'une fois. */
    private var handledGen = -1

    private var attempt = 0
    private var beat: Job? = null
    private var frames = 0L

    /** Ouvre la session si besoin; ne fait rien si elle est déjà ouverte. */
    fun connect() {
        wanted = true
        if (_phase.value == Phase.LIVE || _phase.value == Phase.CONNECTING) return
        // Un lancement à la main repart à zéro: la temporisation d'une panne ancienne ne doit pas
        // retarder l'essai que l'utilisateur vient de demander.
        attempt = 0
        open()
    }

    /**
     * Réessaie tout de suite, sans attendre la temporisation: le geste de l'utilisateur après
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
            // Le texte montré vient de la cause décrite côté écran; l'objet structuré, lui, dit la
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
        _phase.value = Phase.CONNECTING
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
            lastSeenAt = System.nanoTime()
            _phase.value = Phase.LIVE
            _problem.value = null
            _notice.value = null
            startHeartbeat(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            if (!current()) return
            val data = bytes.toByteArray()
            if (data.isEmpty()) return
            lastSeenAt = System.nanoTime()
            frames += 1
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
                is ComputerEvent.Ack -> _notice.value = null
                is ComputerEvent.Denied -> _notice.value = ComputerWire.denialMessage(event.reason)
                is ComputerEvent.RunResult -> {
                    _runOutput.value = event.output
                    if (!event.ok) _notice.value = OnyxLocalization["The command didn't finish."]
                }
                ComputerEvent.Pong, ComputerEvent.Ignored -> Unit
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (!current()) return
            // Le pont annonce **pourquoi** il ferme: c'est la cause exacte, dite tout de suite.
            reportClose(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!current()) return
            reportClose(code, reason)
            beat?.cancel()
            if (!wanted) return
            val error = ComputerBridgeError.fromClose(code, reason)
            // Une cause qui demande une action ne se rejoue pas: cela épuiserait les essais du pont.
            if (error != null && error.needsUser) return
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!current()) return
            // Une fermeture a déjà dit la cause: un échec de transport ne la remplace pas.
            if (handledGen == gen) return
            beat?.cancel()
            val error = classify(t, response)
            fail(error, retryLater = !error.needsUser, notice = ComputerBridgeError.fromFailure(t, response?.code))
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
     * Le battement de cœur: un `ping` applicatif, et la **seule** conclusion possible — le pont
     * n'a plus rien dit du tout depuis [silenceDeadlineMs].
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
                if (!webSocket.send(ComputerWire.ping(nextId()))) {
                    giveUpOn(webSocket, silentMs)
                    return@launch
                }
            }
        }
    }

    private fun giveUpOn(webSocket: WebSocket, silentMs: Long) {
        beat?.cancel()
        generation += 1
        webSocket.cancel()
        socket = null
        fail(ComputerBridgeError.Silent((silentMs / 1000).toInt().coerceAtLeast(1)), retryLater = true)
    }

    /**
     * Range la panne, l'affiche, et ne relance que si cela peut servir.
     *
     * La phrase montrée est celle de la cause; [notice] la remplace quand la panne vient du
     * transport, où c'est [ComputerBridgeError.fromFailure] qui nomme ce qu'on peut corriger.
     */
    private fun fail(error: ComputerBridgeError, retryLater: Boolean, notice: String? = null) {
        beat?.cancel()
        _phase.value = Phase.FAILED
        _problem.value = error
        _notice.value = notice ?: error.message()
        if (!retryLater || !wanted) return
        val wait = minOf(2L shl minOf(attempt, 3), 15L)
        attempt += 1
        val gen = generation
        scope.launch {
            delay(wait * 1000L)
            if (wanted && gen == generation && _phase.value != Phase.LIVE) open()
        }
    }

    /** La temporisation de reprise, pour une fermeture polie (le pont a rendu la main). */
    private fun scheduleReconnect() {
        if (!wanted) return
        _phase.value = Phase.FAILED
        val wait = minOf(2L shl minOf(attempt, 3), 15L)
        attempt += 1
        val gen = generation
        scope.launch {
            delay(wait * 1000L)
            if (wanted && gen == generation && _phase.value != Phase.LIVE) open()
        }
    }

    /** Ferme pour de bon: la main repart à l'agent et l'image cesse d'arriver. */
    fun close() {
        wanted = false
        attempt = 0
        generation += 1
        beat?.cancel()
        socket?.close(1000, "app closed the session")
        socket = null
        _phase.value = Phase.IDLE
        _hello.value = null
        _frame.value = null
        _state.value = ComputerState()
        _notice.value = null
        _problem.value = null
        _runOutput.value = null
    }

    /**
     * L'adresse ou le jeton ont changé: on ferme la session en cours et on repart avec les
     * nouveaux — **sans remplacer cet objet**.
     *
     * Un client neuf laisserait l'écran branché sur les flux de l'ancien: plus rien ne bougerait
     * à l'affichage, et le refus du pont ne serait jamais montré. Or c'est exactement ce qu'on fait
     * après un « jeton refusé » — on recolle le bon, et on veut le voir repartir.
     *
     * `close` incrémente la génération: la fermeture de l'ancien socket arrive alors que la
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
            // Rien à envoyer: on redit la dernière panne connue plutôt qu'un « pas connecté »
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

    fun tap(x: Int, y: Int) = send(ComputerWire.tap(nextId(), x, y))

    fun drag(fromX: Int, fromY: Int, toX: Int, toY: Int) = send(ComputerWire.drag(nextId(), fromX, fromY, toX, toY))

    fun scroll(x: Int, y: Int, amount: Int) = send(ComputerWire.scroll(nextId(), x, y, amount))

    fun typeText(text: String) = send(ComputerWire.typeText(nextId(), text))

    fun key(keys: String, approved: Boolean = false) = send(ComputerWire.key(nextId(), keys, approved))

    /** N'part qu'avec `approved = true`: c'est l'app qui a demandé, l'utilisateur qui a dit oui. */
    fun run(command: String, approved: Boolean) = send(ComputerWire.run(nextId(), command, approved))

    companion object {
        /**
         * Un client dédié: pas de délai de lecture (l'image arrive en continu) et **pas de ping de
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
         * L'ordre compte: un certificat refusé et un nom introuvable sont des `IOException` comme
         * les autres; les présenter comme un « délai » enverrait l'utilisateur chercher au mauvais
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

        /** Le détail d'une panne inconnue, court et sans URL: une ligne d'écran, pas une pile. */
        private fun shortDetail(t: Throwable): String {
            val text = (t.message ?: t::class.java.simpleName).replace('\n', ' ').trim()
            return if (text.length <= 60) text else text.take(57) + "…"
        }
    }
}
