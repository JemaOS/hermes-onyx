package com.onyx.app.data.net

/**
 * L'adresse du pont, écrite comme on l'écrit à la main, ramenée à une URL de WebSocket.
 *
 * Le pont parle WebSocket, mais personne ne veut taper `ws://…:8795/ws`: on tape l'adresse du
 * service, telle qu'elle s'affiche dans un navigateur. Les deux formes marchent, et un hôte sans
 * schéma aussi — c'est la faute de frappe la plus fréquente.
 *
 * Le schéma se déduit de la présence d'un **port**:
 *
 * - un **port explicite** (`192.168.1.83:8795`) est le pont local, qui parle en clair: `ws://`;
 * - un hôte **sans port** est un nom public — un nom de tunnel (`xxx.trycloudflare.com`) — servi
 * derrière un terminateur TLS: `wss://`.
 *
 * Se tromper là-dessus ne donne pas une erreur franche: `ws://` sur un nom de tunnel part sur le
 * port 80, où l'edge répond par une redirection HTTP ordinaire. La poignée de main WebSocket
 * n'aboutit jamais, et l'écran « Ordinateur » reste sur « le pont n'est pas connecté » sans dire
 * pourquoi. Un schéma écrit à la main (`http`, `https`, `ws`, `wss`) reste donc **respecté**:
 * c'est l'utilisateur qui décide quand il l'écrit.
 */
object ComputerAddress {

    /** `null` quand rien d'exploitable n'a été saisi. */
    fun websocketUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null

        val (scheme, rest) = when {
            trimmed.startsWith("wss://", ignoreCase = true) -> "wss" to trimmed.substring(6)
            trimmed.startsWith("ws://", ignoreCase = true) -> "ws" to trimmed.substring(5)
            trimmed.startsWith("https://", ignoreCase = true) -> "wss" to trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) -> "ws" to trimmed.substring(7)
            else -> guessScheme(trimmed) to trimmed
        }
        if (rest.isEmpty() || rest.startsWith("/")) return null

        val host = rest.substringBefore('/')
        if (host.isEmpty()) return null

        // Le chemin est nettoyé de ses barres: `hote//ordinateur/` et `hote/ordinateur` mènent au
        // même point, et une barre finale ne doit pas fabriquer un `//ws` cassé.
        val path = rest.substringAfter('/', "").trim('/')
        val suffix = when {
            path.isEmpty() -> "/ws"
            path == "ws" -> "/ws"
            path.endsWith("/ws") -> "/$path"
            else -> "/$path/ws"
        }
        return "$scheme://$host$suffix"
    }

    /**
     * Le schéma d'un hôte écrit sans le sien.
     *
     * Le port est le seul signal disponible, et il est fiable: le pont local s'annonce toujours
     * avec le sien (`:8795`), un nom de tunnel n'en a jamais. Un nom sans port qui répondrait en
     * clair n'existe pas dans l'usage visé — alors qu'un nom sans port derrière TLS est le cas
     * courant, et c'est celui qu'il faut réussir du premier coup, à 1000 km de la machine.
     */
    private fun guessScheme(authority: String): String =
        if (hasPort(authority.substringBefore('/'))) "ws" else "wss"

    /** Un port explicite: `hote:8795`, ou `[::1]:8795` — le `:` qui n'appartient pas à l'IPv6. */
    private fun hasPort(host: String): Boolean {
        val closing = host.lastIndexOf(']')
        return if (closing >= 0) host.indexOf(':', closing) >= 0 else host.contains(':')
    }

    /** La même adresse, débarrassée de son schéma et de son chemin — pour l'affichage. */
    fun display(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withoutScheme = trimmed
            .removePrefix("wss://").removePrefix("ws://")
            .removePrefix("https://").removePrefix("http://")
        return withoutScheme.trimEnd('/')
    }
}
