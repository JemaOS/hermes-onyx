package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
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
        // Sur l'appareil, la vitre posée sur le fil mesure 61, 63, 66 — 1,99:1 avec la page, et
        // c'est la valeur du build que la personne a validé : elle se détache, et ce n'est pas la
        // teinte qu'il fallait toucher (la mesure du build fusionné donnait **la même**).
        //
        // Ici, on ne peut comparer qu'à un aplat : la teinte seule sur du noir pur donne 1,21:1,
        // parce que ce qui passe derrière la barre n'est pas du noir mais le contenu (et le fond de
        // page), que seul l'appareil fait voir. Ce que le test peut tenir, c'est que la vitre
        // **n'est pas la page** : un rapport strictement supérieur à 1, et pas une égalité.
        val overBlack = glass(Color.Black)
        val ratio = OnyxContrast.ratio(overBlack, Color.Black)
        assertTrue("la vitre ne peut pas être la page ($ratio)", ratio > 1.15f)
    }

    @Test
    fun `les encres gardent leurs seuils sur la vitre`() {
        // Le pire cas du plancher de lisibilité : un contenu clair derrière la vitre.
        listOf(Color.Black, Color(0xFFB0B0B0), Color.White).forEach { behind ->
            val face = glass(behind)
            assertTrue(
                "le titre sur un fond $behind (${OnyxContrast.ratio(title, face)})",
                OnyxContrast.ratio(title, face) >= OnyxContrast.TEXT_MIN,
            )
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
