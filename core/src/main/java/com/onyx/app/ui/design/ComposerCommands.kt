package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **Les commandes du compositeur et de la barre d'onglets : leur FOND et leur ENCRE, en une seule
 * source.**
 *
 * Le défaut fermé ici, mesuré et non supposé : un bouton rond du compositeur prenait sa teinte
 * **directement du thème** (`theme.fill`, `theme.accent`) sans jamais la **mesurer contre la surface
 * où il se pose** — la plaque. Or `theme.fill` (`#1C1C22` en sombre) et le sol de la plaque
 * (`#121215`) sont deux voisins : le disque du **mode vocal** mesurait **1,10:1** — il n'existait
 * pas à l'œil. Et quand l'accent d'un thème approche la surface (le thème « Code » en clair :
 * `#CBF0FF` sur `#F9F9F5`), c'est le **disque d'envoi** qui disparaissait (**1,14:1**).
 *
 * Les deux règles sont donc nommées ici, une fois, et **l'écran comme le verrou les appellent** —
 * le chiffre annoncé et le chiffre vérifié sont le même appel :
 *
 *  1. **Le fond d'un disque** — la teinte du thème, **écartée du sol** ([OnyxContrast.distinctOn])
 *     juste assez pour tenir le plancher d'icône de la maison (3:1). Une teinte qui tient déjà ne
 *     bouge pas d'un point : rien n'est « corrigé » sans raison.
 *  2. **L'encre d'un glyphe** — l'encre du thème, **mesurée** contre **ce qui est peint dessous** :
 *     le disque pour un bouton plein ([inkOnDisc]), le sol de la plaque pour une commande au trait
 *     ([glyphOnPlaque]). Jamais une couleur en dur, jamais `Color.Unspecified` (qui écrit du NOIR
 *     par défaut — le piège des titres de cette app).
 *
 * La barre d'onglets suit la **même** règle par ses fonctions existantes ([plaqueVeil],
 * [plaqueGround], [plaqueInk]) : ses encres sont déjà dérivées du fond peint, et le verrou le
 * revérifie ici pour que les deux rangées ne puissent pas diverger.
 */
object ComposerCommands {

    /** La marge de l'encre d'un trait au-dessus du plancher (le trait est antialiasé). */
    const val IDLE_MARGIN: Float = 0.5f

    /**
     * **Le sol des commandes** : la plaque telle qu'elle est **peinte** — le voile de sa vitre sur
     * la page de l'apparence courante. C'est la même fonction que mesure la barre d'onglets
     * ([plaqueGround]), donc les deux rangées de la plaque lisent le même fond.
     */
    fun ground(theme: ResolvedTheme): Color = plaqueGround(theme, selected = false)

    /**
     * **Le fond d'un disque de commande** : la teinte voulue par le thème, écartée du sol juste
     * assez pour se voir. La teinte d'origine est rendue telle quelle dès qu'elle tient déjà le
     * plancher d'icône — un disque d'accent bien détaché ne bouge donc pas.
     */
    fun disc(theme: ResolvedTheme, tint: Color): Color =
        OnyxContrast.distinctOn(tint, ground(theme), OnyxContrast.ICON_MIN)

    /**
     * **L'encre d'un glyphe posé sur le sol de la plaque** — les commandes **sans disque** (le
     * « + », le micro, l'agrandissement) : au trait, elles se lisent sur la plaque elle-même.
     */
    fun glyphOnPlaque(theme: ResolvedTheme, preferred: Color): Color =
        readableInk(preferred, ground(theme), (OnyxContrast.ICON_MIN + IDLE_MARGIN).toDouble())

    /**
     * **L'encre d'un glyphe posé sur le disque d'une commande** : l'encre préférée du thème,
     * mesurée contre **le disque réellement peint** (donc après l'écartement du point 1).
     */
    fun inkOnDisc(
        theme: ResolvedTheme,
        disc: Color,
        preferred: Color = theme.userText,
        minRatio: Double = OnyxContrast.TEXT_MIN.toDouble(),
    ): Color = readableInk(preferred, disc, minRatio)

    /**
     * **Un bouton inerte (l'envoi sans brouillon) ne se peint pas autrement qu'un bouton armé.**
     *
     * C'est la propriété que le défaut violait, et elle est ici, mesurable : `enabled` ne vaut
     * `false` que sur un champ vide, et la couche d'opacité qu'il déclenche (`onyxClickable`,
     * 0,35) s'applique à **tout l'arbre** — le disque **et** la flèche. Composé sur la plaque, le
     * disque d'envoi mesurait alors **1,49:1** en apparence sombre (« Standard »), et **1,40:1** au
     * pire des sept thèmes : la zone du bouton était vide, exactement la capture du propriétaire.
     * Un disque mesuré à 35 % retombe donc sous le plancher d'icône (3:1) dans **les sept thèmes,
     * dans les deux apparences** — l'atténuation d'un contrôle mesuré n'est pas une option
     * d'aspect, c'est la disparition du contrôle.
     *
     * Les commandes du compositeur passent donc [INERT_ALPHA] : le clic est refusé, la peinture ne
     * bouge pas. Le chiffre de cette ligne et celui du verrou sont le même appel.
     */
    const val INERT_ALPHA: Float = 1f
}
