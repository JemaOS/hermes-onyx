package com.onyx.app.data.model.serve

import com.onyx.app.data.net.DashboardSkill
import com.onyx.app.data.net.GatewayToolset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La recherche et les comptes des trois familles : ce que l'écran tient sans le montrer.
 *
 * La règle mesurée ici est celle qui se voit à l'écran — **le compte affiché est celui de la liste**
 * — pour les compétences, les jeux d'outils et les serveurs MCP. Une recherche doit porter sur tout
 * ce que la ligne peut dire : le nom, la description et, pour un jeu, les **noms de ses outils**.
 */
class CapabilityFilteringTest {

    private val toolsets = listOf(
        GatewayToolset(
            name = "web",
            label = "Web Search & Scraping",
            description = "web_search, web_extract",
            enabled = true,
            tools = listOf("web_search", "web_extract"),
        ),
        GatewayToolset(name = "terminal", label = "Terminal & Processes", enabled = false, tools = listOf("terminal")),
        GatewayToolset(name = "kanban", label = "Kanban", enabled = true, tools = emptyList()),
    )

    private val servers = listOf(
        McpServer(
            name = "files",
            target = "npx -y @modelcontextprotocol/server-filesystem",
            enabled = true,
            transport = "stdio",
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-filesystem"),
        ),
        McpServer(
            name = "corpus",
            target = "https://corpuslaw.us/api/mcp",
            enabled = false,
            transport = "http",
            url = "https://corpuslaw.us/api/mcp",
            auth = "oauth",
        ),
    )

    @Test
    fun `la recherche d'un jeu d'outils porte sur le nom, le libelle et les outils`() {
        assertEquals(listOf("web"), toolsetMatches(toolsets, "web").map { it.name })
        assertEquals(listOf("web"), toolsetMatches(toolsets, "scraping").map { it.name })
        // « web_extract » est un nom d'outil, pas un mot du libellé : il doit ramener le jeu.
        assertEquals(listOf("web"), toolsetMatches(toolsets, "web_extract").map { it.name })
        assertEquals(listOf("terminal"), toolsetMatches(toolsets, "TERMINAL").map { it.name })
        assertEquals(emptyList<GatewayToolset>(), toolsetMatches(toolsets, "rien de tel"))
    }

    @Test
    fun `la recherche d'un serveur MCP porte sur son nom, son transport et sa cible`() {
        assertEquals(listOf("files"), mcpMatches(servers, "files").map { it.name })
        assertEquals(listOf("files"), mcpMatches(servers, "npx").map { it.name })
        assertEquals(listOf("corpus"), mcpMatches(servers, "corpuslaw").map { it.name })
        assertEquals(listOf("corpus"), mcpMatches(servers, "http").map { it.name })
        assertEquals(emptyList<McpServer>(), mcpMatches(servers, "rien de tel"))
    }

    @Test
    fun `le compte suit la liste, filtree ou non`() {
        assertEquals(3, toolsetMatchCount(toolsets, ""))
        assertEquals(1, toolsetMatchCount(toolsets, "web"))
        assertEquals(2, mcpMatchCount(servers, ""))
        assertEquals(1, mcpMatchCount(servers, "corpus"))
        assertEquals(toolsetMatches(toolsets, "web").size, toolsetMatchCount(toolsets, "web"))
        assertEquals(mcpMatches(servers, "corpus").size, mcpMatchCount(servers, "corpus"))
    }

    @Test
    fun `les familles se trient allumees d'abord, puis par nom`() {
        assertEquals(listOf("kanban", "web", "terminal"), toolsetMatches(toolsets, "").map { it.name })
        assertEquals(listOf("files", "corpus"), mcpMatches(servers, "").map { it.name })
    }

    @Test
    fun `les competences se comptent comme elles se groupent`() {
        val skills = listOf(
            DashboardSkill(name = "arxiv", description = "Papiers", category = "research", enabled = true),
            DashboardSkill(name = "search", description = "Chercher", category = "web", enabled = false),
            DashboardSkill(name = "sans-famille", description = "Autre"),
        )
        assertEquals(3, skillMatchCount(skills, ""))
        assertEquals(1, skillMatchCount(skills, "papiers"))
        assertEquals(1, skillMatchCount(skills, "arxiv"))
        assertEquals(3, skillMatchCount(skills, "e"))
        val groups = skillGroups(skills, "")
        assertEquals("la famille sans nom reste en dernier", listOf("research", "web", "other"), groups.keys.toList())
        assertEquals(groups.values.sumOf { it.size }, skillMatchCount(skills, ""))
    }

    @Test
    fun `le transport d'un serveur se nomme, meme quand le serveur ne le dit pas`() {
        assertEquals("Local command", mcpTransportLabel(servers.first()))
        assertEquals("Over the network", mcpTransportLabel(servers.last()))
        // Une version antérieure qui ne dit ni transport ni champ : la ligne l'avoue.
        assertEquals("Transport unknown", mcpTransportLabel(McpServer(name = "muet")))
        // Un `command` sans `transport` se lit quand même comme une commande locale.
        assertEquals("Local command", mcpTransportLabel(McpServer(name = "vieux", command = "npx")))
    }

    @Test
    fun `le mode d'authentification se nomme, ou reste absent`() {
        assertEquals("Sign-in (OAuth)", mcpAuthLabel(servers.last()))
        assertEquals("Header token", mcpAuthLabel(McpServer(name = "x", auth = "header")))
        assertEquals(null, mcpAuthLabel(McpServer(name = "x", auth = "none")))
        assertEquals(null, mcpAuthLabel(McpServer(name = "x")))
    }

    @Test
    fun `la ligne de commande se recompose du serveur tel qu'il l'a ecrite`() {
        assertEquals("npx -y @modelcontextprotocol/server-filesystem", servers.first().commandLine)
        assertEquals("https://corpuslaw.us/api/mcp", servers.last().commandLine)
        assertTrue(servers.first().isLocalCommand)
        assertTrue(!servers.last().isLocalCommand)
    }
}
