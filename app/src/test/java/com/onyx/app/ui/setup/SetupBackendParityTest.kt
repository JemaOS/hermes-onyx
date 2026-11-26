package com.onyx.app.ui.setup

import com.onyx.app.data.model.Transport
import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parité de l'écran « Set up Onyx » avec la capture référence 2.0.
 *
 * La carte d'intro à trois lignes, « Have a setup code? » et son bouton « Paste », la liste des
 * trois transports avec sa coche, et les champs par transport.
 */
class SetupBackendParityTest {
    private val source = File("src/main/java/com/onyx/app/ui/setup/SetupScreen.kt").readText()

    @Test
    fun `la liste des transports suit l'ordre et les mots de la référence`() {
        assertEquals(
            listOf(Transport.HERMES_SERVE, Transport.HERMES_SESSIONS, Transport.CHAT_COMPLETIONS),
            BackendTransports,
        )
        assertEquals("Hermes Dashboard (WebSocket)", transportLabel(Transport.HERMES_SERVE))
        assertEquals("Hermes API (shared sessions)", transportLabel(Transport.HERMES_SESSIONS))
        assertEquals("OpenAI-compatible (direct to a model)", transportLabel(Transport.CHAT_COMPLETIONS))
    }

    @Test
    fun `les trois transports sont traduits en francais`() {
        val fr = Locale.FRANCE
        assertEquals("Dashboard Hermes (WebSocket)", OnyxLocalization.translate("Hermes Dashboard (WebSocket)", fr))
        assertEquals("API Hermes (sessions partagées)", OnyxLocalization.translate("Hermes API (shared sessions)", fr))
        assertEquals(
            "Compatible OpenAI (directement vers un modèle)",
            OnyxLocalization.translate("OpenAI-compatible (direct to a model)", fr),
        )
        assertEquals("Serveur API Hermes", OnyxLocalization.translate("Hermes API server", fr))
    }

    @Test
    fun `le choix est une liste avec coche, plus le controle segmente a deux options`() {
        assertFalse(source.contains("OnyxSegmented("))
        assertTrue(source.contains("BackendTransports.forEachIndexed"))
        assertTrue(source.contains("OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)"))
        assertTrue(source.contains("header = \"How does Onyx reach it?\""))
    }

    @Test
    fun `la carte d'intro reprend les sous-textes de la référence`() {
        assertTrue(
            source.contains(
                "\"Works with Hermes, the open-source agent you run on your own machine, or any OpenAI-compatible model server.\"",
            ),
        )
        assertTrue(source.contains("\"Your conversations go only to the server you enter below.\""))
        assertTrue(source.contains("\"Your voice is recognized on this device, not in the cloud.\""))
        assertTrue(source.contains("Symbols.ServerRack"))
        assertTrue(source.contains("Symbols.LockShield"))
        assertTrue(source.contains("Symbols.Waveform"))
    }

    @Test
    fun `le collage garde le bouton Paste et le scan QR`() {
        // La capsule « Paste » de la référence, et pas un bouton Material.
        assertTrue(source.contains("SetupPasteButton"))
        assertTrue(source.contains("RoundedCornerShape(50)"))
        assertTrue(source.contains("text = \"Paste\""))
        // L'ancien collage et le scan restent : rien n'est cassé.
        assertTrue(source.contains("Paste a setup link"))
        assertTrue(source.contains("codeScannerSupported(context)"))
        assertTrue(source.contains("Scan a setup code"))
    }

    @Test
    fun `les champs changent avec le transport et nomment le serveur API`() {
        assertTrue(source.contains("header = \"Hermes API server\""))
        assertTrue(source.contains("header = \"Hermes Dashboard\""))
        assertTrue(source.contains("header = \"OpenAI-compatible endpoint\""))
        assertTrue(source.contains("https://your-gateway:8642"))
        assertTrue(source.contains("Transport.HERMES_SESSIONS ->"))
        assertTrue(source.contains("Transport.HERMES_SERVE ->"))
        assertTrue(source.contains("Transport.CHAT_COMPLETIONS ->"))
    }

    @Test
    fun `Skip est une sortie sans chevron, avec Done`() {
        assertTrue(source.contains("backLabel = \"Skip\""))
        assertTrue(source.contains("showBackChevron = false"))
        assertTrue(source.contains("action = \"Done\""))
    }

    @Test
    fun `le test de connexion suit le transport choisi`() {
        // « Tester la connexion » passe par la sonde qui connaît les transports. En mode
        // Dashboard, c'est `api/status` puis `auth/password-login` — pas `v1/models`, que le
        // Dashboard n'expose pas : ce test était mensonger en mode Dashboard.
        assertTrue(source.contains("ConnectionTester.current(draft)"))
        assertFalse(source.contains("ChatCompletionsClient"))
        // L'adresse testée est **celle du champ**, pour le transport retenu : une seule source.
        assertTrue(source.contains("value = transportAddress(config)"))
        assertTrue(source.contains("onChange { withTransportAddress(it, value) }"))
    }

    @Test
    fun `le titre de section est celui de la reference et le paragraphe passe par le dictionnaire`() {
        // Le titre n'est plus le verbe à l'infinitif « Connect ».
        assertFalse(source.contains("header = \"Connect\""))
        // C'est la question de la référence ; sa traduction est « Comment Onyx s'y connecte-t-il ? ».
        assertTrue(source.contains("header = \"How does Onyx reach it?\""))
        // Le paragraphe neuf du lot « tout en un » reste la clé anglaise : c'est le dictionnaire
        // qui le rend en français (aucune chaîne traduite en dur).
        assertTrue(
            source.contains(
                "One link sets up everything at once: server, key, model, voices, the Computer bridge and the language.",
            ),
        )
        // La liste des transports vit dans la même section : une seule question.
        assertTrue(source.contains("BackendTransports.forEach"))
    }

    @Test
    fun `le verdict du test de connexion se traduit`() {
        // Le verdict de la sonde est un message de l'app : les motifs français le lisent.
        assertEquals(
            "Le Dashboard Hermes 2026.10 est accessible, mais la connexion a échoué : non",
            OnyxLocalization.translate(
                "Hermes Dashboard 2026.10 reachable, but login failed: non",
                Locale.FRANCE,
            ),
        )
    }
}
