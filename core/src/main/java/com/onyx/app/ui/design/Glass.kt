package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * L'ombre **dessinée** d'une pastille en verre — et **pourquoi elle remplace `Modifier.shadow`**.
 *
 * **Le défaut, mesuré.** `Modifier.shadow` confie l'ombre à la plateforme : `hwui` la calcule à
 * partir du contour de la forme, et sur un **petit disque** il approxime la silhouette par un
 * **polygone inscrit** — mesuré ×8 sur les boutons ronds : un **octogone à huit pans droits** à
 * l'intérieur du disque, en thème clair **et** en thème sombre. Il se voit *à l'intérieur* parce que
 * la vitre est **translucide** : l'ombre est peinte derrière elle, et la teinte de 20–34 % de blanc
 * laisse passer l'ombre. C'est un artefact de **la plateforme**, pas du code de l'app — il n'y a
 * aucun `Path` polygonal dans le dépôt, et le disque **bleu** du compositeur, qui n'a pas d'ombre,
 * se mesure parfaitement circulaire (aucune harmonique d'ordre 8 sur son bord).
 *
 * **La réparation.** L'ombre est peinte ici, en **anneaux** qui suivent la forme **par l'extérieur** :
 * le contour de la forme, élargi de quelques pas, chaque anneau tracé à une épaisseur qui croît vers
 * l'extérieur. Trois conséquences, toutes voulues : la face de la vitre n'est **jamais** assombrie
 * (le bord intérieur de chaque anneau *est* l'arête de la forme — c'est le comportement de l'ombre
 * d'origine, reproduit), l'opacité décroît en s'éloignant du bord sans le moindre palier, et la
 * silhouette suit **exactement** la forme — un cercle pour un disque, une capsule pour une pilule,
 * un rectangle arrondi pour une carte — donc plus rien n'est tessellé. La force de l'ombre est
 * calibrée sur l'ombre d'origine ([GlassDiscShadowLight]).
 */
fun Modifier.glassDiscShadow(elevation: Dp, shape: Shape): Modifier = composed {
    val elevationPx = with(LocalDensity.current) { elevation.toPx() }
    val dark = LocalOnyxTheme.current.dark
    this.drawBehind {
        if (elevationPx <= 0f || size.width <= 0f || size.height <= 0f) return@drawBehind
        val alpha = if (dark) GlassDiscShadowDark else GlassDiscShadowLight
        val step = elevationPx * GlassDiscShadowReach / GlassDiscShadowRings
        // Chaque anneau couvre la bande **[0, i·pas] hors** de la forme : son bord intérieur est
        // exactement l'arête de la forme — la face de la vitre n'est donc **jamais** assombrie, ce
        // qui reproduit le comportement d'origine — et son épaisseur croît vers l'extérieur, si bien
        // que l'opacité décroît en s'éloignant du bord, sans palier ni facette.
        for (ring in GlassDiscShadowRings downTo 1) {
            val width = step * ring
            val grow = width / 2f
            val grown = Size(size.width + 2f * grow, size.height + 2f * grow)
            val outline = shape.createOutline(grown, layoutDirection, this)
            withTransform({ translate(-grow, -grow) }) {
                drawOutline(
                    outline = outline,
                    color = Color.Black.copy(alpha = alpha),
                    style = Stroke(width = width),
                )
            }
        }
    }
}

/** Combien d'anneaux composent l'ombre dessinée : assez pour être lisse, assez peu pour être gratuit. */
const val GlassDiscShadowRings: Int = 6

/**
 * La portée de l'ombre, en multiples de l'élévation.
 *
 * Mesurée sur l'ombre d'origine : son opacité tenait un **plateau** de ~3,5 % jusqu'à cinq ou six
 * pixels au-delà de l'arête, puis retombait à zéro vers dix. Une portée de 2,2 × l'élévation
 * reproduit cette étendue (2 dp → 12 px, 3 dp → 18 px) : l'ombre détache la vitre au lieu de se
 * coller à elle.
 */
const val GlassDiscShadowReach: Float = 2.2f

/**
 * L'opacité d'un anneau d'ombre, en clair.
 *
 * Calibrée sur l'ombre d'origine, **mesurée** : sur une page à 248, la bande de 50 à 55 px autour du
 * bouton tenait 239,28 — un écart de 8,84 niveaux, soit une opacité moyenne de 3,6 %. Six anneaux à
 * 0,9 % (recouvrement moyen 4,2) donnent 9,2 niveaux : la **même** force, mais lisse au lieu d'être
 * polygonale.
 */
const val GlassDiscShadowLight: Float = 0.009f

/** Idem en sombre, plus appuyée : la page sombre avale les ombres faibles (mesuré : 2,6 niveaux). */
const val GlassDiscShadowDark: Float = 0.021f

/**
 * La pastille en verre (`glassEffect` de la référence) : un dégradé translucent, un liseré clair de
 * 0,5 dp et une ombre douce.
 *
 * Le fond n'est **jamais du blanc pur** : il est teinté de la page ([veilColour]), si bien qu'une
 * pastille posée sur un thème ambré reste ambrée. Le liseré marque l'arête, l'ombre détache la
 * pastille du contenu.
 *
 * C'est l'habillage d'une **forme** (bouton, pastille, capsule). Une **surface traversée par du
 * contenu** — barre, champ, onglet, bandeau — passe par [Frost], qui floute réellement ce qui passe
 * derrière.
 *
 * L'ombre est **dessinée** ([glassDiscShadow]) et non confiée à la plateforme : voir le défaut
 * « les boutons ronds sont octogonaux » documenté sur [glassDiscShadow].
 */
fun Modifier.glassSurface(
    shape: Shape = RoundButtonShape,
    tint: Color? = null,
    intensity: Float = 1f,
): Modifier = composed {
    val theme = LocalOnyxTheme.current
    val base = tint ?: veilColour(theme.pageColor, 1f, theme.dark)
    val topAlpha = (if (theme.dark) 0.34f else 0.72f) * intensity
    val bottomAlpha = (if (theme.dark) 0.20f else 0.54f) * intensity
    val borderAlpha = if (theme.dark) 0.16f else 0.60f

    this
        .glassDiscShadow(
            elevation = (if (theme.dark) 3.dp else 2.dp) * intensity,
            shape = shape,
        )
        .background(
            brush = Brush.verticalGradient(
                listOf(
                    base.copy(alpha = topAlpha),
                    base.copy(alpha = bottomAlpha),
                ),
            ),
            shape = shape,
        )
        .border(0.5.dp, Color.White.copy(alpha = borderAlpha), shape)
}

/**
 * **Le disque d'un contrôle en verre désactivé.**
 *
 * Le défaut relevé sur la capture du propriétaire : « le bouton d'envoi gris à flèche pâle a l'air
 * cassé plutôt que désactivé ». Le bouton portait le **gris de remplissage du thème**
 * ([com.onyx.app.ui.theme.ResolvedTheme.fill]) : sur la carte de verre, le disque prenait exactement
 * la teinte de la vitre et **disparaissait** — il ne restait qu'une flèche pâle qui flottait.
 *
 * Un disque d'**encre** à 12 % se lit sur les deux apparences : il assombrit une vitre claire, il
 * éclaircit une vitre sombre. C'est la même pièce que le remplissage d'un contrôle inactif de la
 * référence, et elle ne peut pas se confondre avec le fond puisqu'elle part de l'encre, jamais du
 * remplissage.
 */
fun glassDisabledDisc(theme: com.onyx.app.ui.theme.ResolvedTheme): Color =
    if (theme.dark) Color.White.copy(alpha = 0.12f) else theme.textColor.copy(alpha = 0.12f)

/**
 * La forme de **tous** les boutons ronds de l'app : un vrai cercle.
 *
 * Elle est déclarée **une seule fois** et citée par chaque bouton rond (le compositeur, l'en-tête,
 * le volet, la capsule de la barre du haut) : c'est ce qui garantit qu'aucun d'eux ne puisse
 * prendre un `Path` polygonal de son côté — un cercle tracé en huit segments *est* un octogone.
 */
val RoundButtonShape: Shape = CircleShape

/** Une pastille arrondie à coins droits (champs, cartes, barres). */
@Composable
fun glassCardShape(radius: Dp) = RoundedCornerShape(radius)
