package com.onyx.app.ui.settings

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ligne « Langue » des Réglages et sa feuille de choix, telles qu'elles sont écrites : la ligne
 * est dans la section RÉPONSES juste après « Apparence », la feuille est celle de l'app
 * (`OnyxActionSheet`, pas de Material), et un choix s'applique **tout de suite** (la langue bascule
 * en mémoire, puis l'activité est recréée pour que l'écran entier se redessine).
 */
class LanguageRowParityTest {

    private val screen = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
    private val form = File("../core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt").readText()
    private val answering = screen.substringAfter("header = \"Appearance\"").substringBefore("header = \"Chat\"")
    private val sheet = screen.substringAfter("if (showLanguage) {").substringBefore("\n    }\n}")

    @Test
    fun `la ligne Langue est dans Reponses, apres Apparence`() {
        val appearance = answering.indexOf("label = \"Appearance\"")
        val language = answering.indexOf("label = \"Language\"")
        assertTrue("la section Réponses doit porter « Apparence »", appearance > 0)
        assertTrue("la ligne « Langue » doit suivre « Apparence »", language > appearance)
        assertTrue(answering.contains("value = OnyxLanguage.label(language)"))
        assertTrue(answering.contains("symbol = Symbols.Globe"))
        assertTrue(answering.contains("onClick = { showLanguage = true }"))
        // Une rangée de formulaire, pas un composant Material.
        assertTrue(answering.contains("OnyxValueRow("))
    }

    @Test
    fun `la feuille propose les trois choix, celui en cours coche`() {
        assertTrue(sheet.contains("OnyxActionSheet("))
        assertTrue(sheet.contains("items = OnyxLanguage.choices.map { choice ->"))
        assertTrue(sheet.contains("label = OnyxLanguage.label(choice)"))
        // LOT anatomie-des-contenus : la tuile de la rangée est l'icône du sujet (le globe), et le
        // contrôle de droite est la coche (`checked`) — la rangée suit le gabarit de la maison.
        assertTrue(sheet.contains("symbol = Lucide.Globe,"))
        assertTrue(sheet.contains("checked = choice == language,"))
        assertTrue(sheet.contains("onDismiss = { showLanguage = false }"))
        assertTrue(sheet.contains("MenuItem("))
        // Rien de Material dans la feuille ni dans la rangée.
        assertTrue(!sheet.contains("androidx.compose.material"))
        assertTrue(!answering.contains("androidx.compose.material"))
    }

    @Test
    fun `un choix s applique tout de suite et recree l activite`() {
        assertTrue(sheet.contains("viewModel.setLanguage(choice)"))
        assertTrue(sheet.contains("context.hostActivity()?.recreate()"))
        assertTrue(screen.contains("val language by viewModel.language.collectAsStateWithLifecycle()"))
        // La rangée montre le choix courant en clair, sans redémarrer l'app.
        assertEquals(
            "Système",
            OnyxLocalization.translate(OnyxLanguage.label(OnyxLanguage.SYSTEM), Locale.FRANCE),
        )
        assertEquals(
            "Français",
            OnyxLocalization.translate(OnyxLanguage.label(OnyxLanguage.FRENCH), Locale.FRANCE),
        )
    }

    @Test
    fun `la ligne et la feuille passent par les pieces du formulaire maison`() {
        // `OnyxValueRow` traduit son libellé et sa valeur, et `OnyxActionSheet` centrée ; les deux
        // vivent dans le fichier de formulaire maison, dessiné à la main.
        assertTrue(form.contains("fun OnyxValueRow("))
        assertTrue(form.contains("fun OnyxFormSection("))
        val actionSheet = File("../core/src/main/java/com/onyx/app/ui/design/OnyxActionSheet.kt").readText()
        assertTrue(actionSheet.contains("fun OnyxActionSheet("))
        assertTrue(!actionSheet.contains("androidx.compose.material"))
    }
}
