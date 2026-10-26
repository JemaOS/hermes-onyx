package com.onyx.app

import com.onyx.app.notifications.ApprovalNotifier
import org.junit.Assert.assertEquals
import org.junit.Test

/** Le réglage de son choisit réellement le canal Android utilisé par les alertes. */
class NotificationChannelPolicyTest {
    @Test
    fun `le canal silencieux est distinct du canal sonore existant`() {
        assertEquals(ApprovalNotifier.CHANNEL_ID, ApprovalNotifier.channelForSound(true))
        assertEquals("onyx.approvals.silent", ApprovalNotifier.channelForSound(false))
    }
}
