package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxItemTest {
    @Test
    fun `un message devient perime apres une heure et garde un id de ligne distinct`() {
        val queuedAt = 1_000_000L
        val item = OutboxItem(
            id = "message-id",
            text = "À envoyer",
            state = OutboxState.WAITING_FOR_CONNECTION,
            queuedAt = queuedAt,
        )

        assertFalse(item.isStale(now = queuedAt + OutboxItem.STALE_AFTER_MS))
        assertTrue(item.isStale(now = queuedAt + OutboxItem.STALE_AFTER_MS + 1))
        assertEquals("queued-message-id", item.rowID)
    }
}
