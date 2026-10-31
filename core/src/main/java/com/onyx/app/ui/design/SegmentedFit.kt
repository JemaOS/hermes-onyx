package com.onyx.app.ui.design

/**
 * La taille de texte d'un contrôle segmenté **qui doit tenir, jamais se couper**.
 *
 * Le défaut vu deux fois sur l'écran d'accueil : à **police 1,3× sur 360 dp**, la rangée de
 * segments demandait plus que la place disponible et Compose **découpait** la dernière case — il
 * n'en restait qu'un fragment de bordure, lu comme un trait parasite. Aucune indication de
 * défilement n'existe sous Compose : la seule correction qui change quelque chose est de **faire
 * tenir** la rangée.
 *
 * La règle est donc : la taille nominale tant que le libellé le plus large tient dans sa case,
 * puis on descend par demi-point jusqu'à ce qu'il tienne — et jamais au-dessous d'un plancher. La
 * recherche est une **fonction pure** : elle reçoit la largeur de la plus large étiquette pour une
 * taille donnée et rend la taille retenue, mesurable et éprouvable sans écran.
 */
object SegmentedFit {

    /** La taille des libellés d'un contrôle segmenté : le **titre de carte** du socle (16 sp). */
    const val BASE_SP = 16f

    /** Le plancher : en dessous, un libellé cesse d'être lisible — et c'est **11 sp**, pas moins. */
    const val FLOOR_SP = 11f

    /**
     * **On ne descend que sur l'échelle** : 16 → 15 → 12 → 11. Les demi-points d'avant produisaient
     * 15,5 / 14,5 / 13,5 — des corps qui n'existent dans aucun autre écran de l'app.
     */
    val Rungs: List<Float> get() = OnyxSocle.TypeScale.map { it.value }

    /** Le dégagement laissé au texte dans sa case (bords compris), en dp. */
    const val INSET_DP = 6f

    /**
     * La taille retenue pour que [widest] tienne dans [segmentWidthDp].
     *
     * [widest] rend la largeur, en dp, de l'étiquette la plus large **pour une taille donnée** —
     * c'est la mesure du texte, injectée pour que la règle reste pure.
     */
    fun fontSize(segmentWidthDp: Float, widest: (Float) -> Float): Float {
        if (segmentWidthDp <= 0f) return BASE_SP
        val rungs = (listOf(BASE_SP) + Rungs).filter { it <= BASE_SP && it >= FLOOR_SP }.distinct().sortedDescending()
        rungs.forEach { size -> if (widest(size) <= segmentWidthDp) return size }
        return FLOOR_SP
    }

    /** La place réellement offerte au texte d'une case : sa largeur moins le dégagement. */
    fun availableWidthDp(segmentWidthDp: Float): Float = (segmentWidthDp - INSET_DP).coerceAtLeast(1f)
}
