package com.onyx.app.ui.chat.components

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
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * « L'agent veut exécuter ceci. » Les boutons reprennent exactement les choix du gateway.
 *
 * Comme: fond orange pâle, la commande en monospace sur son propre bloc,
 * et un bouton bordé par choix — « Once », « This session », « Always », « Deny ».
 */
@Composable
fun ApprovalCard(
    request: ApprovalRequest,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val choices = request.choices.ifEmpty { listOf(ApprovalRequest.CHOICE_ONCE, ApprovalRequest.CHOICE_DENY) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.warning.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OnyxIcon(Symbols.Lock, tint = theme.textColor, size = 15.dp)
            BasicText(
                text = if (request.isPending) "Approval needed" else "Approval answered",
                style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            )
        }

        if (request.command.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.tertiarySurfaceColor, RoundedCornerShape(8.dp))
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

/** Un bouton bordé, comme `Button(...).buttonStyle(.bordered)`. */
@Composable
private fun BorderedChoiceButton(label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(50))
            .onyxClickable(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        BasicText(text = label, style = theme.footnote.copy(color = tint, fontWeight = FontWeight.Medium))
    }
}

/** Les libellés du gateway, mot pour mot comme. */
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
