package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.chat.AttachmentLimits
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les encres de la feuille d'ajout, **mesurées sur tous les thèmes** et dans les deux apparences.
 *
 * Ce qui est en jeu : les quatre libellés (4,5:1 pour du texte), les quatre icônes de pastille
 * (3:1 pour un trait) et le sous-titre, qui porte les chiffres des limites. Un accent pâle sur sa
 * propre teinte, ou un libellé secondaire du système posé tel quel, tombent sous le seuil sans que
 * rien ne le dise — d'où la mesure, qui imprime le tableau des valeurs relevées.
 */
class AttachmentSheetContrastTest {

    private fun theme(name: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = name, palette = name.palette, dark = dark, fonts = OnyxFonts.System)

    @Test
    fun `chaque encre de la feuille tient son seuil, sur tous les themes et dans les deux apparences`() {
        val lines = mutableListOf<String>()
        var checked = 0
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val resolved = theme(name, dark)
                val apparence = if (dark) "sombre" else "clair"
                val panneau = OnyxSurfaces.surfaceHigh(resolved)
                // La vignette de la maison : sa teinte est celle de l'accent, composée sur le
                // panneau ; son encre est mesurée par la vignette, pas recopiée ici.
                val pastille = thumbnailTint(resolved.accent, resolved)

                fun line(what: String, ink: Color, fond: Color, ratio: Float, min: Float) {
                    lines += "%-10s %-6s %-22s %-9s sur %-9s %6.2f  (min %.1f)"
                        .format(name.id, apparence, what, couleur(ink), couleur(fond), ratio, min)
                    assertTrue(
                        "${name.id} $apparence : $what n'atteint que $ratio (min $min)",
                        ratio >= min,
                    )
                }

                val libelle = OnyxContrast.ratioOver(resolved.textColor, panneau)
                line("le titre", resolved.textColor, panneau, libelle, OnyxContrast.TEXT_MIN)
                checked++

                val sousTitre = OnyxContrast.ratioOver(sheetSubtitleInk(resolved), panneau)
                line("le sous-titre", sheetSubtitleInk(resolved), panneau, sousTitre, OnyxContrast.TEXT_MIN)
                checked++

                val icone = OnyxContrast.ratio(thumbnailInk(resolved.accent, resolved), pastille)
                line(
                    "l'icône (la vignette)",
                    thumbnailInk(resolved.accent, resolved), pastille, icone, OnyxContrast.ICON_MIN,
                )
                checked++

                // La pastille garde sa teinte (un carré invisible ne serait pas une pastille), et
                // l'icône tient son seuil : c'est la mesure qui règle les deux, pas l'œil.
                val visible = OnyxContrast.ratio(pastille, panneau)
                assertTrue("${name.id} $apparence : la pastille ne se voit pas ($visible)", visible > 1.05f)
                assertTrue("${name.id} $apparence : l'icône sous le seuil ($icone)", icone >= OnyxContrast.ICON_MIN)
            }
        }
        println("Contrastes de la feuille d'ajout (« $SousTitreApercu ») :")
        lines.forEach(::println)
        assertTrue("$checked mesures", checked >= OnyxTheme.entries.size * 2 * 3)
    }

    /**
     * La **famille destructrice** de la pastille : la feuille de compte y met « Se déconnecter ».
     *
     * Elle suit exactement la règle de l'accent — la pastille se voit, l'icône tient 3:1 dessus —,
     * avec la couleur d'alerte du thème. Sans cette mesure, la ligne la plus lourde de la feuille
     * aurait pu être la moins lisible.
     */
    @Test
    fun `la pastille destructrice tient les memes seuils que l accent`() {
        val lines = mutableListOf<String>()
        var checked = 0
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val resolved = theme(name, dark)
                val apparence = if (dark) "sombre" else "clair"
                val panneau = OnyxSurfaces.surfaceHigh(resolved)
                val pastille = sheetBadgeFill(resolved, resolved.error)
                val icone = sheetBadgeInk(resolved, resolved.error)

                val visible = OnyxContrast.ratio(pastille, panneau)
                val encre = OnyxContrast.ratio(icone, pastille)
                lines += "%-10s %-6s %-22s pastille %-9s icône %-9s %6.2f / %6.2f"
                    .format(name.id, apparence, "alerte", couleur(pastille), couleur(icone), visible, encre)

                assertTrue(
                    "${name.id} $apparence : la pastille d'alerte ne se voit pas ($visible)",
                    visible > 1.05f,
                )
                assertTrue(
                    "${name.id} $apparence : l'icône d'alerte sous le seuil ($encre)",
                    encre >= OnyxContrast.ICON_MIN,
                )
                checked += 2
            }
        }
        println("Contrastes de la pastille destructrice de la feuille :")
        lines.forEach(::println)
        assertTrue("$checked mesures", checked >= OnyxTheme.entries.size * 2 * 2)
    }

    private fun couleur(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
    )

    private companion object {
        /** Le sous-titre tel qu'il sera lu à l'écran, pour que le journal du test le porte aussi. */
        val SousTitreApercu: String = AttachmentLimits.subtitle()
    }
}
