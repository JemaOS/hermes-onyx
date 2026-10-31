package com.onyx.app.ui.settings.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.AndroidDeviceVoice
import com.onyx.app.voice.DeviceVoice
import com.onyx.app.voice.HermesSpeechClient
import com.onyx.app.voice.SpeechAudioPlayer
import com.onyx.app.voice.VoiceTrialOutcome
import com.onyx.app.voice.VoiceTrialValue
import com.onyx.app.voice.shouldFallBackToDevice
import com.onyx.app.voice.voiceServerDetail
import com.onyx.app.voice.voiceTrialOnFailure
import com.onyx.app.voice.voiceTrialShortLabel
import com.onyx.app.voice.voiceTrialValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val HERMES_TEST_SENTENCE = "This is your Hermes, reading replies out with its own voice."

/**
 * Les lignes Hermes de VoiceSettings : disponibilité du Dashboard, test, arrêt et provider.
 *
 * Le test ne met **jamais** l'erreur du serveur dans la rangée : celle-ci garde une valeur courte
 * (« En cours… », puis ✓ ou « Échec ») qui ne dépasse pas une ligne. Le détail part au pied de
 * section, remonté par [onOutcomeUpdate] à l'écran qui le rend sous la carte, comme un pied référence.
 *
 * Si le serveur ne peut pas parler (HTTP 400/403/5xx, délai dépassé, injoignable…), la phrase est
 * prononcée par la voix de l'appareil ; si ce moteur est absent, le pied le dit au lieu de se taire.
 */
@Composable
fun HermesVoiceRows(
    server: ServerConfig,
    speechLocale: String,
    modifier: Modifier = Modifier,
    onOutcomeUpdate: (VoiceTrialOutcome?) -> Unit = {},
) {
    val theme = LocalOnyxTheme.current
    val client = remember(server.configSignature) { HermesSpeechClient(server) }
    if (!client.isAvailable) {
        OnyxRow(modifier = modifier) {
            BasicText(
                text = "Needs the Hermes Dashboard connection: its address and login, under Connection. Until then the on-device voice reads replies.",
                style = theme.footnote.copy(color = theme.secondaryText),
            )
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { SpeechAudioPlayer(context) }
    val device: DeviceVoice = remember { AndroidDeviceVoice(context) }

    var outcome by remember(server.configSignature) { mutableStateOf<VoiceTrialOutcome?>(null) }
    var working by remember(server.configSignature) { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun publish(value: VoiceTrialOutcome?) {
        outcome = value
        onOutcomeUpdate(value)
    }

    fun release(reset: Boolean) {
        job?.cancel()
        job = null
        player.stop()
        device.stop()
        if (reset) {
            working = false
            publish(null)
        }
    }

    /** Le serveur a échoué : l'appareil parle s'il le peut, sinon l'écran l'annonce (pied rouge). */
    fun fallBackToDevice(failure: Throwable) {
        val decided = voiceTrialOnFailure(failure, device.isAvailable)
        if (decided is VoiceTrialOutcome.Device) {
            device.speak(OnyxLocalization[HERMES_TEST_SENTENCE], speechLocale) { spoken ->
                working = false
                publish(if (spoken) decided else VoiceTrialOutcome.Silent(voiceServerDetail(failure)))
            }
        } else {
            working = false
            publish(decided)
        }
    }

    fun test() {
        job?.cancel()
        player.stop()
        device.stop()
        working = true
        publish(null)
        job = scope.launch {
            try {
                val said = client.speak(OnyxLocalization[HERMES_TEST_SENTENCE])
                if (!working) return@launch
                player.play(
                    audio = said,
                    onFinished = { /* Le ✓ et le provider restent affichés, comme sur la référence. */ },
                    onError = { failure -> fallBackToDevice(failure) },
                )
                working = false
                publish(VoiceTrialOutcome.Server(said.provider))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (!working) return@launch
                if (shouldFallBackToDevice(failure)) fallBackToDevice(failure) else working = false
            }
        }
    }

    DisposableEffect(player, server.configSignature) {
        onDispose {
            job?.cancel()
            player.stop()
            device.stop()
            device.release()
        }
    }

    val value = if (working) VoiceTrialValue.WORKING else voiceTrialValue(outcome)

    OnyxRow(
        modifier = modifier,
        onClick = { if (working) release(reset = true) else test() },
    ) {
        BasicText(
            text = if (working) "Stop" else "Say a test sentence",
            style = theme.body.copy(color = theme.primaryText),
            modifier = Modifier.weight(1f),
        )
        when (value) {
            VoiceTrialValue.NONE -> Unit
            VoiceTrialValue.WORKING -> Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoicePreviewSpinner()
                BasicText(
                    text = voiceTrialShortLabel(VoiceTrialValue.WORKING),
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 1,
                )
            }
            VoiceTrialValue.SPOKEN, VoiceTrialValue.FAILED -> BasicText(
                text = voiceTrialShortLabel(value),
                localize = false,
                style = theme.body.copy(
                    color = if (value == VoiceTrialValue.FAILED) theme.error else theme.success,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}
