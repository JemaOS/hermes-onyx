package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotFace

/**
 * Ce qu'un ajout a donné — deux cas seulement, et chacun se dit.
 *
 * [Added] dit le profil créé **et** ce qui n'a pas pu être écrit : un import qui n'aurait appliqué
 * que la moitié du modèle sans le dire serait un mensonge par omission, exactement ce que l'aperçu
 * du partage existe pour éviter.
 */
sealed interface BotTemplateImportOutcome {
    /**
     * Le bot est sur le serveur. [notApplied] nomme ce que le lien portait et que l'écriture n'a
     * pas pris (les jeux d'outils, les compétences, le modèle par défaut) — vide quand tout est
     * passé.
     */
    data class Added(
        val profile: String,
        val notApplied: List<String> = emptyList(),
    ) : BotTemplateImportOutcome

    /** **Rien** n'a été créé : la raison, prête à être montrée. */
    data class Refused(val reason: String) : BotTemplateImportOutcome
}

/**
 * Ce que la feuille du partage montre : le lien prêt à envoyer, et si le prompt du bot y est.
 *
 * [withoutPrompt] est vrai quand le profil n'a pas de `SOUL.md` : le lien reste valable — nom,
 * rôle, visage, outils, compétences et modèle y sont — mais la feuille le dit, plutôt que de
 * laisser croire que la personnalité voyage avec.
 */
internal data class BotTemplateOffer(
    val link: String,
    val withoutPrompt: Boolean,
)

/**
 * Ce que la préparation d'un partage a donné : le lien prêt, ou la raison pour laquelle il n'y en a
 * pas (le codec a refusé le prompt, le serveur est muet, l'app n'a pas de Dashboard).
 */
internal sealed interface BotTemplateOfferOutcome {
    data class Ready(val offer: BotTemplateOffer) : BotTemplateOfferOutcome
    data class Refused(val reason: String) : BotTemplateOfferOutcome
}

/**
 * Le modèle que ce bot partage : ce que le serveur sait de lui, mis dans l'enveloppe du codec.
 *
 * Le prompt d'un bot est son `SOUL.md` (`api/profiles/{nom}/soul`) ; sans lui le lien part sans
 * personnalité, et la feuille du partage le dit au lieu de laisser croire qu'il est dedans. Les
 * outils et les compétences sont ceux que le serveur déclare **allumés** pour ce profil, jamais une
 * liste devinée.
 *
 * Le visage passe par [BotFace.parse] : le lien porte le code que ce téléphone dessine, pas une
 * valeur recopiée telle quelle du `ui_meta`.
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
 * Le nom de profil que prend le bot d'un lien : celui du modèle, rendu sûr pour le serveur, et
 * **libre** — un second import du même modèle s'appelle `-2`, `-3`…, comme la copie d'un bot.
 *
 * Un titre vide ou qui ne garde aucune lettre tombe sur `bot` : un profil sans nom ne se crée pas.
 */
internal fun templateHandle(title: String, taken: Set<String>): String {
    val base = botHandle(title)
        .map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
        .joinToString("")
        .trim('_', '-')
        // La place d'un suffixe `-99` : le serveur refuse un nom de profil trop long.
        .take(61)
        .ifEmpty { "bot" }
    if (base !in taken) return base
    return (2..99)
        .map { n -> "$base-$n" }
        .firstOrNull { candidate -> candidate !in taken }
        ?: "$base-${taken.size + 1}".take(64)
}

/**
 * Ce que la feuille d'aperçu montre d'un lien : le nom, le rôle, les outils, les compétences, le
 * modèle — et ce qui n'y est pas, toujours.
 *
 * Une phrase par ligne, dans l'ordre de l'énumération : c'est la moitié honnête de la promesse, et
 * elle se lit avant le bouton « Ajouter », pas après.
 */
internal fun previewLines(preview: BotTemplatePreview): List<String> = buildList {
    add(preview.name)
    add(preview.role)
    add(OnyxLocalization["Tools"] + " · " + if (preview.tools.isEmpty()) OnyxLocalization["None"] else preview.tools.joinToString(", "))
    add(OnyxLocalization["Skills"] + " · " + if (preview.skills.isEmpty()) OnyxLocalization["None"] else preview.skills.joinToString(", "))
    add(OnyxLocalization["Model"] + " · " + (preview.model ?: OnyxLocalization["Server default"]))
}
