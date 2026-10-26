package com.onyx.app.car

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLooksTest {
    @Test
    fun `les deux reglages Auto gardent ids et defauts du modele CarPlay`() {
        assertEquals(AutoCardLook.COLOR, AutoCardLook.fromId(null))
        assertEquals(AutoCardLook.MARKS, AutoCardLook.fromId("marks"))
        assertEquals(AutoVoiceLook.BARS, AutoVoiceLook.fromId("unknown"))
        assertEquals(AutoVoiceLook.KITT, AutoVoiceLook.fromId("kitt"))
        assertEquals(listOf("color", "glow", "orb", "gold", "graphite", "marks"), AutoCardLook.entries.map { it.id })
        assertEquals(listOf("bars", "lamps", "kitt", "orb", "onyx"), AutoVoiceLook.entries.map { it.id })
        assertEquals(listOf(AutoStart.ASK, AutoStart.NEW_CHAT), AutoStart.entries.toList())
        assertEquals(listOf(AutoVoiceState.THINKING, AutoVoiceState.SPEAKING), AutoVoiceState.entries.toList())
    }
}
