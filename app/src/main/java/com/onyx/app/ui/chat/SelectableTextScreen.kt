package com.onyx.app.ui.chat

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.navigationBarClearance
import com.onyx.app.ui.design.statusBarClearance
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Une copie sélectionnable d'une réponse (`SelectableTextSheet`).
 *
 * Pour attraper une phrase plutôt que le tout ; les entrées et sorties d'outils s'affichent en
 * monospace, parce que c'est de la donnée et non de la prose. Un compteur dit combien de caractères
 * sont pris, et « Copy all » emporte le texte entier.
 *
 * @param toolLabel le nom de la donnée d'outil — « Input » ou « Output » — posé en tête ; quand il
 * est donné, le texte passe en monospace.
 */
@Composable
fun SelectableTextScreen(
    text: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Select text",
    monospaced: Boolean = false,
    toolLabel: String? = null,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val mono = monospaced || toolLabel != null


    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarClearance()) {
            OnyxPageHeader(title = title, onBack = onBack)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Column {
                // Une donnée d'outil se nomme en tête : « Input » ou « Output ».
                if (toolLabel != null) {
                    BasicText(
                        text = OnyxLocalization[toolLabel].uppercase(),
                        localize = false,
                        style = theme.caption.copy(
                            color = theme.tertiaryText,
                            fontFamily = familyFor(true),
                        ),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                SelectionContainer {
                    BasicText(
                        text = text,
                        localize = false,
                        style = if (mono) {
                            theme.codeFont.copy(color = theme.textColor)
                        } else {
                            theme.messageFont.copy(color = theme.textColor)
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surfaceColor)
                // La barre du bas peint son fond jusqu'au bord, et remonte ses commandes
                // au-dessus de la zone de navigation.
                .navigationBarClearance()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnyxIcon(Symbols.QuoteBubble, tint = theme.secondaryText, size = 14.dp)
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "Select some of it and copy it, or quote it in your next message.",
                    style = theme.footnote.copy(color = theme.secondaryText),
                )
            }
            // Le bouton « Copy all » : tout le texte, quelle que soit la sélection.
            Row(
                modifier = Modifier
                    .background(theme.surfaceColor.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .onyxClickable(pressedScale = 0.9f) {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(android.content.ClipData.newPlainText("Onyx", text)),
                            )
                        }
                        copied = true
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OnyxIcon(
                    symbol = if (copied) Symbols.Checkmark else Symbols.DocOnDoc,
                    tint = if (copied) theme.success else theme.secondaryText,
                    size = 12.dp,
                )
                BasicText(
                    text = if (copied) "Copied" else "Copy all",
                    style = theme.caption.copy(color = if (copied) theme.success else theme.secondaryText),
                )
            }
        }
    }
}


/**
 * Un morceau de texte antérieur, mis en citation Markdown avec la place de la question après
 * (`Quote.markdown`).
 */
fun quoteMarkdown(text: String): String {
    val lines = text.trim().split("\n").map { it.trim() }
    if (lines.all { it.isEmpty() }) return ""
    return lines.joinToString("\n") { if (it.isEmpty()) ">" else "> $it" } + "\n\n"
}

private fun familyFor(monospaced: Boolean) = if (monospaced) FontFamily.Monospace else FontFamily.Default
