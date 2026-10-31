package com.onyx.app.data.net

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La géométrie de l'écran « Ordinateur », dans les deux états d'un pliable.
 *
 * **Fermé** (téléphone, ~411 dp) : une seule colonne — l'aperçu prend la place, les commandes se
 * rangent dessous.
 *
 * **Déplié** (grand écran, ~862 dp) : deux panneaux. L'aperçu occupe **son volet en entier** (il
 * est 16:9 : un volet large vaut mieux qu'une bande au milieu du vide) et les commandes restent
 * accessibles dans un volet à droite, à largeur **bornée** — jamais étirées sur la moitié de
 * l'écran pour faire joli.
 *
 * Le seuil est un seuil de place, pas un nom d'appareil : deux volets demandent de quoi poser un
 * aperçu utilisable *et* une colonne de commandes.
 */
object ComputerLayout {

    /** La place minimale pour un aperçu : en dessous, deux volets n'ont pas de sens. */
    val PREVIEW_MIN_WIDTH: Dp = 420.dp

    /** Le volet des commandes : borné, comme le volet de la liste d'iPadOS. */
    val CONTROLS_MIN_WIDTH: Dp = 300.dp
    val CONTROLS_MAX_WIDTH: Dp = 380.dp

    /** En dessous de cette largeur de fenêtre, un seul panneau. */
    val TWO_PANE_MIN_WIDTH: Dp = 720.dp

    fun twoPanes(windowWidth: Dp): Boolean =
        windowWidth >= TWO_PANE_MIN_WIDTH &&
            windowWidth - CONTROLS_MIN_WIDTH >= PREVIEW_MIN_WIDTH

    /** La largeur du volet des commandes pour cette fenêtre (bornée, jamais proportionnelle). */
    fun controlsWidth(windowWidth: Dp): Dp {
        if (!twoPanes(windowWidth)) return windowWidth
        return minOf(CONTROLS_MAX_WIDTH, windowWidth - PREVIEW_MIN_WIDTH)
            .coerceAtLeast(CONTROLS_MIN_WIDTH)
    }

    /** La largeur du volet de l'aperçu. */
    fun previewWidth(windowWidth: Dp): Dp =
        if (twoPanes(windowWidth)) windowWidth - controlsWidth(windowWidth) else windowWidth

    /**
     * La largeur du volet de l'aperçu en **pixels réels** — ce que l'app annonce au pont, et qui
     * décide du poids de chaque image.
     */
    fun previewWidthPx(paneWidth: Dp, density: Float): Int {
        val px = (paneWidth.value * density).toInt()
        return maxOf(1, px)
    }
}
