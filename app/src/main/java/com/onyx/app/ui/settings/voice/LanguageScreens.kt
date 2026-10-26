package com.onyx.app.ui.settings.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Voix → Listening language (`SpeechLanguageView`).
 *
 * La transcription embarquée comprend une langue à la fois: choisir la sienne télécharge son
 * modèle sur le téléphone si besoin.
 */
@Composable
fun SpeechLanguageScreen(
    current: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Listening language", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                footer = "On-device transcription understands one language at a time; picking one downloads its speech model if it isn't on the phone yet.",
            ) {
                LanguageRow(
                    title = "The phone's language",
                    detail = java.util.Locale.getDefault().toLanguageTag(),
                    selected = current.isEmpty(),
                    localizeTitle = true,
                    onClick = { onPick("") },
                )
            }

            OnyxFormSection(header = "Languages") {
                SettingsRepository.SPEECH_LOCALES.forEachIndexed { index, (tag, name) ->
                    if (index > 0) OnyxRowDivider()
                    LanguageRow(
                        title = name,
                        detail = tag,
                        selected = tag == current,
                        onClick = { onPick(tag) },
                    )
                }
            }
        }
    }
}

/**
 * Réglages → Voix → Reply language (`ReplyLanguageView`).
 *
 * « Automatic » d'abord, puis les langues, avec la même recherche et le même pied de section.
 */
@Composable
fun ReplyLanguageScreen(
    current: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var query by remember { mutableStateOf("") }

    val needle = query.trim()
    val languages = remember(needle) {
        SettingsRepository.REPLY_LANGUAGES
            .filter { it.first.isNotEmpty() }
            .filter { needle.isEmpty() || it.second.contains(needle, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Reply language", onBack = onBack)
        }

        // « Search languages », sous la barre.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(theme.fill, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnyxIcon(Symbols.MagnifyingGlass, tint = theme.secondaryText, size = 14.dp)
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                if (query.isEmpty()) {
                    BasicText("Search languages", style = theme.body.copy(color = theme.tertiaryText))
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = theme.body.copy(color = theme.primaryText),
                    singleLine = true,
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            if (query.isEmpty()) {
                OnyxFormSection(
                    footer = "Asks the agent to answer in this language, whatever language you write or speak in. On the Hermes Dashboard it's added to each message as a note, “(Reply in …)”, which other apps show; Onyx hides it. For voice mode, pick a matching Listening language too.",
                ) {
                    LanguageRow(
                        title = "Automatic",
                        detail = "The language you write or speak in",
                        selected = current.isEmpty(),
                        localizeTitle = true,
                        localizeDetail = true,
                        onClick = { onPick("") },
                    )
                }
            }

            OnyxFormSection(header = "Languages") {
                languages.forEachIndexed { index, (code, name) ->
                    if (index > 0) OnyxRowDivider()
                    LanguageRow(
                        title = name,
                        detail = code,
                        selected = code == current,
                        onClick = { onPick(code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    title: String,
    detail: String?,
    selected: Boolean,
    localizeTitle: Boolean = false,
    localizeDetail: Boolean = false,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(title, localize = localizeTitle, style = theme.body.copy(color = theme.primaryText))
            detail?.takeIf { it.isNotBlank() }?.let {
                BasicText(it, localize = localizeDetail, style = theme.caption.copy(color = theme.secondaryText))
            }
        }
        if (selected) {
            OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
        }
    }
}
