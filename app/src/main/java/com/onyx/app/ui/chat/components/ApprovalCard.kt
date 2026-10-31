package com.onyx.app.ui.chat.components

import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * « L'agent veut exécuter ceci. » Les boutons reprennent exactement les choix du gateway.
 *
 * Port de `ApprovalCard` : la commande en monospace sur son propre bloc, et un bouton bordé
 * par choix — « Once », « This session », « Always », « Deny ».
 *
 * **LOT fil-vivant : c'est une carte de contexte** ([ContextCard]) — même rayon, même filet, même
 * en-tête, état à gauche, action à droite, exactement comme un appel d'outil ou un sous-agent. Sa
 * puce dit son **vrai** état ([approvalCardState]) : `En attente` tant que la décision manque,
 * `Terminé` une fois répondue.
 *
 * **L'aplat orange de la fiche a disparu, et c'est voulu.** C'était une **seconde couleur
 * saturée** employée pour dire un état — précisément ce que la consigne refuse (« pas de couleur
 * saturée par état : un seul accent »). Ce qui dit « ça t'attend » tient désormais à trois choses
 * qui ne coûtent aucune teinte de plus : le **filet d'accent** de la carte vivante, la **lueur
 * tenue** de la puce — le seul état de la grammaire qui s'immobilise en luisant —, et les
 * **choix**, présents à l'écran parce qu'il y a une décision à prendre.
 */
@Composable
fun ApprovalCard(
    request: ApprovalRequest,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Vrai quand la personne remonte lire le fil : la puce **ralentit**. */
    readingUp: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val choices = request.choices.ifEmpty { listOf(ApprovalRequest.CHOICE_ONCE, ApprovalRequest.CHOICE_DENY) }

    ContextCard(
        state = approvalCardState(request),
        title = if (request.isPending) "Approval needed" else "Approval answered",
        modifier = modifier,
        readingUp = readingUp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (request.command.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.tertiarySurfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                        .padding(8.dp),
                ) {
                    SelectableText(
                        text = request.command,
                        localize = false,
                        style = theme.footnote.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                    )
                }
            }

            request.description?.takeIf { it.isNotBlank() }?.let { description ->
                BasicText(text = description, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
            }

            if (request.isPending) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    choices.forEach { choice ->
                        BorderedChoiceButton(
                            label = choiceLabel(choice),
                            tint = if (choice == ApprovalRequest.CHOICE_DENY) theme.error else theme.accent,
                        ) {
                            onChoose(choice)
                        }
                    }
                }
            } else {
                BasicText(
                    text = decisionLabel(request.decision.orEmpty()),
                    style = theme.footnote.copy(color = theme.secondaryText),
                )
            }
        }
    }
}

/** Un bouton bordé, comme `Button(...).buttonStyle(.bordered)`. */
@Composable
private fun BorderedChoiceButton(label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(50))
            .onyxClickable(pressedScale = 0.96f, onClick = onClick)
            // **La cible fait 48 dp**, comme tous les contrôles de la maison : avec 7 dp de marge
            // verticale pour un texte de 13, le choix tombait sous la cible minimale et le système
            // l'élargissait de 5 dp de chaque côté — deux choix voisins se recouvraient alors de
            // 6 dp, exactement le défaut que la maison a déjà payé.
            .padding(horizontal = 14.dp, vertical = OnyxSpace.Half),
    ) {
        BasicText(text = label, style = theme.footnote.copy(color = tint, fontWeight = FontWeight.Medium))
    }
}

/** Les libellés du gateway, mot pour mot comme côté référence. */
private fun choiceLabel(choice: String): String = when (choice) {
    ApprovalRequest.CHOICE_ONCE -> "Once"
    ApprovalRequest.CHOICE_SESSION -> "This session"
    ApprovalRequest.CHOICE_ALWAYS -> "Always"
    ApprovalRequest.CHOICE_DENY -> "Deny"
    else -> choice.replaceFirstChar { it.uppercase() }
}

private fun decisionLabel(decision: String): String = when (decision) {
    ApprovalRequest.CHOICE_ONCE -> "Approved once."
    ApprovalRequest.CHOICE_SESSION -> "Approved for this session."
    ApprovalRequest.CHOICE_ALWAYS -> "Always approved."
    ApprovalRequest.CHOICE_DENY -> "Denied."
    else -> decision
}
