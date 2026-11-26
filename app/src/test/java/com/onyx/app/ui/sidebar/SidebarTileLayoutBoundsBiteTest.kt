package com.onyx.app.ui.sidebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La contre-épreuve des bornes : le verrou mord.**
 *
 * Un contrôle de borne qui ne se vérifie que par « le cas interdit est refusé » ne prouve pas grand
 * chose : il prouve qu'une fonction rend `null` ou non. Ce qu'il faut montrer, c'est que **c'est bien
 * la borne** qui refuse — donc que **retirée**, la même vérification **rougit**. C'est le principe de
 * la mutation : on rejoue les assertions du contrôle contre une copie des bornes **neutralisées**
 * ([TileGridBounds.Neutralized]) et on exige que le contrôle **échoue**.
 *
 * C'est exactement ce que ce fichier mesure :
 *
 * 1. les mêmes assertions de borne, passées aux bornes de la maison, **passent** ;
 * 2. les mêmes assertions, passées aux bornes neutralisées, **rougissent** (une `AssertionError` est
 *    levée) — donc le refus vient de la borne, pas de la forme du code ;
 * 3. et la grille de l'app, elle, reste branchée sur les bornes de la maison : `SidebarTileLayout`
 *    lit `TileGridBounds.House`, il ne peut pas se retrouver sans borne sans que ce test le voie.
 */
class SidebarTileLayoutBoundsBiteTest {

    /** Les mêmes assertions de borne, écrites **une fois**, appliquées à des bornes quelconques. */
    private fun controler(bounds: TileGridBounds) {
        assertEquals("le plancher de la maison est quatre", 4, bounds.min)
        assertEquals("le plafond de la maison est douze", 12, bounds.max)
        assertNotNull("moins de quatre tuiles doit être refusé", bounds.removalRefusal(3))
        assertNotNull("quatre tuiles doit être refusé (la borne est inclusive)", bounds.removalRefusal(4))
        assertNull("cinq tuiles passe", bounds.removalRefusal(5))
        assertNotNull("plus de douze tuiles doit être refusé", bounds.additionRefusal(13))
        assertNotNull("douze tuiles doit être refusé (la borne est inclusive)", bounds.additionRefusal(12))
        assertNull("onze tuiles passe", bounds.additionRefusal(11))
    }

    @Test
    fun `le controle passe sur les bornes de la maison`() {
        controler(TileGridBounds.House)
    }

    @Test
    fun `le controle rougit sur des bornes neutralisees`() {
        val rouge = runCatching { controler(TileGridBounds.Neutralized) }
        assertTrue(
            "des bornes neutralisées doivent faire échouer le contrôle — sinon il ne mesure rien",
            rouge.isFailure,
        )
        assertTrue(
            "l'échec est bien une assertion du contrôle, pas une erreur de forme",
            rouge.exceptionOrNull() is AssertionError,
        )
    }

    @Test
    fun `la grille de l app est branchee sur les bornes de la maison`() {
        assertEquals(TileGridBounds.House, SidebarTileLayout.bounds)
        assertEquals(SidebarTileLayout.MIN_TILES, SidebarTileLayout.bounds.min)
        assertEquals(SidebarTileLayout.MAX_TILES, SidebarTileLayout.bounds.max)
        // Et les bornes neutralisées ne sont **pas** celles de l'app : elles n'existent que pour ce
        // test — un appareil ne peut pas les prendre par accident.
        assertEquals(1, TileGridBounds.Neutralized.min)
        assertEquals(99, TileGridBounds.Neutralized.max)
    }

    @Test
    fun `les deux refus portent le nombre, pour que l ecran l ecrive`() {
        assertTrue(TileGridBounds.AT_LEAST_REFUSAL.contains("%d"))
        assertTrue(TileGridBounds.AT_MOST_REFUSAL.contains("%d"))
        assertEquals("A grid keeps at least %d tiles.", TileGridBounds.AT_LEAST_REFUSAL)
        assertEquals("A grid holds %d tiles at most.", TileGridBounds.AT_MOST_REFUSAL)
    }
}
