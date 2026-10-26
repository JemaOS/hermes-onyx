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

/**
 * Le thème Onyx: porte d'entrée unique de l'apparence de l'app.
 *
 * L'apparence (clair / sombre / système) et le thème (les sept de ) sont fournis à
 * l'arbre par [LocalOnyxTheme]; aucun composant Material n'est utilisé.
 */
@Composable
fun OnyxAppTheme(
    theme: OnyxTheme = OnyxTheme.STANDARD,
    appearance: AppearanceMode = AppearanceMode.SYSTEM,
    colors: ThemeColors? = null,
    content: @Composable () -> Unit,
) {
    val assets = LocalContext.current.assets
    val fonts = remember(assets) { OnyxFonts.forAssets(assets) }
    val dark = appearance.isDark(isSystemInDarkTheme())
    val resolved = remember(theme, dark, fonts, colors) { ResolvedTheme.resolve(theme, dark, fonts, colors) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            // Le contenu du fond est celui de la page: les icônes système suivent son contraste.
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    // Les poignées et le surlignage d'une sélection prennent la couleur du thème, comme le reste de
    // l'habillage: c'est le seul endroit où les couleurs de sélection sont posées, si bien que le
    // fil, le terminal et les écrans de texte copiable sont d'accord entre eux.
    val selection = remember(resolved.accent) {
        TextSelectionColors(
            handleColor = resolved.accent,
            backgroundColor = resolved.accent.copy(alpha = 0.35f),
        )
    }

    CompositionLocalProvider(
        LocalOnyxTheme provides resolved,
        LocalTextSelectionColors provides selection,
        content = content,
    )
}
