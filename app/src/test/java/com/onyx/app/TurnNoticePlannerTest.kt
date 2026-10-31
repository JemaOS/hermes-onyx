package com.onyx.app

import com.onyx.app.data.net.TurnEvent
import com.onyx.app.data.net.TransportError
import com.onyx.app.notifications.TurnNotice
import com.onyx.app.notifications.TurnNoticePlanner
import com.onyx.app.notifications.WorkTracking
import com.onyx.app.ui.chat.TurnSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La décision du suivi, repassée comme la boucle du processus la repasse — une fois par seconde.
 *
 * C'est le cœur du lot : un tour qui se termine doit produire **une** notification de fin, pas
 * trois ; un tour arrêté la fait disparaître ; un tour que l'utilisateur a sous les yeux ne
 * produit rien ; « jamais » ne produit rien du tout.
 */
class TurnNoticePlannerTest {

    private fun session(id: String = "fil", assistantId: String = "answer", startedAt: Long = 1_000L) =
        TurnSession(
            conversationId = id,
            assistantId = assistantId,
            userText = "Compte les routes, et dis-moi ce que tu trouves.",
            startedAt = startedAt,
        )

    private fun planner(
        tracking: WorkTracking = WorkTracking.ON_FINISH,
        sound: Boolean = true,
        now: Long = 13_000L,
    ) = TurnNoticePlanner(
        tracking = { tracking },
        sound = { sound },
        titleOf = { "Quill" },
        clock = { now },
    )

    private fun finishes(notices: List<TurnNotice>) = notices.filterIsInstance<TurnNotice.Finish>()

    @Test
    fun `un tour fini produit une seule notification de fin, pas trois`() {
        val done = session().also {
            it.apply(TurnEvent.TextDelta("54 routes, toutes cohérentes."))
            it.succeed(5_000)
        }
        val sessions = mapOf("fil" to done)
        val planner = planner()

        // La boucle du processus repasse la décision à chaque battement.
        val first = planner.plan(sessions, appInForeground = false, visibleConversationId = null)
        val second = planner.plan(sessions, appInForeground = false, visibleConversationId = null)
        val third = planner.plan(sessions, appInForeground = false, visibleConversationId = null)

        assertEquals(1, finishes(first).size)
        assertEquals("republier la même fin n'en empile pas une deuxième", 0, finishes(second).size)
        assertEquals(0, finishes(third).size)
        assertEquals(TurnNotice.Finish(
            conversationId = "fil",
            title = "Quill",
            reply = "54 routes, toutes cohérentes.",
            failed = false,
            sound = true,
        ), finishes(first).single())
        // Plus rien à suivre : la notification d'avancement s'efface.
        assertTrue(first.contains(TurnNotice.Idle))
    }

    @Test
    fun `un echec se dit echec, avec l'extrait de ce qui a ete ecrit`() {
        val failed = session().also {
            it.apply(TurnEvent.TextDelta("Je n'ai pas pu joindre le serveur."))
            it.fail(TransportError.StreamLost("flux coupé"), 5_000)
        }
        val notice = finishes(planner().plan(mapOf("fil" to failed), false, null)).single()
        assertTrue(notice.failed)
        assertEquals("Je n'ai pas pu joindre le serveur.", notice.reply)
    }

    @Test
    fun `un tour arrete fait disparaitre sa notification, sans rien annoncer`() {
        val stopped = session().also { it.stop(5_000) }
        val notices = planner().plan(mapOf("fil" to stopped), appInForeground = false, visibleConversationId = null)

        assertTrue(notices.contains(TurnNotice.Drop("fil")))
        assertTrue("un arrêt ne se raconte pas comme une fin", finishes(notices).isEmpty())
    }

    @Test
    fun `jamais ne produit rien`() {
        val done = session().also {
            it.apply(TurnEvent.TextDelta("Une réponse."))
            it.succeed(5_000)
        }
        val running = session("autre", "answer-2", startedAt = 9_000)

        val notices = planner(tracking = WorkTracking.NEVER)
            .plan(mapOf("fil" to done, "autre" to running), appInForeground = false, visibleConversationId = null)

        assertTrue(finishes(notices).isEmpty())
        assertTrue(notices.filterIsInstance<TurnNotice.Keep>().isEmpty())
    }

    @Test
    fun `l'app ouverte sur la conversation ne notifie rien`() {
        val running = session()
        val notices = planner(tracking = WorkTracking.EVERY_STEP)
            .plan(mapOf("fil" to running), appInForeground = true, visibleConversationId = "fil")

        assertTrue(notices.filterIsInstance<TurnNotice.Keep>().isEmpty())
    }

    @Test
    fun `l'app ouverte ailleurs suit le tour dans la barre d'etat`() {
        val running = session()
        val notices = planner(tracking = WorkTracking.EVERY_STEP)
            .plan(mapOf("fil" to running), appInForeground = true, visibleConversationId = "autre")

        val keep = notices.filterIsInstance<TurnNotice.Keep>().single()
        assertEquals("fil", keep.conversationId)
        assertEquals("Quill", keep.title)
        // Douze secondes écoulées, et l'étape honnête tant que le serveur n'a rien dit.
        assertEquals("Answering… · 12s", keep.text)
    }

    @Test
    fun `a la fin seulement n'installe rien dans la barre d'etat pendant le tour`() {
        val running = session().also { it.apply(TurnEvent.ToolStarted(name = "terminal", preview = null)) }
        val notices = planner(tracking = WorkTracking.ON_FINISH)
            .plan(mapOf("fil" to running), appInForeground = false, visibleConversationId = null)

        assertTrue(notices.filterIsInstance<TurnNotice.Keep>().isEmpty())
        assertTrue(notices.contains(TurnNotice.Idle))
    }

    @Test
    fun `la conversation sous les yeux ne sera pas notifiee plus tard`() {
        val done = session().also {
            it.apply(TurnEvent.TextDelta("Une réponse déjà lue."))
            it.succeed(5_000)
        }
        val sessions = mapOf("fil" to done)
        val planner = planner()

        // L'utilisateur regarde la conversation : rien, et c'est définitif.
        assertTrue(finishes(planner.plan(sessions, appInForeground = true, visibleConversationId = "fil")).isEmpty())
        // Il passe à autre chose : la réponse déjà vue ne ressort pas en notification.
        assertTrue(finishes(planner.plan(sessions, appInForeground = false, visibleConversationId = null)).isEmpty())
    }

    @Test
    fun `le titre de la notification suit le nom du fil`() {
        val running = session()
        val keep = planner(tracking = WorkTracking.EVERY_STEP)
            .plan(mapOf("fil" to running), false, null)
            .filterIsInstance<TurnNotice.Keep>()
            .single()
        assertEquals("Quill", keep.title)
        assertFalse(keep.text.isBlank())
    }
}
