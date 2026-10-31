package com.onyx.app

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le jeton du service vocal protégé (mandataire à jeton) : il se règle **à part** de l'adresse (une
 * adresse qui porte un secret finit dans les copies d'écran et les journaux), il se **masque** à
 * l'écran — copie seule, jamais un œil —, et il part dans l'en-tête `Authorization` des **deux**
 * appels de `KokoroSpeechClient` (`voiceIds` et `speak`), sans rien changer quand il est vide.
 */
class VoiceTokenSettingTest {
    private val repo = File("src/main/java/com/onyx/app/data/repo/SettingsRepository.kt").readText()
    private val viewModel = File("src/main/java/com/onyx/app/ui/settings/SettingsViewModel.kt").readText()
    private val screen = File("src/main/java/com/onyx/app/ui/settings/VoiceSettingsScreen.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val client = File("../core/src/main/java/com/onyx/app/voice/RemoteSpeech.kt").readText()

    @Test
    fun `le jeton est un reglage a part de l adresse`() {
        assertTrue(repo.contains("stringPreferencesKey(\"kokoroToken\")"))
        assertTrue(repo.contains("val DEFAULT_KOKORO_TOKEN: String = com.onyx.app.BuildConfig.VOICE_TOKEN"))
        assertTrue(repo.contains("suspend fun setKokoroToken(value: String)"))
        assertTrue(viewModel.contains("val kokoroToken: StateFlow<String> = settingsRepo.kokoroToken"))
        assertTrue(viewModel.contains("fun setKokoroToken(value: String)"))
    }

    @Test
    fun `l ecran masque le jeton et ne montre jamais un caractere`() {
        assertTrue(screen.contains("label = \"Voice token\""))
        assertTrue(screen.contains("secret = true,"))
        assertTrue(screen.contains("onValueChange = viewModel::setKokoroToken"))
        assertFalse("un secret ne se révèle jamais", screen.contains("Symbols.Eye"))
    }

    @Test
    fun `les deux appels de la voix envoient l en-tete, et rien sans jeton`() {
        assertTrue(client.contains("private val token: String = \"\","))
        assertEquals(
            2,
            Regex("header\\(\"Authorization\", \"Bearer \" \\+ token\\)").findAll(client).count(),
        )
        assertTrue("un jeton vide n'ajoute rien", client.contains("if (token.isNotBlank())"))
        // Le jeton suit la lecture distante et la liste des voix, jamais l'adresse.
        assertTrue(app.contains("kokoroToken = kokoroToken,"))
        assertTrue(app.contains("token = kokoroToken,"))
    }

    @Test
    fun `le libelle et sa note sont dits en francais`() {
        assertEquals("Jeton de la voix", OnyxLocalization.translate("Voice token", Locale.FRENCH))
        assertTrue(
            OnyxLocalization.translate(
                "Sent to the voice server as a bearer token. Leave it empty when the server needs none.",
                Locale.FRENCH,
            ).startsWith("Envoyé au service vocal"),
        )
    }
}
