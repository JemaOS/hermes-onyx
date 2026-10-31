package com.onyx.app

import com.onyx.app.notifications.ApprovalNotifier
import com.onyx.app.notifications.AgentCallService
import com.onyx.app.notifications.CallRinger
import com.onyx.app.notifications.NotificationChannels
import com.onyx.app.notifications.TurnService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les canaux listés à Réglages → Notifications sont ceux que Onyx publie réellement, et leur état
 * suit ce qu'Android rapporte : absent avant la première alerte, éteint sur « aucun », allumé sinon.
 */
class NotificationChannelsTest {

    @Test
    fun `les canaux listes sont ceux que Onyx enregistre`() {
        assertEquals(
            listOf(
                ApprovalNotifier.CHANNEL_ID,
                ApprovalNotifier.SILENT_CHANNEL_ID,
                TurnService.CHANNEL_ID,
                CallRinger.CHANNEL_ID,
                AgentCallService.CHANNEL_ID,
            ),
            NotificationChannels.all.map { it.id },
        )
    }

    @Test
    fun `un canal qui n'existe pas encore est absent`() {
        assertEquals(NotificationChannels.State.ABSENT, NotificationChannels.state(null))
    }

    @Test
    fun `l'importance none eteint le canal`() {
        assertEquals(
            NotificationChannels.State.OFF,
            NotificationChannels.state(NotificationChannels.IMPORTANCE_NONE),
        )
        assertEquals(NotificationChannels.State.OFF, NotificationChannels.state(0))
    }

    @Test
    fun `une importance vivante allume le canal`() {
        // IMPORTANCE_MIN = 1, IMPORTANCE_DEFAULT = 3, IMPORTANCE_HIGH = 4.
        listOf(1, 3, 4).forEach { importance ->
            assertEquals("importance $importance", NotificationChannels.State.ON, NotificationChannels.state(importance))
        }
    }

    @Test
    fun `chaque canal porte son libelle localisable`() {
        assertEquals(
            listOf("Approvals", "Silent approvals", "Answers", "Calls", "Reachable"),
            NotificationChannels.all.map { it.label },
        )
    }
}
