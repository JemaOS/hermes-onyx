package com.onyx.app.ui.settings

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux sections de la référence qui vivaient dans le premier écran des Réglages — `MetricsSettings` et
 * `ResetSettings` — et la page « Connection details » qu'elles encadrent. Tout ce que l'écran
 * promet doit être branché : la fenêtre de contexte s'écrit sur le serveur, et l'effacement
 * efface pour de vrai.
 */
class AbsentRoutesSettingsParityTest {

    private val screen = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
    private val details = File("src/main/java/com/onyx/app/ui/settings/ConnectionDetailsScreen.kt").readText()
    private val viewModel = File("src/main/java/com/onyx/app/ui/settings/SettingsViewModel.kt").readText()
    private val container = File("src/main/java/com/onyx/app/di/AppContainer.kt").readText()
    private val servers = File("src/main/java/com/onyx/app/data/repo/ServerRepository.kt").readText()
    private val settings = File("src/main/java/com/onyx/app/data/repo/SettingsRepository.kt").readText()
    private val conversations = File("src/main/java/com/onyx/app/data/repo/ConversationRepository.kt").readText()

    // MARK: - MetricsSettings

    @Test
    fun `la section Mesures porte le champ Context window de la référence`() {
        val metrics = screen.substringAfter("header = \"Metrics\"").substringBefore("onOpenTerminal")
        assertTrue("l'en-tête de la référence", screen.contains("header = \"Metrics\""))
        assertTrue("le libellé du champ", metrics.contains("label = \"Context window\""))
        assertTrue("l'invite de la référence", metrics.contains("placeholder = \"131072\""))
        assertTrue("un clavier numérique", metrics.contains("keyboardType = KeyboardType.Number"))
        assertTrue("le pied de page de la référence", metrics.contains("Used for the “ctx %” figure"))
    }

    @Test
    fun `la fenetre de contexte s ecrit sur le serveur, elle ne fait pas que s afficher`() {
        assertTrue(screen.contains("onSetContextWindow(server.id, digits.toIntOrNull() ?: 0)"))
        assertTrue(viewModel.contains("fun setContextWindow(id: String, value: Int)"))
        assertTrue(viewModel.contains("serversRepo.upsert(server.copy(contextWindow = value))"))
    }

    // MARK: - ResetSettings

    @Test
    fun `la ligne destructive et sa confirmation sont celles de la référence`() {
        assertTrue("la ligne d'effacement", screen.contains("text = \"Erase everything…\""))
        assertTrue("rouge, comme destructive", screen.contains("style = theme.body.copy(color = theme.error)"))
        assertTrue("le titre de la confirmation", screen.contains("title = \"Erase everything on this device?\""))
        assertTrue(
            "le message de la confirmation",
            screen.contains("\"Conversations, attachments, credentials and settings. This can't be undone.\""),
        )
        assertTrue("l'action destructrice", screen.contains("com.onyx.app.ui.design.AlertAction(\"Erase everything\", destructive = true)"))
        assertTrue("et son annulation", screen.contains("com.onyx.app.ui.design.AlertAction(\"Cancel\")"))
        assertTrue("le pied de page de la référence", screen.contains("Deletes every conversation and attachment on this device"))
    }

    @Test
    fun `l effacement efface vraiment, conversations, serveurs, cles et reglages`() {
        assertTrue("le conteneur porte l'effacement", container.contains("suspend fun eraseEverything()"))
        assertTrue("les conversations", container.contains("conversations.eraseAll()"))
        assertTrue("les brouillons", container.contains("drafts.clearAll()"))
        assertTrue("les visages des bots", container.contains("botFaces.clearAll()"))
        assertTrue("les serveurs et leurs cles", container.contains("servers.eraseAll()"))
        assertTrue("les reglages d'origine", container.contains("settings.reset()"))
        assertTrue(conversations.contains("suspend fun eraseAll()"))
        assertTrue(servers.contains("suspend fun eraseAll()"))
        assertTrue(servers.contains("secrets.clearAll()"))
        assertTrue(settings.contains("suspend fun reset()"))
        assertTrue(settings.contains("store.edit { it.clear() }"))
    }

    // MARK: - ConnectionDetailsView

    @Test
    fun `la page Détails de connexion reprend les sections de la référence`() {
        assertTrue(details.contains("OnyxPageHeader(title = \"Connection details\""))
        // `GatewayKeySettings`, `ServeLoginSettings`, `CloudflareSettings`, `CustomHeadersSettings`.
        assertTrue(details.contains("header = \"Hermes API\""))
        assertTrue(details.contains("header = \"Hermes Dashboard\""))
        assertTrue(details.contains("header = \"Cloudflare Access (optional)\""))
        assertTrue(details.contains("header = \"Custom headers (optional)\""))
        assertTrue("l'adresse de l'API", details.contains("placeholder = \"https://your-gateway:8642\""))
        assertTrue("la cle API", details.contains("label = \"API key\""))
        assertTrue("l'adresse du Dashboard", details.contains("placeholder = \"http://your-onyx:9119\""))
        assertTrue("l'etat de la connexion", details.contains("text = if (draft.hasServeCredentials) \"configured\" else \"not connected\""))
        assertTrue("le jeton Cloudflare", details.contains("label = \"Client secret\""))
        assertTrue("les en-tetes du proxy", details.contains("text = \"Add header\""))
    }

    @Test
    fun `la page ecrit ce qu on y saisit`() {
        assertTrue(details.contains("onSave(draft)"))
        assertTrue(details.contains("fun edit(transform: (ServerConfig) -> ServerConfig)"))
        assertTrue(viewModel.contains("fun saveConnection(server: ServerConfig)"))
        assertTrue(viewModel.contains("serversRepo.upsert(server)"))
    }

    @Test
    fun `la ligne des Reglages ouvre la page`() {
        assertTrue(screen.contains("label = \"Connection details\""))
        assertTrue(screen.contains("symbol = Symbols.KeyOutline"))
        assertTrue(screen.contains("onClick = openDetails"))
    }

    // MARK: - La langue

    @Test
    fun `les libelles neufs sont dits en francais`() {
        assertEquals("Détails de connexion", OnyxLocalization.translate("Connection details", Locale.FRENCH))
        assertEquals("Mesures", OnyxLocalization.translate("Metrics", Locale.FRENCH))
        assertEquals("Fenêtre de contexte", OnyxLocalization.translate("Context window", Locale.FRENCH))
        assertEquals("Tout effacer…", OnyxLocalization.translate("Erase everything…", Locale.FRENCH))
        assertEquals("Tout effacer sur cet appareil ?", OnyxLocalization.translate("Erase everything on this device?", Locale.FRENCH))
        assertEquals("Tout effacer", OnyxLocalization.translate("Erase everything", Locale.FRENCH))
        assertEquals("En-têtes personnalisés (facultatif)", OnyxLocalization.translate("Custom headers (optional)", Locale.FRENCH))
        assertEquals("Ajouter l’en-tête", OnyxLocalization.translate("Add header", Locale.FRENCH))
        // En anglais, la clé reste telle quelle.
        assertEquals("Metrics", OnyxLocalization.translate("Metrics", Locale.ENGLISH))
    }
}
