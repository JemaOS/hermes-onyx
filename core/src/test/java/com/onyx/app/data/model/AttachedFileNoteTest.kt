package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachedFileNoteTest {
    @Test
    fun `les notes sont retirees du texte et les chemins restent dans l ordre`() {
        val said = "Regarde ces fichiers" +
            AttachedFileNote.note("/home/onyx/Onyx attachments/2026-10-24 120000 notes.txt") +
            AttachedFileNote.picture("/home/onyx/Onyx attachments/2026-10-24 120001 photo.jpg")

        val split = AttachedFileNote.split(said)

        assertEquals("Regarde ces fichiers", split.text)
        assertEquals(
            listOf(
                "/home/onyx/Onyx attachments/2026-10-24 120000 notes.txt",
                "/home/onyx/Onyx attachments/2026-10-24 120001 photo.jpg",
            ),
            split.paths,
        )
    }

    @Test
    fun `un message compose seulement d une image est reconnu`() {
        val path = "/srv/Onyx attachments/2026-10-24 120000 image.png"
        val split = AttachedFileNote.split(AttachedFileNote.picture(path).trim())

        assertEquals("", split.text)
        assertEquals(listOf(path), split.paths)
    }

    @Test
    fun `un texte ordinaire ou un crochet final n est pas une note`() {
        assertEquals("Réponse [détail]", AttachedFileNote.split("Réponse [détail]").text)
        assertEquals("notes.txt", AttachedFileNote.sentName("/srv/2026-10-24 120000 notes.txt"))
        assertEquals("notes.txt", AttachedFileNote.sentName("/srv/2026-10-24 120000 2 notes.txt"))
        assertEquals("2026-notes.txt", AttachedFileNote.sentName("/srv/2026-notes.txt"))
    }
}
