package com.onyx.app.ui.capture

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.capture.CaptureNote
import com.onyx.app.data.capture.CaptureOutcome
import com.onyx.app.data.capture.QuickCapture
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * « Capture rapide » : une note prise en passant, que l'agent range.
 *
 * C'est l'écran de la capture de la référence : on écrit une note (deux gestes depuis n'importe
 * où — le raccourci de l'illustration, ou le menu de la liste), la note est **gardée tout de
 * suite**, et classée par Hermes dans sa mémoire, une liste, un rappel ou le calendrier. **Rien ne
 * se dit en retour** : la réponse de l'agent n'est pas lue à voix haute et ne s'invite pas dans la
 * conversation ouverte.
 *
 * Les notes pas encore classées restent là, visibles, avec leur heure.
 */
@Composable
fun QuickCaptureScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    waiting: List<CaptureNote> = QuickCapture.waiting.collectAsStateWithLifecycle().value,
    outcome: CaptureOutcome? = QuickCapture.outcome.collectAsStateWithLifecycle().value,
    filing: Boolean = QuickCapture.filing.collectAsStateWithLifecycle().value,
    onTake: (String) -> Boolean = { QuickCapture.add(it) },
    onRemove: (String) -> Unit = { QuickCapture.remove(it) },
    whenLabel: (Long) -> String = { QuickCapture.whenLabel(it) },
) {
    val theme = LocalOnyxTheme.current
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme)
            .imePadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Take a note", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            OnyxFormSection(
                header = "The note",
                footer = "It is kept the moment you take it and filed as soon as the server answers — " +
                    "your memory, a list, a reminder, the calendar, whatever fits. Nothing comes back " +
                    "to you: Onyx files it and stays quiet.",
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OnyxTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = "The plumber comes Thursday at ten",
                        singleLine = false,
                        maxLines = 4,
                    )
                }
                OnyxRowDivider(inset = 0.dp)
                // Le bouton de la note : un appui, et elle est prise.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onyxClickable(enabled = draft.isNotBlank() && !filing) {
                            if (onTake(draft)) draft = ""
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OnyxIcon(
                        symbol = Symbols.SquareAndPencil,
                        tint = if (draft.isNotBlank() && !filing) theme.accent else theme.tertiaryText,
                        size = 16.dp,
                    )
                    BasicText(
                        text = "Take the note",
                        style = theme.body.copy(
                            color = if (draft.isNotBlank() && !filing) theme.accent else theme.tertiaryText,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }

            // L'issue du dernier appui : jamais muet, comme le veut le contrat de l'app.
            val line = when {
                filing -> OnyxLocalization["Filing…"]
                outcome == CaptureOutcome.FILED -> OnyxLocalization["Filed."]
                outcome == CaptureOutcome.WAITING -> OnyxLocalization["Waiting for the server. It will go on its own."]
                else -> null
            }
            line?.let { text ->
                BasicText(
                    text = text,
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                )
            }

            OnyxFormSection(
                header = "Waiting",
                footer = if (waiting.isEmpty()) null
                else OnyxLocalization["They stay here until the server that was in use answers."],
            ) {
                if (waiting.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OnyxEmptyState(
                            symbol = Symbols.SquareAndPencil,
                            title = "Nothing waiting.",
                        )
                    }
                } else {
                    waiting.forEachIndexed { index, note ->
                        if (index > 0) OnyxRowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = note.text,
                                    localize = false,
                                    style = theme.body.copy(color = theme.primaryText),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                BasicText(
                                    text = whenLabel(note.at),
                                    localize = false,
                                    style = theme.caption.copy(color = theme.secondaryText),
                                )
                            }
                            OnyxIcon(
                                symbol = Symbols.Trash,
                                tint = theme.secondaryText,
                                size = 16.dp,
                                modifier = Modifier.onyxClickable { onRemove(note.id) },
                                contentDescription = "Delete",
                            )
                        }
                    }
                }
            }
        }
    }
}
