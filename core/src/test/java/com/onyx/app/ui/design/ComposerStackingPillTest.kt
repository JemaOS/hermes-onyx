package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le bouton « aller au dernier message » et la pile du bas : le seul chiffre qui compte est
 * **combien le bouton laisse au-dessus d'elle**.
 *
 * Le défaut signalé : le bouton était dessiné **sous** la carte du compositeur, à moitié recouvert.
 * La marge valait la somme de deux estimations (`composerHeight + tabBarHeight + 8`) **plus** un
 * `navigationBarsPadding()` — l'encart du système y était donc compté deux fois, exactement comme
 * il l'avait été pour la réserve du fil avant son correctif.
 */
class ComposerStackingPillTest {

    @Test
    fun `le bouton est entierement au dessus de la pile`() {
        // Les hauteurs **mesurées** à l'appareil de recette (navigation par gestes, densité 2,625) :
        // compositeur 96 dp, rangée 65 dp, navigation 24 dp.
        val composerDp = 96
        val rowDp = 65
        val navigationDp = 24
        val reserve = ComposerStacking.threadBottomReserveDp(
            composerDp = composerDp,
            bottomRowDp = rowDp,
            imeDp = 0,
            navigationBarsDp = navigationDp,
        )
        // Ce que le bouton laisse sous lui, compté depuis le bord bas de la fenêtre.
        val pillBottom = reserve + ComposerStacking.PillClearanceDp
        // Le haut de la pile, compté depuis le même bord. Le bouton doit être **au-dessus**,
        // avec un dégagement franc.
        val stackTop = composerDp + rowDp + navigationDp
        assertTrue(
            "le bouton doit être au-dessus du haut de la pile ($pillBottom contre $stackTop)",
            pillBottom >= stackTop + 8,
        )
        // Et le dégagement n'est pas un chiffre en l'air : c'est une marche de l'échelle.
        assertEquals("le dégagement est la marche BlockGap", OnyxSpace.BlockGap.value.toInt(), ComposerStacking.PillClearanceDp)
        assertTrue(
            "le dégagement doit être une marche de l'échelle",
            OnyxSpace.Scale.any { it.value.toInt() == ComposerStacking.PillClearanceDp },
        )
    }

    @Test
    fun `la meme reserve sert au fil et au bouton`() {
        // Une seule valeur pour les deux : le rembourrage du bas de la liste et la position du
        // bouton. Deux estimations, c'est ce qui les faisait diverger.
        val reserve = ComposerStacking.threadBottomReserveDp(
            composerDp = 96,
            bottomRowDp = 65,
            imeDp = 0,
            navigationBarsDp = 24,
        )
        assertTrue(
            "la réserve couvre la pile, et le bouton s'y ajoute",
            ComposerStacking.reserveCoversStack(
                reserveDp = reserve,
                composerDp = 96,
                bottomRowDp = 65,
                imeDp = 0,
                navigationBarsDp = 24,
            ),
        )
        assertEquals("réserve = 96 + 65 + 24 + 8", 193, reserve)
    }
}
