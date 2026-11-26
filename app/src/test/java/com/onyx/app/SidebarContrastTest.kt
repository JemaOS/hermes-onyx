package com.onyx.app

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.sidebar.sidebarInk
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le volet du Chat, **mesuré** : les seuils WCAG 2.1 AA sur les treize thèmes et les deux
 * apparences — 4,5:1 pour du texte, 3:1 pour une icône, un voyant ou une pastille.
 *
 * Ce test mesure ce que l'écran compose **réellement** : les encres du volet passent par
 * [sidebarInk], qui les écarte de leur fond juste assez pour tenir le seuil. Les couples sont donc
 * calculés comme `ChatSidebar` les calcule, pas à partir d'une intention. Le tableau complet part
 * dans la sortie du test — c'est la preuve du lot.
 */
class SidebarContrastTest {

    private fun theme(theme: OnyxTheme, dark: Boolean) =
        ResolvedTheme.resolve(theme, dark = dark, fonts = OnyxFonts.System)

    private data class Pair(val what: String, val ink: Color, val ground: Color, val min: Float)

    private fun pairs(theme: OnyxTheme, dark: Boolean): List<Pair> {
        val t = theme(theme, dark)
        val page = t.pageColor
        val surface = t.surfaceColor
        val discFill = OnyxContrast.over(t.fill, page)
        val accentFill = OnyxContrast.over(t.accent.copy(alpha = 0.16f), page)
        val warningFill = OnyxContrast.over(t.warning.copy(alpha = 0.18f), page)
        val text = OnyxContrast.TEXT_MIN
        val icon = OnyxContrast.ICON_MIN
        return listOf(
            // --- Du texte : titre, seconde ligne, âge, compte ---
            Pair("titre · page", t.textColor, page, text),
            Pair("titre · carte", t.textColor, surface, text),
            Pair("seconde ligne · page", sidebarInk(t.textColor, page, text), page, text),
            Pair("seconde ligne · carte", sidebarInk(t.textColor, surface, text), surface, text),
            Pair("âge · page", sidebarInk(t.textColor, page, text), page, text),
            Pair("âge · carte", sidebarInk(t.textColor, surface, text), surface, text),
            Pair("compte d'une puce · page", sidebarInk(t.textColor, page, text), page, text),
            Pair("puce choisie · capsule", sidebarInk(t.textColor, accentFill, text), accentFill, text),
            Pair("initiale · rond", t.textColor, accentFill, text),
            // --- Des icônes ---
            Pair("icône de tuile · carte", sidebarInk(t.accent, surface, icon), surface, icon),
            Pair("icône d'une carte de bot · carte", sidebarInk(t.accent, surface, icon), surface, icon),
            Pair("loupe · page", sidebarInk(t.textColor, page, icon + 0.5f), page, icon),
            Pair("croix de la recherche · page", sidebarInk(t.textColor, page, icon + 0.5f), page, icon),
            Pair("pastille au repos · disque", sidebarInk(t.tertiaryText, discFill, icon), discFill, icon),
            Pair("pastille en cours · disque", sidebarInk(t.accent, accentFill, icon), accentFill, icon),
            Pair("pastille en attente · disque", sidebarInk(t.warning, warningFill, icon), warningFill, icon),
            // --- Des voyants (la tête du volet) ---
            Pair("voyant joignable · page", sidebarInk(t.success, page, icon), page, icon),
            Pair("voyant injoignable · page", sidebarInk(t.error, page, icon), page, icon),
            Pair("voyant incertain · page", sidebarInk(t.warning, page, icon), page, icon),
            // --- Le bouton plein du pied ---
            Pair("bouton plein · encre", OnyxContrast.inkOn(OnyxContrast.filled(t.accent)), OnyxContrast.filled(t.accent), text),
        ) + flatPairs(t, dark)
    }

    /**
     * **Les surfaces plates du volet clair** ([com.onyx.app.ui.design.flatWellColor] et sa famille).
     *
     * Le lot « volet clair propre » a retiré le verre du volet **au clair seulement**. Ce qui
     * détachait la surface — liseré, filet interne, lumière de tête, ombre de pied — a disparu avec
     * lui : ces couples sont donc la **preuve** que ce qui reste tient tout seul, et le tableau du
     * test les imprime à côté des autres. En sombre, la liste est vide : le verre est intact, et
     * c'est `LiquidGlassTest` qui en répond.
     */
    private fun flatPairs(t: ResolvedTheme, dark: Boolean): List<Pair> {
        if (dark) return emptyList()
        val text = OnyxContrast.TEXT_MIN
        val icon = OnyxContrast.ICON_MIN
        val pane = com.onyx.app.ui.design.sidebarPaneColor(t)
        val well = com.onyx.app.ui.design.flatWellColor(t, pane)
        val pill = com.onyx.app.ui.design.flatActivePillColor(t)
        val round = OnyxContrast.over(t.accent.copy(alpha = 0.16f), pane)
        return listOf(
            // Le détachement des surfaces plates : 1,20:1, le seuil de la maison.
            Pair("tuile · panneau", well, pane, com.onyx.app.ui.design.FlatDetachmentMin),
            Pair("piste du sélecteur · page", com.onyx.app.ui.design.flatTrackColor(t), t.pageColor, com.onyx.app.ui.design.FlatDetachmentMin),
            Pair("pastille du sélecteur · piste", com.onyx.app.ui.design.flatTrackPillColor(t), com.onyx.app.ui.design.flatTrackColor(t), com.onyx.app.ui.design.FlatDetachmentMin),
            // …et les encres qui s'y posent.
            Pair("icône de tuile · tuile", com.onyx.app.ui.design.flatIconInk(t, well), well, icon),
            Pair("libellé de tuile · tuile", t.textColor, well, text),
            Pair("puce au repos · gris des creux", sidebarInk(t.textColor, well, text), well, text),
            Pair("puce choisie · pastille pleine", com.onyx.app.ui.design.flatActivePillInk(t), pill, text),
            Pair("cercle d'une rangée · gris des creux", well, pane, com.onyx.app.ui.design.FlatDetachmentMin),
            Pair("pastille au repos · cercle gris", sidebarInk(t.tertiaryText, well, icon), well, icon),
            Pair("pastille en cours · cercle gris", sidebarInk(t.accent, well, icon), well, icon),
            Pair("pastille en attente · cercle gris", sidebarInk(t.warning, well, icon), well, icon),
            // La ligne d'état et son bouton discret.
            Pair("ligne d'état · panneau", sidebarInk(t.textColor, pane, text), pane, text),
            Pair("« Try again » · panneau", sidebarInk(t.accent, pane, text), pane, text),
            // Le pied : le point d'état sur le rond de l'initiale.
            Pair("point vert du pied · rond", sidebarInk(t.success, round, icon), round, icon),
            Pair("point ambre du pied · rond", sidebarInk(t.warning, round, icon), round, icon),
            Pair("point gris du pied · rond", sidebarInk(t.tertiaryText, round, icon), round, icon),
            // Le titre du pied, sur la pilule pleine.
            Pair("pied · pilule pleine", OnyxContrast.inkOn(OnyxContrast.filled(t.accent)), OnyxContrast.filled(t.accent), text),
        )
    }

    @Test
    fun `le texte et les icones du volet tiennent les seuils, sur tous les themes`() {
        val report = StringBuilder()
        report.append("volet · thème · apparence · ce qui se lit · seuil · lu\n")
        var failures = 0
        for (theme in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                for (pair in pairs(theme, dark)) {
                    val ratio = OnyxContrast.ratioOver(pair.ink, pair.ground)
                    val ok = ratio + 1e-4f >= pair.min
                    if (!ok) failures++
                    report.append(
                        "${theme.id} · ${if (dark) "sombre" else "clair"} · ${pair.what} · " +
                            "${"%.1f".format(pair.min)} · ${"%.2f".format(ratio)} ${if (ok) "ok" else "SOUS LE SEUIL"}\n",
                    )
                }
            }
        }
        println(report)
        assertTrue(
            "des couples passent sous leur seuil :\n" +
                report.lineSequence().filter { it.contains("SOUS LE SEUIL") }.joinToString("\n"),
            failures == 0,
        )
    }
}
