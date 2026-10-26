package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.BotAvatar
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMark
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Le choix du visage d'un bot (`BotFacePicker`).
 *
 * Sa forme, ses yeux, sa bouche, sa marque et sa couleur — chaque choix dessiné comme le bot avec
 * cette partie changée — puis « Surprise Me » et « Its Own Face ». La bulle et les deux couleurs de
 * Onyx ne sont proposées qu'au profil principal, comme.
 */
@Composable
fun BotFacePickerScreen(
    botName: String,
    displayName: String,
    current: String?,
    onSave: (String?) -> Unit,
    onCancel: () -> Unit,
    isDefault: Boolean = botName == "default",
    hasPickedFace: Boolean = current != null,
    serverBacked: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val start = remember(current) { BotFace.parse(current) ?: BotFace.forName(botName) }
    var face by remember(current) { mutableStateOf(start) }
    val changed = face != start
    val shapes = FaceShape.offered(isDefault)
    val colours = BotFace.offeredColors(isDefault)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = displayName,
                onBack = onCancel,
                action = "Save",
                onAction = { onSave(face.code()) },
                actionEnabled = changed,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BotAvatar(name = botName, size = 132.dp, faceCode = face.code(), showsInitial = false)
            }

            OnyxFormSection(header = "Shape") {
                PartRow(
                    options = shapes.map { it.id },
                    selected = face.shape.id,
                    preview = { index -> face.copy(shape = shapes[index]) },
                    name = botName,
                ) { index -> face = face.copy(shape = shapes[index]) }
            }

            OnyxFormSection(header = "Eyes") {
                PartRow(
                    options = FaceEyes.ordered.map { it.id },
                    selected = face.eyes.id,
                    preview = { index -> face.copy(eyes = FaceEyes.ordered[index]) },
                    name = botName,
                ) { index -> face = face.copy(eyes = FaceEyes.ordered[index]) }
            }

            OnyxFormSection(header = "Mouth") {
                PartRow(
                    options = FaceMouth.ordered.map { it.id },
                    selected = face.mouth.id,
                    preview = { index -> face.copy(mouth = FaceMouth.ordered[index]) },
                    name = botName,
                ) { index -> face = face.copy(mouth = FaceMouth.ordered[index]) }
            }

            OnyxFormSection(header = "Mark") {
                PartRow(
                    options = FaceMark.ordered.map { it.id },
                    selected = face.mark.id,
                    preview = { index -> face.copy(mark = FaceMark.ordered[index]) },
                    name = botName,
                ) { index -> face = face.copy(mark = FaceMark.ordered[index]) }
            }

            OnyxFormSection(header = "Colour") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    colours.forEach { index ->
                        val selected = index == face.colour
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .widthIn(min = 52.dp)
                                .onyxTappable { face = face.copy(colour = index) }
                                .padding(vertical = 4.dp),
                        ) {
                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                val (light, deep) = BotFace.gradient(index, theme)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Brush.linearGradient(listOf(light, deep)), CircleShape)
                                        .then(
                                            if (index >= BotFace.seededColors) {
                                                Modifier.border(1.dp, theme.tertiaryText.copy(alpha = 0.35f), CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                )
                                if (selected) {
                                    Box(modifier = Modifier.size(44.dp).border(2.5.dp, theme.accent, CircleShape))
                                }
                            }
                            BasicText(
                                text = BotFace.colourNames[index],
                                style = theme.caption2.copy(
                                    color = if (selected) theme.textColor else theme.secondaryText,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            OnyxFormSection(
                footer = if (serverBacked) {
                    "Kept on your Hermes server, so Onyx on your other devices shows it too."
                } else {
                    "Kept on this phone; this connection has no Hermes server login."
                },
            ) {
                OnyxRow(onClick = { face = BotFace.random(keeping = face) }) {
                    BasicText("Surprise Me", style = theme.body.copy(color = theme.accent))
                }
                if (hasPickedFace) {
                    OnyxRowDivider()
                    OnyxRow(onClick = { onSave(null) }) {
                        BasicText("Its Own Face", style = theme.body.copy(color = theme.accent))
                    }
                }
            }
        }
    }
}

/** La rangée d'une partie: chaque option est le visage entier avec cette partie changée. */
@Composable
private fun PartRow(
    options: List<String>,
    selected: String,
    preview: (Int) -> BotFace,
    name: String,
    onSelect: (Int) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val picked = option == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .background(
                        if (picked) theme.accent.copy(alpha = 0.14f) else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .border(
                        2.dp,
                        if (picked) theme.accent else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .onyxClickable { onSelect(index) }
                    .padding(5.dp),
            ) {
                BotAvatar(name = name, size = 50.dp, faceCode = preview(index).code(), showsInitial = false)
                BasicText(
                    text = option.replaceFirstChar { it.uppercase() },
                    style = theme.caption2.copy(
                        color = if (picked) theme.textColor else theme.secondaryText,
                    ),
                )
            }
        }
    }
}
