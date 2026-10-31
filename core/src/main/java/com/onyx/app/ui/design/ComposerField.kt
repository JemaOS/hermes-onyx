package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * Le fond, l'encre et la sélection d'un **champ de saisie** — une seule règle pour tous les
 * compositeurs de l'app.
 *
 * Le défaut mesuré : quand le clavier ouvrait et qu'on écrivait, une **bande claire** se lisait
 * derrière le texte du champ — sur un thème sombre. Elle venait d'un fond **opaque** peint par le
 * champ lui-même ([ResolvedTheme.surfaceColor], ou la vitre claire quand le thème ne publie pas de
 * surface) : le champ ne laissait pas voir la vitre de la barre qui le porte, et cette surface de
 * repli est claire. Rien dans le code n'empêchait ce blanc, et rien ne pouvait le signaler.
 *
 * Trois décisions, tenues ici et nulle part ailleurs :
 *
 * 1. **Le fond du champ ne peint pas d'aplat.** La vitre de la barre qui le porte passe au travers
 *    ([composerFieldSurface]) : un champ ne peut donc plus poser un bloc clair derrière son texte.
 * 2. **La sélection a la teinte de l'accent, translucide**, et son opacité est **bornée par la
 *    lisibilité** de l'encre ([selectionAlphaFor]) — jamais un blanc par défaut, jamais une encre
 *    illisible. Les poignées prennent l'accent, le curseur aussi.
 * 3. **Rien n'est supposé du thème** : [ProvideFieldTextColours] repose les couleurs de sélection
 *    autour du champ, pour qu'un champ composé hors du thème ne retombe pas sur les valeurs par
 *    défaut de Compose (un bleu opaque, et un blanc en clair).
 *
 * Les fonctions sont **pures** : c'est ce que le test interroge, donc le chiffre annoncé et le
 * chiffre vérifié sont le même.
 */

// ---------------------------------------------------------------------------------------------
// Les couleurs
// ---------------------------------------------------------------------------------------------

/**
 * Le voile d'un champ quand une surface doit être peinte (une pastille, un fond de bandeau) :
 * la couleur de page teintée — **jamais du blanc pur** (voir [veilColour]).
 *
 * En sombre le voile se pose d'un cran **sous** la page (le champ se creuse) ; en clair il reste la
 * page. C'est la teinte des barres de l'app, pas une couleur nouvelle.
 */
fun fieldSurfaceColour(theme: ResolvedTheme): Color =
    veilColour(theme.pageColor, if (theme.dark) 0.34f else 0.55f, theme.dark)

/** Vrai si une couleur est un blanc d'aplat — ce qu'un fond de champ ne doit jamais être en sombre. */
fun isNearWhite(colour: Color, threshold: Float = 0.92f): Boolean =
    colour.red >= threshold && colour.green >= threshold && colour.blue >= threshold

/**
 * Vrai si le fond de champ d'un thème est acceptable : en **sombre**, il ne doit pas être un blanc
 * d'aplat ; en clair, il suit la page (le blanc y est la teinte du thème, pas un parasite).
 */
fun fieldSurfaceIsSafe(theme: ResolvedTheme): Boolean = !theme.dark || !isNearWhite(fieldSurfaceColour(theme))

/**
 * L'opacité du voile de sélection, **bornée par la lisibilité** : on part de la valeur de
 * l'apparence, puis on la baisse tant que l'encre perdrait son seuil de contraste (4,5:1) sur le
 * fond composé. Une sélection ne fait donc jamais perdre la lecture — la règle est la même que
 * celle de la barre d'onglets ([readableInk]).
 */
fun selectionAlphaFor(theme: ResolvedTheme): Float {
    var alpha = if (theme.dark) 0.42f else 0.28f
    val floor = 0.12f
    while (alpha > floor && contrastRatio(theme.textColor, selectionBand(theme, alpha)) < TEXT_CONTRAST_MIN) {
        alpha -= 0.02f
    }
    return alpha
}

/** Le fond réellement composé d'une sélection : le voile d'accent posé sur le fond du champ. */
fun selectionBand(theme: ResolvedTheme, alpha: Float): Color =
    compositedBackdrop(theme.accent.copy(alpha = alpha), fieldSurfaceColour(theme))

/** Le rapport d'encre d'une sélection, mesuré sur le fond composé (le test l'exige ≥ 4,5:1). */
fun selectionInkContrast(theme: ResolvedTheme): Double =
    contrastRatio(theme.textColor, selectionBand(theme, selectionAlphaFor(theme)))

/**
 * Les couleurs de sélection d'un champ : une **teinte d'accent translucide** et les poignées de
 * l'accent. Jamais le blanc par défaut de Compose, jamais une couleur hors du thème.
 */
fun fieldSelectionColours(theme: ResolvedTheme): TextSelectionColors =
    TextSelectionColors(
        handleColor = theme.accent,
        backgroundColor = theme.accent.copy(alpha = selectionAlphaFor(theme)),
    )

/** Le curseur d'un champ : la couleur d'accent du thème. */
fun fieldCursorColour(theme: ResolvedTheme): Color = theme.accent

// ---------------------------------------------------------------------------------------------
// Le fond du champ
// ---------------------------------------------------------------------------------------------

/**
 * Le fond d'un champ : **rien d'opaque**. La vitre de la barre qui le porte passe au travers, et le
 * champ n'ajoute que son arête — un liseré de 1 dp, celui du séparateur, porté à l'accent quand le
 * champ a le focus. Un aplat clair hérité ne peut donc plus se lire derrière le texte.
 *
 * [fill] ne sert qu'aux surfaces qui doivent **vraiment** se détacher (un champ posé sur une image) :
 * la teinte vient alors de [fieldSurfaceColour], jamais d'un blanc.
 */
fun Modifier.composerFieldSurface(
    theme: ResolvedTheme,
    shape: Shape,
    focused: Boolean,
    fill: Boolean = false,
    width: Dp = 1.dp,
    /**
     * **Le liseré est-il tracé ?** Faux quand le champ est posé **sur la plaque** : la planche ne
     * dessine aucun cadre autour du champ (`.askf` n'a pas de bordure), et un second contour dans
     * une plaque déjà bordée se lit « un champ dans un champ ». La règle du fond ne change pas :
     * aucun aplat n'est peint, la vitre de la plaque passe au travers.
     *
     * (`width = 0.dp` ne suffit pas : `Modifier.border` trace un trait d'un pixel même à largeur
     * nulle — c'est une arête, pas une absence.)
     */
    outlined: Boolean = true,
): Modifier = this
    .then(if (fill) Modifier.background(fieldSurfaceColour(theme), shape) else Modifier)
    .then(
        if (outlined) {
            Modifier.border(
                width = width,
                color = if (focused) theme.accent.copy(alpha = 0.55f) else theme.separator.copy(alpha = 0.5f),
                shape = shape,
            )
        } else {
            Modifier
        },
    )

/**
 * Repose les couleurs de sélection autour d'un champ. Le thème les pose déjà pour tout l'arbre
 * ([com.onyx.app.ui.theme.OnyxAppTheme]) ; les reposer **ici** garantit qu'un champ garde la règle
 * du lot même composé seul (aperçu, test, écran hors thème) — et qu'il ne retombe jamais sur le
 * blanc par défaut.
 */
@Composable
fun ProvideFieldTextColours(
    theme: ResolvedTheme = LocalOnyxTheme.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTextSelectionColors provides fieldSelectionColours(theme),
        content = content,
    )
}

/** Le liseré d'un champ, exposé pour les tests de source et les aperçus. */
val FieldDefaultBorderWidth: Dp = 1.dp
