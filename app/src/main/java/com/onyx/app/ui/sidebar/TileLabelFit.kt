package com.onyx.app.ui.sidebar

import kotlin.math.roundToInt

/**
 * La taille d'un libellé de tuile du volet **qui doit tenir, jamais se couper au milieu d'un mot**.
 *
 * Relevé sur la vidéo du propriétaire : dans le volet, la tuile « Compétences » se dessinait
 * « Compétence / s » — le mot était coupé en deux parce que la tuile était un peu plus étroite que
 * le mot. Ce n'est pas un défaut de la grille mais de la **largeur offerte au mot** : sous Compose,
 * un mot plus large que sa ligne est découpé caractère par caractère, et aucune propriété ne
 * l'interdit. La seule correction qui change quelque chose est de **faire tenir le mot**.
 *
 * La règle est donc : la taille nominale tant que le mot le plus large de la grille tient dans la
 * largeur du libellé, sinon on descend — proportionnellement, parce que la largeur d'un texte croît
 * avec sa taille — et jamais sous un plancher, pour qu'un libellé reste lisible. La même taille est
 * retenue pour **toutes** les tuiles de la grille : une grille dont les libellés changent de taille
 * d'une case à l'autre se lit comme un défaut.
 *
 * La recherche est une **fonction pure** — elle reçoit des largeurs mesurées, rend la taille
 * retenue — donc mesurable et éprouvable sans écran.
 */
internal object TileLabelFit {

    /** Le plancher : une fraction de la taille nominale. En dessous, le libellé ne se lit plus. */
    const val FLOOR_FRACTION = 0.85f

    /**
     * La taille retenue, en points, pour que [widestWordDp] tienne dans [labelWidthDp].
     *
     * [widestWordDp] est la largeur du mot le plus large de la grille **à la taille nominale**
     * ([baseSp]) — c'est la mesure du texte, injectée pour que la règle reste pure.
     */
    fun fontSize(baseSp: Float, labelWidthDp: Float, widestWordDp: Float): Float {
        if (baseSp <= 0f) return 0f
        if (widestWordDp <= 0f || labelWidthDp <= 0f) return baseSp
        if (widestWordDp <= labelWidthDp) return baseSp
        val fit = baseSp * (labelWidthDp / widestWordDp)
        val floor = baseSp * FLOOR_FRACTION
        return fit.coerceAtLeast(floor)
    }

    /**
     * La largeur du mot le plus large d'une étiquette, à une taille donnée.
     *
     * La largeur d'un texte croît avec sa taille : une seule mesure à la taille nominale suffit donc
     * à connaître toutes les autres, sans remesurer à chaque pas.
     */
    fun wordWidthAt(baseSp: Float, measuredAtBaseSp: Float, sizeSp: Float): Float =
        if (baseSp <= 0f) 0f else round2(measuredAtBaseSp * sizeSp / baseSp)

    /** La largeur du libellé d'une tuile : la case moins l'icône, son écart et ses marges. */
    fun labelWidthDp(cellWidthDp: Float, iconDp: Float, iconGapDp: Float, paddingDp: Float): Float =
        (cellWidthDp - iconDp - iconGapDp - paddingDp * 2f).coerceAtLeast(1f)

    /**
     * La largeur d'une case de la grille : deux tuiles par rangée, leurs marges et l'écart entre
     * elles.
     */
    fun cellWidthDp(gridWidthDp: Float, paddingDp: Float, gapDp: Float): Float =
        ((gridWidthDp - paddingDp * 2f - gapDp) / 2f).coerceAtLeast(1f)

    private fun round2(value: Float): Float = (value * 100f).roundToInt() / 100f
}
