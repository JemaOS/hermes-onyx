package com.onyx.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

/**
 * La pièce jointe vidéo : le lecteur de l'app, sa vignette, et son câblage.
 *
 * Le contrat est celui de la référence : une vidéo ne part **pas** à l'app du système comme un
 * fichier quelconque, elle se lit dans Onyx, et sa première image sert de vignette dans le fil.
 * Les tests de câblage lisent la source, comme les autres tests du dépôt.
 */
class VideoAttachmentTest {

    private val root = File("..")
    private val fileViewers = File("src/main/java/com/onyx/app/ui/chat/FileViewers.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val messageRow = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
    private val composer = File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun branch(name: String): String =
        app.substringAfter("Route.$name ->").substringBefore("\n            Route.")

    @Test
    fun `une video se reconnait par son type ou par son nom`() {
        assertEquals(FileViewerKind.VIDEO, FileViewers.kind("clip.mp4", "video/mp4"))
        assertEquals(FileViewerKind.VIDEO, FileViewers.kind("clip.MOV", ""))
        assertEquals(FileViewerKind.VIDEO, FileViewers.kind("inconnu.bin", "video/webm"))
        assertTrue(FileViewers.isVideo("holiday.mkv", ""))
        assertTrue(FileViewers.isVideo("a", "video/quicktime"))
        assertFalse("une image n'est pas une vidéo", FileViewers.isVideo("photo.png", "image/png"))
        assertFalse("un PDF n'est pas une vidéo", FileViewers.isVideo("doc.pdf", ""))
    }

    @Test
    fun `le lecteur reste dans l app, comme pour le PDF et l image`() {
        assertTrue("le type de fichier décide", fileViewers.contains("isVideo(name, mime) -> FileViewerKind.VIDEO"))
        assertTrue("la route est déclarée", app.contains("PHOTO, PDF, VIDEO,"))
        assertTrue("le routeur dessine le lecteur", branch("VIDEO").contains("VideoPlayerScreen("))
        assertTrue("la route a un chemin de retour", branch("VIDEO").contains("navTo(Route.CHAT.name)"))
        assertTrue("la route est présentée comme une feuille", app.contains("Route.PHOTO, Route.PDF, Route.VIDEO,"))
        assertTrue("l'ouverture passe par la route", app.contains("navTo(Route.VIDEO.name)"))
        assertTrue("la vidéo est confiée au lecteur, pas au système", branch("VIDEO").contains("if (source == null)"))
    }

    @Test
    fun `l etat vide de la route se replie hors de la composition`() {
        // Le défaut cosmétique de l'audit des routes (C1) — un `navTo` écrit pendant la
        // composition — n'est pas répété sur cette nouvelle route : le repli est dans un
        // `LaunchedEffect`, et la route vide n'entre pas dans la pile.
        val video = branch("VIDEO")
        assertTrue("le repli est posé dans un LaunchedEffect", video.contains("LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }"))
    }

    @Test
    fun `la vignette du fil montre la premiere image et le rond de lecture`() {
        assertTrue("la vignette lit la première image", messageRow.contains("VideoPoster.frame("))
        assertTrue("le rond de lecture est posé dessus", messageRow.contains("symbol = Symbols.Play"))
        assertTrue("l'appui ouvre le lecteur", messageRow.contains("onOpenFile?.invoke(attachment)"))
        assertTrue("la vidéo ne tombe pas dans la pastille de fichier", messageRow.contains("FileViewers.isVideo("))
        assertTrue("le lecteur d'écran l'annonce", messageRow.contains("VideoMeta.accessibilityLabel("))
    }

    @Test
    fun `la bande du composeur montre aussi la vignette de la video`() {
        assertTrue(composer.contains("VideoStripThumbnail(attachment = attachment, onClick = onOpen)"))
        assertTrue("le symbole d'une vidéo est le film", composer.contains("-> Symbols.Film"))
        assertTrue(composer.contains("VideoPoster.frame(context, attachment.uri, StripThumbnailPixels)"))
    }

    @Test
    fun `une video partagee entre par le composeur`() {
        assertTrue("le manifeste accepte un partage de vidéo", manifest.contains("<data android:mimeType=\"video/*\" />"))
        assertTrue("l'ouverture d'une vidéo est acceptée", manifest.contains("<data android:scheme=\"content\" android:mimeType=\"video/*\" />"))
    }

    @Test
    fun `les durees et les poids se disent comme dans la reference`() {
        assertEquals("0:07", VideoMeta.durationLabel(7_400))
        assertEquals("1:02", VideoMeta.durationLabel(62_000))
        assertEquals("1:02:03", VideoMeta.durationLabel(3_723_000))
        assertEquals("", VideoMeta.durationLabel(0))
        assertEquals("820 o", VideoMeta.sizeLabel(820, java.util.Locale.US))
        assertEquals("1.5 ko", VideoMeta.sizeLabel(1_480, java.util.Locale.US))
        assertEquals("12.4 Mo", VideoMeta.sizeLabel(12_400_000, java.util.Locale.US))
        // Le même nombre se dit avec une virgule en français : c'est un nombre, pas un identifiant.
        assertEquals("12,4 Mo", VideoMeta.sizeLabel(12_400_000, java.util.Locale.FRANCE))
        assertEquals("Video: clip.mp4, 12.4 Mo", VideoMeta.accessibilityLabel("clip.mp4", 12_400_000, java.util.Locale.US))
        assertEquals("Video: clip.mp4", VideoMeta.accessibilityLabel("clip.mp4", 0, java.util.Locale.US))
    }

    @Test
    fun `les libelles du lecteur suivent la langue`() {
        val french = java.util.Locale.FRANCE
        assertEquals(
            "Vidéo : clip.mp4, 12.4 Mo",
            com.onyx.app.localization.OnyxLocalization.translate("Video: clip.mp4, 12.4 Mo", french),
        )
        assertEquals(
            "Cette vidéo ne peut pas être lue.",
            com.onyx.app.localization.OnyxLocalization.translate("This video can't be played.", french),
        )
        val remaining = File("../core/src/main/java/com/onyx/app/localization/OnyxFrenchRemaining.kt").readText()
        assertTrue(remaining.contains("\"Back 15 seconds\" to"))
        assertTrue(remaining.contains("\"Forward 15 seconds\" to"))
    }

    @Test
    fun `aucun mot de la plateforme d origine dans les textes ajoutes`() {
        // Ce qui est balayé, c'est ce que ce lot AJOUTE : les fichiers neufs, et les valeurs
        // françaises. Les commentaires d'avant (qui citent la source) restent tels quels.
        val added = File("src/main/java/com/onyx/app/ui/chat/VideoPlayerScreen.kt").readText() +
            File("src/main/java/com/onyx/app/ui/chat/VideoPoster.kt").readText() +
            File("../core/src/main/java/com/onyx/app/localization/OnyxFrenchRemaining.kt").readText()
        listOf("i" + "OS", "i" + "Phone", "Ap" + "ple", "S" + "iri", "AVPlayer", "QuickLook").forEach { word ->
            assertFalse("« $word » ne doit pas apparaître dans le texte ajouté", added.contains(word))
        }
        assertTrue(root.isDirectory)
    }
}
