package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le « + » du compositeur et ce qu'il ouvre : la feuille du bas, ses quatre lignes, et le refus
 * d'un fichier trop gros **avant** l'envoi.
 *
 * Ces assertions portent sur la source : c'est elle qui décide du câblage des quatre routes, et
 * c'est elle qu'un lot voisin peut casser sans qu'aucun test d'écran ne s'en aperçoive.
 */
class AttachmentSheetWiringTest {

    private val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val model = File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()
    private val composer =
        File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()

    // ---- 1. le « + » ouvre la feuille, plus la carte du milieu -----------------------------------

    @Test
    fun `le plus ouvre la feuille du bas et non plus un menu au milieu de l ecran`() {
        assertTrue("la feuille est appelée", screen.contains("AttachmentSheet("))
        assertTrue("le titre de la feuille", screen.contains("title = \"Add to message\""))
        assertFalse("le menu flottant n'est plus le menu du « + »", screen.contains("MenuItem(\"Photo Library\""))
        assertFalse(screen.contains("MenuItem(\"Files\", Symbols.Folder)"))
        // Le titre et les limites viennent du dictionnaire et du code, pas d'une chaîne recopiée.
        assertTrue(screen.contains("subtitle = AttachmentLimits.subtitle()"))
    }

    @Test
    fun `chaque ligne appelle sa route`() {
        assertTrue("la photothèque", screen.contains("AttachmentAction(\"Photo Library\", Lucide.Image)"))
        assertTrue("le sélecteur d'images", screen.contains("ActivityResultContracts.PickVisualMedia.ImageOnly"))
        assertTrue("l'appareil photo", screen.contains("AttachmentAction(\"Camera\", Lucide.Camera)"))
        assertTrue("il ouvre l'écran de prise de vue", screen.contains("onOpenCamera()"))
        assertTrue("les fichiers", screen.contains("AttachmentAction(\"Files\", Lucide.Paperclip)"))
        assertTrue("le sélecteur de fichiers", screen.contains("filePicker.launch(arrayOf(\"*/*\"))"))
        assertTrue("le collage", screen.contains("AttachmentAction(\"Paste\", Lucide.Clipboard)"))
        assertTrue("les images du presse-papiers", screen.contains("onAddAttachments(clipboardImages(context))"))
        // Les quatre lignes referment la feuille avant d'agir, sinon elle recouvre l'écran ouvert.
        assertEquals("les quatre referment la feuille", 4, Regex("showAddMenu = false").findAll(
            screen.substringAfter("if (showAddMenu) {").substringBefore("onDismiss = { showAddMenu = false }"),
        ).count())
    }

    @Test
    fun `les icones de la feuille sont celles de la famille du menu`() {
        val symbols = File("../core/src/main/java/com/onyx/app/ui/design/Symbols.kt").readText()
        // Les quatre dessins : l'image, l'appareil photo, le trombone, le presse-papiers.
        listOf("PhotoOnRectangle", "Camera", "Paperclip", "DocOnClipboard").forEach { nom ->
            assertTrue("$nom manquant du jeu", symbols.contains("val $nom = R.drawable.ic_sym_"))
        }
        // Le trombone remplace le dossier dans la feuille : c'est la forme de la référence.
        assertFalse(screen.contains("AttachmentAction(\"Files\", Symbols.Folder)"))
    }

    // ---- 2. le refus, avant l'envoi --------------------------------------------------------------

    @Test
    fun `un fichier trop gros est refuse a l ajout, avant l envoi`() {
        assertTrue("l'ajout passe à l'examen", model.contains("admitAttachments(app, _state.value.attachments, uris)"))
        assertTrue("hors du fil principal", model.contains("withContext(Dispatchers.IO) {"))
        assertTrue("le refus est publié", model.contains("attachmentProblem = admission.problem"))
        assertFalse("l'ancien ajout sans examen n'est plus là", model.contains("val added = pendingAttachmentsFromUris(resolver, uris)"))
        // Le message du refus s'affiche sous le champ, à la place du problème de dictée.
        assertTrue("le refus s'affiche", screen.contains("problem = state.attachmentProblem ?: dictationProblem"))
    }

    @Test
    fun `l examen mesure le poids et s arrete au plafond`() {
        val sizes = File("src/main/java/com/onyx/app/ui/chat/AttachmentSizes.kt").readText()
        assertTrue("la taille annoncée", sizes.contains("OpenableColumns.SIZE"))
        assertTrue("le comptage borné", sizes.contains("if (total > cap) return@runCatching total"))
        assertTrue("le verdict vient de la règle", sizes.contains("AttachmentLimits.refusal("))
        assertTrue("le premier refus se dit", sizes.contains("if (problem == null) problem = refusal"))
        assertTrue("et ce qui tient entre", sizes.contains("accepted += candidate"))
    }

    @Test
    fun `la limite du fichier texte du ViewModel est celle du jeu`() {
        assertTrue(model.contains("const val MAX_TEXT_ATTACHMENT = AttachmentLimits.MAX_TEXT_BYTES"))
    }

    @Test
    fun `la salle de groupe ouvre la meme feuille, avec le meme examen`() {
        val group = File("src/main/java/com/onyx/app/ui/bots/GroupComposer.kt").readText()
        assertTrue("la même feuille", group.contains("AttachmentSheet("))
        assertTrue("les mêmes chiffres", group.contains("subtitle = AttachmentLimits.subtitle()"))
        assertTrue("le trombone des fichiers", group.contains("AttachmentAction(\"Files\", Symbols.Paperclip)"))
        assertTrue("le même examen à l'ajout", group.contains("admitAttachments(context, attachments, uris)"))
        assertFalse("plus de carte flottante au milieu", group.contains("OnyxMenu("))
    }

    // ---- 3. une seule source pour les chiffres ---------------------------------------------------

    @Test
    fun `le compositeur et la feuille lisent les memes limites`() {
        assertTrue(composer.contains("private const val MaxAttachmentCount = AttachmentLimits.MAX_COUNT"))
        assertTrue(composer.contains("private const val MaxAttachmentBytes = AttachmentLimits.MAX_TOTAL_BYTES"))
        assertTrue(composer.contains("internal fun attachmentSizeLabel(bytes: Long): String = AttachmentLimits.sizeLabel(bytes)"))
    }
}
