package com.onyx.app.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.ui.chat.ChatControlsSection
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Le choix du modèle, et de la force avec laquelle il réfléchit (`ModelPickerView`).
 *
 * Les listes viennent du serveur actif: `/v1/models` pour un endpoint OpenAI-compatible. Un
 * modèle local passe en premier, les autres fournisseurs suivent par ordre alphabétique; chaque
 * section se replie.
 */
@Composable
fun ModelsScreen(
    server: ServerConfig,
    reasoningEffort: String,
    onPickReasoning: (String) -> Unit,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    /** La session ouverte reçoit les réglages de `ChatControlsSection`, comme. */
    serverSessionId: String? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var models by remember(server.id) { mutableStateOf<List<String>>(emptyList()) }
    var problem by remember(server.id) { mutableStateOf<String?>(null) }
    var loading by remember(server.id) { mutableStateOf(true) }
    var collapsed by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(server.id) {
        loading = true
        val probe = runCatching { ChatCompletionsClient(server).testConnection() }.getOrNull()
        models = probe?.models.orEmpty()
        problem = if (probe?.ok == false) probe.message else null
        loading = false
    }

    val groups = remember(models) { grouped(models) }
    val isFastLane = server.transport == com.onyx.app.data.model.Transport.CHAT_COMPLETIONS

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Model", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // L'effort de raisonnement, en menu plutôt qu'en segments: sept niveaux ne tiennent
            // pas sur une ligne sans tronquer « Default » et « Medium ».
            OnyxFormSection(
                header = "Reasoning effort",
                footer = if (isFastLane) {
                    "How much the model thinks before answering. None asks the model not to think; any other level asks it to, and is passed on as its effort. Models that don't grade effort treat every level the same, and a server that fixes thinking on or off has the last word. Default leaves it to the server."
                } else {
                    "How much the model thinks before answering. None asks the model not to think; some models and servers decide that themselves. X-High and Max are for frontier models such as Claude and GPT; others treat them as High or may refuse them. Applies to this conversation and to new ones. Default leaves it to the gateway, and leaves the open conversation at the level it has."
                },
            ) {
                SettingsRepository.REASONING_EFFORTS.forEachIndexed { index, (value, label) ->
                    if (index > 0) OnyxRowDivider()
                    OnyxRow(onClick = { onPickReasoning(value) }) {
                        BasicText(
                            text = label,
                            style = theme.body.copy(color = theme.textColor),
                            modifier = Modifier.weight(1f),
                        )
                        if (value == reasoningEffort) {
                            OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                        }
                    }
                }
            }

            if (serverSessionId != null && server.transport != com.onyx.app.data.model.Transport.CHAT_COMPLETIONS) {
                ChatControlsSection(server = server, stored = serverSessionId)
            }

            OnyxFormSection(
                header = "Model",
                footer = if (isFastLane) null else "Applies to this conversation and to new ones.",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onyxTappable { onPick(models.firstOrNull().orEmpty()) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = if (isFastLane) "The endpoint's default" else "Gateway default",
                            style = theme.body.copy(color = theme.textColor),
                        )
                        BasicText(
                            text = if (isFastLane) {
                                "Whatever the endpoint is configured to use"
                            } else {
                                "Whatever Onyx is configured to use"
                            },
                            style = theme.caption.copy(color = theme.secondaryText),
                        )
                    }
                    if (server.model.isEmpty()) {
                        OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                    }
                }
            }

            when {
                problem != null -> OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.WifiExclamation, tint = theme.secondaryText, size = 16.dp)
                        BasicText(
                            text = problem.orEmpty(),
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                loading -> OnyxFormSection {
                    OnyxRow { BasicText("Loading models…", style = theme.body.copy(color = theme.secondaryText)) }
                }

                else -> groups.forEach { group ->
                    val folded = group.provider in collapsed
                    OnyxFormSection(header = group.name, localizeHeader = false) {
                        OnyxRow(onClick = {
                            collapsed = if (folded) collapsed - group.provider else collapsed + group.provider
                        }) {
                            BasicText(
                                text = if (folded) "Show ${group.models.size}" else "Hide",
                                style = theme.footnote.copy(color = theme.accent),
                                modifier = Modifier.weight(1f),
                            )
                            OnyxIcon(
                                symbol = if (folded) Symbols.ChevronRight else Symbols.ChevronDown,
                                tint = theme.secondaryText,
                                size = 12.dp,
                            )
                        }
                        if (!folded) {
                            group.models.forEach { model ->
                                OnyxRowDivider()
                                OnyxRow(onClick = { onPick(model) }) {
                                    BasicText(
                                        text = model,
                                        localize = false,
                                        style = theme.body.copy(color = theme.textColor),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (model == server.model) {
                                        OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Un groupe de modèles: un fournisseur, et les siens. */
private data class ModelGroup(val provider: String, val name: String, val models: List<String>)

/**
 * Regroupe les modèles: une section « Local » d'abord (les identifiants sans
 * fournisseur), puis les autres par ordre alphabétique.
 */
private fun grouped(models: List<String>): List<ModelGroup> {
    val byProvider = models.groupBy { model ->
        val prefix = model.substringBefore('/', missingDelimiterValue = "")
        if (prefix.isEmpty() || model.contains('.') && !model.contains('/')) "" else prefix
    }
    val local = byProvider[""].orEmpty().sorted()
    val others = byProvider.filterKeys { it.isNotEmpty() }
    return buildList {
        if (local.isNotEmpty()) add(ModelGroup("", "Local", local))
        others.keys.sorted().forEach { key ->
            val list = others[key].orEmpty().sorted()
            if (list.isNotEmpty()) add(ModelGroup(key, key.replaceFirstChar { it.uppercase() }, list))
        }
    }
}
