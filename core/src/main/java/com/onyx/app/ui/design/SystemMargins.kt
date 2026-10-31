package com.onyx.app.ui.design

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Les marges du système, calculées **à un seul endroit** pour toute l'app.
 *
 * L'app est bord à bord : la fenêtre va sous les bandes du système. Un écran sans barre doit donc
 * laisser libre la **bande haute** (barre d'état) et la **bande basse** (barre de navigation ou
 * geste), puis poser son contenu après elles.
 *
 * Le défaut classique — et celui qu'on répare ici — est d'écrire un rembourrage local
 * (`16.dp` du bord de l'écran, comme si la bande du système n'existait pas) : une croix de
 * fermeture se retrouve alors **dans** la barre d'état, et une légende de fichier **sur** la zone
 * de navigation. Le calcul ne se refait plus écran par écran : lecteurs plein écran (image, vidéo,
 * PDF, aperçu de pièce jointe), barres et en-têtes passent tous par ici.
 *
 * Les fonctions de [SystemMargins] sont pures (des pixels, une densité) : elles se prouvent par un
 * test de verrouillage, sans appareil. Les deux extensions de [Modifier] sont la façon dont un
 * écran s'en sert.
 */
object SystemMargins {

    /**
     * La marge nette qu'on laisse toujours entre le contenu et la bande du système, en dp.
     *
     * Elle est au moins l'espacement du reste de l'app : jamais moins de 8 dp, même quand la
     * bande du système est absente. C'est ce qui garantit que rien n'est **collé** au bord.
     */
    const val ClearanceDp: Int = 8

    /**
     * Le décalage d'un bord, en pixels : la hauteur de la bande du système, plus la marge nette.
     *
     * Avec [insetPx] à zéro — aucune bande — il reste la marge nette seule : le contenu n'est
     * jamais collé au bord de l'écran.
     */
    fun edgeOffsetPx(insetPx: Int, density: Float, clearanceDp: Int = ClearanceDp): Int =
        insetPx + Math.round(clearanceDp * density)

    /**
     * Le haut du contenu sous la bande haute, en pixels : la croix d'un lecteur plein écran se
     * pose là. Sans bande (aucune barre d'état), c'est la marge nette seule.
     */
    fun topPx(statusBarPx: Int, density: Float, clearanceDp: Int = ClearanceDp): Int =
        edgeOffsetPx(statusBarPx, density, clearanceDp)

    /**
     * Le bas du contenu au-dessus de la bande basse, en pixels : la légende d'un fichier et tout
     * autre élément de pied se posent là. Sans bande, c'est la marge nette seule.
     */
    fun bottomPx(navigationBarPx: Int, density: Float, clearanceDp: Int = ClearanceDp): Int =
        edgeOffsetPx(navigationBarPx, density, clearanceDp)

    /**
     * Vrai si un contenu dont le bord est à [contentEdgePx] du bord de l'écran ne mord pas sur la
     * bande du système qui occupe [insetPx] : il doit rester au moins la marge nette après elle.
     */
    fun clearsSystemBand(
        contentEdgePx: Int,
        insetPx: Int,
        density: Float,
        clearanceDp: Int = ClearanceDp,
    ): Boolean = contentEdgePx >= edgeOffsetPx(insetPx, density, clearanceDp)

    // ---- Le même calcul, en dp -------------------------------------------------------------
    //
    // Une barre (celle des onglets, l'en-tête d'un écran, la rangée d'actions d'une barre basse)
    // se place en dp, pas en pixels : `Modifier.padding` la veut ainsi. Les quatre fonctions qui
    // suivent sont **la même règle**, exprimée dans cette unité ; c'est `SafeBarSpacing` qui les
    // appelait déjà, et c'est désormais ici qu'elles vivent — un seul endroit compte.

    /**
     * Le décalage d'un bord, en dp : la bande du système, plus la marge nette.
     *
     * Un encart négatif — qui n'existe pas — ne rabote jamais la marge.
     */
    fun edgeOffsetDp(insetDp: Int, clearanceDp: Int = ClearanceDp): Int =
        insetDp.coerceAtLeast(0) + clearanceDp.coerceAtLeast(0)

    /**
     * Le haut d'une **barre**, en dp sous le haut de la fenêtre : la bande haute seule.
     *
     * La différence avec [topPx] est voulue : une barre dessine déjà son propre rembourrage
     * intérieur, le vide de la barre d'état est donc le sien et on n'ajoute rien. Un contenu qui
     * n'a pas ce rembourrage — la croix d'un lecteur plein écran — prend [topPx], marge nette
     * comprise.
     */
    fun barTopDp(systemTopInsetDp: Int): Int = systemTopInsetDp.coerceAtLeast(0)

    /**
     * Le bas d'une **barre**, en dp sous le bas de la fenêtre : la bande basse, plus l'espace
     * visuel de la maison ([gapDp], la marge nette par défaut) pour qu'elle ne paraisse jamais
     * collée au bord.
     */
    fun barBottomDp(systemBottomInsetDp: Int, gapDp: Int = ClearanceDp): Int =
        edgeOffsetDp(systemBottomInsetDp, gapDp)

    /** Le verdict d'une barre, en dp : son bord ne mord pas sur la bande du système. */
    fun clearsBand(edgeDp: Int, insetDp: Int): Boolean = edgeDp >= insetDp.coerceAtLeast(0)
}

/**
 * La bande haute que tout écran sans barre laisse libre : la barre d'état, plus la marge nette.
 *
 * À poser en tête d'un écran bord à bord, **avant** son propre rembourrage : le contenu descend
 * alors sous la barre d'état au lieu de s'y superposer.
 */
@Composable
fun Modifier.statusBarClearance(clearance: Dp = SystemMargins.ClearanceDp.dp): Modifier {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return padding(top = top + clearance)
}

/**
 * La bande basse : la zone de navigation (geste ou trois boutons), plus la marge nette.
 *
 * À poser au pied d'un écran bord à bord, **avant** son propre rembourrage : la légende et les
 * commandes du bas se posent au-dessus de la zone de navigation au lieu d'y tomber.
 */
@Composable
fun Modifier.navigationBarClearance(clearance: Dp = SystemMargins.ClearanceDp.dp): Modifier {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return padding(bottom = bottom + clearance)
}
