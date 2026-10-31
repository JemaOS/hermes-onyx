package com.onyx.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSocle

/** Réglages → Apparence : clair, sombre, ou comme le système (comme sur la référence). */
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
 * Un thème tel que l'app le rend : la palette plus l'apparence résolue.
 *
 * Les dérivations de l'accent vivent ici, une fois, si bien que le thème de base et la copie
 * résolue répondent identiquement — comme le protocole `ThemeStyle` de Theme.
 */
@Immutable
class ResolvedTheme(
    val theme: OnyxTheme,
    val palette: Palette,
    val dark: Boolean,
    val fonts: OnyxFonts,
    /** L'échelle du texte de la conversation (`chatTextSize`) ; 1 = la taille du système. */
    val fontScale: Float = 1f,
    /**
     * « Noir pur » (`pureBlack`) : en apparence **sombre** seulement, les fonds de page passent au
     * noir absolu (`#000000`) — plus doux sur un écran OLED, où un noir profond vaut un pixel
     * éteint. Sans effet en apparence claire : c'est un réglage d'OLED, pas de thème.
     */
    val pureBlack: Boolean = false,
    /**
     * « Halo » (`halo`) : une lumière colorée douce derrière le haut de chaque écran. Éteint, plus
     * rien n'est peint — la page est **plate**, sans dégradé. C'est un booléen pur (aucune autre
     * couleur n'en dépend), donc « éteint » aplatit vraiment tout.
     */
    val halo: Boolean = true,
) {
    val id: String get() = theme.id
    val label: String get() = theme.label
    val blurb: String get() = theme.blurb

    /** Le noir pur ne s'applique qu'en apparence sombre : en clair, le réglage reste sans effet. */
    val oledBlack: Boolean get() = pureBlack && dark

    val accent: Color get() = palette.accent.resolve(dark)

    /**
     * L'accent **en encre** : la couleur d'un texte ou d'une icône d'accent, par opposition à un
     * aplat. La référence a deux rôles (`AccentColors.accent` et `.text`) ; sans cette distinction,
     * l'accent d'un thème clair posé sur une vitre sombre descend sous le plancher de 3:1. Voir
     * [Palette.accentText].
     */
    val accentInk: Color get() = palette.accentText?.resolve(dark) ?: accent
    val pageColor: Color
        get() = if (oledBlack) {
            Color.Black
        } else {
            palette.background?.resolve(dark) ?: OnyxSystem.background.resolve(dark)
        }
    val surfaceColor: Color get() = palette.surface?.resolve(dark) ?: OnyxSystem.secondaryBackground.resolve(dark)
    val textColor: Color get() = palette.text?.resolve(dark) ?: OnyxSystem.label.resolve(dark)

    /** Les `Form` de Settings/Setup restent ceux de la référence ; la palette colore leur accent, pas leurs cartes. */
    val formPageColor: Color
        get() = if (oledBlack) Color.Black else OnyxSystem.elevatedGroupedBackground.resolve(dark)
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
    /**
     * L'encre d'un texte secondaire posé sur une **carte** claire (l'aperçu d'une carte du Flux,
     * la note de sa tête).
     *
     * Le gris système y tombe à **3,29:1** (relevé au pixel sur 1.0.0+546, 411 dp) : sous le
     * plancher de 4,5:1 de la charte. Cette encre, elle, mesure **4,79:1** sur la carte `#F2F2F7`
     * et **5,41:1** sur la page blanche. Elle est **nommée à part** — et non substituée au jeton —
     * parce que `FrostTest` verrouille le contraste des vitres sur 1260 combinaisons : un gris plus
     * sombre y échoue sur les palettes sombres regardées en clair (`githubDark`), où la vitre est
     * sombre. Les surfaces claires de la maison, elles, la portent sans réserve.
     */
    val secondaryOnCard: Color
        get() = if (!dark && OnyxContrast.luminance(surfaceColor) > 0.5f) CardSecondaryInk else secondaryText
    val tertiaryText: Color get() = OnyxSystem.tertiaryLabel.resolve(dark)
    val quaternaryText: Color get() = OnyxSystem.quaternaryLabel.resolve(dark)
    val separator: Color get() = OnyxSystem.separator.resolve(dark)
    val fill: Color get() = OnyxSystem.fill.resolve(dark)

    /**
     * La **scène** de l'aperçu : la couleur des bandes autour de l'écran du PC.
     *
     * Un écran de PC est 16:9 ; un téléphone est haut. Il reste donc toujours des bandes, et elles
     * ne doivent jamais être du blanc : une image de bureau posée sur un blanc immense fait
     * « page vide », pas « écran en direct ». La scène est le fond du thème **assombri** — elle
     * garde la teinte du thème choisi (une scène froide pour Slate, chaude pour Paper) et reste
     * sombre dans toutes les apparences, comme la vitre d'un lecteur vidéo.
     */
    val stageColor: Color get() = lerp(pageColor, Color.Black, if (dark) 0.55f else 0.88f)

    /** Le texte de la scène : toujours lisible sur [stageColor], quel que soit le thème. */
    val stageText: Color get() = lerp(primaryText, Color.White, if (dark) 0.72f else 1f)

    val error: Color get() = OnyxSystem.red.resolve(dark)
    val warning: Color get() = OnyxSystem.orange.resolve(dark)
    val success: Color get() = OnyxSystem.green.resolve(dark)

    fun syntaxPalette(): SyntaxPalette = if (dark) palette.syntaxDark else palette.syntaxLight

    /** Une taille à l'échelle du texte de la conversation (`Settings.chatTextSize`). */
    private fun scaled(size: TextUnit): TextUnit = size * fontScale

    // MARK: - Typographie (tailles des styles la référence)

    /** Texte des réponses. */
    val messageFont: TextStyle
        get() = TextStyle(
            fontFamily = responseFamily,
            fontSize = scaled(BodySize),
            lineHeight = scaled(BodySize) * 1.22f,
        )

    /** Titres dans les réponses ; les thèmes CRT gardent une seule taille, en gras. */
    fun headingFont(level: Int): TextStyle {
        if (palette.promptPrefix != null) {
            return messageFont.copy(fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.SemiBold)
        }
        if (palette.monoFontName != null || palette.bodyFontName != null) {
            val size = when (level) {
                1 -> OnyxSocle.Title
                2 -> OnyxSocle.Title
                else -> OnyxSocle.Body
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
        // Un titre de réponse est un **titre de carte** (16) ; un sous-titre, un corps (15).
        val size = when (level) {
            1 -> OnyxSocle.Title
            2 -> OnyxSocle.Title
            else -> OnyxSocle.Body
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
            return TextStyle(fontFamily = family, fontSize = scaled(OnyxSocle.Meta), lineHeight = scaled(OnyxSocle.Meta) * 1.4f)
        }

    /** Vos propres messages : le thème Code les garde en sans système. */
    val userFont: TextStyle
        get() = if (palette.sansUserFont) {
            TextStyle(fontFamily = fonts.familyFor(FontDesign.DEFAULT), fontSize = scaled(BodySize), lineHeight = scaled(BodySize) * 1.22f)
        } else {
            messageFont
        }

    val codeFont: TextStyle
        get() = TextStyle(
            fontFamily = fonts.familyFor(FontDesign.MONOSPACED, palette.monoFontName),
            fontSize = scaled(OnyxSocle.Meta),
            lineHeight = scaled(OnyxSocle.Meta) * 1.45f,
        )

    private val responseFamily get() = fonts.familyFor(palette.replyDesign, palette.monoFontName ?: palette.bodyFontName)

    /**
     * **Les styles nommés, ramenés aux six corps du socle.**
     *
     * Les noms restent ceux de la référence — les écrans les appellent par leur rôle — mais le **corps** ne
     * s'écrit plus ici : il vient de [OnyxSocle]. Une seule échelle, donc, pour toutes les routes :
     * un titre d'écran est **toujours** 30, un titre de feuille **toujours** 26, un titre de carte
     * **toujours** 16, un corps **toujours** 15, une méta **toujours** 12, un plancher **toujours**
     * 11. Les valeurs la référence intermédiaires (34, 28, 22, 20, 17, 16, 15, 13) n'ont plus de
     * traduction d'usage : 22 et 28 tombent au même titre, 20 et 17 au même titre de carte, 16 et 15
     * au même corps. Quand deux rôles partagent un corps, c'est le **poids** qui les sépare — plus
     * jamais un demi-point.
     */
    val largeTitle get() = style(OnyxSocle.Display, FontWeight.Normal, FontDesign.DEFAULT)
    val title get() = style(OnyxSocle.Heading, FontWeight.Normal, FontDesign.DEFAULT)
    val title2 get() = style(OnyxSocle.Heading, FontWeight.Normal, FontDesign.DEFAULT)
    val title3 get() = style(OnyxSocle.Title, FontWeight.Normal, FontDesign.DEFAULT)
    val headline get() = style(OnyxSocle.Title, FontWeight.SemiBold, FontDesign.DEFAULT)
    val body get() = style(BodySize, FontWeight.Normal, FontDesign.DEFAULT)
    val callout get() = style(BodySize, FontWeight.Normal, FontDesign.DEFAULT)
    val subheadline get() = style(BodySize, FontWeight.Normal, FontDesign.DEFAULT)
    val footnote get() = style(OnyxSocle.Meta, FontWeight.Normal, FontDesign.DEFAULT)
    val caption get() = style(OnyxSocle.Meta, FontWeight.Normal, FontDesign.DEFAULT)
    val caption2 get() = style(OnyxSocle.Micro, FontWeight.Normal, FontDesign.DEFAULT)

    /** Le style système pour du texte monospace (métadonnées, compteurs) : une méta, au corps des métas. */
    val monospacedFootnote get() = codeFont.copy(fontSize = scaled(OnyxSocle.Meta), lineHeight = scaled(OnyxSocle.Meta) * 1.4f)

    private fun style(size: TextUnit, weight: FontWeight, design: FontDesign): TextStyle =
        TextStyle(
            fontFamily = fonts.familyFor(design),
            fontSize = scaled(size),
            lineHeight = scaled(size) * 1.2f,
            fontWeight = weight,
        )

    /** Le même thème, avec le texte de la conversation mis à l'échelle (`chatTextSize`). */
    fun withFontScale(scale: Float): ResolvedTheme =
        if (scale == fontScale) this else ResolvedTheme(theme, palette, dark, fonts, scale, pureBlack, halo)

    /**
     * Le même thème, avec d'autres couleurs de bulle et d'accent — celles d'un bot dans son chat
     * (`BotFace.chatColors`, « Bot colors in their chats »).
     */
    fun withColors(colors: ThemeColors?): ResolvedTheme =
        if (colors == null || colors.isEmpty) this
        else ResolvedTheme(theme, palette.withColors(colors), dark, fonts, fontScale, pureBlack, halo)

    /** Le même thème, avec « Noir pur » et « Halo » tels que les Réglages les portent. */
    fun withDisplay(pureBlack: Boolean = this.pureBlack, halo: Boolean = this.halo): ResolvedTheme =
        if (pureBlack == this.pureBlack && halo == this.halo) this
        else ResolvedTheme(theme, palette, dark, fonts, fontScale, pureBlack, halo)

    companion object {
        /**
         * `body` : le **corps de texte du socle** (15 sp, `.mtx2`).
         *
         * C'était 17 — la valeur de la référence, une échelle qui n'est pas la nôtre : elle posait un
         * corps de 17 à côté des six corps du socle, et le texte des réponses ne s'alignait donc sur
         * aucune route. Le corps de l'app est 15 ; « Chat text size » reste un **facteur** appliqué
         * par-dessus ([chatTextScale]), jamais un corps à part.
         */
        val BodySize = OnyxSocle.Body

        /**
         * Les cinq pas de « Chat text size », et leurs libellés, comme `ChatTextSize` de la référence.
         *
         * Le pas 0 suit le système ; chaque pas multiplie les tailles du corps, ce qui suit
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

        fun resolve(
            theme: OnyxTheme,
            dark: Boolean,
            fonts: OnyxFonts,
            colors: ThemeColors? = null,
            pureBlack: Boolean = false,
            halo: Boolean = true,
        ): ResolvedTheme =
            ResolvedTheme(
                theme = theme,
                palette = theme.palette.withColors(colors),
                dark = dark,
                fonts = fonts,
                pureBlack = pureBlack,
                halo = halo,
            )

        /** Le défaut du CompositionLocal : utile aux aperçus, jamais à l'écran. */
        fun fallback(): ResolvedTheme = resolve(OnyxTheme.STANDARD, dark = false, fonts = OnyxFonts.System)
    }
}

val LocalOnyxTheme = staticCompositionLocalOf { ResolvedTheme.fallback() }

/**
 * L'encre des textes secondaires posés sur une carte claire : le **tertiaire de la référence**
 * (`#6B6B75`, `Palette.LightText4`). Voir [ResolvedTheme.secondaryOnCard].
 *
 * La valeur de la maison était `#6A6A6F` — à un point de la référence sur les trois canaux. Elle est
 * ramenée à la référence pour qu'il n'y ait qu'une seule valeur dans l'app.
 */
internal val CardSecondaryInk = ReferencePalette.LightText4
