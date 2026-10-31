package com.onyx.app.ui.insights

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.barInk
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.design.iconInk
import com.onyx.app.ui.design.recededInk
import com.onyx.app.ui.design.signalInk
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les contrastes des écrans de ce lot — « Capacités » (ses trois familles, la **fiche** d'un serveur
 * MCP et le **formulaire** qui l'ajoute) et « Aperçus » — **mesurés**, thème par thème, dans les deux
 * apparences.
 *
 * Seuils WCAG 2.1 AA, les mêmes que le reste de l'app : **4,5:1** pour un mot, **3:1** pour une
 * forme (le trait d'une icône, la barre d'un graphique). Le test imprime le tableau des valeurs
 * relevées : le chiffre annoncé dans le rapport est celui-ci, mesuré par la même fonction qui décide
 * à l'écran — les deux ne peuvent pas diverger.
 *
 * Les rangées d'un formulaire (`OnyxFormSection`, `OnyxFieldRow`) gardent, elles, les libellés du
 * système (`secondaryLabel`, `tertiaryLabel`) : ce sont ceux de tous les Réglages de l'app, hors du
 * lot. Ce qui est mesuré ici, ce sont **les encres que ces deux écrans choisissent eux-mêmes** —
 * l'encre d'un mot qui se touche, d'un résultat de test, d'une ligne d'outil, d'un refus.
 */
class InsightsContrastTest {

    private fun theme(name: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = name, palette = name.palette, dark = dark, fonts = OnyxFonts.System)

    private fun hex(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
    )

    @Test
    fun `les mots et les formes des deux ecrans tiennent leurs seuils, sur tous les themes`() {
        val lines = mutableListOf<String>()
        var checked = 0

        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val t = theme(name, dark)
                val page = t.formPageColor
                val panel = t.formSurfaceColor
                val appearance = if (dark) "sombre" else "clair"

                fun check(what: String, ink: Color, surface: Color, min: Double) {
                    val ratio = OnyxContrast.ratio(OnyxContrast.over(ink, surface), surface).toDouble()
                    lines += "%-10s %-7s %-34s %-9s %-9s %6.2f  (min %.1f)"
                        .format(name.id, appearance, what, hex(ink), hex(surface), ratio, min)
                    assertTrue("$name/$appearance · $what : %.2f:1".format(ratio), ratio >= min)
                    checked++
                }

                // — Aperçus : le coût en très gros, puis sa légende.
                check("coût (grand nombre)", t.textColor, page, 4.5)
                check("légende du coût", bodyInk(t, page), page, 4.5)
                check("période et adresse", bodyInk(t, page), page, 4.5)
                // — Les trois cartes.
                check("valeur d'une carte", t.primaryText, panel, 4.5)
                check("légende d'une carte", bodyInk(t, panel), panel, 4.5)
                // — L'histogramme : le titre, l'annotation du pic, la ligne du jour choisi.
                check("titre « jetons par jour »", t.primaryText, panel, 4.5)
                check("annotation du pic", bodyInk(t, panel), panel, 4.5)
                check("jour choisi (ligne)", t.textColor, panel, 4.5)
                check("invite « touchez un jour »", bodyInk(t, panel, t.tertiaryText), panel, 4.5)
                check("dates de l'axe", bodyInk(t, panel, t.tertiaryText), panel, 4.5)
                // — Les barres : le pic, le jour choisi, et les jours reculés (jamais sous 3:1).
                check("barre du pic", barInk(t, panel), panel, 3.0)
                check("barre au repos", recededInk(t, panel), panel, 3.0)
                // — Les modèles : la barre sur sa piste, et le compte de jetons.
                val track = OnyxContrast.over(t.fill, panel)
                check("barre d'un modèle", barInk(t, track), track, 3.0)
                check("jetons d'un modèle", bodyInk(t, page), page, 4.5)
                // — La note de bas de page (« ce profil seulement »).
                check("note « ce profil »", bodyInk(t, page, t.tertiaryText), page, 4.5)

                // — Capacités : le titre de famille, le nom, la description, l'interrupteur.
                check("titre de famille", bodyInk(t, page, t.tertiaryText), page, 4.5)
                check("nom d'une capacité", t.textColor, page, 4.5)
                check("capacité éteinte", bodyInk(t, page), page, 4.5)
                check("description d'une ligne", bodyInk(t, page), page, 4.5)
                check("champ de recherche (loupe)", iconInk(t, t.surfaceColor), t.surfaceColor, 3.0)
                check("valeur du champ de recherche", t.textColor, t.surfaceColor, 4.5)
                check("bouton du catalogue (mot)", signalInk(t, t.surfaceColor), t.surfaceColor, 4.5)
                check("mot qui se touche (Tester)", signalInk(t, page), page, 4.5)
                check("note « à partir de la prochaine »", bodyInk(t, page), page, 4.5)
                // — Le catalogue.
                check("ligne du catalogue", t.textColor, page, 4.5)
                check("famille du catalogue", bodyInk(t, page, t.tertiaryText), page, 4.5)
                check("action du catalogue", signalInk(t, page), page, 4.5)
                // — Les formes du système posées par ces deux écrans : un triangle d'alerte, un
                //   résultat de test. Un mot d'erreur est un mot ; un triangle est une forme.
                check(
                    "triangle d'alerte",
                    OnyxContrast.distinctOn(t.warning, page, OnyxContrast.ICON_MIN),
                    page,
                    3.0,
                )
                check(
                    "résultat d'un test MCP",
                    OnyxContrast.distinctOn(t.error, page, OnyxContrast.TEXT_MIN),
                    page,
                    4.5,
                )
            }
        }

        println("Thème      Appar.  Élément                            encre     fond      ratio   seuil")
        lines.forEach(::println)
        assertTrue(
            "au moins un contrôle par thème et par apparence",
            checked >= OnyxTheme.entries.size * 2 * 27,
        )
    }

    @Test
    fun `l'interrupteur de la maison se distingue du fond, et c'est mesuré`() {
        // L'interrupteur est celui de l'app (`OnyxSwitch`) : ce test **relève** son rapport plutôt que
        // de le supposer, et vaut pour les deux écrans qui l'emploient.
        val lines = mutableListOf<String>()
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val t = theme(name, dark)
                val track = OnyxContrast.distinctOn(t.success, t.formPageColor)
                val page = t.formPageColor
                val ratio = OnyxContrast.ratio(OnyxContrast.over(track, page), page).toDouble()
                lines += "%-10s %-7s interrupteur allumé %-9s sur %-9s %6.2f"
                    .format(name.id, if (dark) "sombre" else "clair", hex(track), hex(page), ratio)
                assertTrue(
                    "$name/${if (dark) "sombre" else "clair"} : l'interrupteur allumé se détache du fond",
                    ratio >= 3.0,
                )
            }
        }
        lines.forEach(::println)
    }

    /**
     * La **fiche d'un serveur MCP** et le **formulaire** qui l'ajoute : les mots et les formes que
     * ces deux écrans posent eux-mêmes, sur la page et sur la surface d'une section.
     *
     * Chaque contrôle emploie l'encre que l'écran emploie : `bodyInk`/`signalInk` pour un mot qui
     * doit se lire, `distinctOn` pour une couleur de système (un rouge d'erreur, un orange d'alerte)
     * qu'il faut éloigner du fond juste assez, `theme.primaryText` pour un libellé de rangée.
     */
    @Test
    fun `les mots et les formes de la fiche MCP et de son formulaire tiennent leurs seuils`() {
        val lines = mutableListOf<String>()
        var checked = 0

        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val t = theme(name, dark)
                val page = t.formPageColor
                val panel = t.formSurfaceColor
                val appearance = if (dark) "sombre" else "clair"

                fun check(what: String, ink: Color, surface: Color, min: Double) {
                    val ratio = OnyxContrast.ratio(OnyxContrast.over(ink, surface), surface).toDouble()
                    lines += "%-10s %-7s %-40s %-9s %-9s %6.2f  (min %.1f)"
                        .format(name.id, appearance, what, hex(ink), hex(surface), ratio, min)
                    assertTrue("$name/$appearance · $what : %.2f:1".format(ratio), ratio >= min)
                    checked++
                }

                // — La fiche : le titre (le nom du serveur), puis les rangées de « MCP ».
                check("titre de la fiche", t.primaryText, page, 4.5)
                check("libellé d'une rangée (Transport)", t.primaryText, panel, 4.5)
                check("valeur d'une rangée (adresse/transport)", bodyInk(t, panel), panel, 4.5)
                check("mot d'authentification", bodyInk(t, panel), panel, 4.5)
                check("note « apporté par le greffon »", bodyInk(t, panel), panel, 4.5)

                // — Le test : « en cours », le mot qui se touche, et le résultat réel.
                check("« Test en cours… »", bodyInk(t, panel), panel, 4.5)
                check("mot qui se touche (Tester) de la fiche", signalInk(t, panel), panel, 4.5)
                check("résultat d'un test réussi (mot)", t.textColor, panel, 4.5)
                check("résultat d'un test échoué (mot)", OnyxContrast.distinctOn(t.error, panel, OnyxContrast.TEXT_MIN), panel, 4.5)
                check("latence mesurée", bodyInk(t, panel, t.tertiaryText), panel, 4.5)
                check("message d'erreur du test", OnyxContrast.distinctOn(t.error, panel, OnyxContrast.TEXT_MIN), panel, 4.5)
                check("compte des invites et des ressources", bodyInk(t, panel), panel, 4.5)
                check("icône d'un test réussi", OnyxContrast.distinctOn(t.success, panel, OnyxContrast.ICON_MIN), panel, 3.0)
                check("icône d'un test échoué", OnyxContrast.distinctOn(t.error, panel, OnyxContrast.ICON_MIN), panel, 3.0)

                // — Les outils exposés : le compte dépliable, chaque ligne, et le cas « aucun ».
                check("titre du dépliable « outils exposés »", t.primaryText, panel, 4.5)
                check("nom d'un outil", t.textColor, panel, 4.5)
                check("description d'un outil", bodyInk(t, panel), panel, 4.5)
                check("numéro d'un outil", bodyInk(t, panel, t.tertiaryText), panel, 4.5)
                check("« aucun outil listé »", bodyInk(t, panel), panel, 4.5)

                // — Le retrait : le mot destructif et son icône, sur la surface de la section.
                check("mot « Supprimer » (destructif)", OnyxContrast.distinctOn(t.error, panel, OnyxContrast.TEXT_MIN), panel, 4.5)
                check("icône de corbeille", OnyxContrast.distinctOn(t.error, panel, OnyxContrast.TEXT_MIN), panel, 3.0)
                check("triangle d'alerte de la fiche", OnyxContrast.distinctOn(t.warning, panel, OnyxContrast.ICON_MIN), panel, 3.0)

                // — Le formulaire : son titre, l'action qui ajoute, un libellé de champ, et le refus.
                check("titre du formulaire", t.primaryText, page, 4.5)
                check("libellé d'un champ (Nom)", t.primaryText, panel, 4.5)
                check("mot « Ajouter le serveur »", signalInk(t, panel), panel, 4.5)
                check("phrase de refus du formulaire", bodyInk(t, panel), panel, 4.5)
                check("triangle d'alerte du formulaire", OnyxContrast.distinctOn(t.warning, panel, OnyxContrast.ICON_MIN), panel, 3.0)
            }
        }

        println("Thème      Appar.  Élément (fiche MCP / formulaire)       encre     fond      ratio   seuil")
        lines.forEach(::println)
        assertTrue(
            "au moins un contrôle par thème et par apparence",
            checked >= OnyxTheme.entries.size * 2 * 27,
        )
    }
}
