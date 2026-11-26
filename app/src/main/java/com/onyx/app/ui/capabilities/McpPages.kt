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
 * The page for an MCP server: **how** it connects, what it exposes, and what can be done with it.
 *
 * Nothing is assumed: the transport, the target, the arguments, the environment variables (as
 * the server masks them) come from the list route; the tools and the latency come from the
 * **real test**, and a test error is shown as an error — never as a success.
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

            // The server's actual shape: what the configuration says, as is.
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
                        // A local command has no "address": the word follows the shape.
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
                                // The server masks values that look like a secret: they are
                                // shown as it rendered them, never rebuilt.
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

            // The switch, here as in the list: a server brought by a plugin cannot be
            // configured by hand.
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

            // The test: what it actually returned, or what it actually failed to do.
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
                                    // The word is composed here, in French: the server's sentence
                                    // ("Connected · 5 tools") is not a dictionary key.
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

            // The tools the server exposes: expandable, as the test listed them.
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

            // The tool selection written in the configuration, when the server restricts it.
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

/** A tool row: its name verbatim, and its description when the server gives one. */
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
 * "Add an MCP source": the form, its validation, and the server's refusals in French.
 *
 * The form **only sends what a transport accepts**: an address and its authentication,
 * or a command, its arguments and its variables. The server refuses the rest (`Provide exactly one
 * of URL…`); the upfront refusal is stated here, without a round trip, and the server's refusal is
 * stated too — the two sentences are distinct and name the cause.
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

    // A displayed refusal does not survive the correction of the form: without this, the message
    // stayed in view while the field had just been filled in — and one no longer knew whether it
    // still applied. Changing the draft clears the message, a failed send sets it.
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
