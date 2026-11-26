package com.onyx.app.data.model.serve

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les horodatages d'une tâche planifiée, lisibles — et jamais un nombre de millisecondes.**
 *
 * Les valeurs du test sont celles de la capture du propriétaire, au chiffre près : `1793292701521`
 * (un `next_run` en millisecondes), `1793289101521` (un `last_run` en millisecondes) et
 * `179328282.757681` (le `at` d'une exécution). Les deux premières s'affichaient telles quelles dans
 * l'interface ; la troisième, qui **n'est pas** un instant (1975 en secondes), y était le titre d'un
 * passage — elle doit rester un identifiant technique, en monospace, jamais une date inventée.
 */
class CronStampTest {

    private val fr = Locale.FRANCE
    private val paris = ZoneId.of("Europe/Paris")

    /** Une forme affichée ne peut pas contenir une suite de dix chiffres ou plus. */
    private val brut = Regex("\\d{10,}")

    @Test
    fun `les formes du serveur qui sont des instants se lisent`() {
        // Millisecondes (13 chiffres) — la forme brute de `next_run` / `last_run`.
        assertEquals(1_793_292_701_521L, cronStampMillis("1793292701521"))
        assertEquals(1_793_289_101_521L, cronStampMillis("1793289101521"))
        // Secondes (10 chiffres) — `next_run_at` du Dashboard.
        assertEquals(1_793_289_101_000L, cronStampMillis("1793289101"))
        // Une date ISO reste une date, avec son fuseau.
        assertEquals(1_793_291_501_000L, cronStampMillis("2026-10-29T16:31:41Z"))
    }

    @Test
    fun `ce qui n est pas un instant n est pas pris pour une date`() {
        // La valeur de la capture : 1975 en secondes — pas un horaire de tâche, donc pas un instant.
        assertNull(cronStampMillis("179328282.757681"))
        assertNull(cronStampMillis("0 */6 * * *"))
        assertNull(cronStampMillis("every 60m"))
        assertNull(cronStampMillis("next run"))
        assertNull(cronStampMillis("20261201"))
        assertNull(cronStampMillis(""))
        assertNull(cronStampMillis(null))
    }

    @Test
    fun `un horodatage affiche une date francaise et son relatif`() {
        val now = 1_793_292_700_000L
        val stamp = cronStamp(raw = "1793292701521", nowMillis = now, locale = fr, zone = paris)
        assertNotNull(stamp)
        // La date est celle de la locale, pas un nombre : la même seconde, écrite.
        assertEquals("29/10/2026 17:51", stamp!!.absolute)
        // Et de près.
        assertEquals("maintenant", stamp.relative)
        assertEquals("maintenant · 29/10/2026 17:51", stamp.line)
        assertFalse("un nombre brut est affiché : ${stamp.line}", brut.containsMatchIn(stamp.line))
    }

    @Test
    fun `ce qui est proche se dit en relatif, dans la langue demandee`() {
        val now = 1_793_292_700_000L
        // Douze minutes plus tard, huit minutes plus tôt.
        assertEquals(
            "dans 12 minutes",
            cronStamp(millis = now + 12 * 60_000L, nowMillis = now, locale = fr, zone = paris)!!.relative,
        )
        assertEquals(
            "il y a 8 minutes",
            cronStamp(millis = now - 8 * 60_000L, nowMillis = now, locale = fr, zone = paris)!!.relative,
        )
        // Et l'anglais suit sa langue, sans mot français qui traîne.
        assertEquals(
            "in 12 minutes",
            cronStamp(millis = now + 12 * 60_000L, nowMillis = now, locale = Locale.US, zone = paris)!!.relative,
        )
    }

    @Test
    fun `aucune forme du serveur qui est un instant ne ressort en nombre brut`() {
        val now = 1_793_292_700_000L
        listOf("1793292701521", "1793289101521", "1793289101").forEach { raw ->
            val stamp = cronStamp(raw = raw, nowMillis = now, locale = fr, zone = paris)
            assertNotNull("« $raw » doit se lire comme un instant", stamp)
            val line = stamp!!.line
            assertFalse("nombre de millisecondes affiché pour « $raw » : $line", brut.containsMatchIn(line))
            // Et un instant ne reste **jamais** un identifiant technique : il a une date.
            assertNull("« $raw » reste un identifiant", cronIdentity(raw))
        }
    }

    @Test
    fun `un identifiant qui n est pas un instant reste un identifiant`() {
        assertEquals("179328282.757681", cronIdentity("179328282.757681"))
        assertEquals("run-8f31c0", cronIdentity("run-8f31c0"))
        assertEquals("abc123", cronIdentity(" abc123 "))
        assertNull(cronIdentity("   "))
        assertNull(cronIdentity(null))
    }

    @Test
    fun `l horaire d une tache se raconte en francais`() {
        val job = CronJob(id = "a", schedule = "every 60m")
        assertEquals("toutes les 60 min", job.scheduleText(fr))
        assertEquals("en semaine à 9 h", CronJob(id = "b", schedule = "weekdays at 9am").scheduleText(fr))
        assertEquals("dans 2 h", CronJob(id = "c", schedule = "in 2h").scheduleText(fr))
        assertEquals("chaque jour à 9 h", CronJob(id = "e", schedule = "every day at 9am").scheduleText(fr))
        // Une expression cron n'est pas une phrase : elle reste telle quelle.
        assertEquals("0 */6 * * *", CronJob(id = "d", schedule = "0 */6 * * *").scheduleText(fr))
        // En anglais, la syntaxe du serveur ne bouge pas.
        assertEquals("every 60m", job.scheduleText(Locale.US))
    }
}
