package com.onyx.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.BuildConfig
import com.onyx.app.car.AutoCardLook
import com.onyx.app.car.AutoVoiceLook
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxDisclosureSummary
import com.onyx.app.ui.design.OnyxFieldRow
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSegmented
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.OnyxValueRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.AppearanceMode
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.theme.ThemeColors
import com.onyx.app.ui.voice.OrbLight
import com.onyx.app.ui.voice.VoiceOrbChoice
import kotlin.math.roundToInt

/**
 * Réglages, première page: la connexion et le chemin vers l'apparence.
 *
 * Comme: un formulaire groupé, une page par sujet long, et un bouton
 * « Done » en haut à droite. Les pages Voix, Écriture et Agent arriveront à leurs étapes.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onDone: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenGateway: (() -> Unit)? = null,
    onOpenFiles: (() -> Unit)? = null,
    onOpenContext: (() -> Unit)? = null,
    onOpenMemory: (() -> Unit)? = null,
    onOpenCron: (() -> Unit)? = null,
    onOpenKanban: (() -> Unit)? = null,
    onOpenSkills: (() -> Unit)? = null,
    onOpenTools: (() -> Unit)? = null,
    onOpenPhoneTools: (() -> Unit)? = null,
    onOpenBots: (() -> Unit)? = null,
    onOpenServers: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
    onOpenWhatsNew: (() -> Unit)? = null,
    onOpenTipJar: (() -> Unit)? = null,
    onOpenLock: (() -> Unit)? = null,
    onOpenConnect: (() -> Unit)? = null,
    onOpenTerminal: (() -> Unit)? = null,
    onOpenAcknowledgements: (() -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
    onOpenConnectionDetails: (() -> Unit)? = null,
    /** « Erase everything »: la confirmation est demandée avant que l'hôte ne le fasse. */
    onEraseEverything: (() -> Unit)? = null,
    /** La fenêtre de contexte du serveur actif (`MetricsSettings`). */
    onSetContextWindow: (String, Int) -> Unit = { _, _ -> },
    /**
     * Le contrôle du volet (`sidebar.left`): les Réglages se présentent **par-dessus** les deux
     * panneaux, donc leur barre garde le même contrôle que les autres routes — replier la liste
     * depuis ici la retrouve repliée en sortant.
     */
    sidebarControl: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val currentTheme by viewModel.theme.collectAsStateWithLifecycle()
    val speechLocale by viewModel.speechLocale.collectAsStateWithLifecycle()
    val handsFreeByDefault by viewModel.handsFreeByDefault.collectAsStateWithLifecycle()
    val returnSends by viewModel.returnSends.collectAsStateWithLifecycle()
    val suggestFollowUps by viewModel.suggestFollowUps.collectAsStateWithLifecycle()
    val notifyInBackground by viewModel.notifyInBackground.collectAsStateWithLifecycle()
    var showLanguages by remember { mutableStateOf(false) }
    val speechLocales = SettingsRepository.SPEECH_LOCALES
    // Le serveur actif: ses métriques et la page « Connection details » l'ouvrent.
    val activeServer = servers.firstOrNull { it.id == activeId }
    var confirmingErase by remember { mutableStateOf(false) }

    val editing = draft

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Settings", onBack = null, leading = sidebarControl, action = "Done", onAction = onDone)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            if (editing != null) {
                ServerEditor(
                    config = editing,
                    probeMessage = probe?.message,
                    isTesting = isTesting,
                    onChange = { transform -> viewModel.update(transform) },
                    onTest = viewModel::testConnection,
                    onSave = viewModel::saveAndClose,
                    onCancel = viewModel::closeEditor,
                    onDelete = { viewModel.delete(editing) },
                )
            } else {
                ServersList(
                    servers = servers,
                    activeId = activeId,
                    onSelect = { viewModel.setActive(it.id) },
                    onEdit = { viewModel.open(it) },
                    onAdd = viewModel::addServer,
                )
            }

            OnyxFormSection(header = "Answering") {
                OnyxValueRow(
                    label = "Model",
                    value = servers.firstOrNull { it.id == activeId }?.effectiveModel?.ifEmpty { OnyxLocalization["Server default"] }
                        ?: OnyxLocalization["Server default"],
                    localizeValue = false,
                    symbol = Symbols.Bolt,
                    onClick = onOpenModels,
                )
                OnyxRowDivider()
                OnyxValueRow(
                    label = "Appearance",
                    value = currentTheme.label,
                    symbol = Symbols.PaintbrushOutline,
                    onClick = onOpenAppearance,
                )
            }

            OnyxFormSection(header = "Voice") {
                OnyxValueRow(
                    label = "Voice",
                    value = speechLocale,
                    symbol = Symbols.Waveform,
                    onClick = onOpenVoice,
                )
                onOpenNotifications?.let {
                    OnyxRowDivider()
                    OnyxValueRow(
                        label = "Notifications",
                        value = if (notifyInBackground) "On" else "Off",
                        symbol = Symbols.Bell,
                        onClick = it,
                    )
                }
            }

            // Le pied vient de `ComposerSettings`: il suit la touche Entrée, mot pour mot.
            // « Suggest follow-up questions », logé ici côté Android, est documenté par la
            // phrase de `VoiceSettings` qui le décrit.
            OnyxFormSection(
                header = "Writing",
                footer = (if (returnSends) {
                    "Return sends the message. On a keyboard, Shift-Return starts a new line; " +
                        "for a long message, the field's editor button opens a page where Return always does."
                } else {
                    "Return starts a new line, and the Send button sends. On a keyboard, ⌘Return sends."
                }) + "\n\n" +
                    "“Suggest follow-up questions” shows three things you might ask next under each reply, " +
                    "written by the model on your OpenAI-compatible connection (it needs that address set, " +
                    "whichever connection the conversation uses).",
            ) {
                OnyxToggleRow(
                    label = "Return key sends",
                    checked = returnSends,
                    onCheckedChange = viewModel::setReturnSends,
                )
                OnyxRowDivider()
                OnyxToggleRow(
                    label = "Suggest follow-up questions",
                    checked = suggestFollowUps,
                    onCheckedChange = viewModel::setSuggestFollowUps,
                )
            }

            OnyxFormSection(
                header = "Agent",
                footer = "Context, memory, files and the gateway are edited in place on the Hermes host, through its Dashboard.",
            ) {
                val dashboard = servers.firstOrNull { it.id == activeId }?.canAdminister == true
                val gateway = servers.firstOrNull { it.id == activeId }?.canReachGateway == true
                // Chaque ligne n'apparaît que si son écran existe: rien ne promet dans le vide.
                val rows = listOfNotNull(
                    onOpenSkills?.let { AgentEntry("Skills", Symbols.Sparkles, dashboard, it) },
                    onOpenTools?.let { AgentEntry("Tools", Symbols.WrenchAndScrewdriver, dashboard, it) },
                    onOpenPhoneTools?.let { AgentEntry("Phone tools", Symbols.Iphone, true, it) },
                    onOpenBots?.let { AgentEntry("Bots", Symbols.Person2, dashboard, it) },
                    onOpenProfile?.let { AgentEntry("Profile", Symbols.PersonCircle, true, it) },
                    onOpenContext?.let { AgentEntry("Context files", Symbols.PersonCircle, dashboard, it) },
                    onOpenMemory?.let { AgentEntry("Memory", Symbols.Brain, dashboard, it) },
                    onOpenFiles?.let { AgentEntry("Files", Symbols.Folder, dashboard, it) },
                    onOpenGateway?.let { AgentEntry("Gateway", Symbols.Gearshape, dashboard, it) },
                    onOpenCron?.let { AgentEntry("Cron", Symbols.Clock, dashboard, it) },
                    onOpenKanban?.let { AgentEntry("Kanban", Symbols.RectangleSplit, dashboard, it) },
                )
                rows.forEachIndexed { index, entry ->
                    if (index > 0) OnyxRowDivider()
                    AgentRow(entry.label, entry.symbol, entry.enabled, entry.onClick)
                }
            }

            // `ConnectionDetailsView`: toutes les adresses, clés et identifiants sur une
            // page à part, hors du premier écran — y compris ceux d'une connexion qu'on n'utilise
            // pas en ce moment.
            onOpenConnectionDetails?.let { openDetails ->
                OnyxFormSection(
                    footer = "URLs, keys and logins for every connection, including ones you " +
                        "aren't using right now. The Hermes Dashboard login also unlocks the " +
                        "context and memory files, and files the agent sends.",
                ) {
                    OnyxValueRow(
                        label = "Connection details",
                        value = "",
                        symbol = Symbols.KeyOutline,
                        onClick = openDetails,
                    )
                }
            }

            // `MetricsSettings`: la fenêtre de contexte, celle de l'anneau « ctx % ».
            activeServer?.let { server ->
                var contextWindowText by remember(server.id, server.contextWindow) {
                    mutableStateOf(server.contextWindow.takeIf { it > 0 }?.toString().orEmpty())
                }
                OnyxFormSection(
                    header = "Metrics",
                    footer = "Used for the “ctx %” figure when the backend can't report its window. " +
                        "A llama.cpp server reports it automatically.",
                ) {
                    OnyxFieldRow(
                        label = "Context window",
                        value = contextWindowText,
                        placeholder = "131072",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        onValueChange = { value ->
                            val digits = value.filter { it.isDigit() }.take(9)
                            contextWindowText = digits
                            onSetContextWindow(server.id, digits.toIntOrNull() ?: 0)
                        },
                    )
                }
            }

            onOpenTerminal?.let { openTerminal ->
                OnyxFormSection {
                    OnyxValueRow(
                        label = "Terminal",
                        value = "",
                        symbol = Symbols.Terminal,
                        onClick = openTerminal,
                    )
                }
            }

            OnyxFormSection(header = "Privacy") {
                onOpenLock?.let {
                    OnyxValueRow(label = "Lock", value = "", symbol = Symbols.LockOutline, onClick = it)
                }
                if (onOpenLock != null && onOpenConnect != null) OnyxRowDivider()
                onOpenConnect?.let {
                    OnyxValueRow(label = "Set up another device", value = "", symbol = Symbols.QrCode, onClick = it)
                }
            }

            OnyxFormSection(header = "About", footer = "Onyx for Android, a client for your own Hermes server.") {
                onOpenAcknowledgements?.let {
                    OnyxValueRow(label = "Acknowledgements", value = "", symbol = Symbols.Doc, onClick = it)
                    OnyxRowDivider()
                }
                onOpenWhatsNew?.let {
                    OnyxValueRow(label = "What's New", value = "", symbol = Symbols.Sparkles, onClick = it)
                    OnyxRowDivider()
                }
                onOpenTipJar?.let {
                    OnyxValueRow(label = "Support Onyx", value = "", symbol = Symbols.Heart, onClick = it)
                    OnyxRowDivider()
                }
                onOpenServers?.let {
                    OnyxValueRow(
                        label = "Servers",
                        value = "${servers.size}",
                        symbol = Symbols.ServerRack,
                        onClick = it,
                    )
                    OnyxRowDivider()
                }
                OnyxRow {
                    BasicText(
                        text = "Version",
                        style = theme.body.copy(color = theme.primaryText),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = theme.body.copy(color = theme.secondaryText),
                    )
                }
            }

            // `ResetSettings`: retour à une installation neuve. La ligne destructive est la
            // dernière du formulaire, comme dans.
            onEraseEverything?.let { erase ->
                OnyxFormSection(
                    footer = "Deletes every conversation and attachment on this device, forgets " +
                        "all servers, keys and passwords, and returns the settings to their " +
                        "defaults. Nothing on your servers is touched.",
                ) {
                    OnyxRow(onClick = { confirmingErase = true }) {
                        BasicText(
                            text = "Erase everything…",
                            style = theme.body.copy(color = theme.error),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    // La confirmation: un titre visible, « Erase everything » destructeur, et « Cancel ».
    if (confirmingErase) {
        com.onyx.app.ui.design.OnyxAlert(
            title = "Erase everything on this device?",
            message = "Conversations, attachments, credentials and settings. This can't be undone.",
            actions = listOf(
                com.onyx.app.ui.design.AlertAction("Erase everything", destructive = true) {
                    confirmingErase = false
                    onEraseEverything?.invoke()
                },
                com.onyx.app.ui.design.AlertAction("Cancel") { confirmingErase = false },
            ),
            onDismiss = { confirmingErase = false },
        )
    }
}

/** Une ligne de la section Agent, et si le transport courant la permet. */
private data class AgentEntry(
    val label: String,
    val symbol: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** Une ligne de la section Agent: libellé, icône, et grisée si le transport ne la permet pas. */
@Composable
private fun AgentRow(
    label: String,
    symbol: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow(onClick = if (enabled) onClick else null) {
        OnyxIcon(
            symbol = symbol,
            tint = if (enabled) theme.accent else theme.tertiaryText,
            size = 18.dp,
        )
        BasicText(
            text = label,
            style = theme.body.copy(color = if (enabled) theme.primaryText else theme.tertiaryText),
            modifier = Modifier.weight(1f),
        )
        if (!enabled) {
            OnyxIcon(Symbols.LockOutline, tint = theme.tertiaryText, size = 13.dp)
        } else {
            OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
        }
    }
}

/** La liste des serveurs: celui qui répond, et les autres. */
@Composable
private fun ServersList(
    servers: List<ServerConfig>,
    activeId: String,
    onSelect: (ServerConfig) -> Unit,
    onEdit: (ServerConfig) -> Unit,
    onAdd: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    OnyxFormSection(
        header = "Connection",
        footer = "The server that answers. Tap a line to change its address, key and profile.",
    ) {
        if (servers.isEmpty()) {
            // Aucun serveur: la phrase et le chemin pour en poser un, plutôt qu'une section vide.
            OnyxRow {
                BasicText(
                    text = "No server yet",
                    style = theme.body.copy(color = theme.secondaryText),
                    modifier = Modifier.weight(1f),
                )
            }
            OnyxRowDivider()
            OnyxRow(onClick = onAdd) {
                OnyxIcon(Symbols.Plus, tint = theme.accent, size = 17.dp)
                BasicText(
                    text = "Set up a server…",
                    style = theme.body.copy(color = theme.accent),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            servers.forEachIndexed { index, config ->
                if (index > 0) OnyxRowDivider()
                OnyxRow(onClick = { onSelect(config) }) {
                    OnyxIcon(
                        symbol = if (config.id == activeId) Symbols.CheckmarkCircle else Symbols.Circle,
                        tint = if (config.id == activeId) theme.accent else theme.tertiaryText,
                        size = 20.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(text = config.displayName, localize = false, style = theme.body.copy(color = theme.primaryText))
                        // La phrase sous le nom: transport · adresse · profil.
                        BasicText(
                            text = serverSummary(config),
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                    BasicText(
                        text = "Edit",
                        style = theme.body.copy(color = theme.accent),
                        modifier = Modifier.onyxClickable { onEdit(config) },
                    )
                }
            }
            OnyxRowDivider()
            OnyxRow(onClick = onAdd) {
                OnyxIcon(Symbols.Plus, tint = theme.accent, size = 17.dp)
                BasicText(text = "Add a server", style = theme.body.copy(color = theme.accent), modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * La phrase de résumé d'un serveur, mot pour mot de `ServersView.detail`.
 *
 * Le transport (« Hermes API » sur `hermesSessions`, « Hermes Dashboard » sinon), l'adresse —
 * ou « not set up » quand elle manque, l'API ou le Dashboard selon le transport — puis
 * « profile X » lorsque le profil n'est pas celui par défaut; le tout séparé par « · ».
 */
private fun serverSummary(config: ServerConfig): String {
    val api = config.transport == Transport.HERMES_SESSIONS
    val address = (if (api) config.baseUrl else config.serveBaseUrl).trim()
    val parts = mutableListOf(
        if (api) OnyxLocalization["Hermes API"] else OnyxLocalization["Hermes Dashboard"],
        address.ifEmpty { OnyxLocalization["not set up"] },
    )
    // `effectiveProfile` écarte déjà `""` et `"default"`, comme le `detail`.
    config.effectiveProfile?.let { parts += OnyxLocalization.format("profile %1\$s", it) }
    return parts.joinToString(" · ")
}

/** Le formulaire d'un serveur: adresse, clé, profil, transport. */
@Composable
private fun ServerEditor(
    config: ServerConfig,
    probeMessage: String?,
    isTesting: Boolean,
    onChange: ((ServerConfig) -> ServerConfig) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val transports = listOf(Transport.CHAT_COMPLETIONS, Transport.HERMES_SESSIONS)

    OnyxFormSection(
        header = "Server",
        footer = "A Hermes gateway scopes its URL by profile; an OpenAI-compatible server (LM Studio, llama.cpp, OpenRouter…) does not.",
    ) {
        OnyxFieldRow(
            label = "Name",
            value = config.name,
            placeholder = "Onyx",
            onValueChange = { value -> onChange { it.copy(name = value) } },
        )
        OnyxRowDivider()
        OnyxFieldRow(
            label = "Address",
            value = config.baseUrl,
            placeholder = "http://192.168.1.10:1234/v1",
            onValueChange = { value -> onChange { it.copy(baseUrl = value) } },
        )
        OnyxRowDivider()
        OnyxFieldRow(
            label = "API key",
            value = config.apiKey,
            placeholder = "None",
            secret = true,
            onValueChange = { value -> onChange { it.copy(apiKey = value) } },
        )
        OnyxRowDivider()
        OnyxFieldRow(
            label = "Hermes profile",
            value = config.hermesProfile,
            placeholder = "Default",
            onValueChange = { value -> onChange { it.copy(hermesProfile = value) } },
        )
        OnyxRowDivider()
        OnyxRow {
            BasicText(text = "Transport", style = theme.body.copy(color = theme.primaryText), modifier = Modifier.weight(1f))
        }
    }

    OnyxFormSection {
        OnyxRow {
            OnyxSegmented(
                options = listOf("API", "Gateway"),
                selectedIndex = transports.indexOf(config.transport).coerceAtLeast(0),
                onSelect = { index -> onChange { it.copy(transport = transports[index]) } },
            )
        }
    }

    OnyxFormSection(
        header = "Dashboard",
        footer = "Hermes's own Dashboard (hermes serve) opens the gateway, the files, the context and memory files, the cron jobs and the kanban board.",
    ) {
        OnyxFieldRow(
            label = "Address",
            value = config.serveBaseUrl,
            placeholder = "http://192.168.1.10:8080",
            onValueChange = { value -> onChange { it.copy(serveBaseUrl = value) } },
        )
        OnyxRowDivider()
        OnyxFieldRow(
            label = "Username",
            value = config.serveUsername,
            placeholder = "you",
            onValueChange = { value -> onChange { it.copy(serveUsername = value) } },
        )
        OnyxRowDivider()
        OnyxFieldRow(
            label = "Password",
            value = config.servePassword,
            placeholder = "None",
            secret = true,
            onValueChange = { value -> onChange { it.copy(servePassword = value) } },
        )
    }

    OnyxFormSection {
        OnyxRow(onClick = onTest) {
            OnyxIcon(Symbols.WifiExclamation, tint = theme.accent, size = 17.dp)
            BasicText(
                text = if (isTesting) "Testing…" else "Test the connection",
                style = theme.body.copy(color = theme.accent),
                modifier = Modifier.weight(1f),
            )
        }
        probeMessage?.let { message ->
            OnyxRowDivider()
            OnyxRow {
                BasicText(text = message, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
            }
        }
    }

    OnyxFormSection {
        OnyxRow(onClick = onSave) {
            BasicText(
                text = "Save",
                style = theme.body.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
        }
        OnyxRowDivider()
        OnyxRow(onClick = onCancel) {
            BasicText(text = "Cancel", style = theme.body.copy(color = theme.secondaryText), modifier = Modifier.weight(1f))
        }
        OnyxRowDivider()
        OnyxRow(onClick = onDelete) {
            BasicText(
                text = "Delete this server",
                style = theme.body.copy(color = theme.error),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * La page Apparence, port fidèle d'`AppearanceSettings`: le nom de
 * l'assistant, le thème replié sur son résumé, les couleurs du thème, l'icône de l'app, puis les
 * habillages Android Auto (l'équivalent CarPlay de la section ).
 */
@Composable
fun AppearanceScreen(
    theme: OnyxTheme,
    appearance: AppearanceMode,
    themeColors: Map<String, ThemeColors>,
    displayName: String,
    autoCards: AutoCardLook,
    autoVoice: AutoVoiceLook,
    botFaces: Boolean,
    botPictures: Boolean,
    botColors: Boolean,
    linkPreviews: Boolean,
    chatTextSize: Int,
    voiceOrb: VoiceOrbChoice,
    orbLight: OrbLight,
    onPickTheme: (OnyxTheme) -> Unit,
    onPickAppearance: (AppearanceMode) -> Unit,
    onPickThemeColors: (Map<String, ThemeColors>) -> Unit,
    onDisplayName: (String) -> Unit,
    onPickAutoCards: (AutoCardLook) -> Unit,
    onPickAutoVoice: (AutoVoiceLook) -> Unit,
    onBotFaces: (Boolean) -> Unit,
    onBotPictures: (Boolean) -> Unit,
    onBotColors: (Boolean) -> Unit,
    onLinkPreviews: (Boolean) -> Unit,
    onChatTextSize: (Int) -> Unit,
    onPickVoiceOrb: (VoiceOrbChoice) -> Unit,
    onPickOrbLight: (OrbLight) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolved = LocalOnyxTheme.current
    val modes = AppearanceMode.entries
    var showThemes by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }

    // Les couleurs du thème: l'accent, et la bulle — ou la ligne d'invite des thèmes CRT,
    // qui n'ont pas de bulle (`promptPrefix != nil` ).
    val picked = themeColors[theme.id]
    val accent = resolved.accent
    val bubble = if (resolved.promptPrefix != null) resolved.promptColor else resolved.userBubbleColor

    fun pick(transform: (ThemeColors) -> ThemeColors) {
        onPickThemeColors(themeColors + (theme.id to transform(themeColors[theme.id] ?: ThemeColors())))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(resolved.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Appearance", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // `NameSettings`: ce que l'en-tête et la zone de saisie appellent l'assistant.
            OnyxFormSection(
                header = "Name",
                footer = "Shown in the header and the message box. Spoken phrases and the Home Screen icon keep the app's real name.",
            ) {
                OnyxRow {
                    OnyxTextField(
                        value = displayName,
                        onValueChange = onDisplayName,
                        placeholder = "Onyx",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            OnyxFormSection(
                header = "Appearance",
                footer = if (showColors) {
                    "Each theme keeps its own colors. Text on your bubbles and on accent buttons turns light or dark to stay readable."
                } else {
                    "“Chat text size” sets the conversation larger or smaller than the rest of the phone's text. It still follows the phone's own text size from there."
                },
            ) {
                // `DisclosureGroup` « Theme »: replié sur « Messages, light » et un chevron.
                OnyxDisclosureSummary(
                    title = "Theme",
                    expanded = showThemes,
                    onClick = { showThemes = !showThemes },
                ) {
                    BasicText(
                        text = "${theme.label}, ${appearance.label.lowercase()}",
                        style = resolved.body.copy(color = resolved.secondaryText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showThemes) {
                    // `Picker("Appearance")` segmenté, insets 8/16/8/16, puis la liste des thèmes.
                    OnyxRow {
                        OnyxSegmented(
                            options = modes.map { it.label },
                            selectedIndex = modes.indexOf(appearance).coerceAtLeast(0),
                            onSelect = { index -> onPickAppearance(modes[index]) },
                        )
                    }
                    OnyxTheme.entries.forEach { entry ->
                        OnyxRowDivider()
                        ThemeRow(
                            entry = entry,
                            selected = entry == theme,
                            colors = themeColors[entry.id],
                            onClick = { onPickTheme(entry) },
                        )
                    }
                }

                OnyxRowDivider()

                // `DisclosureGroup` « Messages colors »: deux pastilles dans le résumé.
                OnyxDisclosureSummary(
                    title = "${theme.label} colors",
                    expanded = showColors,
                    onClick = { showColors = !showColors },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(accent, CircleShape),
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(bubble, CircleShape),
                        )
                    }
                }
                if (showColors) {
                    OnyxRowDivider()
                    ColorPickerRow(
                        label = "Accent",
                        color = accent,
                        onPick = { color -> pick { it.copy(accent = ThemeColors.RGB(color)) } },
                    )
                    OnyxRowDivider()
                    ColorPickerRow(
                        label = if (resolved.promptPrefix != null) "Your messages" else "Your bubbles",
                        color = bubble,
                        onPick = { color -> pick { it.copy(bubble = ThemeColors.RGB(color)) } },
                    )
                    if (!(picked?.isEmpty ?: true)) {
                        OnyxRowDivider()
                        OnyxRow(onClick = { onPickThemeColors(themeColors - theme.id) }) {
                            BasicText(
                                text = "Use ${theme.label}'s own colors",
                                style = resolved.body.copy(color = resolved.accent),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                OnyxRowDivider()
                // `Toggle(isOn: $settings.botFaces)`: chacun avec son explication sous le titre.
                BlurbToggleRow(
                    title = "Bot faces",
                    blurb = "Off, each bot, Onyx too, is its initial on a circle of its color, and voice mode shows the Bubble orb in place of a face. The bots work the same either way.",
                    checked = botFaces,
                    onCheckedChange = onBotFaces,
                )
                OnyxRowDivider()
                BlurbToggleRow(
                    title = "Pictures from Hermes",
                    blurb = "A bot with a picture set in Hermes Desktop shows that picture instead of its face.",
                    checked = botPictures,
                    onCheckedChange = onBotPictures,
                )
                OnyxRowDivider()
                BlurbToggleRow(
                    title = "Bot colors in their chats",
                    blurb = "Your bubbles and the buttons in a bot's chat take its color, and Onyx's own chats take its face's if it has one. Group chats keep the theme's.",
                    checked = botColors,
                    onCheckedChange = onBotColors,
                )
                OnyxRowDivider()
                ChatTextSizeRow(steps = chatTextSize, onPick = onChatTextSize)

                OnyxRowDivider()
                // `Toggle(isOn: $settings.linkPreviews)`: éteint par défaut, avec la réserve de
                // vie privée sous le titre (le site voit la visite).
                BlurbToggleRow(
                    title = "Link previews",
                    blurb = "A card with the page's title and picture under a message with a link. " +
                        "To make it, Onyx opens the page itself, so the site sees the visit. " +
                        "Off, Onyx only talks to your own servers.",
                    checked = linkPreviews,
                    onCheckedChange = onLinkPreviews,
                )

                OnyxRowDivider()
                // La rangée et la grille vivent dans la même carte que le reste, comme.
                AppIconSettings(embedded = true)

                OnyxRowDivider()
                // `VoiceOrbSettings`: l'orbe replié sur son résumé, la grille, puis « Orb light ».
                VoiceOrbSettingsRows(
                    orb = voiceOrb,
                    light = orbLight,
                    onPickOrb = onPickVoiceOrb,
                    onPickLight = onPickOrbLight,
                )
            }

            OnyxFormSection(header = "Android Auto") {
                AutoCardSettings(current = autoCards, onPick = onPickAutoCards)
                OnyxRowDivider()
                AutoVoiceSettings(current = autoVoice, onPick = onPickAutoVoice)
            }
        }
    }
}

/** Une ligne de thème: son aperçu dans l'apparence active, son nom, sa phrase. */
@Composable
private fun ThemeRow(
    entry: OnyxTheme,
    selected: Boolean,
    colors: ThemeColors?,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow(onClick = onClick) {
        ThemeSwatch(theme = ResolvedTheme.resolve(entry, theme.dark, theme.fonts, colors))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = entry.label, style = theme.body.copy(color = theme.primaryText))
            BasicText(text = entry.blurb, style = theme.footnote.copy(color = theme.secondaryText))
        }
        if (selected) {
            OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
        }
    }
}

/** Les deux capsules du `ThemeSwatch`: surface à gauche, bulle de l'utilisateur à droite. */
@Composable
private fun ThemeSwatch(theme: ResolvedTheme) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 40.dp)
            .background(theme.pageColor, RoundedCornerShape(8.dp))
            .border(1.dp, theme.quaternaryText, RoundedCornerShape(8.dp))
            .padding(7.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 8.dp)
                    .background(theme.surfaceColor, RoundedCornerShape(50)),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 8.dp)
                        .background(theme.userBubbleColor, RoundedCornerShape(50)),
                )
            }
        }
    }
}

/** Une rangée de `ColorPicker`: le libellé, puis la pastille qui ouvre le sélecteur. */
@Composable
private fun ColorPickerRow(label: String, color: Color, onPick: (Color) -> Unit) {
    val theme = LocalOnyxTheme.current
    var picking by remember { mutableStateOf(false) }
    OnyxRow(onClick = { picking = true }) {
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.primaryText),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color)
                .border(0.5.dp, theme.quaternaryText, RoundedCornerShape(7.dp)),
        )
    }
    if (picking) {
        ColorPickerDialog(
            title = label,
            initial = color,
            onDismiss = { picking = false },
            onPick = { chosen ->
                picking = false
                onPick(chosen)
            },
        )
    }
}

/** Le sélecteur de couleur: teinte, saturation et valeur, à la place de la feuille. */
@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Color,
    onDismiss: () -> Unit,
    onPick: (Color) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val start = remember(initial) {
        FloatArray(3).also { hsv ->
            android.graphics.Color.colorToHSV(
                android.graphics.Color.argb(
                    255,
                    (initial.red * 255f).roundToInt(),
                    (initial.green * 255f).roundToInt(),
                    (initial.blue * 255f).roundToInt(),
                ),
                hsv,
            )
        }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val chosen = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.formSurfaceColor, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(text = title, style = theme.headline.copy(color = theme.primaryText))
            SaturationValueField(
                hue = hue,
                saturation = saturation,
                value = value,
                onChange = { s, v ->
                    saturation = s
                    value = v
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )
            HueField(
                hue = hue,
                onChange = { hue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(chosen)
                        .border(0.5.dp, theme.quaternaryText, RoundedCornerShape(7.dp)),
                )
                Spacer(modifier = Modifier.weight(1f))
                BasicText(
                    text = "Cancel",
                    style = theme.body.copy(color = theme.accent),
                    modifier = Modifier.onyxClickable(onClick = onDismiss),
                )
                BasicText(
                    text = "Done",
                    style = theme.body.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.onyxClickable { onPick(chosen) },
                )
            }
        }
    }
}

/** Le carré saturation/valeur: blanc à gauche, la teinte à droite, noir vers le bas. */
@Composable
private fun SaturationValueField(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.lastOrNull() ?: continue
                        if (change.pressed) {
                            onChange(
                                (change.position.x / size.width).coerceIn(0f, 1f),
                                (1f - change.position.y / size.height).coerceIn(0f, 1f),
                            )
                            change.consume()
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
        )
        Box(
            modifier = Modifier
                .offset(x = maxWidth * saturation - 6.dp, y = maxHeight * (1f - value) - 6.dp)
                .size(12.dp)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

/** La barre de teinte: les six secteurs du cercle chromatique. */
@Composable
private fun HueField(hue: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val stops = (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.horizontalGradient(stops))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.lastOrNull() ?: continue
                        if (change.pressed) {
                            onChange(((change.position.x / size.width) * 360f).coerceIn(0f, 360f))
                            change.consume()
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .offset(x = maxWidth * (hue / 360f) - 3.dp)
                .width(6.dp)
                .fillMaxHeight()
                .border(1.5.dp, Color.White, RoundedCornerShape(3.dp)),
        )
    }
}
