package com.onyx.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.voice.OrbLight
import com.onyx.app.ui.voice.VoiceOrbChoice

/**
 * Une rangée de réglage avec son explication sous le titre, comme le `Toggle` où le corps
 * est un `VStack` (titre + note en pied). L'interrupteur est celui , jamais un `Switch` Material.
 */
@Composable
fun BlurbToggleRow(
    title: String,
    blurb: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onyxClickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = theme.body.copy(color = theme.primaryText))
            BasicText(
                text = blurb,
                style = theme.footnote.copy(color = theme.secondaryText),
            )
        }
        OnyxSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * « Chat text size »: la rangée du `Picker` en menu , sa valeur à droite, et le choix parmi
 * les cinq pas de `Settings.chatTextSizes`, dans l'ordre.
 */
@Composable
fun ChatTextSizeRow(
    steps: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var picking by remember { mutableStateOf(false) }
    OnyxRow(modifier = modifier, onClick = { picking = true }) {
        BasicText(
            text = "Chat text size",
            style = theme.body.copy(color = theme.primaryText),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = ResolvedTheme.chatTextLabel(steps),
            style = theme.body.copy(color = theme.secondaryText),
            maxLines = 1,
        )
    }
    if (picking) {
        OnyxMenu(
            items = ResolvedTheme.chatTextSizes.map { value ->
                MenuItem(
                    label = ResolvedTheme.chatTextLabel(value),
                    checked = value == steps,
                    onSelect = {
                        picking = false
                        onPick(value)
                    },
                )
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Les réglages de l'orbe vocal, comme `VoiceOrbSettings`: le choix replié
 * sur l'orbe courant — son libellé et son aperçu —, la grille de toutes les faces une fois ouvert,
 * puis « Orb light ».
 */
@Composable
fun VoiceOrbSettingsRows(
    orb: VoiceOrbChoice,
    light: OrbLight,
    onPickOrb: (VoiceOrbChoice) -> Unit,
    onPickLight: (OrbLight) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var pickingLight by remember { mutableStateOf(false) }

    // `DisclosureSummary(title: "Voice orb")`: le libellé de l'orbe et son aperçu à droite.
    com.onyx.app.ui.design.OnyxDisclosureSummary(
        title = "Voice orb",
        expanded = expanded,
        onClick = { expanded = !expanded },
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = orb.label, style = LocalOnyxTheme.current.body.copy(color = LocalOnyxTheme.current.secondaryText))
            VoiceOrbPicture(orb = orb, size = 24.dp)
        }
    }

    if (expanded) {
        VoiceOrbGrid(orb = orb, onPickOrb = onPickOrb)
    }

    OnyxRow(onClick = { pickingLight = true }) {
        BasicText(
            text = "Orb light",
            style = LocalOnyxTheme.current.body.copy(color = LocalOnyxTheme.current.primaryText),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = light.label,
            style = LocalOnyxTheme.current.body.copy(color = LocalOnyxTheme.current.secondaryText),
            maxLines = 1,
        )
    }
    if (pickingLight) {
        OnyxMenu(
            items = OrbLight.entries.map { option ->
                MenuItem(
                    label = option.label,
                    checked = option == light,
                    onSelect = {
                        pickingLight = false
                        onPickLight(option)
                    },
                )
            },
            onDismiss = { pickingLight = false },
        )
    }
}

/** La grille du choix d'orbe: la note, puis chaque face avec son libellé sous elle. */
@Composable
private fun VoiceOrbGrid(orb: VoiceOrbChoice, onPickOrb: (VoiceOrbChoice) -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            text = "The bars move when you speak; the light inside follows “Orb light”. Onyx looks at you instead, and talks with the reply.",
            style = theme.footnote.copy(color = theme.secondaryText),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // `GridItem(.adaptive(minimum: 72), spacing: 12)`.
            val columns = ((maxWidth + 12.dp) / 84.dp).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VoiceOrbChoice.entries.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        row.forEach { choice ->
                            VoiceOrbCell(
                                choice = choice,
                                selected = choice == orb,
                                onClick = { onPickOrb(choice) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Une case de la grille: l'aperçu entouré quand il est retenu, puis son libellé. */
@Composable
private fun VoiceOrbCell(
    choice: VoiceOrbChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .onyxClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
            },
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .padding(4.dp)
                .border(
                    width = if (selected) 2.5.dp else 0.dp,
                    color = if (selected) theme.accent else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(if (choice.isRound) 40.dp else 16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            VoiceOrbPicture(orb = choice, size = 58.dp)
        }
        BasicText(
            text = choice.label,
            style = theme.caption.copy(
                color = if (selected) theme.primaryText else theme.secondaryText,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

/**
 * L'aperçu d'une face d'orbe, comme `VoiceOrbPicture`: Onyx est son visage dessiné, les autres
 * sont les images `…Blank` du catalogue.
 */
@Composable
private fun VoiceOrbPicture(orb: VoiceOrbChoice, size: androidx.compose.ui.unit.Dp) {
    val face = orb.face
    if (face == null) {
        BotLook(
            name = "default",
            faceCode = BotFace.main.code(),
            size = size,
            showsInitial = false,
        )
    } else {
        Image(
            painter = painterResource(face),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    }
}

/** Le libellé localisé d'un libellé d'orbe, pour les tests et l'accessibilité. */
internal fun orbLabel(orb: VoiceOrbChoice): String = OnyxLocalization[orb.label]
