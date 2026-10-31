package com.onyx.app.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.serve.GatewayStatus
import com.onyx.app.data.model.serve.McpServer
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.OnyxValueRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.design.IdentifierText

/**
 * Réglages → Gateway.
 *
 * Port de `GatewayView` : l'état d'Hermes, ce à quoi il est connecté, ses serveurs MCP avec
 * leur interrupteur et leur test, les journaux, et la maintenance (redémarrage, mise à jour).
 */
@Composable
fun GatewayScreen(
    viewModel: GatewayViewModel,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmUpdate by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Gateway", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            when {
                state.loading -> {
                    OnyxFormSection {
                        OnyxRow {
                            BasicText("Loading from Onyx…", style = theme.body.copy(color = theme.secondaryText))
                        }
                    }
                }

                state.problem != null && state.status == null -> {
                    OnyxFormSection {
                        OnyxRow {
                            OnyxIcon(Symbols.WifiExclamation, tint = theme.warning, size = 18.dp)
                            BasicText(
                                text = "Couldn't reach the gateway",
                                style = theme.body.copy(color = theme.primaryText),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OnyxRowDivider()
                        OnyxRow {
                            BasicText(state.problem.orEmpty(), localize = false, style = theme.footnote.copy(color = theme.secondaryText))
                        }
                        OnyxRowDivider()
                        OnyxRow(onClick = viewModel::refresh) {
                            BasicText("Try Again", style = theme.body.copy(color = theme.accent))
                        }
                    }
                }

                else -> {
                    state.status?.let { StatusSection(it) }

                    if (state.status?.connections?.isNotEmpty() == true) {
                        ConnectionsSection(state.status!!)
                    }

                    McpSection(
                        servers = state.servers,
                        probes = state.probes,
                        testing = state.testing,
                        onToggle = viewModel::setServer,
                        onTest = viewModel::testServer,
                    )

                    OnyxFormSection {
                        OnyxValueRow(label = "Logs", value = "", symbol = Symbols.ListBullet, onClick = onOpenLogs)
                    }

                    MaintenanceSection(
                        state = state,
                        onRestart = { confirmRestart = true },
                        onCheckForUpdates = viewModel::checkForUpdates,
                        onUpdate = { confirmUpdate = true },
                    )
                }
            }
        }
    }

    if (confirmRestart) {
        OnyxAlert(
            title = "Restart the gateway?",
            message = if (state.status?.turnsRunning ?: 0 > 0) {
                "A turn is running. Restarting ends it."
            } else {
                "It comes back on its own in a few seconds."
            },
            actions = listOf(
                AlertAction("Cancel") { confirmRestart = false },
                AlertAction("Restart", destructive = true) {
                    confirmRestart = false
                    viewModel.restart()
                },
            ),
            onDismiss = { confirmRestart = false },
        )
    }

    if (confirmUpdate) {
        OnyxAlert(
            title = "Update Hermes?",
            message = "Hermes updates on its host and the gateway restarts. A turn that is running ends.",
            actions = listOf(
                AlertAction("Cancel") { confirmUpdate = false },
                AlertAction("Update Hermes", destructive = true) {
                    confirmUpdate = false
                    viewModel.updateHermes()
                },
            ),
            onDismiss = { confirmUpdate = false },
        )
    }
}

@Composable
private fun StatusSection(status: GatewayStatus) {
    val theme = LocalOnyxTheme.current
    OnyxFormSection(header = "Status") {
        if (status.versionLine.isNotEmpty()) {
            LabeledRow(label = "Hermes", value = status.versionLine, localizeValue = false)
            OnyxRowDivider()
        }
        OnyxRow {
            BasicText("Gateway", style = theme.body.copy(color = theme.primaryText), modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (status.gatewayOk) theme.success else theme.error, CircleShape),
            )
            BasicText(
                text = status.gatewayLabel,
                style = theme.body.copy(color = theme.secondaryText),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        status.exitReason?.takeIf { it.isNotBlank() }?.let {
            OnyxRowDivider()
            OnyxRow {
                BasicText(it, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
            }
        }
        if (status.turnsRunning > 0) {
            OnyxRowDivider()
            LabeledRow(label = "Turns running", value = status.turnsRunning.toString())
        }
        status.troubles.forEach { trouble ->
            OnyxRowDivider()
            OnyxRow {
                OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                BasicText(
                    text = trouble.label,
                    localize = false,
                    style = theme.body.copy(color = theme.warning),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        listOf(
            "Host" to status.host,
            "System" to status.system,
            "Up for" to status.upFor,
            "Memory in use" to status.memory,
            "Disk in use" to status.disk,
        ).forEach { (label, value) ->
            value?.takeIf { it.isNotBlank() }?.let {
                OnyxRowDivider()
                LabeledRow(label = label, value = it, localizeValue = false)
            }
        }
    }
}

@Composable
private fun ConnectionsSection(status: GatewayStatus) {
    val theme = LocalOnyxTheme.current
    OnyxFormSection(
        header = "Connections",
        footer = "What the gateway is connected to. Messaging platforms are set up on the Hermes host.",
    ) {
        status.connections.forEachIndexed { index, connection ->
            if (index > 0) OnyxRowDivider()
            LabeledRow(
                label = connection.title,
                value = connection.state.replace('_', ' ').replaceFirstChar { it.uppercase() },
                valueColor = if (connection.isConnected) theme.secondaryText else theme.warning,
                localizeLabel = false,
            )
            connection.error?.takeIf { it.isNotBlank() }?.let {
                OnyxRow {
                    BasicText(it, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
                }
            }
        }
    }
}

@Composable
private fun McpSection(
    servers: List<McpServer>,
    probes: Map<String, com.onyx.app.data.model.serve.McpProbe>,
    testing: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onTest: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    OnyxFormSection(
        header = "MCP servers",
        footer = "A switch changes Hermes's config, as the Dashboard's own does. It takes effect in new conversations. "
            + "Test has Hermes connect to the server and list its tools.",
    ) {
        if (servers.isEmpty()) {
            OnyxRow {
                BasicText(
                    "No MCP servers are set up on this Hermes.",
                    style = theme.body.copy(color = theme.secondaryText),
                )
            }
            return@OnyxFormSection
        }

        servers.forEachIndexed { index, server ->
            if (index > 0) OnyxRowDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = server.name,
                        localize = false,
                        style = theme.body.copy(color = theme.primaryText),
                        modifier = Modifier.weight(1f),
                    )
                    if (server.plugin == null) {
                        OnyxSwitch(checked = server.enabled, onCheckedChange = { onToggle(server.name, it) })
                    }
                }
                if (server.target.isNotBlank()) {
                    IdentifierText(
                        text = server.target,
                        style = theme.footnote.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                    )
                }
                server.plugin?.let {
                    BasicText(
                        text = "From the plugin $it; switched with it.",
                        style = theme.footnote.copy(color = theme.secondaryText),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText(
                        text = if (server.name in testing) "Testing…" else "Test",
                        style = theme.footnote.copy(color = theme.accent),
                        modifier = Modifier.onyxClickable(enabled = server.name !in testing) { onTest(server.name) },
                    )
                    probes[server.name]?.let { probe ->
                        BasicText(
                            text = probe.summary.ifEmpty { probe.error.orEmpty() },
                            localize = false,
                            style = theme.footnote.copy(color = if (probe.ok) theme.secondaryText else theme.warning),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceSection(
    state: GatewayUiState,
    onRestart: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onUpdate: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    OnyxFormSection(
        header = "Maintenance",
        footer = if (state.canUpdate) {
            "Hermes updates itself on its host; the gateway restarts when it is done."
        } else {
            "This Hermes can't be updated from Onyx. Update it on the host."
        },
    ) {
        val working = state.working
        if (working != null) {
            OnyxRow {
                Column {
                    BasicText(working, style = theme.body.copy(color = theme.primaryText))
                    state.progress.forEach { line ->
                        BasicText(
                            text = line,
                            localize = false,
                            style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            return@OnyxFormSection
        }

        OnyxRow(onClick = onRestart) {
            BasicText("Restart Gateway…", style = theme.body.copy(color = theme.accent))
        }

        if (state.canUpdate) {
            OnyxRowDivider()
            OnyxRow(onClick = onCheckForUpdates) {
                BasicText("Check for Updates", style = theme.body.copy(color = theme.accent))
            }
            state.update?.let { update ->
                OnyxRowDivider()
                LabeledRow(
                    label = "Hermes ${update.currentVersion}".trim(),
                    value = update.summary.ifEmpty { update.latestVersion },
                    localizeValue = false,
                )
                OnyxRowDivider()
                OnyxRow(onClick = onUpdate) {
                    BasicText("Update Hermes…", style = theme.body.copy(color = theme.accent))
                }
            }
        }

        state.outcome?.let { outcome ->
            OnyxRowDivider()
            OnyxRow {
                BasicText(outcome, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
            }
        }
    }
}

/** Une ligne « libellé → valeur », comme `LabeledContent` dans un formulaire référence. */
@Composable
private fun LabeledRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    localizeLabel: Boolean = true,
    localizeValue: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        // Poids partagés : sans largeur réservée, une valeur longue (la chaîne `uname` du
        // serveur, par exemple) mangeait toute la ligne et le libellé se réduisait à une
        // lettre par ligne (« OS » affiché en colonne verticale).
        BasicText(
            label,
            localize = localizeLabel,
            style = theme.body.copy(color = theme.primaryText),
            modifier = Modifier.weight(0.42f),
        )
        BasicText(
            text = value,
            localize = localizeValue,
            style = theme.body.copy(color = valueColor ?: theme.secondaryText, fontWeight = FontWeight.Normal),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f),
        )
    }
}
