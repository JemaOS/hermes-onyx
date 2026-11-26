package com.onyx.app.ui.sidebar

import androidx.compose.ui.graphics.Color
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La pilule du glissement, mesurée** — et la zone morte du bord, prouvée par son arithmétique.
 *
 * Le propriétaire a montré le geste de la référence : vers la droite une pilule **claire** à gauche,
 * vers la gauche la même pilule à droite, avec une icône et un libellé dedans, **aucune couleur
 * saturée** et **pas de rouge**. Un habillage qui se contente d'être joli ne vaut rien ici : ce
 * fichier mesure ce qui le rend vrai, sur **tous les thèmes** et dans **les deux apparences**.
 *
 * 1. **Le libellé se lit** : l'encre de la pilule tient 4,5:1 sur sa teinte, chaque thème compris.
 * 2. **La pilule est une surface** : elle se détache de son panneau (1,20:1), sinon elle n'existe pas.
 * 3. **Elle ne porte aucune couleur** : sa teinte n'ajoute pas de chroma à celle de son fond — ni
 *    l'accent, ni le rouge. C'est ce que « aucune couleur saturée » veut dire, en chiffres.
 * 4. **Elle est claire dans les deux apparences** : plus claire que le panneau en sombre, et le seul
 *    gris très doux en clair (jamais un aplat de couleur).
 * 5. **La zone morte du bord** : la bande du système **plus** la marge de la maison, pure et bornée.
 */
class SidebarSwipeTest {

    private fun theme(theme: OnyxTheme, dark: Boolean) =
        ResolvedTheme.resolve(theme, dark = dark, fonts = OnyxFonts.System)

    /** La chroma d'une couleur : l'écart entre son canal le plus fort et le plus faible. */
    private fun chroma(colour: Color): Float {
        val max = maxOf(colour.red, colour.green, colour.blue)
        val min = minOf(colour.red, colour.green, colour.blue)
        return max - min
    }

    // -----------------------------------------------------------------------------------------
    // 1 … 4 : la pilule, thème par thème
    // -----------------------------------------------------------------------------------------

    @Test
    fun `le libelle de la pilule se lit sur sa teinte, sur tous les themes`() {
        var failures = 0
        val report = StringBuilder()
        for (theme in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val t = theme(theme, dark)
                val pill = sidebarSwipePillColor(t)
                val ink = sidebarSwipePillInk(t)
                val contrast = OnyxContrast.ratio(ink, pill)
                val ok = contrast + 1e-4f >= SIDEBAR_SWIPE_PILL_INK_MIN
                if (!ok) failures++
                report.append(
                    "${theme.id} · ${if (dark) "sombre" else "clair"} · " +
                        "pilule (%.3f, %.3f, %.3f) · encre (%.3f, %.3f, %.3f) · %.2f:1 · %s\n".format(
                            pill.red, pill.green, pill.blue,
                            ink.red, ink.green, ink.blue,
                            contrast,
                            if (ok) "ok" else "SOUS LE SEUIL",
                        ),
                )
            }
        }
        println(report)
        assertEquals(
            "des libellés de pilule passent sous 4,5:1 :\n" +
                report.lineSequence().filter { it.contains("SOUS LE SEUIL") }.joinToString("\n"),
            0,
            failures,
        )
    }

    @Test
    fun `la pilule se detache de son panneau sur tous les themes`() {
        var failures = 0
        val report = StringBuilder()
        for (theme in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val t = theme(theme, dark)
                val detachment = sidebarSwipePillDetachment(t)
                val ok = detachment + 1e-4f >= 1.2f
                if (!ok) failures++
                report.append(
                    "${theme.id} · ${if (dark) "sombre" else "clair"} · détachement %.3f:1 · %s\n".format(
                        detachment,
                        if (ok) "ok" else "SANS SURFACE",
                    ),
                )
            }
        }
        println(report)
        assertEquals(
            "des pilules ne se détachent pas de leur panneau :\n" +
                report.lineSequence().filter { it.contains("SANS SURFACE") }.joinToString("\n"),
            0,
            failures,
        )
    }

    @Test
    fun `la pilule n ajoute aucune couleur a son fond`() {
        // « Aucune couleur saturée » : la teinte de la pilule ne peut pas porter plus de chroma que
        // le fond sur lequel elle se pose — c'est ce qui interdit l'accent, le rouge et tout aplat.
        var failures = 0
        val report = StringBuilder()
        for (theme in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val t = theme(theme, dark)
                val pane = com.onyx.app.ui.design.sidebarPaneColor(t)
                val pill = sidebarSwipePillColor(t)
                val added = chroma(pill) - chroma(pane)
                val ok = added <= 0.02f
                if (!ok) failures++
                report.append(
                    "${theme.id} · ${if (dark) "sombre" else "clair"} · chroma pilule %.3f · " +
                        "panneau %.3f · ajout %.3f · %s\n".format(
                            chroma(pill),
                            chroma(pane),
                            added,
                            if (ok) "ok" else "COULEUR AJOUTÉE",
                        ),
                )
            }
        }
        println(report)
        assertEquals(
            "des pilules ajoutent une couleur à leur fond :\n" +
                report.lineSequence().filter { it.contains("COULEUR AJOUTÉE") }.joinToString("\n"),
            0,
            failures,
        )
    }

    @Test
    fun `la pilule reste claire dans les deux apparences`() {
        for (theme in OnyxTheme.entries) {
            val clair = theme(theme, dark = false)
            val sombre = theme(theme, dark = true)
            assertTrue(
                "${theme.id} clair : la pilule doit être le gris très doux sous le panneau quasi blanc",
                OnyxContrast.luminance(sidebarSwipePillColor(clair)) <
                    OnyxContrast.luminance(com.onyx.app.ui.design.sidebarPaneColor(clair)),
            )
            assertTrue(
                "${theme.id} sombre : la pilule doit rester claire sur le panneau",
                OnyxContrast.luminance(sidebarSwipePillColor(sombre)) >
                    OnyxContrast.luminance(com.onyx.app.ui.design.sidebarPaneColor(sombre)),
            )
        }
    }

    @Test
    fun `les deux libelles du geste sont dits en francais`() {
        assertEquals("Épingler", OnyxLocalization.translate("Pin", Locale.FRANCE))
        assertEquals("Archiver", OnyxLocalization.translate("Archive", Locale.FRANCE))
        assertEquals("Pin", OnyxLocalization.translate("Pin", Locale.ENGLISH))
    }

    // -----------------------------------------------------------------------------------------
    // 5 : la zone morte du bord
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la zone morte est la bande du systeme plus la marge`() {
        assertEquals(152f, sidebarSwipeDeadZonePx(systemGesturesLeftPx = 140f, marginPx = 12f), 0.001f)
        assertEquals("une bande nulle laisse la marge seule", 12f, sidebarSwipeDeadZonePx(0f, 12f), 0.001f)
        assertEquals("aucun terme négatif ne retire", 12f, sidebarSwipeDeadZonePx(-40f, 12f), 0.001f)
        assertEquals("aucun terme négatif ne retire", 0f, sidebarSwipeDeadZonePx(0f, -1f), 0.001f)
    }

    @Test
    fun `la marge de la zone morte est celle de la pilule`() {
        // Une seule valeur : l'air de la pilule avec le bord **est** la marge de la zone morte.
        assertEquals(12, SidebarSwipeMargin.value.toInt())
    }
}
