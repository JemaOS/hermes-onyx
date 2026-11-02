package com.onyx.app

import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.glassInks
import com.onyx.app.ui.design.glassPaintBlurDp
import com.onyx.app.ui.design.glassPaintTintAlpha
import com.onyx.app.ui.design.glassPlateEdgeDp
import com.onyx.app.ui.design.glassPlateEdgeUsed
import com.onyx.app.ui.design.glassPlateGrainUsed
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Le verrou du lot « verre-fond » — le verre doit être VISIBLE AU REPOS.**
 *
 * La plainte du propriétaire, capture d'appareil à l'appui : « l'effet de transparence verre ne
 * s'active qu'au clic ; il doit être là de base ». Un lot précédent a établi, mesures à l'appui,
 * que ce **n'est pas** un état d'interaction (`clair-repos` contre `clair-clic` = 0 pixel
 * différent sur 32 900) : le chemin de peinture ne lit ni enfoncement ni défilement. Ce que le
 * repos n'a pas, c'est **du fond à voir à travers la vitre**.
 *
 * Ce que ce lot mesure et verrouille, en le **nommant** quand ça tombe :
 *
 *  1. **la vitre échantillonne la page qu'elle recouvre.** Mesuré au repos sur l'appareil (APK
 *     991, `sonde2.py`) : la page au-dessus de la plaque vaut `#D3DCD7` (clair) / `#181818`
 *     (sombre) — c'est la matière, qui teinte et assombrit la couleur de page. La plaque, elle,
 *     valait `#FCFCFD` / `#707072` : un **aplat neutre**, indifférent à la page. La cause : la
 *     page était peinte **hors** de la source Haze, qui ne contenait que le contenu du pageur ;
 *     Haze remplissait donc le vide avec `theme.pageColor` — un blanc qui n'est pas ce qu'on voit.
 *     La matière de la page doit donc être **dans** la source (posée après `onyxHazeSource`),
 *     sans quoi la vitre est un aplat et **rien ne traverse**.
 *  2. **la plaque reste une vitre** : flou non nul, voile non opaque, **lisse (sans grain)** et arête
 *     présente — les pièces qui font qu'une surface se lit comme du verre et non comme un panneau.
 *
 * **Ce test MORD AVANT ce lot** : à l'état d'avant, la matière est posée **avant** la source, donc
 * la première assertion échoue en nommant l'ordre fautif et la valeur mesurée du repos.
 */
class VerreFondVerrouTest {

    private fun code(relative: String): String {
        val candidates = listOf(File(relative), File("../$relative"), File("core/$relative"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("source introuvable : $relative (depuis ${File(".").absolutePath})")
        return file.readText()
    }

    private fun themeFor(dark: Boolean): ResolvedTheme = ResolvedTheme(
        theme = OnyxTheme.entries.first(),
        palette = OnyxTheme.entries.first().palette,
        dark = dark,
        fonts = OnyxFonts.System,
    )

    // ---------------------------------------------------------------------------------------------
    // 1 · LE FOND TRAVERSE LA VITRE — la page est DANS la source, donc échantillonnée
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `au repos la vitre echantillonne la page qu elle recouvre`() {
        val screen = code("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
        val source = screen.indexOf("onyxHazeSource(")
        val matiere = screen.indexOf(".matierePage(theme)")
        assertTrue(
            "ChatScreen ne pose plus la source Haze (`onyxHazeSource`) : la vitre ne peut plus " +
                "échantillonner, elle retombe sur son voile.",
            source >= 0,
        )
        assertTrue(
            "ChatScreen ne peint plus la matière de la page (`matierePage(theme)`) : la page " +
                "n'a plus de teinte ni de grain, et la vitre n'a rien à montrer.",
            matiere >= 0,
        )
        assertTrue(
            "LA MATIÈRE DE LA PAGE EST PEINTE AVANT LA SOURCE HAZE (indice $matiere < $source) : " +
                "la source ne la capture donc pas. Mesuré au repos sur l'appareil (APK 991) : le " +
                "corps de la plaque valait `#FCFCFD` (clair) et `#707072` (sombre) — un aplat " +
                "NEUTRE, identique à lui-même et indifférent à la page (`#D3DCD7` / `#181818`) : " +
                "c'est exactement le défaut « le verre ne s'active qu'au clic ». La matière doit " +
                "être posée DANS la source (`Box(Modifier.matchParentSize().matierePage(theme))` " +
                "après `onyxHazeSource`), pour que la plaque montre la page qu'elle recouvre.",
            matiere > source,
        )
    }

    /**
     * **La salle de groupe, le même verrou.**
     *
     * La salle avait exactement le défaut du fil : sa matière de page (`onyxPage`) était peinte sur
     * la `Box` **racine**, donc **hors** de la source Haze — et le fil seul était capturé. Le menu
     * ancré, posé au-dessus de la salle, échantillonnait alors un **aplat** : mesuré au repos,
     * l'écart-type du fond sous le panneau valait **1,3** pour un contenu réel variant de **0,6** à
     * **94,7** — la teinte du verre, posée sur du vide.
     *
     * La matière doit donc être dans la source, comme dans le fil.
     */
    @Test
    fun `la salle peint sa matiere dans la source, comme le fil`() {
        val salle = code("src/main/java/com/onyx/app/ui/bots/GroupRoomScreen.kt")
        val source = salle.indexOf("onyxHazeSource(")
        val matiere = salle.indexOf(".onyxPage(theme)")
        assertTrue(
            "la salle ne pose plus la source Haze (`onyxHazeSource`) : la vitre ne peut plus " +
                "échantillonner, elle retombe sur son voile.",
            source >= 0,
        )
        assertTrue(
            "la salle ne peint plus la matière de la page (`onyxPage(theme)`) : la page n'a plus de " +
                "teinte, et la vitre n'a rien à montrer.",
            matiere >= 0,
        )
        // La matière doit être un **enfant** de la source (`Box(Modifier.matchParentSize()
        // .onyxPage(theme))` dans la source), et non un modificateur de la chaîne de la source :
        // dans une chaîne, ce qui est écrit avant est appliqué **en dernier** — donc **dessus** la
        // source, hors de sa capture. Mesuré sur l'appareil : derrière le menu ancré, le fond avait
        // un écart-type de 1,3 pour un contenu réel de 0,6 à 94,7 — la teinte du verre posée sur du
        // vide. C'est la même faute que le fil avait, et la même correction.
        assertTrue(
            "la matière de la page est un ENFANT de la source Haze (indice $matiere > $source) : " +
                "posée dans la chaîne de la source, elle serait peinte **dessus** elle — donc hors " +
                "de sa capture — et la vitre n'aurait qu'un aplat à manger. Elle doit être posée " +
                "comme dans le fil : `Box(Modifier.matchParentSize().onyxPage(theme))` à l'intérieur " +
                "de la source.",
            matiere > source,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 2 · LA PLAQUE RESTE UNE VITRE — flou, voile, grain, arête
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la plaque du bas reste une vitre et non un panneau`() {
        val surface = GlassSurface.TAB_BAR
        val blur = glassPaintBlurDp(surface)
        assertTrue(
            "le flou peint de la plaque vaut $blur dp — un flou NUL fait de la plaque un panneau " +
                "opaque (la vitre n'a plus rien à noyer).",
            blur > 0f,
        )
        listOf(false, true).forEach { dark ->
            val veil = glassPaintTintAlpha(surface, dark, glassInks(surface, themeFor(dark)))
            assertTrue(
                "le voile peint de la plaque (sombre=$dark) vaut $veil — une vitre OPAQUE ne " +
                    "laisse rien traverser.",
                veil < 1f,
            )
        }
        // **EXIGENCE DU PROPRIÉTAIRE : la plaque est LISSE.** Ce cas exigeait le CONTRAIRE avant ce
        // lot (« la plaque porte son grain », ajouté par le lot « verre-plaques » pour « faire
        // matière » au repos) ; il est RE-POINTÉ sur la nouvelle exigence, avec le motif :
        // « tu as mis une espèce de grain, supprime ce grain ». Ce qui fait lire la vitre comme du
        // verre, c'est désormais sa TEINTE — pas un motif.
        assertEquals(
            "la plaque porte ENCORE un grain (glassPlateGrainUsed = ${glassPlateGrainUsed(surface)}) : " +
                "le propriétaire l'a REFUSÉ sur capture (« supprime ce grain »). La vitre est lisse.",
            0f,
            glassPlateGrainUsed(surface),
            0f,
        )
        assertTrue(
            "la plaque n'a plus son arête de verre (glassPlateEdgeUsed = " +
                "${glassPlateEdgeUsed(surface)}) : sans arête, la plaque se lit comme un aplat " +
                "découpé.",
            glassPlateEdgeUsed(surface),
        )
        assertTrue(
            "l'arête de la plaque a une épaisseur nulle (${glassPlateEdgeDp(surface)} dp).",
            glassPlateEdgeDp(surface) > 0f,
        )
        println(
            "\nVERRE AU REPOS — plaque du bas (TAB_BAR)\n" +
                "  flou peint            : $blur dp\n" +
                "  voile clair / sombre  : ${glassPaintTintAlpha(surface, false, glassInks(surface, themeFor(false)))} / " +
                "${glassPaintTintAlpha(surface, true, glassInks(surface, themeFor(true)))}\n" +
                "  grain                 : ${glassPlateGrainUsed(surface)}\n" +
                "  arête                 : ${glassPlateEdgeDp(surface)} dp (peinte = ${glassPlateEdgeUsed(surface)})\n",
        )
    }
}
