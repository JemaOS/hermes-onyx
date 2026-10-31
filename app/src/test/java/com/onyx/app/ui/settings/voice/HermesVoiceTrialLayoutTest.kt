package com.onyx.app.ui.settings.voice

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La rangée « Dire une phrase de test » garde une valeur courte ; le détail du serveur vit dans un
 * pied de section sous la carte, et un serveur muet fait parler la voix de l'appareil.
 */
class HermesVoiceTrialLayoutTest {

    private val rows = File("src/main/java/com/onyx/app/ui/settings/voice/HermesVoiceRows.kt").readText()
    private val screen = File("src/main/java/com/onyx/app/ui/settings/VoiceSettingsScreen.kt").readText()

    @Test
    fun `un echec du serveur fait parler la voix de l appareil`() {
        assertTrue(rows.contains("AndroidDeviceVoice(context)"))
        assertTrue(rows.contains("val device: DeviceVoice = remember { AndroidDeviceVoice(context) }"))
        assertTrue(rows.contains("voiceTrialOnFailure(failure, device.isAvailable)"))
        assertTrue(rows.contains("device.speak(OnyxLocalization[HERMES_TEST_SENTENCE], speechLocale)"))
        assertTrue(rows.contains("shouldFallBackToDevice(failure)"))
        assertTrue(rows.contains("VoiceTrialOutcome.Silent(voiceServerDetail(failure))"))
    }

    @Test
    fun `la rangee ne montre qu une valeur courte, jamais l erreur brute`() {
        // Le défaut constaté : le message HTTP dans la colonne de valeur, sur plusieurs lignes.
        assertFalse(rows.contains("maxLines = 3"))
        assertFalse(rows.contains("HermesTrial.Failed"))
        assertFalse(rows.contains("text = state.reason"))
        assertFalse(rows.contains("textAlign = TextAlign.End),\n                maxLines = 3"))

        assertTrue(rows.contains("val value = if (working) VoiceTrialValue.WORKING else voiceTrialValue(outcome)"))
        assertTrue(rows.contains("text = voiceTrialShortLabel(value)"))
        assertTrue(rows.contains("text = voiceTrialShortLabel(VoiceTrialValue.WORKING)"))
        assertTrue(rows.contains("maxLines = 1"))
        assertTrue(rows.contains("VoiceTrialValue.SPOKEN, VoiceTrialValue.FAILED -> BasicText("))
    }

    @Test
    fun `le detail part au pied de section sous la carte`() {
        assertTrue(screen.contains("onOutcomeUpdate = { hermesTrial = it }"))
        assertTrue(screen.contains("speechLocale = speechLocale"))
        assertTrue(screen.contains("voiceTrialFootnote(hermesTrial)?.let { detail ->"))
        assertTrue(screen.contains("voiceTrialFootnoteIsError(hermesTrial)"))
        assertTrue(screen.contains("Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp)"))
        // Le pied suit la carte du test, dans l'écran qui la dessine.
        assertTrue(screen.indexOf("OnyxFormSection(header = \"Voice\"") < screen.indexOf("voiceTrialFootnote(hermesTrial)"))
    }

    @Test
    fun `les libelles passent par OnyxLocalization et rien par Material`() {
        assertTrue(rows.contains("OnyxLocalization[HERMES_TEST_SENTENCE]"))
        assertTrue(rows.contains("OnyxLocalization[HERMES_TEST_SENTENCE], speechLocale"))
        assertFalse(rows.contains("androidx.compose.material"))
        assertFalse(screen.contains("androidx.compose.material"))
    }
}
