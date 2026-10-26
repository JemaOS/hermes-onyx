package com.onyx.app.ui.chat

import java.io.File
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le lecteur de PDF et le partage entrant sont vraiment branchés: une route qui dessine le
 * lecteur, un chemin de retour, l'entrée depuis une autre app, et le rendu hors ligne.
 *
 * Un écran sans route, ou un filtre d'intent sans code derrière, serait une fonction factice.
 */
class PdfWiringTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val viewer = File("src/main/java/com/onyx/app/ui/chat/PdfViewerScreen.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val composer = File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()
    private val chatScreen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val messageRow = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
    private val activity = File("src/main/java/com/onyx/app/MainActivity.kt").readText()

    private fun branch(name: String): String =
        app.substringAfter("Route.$name ->").substringBefore("\n            Route.")

    @Test
    fun `la route du PDF dessine le lecteur et sait revenir`() {
        val routes = app.substringAfter("private enum class Route {").substringBefore("\n}")
        assertTrue("Route.PDF est déclarée", routes.contains("PDF"))
        assertTrue("le routeur dessine le lecteur", branch("PDF").contains("PdfViewerScreen("))
        assertTrue("la route a un chemin de retour", branch("PDF").contains("navTo(Route.CHAT.name)"))
        assertTrue("la route est présentée comme une feuille", app.contains("Route.PHOTO, Route.PDF,"))
    }

    @Test
    fun `un PDF ou une image joints s ouvrent dans l app`() {
        assertTrue("le tap d'une pastille de fichier", messageRow.contains("onOpenFile = onOpenFile"))
        assertTrue("le tap d'une pièce jointe du champ", chatScreen.contains("onOpenAttachment = onOpenPending"))
        assertTrue("la bande du composeur ouvre au tap", composer.contains("onOpen = { onOpenAttachment(attachment) }"))
        assertTrue("le choix du lecteur", app.contains("com.onyx.app.ui.chat.FileViewers.kind(name, mime)"))
        assertTrue("un PDF livré passe par le cache", app.contains("DeliveredFiles.local(context, state.server, file)"))
        assertTrue("le type MIME suit le nom", app.contains("FileViewers.mimeFor(file.name)"))
    }

    @Test
    fun `un partage de fichier depuis une autre app entre dans le composer`() {
        assertTrue("le manifeste accepte un partage d'image", manifest.contains("<data android:mimeType=\"image/*\" />"))
        assertTrue("le manifeste accepte un partage de PDF", manifest.contains("<data android:mimeType=\"application/pdf\" />"))
        assertTrue("le manifeste accepte une ouverture de fichier", manifest.contains("android.intent.action.VIEW"))
        assertTrue("le partage d'un fichier est lu", activity.contains("ShareInbox.offerFile(uri.toString(), intent.type"))
        assertTrue("l'ouverture d'un fichier est lue", activity.contains("Intent.ACTION_VIEW ->"))
        assertTrue("le stream passe par ClipData", activity.contains("clip.getItemAt(0).uri"))
        assertTrue("le fil joint le fichier reçu", app.contains("chatViewModel.addAttachments(listOf(android.net.Uri.parse(shared.uri)))"))
    }

    @Test
    fun `le lecteur ne sort jamais du telephone`() {
        assertTrue("le rendu passe par PdfRenderer", viewer.contains("PdfRenderer"))
        assertTrue("la page est dessinée pour l'écran", viewer.contains("RENDER_MODE_FOR_DISPLAY"))
        assertTrue("les pages se tournent", viewer.contains("HorizontalPager("))
        assertTrue("le zoom au pincement", viewer.contains("calculateZoom()"))
        assertFalse("aucune bibliothèque distante", viewer.contains("http://") || viewer.contains("https://"))
    }

    @Test
    fun `les libelles du lecteur suivent la langue`() {
        val fr = Locale.FRANCE
        val localization = File("../core/src/main/java/com/onyx/app/localization/OnyxLocalization.kt").readText()
        assertTrue(
            "le compte des pages a son gabarit français",
            localization.contains("Regex(\"^Page (\\\\d+) of (\\\\d+)\$\")"),
        )
        val remaining = File("../core/src/main/java/com/onyx/app/localization/OnyxFrenchRemaining.kt").readText()
        assertTrue("le refus d'un PDF se dit en français", remaining.contains("\"This PDF can't be opened.\" to"))
        assertTrue("les flèches se disent en français", remaining.contains("\"Previous page\" to") && remaining.contains("\"Next page\" to"))
        // Le gabarit appelé par le lecteur est bien celui du catalogue.
        assertTrue(com.onyx.app.localization.OnyxLocalization.translate("Page 2 of 3", fr) == "Page 2 sur 3")
    }
}
