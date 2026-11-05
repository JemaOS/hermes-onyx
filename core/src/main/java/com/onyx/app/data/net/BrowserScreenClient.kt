package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Un geste de l'humain sur la page de l'agent.
 *
 * Les coordonnées sont des **fractions** de l'image (0..1) : c'est la seule forme qui survit à un
 * changement de taille d'écran, et c'est ce que le serveur multiplie par le viewport CSS qu'il a
 * mesuré. Le `seq` voyage **avec** le geste : le serveur refuse une saisie qui a été calculée sur
 * une image qui n'est plus la dernière (§7, règle 3).
 */
sealed interface ScreenInput {
    data class Tap(val x: Float, val y: Float) : ScreenInput
    data class Scroll(val x: Float, val y: Float, val dy: Float) : ScreenInput
    data class Type(val text: String) : ScreenInput
    data class Key(val key: String) : ScreenInput
    data class Fill(val secret: String) : ScreenInput

    /** Le corps envoyé au serveur : `action` et ses champs, jamais autre chose. */
    fun body(seq: Int, css: Pair<Int, Int>?): JsonObject = buildJsonObject {
        put("type", "input")
        put("seq", seq)
        // La taille CSS mesurée par le serveur, pour que le geste tombe juste même si la page
        // a changé de viewport depuis la trame affichée.
        css?.let { size ->
            put("css", JsonArray(listOf(JsonPrimitive(size.first), JsonPrimitive(size.second))))
        }
        when (this@ScreenInput) {
            is Tap -> {
                put("action", "click")
                put("x", x.coerceIn(0f, 1f))
                put("y", y.coerceIn(0f, 1f))
            }
            is Scroll -> {
                put("action", "scroll")
                put("x", x.coerceIn(0f, 1f))
                put("y", y.coerceIn(0f, 1f))
                put("dy", dy)
            }
            is Type -> {
                put("action", "type")
                // 2 000 caractères : le plafond du serveur, appliqué ici pour ne pas envoyer
                // un corps qu'il refuserait de toute façon.
                put("text", text.take(2000))
            }
            is Key -> {
                put("action", "key")
                put("key", key)
            }
            is Fill -> {
                // Le nom du secret part ; **sa valeur ne part pas** : c'est le serveur qui la lit.
                put("action", "fill")
                put("secret", secret)
            }
        }
    }
}

/**
 * Le WebSocket du navigateur de l'agent : des trames, des gestes, et la fin.
 *
 * Pas de reconnexion automatique, volontairement : le ticket est à **usage unique** et périme en
 * trente secondes, donc une reconnexion muette échouerait de toute façon. Une session perdue se
 * rouvre par un geste de la personne (l'écran le dit), pas par un fil qui tourne en boucle.
 *
 * Le jeton d'appairage ne sort jamais d'ici : il part dans un en-tête, il n'est ni journalisé ni
 * rendu. Le socket, lui, s'ouvre avec le **ticket**, pas avec le jeton.
 */
class BrowserScreenClient(
    server: ServerConfig,
    private val onFrame: (BrowserFrame) -> Unit,
    private val onLease: (BrowserScreenApi.Lease) -> Unit,
    private val onRefused: (Int, String) -> Unit,
    private val onClosed: (String?) -> Unit,
    /** La fin d'une prise en main se dit : `lost` dit que la page a disparu, pas que l'agent a repris. */
    private val onEnded: (Boolean, String) -> Unit = { _, _ -> },
    /** Le flux tourne, mais il n'y a **aucune page** à montrer. */
    private val onIdle: () -> Unit = {},
    /**
     * La page est chez un **autre** processus : celui-ci n'a rien à montrer.
     *
     * Le serveur le signale puis ferme le socket. L'app doit repartir — c'est le relais qui
     * renverra alors l'écran vers le bon processus, puisque le ticket sera redemandé.
     */
    private val onPageElsewhere: () -> Unit = {},
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val base: String = server.serveBaseUrl.ifBlank { server.baseUrl }
    // La clé du profil effectif, retenue à la construction : `server` n'est pas une propriété,
    // et c'est elle que le relais attend sur la poignée de main.
    private val cle: String = server.effectiveApiKey
    private val http: OkHttpClient = OkHttpClient.Builder()
        // Un flux d'images ne doit pas être coupé pour inactivité : le silence est normal quand
        // personne ne regarde (le serveur passe alors à une trame toutes les 1,5 s).
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null

    val connected: Boolean get() = socket != null

    /** Ouvre le flux. [ticket] vient de `observe` et ne sert qu'une fois. */
    fun connect(ticket: String): Boolean {
        close()
        val url = webSocketUrl(base, BrowserScreenApi.WS_PATH, ticket) ?: run {
            onClosed("address")
            return false
        }
        // **La clé voyage aussi sur le WebSocket.** Le relais de l'app (`onyx-one`) authentifie
        // *toutes* les routes qu'il sert, y compris la poignée de main ; sans cet en-tête il
        // répondait 401 et OkHttp levait « Expected HTTP 101 response but was '401 Unauthorized' ».
        // Le ticket, lui, reste dans l'URL : il est à usage unique et propre à cette connexion.
        val request = Request.Builder()
            .url(url)
            .apply {
                cle.takeIf { it.isNotBlank() }?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
            .build()
        val opened = http.newWebSocket(request, Listener())
        socket = opened
        return true
    }

    fun watch(on: Boolean, takeover: Boolean) {
        send(buildJsonObject {
            put("type", "watch")
            put("on", on)
            put("takeover", takeover)
        })
    }

    fun send(input: ScreenInput, seq: Int, css: Pair<Int, Int>? = null): Boolean {
        val body = input.body(seq, css)
        return send(body)
    }

    fun handBack(id: String?, note: String) {
        send(buildJsonObject {
            put("type", "done")
            put("id", id.orEmpty())
            put("note", note)
        })
    }

    fun close() {
        socket?.close(1000, null)
        socket = null
    }

    private fun send(body: JsonObject): Boolean = socket?.send(body.toString()) == true

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val rows = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: return
            when (rows.strOrNull("type")) {
                "frame" -> BrowserFrames.frameOf(text)?.let(onFrame)
                "lease" -> BrowserScreenApi.leaseOf(rows)?.let(onLease)
                "refused" -> onRefused(rows.int("seq") ?: 0, rows.strOrNull("reason").orEmpty())
                // **La fin d'une prise en main se dit.** Elle était ignorée : quand le serveur
                // rendait la main d'office — page fermée par l'inactivité de Hermes, délai de
                // quinze minutes écoulé — l'écran restait sur la dernière image avec « en
                // attente », et la personne attendait un rendez-vous qui n'existait plus.
                "takeover_done" -> onEnded(
                    rows["lost"]?.let { it is JsonPrimitive && it.content == "true" } == true,
                    rows.strOrNull("reason").orEmpty(),
                )
                "idle" -> onIdle()
                // **La page est chez un autre processus.** Le serveur le signale avant de fermer
                // le socket : sans ce traitement, l'app attend un flux qui ne viendra plus.
                "page_elsewhere" -> onPageElsewhere()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            socket = null
            onClosed(t.message ?: "network")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
            onClosed(reason.ifBlank { code.toString() })
        }
    }
}

/**
 * L'adresse du socket à partir de celle du serveur.
 *
 * `https` devient `wss`, `http` devient `ws`, et un chemin déjà présent est respecté : la même
 * origine que l'app connaît, plus `/onyx-browser/ws`. Rend `null` quand la base n'est pas une
 * adresse absolue — on ne fabrique pas une URL bancale pour la donner à OkHttp.
 */
fun webSocketUrl(base: String, path: String, ticket: String): String? {
    val clean = base.trim().trimEnd('/')
    if (clean.isEmpty()) return null
    val scheme = when {
        clean.startsWith("https://", ignoreCase = true) -> "wss"
        clean.startsWith("http://", ignoreCase = true) -> "ws"
        clean.startsWith("wss://", ignoreCase = true) || clean.startsWith("ws://", ignoreCase = true) ->
            clean.substringBefore("://")
        else -> return null
    }
    val rest = clean.substringAfter("://")
    if (rest.isEmpty()) return null
    val tail = path.trimStart('/')
    return "$scheme://$rest/$tail?ticket=$ticket"
}
