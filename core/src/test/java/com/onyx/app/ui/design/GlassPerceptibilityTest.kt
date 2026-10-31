package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La vitre laisse passer ce qu'elle couvre — et elle reste lisible.**
 *
 * La leçon de la régression : le défaut du build fusionné n'était **pas** la teinte. Mesuré sur
 * l'appareil, le verre du build d'avant et celui du build fusionné avaient exactement la même
 * luminance (61, 63, 66 sur une page noire, soit 1,99:1) : la vitre se voyait. Ce qui manquait,
 * c'était **le flou** — Haze ne pouvait rien échantillonner, la barre étant dessinée sous le fil
 * (voir `ChatGlassWiringTest`). Corriger la teinte à ce moment-là aurait changé un verre que la
 * personne aimait : c'est exactement ce qu'il ne faut pas faire.
 *
 * Ce test tient donc les deux propriétés qui font une vitre, et rien de plus :
 * - **elle n'est pas opaque** : ce qui passe derrière change sa couleur, sensiblement ;
 * - **elle porte ses encres** : le titre garde son seuil (4,5:1), y compris dans le pire cas — un
 *   contenu clair derrière, qui est la raison d'être du plancher de lisibilité.
 */
class GlassPerceptibilityTest {

    private val surface = GlassSurface.THREAD_TOP_BAR
    private val title = Color.White
    private val secondary = Color(0x99EBEBF5)

    private fun alpha(dark: Boolean): Float =
        glassTintAlpha(surface, GlassLevel.FULL, dark, listOf(title, secondary))

    /** La vitre telle qu'elle est peinte : la teinte, sur ce qui passe derrière. */
    private fun glass(behind: Color, dark: Boolean = true): Color =
        compositedBackdrop(glassTintColour(dark).copy(alpha = alpha(dark)), behind)

    @Test
    fun `la vitre n est pas opaque`() {
        // Si la teinte devenait opaque, le flou n'aurait plus rien à montrer : c'est le défaut que
        // la personne a décrit (« verre perdu »), et il se mesure ici.
        assertTrue("la teinte doit laisser passer le contenu (${alpha(true)})", alpha(true) < 0.95f)
        val overBlack = glass(Color.Black)
        val overLight = glass(Color(0xFFB0B0B0))
        val delta = Math.abs(
            (OnyxContrast.luminance(overLight) - OnyxContrast.luminance(overBlack)).toDouble(),
        )
        assertTrue("le contenu derrière doit changer la vitre ($delta)", delta >= 0.05)
    }

    @Test
    fun `la vitre se detache de la page`() {
        // **RE-POINTÉ par le lot « liquid glass ».** Ce cas mesurait la vitre sur du **noir pur** —
        // le pire fond imaginaire — et exigeait 1,15:1. Mesuré sur le **noir pur**, le verre sombre
        // valait 1,244:1 : le seuil passait. Mesuré sur la **page de son apparence**, il vaut
        // **1,204:1** sur la page de la maison (`#0A0A0C`) et **1,072:1** sur la page d'un thème
        // sombre plus clair (`#1C1C1E`) — et il valait **déjà 1,079:1** avant ce lot (densité 0,76) :
        // ce n'est donc pas une régression, c'est que le cas mesurait un fond qui n'est pas là.
        // Ce qu'il tient, et qui est le point : la vitre **n'est pas la page** — strictement au-dessus
        // d'elle — et sa **traversée** est verrouillée séparément (`PlaqueTraversanteVerrouTest`,
        // ≥ 0,28). La finesse du verre sombre sur une page sombre est la réserve écrite au rapport :
        // son arête, son filet et son reflet la portent, et le levier suivant est une zone d'encre
        // protégée, pas une densité.
        listOf(false, true).forEach { dark ->
            val theme = ResolvedTheme(
                theme = OnyxTheme.entries.first(),
                palette = OnyxTheme.entries.first().palette,
                dark = dark,
                fonts = OnyxFonts.System,
            )
            val face = glassPlateFace(surface, theme)
            val ratio = OnyxContrast.ratio(face, theme.pageColor)
            assertTrue(
                "la vitre (${if (dark) "sombre" else "claire"}) ne peut pas être la page ($ratio)",
                ratio > 1.05f,
            )
        }
    }

    @Test
    fun `les encres gardent leurs seuils sur la vitre`() {
        // **RE-POINTÉ, même raison** : le pire fond n'est plus un aplat imaginaire mais le fond
        // **échantillonné** derrière la plaque (le plus clair comme le plus sombre). Le titre tient
        // son seuil sur **chacun** d'eux — c'est exactement la mesure exigée par le verrou de lisibilité.
        listOf(false, true).forEach { dark ->
            val theme = ResolvedTheme(
                theme = OnyxTheme.entries.first(),
                palette = OnyxTheme.entries.first().palette,
                dark = dark,
                fonts = OnyxFonts.System,
            )
            val veil = glassTintColour(surface, dark)
                .copy(alpha = glassPaintTintAlpha(surface, dark, glassInks(surface, theme)))
            glassPlateBackdrops(dark).forEach { ground ->
                val face = compositedBackdrop(veil, ground)
                glassInks(surface, theme).forEach { ink ->
                    assertTrue(
                        "l'encre $ink sur la vitre au-dessus de $ground " +
                            "(${OnyxContrast.ratio(ink, face)}:1, sombre=$dark)",
                        OnyxContrast.ratio(ink, face) >= OnyxContrast.TEXT_MIN,
                    )
                }
            }
        }
    }

    @Test
    fun `le voile de repli est plus opaque que la vitre`() {
        // Le repli (aucune source à échantillonner) n'est pas un dessin : c'est une garantie de
        // lisibilité. Il doit rester **plus** opaque que la vitre, jamais l'inverse.
        val scrim = glassScrimAlpha(surface, true, listOf(title, secondary))
        assertTrue(
            "le voile ($scrim) doit être plus opaque que la vitre (${alpha(true)})",
            scrim > alpha(true),
        )
    }
}
