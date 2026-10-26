package com.onyx.app.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le suivi du bas du fil, sans Compose.
 *
 * Le défaut, signalé deux fois: quand l'agent réfléchissait, on ne pouvait pas remonter lire — le
 * contenu qui grandissait (la réflexion, les lignes d'outils, le compteur de durée) ramenait la vue
 * en bas à chaque morceau. La règle: le suivi se détache **au premier doigt**, pas quand la
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
        // Elle pose le doigt pour remonter: le suivi se détache tout de suite.
        following = ScrollFollow.afterTouch()
        // Un jeton de raisonnement arrive alors que la vue est **encore** au bas (le temps d'un
        // jeton, la position n'a pas bougé): c'est le geste qui gagne, le suivi ne se ré-attache
        // pas — sinon l'effet de contenu ramènerait la vue en bas et on ne pourrait pas remonter.
        following = ScrollFollow.atRest(scrolling = true, atBottom = true, following = following)
        assertFalse("un geste en cours ne ré-attache jamais le suivi", following)
        // La ligne d'outil suivante, le compteur qui bat, « Utilisés: N outils » qui change:
        // rien ne change, on est toujours détaché.
        following = ScrollFollow.atRest(scrolling = true, atBottom = true, following = following)
        assertFalse(following)
    }

    @Test
    fun `revenir au bas et lacher re-attache le suivi`() {
        assertTrue(ScrollFollow.atRest(scrolling = false, atBottom = true, following = false))
    }

    @Test
    fun `rester loin du bas au repos garde le suivi detache`() {
        assertFalse(ScrollFollow.atRest(scrolling = false, atBottom = false, following = false))
    }

    @Test
    fun `aller au dernier message re-attache le suivi`() {
        assertTrue(ScrollFollow.jumpToLatest())
    }

    @Test
    fun `le seuil du bas est celui de la reference`() {
        assertEquals(120f, ScrollFollow.Reach, 0.001f)
    }

    private fun assertEquals(expected: Float, actual: Float, delta: Float) {
        org.junit.Assert.assertEquals(expected, actual, delta)
    }
}
