package com.onyx.app.ui.chat

import com.onyx.app.ui.design.OnyxSpace

/**
 * Le suivi du bas du fil : « on suit » ou « détaché » — et **où passe la frontière**.
 *
 * Toute la décision vit ici, sans dépendre de Compose, pour rester testable sur la JVM — même
 * forme que [ChatShortcuts]. Trois règles, celles de la référence :
 *
 *  1. un doigt posé sur la liste détache le suivi **tout de suite** — c'est la personne qui décide,
 *     pas la position ;
 *  2. au repos, il se ré-attache si la fin du fil est à l'écran **dans la marge d'ancrage** ;
 *  3. le contenu qui grandit n'entraîne la vue que si le suivi est attaché.
 *
 * **Le défaut corrigé ici est la mesure de « en bas »**, pas la règle. La distance était comptée
 * jusqu'au bord du viewport, alors que la fin du fil s'arrête au-dessus de la réserve du
 * compositeur : la marge valait donc `120 + la réserve` — **330 dp mesurés à l'appareil, six
 * rangées**. On remontait d'un tiers d'écran, on lâchait, le suivi se ré-attachait, et la réponse
 * suivante ramenait la vue en bas : « quand je défile doucement pour tout lire, ça force au dernier
 * message ». La réserve du bas est donc **retirée** de la distance ([distanceToBottom]), et la
 * frontière est une **hauteur de rangée** ([AnchorMarginDp]).
 */
object ScrollFollow {
    /**
     * **La marge d'ancrage**, en dp : la distance sous laquelle la fin du fil compte comme « à
     * l'écran ». C'est **une hauteur de rangée** ([OnyxSpace.RowHeight], 56 dp) : le doigt qui a
     * remonté d'une ligne tient encore le bas ; au-delà, la personne lit, et la liste ne bouge plus
     * — quoi qu'il arrive (nouvelle réponse, lignes d'outils, texte qui s'écrit).
     */
    val AnchorMarginDp: Int = OnyxSpace.RowHeight.value.toInt()

    /** Un doigt sur la liste : la personne décide, le suivi se détache tout de suite. */
    fun afterTouch(): Boolean = false

    /**
     * **La distance, en pixels, dont la fin du fil a été remontée** depuis son vrai bas :
     * `0` quand la fin est collée au haut de la réserve, positive quand on a remonté, négative
     * quand le fil est plus court que son cadre (il ne peut pas défiler : il est au bas).
     *
     * @param itemEndPx la fin de la dernière ligne (`offset + taille`) ;
     * @param viewportEndPx le bord bas du viewport, dans le **même** repère que `itemEndPx` ;
     * @param bottomPaddingPx la réserve que la liste pose sous sa dernière ligne
     *        (`LazyListLayoutInfo.afterContentPadding`). **C'est le terme qui manquait** : sans lui,
     *        la pile du compositeur — 205 dp mesurés — était comptée comme du fil remonté.
     */
    fun distanceToBottom(itemEndPx: Int, viewportEndPx: Int, bottomPaddingPx: Int): Int =
        itemEndPx + bottomPaddingPx - viewportEndPx

    /** Vrai si la fin du fil est à l'écran, dans la marge d'ancrage. */
    fun withinAnchor(distanceToBottomPx: Int, marginPx: Int): Boolean =
        distanceToBottomPx <= marginPx

    /**
     * L'état du suivi au repos. Un geste en cours ne ré-attache **jamais** : pendant le geste, le
     * contenu qui grandit peut faire lire « au bas » à tort, et le suivi viendrait justement d'y
     * ramener la vue. Au repos, être **dans la marge d'ancrage** ré-attache ; ailleurs l'état ne
     * change pas — c'est le doigt, et lui seul, qui détache (voir [afterTouch]).
     */
    fun atRest(scrolling: Boolean, atAnchor: Boolean, following: Boolean): Boolean = when {
        scrolling -> following
        atAnchor -> true
        else -> following
    }

    /** Un nouveau message de la personne, ou « aller au dernier message » : on repart en suivant. */
    fun jumpToLatest(): Boolean = true

    /**
     * **Le suivi, rejoué à chaque transition d'état du tour.**
     *
     * Le contenu neuf — l'attente, le début de réflexion, le début de réponse, le premier jeton —
     * ne ramène la vue en bas que si la personne n'a pas remonté : c'est [following] qui répond, et
     * un doigt posé le met à `false` pour **tout** le tour ([afterTouch]). Une transition d'état ne
     * **ré-attache donc jamais** : sans cette règle, le premier jeton reçu après avoir remonté lire
     * ramènerait la vue au bas — le défaut d'origine (« quand je défile doucement pour lire, ça
     * force au dernier message »), revenu par la porte des états.
     */
    fun followsNewContent(following: Boolean): Boolean = following

    /**
     * **La réserve du bas laisse-t-elle de la place au contenu neuf ?**
     *
     * C'est l'autre moitié de « le fil suit », et celle qui manquait : un fil **posé au bas** peut
     * quand même paraître **vide** si le rembourrage que la liste pose sous sa dernière ligne ne
     * laisse plus la place d'une rangée. Au bas du défilement, la vue montre le contenu
     * `fil − rembourrage` au-dessus de la dernière ligne : il faut donc qu'il en reste **au moins
     * une rangée** ([marginPx], la marge d'ancrage), sinon le message neuf est écrasé contre le haut
     * du fil — et, sous la barre du haut qui le recouvre, l'écran paraît vide.
     *
     * Chiffres mesurés à l'appareil (densité 2,625, 1080×2400), clavier ouvert, `fil = 1 517 px` :
     *  - réserve **552 dp** (clavier compté deux fois : `484 + 56 + 12`) → `1 449 px`, il restait
     *    **68 px** (26 dp) — moins d'une rangée, et le fil était **vide** : le défaut du
     *    propriétaire ;
     *  - réserve **240 dp** (`172 + 56 + 12`) → `630 px`, il reste **887 px** (338 dp) : le message
     *    envoyé et la réponse sont visibles.
     *
     * @param bottomPaddingPx la réserve de la liste (`afterContentPadding` : compositeur, onglets,
     *        encart du système — clavier **non** compté deux fois —, voie flottante, écart) ;
     * @param viewportPx la hauteur du fil ;
     * @param marginPx la hauteur sous laquelle le contenu neuf doit rester visible (une rangée).
     */
    fun bottomPaddingLeavesRoomForNewContent(
        bottomPaddingPx: Int,
        viewportPx: Int,
        marginPx: Int,
    ): Boolean = viewportPx - bottomPaddingPx >= marginPx
}

/** L'étiquette de la trace du suivi, telle qu'elle se lit dans `logcat`. */
const val ScrollFollowLogTag: String = "OnyxScrollFollow"
