package com.onyx.app.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Le bouton rond du composer (`ComposerButton`) : un cercle teinté, glyphe dans la couleur
 * « texte sur bulle » du thème. Rien de Material — pas de ripple, pas d'élévation.
 */
@Composable
fun ComposerCircleButton(
    @DrawableRes symbol: Int?,
    label: String,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    waveform: Boolean = false,
    contentTint: Color? = null,
    iconSize: Dp? = null,
    /**
     * L'**opacité d'un bouton inerte**. 0,35 est l'atténuation habituelle de la maison pour un
     * contrôle désactivé ; **un bouton dont l'encre est mesurée passe `1f`**, et `enabled` ne
     * commande alors que le **clic**, jamais la peinture (même règle que les touches de l'écran
     * Ordinateur, `ComputerScreen`).
     *
     * La raison est mesurée : à 35 %, le disque d'envoi composé sur la plaque mesure **1,49:1** en
     * apparence sombre (« Standard ») et **1,40:1** au pire des sept thèmes — le bouton et sa flèche
     * n'existent plus à l'œil, c'est la capture du propriétaire (« la zone du bouton d'envoi est
     * vide »). La couche d'opacité s'applique en effet à **tout** l'arbre, disque **et** glyphe.
     */
    disabledAlpha: Float = 0.35f,
    /**
     * Le **filet** du bouton quand il n'est pas accentué : le seul filet de l'app
     * ([OnyxSurfaces.hairline]). `null` = pas de filet (le bouton est un aplat).
     *
     * C'est ce qui distingue un bouton **au repos** d'un bouton **cassé** : un cercle gris sans
     * bordure se lit comme un contrôle désactivé ou inachevé ; le même cercle **bordé** se lit comme
     * une action disponible qui attend son tour. Le propriétaire a vu la première version.
     */
    border: Color? = null,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // Le **gabarit unique** des boutons ronds : cité une fois et réemployé, si bien que la forme
    // d'un bouton rond n'a qu'un seul chemin dans toute l'app.
    val shape = RoundButtonShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint, shape)
            .then(
                if (border != null) {
                    Modifier.border(OnyxSpace.Stroke, border, shape)
                } else {
                    Modifier
                },
            )
            .onyxClickable(enabled = enabled, disabledAlpha = disabledAlpha, onClick = onClick)
            .semanticsLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        if (waveform) {
            WaveformPulse(
                color = contentTint ?: theme.userText,
                height = size.value * 0.56f,
                spacing = 2.5f,
                still = true,
                shape = floatArrayOf(0.38f, 0.66f, 1f, 0.66f, 0.38f),
            )
        } else if (symbol != null) {
            OnyxIcon(symbol = symbol, tint = contentTint ?: theme.userText, size = iconSize ?: size * 0.42f)
        }
    }
}

/** Un bouton rond en verre (pastille de liste de la barre de titre, « Nouvelle conversation »). */
@Composable
fun GlassCircleButton(
    @DrawableRes symbol: Int,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .size(size)
            .glassSurface(RoundButtonShape)
            .onyxClickable(onClick = onClick)
            .semanticsLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(symbol = symbol, tint = tint ?: theme.accent, size = iconSize)
    }
}

/**
 * La pastille en verre de la barre de titre : « … » et crayon côte à côte.
 *
 * [solid] — **la pastille ne laisse plus rien voir au travers.** Le verre d'une pastille est mince
 * (34 % de voile en sombre, [glassSurface]) : posée **sur du texte** — c'est le cas du bouton
 * « aller au dernier message », qui flotte au-dessus du fil — le contenu qui passe dessous se lit
 * **dans** la pastille, et le libellé se mélange aux messages (défaut relevé par le propriétaire :
 * « la pastille se mélange au texte »). Une pastille flottante qui doit rester lisible au-dessus de
 * ce qu'elle survole prend donc un voile **plein** : elle se détache comme un objet, et son encre
 * reste seule lisible. Les pastilles **dans une barre**, elles, gardent le verre mince — elles sont
 * posées sur une surface, jamais sur du texte.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .glassSurface(
                shape = RoundedCornerShape(50),
                intensity = if (solid) SolidPillIntensity else 1f,
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

/**
 * L'intensité du voile d'une pastille **pleine** : ×4 sur les deux alphas de [glassSurface]
 * (34 % → 1,00 en sombre, 72 % → 1,00 en clair). Le voile est alors **opaque**, sans jamais changer
 * de forme ni de liseré : c'est le même dessin, plein au lieu de mince.
 */
const val SolidPillIntensity: Float = 4f

/**
 * L'en-tête d'un repli : chevron qui pivote d'un quart de tour, puis le libellé.
 * Porté de `MessageRow.foldHeader`.
 */
@Composable
fun FoldHeader(
    open: Boolean,
    modifier: Modifier = Modifier,
    labelColor: Color? = null,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val chevronAngle by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = OnyxMotion.Sites.MessageFold,
        label = "foldChevron",
    )
    Row(
        modifier = modifier
            .onyxClickable(pressedScale = 0.98f, pressedAlpha = 0.7f, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(
            symbol = Symbols.ChevronRight,
            tint = theme.secondaryText,
            size = 11.dp,
            modifier = Modifier.rotate(chevronAngle),
        )
        label()
    }
}

/** Même en-tête avec un libellé textuel. */
@Composable
fun FoldHeader(
    open: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color? = null,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    FoldHeader(open = open, modifier = modifier, labelColor = labelColor, onClick = onClick) {
        BasicText(
            text = label,
            style = theme.subheadline.copy(color = labelColor ?: theme.secondaryText),
        )
    }
}

/** Cinq barres qui montent et descendent en décalage : la forme d'onde de l'app. */
@Composable
fun WaveformPulse(
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    height: Float = 20f,
    spacing: Float = 4f,
    barWidth: Float = 4f,
    still: Boolean = false,
    shape: FloatArray = floatArrayOf(0.45f, 0.75f, 1f, 0.75f, 0.45f),
) {
    val reduceMotion = LocalReduceMotion.current
    // `WaveformPulse` : `still: still || reduceMotion` (`Composer:92-95`) — la forme d'onde
    // du composeur se fige, comme celle de la marque de l'écran de départ.
    val frozen = still || reduceMotion
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase = ambientLoop(0f) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 628, easing = OnyxMotion.EaseInOut),
                repeatMode = RepeatMode.Restart,
            ),
            label = "waveformPhase",
        ).value
    }
    val canvasWidth = barCount * (barWidth + spacing) - spacing

    Canvas(
        modifier = modifier
            .width(canvasWidth.dp)
            .height(height.dp),
    ) {
        waveformBarRects(
            phase = phase,
            height = height,
            spacing = spacing,
            barWidth = barWidth,
            density = density,
            still = frozen,
            shape = shape,
            barCount = barCount,
        ).forEach { bar ->
            drawRoundRect(
                color = color,
                topLeft = Offset(bar.left, bar.top),
                size = Size(bar.width, bar.height),
                cornerRadius = CornerRadius(bar.cornerRadius, bar.cornerRadius),
            )
        }
    }
}

/** Une barre de waveform, déjà convertie dans les pixels du Canvas. */
internal data class WaveformBarRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
)

/**
 * La géométrie de `WaveformBarsView` : les paramètres sont en dp/points, le résultat en pixels.
 * Les calculs de hauteur et de phase restent ceux de l'animation référence ; seule la conversion change.
 */
internal fun waveformBarRects(
    phase: Float,
    height: Float,
    spacing: Float,
    barWidth: Float,
    density: Float,
    still: Boolean,
    shape: FloatArray,
    barCount: Int = 5,
): List<WaveformBarRect> {
    val heightPx = height * density
    val barWidthPx = barWidth * density
    val spacingPx = spacing * density
    return List(barCount) { index ->
        val rest = shape[index % shape.size]
        val breathe = if (still) 1f else (0.5f + 0.5f * kotlin.math.sin(phase + 0.9f * index))
        val barHeightPx = (height * rest * (0.35f + 0.65f * breathe)).coerceAtLeast(4f) * density
        WaveformBarRect(
            left = index * (barWidthPx + spacingPx),
            top = (heightPx - barHeightPx) / 2f,
            width = barWidthPx,
            height = barHeightPx,
            cornerRadius = barWidthPx / 2f,
        )
    }
}

/** `LiveMark` : le point et son anneau qui s'élargit, une boucle `easeOut` de 1,6 s. */
@Composable
fun LiveMark(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val transition = rememberInfiniteTransition(label = "live")
    // `LiveMark.onAppear { guard !reduceMotion else { return } }` (`StreamingTail:134-135`) :
    // pas de boucle, le point et son anneau tiennent leur repos (anneau 0,6, point 0,9).
    val pulse = ambientLoop(0f) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = OnyxMotion.MS_1600, easing = OnyxMotion.EaseOut),
                repeatMode = RepeatMode.Restart,
            ),
            label = "livePulse",
        ).value
    }
    Canvas(modifier = modifier.size(size)) {
        val ringScale = 0.6f + 1.3f * pulse
        val ringStroke = 1.5.dp.toPx() * ringScale
        drawCircle(
            color = color.copy(alpha = (1f - pulse) * 0.7f),
            radius = this.size.minDimension / 2f * ringScale - ringStroke / 2f,
            style = Stroke(width = ringStroke),
        )
        drawCircle(
            color = color,
            radius = 4.dp.toPx() * (0.9f + 0.25f * pulse),
        )
    }
}

/**
 * Du texte traversé par une bande de lumière (`ShimmerText`).
 *
 * Le fond de la bande est la couleur de base, si bien que le texte reste lisible partout et
 * qu'une bande plus claire le balaie de gauche à droite.
 */
@Composable
fun ShimmerText(
    text: String,
    style: TextStyle,
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    animating: Boolean = true,
) {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    // `ShimmerText` : « Still text under Reduce Motion » (`MessageRow:538-557`) — la boucle
    // n'est même pas composée, et le texte reste d'aplat.
    // `ShimmerText.phase` : -1 → 1,2, `linear` 1,8 s. La bande fait 0,6 × la largeur du texte et
    // son bord d'attaque suit la phase.
    val shift = ambientLoop(-1f) {
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = OnyxMotion.Sites.Shimmer,
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmerShift",
        ).value
    }

    val brush = if (!animating || reduceMotion) {
        Brush.linearGradient(listOf(baseColor, baseColor))
    } else {
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to baseColor,
                shift.coerceIn(0f, 1f) to baseColor,
                (shift + 0.3f).coerceIn(0f, 1f) to highlightColor,
                (shift + 0.6f).coerceIn(0f, 1f) to baseColor,
                1f to baseColor,
            ),
        )
    }

    BasicText(text = text, style = style.copy(brush = brush), modifier = modifier)
}

/** Pose un `contentDescription` localisé sans passer par un composant Material. */
fun Modifier.semanticsLabel(label: String): Modifier =
    this.semantics { contentDescription = OnyxLocalization[label] }
