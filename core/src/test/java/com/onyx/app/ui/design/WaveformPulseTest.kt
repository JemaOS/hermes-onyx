package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformPulseTest {
    @Test
    fun `la waveform du composer convertit chaque dimension de dp en pixels`() {
        val bars = waveformBarRects(
            phase = 0f,
            height = 26.88f,
            spacing = 2.5f,
            barWidth = 4f,
            density = 2.625f,
            still = true,
            shape = floatArrayOf(0.38f, 0.66f, 1f, 0.66f, 0.38f),
        )

        assertEquals(5, bars.size)
        assertEquals(10.5f, bars[2].width, 0.0001f)
        assertEquals(70.56f, bars[2].height, 0.0001f)
        assertEquals(0f, bars[2].top, 0.0001f)
        assertEquals(34.125f, bars[2].left, 0.0001f)
        assertEquals(5.25f, bars[2].cornerRadius, 0.0001f)
        assertEquals(26.8128f, bars[0].height, 0.0001f)
        assertEquals(21.8736f, bars[0].top, 0.0001f)
        assertEquals(78.75f, bars.last().left + bars.last().width, 0.0001f)
    }
}
