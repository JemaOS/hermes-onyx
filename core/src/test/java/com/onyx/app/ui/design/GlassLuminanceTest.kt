package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * **Une vitre se voit : elle se détache de sa page, et elle n'est pas plate.**
 *
 * C'est la leçon du lot. Mesuré sur la capture du propriétaire, le verre du build précédent valait
 * déjà 1,99:1 avec la page — il se **détachait** donc, et corriger sa teinte aurait changé un verre
 * que la personne aimait. Ce qui manquait était ailleurs : la vitre était **unie d'un bord à
 * l'autre**. Un aplat, même détaché, se lit comme du plastique.
 *
 * Deux chiffres portent donc le lot, et ce test exige les deux, pour **chaque** surface de la table,
 * dans les deux apparences et aux trois qualités :
 *
 * - la **détache** — le plus grand écart que la surface présente avec sa page, pris sur la face, le
 *   bord haut, le bord bas et le liseré interne ([glassDetachment]). Un seul de ces quatre ne
 *   suffirait pas : en clair, la face *est* la page (la règle du voile interdit de la tirer vers le
 *   blanc), et c'est alors le liseré sombre qui détache ; en sombre, c'est la face. Prendre le
 *   maximum est la seule mesure qui reste vraie dans les deux cas ;
 * - la **profondeur** — l'écart entre le bord haut et le bord bas ([glassDepth]). C'est le chiffre
 *   qui manquait : une vitre plate vaut 1,00:1 ici, une vitre éclairée en haut et ombrée en bas vaut
 *   1,2 à 1,8:1.
 *
 * Le tableau est **imprimé** par le test : le chiffre du rapport et le chiffre vérifié sortent du
 * même code, au même moment. Les rapports d'encre (4,5:1 / 3:1) sont, eux, tenus par
 * `LiquidGlassTest` — la lisibilité et la visibilité se mesurent séparément, et les deux doivent
 * tenir ensemble.
 */
class GlassLuminanceTest {

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

    @Test
    fun `chaque surface se detache de sa page et n est pas plate`() {
        val report = StringBuilder(
            "\nTABLE DES LUMINANCES — fond / vitre / tranche (luminance relative, écart WCAG)\n" +
                "surface            apparence  qualité  fond      vitre     écart  haut/bas  détache  seuil  verdict\n",
        )
        var checked = 0
        val failures = mutableListOf<String>()
        themes().forEach { theme ->
            surfaces.forEach { surface ->
                GlassLevel.entries.forEach { level ->
                    val backdrop = theme.pageColor
                    val face = glassFace(surface, level, theme, backdrop)
                    val top = glassTopRim(surface, level, theme, backdrop)
                    val bottom = glassBottomRim(surface, level, theme, backdrop)
                    val detachment = glassDetachment(surface, level, theme, backdrop)
                    val depth = glassDepth(surface, level, theme, backdrop)
                    val ok = detachment >= GlassDetachmentMin && depth >= GlassDepthMin
                    report.append(
                        "%-18s %-10s %-8s %-9s %-9s %-6.2f %-9.2f %-8.2f %-6.2f %s\n".format(
                            Locale.ROOT,
                            surface.name,
                            if (theme.dark) "sombre" else "clair",
                            level.name,
                            hex(backdrop),
                            hex(face),
                            OnyxContrast.ratio(face, backdrop),
                            depth,
                            detachment,
                            GlassDetachmentMin,
                            if (ok) "ok" else "ÉCHEC",
                        ),
                    )
                    if (!ok) {
                        failures.add(
                            "${surface.name}/${if (theme.dark) "sombre" else "clair"}/$level : " +
                                "détache $detachment (min $GlassDetachmentMin), profondeur $depth " +
                                "(min $GlassDepthMin) — fond ${hex(backdrop)}, haut ${hex(top)}, " +
                                "bas ${hex(bottom)}",
                        )
                    }
                    // Le haut doit être plus clair que le bas : c'est le sens de la lumière, et
                    // l'inverse se lirait comme une vitre creusée à l'envers.
                    assertTrue(
                        "${surface.name}/$level : la lumière de tête doit éclairer plus que l'ombre de pied",
                        OnyxContrast.luminance(top) >= OnyxContrast.luminance(bottom),
                    )
                    checked++
                }
            }
        }
        report.append("lignes vérifiées : $checked\n")
        // Le tableau est **imprimé avant** l'assertion : un échec ne peut pas cacher les chiffres
        // qui l'ont produit — c'est la règle du lot, un chiffre annoncé est un chiffre montré.
        print(report)
        failures.forEach { println("ÉCHEC  $it") }
        assertTrue(
            "${failures.size} ligne(s) sous le seuil de perceptibilité :\n" +
                failures.take(12).joinToString("\n"),
            failures.isEmpty(),
        )
        assertEquals(
            themes().size * surfaces.size * GlassLevel.entries.size,
            checked,
        )
    }

    @Test
    fun `le repli garde un lisere et une vitre sans flou reste une vitre`() {
        // Le repli (appareil qui refuse le flou) n'a plus de flou du tout : il ne lui reste que sa
        // teinte et sa finition. Si la finition ne tenait qu'au flou, le repli redeviendrait un aplat.
        val failures = mutableListOf<String>()
        themes().forEach { theme ->
            surfaces.forEach { surface ->
                val value = glassDetachment(surface, GlassLevel.SCRIM, theme, theme.pageColor)
                if (value < GlassDetachmentMin) {
                    failures.add("${surface.name}/${if (theme.dark) "sombre" else "clair"} : $value")
                }
            }
        }
        failures.forEach { println("ÉCHEC REPLI  $it") }
        assertTrue(
            "${failures.size} surface(s) dont le repli ne se détache plus :\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `la table declare ce que chaque surface peut echantillonner`() {
        // Aucune surface ne peut rester sans décision : c'est l'oubli qui produit une vitre posée sur
        // un aplat et un flou qu'aucune source n'alimente (le « verre perdu »).
        val inScreen = GlassSurface.entries.filter { it.sampling == GlassSampling.IN_SCREEN }
        val ownWindow = GlassSurface.entries.filter { it.sampling == GlassSampling.OWN_WINDOW }
        val flat = GlassSurface.entries.filter { it.sampling == GlassSampling.FLAT }
        assertTrue("des surfaces recouvrent du contenu de la même fenêtre", inScreen.isNotEmpty())
        assertTrue("les panneaux à fenêtre propre sont déclarés", ownWindow.isNotEmpty())
        assertTrue("les surfaces sur aplat sont déclarées", flat.isNotEmpty())
        // Les barres du fil et le volet sont dans la fenêtre : ce sont eux qui floutent pour de vrai.
        assertTrue(inScreen.contains(GlassSurface.THREAD_TOP_BAR))
        assertTrue(inScreen.contains(GlassSurface.SIDEBAR_PANEL))
        // Les deux feuilles, le menu et l'alerte vivent dans leur propre fenêtre.
        listOf(GlassSurface.SHEET, GlassSurface.ALERT, GlassSurface.MENU).forEach {
            assertTrue("${it.name} doit être déclarée à fenêtre propre", ownWindow.contains(it))
        }
    }

    @Test
    fun `la profondeur existe dans les deux apparences, pas dans une seule`() {
        // Le piège : une finition réglée en sombre seulement. La lumière de tête blanche ne se voit
        // pas sur une page blanche, l'ombre de pied noire ne se voit pas sur une page noire — c'est
        // l'écart entre les deux qui doit tenir partout.
        themes().forEach { theme ->
            surfaces.forEach { surface ->
                val depth = glassDepth(surface, GlassLevel.FULL, theme, theme.pageColor)
                assertTrue(
                    "${surface.name} (${if (theme.dark) "sombre" else "clair"}) : profondeur $depth",
                    depth >= GlassDepthMin,
                )
            }
        }
    }
}
