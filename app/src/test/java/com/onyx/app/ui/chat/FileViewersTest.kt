package com.onyx.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Où chaque fichier joint s'ouvre: le lecteur de PDF de l'app, le visualiseur d'images, ou l'app
 * du système. Le compte des pages d'un PDF est vérifié ici aussi, puisqu'il décide de ce que la
 * barre du lecteur affiche.
 */
class FileViewersTest {

    @Test
    fun `un PDF va au lecteur de l app, par son type ou par son nom`() {
        assertEquals(FileViewerKind.PDF, FileViewers.kind("Contract", "application/pdf"))
        assertEquals(FileViewerKind.PDF, FileViewers.kind("rapport.pdf", "application/octet-stream"))
        assertEquals(FileViewerKind.PDF, FileViewers.kind("deck.PDF"))
        assertTrue(FileViewers.isPdf("plan.pdf"))
        assertFalse(FileViewers.isPdf("plan.docx"))
    }

    @Test
    fun `une image va au visualiseur plein ecran`() {
        assertEquals(FileViewerKind.PHOTO, FileViewers.kind("sunset.jpg", ""))
        assertEquals(FileViewerKind.PHOTO, FileViewers.kind("IMG_0001", "image/jpeg"))
        assertEquals(FileViewerKind.PHOTO, FileViewers.kind("shot.HEIC", "application/octet-stream"))
        assertEquals(FileViewerKind.PHOTO, FileViewers.kind("icone.svg", "image/svg+xml"))
    }

    @Test
    fun `le reste part a l app du systeme`() {
        assertEquals(FileViewerKind.EXTERNAL, FileViewers.kind("notes.txt", "text/plain"))
        assertEquals(FileViewerKind.EXTERNAL, FileViewers.kind("archive.zip"))
        assertEquals(FileViewerKind.EXTERNAL, FileViewers.kind("data.bin", "application/octet-stream"))
    }

    @Test
    fun `une video va au lecteur de l app`() {
        // Une vidéo partait à l'app du système comme un fichier quelconque: elle s'ouvre
        // maintenant dans Onyx, comme le PDF et l'image, avec sa vignette dans le fil.
        assertEquals(FileViewerKind.VIDEO, FileViewers.kind("clip.mov", "video/quicktime"))
        assertEquals(FileViewerKind.VIDEO, FileViewers.kind("clip.mp4", ""))
    }

    @Test
    fun `le type annonce au systeme suit le nom`() {
        assertEquals("application/pdf", FileViewers.mimeFor("a.pdf"))
        assertEquals("image/png", FileViewers.mimeFor("a.png"))
        assertEquals("image/jpeg", FileViewers.mimeFor("a.JPEG"))
        assertEquals("application/octet-stream", FileViewers.mimeFor("a.bin"))
    }

    @Test
    fun `le compte des pages part de un et reste dans les bornes`() {
        assertEquals("Page 1 of 3", PdfPaging.label(0, 3))
        assertEquals("Page 3 of 3", PdfPaging.label(2, 3))
        // Une page hors bornes est ramenée: jamais « Page 0 » ni « Page 4 ».
        assertEquals("Page 1 of 3", PdfPaging.label(-1, 3))
        assertEquals("Page 3 of 3", PdfPaging.label(9, 3))
        // Un document pas encore ouvert se compte comme une page, sans mentir sur le reste.
        assertEquals("Page 1 of 1", PdfPaging.label(0, 0))
        assertEquals(0, PdfPaging.clamp(4, 0))
    }

    @Test
    fun `les fleches savent si elles ont une page a montrer`() {
        assertFalse("rien avant la première page", PdfPaging.canStep(0, 3, -1))
        assertTrue("une page après la première", PdfPaging.canStep(0, 3, 1))
        assertTrue("une page avant la dernière", PdfPaging.canStep(2, 3, -1))
        assertFalse("rien après la dernière page", PdfPaging.canStep(2, 3, 1))
        assertFalse("un document d'une page ne tourne pas", PdfPaging.canStep(0, 1, 1))
    }
}
