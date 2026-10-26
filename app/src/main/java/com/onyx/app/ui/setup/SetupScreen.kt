package com.onyx.app.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.R
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.ConnectionProbe
import com.onyx.app.ui.design.OnyxFieldRow
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal val OnyxMarkSize = 76.dp

/** L'ordre de la liste « How does Onyx reach it? »: Dashboard, API, OpenAI-compatible. */
internal val BackendTransports = listOf(
    Transport.HERMES_SERVE,
    Transport.HERMES_SESSIONS,
    Transport.CHAT_COMPLETIONS,
)

/** Le libellé d'un transport, mot pour mot de `Transport.label`. */
internal fun transportLabel(transport: Transport): String = when (transport) {
    Transport.HERMES_SERVE -> "Hermes Dashboard (WebSocket)"
    Transport.HERMES_SESSIONS -> "Hermes API (shared sessions)"
    Transport.CHAT_COMPLETIONS -> "OpenAI-compatible (direct to a model)"
}

/**
 * Le premier lancement, et « Set up connection » depuis les Réglages.
 *
 * Comme: le mot d'accueil, ce que Onyx promet, le code de configuration, le
 * choix du transport — trois options en liste, comme le `Picker(.inline)` — et les champs
 * du transport retenu. Rien n'est prérempli.
 */
@Composable
fun SetupScreen(
    config: ServerConfig,
    onChange: ((ServerConfig) -> ServerConfig) -> Unit,
    onDone: () -> Unit,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    onScanCode: () -> Unit,
    onPasteCode: (String) -> Boolean,
    /** « Skip »: sortir de la configuration sans connexion établie. */
    onSkip: () -> Unit = onDone,
) {
    val theme = LocalOnyxTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    var testing by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ConnectionProbe?>(null) }
    var pasteProblem by remember { mutableStateOf(false) }

    fun pasteFromClipboard() {
        scope.launch {
            val clip = runCatching { clipboard.getClipEntry() }.getOrNull()?.clipData
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString().orEmpty()
            } else {
                ""
            }
            pasteProblem = !onPasteCode(text)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Set up Onyx",
                onBack = onSkip,
                backLabel = "Skip",
                showBackChevron = false,
                action = "Done",
                onAction = onDone,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // « Welcome to Onyx », et ce qu'elle fait.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_graphite),
                    contentDescription = null,
                    modifier = Modifier.size(OnyxMarkSize).clip(RoundedCornerShape(17.dp)),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(
                        text = "Welcome to Onyx",
                        style = theme.title2.copy(color = theme.primaryText, fontWeight = FontWeight.Bold),
                    )
                    BasicText(
                        text = "Talk to your own AI agent by voice or chat.",
                        style = theme.subheadline.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                    )
                }
            }

            // Les trois promesses, aux mots.
            OnyxFormSection {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SetupFeature(
                        Symbols.ServerRack,
                        "Your own server",
                        "Works with Hermes, the open-source agent you run on your own machine, or any OpenAI-compatible model server.",
                    )
                    SetupFeature(
                        Symbols.LockShield,
                        "Private by design",
                        "Your conversations go only to the server you enter below.",
                    )
                    SetupFeature(
                        Symbols.Waveform,
                        "Speech stays on device",
                        "Your voice is recognized on this device, not in the cloud.",
                    )
                }
            }

            OnyxFormSection(
                header = "Have a setup code?",
                footer = "A setup code fills in everything below. Make one on a device that is already set up: Settings › Connection › Set up another device.",
            ) {
                if (codeScannerSupported(context)) {
                    OnyxRow(onClick = onScanCode) {
                        OnyxIcon(Symbols.QrCodeViewfinder, tint = theme.accent, size = 18.dp)
                        BasicText(
                            text = "Scan a setup code",
                            style = theme.body.copy(color = theme.accent),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OnyxRowDivider()
                }
                // « Paste a setup link » et son bouton « Paste » en capsule, comme le
                // `PasteButton(.capsule)`. Toute la rangée colle aussi (comportement d'avant).
                OnyxRow(onClick = { pasteFromClipboard() }) {
                    OnyxIcon(Symbols.Link, tint = theme.accent, size = 18.dp)
                    BasicText(
                        text = "Paste a setup link",
                        style = theme.body.copy(color = theme.primaryText),
                        modifier = Modifier.weight(1f),
                    )
                    SetupPasteButton(onClick = { pasteFromClipboard() })
                }
                if (pasteProblem) {
                    OnyxRowDivider()
                    OnyxRow {
                        OnyxIcon(Symbols.XmarkOctagonFill, tint = theme.error, size = 17.dp)
                        BasicText(
                            text = "That isn't a Onyx setup link.",
                            style = theme.footnote.copy(color = theme.error),
                        )
                    }
                }
            }

            OnyxFormSection(header = "How does Onyx reach it?") {
                BackendTransports.forEachIndexed { index, transport ->
                    if (index > 0) OnyxRowDivider()
                    OnyxRow(onClick = { onChange { it.copy(transport = transport) } }) {
                        BasicText(
                            text = transportLabel(transport),
                            style = theme.body.copy(color = theme.primaryText),
                            modifier = Modifier.weight(1f),
                        )
                        if (config.transport == transport) {
                            OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                        }
                    }
                }
            }

            when (config.transport) {
                Transport.HERMES_SESSIONS -> OnyxFormSection(
                    header = "Hermes API server",
                    footer = "The gateway's API server and its API_SERVER_KEY. Sessions from every platform appear in Onyx.",
                ) {
                    OnyxFieldRow(
                        label = "Address",
                        value = config.baseUrl,
                        placeholder = "https://your-gateway:8642",
                        keyboardType = KeyboardType.Uri,
                        onValueChange = { value -> onChange { it.copy(baseUrl = value) } },
                    )
                    OnyxRowDivider()
                    OnyxFieldRow(
                        label = "API key",
                        value = config.apiKey,
                        placeholder = "API key",
                        secret = true,
                        onValueChange = { value -> onChange { it.copy(apiKey = value) } },
                    )
                }

                Transport.HERMES_SERVE -> OnyxFormSection(
                    header = "Hermes Dashboard",
                    footer = "The same login as the Hermes Dashboard: its username and password, or Sign in with a browser where it uses Google or another provider. Adds tool approvals and slash commands.",
                ) {
                    OnyxFieldRow(
                        label = "Address",
                        value = config.serveBaseUrl,
                        placeholder = "http://your-onyx:9119",
                        keyboardType = KeyboardType.Uri,
                        onValueChange = { value -> onChange { it.copy(serveBaseUrl = value) } },
                    )
                    OnyxRowDivider()
                    OnyxFieldRow(
                        label = "Username",
                        value = config.serveUsername,
                        placeholder = "Dashboard username",
                        onValueChange = { value -> onChange { it.copy(serveUsername = value) } },
                    )
                    OnyxRowDivider()
                    OnyxFieldRow(
                        label = "Password",
                        value = config.servePassword,
                        placeholder = "Dashboard password",
                        secret = true,
                        onValueChange = { value -> onChange { it.copy(servePassword = value) } },
                    )
                }

                Transport.CHAT_COMPLETIONS -> OnyxFormSection(
                    header = "OpenAI-compatible endpoint",
                    footer = "Straight to a model with no agent in between: llama.cpp, llama-swap, vLLM, Ollama, or a hosted provider.",
                ) {
                    OnyxFieldRow(
                        label = "Address",
                        value = config.baseUrl,
                        placeholder = "http://your-server:8080",
                        keyboardType = KeyboardType.Uri,
                        onValueChange = { value -> onChange { it.copy(baseUrl = value) } },
                    )
                    OnyxRowDivider()
                    OnyxFieldRow(
                        label = "Model",
                        value = config.model,
                        placeholder = "Model name",
                        onValueChange = { value -> onChange { it.copy(model = value) } },
                    )
                    OnyxRowDivider()
                    OnyxFieldRow(
                        label = "API key",
                        value = config.apiKey,
                        placeholder = "None",
                        secret = true,
                        onValueChange = { value -> onChange { it.copy(apiKey = value) } },
                    )
                }
            }

            OnyxFormSection {
                OnyxRow(onClick = {
                    if (testing) return@OnyxRow
                    testing = true
                    probe = null
                    val draft = config
                    scope.launch {
                        probe = ChatCompletionsClient(draft).testConnection()
                        testing = false
                    }
                }) {
                    if (testing) SetupSpinner()
                    BasicText(
                        text = if (testing) "Testing…" else "Test connection",
                        style = theme.body.copy(color = theme.accent),
                        modifier = Modifier.weight(1f),
                    )
                }
                probe?.let { result ->
                    OnyxRowDivider()
                    OnyxRow {
                        OnyxIcon(
                            symbol = if (result.ok) Symbols.CheckmarkCircle else Symbols.XmarkOctagonFill,
                            tint = if (result.ok) theme.success else theme.error,
                            size = 17.dp,
                        )
                        BasicText(
                            text = result.message,
                            localize = false,
                            style = theme.footnote.copy(color = if (result.ok) theme.success else theme.error),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Le bouton « Paste » en capsule du `PasteButton(.capsule)`. */
@Composable
private fun SetupPasteButton(onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(theme.accent)
            .onyxClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Paste",
            style = theme.subheadline.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun SetupFeature(symbol: Int, title: String, detail: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(width = 30.dp, height = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = symbol, tint = theme.accent, size = 20.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(text = title, style = theme.subheadline.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold))
            BasicText(text = detail, style = theme.footnote.copy(color = theme.secondaryText))
        }
    }
}
