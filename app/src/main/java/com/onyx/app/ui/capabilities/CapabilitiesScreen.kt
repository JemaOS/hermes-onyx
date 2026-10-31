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
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.AdaptiveSegmented
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.chat.components.provenanceBadge
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.OnyxSocle
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
 * Les trois familles de capacités du profil, dans l'ordre du sélecteur.
 *
 * Chaque famille porte les **clés** de son compte et de son compte d'allumées, au singulier comme
 * au pluriel : le français ne dit pas « 1 outils » ni « 1 jeux d'outils ». Les libellés eux-mêmes
 * viennent du dictionnaire central.
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
 * « Capacités » — ce que l'agent peut utiliser dans les nouvelles conversations : ses compétences,
 * ses jeux d'outils et ses serveurs MCP.
 *
 * Les trois listes viennent du Dashboard, **par profil** : `GET api/skills`, `GET api/tools/toolsets`
 * et `GET api/mcp/servers`. Les bascules écrivent la configuration du profil
 * (`PUT api/skills/toggle`, `PUT api/tools/toolsets/{nom}`, `PUT api/mcp/servers/{nom}/enabled`) et
 * **s'appliquent à partir de la conversation suivante** — la ligne de note le dit, et rien ici ne
 * prétend changer la conversation en cours.
 *
 * Trois règles tiennent l'écran honnête :
 *
 * 1. **Le compte affiché est celui de la liste** : le sélecteur et la ligne de compte lisent la
 *    liste chargée (et filtrée), jamais un nombre tenu à part ; un interrupteur change le compte
 *    d'allumées d'un coup, parce qu'il change la liste.
 * 2. **Un échec de chargement garde l'écran utilisable** : chaque famille porte son propre bandeau
 *    et son « Réessayer » ; une route muette ne cache pas les deux autres.
 * 3. **Rien n'est supposé** : un test qui échoue s'affiche comme un échec, un ajout refusé dit la
 *    phrase du serveur, et l'interrupteur revient en place si le serveur n'a pas pris le réglage —
 *    un état affiché qui ne serait pas celui du serveur serait un mensonge.
 */
@Composable
fun CapabilitiesScreen(
    server: ServerConfig,
    onBack: () -> Unit,
    /**
     * **L'écriture d'une compétence** : `null` en crée une, un nom ouvre l'éditeur de celle-ci.
     *
     * C'est la **seule** fonction que la tuile « Compétences » portait et que cet écran ne
     * reprenait pas — le catalogue *installe* une compétence du hub, il ne l'écrit pas. Depuis le
     * retrait de la tuile, `onEditSkill` vit ici : rien n'a été perdu au passage.
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

    var openServer by remember(server.id) { mutableStateOf<String?>(null) }
    var addingServer by remember { mutableStateOf(false) }
    var openSkill by remember(server.id) { mutableStateOf<DashboardSkill?>(null) }
    var catalogOpen by remember { mutableStateOf(false) }
    var catalog by remember(server.id) { mutableStateOf<List<CatalogSkill>?>(null) }
    var catalogProblem by remember(server.id) { mutableStateOf<String?>(null) }

    /** Le message d'un échec : la route absente est nommée, le reste vient du transport. */
    fun message(error: Throwable, missingRoute: String): String {
        val http = error as? TransportError
        if (http is TransportError.Http && http.status == 404) return OnyxLocalization[missingRoute]
        return http?.userMessage() ?: error.message.orEmpty()
    }

    // Les trois familles se lisent d'un coup : le compte de chacune s'affiche alors sur le
    // sélecteur dès l'ouverture, et changer d'onglet ne relance rien. Chaque échec est gardé à
    // part, sinon la famille muette effacerait le compte des deux autres.
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
                // Une installation change la liste du profil : on la relit plutôt que de la deviner.
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
                // Le serveur a écrit ; on relit la liste plutôt que de la deviner. La ligne ajoutée
                // est posée tout de suite pour que l'écran ne clignote pas, et la relecture la
                // remplacera par ce que la configuration porte vraiment.
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
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // LOT anatomie-des-contenus : le grand titre et son sous-titre de la référence
            // (« Capabilities » / « What the agent can use in new chats »). C'était une petite
            // barre d'app centrée, comme sur les tâches planifiées.
            SocleLargeHeader(
                title = "Capabilities",
                subtitle = "What the agent can use in new chats",
                onBack = onBack,
            )
            // Le sélecteur appartient au **contenu** : il tient dans la marge d'écran du socle, comme
            // le titre, le sous-titre et la recherche. Sans cette marge il allait jusqu'aux deux
            // bords (relevé : 4,2 dp de chaque côté) alors que tout le reste de l'écran est à 16 —
            // c'est le contrôle qui sortait du système, pas le reste.
            AdaptiveSegmented(
                options = CapabilityFamily.entries.map { family -> familyLabel(family, skills, toolsets, servers) },
                selectedIndex = CapabilityFamily.entries.indexOf(tab),
                onSelect = { index ->
                    // La recherche est celle de la famille qu'on regarde : changer d'onglet repart
                    // d'un champ vide plutôt que d'appliquer le mot de l'autre famille.
                    tab = CapabilityFamily.entries[index]
                    query = ""
                },
                modifier = Modifier.padding(
                    start = OnyxSocle.ScreenMargin,
                    end = OnyxSocle.ScreenMargin,
                ),
                // **La moitié active est un aplat d'accent**, son mot en blanc : la règle de la
                // pastille d'onglet et de la planche validée (`.segi.on{background:var(--accfill);
                // color:#fff}`). La pilule blanche sur piste grise n'était pas le système.
                filled = true,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = OnyxSocle.ScreenMargin,
                    end = OnyxSocle.ScreenMargin,
                    top = OnyxSocle.Loose,
                    bottom = OnyxSocle.Loose,
                ),
            verticalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
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
 * Le test d'un serveur : la sonde est posée vide (donc « en cours »), puis remplacée par **ce que
 * le serveur a réellement rendu** — un échec compris.
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
                    error = (error as? TransportError)?.userMessage() ?: error.message,
                )
            }
        onResult(probe)
    }
}

/** « Compétences 24 » : le libellé de la famille et le compte, dès qu'il est connu. */
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
 * La ligne de compte d'une famille : ce qu'elle contient, et combien est allumé **parmi ce qui est
 * montré**.
 *
 * Pendant une recherche, elle dit les deux — « 3 sur 24 compétences » — parce qu'un compte filtré
 * tout seul ferait croire que la famille a rétréci ; et le compte d'allumées porte alors sur les
 * lignes visibles, pour que les deux chiffres parlent de la même liste.
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
// Compétences
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
    // Deux gestes, deux pilules : le **catalogue** (gris, secondaire) et **l'écriture** — la seule
    // couleur saturée de l'écran reste le geste de création de la famille.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentPill(
            // Un libellé de la maison, pas une donnée : il passe par le dictionnaire central.
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
    // **L'attente ne survit pas à l'échec.** Un squelette affiché sous le message d'erreur dirait
    // « ça arrive » pour une lecture qui est déjà finie — l'écran montrait les deux à la fois
    // (relevé du banc, adresse fermée). Le message et son « Réessayer » restent seuls.
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
        // **Le libellé de section en petites capitales grises** de la référence — et, dessous, une
        // **carte à filet fin** qui contient les rangées.
        ContentSectionLabel(
            text = if (category == OTHER_GROUP) OnyxLocalization["Other"] else category,
            modifier = Modifier.padding(
                start = OnyxSpace.CardPadding + OnyxSpace.Hair,
                end = OnyxSpace.CardPadding,
                top = OnyxSocle.Loose,
                bottom = OnyxSpace.Half,
            ),
        )
        ContentCard(padding = 0.dp) {
            rows.forEachIndexed { index, skill ->
                // Le filet est **en retrait** : il part du libellé, après la vignette, et s'arrête
                // avant le bord droit — aucune bordure entre deux rangées.
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
 * Une compétence : son nom, sa description, l'ouverture qui la fait lire, et son interrupteur.
 *
 * La zone du nom ouvre la compétence ; l'interrupteur reste à droite, à sa place habituelle, pour
 * qu'un réglage ne se déclenche pas en cherchant à lire.
 */
@Composable
private fun SkillListRow(
    skill: DashboardSkill,
    /** LOT organisation-fil-competences : « hub » / « appris » / « externe », ou `null`. */
    badge: String?,
    busy: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    ContentRow(
        // La zone reste **nommée** : un lecteur d'écran annonce « Open <nom> » — c'est ce que
        // la rangée du gabarit précédent annonçait, et une zone cliquable ne devient pas muette
        // en changeant de gabarit.
        modifier = Modifier.tapLabel(OnyxLocalization.format("Open %1\$s", skill.name)),
        // **Le nom d'une compétence s'écrit en monospace** : c'est un identifiant technique, et
        // c'est la signature de la référence (`CapabilitiesPage.kt:482`, police `code`).
        title = skill.name,
        localizeTitle = false,
        monoTitle = true,
        subtitle = skill.description.takeIf { it.isNotBlank() }
            ?.let { OnyxLocalization.translateParagraphs(it) },
        // La vignette grise de l'anatomie, avec l'icône du sujet (l'étincelle).
        symbol = Lucide.Sparkles,
        badge = badge?.let { tag ->
            {
                // La **pastille de comptage** de la référence : « appris » prend la teinte d'accent,
                // les autres restent discrètes.
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
// Outils
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
    // Une **carte à filet fin** qui contient les rangées ; les filets sont **en retrait**.
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
 * Un jeu d'outils : sa **vignette grise**, son nom, sa description, **la pastille de comptage** de
 * ses outils (« 2 tools » — un compte qui vient de la liste elle-même, jamais d'un nombre tenu à
 * part), son dépli et son interrupteur.
 *
 * Le dépli écrit les **noms d'outils en monospace** : ce sont des identifiants techniques
 * (`web_search`, `web_extract`), et c'est la signature de la référence.
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
        // L'état (déplié / replié) reste **annoncé**, en plus de la flèche qui le montre :
        // c'est ce que la rangée précédente portait dans sa description.
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
                        bottom = OnyxSocle.Loose,
                    ),
                )
            } else {
                toolset.tools.forEach { tool ->
                    BasicText(
                        text = tool,
                        localize = false,
                        // Le nom d'un outil est un identifiant : monospace, comme la source.
                        style = contentMonoStyle(FontWeight.Normal, bodyInk(theme, theme.formPageColor)),
                        modifier = Modifier.padding(
                            start = OnyxSpace.CardPadding + Anatomy.DividerInsetWithTile,
                            top = OnyxSpace.GlyphGap,
                            bottom = OnyxSpace.GlyphGap,
                        ),
                    )
                }
                Spacer(Modifier.height(OnyxSocle.Loose))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Serveurs MCP
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
    // La **pilule grise** de l'action secondaire, comme le catalogue des compétences.
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
        // L'état vide dit **quoi faire**, pas seulement qu'il n'y a rien.
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
 * Une ligne de serveur MCP : sa **vignette grise**, son nom, sa forme (transport et cible) en
 * monospace — c'est une adresse —, le test qui lit ses outils, l'ouverture de sa fiche et son
 * interrupteur.
 *
 * Un serveur apporté par un greffon montre un cadenas à la place de l'interrupteur : son état suit
 * le greffon, et l'écran le dit plutôt que d'offrir un réglage sans effet.
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
