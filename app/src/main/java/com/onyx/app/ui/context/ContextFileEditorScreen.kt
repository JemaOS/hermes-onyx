package com.onyx.app.ui.context

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Comme: un fichier distant, édité et sauvegardé sur place. */
@Composable
fun ContextFileEditorScreen(
    viewModel: ContextFileEditorViewModel,
    file: ContextFile,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }

    fun close() {
        if (state.dirty) confirmDiscard = true else onBack()
    }

    // Le retour du bord d'écran passe par la même protection que le bouton visible.
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Box(modifier = modifier.fillMaxSize().background(theme.pageColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.statusBarsPadding()) {
                OnyxPageHeader(
                    title = file.label,
                    onBack = ::close,
                    action = if (state.saving) "Saving…" else "Save",
                    onAction = viewModel::save,
                    actionEnabled = state.dirty && !state.saving,
                    localizeTitle = false,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    state.path?.let {
                        BasicText(
                            text = it,
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 7.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 380.dp)
                            .padding(horizontal = 16.dp)
                            .background(theme.surfaceColor, RoundedCornerShape(12.dp)),
                    ) {
                        BasicTextField(
                            value = state.content,
                            onValueChange = viewModel::onTextChange,
                            textStyle = theme.footnote.copy(
                                color = theme.textColor,
                                fontFamily = FontFamily.Monospace,
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                        )
                    }

                    val missingNote = if (state.homeKnown || file == ContextFile.SOUL) {
                        "This file doesn't exist yet. Saving creates it."
                    } else {
                        "Nothing is at this path, and the Hermes Dashboard didn't say where this profile keeps its files. Saving creates the file here."
                    }
                    val footer = if (state.missing) {
                        OnyxLocalization[missingNote] + " " + OnyxLocalization[file.purpose]
                    } else {
                        OnyxLocalization[file.purpose]
                    }
                    BasicText(
                        text = footer,
                        localize = false,
                        style = theme.footnote.copy(color = theme.secondaryText),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp, bottom = 12.dp),
                    )

                    state.error?.let {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OnyxIcon(Symbols.ExclamationTriangle, tint = theme.error, size = 15.dp)
                            BasicText(
                                text = it,
                                localize = false,
                                style = theme.footnote.copy(color = theme.error),
                                modifier = Modifier.padding(start = 7.dp),
                            )
                        }
                    }
                }

                if (state.loading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(theme.pageColor.copy(alpha = 0.82f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = OnyxLocalization.format("Loading %1\$s…", file.label),
                            localize = false,
                            style = theme.body.copy(color = theme.secondaryText),
                        )
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        OnyxAlert(
            title = "Discard changes?",
            message = "Your edits haven't been saved.",
            actions = listOf(
                AlertAction("Keep Editing") { confirmDiscard = false },
                AlertAction("Discard", destructive = true) {
                    confirmDiscard = false
                    onBack()
                },
            ),
            onDismiss = { confirmDiscard = false },
        )
    }
}
