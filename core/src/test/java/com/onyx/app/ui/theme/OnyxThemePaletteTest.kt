package com.onyx.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

private fun rgb(red: Double, green: Double, blue: Double): Color =
    Color(red.toFloat(), green.toFloat(), blue.toFloat())

private fun dyn(light: Triple<Double, Double, Double>, dark: Triple<Double, Double, Double>): DynColor =
    DynColor(
        rgb(light.first, light.second, light.third),
        rgb(dark.first, dark.second, dark.third),
    )

private val expectedPalettes = mapOf(
    OnyxTheme.STANDARD to Palette(
        accent = DynColor(ReferencePalette.Accent),
        accentText = DynColor(
            light = ReferencePalette.AccentTextLight,
            dark = ReferencePalette.AccentTextDark,
        ),
        flatReplies = true,
        usesGlass = true,
    ),
    OnyxTheme.PAPER to Palette(
        accent = dyn(Triple(0.106, 0.114, 0.227), Triple(0.847, 0.784, 0.651)),
        surface = dyn(Triple(0.910, 0.894, 0.855), Triple(0.169, 0.149, 0.122)),
        userText = dyn(Triple(0.953, 0.945, 0.925), Triple(0.122, 0.106, 0.086)),
        flatReplies = true,
        text = dyn(Triple(0.106, 0.114, 0.227), Triple(0.929, 0.902, 0.847)),
        replyDesign = FontDesign.SERIF,
    ),
    OnyxTheme.SLATE to Palette(
        accent = dyn(Triple(0.239, 0.353, 0.502), Triple(0.541, 0.651, 0.788)),
        background = dyn(Triple(0.855, 0.875, 0.905), Triple(0.043, 0.055, 0.075)),
        surface = dyn(Triple(1.0, 1.0, 1.0), Triple(0.122, 0.153, 0.200)),
        userText = dyn(Triple(1.0, 1.0, 1.0), Triple(0.059, 0.078, 0.106)),
        flatReplies = true,
        text = dyn(Triple(0.114, 0.141, 0.188), Triple(0.890, 0.910, 0.937)),
        usesGlass = true,
    ),
    OnyxTheme.TERMINAL to Palette(
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
    ),
    OnyxTheme.AMBER to Palette(
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
    ),
    OnyxTheme.GITHUB_DARK to Palette(
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
    ),
    OnyxTheme.CLAUDE_CODE to Palette(
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
    ),
)

class OnyxThemePaletteTest {
    @Test
    fun `chaque palette et chaque token resolu correspondent a Theme référence`() {
        assertEquals(OnyxTheme.entries.toSet(), expectedPalettes.keys)

        expectedPalettes.forEach { (theme, expected) ->
            assertEquals(theme.id, expected, theme.palette)
            listOf(false, true).forEach { dark ->
                val actual = ResolvedTheme.resolve(theme, dark, OnyxFonts.System)
                val appearance = "${theme.id}/${if (dark) "dark" else "light"}"
                val accent = expected.accent.resolve(dark)
                val page = expected.background?.resolve(dark) ?: OnyxSystem.background.resolve(dark)
                val surface = expected.surface?.resolve(dark) ?: OnyxSystem.secondaryBackground.resolve(dark)

                assertEquals(appearance, accent, actual.accent)
                assertEquals(appearance, page, actual.pageColor)
                assertEquals(appearance, surface, actual.surfaceColor)
                assertEquals(
                    appearance,
                    expected.text?.resolve(dark) ?: OnyxSystem.label.resolve(dark),
                    actual.textColor,
                )
                assertEquals(appearance, expected.userBubble?.resolve(dark) ?: accent, actual.userBubbleColor)
                assertEquals(appearance, expected.userText.resolve(dark), actual.userText)
                assertEquals(
                    appearance,
                    expected.userBubbleTextColor?.resolve(dark) ?: expected.userText.resolve(dark),
                    actual.userBubbleText,
                )
                assertEquals(
                    appearance,
                    if (expected.flatReplies) expected.background?.resolve(dark) else surface,
                    actual.assistantBubble,
                )
                assertEquals(appearance, expected.linkColor?.resolve(dark) ?: accent, actual.linkColor)
                assertEquals(appearance, expected.checkboxColor?.resolve(dark) ?: accent, actual.checkboxColor)
                assertEquals(
                    appearance,
                    expected.quoteBarColor?.resolve(dark) ?: accent.copy(alpha = 0.6f),
                    actual.quoteBarColor,
                )
                assertEquals(
                    appearance,
                    expected.tableHeaderTint?.resolve(dark) ?: accent.copy(alpha = 0.10f),
                    actual.tableHeaderTint,
                )
                assertEquals(appearance, expected.promptColor?.resolve(dark) ?: accent, actual.promptColor)
                assertEquals(appearance, expected.promptPrefix, actual.promptPrefix)
                assertEquals(appearance, expected.bubbleRadius, actual.bubbleRadius)
                assertEquals(appearance, expected.bubbleVerticalPadding, actual.bubbleVerticalPadding)
                assertEquals(appearance, expected.usesGlass, actual.usesGlass)
                assertEquals(
                    appearance,
                    if (dark) expected.syntaxDark else expected.syntaxLight,
                    actual.syntaxPalette(),
                )
            }
        }
    }
}
