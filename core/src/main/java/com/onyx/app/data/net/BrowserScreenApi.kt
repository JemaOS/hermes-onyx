package com.onyx.app.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.onyx.app.data.model.ServerConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Les **trois routes** du plugin `onyx-browser`, et rien de plus.
 *
 * Le plugin n'est pas le Dashboard : il vit sur son propre port (`127.0.0.1:8788`), et c'est
 * `onyx-one` qui le relaie sous `/onyx-browser/` — la même origine que le reste, la même clé.
 * L'app n'a donc aucun nouveau serveur à configurer : elle ajoute une racine à celle qu'elle
 * connaît déjà.
 *
 * Ce que le serveur ne dit **jamais** : les valeurs tapées. `observe` rend un ticket, `lease` un
 * état, `health` un booléen — aucun de ces trois corps ne peut porter un mot de passe, et les
 * modèles ci-dessous n'ont donc aucun champ pour l'accueillir.
 */
object BrowserScreenApi {

    const val ROOT = "onyx-browser"

    /** Le chemin du WebSocket, relatif à la même origine. */
    const val WS_PATH = "$ROOT/ws"

    /**
     * Le ticket et l'identité de vue : ce que `POST /onyx-browser/observe` rend.
     *
     * `expiresIn` est en secondes (30) ; un ticket **ne sert qu'une fois** et c'est lui, pas le
     * jeton de l'app, qui ouvre le socket.
     */
    data class Ticket(val value: String, val path: String, val viewerId: String, val expiresIn: Int)

    /** Le bail : qui conduit, et un `epoch` qui avance à chaque transition réelle. */
    data class Lease(val holder: String, val epoch: Int) {
        val human: Boolean get() = holder == "human"
    }

    /** L'état du plugin, tel que la sonde de capacité le lit aussi. */
    data class Health(
        val ok: Boolean,
        val browser: Boolean,
        val lease: Lease,
        val watching: Int,
        val pending: PendingRequest?,
    )

    /** Une demande de prise en main en attente, telle que le serveur la publie. */
    data class PendingRequest(val id: String, val reason: String)

    fun pathFor(vararg parts: String): String = parts.joinToString("/")

    /** `POST onyx-browser/observe` — un ticket à usage unique, 30 s. */
    suspend fun observe(
        server: ServerConfig,
        client: ServeClient = ServeClient(server),
    ): Ticket? = runCatching {
        ticketOf(client.request("$ROOT/observe", method = "POST", body = buildJsonObject { }))
    }.getOrNull()

    /**
     * `POST onyx-browser/lease` avec `acquire` — lève si un autre conduit déjà.
     *
     * **La taille part avec la prise en main.** La page prend la forme de la zone où l'app la
     * dessine : un gabarit fixe la rendait minuscule au milieu d'une grande dalle (pliable déplié).
     */
    suspend fun acquire(
        server: ServerConfig,
        width: Int? = null,
        height: Int? = null,
        dpr: Float? = null,
        client: ServeClient = ServeClient(server),
    ): Lease? = runCatching {
        leaseOf(
            client.request(
                "$ROOT/lease",
                method = "POST",
                body = leaseBody("acquire", size = screenSize(width, height, dpr)),
            ),
        )
    }.getOrNull()

    /**
     * `POST onyx-browser/lease` avec `viewport` — la forme de l'écran a changé.
     *
     * Plier ou déplier change la largeur sans changer de page. Sans ce message, la page gardait
     * la forme d'avant : elle flottait dans un cadre qui n'était plus le sien.
     */
    suspend fun viewport(
        server: ServerConfig,
        width: Int,
        height: Int,
        dpr: Float? = null,
        client: ServeClient = ServeClient(server),
    ): Boolean = runCatching {
        client.request(
            "$ROOT/lease",
            method = "POST",
            body = leaseBody("viewport", size = screenSize(width, height, dpr)),
        )
        true
    }.getOrDefault(false)

    /** La taille annoncée au serveur, bornée : jamais zéro, jamais une valeur d'écran absurde. */
    fun screenSize(width: Int?, height: Int?, dpr: Float?): JsonObject? {
        if (width == null || height == null || width < 200 || height < 200) return null
        return buildJsonObject {
            put("width", width.coerceIn(200, 4000))
            put("height", height.coerceIn(200, 4000))
            if (dpr != null && dpr >= 0.5f && dpr <= 6f) put("dpr", dpr)
        }
    }

    /**
     * `GET onyx-browser/health` — l'état du plugin, et **surtout la demande en attente**.
     *
     * C'est ce qui permet à l'app de savoir qu'on lui demande la main **sans dépendre du fil** :
     * l'agent peut tourner dans une autre session que celle affichée, ou le client peut ne pas
     * rendre sa carte. Le serveur, lui, dit toujours la vérité sur ce qu'il attend.
     */
    suspend fun health(
        server: ServerConfig,
        client: ServeClient = ServeClient(server),
    ): Health? = runCatching {
        healthOf(client.request("$ROOT/health", method = "GET"))
    }.getOrNull()

    /** Lit la santé publiée par le plugin ; `null` si le corps n'est pas un JSON d'état. */
    fun healthOf(payload: JsonElement?): Health? {
        val rows = payload as? JsonObject ?: return null
        val attente = rows["pending"] as? JsonObject
        return Health(
            ok = rows.bool("ok") ?: false,
            browser = rows.bool("browser") ?: false,
            lease = leaseOf(rows["lease"]) ?: Lease(holder = "agent", epoch = 0),
            watching = rows.int("watching") ?: 0,
            pending = attente?.let {
                PendingRequest(
                    id = it.strOrNull("id").orEmpty(),
                    // Le plugin publie `reason` ; `note` est la même chose dite autrement selon
                    // la version. Une demande sans texte reste une demande : elle s'affiche.
                    reason = it.strOrNull("reason") ?: it.strOrNull("note").orEmpty(),
                )
            },
        )
    }

    /** `POST onyx-browser/lease` avec `release` — rend la main à l'agent. */
    suspend fun release(
        server: ServerConfig,
        note: String = "",
        client: ServeClient = ServeClient(server),
    ): Lease? = runCatching {
        leaseOf(client.request("$ROOT/lease", method = "POST", body = leaseBody("release", note)))
    }.getOrNull()

    /** Lit `{"ticket":…,"path":…,"viewer_id":…,"expires_in":…}` ; `null` si le corps ment. */
    fun ticketOf(payload: JsonElement?): Ticket? {
        val rows = payload as? JsonObject ?: return null
        val value = rows.strOrNull("ticket") ?: return null
        if (value.isBlank()) return null
        return Ticket(
            value = value,
            path = rows.strOrNull("path") ?: WS_PATH,
            viewerId = rows.strOrNull("viewer_id").orEmpty(),
            expiresIn = rows.int("expires_in") ?: 30,
        )
    }

    /** Lit `{"holder":"human","epoch":3}` — un corps sans `holder` n'est pas un bail. */
    fun leaseOf(payload: JsonElement?): Lease? {
        val rows = payload as? JsonObject ?: return null
        val holder = rows.strOrNull("holder") ?: return null
        return Lease(holder = holder, epoch = rows.int("epoch") ?: 0)
    }

    /** Le corps d'un `acquire` / `release` / `viewport`. */
    fun leaseBody(action: String, note: String? = null, size: JsonObject? = null): JsonObject =
        buildJsonObject {
            put("action", action)
            if (!note.isNullOrBlank()) put("note", note)
            size?.forEach { (cle, valeur) -> put(cle, valeur) }
        }

}


/**
 * Le décodage d'une trame, hors du monde Compose.
 *
 * Le serveur envoie `{"type":"frame","seq":7,"data":"<jpeg base64>","url":…,"width":…,…}`. Ce
 * fichier ne décode **pas** l'image (c'est le travail de l'écran, hors thread principal) : il dit
 * seulement si la trame est exploitable, et avec quel numéro — le `seq` qui servira à refuser une
 * saisie calculée sur une image périmée.
 */
data class BrowserFrame(
    val seq: Int,
    val data: String,
    val url: String,
    val title: String,
    val width: Int,
    val height: Int,
    val cssWidth: Int,
    val cssHeight: Int,
)

object BrowserFrames {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Une trame, ou `null` si le JSON est incomplet (jamais une trame à moitié). */
    fun frameOf(raw: String): BrowserFrame? {
        val rows = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        if (rows.strOrNull("type") != "frame") return null
        val data = rows.strOrNull("data") ?: return null
        val css = (rows["css"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { element ->
            (element as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        }.orEmpty()
        return BrowserFrame(
            seq = rows.int("seq") ?: 0,
            data = data,
            url = rows.strOrNull("url").orEmpty(),
            title = rows.strOrNull("title").orEmpty(),
            width = rows.int("width") ?: 0,
            height = rows.int("height") ?: 0,
            cssWidth = css.getOrNull(0) ?: 0,
            cssHeight = css.getOrNull(1) ?: 0,
        )
    }
}
