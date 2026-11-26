package com.onyx.app.data.model.serve

import com.onyx.app.localization.OnyxLanguage
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Ce que le tableau de bord sait de la consommation d'un profil : les formes du rapport d'usage
 * (`GET api/analytics/usage`), lues champ par champ.
 *
 * Chaque champ a une valeur neutre par défaut (zéro, chaîne vide) : un rapport partiel reste
 * affichable au lieu de faire tomber l'écran. La lecture du flux lui-même — et son **refus** quand
 * ce n'est pas un rapport — vit avec l'appel qui l'obtient, dans `ServeAnalyticsApi` : mieux vaut ne
 * rien dire qu'annoncer un coût qu'on n'a pas lu.
 */
data class UsageDay(
    /** `AAAA-MM-JJ`, dans le fuseau du serveur. Un jour où il ne s'est rien passé est omis. */
    val day: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val estimatedCost: Double = 0.0,
    val actualCost: Double = 0.0,
    val sessions: Int = 0,
    val apiCalls: Int = 0,
) {
    /** Entrée + sortie : ce que la barre du jour et le cumul montrent. */
    val tokens: Long get() = inputTokens + outputTokens

    /**
     * Ce que le fournisseur a réellement facturé quand il le dit, sinon l'estimation au prix
     * catalogue. Jamais l'inverse : un coût réel de zéro n'est pas un coût réel (le serveur écrit
     * zéro quand il n'a rien su), donc l'estimation prend le relais.
     */
    val cost: Double get() = if (actualCost > 0) actualCost else estimatedCost
}

/** Un modèle utilisé pendant la période. */
data class ModelUsage(
    val model: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val estimatedCost: Double = 0.0,
    val sessions: Int = 0,
    val apiCalls: Int = 0,
) {
    val tokens: Long get() = inputTokens + outputTokens
}

/** Un outil appelé pendant la période, avec son nombre d'appels. */
data class ToolUsage(val tool: String, val count: Int = 0)

/** Une compétence lue ou modifiée pendant la période, avec son nombre d'actions. */
data class SkillUsage(val skill: String, val count: Int = 0)

/** Les cumuls de la période. */
data class UsageTotals(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val estimatedCost: Double = 0.0,
    val actualCost: Double = 0.0,
    val sessions: Int = 0,
    val apiCalls: Int = 0,
) {
    val tokens: Long get() = input + output

    val cost: Double get() = if (actualCost > 0) actualCost else estimatedCost

    /**
     * La part de l'invite servie par le cache du fournisseur, en pourcent, ou `null` quand il n'y a
     * aucune entrée à mettre en rapport — une part sur zéro n'existe pas, elle ne doit pas
     * s'afficher comme « 0 % ».
     */
    val cachePercent: Int? get() = (input + cacheRead).takeIf { it > 0 }?.let { (cacheRead * 100 / it).toInt() }
}

/**
 * Le rapport d'usage d'un profil : ses jours, ses modèles, ses cumuls, ses outils et ses
 * compétences.
 *
 * [periodDays] est ce que le serveur a réellement appliqué : il borne la fenêtre de l'histogramme,
 * et c'est lui qui décide, pas la période demandée par l'écran.
 */
data class UsageReport(
    val daily: List<UsageDay> = emptyList(),
    val byModel: List<ModelUsage> = emptyList(),
    val totals: UsageTotals = UsageTotals(),
    val periodDays: Int = 0,
    val tools: List<ToolUsage> = emptyList(),
    val topSkills: List<SkillUsage> = emptyList(),
) {
    /** Vrai quand rien n'a été consommé sur la période : l'écran le dit au lieu de dessiner du vide. */
    val isEmpty: Boolean
        get() = totals.sessions == 0 && totals.apiCalls == 0 && totals.tokens == 0L
}

/**
 * La période **jour par jour**, finissant sur [lastEpochDay], les jours que le serveur a omis
 * (aucun usage) comblés par une ligne à zéro — l'axe du temps reste régulier, et une barre absente
 * veut dire « rien ce jour-là », jamais « pas de données ».
 *
 * La fenêtre est celle du serveur : [UsageReport.periodDays] est respecté, et son **dernier jour ne
 * peut jamais être antérieur à un jour rapporté** — l'horloge de l'appareil peut être en retard sur
 * celle du portail, et un jour que le serveur a écrit ne doit pas disparaître du dessin.
 */
fun UsageReport.dailySeries(lastEpochDay: Long): List<UsageDay> {
    val days = periodDays.coerceIn(1, 366)
    val byDay = daily.mapNotNull { row -> epochDayOf(row.day)?.let { it to row } }.toMap()
    val keys = byDay.keys
    var end = maxOf(lastEpochDay, keys.maxOrNull() ?: lastEpochDay)
    val first = keys.minOrNull()
    if (first != null && first < end - days + 1) end = maxOf(first + days - 1, keys.max())
    return ((end - days + 1)..end).map { day -> byDay[day] ?: UsageDay(day = isoDateOf(day)) }
}

/** Le jour le plus chargé de la série ; `null` quand la série est vide. */
fun peakOf(days: List<UsageDay>): UsageDay? = days.maxByOrNull { it.tokens }

/** Les jours depuis 1970-01-01 d'une date `AAAA-MM-JJ` ; `null` quand ce n'en est pas une. */
fun epochDayOf(iso: String): Long? {
    val parts = iso.take(10).split('-')
    if (parts.size != 3) return null
    val year = parts[0].toLongOrNull() ?: return null
    val month = parts[1].toLongOrNull() ?: return null
    val day = parts[2].toLongOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return runCatching { java.time.LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toEpochDay() }.getOrNull()
}

/** La date `AAAA-MM-JJ` d'un jour depuis 1970-01-01. */
fun isoDateOf(epochDay: Long): String = java.time.LocalDate.ofEpochDay(epochDay).toString()

/**
 * « 26 oct. » — la date courte de l'axe et de l'annotation du pic, dans la langue de l'app.
 *
 * Les mois sont abrégés **par table** plutôt que par `DateTimeFormatter` : la forme courte d'un
 * `DateTimeFormatter` dépend des données de locale de l'appareil, alors que la table donne
 * exactement les deux langues de l'app. Une date illisible est rendue telle quelle.
 */
fun shortDate(iso: String, locale: Locale = OnyxLanguage.preferred): String {
    val parts = iso.split('-')
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return iso
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return iso
    val names = if (locale.language.equals("fr", ignoreCase = true)) MONTHS_FRENCH else MONTHS_ENGLISH
    val name = names.getOrNull(month - 1) ?: return iso
    return "$day $name"
}

private val MONTHS_ENGLISH = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private val MONTHS_FRENCH = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.",
)

/**
 * Les trois formats de chiffres des « Aperçus », tenus au même endroit pour que le rapport, les
 * cartes et l'histogramme ne puissent pas diverger.
 *
 * Les jetons suivent le format déjà employé ailleurs dans l'app (`SessionUsage.compact`) ; les
 * montants suivent celui de l'écran d'usage (`$0`, deux décimales, `<$0.01` sous le centime).
 */
object InsightsFormat {

    /** 999 · 8.2k · 3.1M — jamais « 3.10M » : la décimale inutile ne s'écrit pas. */
    fun tokens(value: Long): String {
        if (value <= 0L) return "0"
        return when {
            value < 1_000 -> value.toString()
            value < 1_000_000 -> oneDecimal(value / 1_000.0) + "k"
            else -> oneDecimal(value / 1_000_000.0) + "M"
        }
    }

    /** Un montant en dollars : `$12.34`, `$0.00`, et `<$0.01` sous le centime. */
    fun usd(amount: Double): String {
        if (!amount.isFinite() || amount <= 0.0) return "$0.00"
        if (amount < 0.01) return "<$0.01"
        val cents = (amount * 100).roundToLong()
        return "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    /** Une part, ou `—` quand elle n'existe pas (aucune entrée à mettre en rapport). */
    fun percent(value: Int?): String = value?.let { "$it %" } ?: "—"

    /** `true` si le montant affiché est le réel du fournisseur, `false` s'il est estimé. */
    fun isBilled(actualCost: Double): Boolean = actualCost > 0.0

    private fun oneDecimal(value: Double): String {
        val tenths = (abs(value) * 10).roundToLong()
        // Au-delà de 999,9 « k », la décimale ne dit plus rien : « 1234k » se lit mieux que
        // « 1234.0k ».
        if (tenths % 10 == 0L || tenths >= 10_000) return (tenths / 10).toString()
        return "${tenths / 10}.${tenths % 10}"
    }
}

/**
 * Les trois périodes proposées : 7, 30 ou 90 jours.
 *
 * Le libellé anglais (« 7 days ») passe par le dictionnaire pour l'affichage ; [days] est la seule
 * valeur qui parte au serveur, qui la borne lui-même à 1…365.
 */
enum class InsightsPeriod(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    MONTH(30, "30 days"),
    QUARTER(90, "90 days"),
    ;

    companion object {
        /** La période par défaut : le mois, comme le tableau de bord. */
        val DEFAULT: InsightsPeriod = MONTH

        /**
         * La période d'un nombre de jours enregistré ; une valeur inconnue (ou absente) retombe sur
         * le défaut plutôt que d'inventer une période qui n'existe pas.
         */
        fun fromDays(raw: Int?): InsightsPeriod = entries.firstOrNull { it.days == raw } ?: DEFAULT
    }
}
