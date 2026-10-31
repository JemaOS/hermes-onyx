package com.onyx.app.ui.sidebar

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.sidebarPaneColor
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le balayage des couples surface / encre du volet, dans les deux apparences.**
 *
 * Le défaut relevé par le propriétaire : « l'invite du champ de recherche a un texte gris sombre sur
 * fond sombre ». Le balayage est donc fait **par les mêmes fonctions que l'écran** (*[sidebarInk] par
 * l'intermédiaire de [sidebarFieldInkOn], [sidebarPaneColor], [OnyxContrast]) — le chiffre imprimé est
 * le chiffre mesuré —, et il couvre les cinq surfaces de texte du volet : l'invite de la recherche,
 * le rond d'initiale du pied (celui que le propriétaire a lu « cercle de compteur » — c'est
 * l'initiale du nom de la passerelle), les pastilles de filtre, la pastille de comptage d'une tuile,
 * et la tête du volet.
 *
 * Chaque ligne nomme **l'écran, le fichier et la ligne** : un échec doit dire **où** corriger, pas
 * seulement **que** c'est sombre.
 */
class SidebarInkVerrouTest {

    private fun name(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
    )

    private fun theme(nom: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = nom, palette = nom.palette, dark = dark, fonts = OnyxFonts.System)

    /** Une ligne du balayage : l'écran, l'élément, la surface, l'encre, le seuil, et d'où elle vient. */
    private fun pair(
        ecran: String,
        element: String,
        ground: Color,
        ink: Color,
        seuil: Float,
        origine: String,
    ): List<String> {
        val ratio = OnyxContrast.ratio(ink, ground)
        val verdict = if (ratio >= seuil) "conforme" else "SOUS LE SEUIL"
        val ligne = "   $ecran · $element · ${name(ground)} / ${name(ink)} = " +
            "${"%.2f".format(ratio)}:1 (seuil ${"%.2f".format(seuil)}) → $verdict [$origine]"
        assertTrue(
            "VOLET / $ecran / $element : encre ${name(ink)} sur ${name(ground)} = " +
                "${"%.2f".format(ratio)}:1, sous le seuil de ${"%.2f".format(seuil)} — $origine",
            ratio >= seuil,
        )
        return listOf(ligne)
    }

    /**
     * **Le relevé, écran → élément → contraste sombre → contraste clair → verdict.** Imprimé par le
     * test : c'est le tableau du rapport.
     */
    @Test
    fun `le releve du volet est imprime et tient les seuils dans les deux apparences`() {
        val ecran = "Volet latéral"
        println("== VOLET — écran → élément → rapport sombre → rapport clair → verdict ==")
        listOf(true, false).forEach { d ->
            val t = theme(OnyxTheme.STANDARD, d)
            val apparance = if (d) "sombre" else "clair"
            println(" -- apparence $apparance")
            val pane = sidebarPaneColor(t)
            val rows = buildList {
                // 1. L'invite du champ de recherche — le défaut relevé.
                add(pair(ecran, "recherche · invite", t.pageColor, sidebarFieldInkOn(t), 4.5f,
                    "ChatSidebar.kt:2869 (sidebarFieldInkOn)"))
                add(pair(ecran, "recherche · loupe", t.pageColor,
                    sidebarInk(t.textColor, t.pageColor, OnyxContrast.ICON_MIN + 0.5f), 3.0f,
                    "ChatSidebar.kt:2845 (mutedIconInk)"))
                // 2. Le pied : le rond d'initiale et son initiale.
                val avatarFill = OnyxContrast.over(t.accent.copy(alpha = 0.16f), pane)
                add(pair(ecran, "pied · rond d'initiale", avatarFill, t.textColor, 4.5f,
                    "ChatSidebar.kt:2628 (avatarFill) et :2676 (l'initiale)"))
                add(pair(ecran, "pied · icône du rond", avatarFill,
                    sidebarInk(t.accent, avatarFill, OnyxContrast.ICON_MIN), 3.0f,
                    "ChatSidebar.kt:2671"))
                add(pair(ecran, "pied · point d'état", avatarFill,
                    sidebarInk(t.success, avatarFill, OnyxContrast.ICON_MIN), 3.0f,
                    "ChatSidebar.kt:2692"))
                // 3. Les pastilles de filtre : choisie, puis au repos.
                val chosen = OnyxContrast.filled(t.accent)
                add(pair(ecran, "filtre · pastille choisie", chosen, OnyxContrast.inkOn(chosen), 4.5f,
                    "ChatSidebar.kt:2737"))
                val resting = if (d) pane else t.formSurfaceColor
                add(pair(ecran, "filtre · pastille au repos", resting,
                    sidebarInk(t.textColor, resting, OnyxContrast.TEXT_MIN), 4.5f,
                    "ChatSidebar.kt:2741-2743"))
                // 4. La pastille de comptage d'une tuile.
                val badgeFill = OnyxContrast.filled(t.accent)
                add(pair(ecran, "tuile · pastille de compte", badgeFill, OnyxContrast.inkOn(badgeFill), 4.5f,
                    "ChatSidebar.kt:2816-2828 (BadgeCount)"))
                // 5. La tête du volet : l'adresse, et le point du voyant.
                add(pair(ecran, "tête · adresse", pane, sidebarInk(t.textColor, pane, OnyxContrast.TEXT_MIN), 4.5f,
                    "ChatSidebar.kt:847"))
                add(pair(ecran, "tête · voyant d'état", pane,
                    sidebarInk(t.success, pane, OnyxContrast.ICON_MIN), 3.0f,
                    "ChatSidebar.kt:785"))
                // 6. Le filet du volet : il doit se distinguer de ce qu'il borde (composé sur le
                //    panneau, sinon le rapport d'un blanc à 12 % ne dit rien).
                add(pair(ecran, "filet du volet", pane,
                    OnyxContrast.over(OnyxSurfaces.hairline(t), pane), OnyxSurfaces.HairlineMin,
                    "OnyxSurfaces.kt:138 (hairline)"))
            }
            rows.forEach { println(it) }
        }
    }

    /** **Tous les thèmes, les deux apparences** : l'invite de la recherche tient son seuil partout. */
    @Test
    fun `l'invite du champ de recherche tient son seuil sur tous les themes`() {
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { d ->
                val t = theme(nom, d)
                val ink = sidebarFieldInkOn(t)
                val ratio = OnyxContrast.ratio(ink, t.pageColor)
                assertTrue(
                    "VOLET / recherche / invite ($nom, ${if (d) "sombre" else "clair"}) : " +
                        "${name(ink)} sur ${name(t.pageColor)} = ${"%.2f".format(ratio)}:1, sous le " +
                        "seuil de 4,50:1 — app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt:2869 " +
                        "(sidebarFieldInkOn)",
                    ratio >= OnyxContrast.TEXT_MIN,
                )
            }
        }
    }

    /**
     * **Le rond d'initiale du pied bat du bon côté dans chaque apparence** : son initiale est l'encre
     * pleine du thème, donc claire sur un rond sombre et sombre sur un rond clair.
     */
    @Test
    fun `le rond d'initiale bat avec l'apparence`() {
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { d ->
                val t = theme(nom, d)
                val fill = OnyxContrast.over(t.accent.copy(alpha = 0.16f), sidebarPaneColor(t))
                val ratio = OnyxContrast.ratio(t.textColor, fill)
                assertTrue(
                    "VOLET / pied / rond d'initiale ($nom, ${if (d) "sombre" else "clair"}) : " +
                        "${name(t.textColor)} sur ${name(fill)} = ${"%.2f".format(ratio)}:1 — " +
                        "ChatSidebar.kt:2628 (avatarFill) et :2676 (l'initiale)",
                    ratio >= OnyxContrast.TEXT_MIN,
                )
            }
        }
    }
}
