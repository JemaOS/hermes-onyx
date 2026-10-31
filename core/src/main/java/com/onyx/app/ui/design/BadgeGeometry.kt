package com.onyx.app.ui.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * **La géométrie d'un compteur — la pastille d'un nombre.**
 *
 * Le défaut relevé par le propriétaire sur la barre d'onglets : « la partie nombre de flux est
 * coupé ». Trois causes, et c'est tout ce fichier :
 *
 * 1. **Aucun rembourrage vertical** (1 dp, mesuré) : la boîte du texte venait au contact de la boîte
 *    peinte, et le moindre arrondi mordait dedans ;
 * 2. **Aucune largeur minimale calculée** : la pastille ne se définissait que par son texte, si bien
 *    qu'un chiffre seul donnait une forme qui dépendait du glyphe, et qu'une **largeur écrite à la
 *    main** (18 dp pour le compteur d'une tuile) restait fausse dès le deuxième chiffre ;
 * 3. **Un ancrage hors de sa zone** : la pastille était poussée de 7 dp à droite et de 4 dp vers le
 *    haut — au-delà de sa cellule — dans une colonne d'onglet **découpée en capsule**, donc coupée.
 *
 * La règle, ici, est une **fonction pure** : elle reçoit les boîtes mesurées de la police réellement
 * rendue ([TextStyle] du thème, échelle système comprise) et rend des chiffres en dp. Elle est donc
 * vérifiée sans appareil, et le nombre annoncé dans un rapport est le nombre que le dessin emploie.
 *
 * **Ce qui est interdit et ce qui est mesuré** : la largeur peinte vaut toujours *au moins* la
 * largeur du texte plus deux [PadHorizontal] ; la hauteur peinte, *au moins* la hauteur du texte plus
 * deux [PadVertical] ; et un seul chiffre donne un **carré optique** (jamais une forme plus étroite
 * que haute, jamais une largeur choisie à la main).
 */
object BadgeGeometry {

    /**
     * **Le rembourrage horizontal** : 5 dp de chaque côté du chiffre.
     *
     * C'est la valeur que la barre portait déjà — elle est conservée, parce qu'elle est au-dessus du
     * minimum demandé (2 dp) et qu'elle donne à un nombre de trois chiffres la forme d'une vraie
     * pastille plutôt que d'un rectangle collé.
     */
    val PadHorizontal: Dp = 5.dp

    /**
     * **Le rembourrage vertical** : 2 dp au-dessus et 2 dp au-dessous du chiffre.
     *
     * Il valait **1 dp** — c'est la moitié du défaut. Deux points est le minimum de la maison pour
     * qu'un arrondi (`RoundedCornerShape(50)`, un stade) ne touche jamais les glyphes : le rayon d'un
     * stade vaut la moitié de la hauteur, donc sans ce rembourrage la courbe passe dans l'encre du
     * chiffre dès que le corps du texte grandit.
     */
    val PadVertical: Dp = 2.dp

    /**
     * **Le retrait de la pastille depuis le coin de sa cellule.** La pastille est ancrée au coin
     * haut-droit de la cellule du repère, **en dedans** : 4 dp (la marche `Hair` de la maison) la
     * séparent du bord haut de la rangée et du bord droit de la cellule.
     *
     * C'est la correction de l'ancrage : l'ancien décalage sortait de la cellule (`x = +7`,
     * `y = -4`), la pastille mordait sur le filet de séparation de la plaque et se faisait couper par
     * la colonne d'onglet. Ici, elle reste **entièrement dans la rangée**, sans exception : le
     * décalage est négatif en abscisse et positif en ordonnée, donc il ne peut pas déborder.
     */
    val AnchorInset: Dp = OnyxSpace.Hair

    /** Le plus grand nombre écrit **entier** ; au-dessus, la forme courte (« 999+ »). */
    const val MaxShown: Int = 999

    /** Le libellé d'un compte : le nombre tel quel, jamais tronqué avant [MaxShown]. */
    fun label(count: Int): String = if (count > MaxShown) "${MaxShown}+" else count.toString()

    /**
     * **L'avance d'un chiffre**, en em du corps : 0,60.
     *
     * Elle ne sert qu'à *réserver* la place d'un compteur dans un calcul de largeur mené **sans
     * mesure** (la largeur laissée au libellé d'une tuile du volet, [SidebarLabels]). Le dessin, lui,
     * ne s'en sert **jamais** : la pastille se dimensionne sur la boîte **mesurée** du texte
     * ([paintedWidthDp]). C'est la seule valeur approchée du fichier, et elle est **majorante** face
     * aux chiffres du jeu de la maison (mesurée : 0,56 em par chiffre à 11 sp sur l'appareil) — une
     * réserve trop grande fait céder le libellé, jamais la pastille.
     */
    const val DigitAdvanceEm: Float = 0.60f

    /** Le corps (en sp) du libellé d'un compteur : `caption2`, les métas de la maison. */
    const val BodySp: Float = 11f

    /** La hauteur d'une ligne d'un corps de [bodySp], à l'échelle [fontScale] : `lineHeight` du thème. */
    fun lineHeightDp(bodySp: Float = BodySp, fontScale: Float = 1f): Float = bodySp * fontScale * 1.2f

    /**
     * La **largeur peinte** : le texte mesuré plus les deux rembourrages horizontaux. Jamais moins.
     */
    fun textWidthDp(
        measuredTextWidthDp: Float,
        padHorizontalDp: Float = PadHorizontal.value,
    ): Float = measuredTextWidthDp + 2f * padHorizontalDp

    /**
     * La **hauteur peinte** : le texte mesuré plus les deux rembourrages verticaux. La hauteur est
     * donc **dérivée du corps du texte** — une police à 1,3× donne une pastille plus haute — et
     * jamais écrite en dur.
     */
    fun paintedHeightDp(
        measuredTextHeightDp: Float,
        padVerticalDp: Float = PadVertical.value,
    ): Float = measuredTextHeightDp + 2f * padVerticalDp

    /**
     * Le **carré optique** de la pastille : sa hauteur peinte. Un nombre d'un seul chiffre n'est donc
     * jamais plus étroit que haut — c'est ce qui remplace la largeur fixe de 18 dp.
     */
    fun squareDp(
        measuredTextHeightDp: Float,
        padVerticalDp: Float = PadVertical.value,
    ): Float = paintedHeightDp(measuredTextHeightDp, padVerticalDp)

    /**
     * **La largeur peinte retenue** : le plus grand du texte rembourré et du carré optique. Elle est
     * donc **calculée** — pour un, deux et trois chiffres, chaque cas donne une largeur différente
     * (strictement croissante, ce que le verrou mesure), et aucune valeur n'est écrite à la main.
     */
    fun paintedWidthDp(
        measuredTextWidthDp: Float,
        measuredTextHeightDp: Float,
        padHorizontalDp: Float = PadHorizontal.value,
        padVerticalDp: Float = PadVertical.value,
    ): Float = max(
        textWidthDp(measuredTextWidthDp, padHorizontalDp),
        squareDp(measuredTextHeightDp, padVerticalDp),
    )

    /** Le **rembourrage résiduel** d'un côté, une fois la boîte peinte arrêtée. Jamais négatif. */
    fun residualPadDp(paintedDp: Float, textDp: Float): Float = (paintedDp - textDp) / 2f

    /**
     * Le verdict de lisibilité, sur les deux axes : la boîte du texte tient dans la boîte peinte avec
     * au moins [padHorizontalDp] / [padVerticalDp] de chaque côté. C'est ce que le verrou interroge.
     */
    fun fits(
        paintedWidthDp: Float,
        paintedHeightDp: Float,
        textWidthDp: Float,
        textHeightDp: Float,
        padHorizontalDp: Float = PadHorizontal.value,
        padVerticalDp: Float = PadVertical.value,
    ): Boolean = residualPadDp(paintedWidthDp, textWidthDp) >= padHorizontalDp - TOLERANCE &&
        residualPadDp(paintedHeightDp, textHeightDp) >= padVerticalDp - TOLERANCE

    /** La tolérance de comparaison, en dp : un dixième de pixel, pour les arrondis de mesure. */
    const val TOLERANCE: Float = 0.1f

    /**
     * La place que la pastille laisse entre elle et les bords de sa **cellule**, à l'ancrage
     * [insetDp] (positif : rentré dans la cellule ; négatif : sorti) — jamais négative : c'est la
     * marge qui prouve qu'aucun ancêtre ne la coupe.
     *
     * Elle prend le **plus petit** des quatre écarts : le bord gauche de la pastille, son bord haut,
     * et ses distances aux bords droit et bas de la cellule. Un ancrage rentré (**positif**) laisse
     * donc au moins [insetDp] de marge de chaque côté ; un ancrage sorti (négatif, l'ancien
     * `offset(x = +7.dp, y = -4.dp)`) rend un nombre **négatif**, et le verrou le refuse.
     */
    fun marginToCellDp(
        cellDp: Float,
        paintedWidthDp: Float,
        paintedHeightDp: Float,
        insetDp: Float = AnchorInset.value,
    ): Float = minOf(
        cellDp - insetDp - paintedWidthDp,
        insetDp,
        cellDp - insetDp - paintedHeightDp,
    )

    /**
     * La réserve d'un compteur dans un calcul de largeur mené **sans mesure** (une tuile du volet) :
     * le rembourrage, plus l'avance d'un chiffre par chiffre. `digits = 0` n'occupe rien.
     *
     * C'est ce qui remplace la largeur fixe : une tuile qui porte « 9 » réserve moins qu'une tuile qui
     * porte « 999 », et aucune des deux ne se fie à un nombre écrit à la main.
     */
    fun reserveDp(
        digits: Int,
        bodySp: Float = BodySp,
        fontScale: Float = 1f,
        padHorizontalDp: Float = PadHorizontal.value,
        padVerticalDp: Float = PadVertical.value,
    ): Float {
        if (digits <= 0) return 0f
        val textWidth = digits * DigitAdvanceEm * bodySp * fontScale
        val textHeight = lineHeightDp(bodySp, fontScale)
        return paintedWidthDp(textWidth, textHeight, padHorizontalDp, padVerticalDp)
    }
}
