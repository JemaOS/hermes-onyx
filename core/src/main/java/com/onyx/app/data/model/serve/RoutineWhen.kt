package com.onyx.app.data.model.serve

import com.onyx.app.localization.OnyxLanguage
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.text.DateFormatSymbols
import java.util.Locale

/** Le moment choisi dans l'éditeur d'une planification de bot. */
private val isoFormatter = DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .appendLiteral('T')
    .appendPattern("HH:mm:ss")
    .appendOffset("+HH:MM", "Z")
    .toFormatter(Locale.ROOT)

data class RoutineWhen(
    val repeats: Repeat = Repeat.WEEKDAYS,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<Int> = setOf(1),
    val everyHours: Int = 2,
    val once: Instant = Instant.now().plusSeconds(3600),
) {
    enum class Repeat(val title: String) {
        DAILY("Every day"),
        WEEKDAYS("Weekdays"),
        WEEKENDS("Weekends"),
        WEEKLY("On some days"),
        HOURS("Every few hours"),
        ONCE("Once"),
    }

    /** L'expression cron conservée par Hermes, dans le fuseau du serveur. */
    val schedule: String
        get() = schedule(ZoneId.systemDefault())

    fun schedule(zoneId: ZoneId): String = when (repeats) {
        Repeat.DAILY -> "$minute $hour * * *"
        Repeat.WEEKDAYS -> "$minute $hour * * 1-5"
        Repeat.WEEKENDS -> "$minute $hour * * 0,6"
        Repeat.WEEKLY -> {
            val selectedDays = days.ifEmpty { setOf(1) }.toSortedSet().joinToString(",")
            "$minute $hour * * $selectedDays"
        }
        Repeat.HOURS -> "every ${everyHours.coerceAtLeast(1)}h"
        Repeat.ONCE -> isoFormatter.withZone(zoneId).format(once)
    }

    /** La phrase de la rangée, dans la langue et le fuseau du téléphone. */
    fun summary(locale: Locale = OnyxLanguage.preferred, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val localTime = if (repeats == Repeat.ONCE) {
            LocalDateTime.ofInstant(once, zoneId).toLocalTime()
        } else {
            LocalTime.of(hour, minute)
        }
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zoneId)
            .format(localTime)
            .replace(Regex("[\\u00A0\\u202F]"), " ")
        val french = locale.language.equals("fr", ignoreCase = true)
        return when (repeats) {
            Repeat.DAILY -> if (french) "Tous les jours à $time" else "Every day at $time"
            Repeat.WEEKDAYS -> if (french) "En semaine à $time" else "Weekdays at $time"
            Repeat.WEEKENDS -> if (french) "Le week-end à $time" else "Weekends at $time"
            Repeat.WEEKLY -> {
                val symbols = DateFormatSymbols.getInstance(locale).shortWeekdays
                val names = days.ifEmpty { setOf(1) }.toSortedSet().map { symbols[it + 1] }
                if (french) "${andList(names, french = true)} à $time" else "${andList(names)} at $time"
            }
            Repeat.HOURS -> when {
                french && everyHours == 1 -> "Toutes les heures"
                french -> "Toutes les $everyHours heures"
                everyHours == 1 -> "Every hour"
                else -> "Every $everyHours hours"
            }
            Repeat.ONCE -> {
                val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(locale)
                    .withZone(zoneId)
                    .format(once)
                if (french) "Une fois, le $date à $time" else "Once, $date at $time"
            }
        }
    }

    companion object {
        /** Ne comprend que les formes que l'éditeur sait réécrire. */
        fun parse(schedule: String): RoutineWhen? {
            val text = schedule.trim()
            Regex("every (\\d+)h").matchEntire(text)?.let { match ->
                val hours = match.groupValues[1].toIntOrNull() ?: return null
                return RoutineWhen(repeats = Repeat.HOURS, everyHours = hours)
            }
            val parts = text.split(Regex("\\s+"))
            if (parts.size == 5 && parts[2] == "*" && parts[3] == "*") {
                val minute = parts[0].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
                val hour = parts[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
                return when (parts[4]) {
                    "*" -> RoutineWhen(repeats = Repeat.DAILY, hour = hour, minute = minute)
                    "1-5" -> RoutineWhen(repeats = Repeat.WEEKDAYS, hour = hour, minute = minute)
                    "0,6", "6,0" -> RoutineWhen(repeats = Repeat.WEEKENDS, hour = hour, minute = minute)
                    else -> {
                        val rawDays = parts[4].split(",")
                        val days = rawDays.map { it.toIntOrNull() ?: return null }
                        if (days.isEmpty() || days.any { it !in 0..6 }) return null
                        RoutineWhen(repeats = Repeat.WEEKLY, hour = hour, minute = minute, days = days.toSet())
                    }
                }
            }
            val instant = runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull() ?: return null
            return RoutineWhen(repeats = Repeat.ONCE, once = instant)
        }
    }
}

private fun andList(values: List<String>, french: Boolean = false): String = when (values.size) {
    0 -> ""
    1 -> values.single()
    2 -> if (french) "${values[0]} et ${values[1]}" else "${values[0]} and ${values[1]}"
    else -> if (french) {
        values.dropLast(1).joinToString(", ") + " et " + values.last()
    } else {
        values.dropLast(1).joinToString(", ") + ", and " + values.last()
    }
}
