package com.onyx.app.ui.sidebar

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Ce qui est révélé autour du volet n'est plus sombre** — mesuré, sur les treize thèmes et les deux
 * apparences.
 *
 * Le défaut du propriétaire, sur la capture en clair : `(8,8,10)` sous les deux coins arrondis du
 * bord extérieur et `(153,153,153)` dans la bande révélée, contre `(255,255,255)` pour la surface du
 * volet. Deux causes, deux mesures ici :
 *
 * 1. le **fond révélé** est la page ([drawerGround]) — jamais le fond de fenêtre, dont la luminance
 *    est si basse qu'un voile ne peut pas la sauver ;
 * 2. le **voile suit l'apparence** ([drawerVeil]) — en clair il ne descend plus sous la page
 *    (`Color.Black` à 0,4, écrit en dur, la faisait tomber à 0,32 de luminance).
 *
 * Les seuils sont ceux de la maison : un rapport de contraste, pas une impression.
 */
class SidebarRevealTest {

    private fun theme(theme: OnyxTheme, dark: Boolean) =
        ResolvedTheme.resolve(theme, dark = dark, fonts = OnyxFonts.System)

    /** Ce que l'écran compose réellement autour du volet : le voile posé sur le fond révélé. */
    private fun revealed(theme: ResolvedTheme): Color =
        OnyxContrast.over(drawerVeil(theme, 1f), drawerGround(theme))

    @Test
    fun `le fond revele est la page, jamais le fond de fenetre`() {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val t = theme(name, dark)
                assertEquals(
                    "le fond révélé doit être la page (${name.id}, ${if (dark) "sombre" else "clair"})",
                    t.pageColor,
                    drawerGround(t),
                )
                // Le défaut du propriétaire est un fond de fenêtre quasi noir **sous une surface
                // claire** : c'est ce cas-là qui se mesure. Un thème dont la page est déjà sombre
                // n'a rien à voir avec le fond de fenêtre, il EST sombre.
                if (OnyxContrast.luminance(t.pageColor) > 0.5f) {
                    assertTrue(
                        "le fond de fenêtre ne doit pas paraître sous une page claire (${name.id})",
                        OnyxContrast.ratio(drawerGround(t), WindowBackground) > 3f,
                    )
                }
            }
        }
    }

    @Test
    fun `en clair, ce qui est revele ne descend pas sous la page`() {
        val report = StringBuilder("thème · page · révélé · rapport · seuil\n")
        var failures = 0
        for (name in OnyxTheme.entries) {
            val t = theme(name, dark = false)
            val page = t.pageColor
            val reveal = revealed(t)
            val ratio = OnyxContrast.ratio(page, reveal)
            // Le voile a le droit de **baisser** un peu la page (c'est un voile), jamais de la
            // rendre sombre : le seuil est celui d'un écart qui ne se lit pas comme une bande noire.
            val ok = ratio <= 1.25f && OnyxContrast.ratio(reveal, WindowBackground) >= 5f
            if (!ok) failures++
            report.append(
                "${name.id} · ${hex(page)} · ${hex(reveal)} · " +
                    "${"%.2f".format(ratio)}:1 · ≤ 1,25:1 ${if (ok) "ok" else "SOUS LE SEUIL"}\n",
            )
        }
        println(report)
        assertTrue(
            "un thème clair révèle une zone sombre :\n" +
                report.lineSequence().filter { it.contains("SOUS LE SEUIL") }.joinToString("\n"),
            failures == 0,
        )
    }

    @Test
    fun `en sombre, le voile ne s eclaircit pas`() {
        for (name in OnyxTheme.entries) {
            val t = theme(name, dark = true)
            val page = OnyxContrast.luminance(t.pageColor)
            val reveal = OnyxContrast.luminance(revealed(t))
            assertTrue(
                "en sombre, le révélé ne doit pas être plus clair que la page (${name.id})",
                reveal <= page + 0.0005f,
            )
        }
    }

    @Test
    fun `le voile n est plus un noir ecrit en dur`() {
        // La garde du défaut, sur le thème de la capture du propriétaire : si quelqu'un réécrit
        // `Color.Black` dans le voile, la teinte redevient noire (« il y a du noir ») et le test
        // échoue. Les autres thèmes sont vus par le test des seuils, qui compare le voile composé à
        // leur **propre** page — un thème sombre n'a pas à avoir un voile clair.
        val standard = theme(OnyxTheme.STANDARD, dark = false)
        val tint = drawerVeilTint(standard)
        assertTrue("la teinte du voile ne peut pas être le noir en dur", tint != Color.Black)
        assertTrue(
            "la teinte du voile clair est trop sombre (luminance ${OnyxContrast.luminance(tint)})",
            OnyxContrast.luminance(tint) > 0.6f,
        )
        for (name in OnyxTheme.entries) {
            val t = theme(name, dark = false)
            if (OnyxContrast.luminance(t.pageColor) <= 0.5f) continue
            // Ce qui se vérifie ici n'est pas une luminance absolue — la teinte est la page tirée
            // d'un cran, et l'écart composé est mesuré par le test des seuils —, c'est qu'aucun thème
            // clair ne retombe sur le noir en dur.
            assertTrue(
                "la teinte du voile clair redevient noire (${name.id})",
                drawerVeilTint(t) != Color.Black,
            )
        }
    }

    @Test
    fun `le voile suit le doigt`() {
        val t = theme(OnyxTheme.STANDARD, dark = false)
        assertEquals("volet fermé : pas de voile", 0f, drawerVeilAlpha(t, 0f), 0.0005f)
        val quarter = drawerVeilAlpha(t, 0.25f)
        val half = drawerVeilAlpha(t, 0.5f)
        val full = drawerVeilAlpha(t, 1f)
        assertTrue("le voile croît avec l'ouverture", quarter < half && half < full)
        assertEquals(
            "et l'opacité pleine est celle de l'apparence claire",
            SidebarReveal.LIGHT_ALPHA,
            full,
            0.0005f,
        )
        assertEquals(
            "en sombre, c'est le voile de la maison",
            SidebarReveal.DARK_ALPHA,
            drawerVeilAlpha(theme(OnyxTheme.STANDARD, dark = true), 1f),
            0.0005f,
        )
        // Au-delà de 1 (un glissement raté) le voile ne s'emballe pas.
        assertEquals(full, drawerVeilAlpha(t, 1.7f), 0.0005f)
    }

    private fun hex(color: Color): String =
        "#%02X%02X%02X".format((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
}
