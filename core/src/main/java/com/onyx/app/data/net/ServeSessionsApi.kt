package com.onyx.app.data.net

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/**
 * Une session telle que le registre du gateway la tient.
 *
 * Comme `HermesSessionsAPI.SessionSummary` 
 *: les mêmes champs, lus sur
 * `GET api/sessions`, et les mêmes dérivés (`totalTokens`, `cost`, `sourceLabel`, `displayTitle`).
 */
data class SessionSummary(
    val id: String,
    val title: String? = null,
    val preview: String? = null,
    val model: String? = null,
    val source: String? = null,
    /** Le dossier où la session travaille: c'est lui qui dit de quel projet elle est. */
    val cwd: String? = null,
    val messageCount: Int? = null,
    val toolCallCount: Int? = null,
    val apiCallCount: Int? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val cacheReadTokens: Int? = null,
    val cacheWriteTokens: Int? = null,
    val estimatedCostUsd: Double? = null,
    val actualCostUsd: Double? = null,
    val startedAt: Double? = null,
    val lastActive: Double? = null,
) {
    val totalTokens: Int get() = (inputTokens ?: 0) + (outputTokens ?: 0)

    /** Ce que la session a coûté: le prix réel s'il est là, sinon l'estimation. */
    val cost: Double? get() = actualCostUsd ?: estimatedCostUsd

    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: preview?.takeIf { it.isNotBlank() }?.take(80)
            ?: id

    val lastActiveMillis: Long?
        get() = (lastActive ?: startedAt)?.let { seconds -> (seconds * 1000).toLong() }

    /** Le libellé de l'origine, mot pour mot de `SessionSummary.sourceLabel`. */
    val sourceLabel: String
        get() = when (source.orEmpty()) {
            "api_server", "api-server" -> "Onyx / API"
            "cli" -> "CLI"
            "" -> "?"
            else -> source.orEmpty().replace('_', ' ')
                .split(' ')
                .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        }

    /** « ▲ 8.2k ▼ 1.1k · $0.01 »: la ligne de consommation d'une rangée, rien sans usage. */
    val usageLine: String?
        get() {
            if (totalTokens <= 0) return null
            val parts = mutableListOf("\u25B2 ${SessionUsage.compact(inputTokens ?: 0)} \u25BC ${SessionUsage.compact(outputTokens ?: 0)}")
            cost?.takeIf { it > 0 }?.let { parts += String.format(Locale.US, "$%.3f", it) }
            return parts.joinToString(" · ")
        }

    companion object {
        /**
         * La consommation d'une conversation locale, faute de registre côté gateway.
         *
         * Android ne tient pas de transport `api/sessions`: les compteurs viennent donc des
         * messages du fil, dont chacun porte l'`usage` remonté par le flux. Les champs que le
         * flux ne donne pas (raisonnement, coût) restent absents plutôt que d'être inventés.
         */
        fun of(conversation: Conversation): SessionSummary {
            val usages = conversation.messages.mapNotNull { it.usage }
            val input = usages.sumOf { it.input }
            val output = usages.sumOf { it.output }
            return SessionSummary(
                id = conversation.serverSessionId ?: conversation.id,
                title = conversation.title,
                preview = conversation.messages.lastOrNull()?.text,
                messageCount = conversation.messages.count { it.role != Role.SYSTEM },
                toolCallCount = conversation.messages.sumOf { message -> message.tools.size },
                inputTokens = input.takeIf { it > 0 },
                outputTokens = output.takeIf { it > 0 },
                lastActive = (conversation.updatedAt / 1000).toDouble(),
            )
        }
    }
}

/** Les formats de chiffres de `SessionUsageView`: « $0 », « $%.4f », et les milliers. */
object SessionUsage {

    /** `compact(_:)`: 999, 8.2k, 1.20M. */
    fun compact(value: Int): String = when {
        value < 1000 -> value.toString()
        value < 1_000_000 -> String.format(Locale.US, "%.1fk", value / 1000.0)
        else -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
    }

    /** Le prix d'une session: « — » sans valeur, « $0 » à zéro, quatre décimales au-delà. */
    fun money(value: Double?): String = when {
        value == null -> "—"
        value == 0.0 -> "$0"
        else -> String.format(Locale.US, "$%.4f", value)
    }

    /** Un compteur entier du registre, `(x ?? 0)` comme le `row(_:_:)`. */
    fun count(value: Int?): String = (value ?: 0).toString()
}

/**
 * Les sessions du gateway: `GET api/sessions`.
 *
 * La même route et la même lecture tolérante des deux enveloppes (`sessions` / `data`) que
 * `HermesServeClient.listSessions`.
 */
class ServeSessionsApi(private val client: ServeClient) {

    /** Les sessions, les plus récentes d'abord. */
    suspend fun summaries(limit: Int = 100): List<SessionSummary> {
        val payload = client.request(
            path = "api/sessions",
            query = mapOf("limit" to limit.toString(), "offset" to "0", "order" to "recent"),
        )
        val object_ = payload.asObject()
        val rows = object_?.array("sessions") ?: object_?.array("data") ?: payload.asArray() ?: emptyList()
        return rows.mapNotNull { element -> element.asObject()?.let(::sessionSummaryFromRow) }
    }

    /** Une session par son identifiant, ou `null` si le registre ne la connaît plus. */
    suspend fun summary(id: String): SessionSummary? = summaries().firstOrNull { it.id == id }
}

/** Une ligne de `GET api/sessions`, lue champ par champ comme `SessionSummary`. */
fun sessionSummaryFromRow(row: JsonObject): SessionSummary? {
    val id = row.firstString("id", "session_id", "resolved_id") ?: return null
    return SessionSummary(
        id = id,
        title = row.strOrNull("title"),
        preview = row.strOrNull("preview"),
        model = row.strOrNull("model"),
        source = row.strOrNull("source"),
        cwd = row.strOrNull("cwd"),
        messageCount = row.looseInt("message_count"),
        toolCallCount = row.looseInt("tool_call_count"),
        apiCallCount = row.looseInt("api_call_count"),
        inputTokens = row.looseInt("input_tokens"),
        outputTokens = row.looseInt("output_tokens"),
        reasoningTokens = row.looseInt("reasoning_tokens"),
        cacheReadTokens = row.looseInt("cache_read_tokens"),
        cacheWriteTokens = row.looseInt("cache_write_tokens"),
        estimatedCostUsd = row.looseDouble("estimated_cost_usd"),
        actualCostUsd = row.looseDouble("actual_cost_usd"),
        startedAt = row.looseDouble("started_at"),
        lastActive = row.looseDouble("last_active"),
    )
}

/**
 * Un entier du registre, lu même quand le serveur le donne en flottant (`12.0`).
 *
 * `intOrNull` de kotlinx refuse « 12.0 »: sans cette tolérance, les compteurs d'une session
 * rendue par certaines versions du Dashboard disparaîtraient silencieusement.
 */
internal fun JsonObject.looseInt(key: String): Int? {
    val element: JsonElement = this[key] ?: return null
    element.asInt()?.let { return it }
    return element.asString()?.toDoubleOrNull()?.toInt()
}

/** Un flottant du registre, entier ou non. */
internal fun JsonObject.looseDouble(key: String): Double? {
    val element: JsonElement = this[key] ?: return null
    return element.asString()?.toDoubleOrNull()
}
