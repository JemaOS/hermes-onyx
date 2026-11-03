package com.onyx.app.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La capture d'une démonstration**, sans téléphone ni Compose.
 *
 * Ce que ce fichier tient, et qui n'est pas décoratif :
 *
 * 1. **l'ordre et l'horodatage** — les gestes sortent dans l'ordre où ils ont eu lieu, avec un
 *    `at_ms` qui ne recule jamais (le serveur refuse un journal qui recule) ;
 * 2. **le secret ne laisse rien** — un texte marqué secret devient `Type("", isSecret = true)`, et
 *    la chaîne saisie n'apparaît dans aucun `toString()` du journal ;
 * 3. **le libellé reste nul** — le pont ne donne pas le nom d'accessibilité, donc l'appui part
 *    sans libellé et le convertisseur le marquera « à relire » ;
 * 4. **hors fenêtre, rien ne s'écrit** — un geste avant `start` ou après `stop` n'entre pas, et un
 *    second `start` jette la démonstration précédente.
 */
class SkillRecorderTest {

    /** Une horloge qu'on avance à la main : le journal ne dépend pas du temps réel. */
    private class FakeClock(var now: Long = 1_000L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    private fun recorder(clock: FakeClock) = SkillRecorder { clock.now }

    @Test
    fun `les gestes sortent dans l ordre, horodates depuis l ouverture`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()

        clock.advance(1_240)
        recorder.tap(421f, 183f)
        clock.advance(360)
        recorder.type("Rapport 2026")
        clock.advance(500)
        recorder.scroll(ScrollDirection.DOWN, 3)
        clock.advance(100)
        recorder.key(NamedKey.ENTER)
        val duration = recorder.stop()

        val journal = recorder.journal(windowWidth = 1_000, windowHeight = 1_000)
        assertEquals(
            listOf(
                TracedAction.Tap(x = 421f, y = 183f, label = null),
                TracedAction.Type(text = "Rapport 2026"),
                TracedAction.Scroll(ScrollDirection.DOWN, 3),
                TracedAction.Key(NamedKey.ENTER),
            ),
            journal.actions,
        )
        assertEquals(listOf(1_240L, 1_600L, 2_100L, 2_200L), recorder.entries().map { it.atMs })
        assertEquals(2_200L, duration)
        assertEquals(1_000, journal.windowWidth)
        assertEquals(1_000, journal.windowHeight)
    }

    @Test
    fun `un appui sans libelle reste sans libelle`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()
        recorder.tap(10f, 20f)

        val tap = recorder.journal(800, 600).actions.single() as TracedAction.Tap
        assertEquals(null, tap.label)
        // Et le brouillon le dit : position en pourcentage, marque « à relire ».
        val draft = skillDraftMarkdown(recorder.journal(800, 600))
        assertTrue(draft, draft.contains("Tap at 1% / 3% of the window — à relire : libellé absent"))
    }

    @Test
    fun `un texte secret ne laisse ni texte ni longueur`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()
        recorder.type("mot-de-passe-très-long", secret = true)
        recorder.stop()

        val journal = recorder.journal(1000, 1000)
        val typed = journal.actions.single() as TracedAction.Type
        assertTrue(typed.isSecret)
        assertEquals("", typed.text)
        assertFalse("le texte secret ne doit apparaître nulle part", journal.toString().contains("mot-de-passe"))

        val draft = skillDraftMarkdown(journal)
        assertTrue(draft, draft.contains("Type •••• — secret, non enregistré"))
        assertFalse("pas même le nombre de puces", draft.contains("•".repeat(5)))
    }

    @Test
    fun `hors de la fenetre, aucun geste n entre`() {
        val clock = FakeClock()
        val recorder = recorder(clock)

        recorder.tap(1f, 2f)
        assertEquals(0, recorder.count)

        recorder.start()
        recorder.tap(1f, 2f)
        recorder.stop()
        recorder.type("après l'arrêt")
        assertEquals(1, recorder.count)
    }

    @Test
    fun `un second depart jette la demonstration precedente`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()
        recorder.tap(1f, 2f)
        clock.advance(100)
        recorder.stop()

        recorder.start()
        clock.advance(50)
        recorder.tap(3f, 4f)

        val journal = recorder.journal(100, 100)
        assertEquals(listOf(TracedAction.Tap(3f, 4f)), journal.actions)
        assertEquals(listOf(50L), recorder.entries().map { it.atMs })
    }

    @Test
    fun `l horloge qui recule ne fait pas reculer le journal`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()
        clock.advance(500)
        recorder.tap(1f, 1f)
        // Une horloge murale peut sauter en arrière ; `at_ms` ne doit pas suivre.
        clock.now -= 200
        recorder.tap(2f, 2f)

        assertEquals(listOf(500L, 500L), recorder.entries().map { it.atMs })
    }

    @Test
    fun `la duree est bornee au plafond de dix minutes`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()
        clock.advance(11 * 60 * 1000L)
        recorder.tap(1f, 1f)

        assertEquals(SkillRecorder.MAX_DURATION_MS, recorder.entries().single().atMs)
    }

    @Test
    fun `un defilement nul n est pas une action`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()
        recorder.scroll(ScrollDirection.UP, 0)
        assertEquals(0, recorder.count)
    }
}
