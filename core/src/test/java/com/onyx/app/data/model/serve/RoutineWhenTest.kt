package com.onyx.app.data.model.serve

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Le formulaire d'une planification de bot, relu dans le format conservé par Hermes. */
class RoutineWhenTest {
    @Test
    fun `un cron de semaine est relu et resume sans perdre sa valeur`() {
        val parsed = RoutineWhen.parse("0 7 * * 1-5")

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(RoutineWhen.Repeat.WEEKDAYS, parsed.repeats)
        assertEquals(7, parsed.hour)
        assertEquals(0, parsed.minute)
        assertEquals("0 7 * * 1-5", parsed.schedule)
        assertEquals(
            "Weekdays at 7:00 AM",
            parsed.summary(Locale.US, TimeZone.getTimeZone("UTC").toZoneId()),
        )
    }

    @Test
    fun `une repetition horaire est relue et resume`() {
        val parsed = RoutineWhen.parse("every 4h")

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(RoutineWhen.Repeat.HOURS, parsed.repeats)
        assertEquals(4, parsed.everyHours)
        assertEquals("every 4h", parsed.schedule)
        assertEquals("Every 4 hours", parsed.summary(Locale.US))
    }

    @Test
    fun `les formes cron de l editeur sont distinguees`() {
        val utc = TimeZone.getTimeZone("UTC").toZoneId()
        val cases = listOf(
            Triple("30 6 * * *", RoutineWhen.Repeat.DAILY, "Every day at 6:30 AM"),
            Triple("0 8 * * 0,6", RoutineWhen.Repeat.WEEKENDS, "Weekends at 8:00 AM"),
            Triple("15 9 * * 1,3,5", RoutineWhen.Repeat.WEEKLY, "Mon, Wed, and Fri at 9:15 AM"),
        )

        cases.forEach { (schedule, repeat, summary) ->
            val parsed = RoutineWhen.parse(schedule)
            assertNotNull(schedule, parsed)
            requireNotNull(parsed)
            assertEquals(repeat, parsed.repeats)
            assertEquals(schedule, parsed.schedule)
            assertEquals(summary, parsed.summary(Locale.US, utc))
        }
    }

    @Test
    fun `une execution unique conserve sa date et son fuseau`() {
        val parsed = RoutineWhen.parse("2026-12-01T09:00:00Z")

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(RoutineWhen.Repeat.ONCE, parsed.repeats)
        assertEquals(Instant.parse("2026-12-01T09:00:00Z"), parsed.once)
        assertEquals("2026-12-01T09:00:00Z", parsed.schedule(ZoneId.of("UTC")))
        assertEquals(
            "Once, Dec 1, 2026 at 9:00 AM",
            parsed.summary(Locale.US, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `les resumes d horaires suivent la langue francaise`() {
        val utc = TimeZone.getTimeZone("UTC").toZoneId()
        assertEquals(
            "Tous les jours à 07:00",
            RoutineWhen(repeats = RoutineWhen.Repeat.DAILY, hour = 7, minute = 0)
                .summary(Locale.FRANCE, utc),
        )
        assertEquals(
            "En semaine à 07:00",
            RoutineWhen(repeats = RoutineWhen.Repeat.WEEKDAYS, hour = 7, minute = 0)
                .summary(Locale.FRANCE, utc),
        )
        assertEquals(
            "Toutes les 4 heures",
            RoutineWhen(repeats = RoutineWhen.Repeat.HOURS, everyHours = 4)
                .summary(Locale.FRANCE, utc),
        )
        assertEquals(
            "Toutes les heures",
            RoutineWhen(repeats = RoutineWhen.Repeat.HOURS, everyHours = 1)
                .summary(Locale.FRANCE, utc),
        )
    }

    @Test
    fun `une forme inconnue reste personnalisee`() {
        assertNull(RoutineWhen.parse("every 30m"))
        assertNull(RoutineWhen.parse("0 7 * * 1-5 extra"))
        assertNull(RoutineWhen.parse("99 7 * * *"))
    }
}
