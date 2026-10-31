package com.onyx.app.ui.capabilities

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'écran « Capacités » complété : ce que l'audit statique peut tenir sans appareil.
 *
 * Les trois familles rendues, la recherche sur chacune, l'ajout d'un serveur MCP avec sa validation,
 * le test qui remonte l'échec réel, l'activation, la suppression avec confirmation, la liste des
 * outils dépliable, la lecture d'une compétence et l'installation depuis le catalogue. Ce qui compte
 * ici est que **le chemin existe** — le rappel est fourni, relié à une route, et rien d'inerte ne
 * subsiste — et que l'écran reste dans le thème d'Onyx : **aucun composant Material**.
 */
class CapabilitiesExhaustiveWiringTest {

    private val root = File("src/main/java/com/onyx/app/ui/capabilities")
    private val screen = File(root, "CapabilitiesScreen.kt").readText()
    private val parts = File(root, "CapabilityParts.kt").readText()
    private val mcp = File(root, "McpPages.kt").readText()
    private val skills = File(root, "SkillPages.kt").readText()

    // MARK: - Les trois familles

    @Test
    fun `les trois familles sont rendues, comptees et filtrables`() {
        assertTrue(screen.contains("enum class CapabilityFamily"))
        assertTrue("les trois familles sont dans le sélecteur", screen.contains("AdaptiveSegmented("))
        assertTrue(screen.contains("SKILLS(\"Skills\", \"%d skills\", \"%d skill\""))
        assertTrue(screen.contains("TOOLS(\"Toolsets\", \"%d toolsets\", \"%d toolset\""))
        assertTrue(screen.contains("MCP(\"MCP\", \"%d MCP servers\", \"%d MCP server\""))
        // Le compte du sélecteur vient des listes chargées, jamais d'un nombre tenu à part.
        assertTrue(screen.contains("familyLabel(family, skills, toolsets, servers)"))
        assertTrue(screen.contains("return \"\$label \$count\""))
        // La recherche porte sur la famille regardée : un champ par famille, et le filtre appliqué.
        assertTrue(screen.contains("skillGroups(loaded, query)"))
        assertTrue(screen.contains("toolsetMatches(loaded, query)"))
        assertTrue(screen.contains("mcpMatches(loaded, query)"))
        // Le compte et le compte d'allumées lisent la liste **montrée** : ils ne peuvent pas
        // diverger de ce qui est affiché, et un interrupteur les change d'un coup.
        assertEquals(
            "un compte par famille",
            3,
            Regex("countLine\\(\\s*CapabilityFamily\\.(SKILLS|TOOLS|MCP)").findAll(screen).count(),
        )
        assertEquals(
            "le compte d'allumées porte sur les lignes montrées",
            3,
            Regex("shown\\.count \\{ it\\.enabled \\}").findAll(screen).count(),
        )
        // Chaque onglet dit ce que sa famille engage.
        assertTrue(screen.contains("\"Skills the agent can read when a request matches.\""))
        assertTrue(screen.contains("\"Toolsets the agent gets in new chats.\""))
        assertTrue(screen.contains("\"Changes apply from the next chat.\""))
    }

    @Test
    fun `un echec de chargement garde l'ecran utilisable, avec son reessai`() {
        assertTrue("chaque famille a son propre problème", screen.contains("var skillsProblem"))
        assertTrue(screen.contains("var toolsProblem"))
        assertTrue(screen.contains("var mcpProblem"))
        assertTrue("un bandeau et un réessai", parts.contains("fun FailureBanner"))
        assertTrue(parts.contains("retry: String = \"Try again\""))
        assertTrue(parts.contains("onRetry"))
        assertTrue("le réessai relit les trois familles", screen.contains("reloadKey += 1"))
        // Une route absente est nommée plutôt que rendue comme un code nu.
        assertTrue(screen.contains("\"This Hermes does not serve the MCP servers route.\""))
        assertTrue(screen.contains("http.status == 404"))
    }

    // MARK: - MCP

    @Test
    fun `l'ajout d'un serveur MCP passe par la route, avec sa validation`() {
        assertTrue("un chemin d'ajout existe", screen.contains("text = \"Add an MCP source\"") && screen.contains("onClick = onAdd"))
        assertTrue(screen.contains("if (addingServer) {"))
        assertTrue(screen.contains("McpAddPage("))
        assertTrue("le formulaire a ses champs", mcp.contains("\"Name\""))
        assertTrue(mcp.contains("\"Command\""))
        assertTrue(mcp.contains("\"Arguments\""))
        assertTrue(mcp.contains("\"Environment variables\""))
        assertTrue(mcp.contains("\"Address\""))
        assertTrue(mcp.contains("\"Authentication\""))
        assertTrue("la validation se fait avant l'envoi", mcp.contains("val refusal = mcpDraftProblem(draft)"))
        assertTrue("et le refus du serveur est dit", mcp.contains("mcpFailureMessage(it, draft.name.trim())"))
        assertTrue("l'écriture passe par la route d'ajout", mcp.contains(".addMcpServer(draft)"))
        assertTrue("une réussite rend le serveur écrit", screen.contains("onAdded = { added ->"))
    }

    @Test
    fun `le test d'un serveur remonte son resultat reel, jamais un succes suppose`() {
        assertTrue("la sonde est posée « en cours », puis remplacée", screen.contains("onResult(McpProbe(ok = false, summary = \"\"))"))
        assertTrue(screen.contains(".testMcp(name)"))
        assertTrue("un échec se lit comme un échec", mcp.contains("!probe.ok -> OnyxLocalization[\"Not connected\"]"))
        assertTrue(
            "un succès nomme le nombre d'outils en français",
            mcp.contains("probe.tools.isNotEmpty() -> OnyxLocalization.format("),
        )
        assertTrue("l'erreur réelle est montrée", mcp.contains("probe.error?.let { message ->"))
        assertTrue("la latence mesurée est montrée", mcp.contains("OnyxLocalization.format(\"Took %1\\\$s ms\", millis)"))
        assertTrue("aucun succès n'est écrit en dur", !mcp.contains("ok = true,"))
    }

    @Test
    fun `l'activation, la suppression et la liste des outils sont sur la fiche`() {
        assertTrue("l'interrupteur de la fiche écrit la route", screen.contains(".setMcpEnabled(openName, on)"))
        assertTrue("la suppression demande confirmation", mcp.contains("OnyxAlert("))
        assertTrue(mcp.contains("AlertAction(label = \"Delete\", destructive = true)"))
        assertTrue(mcp.contains("AlertAction(label = \"Cancel\")"))
        assertTrue("et passe par la route de retrait", mcp.contains(".removeMcpServer(entry.name)"))
        assertTrue("la liste est relue après le retrait", screen.contains("servers = servers?.filterNot { it.name == openName }"))
        assertTrue("les outils du serveur sont dépliables", mcp.contains("toolsOpen"))
        assertTrue(mcp.contains("DisclosureRow("))
        assertTrue("l'état vide de MCP dit quoi faire", screen.contains("\"Nothing here yet. Use “Add an MCP source” above.\""))
    }

    // MARK: - Outils

    @Test
    fun `un jeu d'outils montre son nom, sa description, son compte et ses outils`() {
        assertTrue(screen.contains("private fun ToolsetRow("))
        // LOT anatomie-des-contenus : la rangée est celle du gabarit (`ContentRow(title = …)`),
        // avec sa vignette, sa pastille de comptage et son état à droite.
        assertTrue("le nom et la description", screen.contains("title = toolset.title,"))
        assertTrue(screen.contains("OnyxLocalization.translateParagraphs(it)"))
        assertTrue(
            "le compte d'outils du jeu, au singulier comme au pluriel",
            screen.contains("if (toolset.tools.size == 1) \"%d tool\" else \"%d tools\""),
        )
        assertTrue("les outils se déplient", screen.contains("if (open) {"))
        assertTrue(screen.contains("toolset.tools.forEach { tool ->"))
        assertTrue("et l'interrupteur du groupe est là", screen.contains("OnyxSwitch(checked = toolset.enabled"))
    }

    // MARK: - Compétences

    @Test
    fun `une competence s'ouvre pour etre lue et le catalogue installe et retire`() {
        assertTrue(screen.contains("onOpen = { skill -> openSkill = skill }"))
        assertTrue(screen.contains("SkillDetailPage("))
        assertTrue("le contenu est lu par la route", skills.contains(".content(skill.name)"))
        assertTrue("la famille et la description sont montrées", skills.contains("\"Category\""))
        assertTrue("l'état réel : installée", skills.contains("entry.installed -> \"Installed\""))
        assertTrue("l'état réel : disponible", skills.contains("else -> \"Available\""))
        assertTrue("l'état réel : en cours", skills.contains("busy -> \"Installing…\""))
        assertTrue("l'installation passe par la route du hub", skills.contains(".installFromCatalog(entry.identifier)"))
        assertTrue("le retrait aussi", skills.contains(".uninstallFromCatalog(entry.name)"))
        assertTrue("et la liste est relue après coup", skills.contains("ServeSkillsApi(ServeClient(server)).catalog()"))
    }

    // MARK: - Thème

    @Test
    fun `aucun composant Material ne s'invite dans l'ecran`() {
        for ((name, source) in listOf(
            "CapabilitiesScreen.kt" to screen,
            "CapabilityParts.kt" to parts,
            "McpPages.kt" to mcp,
            "SkillPages.kt" to skills,
        )) {
            assertFalse("$name importe Material", source.contains("androidx.compose.material"))
            assertFalse("$name utilise un composant Material", source.contains("MaterialTheme"))
        }
    }

    @Test
    fun `les mots anglais de l'ecran sont tous traduits`() {
        val french = Locale.FRANCE
        val declared = linkedSetOf(
            "Add an MCP source",
            "Add the server",
            "Adding…",
            "Delete this server?",
            "Deleting…",
            "Test again",
            "Back to the servers",
            "Tools this server exposes",
            "Category",
            "Available",
            "Installed",
            "Installing…",
            "Removing…",
            "No tools were listed.",
            "Nothing here yet. Use “Add an MCP source” above.",
            "Over the network",
            "Local command",
            "Transport unknown",
            "Sign-in (OAuth)",
            "Header token",
            "Expanded",
            "Collapsed",
            "Reading…",
        )
        val untranslated = declared.filter { OnyxLocalization.translate(it, french) == it }
        assertTrue("libellés encore en anglais : $untranslated", untranslated.isEmpty())
    }
}
