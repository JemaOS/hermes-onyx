package com.onyx.app.calls

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les heures de tranquillité d'un appel, **telles que la référence les définit**.
 *
 * Ce qui est éprouvé : la fenêtre qui traverse minuit (22:00 → 07:00), la fenêtre vide (début =
 * fin), les bords (le début est dedans, la fin est dehors), l'allumage, l'écriture « 22:00 » et sa
 * lecture.
 */
class QuietHoursTest {

    // ---- la fenêtre, minute par minute ------------------------------------------------------------

    @Test
    fun `une plage ordinaire prend le debut et rend la fin`() {
        // 22:00 (1320) → 07:00 (420) : traverse minuit.
        assertTrue(QuietHours.inWindow(1320, 420, 1320))
        assertTrue(QuietHours.inWindow(1320, 420, 1380)) // 23:00
        assertTrue(QuietHours.inWindow(1320, 420, 0)) // minuit
        assertTrue(QuietHours.inWindow(1320, 420, 419)) // 06:59
        assertFalse(QuietHours.inWindow(1320, 420, 420)) // 07:00, la fin est dehors
        assertFalse(QuietHours.inWindow(1320, 420, 720)) // midi
    }

    @Test
    fun `une plage dans la meme journee ne touche pas minuit`() {
        // 13:00 (780) → 14:00 (840).
        assertTrue(QuietHours.inWindow(780, 840, 780))
        assertFalse(QuietHours.inWindow(780, 840, 779))
        assertFalse(QuietHours.inWindow(780, 840, 840))
    }

    @Test
    fun `une plage vide ne couvre rien`() {
        assertFalse(QuietHours.inWindow(600, 600, 600))
        assertFalse(QuietHours.inWindow(0, 0, 0))
    }

    // ---- l'allumage ------------------------------------------------------------------------------

    @Test
    fun `eteintes, rien ne sonne quelle que soit l'heure`() {
        assertFalse(QuietHours.now(1380, enabled = false, from = 1320, until = 420))
    }

    @Test
    fun `allumees, la nuit ne sonne pas et le jour sonne`() {
        assertTrue(QuietHours.now(1380, enabled = true, from = 1320, until = 420)) // 23:00
        assertFalse(QuietHours.now(720, enabled = true, from = 1320, until = 420)) // midi
    }

    @Test
    fun `minute du jour repliee sur vingt-quatre heures`() {
        // 1440 équivaut à minuit, donc dedans.
        assertTrue(QuietHours.now(1440, enabled = true, from = 1320, until = 420))
    }

    // ---- l'écriture et la lecture ----------------------------------------------------------------

    @Test
    fun `l'heure s'ecrit et se relit`() {
        assertEquals("22:00", QuietHours.clock(1320))
        assertEquals("07:00", QuietHours.clock(420))
        assertEquals("00:00", QuietHours.clock(0))
        assertEquals("22:00", QuietHours.clock(1440 + 1320))

        assertEquals(1320, QuietHours.parse("22:00"))
        assertEquals(420, QuietHours.parse("07:00"))
        assertEquals(0, QuietHours.parse("00:00"))
    }

    @Test
    fun `une heure illisible ne donne rien`() {
        assertNull(QuietHours.parse(""))
        assertNull(QuietHours.parse("22"))
        assertNull(QuietHours.parse("25:00"))
        assertNull(QuietHours.parse("22:60"))
        assertNull(QuietHours.parse("nuit"))
    }

    @Test
    fun `la minute du jour suit l'horloge du telephone`() {
        // 2026-10-28 22:30 UTC.
        val millis = 1793226600000L // 2026-10-28T22:30:00Z
        val utc = QuietHours.minuteOfDay(millis, TimeZone.getTimeZone("UTC"))
        assertEquals(22 * 60 + 30, utc)
    }

    // ---- le résultat et la raison ----------------------------------------------------------------

    @Test
    fun `les resultats portent les identifiants de la reference`() {
        assertEquals("answered", CallOutcome.ANSWERED.id)
        assertEquals("declined", CallOutcome.DECLINED.id)
        assertEquals("no_answer", CallOutcome.NO_ANSWER.id)
        assertEquals("busy", CallOutcome.BUSY.id)
        assertEquals("quiet", CallOutcome.QUIET.id)
    }

    @Test
    fun `une raison garde ses details facultatifs`() {
        assertEquals("Besoin d'un accord", CallReason("Besoin d'un accord").reason)
        assertNull(CallReason("Besoin d'un accord").details)
        assertEquals("contexte", CallReason("raison", "contexte").details)
    }
}
