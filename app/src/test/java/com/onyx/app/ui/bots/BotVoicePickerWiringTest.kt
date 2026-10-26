package com.onyx.app.ui.bots

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.voice.BotVoice
import com.onyx.app.voice.BotVoices
import com.onyx.app.voice.SpokenVoice
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La voix d'un bot (`BotVoicePicker`): l'entrée « Voice… » du menu ancré ouvre enfin la feuille
 * (`onVoice` n'est plus nul), le choix est **gardé** puis relu après un redémarrage, la voix
 * choisie est celle dont le bot lit ses répliques, et sans choix rien ne change.
 */
class BotVoicePickerWiringTest {
    private val root = File("src/main/java/com/onyx/app/ui")
    private val botsScreen = File(root, "bots/BotsScreen.kt").readText()
    private val conversations = File(root, "conversations/ConversationsScreen.kt").readText()
    private val menus = File(root, "bots/BotMenus.kt").readText()
    private val editor = File(root, "bots/BotEditorScreen.kt").readText()
    private val picker = File(root, "bots/BotVoicePickerScreen.kt").readText()
    private val app = File(root, "OnyxApp.kt").readText()

    private val french = listOf(
        SpokenVoice("fr-fr-x-frd-local", "fr-FR", quality = 1),
        SpokenVoice("fr-fr-x-frf-local", "fr-FR", quality = 1),
    )

    // MARK: - L'entrée du menu est branchée

    @Test
    fun `les deux tuiles ouvrent la feuille de voix`() {
        assertTrue("le menu porte l'entrée", menus.contains("BotMenuAction.Voice -> onVoice"))
        assertTrue(
            "la tuile des Réglages la branche",
            botsScreen.contains("onVoice = onVoice?.let { open -> { open(bot) } }"),
        )
        assertTrue(
            "celle de la liste aussi",
            conversations.contains("onVoice = onBotVoice?.let { open -> { open(bot) } }"),
        )
        assertFalse("plus d'entrée muette", botsScreen.contains("onVoice = null"))
        assertFalse("ni dans la liste", conversations.contains("onVoice = null"))

        // Les deux écrans remontent l'ouverture, et la route mène à la feuille.
        assertTrue(app.contains("onVoice = { bot ->"))
        assertTrue(app.contains("onBotVoice = { bot ->"))
        assertTrue(app.contains("navTo(Route.BOT_VOICE.name)"))
        assertTrue("la feuille existe et est montée", app.contains("BotVoicePickerScreen("))
        assertTrue("le profil du bot la suit", app.contains("botVoiceProfile = bot.name"))
    }

    @Test
    fun `l editeur d un bot ouvre la meme feuille et dit sa voix`() {
        assertTrue(
            "« Voice » de l'éditeur mène à la feuille, plus aux réglages vocaux",
            app.contains("botVoiceProfile = bot?.name\n                        navTo(Route.BOT_VOICE.name)"),
        )
        assertTrue("la rangée montre le choix", editor.contains("text = voiceSummary"))
        assertTrue("et sa valeur vient du réglage gardé", app.contains("BotVoicePicker.summary(botVoicesHere[state.server.id]?.get(bot?.name))"))
    }

    // MARK: - Le choix est gardé, puis relu

    @Test
    fun `le choix d une voix est garde puis relu apres un redemarrage`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.load()
        // Rien de réglé: le bot reçoit une voix de son cru, comme avant.
        assertTrue(settings.botVoicesHere("srv").isEmpty())

        settings.setBotVoice("srv", "quill", BotVoice(kokoro = "am_adam", speed = 1.2f))

        // Un redémarrage: le même magasin, une instance neuve.
        val restarted = SettingsRepository(store)
        restarted.load()
        assertEquals(
            BotVoice(kokoro = "am_adam", speed = 1.2f),
            restarted.botVoicesHere("srv")["quill"],
        )

        // Une voix de l'appareil, et par serveur: l'autre serveur ne la voit pas.
        restarted.setBotVoice("srv", "scout", BotVoice(onDevice = "fr-fr-x-frf-local"))
        val again = SettingsRepository(store)
        again.load()
        assertEquals("fr-fr-x-frf-local", again.botVoicesHere("srv")["scout"]?.onDevice)
        assertTrue("rien n'a fui sur un autre serveur", again.botVoicesHere("autre").isEmpty())

        // « Automatique » efface le réglage; une fiche vide n'est jamais gardée.
        again.setBotVoice("srv", "quill", null)
        again.setBotVoice("srv", "scout", BotVoice())
        val cleared = SettingsRepository(store)
        cleared.load()
        assertTrue("plus rien de réglé", cleared.botVoicesHere("srv").isEmpty())
    }

    // MARK: - La voix choisie est celle dont le bot lit

    @Test
    fun `la voix reglee est celle que la lecture donne au bot`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.load()
        val server = "srv"
        val names = listOf("quill", "scout")

        // Sans choix: la répartition d'avant (`BotVoices`), inchangée.
        val before = BotVoices.onDevice("quill", settings.botVoicesHere(server), "fr", "fr-fr-x-frd-local", french, names)
        assertEquals(
            "rien réglé, rien qui change",
            BotVoices.onDevice("quill", emptyMap(), "fr", "fr-fr-x-frd-local", french, names),
            before,
        )

        // La feuille écrit le choix (`setBotVoice`), et c'est cette voix que la lecture donne.
        settings.setBotVoice(server, "quill", BotVoice(onDevice = "fr-fr-x-frf-local"))
        assertEquals(
            "le bot lit de la voix choisie",
            "fr-fr-x-frf-local",
            BotVoices.onDevice("quill", settings.botVoicesHere(server), "fr", "fr-fr-x-frd-local", french, names),
        )
        // « Identique à l'agent principal »: la voix d'Onyx, comme le `sameAsMain` du modèle.
        settings.setBotVoice(server, "quill", BotVoice(sameAsMain = true))
        assertNull(
            BotVoices.onDevice("quill", settings.botVoicesHere(server), "fr", "fr-fr-x-frd-local", french, names),
        )
        // Sa vitesse suit, dans la lecture comme dans le résumé.
        settings.setBotVoice(server, "quill", BotVoice(onDevice = "fr-fr-x-frf-local", speed = 1.3f))
        assertEquals(
            1.3f,
            BotVoices.speed("quill", settings.botVoicesHere(server), 1.0f),
            0.0001f,
        )

        // Le mécanisme de lecture est celui-là: la feuille n'invente rien.
        assertTrue("la feuille écrit le réglage", app.contains("settingsRepo.setBotVoice(state.server.id, bot.name, voice)"))
        assertTrue("le choix part au moteur", app.contains("botVoices = settingsRepo.botVoicesHere(state.server.id)"))
        val speechOutput = File("../core/src/main/java/com/onyx/app/voice/SpeechOutput.kt").readText()
        assertTrue(
            "et la réplique d'un bot se résout par son profil, pas par une hauteur inventée",
            speechOutput.contains("speaker: String? = null") &&
                speechOutput.contains("forProfile = speaker,") &&
                speechOutput.contains("picked = botVoices,"),
        )
    }

    // MARK: - Ce que la feuille offre et dit

    @Test
    fun `la feuille offre automatique, l agent principal, et chaque voix avec son ecoute`() {
        assertTrue("« Automatique »", picker.contains("title = \"Automatic\""))
        assertTrue("« Identique à <nom> »", picker.contains("title = \"Same as \$mainName\""))
        assertTrue("« Écouter » un échantillon", picker.contains("label = if (playing) \"Stop preview\" else \"Hear it\""))
        assertTrue("la coche de la voix courante", picker.contains("current?.kokoro == choice.id || current?.onDevice == choice.id"))
        assertTrue("sa vitesse", picker.contains("checked = current?.speed != null"))
        assertTrue("rien n'est gardé par la feuille : elle relit", picker.contains("val current = picked[botName]"))
        assertTrue("la mise en page suit OnyxForm", picker.contains("OnyxFormSection(") && picker.contains("OnyxPageHeader("))
    }

    // MARK: - La langue

    @Test
    fun `les libelles de la feuille sont dits en francais`() {
        assertEquals("Écouter", OnyxLocalization.translate("Hear it", Locale.FRENCH))
        assertEquals("Vitesse", OnyxLocalization.translate("Speed", Locale.FRENCH))
        assertEquals("Sa propre vitesse", OnyxLocalization.translate("Its own speed", Locale.FRENCH))
        assertEquals("Voix de ce téléphone", OnyxLocalization.translate("Voices on this phone", Locale.FRENCH))
        assertEquals("Voix Kokoro", OnyxLocalization.translate("Kokoro voices", Locale.FRENCH))
        assertEquals("Automatique", OnyxLocalization.translate("Automatic", Locale.FRENCH))
        assertEquals("Voix de Kilo", OnyxLocalization.translate("Kilo's Voice", Locale.FRENCH))
        assertEquals("Identique à Hermes", OnyxLocalization.translate("Same as Hermes", Locale.FRENCH))
        assertEquals("la voix de Hermes", OnyxLocalization.translate("Hermes's voice", Locale.FRENCH))
        assertEquals("Améliorée · fr-FR", OnyxLocalization.translate("Enhanced · fr-FR", Locale.FRENCH))
        assertEquals("Par défaut · fr-FR", OnyxLocalization.translate("Default · fr-FR", Locale.FRENCH))
        assertEquals(
            "la voix de Hermes, pour l’instant : le serveur n’a pas d’autre voix dans sa langue",
            OnyxLocalization.translate("Hermes's voice, for now: the server has no other voice in its language", Locale.FRENCH),
        )
        assertEquals(
            "Kilo lit à la vitesse de Hermes, 1.00× (Réglages → Voix → Voice speed).",
            OnyxLocalization.format(
                "%1\$s reads at %2\$s's speed, %3\$s (Settings → Voice → Voice speed).",
                "Kilo",
                "Hermes",
                "1.00×",
                locale = Locale.FRENCH,
            ),
        )
        // En anglais, les libellés restent ceux du modèle.
        assertEquals("Hear it", OnyxLocalization.translate("Hear it", Locale.ENGLISH))
        assertEquals("Kilo's Voice", OnyxLocalization.translate("Kilo's Voice", Locale.ENGLISH))
    }

    private class FakePreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
