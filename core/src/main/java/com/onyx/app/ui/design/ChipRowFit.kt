package com.onyx.app.ui.design

/**
 * La règle qui empêche une rangée de puces de **déborder** — et donc d'en couper une.
 *
 * Le défaut vu sur l'écran Code : à **police 1,3×**, la quatrième puce de filtre dépassait la
 * largeur de l'écran et il n'en restait qu'un **fragment de bord** au bord droit ; dans le
 * compositeur, la puce de branche se coupait au milieu d'un mot. Compose **ne dessine aucun
 * indicateur de défilement** : tout trait vu là est un morceau d'élément coupé. La seule correction
 * qui change quelque chose est de **faire tenir** la rangée.
 *
 * Même méthode que la rangée d'onglets : on **mesure** chaque libellé sous la police courante, on
 * **réduit** d'un demi-point jusqu'à ce que la rangée tienne, et jamais sous un plancher de
 * lisibilité. La recherche est une **fonction pure** — elle reçoit les largeurs mesurées pour une
 * taille donnée et rend ce qui est retenu : mesurable et éprouvable sans écran.
 */
object ChipRowFit {

    /** La taille nominale d'une puce : le **corps** du socle (15 sp, `.mtx2`). */
    const val BASE_SP = 15f

    /** Le plancher : en dessous, un libellé cesse d'être lisible — et c'est **11 sp**, pas moins. */
    const val FLOOR_SP = 11f

    /**
     * **On ne descend que sur l'échelle.** Les demi-points de la première version rendaient
     * 15,5 / 14,5 / 13,5 / 12,5 — quatre corps qui n'existent nulle part ailleurs dans l'app : la
     * rangée « tenait », mais elle ne parlait plus la même langue que le reste des écrans. La
     * descente suit donc les six corps du socle, et rien d'autre : 15 → 12 → 11.
     */
    val Rungs: List<Float> get() = OnyxSocle.TypeScale.map { it.value }

    /**
     * La largeur totale de la rangée : ses libellés mesurés, le dégagement de chaque puce (ses
     * marges intérieures, son icône, son chevron) et les écarts entre les puces.
     */
    fun rowWidth(labelWidthsDp: List<Float>, chipInsetDp: Float, gapDp: Float): Float {
        if (labelWidthsDp.isEmpty()) return 0f
        return labelWidthsDp.sum() +
            chipInsetDp * labelWidthsDp.size +
            gapDp * (labelWidthsDp.size - 1)
    }

    /**
     * La taille retenue pour que la rangée entière tienne dans [availableDp].
     *
     * [labelWidths] rend la largeur, en dp, de chaque libellé **pour une taille donnée** — c'est la
     * mesure du texte, injectée pour que la règle reste pure.
     */
    fun fontSize(
        availableDp: Float,
        chipInsetDp: Float,
        gapDp: Float,
        baseSp: Float = BASE_SP,
        floorSp: Float = FLOOR_SP,
        labelWidths: (Float) -> List<Float>,
    ): Float {
        if (availableDp <= 0f) return baseSp
        // Les marches essayées : la nominale, puis les corps du socle sous elle, jamais sous le plancher.
        val rungs = (listOf(baseSp) + Rungs).filter { it <= baseSp && it >= floorSp }.distinct().sortedDescending()
        rungs.forEach { size ->
            if (rowWidth(labelWidths(size), chipInsetDp, gapDp) <= availableDp) return size
        }
        return floorSp
    }

    /** La rangée tient-elle à cette taille ? Le plan le dit, plutôt que de le supposer. */
    fun fits(
        availableDp: Float,
        fontSizeSp: Float,
        chipInsetDp: Float,
        gapDp: Float,
        labelWidths: (Float) -> List<Float>,
    ): Boolean = rowWidth(labelWidths(fontSizeSp), chipInsetDp, gapDp) <= availableDp

    /**
     * Un libellé raccourci **aux frontières de segment**, jamais au milieu d'un mot.
     *
     * « code/session-neuve » devient « …/session-neuve » quand le premier ne tient pas : on retire
     * des **segments entiers** depuis la gauche, et la fin — la plus informative — reste lisible.
     * Rendu tel quel quand il tient ; réduit à son dernier segment quand même celui-là ne tient pas.
     * Un libellé sans séparateur n'est **jamais** coupé.
     */
    fun shortenAtBoundary(text: String, sep: Char, fits: (String) -> Boolean): String {
        if (fits(text)) return text
        val parts = text.split(sep)
        if (parts.size < 2) return text
        for (drop in 1 until parts.size) {
            val candidate = "…" + sep + parts.drop(drop).joinToString(sep.toString())
            if (fits(candidate)) return candidate
        }
        return parts.last()
    }

    /**
     * Le plan d'une rangée de filtres : la taille retenue, et si les compteurs y tiennent encore.
     *
     * Les compteurs (« Tout 2 ») sont utiles mais pas au prix d'une puce coupée : on les **garde**
     * tant que la rangée tient, et on les **laisse tomber** — les quatre puces restent, entières —
     * quand la largeur ne suffit plus. Jamais l'inverse : une puce visiblement tronquée est le
     * défaut, un compteur absent se voit sans mentir.
     */
    fun plan(
        availableDp: Float,
        chipInsetDp: Float,
        gapDp: Float,
        baseSp: Float = BASE_SP,
        floorSp: Float = FLOOR_SP,
        labels: (Float, Boolean) -> List<Float>,
    ): ChipRowPlan {
        for (counts in listOf(true, false)) {
            val size = fontSize(availableDp, chipInsetDp, gapDp, baseSp, floorSp) { s -> labels(s, counts) }
            if (fits(availableDp, size, chipInsetDp, gapDp) { s -> labels(s, counts) }) {
                return ChipRowPlan(fontSizeSp = size, showCounts = counts, fits = true)
            }
        }
        return ChipRowPlan(fontSizeSp = floorSp, showCounts = false, fits = false)
    }

    /**
     * Le plan d'une ligne de puces qui **ne perd jamais la moitié d'un mot**.
     *
     * Trois leviers, dans cet ordre, et on s'arrête dès que ça tient : on **réduit** la taille
     * (jamais sous le plancher), on **abrège aux frontières de segment** — d'abord un segment
     * entier préfixé d'une ellipse, puis le dernier segment seul —, et enfin on **retire les
     * ornements** des puces (icône et chevron, 32 dp chacune) avec le premier [insetsDp] qui laisse
     * tenir les libellés **entiers**. Un chevron en moins vaut mieux qu'un mot coupé ; le libellé,
     * lui, ne perd jamais un caractère.
     *
     * [insetsDp] donne les dégagements à essayer, du plus riche au plus sobre.
     */
    fun labelPlan(
        labels: List<String>,
        availableDp: Float,
        insetsDp: List<Float>,
        gapDp: Float,
        baseSp: Float = BASE_SP,
        floorSp: Float = FLOOR_SP,
        sep: Char = '/',
        width: (String, Float) -> Float,
    ): ChipLabelPlan {
        var fallback: ChipLabelPlan? = null
        for ((index, inset) in insetsDp.withIndex()) {
            val size = fontSize(availableDp, inset, gapDp, baseSp, floorSp) { s -> labels.map { width(it, s) } }
            val kept = labelNames(labels, size, availableDp, inset, gapDp, sep, width)
            val fits = rowWidth(kept.map { width(it, size) }, inset, gapDp) <= availableDp
            val plan = ChipLabelPlan(labels = kept, fontSizeSp = size, plain = index > 0, fits = fits)
            // Le premier dégagement qui tient gagne ; sinon on garde le plus riche comme repli.
            if (fits) return plan
            if (fallback == null) fallback = plan
        }
        // Rien ne tient, même sobre : l'appelant décide (défiler plutôt que couper), et il défile
        // avec les libellés **entiers** — la seule issue qui ne coupe pas un mot.
        return fallback ?: ChipLabelPlan(labels, baseSp, plain = false, fits = false)
    }

    /**
     * Les libellés d'une ligne de puces : les **entiers** tant qu'ils tiennent, puis **abrégés aux
     * frontières de segment** du plus large au plus étroit, puis réduits à leur **dernier
     * segment**. Jamais un caractère au milieu d'un mot, jamais une ellipse posée au hasard.
     */
    private fun labelNames(
        labels: List<String>,
        fontSizeSp: Float,
        availableDp: Float,
        chipInsetDp: Float,
        gapDp: Float,
        sep: Char,
        width: (String, Float) -> Float,
    ): List<String> {
        val needed: (List<String>) -> Float = { current ->
            rowWidth(current.map { width(it, fontSizeSp) }, chipInsetDp, gapDp)
        }
        if (needed(labels) <= availableDp) return labels
        var current = labels
        // Le plus large d'abord : on n'abrège que ce qu'il faut.
        val order = labels.indices.sortedByDescending { width(labels[it], fontSizeSp) }
        for (onlyLastSegment in listOf(false, true)) {
            for (index in order) {
                val others = current.filterIndexed { i, _ -> i != index }
                val next = when {
                    onlyLastSegment -> if (current[index].contains(sep)) {
                        current[index].substringAfterLast(sep)
                    } else {
                        current[index]
                    }
                    else -> shortenAtBoundary(current[index], sep) { candidate ->
                        rowWidth((others + candidate).map { width(it, fontSizeSp) }, chipInsetDp, gapDp) <= availableDp
                    }
                }
                current = current.toMutableList().also { it[index] = next }
                if (needed(current) <= availableDp) return current
            }
        }
        return current
    }
}

/** Ce qu'une rangée de filtres retient : sa taille, ses compteurs, et si elle tient sans défiler. */
data class ChipRowPlan(
    val fontSizeSp: Float,
    val showCounts: Boolean,
    val fits: Boolean,
)

/**
 * Ce qu'une ligne de puces retient : ses libellés, leur taille, si les ornements sont tombés — et
 * si la ligne tient **sans défiler**.
 */
data class ChipLabelPlan(
    val labels: List<String>,
    val fontSizeSp: Float,
    val plain: Boolean,
    val fits: Boolean,
)
