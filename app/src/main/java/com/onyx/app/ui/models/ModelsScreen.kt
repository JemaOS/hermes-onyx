package com.onyx.app.ui.models

import com.onyx.app.ui.design.onyxPage

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
import androidx.compose.runtime.mutableIntStateOf
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
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.ChatControlsSection
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.common.OnyxTroublePanel
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.design.IdentifierText

/**
 * Le choix du modèle, et de la force avec laquelle il réfléchit (`ModelPickerView`).
 *
 * Les listes viennent du serveur actif : `/v1/models` pour un endpoint OpenAI-compatible. Un
 * modèle local passe en premier, les autres fournisseurs suivent par ordre alphabétique ; chaque
 * section se replie.
 */
@Composable
fun ModelsScreen(
    server: ServerConfig,
    reasoningEffort: String,
    onPickReasoning: (String) -> Unit,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    /** La session ouverte reçoit les réglages de `ChatControlsSection`, comme sur la référence. */
    serverSessionId: String? = null,
    /**
     * Le modèle **réellement en force** de la conversation, quand il diffère du modèle configuré
     * (repli de la passerelle) : c'est lui qui porte la coche, et il est nommé même s'il n'est pas
     * dans la liste exposée par le serveur.
     */
    inForceModel: String? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var models by remember(server.id) { mutableStateOf<List<String>>(emptyList()) }
    var problem by remember(server.id) { mutableStateOf<String?>(null) }
    var loading by remember(server.id) { mutableStateOf(true) }
    var collapsed by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Le compteur d'essais : « Réessayer » relit vraiment la liste des modèles.
    var attempt by remember(server.id) { mutableIntStateOf(0) }

    LaunchedEffect(server.id, attempt) {
        loading = true
        // **Un échec de sonde ne doit pas rester muet.** Avant ce lot, seule une réponse *reçue*
        // (`probe.ok == false`) posait un problème : une exception — adresse fermée, réseau coupé —
        // rendait `probe == null`, donc **aucun** message, et l'écran se contentait des groupes de
        // modèles vides. C'était l'écran muet que l'audit relève (T6), ici au pire endroit : celui
        // où l'on choisit le modèle.
        val outcome = runCatching { ChatCompletionsClient(server).testConnection() }
        val probe = outcome.getOrNull()
        models = probe?.models.orEmpty()
        problem = when {
            probe != null && !probe.ok -> probe.message
            probe == null -> (outcome.exceptionOrNull() as? TransportError)?.userMessage()
                ?: outcome.exceptionOrNull()?.message
                ?: OnyxLocalization["Couldn't reach the server"]
            else -> null
        }
        loading = false
    }

    val groups = remember(models) { grouped(models) }
    val isFastLane = server.transport == com.onyx.app.data.model.Transport.CHAT_COMPLETIONS

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
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
            // L'effort de raisonnement, en menu plutôt qu'en segments : sept niveaux ne tiennent
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
                // Le modèle **en force** quand il diffère du configuré : le tour est servi par lui,
                // et s'il n'est pas dans la liste exposée, il faut quand même pouvoir le lire ici.
                if (!inForceModel.isNullOrBlank() && inForceModel != server.model) {
                    OnyxRowDivider()
                    OnyxRow(onClick = {}) {
                        Column(modifier = Modifier.weight(1f)) {
                            IdentifierText(
                                text = inForceModel,
                                style = theme.body.copy(color = theme.textColor),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            BasicText(
                                text = "Served now by the fallback",
                                style = theme.caption.copy(color = theme.secondaryText),
                            )
                        }
                        OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                    }
                }
            }

            when {
                problem != null -> OnyxTroublePanel(
                    // Le message du refus ou de la panne, tel que le probe le donne — jamais un
                    // écran muet : la carte nomme la cause et offre de réessayer.
                    problem = problem.orEmpty(),
                    onRetry = { attempt++ },
                )

                loading -> OnyxLoadingRows(label = "Loading models…")

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
                                    IdentifierText(
                                        text = model,
                                        style = theme.body.copy(color = theme.textColor),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (model == server.model || model == inForceModel) {
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

/** Un groupe de modèles : un fournisseur, et les siens. */
private data class ModelGroup(val provider: String, val name: String, val models: List<String>)

/**
 * Regroupe les modèles comme référence : une section « Local » d'abord (les identifiants sans
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
