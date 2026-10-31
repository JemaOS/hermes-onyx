package com.onyx.app.ui.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **La soudure de deux formes de verre.**
 *
 * Deux vitres voisines ne se contentent pas de se toucher : quand elles se rapprochent, une **langue
 * de matière** les relie — le verre se comporte comme un liquide, et deux gouttes qui se frôlent
 * finissent par n'en faire qu'une. C'est la troisième pièce du verre liquide, après la réfraction et
 * la lumière qui suit le doigt, et c'est la seule des trois qu'aucune bibliothèque ne donnait : il a
 * donc fallu l'écrire.
 *
 * **Pourquoi ici, et pas en SDF.** Une bibliothèque qui travaille en **champ de distance signé**
 * (SDF) dessine l'union lisse de deux formes en calculant, dans un processeur d'ombrage, le minimum
 * lissé (`smooth-min`) des deux champs — puis en marchant à la recherche de l'isocontour. C'est la
 * bonne façon de faire, et c'est ce que font les bibliothèques qui fusionnent réellement des formes.
 * Nous ne l'avons pas fait ici, et la raison est mesurable : le champ de distance d'un rectangle
 * **arrondi** n'est pas une formule fermée, et le convertir en tracé demande un marching-squares par
 * image — soit exactement le coût qu'on cherche à éviter sur une barre du bas, à chaque trame, sur
 * un appareil qui n'a déjà pas de processeur d'ombrage. La soudure est donc peinte comme **une
 * région de matière** : un col de largeur variable, borné par deux cubiques, qui se pose **derrière**
 * les deux formes et que celles-ci recouvrent à moitié. Le résultat est le même à l'œil — un pont de
 * matière qui monte du col à la pleine largeur — pour le prix d'un remplissage et d'un trait.
 *
 * **Ce que ce fichier ne prétend pas faire.** La soudure **relie les formes**, elle ne **réfracte
 * pas** à travers le pont : Haze échantillonne chaque vitre dans **ses** bornes, et une source
 * n'éclaire pas une troisième surface posée entre les deux. Un pont qui réfracterait le fil
 * demanderait de peindre la soudure **dans** la même passe que la vitre, donc un moteur de verre à
 * nous — c'est ce que fait la bibliothèque candidate, et c'est expliqué dans le rapport du lot.
 *
 * **Deux fonctions pures**, donc éprouvables sans appareil : [glassWeldProgress] (la soudure se
 * ferme-t-elle ?) et [glassWeldPath] (la géométrie du pont).
 */

/** La distance à laquelle deux formes commencent à se souder, en dp, entre leurs deux bords. */
const val GlassWeldDistanceDp: Float = 34f

/**
 * La demi-largeur du col quand la soudure vient de commencer, en fraction de la demi-largeur
 * commune des deux formes. Jamais zéro : une soudure qui commence par un point serait invisible.
 */
const val GlassWeldNeckMin: Float = 0.26f

/** De combien le pont **entre** dans chaque forme, en dp : c'est ce qui le rend continu. */
const val GlassWeldInsetDp: Float = 5f

/** Ce que la soudure ajoute à la teinte du verre : la matière y est un peu plus dense. */
const val GlassWeldGain: Float = 0.05f

/**
 * **Où en est la soudure**, de 0 (les deux formes sont loin l'une de l'autre) à 1 (elles se
 * touchent).
 *
 * [gapPx] est la distance entre les deux bords face à face ; [weldPx] la distance à laquelle la
 * soudure commence. Le profil est un **lissage cubique** (`smoothstep`) et pas une rampe droite :
 * c'est ce qui fait que la langue de matière **se décide** au milieu du rapprochement au lieu de
 * croître linéairement — à l'œil, une ligne droite se lit comme un rectangle qu'on étire.
 */
fun glassWeldProgress(gapPx: Float, weldPx: Float): Float {
    if (!weldPx.isFinite() || weldPx <= 0f) return 0f
    if (!gapPx.isFinite()) return 0f
    if (gapPx >= weldPx) return 0f
    if (gapPx <= 0f) return 1f
    val t = 1f - gapPx / weldPx
    return t * t * (3f - 2f * t)
}

/**
 * **Les chiffres du pont, sans rien peindre.**
 *
 * C'est la seule partie de la soudure qui décide de sa forme, et elle est **pure** : deux rectangles
 * et une avancée entrent, des nombres sortent. Les tracés ([glassWeldPath], [glassWeldRimPath]) ne
 * font plus que relier ces points — donc la géométrie s'éprouve sans processeur d'ombrage, sans
 * appareil, et sans même construire un `Path` (ce que JVM ne sait pas faire hors appareil).
 */
data class GlassWeldShape(
    /** L'axe du pont. */
    val centreX: Float,
    /** La demi-largeur des bords haut et bas — celle des formes qui se font face. */
    val halfWidth: Float,
    /** La demi-largeur du **col**, au milieu : c'est elle qui dit « soudé » ou « à peine joint ». */
    val neckHalfWidth: Float,
    /** Le bord haut du pont, **dans** la forme du dessus. */
    val top: Float,
    /** Le bord bas du pont, **dans** la forme du dessous. */
    val bottom: Float,
    /** La profondeur de la courbure des deux cubiques : c'est elle qui donne le col concave. */
    val bend: Float,
) {
    /** La largeur du col au point le plus étroit. */
    val neckWidth: Float get() = neckHalfWidth * 2f

    /** La hauteur du pont, bords compris. */
    val span: Float get() = bottom - top
}

/**
 * La forme du pont, ou `null` quand il n'y a rien à souder : formes **disjointes horizontalement**,
 * **jointives ou recouvrantes** (elles se touchent déjà), ou soudure nulle.
 */
fun glassWeldShape(upper: Rect, lower: Rect, weld: Float, insetPx: Float): GlassWeldShape? {
    if (weld <= 0f) return null
    val left = maxOf(upper.left, lower.left)
    val right = minOf(upper.right, lower.right)
    val width = right - left
    if (width <= 0f) return null
    val gap = lower.top - upper.bottom
    if (gap <= 0f) return null

    val half = width / 2f
    val neck = half * (GlassWeldNeckMin + (1f - GlassWeldNeckMin) * weld.coerceIn(0f, 1f))
    val top = upper.bottom - insetPx
    val bottom = lower.top + insetPx
    return GlassWeldShape(
        centreX = (left + right) / 2f,
        halfWidth = half,
        neckHalfWidth = neck,
        top = top,
        bottom = bottom,
        bend = (bottom - top) * 0.55f,
    )
}

/**
 * La **géométrie du pont**, pour deux formes **empilées verticalement** et qui se recouvrent
 * horizontalement.
 *
 * [upper] est la forme du dessus, [lower] celle du dessous, [weld] la sortie de
 * [glassWeldProgress], [insetPx] de combien le pont entre dans chaque forme.
 *
 * Le chemin est **fermé** et décrit la matière : un bord haut posé sur la forme du dessus, un bord
 * bas sur celle du dessous, et deux **cubiques concaves** sur les côtés — c'est leur courbure, et
 * non un raccord droit, qui donne le « col ». Renvoie un chemin **vide** quand il n'y a rien à
 * souder (voir [glassWeldShape]).
 */
fun glassWeldPath(upper: Rect, lower: Rect, weld: Float, insetPx: Float): Path {
    val path = Path()
    val shape = glassWeldShape(upper, lower, weld, insetPx) ?: return path
    val left = shape.centreX - shape.halfWidth
    val right = shape.centreX + shape.halfWidth
    val neckLeft = shape.centreX - shape.neckHalfWidth
    val neckRight = shape.centreX + shape.neckHalfWidth
    val middle = (shape.top + shape.bottom) / 2f

    path.moveTo(left, shape.top)
    // Le côté gauche : du bord haut, on rentre vers le col, puis on ressort vers le bord bas.
    path.cubicTo(left, shape.top + shape.bend, neckLeft, middle - shape.bend, neckLeft, middle)
    path.cubicTo(neckLeft, middle + shape.bend, left, shape.bottom - shape.bend, left, shape.bottom)
    path.lineTo(right, shape.bottom)
    path.cubicTo(right, shape.bottom - shape.bend, neckRight, middle + shape.bend, neckRight, middle)
    path.cubicTo(neckRight, middle - shape.bend, right, shape.top + shape.bend, right, shape.top)
    path.close()
    return path
}

/**
 * Le **liseré de la soudure** : les deux cubiques de côté, et rien d'autre.
 *
 * Le pont lui-même n'a pas d'arête à lui : ses bords haut et bas sont **dans** les deux formes, et
 * les souligner dessinerait la couture qu'on cherche justement à effacer. Ce qui doit se lire, c'est
 * le **col** — c'est lui qui marque la matière.
 */
fun glassWeldRimPath(upper: Rect, lower: Rect, weld: Float, insetPx: Float): Path {
    val path = Path()
    val shape = glassWeldShape(upper, lower, weld, insetPx) ?: return path
    val left = shape.centreX - shape.halfWidth
    val right = shape.centreX + shape.halfWidth
    val neckLeft = shape.centreX - shape.neckHalfWidth
    val neckRight = shape.centreX + shape.neckHalfWidth
    val middle = (shape.top + shape.bottom) / 2f

    path.moveTo(left, shape.top)
    path.cubicTo(left, shape.top + shape.bend, neckLeft, middle - shape.bend, neckLeft, middle)
    path.cubicTo(neckLeft, middle + shape.bend, left, shape.bottom - shape.bend, left, shape.bottom)
    path.moveTo(right, shape.top)
    path.cubicTo(right, shape.top + shape.bend, neckRight, middle - shape.bend, neckRight, middle)
    path.cubicTo(neckRight, middle + shape.bend, right, shape.bottom - shape.bend, right, shape.bottom)
    return path
}

/**
 * **Le pont de matière, peint sous le contenu.**
 *
 * À poser sur le **parent commun** des deux formes (la pile du bas) : le modificateur peint le pont
 * *derrière* tout ce que ce parent contient, donc derrière le compositeur et derrière la barre
 * d'onglets — celles-ci le recouvrent à moitié, et c'est ce recouvrement qui fait la continuité.
 *
 * [upper] et [lower] sont **mesurés** par l'appelant, dans les coordonnées de ce parent
 * (`boundsInParent`) : la soudure ne devine pas où sont les formes, elle les lit.
 */
fun Modifier.glassWeldBehind(
    surface: GlassSurface,
    upper: () -> Rect?,
    lower: () -> Rect?,
): Modifier = composed {
    val theme = LocalOnyxTheme.current
    val dark = theme.dark
    val level = LocalGlassFluidity.current?.level ?: GlassLevel.FULL
    val tint = glassTintColour(dark)
    val tintAlpha = glassTintAlpha(surface, level, dark, glassInks(surface, theme))
    val density = LocalDensity.current
    val weldPx = with(density) { GlassWeldDistanceDp.dp.toPx() }
    val insetPx = with(density) { GlassWeldInsetDp.dp.toPx() }
    val rimPx = with(density) { GlassEdgeDp.dp.toPx() }

    this.drawBehind {
        val a = upper() ?: return@drawBehind
        val b = lower() ?: return@drawBehind
        val weld = glassWeldProgress(b.top - a.bottom, weldPx)
        if (weld <= 0f) return@drawBehind
        val body = glassWeldPath(a, b, weld, insetPx)
        val rim = glassWeldRimPath(a, b, weld, insetPx)
        if (body.isEmpty) return@drawBehind
        // « Réduire les animations » ne l'éteint pas : la soudure **est** de la matière, pas un
        // mouvement. Ce qui se fige, c'est le déplacement qui la fait naître — la forme, elle, reste.
        drawPath(body, tint.copy(alpha = (tintAlpha + GlassWeldGain).coerceIn(0f, 1f)))
        drawPath(
            path = rim,
            color = Color.White.copy(alpha = (GlassEdgeAlpha * weld).coerceIn(0f, 1f)),
            style = Stroke(width = rimPx),
        )
    }
}
