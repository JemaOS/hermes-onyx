package com.onyx.app

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.sidebar.AccountTone
import com.onyx.app.ui.sidebar.accountDetailInk
import com.onyx.app.ui.sidebar.accountSignalInk
import com.onyx.app.ui.sidebar.accountToneHue
import com.onyx.app.ui.sidebar.sidebarInk
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ligne d'état de la feuille de compte, **mesurée** sur les treize thèmes et les deux
 * apparences — 4,5:1 pour du texte, 3:1 pour le voyant.
 *
 * Ce n'est pas une intention qui est vérifiée, c'est ce que la feuille dessine : elle pose son
 * texte sur le panneau de la famille (`surfaceColor`), et son voyant passe par [accountSignalInk],
 * qui l'écarte du panneau juste assez pour tenir le seuil. Les couples sont donc calculés comme le
 * dessin les calcule. Le tableau complet part dans la sortie du test — c'est la preuve du lot.
 */
class AccountSheetContrastTest {

    private fun theme(name: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = name, palette = name.palette, dark = dark, fonts = OnyxFonts.System)

    private fun couleur(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
    )

    @Test
    fun `la ligne d etat et son voyant tiennent leur seuil, sur tous les themes et les deux apparences`() {
        val lines = mutableListOf<String>()
        var checked = 0

        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val resolved = theme(name, dark)
                val apparence = if (dark) "sombre" else "clair"
                val panneau = OnyxSurfaces.surfaceHigh(resolved)

                fun line(what: String, ink: Color, fond: Color, min: Float) {
                    val ratio = OnyxContrast.ratio(ink, fond)
                    lines += "%-10s %-6s %-26s %-9s sur %-9s %6.2f  (min %.1f)"
                        .format(name.id, apparence, what, couleur(ink), couleur(fond), ratio, min)
                    assertTrue(
                        "${name.id} $apparence : $what n'atteint que $ratio (min $min)",
                        ratio >= min,
                    )
                    checked++
                }

                // Le titre d'état (« Connecté » / « Hors ligne ») : le texte plein du thème.
                line("le titre d'état", resolved.textColor, panneau, OnyxContrast.TEXT_MIN)
                // La ligne d'état (le compte à rebours) : la même encre pleine.
                line("la ligne d'état", resolved.textColor, panneau, OnyxContrast.TEXT_MIN)
                // Le motif de la mesure : le texte atténué, jamais sous 4,5:1.
                line(
                    "le motif mesuré",
                    accountDetailInk(resolved.textColor, panneau),
                    panneau,
                    OnyxContrast.TEXT_MIN,
                )
                // Et le sous-titre (l'adresse), qui suit la règle de la famille.
                line(
                    "l'adresse (sous-titre)",
                    accountDetailInk(resolved.textColor, panneau),
                    panneau,
                    OnyxContrast.TEXT_MIN,
                )
                // Les trois voyants : la couleur de l'état, mesurée à 3:1 sur le panneau.
                AccountTone.entries.forEach { tone ->
                    line(
                        "le voyant (${tone.name.lowercase()})",
                        accountSignalInk(panneau, accountToneHue(resolved, tone)),
                        panneau,
                        OnyxContrast.ICON_MIN,
                    )
                }
                // La notice (le retour d'une action) : l'accent **mesuré** sur le panneau, comme
                // partout — l'accent brut y vaut ~3,4:1, sous le seuil du texte.
                line(
                    "la notice (accent mesuré)",
                    sidebarInk(resolved.accent, panneau, OnyxContrast.TEXT_MIN),
                    panneau,
                    OnyxContrast.TEXT_MIN,
                )
            }
        }

        println("Contrastes de la feuille de compte (ligne d'état et voyants) :")
        lines.forEach(::println)
        assertTrue("$checked mesures", checked >= OnyxTheme.entries.size * 2 * 7)
    }

    /**
     * Le voyant ne peut pas être **invisible** : une couleur de thème qui se confondrait avec le
     * panneau ne dirait rien. C'est le pendant de la pastille de la famille.
     */
    @Test
    fun `aucun voyant ne se confond avec le panneau`() {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val resolved = theme(name, dark)
                val panneau = OnyxSurfaces.surfaceHigh(resolved)
                AccountTone.entries.forEach { tone ->
                    val ink = accountSignalInk(panneau, accountToneHue(resolved, tone))
                    assertTrue(
                        "${name.id} : le voyant ${tone.name} ne se voit pas sur le panneau",
                        OnyxContrast.ratio(ink, panneau) >= OnyxContrast.ICON_MIN,
                    )
                    assertTrue(
                        "${name.id} : le voyant ${tone.name} est le panneau",
                        ink != panneau,
                    )
                }
            }
        }
    }
}
