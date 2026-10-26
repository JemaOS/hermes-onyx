package com.onyx.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class OnyxSystemTokenTest {
    @Test
    fun `les labels et separateurs gardent les RGBA dynamiques diOS`() {
        assertEquals(DynColor(Color(0x993C3C43), Color(0x99EBEBF5)), OnyxSystem.secondaryLabel)
        assertEquals(DynColor(Color(0x4D3C3C43), Color(0x4DEBEBF5)), OnyxSystem.tertiaryLabel)
        assertEquals(DynColor(Color(0x2E3C3C43), Color(0x29EBEBF5)), OnyxSystem.quaternaryLabel)
        assertEquals(DynColor(Color(0x4A3C3C43), Color(0x99545458)), OnyxSystem.separator)
    }

    @Test
    fun `les sheets groupees utilisent les surfaces claires et sombres elevees diOS`() {
        listOf(false, true).forEach { dark ->
            val theme = ResolvedTheme.resolve(OnyxTheme.AMBER, dark, OnyxFonts.System)
            val appearance = if (dark) "dark" else "light"

            assertEquals(appearance, if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7), theme.formPageColor)
            assertEquals(appearance, if (dark) Color(0xFF2C2C2E) else Color.White, theme.formSurfaceColor)
            assertEquals(appearance, if (dark) Color.White else Color.Black, theme.primaryText)
            assertEquals(appearance, if (dark) Color(0xFF2C2C2E) else Color.White, theme.tertiarySurfaceColor)
        }
    }
}
