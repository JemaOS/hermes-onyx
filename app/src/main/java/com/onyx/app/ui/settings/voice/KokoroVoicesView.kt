package com.onyx.app.ui.settings.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.KokoroSpeechClient
import com.onyx.app.voice.SpeechAudioPlayer
import com.onyx.app.voice.groupKokoroVoices
import com.onyx.app.voice.kokoroVoiceDisplayName
import com.onyx.app.voice.normalizedKokoroBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Réglages → Voix → Kokoro → Voice.
 * La liste vient du serveur; les mélanges restent saisissables et chaque voix a son écoute.
 */
@Composable
fun KokoroVoicesView(
    baseUrl: String,
    currentVoice: String,
    onVoiceChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { KokoroSpeechClient() }
    val player = remember { SpeechAudioPlayer(context) }

    var voices by remember(baseUrl) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember(baseUrl) { mutableStateOf<String?>(null) }
    var loading by remember(baseUrl) { mutableStateOf(true) }
    var playing by remember { mutableStateOf<String?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        player.stop()
        playing = null
    }

    fun preview(voice: String) {
        if (playing == voice) {
            stopPreview()
            return
        }
        previewJob?.cancel()
        player.stop()
        playing = voice
        previewJob = scope.launch {
            try {
                val name = kokoroVoiceDisplayName(voice)
                val audio = client.speak(
                    text = "Hi, I'm $name. This is how Onyx would sound.",
                    baseUrl = baseUrl,
                    voice = voice,
                )
                if (playing != voice) return@launch
                player.play(
                    audio = audio,
                    onFinished = { if (playing == voice) playing = null },
                    onError = { if (playing == voice) playing = null },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (playing == voice) playing = null
            }
        }
    }

    LaunchedEffect(baseUrl) {
        stopPreview()
        loading = true
        error = null
        if (normalizedKokoroBase(baseUrl) == null) {
            error = OnyxLocalization["Set the server TTS address first."]
            loading = false
            return@LaunchedEffect
        }
        try {
            voices = client.voiceIds(baseUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val reason = (failure as? TransportError)?.userMessage()
                ?: failure.message
                ?: OnyxLocalization["Server unreachable"]
            error = OnyxLocalization.format("Couldn't reach Kokoro: %1\$s", reason)
        }
        loading = false
    }

    DisposableEffect(player) {
        onDispose {
            previewJob?.cancel()
            player.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Kokoro voices", onBack = onBack)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                header = "Current voice",
                footer = "Pick a voice below, or type a blend: voice names joined with + and optional weights in parentheses.",
            ) {
                OnyxRow {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (currentVoice.isEmpty()) {
                            BasicText(
                                text = "am_onyx(2)+bm_george(1)",
                                localize = false,
                                style = theme.body.copy(color = theme.tertiaryText, fontFamily = FontFamily.Monospace),
                            )
                        }
                        BasicTextField(
                            value = currentVoice,
                            onValueChange = onVoiceChange,
                            singleLine = true,
                            textStyle = theme.body.copy(color = theme.primaryText, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(theme.accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            error?.let { message ->
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.WifiExclamation, tint = theme.secondaryText, size = 19.dp)
                        BasicText(
                            text = message,
                            localize = false,
                            style = theme.body.copy(color = theme.secondaryText),
                        )
                    }
                }
            }
            if (error == null && loading) {
                OnyxFormSection {
                    OnyxRow {
                        VoicePreviewSpinner(size = 20.dp)
                        BasicText(
                            text = "Loading voices from Kokoro…",
                            style = theme.body.copy(color = theme.secondaryText),
                        )
                    }
                }
            }

            val groups = remember(voices) { groupKokoroVoices(voices) }
            groups.forEach { group ->
                val language = OnyxLocalization[group.language]
                val gender = OnyxLocalization[group.gender]
                val title = if (gender.isEmpty()) language else "$language · $gender"
                OnyxFormSection(header = title, localizeHeader = false) {
                    group.voices.forEachIndexed { index, voice ->
                        if (index > 0) OnyxRowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp)
                                    .onyxTappable { onVoiceChange(voice) }
                                    .semantics { selected = currentVoice == voice },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicText(
                                    text = kokoroVoiceDisplayName(voice),
                                    localize = false,
                                    style = theme.body.copy(color = theme.primaryText),
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (currentVoice == voice) {
                                    OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                                }
                            }
                            VoicePreviewButton(
                                playing = playing == voice,
                                label = if (playing == voice) "Stop preview" else "Preview ${kokoroVoiceDisplayName(voice)}",
                                onClick = { preview(voice) },
                            )
                        }
                    }
                }
            }
        }
    }
}
