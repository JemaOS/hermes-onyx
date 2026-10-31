package com.onyx.app.ui

import com.onyx.app.ui.sidebar.SidebarDestinations
import com.onyx.app.ui.sidebar.SidebarTileLayout
import com.onyx.app.ui.sidebar.SidebarTileGroup
import com.onyx.app.ui.sidebar.tileColumns
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le doublon « Compétences » retiré — et ce qui le remplace.**
 *
 * Le propriétaire a vu **deux tuiles pour la même chose** : « Capacités » (le groupe de la référence,
 * qui porte la famille Compétences) et « Compétences » (le groupe « Onyx »). Idem dans les Réglages,
 * où une entrée menait au même écran. La parité a d'abord été vérifiée fonction par fonction ; la
 * seule qui manquait à « Capacités » — **l'éditeur** d'une compétence — y a été versée avant le
 * retrait. Ce fichier tient les quatre bouts :
 *
 * - la tuile et la ligne des Réglages ont quitté l'interface, et la route avec elles ;
 * - le groupe « Onyx » est passé à **quatre** tuiles, en **deux rangées pleines** (grille 2 × 2) ;
 * - tout ce que la famille des compétences faisait est **dans** « Capacités » (liste groupée par
 *   famille, compte, recherche, interrupteur, lecture, catalogue du hub avec installer / retirer) ;
 * - l'**éditeur** reste atteignable : « Capacités » l'ouvre, et le retour rend « Capacités ».
 */
class CapabilitiesDoublonRetireTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val settings = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
    private val capabilitiesFile = File("src/main/java/com/onyx/app/ui/capabilities/CapabilitiesScreen.kt").readText()
    private val skillPages = File("src/main/java/com/onyx/app/ui/capabilities/SkillPages.kt").readText()
    private val editor = File("src/main/java/com/onyx/app/ui/capabilities/SkillEditorScreen.kt").readText()
    private val chatSidebar = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()

    @Test
    fun `la tuile Competences a quitte le volet, le groupe Onyx passe a quatre`() {
        assertFalse("plus aucune tuile « Compétences »", SidebarDestinations.tiles.any { it.id == "SKILLS" })
        assertEquals(
            "le groupe « Onyx », dans son ordre, à quatre tuiles",
            listOf("FILES", "MODELS", "KANBAN", "CODE"),
            SidebarDestinations.onyxOrder,
        )
        assertEquals(
            "aucune autre tuile n'a bougé",
            listOf("CRON", "INSIGHTS", "CAPABILITIES", "TOOLS", "GATEWAY", "ARCHIVED") + SidebarDestinations.onyxOrder,
            SidebarDestinations.tiles.map { it.id },
        )
    }

    @Test
    fun `le groupe Onyx tient sur deux rangees pleines, alignees a gauche`() {
        // La géométrie : la grille range les tuiles par rangées entières (`chunked(columns)`, deux ou
        // trois colonnes selon la largeur), et une rangée incomplète garde ses cases vides — une
        // tuile orpheline ne s'étire donc jamais, et un groupe de quatre est **deux rangées pleines**
        // à deux colonnes.
        //
        // Ce lot-ci rend la grille **personnalisable** : elle ne dessine plus le groupe « Onyx » (ces
        // destinations vivent dans la bibliothèque du mode édition), mais la règle de rangée est la
        // même — la vérification porte donc sur la grille, pas sur le groupe qui n'y est plus.
        val onyx = SidebarDestinations.group(SidebarTileGroup.ONYX)
        assertEquals(4, onyx.size)
        assertEquals("la dernière rangée est pleine", 0, onyx.size % 2)
        assertTrue("la grille range ses tuiles par rangées entières", chatSidebar.contains("tiles.chunked(columns)"))
        assertTrue(
            "une rangée incomplète garde ses cases vides (alignement à gauche)",
            chatSidebar.contains("repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }"),
        )
        assertTrue(
            "les quatre destinations du groupe sont dans la bibliothèque par défaut",
            SidebarTileLayout.library(SidebarTileLayout.defaultIds).map { it.id } == onyx.map { it.id },
        )
    }

    @Test
    fun `la ligne Competences a quitte les Reglages, et rien d autre`() {
        assertFalse("plus d'entrée « Compétences » dans les Réglages", settings.contains("onOpenSkills"))
        assertFalse(settings.contains("AgentEntry(\"Skills\""))
        assertFalse("plus de rappel dans le routeur", app.contains("onOpenSkills"))
        // Les voisines restent : rien de plus n'a été retiré.
        assertTrue(settings.contains("AgentEntry(\"Capabilities\""))
        assertTrue(settings.contains("AgentEntry(\"Tools\""))
    }

    @Test
    fun `la route retiree ne peut pas revenir sans que le test tombe`() {
        val routes = app.substringAfter("private enum class Route {").substringBefore("\n}")
            .lines().joinToString("\n") { it.substringBefore("//") }
        assertFalse(
            "Route.SKILLS ne doit plus être déclarée",
            Regex("\\bSKILLS\\b").containsMatchIn(routes),
        )
        assertFalse("aucun appel à Route.SKILLS", app.contains("Route.SKILLS"))
        // Et l'éditeur, lui, garde sa route — c'est ce qui fait que rien n'a été perdu.
        assertTrue(app.contains("Route.SKILL_EDITOR ->"))
    }

    @Test
    fun `Capacites porte ce que Competences faisait`() {
        // La liste groupée par famille, la recherche et le compte.
        assertTrue(capabilitiesFile.contains("skillGroups(loaded, query)"))
        assertTrue(capabilitiesFile.contains("CountLine(countLine(CapabilityFamily.SKILLS"))
        // L'interrupteur par compétence et l'état de chacune.
        assertTrue(capabilitiesFile.contains("ServeSkillsApi(ServeClient(server)).toggle(skill.name, on)"))
        assertTrue(capabilitiesFile.contains("OnyxSwitch(checked = skill.enabled"))
        // L'ouverture pour la lire.
        assertTrue(capabilitiesFile.contains("SkillDetailPage("))
        assertTrue(skillPages.contains(".content(skill.name)"))
        // Le catalogue du hub, avec installer et retirer.
        assertTrue(skillPages.contains(".installFromCatalog(entry.identifier)"))
        assertTrue(skillPages.contains(".uninstallFromCatalog(entry.name)"))
    }

    @Test
    fun `l editeur reste atteignable depuis Capacites`() {
        // Le seul geste qui vivait dans « Compétences » et pas dans « Capacités » : il y est versé.
        assertTrue(capabilitiesFile.contains("onEditSkill"))
        assertTrue(capabilitiesFile.contains("text = \"New skill\""))
        assertTrue(capabilitiesFile.contains("onEdit = { onEditSkill(skillDetail.name) }"))
        assertTrue(editor.contains("fun SkillEditorScreen("))
        // Et les deux entrées du routeur le construisent : dans le volet, et en pleine page.
        assertTrue(app.contains("panePush(Route.SKILL_EDITOR.name)"))
        assertTrue(app.contains("navTo(Route.SKILL_EDITOR.name)"))
    }
}
