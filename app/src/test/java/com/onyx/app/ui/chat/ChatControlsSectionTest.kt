package com.onyx.app.ui.chat

import com.onyx.app.data.net.ChatControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatControlsSectionTest {
    @Test
    fun `le pied explique le mode rapide et l approbation du serveur`() {
        val footer = chatControlsFooter(ChatControls(approvalMode = "off"), offersAutoApprove = true)

        assertEquals(ChatControls.fastFooter + " " + ChatControls.serverNeverAsksFooter, footer)
    }

    @Test
    fun `le mode API ne propose que le mode rapide`() {
        val footer = chatControlsFooter(ChatControls(), offersAutoApprove = false)

        assertEquals(ChatControls.fastFooter + " " + ChatControls.apiFastFooter, footer)
    }

    @Test
    fun `le mode rapide local est isole par connexion et conversation`() {
        assertNotEquals(
            runFastPreferenceKey("server-a", "session"),
            runFastPreferenceKey("server-b", "session"),
        )
        assertEquals("server-a|session", runFastPreferenceKey("server-a", "session"))
    }
}
