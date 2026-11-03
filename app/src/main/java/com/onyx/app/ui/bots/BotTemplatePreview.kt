package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotFace
import java.util.Locale

/**
 * Ce qu'un lien de modèle ne porte **pas** — énuméré, nommé, et montré au destinataire avant qu'il
 * ajoute le bot.
 *
 * Un aperçu qui ne dirait que ce que le lien contient laisserait croire que le bot arrive complet.
 * Ces six lignes sont la moitié honnête de la promesse : ce qui manque est dit, pas découvert
 * après coup.
 */
enum class BotTemplateOmission(val key: String) {
    /** Une clé, un jeton, un mot de passe ou une adresse de serveur. Le codec les refuse. */
    CREDENTIALS(
        "Not in the link: the API key, the token and the server address. A template that carries one is refused.",
    ),

    /** Les conversations, la mémoire, ce que le bot a appris de la personne. */
    CONVERSATIONS("Not in the link: the conversations, the memory and what the bot has learned."),

    /** Ses routines et ses horaires : elles appartiennent au serveur, pas au modèle. */
    ROUTINES("Not in the link: the routines and schedules."),

    /** Sa voix : elle est choisie sur l'appareil, et ne voyage pas dans un texte. */
    VOICE("Not in the link: the voice."),

    /** Qui peut le voir, et ses réglages de partage. */
    VISIBILITY("Not in the link: who can see the bot, and its sharing settings."),

    /** Les fichiers et les pièces jointes qu'il peut lire. */
    FILES("Not in the link: the files and attachments the bot can reach."),
    ;

    /** La phrase, traduite si l'interface est en français. */
    fun label(locale: Locale = OnyxLanguage.preferred): String = OnyxLocalization.translate(key, locale)
}

/**
 * L'aperçu d'un modèle partagé : ce que le destinataire voit **avant** d'ajouter le bot.
 *
 * Il nomme ce qui est là — le nom, le rôle, les outils, les compétences, le modèle par défaut — et
 * ce qui n'y est pas ([omissions]). C'est une vue du partage, pas un écran : elle ne décide de
 * rien, ne parle à aucun serveur, et se teste sans Android.
 */
data class BotTemplatePreview(
    /** Le nom que le bot prendra. */
    val name: String,
    /** Ce qu'il fait, tel que le modèle le dit — ou « aucun rôle indiqué ». */
    val role: String,
    /** Les outils cochés, vides si le modèle n'en impose aucun. */
    val tools: List<String>,
    /** Les compétences cochées, vides si le modèle n'en impose aucune. */
    val skills: List<String>,
    /** Le modèle par défaut, ou `null` : le bot prendra celui du serveur. */
    val model: String?,
    /** Le visage que le bot recevra, ou `null`. */
    val face: BotFace?,
    /** Ce qui n'est pas dans le lien. Jamais vide : l'honnêteté ne se règle pas. */
    val omissions: List<BotTemplateOmission> = BotTemplateOmission.entries,
) {
    /** Le visage décrit en une ligne, ou `null` s'il n'y en a pas. */
    fun faceLabel(locale: Locale = OnyxLanguage.preferred): String? = face?.describe(locale)

    /** Les phrases des absences, dans l'ordre de l'énumération. */
    fun omissionLabels(locale: Locale = OnyxLanguage.preferred): List<String> =
        omissions.map { it.label(locale) }

    companion object {
        /** La phrase qui remplace un rôle vide : ne rien dire serait laisser croire qu'il y en a un. */
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
