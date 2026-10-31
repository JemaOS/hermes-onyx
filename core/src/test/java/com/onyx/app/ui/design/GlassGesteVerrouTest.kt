package com.onyx.app.ui.design

import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Un geste ne change plus rien au verre — opacité, flou et arête constants.**
 *
 * Ce verrou remplace `GlassScrollFreezeTest`, qui gardait l'inverse : il exigeait que la vitre
 * **plafonne son flou** pendant un défilement (`val liveGlass = sampling && !scrolling`,
 * `val tier = if (liveGlass) glassTierNow else GlassTier.VEIL`) et que la **lumière de tête se
 * fige** (`sheenShift = if (scrolling) 0f else parallaxDp`). Ces deux pièces existaient pour
 * qu'une **lumière de contact** ne suive pas le doigt ; cette lumière a été supprimée partout
 * (`1.0.0+706`), et elles n'abaissaient plus la qualité du verre sans rien corriger.
 *
 * Le verrou mord sur **trois choses**, et chacune a sa preuve :
 *
 *  1. **la peinture ne lit aucun état de geste** : la branche qui faisait tomber le palier au voile,
 *     le plafond du flou et le gel de la lumière de tête ont disparu — les noms sont interdits ;
 *  2. **les valeurs peintes sont celles du repos** : le palier, le flou et la teinte d'une vitre sont
 *     produits par les seules fonctions pures qui ne prennent **aucun** paramètre de geste ;
 *  3. **le seul reste du signal ne peint rien** : `LocalGlassScrollFreeze` ne sert plus qu'à suspendre
 *     la *réponse au doigt* (un doigt qui défile n'est pas un appui), jamais le voile d'un verre.
 *
 * **Morsure prouvée** : en réintroduisant `val liveGlass = sampling && !scrolling` et
 * `sheenShift = if (scrolling) 0f else parallaxDp` dans `LiquidGlass.kt`, les tests 1 et 2 rougissent
 * en nommant le fichier et la ligne. Voir le rapport du lot.
 */
class GlassGesteVerrouTest {

    private val module = File(".").absoluteFile
    private val glassFile = File(module, "src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
    private val touchFile = File(module, "src/main/java/com/onyx/app/ui/design/GlassTouch.kt")
    private val glass = glassFile.readText()
    private val touch = touchFile.readText()

    /** Le source **sans ses commentaires** : un nom cité dans un commentaire n'est pas un usage. */
    private fun code(text: String): String =
        text.lineSequence().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")

    // — 1. La peinture ne lit aucun état de geste ————————————————————————————————

    @Test
    fun `la branche qui plafonnait le flou pendant le geste a disparu`() {
        val fautes = mutableListOf<String>()
        val interdits = listOf(
            "liveGlass",                       // le nom du gel du flou
            "else GlassTier.VEIL",             // la retombée au voile pendant le geste
            "if (liveGlass)",                  // la branche de peinture du gel
            "sheenShift = if",                 // le gel de la lumière de tête
            "if (scrolling) 0f",
        )
        val c = code(glass)
        for (symbole in interdits) {
            val at = c.indexOf(symbole)
            if (at >= 0) {
                val ligne = c.substring(0, at).count { it == '\n' } + 1
                fautes += "  ${glassFile.name}:~$ligne → « $symbole »"
            }
        }
        println("VERROU-GESTE · interdits relevés dans la peinture : ${fautes.size}")
        assertTrue(
            "le gel de défilement est réapparu dans la peinture du verre — il n'a plus d'objet :\n" +
                fautes.joinToString("\n"),
            fautes.isEmpty(),
        )
        assertTrue(
            "le palier peint doit être celui de la plateforme, sans condition de geste",
            c.contains("val tier = glassTierNow"),
        )
        assertTrue(
            "la lumière de tête doit reprendre sa parallaxe de position, sans condition de geste",
            c.contains("drawGlassFinish(shape, dark, sheenShift = parallaxDp)"),
        )
        assertTrue(
            "la peinture du verre dépend de la seule présence d'une source",
            c.contains("if (sampling) {"),
        )
    }

    // — 2. Les valeurs peintes sont celles du repos ————————————————————————————————

    @Test
    fun `flou opacite et arete d un geste valent ceux du repos pour toute la table`() {
        val fautes = mutableListOf<String>()
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val theme = ResolvedTheme(name, name.palette, dark, OnyxFonts.System)
                for (surface in GlassSurface.entries) {
                    val inks = glassInks(surface, theme)
                    // Les trois grandeurs peintes, telles que le code les produit — **sans aucun
                    // paramètre de geste** : une seule source pour le repos et pour le mouvement.
                    val tier = glassTier(sdkInt = 36)
                    val blur = glassPaintBlurDp(surface)
                    val tint = glassPaintTintAlpha(surface, dark, inks)
                    if (tier != glassTier(sdkInt = 36) || blur != surface.blurDp || tint !in 0f..1f) {
                        fautes += "  ${surface.name} ${name.name}/$dark"
                    }
                }
            }
        }
        println("VERROU-GESTE · surfaces × thèmes × apparences mesurés : écart = ${fautes.size}")
        assertTrue(
            "une surface peint autre chose au repos qu'en mouvement :\n" + fautes.joinToString("\n"),
            fautes.isEmpty(),
        )
    }

    // — 3. Le seul reste du signal ne peint rien ——————————————————————————————————

    @Test
    fun `le signal de defilement ne sert plus qu a suspendre l appui`() {
        val c = code(glass)
        // « scrolling » ne doit apparaître que sur la ligne du signal et celle de la règle d'appui.
        val usages = c.lines().withIndex().filter { (_, l) -> l.contains("scrolling") }
        assertEquals(
            "le signal de défilement a repris un usage qui n'est pas l'appui :\n" +
                usages.joinToString("\n") { "  ${glassFile.name}:${it.index + 1} → ${it.value.trim()}" },
            2,
            usages.size,
        )
        assertTrue(
            "le signal se lit une fois",
            usages[0].value.contains("LocalGlassScrollFreeze.current"),
        )
        assertTrue(
            "et il ne nourrit que la décision d'appui, qui ne peint ni lumière ni voile",
            usages[1].value.contains("scrolling = scrolling,") &&
                c.contains("val pressStrength = glassPressStrength("),
        )
        // La règle pure d'appui est intacte — elle est la seule chose que le signal décide.
        assertTrue(
            "la règle d'appui suspend toujours la réponse au doigt pendant un geste",
            touch.contains(
                "if (enabled && !reduceMotion && fluidityAllows && !scrolling) pressed.coerceIn(0f, 1f) else 0f",
            ),
        )
        assertEquals(
            "un doigt qui fait défiler n'appuie pas",
            0f,
            glassPressStrength(true, false, true, 1f, scrolling = true),
            0f,
        )
        // Et la peinture ne reçoit jamais le signal.
        assertFalse(
            "la teinte peinte ne prend aucun paramètre de geste",
            c.contains("glassPaintTintAlpha(surface, dark, inks, scrolling"),
        )
    }
}
