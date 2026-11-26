package com.onyx.app.ui.capabilities

import com.onyx.app.data.net.humanFailure
import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.CatalogSkill
import com.onyx.app.data.net.DashboardSkill
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeSkillsApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An **open** skill: its name, its description, its family, its switch, and its
 * `SKILL.md` when the content route provides it.
 *
 * A server that does not serve `api/skills/content` says so in the page — the title, the description and
 * the switch remain readable, and nothing is invented in place of the content.
 */
@Composable
internal fun SkillDetailPage(
    server: ServerConfig,
    skill: DashboardSkill,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    var content by remember(skill.name) { mutableStateOf<String?>(null) }
    var problem by remember(skill.name) { mutableStateOf<String?>(null) }
    var busy by remember(skill.name) { mutableStateOf(false) }

    LaunchedEffect(skill.name) {
        problem = null
        content = null
        busy = true
        runCatching { ServeSkillsApi(ServeClient(server)).content(skill.name) }
            .onSuccess { content = it }
            .onFailure { problem = humanFailure(it) }
        busy = false
    }

    Column(modifier = Modifier.fillMaxSize().background(theme.formPageColor)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = skill.name,
                onBack = onBack,
                localizeTitle = false,
                // **Writing the skill being read**: the same screen that "Skills" used to carry,
                // reached from the "Skills" family of "Capabilities".
                action = "Edit",
                onAction = onEdit,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(header = "Skills") {
                if (skill.description.isNotBlank()) {
                    OnyxRow {
                        BasicText(
                            text = OnyxLocalization.translateParagraphs(skill.description),
                            localize = false,
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                        )
                    }
                    OnyxRowDivider()
                }
                skill.category?.takeIf { it.isNotBlank() }?.let { category ->
                    OnyxRow {
                        BasicText(
                            text = "Category",
                            style = theme.body.copy(color = theme.primaryText),
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = category,
                            localize = false,
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                            modifier = Modifier.weight(1.3f),
                        )
                    }
                    OnyxRowDivider()
                }
                CapabilityRow(
                    title = skill.name,
                    detail = null,
                    localizeTitle = false,
                    checked = skill.enabled,
                    busy = false,
                    canToggle = true,
                    onToggle = onToggle,
                )
            }

            problem?.let {
                FailureBanner(text = it)
            }

            OnyxFormSection(header = "SKILL.md") {
                when {
                    busy -> OnyxRow {
                        BasicText(text = "Reading…", style = theme.body.copy(color = bodyInk(theme, theme.formSurfaceColor)))
                    }
                    content == null -> OnyxRow {
                        BasicText(
                            text = "The content isn't available for this skill.",
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                        )
                    }
                    else -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                            .padding(10.dp),
                    ) {
                        BasicText(
                            text = content.orEmpty(),
                            localize = false,
                            style = theme.footnote.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
    }
}

/**
 * "The catalogue": the skills the server offers to install, with their family and the **actual
 * state** of each in this profile — installed, available, or being installed.
 *
 * Nothing is invented: the list is the one from `GET api/skills/hub/official`, installation goes through
 * `POST api/skills/hub/install` and removal through `POST api/skills/hub/uninstall`. As the server
 * installs in the background, the page **rereads** the list afterwards rather than assuming that the
 * "installed" mark has changed.
 */
@Composable
internal fun CatalogPage(
    server: ServerConfig,
    catalog: List<CatalogSkill>?,
    problem: String?,
    onClose: () -> Unit,
    onLoad: (List<CatalogSkill>?, String?) -> Unit,
    onChanged: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionProblem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server.id, server.configSignature) {
        runCatching { ServeSkillsApi(ServeClient(server)).catalog() }
            .onSuccess { onLoad(it, null) }
            .onFailure { onLoad(null, humanFailure(it)) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "The catalogue", onBack = onClose)
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = OnyxLocalization["Search the catalogue"],
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
        ) {
            Note("Everything the server offers to install, with what is already installed.")
            (problem ?: actionProblem)?.let { FailureBanner(text = it) }
            val loaded = catalog ?: run { LoadingLine("Loading the catalogue…"); return@Column }
            val shown = loaded.filter { entry ->
                query.isBlank() || entry.name.contains(query, true) ||
                    entry.description.contains(query, true) || entry.category.contains(query, true)
            }
            if (shown.isEmpty()) {
                EmptyLine(if (loaded.isEmpty()) "No skills in the catalogue." else "Nothing matches this search.")
                return@Column
            }
            shown.sortedWith(compareBy({ it.category }, { it.name })).forEachIndexed { index, entry ->
                if (index > 0) OnyxRowDivider(inset = 0.dp)
                CatalogRow(
                    entry = entry,
                    busy = entry.name in busy,
                    onInstall = {
                        busy = busy + entry.name
                        actionProblem = null
                        scope.launch {
                            runCatching {
                                ServeSkillsApi(ServeClient(server)).installFromCatalog(entry.identifier)
                            }.onSuccess {
                                onChanged()
                                 // The server installs in the background: the list is reread twice
                                 // rather than placing an "installed" mark that nobody has
                                 // confirmed. If it does not arrive, the row stays "available",
                                 // which is the truth of the moment.
                                repeat(4) {
                                    delay(2_500)
                                    runCatching { ServeSkillsApi(ServeClient(server)).catalog() }
                                        .onSuccess { rows -> onLoad(rows, null) }
                                }
                            }.onFailure {
                                actionProblem = OnyxLocalization.format(
                                    "Couldn't install it",
                                ) + " — " + (humanFailure(it))
                            }
                            busy = busy - entry.name
                        }
                    },
                    onRemove = {
                        busy = busy + entry.name
                        actionProblem = null
                        scope.launch {
                            runCatching {
                                ServeSkillsApi(ServeClient(server)).uninstallFromCatalog(entry.name)
                            }.onSuccess {
                                onChanged()
                                repeat(4) {
                                    delay(2_500)
                                    runCatching { ServeSkillsApi(ServeClient(server)).catalog() }
                                        .onSuccess { rows -> onLoad(rows, null) }
                                }
                            }.onFailure {
                                actionProblem = OnyxLocalization.format(
                                    "Couldn't remove it",
                                ) + " — " + (humanFailure(it))
                            }
                            busy = busy - entry.name
                        }
                    },
                )
            }
        }
    }
}

/** One catalogue entry: its name, its description, its family, its state, and its action. */
@Composable
private fun CatalogRow(
    entry: CatalogSkill,
    busy: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = entry.name,
                localize = false,
                style = theme.body.copy(color = theme.textColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.description.isNotBlank()) {
                BasicText(
                    text = OnyxLocalization.translateParagraphs(entry.description),
                    localize = false,
                    style = theme.caption.copy(color = bodyInk(theme, theme.formPageColor)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(
                text = listOf(OnyxLocalization[entry.category], trustLabel(entry.trustLevel))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                localize = false,
                style = theme.caption2.copy(color = bodyInk(theme, theme.formPageColor, theme.tertiaryText)),
            )
        }
        val state = when {
            busy && entry.installed -> "Removing…"
            busy -> "Installing…"
            entry.installed -> "Installed"
            else -> "Available"
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = state,
                style = theme.caption.copy(color = bodyInk(theme, theme.formPageColor, theme.tertiaryText)),
            )
            when {
                busy -> OnyxIcon(Symbols.Clock, tint = theme.tertiaryText, size = 14.dp)
                entry.installed -> ActionLine(text = "Remove", enabled = true, onClick = onRemove)
                else -> ActionLine(text = "Install", enabled = true, onClick = onInstall)
            }
        }
    }
}

/** The word of the server's `trust_level`, translated — a level this build does not know stays as is. */
private fun trustLabel(trustLevel: String): String = when (trustLevel.lowercase()) {
    "builtin" -> OnyxLocalization["Built-in"]
    "trusted" -> OnyxLocalization["Trusted"]
    "community" -> OnyxLocalization["Community"]
    else -> trustLevel
}
