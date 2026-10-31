package com.onyx.app.ui.settings.voice

import kotlinx.serialization.Serializable

/**
 * Une règle de préfixe parlé (`SpokenPrefix`) : un mot entendu devient un préfixe de texte, et
 * peut envoyer vers un bot ou un modèle.
 */
@Serializable
data class SpokenPrefix(
    val word: String = "",
    val aliases: List<String> = emptyList(),
    val prefix: String = "",
    /** Le profil du bot vers lequel aller, ou `null`. */
    val bot: String? = null,
    /** Le modèle à adopter, ou `null`. */
    val model: String? = null,
    val provider: String? = null,
) {
    val isEmpty: Boolean get() = word.isBlank() && aliases.isEmpty() && prefix.isEmpty() && bot == null && model == null

    /** Ce que la règle fait, sur la droite de la rangée. */
    fun summary(botName: (String) -> String?): String {
        val parts = buildList {
            bot?.takeIf { it.isNotEmpty() }?.let { add("@" + (botName(it) ?: it)) }
            prefix.takeIf { it.isNotEmpty() }?.let { add("«" + it.replace(" ", "␣") + "»") }
            model?.takeIf { it.isNotEmpty() }?.let { add("→ $it") }
        }
        return parts.joinToString(" ").ifEmpty { "—" }
    }
}

/** Ce qu'une phrase devient une fois la règle appliquée. */
data class VoiceRoute(val text: String, val bot: String?, val model: String?)

/**
 * Applique la première règle qui correspond, comme `VoiceRouting.route` côté référence.
 *
 * Le mot doit ouvrir la phrase ; les alias attrapent ce que la reconnaissance entend à la place.
 */
fun routeVoiceText(text: String, rules: List<SpokenPrefix>): VoiceRoute {
    val trimmed = text.trimStart()
    val lower = trimmed.lowercase()

    fun matchAt(word: String): Int? {
        val clean = word.trim().lowercase()
        if (clean.isEmpty() || !lower.startsWith(clean)) return null
        val rest = trimmed.substring(clean.length)
        // Le mot doit être un mot entier : « Claude, … » ou « Claude … », pas « Claudette ».
        if (rest.isNotEmpty() && rest.first().isLetterOrDigit()) return null
        return clean.length
    }

    for (rule in rules) {
        val candidates = listOf(rule.word) + rule.aliases
        val matched = candidates.firstNotNullOfOrNull { matchAt(it) } ?: continue
        val rest = trimmed.substring(matched).trimStart(' ', ',', ':', '-', '—')
        val routed = if (rule.prefix.isEmpty()) rest else rule.prefix + rest
        if (routed.isBlank() && rule.bot == null && rule.model == null) continue
        return VoiceRoute(
            text = routed,
            bot = rule.bot?.takeIf { it.isNotEmpty() },
            model = rule.model?.takeIf { it.isNotEmpty() },
        )
    }
    return VoiceRoute(text = text, bot = null, model = null)
}
