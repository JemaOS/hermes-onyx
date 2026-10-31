package com.onyx.app.ui.settings

import com.onyx.app.localization.OnyxLanguage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.ui.design.OnyxFieldRow
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSegmented
import com.onyx.app.ui.design.OnyxSlider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.OnyxValueRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.settings.voice.HermesVoiceRows
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.ReplyVoice
import com.onyx.app.voice.TalkOver
import com.onyx.app.voice.VoiceTrialOutcome
import com.onyx.app.voice.voiceTrialFootnote
import com.onyx.app.voice.voiceTrialFootnoteIsError

/** Réglages → Voix, avec les trois synthèses du modèle la référence et leurs réglages propres. */
@Composable
fun VoiceSettingsScreen(
    viewModel: SettingsViewModel,
    server: ServerConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenListeningLanguage: () -> Unit = {},
    onOpenReplyLanguage: () -> Unit = {},
    onOpenStopPhrases: () -> Unit = {},
    onOpenSpokenPrefixes: () -> Unit = {},
    onOpenKokoroVoices: () -> Unit = {},
) {
    val theme = LocalOnyxTheme.current
    val speechLocale by viewModel.speechLocale.collectAsStateWithLifecycle()
    val replyLanguage by viewModel.replyLanguage.collectAsStateWithLifecycle()
    val speed by viewModel.voiceSpeed.collectAsStateWithLifecycle()
    val replyVoice by viewModel.replyVoice.collectAsStateWithLifecycle()
    val kokoroUrl by viewModel.kokoroUrl.collectAsStateWithLifecycle()
    val kokoroToken by viewModel.kokoroToken.collectAsStateWithLifecycle()
    val kokoroVoice by viewModel.kokoroVoice.collectAsStateWithLifecycle()
    val handsFree by viewModel.handsFreeByDefault.collectAsStateWithLifecycle()
    val offlineRecognition by viewModel.offlineRecognition.collectAsStateWithLifecycle()
    val talkOver by viewModel.talkOver.collectAsStateWithLifecycle()
    val earpieceAtEar by viewModel.earpieceAtEar.collectAsStateWithLifecycle()

    // Le détail du test vocal Hermes : la rangée n'en garde qu'une valeur courte, ce texte-là va au
    // pied de section, sous la carte, comme un pied de formulaire référence.
    var hermesTrial by remember(replyVoice, server.configSignature) {
        mutableStateOf<VoiceTrialOutcome?>(null)
    }

    val voiceFooter = when (replyVoice) {
        ReplyVoice.ON_DEVICE ->
            "Android's on-device synthesizer. Zero network, fastest start. More voices can be installed in the phone's text-to-speech settings."
        ReplyVoice.KOKORO ->
            "Reads replies with your Kokoro-compatible TTS server and the voice or blend chosen here. Falls back to the on-device voice if the server is unreachable."
        ReplyVoice.HERMES ->
            "Your Hermes reads replies out with the voice set under tts in its config.yaml, through the Dashboard connection. The phone needs no voice key. “Say a test sentence” shows which provider speaks. Speed and language are Hermes's settings too. Falls back to the on-device voice when the Dashboard can't speak."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Voice", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(header = "Voice", footer = voiceFooter) {
                OnyxRow {
                    BasicText("Voice", style = theme.body.copy(color = theme.primaryText), modifier = Modifier.weight(1f))
                }
                OnyxRowDivider()
                OnyxRow {
                    OnyxSegmented(
                        options = ReplyVoice.entries.map { it.label },
                        selectedIndex = ReplyVoice.entries.indexOf(replyVoice).coerceAtLeast(0),
                        onSelect = { index -> viewModel.setReplyVoice(ReplyVoice.entries[index]) },
                    )
                }
                if (replyVoice == ReplyVoice.HERMES) {
                    OnyxRowDivider()
                    HermesVoiceRows(
                        server = server,
                        speechLocale = speechLocale,
                        onOutcomeUpdate = { hermesTrial = it },
                    )
                }
                if (replyVoice == ReplyVoice.KOKORO) {
                    OnyxRowDivider()
                    OnyxRow {
                        OnyxTextField(
                            value = kokoroUrl,
                            onValueChange = viewModel::setKokoroUrl,
                            placeholder = "http://your-tts-server:8880",
                            keyboardType = KeyboardType.Uri,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OnyxRowDivider()
                    // Le jeton du service vocal protégé : masqué (`PasswordVisualTransformation`),
                    // avec bouton copier — jamais relu en clair (la règle de la maison).
                    OnyxFieldRow(
                        label = "Voice token",
                        value = kokoroToken,
                        onValueChange = viewModel::setKokoroToken,
                        placeholder = "",
                        secret = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    )
                    OnyxRowDivider()
                    OnyxValueRow(
                        label = "Voice",
                        value = kokoroVoice,
                        localizeValue = false,
                        onClick = onOpenKokoroVoices,
                    )
                    OnyxRowDivider()
                    OnyxRow {
                        BasicText(
                            text = "Sent to the voice server as a bearer token. " +
                                "Leave it empty when the server needs none.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }
                // Hermes possède sa vitesse dans config.yaml : le curseur ne lui est pas envoyé.
                if (replyVoice != ReplyVoice.HERMES) {
                    OnyxRowDivider()
                    OnyxRow {
                        BasicText("Voice speed", style = theme.body.copy(color = theme.primaryText), modifier = Modifier.weight(1f))
                        BasicText(
                            text = String.format(OnyxLanguage.preferred, "%.2f×", speed),
                            localize = false,
                            style = theme.body.copy(color = theme.secondaryText),
                        )
                    }
                    OnyxRowDivider()
                    OnyxRow {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OnyxSlider(
                                value = speed,
                                onValueChange = viewModel::setVoiceSpeed,
                                range = 0.5f..2.0f,
                            )
                        }
                    }
                }
            }

            // Le pied de section : le détail du test (échec serveur, voix de l'appareil), sous la
            // carte et jamais dans la rangée. Rouge seulement quand rien n'a pu parler.
            if (replyVoice == ReplyVoice.HERMES) {
                voiceTrialFootnote(hermesTrial)?.let { detail ->
                    BasicText(
                        text = detail,
                        localize = false,
                        style = theme.footnote.copy(
                            color = if (voiceTrialFootnoteIsError(hermesTrial)) {
                                theme.error
                            } else {
                                theme.secondaryText
                            },
                        ),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp),
                    )
                }
            }

            OnyxFormSection {
                OnyxValueRow(
                    label = "Listening language",
                    value = speechLocale,
                    symbol = Symbols.MicOutline,
                    onClick = onOpenListeningLanguage,
                )
                OnyxRowDivider()
                OnyxValueRow(
                    label = "Reply language",
                    value = SettingsRepository.REPLY_LANGUAGES.firstOrNull { it.first == replyLanguage }?.second
                        ?: "Automatic",
                    symbol = Symbols.Globe,
                    onClick = onOpenReplyLanguage,
                )
                OnyxRowDivider()
                OnyxValueRow(
                    label = "Stop phrases",
                    value = "",
                    symbol = Symbols.MicOutline,
                    onClick = onOpenStopPhrases,
                )
                OnyxRowDivider()
                OnyxValueRow(
                    label = "Spoken prefixes",
                    value = "",
                    symbol = Symbols.Bubbles,
                    onClick = onOpenSpokenPrefixes,
                )
            }

            OnyxFormSection {
                OnyxToggleRow(
                    label = "Start voice mode in hands-free",
                    checked = handsFree,
                    onCheckedChange = viewModel::setHandsFreeByDefault,
                )
                OnyxRowDivider()
                // « Reconnaître hors ligne » : **éteint par défaut**. C'est le seul chemin vers le
                // moteur hors ligne ; s'il n'a pas la langue, l'écoute bascule en ligne d'elle-même
                // (le code 13 `language-unavailable` ne ferme plus la session).
                OnyxToggleRow(
                    label = "Recognise offline",
                    checked = offlineRecognition,
                    onCheckedChange = viewModel::setOfflineRecognition,
                )
                OnyxRowDivider()
                OnyxToggleRow(
                    label = "Talk over replies",
                    // La rangée est un interrupteur : allumé pour « partout », éteint pour
                    // « jamais ». Le choix exact (avec un casque seulement, le défaut) se règle
                    // dans le panneau vocal de l'écran Voix.
                    checked = talkOver != TalkOver.OFF,
                    onCheckedChange = viewModel::setTalkOver,
                )
                OnyxRowDivider()
                // `SettingsSections:531` : le même libellé qu'référence, mot pour mot.
                OnyxToggleRow(
                    label = "Earpiece when raised to your ear",
                    checked = earpieceAtEar,
                    onCheckedChange = viewModel::setEarpieceAtEar,
                )
            }
        }
    }
}
