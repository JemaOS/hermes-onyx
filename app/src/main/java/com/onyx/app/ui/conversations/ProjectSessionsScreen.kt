package com.onyx.app.ui.conversations

import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ChatProjectsApi
import com.onyx.app.data.net.ProjectLane
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServerProject
import com.onyx.app.data.net.SessionSummary
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.OnyxCardRow
import com.onyx.app.ui.common.OnyxLazyFormPage
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.common.OnyxTroublePanel
import com.onyx.app.ui.common.looksLikeMissingRoute
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxProgressView
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce qu'un projet porte : ses voies, et l'attente ou l'erreur du RPC. */
data class ProjectSessionsState(
    val lanes: List<ProjectLane> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /**
     * Ce gateway ne connaît pas la route des sessions de projet (`projects.project_sessions`) :
     * ce n'est pas une panne, c'est une **absence** — l'écran le dit et ne promet rien.
     */
    val routeMissing: Boolean = false,
)

/**
 * Les sessions d'un projet, par `projects.project_sessions` — la même route que
 * `HermesServeClient.projectSessions` côté référence.
 */
class ProjectSessionsViewModel(
    private val config: ServerConfig,
    private val projectId: String,
    /** Les voies déjà lues par la liste : l'écran les montre tout de suite. */
    initialLanes: List<ProjectLane> = emptyList(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProjectSessionsState(
            lanes = initialLanes,
            loading = initialLanes.isEmpty(),
        ),
    )
    val state: StateFlow<ProjectSessionsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.lanes.isEmpty(), error = null, routeMissing = false) }
            val rpc = ServeRpc(config)
            try {
                val project = ChatProjectsApi(rpc, ServeFilesApi(ServeClient(config))).projectSessions(projectId)
                _state.update {
                    // Un rafraîchissement qui échoue ne doit pas vider un dossier déjà affiché.
                    // Un `null` sans exception veut dire la même chose qu'un refus : la route
                    // n'existe pas sur ce gateway — l'écran le dira au lieu de laisser croire que
                    // le projet est vide.
                    it.copy(
                        lanes = project?.lanes ?: it.lanes,
                        loading = false,
                        routeMissing = project == null && it.lanes.isEmpty(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.userMessage(),
                        routeMissing = looksLikeMissingRoute(error),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: OnyxLocalization["Couldn't load the project"],
                        routeMissing = looksLikeMissingRoute(error),
                    )
                }
            } finally {
                rpc.close()
            }
        }
    }
}

/**
 * « Les sessions d'un projet », port de `ProjectSessionsView`
 * (Echo/Views/ConversationsView:2031).
 *
 * Un projet, ses voies (copie de dépôt et branche) et leurs conversations, la plus récente
 * d'abord, avec la recherche de la référence. Une adaptation assumée : Android n'a pas de transport
 * `api/sessions`, donc une rangée ne peut pas rouvrir la transcription — elle ouvre la fiche
 * d'usage de la session (`SessionUsageView`), qui dit ce qu'elle a consommé.
 *
 * Là où la **référence** s'arrête (elle ne sait déplacer une conversation que par le menu d'une
 * rangée de la liste, et ne gère un projet que par le menu d'une rangée de projet), cet écran-ci
 * ajoute la gestion : la **sélection multiple** de la liste des conversations — la même
 * `SelectionBar`, pas une seconde — y archive, supprime et **déplace vers un autre projet** ; et le
 * menu « … » de sa barre crée, renomme ou retire un projet. Le retrait est exactement celui de la
 * référence : le dossier et les conversations restent sur le serveur, les conversations ne sont
 * simplement plus réunies sous le projet.
 */
@Composable
fun ProjectSessionsScreen(
    project: ServerProject,
    viewModel: ProjectSessionsViewModel,
    onOpenSession: (SessionSummary) -> Unit,
    onBack: () -> Unit,
    /** Les autres projets du serveur : où une conversation peut aller. */
    moveTargets: List<ServerProject> = emptyList(),
    /** Archive les conversations choisies (identifiants du serveur) ; sans effet par défaut. */
    onArchive: suspend (Set<String>) -> Unit = {},
    /** Supprime les conversations choisies : elles partent du registre, pour tous les clients. */
    onDelete: suspend (Set<String>) -> Unit = {},
    /** Déplace les conversations choisies dans ce projet. */
    onMove: suspend (Set<String>, ServerProject) -> Unit = { _, _ -> },
    /** « Nouveau projet » du menu : la feuille vit chez l'appelant, qui fait l'appel. */
    onRequestNewProject: () -> Unit = {},
    /** « Renommer le projet » du menu. */
    onRequestRenameProject: (ServerProject) -> Unit = {},
    /** « Supprimer le projet » du menu : la confirmation vit chez l'appelant. */
    onRequestRemoveProject: (ServerProject) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    // La sélection multiple : les mêmes trois états que la liste des conversations.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var projectMenuOpen by remember { mutableStateOf(false) }
    var moveMenuOpen by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Ce que l'écran montre en titre : le nom du groupe par défaut (« Home », celui du serveur), un
    // vrai projet garde son nom — c'est un dossier sur le serveur, jamais un libellé traduit.
    val title = project.displayLabel

    val lanes = remember(state.lanes, query) {
        val needle = query.trim().lowercase()
        state.lanes.map { lane ->
            val rows = lane.sessions
                .filter { session ->
                    needle.isEmpty() ||
                        session.displayTitle.lowercase().contains(needle) ||
                        session.preview.orEmpty().lowercase().contains(needle)
                }
                .sortedByDescending { it.lastActiveMillis ?: 0L }
            lane.copy(sessions = rows)
        }.filter { it.sessions.isNotEmpty() }
    }
    // Tout ce qui est montré, quelle que soit la voie : la portée de « tout sélectionner ».
    val visible = lanes.flatMap { lane -> lane.sessions.map { session -> session.id } }.toSet()
    val allSelected = visible.isNotEmpty() && selected.containsAll(visible)

    /** Après une action groupée : la sélection se referme, puis le projet se relit. */
    fun afterBulk(action: suspend () -> Unit) {
        selecting = false
        selected = emptySet()
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                problem = (error as? TransportError)?.userMessage() ?: error.message
            }
            viewModel.refresh()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                // Le compte des rangées cochées prend la place du titre, comme dans la liste.
                title = if (selecting) "${selected.size} selected" else title,
                localizeTitle = selecting,
                onBack = onBack,
                action = if (selecting) "Cancel" else "Select",
                onAction = {
                    if (selecting) {
                        selecting = false
                        selected = emptySet()
                    } else {
                        selecting = true
                    }
                },
                // Le menu du projet, « … », à côté de « Sélectionner » : créer, renommer, retirer.
                trailing = if (selecting) {
                    null
                } else {
                    {
                        OnyxIcon(
                            symbol = Symbols.Ellipsis,
                            tint = theme.accent,
                            size = 20.dp,
                            contentDescription = "Project menu",
                            modifier = Modifier.onyxClickable { projectMenuOpen = true },
                        )
                    }
                },
            )
        }

        // Le menu du projet : « Nouveau projet » toujours, puis, pour un projet que quelqu'un a
        // fait (le `isOwn` de la référence), « Renommer » et « Supprimer le projet ».
        if (projectMenuOpen) {
            OnyxMenu(
                items = buildList {
                    add(MenuItem("New Project…", Symbols.FolderBadgePlus) { onRequestNewProject() })
                    if (project.isOwn) {
                        add(MenuItem("Rename project", Symbols.Pencil) { onRequestRenameProject(project) })
                        add(
                            MenuItem(
                                "Remove Project",
                                Symbols.Trash,
                                destructive = true,
                                groupBreak = true,
                            ) { onRequestRemoveProject(project) },
                        )
                    }
                },
                onDismiss = { projectMenuOpen = false },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(theme.fill, RoundedCornerShape(OnyxSpace.PanelCorner))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OnyxIcon(Symbols.MagnifyingGlass, tint = theme.secondaryText, size = 20.dp)
            SearchField(
                // « Rechercher dans <projet> » : le nom s'insère tel quel, la phrase se traduit.
                placeholder = OnyxLocalization.translate("Search in $title"),
                value = query,
                onValueChange = { query = it },
            )
        }

        // **La page est une liste paresseuse** : un projet de 500 sessions ne compose que ses
        // rangées visibles (défaut T9 de l'audit). La carte garde sa forme — `OnyxCardRow` en pose
        // la surface rangée par rangée — et les en-têtes de voie sont des `item` de la même liste.
        OnyxLazyFormPage(modifier = Modifier.weight(1f)) {
            state.error?.takeIf { lanes.isEmpty() }?.let { message ->
                item(key = "problem") {
                    if (state.routeMissing) {
                        // **Pas « réessayez », mais « ça n'est pas là »** : la route du gateway
                        // manque, donc un nouvel essai ne la fera pas apparaître. L'écran dit ce
                        // qu'elle apporterait, et laisse quand même le geste (un gateway peut être
                        // mis à jour entre-temps).
                        Column {
                            OnyxEmptyState(
                                symbol = Symbols.ExclamationTriangle,
                                title = "Not available on this gateway yet",
                                description = "This project's sessions come from the gateway route " +
                                    "projects.project_sessions, which this gateway does not serve. " +
                                    "Onyx shows nothing here rather than an empty project.",
                                localizeDescription = false,
                            )
                            OnyxFormSection {
                                OnyxRow(onClick = viewModel::refresh) {
                                    BasicText("Try Again", style = theme.body.copy(color = theme.accent))
                                }
                            }
                        }
                    } else {
                        // Panne de liaison ou refus : le panneau commun, avec « Réessayer ».
                        OnyxTroublePanel(problem = message, onRetry = viewModel::refresh)
                    }
                }
            }

            if (state.loading && lanes.isEmpty()) {
                item(key = "loading") {
                    OnyxLoadingRows(label = "Loading from Onyx…")
                }
            }

            if (!state.loading && lanes.isEmpty() && state.error == null) {
                item(key = "empty") {
                    OnyxFormSection {
                        OnyxRow {
                            OnyxIcon(Symbols.Folder, tint = theme.secondaryText, size = 18.dp)
                            BasicText(
                                // La phrase est **vraie** et dit l'échelle : ce n'est pas « aucun
                                // élément », c'est « ce projet-ci n'a encore rien reçu ».
                                text = "No conversations in this project yet",
                                style = theme.body.copy(color = theme.secondaryText),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        project.path?.let { path ->
                            OnyxRowDivider()
                            OnyxRow {
                                BasicText(
                                    text = "${
                                        OnyxLocalization["Conversations started here work in its folder on your server:"]
                                    } $path",
                                    localize = false,
                                    style = theme.footnote.copy(color = theme.secondaryText),
                                )
                            }
                        }
                    }
                }
            }

            lanes.forEach { lane ->
                // L'en-tête d'une voie : son dépôt, puis sa branche, quand il y en a plus d'une.
                // Le dépôt ne se redit pas quand la voie porte déjà son nom (« Home » et non
                // « Home / Home »), et le projet lui-même ne se redit pas non plus.
                val repo = lane.repoLabel?.takeIf { it != lane.label && it != project.label }
                if (lanes.size > 1 || lane.label != "main") {
                    item(key = "lane:" + lane.label) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            repo?.let { name ->
                                BasicText(
                                    text = name,
                                    localize = false,
                                    style = theme.footnote.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
                                )
                                BasicText(
                                    text = "/",
                                    localize = false,
                                    style = theme.footnote.copy(color = theme.tertiaryText, fontWeight = FontWeight.SemiBold),
                                )
                            }
                            BasicText(
                                text = lane.label,
                                localize = false,
                                style = theme.footnote.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
                itemsIndexed(lane.sessions, key = { _, session -> "session:" + session.id }) { index, session ->
                    OnyxCardRow(first = index == 0, last = index == lane.sessions.lastIndex) {
                        if (index > 0) OnyxRowDivider()
                        ProjectSessionRow(
                            session = session,
                            selecting = selecting,
                            checked = session.id in selected,
                            onToggle = {
                                selected = if (session.id in selected) selected - session.id else selected + session.id
                            },
                            onClick = {
                                if (selecting) {
                                    selected = if (session.id in selected) selected - session.id else selected + session.id
                                } else {
                                    onOpenSession(session)
                                }
                            },
                        )
                    }
                }
            }
        }

        // La barre de sélection de la liste des conversations, réutilisée **telle quelle** : elle y
        // gagne le déplacement groupé quand un autre projet peut recevoir les conversations.
        if (selecting) {
            SelectionBar(
                count = selected.size,
                allSelected = allSelected,
                onSelectAll = {
                    selected = if (allSelected) emptySet() else visible
                },
                onCancel = {
                    selecting = false
                    selected = emptySet()
                },
                onArchive = {
                    val ids = selected
                    afterBulk { onArchive(ids) }
                },
                onDelete = { confirmBulkDelete = true },
                onMove = if (moveTargets.isEmpty()) null else ({ moveMenuOpen = true }),
            )
        }
    }

    // « Déplacer N » : les autres projets du serveur, par leur nom — le dossier, lui, décide.
    if (moveMenuOpen) {
        val ids = selected
        OnyxMenu(
            items = moveTargets.map { target ->
                MenuItem(target.displayLabel, Symbols.Folder, localize = false) {
                    moveMenuOpen = false
                    afterBulk { onMove(ids, target) }
                }
            },
            onDismiss = { moveMenuOpen = false },
        )
    }

    // La confirmation de la suppression groupée, celle de la liste : les conversations partent du
    // registre pour tous les clients — ce n'est pas une suppression locale.
    if (confirmBulkDelete) {
        val count = selected.size
        OnyxAlert(
            title = "Delete $count conversation${if (count == 1) "" else "s"}?",
            message = "They are removed from Onyx's registry for every client.",
            actions = listOf(
                AlertAction("Delete", destructive = true) {
                    val ids = selected
                    confirmBulkDelete = false
                    afterBulk { onDelete(ids) }
                },
                AlertAction("Cancel") { confirmBulkDelete = false },
            ),
            onDismiss = { confirmBulkDelete = false },
        )
    }

    problem?.let { message ->
        OnyxAlert(
            title = "Couldn't do it",
            message = message,
            localizeMessage = false,
            actions = listOf(AlertAction("OK") { problem = null }),
            onDismiss = { problem = null },
        )
    }
}

/**
 * Une session du projet : son titre, son origine, sa date, ses messages et son aperçu — l'ordre
 * de la référence (`ProjectSessionsView.row`).
 *
 * La ligne de consommation (tokens, coût) n'est **pas** ici : elle ne figure dans aucune rangée de
 * la référence, qui la garde dans sa fiche « Usage & cost » — l'écran que cette rangée ouvre au tap.
 * Sur un fil large, elle était illisible (quatre compteurs et un prix sur la ligne des métadonnées).
 */
@Composable
private fun ProjectSessionRow(
    session: SessionSummary,
    selecting: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow(onClick = onClick) {
        if (selecting) {
            OnyxIcon(
                symbol = if (checked) Symbols.CheckmarkCircle else Symbols.Circle,
                tint = if (checked) theme.accent else theme.tertiaryText,
                size = 20.dp,
                contentDescription = if (checked) "Deselect" else "Select",
                modifier = Modifier.padding(end = 2.dp).onyxClickable(onClick = onToggle),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = session.displayTitle,
                localize = false,
                style = theme.body.copy(color = theme.primaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = session.sourceLabel,
                    localize = false,
                    style = theme.caption2.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .background(theme.fill, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                session.lastActiveMillis?.let { when_ ->
                    BasicText(
                        text = rowTime(when_),
                        style = theme.caption.copy(color = theme.secondaryText),
                    )
                }
                session.messageCount?.let { count ->
                    BasicText(text = "·", style = theme.caption.copy(color = theme.secondaryText))
                    BasicText(
                        text = "$count messages",
                        style = theme.caption.copy(color = theme.secondaryText),
                    )
                }
            }
            session.preview?.takeIf { it.isNotEmpty() }?.let { preview ->
                BasicText(
                    text = preview,
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Le champ de recherche de la référence : sa phrase grise tant qu'il est vide, le curseur en accent. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SearchField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            BasicText(
                text = placeholder,
                localize = false,
                style = TextStyle(color = theme.tertiaryText, fontSize = OnyxSocle.Body),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = theme.textColor, fontSize = OnyxSocle.Body),
            singleLine = true,
            cursorBrush = SolidColor(theme.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
