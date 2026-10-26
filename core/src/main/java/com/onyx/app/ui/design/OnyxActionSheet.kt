package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * La feuille d'actions: un panneau arrondi collé au bas de l'écran.
 *
 * C'est ce que l'app montre pour un appui long — « Mute / 1 hour / Today / This week / Unmute » —
 * là où un menu flottant ne convient pas.
 */
@Composable
fun OnyxActionSheet(
    title: String? = null,
    message: String? = null,
    items: List<MenuItem>,
    cancelLabel: String = "Cancel",
    onDismiss: () -> Unit,
) {
    val theme = LocalOnyxTheme.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surfaceColor, RoundedCornerShape(14.dp)),
                ) {
                    // L'en-tête: le titre en petit, le message en gris, .
                    if (title != null || message != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            title?.let {
                                BasicText(
                                    text = it,
                                    style = theme.footnote.copy(
                                        color = theme.secondaryText,
                                        textAlign = TextAlign.Center,
                                    ),
                                )
                            }
                            message?.let {
                                BasicText(
                                    text = it,
                                    style = theme.caption.copy(
                                        color = theme.tertiaryText,
                                        textAlign = TextAlign.Center,
                                    ),
                                )
                            }
                        }
                        OnyxRowDivider(inset = 0.dp)
                    }

                    items.forEachIndexed { index, item ->
                        if (index > 0) OnyxRowDivider(inset = 0.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onyxTappable { item.onSelect() }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            item.symbol?.let {
                                OnyxIcon(
                                    symbol = it,
                                    tint = if (item.destructive) theme.error else theme.textColor,
                                    size = 16.dp,
                                )
                            }
                            BasicText(
                                text = item.label,
                                style = theme.body.copy(
                                    color = if (item.destructive) theme.error else theme.textColor,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.padding(start = if (item.symbol != null) 8.dp else 0.dp),
                            )
                        }
                    }
                }

                // « Cancel », dans son propre panneau, .
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surfaceColor, RoundedCornerShape(14.dp))
                        .onyxTappable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = cancelLabel,
                        style = theme.body.copy(color = theme.accent, textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}
