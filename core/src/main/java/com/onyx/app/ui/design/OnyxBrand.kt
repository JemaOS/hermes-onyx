package com.onyx.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **LA MARQUE DE L'APPLICATION — le logo du lanceur, à l'identique.**
 *
 * Une marque n'est pas un élément d'interface : c'est **l'identité** de l'app, elle **ne se dérive
 * pas du thème**. Le logo que le propriétaire voit sur son écran d'accueil, c'est le **carène
 * Graphite Block** — la **plaque sombre**, le **cercle** et la **rune** — et c'est *ce dessin-là*,
 * ces trois éléments et ces couleurs-là, que l'application porte partout où elle dit qui elle est,
 * **en clair comme en sombre**, sans jamais passer par l'accent du thème.
 *
 * **Pourquoi ce fichier existe.** La marque était dessinée comme un élément d'interface : un
 * **glyphe monochrome à l'encre mesurée** sur un **disque teinté de l'accent** — d'où une **rune
 * bleue sur un disque bleu pâle**, qui ressemble à une icône d'accent, pas à la marque de l'app. La
 * règle « glyphe monochrome à l'encre sur disque teinté » est une règle d'interface ; appliquée à
 * l'identité, elle produit un logo qui **change de couleur avec le thème**. Ce fichier remplace
 * `OnyxRuneMark(disc = …, tint = …)`, qui **prenait** une teinte : ici, **aucune teinte n'entre**,
 * aucune ne sort — il n'y a que des constantes.
 *
 * **Les couleurs viennent de l'icône, elles ne sont pas choisies ici.** Elles sont *mot pour mot*
 * celles des deux ressources du lanceur, et le verrou
 * (`app/src/test/.../RuneSourceUniqueTest.kt`) les y compare **en nommant le fichier et la ligne** :
 *
 *  - la **plaque** est `@color/ic_launcher_background` (`app/src/main/res/values/colors.xml`) ;
 *  - le **cercle**, son **reflet**, son **contour** et la **rune** sont ceux de la couche de premier
 *    plan (`app/src/main/res/drawable/ic_launcher_adaptive_foreground.xml`), dans son canevas de
 *    108 unités.
 *
 * **Le tracé de la rune n'est pas réécrit** : il vient de [OnyxRune] (la source unique du signe),
 * placé et mis à l'échelle par le même chemin que le reste de l'application.
 */
object OnyxBrand {

    // =============================================================================================
    // Les teintes — **constantes**, prises sur l'icône de lanceur. Aucune n'est un paramètre.
    // =============================================================================================

    /** La **plaque sombre** : `@color/ic_launcher_background` de `values/colors.xml`. */
    val Plate: Color = Color(0xFF1A202A)

    /** Le **cercle** : le haut du dégradé vertical de la couche de premier plan (`#36404C`). */
    val CircleTop: Color = Color(0xFF36404C)

    /** Le **cercle** : le bas du dégradé vertical de la couche de premier plan (`#222937`). */
    val CircleBottom: Color = Color(0xFF222937)

    /** Le **contour clair du cercle** (`#A9B3C2`). */
    val Outline: Color = Color(0xFFA9B3C2)

    /** L'**encre de la rune** (`#DADFE8`). */
    val RuneInk: Color = Color(0xFFDADFE8)

    /** Le **reflet vitreux** en haut à gauche : 15 % de blanc qui s'éteint. */
    val Highlight: Color = Color(0x26FFFFFF)

    // =============================================================================================
    // La géométrie dans le canevas de 108 de la référence (celui de la couche de premier plan).
    // =============================================================================================

    /** Le canevas de la couche de premier plan (108 × 108). */
    const val CANVAS: Float = 108f

    /** Le rayon du cercle dans le canevas : 0,72 de la largeur de l'illustration -> 31,5. */
    const val CIRCLE_RADIUS: Float = 31.5f

    /** L'épaisseur du contour du cercle dans le canevas. */
    const val OUTLINE_WIDTH: Float = 1.8f

    /** Le centre du reflet dans le canevas (haut à gauche). */
    const val HIGHLIGHT_CENTER_X: Float = 45f

    /** Le centre du reflet dans le canevas. */
    const val HIGHLIGHT_CENTER_Y: Float = 43f

    /** Le rayon du reflet dans le canevas. */
    const val HIGHLIGHT_RADIUS: Float = 13f

    /** Le centre du canevas (le cercle et la rune y sont centrés). */
    const val CENTER: Float = 54f

    /** Le côté du carré que la marque occupe : le disque l'occupe en entier. */
    val Side: Dp = OnyxSocle.ThumbnailSize

    // =============================================================================================
    // Le dessin — pur Compose, sans aucune couleur de thème
    // =============================================================================================

    /** Le facteur d'échelle, en pixels, d'une unité du canevas pour un carré de côté [side] px. */
    fun scaleFor(side: Float): Float = side / CANVAS

    /**
     * **La marque, dessinée** : la plaque est le fond du carré (voir [OnyxBrandMark]) ; ce dessin
     * pose le **cercle**, son **reflet**, son **contour** et la **rune**. Le côté du carré est
     * [DrawScope.size] : le disque l'occupe en entier.
     */
    fun DrawScope.drawMark() {
        val side = minOf(size.width, size.height)
        val k = scaleFor(side)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = CIRCLE_RADIUS * k

        // Le cercle : dégradé vertical #36404C -> #222937.
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(CircleTop, CircleBottom),
                startY = cy - radius,
                endY = cy + radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )

        // Le reflet vitreux en haut à gauche.
        val highlightCentre = Offset(
            cx + (HIGHLIGHT_CENTER_X - CENTER) * k,
            cy + (HIGHLIGHT_CENTER_Y - CENTER) * k,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Highlight, Color.Transparent),
                center = highlightCentre,
                radius = HIGHLIGHT_RADIUS * k,
            ),
            radius = HIGHLIGHT_RADIUS * k,
            center = highlightCentre,
        )

        // Le contour clair du cercle.
        drawCircle(
            color = Outline,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = OUTLINE_WIDTH * k),
        )

        // La rune : le tracé de la source unique, à l'anatomie de l'icône — elle occupe
        // HEIGHT_FRACTION (0,40) du **diamètre du cercle**, centrée.
        val runeHeight = 2f * radius * OnyxRune.HEIGHT_FRACTION
        val (points, halfWidth) = OnyxRune.placed(cx, cy, runeHeight)
        val path = Path().apply {
            points.forEachIndexed { index, vertex ->
                if (index == 0) moveTo(vertex.x, vertex.y) else lineTo(vertex.x, vertex.y)
            }
        }
        drawPath(
            path = path,
            color = RuneInk,
            style = Stroke(width = halfWidth * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * **Le logo de l'application, tel qu'il est sur l'écran d'accueil : la plaque sombre, le cercle et
 * la rune.** Il est **identique en clair et en sombre** et ne prend **aucune** teinte — ni le thème,
 * ni l'accent, ni un fond.
 *
 * C'est un **logo**, pas une icône d'accent : ses couleurs sont celles de l'icône de lanceur et rien
 * d'autre (voir [OnyxBrand]). L'appelant ne fournit que la taille et, au besoin, ce que le signe dit
 * ([description]) — jamais une couleur.
 *
 * @param size le côté du carré ; la plaque (ronde) l'occupe en entier.
 * @param description ce que dit le signe pour l'accessibilité ; `null` quand un libellé voisin le
 *        porte déjà, ou quand le contrôle a déjà son nom.
 */
@Composable
fun OnyxBrandMark(
    size: Dp,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(OnyxBrand.Plate, CircleShape)
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { with(OnyxBrand) { drawMark() } }
    }
}
