package com.onyx.app.ui.tools

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Réglages → Outils : le compteur devant l'interrupteur a disparu, et ce qui reste est traduit.
 *
 * L'écran est vérifié sur ses sources, comme les autres passes de parité de l'app : le rendu
 * Compose n'est pas monté dans les tests unitaires, mais ce qui compte ici — la ligne du compteur
 * et les appels de traduction — se lit dans le fichier.
 */
class ToolsPageParityTest {
    private val root = File("src/main/java/com/onyx/app/ui")
    private val toolsScreen = File(root, "tools/ToolsScreen.kt").readText()
    private val botEditor = File(root, "bots/BotEditorScreen.kt").readText()
    private val settingsScreen = File(root, "settings/SettingsScreen.kt").readText()
    private val onyxApp = File(root, "OnyxApp.kt").readText()

    private val toolsetRow = toolsScreen
        .substringAfter("private fun ToolsetRow(")
        .substringBefore("if (toolset.tools.isNotEmpty()) {\n            BasicText(")

    @Test
    fun `le compteur a cote de l interrupteur est retire`() {
        // Ce que l'écran affichait : « A2A 5 », « Browser Automation 14 »…
        assertFalse(
            "la rangée n'affiche plus le nombre d'outils",
            toolsetRow.contains("tools.size"),
        )
        assertFalse(
            "aucun texte de compteur dans la rangée",
            toolsetRow.contains("BasicText(\"\${"),
        )
        assertFalse(
            "l'ancien compteur exact a disparu",
            toolsScreen.contains("BasicText(\"\${toolset.tools.size}\""),
        )
        // La rangée garde son interrupteur, et la liste des noms d'outils juste en dessous.
        assertTrue(toolsetRow.contains("OnyxSwitch("))
        assertTrue(
            "les noms d'outils restent affichés sous la description",
            toolsScreen.contains("toolset.tools.joinToString(\" · \")"),
        )
    }

    @Test
    fun `la rangee d outils tient dans la largeur du volet`() {
        // Le titre **cède la place** : il prend ce qui reste après l'interrupteur…
        assertTrue("le titre prend la place restante", toolsetRow.contains("modifier = Modifier.weight(1f),"))
        // …sur **deux lignes au plus**, abrégé par des points — jamais coupé au milieu d'un mot,
        // jamais trois lignes qui allongent la rangée sans fin (relevé sur le volet étroit du
        // pliable replié, 340 dp).
        assertTrue("le titre s'arrête à deux lignes", toolsetRow.contains("maxLines = 2"))
        assertTrue(
            "ce qui dépasse s'abrège par des points",
            toolsetRow.contains("overflow = TextOverflow.Ellipsis"),
        )
        // L'interrupteur, lui, **ne se comprime pas** : taille fixe (51 dp) et une marge — il reste
        // entier, jamais rogné par le bord du volet.
        assertTrue(toolsetRow.contains("modifier = Modifier.padding(start = 12.dp)"))
        assertFalse(
            "l'interrupteur ne doit pas se partager la largeur",
            Regex("OnyxSwitch\\([^)]*weight").containsMatchIn(toolsetRow),
        )
    }

    @Test
    fun `le libelle et la description de chaque jeu d outils passent par le dictionnaire`() {
        assertTrue(
            "le libellé du serveur est localisé",
            toolsetRow.contains("toolset.title") && toolsetRow.contains("localize = true"),
        )
        assertTrue(
            "la description est traduite paragraphe par paragraphe",
            toolsScreen.contains("OnyxLocalization.translateParagraphs(it)"),
        )
        // Les identifiants d'outils (`a2a_call`, `execute_code`…) restent verbatim.
        val idsLine = toolsScreen
            .substringAfter("text = toolset.tools.joinToString")
            .substringBefore("style =")
        assertTrue("la ligne des identifiants n'est pas traduite", idsLine.contains("localize = false"))
    }

    @Test
    fun `l en tete et le pied de page de la section sont localises`() {
        assertTrue(toolsScreen.contains("header = \"Enabled · \${enabled.size}\""))
        assertTrue(toolsScreen.contains("header = \"Not enabled · \${disabled.size}\""))
        // `OnyxFormSection` traduit l'en-tête et le pied de page par défaut : rien à forcer ici.
        assertFalse(toolsScreen.contains("localizeHeader = false"))
        assertFalse(toolsScreen.contains("localizeFooter = false"))
        assertEquals("Activés · 28", OnyxLocalization.translate("Enabled · 28", Locale.FRANCE))
        assertEquals("Non activés · 4", OnyxLocalization.translate("Not enabled · 4", Locale.FRANCE))
    }

    @Test
    fun `les ecrans voisins ne changent pas`() {
        // L'éditeur de bot garde ses jeux d'outils traduits depuis la passe précédente.
        assertTrue(botEditor.contains("BasicText(toolset.title, localize = true"))
        assertTrue(botEditor.contains("localize = true,\n                                style = theme.caption"))
        // L'entrée « Outils » des Réglages et la route restent en place.
        assertTrue(settingsScreen.contains("AgentEntry(\"Tools\""))
        assertTrue(onyxApp.contains("Route.TOOLS -> ToolsScreen("))
        assertEquals("Outils", OnyxLocalization.translate("Tools", Locale.FRANCE))
        assertTrue(OnyxLocalization.translate("Enabled · 28", Locale.FRANCE).contains("28"))
    }
}
