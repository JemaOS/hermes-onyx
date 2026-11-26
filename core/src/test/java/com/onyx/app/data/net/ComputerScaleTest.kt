package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le doigt, l'image, l'écran du PC.
 *
 * C'est la partie qu'on ne pardonne pas : un cran d'erreur et le clic part ailleurs, sur le bureau
 * de quelqu'un. D'où des cas qui couvrent aussi bien les bandes noires que la rotation du
 * téléphone.
 */
class ComputerScaleTest {

    @Test
    fun `une image du meme rapport remplit la boite`() {
        val fit = ComputerScale.fit(boxWidth = 1280f, boxHeight = 720f, imageWidth = 1280, imageHeight = 720)
        assertEquals(0f, fit.left, 0.001f)
        assertEquals(0f, fit.top, 0.001f)
        assertEquals(1280f, fit.width, 0.001f)
        assertEquals(720f, fit.height, 0.001f)
    }

    @Test
    fun `une vue plus large laisse deux bandes a gauche et a droite`() {
        // 1000x400 pour une image 1280x720 (16:9) : l'image garde sa hauteur et se centre.
        val fit = ComputerScale.fit(boxWidth = 1000f, boxHeight = 400f, imageWidth = 1280, imageHeight = 720)
        assertEquals(400f * 1280f / 720f, fit.width, 0.01f)
        assertEquals(400f, fit.height, 0.01f)
        assertEquals((1000f - fit.width) / 2f, fit.left, 0.01f)
        assertEquals(0f, fit.top, 0.01f)
    }

    @Test
    fun `un telephone en paysage met l'image au milieu`() {
        // Écran 3840x2160 dans une vue 2000x800 : mêmes proportions, donc aucun rognage.
        val fit = ComputerScale.fit(boxWidth = 2000f, boxHeight = 800f, imageWidth = 1280, imageHeight = 720)
        assertEquals(1422.2f, fit.width, 0.2f)
        assertEquals(0f, fit.top, 0.01f)
        assertTrue(fit.left > 0f)
    }

    @Test
    fun `un telephone en portrait laisse des bandes en haut et en bas`() {
        // 720x1280 de vue pour une image 1280x720 : l'image tient dans la largeur.
        val fit = ComputerScale.fit(boxWidth = 720f, boxHeight = 1280f, imageWidth = 1280, imageHeight = 720)
        assertEquals(720f, fit.width, 0.01f)
        assertEquals(405f, fit.height, 0.01f)
        assertEquals(0f, fit.left, 0.01f)
        assertTrue(fit.top > 400f)
    }

    @Test
    fun `la boite vide ne rend aucun ajustement exploitable`() {
        val fit = ComputerScale.fit(boxWidth = 0f, boxHeight = 0f, imageWidth = 1280, imageHeight = 720)
        assertEquals(0f, fit.width, 0.001f)
    }

    @Test
    fun `le centre de l'image tombe au centre de l'ecran du PC`() {
        val fit = ComputerScale.fit(boxWidth = 1280f, boxHeight = 720f, imageWidth = 1280, imageHeight = 720)
        val point = ComputerScale.toScreen(
            touchX = 640f,
            touchY = 360f,
            fit = fit,
            imageWidth = 1280,
            imageHeight = 720,
            screenWidth = 3840,
            screenHeight = 2160,
        )
        // 1280 image → 3840 écran : exactement trois fois.
        assertEquals(1920, point?.x)
        assertEquals(1080, point?.y)
    }

    @Test
    fun `les coins se ramenent aux coins, sans depasser`() {
        val fit = ComputerScale.fit(boxWidth = 640f, boxHeight = 360f, imageWidth = 1280, imageHeight = 720)
        val corner = ComputerScale.toScreen(0f, 0f, fit, 1280, 720, 3840, 2160)
        assertEquals(0, corner?.x)
        assertEquals(0, corner?.y)

        val bottomRight = ComputerScale.toScreen(640f, 360f, fit, 1280, 720, 3840, 2160)
        // Le dernier pixel de la dernière ligne, jamais 3840 (qui n'existe pas).
        assertEquals(3839, bottomRight?.x)
        assertEquals(2159, bottomRight?.y)
    }

    @Test
    fun `un doigt sur une bande noire n'envoie rien`() {
        val fit = ComputerScale.fit(boxWidth = 1000f, boxHeight = 400f, imageWidth = 1280, imageHeight = 720)
        // À gauche de l'image : c'est du vide, pas le bord du PC.
        assertNull(ComputerScale.toScreen(fit.left - 5f, 200f, fit, 1280, 720, 3840, 2160))
        // À droite, au-delà du bord droit de l'image : du vide aussi.
        assertNull(ComputerScale.toScreen(fit.right + 5f, 200f, fit, 1280, 720, 3840, 2160))
        // Dans l'image : un point, pas null.
        assertTrue(ComputerScale.toScreen(fit.left + 5f, 200f, fit, 1280, 720, 3840, 2160) != null)
    }

    @Test
    fun `le point reste borne meme avec une image plus grande que l'ecran`() {
        val fit = ComputerScale.fit(boxWidth = 1280f, boxHeight = 720f, imageWidth = 1280, imageHeight = 720)
        val point = ComputerScale.toScreen(1280f, 720f, fit, 1280, 720, 1280, 720)
        assertEquals(1279, point?.x)
        assertEquals(719, point?.y)
    }
}
