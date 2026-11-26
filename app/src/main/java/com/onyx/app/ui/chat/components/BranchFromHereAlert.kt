package com.onyx.app.ui.chat.components

import androidx.compose.runtime.Composable
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.BranchPrompt
import com.onyx.app.ui.chat.branchPromptLines
import com.onyx.app.ui.design.OnyxConfirm

/**
 * La confirmation de « Brancher à partir d'ici » — **la surface de la maison, comme les autres**.
 *
 * Ce qu'elle était : une **troisième** surface, dessinée à la main dans ce fichier — son propre
 * panneau de verre, son rayon à elle, un titre **centré**, et deux actions **alignées à droite**
 * dont « Annuler » en texte nu. Elle ne suivait donc ni la confirmation de la restauration just
 * à côté d'elle, ni la confirmation de la maison ([OnyxConfirm]) : deux panneaux de confirmation
 * dans le même fil, deux formes, deux marges.
 *
 * Ce qu'elle est : un **adaptateur**. Elle garde ses quatre lignes ([branchPromptLines]) et son
 * geste, et les rend par [OnyxConfirm] — le verre des panneaux, le rayon 28 de la famille, l'arête
 * claire, le grand titre du socle **aligné à gauche**, le corps du socle, et deux pastilles pleine
 * largeur à **16 dp du bord**, séparées de **16 dp**. Une correction de marge vaut donc, désormais,
 * pour toutes les confirmations de l'app.
 *
 * Le geste n'est **pas** destructeur : il copie, il n'efface rien. L'action principale prend donc
 * la **pilule d'accent** (`destructive = false`), pas l'encre de danger.
 *
 * Le refus ne laisse aucune trace : c'est la sortie du panneau (`onCancel`).
 */
@Composable
internal fun BranchConfirmAlert(
    prompt: BranchPrompt,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Les quatre lignes de la confirmation, dans l'ordre de la référence, forment le **corps** du
    // panneau — elles vivent en un seul bloc, comme le corps de n'importe quelle confirmation.
    val body = branchPromptLines(prompt).joinToString(separator = "\n")
    OnyxConfirm(
        title = OnyxLocalization["Branch from here?"],
        message = body,
        destructiveLabel = OnyxLocalization["Branch this conversation"],
        cancelLabel = OnyxLocalization["Cancel"],
        onDestructive = onConfirm,
        onCancel = onCancel,
        destructive = false,
        // Les lignes sortent déjà du dictionnaire : les retraduire serait les traduire deux fois.
        localizeMessage = false,
    )
}
