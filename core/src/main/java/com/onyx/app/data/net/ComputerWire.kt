package com.onyx.app.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Le protocole du pont « Ordinateur » — l'écran du PC, l'image, la main, les entrées.
 *
 * Deux familles de trames sur une seule connexion : les **images** (binaires, du JPEG brut) et le
 * **texte** (JSON, des deux côtés). Une image ne porte jamais de commande, et une commande ne
 * porte jamais d'image : il n'y a donc pas de trames mêlées à découper.
 *
 * La main est un état du pont, pas un réglage de l'app : l'app la demande (`take`), la rend
 * (`release`), et le pont refuse toute entrée de qui ne la détient pas. C'est ce qui fait qu'un
 * simple regard ne peut pas cliquer sur le bureau de quelqu'un.
 */
private val computerJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** La dernière chose que le pont a faite — un clic, une frappe, une commande. */
data class ComputerAction(
    val kind: String,
    val detail: String,
    val x: Int? = null,
    val y: Int? = null,
    val at: Long = 0L,
)

/** Qui a la main, et ce qui vient de se passer — l'état montré sous l'aperçu. */
data class ComputerState(
    val holder: String = HOLDER_AGENT,
    val viewer: String? = null,
    val busy: Boolean = false,
    val last: ComputerAction? = null,
) {
    /** Vrai quand c'est l'app (donc vous) qui pilote. */
    val humanHolds: Boolean get() = holder == HOLDER_HUMAN

    companion object {
        const val HOLDER_AGENT = "agent"
        const val HOLDER_HUMAN = "human"
    }
}

/** Ce que le pont annonce en s'ouvrant : la taille de l'écran du PC et celle de l'image envoyée. */
data class ComputerHello(
    val screenWidth: Int,
    val screenHeight: Int,
    val viewWidth: Int,
    val viewHeight: Int,
    val fps: Int,
    val viewer: String,
)

/** Une trame de texte du pont. */
sealed interface ComputerEvent {
    data class Hello(val hello: ComputerHello) : ComputerEvent
    data class State(val state: ComputerState) : ComputerEvent
    data class Ack(val id: Int?, val detail: String) : ComputerEvent
    data class Denied(val id: Int?, val reason: String) : ComputerEvent
    data class RunResult(val id: Int?, val ok: Boolean, val exit: Int, val output: String) : ComputerEvent
    data object Pong : ComputerEvent

    /** Une trame qu'on ne connaît pas : ignorée, jamais une erreur pour l'écran. */
    data object Ignored : ComputerEvent
}

/** Ce que l'app envoie au pont, et ce qu'elle en lit. */
object ComputerWire {

    /** Les pixels de l'écran du PC — l'espace que l'app emploie. */
    const val SPACE_SCREEN = "screen"

    /** Les pixels de l'image envoyée — l'autre espace, agrandi par le pont. */
    const val SPACE_VIEW = "view"

    // ---- lecture ----------------------------------------------------------------

    fun parse(text: String): ComputerEvent = runCatching {
        val root = computerJson.parseToJsonElement(text).jsonObject
        when (root["t"]?.jsonPrimitive?.contentOrNull) {
            "hello" -> {
                val screen = root["screen"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()
                val view = root["view"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()
                ComputerEvent.Hello(
                    ComputerHello(
                        screenWidth = screen.getOrNull(0) ?: 0,
                        screenHeight = screen.getOrNull(1) ?: 0,
                        viewWidth = view.getOrNull(0) ?: 0,
                        viewHeight = view.getOrNull(1) ?: 0,
                        fps = root["fps"]?.jsonPrimitive?.intOrNull ?: 0,
                        viewer = root["viewer"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    ),
                )
            }
            "state" -> ComputerEvent.State(readState(root))
            "ack" -> ComputerEvent.Ack(root["id"]?.jsonPrimitive?.intOrNull, root["detail"]?.jsonPrimitive?.contentOrNull.orEmpty())
            "denied" -> ComputerEvent.Denied(root["id"]?.jsonPrimitive?.intOrNull, root["reason"]?.jsonPrimitive?.contentOrNull.orEmpty())
            "run_result" -> ComputerEvent.RunResult(
                id = root["id"]?.jsonPrimitive?.intOrNull,
                ok = root["ok"]?.jsonPrimitive?.booleanOrNull ?: false,
                exit = root["exit"]?.jsonPrimitive?.intOrNull ?: -1,
                output = root["output"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            "pong" -> ComputerEvent.Pong
            else -> ComputerEvent.Ignored
        }
    }.getOrDefault(ComputerEvent.Ignored)

    fun readState(root: JsonObject): ComputerState {
        val last = root["last"]?.let { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@let null
            ComputerAction(
                kind = obj["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                detail = obj["detail"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                x = obj["x"]?.jsonPrimitive?.intOrNull,
                y = obj["y"]?.jsonPrimitive?.intOrNull,
                at = obj["at"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L,
            )
        }
        return ComputerState(
            holder = root["holder"]?.jsonPrimitive?.contentOrNull ?: ComputerState.HOLDER_AGENT,
            viewer = root["viewer"]?.jsonPrimitive?.contentOrNull,
            busy = root["busy"]?.jsonPrimitive?.booleanOrNull ?: false,
            last = last,
        )
    }

    // ---- écriture ---------------------------------------------------------------

    private fun frame(type: String, id: Int?, block: JsonObjectBuilder.() -> Unit = {}): String =
        buildJsonObject {
            put("t", type)
            if (id != null) put("id", id)
            block()
        }.toString()

    fun hello(viewer: String): String = frame("hello", null) { put("viewer", viewer) }
    fun take(id: Int): String = frame("take", id)
    fun release(id: Int): String = frame("release", id)
    fun ping(id: Int): String = frame("ping", id)

    /**
     * Un appui.
     *
     * `space` dit dans quel espace sont `x` et `y` : `screen` = des pixels de l'écran du PC (ce
     * que l'app envoie : elle a mesuré l'image affichée chez elle avant de la ramener à l'écran),
     * `view` = des pixels de l'image envoyée, que le pont agrandit lui-même. Le dire évite une
     * mise à l'échelle faite deux fois — un clic qui part ailleurs.
     */
    fun tap(id: Int, x: Int, y: Int, button: Int = 1, space: String = SPACE_SCREEN): String = frame("tap", id) {
        put("x", x)
        put("y", y)
        put("button", button)
        put("space", space)
    }

    fun drag(id: Int, fromX: Int, fromY: Int, toX: Int, toY: Int,
             space: String = SPACE_SCREEN): String = frame("drag", id) {
        put("space", space)
        putJsonArray("from") { add(JsonPrimitive(fromX)); add(JsonPrimitive(fromY)) }
        putJsonArray("to") { add(JsonPrimitive(toX)); add(JsonPrimitive(toY)) }
    }

    fun scroll(id: Int, x: Int, y: Int, amount: Int,
               space: String = SPACE_SCREEN): String = frame("scroll", id) {
        put("x", x)
        put("y", y)
        put("amount", amount)
        put("space", space)
    }

    fun typeText(id: Int, text: String): String = frame("text", id) { put("text", text) }

    /**
     * Un **déplacement** du pointeur, sans clic.
     *
     * Message **additif** : un pont plus ancien ne le connaît pas et le refuse
     * (`unknown_action:motion`) — ce refus ne dit rien de l'écran, donc l'app l'avale, exactement
     * comme la taille d'image et la cadence. Un pont à jour déplace le vrai pointeur du PC ; l'app,
     * elle, dessine déjà le sien, si bien que le pilotage fonctionne même sans ce message.
     */
    fun motion(id: Int, x: Int, y: Int, space: String = SPACE_SCREEN): String = frame("motion", id) {
        put("x", x)
        put("y", y)
        put("space", space)
    }

    /**
     * Ce que l'app veut recevoir : la largeur de l'image, son quantificateur, et — **additif** —
     * un plafond de cadence.
     *
     * Le pont n'est pas obligé de dire oui : il borne (jamais moins de 480, jamais plus que son
     * propre plafond ni que l'écran du PC), et il le dit dans son accusé. Redemander la même chose
     * ne redémarre rien : une session qui se reconnecte ne doit pas faire clignoter la capture.
     *
     * `fps` est **additif** : un pont plus ancien ne lit que `width` et `quality` et ignore le
     * champ en trop — jamais une panne. Il sert à la « liaison économe », qui demande moins
     * d'images. `0` = aucun plafond.
     */
    fun stream(id: Int, width: Int, quality: Int, fps: Int = 0): String = frame("stream", id) {
        put("width", width)
        put("quality", quality)
        if (fps > 0) put("fps", fps)
    }

    /**
     * Le taux d'images demandé : haut pendant un geste, au repos autrement.
     *
     * C'est une demande, pas un ordre — le pont applique son propre plafond et garde la main.
     */
    fun rate(id: Int, moving: Boolean): String = frame("rate", id) { put("moving", moving) }


    fun key(id: Int, keys: String, approved: Boolean): String = frame("key", id) {
        put("keys", keys)
        put("approved", approved)
    }

    fun run(id: Int, command: String, approved: Boolean): String = frame("run", id) {
        put("command", command)
        put("approved", approved)
    }

    /**
     * Le refus du pont, dit à l'écran.
     *
     * Les raisons sont peu nombreuses et techniques ; l'écran, lui, doit dire *pourquoi* — et
     * surtout que ce n'est pas la faute du réseau. Les phrases restent des clés traduisibles.
     */
    fun denialMessage(reason: String): String = when (reason) {
        "not_holder" -> "Someone else has the hand."
        "taken_by_other" -> "Someone else has the hand."
        "needs_approval" -> "This needs your approval."
        "blocked" -> "The bridge refuses this command."
        "empty_text", "empty_command" -> "Nothing to send."
        "unknown_action" -> "The bridge doesn't know this action."
        else -> "The bridge refused this action."
    }
}
