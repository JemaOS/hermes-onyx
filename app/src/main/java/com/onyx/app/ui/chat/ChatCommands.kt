package com.onyx.app.ui.chat

import com.onyx.app.localization.OnyxLocalization

/**
 * Les commandes `/` que Onyx traite elle-même, sans les envoyer au serveur.
 *
 * Miroir du cas spécial de `ComposerView.send` (Echo/Views/ComposerView:569) :
 * `/model` nu ouvre le sélecteur de modèle ; avec un argument, la ligne reste une commande
 * `slash` ordinaire et part au gateway.
 */
internal const val MODEL_COMMAND = "/model"

/**
 * Vrai si le brouillon est exactement `/model` (espaces et casse ignorés).
 *
 * Un `/model gpt-5` n'est **pas** cette commande : c'est une ligne pour le serveur.
 */
internal fun isModelCommand(draft: String): Boolean = draft.trim().lowercase() == MODEL_COMMAND

/**
 * Garantit `/model` en tête du menu `/` quand il correspond à ce qui est tapé.
 *
 * Le menu vient de `complete.slash` du gateway ; `/model` est une commande locale que Onyx
 * intercepte à l'envoi, donc on l'ajoute au menu pour qu'elle soit visible même si le serveur ne
 * la propose pas. Rien n'est dupliqué si le gateway la renvoie déjà.
 */
internal fun withModelCommand(items: List<SlashItem>, draft: String): List<SlashItem> {
    if (items.any { it.text.equals(MODEL_COMMAND, ignoreCase = true) }) return items
    if (!MODEL_COMMAND.startsWith(draft.trim().lowercase())) return items
    return listOf(
        SlashItem(
            text = MODEL_COMMAND,
            display = MODEL_COMMAND,
            detail = OnyxLocalization["Change the model for this conversation"],
        ),
    ) + items
}

/**
 * Ajoute au menu les commandes que l'app connaît — les six fixes et les **compétences du profil** —
 * quand le gateway ne les propose pas déjà.
 *
 * Le menu d'une connexion Dashboard vient de `complete.slash`, qui liste normalement les
 * compétences installées. Mais un serveur qui ne les sert pas dans cette réponse, ou une réponse
 * arrivée trop tard, laisserait le menu sans elles : la **même liste fournie** que l'exécution
 * ([SlashCommands.commands]) les rend visibles, sans doublon et sans copie codée.
 */
internal fun withLocalCommands(
    items: List<SlashItem>,
    draft: String,
    commands: List<SlashCommand>,
): List<SlashItem> {
    val typed = draft.trim().lowercase().substringBefore(' ')
    if (!typed.startsWith("/")) return items
    val present = items.map { it.text.lowercase() }.toSet()
    val extra = commands
        .filter { it.name.startsWith(typed) && it.name.lowercase() !in present }
        .map { command ->
            SlashItem(text = command.name, display = command.display, detail = command.shownDetail)
        }
    // Le menu garde sa taille : la référence en montre huit.
    return (items + extra).take(8)
}
