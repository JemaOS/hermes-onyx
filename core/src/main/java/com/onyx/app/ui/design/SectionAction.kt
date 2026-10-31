package com.onyx.app.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * **L'action d'un en-tête de section** — une seule pièce, une seule géométrie, partout.
 *
 * Le défaut relevé par le propriétaire : le « + » de **BOTS** touchait la première rangée de cartes
 * et celui de **SALONS** recouvrait la première ligne de salon (l'horodatage passait juste sous le
 * bouton) ; et les deux « + » ne se ressemblaient pas — un cercle d'un côté, un carré arrondi de
 * l'autre. Deux causes, et une seule réponse :
 *
 *  - **l'écart.** Un en-tête de section fait exactement la hauteur de son bouton ([Size]) et ne
 *    portait **aucun** rembourrage bas : l'action finissait sur le premier pixel du contenu. La
 *    maison met [Gap] entre un intitulé de section et ce qu'il annonce ([OnyxSocle.Tight], le
 *    rembourrage bas de l'intitulé « CONVERSATIONS ») ; un en-tête qui porte une action prend le
 *    même écart, et son bouton **ne peut plus** toucher la ligne suivante ;
 *  - **la forme et la taille.** Le rond de la maison est [GlassCircleButton] avec
 *    [RoundButtonShape] (un vrai cercle, voir [RoundButtonShape]) ; deux tailles de bouton et deux
 *    tailles de glyphe se croisaient d'un en-tête à l'autre (44 et 48 dp, 16 et 17 dp). Elles sont
 *    ramenées ici à [Size] et [Icon] : même forme, même taille, même épaisseur de trait, même
 *    alignement — le libellé et le bouton sont centrés sur la même ligne.
 *
 * Les valeurs sont **publiques et uniques** : [SectionActionGeometryTest] les relit et échoue si un
 * en-tête repasse sous [MinGap], ou si deux actions de section n'ont plus la même forme ou la même
 * taille.
 */
object SectionAction {

    /** Le côté du rond d'une action de section : la cible tactile de la maison (44 dp). */
    val Size: Dp = 44.dp

    /** La taille du glyphe dans ce rond : 17 dp, la même pour tous les « + » et le crayon. */
    val Icon: Dp = 17.dp

    /** L'écart **sous** l'en-tête, entre le bas de l'action et le haut du contenu : 8 dp. */
    val Gap: Dp = 8.dp

    /**
     * Le plancher de la maison : sous cette valeur, l'action **touche** ce qui suit. C'est le même
     * pas que [Gap] — un écart plus serré n'est plus un écart, c'est un contact.
     */
    val MinGap: Dp = Gap

    /**
     * L'écart réel entre le bas de l'action et le haut du contenu, en dp.
     *
     * L'action est **centrée** dans la ligne d'en-tête ([Alignment.CenterVertically]) : quand la
     * ligne est plus haute que le bouton, la moitié de la différence s'ajoute sous le bouton. Le
     * rembourrage bas, lui, s'ajoute toujours.
     */
    fun gap(headerMinHeight: Dp, actionSize: Dp = Size, bottomPadding: Dp = Gap): Float =
        bottomPadding.value + max(0f, (headerMinHeight.value - actionSize.value) / 2f)

    /** Ce qui reste de l'action sur le contenu quand les deux se recouvrent, en dp (0 = rien). */
    fun overlap(actionBottomDp: Float, contentTopDp: Float): Float =
        max(0f, actionBottomDp - contentTopDp)
}

/**
 * Le bouton d'une action de section : [GlassCircleButton] à la taille et au glyphe de la maison.
 *
 * C'est le **seul** chemin admis pour une action d'en-tête de section — un `GlassCircleButton`
 * écrit à la main dans un en-tête rouvrirait la porte aux deux tailles et aux deux formes.
 */
@Composable
fun SectionActionButton(
    @DrawableRes symbol: Int,
    label: String,
    onClick: () -> Unit,
) {
    GlassCircleButton(
        symbol = symbol,
        label = label,
        size = SectionAction.Size,
        iconSize = SectionAction.Icon,
        onClick = onClick,
    )
}
