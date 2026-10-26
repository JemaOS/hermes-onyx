package com.onyx.app.ui.chat.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarTest {
    @Test
    fun `le titre garde les 60 caracteres du modele et une seule ligne`() {
        val long = "x".repeat(61)
        assertEquals(60, chatHeaderTitle(long).length)
        assertEquals("Première question", chatHeaderTitle("Première question\navec une suite"))
        assertEquals("Sans troncature préalable", chatHeaderTitle("Sans troncature préalable"))
    }

    @Test
    fun `la capsule et le titre reprennent les dimensions de la barre `() {
        assertEquals(86.dp, TopBarCapsuleWidth)
        assertEquals(30.dp, TopBarActionSlotHeight)
        assertEquals(240.dp, TopBarPrincipalMaxWidth)
    }
}
