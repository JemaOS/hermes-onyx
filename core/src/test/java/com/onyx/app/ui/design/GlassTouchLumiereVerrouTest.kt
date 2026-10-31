package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la lumière au toucher — aucune surface de verre ne s'éclaire sous le doigt.**
 *
 * Le propriétaire a donné un ordre explicite : « la lumière au toucher, supprime ça de partout,
 * c'est moche ». Sa capture montrait une tache spéculaire **et** un contour illuminé au point de
 * contact. Ce test **mord** : il rougit si l'une des pièces du halo de doigt **revient** dans le
 * code de production, et il **nomme le fichier et la ligne**.
 *
 * Trois morsures, et rien de décoratif :
 *
 *  1. **Aucun symbole du halo de doigt** ne doit réapparaître dans `core/src/main` ou
 *     `app/src/main` — ni la fonction de dessin, ni les forces qui la nourrissaient, ni le suivi de
 *     point, ni le gain de luminance qui *blanchissait* la vitre. La liste des interdits est celle
 *     des points d'appel supprimés, un par un : c'est le relevé du lot, transformé en garde.
 *  2. **L'appui ne s'éclaire qu'en s'assombrissant** : le seul consommateur de la force d'appui
 *     ([glassPressStrength]) est une ombre (`Color.Black.copy`), jamais un blanc ; et la teinte de
 *     la vitre n'est plus éclaircie par l'appui.
 *  3. **Le gel du flou a été retiré, la règle qui ne peint rien demeure** : le plafonnement du flou
 *     et le gel de la lumière de tête pendant le geste n'existaient que pour une lumière de contact
 *     qui n'est plus ; ce qui reste du signal ne fait que suspendre la *réponse au doigt* — un doigt
 *     qui défile n'est pas un doigt qui appuie.
 *
 * Morsure prouvée : réintroduire `drawGlassHalo` (ou `GlassPressGain`) quelque part dans
 * `core/src/main` fait rougir le test 1 en imprimant `fichier:ligne` — voir le rapport du lot.
 */
class GlassTouchLumiereVerrouTest {

    /**
     * Les points d'appel du halo de doigt, **tels qu'ils existaient** : chacun est une suppression.
     *
     * On ne garde pas le nom nu `glassHalo` (le réglage persisté « Halo », `Keys.GLASS_HALO`, est
     * légitime et reste) : ce sont les symboles du **dessin** et du **suivi de point** qui sont
     * interdits.
     */
    private val interdits = listOf(
        "drawGlassHalo",          // le dessin de la lumière (tache + contour)
        "haloSpecularStrength",   // la force de la tache spéculaire
        "haloRimStrength",        // la force du contour illuminé
        "haloRadiusFraction",     // l'étendue de la tache
        "GlassPressGain",         // le gain de luminance qui blanchissait la vitre
        "glassHaloStrength",      // l'ancienne décision, nommée « halo »
        "GlassHaloState",         // l'état qui portait le point de contact
        "rememberGlassHaloState",
        "Modifier.glassHalo",
        ".glassHalo(",
        "haloFade",               // l'extinction de la lumière après le relâchement
    )

    private fun fichiers(): List<File> =
        listOf(File("src/main"), File("../app/src/main"))
            .filter { it.isDirectory }
            .sortedBy { it.path }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .sortedBy { it.path }

    // ---------------------------------------------------------------------------------------------
    // 1. Aucun symbole du halo de doigt ne revient
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `aucune surface de verre ne peint de lumiere de contact`() {
        val fichiers = fichiers()
        val fautes = mutableListOf<String>()
        for (fichier in fichiers) {
            val texte = fichier.readText()
            for (symbole in interdits) {
                var from = 0
                while (true) {
                    val at = texte.indexOf(symbole, from)
                    if (at < 0) break
                    val ligne = texte.substring(0, at).count { it == '\n' } + 1
                    fautes += "  ${fichier.path}:$ligne → « $symbole »"
                    from = at + symbole.length
                }
            }
        }

        println(
            "VERROU-LUMIERE · fichiers lus=${fichiers.size} · interdits=${interdits.size} · " +
                "occurrences=${fautes.size}",
        )

        assertTrue(
            "une lumière de contact est réapparue dans le verre — elle a été supprimée partout :\n" +
                fautes.joinToString("\n"),
            fautes.isEmpty(),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 2. L'appui ne s'éclaire qu'en s'assombrissant
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `l appui ne s eclaire qu en s assombrissant`() {
        val glass = File("src/main/java/com/onyx/app/ui/design/LiquidGlass.kt").readText()

        // Le consommateur de la force d'appui est l'ombre, pas un blanc.
        assertTrue(
            "l'appui des grandes surfaces doit poser une ombre, pas une lumière",
            glass.contains("Color.Black.copy(alpha = pressShade)"),
        )
        assertFalse(
            "aucun blanc ne doit être peint depuis la force d'appui",
            Regex("""Color\.White\.copy\(alpha = [^)]*pressStrength""").containsMatchIn(glass),
        )
        assertFalse(
            "la teinte de la vitre ne doit plus être éclaircie par l'appui",
            glass.contains("nominalTintAlpha +"),
        )
        // Et le point de contact n'est plus suivi : le suivi ne lit que l'appui.
        assertFalse(
            "le suivi du doigt ne doit plus lire la position (la lumière s'y plaçait)",
            glass.contains("state.move(") || glass.contains(".point"),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Le gel de défilement et son plafond de flou survivent à la suppression
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `le plafond du flou a ete retire et la regle d appui demeure`() {
        val glass = File("src/main/java/com/onyx/app/ui/design/LiquidGlass.kt").readText()
        val touch = File("src/main/java/com/onyx/app/ui/design/GlassTouch.kt").readText()

        assertFalse(
            "le plafonnement du flou pendant le geste a été retiré — il n'avait d'objet que la lumière",
            glass.contains("liveGlass") || glass.contains("if (scrolling) 0f"),
        )
        assertTrue(
            "le signal de défilement ne sert plus qu'à suspendre la réponse au doigt",
            touch.contains("val LocalGlassScrollFreeze = compositionLocalOf { false }"),
        )
        assertTrue(
            "les quatre conditions de l'appui sont intactes",
            touch.contains("if (enabled && !reduceMotion && fluidityAllows && !scrolling) pressed.coerceIn(0f, 1f) else 0f"),
        )
        assertEquals(
            "au repos, l'appui est plein",
            1f,
            glassPressStrength(enabled = true, reduceMotion = false, fluidityAllows = true, pressed = 1f),
            0f,
        )
        assertEquals(
            "et il s'éteint pendant le geste",
            0f,
            glassPressStrength(enabled = true, reduceMotion = false, fluidityAllows = true, pressed = 1f, scrolling = true),
            0f,
        )
    }
}
