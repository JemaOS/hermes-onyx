package com.onyx.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Comme.
 *
 * Un thème est une valeur [Palette] — chaque décision de couleur, de police et de forme au même
 * endroit — avec, à l'intérieur des couleurs, une variante claire et une variante sombre.
 * Réglages → Apparence choisit clair, sombre ou système pour le thème actif.
 */
enum class OnyxTheme(
    val id: String,
    val label: String,
    val blurb: String,
) {
    // L'ordre de déclaration est celui de la liste dans les Réglages, comme.
    GITHUB_DARK("githubDark", "Hermes", "Hermes gold on near-black, set in Inter; the docs-site look."),
    STANDARD("standard", "Messages", "Onyx blue on the system greys."),
    PAPER("paper", "Paper", "Warm paper and ink; dark ink and parchment after dark."),
    SLATE("slate", "Slate", "Cool grey-blue with a steel accent. The office option."),
    TERMINAL("terminal", "Terminal", "Green phosphor on the Mac terminal's grey; your messages become shell prompts."),
    AMBER("amber", "Amber CRT", "Amber phosphor on warm black; your messages become shell prompts."),
    CLAUDE_CODE(
        "claudeCode",
        "Code",
        "Warm near-black and bone with a pale sky accent; serif replies, sans questions, monospaced code.",
    ),
    ;

    val palette: Palette
        get() = when (this) {
            STANDARD -> STANDARD_PALETTE
            PAPER -> PAPER_PALETTE
            SLATE -> SLATE_PALETTE
            TERMINAL -> TERMINAL_PALETTE
            AMBER -> AMBER_PALETTE
            GITHUB_DARK -> GITHUB_DARK_PALETTE
            CLAUDE_CODE -> CLAUDE_CODE_PALETTE
        }

    companion object {
        fun fromId(raw: String?): OnyxTheme =
            entries.firstOrNull { it.id == raw } ?: STANDARD
    }
}

/** Une couleur qui a une valeur en clair et une autre en sombre (le `dyn(_:_:)` de ). */
@Immutable
data class DynColor(val light: Color, val dark: Color) {
    /** La couleur de l'apparence demandée. */
    fun resolve(dark: Boolean): Color = if (dark) this.dark else this.light

    /** La même couleur dans les deux apparences. */
    constructor(color: Color) : this(color, color)

    fun alpha(value: Float): DynColor = DynColor(light.copy(alpha = light.alpha * value), dark.copy(alpha = dark.alpha * value))
}

/** `Font.Design`: la famille de repli quand le thème n'embarque pas de police. */
enum class FontDesign { DEFAULT, SERIF, MONOSPACED }

/** Les teintes de coloration syntaxique, portées de `SyntaxHighlighter.Palette`. */
@Immutable
data class SyntaxPalette(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val attribute: Color,
    val key: Color,
) {
    companion object {
        val LIGHT = SyntaxPalette(
            keyword = Color(0.61f, 0.13f, 0.53f),
            string = Color(0.77f, 0.10f, 0.09f),
            comment = Color(0.42f, 0.47f, 0.51f),
            number = Color(0.11f, 0.00f, 0.81f),
            type = Color(0.24f, 0.48f, 0.55f),
            attribute = Color(0.58f, 0.35f, 0.09f),
            key = Color(0.24f, 0.48f, 0.55f),
        )

        val DARK = SyntaxPalette(
            keyword = Color(0.99f, 0.47f, 0.66f),
            string = Color(0.99f, 0.53f, 0.40f),
            comment = Color(0.49f, 0.55f, 0.60f),
            number = Color(0.84f, 0.76f, 0.47f),
            type = Color(0.60f, 0.86f, 0.99f),
            attribute = Color(0.75f, 0.72f, 0.99f),
            key = Color(0.60f, 0.86f, 0.99f),
        )

        val TERMINAL_DARK = SyntaxPalette(
            keyword = Color(0.92f, 0.36f, 0.48f),
            string = Color(0.87f, 0.77f, 0.45f),
            comment = Color(0.50f, 0.58f, 0.50f),
            number = Color(0.71f, 0.66f, 0.93f),
            type = Color(0.90f, 0.42f, 0.38f),
            attribute = Color(0.60f, 0.71f, 1.00f),
            key = Color(0.60f, 0.71f, 1.00f),
        )

        val TERMINAL_LIGHT = SyntaxPalette(
            keyword = Color(0.72f, 0.18f, 0.32f),
            string = Color(0.60f, 0.48f, 0.10f),
            comment = Color(0.42f, 0.50f, 0.42f),
            number = Color(0.42f, 0.35f, 0.72f),
            type = Color(0.70f, 0.25f, 0.20f),
            attribute = Color(0.22f, 0.38f, 0.72f),
            key = Color(0.22f, 0.38f, 0.72f),
        )

        val HERMES_DARK = SyntaxPalette(
            keyword = Color(1.00f, 0.84f, 0.00f),
            string = Color(0.27f, 0.85f, 0.42f),
            comment = Color(0.55f, 0.55f, 0.50f),
            number = Color(0.95f, 0.72f, 0.25f),
            type = Color(0.86f, 0.86f, 0.80f),
            attribute = Color(1.00f, 0.84f, 0.00f),
            key = Color(0.27f, 0.85f, 0.42f),
        )

        val HERMES_LIGHT = SyntaxPalette(
            keyword = Color(0.55f, 0.45f, 0.00f),
            string = Color(0.13f, 0.55f, 0.25f),
            comment = Color(0.45f, 0.47f, 0.42f),
            number = Color(0.60f, 0.45f, 0.05f),
            type = Color(0.20f, 0.20f, 0.22f),
            attribute = Color(0.55f, 0.45f, 0.00f),
            key = Color(0.13f, 0.55f, 0.25f),
        )
    }
}

/**
 * Tout ce qu'un thème décide.
 *
 * Les champs facultatifs dérivent de `accent` (noté sur chacun), si bien qu'une palette ne
 * déclare que ce qui s'écarte du look « teinté par l'accent ».
 */
@Immutable
data class Palette(
    val accent: DynColor,
    /** Fond de page. `null` = fond système. */
    val background: DynColor? = null,
    /** Surface légèrement relevée (bulles de l'agent, lignes de feuilles). `null` = système. */
    val surface: DynColor? = null,
    /** Votre propre bulle. `null` = l'accent. */
    val userBubble: DynColor? = null,
    /** Texte sur votre bulle, et le glyphe des cercles d'accent (envoi, micro). */
    val userText: DynColor = DynColor(Color.White),
    /** Les réponses sont posées à plat sur la page, sous le nom de l'agent. */
    val flatReplies: Boolean = false,
    /** Couleur du corps de texte. `null` = primaire système. */
    val text: DynColor? = null,
    val linkColor: DynColor? = null,
    val checkboxColor: DynColor? = null,
    val quoteBarColor: DynColor? = null,
    val tableHeaderTint: DynColor? = null,
    /** Saisie style shell: vos messages s'affichent `❯ texte` au lieu d'une bulle. */
    val promptPrefix: String? = null,
    /** Toute la ligne de saisie — préfixe et vos mots. `null` = accent. */
    val promptColor: DynColor? = null,
    /** Famille pour le texte des réponses (paragraphes, listes). */
    val replyDesign: FontDesign = FontDesign.DEFAULT,
    /** Famille des titres dans les réponses. */
    val headingDesign: FontDesign = FontDesign.DEFAULT,
    /** Cellules de tableau en monospace. */
    val monoTables: Boolean = false,
    /** Police monospace embarquée pour les réponses, tableaux et code; `null` = système. */
    val monoFontName: String? = null,
    /** Police proportionnelle embarquée; les blocs de code gardent la monospace. */
    val bodyFontName: String? = null,
    /** Vos messages en sans système face à des réponses stylées (thème Code). */
    val sansUserFont: Boolean = false,
    val syntaxDark: SyntaxPalette = SyntaxPalette.DARK,
    val syntaxLight: SyntaxPalette = SyntaxPalette.LIGHT,
    val usesGlass: Boolean = false,
    val bubbleRadius: Dp = 18.dp,
    val bubbleVerticalPadding: Dp = 10.dp,
    /** Texte sur votre bulle quand sa couleur a été choisie; jamais rempli par une palette. */
    val userBubbleTextColor: DynColor? = null,
)

private fun rgb(red: Double, green: Double, blue: Double, alpha: Double = 1.0): Color =
    Color(red.toFloat(), green.toFloat(), blue.toFloat(), alpha.toFloat())

private fun dyn(light: Triple<Double, Double, Double>, dark: Triple<Double, Double, Double>): DynColor =
    DynColor(
        rgb(light.first, light.second, light.third),
        rgb(dark.first, dark.second, dark.third),
    )

private val STANDARD_PALETTE = Palette(
    accent = dyn(Triple(0.180, 0.545, 0.961), Triple(0.180, 0.545, 0.961)),
    flatReplies = true,
    usesGlass = true,
)

private val PAPER_PALETTE = Palette(
    accent = dyn(Triple(0.106, 0.114, 0.227), Triple(0.847, 0.784, 0.651)),
    surface = dyn(Triple(0.910, 0.894, 0.855), Triple(0.169, 0.149, 0.122)),
    userText = dyn(Triple(0.953, 0.945, 0.925), Triple(0.122, 0.106, 0.086)),
    flatReplies = true,
    text = dyn(Triple(0.106, 0.114, 0.227), Triple(0.929, 0.902, 0.847)),
    replyDesign = FontDesign.SERIF,
)

private val SLATE_PALETTE = Palette(
    accent = dyn(Triple(0.239, 0.353, 0.502), Triple(0.541, 0.651, 0.788)),
    background = dyn(Triple(0.855, 0.875, 0.905), Triple(0.043, 0.055, 0.075)),
    surface = dyn(Triple(1.0, 1.0, 1.0), Triple(0.122, 0.153, 0.200)),
    userText = dyn(Triple(1.0, 1.0, 1.0), Triple(0.059, 0.078, 0.106)),
    flatReplies = true,
    text = dyn(Triple(0.114, 0.141, 0.188), Triple(0.890, 0.910, 0.937)),
    usesGlass = true,
)

/** Un vrai terminal : profil Homebrew, police monospace partout. */
private val TERMINAL_PALETTE = Palette(
    accent = dyn(Triple(0.106, 0.541, 0.243), Triple(0.180, 0.830, 0.440)),
    background = dyn(Triple(0.933, 0.953, 0.933), Triple(0.113, 0.113, 0.113)),
    surface = dyn(Triple(0.875, 0.910, 0.875), Triple(0.155, 0.170, 0.155)),
    userText = dyn(Triple(1.0, 1.0, 1.0), Triple(0.0, 0.0, 0.0)),
    flatReplies = true,
    text = dyn(Triple(0.063, 0.141, 0.059), Triple(0.180, 0.830, 0.440)),
    linkColor = dyn(Triple(0.250, 0.400, 0.750), Triple(0.600, 0.710, 1.000)),
    quoteBarColor = dyn(Triple(0.480, 0.400, 0.750), Triple(0.710, 0.660, 0.930)).alpha(0.8f),
    tableHeaderTint = dyn(Triple(0.250, 0.400, 0.750), Triple(0.600, 0.710, 1.000)).alpha(0.14f),
    promptPrefix = "❯ ",
    promptColor = dyn(Triple(0.063, 0.141, 0.059), Triple(0.000, 0.880, 0.000)),
    replyDesign = FontDesign.MONOSPACED,
    headingDesign = FontDesign.MONOSPACED,
    monoTables = true,
    monoFontName = OnyxFonts.SOURCE_CODE_MEDIUM,
    syntaxDark = SyntaxPalette.TERMINAL_DARK,
    syntaxLight = SyntaxPalette.TERMINAL_LIGHT,
    bubbleRadius = 12.dp,
    bubbleVerticalPadding = 5.dp,
)

private val AMBER_PALETTE = Palette(
    accent = dyn(Triple(0.720, 0.450, 0.000), Triple(0.780, 0.530, 0.000)),
    background = dyn(Triple(0.965, 0.945, 0.900), Triple(0.030, 0.015, 0.000)),
    surface = dyn(Triple(0.925, 0.895, 0.830), Triple(0.090, 0.055, 0.010)),
    userText = dyn(Triple(1.0, 1.0, 1.0), Triple(0.050, 0.025, 0.000)),
    flatReplies = true,
    text = dyn(Triple(0.250, 0.160, 0.040), Triple(0.780, 0.530, 0.000)),
    promptPrefix = "❯ ",
    promptColor = dyn(Triple(0.250, 0.160, 0.040), Triple(1.000, 0.690, 0.000)),
    replyDesign = FontDesign.MONOSPACED,
    headingDesign = FontDesign.MONOSPACED,
    monoTables = true,
    monoFontName = OnyxFonts.SOURCE_CODE_MEDIUM,
    bubbleRadius = 12.dp,
    bubbleVerticalPadding = 5.dp,
)

/** Or pur sur quasi-noir, texte blanc cassé chaud, bulles dorées, Inter. */
private val GITHUB_DARK_PALETTE = Palette(
    accent = dyn(Triple(0.604, 0.494, 0.000), Triple(1.000, 0.843, 0.000)),
    background = dyn(Triple(0.973, 0.969, 0.949), Triple(0.106, 0.106, 0.114)),
    surface = dyn(Triple(0.933, 0.929, 0.902), Triple(0.145, 0.145, 0.157)),
    userText = dyn(Triple(1.0, 1.0, 1.0), Triple(0.106, 0.106, 0.114)),
    flatReplies = true,
    text = dyn(Triple(0.106, 0.106, 0.114), Triple(0.878, 0.878, 0.816)),
    bodyFontName = OnyxFonts.INTER_REGULAR,
    syntaxDark = SyntaxPalette.HERMES_DARK,
    syntaxLight = SyntaxPalette.HERMES_LIGHT,
    bubbleRadius = 12.dp,
)

private val CLAUDE_CODE_PALETTE = Palette(
    accent = DynColor(rgb(0.796, 0.941, 1.0)),
    background = dyn(Triple(0.973, 0.969, 0.949), Triple(0.106, 0.106, 0.114)),
    surface = dyn(Triple(0.933, 0.929, 0.902), Triple(0.145, 0.145, 0.157)),
    userBubble = DynColor(rgb(0.796, 0.941, 1.0)),
    userText = DynColor(rgb(0.106, 0.106, 0.114)),
    flatReplies = true,
    text = dyn(Triple(0.122, 0.118, 0.114), Triple(0.941, 0.933, 0.906)),
    linkColor = dyn(Triple(0.290, 0.420, 0.580), Triple(0.561, 0.659, 0.800)),
    checkboxColor = dyn(Triple(0.240, 0.480, 0.420), Triple(0.450, 0.720, 0.620)),
    quoteBarColor = dyn(Triple(0.480, 0.380, 0.520), Triple(0.698, 0.580, 0.733)).alpha(0.8f),
    tableHeaderTint = dyn(Triple(0.290, 0.420, 0.580), Triple(0.561, 0.659, 0.800)).alpha(0.16f),
    replyDesign = FontDesign.SERIF,
    headingDesign = FontDesign.SERIF,
    sansUserFont = true,
)

/**
 * Ce thème-ci avec les choix de couleurs du lecteur appliqués.
 *
 * Les choix remplacent les valeurs de la palette avant que quoi que ce soit en dérive: les liens,
 * les cases à cocher et le reste suivent donc un accent personnalisé exactement comme ils suivent
 * celui du thème — la `resolved(with:)` de.
 */
fun Palette.withColors(colors: ThemeColors?): Palette {
    if (colors == null || colors.isEmpty) return this
    var palette = this
    colors.accent?.let { pick ->
        palette = palette.copy(
            accent = DynColor(pick.color),
            userText = DynColor(pick.contrastingText),   // le glyphe des cercles d'accent
        )
    }
    colors.bubble?.let { pick ->
        palette = if (palette.promptPrefix != null) {
            // Les thèmes à invite n'ont pas de bulle: toute votre ligne prend la couleur.
            palette.copy(promptColor = DynColor(pick.color))
        } else {
            palette.copy(
                userBubble = DynColor(pick.color),
                userBubbleTextColor = DynColor(pick.contrastingText),
            )
        }
    }
    return palette
}

/** Les couleurs système qu'un thème peut laisser à la place des siennes. */
object OnyxSystem {
    val background = DynColor(Color.White, Color.Black)
    val secondaryBackground = DynColor(Color(0xFFF2F2F7), Color(0xFF1C1C1E))
    val tertiaryBackground = DynColor(Color(0xFFFFFFFF), Color(0xFF2C2C2E))
    /** Form dans une sheet: les fonds groupés du trait `elevated` d'UIKit. */
    val elevatedGroupedBackground = DynColor(Color(0xFFF2F2F7), Color(0xFF1C1C1E))
    val elevatedSecondaryGroupedBackground = DynColor(Color.White, Color(0xFF2C2C2E))
    val label = DynColor(Color.Black, Color.White)
    val secondaryLabel = DynColor(Color(0x993C3C43), Color(0x99EBEBF5))
    val tertiaryLabel = DynColor(Color(0x4D3C3C43), Color(0x4DEBEBF5))
    val quaternaryLabel = DynColor(Color(0x2E3C3C43), Color(0x29EBEBF5))
    val separator = DynColor(Color(0x4A3C3C43), Color(0x99545458))
    val fill = DynColor(Color(0x1F767680), Color(0x2E767680))

    // Les couleurs de signalisation: un thème ne les redéfinit jamais.
    val red = DynColor(Color(0xFFFF3B30), Color(0xFFFF453A))
    val orange = DynColor(Color(0xFFFF9500), Color(0xFFFF9F0A))
    val green = DynColor(Color(0xFF34C759), Color(0xFF30D158))
    val yellow = DynColor(Color(0xFFFFCC00), Color(0xFFFFD60A))
    val teal = DynColor(Color(0xFF30B0C7), Color(0xFF40C8E0))
    val indigo = DynColor(Color(0xFF5856D6), Color(0xFF5E5CE6))
}
