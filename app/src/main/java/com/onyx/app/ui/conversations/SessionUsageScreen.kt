package com.onyx.app.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeSessionsApi
import com.onyx.app.data.net.SessionSummary
import com.onyx.app.data.net.SessionUsage
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * « Usage & cost », comme `SessionUsageView`.
 *
 * Les jetons d'une session (entrée, sortie, raisonnement, cache lu et écrit, puis le total), son
 * coût estimé et réel quand il y en a un, et son activité — messages, appels d'outils, appels
 * d'API, modèle et origine.
 *
 * [initial] porte déjà ce que l'appelant sait — le fil local d'une conversation, ou la ligne du
 * registre. Quand [fetchId] désigne une session du gateway et que [server] peut l'atteindre, les
 * chiffres du serveur remplacent les locaux; sinon l'écran montre ce qu'il a, sans inventer le
 * raisonnement ni le coût, que le flux ne remonte pas.
 */
@Composable
fun SessionUsageScreen(
    initial: SessionSummary,
    onBack: () -> Unit,
    server: ServerConfig? = null,
    fetchId: String? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var summary by remember(initial.id) { mutableStateOf(initial) }

    LaunchedEffect(initial.id, fetchId, server?.id, server?.configSignature) {
        val stored = fetchId ?: return@LaunchedEffect
        val reachable = server ?: return@LaunchedEffect
        if (!reachable.hasServeCredentials) return@LaunchedEffect
        runCatching { ServeSessionsApi(ServeClient(reachable)).summary(stored) }
            .getOrNull()
            ?.let { summary = it }
    }

    val cost = summary.cost ?: 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = summary.displayTitle,
                onBack = null,
                action = "Done",
                onAction = onBack,
                localizeTitle = false,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(header = "Tokens") {
                UsageRow("Input", SessionUsage.count(summary.inputTokens))
                OnyxRowDivider()
                UsageRow("Output", SessionUsage.count(summary.outputTokens))
                OnyxRowDivider()
                UsageRow("Reasoning", SessionUsage.count(summary.reasoningTokens))
                OnyxRowDivider()
                UsageRow("Cache read", SessionUsage.count(summary.cacheReadTokens))
                OnyxRowDivider()
                UsageRow("Cache write", SessionUsage.count(summary.cacheWriteTokens))
                OnyxRowDivider()
                UsageRow("Total", summary.totalTokens.toString())
            }

            if (cost > 0) {
                OnyxFormSection(header = "Cost") {
                    UsageRow("Estimated", SessionUsage.money(summary.estimatedCostUsd))
                    OnyxRowDivider()
                    UsageRow("Actual", SessionUsage.money(summary.actualCostUsd))
                }
            }

            OnyxFormSection(header = "Activity") {
                UsageRow("Messages", SessionUsage.count(summary.messageCount))
                OnyxRowDivider()
                UsageRow("Tool calls", SessionUsage.count(summary.toolCallCount))
                OnyxRowDivider()
                UsageRow("API calls", SessionUsage.count(summary.apiCallCount))
                summary.model?.takeIf { it.isNotEmpty() }?.let { model ->
                    OnyxRowDivider()
                    UsageRow("Model", model, verbatim = true)
                }
                OnyxRowDivider()
                UsageRow("Source", summary.sourceLabel, verbatim = true)
            }

            if (cost > 0) {
                OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = "Costs are the gateway's own estimate from provider pricing.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }
            }
        }
    }
}

/** Une ligne « libellé → valeur » de la feuille, les chiffres en chasse fixe comme. */
@Composable
private fun UsageRow(label: String, value: String, verbatim: Boolean = false) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.primaryText),
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = value,
            localize = !verbatim,
            style = theme.body.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
        )
    }
}
