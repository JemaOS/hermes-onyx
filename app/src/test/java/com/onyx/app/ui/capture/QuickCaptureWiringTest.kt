package com.onyx.app.ui.capture

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Capture rapide » est vraiment branchée: une route, un écran, deux gestes pour y entrer —
 * dont un **depuis n'importe où** (le raccourci de l'illustration, qui passe par un intent) — et
 * la note écrite sur le disque dès qu'elle est prise.
 *
 * Une route sans point d'entrée, ou un filtre d'intent sans code derrière, serait une fonction
 * factice.
 */
class QuickCaptureWiringTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val screen = File("src/main/java/com/onyx/app/ui/capture/QuickCaptureScreen.kt").readText()
    private val intake = File("src/main/java/com/onyx/app/intake/ShareInbox.kt").readText()
    private val activity = File("src/main/java/com/onyx/app/MainActivity.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val shortcuts = File("src/main/res/xml/shortcuts.xml").readText()
    private val application = File("src/main/java/com/onyx/app/OnyxApplication.kt").readText()
    private val session = File("src/main/java/com/onyx/app/data/capture/CaptureSession.kt").readText()

    private fun branch(name: String): String =
        app.substringAfter("Route.$name ->").substringBefore("\n            Route.")

    @Test
    fun `la route existe, dessine l ecran et sait revenir`() {
        assertTrue("Route.QUICK_CAPTURE est déclarée", app.contains("QUICK_CAPTURE,"))
        assertTrue("le routeur dessine l'écran", branch("QUICK_CAPTURE").contains("QuickCaptureScreen("))
        assertTrue("la route a un chemin de retour", branch("QUICK_CAPTURE").contains("navTo(Route.CHAT.name)"))
        assertTrue("la route est présentée comme une feuille", app.contains("Route.QUICK_CAPTURE,"))
    }

    @Test
    fun `la note se prend depuis l app, en deux gestes`() {
        assertTrue(
            "une entrée depuis le fil",
            app.contains("MenuItem(\"Take a note\", Symbols.SquareAndPencil) { navTo(Route.QUICK_CAPTURE.name) }"),
        )
        assertTrue("l'écran a son champ", screen.contains("OnyxTextField("))
        assertTrue("l'écran a son bouton", screen.contains("\"Take the note\""))
        assertTrue("l'écran ne reste pas muet", screen.contains("Filed.") && screen.contains("Waiting for the server. It will go on its own."))
    }

    @Test
    fun `la note se prend aussi depuis n importe ou`() {
        assertTrue("le dépôt d'intake accepte la demande", intake.contains("fun requestCapture()"))
        assertTrue("et la rend à l'UI", intake.contains("val captureRequested: StateFlow<Boolean>"))
        assertTrue("l'action du raccourci est lue", activity.contains("ACTION_CAPTURE -> ShareInbox.requestCapture()"))
        assertTrue("l'action est déclarée", activity.contains("\"com.onyx.app.action.CAPTURE\""))
        assertTrue("le manifeste l'accepte", manifest.contains("<action android:name=\"com.onyx.app.action.CAPTURE\" />"))
        assertTrue("le raccourci de l'illustration existe", shortcuts.contains("android:shortcutId=\"note\""))
        assertTrue("le raccourci porte la même action", shortcuts.contains("android:action=\"com.onyx.app.action.CAPTURE\""))
        assertTrue("l'app ouvre l'écran sur la demande", app.contains("navTo(Route.QUICK_CAPTURE.name)"))
    }

    @Test
    fun `la boite est installee au demarrage et ecrit sur le disque`() {
        assertTrue(application.contains("QuickCapture.install(this)"))
        assertTrue("les notes sont écrites au moment où elles sont prises", session.contains("persist = { notes -> write(preferences, notes) }"))
        assertTrue("et relues au démarrage", session.contains("initial = read(preferences)"))
        assertTrue(
            "et retentées quand l'app reparaît",
            app.contains("LaunchedEffect(Unit) { com.onyx.app.data.capture.QuickCapture.fileNow() }"),
        )
    }

    @Test
    fun `la note part en un seul message, dans un tour que personne ne regarde`() {
        assertTrue("le message est celui du classement", session.contains("CaptureCopy.message(notes)"))
        assertTrue("les deux transports sont couverts", session.contains("Transport.HERMES_SERVE ->") && session.contains("ChatCompletionsClient(server).stream(request)"))
        assertTrue("le flux est jeté", session.contains(".collect { }"))
    }

    @Test
    fun `rien ne se dit en retour`() {
        // Aucune lecture à voix haute n'est branchée sur la remise: la réponse de l'agent n'est
        // ni dite, ni posée dans la conversation ouverte.
        assertFalse("aucune synthèse vocale dans la remise", session.contains("voice.speak"))
        assertFalse("aucun envoi dans la conversation ouverte", session.contains("chatViewModel"))
    }

    @Test
    fun `les libelles suivent la langue`() {
        val french = Locale.FRANCE
        assertEquals("Prendre une note", com.onyx.app.localization.OnyxLocalization.translate("Take a note", french))
        assertEquals("Prendre la note", com.onyx.app.localization.OnyxLocalization.translate("Take the note", french))
        assertEquals("Classée.", com.onyx.app.localization.OnyxLocalization.translate("Filed.", french))
        assertEquals("Rien en attente.", com.onyx.app.localization.OnyxLocalization.translate("Nothing waiting.", french))
        assertEquals("En attente", com.onyx.app.localization.OnyxLocalization.translate("Waiting", french))
        assertEquals(
            "le libellé d'exemple du champ suit la langue",
            "Le plombier passe jeudi à dix heures",
            com.onyx.app.localization.OnyxLocalization.translate("The plumber comes Thursday at ten", french),
        )
    }

    @Test
    fun `aucun mot de la plateforme d origine dans les textes ajoutes`() {
        // Les fichiers neufs de ce lot, et les valeurs françaises qu'il ajoute.
        val added = listOf(screen, session, intake).joinToString("\n") +
            File("../core/src/main/java/com/onyx/app/localization/OnyxFrenchRemaining.kt").readText()
        listOf("iOS", "iPhone", "Apple", "Siri", "AppIntent").forEach { word ->
            assertFalse("« $word » ne doit pas apparaître dans le texte ajouté", added.contains(word))
        }
    }
}
