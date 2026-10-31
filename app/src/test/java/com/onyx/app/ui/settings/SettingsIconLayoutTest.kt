package com.onyx.app.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIconLayoutTest {
    @Test
    fun `les entrees des reglages reprennent les symboles la référence`() {
        val source = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
        listOf(
            "Symbols.Waveform",
            "Symbols.PaintbrushOutline",
            "Symbols.WrenchAndScrewdriver",
            "Symbols.RectangleSplit",
            "Symbols.QrCode",
            "Symbols.Heart",
        ).forEach { assertTrue("$it absent", source.contains(it)) }
        listOf(
            "ic_sym_paintbrush.xml",
            "ic_sym_wrench_and_screwdriver.xml",
            "ic_sym_rectangle_split_3x1.xml",
            "ic_sym_qrcode.xml",
            "ic_sym_heart.xml",
        ).forEach { assertTrue("$it manquant", File("../core/src/main/res/drawable/$it").isFile) }
    }

    @Test
    fun `la bague dune icone app mesure 66 points comme sur la référence`() {
        val source = File("src/main/java/com/onyx/app/ui/settings/AppIconSettings.kt").readText()
        assertTrue(source.contains("AppIconCellOuterSize = 66.dp"))
        assertTrue(source.contains(".size(AppIconCellOuterSize)"))
    }

    @Test
    fun `la grille d icones d app reste repliee dans la carte d Apparence`() {
        val source = File("src/main/java/com/onyx/app/ui/settings/AppIconSettings.kt").readText()
        val appearance = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
        // `AppIconSettings` : `@State private var expanded = false` — la grille reste
        // **repliee** sur la rangée (le chevron ne fait que la déplier) et vit dans la carte
        // d'Apparence au lieu d'un écran poussé.
        assertTrue(source.contains("var expanded by remember { mutableStateOf(false) }"))
        assertTrue(appearance.contains("AppIconSettings(embedded = true)"))
    }
}
