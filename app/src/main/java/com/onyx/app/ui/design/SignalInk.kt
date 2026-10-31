package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * Les encres d'un écran de **données** — « Capacités » et « Aperçus » : un mot qui se touche, une
 * barre de graphique, un état au repos.
 *
 * Le défaut que ces fonctions évitent, mesuré sur tous les thèmes dans `InsightsContrastTest` : une
 * couleur prise au thème pour ce qu'elle est — l'accent, un libellé secondaire — sans vérifier
 * qu'elle **se lit** sur le fond où elle se pose. L'accent d'un thème clair sur une page claire est à
 * 1,0:1 ; le libellé secondaire d'un thème sombre sur la page claire de ce thème, à 3,3:1. Le mot est
 * là, il n'est simplement pas lisible.
 *
 * Toutes les fonctions rendent une couleur **opaque** (composée sur [surface]) : l'écran qui la pose
 * n'a plus à savoir si le thème la donnait translucide. Elles sont **pures** : elles se mesurent, se
 * testent, et ne dépendent que du thème, du fond et du seuil.
 */

/**
 * Une couleur qui **doit porter un mot** : l'accent quand il se lit sur [surface], sinon le texte
 * pleine encre — qui, lui, est fait pour se lire sur cette surface.
 */
fun signalInk(
    theme: ResolvedTheme,
    surface: Color,
    minRatio: Float = OnyxContrast.TEXT_MIN,
): Color {
    val accent = OnyxContrast.over(theme.accent, surface)
    if (readable(accent, surface, minRatio)) return accent
    return bodyInk(theme, surface, theme.textColor, minRatio)
}

/**
 * L'encre d'un mot secondaire (une légende, une description, une note de bas de page) : la couleur
 * demandée si elle tient [minRatio], sinon l'encre pleine, sinon la couleur écartée du fond juste
 * assez pour tenir. **Jamais** sous [minRatio].
 */
fun bodyInk(
    theme: ResolvedTheme,
    surface: Color,
    preferred: Color = theme.secondaryText,
    minRatio: Float = OnyxContrast.TEXT_MIN,
): Color {
    val ink = OnyxContrast.over(preferred, surface)
    if (readable(ink, surface, minRatio)) return ink
    val label = OnyxContrast.over(theme.textColor, surface)
    if (readable(label, surface, minRatio)) return label
    return OnyxContrast.distinctOn(ink, surface, minRatio)
}

/**
 * L'encre d'une **forme** — le trait d'une icône, un chevron, la loupe d'un champ : jamais sous 3:1,
 * la limite en dessous de laquelle une forme cesse d'être reconnaissable.
 */
fun iconInk(
    theme: ResolvedTheme,
    surface: Color,
    preferred: Color = theme.tertiaryText,
    minRatio: Float = OnyxContrast.ICON_MIN,
): Color {
    val ink = OnyxContrast.over(preferred, surface)
    if (readable(ink, surface, minRatio)) return ink
    val label = OnyxContrast.over(theme.textColor, surface)
    if (readable(label, surface, minRatio)) return label
    return OnyxContrast.distinctOn(ink, surface, minRatio)
}

/**
 * L'encre d'une **barre de graphique** : l'accent du thème, écarté de [surface] juste assez pour
 * qu'une forme se distingue de son fond ([OnyxContrast.distinctOn], seuil 3:1).
 *
 * Sur la plupart des thèmes l'accent est rendu tel quel ; là où il tombait à 2,96:1 — la piste d'un
 * classement, un cheveu sous le seuil — la couleur s'éloigne du plus petit pas qui suffit, plutôt
 * que d'être remplacée par une encre sans couleur.
 */
fun barInk(
    theme: ResolvedTheme,
    surface: Color,
    minRatio: Float = OnyxContrast.ICON_MIN,
): Color = OnyxContrast.distinctOn(OnyxContrast.over(theme.accent, surface), surface, minRatio)

/**
 * Une teinte au **repos** (les jours qui reculent quand un autre est choisi) : la plus grande
 * atténuation de l'accent vers [surface] qui tienne encore [minRatio].
 *
 * C'est la règle de la barre du bas, appliquée au dessin d'un graphique : un état au repos doit se
 * distinguer d'un état actif, **sans jamais** descendre sous le seuil d'une forme reconnaissable. Un
 * `alpha = 0,35` posé à la main, lui, tombait sous 3:1 sur les thèmes clairs.
 */
fun recededInk(
    theme: ResolvedTheme,
    surface: Color,
    minRatio: Float = OnyxContrast.ICON_MIN,
): Color = OnyxContrast.dimmed(barInk(theme, surface, minRatio), surface, minRatio)

/** Le rapport réellement lu à l'écran : la composition alpha est faite avant la mesure. */
private fun readable(ink: Color, surface: Color, minRatio: Float): Boolean =
    OnyxContrast.ratio(ink, surface) >= minRatio
