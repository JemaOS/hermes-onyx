package com.onyx.app.ui.tools

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.GatewayToolset
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeToolsetsApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Réglages → Outils (`ToolsView`).
 *
 * Les jeux d'outils que la passerelle peut activer, ceux qui le sont d'abord, et la bascule.
 *
 * Les libellés et les descriptions viennent du serveur, en anglais : ils passent par le
 * dictionnaire (`onyxToolsPageFrench`) pour être lisibles en français. Les identifiants d'outils
 * (`a2a_call`, `execute_code`…) restent tels quels — ce sont des noms d'API.
 *
 * Le compteur qui s'affichait devant l'interrupteur (« A2A 5 ») a été retiré : le propriétaire
 * — « les chiffres à côté du bouton ça veut rien dire, supprime ». Il ne disait que le nombre
 * d'outils du jeu, que la ligne des noms juste en dessous donne déjà.
 */
@Composable
fun ToolsScreen(
    server: ServerConfig,
    scope: CoroutineScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var toolsets by remember(server.id) { mutableStateOf<List<GatewayToolset>>(emptyList()) }
    var loading by remember(server.id) { mutableStateOf(true) }
    var problem by remember(server.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }

    val canToggle = server.hasServeCredentials

    suspend fun load() {
        runCatching { ServeToolsetsApi(ServeClient(server)).toolsets() }
            .onSuccess { toolsets = it; problem = null }
            .onFailure { problem = (it as? TransportError)?.userMessage() ?: it.message }
        loading = false
    }

    LaunchedEffect(server.id) { load() }

    val enabled = remember(toolsets) { toolsets.filter { it.enabled }.sortedBy { it.name } }
    val disabled = remember(toolsets) { toolsets.filterNot { it.enabled }.sortedBy { it.name } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Tools", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            if (problem != null && toolsets.isEmpty()) {
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.WifiExclamation, tint = theme.secondaryText, size = 18.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText("Couldn't reach the gateway", style = theme.body.copy(color = theme.textColor))
                            BasicText(problem.orEmpty(), localize = false, style = theme.footnote.copy(color = theme.secondaryText))
                        }
                    }
                }
                return@Column
            }

            if (loading && toolsets.isEmpty()) {
                OnyxFormSection {
                    OnyxRow { BasicText("Loading from Onyx…", style = theme.body.copy(color = theme.secondaryText)) }
                }
                return@Column
            }

            problem?.let {
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                        BasicText(it, localize = false, style = theme.footnote.copy(color = theme.secondaryText), modifier = Modifier.weight(1f))
                    }
                }
            }

            OnyxFormSection(
                header = "Enabled · ${enabled.size}",
                footer = if (canToggle) {
                    "Switches change the toolset's platform setting in Hermes's config, the same as the dashboard. The API server's own list (platform_toolsets.api_server) is separate and set in config.yaml on the Hermes host."
                } else {
                    "Anything here can be used by asking in plain words. Add the Hermes Dashboard login to toggle toolsets from here."
                },
            ) {
                enabled.forEachIndexed { index, toolset ->
                    if (index > 0) OnyxRowDivider()
                    ToolsetRow(
                        toolset = toolset,
                        canToggle = canToggle,
                        busy = toolset.name in busy,
                        onToggle = { on ->
                            busy = busy + toolset.name
                            toolsets = toolsets.map { if (it.name == toolset.name) it.copy(enabled = on) else it }
                            scope.launch {
                                runCatching { ServeToolsetsApi(ServeClient(server)).setEnabled(toolset.name, on) }
                                    .onFailure { problem = (it as? TransportError)?.userMessage() ?: it.message }
                                load()
                                busy = busy - toolset.name
                            }
                        },
                    )
                }
            }

            if (disabled.isNotEmpty()) {
                OnyxFormSection(header = "Not enabled · ${disabled.size}") {
                    disabled.forEachIndexed { index, toolset ->
                        if (index > 0) OnyxRowDivider()
                        ToolsetRow(
                            toolset = toolset,
                            canToggle = canToggle,
                            busy = toolset.name in busy,
                            onToggle = { on ->
                                busy = busy + toolset.name
                                toolsets = toolsets.map { if (it.name == toolset.name) it.copy(enabled = on) else it }
                                scope.launch {
                                    runCatching { ServeToolsetsApi(ServeClient(server)).setEnabled(toolset.name, on) }
                                        .onFailure { problem = (it as? TransportError)?.userMessage() ?: it.message }
                                    load()
                                    busy = busy - toolset.name
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsetRow(
    toolset: GatewayToolset,
    canToggle: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Le libellé vient du serveur : il est traduit, comme dans l'éditeur de bot.
            //
            // **Deux lignes au plus, coupées au mot** : sur le volet étroit du pliable replié
            // (340 dp), un titre de jeu d'outils long — celui du poste de travail, avec ses trois
            // systèmes entre parenthèses — prenait trois lignes et allongeait la rangée sans fin.
            // Un titre de rangée se lit d'un coup d'œil ; ce qui est plus long s'abrège par des points.
            BasicText(
                toolset.title,
                localize = true,
                style = theme.body.copy(color = theme.textColor),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (canToggle) {
                // L'interrupteur **entier**, avec sa marge : il ne se rogne jamais et ne touche
                // jamais le bord du volet (51 dp fixes, plus 12 dp d'écart avec le libellé).
                OnyxSwitch(
                    checked = toolset.enabled,
                    onCheckedChange = { if (!busy) onToggle(it) },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        toolset.description?.takeIf { it.isNotBlank() }?.let {
            BasicText(
                text = OnyxLocalization.translateParagraphs(it),
                localize = false,
                style = theme.caption.copy(color = theme.secondaryText),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (toolset.tools.isNotEmpty()) {
            BasicText(
                text = toolset.tools.joinToString(" · "),
                localize = false,
                style = theme.caption2.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
