package com.onyx.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onyx.app.car.AutoArtwork
import com.onyx.app.car.AutoCardLook
import com.onyx.app.car.AutoStart
import com.onyx.app.car.AutoVoiceLook
import com.onyx.app.car.AutoVoiceState
import com.onyx.app.ui.design.OnyxDisclosureSummary
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Le disclosure CarPlayCardSettings, limité aux deux cartes qui ont une action Android Auto réelle. */
@Composable
fun AutoCardSettings(
    current: AutoCardLook,
    onPick: (AutoCardLook) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        OnyxDisclosureSummary(
            title = "Android Auto cards",
            expanded = expanded,
            onClick = { expanded = !expanded },
            value = {
                BasicText(
                    text = current.label,
                    style = LocalOnyxTheme.current.body.copy(color = LocalOnyxTheme.current.secondaryText),
                )
                AutoCardImage(AutoStart.ASK, current, Modifier.height(20.dp))
            },
        )
        if (expanded) {
            DisclosureText("How the Ask and New Chat cards look on your car's screen. Android Auto adds their names and controls voice input.")
            AutoCardLook.entries.forEachIndexed { index, look ->
                if (index > 0) OnyxRowDivider()
                OnyxRow(
                    modifier = Modifier.semantics { selected = current == look },
                    onClick = { onPick(look) },
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LookLabel(look.label, look.blurb, current == look)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AutoStart.entries.forEach { start ->
                                AutoCardImage(start, look, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Le disclosure CarPlayVoiceSettings: deux états statiques, sans micro ni animation inventés. */
@Composable
fun AutoVoiceSettings(
    current: AutoVoiceLook,
    onPick: (AutoVoiceLook) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        OnyxDisclosureSummary(
            title = "Android Auto voice screen",
            expanded = expanded,
            onClick = { expanded = !expanded },
            value = {
                BasicText(
                    text = current.label,
                    style = LocalOnyxTheme.current.body.copy(color = LocalOnyxTheme.current.secondaryText),
                )
                AutoVoiceImage(AutoVoiceState.SPEAKING, current, Modifier.size(22.dp))
            },
        )
        if (expanded) {
            DisclosureText("What your car shows while Onyx thinks and speaks, in that order. Android Auto owns voice input and displays these pictures without animation.")
            AutoVoiceLook.entries.forEachIndexed { index, look ->
                if (index > 0) OnyxRowDivider()
                OnyxRow(
                    modifier = Modifier.semantics { selected = current == look },
                    onClick = { onPick(look) },
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LookLabel(look.label, look.blurb, current == look)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AutoVoiceState.entries.forEach { state ->
                                AutoVoiceImage(state, look, Modifier.size(60.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosureText(text: String) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BasicText(text = text, style = theme.footnote.copy(color = theme.secondaryText))
    }
}

@Composable
private fun LookLabel(label: String, blurb: String, selected: Boolean) {
    val theme = LocalOnyxTheme.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(text = label, style = theme.body.copy(color = theme.primaryText))
            BasicText(text = blurb, style = theme.caption.copy(color = theme.secondaryText))
        }
        if (selected) OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
    }
}

@Composable
private fun AutoCardImage(start: AutoStart, look: AutoCardLook, modifier: Modifier = Modifier) {
    val bitmap = remember(start, look) { AutoArtwork.startCard(start, look) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .aspectRatio(AutoArtwork.CARD_WIDTH.toFloat() / AutoArtwork.CARD_HEIGHT)
            .clip(RoundedCornerShape(5.dp)),
    )
}

@Composable
private fun AutoVoiceImage(state: AutoVoiceState, look: AutoVoiceLook, modifier: Modifier = Modifier) {
    val bitmap = remember(state, look) { AutoArtwork.voiceState(state, look) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
