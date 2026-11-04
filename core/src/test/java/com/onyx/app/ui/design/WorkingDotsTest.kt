package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Le battement de la ligne « En cours »** — la partie qui se vérifie sans appareil.
 *
 * Le propriétaire a demandé deux choses ensemble : que l'état d'un agent au travail soit **toujours
 * écrit dans sa langue**, et qu'il **bouge** (« une animation de … qui montre qu'il travaille
 * vraiment »). Le mouvement est ici : un, deux, trois points, puis on recommence. Ce qui ne se
 * vérifie pas sur la JVM — qu'une trame d'animation ne recompose que cette ligne — est tenu par la
 * portée du `remember` dans `Gallery.kt`, pas par ce test.
 */
class WorkingDotsTest {

    @Test
    fun `les points font un deux trois puis recommencent`() {
        assertEquals(".", WorkingDots.of(0))
        assertEquals("..", WorkingDots.of(1))
        assertEquals("...", WorkingDots.of(2))
        assertEquals(".", WorkingDots.of(3))
        assertEquals("..", WorkingDots.of(4))
    }

    @Test
    fun `le cycle ne depend pas du sens de l horloge`() {
        // Un compteur négatif (jamais produit ici, mais une étape est un entier quelconque) ne doit
        // pas sortir du cycle ni lever : c'est `floorMod`, pas `%`.
        assertEquals("...", WorkingDots.of(-1))
        assertEquals("..", WorkingDots.of(-2))
    }

    @Test
    fun `sous reduire les animations les trois points sont poses d un coup`() {
        assertEquals("...", WorkingDots.Static)
        // Et le pas reste lisible : assez lent pour ne pas clignoter, assez vif pour se voir.
        assertEquals(420L, WorkingDots.PeriodMs)
    }
}
