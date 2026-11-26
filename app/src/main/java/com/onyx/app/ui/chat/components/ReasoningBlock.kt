package com.onyx.app.ui.chat.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxSpace

/**
 * Les règles **pures** du bloc de réflexion — celles qui se mesurent hors de Compose.
 *
 * Le bloc a été redessiné parce que, tel qu'il était, il **criait** : un pavé de verre plein,
 * détaché du fond papier, un mur de texte brut en anglais sans un mot pour dire ce que c'était, et
 * pas de fin. Ce fichier porte les décisions qui tiennent le nouveau dessin, pour qu'elles soient
 * vérifiables par un test JVM — la mise en page qui les emploie est dans `MessageRow.kt`.
 *
 * Trois familles de règles vivent ici :
 *
 * 1. **la forme** — un filet vertical à gauche ([RailWidth], le filet des replis de la maison) et
 *    le texte en retrait ([TextIndent]), sur la surface de la page ; **aucun aplat plein**, donc
 *    aucun pavé froid qui se détache du papier ;
 * 2. **la longueur** — au-delà de [CollapsedLines] lignes, le bloc se tronque et porte son
 *    contrôle ([reasoningLineBudget], [reasoningIsLong]) : jamais un mur de dix écrans ;
 * 3. **la lisibilité** — le plancher d'encre d'un texte ([TextContrastFloor]) et la détection de
 *    langue ([reasoningLooksForeign]) qui déclenche la mention « brut, non traduit ».
 *
 * Rien de tout cela n'est un nombre choisi au cas par cas : [RailWidth] est le filet des replis
 * (`FoldBody`), [TextIndent] est la marche `Snug` de l'échelle d'espacement, et [CollapsedLines]
 * se compte en lignes du corps secondaire.
 */
internal object ReasoningBlock {

    /**
     * **Le filet vertical à gauche : 2 dp.** C'est le filet des replis de la maison (`FoldBody`),
     * celui qui marque déjà le détail d'un pas d'outil — un seul geste de lecture pour les deux
     * contenus secondaires du fil. Il n'a **pas** la couleur d'un cadre ni l'épaisseur d'une carte :
     * c'est un repère, pas une bordure.
     */
    val RailWidth: Dp = 2.dp

    /**
     * **Le retrait du texte derrière le filet : 12 dp** ([OnyxSpace.Snug]). Le texte respire à
     * droite du filet sans s'en éloigner d'une marche entière.
     */
    val TextIndent: Dp = OnyxSpace.Snug

    /**
     * **Le nombre de lignes montrées avant « Voir tout ».** Six : trois fois le corps secondaire
     * d'un paragraphe court, la moitié de ce qu'une vue tient. Au-delà, le bloc se tronque.
     */
    const val CollapsedLines: Int = 6

    /**
     * **L'interligne du corps secondaire**, en multiples du corps. 1,5 : un texte long, mis en
     * retrait et lu en petit corps, a besoin d'air entre ses lignes — c'est ce qui le distingue
     * d'un bloc monospace, dont on ne veut **jamais** ici (le monospace est réservé aux
     * identifiants).
     */
    const val LineHeightRatio: Float = 1.5f

    /**
     * **La largeur d'une ligne, en caractères**, au corps des métas sur la colonne du fil : sert à
     * **estimer** le nombre de lignes qu'un texte occupera une fois replié par Compose. La valeur
     * est celle qu'on relève sur une ligne pleine à 411 dp, filet et retrait déduits.
     */
    const val CharsPerLine: Int = 44

    /** Le plancher de contraste WCAG d'un texte : 4,5:1, mesuré. */
    const val TextContrastFloor: Double = 4.5

    /**
     * **La bande basse où flotte la pastille « aller au dernier message ».** Le fil doit réserver
     * au moins cette hauteur sous sa dernière ligne, sinon la pastille se pose **par-dessus** le
     * contenu — c'est le défaut mesuré : la pastille masquait le texte.
     *
     * La hauteur est celle de la pastille ([OnyxSpace.ThumbnailSize], la cible du pouce), plus un
     * écart ([OnyxSpace.Half]) qui sépare franchement le contenu du bord de la pastille.
     */
    object FloatBand {
        /** La hauteur de la pastille flottante. */
        val PillHeight: Dp = OnyxSpace.ThumbnailSize

        /** L'écart entre le haut de la bande et la dernière ligne du fil. */
        val Gap: Dp = OnyxSpace.Half

        /** **La réserve minimale** que le fil doit poser sous sa dernière ligne. */
        val MinReserve: Dp = PillHeight + Gap
    }
}

/**
 * **Le nombre de lignes estimé** d'un texte au corps des métas du fil.
 *
 * Chaque ligne explicite compte pour au moins une ligne, et se poursuit au-delà de
 * [charsPerLine] caractères. C'est une **estimation** — Compose seul connaît le repli réel —, mais
 * elle suffit à décider du contrôle, et la troncature réelle (`maxLines`) tient de toute façon le
 * bloc : même une estimation fausse ne laisse pas passer un mur de texte.
 */
internal fun reasoningLineBudget(
    text: String,
    charsPerLine: Int = ReasoningBlock.CharsPerLine,
): Int {
    if (text.isEmpty()) return 0
    val width = charsPerLine.coerceAtLeast(1)
    return text.lineSequence().sumOf { line ->
        if (line.isEmpty()) 1 else (line.length + width - 1) / width
    }
}

/** Le bloc dépasse-t-il la longueur montrée de but en blanc ([ReasoningBlock.CollapsedLines]) ? */
internal fun reasoningIsLong(text: String): Boolean =
    reasoningLineBudget(text) > ReasoningBlock.CollapsedLines

/** Les mots du français, sans accents, dans le désordre : une proportion suffit à conclure. */
private val FrenchMarkers = setOf(
    "le", "la", "les", "un", "une", "des", "je", "tu", "il", "elle", "nous", "vous", "ils",
    "est", "sont", "pas", "que", "qui", "dans", "pour", "avec", "sur", "mais", "donc", "ainsi",
    "ce", "cette", "plus", "sans", "tout", "comme", "etre", "fait", "doit", "peut", "ici",
)

/** Les mots de l'anglais, même rôle. */
private val EnglishMarkers = setOf(
    "the", "a", "an", "is", "are", "was", "were", "to", "of", "and", "or", "in", "on",
    "for", "with", "that", "this", "it", "i", "you", "we", "they", "not", "be", "have",
    "will", "would", "could", "should", "can", "as", "at", "by", "from", "here", "need",
)

/** Les caractères qui n'existent qu'en français (accents, cédille, ligature). */
private const val FrenchDiacritics = "àâäéèêëîïôöùûüçœÀÂÄÉÈÊËÎÏÔÖÙÛÜÇŒ"

/**
 * **Le texte du raisonnement est-il dans une autre langue que l'app ?**
 *
 * Sert la mention « brut, non traduit » : sans elle, un raisonnement en anglais posé au milieu
 * d'une interface française se lit comme **de l'interface mal traduite**, pas comme le contenu
 * brut du modèle. L'app doit donc **dire** quand le texte n'est pas à elle.
 *
 * L'heuristique est **conservatrice** : un texte de moins de six mots n'est jamais signalé (on ne
 * conclut pas sur « ok » ou « Done »), et il faut un faisceau — des marqueurs de l'autre langue en
 * majorité, ou des diacritiques francophones pour un texte anglais. Un faux négatif laisse un texte
 * étranger sans mention ; un faux positif collerait « brut, non traduit » sur du français, ce qui
 * serait pire. Le seuil penche donc du côté du silence.
 *
 * @param appIsFrench l'app parle-t-elle français ? (voir `OnyxLocalization.isFrench()`).
 */
internal fun reasoningLooksForeign(text: String, appIsFrench: Boolean): Boolean {
    if (text.isBlank()) return false
    val lower = text.lowercase()
    val words = Regex("[a-zà-ÿ']+").findAll(lower).map { it.value }.toList()
    if (words.size < 6) return false
    val french = words.count { it in FrenchMarkers }
    val english = words.count { it in EnglishMarkers }
    val hasFrenchMarks = lower.any { it in FrenchDiacritics }
    return if (appIsFrench) {
        // Français : on signale l'anglais caractérisé, jamais un texte neutre.
        !hasFrenchMarks && english >= 2 && english > french
    } else {
        // Anglais : on signale le français dès qu'il se trahit (diacritique ou marqueurs).
        hasFrenchMarks || (french >= 2 && french > english)
    }
}

/**
 * **L'aire de recouvrement de deux rectangles, en px².** Zéro quand ils ne se touchent pas, ou
 * quand ils se touchent par un bord (une aire nulle n'est pas un recouvrement).
 *
 * Le calcul vit ici, hors de Compose, pour qu'un test puisse **mordre** : c'est lui qui vérifie
 * qu'aucun élément flottant ne recouvre le bloc de réflexion.
 */
internal fun overlapAreaPx2(
    aLeft: Float,
    aTop: Float,
    aRight: Float,
    aBottom: Float,
    bLeft: Float,
    bTop: Float,
    bRight: Float,
    bBottom: Float,
): Float {
    val width = minOf(aRight, bRight) - maxOf(aLeft, bLeft)
    val height = minOf(aBottom, bBottom) - maxOf(aTop, bTop)
    return if (width > 0f && height > 0f) width * height else 0f
}
