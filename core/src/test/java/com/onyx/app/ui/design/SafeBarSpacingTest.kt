package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La marge d'une barre posée dans une fenêtre bord à bord : **la règle est calculée**, jamais
 * écrite en dur, et c'est cette fonction pure que les tests interrogent.
 *
 * Trois jeux d'encarts, qui sont les trois tailles réellement rencontrées : la navigation par
 * **gestes** (24 dp sur l'appareil de référence), la navigation à **trois boutons** (48 dp — la
 * zone la plus haute, celle qui enterrait les rangées), et un pliable **déplié** dont la barre
 * change encore de hauteur (32 dp). Le verrou est le dernier test : une barre collée au bord de
 * l'écran doit être **fausse**, sans quoi le défaut mesuré (le geste d'accueil dessiné par-dessus
 * les libellés « Flux / Fichiers ») peut revenir sans qu'aucun test ne casse.
 */
class SafeBarSpacingTest {

    /** Les trois encarts bas : gestes, trois boutons, pliable déplié. */
    private val bottomInsetDp = listOf(24, 48, 32)

    @Test
    fun `la marge basse couvre la zone systeme, plus l espace visuel`() {
        for (inset in bottomInsetDp) {
            val margin = SafeBarSpacing.bottomMarginDp(inset)
            assertEquals("l'encart plus l'espace visuel", inset + SafeBarSpacing.GapDp, margin)
            assertTrue("l'espace visuel ne descend pas sous 8 dp", margin - inset >= 8)
            assertTrue("la barre doit dégager la zone", SafeBarSpacing.clearsSystemBottom(margin, inset))
        }
    }

    @Test
    fun `la marge haute se pose sous la barre d etat, sans espace ajoute`() {
        for (inset in listOf(0, 24, 51, 135)) {
            assertEquals(inset, SafeBarSpacing.topMarginDp(inset))
            assertTrue(SafeBarSpacing.clearsSystemTop(inset, inset))
        }
    }

    @Test
    fun `un encart negatif ne rabote jamais la marge`() {
        assertEquals(SafeBarSpacing.GapDp, SafeBarSpacing.bottomMarginDp(-40))
        assertEquals(0, SafeBarSpacing.topMarginDp(-1))
        assertTrue("une zone nulle laisse passer une barre au bord", SafeBarSpacing.clearsSystemBottom(0, 0))
    }

    /**
     * Le verrou : **une barre qui ne prend pas l'encart chevauche la zone système**, dès que la
     * zone existe. C'est le défaut d'origine — la barre d'onglets était le dernier enfant d'une
     * colonne bord à bord, donc à 0 dp du bas, et le geste d'accueil passait par-dessus.
     */
    @Test
    fun `une barre collee au bord de l ecran echoue des que la zone existe`() {
        for (inset in bottomInsetDp) {
            assertFalse(
                "collée au bord, la barre mord sur une zone de $inset dp",
                SafeBarSpacing.clearsSystemBottom(0, inset),
            )
        }
        // Et l'écart insuffisant d'un encart non lu est refusé aussi : 8 dp sous une zone de 48 dp
        // reste un chevauchement de 40 dp.
        assertFalse(SafeBarSpacing.clearsSystemBottom(8, 48))
        // La barre que le correctif pose est, elle, dégagée sur les trois.
        for (inset in bottomInsetDp) {
            assertTrue(SafeBarSpacing.clearsSystemBottom(SafeBarSpacing.bottomMarginDp(inset), inset))
        }
    }

    @Test
    fun `l espace visuel de la maison vaut au moins les 8 dp demandes`() {
        assertTrue("au moins 8 dp au-dessus de la zone système", SafeBarSpacing.GapDp >= 8)
    }
}
