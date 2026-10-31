package com.onyx.app.ui.theme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFormParityTest {
    @Test
    fun `les pages et cartes des formulaires utilisent les tokens groupes des sheets référence`() {
        val form = coreSource("ui/design/OnyxForm.kt")
        assertTrue(form.contains(".background(theme.formSurfaceColor, RoundedCornerShape(OnyxSpace.CardCorner))"))
        assertFalse(form.contains(".background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.CardCorner))"))

        listOf(
            "ui/settings/SettingsScreen.kt",
            "ui/settings/GatewayScreen.kt",
            "ui/settings/VoiceSettingsScreen.kt",
            "ui/settings/PushSettingsScreen.kt",
            "ui/settings/PhoneToolsScreen.kt",
            "ui/settings/LockSettingsScreen.kt",
            "ui/settings/voice/KokoroVoicesView.kt",
            "ui/settings/voice/StopPhrasesScreen.kt",
            "ui/settings/voice/LanguageScreens.kt",
            "ui/settings/voice/SpokenPrefixesScreen.kt",
            "ui/setup/SetupScreen.kt",
            "ui/setup/SetupCodeSheet.kt",
        ).forEach { path ->
            val source = appSource(path)
            assertTrue(path, source.contains(".background(theme.formPageColor)"))
            assertFalse(path, source.contains(".background(theme.pageColor)"))
        }

        // Le journal est un ScrollView, pas une liste groupée : son fond reste systemBackground.
        val logs = appSource("ui/settings/LogsScreen.kt")
        assertTrue(logs.contains(".background(OnyxSystem.background.resolve(theme.dark))"))
        assertFalse(logs.contains(".background(theme.pageColor)"))
    }

    @Test
    fun `les formulaires gardent le texte primaire systeme plutot que le texte des reponses`() {
        val files = mutableListOf(
            File("../core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt"),
            File("src/main/java/com/onyx/app/ui/setup/SetupScreen.kt"),
            File("src/main/java/com/onyx/app/ui/setup/SetupCodeSheet.kt"),
        )
        files += File("src/main/java/com/onyx/app/ui/settings").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(files.size > 10)
        files.forEach { file ->
            assertFalse(file.path, file.readText().contains("theme.textColor"))
        }
    }

    @Test
    fun `lapercu de theme reprend les deux bulles du ThemeSwatch la référence`() {
        val source = appSource("ui/settings/SettingsScreen.kt")

        assertTrue(source.contains("ThemeSwatch(theme = ResolvedTheme.resolve(entry, theme.dark, theme.fonts, colors))"))
        assertTrue(source.contains("private fun ThemeSwatch(theme: ResolvedTheme)"))
        assertTrue(source.contains(".size(width = 52.dp, height = 40.dp)"))
        assertTrue(source.contains(".border(OnyxSpace.Stroke, OnyxEdge.fil(theme), RoundedCornerShape(OnyxSpace.ControlCorner))"))
        assertTrue(source.contains(".size(width = 26.dp, height = 8.dp)"))
        assertTrue(source.contains(".size(width = 20.dp, height = 8.dp)"))
        assertFalse(source.contains("ThemeSwatch(entry = entry, dark ="))
    }

    private fun coreSource(path: String): String =
        File("../core/src/main/java/com/onyx/app", path).readText()

    private fun appSource(path: String): String =
        File("src/main/java/com/onyx/app", path).readText()
}
