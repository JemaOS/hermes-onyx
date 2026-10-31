package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le lecteur PDF : ce qu'il doit savoir faire, lu dans sa source.
 *
 * Le zoom existait déjà ; ce qui manquait, c'était de pouvoir **aller voir ailleurs** sur une page
 * agrandie. Le déplacement est donc exigé ici, avec la règle qui le rend utilisable : il prend la
 * main sur une page zoomée, et **pas** sur une page à l'échelle (sinon tourner les pages deviendrait
 * impossible).
 */
class PdfViewerPanWiringTest {

    private val viewer = File("src/main/java/com/onyx/app/ui/chat/PdfViewerScreen.kt").readText()

    @Test
    fun `une page agrandie se deplace au doigt`() {
        assertTrue("le geste de glissement est branché", viewer.contains("detectDragGestures"))
        assertTrue("il ne prend la main qu'agrandie", viewer.contains("if (zoom > 1f) {"))
        assertTrue("le déplacement est borné par la fenêtre", viewer.contains("PdfPan.clamp("))
        assertTrue("il est mesuré sur la fenêtre réelle", viewer.contains("onSizeChanged { viewport = it }"))
        assertTrue("il est porté par le calque", viewer.contains("translationX = pan.x"))
        assertTrue("sur les deux axes", viewer.contains("translationY = pan.y"))
    }

    @Test
    fun `le zoom et le carrousel restent entiers`() {
        assertTrue("le pincement est toujours là", viewer.contains("calculateZoom()"))
        assertTrue("le double tap aussi", viewer.contains("onDoubleTap"))
        assertTrue("le carrousel des pages aussi", viewer.contains("HorizontalPager("))
        assertTrue("le retour à Fit existe", viewer.contains("zoom = 1f"))
        assertTrue("le rendu reste hors ligne", !viewer.contains("http://") && !viewer.contains("https://"))
        assertFalse("le lecteur ne sort pas du téléphone", viewer.contains("Intent.ACTION_VIEW"))
    }
}
