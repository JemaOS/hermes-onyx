package com.onyx.app.intake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ce qui entre par un partage: le texte, et le fichier. Un partage sans adresse ne joint rien, et
 * consommer l'entrée efface les deux — un partage ne doit pas se rejouer au message suivant.
 */
class ShareInboxFileTest {

    @Test
    fun `un fichier partage arrive avec son type et son nom`() {
        ShareInbox.offerFile("content://downloads/42", "application/pdf", "rapport.pdf")
        val file = ShareInbox.pendingFile.value
        assertEquals("content://downloads/42", file?.uri)
        assertEquals("application/pdf", file?.mimeType)
        assertEquals("rapport.pdf", file?.name)
        ShareInbox.consume()
        assertNull(ShareInbox.pendingFile.value)
    }

    @Test
    fun `un partage sans adresse ne joint rien`() {
        ShareInbox.offerFile(null, "image/png")
        assertNull("une adresse nulle ne joint rien", ShareInbox.pendingFile.value)
        ShareInbox.offerFile("   ", "image/png")
        assertNull("une adresse vide ne joint rien", ShareInbox.pendingFile.value)
        ShareInbox.consume()
    }

    @Test
    fun `consommer efface le texte comme le fichier`() {
        ShareInbox.offer("voici le contrat")
        ShareInbox.offerFile("content://downloads/7", "application/pdf", "contrat.pdf")
        ShareInbox.consume()
        assertNull(ShareInbox.pending.value)
        assertNull(ShareInbox.pendingFile.value)
    }

    @Test
    fun `un partage de texte seul ne joint aucun fichier`() {
        ShareInbox.offer("juste une phrase")
        assertEquals("juste une phrase", ShareInbox.pending.value)
        assertNull(ShareInbox.pendingFile.value)
        ShareInbox.consume()
    }
}
