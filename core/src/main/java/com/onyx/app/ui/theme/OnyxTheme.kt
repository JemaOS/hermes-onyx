package com.onyx.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Port de `Echo/Models/Theme`.
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
    // L'ordre de déclaration est celui de la liste dans les Réglages, comme sur la référence.
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

/** Une couleur qui a une valeur en clair et une autre en sombre (le `dyn(_:_:)` de Theme). */
@Immutable
data class DynColor(val light: Color, val dark: Color) {
    /** La couleur de l'apparence demandée. */
    fun resolve(dark: Boolean): Color = if (dark) this.dark else this.light

    /** La même couleur dans les deux apparences. */
    constructor(color: Color) : this(color, color)

    fun alpha(value: Float): DynColor = DynColor(light.copy(alpha = light.alpha * value), dark.copy(alpha = dark.alpha * value))
}

/** `Font.Design` : la famille de repli quand le thème n'embarque pas de police. */
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
    /**
     * L'accent **en encre** — celui d'un texte ou d'une icône, et non d'un aplat.
     *
     * La référence distingue les deux : `AccentColors.accent` remplit une bulle ou un bouton,
     * `AccentColors.text` écrit l'accent. La raison est mesurable : sur un fond **sombre**, le bleu
     * de remplissage (`#2F6BF5`) tombe à **2,85:1** en icône sur une vitre pleine — sous le plancher
     * de 3:1 — et la référence le remplace alors par `#7AA5FF`, plus clair. `null` = l'accent
     * lui-même (le cas des six thèmes qui n'ont qu'une couleur).
     */
    val accentText: DynColor? = null,
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
    /** Saisie style shell : vos messages s'affichent `❯ texte` au lieu d'une bulle. */
    val promptPrefix: String? = null,
    /** Toute la ligne de saisie — préfixe et vos mots. `null` = accent. */
    val promptColor: DynColor? = null,
    /** Famille pour le texte des réponses (paragraphes, listes). */
    val replyDesign: FontDesign = FontDesign.DEFAULT,
    /** Famille des titres dans les réponses. */
    val headingDesign: FontDesign = FontDesign.DEFAULT,
    /** Cellules de tableau en monospace. */
    val monoTables: Boolean = false,
    /** Police monospace embarquée pour les réponses, tableaux et code ; `null` = système. */
    val monoFontName: String? = null,
    /** Police proportionnelle embarquée ; les blocs de code gardent la monospace. */
    val bodyFontName: String? = null,
    /** Vos messages en sans système face à des réponses stylées (thème Code). */
    val sansUserFont: Boolean = false,
    val syntaxDark: SyntaxPalette = SyntaxPalette.DARK,
    val syntaxLight: SyntaxPalette = SyntaxPalette.LIGHT,
    val usesGlass: Boolean = false,
    val bubbleRadius: Dp = 18.dp,
    val bubbleVerticalPadding: Dp = 10.dp,
    /** Texte sur votre bulle quand sa couleur a été choisie ; jamais rempli par une palette. */
    val userBubbleTextColor: DynColor? = null,
)

private fun rgb(red: Double, green: Double, blue: Double, alpha: Double = 1.0): Color =
    Color(red.toFloat(), green.toFloat(), blue.toFloat(), alpha.toFloat())

private fun dyn(light: Triple<Double, Double, Double>, dark: Triple<Double, Double, Double>): DynColor =
    DynColor(
        rgb(light.first, light.second, light.third),
        rgb(dark.first, dark.second, dark.third),
    )

/** Le bleu de la référence (`AccentPalette.Blue`) : **la** couleur de l'app, lue dans sa source. */
private val REFERENCE_ACCENT = DynColor(ReferencePalette.Accent)

/**
 * L'accent **en encre**, les deux variantes de la référence : `AccentColors.text`.
 *
 * `#1F56D6` en clair et `#7AA5FF` en sombre — la seconde est celle qui tient le plancher d'icône de
 * 3:1 sur une vitre sombre, là où le bleu de remplissage mesure 2,85:1.
 */
private val REFERENCE_ACCENT_TEXT =
    DynColor(light = ReferencePalette.AccentTextLight, dark = ReferencePalette.AccentTextDark)

private val STANDARD_PALETTE = Palette(
    accent = REFERENCE_ACCENT,
    accentText = REFERENCE_ACCENT_TEXT,
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

/** Un vrai terminal Mac : le profil Homebrew de la référence Terminal, SF Mono partout. */
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
 * Les choix remplacent les valeurs de la palette avant que quoi que ce soit en dérive : les liens,
 * les cases à cocher et le reste suivent donc un accent personnalisé exactement comme ils suivent
 * celui du thème — la `resolved(with:)` de Theme.
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
            // Les thèmes à invite n'ont pas de bulle : toute votre ligne prend la couleur.
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

/**
 * Les rôles neutres que **tous** les thèmes laissent à la place des leurs, et que les écrans lisent
 * directement (formulaires, encres, séparateurs, signaux).
 *
 * **Le nom est historique, les valeurs ne le sont plus.** Ces rôles portaient les gris du système
 * de la référence : page blanche pure, cartes `#F2F2F7`, encres secondaires translucides, séparateurs à 29 %
 * et 60 % d'opacité. Ils portent maintenant, **1:1**, les valeurs relevées dans la source de la
 * référence ([ReferencePalette]). Le nom reste pour ne pas toucher les 37 points d'appel, mais la
 * table ci-dessous **est** celle du rapport — chaque ligne dit d'où elle vient.
 *
 * Ce qui change pour l'œil, rôle par rôle :
 *
 * | rôle | avant (référence) | après (référence) |
 * |---|---|---|
 * | page | `#FFFFFF` / `#000000` | `#F7F7F9` / `#0A0A0C` |
 * | surface | `#F2F2F7` / `#1C1C1E` | `#FFFFFF` / `#16161A` |
 * | creux | `#FFFFFF` / `#2C2C2E` | `#F1F1F5` / `#1C1C22` |
 * | séparateur | `#3C3C43` 29 % / `#545458` 60 % | `#0A0A1E` 8 % / blanc 7 % |
 * | encre secondaire | `#3C3C43` 60 % / `#EBEBF5` 60 % | `#27272A` / `#D4D4D8` |
 * | danger | `#FF3B30` / `#FF453A` | `#CF222E` / `#F85149` |
 * | avertissement | `#FF9500` / `#FF9F0A` | `#B76E00` / `#F5A524` |
 * | succès | `#34C759` / `#30D158` | `#1A7F37` / `#3FB950` |
 *
 * **Trois couleurs saturées ont disparu** — `yellow`, `teal`, `indigo`. Aucun écran ne les lisait
 * (vérifié sur tout l'arbre) : elles ne servaient qu'à pouvoir en peindre un jour. La règle de la
 * maison n'en admet plus que **cinq** en dehors du neutre : l'accent, le danger, le succès,
 * l'avertissement et le direct.
 */
object OnyxSystem {
    val background = DynColor(ReferencePalette.LightPage, ReferencePalette.DarkPage)
    val secondaryBackground = DynColor(ReferencePalette.LightSurface, ReferencePalette.DarkSurface)
    val tertiaryBackground = DynColor(ReferencePalette.LightSurface2, ReferencePalette.DarkSurface2)
    /** Form dans une sheet : la page de la référence, puis sa surface. */
    val elevatedGroupedBackground = DynColor(ReferencePalette.LightPage, ReferencePalette.DarkPage)
    val elevatedSecondaryGroupedBackground = DynColor(ReferencePalette.LightSurface, ReferencePalette.DarkSurface)
    val label = DynColor(ReferencePalette.LightText, ReferencePalette.DarkText)
    val secondaryLabel = DynColor(ReferencePalette.LightText2, ReferencePalette.DarkText2)
    val tertiaryLabel = DynColor(ReferencePalette.LightText4, ReferencePalette.DarkText4)
    val quaternaryLabel = DynColor(ReferencePalette.LightText5, ReferencePalette.DarkText5)
    val separator = DynColor(ReferencePalette.LightLine, ReferencePalette.DarkLine)
    /** Le fond d'un contrôle : le **deuxième** barreau de la référence, opaque. */
    val fill = DynColor(ReferencePalette.LightSurface2, ReferencePalette.DarkSurface2)

    // Les couleurs de signalisation : un thème ne les redéfinit jamais — et elles sont
    // maintenant celles de la référence, pas celles de la référence.
    val red = ReferencePalette.Danger
    val orange = ReferencePalette.Warning
    val green = ReferencePalette.Success
}
