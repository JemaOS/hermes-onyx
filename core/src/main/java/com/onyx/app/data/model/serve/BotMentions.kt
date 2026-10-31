package com.onyx.app.data.model.serve

import com.onyx.app.data.net.visibleBotProfiles
import java.util.Locale

/**
 * Les mentions `@` d'un message à un bot, comme Hermes Desktop les a : un mot nomme un autre bot,
 * et le message part avec une note qui dit quel bot chaque mot désigne. L'app n'envoie rien
 * elle-même à l'autre bot : c'est l'agent qui s'en charge avec son outil `message_agent`, avec ses
 * propres mots. Un mot qui ne nomme aucun bot, ou plus d'un, reste du texte ordinaire.
 *
 * Miroir de `BotMentions` (Echo/Services/Transport/ServerBots:302) : les mêmes mots réservés,
 * les mêmes formes taggables, le même filtrage de la liste du composeur, et la même insertion —
 * le mot en cours est remplacé par `@poignée ` et rien d'autre du brouillon ne bouge.
 */
object BotMentions {

    /** Les mots qu'un nom amical ne peut jamais devenir : les poignées de salle et celle du profil principal. */
    val reserved: Set<String> = setOf("all", "everyone", "user", "default", "hermes")

    private val NameBreak = Regex("[^a-z0-9_-]+")
    private val TagForm = Regex("[a-z0-9][a-z0-9_-]*")
    /** Le mot `@` en cours, à la fin du brouillon : ce qui suit le « @ » est rendu tel quel. */
    private val TypingPattern = Regex("(?:^|\\s)@([A-Za-z0-9_-]*)$")

    /**
     * Le mot en cours de frappe à la fin de [text] (« @sc » → « sc », « @ » → « »), ou `null`
     * quand il n'y en a pas.
     *
     * Le « @ » doit ouvrir le mot — au début du brouillon ou après une espace —, comme
     * `BotMentions.typing` : `a@b` dans une adresse ne propose donc rien. Le mot n'accepte que
     * des lettres, des chiffres, « _ » et « - » : une espace ou une virgule le termine et referme
     * la liste.
     */
    fun typing(text: String): String? = TypingPattern.find(text)?.groupValues?.get(1)

    /**
     * Les formes taggables d'un nom amical (« Research Buddy ») : « research-buddy » et
     * « researchbuddy ». Un mot réservé n'en est jamais une (« Hermes » ne se tague pas).
     */
    fun nameForms(value: String?): List<String> {
        val name = value.orEmpty().trim().lowercase(Locale.ROOT)
        if (name.isEmpty()) return emptyList()
        val slug = name.replace(NameBreak, "-").trim('-')
        val collapsed = name.replace(NameBreak, "")
        val forms = mutableListOf<String>()
        for (form in listOf(slug, collapsed)) {
            if (form in forms || form in reserved || !TagForm.matches(form)) continue
            forms += form
        }
        return forms
    }

    /** Tout ce à quoi un bot répond : sa poignée, le nom de son profil et ses noms amicaux. */
    fun forms(bot: BotProfile): Set<String> {
        val forms = mutableSetOf(bot.handle.lowercase(Locale.ROOT), bot.name.lowercase(Locale.ROOT))
        nameForms(bot.title).forEach { forms += it }
        nameForms(bot.displayName).forEach { forms += it }
        return forms
    }

    /**
     * Ce qu'une ligne choisie écrit dans le message : le nom amical du bot quand il en a un, sinon
     * sa poignée (`BotMentions.tag`).
     */
    fun tag(bot: BotProfile): String {
        for (friendly in listOf(bot.title, bot.displayName)) {
            val form = nameForms(friendly).firstOrNull()
            if (form != null) return form
        }
        return bot.handle
    }

    /** Les bots que ce mot en cours pourrait être, dans l'ordre du roster. */
    fun suggestions(
        typed: String,
        roster: List<BotProfile>,
        speaking: String?,
        mainName: String = "Onyx",
    ): List<BotProfile> {
        val wanted = typed.lowercase(Locale.ROOT)
        val to = speaking ?: "default"
        return roster.filter { bot ->
            bot.name != to && (
                wanted.isEmpty() ||
                    forms(bot).any { it.startsWith(wanted) } ||
                    bot.label(mainName).lowercase(Locale.ROOT).startsWith(wanted)
                )
        }
    }

    /**
     * [text] avec le mot en cours fini comme celui de [bot] : le « @ » et ce qui le suit sont
     * remplacés par `@` + sa forme, puis une espace. Le reste du brouillon — ce qui précède, y
     * compris l'espace qui ouvrait le mot — est gardé tel quel. Sans mot en cours, [text] est
     * rendu inchangé : une mention qui ne nomme personne reste du texte ordinaire.
     */
    fun complete(text: String, with: BotProfile): String {
        val typed = typing(text) ?: return text
        return text.dropLast(typed.length + 1) + "@" + tag(with) + " "
    }

    /**
     * Ce que la liste du composeur montre pour ce brouillon : les bots offerts par
     * `BotMentions.suggestions`, au plus six, quand un mot `@` est en cours de frappe.
     *
     * `dismissed` est le mot qu'une touche Échap a fait taire : la liste reste fermée tant que le
     * mot en cours est celui-là, et revient dès qu'il change. `speaking` est le bot dont c'est la
     * conversation (le profil principal quand il n'y en a pas) : il ne se propose jamais lui-même.
     * Un brouillon de plusieurs lignes n'ouvre pas la liste, comme `ComposerView.mentionSuggestions`.
     */
    fun menu(
        draft: String,
        roster: List<BotProfile>,
        speaking: String?,
        mainName: String = "Onyx",
        dismissed: String? = null,
    ): List<BotProfile> {
        if (draft.contains("\n")) return emptyList()
        val typed = typing(draft) ?: return emptyList()
        if (dismissed != null && dismissed == typed) return emptyList()
        return suggestions(typed, roster, speaking, mainName).take(6)
    }

    /**
     * Le roster que la liste des mentions lit : `BotRoster.listed` côté la référence — les bots montrables
     * (les profils cachés d'Hermes Desktop sont écartés), le profil principal d'abord, puis le plus
     * récemment parlé. Aucun quand le principal est seul : il n'y a personne d'autre à qui parler.
     */
    fun rosterForMentions(bots: List<BotProfile>, mainName: String = "Onyx"): List<BotProfile> {
        val listed = bots.visibleBotProfiles(mainName)
        return if (listed.any { !it.isDefault }) listed else emptyList()
    }
}
