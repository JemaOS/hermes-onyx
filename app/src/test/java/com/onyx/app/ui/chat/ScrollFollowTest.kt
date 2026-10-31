package com.onyx.app.ui.chat

import com.onyx.app.ui.design.OnyxSpace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le suivi du bas du fil, sans Compose.
 *
 * Le défaut, signalé deux fois : quand l'agent réfléchissait, on ne pouvait pas remonter lire — le
 * contenu qui grandissait (la réflexion, les lignes d'outils, le compteur de durée) ramenait la vue
 * en bas à chaque morceau. La règle : le suivi se détache **au premier doigt**, pas quand la
 * position quitte le bas.
 */
class ScrollFollowTest {
    @Test
    fun `un doigt sur la liste detache le suivi`() {
        assertFalse(ScrollFollow.afterTouch())
    }

    @Test
    fun `le raisonnement qui grandit pendant le geste ne ramene pas la vue en bas`() {
        // La personne est au bas et suit la fin.
        var following = true
        // Elle pose le doigt pour remonter : le suivi se détache tout de suite.
        following = ScrollFollow.afterTouch()
        // Un jeton de raisonnement arrive alors que la vue est **encore** au bas (le temps d'un
        // jeton, la position n'a pas bougé) : c'est le geste qui gagne, le suivi ne se ré-attache
        // pas — sinon l'effet de contenu ramènerait la vue en bas et on ne pourrait pas remonter.
        following = ScrollFollow.atRest(scrolling = true, atAnchor = true, following = following)
        assertFalse("un geste en cours ne ré-attache jamais le suivi", following)
        // La ligne d'outil suivante, le compteur qui bat, « Utilisés : N outils » qui change :
        // rien ne change, on est toujours détaché.
        following = ScrollFollow.atRest(scrolling = true, atAnchor = true, following = following)
        assertFalse(following)
    }

    @Test
    fun `revenir au bas et lacher re-attache le suivi`() {
        assertTrue(ScrollFollow.atRest(scrolling = false, atAnchor = true, following = false))
    }

    @Test
    fun `rester loin du bas au repos garde le suivi detache`() {
        assertFalse(ScrollFollow.atRest(scrolling = false, atAnchor = false, following = false))
    }

    @Test
    fun `aller au dernier message re-attache le suivi`() {
        assertTrue(ScrollFollow.jumpToLatest())
    }

    @Test
    fun `la marge d ancrage vaut une hauteur de rangee`() {
        // La frontière est **nommée**, et c'est une hauteur de rangée : le doigt qui a remonté
        // d'une ligne tient encore le bas, celui qui en a remonté trois lit.
        assertEquals(OnyxSpace.RowHeight.value, ScrollFollow.AnchorMarginDp.toFloat(), 0.001f)
        assertEquals(56f, ScrollFollow.AnchorMarginDp.toFloat(), 0.001f)
    }

    @Test
    fun `la reserve du bas ne compte pas comme du fil remonte`() {
        // Le cœur du défaut signalé. Une fenêtre de 1500 px, une réserve de 540 px (compositeur,
        // barre d'onglets, encart du système, écarts), la dernière ligne collée au haut de cette
        // réserve : la distance au bas est **zéro**. L'ancienne mesure — le bord du viewport —
        // aurait lu 540 px, soit six fois la marge, donc « pas en bas » alors qu'on y était.
        val distance = ScrollFollow.distanceToBottom(
            itemEndPx = 960,
            viewportEndPx = 1500,
            bottomPaddingPx = 540,
        )
        assertEquals(0f, distance.toFloat(), 0.001f)
        assertTrue(ScrollFollow.withinAnchor(distance, marginPx = 147))
    }

    @Test
    fun `remonter au dela de la marge sort de l ancrage`() {
        val margin = 147 // 56 dp à la densité 2,625 de l'appareil de mesure
        assertTrue("dans la marge, on est au bas", ScrollFollow.withinAnchor(margin, margin))
        assertFalse("un pixel de plus, on lit", ScrollFollow.withinAnchor(margin + 1, margin))
        // La remontée mesurée qui ne doit **plus** ramener la vue en bas : 500 px (190 dp), là où
        // l'ancienne marge allait jusqu'à 866 px.
        assertFalse(ScrollFollow.withinAnchor(500, margin))
    }

    private fun assertEquals(expected: Float, actual: Float, delta: Float) {
        org.junit.Assert.assertEquals(expected, actual, delta)
    }
}
