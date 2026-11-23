package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSlider
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.settings.voice.VoicePreviewButton
import com.onyx.app.ui.settings.voice.VoicePreviewSpinner
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.BotVoice
import com.onyx.app.voice.BotVoicePicker
import com.onyx.app.voice.BotVoices
import com.onyx.app.voice.KokoroSpeechClient
import com.onyx.app.voice.ReplyVoice
import com.onyx.app.voice.SpeechOutput
import com.onyx.app.voice.SpokenVoice
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A bot's voice (`BotVoicePicker`): the one in which it reads its lines, set for it alone.
 *
 * "Automatic" ("Automatique", a voice of its own, `BotVoices`), "Same as <name>" ("Identique à <nom>"), or one of the voices
 * Onyx reads in now — those installed on the phone, or those of the Kokoro server —, each
 * with **its preview** before choosing. Its speed too: its own, or Onyx's.
 *
 * The setting goes out through [onPick] (`SettingsRepository.setBotVoice`): nothing is kept here, the
 * sheet rereads what the app wrote — a choice is therefore verified after a restart.
 */
@Composable
fun BotVoicePickerScreen(
    /** The bot's profile (the key of `Settings.botVoices`). */
    botName: String,
    /** What it is called ("Kilo"), for the title and the section footers. */
    displayName: String,
    /** The name of the main agent ("Hermes"), the one in "Same as …" ("Identique à …"). */
    mainName: String,
    /** `Settings.botVoicesHere`: what is set for the bots of this server. */
    picked: Map<String, BotVoice>,
    /** The chosen synthesis (Settings → Voice → Reply voice). */
    replyVoice: ReplyVoice,
    /** Onyx's Kokoro voice, the one a bot set to the main agent receives. */
    kokoroVoice: String,
    /** The Kokoro server address, to ask it for its list of voices. */
    kokoroUrl: String,
    /** The voice service token, when it is protected (Settings → Voice → Voice token). */
    kokoroToken: String,
    /** Onyx's speed (Settings → Voice → Voice speed). */
    voiceSpeed: Float,
    /** The reading language ("fr-FR"): a bot's voices are taken from it. */
    speechLocale: String,
    /** The names of the server's bots: the distribution does not give two the same. */
    botNames: List<String>,
    /** Sets the bot's voice; `null` clears its setting (it takes back a voice of its own). */
    onPick: (BotVoice?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The sample kept apart, like the model's `SpeechOutput()`: listening does not touch the
    // current reading, and goes quiet when the sheet closes.
    val sample = remember { SpeechOutput(context) }
    val kokoro = remember(kokoroToken) { KokoroSpeechClient(token = kokoroToken) }

    var installed by remember { mutableStateOf<List<SpokenVoice>>(emptyList()) }
    var deviceMain by remember { mutableStateOf<String?>(null) }
    var kokoroVoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(replyVoice == ReplyVoice.KOKORO) }
    var playing by remember { mutableStateOf<String?>(null) }
    var speedJob by remember { mutableStateOf<Job?>(null) }

    // The bot being set: what the sheet shows is what the app wrote, reread here.
    val current = picked[botName]
    val others = remember(picked, botName) { picked - botName }
    val listening = remember(speechLocale) {
        BotVoices.base(
            speechLocale.ifEmpty { runCatching { Locale.getDefault().toLanguageTag() }.getOrDefault("") },
        )
    }

    DisposableEffect(sample) {
        sample.onFinished = { playing = null }
        onDispose {
            speedJob?.cancel()
            sample.shutdown()
        }
    }

    // The installed voices: the engine is built without blocking, so we wait until it is ready.
    LaunchedEffect(sample, replyVoice) {
        if (replyVoice != ReplyVoice.ON_DEVICE) return@LaunchedEffect
        var tries = 0
        while (!sample.isReady && tries < 50) {
            delay(100)
            tries++
        }
        installed = sample.installedVoices()
        deviceMain = sample.defaultVoiceId()
    }

    // The Kokoro server's voices: its list, like Settings → Voice → Kokoro.
    LaunchedEffect(replyVoice, kokoroUrl) {
        if (replyVoice != ReplyVoice.KOKORO) return@LaunchedEffect
        loading = true
        kokoroVoices = runCatching { kokoro.voiceIds(kokoroUrl) }.getOrDefault(emptyList())
        loading = false
    }

    /** A sample in [voice]; `null`: the voice "Automatic" gives it right now. */
    fun preview(voice: BotVoice?) {
        val key = voice?.let { it.kokoro ?: it.onDevice ?: "same" } ?: "automatic"
        if (playing == key) {
            sample.stop()
            playing = null
            return
        }
        sample.stop()
        sample.setRate(voiceSpeed)
        sample.setLanguage(speechLocale)
        // The same resolution as reading: the tested entry takes the place of the setting, and nothing
        // else changes. "Automatic" is asked for without it, like the model's `set(nil)`
        // which first clears the setting then reads.
        sample.configure(
            voice = replyVoice,
            kokoroUrl = kokoroUrl,
            kokoroVoice = kokoroVoice,
            kokoroToken = kokoroToken,
            hermesServer = null,
            botVoices = if (voice == null) others else others + (botName to voice),
            kokoroVoices = kokoroVoices,
        )
        playing = key
        sample.speak("Hi, I'm $displayName. This is how I sound.", speaker = botName)
    }

    fun choose(voice: BotVoice?) {
        onPick(BotVoicePicker.withVoice(voice, current))
        preview(voice)
    }

    fun setSpeed(speed: Float?) {
        onPick(BotVoicePicker.withSpeed(speed?.let { snapSpeed(it) }, current))
    }

    /** The slider released: the sample is played at the set speed, like `onEditingChanged`. */
    fun playAtSpeed() {
        speedJob?.cancel()
        speedJob = scope.launch {
            delay(400)
            preview(current)
        }
    }

    val choices = remember(replyVoice, kokoroVoices, kokoroVoice, mainName, installed, listening) {
        BotVoicePicker.choices(
            engine = replyVoice,
            kokoroVoices = kokoroVoices,
            kokoroMain = kokoroVoice,
            mainName = mainName,
            installed = installed,
            listening = listening,
        )
    }

    // What "Automatic" gives it right now, stated plainly: the voice of its own, or
    // Onyx's voice when there is nothing to distribute.
    val automaticDetail = remember(replyVoice, others, mainName, kokoroVoice, kokoroVoices, installed, listening, deviceMain, botNames) {
        val id = BotVoicePicker.automaticVoice(
            engine = replyVoice,
            profile = botName,
            others = others,
            kokoroMain = kokoroVoice,
            kokoroVoices = kokoroVoices,
            installed = installed,
            listening = listening,
            deviceMain = deviceMain,
            names = botNames,
        )
        id?.let { if (replyVoice == ReplyVoice.KOKORO) BotVoicePicker.kokoroName(it) else it }
            ?: when (replyVoice) {
                ReplyVoice.KOKORO -> OnyxLocalization.format(
                    "%1\$s's voice, for now: the server has no other voice in its language",
                    mainName,
                )
                else -> OnyxLocalization.format(
                    "%1\$s's voice, for now: no other voice is installed for its language",
                    mainName,
                )
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // The title and the sheet's single action, like the model's `navigationTitle` and
            // "Done" button: there is no going back, only a way out.
            OnyxPageHeader(
                title = "$displayName's Voice",
                onBack = null,
                action = "Done",
                onAction = onBack,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            if (replyVoice == ReplyVoice.HERMES) {
                // Hermes's voice is the server's, one for all: nothing to set per bot.
                OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = "Replies are read in Hermes's own voice (Settings → Voice → Reply voice), one for every bot. Pick On-device or Kokoro there for each bot to have a voice of its own.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }
            } else {
                // Its speed: Onyx's, or its own.
                OnyxFormSection(
                    header = "Speed",
                    footer = current?.speed?.let {
                        OnyxLocalization.format(
                            "%1\$s reads at this speed in voice mode, in group chats and in the car, whatever its voice.",
                            displayName,
                        )
                    } ?: OnyxLocalization.format(
                        "%1\$s reads at %2\$s's speed, %3\$s (Settings → Voice → Voice speed).",
                        displayName,
                        mainName,
                        speedText(voiceSpeed),
                    ),
                    localizeFooter = false,
                ) {
                    OnyxToggleRow(
                        label = "Its own speed",
                        checked = current?.speed != null,
                        onCheckedChange = { own -> setSpeed(if (own) voiceSpeed else null) },
                    )
                    current?.speed?.let { speed ->
                        OnyxRowDivider()
                        OnyxRow {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(
                                        text = "Speed",
                                        style = theme.body.copy(color = theme.primaryText),
                                        modifier = Modifier.weight(1f),
                                    )
                                    BasicText(
                                        text = speedText(speed),
                                        localize = false,
                                        style = theme.body.copy(color = theme.secondaryText),
                                    )
                                }
                                OnyxSlider(
                                    value = speed,
                                    onValueChange = { value ->
                                        setSpeed(value)
                                        playAtSpeed()
                                    },
                                    range = 0.5f..2.0f,
                                )
                            }
                        }
                    }
                }

                OnyxFormSection(
                    footer = OnyxLocalization.format(
                        "Automatic gives %1\$s a voice of its own, not %2\$s's and not another bot's while there are voices to go round. It reads %1\$s's replies in voice mode, in group chats and in the car.",
                        displayName,
                        mainName,
                    ),
                    localizeFooter = false,
                ) {
                    VoiceChoiceRow(
                        title = "Automatic",
                        detail = automaticDetail,
                        chosen = current?.isAutomatic != false,
                        playing = playing == "automatic",
                        onChoose = { choose(null) },
                        onHear = { preview(null) },
                    )
                    OnyxRowDivider()
                    VoiceChoiceRow(
                        title = "Same as $mainName",
                        detail = null,
                        chosen = current?.sameAsMain == true,
                        playing = playing == "same",
                        onChoose = { choose(BotVoice(sameAsMain = true)) },
                        onHear = { preview(BotVoice(sameAsMain = true)) },
                    )
                }

                OnyxFormSection(
                    header = if (replyVoice == ReplyVoice.KOKORO) "Kokoro voices" else "Voices on this phone",
                ) {
                    if (loading) {
                        OnyxRow {
                            VoicePreviewSpinner(size = 18.dp)
                            BasicText(
                                text = "Loading voices from Kokoro…",
                                style = theme.body.copy(color = theme.secondaryText),
                            )
                        }
                    }
                    choices.forEachIndexed { index, choice ->
                        if (index > 0) OnyxRowDivider()
                        VoiceChoiceRow(
                            title = choice.name,
                            detail = choice.detail,
                            chosen = current?.kokoro == choice.id || current?.onDevice == choice.id,
                            playing = playing == choice.id,
                            localizeTitle = false,
                            onChoose = { choose(choice.voice) },
                            onHear = { preview(choice.voice) },
                        )
                    }
                    if (replyVoice == ReplyVoice.ON_DEVICE) {
                        if (choices.isNotEmpty() || loading) OnyxRowDivider()
                        OnyxRow {
                            BasicText(
                                text = "More voices, and better ones, come from the phone's text-to-speech settings.",
                                style = theme.footnote.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * An offered voice: its name (and its detail), the checkmark when it is its own, and the preview of
 * the sample — the model's `row(_:detail:chosen:voice:choose:)`, with the same
 * `Button("Hear it", systemImage: "play.circle")`.
 */
@Composable
private fun VoiceChoiceRow(
    title: String,
    detail: String?,
    chosen: Boolean,
    playing: Boolean,
    onChoose: () -> Unit,
    onHear: () -> Unit,
    /** False for a voice name: it is a name, it is shown as is. */
    localizeTitle: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
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
                .onyxTappable(onClick = onChoose)
                .semantics { selected = chosen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    localize = localizeTitle,
                    style = theme.body.copy(color = theme.primaryText),
                )
                detail?.let {
                    BasicText(
                        text = it,
                        style = theme.caption.copy(color = theme.secondaryText),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (chosen) {
                OnyxIcon(symbol = Symbols.Checkmark, tint = theme.accent, size = 15.dp)
            }
        }
        VoicePreviewButton(
            playing = playing,
            label = if (playing) "Stop preview" else "Hear it",
            onClick = onHear,
        )
    }
}

/** "1.40×", the figure in the model's `.monospacedDigit()` format. */
private fun speedText(speed: Float): String = String.format(Locale.ROOT, "%.2f×", speed)

/** A 0.05 step over 0.5…2.0: the model's `Slider` step. */
private fun snapSpeed(value: Float): Float =
    (Math.round(value / 0.05f) * 0.05f).coerceIn(0.5f, 2.0f)
