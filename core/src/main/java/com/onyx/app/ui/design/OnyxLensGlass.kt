package com.onyx.app.ui.design

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * **LA LENTILLE — le verre qui courbe ce qu'il recouvre.**
 *
 * Jusqu'ici la vitre d'Onyx savait **flouter** ([LiquidGlass], Haze) : le fil passait derrière, flou,
 * et c'était tout. Le verre de référence (le verre système de 2025, et l'app `myspoti` qui l'a porté sur Android) fait
 * une chose de plus qui change tout à l'œil : il **courbe** le fond au bord de sa forme, comme une
 * goutte. C'est cette courbure — un déplacement des pixels proportionnel à la distance au bord, écrit
 * en AGSL — qui donne l'épaisseur, l'arête qui s'allume et la « lentille » qu'on voit glisser.
 *
 * La mécanique vient de `io.github.kyant0:backdrop` (Apache-2.0, Maven `2.0.1`) :
 *
 *  1. la page qui défile est **enregistrée une fois** dans un `GraphicsLayer` ([rememberOnyxLensPage],
 *     posé par l'écran sur son contenu — c'est le même contenu que celui de `onyxHazeSource`) ;
 *  2. chaque surface de verre **échantillonne cet enregistrement** ([onyxLensGlass]) et lui applique
 *     sa chaîne d'effets : saturation, flou, puis lentille ;
 *  3. la lentille n'existe qu'à partir d'**Android 13** (`RuntimeShader`) : en dessous, la surface
 *     reprend la vitre de Haze ([LiquidGlass]) — jamais de trou, jamais d'aplat.
 *
 * Une seule prise pour toutes les surfaces : c'est aussi **moins cher** que le flou par surface,
 * puisque la page n'est capturée qu'une fois par image.
 *
 * Les chiffres sont ceux de la référence, et ils suivent la **taille** de la surface : une barre
 * mince floute peu et courbe fort ; une grande plaque floute plus et s'opacifie (son texte doit
 * tenir).
 */
object OnyxLens {

    /** En dessous, pas de `RuntimeShader` : la lentille n'existe pas, Haze reste le verre. */
    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Surface « mince » (dp, petit côté) : barre, pastille, bouton. */
    const val THIN_DP = 56f

    /** Surface « large » (dp) : une plaque, une feuille — le texte y est posé. */
    const val LARGE_DP = 200f

    /** Flou d'une barre mince, en dp. */
    const val BLUR_THIN_DP = 3.5f

    /** Flou d'une grande plaque, en dp. */
    const val BLUR_LARGE_DP = 20f

    /** Hauteur de la courbure au bord, en dp — la plus faible d'une barre mince. */
    const val REFRACTION_HEIGHT_THIN_DP = 22f

    /** Et la plus forte, sur une grande plaque. */
    const val REFRACTION_HEIGHT_LARGE_DP = 44f

    /** Bombe de la lentille sous le doigt (fraction de sa courbure). */
    const val PRESS_BULGE = 0.35f

    /** 0 pour une barre mince, 1 pour une grande plaque : tout le reste s'interpole là-dessus. */
    fun depth(minSideDp: Float): Float =
        ((minSideDp - THIN_DP) / (LARGE_DP - THIN_DP)).coerceIn(0f, 1f)

    fun blurDp(minSideDp: Float): Float = mix(BLUR_THIN_DP, BLUR_LARGE_DP, depth(minSideDp))

    fun refractionHeightDp(minSideDp: Float): Float =
        mix(REFRACTION_HEIGHT_THIN_DP, REFRACTION_HEIGHT_LARGE_DP, depth(minSideDp))

    /** La courbure **déplacée** au bord : plus forte sur une barre mince que sur une grande plaque. */
    fun refractionAmountDp(minSideDp: Float): Float =
        refractionHeightDp(minSideDp) * mix(2.0f, 1.5f, depth(minSideDp))

    /**
     * **L'opacité du corps.** C'est elle qui laisse voir la lentille : à `0,42` sur une barre, le
     * fond courbé se lit à travers le verre ; à `0,60` sur une grande plaque, le texte posé dessus
     * tient encore. Le plancher de masquage d'Onyx (≈ 0,8, pour que le fil ne se lise pas derrière un
     * simple **flou**) n'a plus d'objet ici : le fond est **courbé et flouté**, il ne se lit plus
     * comme du texte — et à 0,8 la lentille était invisible, ce qui était exactement le reproche.
     */
    fun bodyAlpha(minSideDp: Float): Float = mix(0.30f, 0.44f, depth(minSideDp))

    /**
     * **Le plancher d'une surface qui MASQUE** ([masking] de `liquidGlass`). Une plaque posée sur un
     * fil qui défile doit arrêter le texte : à 0,44 on lisait les messages **au travers** (« on est
     * revenu en arrière »). 0,84 laisse encore voir la courbure aux arêtes, et ne laisse plus lire
     * l'encre derrière — c'est le plancher que la vitre de Haze appliquait déjà.
     */
    const val MASKED_BODY_ALPHA = 0.92f

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

/**
 * La page que la lentille échantillonne, posée par le thème ([com.onyx.app.ui.theme.OnyxTheme]).
 *
 * `null` avant Android 13, ou dans un aperçu sans page : la vitre reprend alors Haze.
 */
val LocalOnyxLensPage = compositionLocalOf<LayerBackdrop?> { null }

/**
 * **Vrai à l'intérieur de la page qu'on enregistre.**
 *
 * Une vitre posée **dans** le contenu qu'elle échantillonnerait se verrait elle-même (rétroaction :
 * un verre qui se repeint dans son propre fond). Les surfaces de cette zone gardent donc la vitre de
 * Haze — une carte de la liste n'a de toute façon rien à courber : la page est derrière elle, pas
 * dessous. C'est la règle de la bibliothèque (« un verre dessiné dans la page qu'il échantillonne
 * retombe sur un aplat »), écrite ici une fois pour tout l'arbre.
 */
val LocalOnyxLensInside = compositionLocalOf { false }

/**
 * **Le conteneur d'une page enregistrée.** Il pose l'enregistrement sur son contenu
 * ([layerBackdrop]) et dit à tout ce qui vit dedans qu'il est dans la page
 * ([LocalOnyxLensInside]) — c'est ce qui empêche une vitre de s'échantillonner elle-même.
 */
@Composable
fun OnyxLensPage(page: LayerBackdrop, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOnyxLensInside provides true) {
        androidx.compose.foundation.layout.Box(modifier = modifier.layerBackdrop(page)) { content() }
    }
}

/** L'enregistrement d'une page, tenu par l'écran qui défile. */
@Composable
fun rememberOnyxLensPage(): LayerBackdrop = rememberLayerBackdrop()

/** Pose l'enregistrement sur le contenu qui défile (le même nœud que `onyxHazeSource`). */
fun Modifier.onyxLensPage(page: LayerBackdrop): Modifier = this.layerBackdrop(page)

/**
 * **La vitre à lentille.** Même contrat d'usage que [liquidGlass] : on l'applique à la surface, qui
 * prend alors la forme, le corps teinté, l'arête de lumière et l'ombre.
 *
 * @param shape la forme de la surface (la lentille ne sait courber qu'un rectangle arrondi).
 * @param page la page enregistrée à échantillonner.
 * @param tint la teinte du corps posée **sur** le fond courbé (pas un aplat : la lentille se lit au
 * travers).
 * @param press 0..1, l'appui : la lentille bombe et la surface se rétracte un peu.
 */
@Composable
fun Modifier.onyxLensGlass(
    shape: Shape,
    page: LayerBackdrop,
    tint: Color,
    press: (() -> Float)? = null,
    /**
     * **L'arête de lumière de la lentille.** `true` pour une **plaque** (la capsule du
     * compositeur, la barre d'onglets) : c'est son arête qui la détache. `false` pour une grande
     * surface de fond (le panneau du volet, qui fait toute la hauteur de l'écran) : sa forme longe
     * les bords du téléphone, et un rim y dessinait un **cadre autour de l'écran**.
     */
    rim: Boolean = true,
    /** La surface doit masquer ce qu'elle recouvre (plaque sur un fil qui défile). */
    masked: Boolean = false,
): Modifier {
    val bend = shape is CornerBasedShape
    // Les effets sont **retenus** : une lambda neuve à chaque recomposition rebâtirait la chaîne.
    val effects = remember(bend, press) {
        val block: BackdropEffectScope.() -> Unit = {
            val minSideDp = size.minDimension / density
            val grow = 1f + OnyxLens.PRESS_BULGE * (press?.invoke() ?: 0f)
            // La vivacité du fond, puis un souffle plus sombre : un fil clair derrière la plaque
            // ne doit pas laver l'encre posée dessus.
            colorControls(brightness = -0.03f, saturation = 1.6f)
            blur(OnyxLens.blurDp(minSideDp).dp.toPx())
            if (bend) {
                lens(
                    refractionHeight = OnyxLens.refractionHeightDp(minSideDp).dp.toPx() * grow,
                    refractionAmount = OnyxLens.refractionAmountDp(minSideDp).dp.toPx() * grow,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        }
        block
    }
    val surface = remember(tint, masked) {
        val block: DrawScope.() -> Unit = {
            val minSideDp = size.minDimension / density
            val body = OnyxLens.bodyAlpha(minSideDp)
            val alpha = if (masked) maxOf(body, OnyxLens.MASKED_BODY_ALPHA) else body
            drawRect(color = tint.copy(alpha = alpha), size = size)
        }
        block
    }
    val layerBlock = remember(press) {
        press?.let { read ->
            val block: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
                val scale = 1f - 0.04f * read()
                scaleX = scale
                scaleY = scale
            }
            block
        }
    }
    return drawBackdrop(
        backdrop = page,
        shape = { shape },
        effects = effects,
        highlight = if (rim) {
            { OnyxLensHighlight }
        } else {
            null
        },
        shadow = { OnyxLensShadow },
        layerBlock = layerBlock,
        onDrawSurface = surface,
    )
}

/** L'arête du verre : plus fine et plus franche que le défaut de la bibliothèque, comme le verre système de 2025. */
// **Aucun filet blanc sur le périmètre.** À 0,9, l'arête de la lentille se lisait comme un trait
// rapporté autour de la capsule (et, sur une surface qui longe un bord d'écran, comme un cadre). La
// lentille se détache par sa **courbure** et son ombre : c'est le fond qui se plie, pas un contour.
private val OnyxLensHighlight = Highlight(width = 0.5.dp, alpha = 0.0f)

/** L'ombre posée sous la surface, douce et large. */
private val OnyxLensShadow = Shadow(
    radius = 18.dp,
    offset = DpOffset(0.dp, 6.dp),
    color = Color.Black,
    alpha = 0.22f,
)
