package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'aperçu, ajusté puis zoomé et déplacé.
 *
 * C'est la même arithmétique que le doigt→écran : le zoom n'est qu'un rectangle ajusté qu'on
 * multiplie, et le déplacement un décalage qu'on borne. Une erreur d'un seul cran ici envoie le
 * clic ailleurs, sur le bureau de quelqu'un.
 */
class ComputerViewportTest {

    // Un téléphone fermé : 411 dp de large, densité 2,75.
    private val boxWidth = 1080f
    private val boxHeight = 1600f
    private val imageWidth = 1280
    private val imageHeight = 720

    @Test
    fun `a l'ajustement le rectangle est celui de ComputerScale`() {
        val fit = ComputerViewport.fitOf(boxWidth, boxHeight, imageWidth, imageHeight)
        val expected = ComputerScale.fit(boxWidth, boxHeight, imageWidth, imageHeight)
        assertEquals(expected.left, fit.left, 0.001f)
        assertEquals(expected.top, fit.top, 0.001f)
        assertEquals(expected.width, fit.width, 0.001f)
        assertEquals(expected.height, fit.height, 0.001f)
    }

    @Test
    fun `le zoom multiplie le rectangle et le garde centre`() {
        val fit = ComputerViewport.fitOf(boxWidth, boxHeight, imageWidth, imageHeight, zoom = 2f)
        val base = ComputerScale.fit(boxWidth, boxHeight, imageWidth, imageHeight)
        assertEquals(base.width * 2f, fit.width, 0.01f)
        assertEquals(base.height * 2f, fit.height, 0.01f)
        // Centré : les deux bandes restantes sont égales.
        assertEquals(boxWidth - fit.width, fit.left * 2f, 0.01f)
    }

    @Test
    fun `on ne dezoome jamais sous l'ajustement`() {
        val fit = ComputerViewport.fitOf(boxWidth, boxHeight, imageWidth, imageHeight, zoom = 0.2f)
        val base = ComputerScale.fit(boxWidth, boxHeight, imageWidth, imageHeight)
        assertEquals(base.width, fit.width, 0.001f)
        assertEquals(ComputerViewport.FIT, ComputerViewport.zoomOf(0.2f), 0.001f)
        assertEquals(ComputerViewport.MAX_ZOOM, ComputerViewport.zoomOf(99f), 0.001f)
    }

    @Test
    fun `le deplacement ne fait pas sortir l'image de son cadre`() {
        // Ajustée, l'image n'a aucune marge : le déplacement ne fait rien.
        val (x, y) = ComputerViewport.clampPan(boxWidth, boxHeight, boxWidth, 900f, 500f, 500f)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)

        // Zoomée trois fois, elle déborde : le déplacement est borné à ce qui dépasse.
        val zoomed = ComputerScale.fit(boxWidth, boxHeight, imageWidth, imageHeight).width * 3f
        val (bx, by) = ComputerViewport.clampPan(boxWidth, boxHeight, zoomed, zoomed, 9999f, 9999f)
        assertEquals((zoomed - boxWidth) / 2f, bx, 0.01f)
        assertEquals((zoomed - boxHeight) / 2f, by, 0.01f)
    }

    @Test
    fun `le pincement suit le rapport des doigts`() {
        assertEquals(2f, ComputerViewport.pinchZoom(1f, 100f, 200f), 0.001f)
        assertEquals(1.5f, ComputerViewport.pinchZoom(1.5f, 200f, 200f), 0.001f)
        // Deux doigts au même point : rien ne bouge, et surtout pas une division par zéro.
        assertEquals(1.5f, ComputerViewport.pinchZoom(1.5f, 0f, 120f), 0.001f)
    }

    @Test
    fun `un pincement trop large est ramene a un cran raisonnable`() {
        // Un geste de 100 px à 900 px (×9) ne doit pas jeter l'aperçu ×9.
        val bounded = ComputerViewport.pinchZoomBounded(1f, 100f, 900f)
        assertTrue(bounded <= ComputerViewport.MAX_PINCH_STEP)
        // Et l'inverse ne dézoome pas sous l'ajustement.
        assertTrue(ComputerViewport.pinchZoomBounded(1f, 900f, 100f) >= ComputerViewport.FIT)
        // Un pincement modéré passe tel quel.
        assertEquals(1.5f, ComputerViewport.pinchZoomBounded(1f, 100f, 150f), 0.001f)
    }

    @Test
    fun `un double appui inverse le zoom, jamais autre chose`() {
        assertEquals(ComputerViewport.DOUBLE_TAP_ZOOM, ComputerViewport.doubleTapZoom(1f), 0.001f)
        assertEquals(ComputerViewport.FIT, ComputerViewport.doubleTapZoom(2.5f), 0.001f)
        assertEquals(ComputerViewport.FIT, ComputerViewport.doubleTapZoom(6f), 0.001f)
        // Un zoom d'un cheveu au-dessus de l'ajustement compte encore comme ajusté : le double
        // appui rapproche, il ne fait pas un aller-retour pour rien.
        assertEquals(ComputerViewport.DOUBLE_TAP_ZOOM, ComputerViewport.doubleTapZoom(1.01f), 0.001f)
    }

    @Test
    fun `le double appui rapproche sous le doigt, pas au centre`() {
        val zoom = ComputerViewport.DOUBLE_TAP_ZOOM
        // Le doigt touche le quart gauche de l'image.
        val base = ComputerScale.fit(boxWidth, boxHeight, imageWidth, imageHeight)
        val focusX = base.left + base.width * 0.25f
        val focusY = base.top + base.height * 0.25f
        val (panX, panY) = ComputerViewport.panToFocus(
            boxWidth, boxHeight, imageWidth, imageHeight, zoom, focusX, focusY,
        )
        val fit = ComputerViewport.fitOf(boxWidth, boxHeight, imageWidth, imageHeight, zoom, panX, panY)
        // Le quart de l'image tombe encore sous le doigt (le reste a été ramené au bord).
        val atFocus = (focusX - fit.left) / fit.width
        assertTrue("le doigt doit rester sur la même fraction de l'image, vu $atFocus", atFocus in 0.2f..0.3f)
        assertTrue(fit.contains(focusX, focusY))
    }

    @Test
    fun `le doigt hors de l'image n'envoie toujours rien, meme zoome`() {
        val fit = ComputerViewport.fitOf(boxWidth, boxHeight, imageWidth, imageHeight, zoom = 1f)
        // Ajustée, l'image laisse des bandes en haut et en bas : elles n'envoient rien.
        assertEquals(null, ComputerScale.toScreen(fit.left + 10f, 5f, fit, imageWidth, imageHeight, 3840, 2160))
        assertTrue(
            ComputerScale.toScreen(fit.left + 10f, fit.top + 10f, fit, imageWidth, imageHeight, 3840, 2160) != null,
        )
    }

    @Test
    fun `l'etat ajuste se reconnait pour n'afficher le bouton que quand il sert`() {
        assertTrue(ComputerViewport.isFitted(1f, 0f, 0f))
        assertFalse(ComputerViewport.isFitted(1.5f, 0f, 0f))
        assertFalse(ComputerViewport.isFitted(1f, 20f, 0f))
    }
}
