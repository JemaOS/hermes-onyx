package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les fonctions **fichiers** empruntées à la référence, telles qu'elles sont réellement branchées —
 * et pas seulement écrites.
 *
 * Un décodeur qui n'est appelé de nulle part, ou une extension reconnue par un écran mais pas par
 * l'autre, serait une fonction factice. Ces contrôles lisent les sources et exigent la liaison.
 */
class ImageFormatsWiringTest {

    private fun app(path: String) = File("src/main/java/com/onyx/app/$path").readText()
    private fun core(path: String) = File("../core/src/main/java/com/onyx/app/$path").readText()

    private val bounded = app("ui/chat/BoundedImages.kt")
    private val families = core("data/model/ConversationFiles.kt")
    private val viewers = app("ui/chat/FileViewers.kt")
    private val delivered = app("ui/chat/DeliveredFiles.kt")

    @Test
    fun `le decodage borne passe le TIFF au decodeur de la maison`() {
        assertTrue("le décodeur est appelé", bounded.contains("decodeTiff("))
        assertTrue("la signature est reconnue", bounded.contains("isTiff("))
        assertTrue("le fichier est reconnu avant lecture", bounded.contains("fileLooksLikeTiff(file)"))
        assertTrue("le flux est reconnu avant lecture", bounded.contains("streamLooksLikeTiff(open)"))
        assertTrue("les octets en mémoire aussi", bounded.contains("if (isTiff(bytes))"))
        assertTrue("un plafond borne la lecture", bounded.contains("MAX_TIFF_BYTES"))
        assertTrue("l'orientation du fichier est appliquée", bounded.contains("oriented(bitmap, image.orientation)"))
    }

    @Test
    fun `le TIFF ne contourne pas le budget de dessin`() {
        assertTrue("le budget d'octets est revérifié", bounded.contains("count > maxBytes.toLong()"))
        assertTrue("le plafond de dessin aussi", bounded.contains("!BoundedImage.canDraw(count)"))
        assertTrue("le bord demandé borne le décodage", bounded.contains("decodeTiff(bytes, maxPx.coerceAtLeast(1))"))
        assertTrue("le décodage ne lève pas", bounded.contains("runCatching {"))
    }

    @Test
    fun `les trois formats sont reconnus par tous les ecrans`() {
        // L'onglet Fichiers : la famille d'un fichier décide de la vignette et de la puce.
        listOf("\"heic\"", "\"heif\"", "\"avif\"", "\"tif\"", "\"tiff\"").forEach {
            assertTrue("l'onglet Fichiers connaît $it", families.contains(it))
        }
        // Le choix du lecteur : une image s'ouvre dans le visualiseur de la maison.
        assertTrue("le lecteur photo connaît l'AVIF", viewers.contains("\"avif\""))
        assertTrue("le lecteur photo connaît le TIFF", viewers.contains("\"tif\", \"tiff\""))
        // Les pastilles du fil : la vignette dépend de la même liste.
        assertTrue("la pastille connaît l'AVIF", delivered.contains("\"avif\""))
        assertTrue("la pastille connaît le TIFF", delivered.contains("\"tif\", \"tiff\""))
    }

    @Test
    fun `un type MIME est annonce pour les trois`() {
        assertTrue("HEIC", viewers.contains("\"image/heic\""))
        assertTrue("AVIF", viewers.contains("\"image/avif\""))
        assertTrue("TIFF", viewers.contains("\"image/tiff\""))
    }

    @Test
    fun `le decodage ne bloque jamais le fil principal`() {
        val controller = app("ui/chat/ConversationFilesController.kt")
        assertTrue("la vignette est déclarée suspend", controller.contains("suspend fun loadPoster"))
        assertTrue("elle se décode sur IO", controller.contains("withContext(Dispatchers.IO) { decodeBounded(local) }"))
        assertTrue("l'image-clé de vidéo aussi", controller.contains("withContext(Dispatchers.IO) { frameOf(local) }"))
        val deliveredText = app("ui/chat/DeliveredFiles.kt")
        assertTrue(
            "l'aperçu d'un fichier livré aussi",
            deliveredText.contains("withContext(Dispatchers.IO) { BoundedImages.decodeFile(local, maxPx) }"),
        )
    }

    @Test
    fun `le PNG et le JPEG ne sont pas perdus en chemin`() {
        listOf("\"png\"", "\"jpg\"", "\"jpeg\"", "\"gif\"", "\"webp\"", "\"bmp\"").forEach {
            assertTrue("$it reste connu du modèle", families.contains(it))
            assertTrue("$it reste connu du lecteur", viewers.contains(it))
        }
        assertFalse("le décodage borné n'a pas perdu son plan", bounded.contains("inSampleSize = plan.sampleSize").not())
    }
}
