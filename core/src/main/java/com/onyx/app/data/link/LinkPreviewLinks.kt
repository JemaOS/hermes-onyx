package com.onyx.app.data.link

/**
 * Quel lien d'un message reçoit sa fiche.
 *
 * Comme `LinkPreviewLinks`. Le premier choix est le **premier** lien
 * http/https du message, écrit en clair ou en lien Markdown — jamais dans du code, jamais une image.
 */
object LinkPreviewLinks {

    /** Un lien http ou https; s'arrête à l'espace ou à un délimiteur de balisage. */
    private val urlPattern = Regex("""https?://[^\s<>"'`\]]+""", RegexOption.IGNORE_CASE)

    /** Le bout d'un lien qui n'en fait pas partie (ponctuation de fin, parenthèse fermante). */
    private const val TRIM = ").,;:!?»\"'"

    private val codeBlock = Regex("```[\\s\\S]*?(```|$)")
    private val inlineCode = Regex("`[^`\n]*`")
    private val picture = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")

    /** L'adresse du premier lien web, hôte compris, ou `null`. */
    fun first(text: String): String? {
        if (!text.contains("http")) return null
        val prose = withoutCodeOrPictures(text)
        for (match in urlPattern.findAll(prose)) {
            val url = match.value.trimEnd { it in TRIM }
            if (host(url) != null) return url
        }
        return null
    }

    /** Le texte débarrassé des blocs de code, du code en ligne et des images Markdown. */
    fun withoutCodeOrPictures(text: String): String = text
        .replace(codeBlock, " ")
        .replace(inlineCode, " ")
        .replace(picture, " ")

    /** L'hôte d'une adresse http/https, ou `null` — sans port, comme `url.host`. */
    fun host(url: String): String? {
        val match = Regex("""^https?://([^/?#\s]+)""", RegexOption.IGNORE_CASE).find(url) ?: return null
        val authority = match.groupValues[1].substringAfterLast('@')
        return authority.substringBefore(':').takeIf { it.isNotEmpty() }
    }
}
