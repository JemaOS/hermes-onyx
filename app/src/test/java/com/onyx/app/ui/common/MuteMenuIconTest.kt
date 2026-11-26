package com.onyx.app.ui.common

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteMenuIconTest {
    @Test
    fun `le menu utilise les cloches outline et la marque la cloche fill`() {
        val source = File("src/main/java/com/onyx/app/ui/common/MuteMenu.kt").readText()
        assertTrue(source.contains("val mutedMark: Int = Symbols.BellSlashFill"))
        assertTrue(source.contains("MenuItem(label = MuteSpan.describe(until), symbol = null)"))
        assertTrue(File("../core/src/main/res/drawable/ic_sym_bell.xml").isFile)
        assertTrue(File("../core/src/main/res/drawable/ic_sym_bell_slash.xml").isFile)
        assertTrue(File("../core/src/main/res/drawable/ic_sym_bell.xml").readText().contains("strokeColor"))
        assertTrue(File("../core/src/main/res/drawable/ic_sym_bell_slash.xml").readText().contains("strokeColor"))
    }
}
