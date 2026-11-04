package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Le canal JSON-RPC du Dashboard Hermes.
 *
 * Miroir de `HermesServeClient.call` côté référence : un ticket à usage unique
 * (`POST api/auth/ws-ticket`), puis `ws(s)://…/api/ws?ticket=…` et des trames JSON-RPC 2.0.
 * C'est par là que passent les commandes `/`, l'écriture d'un profil de bot et les réponses aux
 * interruptions — les routes REST ne les portent pas.
 */
class ServeRpc(
    private val config: ServerConfig,
    /**
     * Le même bocal à cookies que le client REST : le ticket est demandé avec la session ouverte
     * par le login, sinon le Dashboard le refuse.
     */
    private val httpClient: OkHttpClient = ServeClient.clientFor(config),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()

    /**
     * Les abonnés aux notifications du serveur (`method == "event"`).
     *
     * Le Dashboard pousse les événements d'un tour (`message.delta`, `message.complete`,
     * `session.info`, `tool.start`…) sur le même socket que les réponses RPC : ce sont des
     * trames sans `id`, que le canal ignorait jusqu'ici. C'est par là que passe le chat du
     * transport Dashboard (voir [HermesServeChat]).
     */
    private val listeners = ConcurrentHashMap<String, (ServeEvent) -> Unit>()

    /** S'abonne aux notifications du serveur ; rend la clé à passer à [removeListener]. */
    fun addListener(listener: (ServeEvent) -> Unit): String {
        val key = UUID.randomUUID().toString()
        listeners[key] = listener
        return key
    }

    fun removeListener(key: String) {
        listeners.remove(key)
    }

    /** Distribue une notification à tous les abonnés ; un abonné fautif ne bloque pas les autres. */
    private fun notify(event: ServeEvent) {
        for (listener in listeners.values) {
            runCatching { listener(event) }
        }
    }

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var ready = false

    val isReady: Boolean get() = ready && socket != null

    /**
     * **Le verrou de connexion.** `connect()` est `suspend` et fait des allers-retours réseau ;
     * deux coroutines qui l'appellent en même temps ouvraient **deux** WebSocket, et la seconde
     * écrasait la première (`socket = webSocket`) : la socket jetée restait ouverte côté serveur
     * jusqu'au keepalive, et la première coroutine attendait un `opened` que plus personne ne
     * compléterait. Le journal le montre : cinq `ws: tunnel ouvert` en deux secondes.
     *
     * Le verrou rend l'ouverture **unique** : le second appelant attend celui qui a le verrou,
     * puis repart sur `isReady` — un seul socket, une seule connexion.
     */
    private val connectMutex = Mutex()

    /**
     * Le client du WebSocket, **créé une fois**.
     *
     * Il lui faut une connexion HTTP/1.1 neuve, que le pool partagé du REST ne peut pas lui
     * donner : ce pool porte déjà la connexion h2 (ALPN) du REST vers la même origine, et une
     * connexion h2 ne sait pas porter un upgrade — la requête partait alors dans le vide, sans
     * jamais atteindre le serveur.
     *
     * Un client par ouverture serait une **fuite** : chaque reprise ajouterait un client et ses
     * fils, jamais rendus. Le client est donc unique, et la reprise rouvre le canal dessus.
     */
    private val webSocketClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
            .build()
    }

    /**
     * Le fil de la reprise. Il vit dans la classe : la reconnexion doit survivre au tour qui
     * l'a déclenchée, sinon c'est le `call()` en cours qui la tuerait — et c'est précisément le
     * tour qui a besoin d'elle.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var reconnecting = false

    /** Posé par [close] : une fermeture volontaire, qui ne doit jamais déclencher de reprise. */
    @Volatile
    private var closed = false

    /** Ouvre le canal si besoin. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        connectMutex.withLock {
            // Le premier appelant a pu ouvrir le canal pendant qu'on attendait le verrou.
            if (isReady) return@withLock
            // Un `connect()` explicite est une intention d'usage : la fermeture volontaire qui
            // précédait ne doit pas empêcher le canal de revenir (un canal rouvert reste utilisable,
            // et seule la reprise automatique d'un canal fermé est indésirable).
            closed = false
            openChannel()
        }
    }

    /** L'ouverture réelle ; le verrou est pris par [connect]. */
    private suspend fun openChannel() {
        // La session du Dashboard est ouverte d'abord (bocal à cookies partagé par tout le
        // processus) : le ticket part avec elle. Un Dashboard sans login exigé passe la sonde de
        // `login` sans rien faire. L'échec n'est pas avalé pour autant : si le ticket est ensuite
        // refusé, c'est la connexion qui dit *pourquoi* (identifiants refusés, adresse
        // introuvable) au lieu d'un 401 nu — ou d'une attente sans fin.
        val signInFailure = runCatching { ServeClient(config).login() }.exceptionOrNull()
        val ticket = try {
            ticket()
        } catch (error: TransportError.Http) {
            if (error.status == 401 && signInFailure != null) throw signInFailure
            throw error
        }
        val base = config.serveBase
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> "ws://$base"
        }
        val url = "$wsBase/api/ws?ticket=$ticket"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OnyxHttp.USER_AGENT)
            // « Une clé » : le serveur compagnon exige la clé d'installation sur le WebSocket
            // comme sur le REST — c'est son identité, sans cookie de session côté app.
            .apply { if (config.servesWithKey) header("Authorization", "Bearer ${config.effectiveApiKey}") }
            .build()

        val opened = CompletableDeferred<Boolean>()
        // Le WebSocket a son **propre client** : il lui faut une connexion HTTP/1.1 neuve, que
        // le pool partagé ne peut pas lui donner. Ce pool porte déjà la connexion h2 du REST
        // vers la même origine (ALPN), et une connexion h2 ne sait pas porter un upgrade :
        // la requête partait alors dans le vide, sans jamais atteindre le serveur — d'où
        // zéro `/api/ws` côté serveur et une liste de bots vide alors que le REST répondait.
        // `ConnectionPool(0, ...)` = aucun pool partagé, et `protocols(HTTP_1_1)` verrouille
        // la négociation sur HTTP/1.1.
        //
        // Le client est **créé une fois** : la reconnexion rouvre le canal sur le même client,
        // sinon chaque reprise ajouterait un client et ses fils — une fuite qui se paie en
        // lenteur au bout de quelques coupures.
        val client = webSocketClient

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                ready = true
                opened.complete(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                val id = frame["id"]?.jsonPrimitive?.content
                if (id != null) {
                    val waiting = pending.remove(id) ?: return
                    val error = frame["error"]?.jsonObject
                    if (error != null) {
                        waiting.completeExceptionally(
                            TransportError.Rpc(
                                code = error["code"]?.jsonPrimitive?.intOrNull ?: 0,
                                detail = error["message"]?.jsonPrimitive?.content ?: "RPC error",
                            ),
                        )
                    } else {
                        waiting.complete(frame["result"] ?: JsonObject(emptyMap()))
                    }
                    return
                }
                // Notification du serveur : une trame d'événement, sans `id`.
                if (frame["method"]?.asString() != "event") return
                val params = frame["params"].asObject() ?: return
                val event = ServeEvent(
                    type = params["type"].asString().orEmpty(),
                    sessionId = params["session_id"].asString(),
                    payload = params["payload"].asObject() ?: JsonObject(emptyMap()),
                )
                if (event.type.isEmpty()) return
                notify(event)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                ready = false
                pending.values.forEach { it.completeExceptionally(TransportError.from(t, afterHeaders = true)) }
                pending.clear()
                opened.complete(false)
                announceLoss()
                // La reprise part **ici**, dans le fil de la classe : la coupure est signalée à
                // l'app, puis on tente de rouvrir tout seul.
                scope.launch { reconnect() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                ready = false
                opened.complete(false)
                announceLoss()
                scope.launch { reconnect() }
            }
        }

        client.newWebSocket(request, listener)
        withTimeoutOrNull(10_000) { opened.await() }
    }

    /**
     * **Rouvrir tout seul après une coupure.**
     *
     * Le tunnel Cloudflare coupe les connexions longues quand le réseau change (Wi-Fi → 4G,
     * écran qui dort, tunnel qui redémarre). Avant, la coupure était **définitive** : l'app
     * apprenait `connection.lost`, affichait « connexion perdue », et le canal ne revenait qu'à
     * la prochaine action manuelle — en pleine conversation vocale, c'est le tour qui meurt.
     *
     * Ici, la coupure déclenche une **tentative de reprise** : on rouvre le canal, et si ça
     * marche, l'app repart sur un canal sain sans que la personne ait à relancer quoi que ce
     * soit. La reprise est **limitée** (un nombre de tentatives, espacées) pour ne pas boucler
     * quand le serveur est vraiment down : mieux vaut une coupure franche qu'une tentative
     * sans fin.
     */
    private suspend fun reconnect() {
        if (closed || reconnecting) return
        reconnecting = true
        try {
            var attempt = 0
            while (attempt < RECONNECT_ATTEMPTS && !isReady) {
                attempt++
                delay(RECONNECT_DELAY_MS * attempt)
                val recovered = runCatching {
                    connectMutex.withLock {
                        if (isReady) return@withLock true
                        openChannel()
                        isReady
                    }
                }.getOrDefault(false)
                if (recovered) {
                    notify(ServeEvent("connection.restored", null, JsonObject(emptyMap())))
                    return
                }
            }
        } finally {
            reconnecting = false
        }
    }

    /**
     * La coupure est signalée **et** rattrapée : l'app affiche « connexion perdue » puis reçoit
     * `connection.restored` si la reprise marche. Les deux événements sont ce qui permet à
     * l'interface de distinguer « ça va revenir » de « c'est fini ».
     */
    private fun announceLoss() {
        notify(ServeEvent("connection.lost", null, JsonObject(emptyMap())))
    }

    /** Un appel JSON-RPC ; le profil est ajouté aux paramètres comme sur la référence. */
    suspend fun call(method: String, params: JsonObject = buildJsonObject { }, timeoutMillis: Long = 60_000): JsonElement {
        connect()
        val webSocket = socket ?: throw TransportError.Unreachable(OnyxLocalization["no channel to the Dashboard"])

        val id = "onyx-${nextId.getAndIncrement()}"
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred

        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", withProfile(method, params))
        }
        if (!webSocket.send(frame.toString())) {
            pending.remove(id)
            throw TransportError.Unreachable(OnyxLocalization["the channel closed before sending"])
        }

        return withTimeoutOrNull(timeoutMillis) { deferred.await() }
            ?: run {
                pending.remove(id)
                throw TransportError.Malformed(OnyxLocalization.format("%1\$s did not answer", method))
            }
    }

    fun close() {
        // **Une fermeture voulue n'est pas une coupure.**
        //
        // Sans ce drapeau, `onClosed` relançait `reconnect()` : chaque `ServeRpc` jetable (une
        // instance par action : répondre à un secret, approuver, renommer…) rouvrait un WebSocket
        // sans propriétaire et repayait jusqu'à trois handshakes — login, ticket, upgrade — après
        // avoir rendu la main. La reprise après coupure réseau, elle, garde son chemin : `closed`
        // n'est posé que par une fermeture volontaire.
        closed = true
        runCatching { socket?.close(1000, "closing") }
        socket = null
        ready = false
    }

    private suspend fun ticket(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(config.serveBase + "/api/auth/ws-ticket")
            .post("{}".toRequestBody())
            .header("Content-Type", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            // « Une clé » : la clé d'installation accompagne la demande de ticket, exactement
            // comme elle accompagne chaque appel `/api/*`.
            .apply { if (config.servesWithKey) header("Authorization", "Bearer ${config.effectiveApiKey}") }
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw TransportError.Http(response.code, "")
                val text = response.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                obj?.get("ticket")?.jsonPrimitive?.content
                    ?: throw TransportError.Malformed(OnyxLocalization["unreadable ticket"])
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = false)
        }
    }

    /**
     * Le profil, sur les seules méthodes qui l'acceptent : les autres refusent une clé inconnue.
     */
    private fun withProfile(method: String, params: JsonObject): JsonObject {
        val profile = config.effectiveProfile ?: return params
        if (method !in PROFILE_SCOPED) return params
        return buildJsonObject {
            params.forEach { (key, value) -> put(key, value) }
            put("profile", profile)
        }
    }

    private fun String.toRequestBody() = this.toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        /** Les méthodes du gateway qui prennent un profil ; les autres rejettent la clé. */
        val PROFILE_SCOPED = setOf(
            "chat.send", "session.create", "session.list", "session.resume",
            "complete.slash", "skills.list", "skills.read", "tools.list",
            "cron.list", "files.list", "files.read", "files.write",
        )

        /**
         * Combien de fois on tente de rouvrir le canal après une coupure.
         *
         * Trois suffisent : une coupure réseau se répare en quelques secondes, et une reprise
         * qui échoue trois fois veut dire que le **serveur** est down — mieux vaut une coupure
         * franche que l'app qui réessaie sans fin.
         */
        const val RECONNECT_ATTEMPTS = 3

        /** Le délai entre deux tentatives ; il croît à chaque essai (1 s, 2 s, 3 s). */
        const val RECONNECT_DELAY_MS = 1_000L
    }
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Une notification du Dashboard (`method == "event"`).
 *
 * `type` : `message.delta`, `reasoning.delta`, `tool.start`, `tool.complete`, `status.update`,
 * `message.complete`, `session.info`, `error`… `sessionId` : la session runtime concernée.
 * `payload` : le corps varié de l'événement (texte, usage, drapeaux), lu à la demande.
 */
data class ServeEvent(
    val type: String,
    val sessionId: String?,
    val payload: JsonObject,
)
