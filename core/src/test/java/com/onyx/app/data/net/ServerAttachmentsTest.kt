package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerAttachmentsTest {
    @Test
    fun `les noms d upload datés ont un suffixe en cas de doublon`() {
        assertEquals("2026-10-24 123456 image.jpg", serverAttachmentUploadName("2026-10-24 123456", 1, "image.jpg"))
        assertEquals("2026-10-24 123456 2 image.jpg", serverAttachmentUploadName("2026-10-24 123456", 2, "image.jpg"))
    }
}
