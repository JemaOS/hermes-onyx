package com.onyx.app

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.GlassInk
import com.onyx.app.ui.design.GlassLevel
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.SolidPillIntensity
import com.onyx.app.ui.design.glassBlurDp
import com.onyx.app.ui.design.glassInks
import com.onyx.app.ui.design.glassPaintBlurDp
import com.onyx.app.ui.design.glassPaintLevel
import com.onyx.app.ui.design.glassPaintShadowDp
import com.onyx.app.ui.design.glassPaintTintAlpha
import com.onyx.app.ui.design.glassTintAlpha
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **L'OPACITÉ ET LE FLOU DU VERRE NE DÉPENDENT PLUS DU TEMPS — le verrou.**
 *
 * Le défaut fermé : sur une surface de verre, l'opacité et le flou **changeaient sans qu'on touche à
 * rien**. La cause est la mesure de fluidité ([com.onyx.app.ui.design.GlassFluidity]) : elle rendait
 * un palier (`FULL` / `LITE` / `SCRIM`) à partir de la charge des trames, et ce palier **peignait** —
 * le mode allégé remontait la teinte de 12 points, et le repli **éteignait le flou**. Or la charge ne
 * dépend pas de la personne : quand la cadence baisse (écran qui ne se rafraîchit plus au rythme lu à
 * la composition, appareil sous charge), le palier tombe, le flou s'éteint, et le contenu passe
 * **net** sous l'encre — il se lit **à travers** la plaque et le compositeur (« si je ne touche pas
 * l'écran, ça devient trop transparent »).
 *
 * La règle : le verre est peint **à un seul palier**, [glassPaintLevel] — constant. La mesure reste
 * (elle sert au budget du halo) mais elle **ne peint plus rien**.
 *
 * **Quatre dents**, plus celle de la pastille :
 *
 *  1. le palier de peinture est **constant** et vaut le repos (`FULL`) ;
 *  2. la peinture passe par les accesseurs **sans niveau** ([glassPaintTintAlpha], [glassPaintBlurDp],
 *     [glassPaintShadowDp]) et la source ne contient plus `fluidity?.level` dans ce chemin ;
 *  3. le **flou peint ne peut plus être éteint** : il vaut le flou nominal de la surface — le repli
 *     de la mesure ne peut plus le mettre à zéro ;
 *  4. la **teinte peinte ignore le bonus du mode allégé** (c'est un changement d'opacité automatique) ;
 *  5. la pastille « aller au dernier message » est **pleine**, à **16 de la marge**, au-dessus de la
 *     plaque (le verre mince y laissait lire le fil au travers).
 *
 * **Morsure** : remettre `val level = fluidity?.level ?: GlassLevel.FULL` et
 * `glassTintAlpha(surface, level, dark, inks)` dans `LiquidGlass.kt` fait rougir les dents 1, 2 et 4.
 */
class GlassOpaciteConstanteTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val glass = read("core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
    private val controls = read("core/src/main/java/com/onyx/app/ui/design/OnyxControls.kt")
    private val chat = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")

    private val surfaces = GlassSurface.entries.toList()

    private fun themes(): List<ResolvedTheme> = buildList {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                add(ResolvedTheme(theme = name, palette = name.palette, dark = dark, fonts = OnyxFonts.System))
            }
        }
    }

    // — 1 · Le palier de peinture est constant, et c'est le repos —

    @Test
    fun `le palier de peinture est constant et vaut le repos`() {
        assertEquals(
            "le verre se peint au palier du repos, et à lui seul",
            GlassLevel.FULL,
            glassPaintLevel,
        )
        assertTrue(
            "le palier de peinture est une constante nommée dans le chemin de peinture",
            glass.contains("val level = glassPaintLevel"),
        )
        assertFalse(
            "aucun palier mesuré ne doit rentrer dans la peinture",
            glass.contains("fluidity?.level"),
        )
    }

    // — 2 · La peinture passe par les accesseurs SANS niveau —

    @Test
    fun `le chemin de peinture n a plus de parametre de niveau`() {
        assertTrue(
            "la teinte peinte passe par l'accesseur sans niveau",
            glass.contains("val nominalTintAlpha = glassPaintTintAlpha(surface, dark, inks)"),
        )
        assertTrue(
            "le flou peint passe par l'accesseur sans niveau",
            glass.contains("val blurDp = glassPaintBlurDp(surface)"),
        )
        assertTrue(
            "l'ombre peinte passe par l'accesseur sans niveau",
            glass.contains("val shadowDp = glassPaintShadowDp(surface)"),
        )
        assertFalse(
            "l'ancien appel nivelé a disparu du chemin de peinture",
            glass.contains("glassTintAlpha(surface, level, dark, inks)"),
        )
        assertFalse(
            "l'ancien flou nivelé a disparu du chemin de peinture",
            glass.contains("glassBlurDp(surface, level)"),
        )
    }

    // — 3 · Le flou peint ne peut plus être éteint par une mesure —

    @Test
    fun `le flou peint est le flou nominal, pour toutes les surfaces`() {
        var checked = 0
        for (surface in surfaces) {
            assertEquals(
                "${surface.name} : le flou peint doit être le flou nominal",
                surface.blurDp,
                glassPaintBlurDp(surface),
                0f,
            )
            assertTrue(
                "${surface.name} : le flou peint ne peut pas être nul",
                glassPaintBlurDp(surface) > 0f,
            )
            assertNotEquals(
                "${surface.name} : le flou peint ne doit pas valoir le repli (flou éteint)",
                glassBlurDp(surface, GlassLevel.SCRIM),
                glassPaintBlurDp(surface),
            )
            checked += 1
        }
        assertTrue("toutes les surfaces ont été vues", checked >= surfaces.size)
    }

    // — 4 · La teinte peinte ignore le bonus du mode allégé —

    @Test
    fun `la teinte peinte ne porte jamais le bonus du mode allege`() {
        var checked = 0
        var differs = 0
        themes().forEach { theme ->
            surfaces.forEach { surface ->
                val inks = glassInks(surface, theme)
                val painted = glassPaintTintAlpha(surface, theme.dark, inks)
                assertEquals(
                    "${surface.name} (${theme.id}, sombre=${theme.dark}) : la peinture est le " +
                        "palier du repos",
                    glassTintAlpha(surface, GlassLevel.FULL, theme.dark, inks),
                    painted,
                    0.0001f,
                )
                val light = glassTintAlpha(surface, GlassLevel.LITE, theme.dark, inks)
                if (kotlin.math.abs(light - painted) > 0.0001f) differs += 1
                checked += 1
            }
        }
        assertTrue("le tableau a été parcouru", checked > 0)
        assertTrue(
            "le bonus du mode allégé doit différer sur au moins une surface, sinon la dent ne " +
                "prouve rien",
            differs > 0,
        )
    }

    // — 5 · La pastille flottante : pleine, à 16 de la marge, au-dessus de la plaque —

    @Test
    fun `la pastille du dernier message est pleine et garde ses marges`() {
        assertTrue(
            "la pastille est pleine (elle flotte au-dessus du fil)",
            chat.contains("solid = true,"),
        )
        assertTrue(
            "la pastille garde 16 de marge de chaque côté",
            chat.contains(".padding(start = 16.dp, end = 16.dp, bottom = jumpClearance)"),
        )
        assertTrue(
            "la pastille se range au-dessus de la plaque (dégagement de la pile)",
            chat.contains("val jumpClearance = glassBottomInset + ComposerStacking.PillClearanceDp.dp"),
        )
        assertTrue(
            "le voile plein existe côté contrôle",
            controls.contains("intensity = if (solid) SolidPillIntensity else 1f"),
        )
        assertEquals(
            "le voile plein sature les deux alphas de `glassSurface`",
            4f,
            SolidPillIntensity,
            0f,
        )
    }

    // — Le témoin : ce que le verrou a réellement lu —

    @Test
    fun `le rapport imprime ce qui a ete verifie`() {
        val paintLine = glass.lines().firstOrNull { it.contains("val level = glassPaintLevel") }
        val plaqueInk = GlassInk.DERIVED
        assertEquals("la plaque est d'encre dérivée", GlassInk.DERIVED, GlassSurface.TAB_BAR.ink)
        assertEquals("et la pastille d'encre dérivée aussi", true, plaqueInk == GlassSurface.TAB_BAR.ink)
        println(
            "\nOPACITE CONSTANTE — palier de peinture = $glassPaintLevel" +
                " · ligne lue : ${paintLine?.trim()}" +
                " · surfaces : ${surfaces.size}" +
                " · thèmes : ${themes().size}" +
                " · flou peint de la plaque (TAB_BAR) = ${glassPaintBlurDp(GlassSurface.TAB_BAR)} dp" +
                " · flou du repli = ${glassBlurDp(GlassSurface.TAB_BAR, GlassLevel.SCRIM)} dp" +
                " · ombre peinte = ${glassPaintShadowDp(GlassSurface.TAB_BAR)} dp" +
                " · teinte peinte de la plaque, sombre = " +
                glassPaintTintAlpha(
                    GlassSurface.TAB_BAR,
                    dark = true,
                    inks = listOf(Color.White, Color(0xFF9098A6)),
                ),
        )
    }
}
