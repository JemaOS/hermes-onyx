package com.onyx.app.ui.capabilities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.McpProbe
import com.onyx.app.data.model.serve.McpServer
import com.onyx.app.data.model.serve.McpServerDraft
import com.onyx.app.data.model.serve.McpTool
import com.onyx.app.data.model.serve.mcpAuthLabel
import com.onyx.app.data.model.serve.mcpTransportLabel
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeGatewayApi
import com.onyx.app.data.net.mcpDraftProblem
import com.onyx.app.data.net.mcpFailureMessage
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.AdaptiveSegmented
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxFieldRow
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.signalInk
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch

/**
 * La fiche d'un serveur MCP : **comment** il se connecte, ce qu'il expose, et ce qu'on peut en faire.
 *
 * Rien n'est supposé : le transport, la cible, les arguments, les variables d'environnement (telles
 * que le serveur les masque) viennent de la route de liste ; les outils et la latence viennent du
 * **test réel**, et une erreur de test s'affiche comme une erreur — jamais comme une réussite.
 */
@Composable
internal fun McpServerPage(
    server: ServerConfig,
    entry: McpServer,
    probe: McpProbe?,
    testing: Boolean,
    enabledBusy: Boolean,
    onTest: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var toolsOpen by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(theme.formPageColor)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = entry.name,
                onBack = onBack,
                localizeTitle = false,
                backLabel = "Back to the servers",
                action = if (testing) "Testing…" else "Test",
                onAction = { if (!testing) onTest() },
                actionEnabled = !testing,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            problem?.let {
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(
                            Symbols.ExclamationTriangle,
                            tint = OnyxContrast.distinctOn(theme.warning, theme.formSurfaceColor, OnyxContrast.ICON_MIN),
                            size = 16.dp,
                        )
                        BasicText(
                            text = it,
                            localize = false,
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // La forme réelle du serveur : ce que la configuration dit, tel quel.
            OnyxFormSection(header = "MCP") {
                OnyxRow {
                    BasicText(
                        text = "Transport",
                        style = theme.body.copy(color = theme.primaryText),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = mcpTransportLabel(entry),
                        style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                        modifier = Modifier.weight(1.3f),
                    )
                }
                OnyxRowDivider()
                OnyxRow {
                    BasicText(
                        // Une commande locale n'a pas d'« adresse » : le mot suit la forme.
                        text = if (entry.isLocalCommand) "Command" else "Address",
                        style = theme.body.copy(color = theme.primaryText),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = entry.url ?: entry.commandLine,
                        localize = false,
                        style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                        modifier = Modifier.weight(1.3f),
                    )
                }
                if (entry.isLocalCommand) {
                    if (entry.args.isNotEmpty()) {
                        OnyxRowDivider()
                        OnyxRow {
                            BasicText(
                                text = "Arguments",
                                style = theme.body.copy(color = theme.primaryText),
                                modifier = Modifier.weight(1f),
                            )
                            BasicText(
                                text = entry.args.joinToString(" "),
                                localize = false,
                                style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                                modifier = Modifier.weight(1.3f),
                            )
                        }
                    }
                    if (entry.env.isNotEmpty()) {
                        OnyxRowDivider()
                        OnyxRow {
                            BasicText(
                                text = "Environment variables",
                                style = theme.body.copy(color = theme.primaryText),
                                modifier = Modifier.weight(1f),
                            )
                            BasicText(
                                // Le serveur masque les valeurs qui ont l'air d'un secret : on les
                                // montre comme il les a rendues, jamais reconstruites.
                                text = entry.env.entries.joinToString("\n") { (key, value) -> "$key=$value" },
                                localize = false,
                                style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                                modifier = Modifier.weight(1.3f),
                            )
                        }
                    }
                }
                mcpAuthLabel(entry)?.let { auth ->
                    OnyxRowDivider()
                    OnyxRow {
                        BasicText(
                            text = "Authentication",
                            style = theme.body.copy(color = theme.primaryText),
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = auth,
                            localize = auth == "Sign-in (OAuth)" || auth == "Header token",
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                            modifier = Modifier.weight(1.3f),
                        )
                    }
                }
                if (entry.plugin != null) {
                    OnyxRowDivider()
                    OnyxRow {
                        OnyxIcon(Symbols.LockOutline, tint = theme.tertiaryText, size = 15.dp)
                        BasicText(
                            text = OnyxLocalization.format(
                                "%1\$s is supplied by the plugin %2\$s; it can't be changed here.",
                                entry.name,
                                entry.plugin,
                            ),
                            localize = false,
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // L'interrupteur, ici comme dans la liste : un serveur apporté par un greffon ne se
            // règle pas à la main.
            OnyxFormSection(footer = "Changes apply from the next chat.") {
                CapabilityRow(
                    title = entry.name,
                    detail = null,
                    localizeTitle = false,
                    checked = entry.enabled,
                    busy = enabledBusy,
                    canToggle = entry.plugin == null,
                    onToggle = onToggleEnabled,
                )
            }

            // Le test : ce qu'il a réellement rendu, ou ce qu'il a réellement échoué à faire.
            OnyxFormSection(
                header = "Test",
                footer = if (probe == null && !testing) "Test the server to list its tools." else null,
            ) {
                when {
                    testing -> OnyxRow {
                        BasicText(
                            text = "Testing…",
                            style = theme.body.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                        )
                    }
                    probe == null -> OnyxRow {
                        ActionLine(
                            text = "Test",
                            enabled = true,
                            onClick = onTest,
                            surface = theme.formSurfaceColor,
                        )
                    }
                    else -> Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OnyxIcon(
                                symbol = if (probe.ok) Symbols.CheckmarkCircle else Symbols.XmarkCircle,
                                tint = if (probe.ok) {
                                    OnyxContrast.distinctOn(theme.success, theme.formSurfaceColor, OnyxContrast.ICON_MIN)
                                } else {
                                    OnyxContrast.distinctOn(theme.error, theme.formSurfaceColor, OnyxContrast.ICON_MIN)
                                },
                                size = 16.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    // Le mot est composé ici, en français : la phrase du serveur
                                    // (« Connected · 5 tools ») n'est pas une clé du dictionnaire.
                                    text = when {
                                        !probe.ok -> OnyxLocalization["Not connected"]
                                        probe.tools.isNotEmpty() -> OnyxLocalization.format(
                                            if (probe.tools.size == 1) "Connected · %d tool" else "Connected · %d tools",
                                            probe.tools.size,
                                        )
                                        else -> OnyxLocalization.translateParagraphs(probe.summary)
                                    },
                                    localize = false,
                                    style = theme.body.copy(
                                        color = if (probe.ok) theme.textColor else OnyxContrast.distinctOn(
                                            theme.error,
                                            theme.formSurfaceColor,
                                            OnyxContrast.TEXT_MIN,
                                        ),
                                    ),
                                )
                                probe.latencyMillis?.let { millis ->
                                    BasicText(
                                        text = OnyxLocalization.format("Took %1\$s ms", millis),
                                        localize = false,
                                        style = theme.caption.copy(color = bodyInk(theme, theme.formSurfaceColor, theme.tertiaryText)),
                                    )
                                }
                            }
                            ActionLine(
                                text = "Test again",
                                enabled = true,
                                onClick = onTest,
                                surface = theme.formSurfaceColor,
                            )
                        }
                        probe.error?.let { message ->
                            OnyxRowDivider()
                            OnyxRow {
                                BasicText(
                                    text = message,
                                    localize = false,
                                    style = theme.footnote.copy(
                                        color = OnyxContrast.distinctOn(theme.error, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (probe.prompts > 0 || probe.resources > 0) {
                            OnyxRowDivider()
                            OnyxRow {
                                BasicText(
                                    text = "${OnyxLocalization["Prompts"]} ${probe.prompts} · " +
                                        "${OnyxLocalization["Resources"]} ${probe.resources}",
                                    localize = false,
                                    style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                                )
                            }
                        }
                    }
                }
            }

            // Les outils que le serveur expose : dépliables, tels que le test les a listés.
            if (probe?.ok == true) {
                OnyxFormSection(header = "Tools this server exposes") {
                    if (probe.tools.isEmpty()) {
                        OnyxRow {
                            BasicText(
                                text = "No tools were listed.",
                                style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                            )
                        }
                    } else {
                        DisclosureRow(
                            title = OnyxLocalization.format(
                                if (probe.tools.size == 1) "%d tool" else "%d tools",
                                probe.tools.size,
                            ),
                            subtitle = null,
                            expanded = toolsOpen,
                            onToggle = { toolsOpen = !toolsOpen },
                        )
                        if (toolsOpen) {
                            probe.tools.forEachIndexed { index, tool ->
                                OnyxRowDivider()
                                ToolRow(tool, index)
                            }
                        }
                    }
                }
            }

            // La sélection d'outils écrite dans la configuration, quand le serveur en restreint.
            entry.toolSelection?.takeIf { it.isNotEmpty() }?.let { selection ->
                OnyxFormSection(header = "Tools in this toolset") {
                    OnyxRow {
                        BasicText(
                            text = selection.joinToString(", "),
                            localize = false,
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                        )
                    }
                }
            }

            OnyxFormSection(footer = "This removes %1\$s from the Hermes config file. It can't be undone."
                .let { OnyxLocalization.format(it, entry.name) }) {
                OnyxRow(
                    onClick = if (entry.plugin == null && !removing) ({ confirming = true }) else null,
                ) {
                    OnyxIcon(
                        Symbols.Trash,
                        tint = if (entry.plugin == null) {
                            OnyxContrast.distinctOn(theme.error, theme.formSurfaceColor, OnyxContrast.TEXT_MIN)
                        } else {
                            theme.tertiaryText
                        },
                        size = 17.dp,
                    )
                    BasicText(
                        text = if (removing) "Deleting…" else "Delete",
                        style = theme.body.copy(
                            color = if (entry.plugin == null) {
                                OnyxContrast.distinctOn(theme.error, theme.formSurfaceColor, OnyxContrast.TEXT_MIN)
                            } else {
                                theme.tertiaryText
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (confirming) {
        OnyxAlert(
            title = "Delete this server?",
            message = OnyxLocalization.format(
                "This removes %1\$s from the Hermes config file. It can't be undone.",
                entry.name,
            ),
            localizeMessage = false,
            actions = listOf(
                AlertAction(label = "Cancel") { confirming = false },
                AlertAction(label = "Delete", destructive = true) {
                    confirming = false
                    removing = true
                    scope.launch {
                        runCatching { ServeGatewayApi(ServeClient(server)).removeMcpServer(entry.name) }
                            .onSuccess { onDeleted() }
                            .onFailure { problem = mcpFailureMessage(it, entry.name) }
                        removing = false
                    }
                },
            ),
            onDismiss = { confirming = false },
        )
    }
}

/** Une ligne d'outil : son nom verbatim, et sa description quand le serveur la donne. */
@Composable
private fun ToolRow(tool: McpTool, index: Int) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(
            text = "${index + 1}",
            localize = false,
            style = theme.caption.copy(color = bodyInk(theme, theme.formSurfaceColor, theme.tertiaryText)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = tool.name,
                localize = false,
                style = theme.footnote.copy(color = theme.textColor),
            )
            if (tool.description.isNotBlank()) {
                BasicText(
                    text = OnyxLocalization.translateParagraphs(tool.description),
                    localize = false,
                    style = theme.caption.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                )
            }
        }
    }
}

/**
 * « Ajouter une source MCP » : le formulaire, sa validation, et les refus du serveur en français.
 *
 * Le formulaire **n'envoie que ce qu'un transport accepte** : une adresse et son authentification,
 * ou une commande, ses arguments et ses variables. Le serveur refuse le reste (`Provide exactly one
 * of URL…`) ; le refus d'avance se dit ici, sans aller-retour, et le refus du serveur se dit aussi —
 * les deux phrases sont distinctes et nomment la cause.
 */
@Composable
internal fun McpAddPage(
    server: ServerConfig,
    onAdded: (McpServer) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(McpServerDraft()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    // Un refus affiché ne survit pas à la correction du formulaire : sans cela, le message restait
    // sous les yeux alors que le champ venait d'être rempli — et l'on ne savait plus s'il valait
    // encore. Le changement du brouillon efface le message, l'échec d'un envoi le pose.
    LaunchedEffect(draft) { problem = null }

    val transports = listOf("Local command", "Over the network")
    val transportIndex = if (draft.transport == "http") 1 else 0
    val authOptions = listOf("None", "Sign-in (OAuth)", "Header token")
    val authIndex = when (draft.auth) {
        "oauth" -> 1
        "header" -> 2
        else -> 0
    }

    fun send() {
        val refusal = mcpDraftProblem(draft)
        if (refusal != null) {
            problem = OnyxLocalization[refusal]
            return
        }
        problem = null
        sending = true
        scope.launch {
            runCatching { ServeGatewayApi(ServeClient(server)).addMcpServer(draft) }
                .onSuccess { onAdded(it) }
                .onFailure { problem = mcpFailureMessage(it, draft.name.trim()) }
            sending = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(theme.formPageColor)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Add an MCP source",
                onBack = onBack,
                action = if (sending) "Adding…" else "Add",
                onAction = { if (!sending) send() },
                actionEnabled = !sending,
            )
        }

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            problem?.let {
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(
                            Symbols.ExclamationTriangle,
                            tint = OnyxContrast.distinctOn(theme.warning, theme.formSurfaceColor, OnyxContrast.ICON_MIN),
                            size = 16.dp,
                        )
                        BasicText(
                            text = it,
                            localize = false,
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OnyxFormSection(footer = "A local command, or a server over the network.") {
                OnyxFieldRow(
                    label = "Name",
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    placeholder = "my-server",
                )
                OnyxRowDivider()
                AdaptiveSegmented(
                    options = transports.map { OnyxLocalization[it] },
                    selectedIndex = transportIndex,
                    onSelect = { index ->
                        draft = draft.copy(transport = if (index == 1) "http" else "stdio")
                    },
                )
            }

            if (draft.transport == "http") {
                OnyxFormSection(header = "Over the network") {
                    OnyxFieldRow(
                        label = "Address",
                        value = draft.url,
                        onValueChange = { draft = draft.copy(url = it) },
                        placeholder = "https://example.com/mcp",
                    )
                    OnyxRowDivider()
                    AdaptiveSegmented(
                        options = authOptions.map { OnyxLocalization[it] },
                        selectedIndex = authIndex,
                        onSelect = { index ->
                            draft = draft.copy(
                                auth = when (index) {
                                    1 -> "oauth"
                                    2 -> "header"
                                    else -> "none"
                                },
                            )
                        },
                    )
                    if (draft.auth == "header") {
                        OnyxRowDivider()
                        OnyxFieldRow(
                            label = "Bearer token",
                            value = draft.bearerToken,
                            onValueChange = { draft = draft.copy(bearerToken = it) },
                            secret = true,
                        )
                    }
                }
            } else {
                OnyxFormSection(header = "Local command") {
                    OnyxFieldRow(
                        label = "Command",
                        value = draft.command,
                        onValueChange = { draft = draft.copy(command = it) },
                        placeholder = "npx",
                    )
                    OnyxRowDivider()
                    OnyxFieldRow(
                        label = "Arguments",
                        value = draft.args,
                        onValueChange = { draft = draft.copy(args = it) },
                        placeholder = "-y @scope/server",
                    )
                    OnyxRowDivider()
                    OnyxFieldRow(
                        label = "Environment variables",
                        value = draft.env,
                        onValueChange = { draft = draft.copy(env = it) },
                        placeholder = "KEY=value",
                    )
                }
            }

            OnyxFormSection {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    BasicText(
                        text = if (sending) "Adding…" else "Add the server",
                        style = theme.body.copy(
                            color = if (sending) theme.tertiaryText else signalInk(theme, theme.formSurfaceColor),
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onyxTappable(enabled = !sending, onClick = { send() })
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
