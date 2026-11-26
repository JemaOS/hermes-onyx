package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotFace

/**
 * What an add produced — only two cases, and each is stated.
 *
 * [Added] states the created profile **and** what could not be written: an import that would have applied
 * only half of the template without saying so would be a lie by omission, exactly what the share
 * preview exists to avoid.
 */
sealed interface BotTemplateImportOutcome {
    /**
     * The bot is on the server. [notApplied] names what the link carried and that the write did
     * not take (the toolsets, the skills, the default model) — empty when everything
     * went through.
     */
    data class Added(
        val profile: String,
        val notApplied: List<String> = emptyList(),
    ) : BotTemplateImportOutcome

    /** **Nothing** was created: the reason, ready to be shown. */
    data class Refused(val reason: String) : BotTemplateImportOutcome
}

/**
 * What the share sheet shows: the link ready to send, and whether the bot's prompt is in it.
 *
 * [withoutPrompt] is true when the profile has no `SOUL.md`: the link stays valid — name,
 * role, face, tools, skills and model are in it — but the sheet says so, rather than
 * letting one believe that the personality travels along.
 */
internal data class BotTemplateOffer(
    val link: String,
    val withoutPrompt: Boolean,
)

/**
 * What preparing a share produced: the link ready, or the reason there is none
 * (the codec refused the prompt, the server is silent, the app has no Dashboard).
 */
internal sealed interface BotTemplateOfferOutcome {
    data class Ready(val offer: BotTemplateOffer) : BotTemplateOfferOutcome
    data class Refused(val reason: String) : BotTemplateOfferOutcome
}

/**
 * The template that this bot shares: what the server knows about it, put in the codec's envelope.
 *
 * A bot's prompt is its `SOUL.md` (`api/profiles/{nom}/soul`); without it the link leaves without a
 * personality, and the share sheet says so instead of letting one believe it is in it. The
 * tools and skills are those the server declares **switched on** for this profile, never a
 * guessed list.
 *
 * The face goes through [BotFace.parse]: the link carries the code that this phone draws, not a
 * value copied as is from the `ui_meta`.
 */
internal fun sharedTemplateOf(
    bot: BotProfile,
    label: String,
    soul: String? = null,
    tools: List<String> = emptyList(),
    skills: List<String> = emptyList(),
): BotTemplateShare = BotTemplateShare(
    spec = BotTemplateSpec(
        id = bot.name,
        title = label,
        about = bot.description,
        soul = soul.orEmpty(),
        face = BotFace.parse(bot.faceCode),
    ),
    tools = tools.filter { it.isNotBlank() },
    skills = skills.filter { it.isNotBlank() },
    model = bot.model,
)

/**
 * The profile name that a link's bot takes: the template's, made safe for the server, and
 * **free** — a second import of the same template is called `-2`, `-3`…, like a bot's copy.
 *
 * An empty title or one that keeps no letter falls back to `bot`: a profile without a name is not created.
 */
internal fun templateHandle(title: String, taken: Set<String>): String {
    val base = botHandle(title)
        .map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
        .joinToString("")
        .trim('_', '-')
        // Room for a `-99` suffix: the server refuses a profile name that is too long.
        .take(61)
        .ifEmpty { "bot" }
    if (base !in taken) return base
    return (2..99)
        .map { n -> "$base-$n" }
        .firstOrNull { candidate -> candidate !in taken }
        ?: "$base-${taken.size + 1}".take(64)
}

/**
 * What the preview sheet shows of a link: the name, the role, the tools, the skills, the
 * model — and what is not in it, always.
 *
 * One sentence per line, in the order of the enumeration: it is the honest half of the promise, and
 * it is read before the « Ajouter » (Add) button, not after.
 */
internal fun previewLines(preview: BotTemplatePreview): List<String> = buildList {
    add(preview.name)
    add(preview.role)
    add(OnyxLocalization["Tools"] + " · " + if (preview.tools.isEmpty()) OnyxLocalization["None"] else preview.tools.joinToString(", "))
    add(OnyxLocalization["Skills"] + " · " + if (preview.skills.isEmpty()) OnyxLocalization["None"] else preview.skills.joinToString(", "))
    add(OnyxLocalization["Model"] + " · " + (preview.model ?: OnyxLocalization["Server default"]))
}
