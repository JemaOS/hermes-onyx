package com.onyx.app

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.bots.withSwiftFaces
import com.onyx.app.ui.design.BotFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BotRosterFacesTest {
    @Test
    fun `tous les ecrans recoivent les memes visages de roster`() {
        val bots = listOf(BotProfile("default"), BotProfile("kilo"), BotProfile("scout"))

        val resolved = bots.withSwiftFaces().associate { it.name to it.faceCode }

        assertEquals(BotFace.main.code(), resolved["default"])
        assertEquals("1:diamond:happy:smile:freckles:5:2:-3", resolved["kilo"])
        assertEquals("1:burst:sleepy:oh:freckles:3:2:2", resolved["scout"])
        assertEquals(resolved, bots.reversed().withSwiftFaces().associate { it.name to it.faceCode })
    }

    @Test
    fun `un choix local est reserve et les autres visages le contournent`() {
        val bots = listOf("default", "scout", "writer", "fixit", "coder").map { BotProfile(it) }
        val writer = BotFace.forName("writer")

        val resolved = bots.withSwiftFaces(mapOf("scout" to writer.code())).associate { it.name to it.faceCode }

        assertEquals(writer.code(), resolved["scout"])
        assertNotEquals(writer.code(), resolved["writer"])
    }

    @Test
    fun `les anciens profils numeriques et a tirets ne sont pas perdus`() {
        val numeric = BotProfile("kilo", faceCode = "7")
        val legacy = BotProfile("scout", faceCode = "circle-wide-open-2")

        val resolved = listOf(numeric, legacy).withSwiftFaces().associate { it.name to it.faceCode }

        assertEquals("7", resolved["kilo"])
        assertEquals("1:round:round:oh:none:2:0:0", resolved["scout"])
    }
}
