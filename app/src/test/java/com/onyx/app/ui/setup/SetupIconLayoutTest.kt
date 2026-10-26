package com.onyx.app.ui.setup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupIconLayoutTest {
    private val source = File("src/main/java/com/onyx/app/ui/setup/SetupScreen.kt").readText()

    @Test
    fun `accueil reprend la marque Graphite de 76 points`() {
        assertTrue(source.contains("OnyxMarkSize = 76.dp"))
        assertTrue(source.contains("R.mipmap.ic_launcher_graphite"))
        assertTrue(source.contains("RoundedCornerShape(17.dp)"))
        assertFalse(source.contains("OnyxIcon(Symbols.ServerRack, tint = theme.accent, size = 34.dp)"))
    }

    @Test
    fun `les symboles du setup ne sont plus des substitutions`() {
        assertTrue(source.contains("Symbols.LockShield"))
        assertTrue(source.contains("Symbols.QrCodeViewfinder"))
        assertTrue(source.contains("Symbols.Link"))
        assertTrue(source.contains("Symbols.XmarkOctagonFill"))
        assertTrue(source.contains("Alignment.Top"))
        assertTrue(source.contains("Arrangement.spacedBy(14.dp)"))
        assertTrue(source.contains("size = 20.dp"))
        listOf(
            "ic_sym_lock_shield.xml",
            "ic_sym_qrcode_viewfinder.xml",
            "ic_sym_link.xml",
            "ic_sym_xmark_octagon_fill.xml",
            "ic_sym_exclamationmark_triangle_fill.xml",
            "ic_sym_camera_fill.xml",
        ).forEach { assertTrue("$it manquant", File("../core/src/main/res/drawable/$it").isFile) }
    }

    @Test
    fun `la confirmation et le scanner utilisent les variantes demandees`() {
        val sheet = File("src/main/java/com/onyx/app/ui/setup/SetupCodeSheet.kt").readText()
        val scanner = File("src/main/java/com/onyx/app/ui/setup/SetupCodeScanner.kt").readText()
        assertTrue(sheet.contains("Symbols.ExclamationTriangleFill"))
        assertTrue(sheet.contains("Symbols.XmarkOctagonFill"))
        assertTrue(scanner.contains("Symbols.CameraFill"))
    }

    @Test
    fun `le test de connexion na pas dicone permanente et son resultat est marque`() {
        assertFalse(source.contains("OnyxIcon(Symbols.WifiExclamation"))
        assertTrue(source.contains("if (testing) SetupSpinner()"))
        assertTrue(source.contains("if (result.ok) Symbols.CheckmarkCircle else Symbols.XmarkOctagonFill"))
    }
}
