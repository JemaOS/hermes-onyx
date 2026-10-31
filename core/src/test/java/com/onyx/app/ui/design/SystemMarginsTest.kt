package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou des marges du système, joué sur les **trois jeux de bandes** qu'un téléphone peut
 * présenter — sans barre, gestes, trois boutons — plus un jeu de densité.
 *
 * La règle est la même partout : sous la bande haute, au-dessus de la bande basse, et **jamais
 * moins** que la marge nette ([SystemMargins.ClearanceDp] dp) même quand la bande est absente.
 * C'est la fonction qui place la croix et la légende ; elle se prouve sans appareil.
 */
class SystemMarginsTest {

    // Les trois jeux : (hauteur de la bande haute, hauteur de la bande basse), en dp.
    private val noBands = 0 to 0
    private val gesture = 24 to 24
    private val threeButtons = 24 to 48

    @Test
    fun `sans bande, la croix garde la marge nette et ne colle pas au bord`() {
        val (top, _) = noBands
        // Aucune bande du système : il reste la marge nette seule, jamais zéro.
        assertEquals(SystemMargins.ClearanceDp, SystemMargins.topPx(top, density = 1f))
        assertEquals(8, SystemMargins.topPx(top, density = 1f))
        // Un contenu posé exactement à la marge nette est déjà dégagé.
        assertTrue(SystemMargins.clearsSystemBand(contentEdgePx = 8, insetPx = top, density = 1f))
    }

    @Test
    fun `en gestes, la croix descend sous la bande haute`() {
        val (top, bottom) = gesture
        // Barre d'état de 24 dp + 8 dp de marge nette : la croix ne peut plus la mordre.
        assertEquals(32, SystemMargins.topPx(top, density = 1f))
        // La bande de geste de 24 dp + 8 dp : la légende se pose juste au-dessus.
        assertEquals(32, SystemMargins.bottomPx(bottom, density = 1f))
        // Une croix posée à l'ancienne (16 dp du bord) tombait dans la bande : refusé.
        assertFalse(SystemMargins.clearsSystemBand(contentEdgePx = 16, insetPx = top, density = 1f))
        assertTrue(SystemMargins.clearsSystemBand(contentEdgePx = 32, insetPx = top, density = 1f))
    }

    @Test
    fun `en trois boutons, la legende remonte au-dessus de la barre`() {
        val (top, bottom) = threeButtons
        assertEquals(32, SystemMargins.topPx(top, density = 1f))
        // Barre de navigation de 48 dp + 8 dp de marge nette : la légende ne tombe plus dessus.
        assertEquals(56, SystemMargins.bottomPx(bottom, density = 1f))
        assertFalse(SystemMargins.clearsSystemBand(contentEdgePx = 16, insetPx = bottom, density = 1f))
        assertTrue(SystemMargins.clearsSystemBand(contentEdgePx = 56, insetPx = bottom, density = 1f))
    }

    @Test
    fun `le calcul suit la densite de l ecran`() {
        // Densité 3 : 1 dp = 3 px. Barre d'état de 24 dp = 72 px, marge nette de 8 dp = 24 px.
        assertEquals(96, SystemMargins.topPx(statusBarPx = 72, density = 3f))
        // Barre de navigation de 48 dp = 144 px, plus la même marge nette.
        assertEquals(168, SystemMargins.bottomPx(navigationBarPx = 144, density = 3f))
        // Une marge nette explicite se convertit de la même façon.
        assertEquals(72, SystemMargins.topPx(statusBarPx = 48, density = 3f, clearanceDp = 8))
    }
}
