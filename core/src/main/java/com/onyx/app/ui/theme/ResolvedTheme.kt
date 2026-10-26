package com.onyx.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Réglages → Apparence: clair, sombre, ou comme le système (comme ). */
enum class AppearanceMode(val id: String, val label: String) {
    SYSTEM("system", "Système"),
    LIGHT("light", "Clair"),
    DARK("dark", "Sombre"),
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromId(raw: String?): AppearanceMode = entries.firstOrNull { it.id == raw } ?: SYSTEM
    }
}

/**
 * Un thème tel que l'app le rend: la palette plus l'apparence résolue.
 *
 * Les dérivations de l'accent vivent ici, une fois, si bien que le thème de base et la copie
 * résolue répondent identiquement — comme le protocole `ThemeStyle` de.
 */
@Immutable
class ResolvedTheme(
    val theme: OnyxTheme,
    val palette: Palette,
    val dark: Boolean,
    val fonts: OnyxFonts,
    /** L'échelle du texte de la conversation (`chatTextSize`); 1 = la taille du système. */
    val fontScale: Float = 1f,
) {
    val id: String get() = theme.id
    val label: String get() = theme.label
    val blurb: String get() = theme.blurb

    val accent: Color get() = palette.accent.resolve(dark)
    val pageColor: Color get() = palette.background?.resolve(dark) ?: OnyxSystem.background.resolve(dark)
    val surfaceColor: Color get() = palette.surface?.resolve(dark) ?: OnyxSystem.secondaryBackground.resolve(dark)
    val textColor: Color get() = palette.text?.resolve(dark) ?: OnyxSystem.label.resolve(dark)

    /** Les `Form` de Settings/Setup restent ceux; la palette colore leur accent, pas leurs cartes. */
    val formPageColor: Color get() = OnyxSystem.elevatedGroupedBackground.resolve(dark)
    val formSurfaceColor: Color get() = OnyxSystem.elevatedSecondaryGroupedBackground.resolve(dark)
    /** `Color.primary` / `.primary`, indépendant d'une éventuelle couleur de réponse du thème. */
    val primaryText: Color get() = OnyxSystem.label.resolve(dark)
    /** `Color(.tertiarySystemBackground)`, notamment la commande d'une approbation. */
    val tertiarySurfaceColor: Color get() = OnyxSystem.tertiaryBackground.resolve(dark)

    val userBubbleColor: Color get() = palette.userBubble?.resolve(dark) ?: accent
    val userBubbleText: Color get() = palette.userBubbleTextColor?.resolve(dark) ?: userText
    val userText: Color get() = palette.userText.resolve(dark)

    /** Derrière une réponse. Les thèmes « à plat » posent la réponse sur la page. */
    val assistantBubble: Color? get() = if (palette.flatReplies) palette.background?.resolve(dark) else surfaceColor

    val linkColor: Color get() = palette.linkColor?.resolve(dark) ?: accent
    val checkboxColor: Color get() = palette.checkboxColor?.resolve(dark) ?: accent
    val quoteBarColor: Color get() = palette.quoteBarColor?.resolve(dark) ?: accent.copy(alpha = 0.6f)
    val tableHeaderTint: Color get() = palette.tableHeaderTint?.resolve(dark) ?: accent.copy(alpha = 0.10f)
    val promptPrefix: String? get() = palette.promptPrefix
    val promptColor: Color get() = palette.promptColor?.resolve(dark) ?: accent
    val usesGlass: Boolean get() = palette.usesGlass
    val bubbleRadius: Dp get() = palette.bubbleRadius
    val bubbleVerticalPadding: Dp get() = palette.bubbleVerticalPadding

    val secondaryText: Color get() = OnyxSystem.secondaryLabel.resolve(dark)
    val tertiaryText: Color get() = OnyxSystem.tertiaryLabel.resolve(dark)
    val quaternaryText: Color get() = OnyxSystem.quaternaryLabel.resolve(dark)
    val separator: Color get() = OnyxSystem.separator.resolve(dark)
    val fill: Color get() = OnyxSystem.fill.resolve(dark)

    val error: Color get() = OnyxSystem.red.resolve(dark)
    val warning: Color get() = OnyxSystem.orange.resolve(dark)
    val success: Color get() = OnyxSystem.green.resolve(dark)

    fun syntaxPalette(): SyntaxPalette = if (dark) palette.syntaxDark else palette.syntaxLight

    /** Une taille à l'échelle du texte de la conversation (`Settings.chatTextSize`). */
    private fun scaled(size: TextUnit): TextUnit = size * fontScale

    // MARK: - Typographie (tailles des styles )

    /** Texte des réponses. */
    val messageFont: TextStyle
        get() = TextStyle(
            fontFamily = responseFamily,
            fontSize = scaled(BodySize),
            lineHeight = scaled(BodySize) * 1.22f,
        )

    /** Titres dans les réponses; les thèmes CRT gardent une seule taille, en gras. */
    fun headingFont(level: Int): TextStyle {
        if (palette.promptPrefix != null) {
            return messageFont.copy(fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.SemiBold)
        }
        if (palette.monoFontName != null || palette.bodyFontName != null) {
            val size = when (level) {
                1 -> 20.sp
                2 -> 17.sp
                else -> 15.sp
            }
            return TextStyle(
                fontFamily = responseFamily,
                fontSize = scaled(size),
                lineHeight = scaled(size) * 1.25f,
                fontWeight = when (level) {
                    1 -> FontWeight.Bold
                    2 -> FontWeight.Bold
                    else -> FontWeight.SemiBold
                },
            )
        }
        val family = fonts.familyFor(palette.headingDesign)
        val size = when (level) {
            1 -> 20.sp
            2 -> 17.sp
            else -> 15.sp
        }
        return TextStyle(
            fontFamily = family,
            fontSize = scaled(size),
            lineHeight = scaled(size) * 1.25f,
            fontWeight = when (level) {
                1 -> FontWeight.SemiBold
                2 -> FontWeight.SemiBold
                else -> FontWeight.SemiBold
            },
        )
    }

    val tableFont: TextStyle
        get() {
            val named = palette.monoFontName ?: palette.bodyFontName
            val family = if (named != null) responseFamily
            else if (palette.monoTables) fonts.familyFor(FontDesign.MONOSPACED)
            else fonts.familyFor(FontDesign.DEFAULT)
            return TextStyle(fontFamily = family, fontSize = scaled(13.sp), lineHeight = scaled(18.sp))
        }

    /** Vos propres messages: le thème Code les garde en sans système. */
    val userFont: TextStyle
        get() = if (palette.sansUserFont) {
            TextStyle(fontFamily = fonts.familyFor(FontDesign.DEFAULT), fontSize = scaled(BodySize), lineHeight = scaled(BodySize) * 1.22f)
        } else {
            messageFont
        }

    val codeFont: TextStyle
        get() = TextStyle(
            fontFamily = fonts.familyFor(FontDesign.MONOSPACED, palette.monoFontName),
            fontSize = scaled(13.sp),
            lineHeight = scaled(19.sp),
        )

    private val responseFamily get() = fonts.familyFor(palette.replyDesign, palette.monoFontName ?: palette.bodyFontName)

    // Styles , aux tailles de.
    val largeTitle get() = style(34.sp, FontWeight.Normal, FontDesign.DEFAULT)
    val title get() = style(28.sp, FontWeight.Normal, FontDesign.DEFAULT)
    val title2 get() = style(22.sp, FontWeight.Normal, FontDesign.DEFAULT)
    val title3 get() = style(20.sp, FontWeight.Normal, FontDesign.DEFAULT)
    val headline get() = style(17.sp, FontWeight.SemiBold, FontDesign.DEFAULT)
    val body get() = style(BodySize, FontWeight.Normal, FontDesign.DEFAULT)
    val callout get() = style(16.sp, FontWeight.Normal, FontDesign.DEFAULT)
    val subheadline get() = style(15.sp, FontWeight.Normal, FontDesign.DEFAULT)
    val footnote get() = style(13.sp, FontWeight.Normal, FontDesign.DEFAULT)
    val caption get() = style(12.sp, FontWeight.Normal, FontDesign.DEFAULT)
    val caption2 get() = style(11.sp, FontWeight.Normal, FontDesign.DEFAULT)

    /** Le style système pour du texte monospace (métadonnées, compteurs). */
    val monospacedFootnote get() = codeFont.copy(fontSize = scaled(13.sp), lineHeight = scaled(18.sp))

    private fun style(size: TextUnit, weight: FontWeight, design: FontDesign): TextStyle =
        TextStyle(
            fontFamily = fonts.familyFor(design),
            fontSize = scaled(size),
            lineHeight = scaled(size) * 1.2f,
            fontWeight = weight,
        )

    /** Le même thème, avec le texte de la conversation mis à l'échelle (`chatTextSize`). */
    fun withFontScale(scale: Float): ResolvedTheme =
        if (scale == fontScale) this else ResolvedTheme(theme, palette, dark, fonts, scale)

    /**
     * Le même thème, avec d'autres couleurs de bulle et d'accent — celles d'un bot dans son chat
     * (`BotFace.chatColors`, « Bot colors in their chats »).
     */
    fun withColors(colors: ThemeColors?): ResolvedTheme =
        if (colors == null || colors.isEmpty) this
        else ResolvedTheme(theme, palette.withColors(colors), dark, fonts, fontScale)

    companion object {
        /** `body`: 17 pt dans. */
        val BodySize = 17.sp

        /**
         * Les cinq pas de « Chat text size », et leurs libellés, comme `ChatTextSize`.
         *
         * Le pas 0 suit le système; chaque pas multiplie les tailles du corps, ce qui suit
         * l'échelle de Dynamic Type (≈ ×1,11 par pas) sans dépendre d'un thème typographique.
         */
        val chatTextSizes: List<Int> = listOf(-2, -1, 0, 1, 2)

        fun chatTextLabel(steps: Int): String = when {
            steps <= -2 -> "Smallest"
            steps == -1 -> "Smaller"
            steps == 0 -> "Same as the phone"
            steps == 1 -> "Larger"
            else -> "Largest"
        }

        fun chatTextScale(steps: Int): Float = when {
            steps <= -2 -> 0.81f
            steps == -1 -> 0.90f
            steps == 0 -> 1f
            steps == 1 -> 1.11f
            else -> 1.23f
        }

        fun resolve(theme: OnyxTheme, dark: Boolean, fonts: OnyxFonts, colors: ThemeColors? = null): ResolvedTheme =
            ResolvedTheme(theme = theme, palette = theme.palette.withColors(colors), dark = dark, fonts = fonts)

        /** Le défaut du CompositionLocal: utile aux aperçus, jamais à l'écran. */
        fun fallback(): ResolvedTheme = resolve(OnyxTheme.STANDARD, dark = false, fonts = OnyxFonts.System)
    }
}

val LocalOnyxTheme = staticCompositionLocalOf { ResolvedTheme.fallback() }
