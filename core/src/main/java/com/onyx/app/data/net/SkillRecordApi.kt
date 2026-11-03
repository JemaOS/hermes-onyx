package com.onyx.app.data.net

import com.onyx.app.data.skill.ActionJournal
import com.onyx.app.data.skill.SkillJournal
import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les trois routes du compagnon `onyx-record` — « démonstration → skill ».
 *
 * Miroir de [ServeSkillsApi] : un fichier `*Api.kt`, un seul transport ([ServeClient.request]),
 * aucun chemin écrit ailleurs. Les chemins sont ceux que le greffon monte réellement
 * (`plugins/onyx-record/dashboard/plugin_api.py`), pas une abréviation d'attente :
 *
 * | Nom | Méthode | Chemin |
 * |---|---|---|
 * | `skills.record.start` | `POST` | `api/plugins/onyx-record/skills/record/start` |
 * | `skills.record.stop`  | `POST` | `api/plugins/onyx-record/skills/record/stop` |
 * | `skills.draft`        | `POST` | `api/plugins/onyx-record/skills/draft` |
 *
 * ### Les refus sont des valeurs, pas des messages
 *
 * Le greffon répond `{"detail": {"code": …}}`, et `code` est la **première** clé du corps parce
 * que [ServeClient] ne garde que 300 caractères d'une réponse en erreur (spec §1). [RecordFailure]
 * relit ce code, et l'écran affiche **sa** phrase : le `message` anglais du serveur n'est jamais
 * recopié. Un code inconnu, lui, est montré tel quel — un refus qu'on ne sait pas nommer ne doit
 * pas devenir un « Réessayez » anonyme.
 *
 * ### Ce que `stop` rend, et pourquoi on le relit
 *
 * `stop` rend le journal **canonique** (D3) : ce que le serveur a validé et borné. [RecordStop]
 * le relit dans [ActionJournal] pour que l'aperçu montre ce que le brouillon recevra — pas ce que
 * le téléphone croyait avoir envoyé.
 */
class SkillRecordApi(private val client: ServeClient) {

    /** `POST skills/record/start` — ouvre un bail. */
    suspend fun start(
        bridgeEndpoint: String,
        bridgeSession: String,
        discardPending: Boolean = false,
    ): RecordLease {
        val payload = client.request(
            path = START,
            method = "POST",
            body = buildJsonObject {
                put(
                    "bridge",
                    buildJsonObject {
                        put("endpoint", bridgeEndpoint)
                        put("session", bridgeSession)
                    },
                )
                if (discardPending) put("discard_pending", true)
            },
        ).asObject() ?: throw RecordFailure.malformed()

        val sessionId = payload.strOrNull("session_id")
            ?: throw RecordFailure.malformed()
        return RecordLease(
            sessionId = sessionId,
            startedAt = payload.decimal("started_at") ?: 0.0,
            deadlineAt = payload.decimal("deadline_at") ?: 0.0,
            maxSeconds = payload.int("max_seconds") ?: DEFAULT_MAX_SECONDS,
            graceSeconds = payload.int("grace_seconds") ?: DEFAULT_GRACE_SECONDS,
        )
    }

    /**
     * `POST skills/record/stop` — ferme le bail et rend le journal canonique.
     *
     * [journal] nul est le seul chemin d'annulation (spec §4) : le serveur rend
     * `end_reason = discarded`, sans journal. Un second `stop` dans la fenêtre de collecte rend le
     * même octet — l'appel est idempotent, il ne double pas le journal.
     */
    suspend fun stop(sessionId: String, journal: JsonElement? = null): RecordStop {
        val payload = client.request(
            path = STOP,
            method = "POST",
            body = buildJsonObject {
                put("session_id", sessionId)
                if (journal != null) put("journal", journal)
            },
        ).asObject() ?: throw RecordFailure.malformed()

        val canonical = payload.obj("journal")
        return RecordStop(
            sessionId = payload.strOrNull("session_id") ?: sessionId,
            endReason = payload.str("end_reason"),
            actions = payload.int("actions") ?: 0,
            bytes = payload.int("bytes") ?: 0,
            durationMs = payload.long("duration_ms") ?: 0L,
            truncated = payload.bool("truncated") == true,
            dropped = payload.int("dropped") ?: 0,
            journalJson = canonical,
            journal = canonical?.let { runCatching { SkillJournal.fromJson(it) }.getOrNull() },
        )
    }

    /**
     * `POST skills/draft` — le brouillon Markdown, **sans état** (D4).
     *
     * Le journal est envoyé en clair plutôt que par `journal_id` : la route est alors sans état
     * et ne dépend pas des 120 s de collecte. Un journal expiré côté bail reste donc convertible.
     */
    suspend fun draft(journal: JsonElement, nameHint: String? = null): RecordDraft {
        val payload = client.request(
            path = DRAFT,
            method = "POST",
            body = buildJsonObject {
                put("journal", journal)
                if (!nameHint.isNullOrBlank()) put("name_hint", nameHint.trim())
            },
        ).asObject() ?: throw RecordFailure.malformed()

        return RecordDraft(
            name = payload.str("name"),
            title = payload.str("title"),
            steps = payload.int("steps") ?: 0,
            markdown = payload.str("markdown"),
            review = payload.array("review").mapNotNull { element ->
                val row = element.asObject() ?: return@mapNotNull null
                RecordReview(
                    step = row.int("step") ?: 0,
                    reason = row.str("reason"),
                    line = row.int("line") ?: 0,
                )
            },
            warnings = payload.stringList("warnings"),
            journalBytes = payload.int("journal_bytes") ?: 0,
        )
    }

    companion object {
        /** Les chemins, tels que le greffon les monte. */
        const val START: String = "api/plugins/onyx-record/skills/record/start"
        const val STOP: String = "api/plugins/onyx-record/skills/record/stop"
        const val DRAFT: String = "api/plugins/onyx-record/skills/draft"

        /** Le plafond du lot, quand le serveur ne le dit pas. */
        const val DEFAULT_MAX_SECONDS: Int = 600

        /** La fenêtre de collecte d'un journal arrêté. */
        const val DEFAULT_GRACE_SECONDS: Int = 120

        /** Vrai si l'échec est une route que ce serveur ne sert pas (spec §8.1). */
        fun isMissingRoute(error: Throwable?): Boolean =
            error is RecordFailure && error.routeMissing
    }
}

/** Le bail d'un enregistrement, tel que `start` l'a ouvert. */
data class RecordLease(
    val sessionId: String,
    val startedAt: Double,
    val deadlineAt: Double,
    val maxSeconds: Int,
    val graceSeconds: Int,
    /**
     * Vrai quand le bail a été **adopté** sur un `409 already_recording` : l'app ne l'a pas ouvert,
     * elle a rejoint celui du serveur (spec §7.2). Le chronomètre, lui, ne redémarre pas de zéro.
     */
    val adopted: Boolean = false,
)

/** Le retour de `stop`. [journal] est nul quand le bail a été annulé (`discarded`). */
data class RecordStop(
    val sessionId: String,
    val endReason: String,
    val actions: Int,
    val bytes: Int,
    val durationMs: Long,
    val truncated: Boolean,
    val dropped: Int,
    val journalJson: JsonObject?,
    val journal: ActionJournal?,
)

/** Une étape à relire, telle que `skills.draft` la liste. */
data class RecordReview(
    val step: Int,
    val reason: String,
    val line: Int,
)

/** Le brouillon rendu par `skills.draft` (l'enveloppe de §5, dans la forme du convertisseur du lot). */
data class RecordDraft(
    val name: String,
    val title: String,
    val steps: Int,
    val markdown: String,
    val review: List<RecordReview>,
    val warnings: List<String>,
    val journalBytes: Int,
)

/**
 * **Un refus du greffon**, réduit à son code de contrat et aux champs que l'écran emploie.
 *
 * [status] porte le statut HTTP, parce que `410` et `404` veulent dire deux choses différentes :
 * un bail expiré se raconte (« la démonstration est restée sur cet appareil »), une route absente
 * aussi (« ce serveur ne sait pas… »). Les confondre ferait chercher une mise à jour inutile.
 */
class RecordFailure(
    val code: String,
    val status: Int,
    val sessionId: String? = null,
    val deadlineAt: Double? = null,
    val expiresAt: Double? = null,
) : Exception(code) {

    /** Vrai pour un `404` : le Dashboard ne monte pas le greffon, ou il est plus ancien. */
    val routeMissing: Boolean get() = status == 404 || code == CODE_MISSING_ROUTE

    /**
     * Le bail que le serveur dit déjà ouvert, quand il a donné de quoi le rejoindre.
     *
     * `already_recording` porte `session_id` et `deadline_at` (voir `ECARTS.md` §7) ; le client
     * **adopte** ce bail au lieu d'échouer (spec §7.2) — sinon un écran rouvert resterait bloqué
     * jusqu'à la fin des dix minutes.
     */
    fun adoptLease(nowSeconds: Double): RecordLease? {
        val id = sessionId ?: return null
        if (code != "already_recording") return null
        val deadline = deadlineAt ?: (nowSeconds + SkillRecordApi.DEFAULT_MAX_SECONDS)
        val span = (deadline - nowSeconds).coerceIn(1.0, SkillRecordApi.DEFAULT_MAX_SECONDS.toDouble())
        return RecordLease(
            sessionId = id,
            startedAt = deadline - span,
            deadlineAt = deadline,
            maxSeconds = span.toInt(),
            graceSeconds = SkillRecordApi.DEFAULT_GRACE_SECONDS,
            adopted = true,
        )
    }

    companion object {
        /** Le code maison d'une route absente — jamais écrit par le greffon, posé ici. */
        const val CODE_MISSING_ROUTE: String = "route_missing"

        /** Un `200` dont le corps ne dit pas ce que la route doit dire. */
        internal fun malformed(): RecordFailure = RecordFailure("bad_response", 200)

        /**
         * Le refus porté par une exception du transport, ou `null` quand ce n'est pas un refus.
         *
         * Un serveur injoignable, un délai, une adresse invalide : ce ne sont **pas** des refus du
         * greffon (spec §8.2). L'écran garde alors la phrase du transport, qui dit la panne réelle.
         */
        fun from(error: Throwable): RecordFailure? {
            if (error is RecordFailure) return error
            if (error !is TransportError.Http) return null
            return parse(error.status, error.body)
        }

        /**
         * Lit `{"detail": {"code": …}}` sans jamais recopier le corps.
         *
         * Le code est la seule chose dont l'écran a besoin ; `session_id`, `deadline_at` et
         * `expires_at` servent à adopter un bail ou à dater une expiration. Le `message` anglais
         * du serveur n'est **pas** gardé : l'écran ne l'affiche jamais (spec §1).
         */
        fun parse(status: Int, body: String): RecordFailure {
            val root = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(body.take(4000))
            }.getOrNull().asObject()
            val detail = root?.obj("detail") ?: root
            val code = detail?.strOrNull("code")
                ?: if (status == 404) CODE_MISSING_ROUTE else "http_$status"
            return RecordFailure(
                code = code,
                status = status,
                sessionId = detail?.strOrNull("session_id"),
                deadlineAt = detail?.decimal("deadline_at"),
                expiresAt = detail?.decimal("expires_at"),
            )
        }
    }
}

/** La route visée, pour choisir la phrase d'une route absente (spec §8.1). */
enum class RecordRoute(val missingKey: String) {
    START("This server can't record a demonstration (route missing)."),
    STOP("This server can't close a recording (route missing)."),
    DRAFT("This server can't turn a demonstration into a skill (route missing). The journal stays on this device."),
}

/**
 * **La phrase de l'écran pour un refus** — jamais le `message` du serveur.
 *
 * Trois familles, et elles ne se mélangent pas : une route absente parle de la fonction, un conflit
 * d'état parle de ce qui est ouvert, et un code que l'écran ne connaît pas est **cité tel quel**
 * (`Le serveur a refusé : <code>.`) plutôt que remplacé par un texte vague.
 */
fun recordFailureMessage(failure: RecordFailure, route: RecordRoute): String = when (failure.code) {
    RecordFailure.CODE_MISSING_ROUTE -> OnyxLocalization[route.missingKey]
    "already_recording" -> OnyxLocalization["A recording is already open on this server."]
    "journal_pending" -> OnyxLocalization["A demonstration is waiting to be turned into a skill."]
    "not_recording" -> OnyxLocalization["No recording is open."]
    "unknown_session" -> OnyxLocalization["This server doesn't know that recording."]
    "bridge_mismatch" -> OnyxLocalization["That recording belongs to another bridge."]
    "session_expired" -> OnyxLocalization["The recording window has expired. Your demonstration is still on this device."]
    "still_recording" -> OnyxLocalization["The demonstration is still being recorded."]
    "no_journal" -> OnyxLocalization["Nothing to turn into a skill: the demonstration wasn't recorded."]
    "journal_too_large" -> OnyxLocalization["The demonstration is too long to send."]
    "bad_bridge" -> OnyxLocalization["The bridge address can't be used for a recording."]
    "recorder_unavailable" -> OnyxLocalization["The recording server isn't available."]
    else -> OnyxLocalization.format("The server refused: %1\$s.", failure.code)
}
