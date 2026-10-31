package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.roundToInt
import org.junit.Test

/**
 * **Le relevé des commandes du compositeur et de la barre d'onglets** — AVANT correction.
 *
 * Il n'affirme rien : il **imprime** ce que le code d'avant peint réellement, dans les deux
 * apparences, pour chaque thème. Le tableau du rapport est recopié de cette sortie, jamais ressaisi.
 */
class CompositeurMesureTest {

    private fun theme(nom: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = nom, palette = nom.palette, dark = dark, fonts = OnyxFonts.System)

    private fun name(c: Color): String = "#%02X%02X%02X".format(
        (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt(),
    )

    private fun r(a: Color, b: Color): String = "%.2f".format(OnyxContrast.ratio(a, b))

    /** L'encre résolue par `TrailingComposerButton.ink` pour l'envoi. */
    private fun sendInk(t: ResolvedTheme): Color = readableInk(t.userText, t.accent)

    /** L'encre du bouton vocal, telle que le compositeur la compose. */
    private fun talkInk(t: ResolvedTheme): Color = OnyxContrast.dimmed(
        OnyxContrast.over(t.secondaryText, t.fill), t.fill, OnyxContrast.ICON_MIN + 0.5f,
    )

    @Test
    fun `releve des commandes du compositeur et de la barre`() {
        println("== COMPOSITEUR + BARRE — couleurs peintes et rapports (AVANT) ==")
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { dark ->
                val t = theme(nom, dark)
                val apparence = if (dark) "sombre" else "clair"
                val ground = plaqueGround(t, selected = false)
                val groundOn = plaqueGround(t, selected = true)

                val send = r(sendInk(t), t.accent)
                val sendVsFond = r(t.accent, ground)
                val talk = r(talkInk(t), t.fill)
                val talkVsFond = r(t.fill, ground)
                val add = r(t.secondaryText, ground)
                val mic = r(if (false) t.error else t.secondaryText, ground)
                val expand = r(t.secondaryText, ground)
                val chipBg = ModelContrast.chip(t)
                val chip = r(readableInk(t.textColor, chipBg), chipBg)
                val tabOff = r(plaqueInk(t, selected = false, isFace = false), ground)
                val tabOn = r(plaqueInk(t, selected = true, isFace = false), groundOn)
                val tabFace = r(plaqueInk(t, selected = false, isFace = true), ground)

                println(
                    "%-14s %-6s | voile %s sur page %s -> sol %s | " .format(
                        nom.id, apparence, name(plaqueVeil(t)), name(OnyxSurfaces.page(t)), name(ground),
                    ),
                )
                println(
                    "   envoi  : encre %s / fond %s = %s  ·  cercle/fond = %s".format(
                        name(sendInk(t)), name(t.accent), send, sendVsFond,
                    ),
                )
                println(
                    "   vocal  : encre %s / fond %s = %s  ·  cercle/fond = %s".format(
                        name(talkInk(t)), name(t.fill), talk, talkVsFond,
                    ),
                )
                println("   +      : encre %s / sol %s = %s".format(name(t.secondaryText), name(ground), add))
                println("   micro  : encre %s / sol %s = %s".format(name(t.secondaryText), name(ground), mic))
                println("   agrand.: encre %s / sol %s = %s".format(name(t.secondaryText), name(ground), expand))
                println("   puce   : encre %s / fond %s = %s".format(name(readableInk(t.textColor, chipBg)), name(chipBg), chip))
                println("   onglet actif   : encre %s / sol %s = %s".format(name(plaqueInk(t, true, false)), name(groundOn), tabOn))
                println("   onglet au repos: encre %s / sol %s = %s".format(name(plaqueInk(t, false, false)), name(ground), tabOff))
                println("   onglet visage  : encre %s / sol %s = %s".format(name(plaqueInk(t, false, true)), name(ground), tabFace))
            }
        }
    }
}
