package com.onyx.app.ui.voice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.localization.BasicText

/**
 * Ce qu'on est en train de dire, **mot à mot** (`RisingCaption`, ).
 *
 * Le ne peut pas faire ça avec un `Text`: chaque mot est une sous-vue à part, qui arrive
 * sur un `Transition` — d'un cran plus bas (`offset(y: 8)`) et de rien — pendant que les autres
 * ne bougent pas. C'est ce qui donne à la transcription vivante son air parlé: le dernier mot
 * monte, le reste tient. Le flux qui porte ces mots est centré, rempli ligne à ligne comme du
 * texte centré (`CenteredFlow`, `:1195-1234`).
 *
 * Implémentation: chaque mot porte son propre état d'apparition (`key` sur l'index, comme le `ForEach`
 * ), et le temps de cette apparition est le même `easeOut(0,25)` posé sur le nombre de
 * mots. Sous **Reduce Motion**, garde le fondu et perd l'offset
 * (`reduceMotion ? .opacity: .offset(y: 8).combined(with: .opacity)`): ici [lift] tombe à zéro.
 *
 * Ce qui reste approximatif: quand un mot de plus oblige la légende à se replier, anime
 * le déplacement des mots déjà posés (`.animation(…, value: words.count)`), Compose les replace
 * d'un coup — la même limite que les `LazyColumn` de cette app.
 */
@Composable
fun RisingCaption(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    spacing: Dp = CaptionWordSpacing,
    lineSpacing: Dp = CaptionLineSpacing,
) {
    val words = remember(text) { captionWords(text) }
    Layout(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        content = {
            words.forEachIndexed { index, word ->
                key(index) { CaptionWord(word = word, style = style) }
            }
        },
    ) { measurables, constraints ->
        val available = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            UnboundedCaptionWidth.roundToPx()
        }
        // `.sizeThatFits(.unspecified)`: chaque mot se mesure sans contrainte.
        val places = measurables.map { it.measure(Constraints()) }
        val gap = spacing.roundToPx()
        val lineGap = lineSpacing.roundToPx()
        val rows = captionRows(places.map { it.width }, available, gap)
        val rowHeights = rows.map { row -> row.maxOf { places[it].height } }
        val height = rowHeights.sum() + lineGap * (rows.size - 1).coerceAtLeast(0)
        layout(available, height) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                val rowWidth = row.sumOf { places[it].width } + gap * (row.size - 1).coerceAtLeast(0)
                var x = (available - rowWidth) / 2
                for (index in row) {
                    val placeable = places[index]
                    placeable.place(x, y + (rowHeights[rowIndex] - placeable.height) / 2)
                    x += placeable.width + gap
                }
                y += rowHeights[rowIndex] + lineGap
            }
        }
    }
}

/** Un mot de la légende: il monte de 8 pt en apparaissant, en fondu de 0,25 s. */
@Composable
private fun CaptionWord(word: String, style: TextStyle) {
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = OnyxMotion.Sites.CaptionWord,
        label = "captionWord",
    )
    // `reduceMotion ? .opacity: .offset(y: 8).combined(with: .opacity)`.
    val lift = if (reduceMotion) 0f else with(density) { (1f - progress) * CaptionWordRise.toPx() }
    BasicText(
        text = word,
        localize = false,
        style = style,
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = lift
        },
    )
}

/**
 * Les mots de la légende: `Text(split(separator: " "))` , c'est-à-dire les espaces seuls
 * et les vides écartés.
 */
internal fun captionWords(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

/**
 * Les lignes du flux centré: on remplit tant que le mot tient, sinon on ouvre une ligne
 * (`CenteredFlow.rows`, ).
 */
internal fun captionRows(widths: List<Int>, available: Int, spacing: Int): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    rows.add(mutableListOf())
    var x = 0
    widths.forEachIndexed { index, width ->
        if (x > 0 && x + width > available) {
            rows.add(mutableListOf())
            x = 0
        }
        rows.last().add(index)
        x += width + spacing
    }
    return rows
}

/** `CenteredFlow(spacing: 6, lineSpacing: 2)`. */
internal val CaptionWordSpacing = 6.dp
internal val CaptionLineSpacing = 2.dp

/** `.offset(y: 8)`. */
internal val CaptionWordRise = 8.dp

/** Le repli quand la largeur proposée est indéfinie: `proposal.width ?? 320`. */
private val UnboundedCaptionWidth = 320.dp
