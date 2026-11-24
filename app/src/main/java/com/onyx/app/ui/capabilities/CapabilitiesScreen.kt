package com.onyx.app.ui.capabilities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.McpProbe
import com.onyx.app.data.model.serve.McpServer
import com.onyx.app.data.model.serve.OTHER_GROUP
import com.onyx.app.data.model.serve.mcpMatches
import com.onyx.app.data.model.serve.mcpTransportLabel
import com.onyx.app.data.model.serve.skillGroups
import com.onyx.app.data.model.serve.toolsetMatches
import com.onyx.app.data.net.CatalogSkill
import com.onyx.app.data.net.DashboardSkill
import com.onyx.app.data.net.GatewayToolset
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeGatewayApi
import com.onyx.app.data.net.ServeSkillsApi
import com.onyx.app.data.net.ServeToolsetsApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.humanFailure
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.AdaptiveSegmented
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.chat.components.provenanceBadge
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.OnyxBase
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.design.iconInk
import com.onyx.app.ui.design.ContentCard
import com.onyx.app.ui.design.ContentCountBadge
import com.onyx.app.ui.design.SocleLargeHeader
import com.onyx.app.ui.design.ContentPageHeader
import com.onyx.app.ui.design.ContentPill
import com.onyx.app.ui.design.ContentRow
import com.onyx.app.ui.design.ContentRowDivider
import com.onyx.app.ui.design.ContentSectionLabel
import com.onyx.app.ui.design.Lucide
import com.onyx.app.ui.design.PillSize
import com.onyx.app.ui.design.PillVariant
import com.onyx.app.ui.design.contentMonoStyle
import com.onyx.app.ui.design.Anatomy
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.tapLabel
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch
import com.onyx.app.ui.design.IdentifierText

/**
 * The profile's three capability families, in selector order.
 *
 * Each family carries the **keys** for its count and its switched-on count, in singular as well as
 * plural: French does not say « 1 outils » ("1 tools") nor « 1 jeux d'outils » ("1 toolsets"). The labels
 * themselves come from the central dictionary.
 */
enum class CapabilityFamily(
    val label: String,
    val countKey: String,
    val countOneKey: String,
    val onKey: String,
    val onOneKey: String,
) {
    SKILLS("Skills", "%d skills", "%d skill", "%d skills on", "%d skill on"),
    TOOLS("Toolsets", "%d toolsets", "%d toolset", "%d toolsets on", "%d toolset on"),
    MCP("MCP", "%d MCP servers", "%d MCP server", "%d MCP servers on", "%d MCP server on"),
}

/**
 * "Capabilities" — what the agent can use in new conversations: its skills,
 * its toolsets, and its MCP servers.
 *
 * The three lists come from the Dashboard, **per profile**: `GET api/skills`, `GET api/tools/toolsets`
 * and `GET api/mcp/servers`. The toggles write the profile configuration
 * (`PUT api/skills/toggle`, `PUT api/tools/toolsets/{name}`, `PUT api/mcp/servers/{name}/enabled`) and
 * **take effect from the next conversation onward** — the note line says so, and nothing here
 * pretends to change the current conversation.
 *
 * Three rules keep the screen honest:
 *
 * 1. **The displayed count is the list's count**: the selector and the count line read the
 *    loaded (and filtered) list, never a separately kept number; a switch changes the
 *    switched-on count in one go, because it changes the list.
 * 2. **A load failure keeps the screen usable**: each family carries its own banner
 *    and its "Try again"; a mute route does not hide the other two.
 * 3. **Nothing is assumed**: a failing test is shown as a failure, a refused add says the
 *    server's sentence, and the switch snaps back if the server did not take the setting —
 *    a displayed state that was not the server's would be a lie.
 */
@Composable
fun CapabilitiesScreen(
    server: ServerConfig,
    onBack: () -> Unit,
    /**
     * **Writing a skill**: `null` creates one, a name opens that skill's editor.
     *
     * It is the **only** function the "Skills" tile carried that this screen did not
     * take over — the catalogue *installs* a skill from the hub, it does not write one. Since the
     * tile was removed, `onEditSkill` lives here: nothing was lost in the move.
     */
    onEditSkill: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(CapabilityFamily.SKILLS) }
    var query by remember { mutableStateOf("") }
    var skills by remember(server.id) { mutableStateOf<List<DashboardSkill>?>(null) }
    var toolsets by remember(server.id) { mutableStateOf<List<GatewayToolset>?>(null) }
    var servers by remember(server.id) { mutableStateOf<List<McpServer>?>(null) }
    var skillsProblem by remember(server.id) { mutableStateOf<String?>(null) }
    var toolsProblem by remember(server.id) { mutableStateOf<String?>(null) }
    var mcpProblem by remember(server.id) { mutableStateOf<String?>(null) }
    var busy by remember(server.id) { mutableStateOf<Set<String>>(emptySet()) }
    var probes by remember(server.id) { mutableStateOf<Map<String, McpProbe?>>(emptyMap()) }
    var expandedToolsets by remember(server.id) { mutableStateOf<Set<String>>(emptySet()) }
    var reloadKey by remember(server.id) { mutableStateOf(0) }

    // The **Vault** is no longer here: it has its own entry in Settings (the "Secrets" section)
    // and its shortcut in the icon menu. "Capabilities" only lists what the agent can
    // use — skills, tools, MCP. The VAULT capability is no longer queried here.

    var openServer by remember(server.id) { mutableStateOf<String?>(null) }
    var addingServer by remember { mutableStateOf(false) }
    var openSkill by remember(server.id) { mutableStateOf<DashboardSkill?>(null) }
    var catalogOpen by remember { mutableStateOf(false) }
    var catalog by remember(server.id) { mutableStateOf<List<CatalogSkill>?>(null) }
    var catalogProblem by remember(server.id) { mutableStateOf<String?>(null) }

    /** The message for a failure: a missing route is named, the rest comes from the transport. */
    fun message(error: Throwable, missingRoute: String): String {
        val http = error as? TransportError
        if (http is TransportError.Http && http.status == 404) return OnyxLocalization[missingRoute]
        return humanFailure(error)
    }

    // The three families are read in one go: each one's count then shows on the
    // selector as soon as it opens, and switching tabs relaunches nothing. Each failure is kept
    // separately, otherwise a mute family would erase the other two's counts.
    LaunchedEffect(server.id, server.configSignature, reloadKey) {
        skillsProblem = null
        toolsProblem = null
        mcpProblem = null
        skills = null
        toolsets = null
        servers = null
        val client = ServeClient(server)
        runCatching { ServeSkillsApi(client).skills() }
            .onSuccess { skills = it }
            .onFailure { skillsProblem = message(it, "This Hermes does not serve the skills route.") }
        runCatching { ServeToolsetsApi(client).toolsets() }
            .onSuccess { toolsets = it }
            .onFailure { toolsProblem = message(it, "This Hermes does not serve the toolsets route.") }
        runCatching { ServeGatewayApi(client).mcpServers() }
            .onSuccess { servers = it }
            .onFailure { mcpProblem = message(it, "This Hermes does not serve the MCP servers route.") }
    }

    if (catalogOpen) {
        CatalogPage(
            server = server,
            catalog = catalog,
            problem = catalogProblem,
            onClose = { catalogOpen = false },
            onLoad = { loaded, failure ->
                catalog = loaded
                catalogProblem = failure
            },
            onChanged = {
                // An installation changes the profile's list: we re-read it rather than guess it.
                scope.launch {
                    runCatching { ServeSkillsApi(ServeClient(server)).skills() }
                        .onSuccess { loaded -> skills = loaded }
                }
            },
        )
        return
    }

    if (addingServer) {
        McpAddPage(
            server = server,
            onAdded = { added ->
                // The server has written; we re-read the list rather than guess it. The added row
                // is placed right away so the screen does not flicker, and the re-read will
                // replace it with what the configuration actually carries.
                servers = servers?.let { list -> (list.filterNot { it.name == added.name } + added).sortedBy { it.name } }
                addingServer = false
                reloadKey += 1
            },
            onBack = { addingServer = false },
        )
        return
    }

    val skillDetail = openSkill
    if (skillDetail != null) {
        SkillDetailPage(
            server = server,
            skill = skillDetail,
            onEdit = { onEditSkill(skillDetail.name) },
            onToggle = { on ->
                skills = skills?.map { if (it.name == skillDetail.name) it.copy(enabled = on) else it }
                openSkill = skillDetail.copy(enabled = on)
                scope.launch {
                    runCatching { ServeSkillsApi(ServeClient(server)).toggle(skillDetail.name, on) }
                        .onFailure {
                            skills = skills?.map { row ->
                                if (row.name == skillDetail.name) row.copy(enabled = !on) else row
                            }
                            openSkill = skillDetail.copy(enabled = !on)
                            skillsProblem = message(it, "This Hermes does not serve the skills route.")
                        }
                }
            },
            onBack = { openSkill = null },
        )
        return
    }

    val openName = openServer
    val entry = openName?.let { name -> servers?.firstOrNull { it.name == name } }
    if (openName != null) {
        if (entry == null) {
            openServer = null
        } else {
            McpServerPage(
                server = server,
                entry = entry,
                probe = probes[openName],
                testing = probes.containsKey(openName) && probes[openName] == null,
                enabledBusy = openName in busy,
                onTest = { testMcp(server, openName, scope) { probe -> probes = probes + (openName to probe) } },
                onToggleEnabled = { on ->
                    busy = busy + openName
                    servers = servers?.map { if (it.name == openName) it.copy(enabled = on) else it }
                    scope.launch {
                        runCatching { ServeGatewayApi(ServeClient(server)).setMcpEnabled(openName, on) }
                            .onFailure {
                                servers = servers?.map { row -> if (row.name == openName) row.copy(enabled = !on) else row }
                                mcpProblem = message(it, "This Hermes does not serve the MCP servers route.")
                            }
                        busy = busy - openName
                    }
                },
                onDeleted = {
                    servers = servers?.filterNot { it.name == openName }
                    probes = probes - openName
                    openServer = null
                },
                onBack = { openServer = null },
            )
            return
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // LOT anatomy-of-content: the large title and its subtitle from the reference
            // ("Capabilities" / "What the agent can use in new chats"). It used to be a small
            // centered app bar, as on scheduled tasks.
            SocleLargeHeader(
                title = "Capabilities",
                subtitle = "What the agent can use in new chats",
                onBack = onBack,
            )
            // The selector belongs to the **content**: it fits within the base's screen margin, like
            // the title, the subtitle and the search. Without that margin it went out to both
            // edges (measured: 4.2 dp on each side) while the rest of the screen is at 16 —
            // it was the control that broke out of the system, not the rest.
            AdaptiveSegmented(
                options = CapabilityFamily.entries.map { family -> familyLabel(family, skills, toolsets, servers) },
                selectedIndex = CapabilityFamily.entries.indexOf(tab),
                onSelect = { index ->
                    // The search belongs to the family being viewed: switching tabs starts
                    // from an empty field rather than applying the other family's word.
                    tab = CapabilityFamily.entries[index]
                    query = ""
                },
                modifier = Modifier.padding(
                    start = OnyxBase.ScreenMargin,
                    end = OnyxBase.ScreenMargin,
                ),
                // **The active half is an accent fill**, its word in white: the rule of the tab
                // pill and of the approved sheet (`.segi.on{background:var(--accfill);
                // color:#fff}`). The white pill on a grey track was not the system.
                filled = true,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = OnyxBase.ScreenMargin,
                    end = OnyxBase.ScreenMargin,
                    top = OnyxBase.Loose,
                    bottom = OnyxBase.Loose,
                ),
            verticalArrangement = Arrangement.spacedBy(OnyxBase.Tight),
        ) {
            Note(
                when (tab) {
                    CapabilityFamily.SKILLS -> "Skills the agent can read when a request matches."
                    CapabilityFamily.TOOLS -> "Toolsets the agent gets in new chats."
                    CapabilityFamily.MCP -> "Changes apply from the next chat."
                },
            )

            when (tab) {
                CapabilityFamily.SKILLS -> SkillsBody(
                    skills = skills,
                    problem = skillsProblem,
                    query = query,
                    onQuery = { query = it },
                    onRetry = { reloadKey += 1 },
                    busy = busy,
                    onBrowse = { catalogOpen = true },
                    onNew = { onEditSkill(null) },
                    onOpen = { skill -> openSkill = skill },
                    onToggle = { skill, on ->
                        busy = busy + skill.name
                        skills = skills?.map { if (it.name == skill.name) it.copy(enabled = on) else it }
                        scope.launch {
                            runCatching { ServeSkillsApi(ServeClient(server)).toggle(skill.name, on) }
                                .onFailure {
                                    skills = skills?.map { row ->
                                        if (row.name == skill.name) row.copy(enabled = !on) else row
                                    }
                                    skillsProblem = message(it, "This Hermes does not serve the skills route.")
                                }
                            busy = busy - skill.name
                        }
                    },
                )

                CapabilityFamily.TOOLS -> ToolsBody(
                    toolsets = toolsets,
                    problem = toolsProblem,
                    query = query,
                    onQuery = { query = it },
                    onRetry = { reloadKey += 1 },
                    busy = busy,
                    expanded = expandedToolsets,
                    onExpand = { name ->
                        expandedToolsets = if (name in expandedToolsets) expandedToolsets - name else expandedToolsets + name
                    },
                    onToggle = { toolset, on ->
                        busy = busy + toolset.name
                        toolsets = toolsets?.map { if (it.name == toolset.name) it.copy(enabled = on) else it }
                        scope.launch {
                            runCatching { ServeToolsetsApi(ServeClient(server)).setEnabled(toolset.name, on) }
                                .onFailure {
                                    toolsets = toolsets?.map { row ->
                                        if (row.name == toolset.name) row.copy(enabled = !on) else row
                                    }
                                    toolsProblem = message(it, "This Hermes does not serve the toolsets route.")
                                }
                            busy = busy - toolset.name
                        }
                    },
                )

                CapabilityFamily.MCP -> McpBody(
                    servers = servers,
                    problem = mcpProblem,
                    query = query,
                    onQuery = { query = it },
                    onRetry = { reloadKey += 1 },
                    busy = busy,
                    onAdd = { addingServer = true },
                    onOpen = { row -> openServer = row.name },
                    onToggle = { row, on ->
                        busy = busy + row.name
                        servers = servers?.map { if (it.name == row.name) it.copy(enabled = on) else it }
                        scope.launch {
                            runCatching { ServeGatewayApi(ServeClient(server)).setMcpEnabled(row.name, on) }
                                .onFailure {
                                    servers = servers?.map { other ->
                                        if (other.name == row.name) other.copy(enabled = !on) else other
                                    }
                                    mcpProblem = message(it, "This Hermes does not serve the MCP servers route.")
                                }
                            busy = busy - row.name
                        }
                    },
                    onTest = { row ->
                        testMcp(server, row.name, scope) { probe -> probes = probes + (row.name to probe) }
                    },
                )
            }
        }
    }
}

/**
 * Testing a server: the probe is placed empty (hence "in progress"), then replaced by **what
 * the server actually returned** — a failure included.
 */
private fun testMcp(
    server: ServerConfig,
    name: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (McpProbe) -> Unit,
) {
    onResult(McpProbe(ok = false, summary = ""))
    scope.launch {
        val probe = runCatching { ServeGatewayApi(ServeClient(server)).testMcp(name) }
            .getOrElse { error ->
                McpProbe(
                    ok = false,
                    summary = "",
                    error = humanFailure(error),
                )
            }
        onResult(probe)
    }
}

/** "Skills 24": the family label and the count, as soon as it is known. */
private fun familyLabel(
    family: CapabilityFamily,
    skills: List<DashboardSkill>?,
    toolsets: List<GatewayToolset>?,
    servers: List<McpServer>?,
): String {
    val label = OnyxLocalization[family.label]
    val count = when (family) {
        CapabilityFamily.SKILLS -> skills?.size
        CapabilityFamily.TOOLS -> toolsets?.size
        CapabilityFamily.MCP -> servers?.size
    } ?: return label
    return "$label $count"
}

/**
 * A family's count line: what it contains, and how many are switched on **among what is
 * shown**.
 *
 * During a search, it states both — "3 of 24 skills" — because a filtered count on its own
 * would make it look as if the family shrank; and the switched-on count then covers the visible
 * rows, so both numbers speak about the same list.
 */
private fun countLine(family: CapabilityFamily, total: Int, shown: Int, on: Int): String {
    val head = if (shown == total) {
        OnyxLocalization.format(if (total == 1) family.countOneKey else family.countKey, total)
    } else {
        OnyxLocalization.format("%d of %d", shown, total)
    }
    val switchedOn = OnyxLocalization.format(if (on == 1) family.onOneKey else family.onKey, on)
    return "$head · $switchedOn"
}

// ---------------------------------------------------------------------------------------------
// Skills
// ---------------------------------------------------------------------------------------------

@Composable
private fun SkillsBody(
    skills: List<DashboardSkill>?,
    problem: String?,
    query: String,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    busy: Set<String>,
    onBrowse: () -> Unit,
    onNew: () -> Unit,
    onOpen: (DashboardSkill) -> Unit,
    onToggle: (DashboardSkill, Boolean) -> Unit,
) {
    SearchField(
        value = query,
        onValueChange = onQuery,
        placeholder = skills?.let { OnyxLocalization.format("Search %d skills", it.size) }
            ?: OnyxLocalization["Skills"],
    )
    // Two gestures, two pills: the **catalogue** (grey, secondary) and **writing** — the only
    // saturated color left on the screen is the family's creation gesture.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentPill(
            // A house label, not data: it goes through the central dictionary.
            text = "Browse hub",
            localize = true,
            symbol = Lucide.SquareKanban,
            onClick = onBrowse,
            variant = PillVariant.Secondary,
            size = PillSize.Small,
        )
        ContentPill(
            text = "New skill",
            localize = true,
            symbol = Lucide.Plus,
            onClick = onNew,
            variant = PillVariant.Secondary,
            size = PillSize.Small,
        )
    }
    problem?.let { FailureBanner(text = it, onRetry = onRetry) }
    // **The wait does not survive a failure.** A skeleton shown under the error message would say
    // "it's coming" for a read that is already over — the screen showed both at once
    // (bench reading, closed address). The message and its "Try again" stay alone.
    val loaded = skills ?: run {
        if (problem == null) OnyxLoadingRows(label = "Loading from Onyx…")
        return
    }
    val groups = remember(loaded, query) { skillGroups(loaded, query) }
    val shown = groups.values.flatten()
    CountLine(countLine(CapabilityFamily.SKILLS, loaded.size, shown.size, shown.count { it.enabled }))
    if (loaded.isEmpty()) {
        EmptyLine("No skills yet")
        return
    }
    if (groups.isEmpty()) {
        EmptyLine("Nothing matches this search.")
        return
    }
    groups.forEach { (category, rows) ->
        // **The section label in small grey capitals** from the reference — and, below it, a
        // **thin-outlined card** that holds the rows.
        ContentSectionLabel(
            text = if (category == OTHER_GROUP) OnyxLocalization["Other"] else category,
            modifier = Modifier.padding(
                start = OnyxSpace.CardPadding + OnyxSpace.Hair,
                end = OnyxSpace.CardPadding,
                top = OnyxBase.Loose,
                bottom = OnyxSpace.Half,
            ),
        )
        ContentCard(padding = 0.dp) {
            rows.forEachIndexed { index, skill ->
                // The divider is **inset**: it starts from the label, after the tile, and stops
                // before the right edge — no border between two rows.
                if (index > 0) ContentRowDivider()
                SkillListRow(
                    skill = skill,
                    badge = provenanceBadge(skill.provenance),
                    busy = skill.name in busy,
                    onOpen = { onOpen(skill) },
                    onToggle = { on -> onToggle(skill, on) },
                )
            }
        }
    }
}

/**
 * A skill: its name, its description, the opening that makes it read, and its switch.
 *
 * The name area opens the skill; the switch stays on the right, in its usual place, so
 * that a setting is not triggered while trying to read.
 */
@Composable
private fun SkillListRow(
    skill: DashboardSkill,
    /** LOT skill-thread-organization: "hub" / "learned" / "external", or `null`. */
    badge: String?,
    busy: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    ContentRow(
        // The area stays **named**: a screen reader announces "Open <name>" — that is what
        // the previous template's row announced, and a tappable area does not go mute
        // by changing template.
        modifier = Modifier.tapLabel(OnyxLocalization.format("Open %1\$s", skill.name)),
        // **A skill's name is written in monospace**: it is a technical identifier, and
        // it is the reference's signature (`CapabilitiesPage.kt:482`, `code` font).
        title = skill.name,
        localizeTitle = false,
        monoTitle = true,
        subtitle = skill.description.takeIf { it.isNotBlank() }
            ?.let { OnyxLocalization.translateParagraphs(it) },
        // The anatomy's grey tile, with the subject's icon (the sparkle).
        symbol = Lucide.Sparkles,
        badge = badge?.let { tag ->
            {
                // The reference's **count badge**: "learned" takes the accent tint,
                // the others stay discreet.
                ContentCountBadge(tag, accent = tag == OnyxLocalization["learned"])
            }
        },
        onClick = onOpen,
        trailing = {
            OnyxSwitch(checked = skill.enabled, onCheckedChange = onToggle, enabled = !busy)
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------------------------

@Composable
private fun ToolsBody(
    toolsets: List<GatewayToolset>?,
    problem: String?,
    query: String,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    busy: Set<String>,
    expanded: Set<String>,
    onExpand: (String) -> Unit,
    onToggle: (GatewayToolset, Boolean) -> Unit,
) {
    SearchField(
        value = query,
        onValueChange = onQuery,
        placeholder = toolsets?.let { OnyxLocalization.format("Search %d toolsets", it.size) }
            ?: OnyxLocalization["Toolsets"],
    )
    problem?.let { FailureBanner(text = it, onRetry = onRetry) }
    val loaded = toolsets ?: run {
        if (problem == null) OnyxLoadingRows(label = "Loading from Onyx…")
        return
    }
    val shown = remember(loaded, query) { toolsetMatches(loaded, query) }
    CountLine(
        countLine(
            CapabilityFamily.TOOLS,
            loaded.size,
            shown.size,
            shown.count { it.enabled },
        ),
    )
    if (loaded.isEmpty()) {
        EmptyLine("No toolsets on this Hermes.")
        return
    }
    if (shown.isEmpty()) {
        EmptyLine("Nothing matches this search.")
        return
    }
    // A **thin-outlined card** that holds the rows; the dividers are **inset**.
    ContentCard(padding = 0.dp) {
        shown.forEachIndexed { index, toolset ->
            if (index > 0) ContentRowDivider()
            ToolsetRow(
                toolset = toolset,
                open = toolset.name in expanded,
                busy = toolset.name in busy,
                onExpand = { onExpand(toolset.name) },
                onToggle = { on -> onToggle(toolset, on) },
            )
        }
    }
}

/**
 * A toolset: its **grey tile**, its name, its description, **the count badge** of
 * its tools ("2 tools" — a count that comes from the list itself, never a separately kept
 * number), its disclosure, and its switch.
 *
 * The disclosure writes the **tool names in monospace**: they are technical identifiers
 * (`web_search`, `web_extract`), and that is the reference's signature.
 */
@Composable
private fun ToolsetRow(
    toolset: GatewayToolset,
    open: Boolean,
    busy: Boolean,
    onExpand: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        ContentRow(
        // The state (expanded / collapsed) stays **announced**, in addition to the arrow that shows it:
        // that is what the previous row carried in its description.
        modifier = Modifier.tapLabel(
            "${OnyxLocalization[toolset.title]}, ${
                if (open) OnyxLocalization["Expanded"] else OnyxLocalization["Collapsed"]
            }",
        ),
            title = toolset.title,
            subtitle = toolset.description?.takeIf { it.isNotBlank() }
                ?.let { OnyxLocalization.translateParagraphs(it) },
            symbol = Lucide.Wrench,
            badge = if (toolset.tools.isEmpty()) null else {
                {
                    ContentCountBadge(
                        OnyxLocalization.format(
                            if (toolset.tools.size == 1) "%d tool" else "%d tools",
                            toolset.tools.size,
                        ),
                    )
                }
            },
            onClick = onExpand,
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnyxIcon(
                        symbol = if (open) Symbols.ChevronDown else Lucide.ChevronRight,
                        tint = iconInk(theme, theme.formPageColor),
                        size = 12.dp,
                    )
                    OnyxSwitch(checked = toolset.enabled, onCheckedChange = onToggle, enabled = !busy)
                }
            },
        )
        if (open) {
            if (toolset.tools.isEmpty()) {
                BasicText(
                    text = "No tools listed for this toolset.",
                    style = theme.caption.copy(color = bodyInk(theme, theme.formPageColor)),
                    modifier = Modifier.padding(
                        start = OnyxSpace.CardPadding + Anatomy.DividerInsetWithTile,
                        bottom = OnyxBase.Loose,
                    ),
                )
            } else {
                toolset.tools.forEach { tool ->
                    BasicText(
                        text = tool,
                        localize = false,
                        // A tool's name is an identifier: monospace, like the source.
                        style = contentMonoStyle(FontWeight.Normal, bodyInk(theme, theme.formPageColor)),
                        modifier = Modifier.padding(
                            start = OnyxSpace.CardPadding + Anatomy.DividerInsetWithTile,
                            top = OnyxSpace.GlyphGap,
                            bottom = OnyxSpace.GlyphGap,
                        ),
                    )
                }
                Spacer(Modifier.height(OnyxBase.Loose))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// MCP servers
// ---------------------------------------------------------------------------------------------

@Composable
private fun McpBody(
    servers: List<McpServer>?,
    problem: String?,
    query: String,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    busy: Set<String>,
    onAdd: () -> Unit,
    onOpen: (McpServer) -> Unit,
    onToggle: (McpServer, Boolean) -> Unit,
    onTest: (McpServer) -> Unit,
) {
    SearchField(
        value = query,
        onValueChange = onQuery,
        placeholder = servers?.let { OnyxLocalization.format("Search %d MCP servers", it.size) }
            ?: OnyxLocalization["MCP"],
    )
    // The **grey pill** of the secondary action, like the skills catalogue.
    ContentPill(
        text = "Add an MCP source",
        localize = true,
        symbol = Lucide.Plus,
        onClick = onAdd,
        variant = PillVariant.Secondary,
        size = PillSize.Small,
    )
    problem?.let { FailureBanner(text = it, onRetry = onRetry) }
    val loaded = servers ?: run {
        if (problem == null) OnyxLoadingRows(label = "Loading from Onyx…")
        return
    }
    val shown = remember(loaded, query) { mcpMatches(loaded, query) }
    CountLine(
        countLine(
            CapabilityFamily.MCP,
            loaded.size,
            shown.size,
            shown.count { it.enabled },
        ),
    )
    if (loaded.isEmpty()) {
        // The empty state says **what to do**, not only that there is nothing.
        EmptyLine("No MCP servers on this Hermes.")
        EmptyLine("Nothing here yet. Use “Add an MCP source” above.")
        return
    }
    if (shown.isEmpty()) {
        EmptyLine("Nothing matches this search.")
        return
    }
    ContentCard(padding = 0.dp) {
        shown.forEachIndexed { index, entry ->
            if (index > 0) ContentRowDivider()
            McpListRow(
                entry = entry,
                busy = entry.name in busy,
                onOpen = { onOpen(entry) },
                onToggle = { on -> onToggle(entry, on) },
                onTest = { onTest(entry) },
            )
        }
    }
}

/**
 * An MCP server row: its **grey tile**, its name, its shape (transport and target) in
 * monospace — it is an address —, the test that reads its tools, the opening of its detail sheet,
 * and its switch.
 *
 * A server brought by a plug-in shows a padlock in place of the switch: its state follows
 * the plug-in, and the screen says so rather than offering a setting with no effect.
 */
@Composable
private fun McpListRow(
    entry: McpServer,
    busy: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    ContentRow(
        title = entry.name,
        localizeTitle = false,
        monoTitle = true,
        subtitle = "${OnyxLocalization[mcpTransportLabel(entry)]} · ${entry.target}",
        subtitleMono = true,
        symbol = Lucide.Plug,
        onClick = onOpen,
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContentPill(
                    text = "Test",
                    localize = true,
                    symbol = Lucide.RefreshCw,
                    onClick = onTest,
                    variant = PillVariant.Outline,
                    size = PillSize.Small,
                    enabled = !busy,
                )
                if (entry.plugin == null) {
                    OnyxSwitch(checked = entry.enabled, onCheckedChange = onToggle, enabled = !busy)
                } else {
                    OnyxIcon(
                        symbol = Symbols.LockOutline,
                        tint = iconInk(theme, theme.formPageColor),
                        size = 15.dp,
                        contentDescription = "Set by the plugin",
                    )
                }
            }
        },
    )
}
