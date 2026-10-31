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
 * La voix d'un bot (`BotVoicePicker`) : celle dont il lit ses répliques, réglée pour lui seul.
 *
 * « Automatique » (une voix de son cru, `BotVoices`), « Identique à <nom> », ou une des voix dont
 * Onyx lit maintenant — celles installées sur le téléphone, ou celles du serveur Kokoro —, chacune
 * avec **son écoute** avant de choisir. Sa vitesse aussi : la sienne, ou celle d'Onyx.
 *
 * Le réglage part par [onPick] (`SettingsRepository.setBotVoice`) : rien n'est gardé ici, la
 * feuille relit ce que l'app a écrit — un choix se vérifie donc après un redémarrage.
 */
@Composable
fun BotVoicePickerScreen(
    /** Le profil du bot (la clé de `Settings.botVoices`). */
    botName: String,
    /** Ce qu'il est appelé (« Kilo »), pour le titre et les pieds de section. */
    displayName: String,
    /** Le nom de l'agent principal (« Hermes »), celui de « Identique à … ». */
    mainName: String,
    /** `Settings.botVoicesHere` : ce qui est réglé pour les bots de ce serveur. */
    picked: Map<String, BotVoice>,
    /** La synthèse choisie (Réglages → Voix → Reply voice). */
    replyVoice: ReplyVoice,
    /** La voix Kokoro d'Onyx, celle qu'un bot réglé sur l'agent principal reçoit. */
    kokoroVoice: String,
    /** L'adresse du serveur Kokoro, pour lui demander sa liste de voix. */
    kokoroUrl: String,
    /** Le jeton du service vocal, quand il est protégé (Réglages → Voix → Jeton de la voix). */
    kokoroToken: String,
    /** La vitesse d'Onyx (Réglages → Voix → Voice speed). */
    voiceSpeed: Float,
    /** La langue de lecture (« fr-FR ») : les voix d'un bot y sont prises. */
    speechLocale: String,
    /** Les noms des bots du serveur : la répartition n'en donne pas deux pareilles. */
    botNames: List<String>,
    /** Règle la voix du bot ; `null` efface son réglage (il reprend une voix de son cru). */
    onPick: (BotVoice?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // L'échantillon à part, comme le `SpeechOutput()` du modèle : écouter ne touche pas à la
    // lecture en cours, et se tait quand la feuille se ferme.
    val sample = remember { SpeechOutput(context) }
    val kokoro = remember(kokoroToken) { KokoroSpeechClient(token = kokoroToken) }

    var installed by remember { mutableStateOf<List<SpokenVoice>>(emptyList()) }
    var deviceMain by remember { mutableStateOf<String?>(null) }
    var kokoroVoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(replyVoice == ReplyVoice.KOKORO) }
    var playing by remember { mutableStateOf<String?>(null) }
    var speedJob by remember { mutableStateOf<Job?>(null) }

    // Le bot qui se règle : ce que la feuille montre est ce que l'app a écrit, relu ici.
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

    // Les voix installées : le moteur se construit sans bloquer, donc on attend qu'il soit prêt.
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

    // Les voix du serveur Kokoro : sa liste, comme Réglages → Voix → Kokoro.
    LaunchedEffect(replyVoice, kokoroUrl) {
        if (replyVoice != ReplyVoice.KOKORO) return@LaunchedEffect
        loading = true
        kokoroVoices = runCatching { kokoro.voiceIds(kokoroUrl) }.getOrDefault(emptyList())
        loading = false
    }

    /** Un échantillon dans [voice] ; `null` : la voix que « Automatique » lui donne maintenant. */
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
        // La même résolution que la lecture : la fiche essayée prend la place du réglage, et rien
        // d'autre ne change. « Automatique » se demande sans elle, comme le `set(nil)` du modèle
        // qui efface d'abord le réglage puis lit.
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

    /** Le curseur relâché : l'échantillon se dit à la vitesse réglée, comme `onEditingChanged`. */
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

    // Ce que « Automatique » lui donne maintenant, dit en clair : la voix de son cru, ou la voix
    // d'Onyx quand il n'y a rien à répartir.
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
            // Le titre et l'unique action de la feuille, comme le `navigationTitle` et le bouton
            // « Done » du modèle : il n'y a pas de retour en arrière, seulement une sortie.
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
                // La voix d'Hermes est celle du serveur, une pour tous : rien à régler par bot.
                OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = "Replies are read in Hermes's own voice (Settings → Voice → Reply voice), one for every bot. Pick On-device or Kokoro there for each bot to have a voice of its own.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }
            } else {
                // Sa vitesse : celle d'Onyx, ou la sienne.
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
 * Une voix offerte : son nom (et son détail), la coche quand c'est la sienne, et l'écoute de
 * l'échantillon — `row(_:detail:chosen:voice:choose:)` du modèle, avec le même
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
    /** Faux pour un nom de voix : c'est un nom, il se montre tel quel. */
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

/** « 1.40× », le chiffre au format du `.monospacedDigit()` du modèle. */
private fun speedText(speed: Float): String = String.format(Locale.ROOT, "%.2f×", speed)

/** Un cran de 0,05 sur 0,5…2,0 : le pas du `Slider` du modèle. */
private fun snapSpeed(value: Float): Float =
    (Math.round(value / 0.05f) * 0.05f).coerceIn(0.5f, 2.0f)
