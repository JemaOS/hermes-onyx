package com.onyx.app.data.model.serve

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La série jour par jour des « Aperçus » : c'est elle qui donne à l'histogramme son axe régulier.
 *
 * Le serveur n'envoie **que les jours où il s'est passé quelque chose** : un jour sans usage n'est
 * pas une ligne à zéro, c'est une ligne absente. L'écran doit donc la fabriquer — sans quoi le mois
 * d'un utilisateur qui a travaillé trois jours se dessinerait comme trois barres collées, et
 * l'absence de barre se confondrait avec « pas de données ».
 */
class UsageSeriesTest {

    private val today = epochDayOf("2026-10-27")!!

    @Test
    fun `les jours sans usage sont combles par des lignes a zero`() {
        val report = UsageReport(
            periodDays = 7,
            daily = listOf(
                UsageDay(day = "2026-10-21", inputTokens = 100, outputTokens = 20),
                UsageDay(day = "2026-10-27", inputTokens = 5_000, outputTokens = 900),
            ),
        )

        val series = report.dailySeries(today)

        assertEquals(7, series.size)
        assertEquals("2026-10-21", series.first().day)
        assertEquals("2026-10-27", series.last().day)
        assertEquals("5 jours vides", 5, series.count { it.tokens == 0L })
        assertEquals(120L, series.first().tokens)
        // Un jour vide n'est pas un jour manquant : la date est là, les chiffres sont à zéro.
        assertEquals("2026-10-22", series[1].day)
        assertEquals(0L, series[1].tokens)
        assertEquals(0.0, series[1].cost, 0.0001)
    }

    @Test
    fun `un jour rapporte apres aujourd'hui n'est jamais coupe`() {
        // L'horloge de l'appareil peut être en retard sur celle du serveur : la fenêtre recule
        // alors sur ce que le serveur a réellement rapporté, au lieu de le perdre.
        val report = UsageReport(
            periodDays = 7,
            daily = listOf(
                UsageDay(day = "2026-10-21", inputTokens = 10),
                UsageDay(day = "2026-11-03", inputTokens = 10),
            ),
        )

        val series = report.dailySeries(today)

        assertEquals("2026-11-03", series.last().day)
        assertEquals(7, series.size)
    }

    @Test
    fun `un jour plus vieux que la periode recule toute la fenetre`() {
        // Le serveur a rendu un jour antérieur à la fenêtre demandée : la fenêtre **recule** de
        // toute sa longueur pour le garder, plutôt que de perdre une donnée qu'il a écrite. Elle
        // reste une période de `period_days` jours, et l'axe reste régulier.
        val report = UsageReport(
            periodDays = 7,
            daily = listOf(UsageDay(day = "2026-10-15", inputTokens = 10)),
        )

        val series = report.dailySeries(today)

        assertEquals("2026-10-15", series.first().day)
        assertEquals("2026-10-21", series.last().day)
        assertEquals(7, series.size)
        assertEquals(10L, series.sumOf { it.tokens })
    }

    @Test
    fun `la fenetre suit period_days du serveur, pas la periode demandee`() {
        val report = UsageReport(periodDays = 30, daily = listOf(UsageDay(day = "2026-10-27", inputTokens = 1)))

        val series = report.dailySeries(today)

        assertEquals(30, series.size)
        assertEquals("2026-09-28", series.first().day)
    }

    @Test
    fun `le pic est le jour le plus charge, et il n'existe pas sans serie`() {
        val days = listOf(
            UsageDay(day = "2026-10-25", inputTokens = 10),
            UsageDay(day = "2026-10-26", inputTokens = 3_000_000, outputTokens = 100_000),
            UsageDay(day = "2026-10-27", inputTokens = 20),
        )

        assertEquals("2026-10-26", peakOf(days)?.day)
        assertNull(peakOf(emptyList()))
    }

    @Test
    fun `une date illisible est rendue telle quelle`() {
        assertEquals("pas une date", shortDate("pas une date"))
        assertNull(epochDayOf("2026-13-45"))
        assertNull(epochDayOf("hier"))
    }

    @Test
    fun `la date courte suit la langue de l'app`() {
        assertEquals("26 oct.", shortDate("2026-10-26", Locale.FRANCE))
        assertEquals("26 Oct", shortDate("2026-10-26", Locale.ENGLISH))
        assertEquals("31 déc.", shortDate("2026-12-31", Locale.FRANCE))
        assertEquals("1 janv.", shortDate("2027-01-01", Locale.FRANCE))
    }

    @Test
    fun `les jetons s'ecrivent sans decimale inutile`() {
        assertEquals("0", InsightsFormat.tokens(0))
        assertEquals("999", InsightsFormat.tokens(999))
        assertEquals("1.2k", InsightsFormat.tokens(1_234))
        assertEquals("3.1M", InsightsFormat.tokens(3_100_000))
        assertEquals("12k", InsightsFormat.tokens(12_000))
    }

    @Test
    fun `un montant s'ecrit en dollars, sous le centime dit moins d'un centime`() {
        assertEquals("\$0.00", InsightsFormat.usd(0.0))
        assertEquals("\$1.57", InsightsFormat.usd(1.57))
        assertEquals("\$38.40", InsightsFormat.usd(38.4))
        assertEquals("<\$0.01", InsightsFormat.usd(0.004))
        assertEquals("\$0.00", InsightsFormat.usd(Double.NaN))
    }

    @Test
    fun `une part sans entree ne s'affiche pas comme zero`() {
        assertEquals("—", InsightsFormat.percent(null))
        assertEquals("4 %", InsightsFormat.percent(4))
        assertNull(UsageTotals().cachePercent)
        assertEquals(4, UsageTotals(input = 24_000_000, cacheRead = 1_000_000).cachePercent)
    }

    @Test
    fun `le cout est le vrai du fournisseur des qu'il existe`() {
        assertTrue("estimé quand rien n'est facturé", !InsightsFormat.isBilled(0.0))
        assertTrue("facturé dès que le vrai est connu", InsightsFormat.isBilled(0.01))
        assertEquals(2.5, UsageDay(day = "2026-10-26", estimatedCost = 9.9, actualCost = 2.5).cost, 0.0001)
        assertEquals(9.9, UsageDay(day = "2026-10-26", estimatedCost = 9.9).cost, 0.0001)
    }

    @Test
    fun `un rapport sans consommation est vide, un rapport avec une seule l'est aussi`() {
        assertTrue(UsageReport().isEmpty)
        assertTrue(!UsageReport(totals = UsageTotals(sessions = 1)).isEmpty)
        assertTrue(!UsageReport(totals = UsageTotals(input = 1)).isEmpty)
    }

    @Test
    fun `la periode enregistree retombe sur le mois quand elle est inconnue`() {
        assertEquals(InsightsPeriod.MONTH, InsightsPeriod.fromDays(30))
        assertEquals(InsightsPeriod.WEEK, InsightsPeriod.fromDays(7))
        assertEquals(InsightsPeriod.QUARTER, InsightsPeriod.fromDays(90))
        assertEquals("une valeur inconnue ne crée pas de période", InsightsPeriod.MONTH, InsightsPeriod.fromDays(12))
        assertEquals(InsightsPeriod.MONTH, InsightsPeriod.fromDays(null))
    }
}
