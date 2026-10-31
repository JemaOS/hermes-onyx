package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.AppearanceMode
import com.onyx.app.ui.theme.DynColor
import com.onyx.app.ui.theme.OnyxSystem
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.theme.ThemeColors
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'encre de la barre du bas de l'écran « Ordinateur », **mesurée sur tous les thèmes**.
 *
 * Le défaut d'origine : les quatre touches de la barre tiraient leur encre de
 * `OnyxSystem.tertiaryLabel` — un libellé du **système**, prévu pour du texte effacé sur une
 * surface — posé sur `OnyxSystem.fill`, une teinte translucide. Les deux se composent sur le fond de
 * la barre, et le résultat tombait entre 1,1:1 et 2,5:1 en sombre comme en clair : des icônes qu'on
 * ne distingue pas. Un thème à l'accent sombre ajoutait le même défaut aux touches **actives**
 * (l'accent sur la scène, en apparence claire : 1,1:1).
 *
 * Ce test ne juge pas des noms de couleurs mais des **rapports** : pour chaque thème, chaque
 * apparence, et les deux fonds possibles de la barre — la page, et la scène du plein écran (toujours
 * sombre) — il exige les seuils WCAG AA et **imprime le tableau** des valeurs relevées.
 */
class BarInkContrastTest {

    private fun hex(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
    )

    private fun theme(name: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = name, palette = name.palette, dark = dark, fonts = OnyxFonts.System)

    /** Les deux fonds sur lesquels la barre se pose, et l'encre qui va avec. */
    private fun inks(theme: ResolvedTheme): List<Pair<String, BarInk>> = listOf(
        "page" to BarInk(
            surface = theme.pageColor,
            ink = theme.textColor,
            accent = theme.accent,
        ),
        "scène (plein écran)" to BarInk(
            surface = theme.stageColor,
            ink = theme.stageText,
            accent = theme.accent,
        ),
    )

    /** Le bouton **d'avant** : un fond translucide du système, et l'accent (ou le libellé effacé). */
    /**
     * **Le relevé d'avant, gelé.** Ce n'est plus `OnyxSystem.fill` ni `OnyxSystem.tertiaryLabel` :
     * ces rôles portent maintenant les valeurs de la référence, donc les lire ne reconstituerait
     * plus l'ancien dessin et le relevé perdrait son sens. Un « avant » se fige.
     *
     * Valeurs d'origine : `fill` référence = `#1F767680` / `#2E767680`, `tertiaryLabel` référence =
     * `#4D3C3C43` / `#4DEBEBF5`.
     */
    private val legacyFill = DynColor(Color(0x1F767680), Color(0x2E767680))
    private val legacyTertiaryLabel = DynColor(Color(0x4D3C3C43), Color(0x4DEBEBF5))

    private fun legacyButton(dark: Boolean, surface: Color) =
        OnyxContrast.over(legacyFill.resolve(dark), surface)

    @Test
    fun `chaque encre de la barre tient son seuil, sur tous les themes et dans les deux apparences`() {
        val lines = mutableListOf<String>()
        var checked = 0
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val resolved = theme(name, dark)
                val apparence = if (dark) "sombre" else "clair"
                for ((fond, bar) in inks(resolved)) {
                    fun line(what: String, ratio: Float, min: Float) {
                        lines += "%-9s %-6s %-18s %-28s %6.2f  (min %.1f)" 
                            .format(name.id, apparence, fond, what, ratio, min)
                        assertTrue(
                            "$name/$apparence/$fond : $what ne tient pas $min ($ratio)",
                            ratio >= min,
                        )
                        checked++
                    }
                    // Les quatre touches : la case (son fond, puis sa bordure) et son encre.
                    line("icône active — case ${hex(bar.swatch)}", OnyxContrast.ratio(bar.icon, bar.swatch), OnyxContrast.TEXT_MIN)
                    line("icône au repos sur la case", OnyxContrast.ratio(bar.iconIdle, bar.swatch), OnyxContrast.ICON_MIN)
                    line("bordure de la case", OnyxContrast.ratio(bar.edge, bar.surface), OnyxContrast.ICON_MIN)
                    line("l'envoi, au repos, sur la barre", OnyxContrast.ratio(bar.iconIdleOnSurface, bar.surface), OnyxContrast.ICON_MIN)
                    // Les textes de la barre.
                    line("l'invite (texte) sur la barre", OnyxContrast.ratio(bar.muted, bar.surface), OnyxContrast.TEXT_MIN)
                    line("la note (texte) sur la case", OnyxContrast.ratio(bar.mutedOnSwatch, bar.swatch), OnyxContrast.TEXT_MIN)
                    line("l'état (texte) sur la barre", OnyxContrast.ratio(bar.ink, bar.surface), OnyxContrast.TEXT_MIN)
                    // Le bouton plein à la couleur du thème, et la pastille de couleur.
                    line("le mot sur le bouton plein", OnyxContrast.ratio(bar.onAccent, bar.accentFill), OnyxContrast.TEXT_MIN)
                    line("la pastille de couleur sur la barre", OnyxContrast.ratio(bar.signal, bar.surface), OnyxContrast.ICON_MIN)
                    // La case doit se voir : une frontière de contrôle (WCAG 1.4.11 demande 3:1
                    // pour la bordure, c'est la bordure qui la porte, mesure juste au-dessus).
                    line("la case existe sur le fond", OnyxContrast.ratio(bar.swatch, bar.surface), 1.2f)
                }
            }
        }
        println("=== APRÈS : les rapports de contraste de la barre du bas (Ordinateur) ===")
        println("%-9s %-6s %-18s %-28s %6s  (%s)".format("thème", "appar.", "fond de la barre", "élément", "ratio", "seuil"))
        lines.forEach(::println)
        println("$checked rapports vérifiés, tous au-dessus de leur seuil")
        assertTrue(checked >= 7 * 2 * 2 * 10)
    }

    @Test
    fun `les deux encre d'une touche se distinguent, et l'icone active bat le seuil du texte`() {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                for ((fond, bar) in inks(theme(name, dark))) {
                    // Sinon l'état « je peux appuyer » ne se lit pas dans la barre.
                    assertTrue(
                        "$name/$dark/$fond : la touche au repos doit être plus faible que la touche active",
                        OnyxContrast.ratio(bar.iconIdle, bar.swatch) < OnyxContrast.ratio(bar.icon, bar.swatch),
                    )
                    // Et le trait de la case doit dominer son fond : c'est lui qui dessine la case.
                    assertTrue(
                        "$name/$dark/$fond : la bordure doit être le trait visible de la case",
                        OnyxContrast.ratio(bar.edge, bar.surface) > OnyxContrast.ratio(bar.swatch, bar.surface),
                    )
                    // Et le bouton plein reste l'élément le plus lourd de la barre.
                    assertTrue(
                        "$name/$dark/$fond : la case doit se détacher du fond de la barre",
                        OnyxContrast.ratio(bar.swatch, bar.surface) > 1f,
                    )
                }
            }
        }
    }

    @Test
    fun `avant, les quatre touches tombaient sous le seuil, dans les deux apparences`() {
        // Le défaut, chiffré : la reconstitution de l'ancien dessin (fond `fill`, encre de
        // `tertiaryLabel` au repos, accent quand la main est prise). Le relevé sert de référence au
        // rapport : il ne doit plus jamais être reproduit.
        val before = mutableListOf<String>()
        for (name in listOf(OnyxTheme.STANDARD, OnyxTheme.PAPER, OnyxTheme.CLAUDE_CODE)) {
            for (dark in listOf(false, true)) {
                val resolved = theme(name, dark)
                for ((fond, surface) in listOf(
                    "page" to resolved.pageColor,
                    "scène (plein écran)" to resolved.stageColor,
                )) {
                    val button = legacyButton(dark, surface)
                    val atRest = OnyxContrast.ratio(OnyxContrast.over(legacyTertiaryLabel.resolve(dark), button), button)
                    val active = OnyxContrast.ratio(resolved.accent, button)
                    before += "%-12s %-6s %-18s repos %5.2f  actif %5.2f  (seuils 3,0 / 4,5)"
                        .format(name.id, if (dark) "sombre" else "clair", fond, atRest, active)
                    // L'encre au repos ne tenait aucun seuil, dans aucune de ces configurations.
                    assertTrue("$name/$dark/$fond : le relevé d'avant doit être sous 3,0", atRest < OnyxContrast.ICON_MIN)
                }
            }
        }
        println("=== AVANT : les quatre touches (relevé de l'ancien dessin, même formule) ===")
        before.forEach(::println)
    }

    @Test
    fun `une encre attenuee redescend au seuil, jamais au-dela`() {
        // La règle qui remplace le choix de `tertiaryLabel` : on atténue **le plus loin possible**,
        // et on s'arrête au seuil. Vérifié sur des couleurs franches, des deux côtés.
        val cases = listOf(
            OnyxContrast.DarkInk to Color.White,
            Color.White to Color.Black,
            Color(0xFF1B1B1D) to Color(0xFFF6F1E6),
            Color(0xFF40290A) to Color(0xFFF6F1E6),
        )
        for ((ink, background) in cases) {
            for (min in listOf(OnyxContrast.ICON_MIN, OnyxContrast.TEXT_MIN)) {
                val dim = OnyxContrast.dimmed(ink, background, min)
                val ratio = OnyxContrast.ratio(dim, background)
                assertTrue("atténuée sous le seuil : $ratio < $min", ratio >= min)
                assertTrue("atténuée au-delà du seuil : $ratio", ratio <= min + 0.6f)
                assertTrue("l'encre pleine doit rester la plus forte", ratio <= OnyxContrast.ratio(ink, background))
            }
        }
        // Une encre déjà sous le seuil ne s'atténue pas : elle est rendue telle quelle (le défaut
        // est dans le choix de l'encre, pas dans l'atténuation).
        val low = Color(0xFF555555)
        val bg = Color(0xFF666666)
        assertEquals(low, OnyxContrast.dimmed(low, bg, OnyxContrast.ICON_MIN))
    }

    @Test
    fun `les seuils sont ceux de la norme, et le rapport est symetrique`() {
        assertEquals(4.5f, OnyxContrast.TEXT_MIN, 0f)
        assertEquals(3.0f, OnyxContrast.ICON_MIN, 0f)
        assertEquals(21f, OnyxContrast.ratio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, OnyxContrast.ratio(Color.White, Color.White), 0.001f)
        val a = Color(0xFF2E8BF5)
        val b = Color(0xFF151517)
        assertEquals(OnyxContrast.ratio(a, b), OnyxContrast.ratio(b, a), 0.0001f)
        // L'apparence « système » suit celle du téléphone ; clair et sombre sont explicites.
        assertTrue(!AppearanceMode.LIGHT.isDark(systemDark = true))
        assertTrue(AppearanceMode.DARK.isDark(systemDark = false))
        // Le blanc sur l'accent bleu de la maison ne tient pas 4,5:1 : c'est ce que la barre corrige.
        assertTrue(OnyxContrast.ratio(Color.White, a) < OnyxContrast.TEXT_MIN)
        assertEquals(Color(0xFF1B1B1D), OnyxContrast.inkOn(a))
        // Et l'encre choisie tient le seuil du texte, mieux que le blanc.
        val chosen = OnyxContrast.inkOn(a)
        assertTrue(OnyxContrast.ratio(chosen, a) >= OnyxContrast.TEXT_MIN)
        assertTrue(OnyxContrast.ratio(chosen, a) > OnyxContrast.ratio(Color.White, a))
        // Une même règle sert le choix de l'encre d'une bulle (l'accent personnalisé du lecteur) :
        // les deux ne peuvent pas diverger.
        assertEquals(chosen, ThemeColors.RGB(a).contrastingText)
    }
}
