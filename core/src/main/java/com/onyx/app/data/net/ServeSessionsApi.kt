package com.onyx.app.data.net

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/**
 * Une session telle que le registre du gateway la tient.
 *
 * Miroir de `HermesSessionsAPI.SessionSummary` côté référence
 * (Echo/Services/Transport/HermesSessionsTransport:296) : les mêmes champs, lus sur
 * `GET api/sessions`, et les mêmes dérivés (`totalTokens`, `cost`, `sourceLabel`, `displayTitle`).
 */
data class SessionSummary(
    val id: String,
    val title: String? = null,
    val preview: String? = null,
    val model: String? = null,
    val source: String? = null,
    /** Le dossier où la session travaille : c'est lui qui dit de quel projet elle est. */
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
    val totalTokens: Int get() = totalTokensOrNull ?: 0

    /**
     * Le total des jetons d'entrée et de sortie, ou `null` quand **aucun des deux** n'est connu.
     *
     * C'est le compteur qui mentait : `totalTokens` repliait deux valeurs absentes en `0`, et la
     * fiche affichait « Total 0 » sous une section qui avait l'air d'une mesure. Une absence se
     * lit « — ».
     */
    val totalTokensOrNull: Int?
        get() = if (inputTokens == null && outputTokens == null) null
        else (inputTokens ?: 0) + (outputTokens ?: 0)

    /** Ce que la session a coûté : le prix réel s'il est là, sinon l'estimation. */
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

    /** « ▲ 8.2k ▼ 1.1k · $0.01 » : la ligne de consommation d'une rangée, rien sans usage. */
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
         * Android ne tient pas de transport `api/sessions` : les compteurs viennent donc des
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

/** Les formats de chiffres de `SessionUsageView` : « $0 », « $%.4f », et les milliers de la référence. */
object SessionUsage {

    /** `compact(_:)` de la référence : 999, 8.2k, 1.20M. */
    fun compact(value: Int): String = when {
        value < 1000 -> value.toString()
        value < 1_000_000 -> String.format(Locale.US, "%.1fk", value / 1000.0)
        else -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
    }

    /** Le prix d'une session : « — » sans valeur, « $0 » à zéro, quatre décimales au-delà. */
    fun money(value: Double?): String = when {
        value == null -> "—"
        value == 0.0 -> "$0"
        else -> String.format(Locale.US, "$%.4f", value)
    }

    /**
     * Un compteur du registre, ou **« — » quand il n'y en a pas**.
     *
     * `(value ?: 0)` fabriquait un zéro : la fiche affichait « Entrée 0, Sortie 0, Total 0 » alors
     * que le registre n'avait rien dit de cette session, et ce zéro se lisait comme une mesure.
     * Un compteur absent se lit « — » ; un zéro **rendu par le serveur** reste « 0 ».
     */
    fun count(value: Int?): String = value?.toString() ?: "—"
}

/**
 * Les sessions du gateway : `GET api/sessions`.
 *
 * La même route et la même lecture tolérante des deux enveloppes (`sessions` / `data`) que
 * `HermesServeClient.listSessions` côté référence.
 */
class ServeSessionsApi(private val client: ServeClient) {

    /** Les sessions, les plus récentes d'abord. */
    suspend fun summaries(limit: Int = 100, profile: String? = null): List<SessionSummary> {
        val payload = client.request(
            path = "api/sessions",
            query = pageQuery(limit = limit, offset = 0, order = "recent", profile = profile),
        )
        val object_ = payload.asObject()
        val rows = object_?.array("sessions") ?: object_?.array("data") ?: payload.asArray() ?: emptyList()
        return rows.mapNotNull { element -> element.asObject()?.let(::sessionSummaryFromRow) }
    }

    /**
     * **Toutes** les sessions du registre, page après page.
     *
     * `GET api/sessions` plafonne `limit` à 100 (`Query(20, ge=0, le=100)` : « an unbounded limit
     * lets one request drag every session row out of SQLite in a single hit ») — une seule page ne
     * dit donc jamais plus de cent conversations, et la base en compte bien plus. On demande les
     * pages jusqu'à ce qu'une revienne **courte**, dans la limite de [maxPages] : c'est ce qui rend
     * la règle du groupe par défaut (« tout ce qu'aucun projet ne réclame ») vérifiable sur la base
     * entière et pas sur sa première page.
     *
     * Les identifiants sont dédoublonnés : une écriture pendant la lecture ne fait pas compter deux
     * fois la même conversation.
     */
    suspend fun allSummaries(pageSize: Int = 100, maxPages: Int = 20, profile: String? = null): List<SessionSummary> {
        val all = LinkedHashMap<String, SessionSummary>()
        for (page in 0 until maxPages) {
            val rows = page(pageSize, page * pageSize, profile)
            rows.forEach { summary -> all[summary.id] = summary }
            if (rows.size < pageSize) break
        }
        return all.values.toList()
    }

    /**
     * **Toutes** les sessions de plusieurs profils, fusionnées par identifiant.
     *
     * Une session est écrite dans la base **de son profil** : `/home/john/.hermes/state.db` pour
     * `default`, `/home/john/.hermes/profiles/<bot>/state.db` pour un bot. Une lecture qui oublie le
     * profil lit la base `default` et ne trouve donc **jamais** la ligne d'un bot — le défaut
     * signalé. Ici chaque profil est lu, et les lignes se réunissent ; un profil dont la lecture
     * échoue ne fait pas tomber les autres (il rend simplement ses lignes en moins).
     *
     * C'est le pendant registre de `ModelCatalog.fetchModelOptions(config, profile)`, qui lit déjà
     * les prix profil par profil : une seule façon de nommer un profil, pas deux.
     */
    suspend fun summariesIn(profiles: List<String>, pageSize: Int = 100, maxPages: Int = 20): List<SessionSummary> {
        val all = LinkedHashMap<String, SessionSummary>()
        for (profile in profiles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            allSummaries(pageSize, maxPages, profile).forEach { summary -> all[summary.id] = summary }
        }
        return all.values.toList()
    }

    /** Une page du registre : `offset` trie par date de création, ce qui ne bouge pas d'une page à l'autre. */
    private suspend fun page(limit: Int, offset: Int, profile: String? = null): List<SessionSummary> {
        val payload = client.request(
            path = "api/sessions",
            query = pageQuery(limit = limit, offset = offset, order = "created", profile = profile),
        )
        val object_ = payload.asObject()
        val rows = object_?.array("sessions") ?: object_?.array("data") ?: payload.asArray() ?: emptyList()
        return rows.mapNotNull { element -> element.asObject()?.let(::sessionSummaryFromRow) }
    }

    /** Une session par son identifiant, dans [profile] (ou le profil du serveur quand il est vide). */
    suspend fun summary(id: String, profile: String? = null): SessionSummary? =
        allSummaries(profile = profile).firstOrNull { it.id == id }

    /**
     * La ligne du registre d'une conversation, cherchée **dans les profils qui la portent**.
     *
     * C'est ce qui rend une lecture « par bot » : la session d'un bot vit dans la base de son
     * profil, et interroger la base `default` rendait une liste où sa ligne n'était pas — d'où un
     * écran qui concluait « le registre n'a pas répondu » sur une session parfaitement enregistrée.
     */
    suspend fun summaryIn(id: String, profiles: List<String>): SessionSummary? =
        summariesIn(profiles).firstOrNull { it.id == id }

    companion object {
        /**
         * Les paramètres d'une page du registre.
         *
         * Le profil n'est **pas** envoyé pour `default` : la route le réserve aux profils nommés
         * (`core` lit alors la base du processus, qui *est* le profil par défaut), exactement comme
         * `ServerConfig.effectiveProfile` écarte `""` et `"default"`.
         */
        fun pageQuery(limit: Int, offset: Int, order: String, profile: String?): Map<String, String> =
            buildMap {
                put("limit", limit.toString())
                put("offset", offset.toString())
                put("order", order)
                named(profile)?.let { put("profile", it) }
            }

        /** Le nom de profil qui se dit tel quel sur une route : `null` pour `""` et `default`. */
        fun named(profile: String?): String? =
            profile?.trim()?.takeIf { it.isNotEmpty() && !it.equals("default", ignoreCase = true) }
    }
}

/** Une ligne de `GET api/sessions`, lue champ par champ comme `SessionSummary` de la référence. */
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
 * `intOrNull` de kotlinx refuse « 12.0 » : sans cette tolérance, les compteurs d'une session
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
