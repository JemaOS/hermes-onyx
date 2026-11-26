package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuViewportBoundsTest {
    @Test fun wideWindowKeepsMenuAtReadableWidth() {
        val bounds = menuViewportBounds(1200, 760, 280, 16)
        assertEquals(280, bounds.width)
        assertEquals(728, bounds.height)
        assertEquals(16, bounds.left)
        assertEquals(1184, bounds.right)
    }

    @Test fun narrowAndShortWindowsNeverProduceOutOfBoundsPanels() {
        for (width in listOf(0, 12, 32, 180, 360, 900)) {
            for (height in listOf(0, 12, 32, 96, 320, 720)) {
                val bounds = menuViewportBounds(width, height, 280, 16)
                assertTrue(bounds.left >= 0)
                assertTrue(bounds.right <= width)
                assertTrue(bounds.width in 0..(bounds.right - bounds.left))
                assertTrue(bounds.top >= 0)
                assertTrue(bounds.bottom <= height)
                assertTrue(bounds.height >= 0)
                // The same range clamps primary panels and submenus after scrolling.
                assertTrue(bounds.left <= bounds.right - bounds.width)
                assertTrue(bounds.top <= bounds.bottom - bounds.height)
            }
        }
    }

    @Test fun imeReducedViewportCapsPanelHeightWithoutShrinkingRows() {
        val open = menuViewportBounds(840, 700, 280, 16)
        val keyboard = menuViewportBounds(840, 180, 280, 16)
        assertEquals(open.width, keyboard.width)
        assertEquals(148, keyboard.height)
        assertTrue(keyboard.height < open.height)
    }
}
