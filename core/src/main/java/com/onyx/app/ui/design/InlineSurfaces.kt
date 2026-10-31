package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * Les fonds « en ligne » d'un message : le **code en ligne**, le **bloc de code**, la **citation**,
 * la **mention `@`** et le **lien** — chacun une teinte **dérivée du thème**, jamais un gris clair
 * hérité.
 *
 * Le défaut mesuré : sur un thème sombre, les passages en code en ligne se lisaient comme des
 * **bandes claires** au milieu du texte — un fond clair posé au milieu d'un message sombre. Une
 * surface « en ligne » ne doit donc pas être une couleur choisie par un composant : elle se dérive
 * de la surface du thème (la surface **surélevée**), et son encre est **mesurée** contre elle.
 *
 * Les fonctions sont pures : le test les interroge thème par thème, dans les deux apparences, et
 * lit le même rapport que celui annoncé ici.
 */

/** Le seuil d'encre du code en ligne, comme le reste de l'app ([TEXT_CONTRAST_MIN]). */
const val INLINE_INK_MIN = TEXT_CONTRAST_MIN

/**
 * La surface d'un **code en ligne** : la surface du thème, un cran plus haut que la page.
 *
 * En sombre elle se relève vers le blanc d'une **petite** part seulement : assez pour détacher la
 * pastille de la page, jamais assez pour devenir un gris clair. En clair elle se creuse vers le
 * noir, symétriquement.
 */
fun inlineCodeSurface(theme: ResolvedTheme): Color {
    val base = theme.surfaceColor
    val lifted = lerp(base, if (theme.dark) Color.White else Color.Black, 0.06f)
    // Un thème qui publierait une surface claire en sombre ne doit pas relever cette surface : on
    // garde alors la surface telle quelle, et le voile de page par-dessus (jamais du blanc).
    return if (theme.dark && isNearWhite(base)) fieldSurfaceColour(theme) else lifted
}

/** La surface d'un **bloc de code** : la surface du thème, un cran sous la page en clair. */
fun codeBlockSurface(theme: ResolvedTheme): Color =
    lerp(theme.surfaceColor, if (theme.dark) Color.White else Color.Black, 0.04f)

/** L'encre d'un code en ligne, tenue au seuil : jamais illisible sur sa pastille. */
fun inlineCodeInk(theme: ResolvedTheme): Color =
    readableInk(theme.secondaryText, inlineCodeSurface(theme), INLINE_INK_MIN)

/** Le rapport d'encre d'un code en ligne, mesuré sur la surface réelle. */
fun inlineCodeContrast(theme: ResolvedTheme): Double =
    contrastRatio(inlineCodeInk(theme), inlineCodeSurface(theme))

/**
 * Vrai si la règle du code en ligne tient pour ce thème : **pas de pastille claire en sombre**, et
 * une encre lisible sur la pastille dans les deux apparences.
 */
fun inlineCodeRuleHolds(theme: ResolvedTheme): Boolean {
    if (theme.dark && isNearWhite(inlineCodeSurface(theme))) return false
    return inlineCodeContrast(theme) >= INLINE_INK_MIN
}

/** La barre d'une **citation** : l'accent du thème, voilé — jamais un aplat. */
fun quoteRuleColour(theme: ResolvedTheme): Color = theme.quoteBarColor

/** Le fond d'une **mention `@`** ou d'une puce : la surface du thème, un souffle plus haut. */
fun mentionSurface(theme: ResolvedTheme): Color =
    lerp(theme.surfaceColor, if (theme.dark) Color.White else Color.Black, 0.05f)

/** Un **lien** garde la couleur du thème ([ResolvedTheme.linkColor]) : il ne pose pas de fond. */
fun linkInk(theme: ResolvedTheme): Color = theme.linkColor
