package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lecture de `GET api/analytics/usage` — la seule source des chiffres de l'écran « Aperçus ».
 *
 * Le point qui compte : **rien n'est inventé**. Un flux qui n'est pas un rapport est refusé (jamais
 * rendu « tout à zéro », ce qui afficherait un coût faux), une route absente est dite absente, et un
 * rapport partiel n'est lu que pour ce qu'il porte.
 *
 * La forme attendue est celle du serveur : `daily` (jour, jetons d'entrée et de sortie, jetons lus
 * dans le cache, coût estimé et coût réel, sessions, appels d'API), `by_model`, `totals`,
 * `period_days`, `skills.top_skills` et `tools`.
 */
class ServeAnalyticsApiTest {

    @Test
    fun `le rapport du serveur est lu champ par champ`() = runBlocking {
        val api = analyticsApi {
            it.respond(200, REPORT)
        }

        val outcome = api.usage(days = 7)

        val report = (outcome as UsageOutcome.Ready).report
        assertEquals(7, report.periodDays)
        assertEquals(2, report.daily.size)
        val day = report.daily.first()
        assertEquals("2026-10-26", day.day)
        assertEquals(2_760_000L, day.inputTokens)
        assertEquals(340_000L, day.outputTokens)
        assertEquals(3_100_000L, day.tokens)
        assertEquals(900_000L, day.cacheReadTokens)
        assertEquals(4.21, day.estimatedCost, 0.0001)
        assertEquals(0.0, day.actualCost, 0.0001)
        // Le coût du jour est l'estimation quand le fournisseur n'a rien facturé.
        assertEquals(4.21, day.cost, 0.0001)
        assertEquals(12, day.sessions)

        assertEquals(26_300_000L, report.totals.tokens)
        assertEquals(18, report.totals.sessions)
        // 1 234 567 lus dans le cache sur 28 000 000 d'entrées + cache : 4 %.
        assertEquals(4, report.totals.cachePercent)
        assertEquals(38.4, report.totals.cost, 0.0001)

        assertEquals(listOf("deepseek/deepseek-v4.1-flash", "qwen/qwen3-32b"), report.byModel.map { it.model })
        assertEquals(24_600_000L, report.byModel.first().tokens)

        assertEquals(listOf("terminal", "read_file"), report.tools.map { it.tool })
        assertEquals(listOf("hermes-agent" to 12), report.topSkills.map { it.skill to it.count })
    }

    @Test
    fun `une route que le serveur ne sert pas est dite absente, jamais zero`() = runBlocking {
        val api = analyticsApi { it.respond(404, """{"error":"not found"}""") }

        assertEquals(UsageOutcome.Missing, api.usage(days = 30))
    }

    @Test
    fun `une panne du serveur n'est pas un rapport vide`() = runBlocking {
        val api = analyticsApi { it.respond(503, """{"detail":"corrupt store"}""") }

        val outcome = api.usage(days = 30)
        assertTrue("une panne reste une panne", outcome is UsageOutcome.Failed)
    }

    @Test
    fun `un flux qui n'est pas un rapport est refuse`() = runBlocking {
        // Un objet JSON valide, mais aucune des deux clés d'un rapport : le lire comme « zéro »
        // afficherait un coût de 0 $ — faux.
        val api = analyticsApi { it.respond(200, """{"ok":true,"models":[]}""") }

        val outcome = api.usage(days = 30)
        assertTrue("refusé plutôt que zéro", outcome is UsageOutcome.Failed)
    }

    @Test
    fun `un rapport present mais vide reste un fait`() = runBlocking {
        val api = analyticsApi {
            it.respond(200, """{"daily":[],"by_model":[],"totals":{"total_sessions":0},"period_days":7}""")
        }

        val report = (api.usage(days = 7) as UsageOutcome.Ready).report
        assertTrue("rien consommé : l'écran le dit", report.isEmpty)
        assertEquals(7, report.periodDays)
    }

    @Test
    fun `un rapport partiel ne fait pas tomber l'ecran`() = runBlocking {
        // Seulement les cumuls : la fenêtre se déduit des jours reçus (aucun), donc zéro — jamais
        // d'une valeur inventée.
        val api = analyticsApi { it.respond(200, """{"totals":{"total_input":900,"total_output":100}}""") }

        val report = (api.usage(days = 30) as UsageOutcome.Ready).report
        assertEquals(1_000L, report.totals.tokens)
        assertEquals(0, report.periodDays)
        assertEquals(0, report.daily.size)
        // Des entrées sans rien de servi par le cache : la part existe et vaut zéro — c'est un
        // fait, pas une absence. Une part sans **aucune** entrée vaut `null` (voir UsageSeriesTest).
        assertEquals(0, report.totals.cachePercent)
    }

    @Test
    fun `les lignes sans identifiant sont ecartees`() = runBlocking {
        val api = analyticsApi {
            it.respond(
                200,
                """{"daily":[{"day":"2026-10-26","input_tokens":5},
                    {"input_tokens":99}],
                    "by_model":[{"model":"a","input_tokens":1},{"input_tokens":7}],
                    "tools":[{"tool":"terminal","count":2},{"count":1}]}""",
            )
        }

        val report = (api.usage(days = 7) as UsageOutcome.Ready).report
        assertEquals(listOf("2026-10-26"), report.daily.map { it.day })
        assertEquals(listOf("a"), report.byModel.map { it.model })
        assertEquals(listOf("terminal"), report.tools.map { it.tool })
    }

    @Test
    fun `le cout reel du fournisseur prend le pas sur l'estimation`() = runBlocking {
        val api = analyticsApi {
            it.respond(
                200,
                """{"daily":[{"day":"2026-10-26","estimated_cost":4.2,"actual_cost":1.57}],
                    "totals":{"total_estimated_cost":4.2,"total_actual_cost":1.57}}""",
            )
        }

        val report = (api.usage(days = 7) as UsageOutcome.Ready).report
        assertEquals(1.57, report.totals.cost, 0.0001)
        assertTrue("le rapport dit que c'est facturé", report.totals.actualCost > 0.0)
        assertEquals(1.57, report.daily.first().cost, 0.0001)
    }

    @Test
    fun `les jours demandes sont bornes a un an et le profil accompagne l'appel`() = runBlocking {
        var seen: String = ""
        val api = analyticsApi {
            seen = it.url.toString()
            it.respond(200, """{"daily":[],"totals":{},"period_days":365}""")
        }

        api.usage(days = 5_000)
        assertTrue("days borné à 365 : $seen", seen.contains("days=365"))
    }

    @Test
    fun `le profil choisi est celui du serveur quand aucun n'est nomme`() = runBlocking {
        var seen: String = ""
        val api = analyticsApi(profile = "kilo") {
            seen = it.url.toString()
            it.respond(200, """{"daily":[],"totals":{},"period_days":7}""")
        }

        api.usage(days = 7)
        assertTrue("le profil part sur la route : $seen", seen.contains("profile=kilo"))
    }

    private fun analyticsApi(
        profile: String? = null,
        respond: (Request) -> Response,
    ): ServeAnalyticsApi {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/auth/password-login") -> request.respond(200, """{"ok":true}""")
                request.url.encodedPath.endsWith("/api/sessions") -> request.respond(200, """{"sessions":[]}""")
                else -> respond(request)
            }
        }.build()
        val config = ServerConfig(
            id = "test",
            serveBaseUrl = "https://hermes.test",
            serveUsername = "user",
            servePassword = "x",
            hermesProfile = profile.orEmpty(),
        )
        return ServeAnalyticsApi(ServeClient(config, http))
    }

    private fun Request.respond(code: Int, body: String): Response = Response.Builder()
        .request(this)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
        .build()

    private companion object {
        /** La forme rendue par `GET api/analytics/usage`, telle que le serveur l'écrit. */
        const val REPORT = """
        {
          "daily": [
            {"day": "2026-10-26", "input_tokens": 2760000, "output_tokens": 340000,
             "cache_read_tokens": 900000, "reasoning_tokens": 0,
             "estimated_cost": 4.21, "actual_cost": 0, "sessions": 12, "api_calls": 41},
            {"day": "2026-10-27", "input_tokens": 22400000, "output_tokens": 800000,
             "cache_read_tokens": 434567, "reasoning_tokens": 0,
             "estimated_cost": 34.19, "actual_cost": 0, "sessions": 6, "api_calls": 22}
          ],
          "by_model": [
            {"model": "deepseek/deepseek-v4.1-flash", "input_tokens": 24000000,
             "output_tokens": 600000, "estimated_cost": 30.0, "sessions": 12, "api_calls": 50},
            {"model": "qwen/qwen3-32b", "input_tokens": 1400000, "output_tokens": 540000,
             "estimated_cost": 8.4, "sessions": 6, "api_calls": 13}
          ],
          "totals": {
            "total_input": 25160000, "total_output": 1140000, "total_cache_read": 1234567,
            "total_reasoning": 0, "total_estimated_cost": 38.4, "total_actual_cost": 0,
            "total_sessions": 18, "total_api_calls": 63
          },
          "period_days": 7,
          "skills": {"summary": {"total_skill_loads": 12},
                     "top_skills": [{"skill": "hermes-agent", "view_count": 12, "manage_count": 0,
                                     "total_count": 12, "percentage": 100}]},
          "tools": [{"tool": "terminal", "count": 128, "percentage": 71},
                    {"tool": "read_file", "count": 52, "percentage": 29}]
        }
        """
    }
}
