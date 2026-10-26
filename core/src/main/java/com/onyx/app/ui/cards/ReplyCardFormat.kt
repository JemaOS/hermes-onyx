package com.onyx.app.ui.cards

import com.onyx.app.data.model.ReplyCard
import com.onyx.app.localization.OnyxLocalization
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Les mots et les chiffres des cartes d'une réponse: les mêmes que ceux 
 * , écrits selon la langue et l'heure du téléphone.
 *
 * Tout est pur (pas de Compose, pas d'Android hors du format d'heure localisé): c'est éprouvable
 * et cela garantit qu'aucune chaîne n'est écrite en dur dans une vue.
 */
object ReplyCardFormat {

    /** « 19° »: un degré entier, comme `Int(value.rounded)°`. */
    fun degrees(value: Double): String {
        // `Int(value.rounded)` arrondit loin de zéro: -11,5 donne -12, là où `Math.round`
        // de Java donnerait -11.
        val rounded = Math.round(Math.abs(value))
        return if (value < 0 && rounded != 0L) "-$rounded°" else "$rounded°"
    }

    /**
     * « 14:00 », « 2026-10-22T14:00 » ou « 2 PM » écrit comme le téléphone écrit une heure; le
     * texte est rendu tel quel s'il n'est pas une heure.
     *
     * Le gabarit vient de la locale (`java.text.DateFormat`, la même lecture que le système), sans
     * la minute: écrit `date.formatted(.dateTime.hour)` — « 2 PM », pas « 2:00 PM ».
     */
    fun hourLabel(text: String, locale: Locale = Locale.getDefault()): String {
        val clock = text.substringAfterLast('T')
        val parts = clock.split(':')
        val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return text
        if (hour !in 0..23) return text
        val minute = parts.getOrNull(1)?.trim()?.take(2)?.toIntOrNull() ?: 0
        return runCatching {
            LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(hourPattern(locale), locale))
        }.getOrDefault(text)
    }

    /** « h a » (États-Unis), « HH 'h' » (France): l'heure seule, comme `dateTime.hour`. */
    private fun hourPattern(locale: Locale): String {
        // Sur Android, le gabarit d'ICU pour le squelette « j » est exactement celui.
        runCatching { android.text.format.DateFormat.getBestDateTimePattern(locale, "j") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it.any { c -> c == 'H' || c == 'h' || c == 'K' || c == 'k' } }
            ?.let { return it }
        // Hors Android (les épreuves JVM), le gabarit court de la locale, sans les minutes.
        val short = (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale) as? java.text.SimpleDateFormat)
            ?.toPattern()
        val hour = short?.replace(Regex("[:.,\\s]*m+"), "")?.replace(Regex("\\s+"), " ")?.trim()
        return hour?.takeIf { it.isNotEmpty() && it.any { c -> c == 'H' || c == 'h' || c == 'K' || c == 'k' } } ?: "HH"
    }

    /**
     * « 2026-10-23 » écrit en jour de la semaine (« ven. »), aujourd'hui écrit « Aujourd'hui »;
     * le texte est rendu tel quel s'il n'est pas une date. La date est lue comme ce jour-là, sur le
     * calendrier du téléphone (lue comme un instant, minuit UTC, elle tombait la veille).
     */
    fun dayLabel(text: String, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String {
        val date = date(text) ?: return text
        if (date == today) return OnyxLocalization.translate("Today", locale)
        // Le jour abrégé, comme `weekday(.abbreviated)` (« Fri », « ven. »).
        return runCatching {
            date.format(DateTimeFormatter.ofPattern("EEE", locale))
        }.getOrDefault(text)
    }

    /** « EEEE, MMMM d » / « EEEE d MMMM »: le jour long, son quantième et son mois, sans l'année. */
    private fun weekdayDayMonthPattern(locale: Locale): String {
        val full = (java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, locale) as? java.text.SimpleDateFormat)
            ?.toPattern()
            ?: return "EEEE d MMMM"
        val withoutYear = full
            .replace(Regex("\\s*[yY]+\\s*[年]?\\s*[,.\\-]?"), " ")
            .replace(Regex("[,.\\-]?\\s*[yY]+\\s*[年]?\\s*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim(',', '.', '-')
            .trim()
        return withoutYear.takeIf { it.contains('E') && it.isNotEmpty() } ?: "EEEE d MMMM"
    }

    /** La date écrite en toutes lettres (« vendredi 23 octobre »), dans sa zone et sa langue. */
    fun longDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        runCatching {
            date.format(DateTimeFormatter.ofPattern(weekdayDayMonthPattern(locale), locale))
        }.getOrDefault(date.toString())

    /** La date d'un « 2026-10-23 », ou `null`. */
    fun date(text: String): LocalDate? {
        val parts = text.take(10).split('-')
        if (parts.size < 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** Le nom d'un temps, tel que l'app le nomme (la traduction se fait au rendu). */
    fun conditionName(condition: ReplyCard.Condition): String = when (condition) {
        // « Clear skies » et non « Clear »: ce dernier est déjà un libellé d'écran (« Clear »,
        // effacer les outils du téléphone) et une clé partagée se traduirait pour les deux.
        ReplyCard.Condition.CLEAR -> "Clear skies"
        ReplyCard.Condition.PARTLY_CLOUDY -> "Partly cloudy"
        ReplyCard.Condition.CLOUDY -> "Cloudy"
        ReplyCard.Condition.FOG -> "Fog"
        ReplyCard.Condition.DRIZZLE -> "Drizzle"
        ReplyCard.Condition.RAIN -> "Rain"
        ReplyCard.Condition.HEAVY_RAIN -> "Heavy rain"
        ReplyCard.Condition.THUNDERSTORM -> "Thunderstorms"
        ReplyCard.Condition.SNOW -> "Snow"
        ReplyCard.Condition.SLEET -> "Sleet"
        ReplyCard.Condition.HAIL -> "Hail"
        ReplyCard.Condition.WIND -> "Windy"
        ReplyCard.Condition.UNKNOWN -> ""
    }

    /** « Walk », « Drive », « Transit », « Cycle » — les mots de `MapCard.modeName`. */
    fun modeName(mode: ReplyCard.Route.Mode): String = when (mode) {
        ReplyCard.Route.Mode.DRIVING -> "Drive"
        ReplyCard.Route.Mode.WALKING -> "Walk"
        ReplyCard.Route.Mode.TRANSIT -> "Transit"
        ReplyCard.Route.Mode.CYCLING -> "Cycle"
    }

    /** La durée d'un chemin, en unités abrégées (« 1 h 20 min »). */
    fun duration(seconds: Long): String {
        val minutes = Math.round(seconds / 60.0)
        if (minutes < 60) return OnyxLocalization.format("%1\$d min", minutes)
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0L) {
            OnyxLocalization.format("%1\$d hr", hours)
        } else {
            OnyxLocalization.format("%1\$d hr %2\$d min", hours, rest)
        }
    }

    /** Une distance à la route (« 600 m », « 1,2 km »), comme `Measurement(.road)`. */
    fun distance(meters: Double, locale: Locale = Locale.getDefault()): String =
        if (meters < 1000) {
            String.format(locale, "%.0f m", meters)
        } else {
            String.format(locale, "%.1f km", meters / 1000.0)
        }

    /** La distance à vol d'oiseau entre deux points, en mètres. */
    fun straightLineMeters(latitude1: Double, longitude1: Double, latitude2: Double, longitude2: Double): Double {
        val earth = 6_371_000.0
        val phi1 = Math.toRadians(latitude1)
        val phi2 = Math.toRadians(latitude2)
        val dPhi = Math.toRadians(latitude2 - latitude1)
        val dLambda = Math.toRadians(longitude2 - longitude1)
        val a = Math.sin(dPhi / 2).let { it * it } +
            Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2).let { it * it }
        return 2 * earth * Math.asin(Math.min(1.0, Math.sqrt(a)))
    }

    /**
     * Où ouvrir l'app de cartes du téléphone: un `geo:` sur le lieu quand il n'y en a qu'un, et le
     * site d'OpenStreetMap (la boîte, ou le point) quand il y en a plusieurs — Android n'ouvre
     * qu'une épingle à la fois.
     */
    fun mapsUri(places: List<ReplyCard.Place>, label: String? = null): String? {
        val located = places.filter { it.hasCoordinate }
        if (located.isEmpty()) return null
        val first = located.first()
        val name = label ?: first.name
        if (located.size == 1) {
            return "geo:${first.latitude},${first.longitude}?q=${first.latitude},${first.longitude}(${uriText(name)})"
        }
        val box = OsmTiles.Bounds.of(located.map { it.latitude!! to it.longitude!! }) ?: return null
        return "https://www.openstreetmap.org/?mlat=${box.centerLatitude}&mlon=${box.centerLongitude}#map=14"
    }

    /** Le `geo:` d'un chemin: l'arrivée, ouverte dans l'app de cartes. */
    fun routeUri(route: ReplyCard.Route): String? {
        val to = route.to
        if (!to.hasCoordinate) return null
        return "geo:${to.latitude},${to.longitude}?q=${to.latitude},${to.longitude}(${uriText(to.name)})"
    }

    /**
     * Le texte d'un lieu pour une URI, sans parenthèse ni retour à la ligne (ils cassent le
     * `q=` d'un `geo:`).
     */
    private fun uriText(text: String): String =
        java.net.URLEncoder.encode(text.replace('(', '[').replace(')', ']').replace('\n', ' '), "UTF-8")
}
