package com.onyx.app

import com.onyx.app.data.net.TurnEvent
import com.onyx.app.data.net.TransportError
import com.onyx.app.notifications.WorkTracking
import com.onyx.app.notifications.WorkTrackingPolicy
import com.onyx.app.ui.chat.TurnSession
import com.onyx.app.ui.chat.TurnStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le suivi du travail : qui se notifie, et qui ne se notifie pas.
 *
 * Les trois règles du propriétaire, une par test — « jamais » ne dit rien, ce qu'il a sous les
 * yeux ne se notifie pas, un tour arrêté ne se raconte pas — et le suivi en direct réservé au
 * choix « à chaque étape ».
 */
class WorkTrackingPolicyTest {

    private fun session(id: String = "fil", startedAt: Long = 1_000L) = TurnSession(
        conversationId = id,
        assistantId = "a-$id",
        userText = "Une question de l'utilisateur assez longue.",
        startedAt = startedAt,
    )

    private fun finished(text: String = "54 routes, toutes cohérentes."): TurnSession =
        session().also {
            it.apply(TurnEvent.TextDelta(text))
            it.succeed(5_000)
        }

    @Test
    fun `jamais ne notifie rien`() {
        assertFalse(WorkTrackingPolicy.tracks(WorkTracking.NEVER, false, null, "fil"))
        assertFalse(WorkTrackingPolicy.tracks(WorkTracking.NEVER, true, "autre", "fil"))
    }

    @Test
    fun `les deux autres choix notifient un tour qui n'est pas sous les yeux`() {
        assertTrue(WorkTrackingPolicy.tracks(WorkTracking.ON_FINISH, false, null, "fil"))
        assertTrue(WorkTrackingPolicy.tracks(WorkTracking.EVERY_STEP, false, "autre", "fil"))
    }

    @Test
    fun `l'app ouverte sur la conversation ne notifie pas`() {
        assertFalse(WorkTrackingPolicy.tracks(WorkTracking.ON_FINISH, true, "fil", "fil"))
        assertFalse(WorkTrackingPolicy.tracks(WorkTracking.EVERY_STEP, true, "fil", "fil"))
    }

    @Test
    fun `une autre conversation ouverte laisse notifier`() {
        assertTrue(WorkTrackingPolicy.tracks(WorkTracking.ON_FINISH, true, "autre", "fil"))
    }

    @Test
    fun `le suivi en direct ne vaut que pour le choix a chaque etape`() {
        val running = session()
        val sessions = mapOf("fil" to running)

        assertNull(
            "« à la fin seulement » n'installe rien dans la barre d'état",
            WorkTrackingPolicy.tracked(sessions.values, WorkTracking.ON_FINISH, false, null),
        )
        assertNull(
            "« jamais » non plus",
            WorkTrackingPolicy.tracked(sessions.values, WorkTracking.NEVER, false, null),
        )
        assertSame(
            running,
            WorkTrackingPolicy.tracked(sessions.values, WorkTracking.EVERY_STEP, false, null),
        )
    }

    @Test
    fun `le tour suivi est le plus recent, et jamais celui qu'on regarde`() {
        val older = session("vieux", startedAt = 1_000)
        val recent = session("recent", startedAt = 9_000)
        val sessions = listOf(older, recent)

        assertSame(recent, WorkTrackingPolicy.tracked(sessions, WorkTracking.EVERY_STEP, false, null))
        assertSame(
            "celui qu'on a sous les yeux n'est pas celui qu'on notifie",
            older,
            WorkTrackingPolicy.tracked(sessions, WorkTracking.EVERY_STEP, true, "recent"),
        )
    }

    @Test
    fun `un tour en cours ne dit pas encore son issue`() {
        val running = session()
        assertNull(WorkTrackingPolicy.finishOf(running.live.value))
        assertEquals(TurnStep.STARTING, running.phase.step)
    }

    @Test
    fun `un tour fini dit son issue`() {
        assertEquals(false, WorkTrackingPolicy.finishOf(finished().live.value)?.failed)

        val failed = session("echec").also { it.fail(TransportError.StreamLost("flux coupé"), 5_000) }
        assertEquals(true, WorkTrackingPolicy.finishOf(failed.live.value)?.failed)
    }

    @Test
    fun `le suivi egrene pendant tout le tour, meme s'il ne le montre pas encore`() {
        // Le défaut trouvé en recette : le suivi ne se réveillait que sur un changement du
        // magasin de tours — or un tour ne change pas le magasin en avançant. Endormi au premier
        // passage (l'app montrait la conversation), il dormait pendant **tout** le tour et ne
        // publiait que la fin, une minute plus tard.
        val running = session()
        val sessions = listOf(running)
        assertTrue(
            "un tour en cours fait égrener, même en « à la fin seulement »",
            WorkTrackingPolicy.keepsTicking(sessions, WorkTracking.ON_FINISH),
        )
        assertTrue(WorkTrackingPolicy.keepsTicking(sessions, WorkTracking.EVERY_STEP))
        assertFalse(
            "« jamais » n'a rien à égrener",
            WorkTrackingPolicy.keepsTicking(sessions, WorkTracking.NEVER),
        )
        val done = finished().also { }
        assertFalse(
            "plus rien ne tourne : on peut attendre",
            WorkTrackingPolicy.keepsTicking(listOf(done), WorkTracking.EVERY_STEP),
        )
    }

    @Test
    fun `un tour arrete ne recoit pas de notification de fin`() {
        val stopped = session().also { it.stop(5_000) }
        assertEquals(TurnStep.STOPPED, stopped.phase.step)
        assertNull("l'utilisateur sait qu'il vient d'arrêter", WorkTrackingPolicy.finishOf(stopped.live.value))
    }
}
