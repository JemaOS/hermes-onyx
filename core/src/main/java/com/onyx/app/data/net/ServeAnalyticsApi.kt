package com.onyx.app.data.net

import com.onyx.app.data.model.serve.ModelUsage
import com.onyx.app.data.model.serve.SkillUsage
import com.onyx.app.data.model.serve.ToolUsage
import com.onyx.app.data.model.serve.UsageDay
import com.onyx.app.data.model.serve.UsageReport
import com.onyx.app.data.model.serve.UsageTotals
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Ce qu'un appel à l'analytique a donné.
 *
 * Trois issues, et **trois seulement** : un rapport, une route que ce tableau de bord ne sert pas,
 * ou une panne. La distinction compte pour l'écran des aperçus : une route absente ou une panne ne
 * doivent **jamais** se peindre comme « 0 $ » — un coût faux serait pire que pas de coût.
 */
sealed interface UsageOutcome {

    /** Le serveur a rendu un rapport. Il peut être vide (rien consommé sur la période) : c'est un fait. */
    data class Ready(val report: UsageReport) : UsageOutcome

    /** Cette version du tableau de bord ne sert pas `api/analytics/usage` (404/405/501). */
    data object Missing : UsageOutcome

    /** Le serveur a répondu autre chose qu'un rapport : réseau, session, forme illisible. */
    data class Failed(val message: String) : UsageOutcome
}

/**
 * L'analytique du tableau de bord : ce que le profil a consommé.
 *
 * La route est `GET api/analytics/usage?days=N[&profile=P]` — celle des Analyses du Dashboard, qui
 * lit la base de sessions du profil : les jours, les modèles, les cumuls, les outils et les
 * compétences. Elle est **profil par profil** : `profile` désigne la base visée, et son absence
 * vise le profil du processus du tableau de bord. Elle ne couvre donc jamais « tous les profils »
 * d'un coup — l'écran le dit sous ses chiffres.
 */
class ServeAnalyticsApi(private val client: ServeClient) {

    /**
     * L'usage des [days] derniers jours (le serveur borne à 1…365), pour [profile] ou le profil du
     * serveur.
     */
    suspend fun usage(days: Int, profile: String? = null): UsageOutcome {
        val clamped = days.coerceIn(1, 365)
        val query = buildMap {
            put("days", clamped.toString())
            // Le profil nommé, sinon celui du serveur choisi — même règle que les autres routes.
            val named = profile?.takeIf { it.isNotBlank() } ?: client.profile
            named?.takeIf { it.isNotBlank() && !it.equals("default", true) }?.let { put("profile", it) }
        }
        val payload = try {
            client.request("api/analytics/usage", query = query)
        } catch (error: TransportError.Http) {
            // Une route que le serveur ne connaît pas n'est pas une panne : c'est une version plus
            // ancienne du tableau de bord, et l'écran doit le dire au lieu d'inventer des chiffres.
            if (error.status in ROUTE_ABSENT) return UsageOutcome.Missing
            return UsageOutcome.Failed(error.userMessage())
        } catch (error: TransportError) {
            return UsageOutcome.Failed(error.userMessage())
        }
        val report = usageReportFrom(payload.asObject())
            ?: return UsageOutcome.Failed(UNREADABLE)
        return UsageOutcome.Ready(report)
    }

    private companion object {
        /** Les statuts qui veulent dire « cette route n'existe pas ici ». */
        val ROUTE_ABSENT = setOf(404, 405, 501)

        const val UNREADABLE = "unreadable analytics report"
    }
}

/**
 * Le rapport d'un flux JSON, ou `null` quand ce n'en est pas un.
 *
 * Le refus est délibéré : un flux sans `totals` **ni** `daily` n'est pas un rapport d'usage, quand
 * bien même il serait un objet JSON valide (une page d'erreur, un manifeste, la réponse d'une autre
 * route). Le rendre comme « tout à zéro » ferait afficher un coût de 0 $ — faux. Un rapport présent
 * mais vide, lui, est légitime : c'est le serveur qui dit « rien sur cette période ».
 */
fun usageReportFrom(root: JsonObject?): UsageReport? {
    if (root == null) return null
    if (!root.containsKey("totals") && !root.containsKey("daily")) return null

    val daily = root.array("daily").mapNotNull { element ->
        val row = element.asObject() ?: return@mapNotNull null
        val day = row.firstString("day", "date") ?: return@mapNotNull null
        UsageDay(
            day = day.take(10),
            inputTokens = row.long("input_tokens") ?: 0L,
            outputTokens = row.long("output_tokens") ?: 0L,
            cacheReadTokens = row.long("cache_read_tokens") ?: 0L,
            estimatedCost = row.double("estimated_cost") ?: 0.0,
            actualCost = row.double("actual_cost") ?: 0.0,
            sessions = row.int("sessions") ?: 0,
            apiCalls = row.int("api_calls") ?: 0,
        )
    }

    val models = root.array("by_model").mapNotNull { element ->
        val row = element.asObject() ?: return@mapNotNull null
        val model = row.firstString("model") ?: return@mapNotNull null
        ModelUsage(
            model = model,
            inputTokens = row.long("input_tokens") ?: 0L,
            outputTokens = row.long("output_tokens") ?: 0L,
            estimatedCost = row.double("estimated_cost") ?: 0.0,
            sessions = row.int("sessions") ?: 0,
            apiCalls = row.int("api_calls") ?: 0,
        )
    }

    val totalsRow = root.obj("totals")
    val totals = UsageTotals(
        input = totalsRow?.long("total_input") ?: 0L,
        output = totalsRow?.long("total_output") ?: 0L,
        cacheRead = totalsRow?.long("total_cache_read") ?: 0L,
        estimatedCost = totalsRow?.double("total_estimated_cost") ?: 0.0,
        actualCost = totalsRow?.double("total_actual_cost") ?: 0.0,
        sessions = totalsRow?.int("total_sessions") ?: 0,
        apiCalls = totalsRow?.int("total_api_calls") ?: 0,
    )

    val tools = root.array("tools").mapNotNull { element ->
        val row = element.asObject() ?: return@mapNotNull null
        val tool = row.firstString("tool", "tool_name") ?: return@mapNotNull null
        ToolUsage(tool = tool, count = row.int("count") ?: 0)
    }

    // Le bloc des compétences est enveloppé (`skills.top_skills`), mais une liste nue est acceptée :
    // la même route a servi les deux formes.
    val skillsBlock = root.obj("skills")
    val topSkills = (skillsBlock?.array("top_skills") ?: root.array("top_skills")).mapNotNull { element ->
        val row = element.asObject() ?: return@mapNotNull null
        val skill = row.firstString("skill", "name") ?: return@mapNotNull null
        // `total_count` quand le serveur le donne ; sinon le plus grand des deux compteurs connus.
        val count = row.int("total_count")
            ?: maxOf(row.int("view_count") ?: 0, row.int("manage_count") ?: 0)
        SkillUsage(skill = skill, count = count)
    }

    // `period_days` absent : la fenêtre se déduit des jours reçus, jamais d'une valeur inventée.
    val periodDays = root.int("period_days")
        ?: daily.size.takeIf { it > 0 }
        ?: 0

    return UsageReport(
        daily = daily,
        byModel = models,
        totals = totals,
        periodDays = periodDays,
        tools = tools,
        topSkills = topSkills,
    )
}

/** Un nombre décimal d'un objet JSON, entier ou flottant. */
private fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
