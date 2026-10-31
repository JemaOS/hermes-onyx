package com.onyx.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.onyx.app.ui.design.GlassSourcePresence
import com.onyx.app.ui.design.LocalBackdrop
import com.onyx.app.ui.design.LocalFrostLevel
import com.onyx.app.ui.design.LocalGlassFluidity
import com.onyx.app.ui.design.LocalGlassSourcePresence
import com.onyx.app.ui.design.LocalHazeState
import com.onyx.app.ui.design.LocalOnyxTextScale
import com.onyx.app.ui.design.rememberBackdrop
import com.onyx.app.ui.design.rememberFrostLevel
import com.onyx.app.ui.design.rememberGlassFluidity
import dev.chrisbanes.haze.rememberHazeState

/**
 * Le thème Onyx : porte d'entrée unique de l'apparence de l'app.
 *
 * L'apparence (clair / sombre / système) et le thème (les sept de Theme) sont fournis à
 * l'arbre par [LocalOnyxTheme] ; aucun composant Material n'est utilisé.
 */
@Composable
fun OnyxAppTheme(
    theme: OnyxTheme = OnyxTheme.STANDARD,
    appearance: AppearanceMode = AppearanceMode.SYSTEM,
    colors: ThemeColors? = null,
    /** « Noir pur » : en apparence sombre, les fonds de page passent au noir absolu (OLED). */
    pureBlack: Boolean = false,
    /** « Halo » : la lumière colorée douce derrière le haut de chaque écran. Éteint, rien n'est peint. */
    halo: Boolean = true,
    content: @Composable () -> Unit,
) {
    val assets = LocalContext.current.assets
    val fonts = remember(assets) { OnyxFonts.forAssets(assets) }
    val dark = appearance.isDark(isSystemInDarkTheme())
    val resolved = remember(theme, dark, fonts, colors, pureBlack, halo) {
        ResolvedTheme.resolve(theme, dark, fonts, colors, pureBlack, halo)
    }
    // **L'échelle de texte des Réglages s'applique à TOUT l'arbre.** Elle ne tenait qu'au fil de
    // conversation (les trois écrans de fil la reposaient eux-mêmes) ; le réglage paraissait donc
    // sans effet partout ailleurs. Elle est lue ici, une fois, et posée avec le thème : les écrans
    // de fil ne la reposent plus, sinon elle s'appliquerait deux fois (1,23 × 1,23 = 1,51).
    val textScale = LocalOnyxTextScale.current
    val scaled = remember(resolved, textScale) { resolved.withFontScale(textScale) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            // Le contenu du fond est celui de la page : les icônes système suivent son contraste.
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    // Les poignées et le surlignage d'une sélection prennent la couleur du thème, comme le reste de
    // l'habillage : c'est le seul endroit où les couleurs de sélection sont posées, si bien que le
    // fil, le terminal et les écrans de texte copiable sont d'accord entre eux.
    val selection = remember(resolved.accent) {
        TextSelectionColors(
            handleColor = resolved.accent,
            backgroundColor = resolved.accent.copy(alpha = 0.35f),
        )
    }

    // Le verre liquide se décide ici, une fois pour tout l'arbre : la qualité (pleine, allégée,
    // repli) et le calque du contenu sont posés au même endroit que le thème, si bien qu'un
    // changement d'apparence ou de réglage d'accessibilité se voit sans redémarrer l'app.
    val frost = rememberFrostLevel()
    val backdrop = rememberBackdrop()
    // Et la qualité du **verre premium** (`LiquidGlass`), mesurée sur les trames réellement
    // produites plutôt que déduite de l'appareil : posée ici, elle est une pour tous les écrans.
    val glass = rememberGlassFluidity()
    // L'état Haze : la **source** est posée par l'écran qui fait défiler son contenu
    // (`Modifier.onyxHazeSource`), les vitres le lisent ici. Un seul état par racine, comme le calque.
    val glassState = rememberHazeState()
    // Et le **compte des sources** : Haze 2 ne dit plus à la vitre s'il y a quelque chose à
    // échantillonner (sa liste de zones est `internal`), donc on tient le compte ici, à côté de
    // l'état — un écran qui pose sa source l'incrémente, et le retire en quittant.
    val glassSources = remember { GlassSourcePresence() }

    CompositionLocalProvider(
        LocalOnyxTheme provides scaled,
        LocalTextSelectionColors provides selection,
        LocalFrostLevel provides frost,
        LocalBackdrop provides backdrop,
        LocalGlassFluidity provides glass,
        LocalHazeState provides glassState,
        LocalGlassSourcePresence provides glassSources,
        content = content,
    )
}
