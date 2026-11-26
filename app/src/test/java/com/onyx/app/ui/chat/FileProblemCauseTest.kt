package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Une réponse qui n'est pas le fichier se **nomme**.
 *
 * Constaté sur l'émulateur : l'onglet Fichiers annonçait « Disponible · 12 o » pour une image
 * de 1 Mo, et le lecteur s'ouvrait tout noir — le serveur avait rendu un accusé
 * `{"ok": true}` à la place du fichier. Une ligne « Disponible » qui ouvre sur du noir est un
 * mensonge ; une carte « le serveur a répondu par un accusé » est une cause.
 */
class FileProblemCauseTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun file(name: String, content: ByteArray): File =
        folder.newFile(name).apply { writeBytes(content) }

    @Test
    fun `un accuse json n'est pas une image`() {
        val ack = file("photo.jpg", """{"ok": true}""".toByteArray())
        assertTrue(DeliveredFiles.looksLikeAck(ack, "photo.jpg"))
    }

    @Test
    fun `un vrai fichier binaire n'est pas un accuse`() {
        val jpeg = file("photo2.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16))
        assertFalse(DeliveredFiles.looksLikeAck(jpeg, "photo2.jpg"))
    }

    @Test
    fun `un fichier json a le droit de commencer par une accolade`() {
        val json = file("donnees.json", """{"ok": true}""".toByteArray())
        assertFalse("un .json est du texte, pas un accusé", DeliveredFiles.looksLikeAck(json, "donnees.json"))
    }

    @Test
    fun `un fichier absent ne fait pas lever`() {
        val missing = File(folder.root, "absent.jpg")
        assertFalse(DeliveredFiles.looksLikeAck(missing, "absent.jpg"))
    }

    @Test
    fun `un echec local n'offre pas un bouton qui ne peut pas aboutir`() {
        // L'autorisation d'une pièce jointe de galerie meurt avec le processus : « Réessayer »
        // échouait à chaque appui. La ligne doit pouvoir dire qu'aucun nouvel essai n'aidera.
        val expired = FileAvailability.Failed("L’accès à ce fichier a expiré.", retryable = false)
        assertFalse(expired.retryable)
        val server = FileAvailability.Failed("Le serveur a refusé la demande (400).")
        assertTrue("un refus serveur se retente", server.retryable)
    }
}
