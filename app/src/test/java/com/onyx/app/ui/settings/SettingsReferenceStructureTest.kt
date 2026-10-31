package com.onyx.app.ui.settings

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les Réglages réorganisés selon la structure de la référence : la **carte de la passerelle** en
 * tête (vignette teintée, adresse, état, bouton « Vérifier » qui teste pour de vrai), les sections
 * de la référence (Apparence, Chat, Voix, Notifications, Téléphone, Confidentialité, Compte,
 * À propos), les lignes séparées par un filet — et **rien de perdu** : chaque entrée existante
 * garde son point d'accès.
 */
class SettingsReferenceStructureTest {

    private fun appSource(path: String): String = File("src/main/java/com/onyx/app/$path").readText()
    private fun coreSource(path: String): String = File("../core/src/main/java/com/onyx/app/$path").readText()

    private val screen = appSource("ui/settings/SettingsScreen.kt")
    private val card = appSource("ui/settings/GatewayCard.kt")
    private val viewModel = appSource("ui/settings/SettingsViewModel.kt")
    private val repo = appSource("data/repo/SettingsRepository.kt")

    // MARK: - La carte de la passerelle, et son bouton « Vérifier »

    @Test
    fun `la carte de la passerelle suit le gabarit de la reference`() {
        // La **marque du lanceur** dans la vignette, l'adresse, le sous-titre d'état et le bouton à
        // droite. La vignette n'est plus « teintée » : c'est le logo de l'app, aux couleurs de
        // l'icône, identique en clair et en sombre — un logo ne se dérive pas du thème.
        assertTrue("la marque de la maison", card.contains("OnyxBrandMark("))
        assertFalse(
            "la vignette n'est plus teintée de l'accent",
            card.contains("theme.accent.copy(alpha = 0.16f)"),
        )
        assertTrue("l'adresse en sous-titre", card.contains("text = address"))
        assertTrue("l'état", card.contains("text = status"))
        assertTrue("le bouton", card.contains("VerifyButton(label = OnyxLocalization[\"Check\"]"))
        // Trois teintes d'état : réussi, échoué, en attente.
        assertTrue(card.contains("GatewayTone.OK -> theme.success"))
        assertTrue(card.contains("GatewayTone.BAD -> theme.error"))
        assertTrue(card.contains("GatewayTone.IDLE -> theme.secondaryText"))
    }

    @Test
    fun `la carte est en tete des Reglages, avant les sections`() {
        val card = screen.indexOf("GatewayCard(")
        val firstSection = screen.indexOf("OnyxFormSection(header = \"Appearance\"")
        assertTrue("la carte est posée", card > 0)
        assertTrue("la carte passe avant la première section", card < firstSection)
    }

    @Test
    fun `Verifier teste pour de vrai le serveur actif et dit ce qui a echoue`() {
        assertTrue(viewModel.contains("fun verifyActiveGateway()"))
        // Le serveur **actif**, pas le brouillon de l'éditeur.
        assertTrue(viewModel.contains("serversRepo.servers.value.firstOrNull { it.id == serversRepo.activeId.value }"))
        assertTrue("un vrai test de connexion", viewModel.contains("ChatCompletionsClient(server).testConnection()"))
        assertTrue("l'échec est gardé", viewModel.contains("ConnectionProbe(false, error.userMessage()"))
        assertTrue("l'état du test", viewModel.contains("private val _gatewayVerifying = MutableStateFlow(false)"))
        assertTrue("le résultat", viewModel.contains("val gatewayProbe: StateFlow<ConnectionProbe?>"))
        // Et le message d'échec distingue bien l'adresse introuvable, les identifiants refusés et le
        // serveur muet (les trois cas exigés) — la classification vit dans le transport.
        val transport = coreSource("data/net/TransportError.kt")
        assertTrue(transport.contains("Adresse introuvable"))
        assertTrue(transport.contains("Le serveur a refusé la clé API"))
        assertTrue(transport.contains("Serveur injoignable"))
        assertTrue(transport.contains("Le serveur n’a pas répondu à temps"))
    }

    // MARK: - L'inventaire est conservé : chaque entrée garde son accès

    @Test
    fun `chaque entree des Reglages garde son point d acces`() {
        // Toutes les familles de la référence, plus celles qu'Onyx a en plus.
        listOf(
            "Appearance" to "onOpenAppearance: () -> Unit,",
            "Chat" to "onOpenModels: () -> Unit,",
            "Voice" to "onOpenVoice: () -> Unit,",
            "Notifications" to "onOpenNotifications: (() -> Unit)? = null,",
            "Phone" to "onOpenPhoneTools: (() -> Unit)? = null,",
            "Privacy" to "onOpenLock: (() -> Unit)? = null,",
            "Account" to "onOpenGateway: (() -> Unit)? = null,",
            "About" to "onOpenAcknowledgements: (() -> Unit)? = null,",
        ).forEach { (section, param) ->
            assertTrue("l'en-tête $section", screen.contains("header = \"$section\""))
            assertTrue("l'accès $param est promis par un paramètre", screen.contains(param))
        }
        // Les entrées du compte (chaque AgentEntry) sont toutes encore là.
        listOf(
            "Capabilities", "Insights", "Tools", "Bots", "Profile", "Context files",
            "Memory", "Files", "Gateway", "Cron", "Kanban",
        ).forEach { assertTrue("l'entrée $it", screen.contains("AgentEntry(\"$it\"")) }
        // La ligne « Compétences » a été retirée : elle doublait « Capacités ».
        assertFalse("la ligne « Compétences » a quitté les Réglages", screen.contains("AgentEntry(\"Skills\""))
        // Celles qu'Onyx a en plus du fait de son portage restent atteignables.
        listOf(
            "AppIconSettings(embedded = true)", "VoiceOrbSettingsRows(", "ChatTextSizeRow(",
            "BlurbToggleRow(", "Connection details", "Context window", "Terminal",
            "Lock", "Set up another device", "What's New", "Support Onyx", "Erase everything…",
        ).forEach { assertTrue("$it doit rester", screen.contains(it)) }
    }

    // MARK: - Les trois réglages ajoutés, branchés sur un vrai état

    @Test
    fun `noir pur et halo sont des reglages persistants`() {
        // Dépôt : flux, écriture, clés.
        assertTrue(repo.contains("val pureBlack: StateFlow<Boolean>"))
        assertTrue(repo.contains("val halo: StateFlow<Boolean>"))
        assertTrue(repo.contains("suspend fun setPureBlack(value: Boolean)"))
        assertTrue(repo.contains("suspend fun setHalo(value: Boolean)"))
        assertTrue(repo.contains("booleanPreferencesKey(\"pureBlack\")"))
        assertTrue(repo.contains("booleanPreferencesKey(\"halo\")"))
        // Relus au démarrage et remis par `reset()` (qui rejoue `load()`).
        assertTrue(repo.contains("_pureBlack.value = preferences[Keys.PURE_BLACK] ?: false"))
        assertTrue(repo.contains("_halo.value = preferences[Keys.HALO] ?: true"))
        // ViewModel : flux et écritures.
        assertTrue(viewModel.contains("val pureBlack: StateFlow<Boolean> = settingsRepo.pureBlack"))
        assertTrue(viewModel.contains("val halo: StateFlow<Boolean> = settingsRepo.halo"))
        assertTrue(viewModel.contains("fun setPureBlack(value: Boolean)"))
        assertTrue(viewModel.contains("fun setHalo(value: Boolean)"))
    }

    @Test
    fun `les deux interrupteurs vivent dans la carte Apparence, avec leur description`() {
        listOf(
            "title = \"Pure black\"",
            "True black backgrounds in dark mode, easier on OLED screens.",
            "title = \"Halo\"",
            "Soft colored light behind the top of each screen and under your messages and buttons. Off, everything is flat.",
            "checked = pureBlack",
            "onCheckedChange = onPureBlack",
            "checked = halo",
            "onCheckedChange = onHalo",
        ).forEach { assertTrue("$it doit être dans la carte Apparence", screen.contains(it)) }
    }

    @Test
    fun `le halo et la couleur de fond sont portes par le theme`() {
        val resolved = coreSource("ui/theme/ResolvedTheme.kt")
        assertTrue(resolved.contains("val pureBlack: Boolean = false,"))
        assertTrue(resolved.contains("val halo: Boolean = true,"))
        assertTrue(resolved.contains("val oledBlack: Boolean get() = pureBlack && dark"))
        assertTrue(resolved.contains("pureBlack: Boolean = false,") && resolved.contains("halo: Boolean = true,"))
        val theme = coreSource("ui/theme/Theme.kt")
        assertTrue(theme.contains("pureBlack: Boolean = false,"))
        assertTrue(theme.contains("halo: Boolean = true,"))
        assertTrue(theme.contains("ResolvedTheme.resolve(theme, dark, fonts, colors, pureBlack, halo)"))
        // Le halo est un dégradé d'accent, et « éteint » ne peint rien.
        val halo = coreSource("ui/design/Halo.kt")
        assertTrue(halo.contains("if (alpha <= 0f) return emptyList()"))
        assertTrue(halo.contains("if (stops.isEmpty()) return"))
    }

    // MARK: - Les contrôles de la carte Apparence

    @Test
    fun `le theme est segmente avec ses icones, comme la reference`() {
        val form = coreSource("ui/design/OnyxForm.kt")
        assertTrue("le segmenté accepte des icônes", form.contains("icons: List<Int?> = emptyList()"))
        assertTrue(
            "le contrôle du thème porte le téléphone, le soleil et la lune",
            screen.contains("AppearanceMode.SYSTEM -> Symbols.Phone") &&
                screen.contains("AppearanceMode.LIGHT -> Symbols.SunMax") &&
                screen.contains("AppearanceMode.DARK -> Symbols.MoonFill"),
        )
    }

    @Test
    fun `l accent porte sa description et ses six pastilles, la retenue cerclee`() {
        // La description de la référence.
        assertTrue(screen.contains("text = \"Buttons, links and your own messages.\""))
        assertTrue(screen.contains("AccentSwatchRow("))
        val rows = appSource("ui/settings/AppearanceRows.kt")
        // Six accents, et la sélection cerclée de sa propre couleur.
        assertEquals(6, AccentPreset.all().size)
        assertTrue(rows.contains("enum class AccentPreset("))
        assertTrue(rows.contains("width = if (selected) 2.dp else 0.dp"))
        assertTrue(rows.contains("color = if (selected) color"))
    }

    // MARK: - L'habillage maison, et le français

    @Test
    fun `aucun composant Material dans les fichiers touches`() {
        listOf(
            "ui/settings/GatewayCard.kt",
            "ui/settings/AppearanceRows.kt",
            "ui/settings/SettingsScreen.kt",
        ).forEach { path ->
            val source = appSource(path)
            assertFalse("$path ne doit pas importer Material", source.contains("androidx.compose.material"))
        }
        val halo = coreSource("ui/design/Halo.kt")
        assertFalse("le halo est dessiné à la main", halo.contains("androidx.compose.material"))
    }

    @Test
    fun `les libelles neufs sont dits en francais`() {
        assertEquals("Passerelle", OnyxLocalization.translate("Connection", Locale.FRENCH))
        assertEquals("Écriture", OnyxLocalization.translate("Writing", Locale.FRENCH))
        assertEquals("À propos", OnyxLocalization.translate("About", Locale.FRENCH))
        assertEquals("Vérifier", OnyxLocalization.translate("Check", Locale.FRENCH))
        assertEquals("Noir pur", OnyxLocalization.translate("Pure black", Locale.FRENCH))
        assertEquals("Halo", OnyxLocalization.translate("Halo", Locale.FRENCH))
        assertEquals(
            "Des fonds noirs purs en mode sombre, plus doux pour les écrans OLED.",
            OnyxLocalization.translate("True black backgrounds in dark mode, easier on OLED screens.", Locale.FRENCH),
        )
        // La description du halo est celle de la source, **mot pour mot** — c'est ce qui la traduit.
        assertEquals(
            "Une lumière colorée douce derrière le haut de chaque écran, et sous vos messages et boutons. Éteint, tout est plat.",
            OnyxLocalization.translate(
                "Soft colored light behind the top of each screen and under your messages and buttons. Off, everything is flat.",
                Locale.FRENCH,
            ),
        )
        assertEquals(
            "Les boutons, les liens et vos propres messages.",
            OnyxLocalization.translate("Buttons, links and your own messages.", Locale.FRENCH),
        )
        // En anglais, la clé reste telle quelle.
        assertEquals("Pure black", OnyxLocalization.translate("Pure black", Locale.ENGLISH))
        assertEquals("Check", OnyxLocalization.translate("Check", Locale.ENGLISH))
    }
}
