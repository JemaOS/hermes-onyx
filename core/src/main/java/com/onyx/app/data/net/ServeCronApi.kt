package com.onyx.app.data.net

import com.onyx.app.data.model.serve.CronJob
import com.onyx.app.data.model.serve.CronRun
import com.onyx.app.data.model.serve.DeliveryTarget
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les tâches planifiées du gateway, par le Dashboard.
 *
 * Miroir de `CronBackend` côté référence (Echo/Services/HermesAutomation:142) pour sa moitié
 * `hermes serve` : `api/cron/jobs`, avec `profile=all` — une tâche peut viser n'importe quel
 * profil, et la liste les montre toutes.
 */
class ServeCronApi(private val client: ServeClient) {

    /** `GET api/cron/jobs?profile=…` — `all` par défaut, un profil pour les horaires d'un bot. */
    suspend fun jobs(profile: String = "all"): List<CronJob> {
        val payload = client.request("api/cron/jobs", query = mapOf("profile" to profile))
        return parseCronJobs(payload)
    }

    /** `GET api/cron/delivery-targets`. */
    suspend fun deliveryTargets(): List<DeliveryTarget> {
        val payload = client.request("api/cron/delivery-targets")
        val rows = payload.asObject()?.array("targets") ?: payload.asArray()
        return rows.orEmpty().mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val id = obj.firstString("id") ?: return@mapNotNull null
            DeliveryTarget(
                id = id,
                name = obj.firstString("name", "title", "label") ?: id,
                available = obj["available"].asBool() ?: true,
            )
        }
    }

    /** `POST api/cron/jobs`. */
    suspend fun create(title: String, schedule: String, prompt: String, deliverTo: String?, profile: String? = null) {
        client.request(
            path = "api/cron/jobs",
            method = "POST",
            query = cronProfileQuery(profile),
            body = buildJsonObject {
                // Le gateway attend `name` et `deliver`, comme le Dashboard.
                put("name", title)
                put("schedule", schedule)
                put("prompt", prompt)
                if (!deliverTo.isNullOrEmpty()) put("deliver", deliverTo)
            },
        )
    }

    /** `PUT api/cron/jobs/{id}` — le serveur attend les changements sous `updates`. */
    suspend fun update(
        id: String,
        title: String,
        schedule: String,
        prompt: String,
        deliverTo: String?,
        profile: String? = null,
    ) {
        val updates = buildJsonObject {
            put("name", title)
            put("schedule", schedule)
            put("prompt", prompt)
            if (!deliverTo.isNullOrEmpty()) put("deliver", deliverTo)
        }
        client.request(
            path = "api/cron/jobs/$id",
            method = "PUT",
            query = cronProfileQuery(profile),
            body = buildJsonObject { put("updates", updates) },
        )
    }

    suspend fun pause(id: String, profile: String? = null) {
        client.request(
            "api/cron/jobs/$id/pause",
            method = "POST",
            query = cronProfileQuery(profile),
            body = client.emptyBody(),
        )
    }

    suspend fun resume(id: String, profile: String? = null) {
        client.request(
            "api/cron/jobs/$id/resume",
            method = "POST",
            query = cronProfileQuery(profile),
            body = client.emptyBody(),
        )
    }

    /** `POST api/cron/jobs/{id}/trigger` — 10 min : la tâche tourne pour de vrai. */
    suspend fun trigger(id: String, profile: String? = null) {
        client.request(
            "api/cron/jobs/$id/trigger",
            method = "POST",
            query = cronProfileQuery(profile),
            body = client.emptyBody(),
            timeoutSeconds = 600,
        )
    }

    suspend fun delete(id: String, profile: String? = null) {
        client.request(
            "api/cron/jobs/$id",
            method = "DELETE",
            query = cronProfileQuery(profile),
            body = client.emptyBody(),
        )
    }

    /** `GET api/cron/jobs/{id}/runs?limit=20`. */
    suspend fun runs(id: String, profile: String? = null): List<CronRun> {
        val payload = client.request(
            "api/cron/jobs/$id/runs",
            query = cronProfileQuery(profile, mapOf("limit" to "20")),
        )
        val rows = payload.asObject()?.array("runs") ?: payload.asArray()
        return rows.orEmpty().mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            CronRun(
                at = obj.firstString("at", "started_at", "time").orEmpty(),
                state = obj.firstString("state", "status").orEmpty(),
                output = obj.strOrNull("output") ?: obj.strOrNull("summary"),
            )
        }
    }

    /** Un corps de brouillon, prêt à être repris par l'éditeur. */
    fun draft(title: String, schedule: String, prompt: String, deliverTo: String?): JsonObject = buildJsonObject {
        put("name", title)
        put("schedule", schedule)
        put("prompt", prompt)
        if (!deliverTo.isNullOrEmpty()) put("deliver", deliverTo)
    }
}

/** Une query porte toujours le profil propriétaire de la tâche, pas seulement celui de la liste. */
internal fun cronProfileQuery(profile: String?, extra: Map<String, String> = emptyMap()): Map<String, String> =
    extra + profile?.takeIf { it.isNotEmpty() }?.let { mapOf("profile" to it) }.orEmpty()

/** Normalise les trois formes renvoyées par le Dashboard, comme `CronJob.init` côté référence. */
internal fun parseCronJobs(payload: JsonElement): List<CronJob> {
    val rows = payload.asObject()?.array("jobs") ?: payload.asArray()
    return rows.orEmpty().mapNotNull { element ->
        val obj = element.asObject() ?: return@mapNotNull null
        val id = obj.firstString("id", "job_id") ?: return@mapNotNull null
        val enabled = obj["enabled"].asBool() ?: true
        val rawState = obj.firstString("state", "status").orEmpty()
        val nextRunAt = obj["next_run_at"].cronDateMillis()
        val lastRunAt = obj["last_run_at"].cronDateMillis()
        CronJob(
            id = id,
            title = obj.firstString("name", "title").orEmpty(),
            prompt = obj.firstString("prompt", "prompt_preview").orEmpty(),
            schedule = obj.firstString("schedule_display", "schedule", "cron", "expression")
                ?: obj.obj("schedule")?.str("display").orEmpty(),
            botName = obj.strOrNull("bot_name") ?: obj.strOrNull("profile"),
            state = when (rawState) {
                "completed", "error" -> rawState
                else -> if (enabled) "scheduled" else "paused"
            },
            paused = rawState == "paused" || !enabled,
            nextRun = obj.firstString("next_run", "next") ?: nextRunAt?.toString(),
            lastRun = obj.firstString("last_run", "last") ?: lastRunAt?.toString(),
            lastError = obj.strOrNull("last_error"),
            deliverTo = obj.firstString("deliver", "deliver_to", "target"),
            model = obj.strOrNull("model"),
            nextRunAt = nextRunAt,
            lastRunAt = lastRunAt,
            lastStatus = obj.strOrNull("last_status"),
        )
    }
}

private fun JsonElement?.cronDateMillis(): Long? {
    val numeric = asLong()
    if (numeric != null) {
        return if (kotlin.math.abs(numeric) < 100_000_000_000L) numeric * 1000 else numeric
    }
    val text = asString() ?: return null
    return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
}
