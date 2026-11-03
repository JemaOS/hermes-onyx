package com.onyx.app

import com.onyx.app.notifications.TurnNotice
import com.onyx.app.notifications.TurnNoticePlanner
import com.onyx.app.notifications.WorkTracking
import com.onyx.app.ui.chat.TurnSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le processus doit tenir tant qu'un tour tourne.
 *
 * Android endort un processus en arrière-plan, et un processus endort ne lit plus son socket : le
 * canal meurt, et le tour avec lui. Le service de premier plan est ce qui l'empêche — mais il était
 * lié à la **politique de notification**, dont le choix par défaut (« à la fin seulement ») ne
 * demande aucun suivi visible. Résultat : le service ne partait jamais, le canal mourait, et le
 * tour disparaissait au moment précis où la personne l'attendait.
 *
 * Une politique dit *ce qu'on montre*, pas *si le travail survit*.
 */
class TourTenuProcessusTest {

    private fun session(id: String = "fil", assistantId: String = "answer", startedAt: Long = 1_000L) =
        TurnSession(
            conversationId = id,
            assistantId = assistantId,
            userText = "Compte les routes, et dis-moi ce que tu trouves.",
            startedAt = startedAt,
        )

    private fun planner(tracking: WorkTracking) = TurnNoticePlanner(
        tracking = { tracking },
        sound = { true },
        titleOf = { "Onyx" },
        clock = { 13_000L },
    )

    private fun keeps(notices: List<TurnNotice>) = notices.filterIsInstance<TurnNotice.Keep>()

    @Test
    fun `un tour qui tourne et l app en arriere-plan tiennent le processus`() {
        val notices = planner(WorkTracking.ON_FINISH)
            .plan(mapOf("fil" to session()), appInForeground = false, visibleConversationId = null)

        val keep = keeps(notices).single()
        assertEquals("fil", keep.conversationId)
        assertNull("le maintien se passe de progression : un mot, pas un bandeau", keep.percent)
    }

    @Test
    fun `un tour qui tourne tient le service, meme quand l app est au premier plan`() {
        val notices = planner(WorkTracking.ON_FINISH)
            .plan(mapOf("fil" to session()), appInForeground = true, visibleConversationId = "fil")

        val keep = keeps(notices).single()
        assertEquals("fil", keep.conversationId)
        assertNull("le maintien se passe de progression : un mot, pas un bandeau", keep.percent)
    }

    @Test
    fun `jamais ne tient pas de service`() {
        val notices = planner(WorkTracking.NEVER)
            .plan(mapOf("fil" to session()), appInForeground = false, visibleConversationId = null)

        assertTrue("« jamais » veut dire jamais", keeps(notices).isEmpty())
    }

    @Test
    fun `a chaque etape garde la progression`() {
        val notices = planner(WorkTracking.EVERY_STEP)
            .plan(mapOf("fil" to session()), appInForeground = false, visibleConversationId = null)

        assertEquals("fil", keeps(notices).single().conversationId)
    }

    @Test
    fun `un tour fini ne tient plus le processus`() {
        val done = session().also { it.succeed(5_000) }
        val notices = planner(WorkTracking.ON_FINISH)
            .plan(mapOf("fil" to done), appInForeground = false, visibleConversationId = null)

        assertTrue("plus rien à tenir", keeps(notices).isEmpty())
        assertTrue(TurnNotice.Idle in notices)
    }
}
