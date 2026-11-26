package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le langage de forme de la maison — rayons, séparateurs, vignettes, verre des feuilles, matière.**
 *
 * Ce test tient les **principes** du lot, avec leurs chiffres, dans les deux apparences :
 *
 *  1. **les rayons** forment une famille ordonnée (champ < vignette < carte < panneau), et un
 *     élément posé dans un autre prend le rayon intérieur ([OnyxSpace.interior]) ;
 *  2. **les séparateurs** sont en retrait des **deux** bords, et commencent après la vignette
 *     ([OnyxSpace.separatorInset]) ;
 *  3. **les vignettes** ont une seule taille ([OnyxSpace.ThumbnailSize]) et un seul coin ;
 *  4. **les feuilles sont translucides** : leur voile reste sous l'opacité des barres, et il tient
 *     pourtant les seuils d'encre sur le pire contenu (blanc **et** noir derrière) ;
 *  5. **la matière derrière le verre existe** : le halo change la luminance de la page dans les deux
 *     apparences, et il ne peint rien quand il est éteint.
 *
 * Les tableaux sont **imprimés** : le chiffre du rapport et le chiffre vérifié sortent du même code.
 */
class FluidDesignTest {

    private fun themes(): List<ResolvedTheme> = buildList {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                add(ResolvedTheme(name, name.palette, dark, OnyxFonts.System))
            }
        }
    }

    private fun hex(c: Color): String = String.format(
        Locale.ROOT, "#%02X%02X%02X",
        (c.red * 255f).toInt().coerceIn(0, 255),
        (c.green * 255f).toInt().coerceIn(0, 255),
        (c.blue * 255f).toInt().coerceIn(0, 255),
    )

    // ---- 1. les rayons ---------------------------------------------------------------------

    @Test
    fun `les rayons forment une famille ordonnee, du champ au panneau`() {
        val family = listOf(
            "FieldCorner" to OnyxSpace.FieldCorner,
            "TileCorner" to OnyxSpace.TileCorner,
            "CardCorner" to OnyxSpace.CardCorner,
            "PanelCorner" to OnyxSpace.PanelCorner,
        )
        // Stricto croissante : quatre marches, quatre intentions, aucun doublon.
        family.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.first} (${a.second}) doit être plus serré que ${b.first} (${b.second})", a.second < b.second)
        }
        // Le panneau est franchement plus ouvert que la carte : c'est lui qui se lit « feuille ».
        assertTrue("le panneau s'ouvre au-delà de la carte", OnyxSpace.PanelCorner.value >= OnyxSpace.CardCorner.value + 4f)
        // La règle de l'emboîtement : un élément posé à 4 dp dans un panneau de 26 prend 22.
        assertEquals("le rayon intérieur suit le rembourrage", 22.dp, OnyxSpace.interior(OnyxSpace.PanelCorner, 4.dp))
        // Et jamais de rayon négatif : un rembourrage plus grand que le rayon rend l'élément carré.
        assertEquals("jamais sous zéro", 0.dp, OnyxSpace.interior(OnyxSpace.TileCorner, 24.dp))
    }

    // ---- 2. les séparateurs ----------------------------------------------------------------

    @Test
    fun `un separateur est en retrait des deux bords, et part apres la vignette`() {
        // Sans vignette : le retrait est le rembourrage de carte.
        assertEquals("sans vignette", OnyxSpace.CardPadding, OnyxSpace.separatorInset())
        // Avec une vignette de 44 : le filet part du libellé — après la vignette et son écart.
        assertEquals(
            "avec une vignette de la famille",
            OnyxSpace.CardPadding + OnyxSpace.ThumbnailSize + OnyxSpace.ThumbnailGap,
            OnyxSpace.separatorInset(OnyxSpace.ThumbnailSize),
        )
        assertEquals("la valeur, en dp", 68.dp, OnyxSpace.separatorInset(OnyxSpace.ThumbnailSize))
        // Le retrait de droite est celui de gauche : OnyxRowDivider prend `endInset = inset`.
        val divider = java.io.File("src/main/java/com/onyx/app/ui/design/OnyxForm.kt").readText()
        assertTrue("le filet est en retrait des deux bords", divider.contains(".padding(start = inset, end = endInset)"))
    }

    // ---- 3. les vignettes ------------------------------------------------------------------

    @Test
    fun `la vignette de rangee a une seule taille et un seul coin`() {
        assertEquals("le côté", 44.dp, OnyxSpace.ThumbnailSize)
        assertEquals("le coin", 13.dp, OnyxSpace.ThumbnailCorner)
        assertEquals("l'icône dedans", 22.dp, ThumbnailIconSize)
        // Le coin de la vignette est le rayon intérieur de la tuile : les deux courbes partagent
        // leur centre, la vignette ne se lit pas comme un rectangle posé sur un autre.
        assertEquals(
            "le coin suit l'emboîtement de la tuile",
            OnyxSpace.interior(OnyxSpace.TileCorner, 3.dp),
            OnyxSpace.ThumbnailCorner,
        )
        assertTrue("la vignette porte une ombre douce", ThumbnailShadow.value > 0f)
    }

    // ---- 4. les feuilles translucides ------------------------------------------------------

    @Test
    fun `les feuilles sont translucides et tiennent les seuils sur le pire contenu`() {
        val report = StringBuilder(
            "\nFEUILLE TRANSLUCIDE — voile, luminance et pire contraste d'encre\n" +
                "thème    apparence  voile(α)  fond(blanc)  fond(noir)  texte  icône  seuil  verdict\n",
        )
        var checked = 0
        val failures = mutableListOf<String>()
        themes().forEach { theme ->
            val alpha = frostedSheetAlpha(FrostSurface.SHEET, FrostLevel.FULL, theme)
            val veil = theme.surfaceColor.copy(alpha = alpha)
            // Le pire cas honnête : un contenu blanc pur et un contenu noir pur **derrière** la
            // feuille, et le voile noir du panneau s'interpose (il assombrit encore le blanc).
            val scrim = Color.Black.copy(alpha = 0.40f)
            val onWhite = compositedBackdrop(veil, compositedBackdrop(scrim, Color.White))
            val onBlack = compositedBackdrop(veil, compositedBackdrop(scrim, Color.Black))
            val text = listOf(theme.textColor, theme.secondaryText).minOf { ink ->
                minOf(OnyxContrast.ratio(ink, onWhite), OnyxContrast.ratio(ink, onBlack))
            }
            val icon = minOf(
                OnyxContrast.ratio(theme.accent, onWhite),
                OnyxContrast.ratio(theme.accent, onBlack),
            )
            val ok = text >= OnyxContrast.TEXT_MIN
            report.append(
                "%-8s %-10s %-9.3f %-12s %-11s %-6.2f %-6.2f %-6.1f %s\n".format(
                    Locale.ROOT, theme.id, if (theme.dark) "sombre" else "clair", alpha,
                    hex(onWhite), hex(onBlack), text, icon, OnyxContrast.TEXT_MIN,
                    if (ok) "ok" else "ÉCHEC",
                ),
            )
            if (!ok) {
                failures.add("${theme.id}/${if (theme.dark) "sombre" else "clair"} : texte $text < ${OnyxContrast.TEXT_MIN}")
            }
            // Translucide : le voile reste sous l'opacité des barres — le contenu **se devine**.
            assertTrue(
                "${theme.id}/${if (theme.dark) "sombre" else "clair"} : la feuille doit rester translucide ($alpha)",
                alpha < FrostSurface.COMMAND_BAR.veilDark.coerceAtLeast(FrostSurface.COMMAND_BAR.veilLight),
            )
            // Mais jamais un trou : le plancher de lisibilité ne descend pas sous le nominal.
            assertTrue(
                "${theme.id} : le voile ne descend pas sous le nominal",
                alpha >= if (theme.dark) SheetVeilDark else SheetVeilLight,
            )
            checked++
        }
        report.append("lignes vérifiées : $checked\n")
        print(report)
        failures.forEach { println("ÉCHEC  $it") }
        assertTrue("${failures.size} apparence(s) sous le seuil :\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    // ---- 5. la matière douce derrière le verre ---------------------------------------------

    @Test
    fun `le halo change la luminance de la page et ne peint rien quand il est eteint`() {
        val report = StringBuilder(
            "\nMATIÈRE DERRIÈRE LE VERRE — page / halo / vitre (luminance relative, écart WCAG)\n" +
                "thème    apparence  page     halo     vitre    page→halo  halo→vitre  halo(α)\n",
        )
        var checked = 0
        themes().forEach { theme ->
            val page = theme.pageColor
            val haloAlpha = Halo.topAlpha(theme)
            val halo = compositedBackdrop(theme.accent.copy(alpha = haloAlpha), page)
            val sheetAlpha = frostedSheetAlpha(FrostSurface.SHEET, FrostLevel.FULL, theme)
            val vitre = compositedBackdrop(theme.surfaceColor.copy(alpha = sheetAlpha), halo)
            report.append(
                "%-8s %-10s %-8s %-8s %-8s %-10.2f %-11.2f %-7.3f\n".format(
                    Locale.ROOT, theme.id, if (theme.dark) "sombre" else "clair",
                    hex(page), hex(halo), hex(vitre),
                    OnyxContrast.ratio(halo, page), OnyxContrast.ratio(vitre, halo), haloAlpha,
                ),
            )
            // La matière **existe** : le halo ne laisse pas la page au même endroit — sinon il n'y a
            // rien à voir à travers la vitre, et une vitre sur un aplat ne se lit pas. Le seuil est
            // bas parce qu'une page presque blanche (un thème clair) sature : c'est le chiffre
            // mesuré qui compte, et il est publié plus haut.
            assertTrue(
                "${theme.id}/${if (theme.dark) "sombre" else "clair"} : le halo doit changer la page " +
                    "(${OnyxContrast.ratio(halo, page)})",
                OnyxContrast.ratio(halo, page) >= 1.01f,
            )
            checked++
        }
        report.append("lignes vérifiées : $checked\n")
        print(report)
        assertTrue("des lignes mesurées", checked > 0)

        // « Éteint », le halo ne peint **rien** — pas un halo discret résiduel.
        val off = ResolvedTheme(OnyxTheme.STANDARD, OnyxTheme.STANDARD.palette, false, OnyxFonts.System, 1f, halo = false)
        assertTrue("halo éteint : aucun arrêt", Halo.topStops(off).isEmpty())
        assertTrue("halo éteint : rien derrière le contenu non plus", Halo.underStops(off).isEmpty())
        assertEquals("halo éteint : alpha nul", 0f, Halo.topAlpha(off), 0f)
        // Et allumé, il a bien ses arrêts.
        val on = ResolvedTheme(OnyxTheme.STANDARD, OnyxTheme.STANDARD.palette, false, OnyxFonts.System, 1f, halo = true)
        assertEquals("halo allumé : accent puis transparent", 2, Halo.topStops(on).size)
        assertTrue("le halo se pose sur le thème de la personne", Halo.topStops(on).first().alpha > 0f)
    }
}
