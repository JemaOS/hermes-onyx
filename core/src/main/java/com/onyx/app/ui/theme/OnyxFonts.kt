package com.onyx.app.ui.theme

import android.content.res.AssetManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Les polices embarquées, copiées depuis: Inter lorsque la palette nomme
 * cette police (Hermes), Source Code Pro lorsqu'elle nomme la monospace (Terminal et Amber).
 * Sans nom explicite, le thème conserve les familles système comme.
 *
 * Les fichiers vivent dans `assets/fonts/`; [forAssets] construit les familles au démarrage.
 */
class OnyxFonts private constructor(assets: AssetManager?) {
    val inter: FontFamily = if (assets == null) {
        FontFamily.SansSerif
    } else {
        FontFamily(
            Font(INTER_REGULAR, assets, FontWeight.Normal, FontStyle.Normal),
            Font(INTER_REGULAR, assets, FontWeight.Medium, FontStyle.Normal),
            Font(INTER_SEMI_BOLD, assets, FontWeight.SemiBold, FontStyle.Normal),
            Font(INTER_BOLD, assets, FontWeight.Bold, FontStyle.Normal),
            Font(INTER_BOLD, assets, FontWeight.Black, FontStyle.Normal),
        )
    }

    val sourceCode: FontFamily = if (assets == null) {
        FontFamily.Monospace
    } else {
        FontFamily(
            Font(SOURCE_CODE_MEDIUM, assets, FontWeight.Normal, FontStyle.Normal),
            Font(SOURCE_CODE_MEDIUM, assets, FontWeight.Medium, FontStyle.Normal),
            Font(SOURCE_CODE_MEDIUM, assets, FontWeight.SemiBold, FontStyle.Normal),
            Font(SOURCE_CODE_BOLD, assets, FontWeight.Bold, FontStyle.Normal),
            Font(SOURCE_CODE_BOLD, assets, FontWeight.Black, FontStyle.Normal),
        )
    }

    /** La famille à utiliser pour une palette donnée, avec repli sur la famille système. */
    fun familyFor(design: FontDesign, named: String? = null): FontFamily = when {
        named != null -> sourceCodeOrInter(named)
        design == FontDesign.MONOSPACED -> FontFamily.Monospace
        design == FontDesign.SERIF -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }

    private fun sourceCodeOrInter(name: String): FontFamily =
        if (name.contains("SourceCode")) sourceCode else inter

    companion object {
        /** Chemins dans `assets/`. */
        const val INTER_REGULAR = "fonts/Inter-Regular.ttf"
        const val INTER_SEMI_BOLD = "fonts/Inter-SemiBold.ttf"
        const val INTER_BOLD = "fonts/Inter-Bold.ttf"
        const val SOURCE_CODE_MEDIUM = "fonts/SourceCodePro-Medium.ttf"
        const val SOURCE_CODE_BOLD = "fonts/SourceCodePro-Bold.ttf"

        fun forAssets(assets: AssetManager): OnyxFonts = OnyxFonts(assets)

        /** Repli système, pour les aperçus et le défaut du CompositionLocal. */
        val System: OnyxFonts = OnyxFonts(null)
    }
}
