package com.onyx.app.data.model.serve

import com.onyx.app.data.net.GatewayToolset

/**
 * La recherche et le tri des deux familles qui n'ont pas de sous-groupes : les **jeux d'outils** et
 * les **serveurs MCP**.
 *
 * Comme pour les compétences (`skillGroups`), la recherche porte sur tout ce que la ligne peut dire
 * — pour un jeu d'outils son libellé, sa description et les **noms de ses outils** ; pour un serveur
 * MCP son nom, son transport, son adresse et sa ligne de commande. Chercher « web » doit ramener le
 * jeu « Recherche et lecture web » comme le serveur qui expose un outil `web_search`.
 *
 * Ces fonctions sont **pures** : elles se mesurent sans écran, et c'est ce qui permet de tenir la
 * règle « le compte affiché est celui de la liste ».
 */

/** Vrai si [needle] se lit dans le nom, le libellé, la description ou les outils du jeu. */
private fun GatewayToolset.matches(needle: String): Boolean =
    listOf(name, label.orEmpty(), description.orEmpty()).any { it.contains(needle, ignoreCase = true) } ||
        tools.any { it.contains(needle, ignoreCase = true) }

/** Vrai si [needle] se lit dans ce qu'un serveur MCP montre de lui-même. */
private fun McpServer.matches(needle: String): Boolean =
    listOf(name, target, url.orEmpty(), commandLine, transport, auth.orEmpty())
        .any { it.contains(needle, ignoreCase = true) } ||
        args.any { it.contains(needle, ignoreCase = true) }

/** Les jeux d'outils que [query] retient, allumés d'abord puis par nom — rien de plus. */
fun toolsetMatches(toolsets: List<GatewayToolset>, query: String): List<GatewayToolset> {
    val needle = query.trim()
    return toolsets
        .filter { needle.isEmpty() || it.matches(needle) }
        .sortedWith(compareByDescending<GatewayToolset> { it.enabled }.thenBy { it.name })
}

/** Le nombre de jeux d'outils que [query] retient, sans les trier. */
fun toolsetMatchCount(toolsets: List<GatewayToolset>, query: String): Int {
    val needle = query.trim()
    return if (needle.isEmpty()) toolsets.size else toolsets.count { it.matches(needle) }
}

/** Les serveurs MCP que [query] retient, allumés d'abord puis par nom. */
fun mcpMatches(servers: List<McpServer>, query: String): List<McpServer> {
    val needle = query.trim()
    return servers
        .filter { needle.isEmpty() || it.matches(needle) }
        .sortedWith(compareByDescending<McpServer> { it.enabled }.thenBy { it.name })
}

/** Le nombre de serveurs MCP que [query] retient, sans les trier. */
fun mcpMatchCount(servers: List<McpServer>, query: String): Int {
    val needle = query.trim()
    return if (needle.isEmpty()) servers.size else servers.count { it.matches(needle) }
}

/**
 * Le mot du transport, tel qu'il s'affiche sous le nom — une clé du dictionnaire, jamais un code.
 *
 * Un serveur d'une version antérieure ne dit pas son transport : ses champs (`url`, `command`) le
 * disent à sa place, et s'ils sont muets aussi, la ligne l'avoue plutôt que de deviner.
 */
fun mcpTransportLabel(entry: McpServer): String {
    val transport = when {
        entry.transport == "http" || entry.transport == "stdio" -> entry.transport
        entry.url != null -> "http"
        entry.command != null -> "stdio"
        else -> ""
    }
    return when (transport) {
        "http" -> "Over the network"
        "stdio" -> "Local command"
        else -> "Transport unknown"
    }
}

/** Le mode d'authentification tel qu'il s'affiche, ou `null` quand il n'y en a pas. */
fun mcpAuthLabel(entry: McpServer): String? = when (entry.auth?.lowercase()) {
    "oauth" -> "Sign-in (OAuth)"
    "header" -> "Header token"
    "none", null -> null
    else -> entry.auth
}
