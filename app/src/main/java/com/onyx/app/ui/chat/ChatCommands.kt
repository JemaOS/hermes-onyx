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

/** La taille du menu : la référence en montre huit. */
internal const val SLASH_MENU_LINES = 8

/**
 * Ajoute au menu les commandes que l'app connaît — les six fixes et les **compétences du profil** —
 * et les fait **passer devant** ce que le gateway propose.
 *
 * Défaut relevé sur l'appareil : « les compétences sont inutilisables ». `complete.slash` d'un
 * serveur qui remplit les huit lignes du menu (ses propres commandes) ne laissait aucune place aux
 * compétences, ajoutées *après* puis coupées par `take(8)` : elles existaient sans jamais
 * s'afficher. Rien n'était cassé dans la liste fournie — c'est la place qui manquait.
 *
 * L'ordre est donc : les commandes de l'app qui répondent au préfixe (les six fixes, puis les
 * compétences), puis ce que le serveur propose, dédoublonné, le tout coupé à [SLASH_MENU_LINES].
 * Un préfixe plus précis (`/you`, `/re`) fait la part belle au serveur dès que les commandes de
 * l'app ne répondent plus.
 */
internal fun withLocalCommands(
    items: List<SlashItem>,
    draft: String,
    commands: List<SlashCommand>,
): List<SlashItem> {
    val typed = draft.trim().lowercase().substringBefore(' ')
    if (!typed.startsWith("/")) return items
    val present = items.map { it.text.lowercase() }.toSet()
    val local = commands
        .filter { it.name.startsWith(typed) && it.name.lowercase() !in present }
        .map { command ->
            SlashItem(text = command.name, display = command.display, detail = command.shownDetail)
        }
    if (local.isEmpty()) return items
    return (local + items).take(SLASH_MENU_LINES)
}
