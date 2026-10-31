package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * **La matière existe, et l'encre tient dessus.**
 *
 * C'est le test qui répond à la question du propriétaire — « je ne sais pas où est ton verre
 * liquide » — par une mesure, pas par une intention. Une vitre ne se lit que s'il y a **quelque chose
 * derrière** ; le fond de la maison était d'un bleu-gris plat, donc le verre n'avait rien à montrer.
 * [Matter] pose des **zones** (froides en haut, chaudes en bas) sur la page, et ce test vérifie les
 * trois choses qui font qu'une matière est une matière et pas un aplat déguisé :
 *
 * 1. **elle change le fond** — l'écart de luminance entre la page et la matière est mesuré, et il est
 *    imprimé (une matière qui ne change pas la page ne sert à rien) ;
 * 2. **elle a du chaud et du froid** — la zone chaude est plus rouge que la zone froide, la zone
 *    froide plus bleue que la chaude : c'est la définition du mot, et elle se mesure ;
 * 3. **l'encre du contexte y tient son seuil** — texte 4,5:1 et encre secondaire 4,5:1 au **pire
 *    cas** de la matière (le point le plus clair et le point le plus creux, toutes zones recouvertes).
 *
 * « Halo » éteint ne laisse pas la page plate : la matière reste, **neutre** (aucune couleur n'est
 * ajoutée à l'écran) — c'est ce que teste le dernier cas.
 */
class MatterLegibilityTest {

    private fun themes(halo: Boolean = true): List<ResolvedTheme> = buildList {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                add(ResolvedTheme(name, name.palette, dark, OnyxFonts.System, halo = halo))
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
    fun `la matiere change le fond, et l encre tient dessus dans les deux apparences`() {
        val report = StringBuilder(
            "\nLA MATIERE — fond, zones, échelle mesurée, et l'encre au pire cas (luminance WCAG)\n" +
                "thème            appa    page      clair     creux     éclat  échelle froid  chaud  texte  secondaire  verdict\n",
        )
        val failures = mutableListOf<String>()
        themes().forEach { theme ->
            val page = theme.pageColor
            val bright = Matter.brightest(theme)
            val dark = Matter.darkest(theme)
            val eclat = OnyxContrast.ratio(bright, dark)
            val zones = Matter.zones(theme)
            val cold = zones.first()
            val warm = zones.last()
            // Le froid est **plus bleu** que le chaud, le chaud **plus rouge** que le froid : c'est
            // la définition des deux zones, et elle ne dépend d'aucune appréciation.
            val coldIsCooler = (cold.colour.blue - cold.colour.red) > (warm.colour.blue - warm.colour.red)
            val text = OnyxContrast.ratio(theme.textColor, bright)
            val textDark = OnyxContrast.ratio(theme.textColor, dark)
            val secondary = minOf(
                OnyxContrast.ratio(theme.secondaryText, bright),
                OnyxContrast.ratio(theme.secondaryText, dark),
            )
            val ok = text >= OnyxContrast.TEXT_MIN && textDark >= OnyxContrast.TEXT_MIN &&
                secondary >= OnyxContrast.TEXT_MIN && coldIsCooler
            report.append(
                "%-16s %-6s %-9s %-9s %-6.2f %-7.2f %-6s %-6s %-6.2f %-6.2f %s\n".format(
                    Locale.ROOT,
                    theme.theme,
                    if (theme.dark) "sombre" else "clair",
                    hex(page),
                    hex(bright),
                    eclat,
                    Matter.scale(theme),
                    hex(cold.colour),
                    hex(warm.colour),
                    text,
                    secondary,
                    if (ok) "ok" else "ÉCHEC",
                ),
            )
            if (!ok) {
                failures.add(
                    "${theme.theme}/${if (theme.dark) "sombre" else "clair"} : matière ${hex(bright)} → " +
                        "${hex(dark)} (éclat $eclat), texte $text / $textDark, secondaire $secondary, " +
                        "froid-plus-bleu=$coldIsCooler",
                )
            }
        }
        report.append("thèmes mesurés : ${themes().size}\n")
        print(report)
        failures.forEach { println("ÉCHEC  $it") }
        assertTrue(
            "${failures.size} cas hors mesure :\n" + failures.take(8).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `la matiere a du froid et du chaud, et plusieurs zones`() {
        themes().forEach { theme ->
            val zones = Matter.zones(theme)
            assertTrue("${theme.theme} : une seule zone n'est pas une matière", zones.size >= 3)
            // Chaque zone couvre la surface où elle est posée : une zone entièrement hors du cadre ne
            // peindrait rien et ferait croire à une matière.
            zones.forEach { assertTrue("zone hors cadre : $it", Matter.covers(it)) }
            // Les deux températures : la première zone est la plus froide, la dernière la plus chaude.
            val cold = zones.first().colour
            val warm = zones.last().colour
            assertTrue(
                "${theme.theme} : la zone chaude n'est pas plus chaude que la froide",
                (warm.red - warm.blue) > (cold.red - cold.blue),
            )
        }
    }

    @Test
    fun `le halo eteint laisse une matiere neutre, sans une seule couleur`() {
        themes(halo = false).forEach { theme ->
            val zones = Matter.zones(theme)
            assertEquals("éteint, la matière est celle de la maison", Matter.neutralZones(theme), zones)
            // Aucune zone n'emprunte l'accent : « éteint » veut dire aucune couleur ajoutée.
            zones.forEach { zone ->
                assertFalse(
                    "${theme.theme} : le halo est éteint et une zone porte l'accent",
                    zone.colour == theme.accent,
                )
            }
            // Et elle change quand même le fond — sinon le verre n'aurait rien à manger. Le chiffre
            // est **imprimé** : c'est lui qui dit si la lumière neutre se voit au niveau de gris près.
            val page = theme.pageColor
            val bright = Matter.brightest(theme)
            val dark = Matter.darkest(theme)
            println(
                "matière neutre %-14s %-6s : page %s → clair %s · creux %s".format(
                    theme.theme, if (theme.dark) "sombre" else "clair", hex(page), hex(bright), hex(dark),
                ),
            )
            assertTrue(
                "${theme.theme} : halo éteint, la matière ne change pas la page (" +
                    "page ${hex(page)}, clair ${hex(bright)}, creux ${hex(dark)})",
                hex(bright) != hex(page) || hex(dark) != hex(page),
            )
        }
    }

    @Test
    fun `la ligne d etat du verre dit le palier, la qualite et le nombre de surfaces`() {
        // Le propriétaire ne pouvait pas savoir quel palier tournait sur son appareil : la ligne est
        // figée ici **mot pour mot**, donc le mot affiché dans les Réglages est celui-ci.
        assertEquals(
            "Verre liquide : réfraction (Android 36) · qualité pleine · 3 surfaces en verre",
            GlassDiagnostics.line(GlassTier.REFRACTION, GlassLevel.FULL, 3, 36),
        )
        assertEquals(
            "Verre liquide : voile (Android 30) · qualité repli · aucune surface en verre ici",
            GlassDiagnostics.line(GlassTier.VEIL, GlassLevel.SCRIM, 0, 30),
        )
        assertEquals(
            "Verre liquide : flou (Android 31) · qualité allégée · 1 surface en verre",
            GlassDiagnostics.line(GlassTier.BLUR, GlassLevel.LITE, 1, 31),
        )
        // Le palier n'est pas un nom de marque : c'est celui de la maison.
        GlassTier.entries.forEach { tier ->
            val label = GlassDiagnostics.tierLabel(tier)
            assertTrue("le libellé du palier est vide", label.isNotBlank())
            assertFalse("un nom de plateforme s'est glissé dans un libellé : $label", label.contains("API"))
        }
    }
}
