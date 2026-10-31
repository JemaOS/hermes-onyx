package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * **La matière est là, et la vitre tient ses encres dessus.**
 *
 * Le lot généralise `onyxPage(theme)` (halo « matière ») à tous les écrans : chaque surface en verre a
 * donc **quelque chose à échantillonner**, au lieu de se poser sur un aplat. Mais la matière
 * **éclaircit** le haut de la page (un accent doux par-dessus la couleur de page), et c'est le
 * nouveau **pire cas** de lisibilité : une portion du voile de tête du halo passe derrière la vitre
 * et remonte le fond.
 *
 * Ce test mesure, pour **chaque** surface de la table et les **deux** apparences, le cas honnête :
 *
 * - l'**écart de luminance** entre la page et la matière posée dessus (la plateforme) — il prouve
 *   que le fond-matière existe réellement et n'est pas un aplat déguisé ;
 * - la **détache** de la vitre de cette plateforme, au même seuil que partout ailleurs ;
 * - le **contraste** de l'encre du contexte (texte primaire et secondaire) posée sur la face de la
 *   vitre **au-dessus de la matière**, dans le pire cas : 4,5:1 pour un texte, 3:1 pour une icône.
 *
 * Le tableau est imprimé avant l'assertion : le chiffre annoncé est le chiffre montré.
 */
class MatiereVitreMesureTest {

    private val surfaces = GlassSurface.entries.toList()

    private fun themes(): List<ResolvedTheme> = buildList {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                add(ResolvedTheme(name, name.palette, dark, OnyxFonts.System))
            }
        }
    }

    private fun hex(c: Color): String = String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        (c.red * 255f).toInt().coerceIn(0, 255),
        (c.green * 255f).toInt().coerceIn(0, 255),
        (c.blue * 255f).toInt().coerceIn(0, 255),
    )

    /** La plateforme derrière une vitre d'un écran à matière : la page, plus le halo du haut. */
    private fun matiere(theme: ResolvedTheme): Color =
        compositedBackdrop(theme.accent.copy(alpha = Halo.topAlpha(theme)), theme.pageColor)

    @Test
    fun `la matiere eclaire la page et la vitre tient ses encres dessus`() {
        val report = StringBuilder(
            "\nLA MATIERE ET LA VITRE — fond-matiere / vitre / encres (luminance WCAG)\n" +
                "surface            appa    page      matiere   eclat  vitre     detach  texte  icone  verdict\n",
        )
        var checked = 0
        val failures = mutableListOf<String>()
        themes().forEach { theme ->
            val page = theme.pageColor
            val matiere = matiere(theme)
            val eclat = OnyxContrast.ratio(matiere, page)
            surfaces.forEach { surface ->
                val glass = glassFace(surface, GlassLevel.FULL, theme, matiere)
                val detach = glassDetachment(surface, GlassLevel.FULL, theme, matiere)
                val textRatio = OnyxContrast.ratio(theme.textColor, glass)
                val iconRatio = OnyxContrast.ratio(theme.secondaryText, glass)
                val ok = detach >= GlassDetachmentMin &&
                    textRatio >= OnyxContrast.TEXT_MIN &&
                    iconRatio >= OnyxContrast.ICON_MIN
                report.append(
                    "%-18s %-6s %-9s %-9s %-6.2f %-9s %-6.2f %-6.2f %-6.2f %s\n".format(
                        Locale.ROOT,
                        surface.name,
                        if (theme.dark) "sombre" else "clair",
                        hex(page),
                        hex(matiere),
                        eclat,
                        hex(glass),
                        detach,
                        textRatio,
                        iconRatio,
                        if (ok) "ok" else "ÉCHEC",
                    ),
                )
                if (!ok) {
                    failures.add(
                        "${surface.name}/${if (theme.dark) "sombre" else "clair"} : " +
                            "détache $detach (min $GlassDetachmentMin), texte $textRatio " +
                            "(min ${OnyxContrast.TEXT_MIN}), icône $iconRatio (min ${OnyxContrast.ICON_MIN})",
                    )
                }
                checked++
            }
        }
        report.append("lignes vérifiées : $checked\n")
        print(report)
        failures.forEach { println("ÉCHEC  $it") }
        assertTrue(
            "${failures.size} ligne(s) sous le seuil :\n" + failures.take(12).joinToString("\n"),
            failures.isEmpty(),
        )
        checked.let { assertTrue("aucune ligne mesurée", it > 0) }
    }

    @Test
    fun `la matiere change vraiment le fond derriere la page partout`() {
        // « Un fond-matière », pas « une couleur de page » : posé (halo actif), le fond derrière la
        // vitre doit différer de la page d'un écart mesurable, dans les deux apparences. Allumé
        // seulement en sombre, le test le dirait ici.
        themes().forEach { theme ->
            val page = theme.pageColor
            val matiere = matiere(theme)
            val eclat = OnyxContrast.ratio(matiere, page)
            // La matière est un voile d'accent : discrète par nature. On exige qu'elle **change** le
            // fond (jamais la page telle quelle) et on imprime l'écart — c'est la mesure, pas un avis.
            println(
                "matière %-14s %-6s : page %s → matière %s (éclat %.3f)".format(
                    theme.theme, if (theme.dark) "sombre" else "clair",
                    hex(page), hex(matiere), eclat,
                ),
            )
            assertTrue(
                "${theme.theme}/${if (theme.dark) "sombre" else "clair"} : la matière ne change pas la page",
                eclat > 1.001f,
            )
        }
    }

    @Test
    fun `le halo eteint ne laisse aucune matiere`() {
        // La promesse du réglage : « éteint », on ne peint rien. La plateforme redevient la page.
        val dark = ResolvedTheme(OnyxTheme.STANDARD, OnyxTheme.STANDARD.palette, true, OnyxFonts.System, halo = false)
        assertTrue(Halo.topStops(dark).isEmpty())
        assertTrue(Halo.topAlpha(dark) == 0f)
    }
}
