package com.onyx.app.ui.sidebar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.BadgeGeometry
import com.onyx.app.ui.design.OnyxSpace
import kotlin.math.abs

/**
 * La largeur du volet et ce qu'on a le droit d'en faire — **un mot ne se coupe jamais**.
 *
 * Le défaut relevé par le propriétaire, sur un téléphone étroit à la police du système 1,3× :
 * la tuile « Compétences » s'écrivait « Compétence » puis « s », et « Fichiers du serveur » se
 * repliait en deux lignes bancales. C'est la règle inverse de celle que le projet tient ailleurs :
 * une coupure au milieu d'un mot est un défaut, pas un débordement.
 *
 * Trois règles, ici, et elles se mesurent :
 *
 * 1. [tileLabelPlan] découpe un libellé **aux espaces** et jamais dans un mot ; s'il faut réduire
 *    la taille pour que le mot le plus long tienne, elle est réduite ([TILE_LABEL_MIN_RATIO] étant
 *    le plancher) — c'est la « taille adaptée selon la largeur réelle » ;
 * 2. [tileLabelWidth] dit la largeur **réellement** laissée au libellé : la tuile moins son
 *    rembourrage, son icône, son écart et son compteur. C'est elle qu'on mesure, pas la largeur du
 *    volet ;
 * 3. [projectChipsWidth] et [chipRows] disent où passent les puces : sous le bouton d'ajout il y a
 *    un filet d'air (jamais un chevauchement), et une puce de filtre qui ne tient pas descend d'une
 *    rangée au lieu de sortir de l'écran.
 */

/** La taille du libellé d'une tuile, en points de la maison — celle du texte de pied de page. */
internal val TileLabelIcon: Dp = 16.dp

/**
 * Le rond d'une tuile en édition (retirer / ajouter) : une **cible d'icône** de la bande du haut,
 * pas un compteur — son nom est trompeur, la valeur est celle d'un rond d'icône.
 */
internal val TileBadge: Dp = 18.dp

/**
 * **Le nombre de chiffres qu'un compteur de tuile peut écrire** : celui du plus grand nombre écrit
 * entier ([BadgeGeometry.MaxShown], « 999 »). La place réservée au libellé est donc calculée sur
 * **le pire cas réel**, jamais sur une largeur écrite à la main (elle valait 18 dp — juste pour un
 * chiffre, fausse dès le second).
 */
internal const val TILE_BADGE_DIGITS: Int = 3

/**
 * La place qu'un **compteur** de tuile prend, selon le nombre de chiffres qu'il écrit, écart
 * compris : la géométrie du compteur ([BadgeGeometry.reserveDp]) plus l'écart icône-libellé.
 * `digits = 0` n'occupe rien, et aucune valeur n'est écrite à la main.
 */
internal fun tileBadgeReserveDp(
    digits: Int,
    fontScale: Float = 1f,
): Dp = if (digits <= 0) {
    0.dp
} else {
    (BadgeGeometry.reserveDp(digits, fontScale = fontScale) + OnyxSpace.IconLabelGap.value).dp
}

/** Le plancher de la taille d'un libellé de tuile : on ne descend pas sous ce rapport. */
internal const val TILE_LABEL_MIN_RATIO = 0.78f

/** Les lignes qu'une tuile accepte avant que la taille ne cède : deux, comme la maison. */
internal const val TILE_LABEL_MAX_LINES = 2

/** Les paliers de la recherche de taille : 25 pas entre le plancher et la taille pleine. */
private const val TILE_LABEL_STEPS = 25

/** Le plan d'un libellé de tuile : ses lignes, et la taille de police qui va avec. */
internal data class TileLabelPlan(val lines: List<String>, val fontRatio: Float)

private val SPACES = Regex("\\s+")

/**
 * Les lignes d'un libellé de tuile, et la taille qu'il faut pour que **ses mots** tiennent.
 *
 * [measure] rend la largeur d'un texte à la **taille de base** (celle de la maison) ; les largeurs
 * sont ensuite mises à l'échelle du rapport choisi, la largeur d'un texte étant proportionnelle à sa
 * taille de police. [available] est la largeur réellement laissée au libellé ([tileLabelWidth]).
 *
 * La recherche tient en deux temps :
 *
 * 1. la taille part de la plus grande qui fasse tenir **le mot le plus long** sur une ligne — sans
 *    quoi le moteur de texte casserait le mot, quel que soit le nombre de lignes accordé —, puis
 *    descend par paliers jusqu'à ce que le libellé se range en [maxLines] lignes ;
 * 2. les lignes ne se coupent **qu'aux espaces**, et à la coupure la plus équilibrée qui tienne (deux
 *    lignes bancales sont ce que le propriétaire a vu : « Fichiers du serveur » se repliait mal).
 *
 * Si aucune taille au-dessus du plancher ne suffit, le libellé prend les lignes qu'il faut et c'est
 * la **hauteur** de la tuile qui cède : un mot ne se coupe jamais.
 */
internal fun tileLabelPlan(
    label: String,
    measure: (String) -> Float,
    available: Float,
    minRatio: Float = TILE_LABEL_MIN_RATIO,
    maxLines: Int = TILE_LABEL_MAX_LINES,
): TileLabelPlan {
    val words = label.trim().split(SPACES).filter { it.isNotEmpty() }
    if (words.isEmpty()) return TileLabelPlan(emptyList(), 1f)
    if (available <= 0f) return TileLabelPlan(listOf(label), 1f)

    // La taille de départ : celle où le mot le plus long tient, jamais plus.
    val widest = words.maxOf { measure(it) }
    val start = if (widest <= available) 1f else (available / widest).coerceIn(minRatio, 1f)

    for (step in 0..TILE_LABEL_STEPS) {
        val ratio = start - (start - minRatio) * step / TILE_LABEL_STEPS
        if (words.size == 1) return TileLabelPlan(listOf(words[0]), ratio)
        if (measure(label) * ratio <= available) return TileLabelPlan(listOf(label), ratio)
        balanced(words, measure, available, ratio)?.let { lines ->
            return TileLabelPlan(lines, ratio)
        }
    }
    // Dernier recours : la taille du plancher, le repli glouton, et la tuile qui grandit.
    return TileLabelPlan(wrap(words, measure, available, minRatio), minRatio)
}

/**
 * La coupure la plus **équilibrée** du libellé qui tienne en [TILE_LABEL_MAX_LINES] lignes à cette
 * taille, ou `null` si aucune ne tient.
 */
private fun balanced(
    words: List<String>,
    measure: (String) -> Float,
    available: Float,
    ratio: Float,
): List<String>? {
    var best: List<String>? = null
    var bestBalance = Float.MAX_VALUE
    for (cut in 1 until words.size) {
        val left = words.subList(0, cut).joinToString(" ")
        val right = words.subList(cut, words.size).joinToString(" ")
        val leftWidth = measure(left) * ratio
        val rightWidth = measure(right) * ratio
        if (leftWidth > available || rightWidth > available) continue
        val balance = abs(leftWidth - rightWidth)
        if (balance < bestBalance) {
            bestBalance = balance
            best = listOf(left, right)
        }
    }
    return best
}

/** Le repli glouton, mot à mot : chaque ligne est un groupe de mots entiers. */
private fun wrap(
    words: List<String>,
    measure: (String) -> Float,
    available: Float,
    ratio: Float,
): List<String> {
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (current.isNotEmpty() && measure(candidate) * ratio > available) {
            lines += current
            current = word
        } else {
            current = candidate
        }
    }
    if (current.isNotEmpty()) lines += current
    return lines
}

/**
 * Le rembourrage que la **liste** laisse autour de la grille de tuiles — le `contentPadding` de
 * [ConversationList] : sans lui, la largeur calculée dépasse de 6 dp par côté celle que la tuile a
 * vraiment, et le plan croit avoir de la place qu'il n'a pas.
 */
internal val TileGridInset: Dp = 6.dp

/**
 * La largeur qu'une tuile laisse **réellement** à son libellé, dans une grille dont on connaît la
 * largeur et le nombre de colonnes ([columns]) : la tuile (la grille divisée par ses colonnes, moins
 * ses rembourrages et les gouttières), moins son rembourrage, son icône, son écart icône-libellé et,
 * quand elle en porte un, son compteur.
 *
 * C'est la grille qui donne cette largeur : la mesurer dans la tuile elle-même dépendait de la
 * contrainte reçue (`BoxWithConstraints`), et une contrainte plus large que la tuile faisait tenir
 * « Fichiers du serveur » sur une seule ligne — coupée par le bord de la tuile, exactement le défaut
 * qu'on corrige.
 */
internal fun tileLabelWidthIn(
    gridWidth: Dp,
    badgeDigits: Int = 0,
    columns: Int = TILE_COLUMNS_MIN,
): Dp {
    // Le lot des polices bornait le nombre de colonnes à 1 (une grille ne se divise pas par zéro) :
    // l'apport est gardé, la largeur de tuile restant déléguée à [tileWidthIn], déjà là.
    val cols = columns.coerceAtLeast(1)
    val tile = tileWidthIn(gridWidth, cols)
    // La réserve de la pastille suit le **nombre de chiffres** qu'elle peut peindre : un compteur à
    // trois chiffres mange plus de place qu'un compteur à un (apport du lot du compteur, gardé).
    val badge = tileBadgeReserveDp(badgeDigits)
    return (tile - OnyxSpace.Snug * 2 - TileLabelIcon - OnyxSpace.IconLabelGap - badge)
        .coerceAtLeast(0.dp)
}

/** Le nombre de colonnes **minimal** d'une grille : deux, la disposition de la maison. */
internal const val TILE_COLUMNS_MIN: Int = 2

/** Le nombre de colonnes **maximal** : trois, et seulement sur un volet assez large pour les tenir. */
internal const val TILE_COLUMNS_MAX: Int = 3

/**
 * La largeur du volet la plus étroite que l'app sert, **deux colonnes** : une fenêtre de 360 dp, moins
 * la marge de la liste. C'est la référence de la règle des colonnes — la largeur sur laquelle les
 * libellés ont été vérifiés.
 */
internal val TileGridNarrow: Dp = 336.dp

/**
 * **La largeur d'une tuile**, pour cette grille et ce nombre de colonnes : la grille, moins ses deux
 * rembourrages, moins une gouttière entre chaque paire de colonnes.
 */
internal fun tileWidthIn(gridWidth: Dp, columns: Int): Dp =
    ((gridWidth - OnyxSpace.Snug * 2 - OnyxSpace.Hair * (columns - 1)) / columns).coerceAtLeast(0.dp)

/**
 * **Le nombre de colonnes de la grille pour cette largeur** — deux ou trois, jamais une seule.
 *
 * Trois colonnes **seulement** quand la tuile y laisse encore assez de place au libellé pour que le
 * mot le plus large de la grille tienne **au plancher de taille** ([TileLabelFit.FLOOR_FRACTION]) —
 * et jamais moins que ce qu'une tuile laisse déjà sur le volet le plus étroit à deux colonnes. Une
 * troisième colonne qui appauvrirait le libellé ne s'obtient donc pas : c'est la règle « un mot ne se
 * coupe jamais » qui décide du nombre de colonnes, pas l'inverse.
 *
 * La fonction est **pure** (des largeurs mesurées entrent, un nombre sort) : elle se vérifie sans
 * écran, et un test la couvre sur les quatre largeurs du lot (360, 411, pliable déplié, tablette).
 */
internal fun tileColumnsFor(gridWidth: Dp, widestWord: Dp): Int {
    val floorWidth = widestWord * TileLabelFit.FLOOR_FRACTION
    val narrowest = tileLabelWidthIn(TileGridNarrow, badgeDigits = TILE_BADGE_DIGITS, columns = TILE_COLUMNS_MIN)
    val need = maxOf(floorWidth, narrowest)
    val asThree = tileLabelWidthIn(gridWidth, badgeDigits = TILE_BADGE_DIGITS, columns = TILE_COLUMNS_MAX)
    return if (asThree >= need) TILE_COLUMNS_MAX else TILE_COLUMNS_MIN
}

/**
 * **Le nombre de colonnes de la grille de tuiles.** Deux, sauf quand la police du système est
 * grande : à 1,3× et sur un téléphone de 360 dp, deux colonnes laissent 86,8 dp au libellé, et
 * « Compétences » en demande 88 à **11 sp** — le plancher de lisibilité de l'app. Une taille ne se
 * négocie pas pour faire tenir un mot : c'est la **mise en page** qui cède, et la grille passe à une
 * colonne (la grille des bots le fait déjà, `botGridColumns`).
 */
internal fun tileColumns(fontScale: Float): Int = if (fontScale >= 1.3f) 1 else 2

/**
 * La même largeur, vue du **volet** : la grille est un élément de la liste, qui porte son propre
 * `contentPadding` ([TileGridInset] de chaque côté).
 *
 * Vérifiée sur l'appareil : volet de 340 dp (téléphone de 411 dp) → tuile de 150 dp, libellé de
 * **102 dp**, ce que l'arbre rend à `266 px` à 420 dpi (2 px d'écart, la ligne la plus large étant
 * toujours un peu plus courte que la place qui lui est donnée).
 */
internal fun tileLabelWidth(panelWidth: Dp, badgeDigits: Int = 0): Dp =
    tileLabelWidthIn(panelWidth - TileGridInset * 2, badgeDigits)

/**
 * La largeur laissée aux puces de projets : la fenêtre, moins le bouton d'ajout et **son filet
 * d'air**. Le filet est ce qui manquait : les puces défilaient jusqu'au bord du bouton, et une puce
 * à moitié coupée contre le rond du « + » se lit comme un chevauchement.
 */
internal fun projectChipsWidth(available: Float, button: Float, gutter: Float): Float =
    (available - button - gutter).coerceAtLeast(0f)

/** Le filet d'air entre les puces de projets et le bouton d'ajout : un jeton de la maison. */
internal val ProjectChipsGutter: Dp = OnyxSpace.Half

/**
 * Les rangées d'un groupe de puces qui passe à la ligne (`FlowRow`), simulées : chaque puce est
 * placée entière dans la rangée courante si elle y tient avec l'écart, sinon elle descend.
 *
 * Rend, pour chaque rangée, les **index** des puces qu'elle porte. Une puce plus large que la
 * rangée occupe la sienne — elle déborde alors d'un seul côté, jamais coupée par une voisine.
 */
internal fun chipRows(widths: List<Float>, available: Float, gap: Float): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    var current = mutableListOf<Int>()
    var used = 0f
    widths.forEachIndexed { index, width ->
        val need = if (current.isEmpty()) width else used + gap + width
        if (current.isNotEmpty() && need > available) {
            rows += current
            current = mutableListOf(index)
            used = width
        } else {
            current += index
            used = need
        }
    }
    if (current.isNotEmpty()) rows += current
    return rows
}
