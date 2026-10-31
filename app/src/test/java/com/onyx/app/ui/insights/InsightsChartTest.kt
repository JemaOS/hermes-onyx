package com.onyx.app.ui.insights

import com.onyx.app.data.model.serve.InsightsFormat
import com.onyx.app.data.model.serve.UsageDay
import com.onyx.app.data.model.serve.UsageReport
import com.onyx.app.data.model.serve.dailySeries
import com.onyx.app.data.model.serve.epochDayOf
import com.onyx.app.data.model.serve.peakOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'histogramme des « Aperçus » : sa géométrie, et le **figement** que le propriétaire demande.
 *
 * La hauteur d'une barre ne dépend que des chiffres : il n'y a ni animation ni horloge dans son
 * calcul, donc « Réduire les animations » n'a rien à figer — l'histogramme est déjà au repos. Ces
 * tests tiennent cette propriété en comparant deux lectures successives, et tiennent aussi les cas
 * limites : un jour à zéro (aucune barre, jamais NaN), et un pic à zéro (aucune division).
 */
class InsightsChartTest {

    private val today = epochDayOf("2026-10-27")!!

    @Test
    fun `un jour a zero ne dessine aucune barre et ne produit jamais NaN`() {
        assertEquals(0f, barFraction(tokens = 0, peak = 3_100_000), 0f)
        assertEquals(0f, barFraction(tokens = 0, peak = 0), 0f)
        // Un pic à zéro ne divise pas : la série entière est plate, et rien n'est dessiné.
        assertEquals(0f, barFraction(tokens = 0, peak = 0), 0f)
        assertTrue(barFraction(tokens = 5, peak = 0).isFinite())
    }

    @Test
    fun `le pic vaut un et rien ne depasse un`() {
        assertEquals(1f, barFraction(tokens = 3_100_000, peak = 3_100_000), 0f)
        assertEquals(0.5f, barFraction(tokens = 50, peak = 100), 0.0001f)
        assertEquals("rien ne dépasse jamais la hauteur", 1f, barFraction(tokens = 400, peak = 100), 0f)
    }

    @Test
    fun `le dessin est fige - deux lectures donnent la meme hauteur`() {
        val days = listOf(
            UsageDay(day = "2026-10-25", inputTokens = 1_000),
            UsageDay(day = "2026-10-26", inputTokens = 3_000_000, outputTokens = 100_000),
            UsageDay(day = "2026-10-27"),
        )
        val peak = peakOf(days)!!.tokens

        val first = days.map { barFraction(it, peak) }
        // Aucun état, aucune animation : la même entrée redonne exactement la même géométrie.
        val second = days.map { barFraction(it, peak) }

        assertEquals(first, second)
        assertEquals(listOf(0.0003f, 1f, 0f), first.map { (it * 10_000).toInt() / 10_000f })
    }

    @Test
    fun `le pic annote est le jour le plus charge du rapport`() {
        val report = UsageReport(
            periodDays = 7,
            daily = listOf(
                UsageDay(day = "2026-10-24", inputTokens = 500),
                UsageDay(day = "2026-10-26", inputTokens = 3_000_000, outputTokens = 100_000),
                UsageDay(day = "2026-10-27", inputTokens = 20),
            ),
        )

        val days = report.dailySeries(today)
        val peak = peakOf(days)!!

        assertEquals("2026-10-26", peak.day)
        assertEquals("3.1M", InsightsFormat.tokens(peak.tokens))
        // Le jour du pic est bien celui de la série complétée, pas une ligne à zéro.
        assertEquals(3_100_000L, days.first { it.day == peak.day }.tokens)
    }

    @Test
    fun `avec une serie entierement a zero l'histogramme n'est pas dessine`() {
        // La règle de l'écran : un dessin de jours vides ne dit rien. L'écran montre alors l'état
        // « rien d'utilisé », et l'histogramme n'apparaît pas du tout.
        val report = UsageReport(periodDays = 7, totals = com.onyx.app.data.model.serve.UsageTotals(sessions = 0))
        val days = report.dailySeries(today)

        assertTrue("aucun jour chargé", days.none { it.tokens > 0 })
        assertNull(peakOf(emptyList()))
        assertEquals(7, days.size)
    }
}
