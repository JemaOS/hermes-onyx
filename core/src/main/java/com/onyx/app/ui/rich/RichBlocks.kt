package com.onyx.app.ui.rich

import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.ui.design.OnyxActionSheet
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Un fence ```mermaid : le dessin hors ligne, sa source, la copie et le lecteur plein écran.
 * Port de `MermaidBlock` et `DiagramViewer` dans `WebBlocks`.
 */
@Composable
fun MermaidBlock(
    source: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showSource by remember(source) { mutableStateOf(false) }
    var expanded by remember(source) { mutableStateOf(false) }
    var copied by remember(source) { mutableStateOf(false) }
    val heightKey = remember(source) { WebBlockHeights.key("mermaid", source) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
            .semantics { contentDescription = "Mermaid diagram" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "DIAGRAM",
                style = theme.caption.copy(color = theme.tertiaryText, fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            OnyxIcon(
                symbol = Symbols.Expand,
                tint = theme.secondaryText,
                size = 13.dp,
                contentDescription = "Open diagram full screen",
                modifier = Modifier
                    .onyxClickable(pressedScale = 0.9f) { expanded = true }
                    .padding(8.dp),
            )
            BasicText(
                text = if (showSource) "Diagram" else "Source",
                style = theme.caption.copy(color = theme.secondaryText),
                modifier = Modifier
                    .onyxClickable(pressedScale = 0.95f) { showSource = !showSource }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            RichCopyButton(
                copied = copied,
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Mermaid source", source)))
                    }
                    copied = true
                },
            )
        }

        if (showSource) {
            BasicText(
                text = source,
                style = theme.codeFont.copy(color = theme.textColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        } else {
            RichWebView(
                kind = RichKind.MERMAID,
                source = source,
                heightKey = heightKey,
                onTap = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .padding(bottom = 6.dp),
            )
        }
    }

    if (expanded) {
        DiagramViewer(source = source, onClose = { expanded = false })
    }
}

/** Le lecteur d'un diagramme : pincement jusqu'à 6×, déplacement et copie de la source. */
@Composable
private fun DiagramViewer(
    source: String,
    onClose: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onyxPage(theme),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "Copy source",
                    style = theme.subheadline.copy(color = theme.accent),
                    modifier = Modifier
                        .onyxClickable(pressedScale = 0.95f) {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Mermaid source", source)))
                            }
                        }
                        .padding(8.dp),
                )
                BasicText(
                    text = "Diagram",
                    style = theme.subheadline.copy(color = theme.textColor, textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    text = "Done",
                    style = theme.subheadline.copy(color = theme.accent, textAlign = TextAlign.End),
                    modifier = Modifier
                        .onyxClickable(pressedScale = 0.95f, onClick = onClose)
                        .padding(8.dp),
                )
            }
            RichWebView(
                kind = RichKind.MERMAID,
                source = source,
                zoomable = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
            )
        }
    }
}

/** Une formule KaTeX ; l'appui long ouvre « Copy LaTeX », comme le menu contextuel référence. */
@Composable
fun MathBlock(
    source: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showActions by remember(source) { mutableStateOf(false) }
    val heightKey = remember(source) { WebBlockHeights.key("math", source) }

    RichWebView(
        kind = RichKind.MATH,
        source = source,
        heightKey = heightKey,
        onLongPress = { showActions = true },
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Math: $source" },
    )

    if (showActions) {
        OnyxActionSheet(
            title = "Math",
            items = listOf(
                MenuItem(
                    label = "Copy LaTeX",
                    symbol = Symbols.DocOnDoc,
                    onSelect = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("LaTeX", source)))
                        }
                        showActions = false
                    },
                ),
            ),
            onDismiss = { showActions = false },
        )
    }
}

@Composable
private fun RichCopyButton(
    copied: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .background(theme.surfaceColor.copy(alpha = 0.6f), RoundedCornerShape(50))
            .onyxClickable(pressedScale = 0.9f, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnyxIcon(
            symbol = if (copied) Symbols.Checkmark else Symbols.DocOnDoc,
            tint = if (copied) theme.success else theme.secondaryText,
            size = 12.dp,
        )
        BasicText(
            text = if (copied) "Copied" else "Copy",
            style = theme.caption.copy(color = if (copied) theme.success else theme.secondaryText),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
