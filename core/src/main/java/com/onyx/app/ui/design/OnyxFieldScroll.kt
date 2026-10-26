package com.onyx.app.ui.design

/**
 * Le défilement horizontal d'un champ à une seule rangée.
 *
 * Une valeur plus longue que le champ se cale à droite au repos — c'est le regard du modèle —
 * mais dès qu'on l'édite, le texte doit défiler pour que le curseur reste dans la fenêtre visible.
 * Le calcul vit ici, hors du dessin: les pixels d'une rangée étroite, une police agrandie et un
 * collage en fin de texte se mesurent sans monter un écran.
 */
object OnyxFieldScroll {

    /**
     * L'origine du texte au repos, en pixels: sa fin est visible.
     *
     * Une valeur alignée à droite montre sa fin, son début se coupe à gauche; une valeur alignée à
     * gauche part de zéro, comme le champ ordinaire.
     */
    fun rest(viewport: Int, content: Int, alignEnd: Boolean): Int =
        if (alignEnd) viewport - content else 0

    /**
     * L'origine du texte (bord gauche, en pixels) qui garde le curseur visible.
     *
     * [previous] est l'origine courante: on ne bouge que si le curseur sort de la fenêtre, et on ne
     * dépasse jamais le début ni la fin de la valeur — c'est ce qui permet de lire toute la valeur en
     * déplaçant le curseur d'un bout à l'autre.
     */
    fun origin(
        viewport: Int,
        content: Int,
        caretLeft: Int,
        caretRight: Int,
        previous: Int,
        alignEnd: Boolean = true,
        margin: Int = 0,
    ): Int {
        val lower = minOf(0, viewport - content)
        val upper = if (alignEnd) maxOf(0, viewport - content) else 0
        val desired = when {
            previous + caretRight + margin > viewport -> viewport - margin - caretRight
            previous + caretLeft - margin < 0 -> margin - caretLeft
            else -> previous
        }
        return desired.coerceIn(lower, upper)
    }
}
