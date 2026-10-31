package com.onyx.app.ui.rich

import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.design.LocalReduceMotion
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay

/**
 * Le bord doux d'une réponse qui arrive (`StreamingTail`).
 *
 * Les derniers caractères sont dessinés pâles, un peu bas et, tout au bout, flous, puis remontent à
 * pleine force à mesure que du texte s'installe derrière eux, avec un point après le dernier. Si le
 * texte s'arrête un moment (le modèle réfléchit, appelle un outil), le bord se stabilise et tout
 * reprend sa force — le point reste, la réponse n'est pas finie.
 *
 * @param dot la couleur du point ; celle de l'accent si l'on n'en donne pas.
 * @param settleDelayMillis le silence après lequel le bord se stabilise, en millisecondes.
 */
@Composable
fun StreamingTailText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    settleDelayMillis: Long = SETTLE_DELAY_MS,
) {
    val theme = LocalOnyxTheme.current
    val dotColor = dot ?: theme.accent

    // Après un court silence, le bord se stabilise : plus de pâleur, plus de décalage, mais le
    // point reste — la réponse n'est pas finie.
    var revision by remember { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(text.length, revision) {
        settled = false
        delay(settleDelayMillis)
        settled = true
    }
    LaunchedEffect(text.length) { revision++ }

    val annotated = remember(text, style, dotColor, settled, settleDelayMillis) {
        buildAnnotatedString {
            if (settled || text.length <= TAIL_LENGTH) {
                append(text)
            } else {
                append(text.dropLast(TAIL_LENGTH))
                val tail = text.takeLast(TAIL_LENGTH)
                tail.forEachIndexed { index, char ->
                    // La distance depuis la fin : le dernier glyphe est à 0.
                    val remaining = tail.length - 1 - index
                    val strength = tailStrength(remaining = remaining, settled = settled)
                    if (strength >= 1f) {
                        append(char)
                    } else {
                        val ink = style.color.copy(alpha = 0.12f + 0.88f * strength)
                        withStyle(
                            SpanStyle(
                                color = ink,
                                // Le glyphe part trois points plus bas et remonte en arrivant.
                                baselineShift = BaselineShift(-(1f - strength) * LIFT_MULTIPLIER),
                                // Les cinq derniers, encore flous : Compose n'a pas de flou par
                                // glyphe, un halo de la couleur du texte en tient lieu.
                                shadow = if (remaining < BLURRED_LENGTH) {
                                    Shadow(color = ink, blurRadius = (1f - strength) * BLUR_RADIUS)
                                } else {
                                    null
                                },
                            ),
                        ) { append(char) }
                    }
                }
            }
            // Le point suit le dernier glyphe et reste même après la stabilisation.
            withStyle(SpanStyle(color = dotColor)) { append(" •") }
        }
    }

    // Avec « Réduire les animations », le bord ne s'anime pas : le texte arrive d'un bloc, comme
    // sur la référence quand l'accessibilité le demande (`StreamingTail:94-101`). Le drapeau vient
    // du réglage système, posé par `ProvideReduceMotion` à la racine.
    val reduceMotion = LocalReduceMotion.current

    if (reduceMotion) {
        BasicText(text = text, localize = false, style = style, modifier = modifier)
    } else {
        BasicText(text = annotated, style = style, modifier = modifier)
    }
}

/**
 * Combien un glyphe a monté, de 0 (à peine posé) à 1 (comme le reste du texte) : selon sa distance
 * à la fin du texte, et selon que le bord s'est stabilisé ou non. Port de `StreamingTail.strength`.
 */
private fun tailStrength(remaining: Int, settled: Boolean): Float {
    if (remaining >= TAIL_LENGTH) return 1f
    val arrived = (remaining + 1).toFloat() / (TAIL_LENGTH + 1).toFloat()
    val settle = if (settled) 1f else 0f
    return arrived + (1f - arrived) * settle
}

/** Combien des derniers glyphes sont encore en train de monter. */
private const val TAIL_LENGTH = 18

/** Combien, parmi eux, sont encore flous. */
private const val BLURRED_LENGTH = 5

/** De combien un glyphe part plus bas, en fraction de la hauteur d'ascendante (≈ trois points). */
private const val LIFT_MULTIPLIER = 0.2f

/** Le rayon du halo des glyphes qui arrivent, en pixels. */
private const val BLUR_RADIUS = 2f

/** Le silence après lequel le bord se stabilise, en millisecondes. */
private const val SETTLE_DELAY_MS = 700L
