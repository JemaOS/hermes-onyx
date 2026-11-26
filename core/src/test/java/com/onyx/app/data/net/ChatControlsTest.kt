package com.onyx.app.data.net

import com.onyx.app.data.net.ChatControls
import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.net.chatModelOptions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatControlsTest {
    @Test
    fun `les informations partielles ne reinitialisent pas les autres controles`() {
        val controls = ChatControls(autoApprove = true, approvalMode = "manual", fast = true)

        val merged = controls.merged(
            buildJsonObject {
                put("service_tier", "normal")
                put("project", buildJsonObject { put("name", "Onyx") })
            },
        )

        assertTrue(merged.autoApprove)
        assertFalse(merged.fast)
        assertEquals("manual", merged.approvalMode)
    }

    @Test
    fun `le tier prioritaire et le mode approvals off sont compris`() {
        val merged = ChatControls().merged(
            buildJsonObject {
                put("service_tier", "priority")
                put("approval_mode", "off")
            },
        )

        assertTrue(merged.fast)
        assertTrue(merged.serverNeverAsks)
    }

    @Test
    fun `les refus de mode rapide ont un libelle utile`() {
        assertEquals(
            "This model has no fast mode.",
            ChatControls.fastRefusal("fast is not available for this model"),
        )
        assertEquals(
            "Pick a model first: fast mode belongs to a model.",
            ChatControls.fastRefusal("cannot enable fast without a selected model"),
        )
        assertEquals("server refusal", ChatControls.fastRefusal("server refusal"))
    }

    @Test
    fun `le mode rapide local part bien dans les options du tour`() {
        val options = chatModelOptions(ChatRequest(history = emptyList(), fast = true))

        assertEquals(true, options?.get("fast")?.asBool())
        assertNull(chatModelOptions(ChatRequest(history = emptyList())))
    }
}
