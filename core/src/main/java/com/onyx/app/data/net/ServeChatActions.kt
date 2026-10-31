package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * Ce que la feuille d'actions d'une conversation interroge sur le serveur : sa **consommation**,
 * ses **processus en arrière-plan**, ses **points de restauration** (et leurs diffs), et son
 * **compte**.
 *
 * Toutes ces routes vivent sur le canal JSON-RPC du Dashboard (le même socket que
 * `session.active_list` et `subagent.list`) :
 *
 *  - `session.usage { session_id }` → les compteurs cumulés (jetons, cache, coût, contexte) ;
 *  - `session.context_breakdown { session_id }` → **ce qui remplit la fenêtre de contexte**
 *    (catégories + manifeste des fichiers lus) ;
 *  - `process.list { session_id }` → les processus en arrière-plan de la session, et
 *    `process.kill { session_id, process_id }` pour en arrêter un ;
 *  - `rollback.list { session_id }` → les points de restauration, `rollback.diff` pour **le diff**
 *    d'un point (ligne de compte + diff unifié), et `rollback.restore { session_id, hash }` ;
 *  - `usage.bars { profile }` → le **compte** (barre de plan, échéance de renouvellement).
 *
 * Aucun chiffre n'est inventé : un champ que le serveur ne renvoie pas reste `null` et s'affiche
 * « — », un coût absent se dit au lieu de se devine, et une route indisponible (ou `available:false`)
 * se **nomme** au lieu de peindre une carte plausible.
 *
 * **L'identifiant d'exécution.** Ces routes ne connaissent pas l'identifiant du **registre**
 * que l'app garde (`api-…`, celui des conversations) : le gateway cherche celui qu'on lui envoie
 * dans ses sessions **vivantes** et refuse l'autre en `4001 session not found`. Chaque lecture
 * demande donc d'abord à `session.resume` l'identifiant **d'exécution** de la session ([liveSessionId]),
 * et lit ensuite avec celui-là — sans quoi la feuille afficherait un échec sur une conversation
 * parfaitement ordinaire.
 */
data class ChatUsage(
    val model: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val cacheReadTokens: Int? = null,
    val cacheWriteTokens: Int? = null,
    /** `cache_hit_pct` du serveur : la part du cache dans les jetons d'entrée, en pour cent. */
    val cacheHitPercent: Int? = null,
    val totalTokens: Int? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val apiCalls: Int? = null,
    val contextUsed: Int? = null,
    val contextMax: Int? = null,
    val contextPercent: Int? = null,
    val contextEstimated: Boolean? = null,
    /** `context_source` du serveur : `provider_usage` (mesuré) ou `local_estimate` (estimé). */
    val contextSource: String? = null,
    val compressions: Int? = null,
    val activeSubagents: Int? = null,
    val estimatedCostUsd: Double? = null,
    val actualCostUsd: Double? = null,
    /** `cost_status` du serveur : `estimated` quand le prix vient des tarifs du modèle. */
    val costStatus: String? = null,
    /** Les catégories de `session.context_breakdown` : ce qui remplit la fenêtre. */
    val contextSlices: List<ChatContextSlice> = emptyList(),
    val contextFiles: List<ChatContextFile> = emptyList(),
) {
    /** Le prix retenu : le réel s'il est là, sinon l'estimation du serveur. `null` = pas de prix. */
    val costUsd: Double? get() = actualCostUsd ?: estimatedCostUsd

    /**
     * Vrai quand le serveur n'a rien dit **du tout** de cette conversation : la feuille le dit
     * plutôt que d'afficher une grille de zéros qui a l'air d'une mesure.
     */
    val isEmpty: Boolean
        get() = inputTokens == null && outputTokens == null && totalTokens == null &&
            promptTokens == null && apiCalls == null

    /**
     * La part du cache dans les jetons d'entrée, en pour cent.
     *
     * `cache_hit_pct` du serveur quand il le donne ; sinon calculée sur `cache_read_tokens` et les
     * jetons d'entrée — et `null` quand ni l'un ni l'autre n'est là (jamais 0 % par défaut).
     */
    fun cacheShare(): Int? {
        cacheHitPercent?.let { return it.coerceIn(0, 100) }
        val read = cacheReadTokens ?: return null
        val input = inputTokens ?: return null
        val baseline = input + read
        if (baseline <= 0) return null
        return ((read.toDouble() / baseline.toDouble()) * 100).toInt().coerceIn(0, 100)
    }
}

/** Une tranche de `session.context_breakdown` : un poste du contexte, en jetons. */
data class ChatContextSlice(
    val id: String,
    /** Le libellé du serveur (anglais), traduit à l'affichage. */
    val label: String,
    val tokens: Int,
)

/** Un fichier de contexte chargé (`session.context_breakdown.context_files`). */
data class ChatContextFile(
    val label: String,
    val path: String,
    val tokens: Int?,
    /** Les caractères du fichier — la « taille » que montre le bloc « fichiers lus ». */
    val chars: Int? = null,
    val loaded: Boolean,
)

/** Un processus en arrière-plan tel que `process.list` le rend. */
data class BackgroundProcess(
    val id: String,
    val command: String,
    val status: String,
    val exitCode: Int? = null,
    val completionReason: String? = null,
    val uptimeSeconds: Int? = null,
    /** Le pid système, quand le serveur le donne — la ligne « durée · pid · chemin ». */
    val pid: Int? = null,
    /** Le dossier de travail du processus. */
    val cwd: String? = null,
    /** La sortie connue (`output_tail`, sinon `output_preview`) — jamais inventée. */
    val output: String? = null,
) {
    val running: Boolean get() = status != "exited"

    /** « durée · pid 1234 · /chemin » : chaque morceau **réellement fourni**, jamais un zéro postiche. */
    fun metaLine(): String {
        val parts = mutableListOf<String>()
        ServeChatActions.duration(uptimeSeconds)?.let { parts += it }
        pid?.let { parts += "pid $it" }
        cwd?.takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.joinToString(" · ")
    }
}

/** Un point de restauration tel que `rollback.list` le rend. */
data class RestorePoint(
    val hash: String,
    val timestamp: String,
    val message: String,
) {
    /** L'identifiant court en monospace : les six premiers caractères. */
    val shortHash: String get() = hash.take(6)
}

/** Ce que `rollback.list` a répondu : le point de restauration du dossier de la session. */
data class RestorePointList(
    /** `false` : le serveur ne coche rien pour ce dossier — rien à restaurer, et il le dit. */
    val enabled: Boolean,
    val items: List<RestorePoint> = emptyList(),
)

/** La nature d'une ligne de diff unifié — c'est elle qui porte l'encre à l'affichage. */
enum class DiffLineKind { HUNK, ADD, REMOVE, FILE, CONTEXT }

/** Une ligne du diff d'un point de restauration. */
data class DiffLine(val kind: DiffLineKind, val text: String)

/**
 * Le diff d'un point de restauration (`rollback.diff`) : la ligne de compte **comptée sur le diff
 * réel**, puis les lignes du diff unifié. Un `stat` vide et des lignes vides veulent dire « le
 * serveur n'a pas donné de diff » — l'écran le dit, il n'invente pas une modification.
 */
data class RestoreDiff(
    /** Le `stat` brut du serveur (format git), conservé pour ne rien perdre. */
    val stat: String,
    val lines: List<DiffLine>,
) {
    val isEmpty: Boolean get() = lines.isEmpty() && stat.isBlank()

    val filesChanged: Int get() = lines.count { it.kind == DiffLineKind.FILE } / 2
    val insertions: Int get() = lines.count { it.kind == DiffLineKind.ADD }
    val deletions: Int get() = lines.count { it.kind == DiffLineKind.REMOVE }

    /** « 1 fichier modifié, 3 insertions(+), 1 suppression(−) » — compté sur le diff, en français. */
    fun countLine(): String? {
        if (isEmpty) return null
        val files = if (filesChanged <= 1) "$filesChanged fichier modifié" else "$filesChanged fichiers modifiés"
        val ins = "$insertions insertion${if (insertions > 1) "s" else ""}(+)"
        val del = "$deletions suppression${if (deletions > 1) "s" else ""}(−)"
        return "$files, $ins, $del"
    }
}

/**
 * Le **compte** tel que `usage.bars` le rend (forme fermée `{ok, available}` quand il n'y a rien).
 * `available:false` = le serveur ne publie pas de compte : la feuille le dit, elle ne peint ni
 * barre ni échéance.
 */
data class ChatAccount(
    val available: Boolean,
    val planName: String? = null,
    /** L'échéance de renouvellement, déjà mise en forme par le serveur. */
    val renewsDisplay: String? = null,
    /** La part du plan consommée, en pour cent (`plan_bar.pct_used`). */
    val planPercent: Int? = null,
    val planSpentDisplay: String? = null,
    val planTotalDisplay: String? = null,
    val planRemainingDisplay: String? = null,
)

/**
 * Les données de la feuille d'actions, par le canal du Dashboard.
 *
 * Le canal est construit paresseusement, comme ailleurs (`ChatViewModel`) : la feuille ne coûte un
 * socket que le jour où on ouvre une de ses trois listes.
 *
 * Chaque lecture rend un [Result] : un échec n'est pas une liste vide. L'écran distingue donc
 * « le serveur a répondu, il n'y a rien » de « la route n'existe pas / n'a pas répondu » — c'est
 * cette distinction qui fait une ligne honnête plutôt qu'un écran vide muet.
 */
class ServeChatActions(
    private val server: ServerConfig,
    /** Le canal déjà ouvert, quand l'appelant en a un (les tests s'en servent). */
    rpc: RpcChannel? = null,
) {
    private var channel: RpcChannel? = rpc

    private fun channel(): RpcChannel =
        channel ?: ServeRpc(server).asChannel().also { channel = it }

    /**
     * La consommation et le coût de cette conversation, et ce qui remplit son contexte.
     *
     * [cost] est le couple (estimé, réel) de `GET api/sessions` : `session.usage` ne porte **pas**
     * toujours de prix, et c'est cette source-là qui le donne. Quand aucun des deux ne le donne, le
     * prix reste absent — jamais zéro.
     */
    suspend fun usage(sessionId: String, cost: Pair<Double?, Double?>? = null): Result<ChatUsage> {
        // L'identifiant **d'exécution** d'abord : c'est le seul que ces routes connaissent.
        val params = sessionParams(liveSessionId(sessionId))
        // Les deux routes se lisent ensemble : la consommation d'un côté, les postes du contexte de
        // l'autre. La première est l'essentielle ; la seconde vient enrichir, et son absence ne
        // transforme pas une consommation lue en échec.
        val usage = runCatching { channel().call("session.usage", params, TIMEOUT) }
        val breakdown = runCatching { channel().call("session.context_breakdown", params, TIMEOUT) }
        return usage.map { usageOf(it, breakdown.getOrNull(), cost) }
    }

    /** Les processus en arrière-plan de cette conversation. */
    suspend fun processes(sessionId: String): Result<List<BackgroundProcess>> =
        runCatching { channel().call("process.list", sessionParams(liveSessionId(sessionId)), TIMEOUT) }
            .map { processesOf(it) }

    /** Arrête un processus ; rend le motif exact de l'échec, ou `null` quand c'est arrêté. */
    suspend fun stopProcess(sessionId: String, processId: String): String? {
        val result = runCatching {
            channel().call(
                "process.kill",
                buildJsonObject {
                    put("session_id", liveSessionId(sessionId))
                    put("process_id", processId)
                },
                TIMEOUT,
            )
        }.getOrElse { error -> return failureReason(error.message ?: error.toString()) }
        val object_ = result.asObject() ?: return OnyxLocalization["the server answered nothing"]
        return when (object_.str("status")) {
            "killed", "already_exited" -> null
            "not_found" -> OnyxLocalization["this process is no longer running"]
            else -> object_.strOrNull("error")
                ?: OnyxLocalization["the server refused to stop it"]
        }
    }

    /** Les points de restauration du dossier de cette conversation. */
    suspend fun restorePoints(sessionId: String): Result<RestorePointList> =
        runCatching { channel().call("rollback.list", sessionParams(liveSessionId(sessionId)), TIMEOUT) }
            .map { restorePointsOf(it) }

    /**
     * Le diff d'un point (`rollback.diff`) : la ligne de compte et le diff unifié. Une réponse vide
     * rend `null` — l'écran dira « le serveur n'a pas donné de diff », jamais une modification vide.
     */
    suspend fun diff(sessionId: String, hash: String): Result<RestoreDiff?> =
        runCatching {
            channel().call(
                "rollback.diff",
                buildJsonObject {
                    put("session_id", liveSessionId(sessionId))
                    put("hash", hash)
                },
                TIMEOUT,
            )
        }.map { answer -> diffOf(answer)?.takeUnless { it.isEmpty } }

    /** Restaure un point ; rend le motif exact de l'échec, ou `null` quand c'est restauré. */
    suspend fun restore(sessionId: String, hash: String): String? {
        val result = runCatching {
            channel().call(
                "rollback.restore",
                buildJsonObject {
                    put("session_id", liveSessionId(sessionId))
                    put("hash", hash)
                },
                RESTORE_TIMEOUT,
            )
        }.getOrElse { error -> return failureReason(error.message ?: error.toString()) }
        val object_ = result.asObject() ?: return OnyxLocalization["the server answered nothing"]
        if (object_.bool("success") == true) return null
        return object_.strOrNull("error")
            ?: object_.strOrNull("reason")
            ?: OnyxLocalization["the server refused to restore it"]
    }

    /**
     * Le compte (`usage.bars`). La route répond `{ok:true, available:false}` quand le serveur ne
     * publie rien : c'est dit, ce n'est pas une barre vide.
     */
    suspend fun account(): Result<ChatAccount> =
        runCatching { channel().call("usage.bars", buildJsonObject {}, TIMEOUT) }
            .map { accountOf(it) }

    fun close() {
        (channel as? AutoCloseable)?.let { runCatching { it.close() } }
        channel = null
    }

    /**
     * L'identifiant **d'exécution** de la session — celui sous lequel le gateway l'enregistre.
     *
     * Les routes de la feuille (`session.usage`, `session.context_breakdown`, `process.list`,
     * `rollback.list` / `rollback.diff`) ne connaissent que lui : le gateway cherche l'identifiant
     * reçu dans ses sessions **vivantes** et refuse celui du **registre** (`api-…`, la clé que la
     * conversation garde) en `4001 session not found`
     * (`tui_gateway/session_registry.py::_sess_nowait`).
     *
     * On le demande donc à `session.resume` — la route et l'identifiant de reprise que
     * `ChatControlsApi` et `HermesServeChat.openSession` emploient déjà, `omit_messages` en plus
     * pour ne rien rapatrier du fil : le serveur rend la session qu'il tenait déjà, ou celle qu'il
     * vient de rouvrir. Un serveur qui refuse la reprise rend l'identifiant qu'on avait —
     * la lecture échouera alors **franchement** plutôt que de mentir.
     */
    suspend fun liveSessionId(stored: String): String {
        if (stored.isBlank()) return stored
        val answer = runCatching {
            channel().call(
                "session.resume",
                buildJsonObject {
                    put("session_id", stored)
                    put("omit_messages", true)
                },
                TIMEOUT,
            )
        }.getOrNull()
        return liveSessionIdOf(answer, stored)
    }

    companion object {
        /** Le temps d'un aller-retour de lecture : au-delà, la feuille dit qu'elle n'a pas eu de réponse. */
        const val TIMEOUT = 15_000L

        /** Restaurer touche le dossier : un peu plus de marge. */
        const val RESTORE_TIMEOUT = 30_000L

        fun sessionParams(sessionId: String): JsonObject = buildJsonObject {
            if (sessionId.isNotBlank()) put("session_id", sessionId)
        }

        /**
         * L'identifiant d'exécution qu'une réponse de `session.resume` rend, ou celui qu'on avait.
         *
         * Le gateway nomme la session vivante `session_id` et rappelle sous `resumed` la clé du
         * registre qu'on lui a passée (`_resume_response`, `_resume_reuse_live_locked`) : c'est ce
         * premier champ qui compte ici. Pur, donc mesurable — c'est lui que le test interroge.
         */
        fun liveSessionIdOf(answer: JsonElement?, stored: String): String =
            answer.asObject()?.strOrNull("session_id")?.takeIf { it.isNotBlank() } ?: stored

        /** Les refus du gateway qui veulent dire « cette session n'est pas vivante ». */
        val SESSION_NOT_LIVE = listOf("session not found", "not in memory", "not found")

        /** Les refus qui veulent dire « ce serveur ne connaît pas cette route ». */
        val NO_SUCH_READING = listOf("unknown method", "method not found", "not supported")

        /**
         * Le motif d'un échec de lecture, **dit en clair et en français**.
         *
         * Le gateway refuse l'identifiant du registre en `4001 session not found` : rendu tel quel,
         * ce code anglais s'affichait sous une phrase française, sans rien dire de ce qu'il faut
         * faire — exactement le défaut signalé. Les deux refus connus sont donc traduits ; les
         * autres sont encadrés par une phrase française qui garde la raison du serveur à côté,
         * plutôt que de la taire.
         */
        fun failureReason(raw: String): String {
            val text = raw.trim()
            return when {
                text.isEmpty() -> OnyxLocalization["the server gave no reason"]
                // « method not found » d'abord : il contient « not found », et le lire comme une
                // session morte enverrait la personne rouvrir une conversation bien vivante.
                NO_SUCH_READING.any { text.contains(it, ignoreCase = true) } ->
                    OnyxLocalization["this server does not offer this reading"]
                SESSION_NOT_LIVE.any { text.contains(it, ignoreCase = true) } ->
                    OnyxLocalization["the server no longer holds this conversation's live session"]
                else -> OnyxLocalization.format("the server refused to read it: %1\$s", text)
            }
        }

        /** `session.usage` (+ `session.context_breakdown`) → la consommation, sans rien inventer. */
        fun usageOf(
            usage: JsonElement?,
            breakdown: JsonElement?,
            cost: Pair<Double?, Double?>? = null,
        ): ChatUsage {
            val rows = usage.asObject()
            val breakdownRows = breakdown.asObject()
            return ChatUsage(
                model = rows?.strOrNull("model") ?: breakdownRows?.strOrNull("model"),
                inputTokens = rows?.int("input"),
                outputTokens = rows?.int("output"),
                reasoningTokens = rows?.int("reasoning"),
                // Le serveur nomme le cache `cache_read` / `cache_write` ; certaines versions
                // l'écrivent `cache_read_tokens`. On lit les deux, jamais un zéro inventé.
                cacheReadTokens = rows?.int("cache_read") ?: rows?.int("cache_read_tokens"),
                cacheWriteTokens = rows?.int("cache_write") ?: rows?.int("cache_write_tokens"),
                cacheHitPercent = rows?.int("cache_hit_pct"),
                totalTokens = rows?.int("total"),
                promptTokens = rows?.int("prompt"),
                completionTokens = rows?.int("completion"),
                apiCalls = rows?.int("calls"),
                contextUsed = rows?.int("context_used") ?: breakdownRows?.int("context_used"),
                contextMax = rows?.int("context_max") ?: breakdownRows?.int("context_max"),
                contextPercent = rows?.int("context_percent") ?: breakdownRows?.int("context_percent"),
                contextEstimated = rows?.bool("context_estimated") ?: breakdownRows?.bool("context_estimated"),
                contextSource = rows?.strOrNull("context_source") ?: breakdownRows?.strOrNull("context_source"),
                compressions = rows?.int("compressions"),
                activeSubagents = rows?.int("active_subagents"),
                // Le prix : celui de `api/sessions` (réel, estimé), sinon `cost_usd` de
                // `session.usage` — et rien du tout quand aucun ne le donne.
                estimatedCostUsd = cost?.first ?: rows?.looseDouble("cost_usd"),
                actualCostUsd = cost?.second,
                costStatus = rows?.strOrNull("cost_status"),
                contextSlices = contextSlicesOf(breakdown),
                contextFiles = contextFilesOf(breakdown),
            )
        }

        /** Les catégories de `session.context_breakdown`, dans l'ordre du serveur. */
        fun contextSlicesOf(breakdown: JsonElement?): List<ChatContextSlice> =
            breakdown.asObject()?.array("categories").orEmpty().mapNotNull { element ->
                val row = element.asObject() ?: return@mapNotNull null
                val label = row.strOrNull("label") ?: row.strOrNull("id") ?: return@mapNotNull null
                ChatContextSlice(
                    id = row.strOrNull("id") ?: label,
                    label = label,
                    tokens = row.int("tokens") ?: 0,
                )
            }

        /** Le manifeste des fichiers de contexte (`context_files`) — les fichiers lus. */
        fun contextFilesOf(breakdown: JsonElement?): List<ChatContextFile> =
            breakdown.asObject()?.array("context_files").orEmpty().mapNotNull { element ->
                val row = element.asObject() ?: return@mapNotNull null
                ChatContextFile(
                    label = row.strOrNull("label") ?: row.strOrNull("path") ?: return@mapNotNull null,
                    path = row.strOrNull("path").orEmpty(),
                    tokens = row.int("est_tokens"),
                    chars = row.int("chars"),
                    loaded = row.bool("loaded") != false,
                )
            }

        /** `process.list` → les processus, ceux qui tournent d'abord, puis les plus récents. */
        fun processesOf(answer: JsonElement?): List<BackgroundProcess> {
            val rows = answer.asObject()?.array("processes").orEmpty().mapNotNull { element ->
                val row = element.asObject() ?: return@mapNotNull null
                val id = row.strOrNull("session_id") ?: row.strOrNull("process_id") ?: return@mapNotNull null
                BackgroundProcess(
                    id = id,
                    command = row.strOrNull("command").orEmpty(),
                    status = row.strOrNull("status") ?: "running",
                    exitCode = row.int("exit_code"),
                    completionReason = row.strOrNull("completion_reason"),
                    uptimeSeconds = row.int("uptime_seconds"),
                    pid = row.int("pid"),
                    cwd = row.strOrNull("cwd"),
                    // La sortie vient du serveur (`output_tail`, sinon `output_preview`) : ni
                    // reconstruite, ni inventée.
                    output = row.strOrNull("output_tail")
                        ?.takeIf { it.isNotBlank() }
                        ?: row.strOrNull("output_preview")?.takeIf { it.isNotBlank() },
                )
            }
            return rows.sortedWith(compareByDescending<BackgroundProcess> { it.running })
        }

        /** `rollback.list` → les points de restauration du dossier. */
        fun restorePointsOf(answer: JsonElement?): RestorePointList {
            val object_ = answer.asObject() ?: return RestorePointList(enabled = false)
            val items = object_.array("checkpoints").mapNotNull { element ->
                val row = element.asObject() ?: return@mapNotNull null
                val hash = row.strOrNull("hash") ?: return@mapNotNull null
                RestorePoint(
                    hash = hash,
                    timestamp = row.strOrNull("timestamp").orEmpty(),
                    message = row.strOrNull("message").orEmpty(),
                )
            }
            return RestorePointList(enabled = object_.bool("enabled") == true, items = items)
        }

        /** `rollback.diff` → le diff unifié, lu ligne à ligne. `null` quand le serveur n'en donne pas. */
        fun diffOf(answer: JsonElement?): RestoreDiff? {
            val object_ = answer.asObject() ?: return null
            val stat = object_.strOrNull("stat").orEmpty()
            val raw = object_.strOrNull("diff").orEmpty()
            if (raw.isBlank() && stat.isBlank()) return null
            val lines = raw.split("\n").dropLastWhile { it.isEmpty() }.map { text ->
                val kind = when {
                    text.startsWith("@@") -> DiffLineKind.HUNK
                    text.startsWith("+++") || text.startsWith("---") -> DiffLineKind.FILE
                    text.startsWith("+") -> DiffLineKind.ADD
                    text.startsWith("-") -> DiffLineKind.REMOVE
                    else -> DiffLineKind.CONTEXT
                }
                DiffLine(kind, text)
            }
            return RestoreDiff(stat = stat, lines = lines)
        }

        /** `usage.bars` → le compte. `available:false` (ou réponse vide) reste `available:false`. */
        fun accountOf(answer: JsonElement?): ChatAccount {
            val object_ = answer.asObject() ?: return ChatAccount(available = false)
            if (object_.bool("available") != true) return ChatAccount(available = false)
            val planBar = object_.obj("plan_bar")
            return ChatAccount(
                available = true,
                planName = object_.strOrNull("plan_name"),
                renewsDisplay = object_.strOrNull("renews_display") ?: object_.strOrNull("renews_at"),
                planPercent = planBar?.int("pct_used"),
                planSpentDisplay = planBar?.strOrNull("spent_display"),
                planTotalDisplay = planBar?.strOrNull("total_display"),
                planRemainingDisplay = planBar?.strOrNull("remaining_display"),
            )
        }

        /** « 8,2 k », « 1,20 M » : les mêmes formats que la ligne de consommation des sessions. */
        fun compact(value: Int?): String = value?.let { SessionUsage.compact(it) } ?: DASH

        /** Le prix d'une conversation : « — » quand le serveur n'en donne aucun. */
        fun money(value: Double?): String = value?.let { SessionUsage.money(it) } ?: DASH

        /** Le tiret cadratin d'une valeur que le serveur ne fournit pas. */
        const val DASH = "\u2014"

        /** Le pourcentage, ou « — ». */
        fun percent(value: Int?): String = value?.let { "$it %" } ?: DASH

        /** Un entier, ou « — ». */
        fun count(value: Int?): String = value?.toString() ?: DASH

        /**
         * La durée d'une tâche en arrière-plan, en français simple : « 42 s », « 3 min »,
         * « 1 h 12 min ». Négative ou absente : rien.
         */
        fun duration(seconds: Int?): String? {
            val total = seconds?.takeIf { it >= 0 } ?: return null
            val hours = total / 3600
            val minutes = (total % 3600) / 60
            return when {
                hours > 0 -> String.format(Locale.FRANCE, "%d h %02d min", hours, minutes)
                minutes > 0 -> String.format(Locale.FRANCE, "%d min", minutes)
                else -> String.format(Locale.FRANCE, "%d s", total)
            }
        }

        /** La taille d'un fichier lu : « 1,2 ko », « 3,4 Mo ». `null` → rien, jamais un 0 inventé. */
        fun fileSize(chars: Int?): String? {
            val n = chars?.takeIf { it >= 0 } ?: return null
            return when {
                n >= 1_048_576 -> String.format(Locale.FRANCE, "%.1f Mo", n / 1_048_576.0)
                n >= 1024 -> String.format(Locale.FRANCE, "%.1f ko", n / 1024.0)
                else -> "$n octets"
            }
        }

        /** Le sujet d'un processus en arrière-plan : sa première ligne, bornée pour tenir en une. */
        fun processSubject(command: String): String {
            val first = command.lineSequence().firstOrNull()?.trim().orEmpty()
            return if (first.length > 120) first.take(119) + "\u2026" else first
        }
    }
}
