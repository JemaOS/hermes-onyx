package com.onyx.app.ui.settings

import com.onyx.app.ui.design.OnyxSpace

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.platform.LocalContext
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
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxEdge
import com.onyx.app.ui.design.SocleLargeHeader
import com.onyx.app.ui.design.OnyxActionSheet
import com.onyx.app.ui.design.Anatomy
import com.onyx.app.ui.design.OnyxAnatomyDivider
import com.onyx.app.ui.design.OnyxDisclosureSummary
import com.onyx.app.ui.design.OnyxFieldRow
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxIconTile
import com.onyx.app.ui.design.OnyxInfoRow
import com.onyx.app.ui.design.OnyxLargePageHeader
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.haloBackdrop
import com.onyx.app.ui.design.OnyxSegmented
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.OnyxValueRow
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.OnyxInkOn
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.Lucide
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.hostActivity
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.AppearanceMode
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.theme.ThemeColors
import com.onyx.app.ui.voice.OrbLight
import com.onyx.app.ui.voice.VoiceOrbChoice
import kotlin.math.roundToInt

/**
 * Réglages, première page : la connexion et le chemin vers l'apparence.
 *
 * Port de `SettingsView` : un formulaire groupé, une page par sujet long, et un bouton
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
    onOpenTools: (() -> Unit)? = null,
    /** « Capacités » : les compétences, les jeux d'outils et les serveurs MCP à la même page. */
    onOpenCapabilities: (() -> Unit)? = null,
    /** « Aperçus » : le coût, les jetons et le cache de ce profil, sur une période. */
    onOpenInsights: (() -> Unit)? = null,
    onOpenPhoneTools: (() -> Unit)? = null,
    /** Ouvre les réglages d'appel (sonnerie pour une décision, heures de tranquillité). */
    onOpenCalls: (() -> Unit)? = null,
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
    /**
     * Journaux — **toujours accessible**, contrairement à la Passerelle : le journal vocal
     * (Journaux → Voix) est **local à l'appareil** et ne dépend d'aucun identifiant de Dashboard.
     */
    onOpenLogs: (() -> Unit)? = null,
    onOpenConnectionDetails: (() -> Unit)? = null,
    /** « Erase everything » : la confirmation est demandée avant que l'hôte ne le fasse. */
    onEraseEverything: (() -> Unit)? = null,
    /** La fenêtre de contexte du serveur actif (`MetricsSettings`). */
    onSetContextWindow: (String, Int) -> Unit = { _, _ -> },
    /**
     * Le contrôle du volet (`sidebar.left`) : les Réglages se présentent **par-dessus** les deux
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
    val gatewayProbe by viewModel.gatewayProbe.collectAsStateWithLifecycle()
    val gatewayVerifying by viewModel.gatewayVerifying.collectAsStateWithLifecycle()
    val currentTheme by viewModel.theme.collectAsStateWithLifecycle()
    val speechLocale by viewModel.speechLocale.collectAsStateWithLifecycle()
    val handsFreeByDefault by viewModel.handsFreeByDefault.collectAsStateWithLifecycle()
    val returnSends by viewModel.returnSends.collectAsStateWithLifecycle()
    val suggestFollowUps by viewModel.suggestFollowUps.collectAsStateWithLifecycle()
    val notifyInBackground by viewModel.notifyInBackground.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    var showLanguages by remember { mutableStateOf(false) }
    // La langue de l'interface : son choix ouvre la feuille, et son application recrée l'activité.
    var showLanguage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val speechLocales = SettingsRepository.SPEECH_LOCALES
    // Le serveur actif : ses métriques et la page « Connection details » l'ouvrent.
    val activeServer = servers.firstOrNull { it.id == activeId }
    var confirmingErase by remember { mutableStateOf(false) }

    val editing = draft

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            // La même matière que le fil et la liste : le halo du haut, derrière les sections.
            .haloBackdrop(theme)
            // La zone système est prise par la racine : la dernière rangée du corps ne peut plus
            // être enterrée sous la barre de navigation, et l'encart est lu à l'exécution (24 dp en
            // gestes, 48 dp à trois boutons) — jamais une valeur en dur qui ne vaut que pour un écran.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // L'en-tête de la référence : la **grande flèche de retour**, puis le titre « Réglages »
            // **en très grand** et aligné à gauche — aucune autre chrome au-dessus. Le « Done »
            // d'avant devient la flèche (c'est la même sortie, `onDone`) ; le contrôle du volet
            // garde sa place à droite, pour que replier la liste d'ici continue de fonctionner.
            SocleLargeHeader(
                title = "Settings",
                subtitle = "Apparence, agent, compte",
                onBack = onDone,
                actions = sidebarControl,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Le halo est **peint par la page** (le modificateur `haloBackdrop` de la racine,
            // au-dessus) : il ne consomme aucune hauteur. Il était ici un bloc de 300 dp, et ces
            // 300 dp étaient un **trou** au milieu des Réglages — le contenu défilé se coupait net
            // au bord de la zone qui restait (relevé sur la vidéo du propriétaire).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = OnyxSocle.Loose),
            ) {
                // La carte de la passerelle, en tête : vignette teintée, adresse, état et « Vérifier ».
                val gwServer = servers.firstOrNull { it.id == activeId }
                val gwAddress = (gwServer?.baseUrl?.ifBlank { gwServer.serveBaseUrl } ?: "")
                    .trim()
                    .ifEmpty { OnyxLocalization["not set up"] }
                val gwStatus: String
                val gwTone: GatewayTone
                when {
                    gatewayVerifying -> {
                        gwStatus = OnyxLocalization["Checking"]
                        gwTone = GatewayTone.IDLE
                    }
                    gatewayProbe != null -> {
                        gwStatus = gatewayProbe!!.message
                        gwTone = if (gatewayProbe!!.ok) GatewayTone.OK else GatewayTone.BAD
                    }
                    gwServer == null -> {
                        gwStatus = OnyxLocalization["No server yet"]
                        gwTone = GatewayTone.IDLE
                    }
                    gwServer.canAdminister -> {
                        gwStatus = OnyxLocalization["Connected."]
                        gwTone = GatewayTone.OK
                    }
                    else -> {
                        gwStatus = OnyxLocalization["configured"]
                        gwTone = GatewayTone.IDLE
                    }
                }
                GatewayCard(
                    name = gwServer?.displayName ?: OnyxLocalization["Servers"],
                    address = gwAddress,
                    status = gwStatus,
                    tone = gwTone,
                    verifying = gatewayVerifying,
                    onVerify = viewModel::verifyActiveGateway,
                    // La carte est un **état**, pas un second chemin : la seule entrée vers l'écran
                    // Passerelle reste la ligne « Passerelle » de la section Compte (une destination
                    // = un point d'entrée). Aucun réglage n'est rendu inatteignable pour autant.
                    onOpen = null,
                )

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

            // La carte Apparence de la référence : l'apparence et la langue de l'interface.
            OnyxFormSection(header = "Appearance") {
                OnyxValueRow(
                    label = "Appearance",
                    value = currentTheme.label,
                    symbol = Symbols.PaintbrushOutline,
                    onClick = onOpenAppearance,
                )
                OnyxAnatomyDivider()
                OnyxValueRow(
                    label = "Language",
                    value = OnyxLanguage.label(language),
                    symbol = Symbols.Globe,
                    onClick = { showLanguage = true },
                )
            }

            // La section Chat de la référence : le modèle qui répond, puis l'écriture.
            // Le pied vient de `ComposerSettings` : il suit la touche Entrée, mot pour mot.
            // « Suggest follow-up questions », logé ici côté Android, est documenté par la
            // phrase de `VoiceSettings` qui le décrit.
            OnyxFormSection(
                header = "Chat",
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
                OnyxValueRow(
                    label = "Model",
                    value = servers.firstOrNull { it.id == activeId }?.effectiveModel?.ifEmpty { OnyxLocalization["Server default"] }
                        ?: OnyxLocalization["Server default"],
                    localizeValue = false,
                    symbol = Symbols.BoltOutline,
                    onClick = onOpenModels,
                )
                OnyxAnatomyDivider()
                OnyxToggleRow(
                    label = "Return key sends",
                    checked = returnSends,
                    onCheckedChange = viewModel::setReturnSends,
                    symbol = Symbols.Keyboard,
                )
                OnyxAnatomyDivider()
                OnyxToggleRow(
                    label = "Suggest follow-up questions",
                    checked = suggestFollowUps,
                    onCheckedChange = viewModel::setSuggestFollowUps,
                    symbol = Lucide.Sparkles,
                )
            }

            OnyxFormSection(header = "Voice") {
                OnyxValueRow(
                    label = "Voice",
                    value = speechLocale,
                    symbol = Symbols.Waveform,
                    onClick = onOpenVoice,
                )
                // Les journaux, **sans identifiant de Dashboard** : le journal vocal (Journaux →
                // Voix) est **local à l'appareil** — démarrage du moteur, permission, trames
                // reçues, erreur exacte — et c'est le seul endroit où se lit un micro qui ne
                // marche pas. Il n'était atteignable que par Réglages → Agent → Passerelle, dont
                // la ligne est fermée sans session du Dashboard : quelqu'un dont le micro ne
                // marche pas ne pouvait donc pas lire ce qui casse. Cette ligne-ci n'est jamais
                // fermée — elle ne dépend d'aucune configuration.
                onOpenLogs?.let {
                    OnyxAnatomyDivider()
                    OnyxValueRow(
                        label = "Logs",
                        value = "",
                        symbol = Symbols.Doc,
                        onClick = it,
                    )
                }
            }

            // La section Notifications de la référence : l'alerte d'arrière-plan.
            onOpenNotifications?.let { openNotifications ->
                OnyxFormSection(header = "Notifications") {
                    OnyxValueRow(
                        label = "Notifications",
                        value = if (notifyInBackground) "On" else "Off",
                        symbol = Symbols.Bell,
                        onClick = openNotifications,
                    )
                }
            }

            // La section Téléphone de la référence : les outils du téléphone, et les appels.
            if (onOpenPhoneTools != null || onOpenCalls != null) {
                OnyxFormSection(header = "Phone") {
                    onOpenPhoneTools?.let { openPhoneTools ->
                        AgentRow("Phone tools", Symbols.Phone, true, openPhoneTools)
                    }
                    onOpenCalls?.let { openCalls ->
                        // Le filet **en retrait** de l'anatomie des Réglages, pas le filet bord à bord.
                        if (onOpenPhoneTools != null) OnyxAnatomyDivider()
                        AgentRow("Calls", Symbols.PhoneOutline, true, openCalls)
                    }
                }
            }

            OnyxFormSection(
                header = "Account",
                footer = "Context, memory, files and the gateway are edited in place on the Hermes host, through its Dashboard.",
            ) {
                val dashboard = servers.firstOrNull { it.id == activeId }?.canAdminister == true
                val gateway = servers.firstOrNull { it.id == activeId }?.canReachGateway == true
                // Chaque ligne n'apparaît que si son écran existe : rien ne promet dans le vide.
                val rows = listOfNotNull(
                    onOpenCapabilities?.let { AgentEntry("Capabilities", Symbols.SquareStack3dUp, dashboard, it) },
                    onOpenInsights?.let { AgentEntry("Insights", Symbols.ChartBar, dashboard, it) },
                    // La ligne « Compétences » a été **retirée** : elle menait au même écran que
                    // « Capacités », juste au-dessus. L'éditeur d'une compétence vit désormais
                    // **dans** cet écran (famille « Compétences »).
                    onOpenTools?.let { AgentEntry("Tools", Symbols.WrenchAndScrewdriver, dashboard, it) },
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
                    if (index > 0) OnyxAnatomyDivider()
                    AgentRow(entry.label, entry.symbol, entry.enabled, entry.onClick)
                }
            }

            // `ConnectionDetailsView` de la référence : toutes les adresses, clés et identifiants sur une
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

            // `MetricsSettings` : la fenêtre de contexte, celle de l'anneau « ctx % ».
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
                if (onOpenLock != null && onOpenConnect != null) OnyxAnatomyDivider()
                onOpenConnect?.let {
                    OnyxValueRow(label = "Set up another device", value = "", symbol = Symbols.QrCode, onClick = it)
                }
            }

            OnyxFormSection(header = "About", footer = "Onyx for Android, a client for your own Hermes server.") {
                onOpenAcknowledgements?.let {
                    OnyxValueRow(label = "Acknowledgements", value = "", symbol = Symbols.Doc, onClick = it)
                    OnyxAnatomyDivider()
                }
                onOpenWhatsNew?.let {
                    OnyxValueRow(label = "What's New", value = "", symbol = Lucide.Sparkles, onClick = it)
                    OnyxAnatomyDivider()
                }
                onOpenTipJar?.let {
                    OnyxValueRow(label = "Support Onyx", value = "", symbol = Symbols.Heart, onClick = it)
                    OnyxAnatomyDivider()
                }
                onOpenServers?.let {
                    OnyxValueRow(
                        label = "Servers",
                        value = "${servers.size}",
                        symbol = Symbols.ServerRack,
                        onClick = it,
                    )
                    OnyxAnatomyDivider()
                }
                // Le pied de la référence : **la version** puis **l'état de la passerelle**, chacun
                // en titre + valeur dessous — « Vérification… » pendant que la carte teste.
                OnyxInfoRow(
                    title = "App version",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    valueLocalize = false,
                )
                OnyxAnatomyDivider()
                OnyxInfoRow(
                    title = "Gateway",
                    value = if (gatewayVerifying) OnyxLocalization["Verifying…"] else gwStatus,
                    valueLocalize = false,
                )
            }

            // `ResetSettings` : retour à une installation neuve. La ligne destructive est la
            // dernière du formulaire, comme dans la référence.
            onEraseEverything?.let { erase ->
                OnyxFormSection(
                    footer = "Deletes every conversation and attachment on this device, forgets " +
                        "all servers, keys and passwords, and returns the settings to their " +
                        "defaults. Nothing on your servers is touched.",
                ) {
                    OnyxRow(onClick = { confirmingErase = true }) {
                        BasicText(
                            text = "Erase everything…",
                            style = theme.body.copy(color = OnyxInkOn(theme.error, theme.formSurfaceColor)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            }
        }
    }

    // La confirmation de la référence : un titre visible, « Erase everything » destructeur, et « Cancel ».
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

    // Le choix de la langue : la feuille de l'app, trois entrées, et le droit de revenir à
    // « Système ». Le changement s'applique **tout de suite** : la langue bascule en mémoire, puis
    // l'activité est recréée pour que l'écran entier se redessine — y compris les libellés déjà
    // calculés en dehors du wrapper de texte (les en-têtes de section, les dates).
    if (showLanguage) {
        OnyxActionSheet(
            title = "Language",
            message = "Onyx follows the phone's language unless you pick one. Screens, sheets, " +
                "menus and notifications follow it at once.",
            items = OnyxLanguage.choices.map { choice ->
                MenuItem(
                    label = OnyxLanguage.label(choice),
                    // LOT anatomie-des-contenus : la **tuile** de la rangée est l'icône du sujet (le
                    // globe), et la **coche** est le contrôle de droite (`checked`) — c'était la coche
                    // en guise de tuile, dans une ligne de texte centrée sans rangée ni filet.
                    symbol = Lucide.Globe,
                    checked = choice == language,
                    onSelect = {
                        showLanguage = false
                        // La langue bascule en mémoire **avant** la recréation : l'écran qui
                        // revient est déjà dans la nouvelle langue, sans clignotement.
                        OnyxLanguage.apply(choice)
                        viewModel.setLanguage(choice)
                        context.hostActivity()?.recreate()
                    },
                )
            },
            onDismiss = { showLanguage = false },
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

/** Une ligne de la section Compte : **vignette d'icône grise**, libellé, et le contrôle à droite —
 * le chevron, ou un cadenas quand le transport courant ne permet pas la ligne. */
@Composable
private fun AgentRow(
    label: String,
    symbol: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Anatomy.NavRowMinHeight)
            .then(if (enabled) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .padding(horizontal = Anatomy.RowPaddingH, vertical = Anatomy.RowPaddingV),
        horizontalArrangement = Arrangement.spacedBy(Anatomy.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnyxIconTile(symbol = symbol, tint = if (enabled) null else theme.tertiaryText)
        BasicText(
            text = label,
            style = theme.body.copy(color = if (enabled) theme.primaryText else theme.tertiaryText),
            modifier = Modifier.weight(1f),
        )
        if (!enabled) {
            OnyxIcon(Symbols.LockOutline, tint = theme.tertiaryText, size = 13.dp)
        } else {
            OnyxIcon(
                Symbols.ChevronRight,
                tint = OnyxInkOn(theme.tertiaryText, theme.formSurfaceColor, OnyxContrast.ICON_MIN),
                size = Anatomy.Chevron,
            )
        }
    }
}

/** La liste des serveurs : celui qui répond, et les autres. */
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
            // Aucun serveur : la phrase et le chemin pour en poser un, plutôt qu'une section vide.
            OnyxRow {
                BasicText(
                    text = "No server yet",
                    style = theme.body.copy(color = theme.secondaryText),
                    modifier = Modifier.weight(1f),
                )
            }
            OnyxAnatomyDivider()
            OnyxRow(onClick = onAdd) {
                OnyxIcon(Symbols.Plus, tint = theme.accent, size = 17.dp)
                BasicText(
                    text = "Set up a server…",
                    style = theme.body.copy(color = OnyxInkOn(theme.accent, theme.formSurfaceColor)),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            servers.forEachIndexed { index, config ->
                if (index > 0) OnyxAnatomyDivider()
                OnyxRow(onClick = { onSelect(config) }) {
                    OnyxIcon(
                        symbol = if (config.id == activeId) Symbols.CheckmarkCircle else Symbols.Circle,
                        tint = if (config.id == activeId) theme.accent else theme.tertiaryText,
                        size = 20.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(text = config.displayName, localize = false, style = theme.body.copy(color = theme.primaryText))
                        // La phrase de la référence sous le nom : transport · adresse · profil.
                        BasicText(
                            text = serverSummary(config),
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                    BasicText(
                        text = "Edit",
                        style = theme.body.copy(color = OnyxInkOn(theme.accent, theme.formSurfaceColor)),
                        modifier = Modifier.onyxClickable { onEdit(config) },
                    )
                }
            }
            OnyxAnatomyDivider()
            OnyxRow(onClick = onAdd) {
                OnyxIcon(Symbols.Plus, tint = theme.accent, size = 17.dp)
                BasicText(
                    text = "Add a server",
                    style = theme.body.copy(color = OnyxInkOn(theme.accent, theme.formSurfaceColor)),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * La phrase de résumé d'un serveur, mot pour mot de `ServersView.detail` côté référence.
 *
 * Le transport (« Hermes API » sur `hermesSessions`, « Hermes Dashboard » sinon), l'adresse —
 * ou « not set up » quand elle manque, l'API ou le Dashboard selon le transport — puis
 * « profile X » lorsque le profil n'est pas celui par défaut ; le tout séparé par « · ».
 */
private fun serverSummary(config: ServerConfig): String {
    val api = config.transport == Transport.HERMES_SESSIONS
    val address = (if (api) config.baseUrl else config.serveBaseUrl).trim()
    val parts = mutableListOf(
        if (api) OnyxLocalization["Hermes API"] else OnyxLocalization["Hermes Dashboard"],
        address.ifEmpty { OnyxLocalization["not set up"] },
    )
    // `effectiveProfile` écarte déjà `""` et `"default"`, comme le `detail` de la référence.
    config.effectiveProfile?.let { parts += OnyxLocalization.format("profile %1\$s", it) }
    return parts.joinToString(" · ")
}

/** Le formulaire d'un serveur : adresse, clé, profil, transport. */
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
        OnyxAnatomyDivider()
        OnyxFieldRow(
            label = "Address",
            value = config.baseUrl,
            placeholder = "http://192.168.1.10:1234/v1",
            onValueChange = { value -> onChange { it.copy(baseUrl = value) } },
        )
        OnyxAnatomyDivider()
        OnyxFieldRow(
            label = "API key",
            value = config.apiKey,
            placeholder = "None",
            secret = true,
            onValueChange = { value -> onChange { it.copy(apiKey = value) } },
        )
        OnyxAnatomyDivider()
        OnyxFieldRow(
            label = "Hermes profile",
            value = config.hermesProfile,
            placeholder = "Default",
            onValueChange = { value -> onChange { it.copy(hermesProfile = value) } },
        )
        OnyxAnatomyDivider()
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
        OnyxAnatomyDivider()
        OnyxFieldRow(
            label = "Username",
            value = config.serveUsername,
            placeholder = "you",
            onValueChange = { value -> onChange { it.copy(serveUsername = value) } },
        )
        OnyxAnatomyDivider()
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
                style = theme.body.copy(color = OnyxInkOn(theme.accent, theme.formSurfaceColor)),
                modifier = Modifier.weight(1f),
            )
        }
        probeMessage?.let { message ->
            OnyxAnatomyDivider()
            OnyxRow {
                BasicText(text = message, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
            }
        }
    }

    OnyxFormSection {
        OnyxRow(onClick = onSave) {
            BasicText(
                text = "Save",
                style = theme.body.copy(
                    color = OnyxInkOn(theme.accent, theme.formSurfaceColor),
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        OnyxAnatomyDivider()
        OnyxRow(onClick = onCancel) {
            BasicText(text = "Cancel", style = theme.body.copy(color = theme.secondaryText), modifier = Modifier.weight(1f))
        }
        OnyxAnatomyDivider()
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
 * La page Apparence, port fidèle d'`AppearanceSettings` (SettingsSections) : le nom de
 * l'assistant, le thème replié sur son résumé, les couleurs du thème, l'icône de l'app, puis les
 * habillages Android Auto (l'équivalent CarPlay de la section référence).
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
    /** « Noir pur » : les fonds au noir absolu en apparence sombre (OLED). */
    pureBlack: Boolean,
    /** « Halo » : la lumière colorée douce derrière le haut de chaque écran. */
    halo: Boolean,
    /** « Fluide » : la couverture animée des cartes de conversation (variante B). */
    fluideCover: Boolean,
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
    onPureBlack: (Boolean) -> Unit,
    onHalo: (Boolean) -> Unit,
    /** « Fluide » : la couverture animée des cartes de conversation. */
    onFluideCover: (Boolean) -> Unit,
    onPickVoiceOrb: (VoiceOrbChoice) -> Unit,
    onPickOrbLight: (OrbLight) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolved = LocalOnyxTheme.current
    val modes = AppearanceMode.entries
    var showThemes by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }

    // Les couleurs du thème : l'accent, et la bulle — ou la ligne d'invite des thèmes CRT,
    // qui n'ont pas de bulle (`promptPrefix != nil` côté la référence).
    val picked = themeColors[theme.id]
    val accent = resolved.accent
    val bubble = if (resolved.promptPrefix != null) resolved.promptColor else resolved.userBubbleColor

    fun pick(transform: (ThemeColors) -> ThemeColors) {
        onPickThemeColors(themeColors + (theme.id to transform(themeColors[theme.id] ?: ThemeColors())))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(resolved.formPageColor)
            // Le halo de la référence : une lumière d'accent derrière le haut de la page, **peinte
            // par la page** — jamais un bloc dans le défilement, qui poussait tout le contenu vers
            // le bas et laissait un trou au-dessus de la première section. Éteint, rien n'est peint.
            .haloBackdrop(resolved)
            // Même règle qu'aux Réglages : le bas de la page s'écarte de la zone système.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // La page Apparence est atteinte **depuis** les Réglages : elle prend le même en-tête
            // que lui — la grande flèche de retour et le titre en très grand, rien au-dessus.
            OnyxLargePageHeader(title = "Appearance", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = OnyxSocle.Loose),
        ) {
            // `NameSettings` : ce que l'en-tête et la zone de saisie appellent l'assistant.
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
                // `DisclosureGroup` « Theme » : replié sur « Messages, light » et un chevron.
                OnyxDisclosureSummary(
                    title = "Theme",
                    expanded = showThemes,
                    onClick = { showThemes = !showThemes },
                ) {
                    BasicText(
                        text = "${theme.label}, ${appearance.label.lowercase()}",
                        style = resolved.body.copy(color = resolved.secondaryText),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showThemes) {
                    // `Picker("Appearance")` segmenté, insets 8/16/8/16, puis la liste des thèmes.
                    // Trois cases avec leur icône, comme le `Picker` de la référence : téléphone,
                    // soleil, lune.
                    OnyxRow {
                        OnyxSegmented(
                            options = modes.map { it.label },
                            selectedIndex = modes.indexOf(appearance).coerceAtLeast(0),
                            onSelect = { index -> onPickAppearance(modes[index]) },
                            icons = modes.map { mode ->
                                when (mode) {
                                    AppearanceMode.SYSTEM -> Symbols.Phone
                                    AppearanceMode.LIGHT -> Symbols.SunMax
                                    AppearanceMode.DARK -> Symbols.MoonFill
                                }
                            },
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

                // `DisclosureGroup` « Messages colors » : deux pastilles dans le résumé.
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
                    // La description et les six pastilles de la référence : le même réglage d'accent,
                    // offert en raccourci — la pastille retenue est cerclée de sa propre couleur.
                    OnyxRow {
                        BasicText(
                            text = "Buttons, links and your own messages.",
                            style = resolved.footnote.copy(color = resolved.secondaryText),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OnyxRow {
                        AccentSwatchRow(
                            current = accent,
                            dark = resolved.dark,
                            onPick = { color -> pick { it.copy(accent = ThemeColors.RGB(color)) } },
                        )
                    }
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
                                style = resolved.body.copy(
                                    color = OnyxInkOn(resolved.accent, resolved.formSurfaceColor),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // « Noir pur » et « Halo » : les deux interrupteurs de la carte Apparence de la
                // référence, chacun avec son explication sous le titre.
                OnyxRowDivider()
                BlurbToggleRow(
                    title = "Pure black",
                    blurb = "True black backgrounds in dark mode, easier on OLED screens.",
                    checked = pureBlack,
                    onCheckedChange = onPureBlack,
                )
                OnyxRowDivider()
                BlurbToggleRow(
                    title = "Halo",
                    blurb = "Soft colored light behind the top of each screen and under your messages and buttons. Off, everything is flat.",
                    checked = halo,
                    onCheckedChange = onHalo,
                )
                // « Fluide » : la couverture d'une carte de conversation devient un fluide animé —
                // les filaments de la variante B, tirés de l'identifiant de la conversation.
                OnyxRowDivider()
                BlurbToggleRow(
                    title = "Fluid covers",
                    blurb = "Conversation cards show a moving fluid, drawn from the conversation itself — the same one every time it opens. It starts breathing five seconds after you arrive, and stops as soon as you touch or scroll. Off, cards keep the flat cover.",
                    checked = fluideCover,
                    onCheckedChange = onFluideCover,
                )

                OnyxRowDivider()
                // **La ligne d'état du verre liquide.** Le propriétaire ne pouvait pas savoir quel
                // palier tournait sur son appareil — « je ne sais pas où est ton verre liquide ». Ce
                // n'est pas un réglage : c'est ce que l'app **fait**, sur cet appareil, maintenant
                // (palier de la plateforme, qualité retenue par la mesure de fluidité, et le nombre
                // de vitres qui échantillonnent réellement l'arrière-plan). Il vit ici, dans la page
                // Apparence, et nulle part à l'écran.
                GlassStateRow()

                OnyxRowDivider()
                // `Toggle(isOn: $settings.botFaces)` : chacun avec son explication sous le titre.
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
                // `Toggle(isOn: $settings.linkPreviews)` : éteint par défaut, avec la réserve de
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
                // La rangée et la grille vivent dans la même carte que le reste, comme sur la référence.
                AppIconSettings(embedded = true)

                OnyxRowDivider()
                // `VoiceOrbSettings()` : l'orbe replié sur son résumé, la grille, puis « Orb light ».
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

/** Une ligne de thème : son aperçu dans l'apparence active, son nom, sa phrase. */
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

/** Les deux capsules du `ThemeSwatch` la référence : surface à gauche, bulle de l'utilisateur à droite. */
@Composable
private fun ThemeSwatch(theme: ResolvedTheme) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 40.dp)
            .background(theme.pageColor, RoundedCornerShape(OnyxSpace.ControlCorner))
            .border(OnyxSpace.Stroke, OnyxEdge.fil(theme), RoundedCornerShape(OnyxSpace.ControlCorner))
            .padding(OnyxSocle.Tight),
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

/** Une rangée de `ColorPicker` : le libellé, puis la pastille qui ouvre le sélecteur. */
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
                .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
                .background(color)
                .border(OnyxSpace.Stroke, OnyxEdge.fil(theme), RoundedCornerShape(OnyxSpace.ControlCorner)),
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

/** Le sélecteur de couleur : teinte, saturation et valeur, à la place de la feuille de la référence. */
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
                .background(theme.formSurfaceColor, RoundedCornerShape(OnyxSpace.FieldCorner))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(OnyxSocle.Loose),
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
                horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Loose),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
                        .background(chosen)
                        .border(OnyxSpace.Stroke, OnyxEdge.fil(theme), RoundedCornerShape(OnyxSpace.ControlCorner)),
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

/** Le carré saturation/valeur : blanc à gauche, la teinte à droite, noir vers le bas. */
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
            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
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

/** La barre de teinte : les six secteurs du cercle chromatique. */
@Composable
private fun HueField(hue: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val stops = (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(OnyxSpace.ThumbnailCorner))
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
                .border(1.5.dp, Color.White, RoundedCornerShape(OnyxSpace.ControlCorner)),
        )
    }
}
