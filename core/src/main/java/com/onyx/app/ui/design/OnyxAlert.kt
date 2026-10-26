package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Un bouton d'alerte: son libellé, et s'il est destructeur. */
data class AlertAction(
    val label: String,
    val destructive: Boolean = false,
    val onSelect: () -> Unit,
)

/**
 * L'alerte: un panneau au centre, un titre, un message, puis une ligne par bouton.
 *
 * Compose n'a pas d'alerte qui ressemble à celle — et rien de Material ne doit apparaître —
 * d'où ce panneau dessiné à la main, avec le même ordre de boutons (l'annulation d'abord, comme
 * quand il y a deux choix).
 */
@Composable
fun OnyxAlert(
    title: String,
    message: String? = null,
    actions: List<AlertAction>,
    /**
     * La sortie par le dehors (le toucher à côté, le retour); `null`: il n'y en a pas, comme une
     * `UIAlertController` , à qui seule une de ses actions répond. Un `{}` laisserait un
     * geste qui ne fait rien.
     */
    onDismiss: (() -> Unit)? = null,
    /** Les erreurs et textes venant du serveur passent avec `false`, sans traduction. */
    localizeMessage: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    val dismissable = onDismiss != null

    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(theme.surfaceColor, RoundedCornerShape(14.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        text = title,
                        style = theme.headline.copy(color = theme.textColor, textAlign = TextAlign.Center),
                    )
                    message?.takeIf { it.isNotBlank() }?.let {
                        BasicText(
                            text = it,
                            localize = localizeMessage,
                            style = theme.footnote.copy(
                                color = theme.textColor,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                actions.forEach { action ->
                    OnyxRowDivider(inset = 0.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onyxTappable { action.onSelect() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = action.label,
                            style = theme.body.copy(
                                color = when {
                                    action.destructive -> theme.error
                                    actions.size > 1 && action == actions.last() -> theme.secondaryText
                                    else -> theme.accent
                                },
                                fontWeight = if (action == actions.last() && actions.size > 2) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }
    }
}
