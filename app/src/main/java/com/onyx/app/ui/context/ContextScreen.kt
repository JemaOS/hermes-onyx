package com.onyx.app.ui.context

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Contexte et mémoire.
 *
 * Comme la section « Context files » / « Memory » d'`AgentSettings`: les quatre fichiers, leur
 * phrase, et le chemin où ils vivent sur l'hôte.
 */
@Composable
fun ContextScreen(
    viewModel: ContextViewModel,
    mode: Mode,
    onBack: () -> Unit,
    onOpenFile: (ContextFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val files = when (mode) {
        Mode.CONTEXT -> listOf(ContextFile.SOUL, ContextFile.ENVIRONMENT)
        Mode.MEMORY -> listOf(ContextFile.MEMORY, ContextFile.USER)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = if (mode == Mode.CONTEXT) "Context files" else "Memory",
                onBack = onBack,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            when {
                state.loading -> OnyxFormSection {
                    OnyxRow {
                        BasicText("Loading from Onyx…", style = theme.body.copy(color = theme.secondaryText))
                    }
                }

                else -> OnyxFormSection(
                    footer = state.problem ?: "Edited in place on the Hermes host through the Hermes Dashboard.",
                    localizeFooter = state.problem == null,
                ) {
                    files.forEachIndexed { index, file ->
                        if (index > 0) OnyxRowDivider()
                        val path = state.pathFor(file)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onyxTappable { onOpenFile(file) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            OnyxIcon(
                                symbol = if (mode == Mode.CONTEXT) Symbols.PersonCircle else Symbols.Brain,
                                tint = theme.accent,
                                size = 18.dp,
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                BasicText(
                                    text = file.label,
                                    style = theme.body.copy(color = theme.textColor),
                                )
                                BasicText(
                                    text = file.purpose,
                                    style = theme.footnote.copy(color = theme.secondaryText),
                                )
                                path?.let {
                                    BasicText(
                                        text = it,
                                        localize = false,
                                        style = theme.caption.copy(
                                            color = theme.tertiaryText,
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Les deux pages que l'écran sert: le contexte de l'agent, ou sa mémoire. */
enum class Mode { CONTEXT, MEMORY }
