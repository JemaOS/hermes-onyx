package com.onyx.app.data.model.serve

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Détection d'une planification dont le serveur a laissé passer l'heure. */
class CronJobTest {
    private val next = 1_700_000_000_000L

    @Test
    fun `une tache est manquee seulement apres cinq minutes`() {
        val job = CronJob(id = "a", state = "scheduled", nextRunAt = next)

        assertFalse(job.isMissed(nowMillis = next + 300_000L))
        assertTrue(job.isMissed(nowMillis = next + 300_001L))
    }

    @Test
    fun `une execution deja faite ou une pause n est pas manquee`() {
        assertFalse(
            CronJob(id = "a", state = "scheduled", nextRunAt = next, lastRunAt = next)
                .isMissed(nowMillis = next + 600_000L),
        )
        assertFalse(
            CronJob(id = "a", state = "paused", paused = true, nextRunAt = next)
                .isMissed(nowMillis = next + 600_000L),
        )
    }

    @Test
    fun `les prochaines executions gardent le sens du temps`() {
        assertEquals("in 2 hours", relativeLabel(next + 7_200_000L, nowMillis = next, locale = Locale.US))
        assertEquals("5 minutes ago", relativeLabel(next - 300_000L, nowMillis = next, locale = Locale.US))
    }

    @Test
    fun `les dates relatives suivent la langue francaise`() {
        assertEquals("dans 2 heures", relativeLabel(next + 7_200_000L, nowMillis = next, locale = Locale.FRANCE))
        assertEquals("il y a 5 minutes", relativeLabel(next - 300_000L, nowMillis = next, locale = Locale.FRANCE))
        assertEquals("dans 1 jour", relativeLabel(next + 86_400_000L, nowMillis = next, locale = Locale.FRANCE))
        assertEquals("maintenant", relativeLabel(next, nowMillis = next, locale = Locale.FRANCE))
    }

    @Test
    fun `le prefixe desktop identifie une routine partagee`() {
        assertTrue(CronJob(id = "a", title = "[bot:scout] What to wear").sharedWithDesktop)
        assertFalse(CronJob(id = "b", title = "What to wear").sharedWithDesktop)
        assertFalse(CronJob(id = "c", title = "[bot:scout What to wear").sharedWithDesktop)
    }
}
