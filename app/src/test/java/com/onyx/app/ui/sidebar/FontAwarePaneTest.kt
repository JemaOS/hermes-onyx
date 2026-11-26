package com.onyx.app.ui.sidebar

import androidx.compose.ui.unit.dp
import com.onyx.app.ui.listDetailLayout
import org.junit.Assert.*
import org.junit.Test

class FontAwarePaneTest {
    @Test fun `large text falls back before detail becomes unusable`() {
        assertEquals(SidebarMode.DOCKED, sidebarModeFor(840.dp))
        assertEquals(SidebarMode.COMPACT, sidebarModeFor(840.dp, 1.5f))
        assertFalse(listDetailLayout(840.dp, fontScale = 1.5f).twoPanes)
        assertEquals(840.dp, listDetailLayout(840.dp, fontScale = 1.5f).detailWidth)
    }

    @Test fun `resize retains active destination and separate mode visibility`() {
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.DOCKED)
        state.pushDestination("VAULT")
        state.updateMode(sidebarModeFor(400.dp, 1.5f))
        assertEquals("VAULT", state.destination)
        state.open()
        state.updateMode(sidebarModeFor(1600.dp, 1.5f))
        assertEquals(SidebarMode.DOCKED, state.mode)
        assertEquals("VAULT", state.destination)
        state.updateMode(SidebarMode.COMPACT)
        assertTrue(state.isOpen)
        assertEquals("VAULT", state.destination)
    }

    @Test fun `large window can still dock at large text without negative detail width`() {
        val layout = listDetailLayout(1600.dp, fontScale = 1.5f)
        assertTrue(layout.twoPanes)
        assertTrue(layout.detailWidth >= 400.dp)
        assertEquals(SidebarMode.DOCKED, sidebarModeFor(1600.dp, 1.5f))
    }
}
