package com.onyx.app.ui.record

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le bandeau de capture, à la largeur d'un téléphone.**
 *
 * Défaut relevé par le propriétaire sur son Honor Magic V3 : le bandeau restait sur **une** rangée
 * (un texte, deux pilules), et à 360 dp — plié — il ne passait pas. Le texte se réduisait à rien et
 * les pilules prenaient tout : « on voit rien ». La décision est ici, mesurable, plutôt que dans une
 * largeur choisie au fond d'un `Modifier`.
 */
class RecordBannerLayoutTest {

    @Test
    fun `plie, le bandeau passe sur deux rangs`() {
        // 360 dp : le Magic V3 fermé, un téléphone étroit, une fenêtre réduite.
        assertTrue(bannerStacksVertically(360f))
        assertTrue(bannerStacksVertically(412f))
    }

    @Test
    fun `deplie, il tient sur une rangee`() {
        // 673 dp : le même appareil ouvert. Au-delà de 673, tablette et paysage.
        assertFalse(bannerStacksVertically(673f))
        assertFalse(bannerStacksVertically(800f))
    }

    @Test
    fun `le seuil est celui de la mise en page, pas du dessin`() {
        // La frontière elle-même : à 420 dp, la rangée unique de la planche tient.
        assertFalse(bannerStacksVertically(NarrowWidth.value))
        assertTrue(bannerStacksVertically(NarrowWidth.value - 1f))
    }
}
