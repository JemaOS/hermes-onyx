package com.onyx.app.ui.voice

import com.onyx.app.data.model.serve.LogLevel
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.settings.LogFilter
import com.onyx.app.ui.settings.voiceLogLevel
import com.onyx.app.ui.settings.voiceLogLines
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le lot « fin de session prématurée » : le réglage du hors ligne, et les Journaux **sans
 * identifiant de Dashboard**.
 *
 * Deux défauts, deux preuves de câblage (le rendu réel se juge sur la capture) :
 *  - le hors ligne n'est plus imposé : il se **choisit** dans Réglages → Voix, et son libellé est
 *    traduit — sinon la ligne qui répare le code 13 serait invisible pour un utilisateur français ;
 *  - la ligne **Journaux** des Réglages n'est **jamais** fermée : le journal vocal vit sur
 *    l'appareil, donc quelqu'un dont le micro ne marche pas doit pouvoir lire ce qui casse sans
 *    session de Dashboard. Avant, il n'y avait que Réglages → Agent → Passerelle, dont la ligne est
 *    grisée sans identifiant.
 */
class VoiceOfflineAndLogsAccessTest {

    private val settings = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
    private val voiceSettings = File("src/main/java/com/onyx/app/ui/settings/VoiceSettingsScreen.kt").readText()
    private val logs = File("src/main/java/com/onyx/app/ui/settings/LogsViewModel.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    /** Le dictionnaire français, tel qu'il est écrit dans le cœur : les paires `"clé" to "valeur"`. */
    private val frenchDictionary: Map<String, String> by lazy {
        val text = File("../core/src/main/java/com/onyx/app/localization/OnyxFrench.kt").readText()
        Regex("\"((?:[^\"\\\\]|\\\\.)*)\" to \"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    // MARK: - Les Journaux, sans identifiant de Dashboard

    @Test
    fun `la ligne Journaux des Reglages n est jamais fermee`() {
        // Elle existe, et elle n'est **pas** du type `AgentEntry` (dont la ligne est grisée par
        // `dashboard = canAdminister`) : un `OnyxValueRow` sans condition de transport.
        assertTrue(settings.contains("onOpenLogs: (() -> Unit)? = null,"))
        assertTrue(settings.contains("label = \"Logs\","))
        assertTrue(
            "la ligne est posée dans la section Voix, sans condition de configuration",
            settings.contains("onOpenLogs?.let {") &&
                settings.indexOf("onOpenLogs?.let {") > settings.indexOf("OnyxFormSection(header = \"Voice\")"),
        )
        // Et l'écran des Réglages la reçoit de l'hôte, sans passer par la Passerelle.
        assertTrue(app.contains("onOpenLogs = { navTo(Route.LOGS.name) },"))
        // La Passerelle, elle, garde son verrou : c'est de l'administration, pas de la lecture.
        assertTrue(settings.contains("AgentEntry(\"Gateway\", Symbols.Gearshape, dashboard, it)"))
    }

    @Test
    fun `sans Dashboard joignable, les Journaux s ouvrent sur le journal vocal`() {
        assertTrue(logs.contains("private val localOnly: Boolean = false,"))
        assertTrue(
            "le journal de l'appareil est celui qui s'ouvre",
            logs.contains("LogsUiState(file = if (localOnly) LogFile.VOICE else LogFile.AGENT)"),
        )
        assertTrue(app.contains("localOnly = !state.server.canReachGateway,"))
        // Et le retour va **d'où l'on vient** (la Passerelle quand on y était, les Réglages sinon),
        // au lieu de renvoyer toujours à un écran verrouillé.
        assertTrue(app.contains("onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },"))
    }

    @Test
    fun `la bascule hors ligne vers en ligne se lit dans le journal vocal, en orange`() {
        // La ligne que le propriétaire doit nous transmettre : elle est visible sans filtre, et
        // colorée en avertissement (ce n'est pas une erreur : l'écoute continue en ligne).
        val line = "10:41:07  voice: bascule hors ligne → en ligne (code=13 language-unavailable) : " +
            "le moteur hors ligne ne sert pas la langue=fr-FR, l'essai suivant se fait en ligne"
        assertEquals(LogLevel.WARNING, voiceLogLevel(line))
        val all = voiceLogLines(listOf(line), LogFilter.EVERYTHING)
        assertEquals(1, all.size)
        assertEquals(1, voiceLogLines(listOf(line), LogFilter.WARNINGS).size)
        assertEquals(0, voiceLogLines(listOf(line), LogFilter.ERRORS_ONLY).size)
        // Et la recherche locale la trouve.
        assertEquals(1, voiceLogLines(listOf(line), LogFilter.EVERYTHING, search = "bascule").size)
    }

    @Test
    fun `le mode du moteur se lit au demarrage, et il est en ligne par defaut`() {
        // La première ligne du journal dit lequel des deux modes tourne : avec le hors ligne non
        // imposé, c'est « en ligne » qui doit apparaître.
        assertTrue(voiceLogLevel("10:41:00  voice: moteur démarré (langue=fr-FR, en ligne)") == LogLevel.PLAIN)
        assertTrue(voiceLogLevel("10:41:00  voice: moteur démarré (langue=fr-FR, hors ligne)") == LogLevel.PLAIN)
    }

    // MARK: - Le réglage, en français

    @Test
    fun `reconnaitre hors ligne est traduit en francais`() {
        assertEquals("Reconnaître hors ligne", OnyxLocalization.translate("Recognise offline", Locale.FRENCH))
        assertEquals("Journaux", OnyxLocalization.translate("Logs", Locale.FRENCH))
        // Le dictionnaire porte bien la clé (et non un mot voisin qui la ferait passer en anglais).
        assertNotNull("« Recognise offline » manque au dictionnaire français", frenchDictionary["Recognise offline"])
        assertEquals("Reconnaître hors ligne", frenchDictionary["Recognise offline"])
    }

    @Test
    fun `le reglage est celui de la voix, et pas un interrupteur mort`() {
        assertTrue(voiceSettings.contains("label = \"Recognise offline\","))
        assertTrue(voiceSettings.contains("onCheckedChange = viewModel::setOfflineRecognition,"))
        // Le brouillon du réglage va jusqu'au moteur de la session.
        assertTrue(app.contains("val offlineRecognition by settingsRepo.offlineRecognition.collectAsStateWithLifecycle()"))
        assertTrue(app.contains("voice.offlineRecognition = offlineRecognition"))
        assertFalse(
            "le hors ligne n'est plus imposé au moteur",
            File("../core/src/main/java/com/onyx/app/voice/SpeechEngine.kt").readText().contains("preferOffline = true"),
        )
    }
}
