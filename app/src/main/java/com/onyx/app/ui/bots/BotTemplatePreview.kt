package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotFace
import java.util.Locale

/**
 * What a template link does **not** carry — enumerated, named, and shown to the recipient before they
 * add the bot.
 *
 * A preview that said only what the link contains would let one believe the bot arrives complete.
 * These six lines are the honest half of the promise: what is missing is said, not discovered
 * after the fact.
 */
enum class BotTemplateOmission(val key: String) {
    /** A key, a token, a password or a server address. The codec refuses them. */
    CREDENTIALS(
        "Not in the link: the API key, the token and the server address. A template that carries one is refused.",
    ),

    /** The conversations, the memory, what the bot has learned about the person. */
    CONVERSATIONS("Not in the link: the conversations, the memory and what the bot has learned."),

    /** Its routines and schedules: they belong to the server, not to the template. */
    ROUTINES("Not in the link: the routines and schedules."),

    /** Its voice: it is chosen on the device, and does not travel in a text. */
    VOICE("Not in the link: the voice."),

    /** Who can see it, and its sharing settings. */
    VISIBILITY("Not in the link: who can see the bot, and its sharing settings."),

    /** The files and attachments it can read. */
    FILES("Not in the link: the files and attachments the bot can reach."),
    ;

    /** The sentence, translated if the interface is in French. */
    fun label(locale: Locale = OnyxLanguage.preferred): String = OnyxLocalization.translate(key, locale)
}

/**
 * The preview of a shared template: what the recipient sees **before** adding the bot.
 *
 * It names what is there — the name, the role, the tools, the skills, the default model — and
 * what is not in it ([omissions]). It is a view of the share, not a screen: it decides
 * nothing, talks to no server, and is tested without Android.
 */
data class BotTemplatePreview(
    /** The name that the bot will take. */
    val name: String,
    /** What it does, as the template says it — or « aucun rôle indiqué » (no role given). */
    val role: String,
    /** The checked tools, empty if the template imposes none. */
    val tools: List<String>,
    /** The checked skills, empty if the template imposes none. */
    val skills: List<String>,
    /** The default model, or `null`: the bot will take the server's. */
    val model: String?,
    /** The face that the bot will receive, or `null`. */
    val face: BotFace?,
    /** What is not in the link. Never empty: honesty is not a setting. */
    val omissions: List<BotTemplateOmission> = BotTemplateOmission.entries,
) {
    /** The face described in one line, or `null` if there is none. */
    fun faceLabel(locale: Locale = OnyxLanguage.preferred): String? = face?.describe(locale)

    /** The sentences of the omissions, in the order of the enumeration. */
    fun omissionLabels(locale: Locale = OnyxLanguage.preferred): List<String> =
        omissions.map { it.label(locale) }

    companion object {
        /** The sentence that replaces an empty role: saying nothing would let one believe there is one. */
        const val NO_ROLE = "No role given."

        fun of(share: BotTemplateShare): BotTemplatePreview = from(share.spec, share.tools, share.skills, share.model)

        fun from(
            spec: BotTemplateSpec,
            tools: List<String> = emptyList(),
            skills: List<String> = emptyList(),
            model: String? = null,
        ): BotTemplatePreview = BotTemplatePreview(
            name = spec.title.ifBlank { OnyxLocalization.translate("Untitled bot") },
            role = spec.about.ifBlank { OnyxLocalization.translate(NO_ROLE) },
            tools = tools.filter { it.isNotBlank() },
            skills = skills.filter { it.isNotBlank() },
            model = model?.takeIf { it.isNotBlank() },
            face = spec.face,
        )
    }
}
