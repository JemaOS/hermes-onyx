package com.onyx.app.ui.design

import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Ce que le gel de défilement changeait au verre — chiffré, surface par surface.**
 *
 * Le gel ([LocalGlassScrollFreeze]) faisait passer la vitre, pendant le geste, du verre vivant
 * ([glassTierNow], flou de la surface, teinte nominative) à un **voile de repli**
 * ([GlassTier.VEIL], flou **nul**, voile **plus opaque**). Ce test **mesure l'écart** entre les deux
 * états avec les **mêmes fonctions pures** qui peignaient à l'écran — le chiffre annoncé et le
 * chiffre peint ne peuvent donc pas diverger.
 *
 * Il **imprime** la table (voile, flou, arête, opacité). L'écart qu'il montre est celui que le gel
 * **produisait** : c'est la démonstration de ce qui a été retiré, et la raison pour laquelle le verre
 * ne peut plus en dépendre — la peinture ne lit plus aucun état de geste (voir `GlassGesteVerrouTest`,
 * qui mord sur cette absence).
 */
class GlassGelMesureTest {

    /** La table du verre, et l'écart repos ↔ geste de chacune. */
    private fun theme(dark: Boolean): ResolvedTheme =
        ResolvedTheme(OnyxTheme.STANDARD, OnyxTheme.STANDARD.palette, dark, OnyxFonts.System)

    private fun table(dark: Boolean): List<String> {
        val t = theme(dark)
        return GlassSurface.entries.map { surface ->
            val inks = glassInks(surface, t)
            // — Le REPOS : ce que la vitre peint quand rien ne bouge.
            // Le palier de l'appareil de recette (API 36) est écrit ici, pas lu de `Build.VERSION` :
            // la JVM des tests rapporte SDK 0, donc `glassTierNow` y vaut VEIL et la colonne
            // « arête » mentirait sur ce que la vitre peint réellement sur le téléphone.
            val reposTier = glassTier(sdkInt = 36)
            val reposBlur = glassPaintBlurDp(surface)
            val reposTint = glassPaintTintAlpha(surface, dark, inks)
            // — Le GESTE, tel que le gel le peignait : voile de repli, flou nul —
            val gesteTier = GlassTier.VEIL
            val gesteBlur = 0f
            val gesteTint = glassScrimAlpha(surface, dark, inks)
            val dBlur = if (reposBlur == 0f) 0.0 else 100.0 * (gesteBlur - reposBlur) / reposBlur
            val dTint = if (reposTint == 0f) 0.0 else 100.0 * (gesteTint - reposTint) / reposTint
            "  %-16s %-11s %6.1f → %6.1f dp (%+7.1f %%)   %5.3f → %5.3f (%+7.1f %%)   %s → %s".format(
                surface.name, if (dark) "sombre" else "clair",
                reposBlur, gesteBlur, dBlur,
                reposTint, gesteTint, dTint,
                reposTier, gesteTier,
            )
        }
    }

    @Test
    fun `imprime l ecart du gel entre le repos et le geste`() {
        println()
        println("GLASS-GEL · écart REPOS ↔ GESTE (ce que le gel changeait)")
        println("  %-16s %-11s %-34s %-34s %s".format("surface", "apparence", "FLou (dp)", "OPACITÉ de teinte", "ARÊTE"))
        for (dark in listOf(true, false)) table(dark).forEach(::println)
        println()
    }

    @Test
    fun `l ecart etait reel et du bon sens - le voile monte et le flou tombe`() {
        for (dark in listOf(true, false)) {
            val t = theme(dark)
            for (surface in listOf(
                GlassSurface.LIST_ROW, GlassSurface.SIDEBAR_TILE, GlassSurface.SIDEBAR_PANEL,
            )) {
                val inks = glassInks(surface, t)
                val reposTint = glassPaintTintAlpha(surface, dark, inks)
                val gesteTint = glassScrimAlpha(surface, dark, inks)
                assertTrue(
                    "${surface.name} ${if (dark) "sombre" else "clair"} : le voile du geste doit être " +
                        "plus opaque que celui du repos ($gesteTint vs $reposTint)",
                    gesteTint > reposTint,
                )
                assertTrue(
                    "${surface.name} : le flou du geste doit tomber à zéro",
                    glassPaintBlurDp(surface) > 0f,
                )
            }
        }
    }
}
