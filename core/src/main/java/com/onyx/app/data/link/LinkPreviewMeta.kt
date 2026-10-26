package com.onyx.app.data.link

/**
 * Ce qu'une page dit d'elle-même: son titre et l'image de sa fiche.
 *
 * Côté c'est `LPMetadataProvider` qui s'en charge; Android n'a pas d'équivalent, donc le port
 * lit lui-même les balises Open Graph (`og:title`, `og:image`), puis les replis de Messages — la
 * balise `<title>`, puis l'icône du site. Tout ici est du texte: aucun accès Android, donc
 * éprouvable sur la JVM.
 */
data class LinkPreviewMeta(
    /** Le titre de la page, déjà nettoyé et décodé; `null` s'il est vide. */
    val title: String? = null,
    /** L'image de la page, telle qu'écrite dans la balise (relative possible); `null` sinon. */
    val imageUrl: String? = null,
) {
    companion object {
        private val metaTag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val titleTag = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
        private val attribute = Regex(
            """([a-zA-Z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        )

        /** Lit les balises de la page. [html] est le corps reçu de la page elle-même. */
        fun parse(html: String): LinkPreviewMeta {
            var title: String? = null
            var image: String? = null

            for (tag in metaTag.findAll(html)) {
                val attrs = attributes(tag.value)
                val key = (attrs["property"] ?: attrs["name"]).orEmpty().lowercase()
                val content = attrs["content"]?.takeIf { it.isNotBlank() } ?: continue
                when (key) {
                    "og:title", "twitter:title" -> if (title == null) title = content
                    "og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src" ->
                        if (image == null) image = content
                }
            }

            // Replis, dans l'ordre de Messages: la balise <title>, puis l'icône du site.
            if (title == null) title = titleTag.find(html)?.groupValues?.get(1)
            if (image == null) {
                for (tag in linkTag.findAll(html)) {
                    val attrs = attributes(tag.value)
                    if (!attrs["rel"].orEmpty().lowercase().contains("icon")) continue
                    val href = attrs["href"]?.takeIf { it.isNotBlank() } ?: continue
                    image = href
                    break
                }
            }

            return LinkPreviewMeta(
                title = decodeEntities(title)?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = image?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        /** Les attributs d'une balise, guillemets doubles, simples ou nus. */
        private fun attributes(tag: String): Map<String, String> = buildMap {
            for (match in attribute.findAll(tag)) {
                val name = match.groupValues[1].lowercase()
                val value = match.groupValues[2].ifEmpty {
                    match.groupValues[3].ifEmpty { match.groupValues[4] }
                }
                putIfAbsent(name, value)
            }
        }

        /** Les entités HTML courantes; de quoi lire un titre sans dépendance. */
        private fun decodeEntities(text: String?): String? {
            if (text == null) return null
            if (!text.contains('&')) return text
            return text
                .replace("&amp;", "&", ignoreCase = false)
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace(Regex("&#(\\d+);")) { match ->
                    match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value
                }
        }
    }
}
