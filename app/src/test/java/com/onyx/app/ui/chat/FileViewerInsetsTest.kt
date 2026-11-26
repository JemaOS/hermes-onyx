package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou des lecteurs plein écran : chacun passe par l'utilitaire commun des marges du système.
 *
 * Ce qui se prouve ici, c'est la **règle** — la croix sous la bande haute, la légende au-dessus de
 * la bande basse, une seule source pour ces marges — et non le rendu, qui se juge sur capture.
 * Une régression qui remettrait un rembourrage local (une croix collée à la barre d'état) fait
 * rougir ce test.
 */
class FileViewerInsetsTest {

    private fun read(path: String) = File(path).readText()

    private val photo = read("src/main/java/com/onyx/app/ui/chat/PhotoViewerScreen.kt")
    private val video = read("src/main/java/com/onyx/app/ui/chat/VideoPlayerScreen.kt")
    private val pdf = read("src/main/java/com/onyx/app/ui/chat/PdfViewerScreen.kt")
    private val text = read("src/main/java/com/onyx/app/ui/chat/SelectableTextScreen.kt")

    @Test
    fun `le visualiseur d images pose la croix sous la bande haute et la legende au-dessus de la bande basse`() {
        // La règle commune, pas un calcul local.
        assertTrue("la croix descend sous la barre d'état", photo.contains(".statusBarClearance()"))
        assertTrue("la légende remonte au-dessus de la zone de navigation", photo.contains(".navigationBarClearance()"))
        // Le défaut d'origine : un rembourrage unique de 16 dp, collé au bord de l'écran.
        val oldTopChrome = Regex(
            """align\(Alignment\.TopCenter\)\s*\.fillMaxWidth\(\)\s*\.padding\(16\.dp\)""",
        )
        assertFalse("la croix ne doit plus être posée à 16 dp du bord", oldTopChrome.containsMatchIn(photo))
        // La croix ferme toujours : son appui appelle `onClose`.
        assertTrue("la croix doit fermer", photo.contains("onClick = onClose"))
    }

    @Test
    fun `la croix ne bouge pas quand l image est glissee ou zoomee`() {
        // La bande de commandes est posée dans la **boîte** de l'écran (le `Modifier.align` n'existe
        // que là), donc à côté du carrousel et hors de son `graphicsLayer` : le zoom et le
        // glissement de l'image ne peuvent pas la déplacer.
        assertTrue(photo.contains(".align(Alignment.TopCenter)"))
        assertTrue(photo.contains(".align(Alignment.BottomCenter)"))
        // Et le zoom vit sur la page, pas sur la bande : translation et échelle sont sur le
        // carrousel et sur l'image.
        assertTrue(photo.contains("translationY = dragY"))
        assertTrue(photo.contains("scaleX = scale"))
    }

    @Test
    fun `le lecteur video prend les deux bandes`() {
        assertTrue(video.contains(".statusBarClearance()"))
        assertTrue(video.contains(".navigationBarClearance()"))
        // Plus d'appel direct à la marge du système, qui écrivait la règle un écran à la fois.
        assertFalse(video.contains("statusBarsPadding()"))
        assertFalse(video.contains("navigationBarsPadding()"))
        assertTrue("la croix doit fermer", video.contains("onClick = onClose"))
    }

    @Test
    fun `le lecteur de PDF prend la bande haute et la bande basse`() {
        assertTrue(pdf.contains(".statusBarClearance()"))
        assertTrue(pdf.contains(".navigationBarClearance()"))
        assertFalse(pdf.contains("statusBarsPadding()"))
        assertFalse(pdf.contains("navigationBarsPadding()"))
        assertTrue("la croix doit fermer", pdf.contains("onClick = onClose"))
    }

    @Test
    fun `l apercu de piece jointe texte prend la bande haute et la bande basse`() {
        assertTrue(text.contains(".statusBarClearance()"))
        assertTrue(text.contains(".navigationBarClearance()"))
    }
}
