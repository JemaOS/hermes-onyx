package com.onyx.app.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxProgressView
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
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

/** Ce qu'un projet porte: ses voies, et l'attente ou l'erreur du RPC. */
data class ProjectSessionsState(
    val lanes: List<ProjectLane> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Les sessions d'un projet, par `projects.project_sessions` — la même route que
 * `HermesServeClient.projectSessions`.
 */
class ProjectSessionsViewModel(
    private val config: ServerConfig,
    private val projectId: String,
    /** Les voies déjà lues par la liste: l'écran les montre tout de suite. */
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
            _state.update { it.copy(loading = it.lanes.isEmpty(), error = null) }
            val rpc = ServeRpc(config)
            try {
                val project = ChatProjectsApi(rpc, ServeFilesApi(ServeClient(config))).projectSessions(projectId)
                _state.update {
                    // Un rafraîchissement qui échoue ne doit pas vider un dossier déjà affiché.
                    it.copy(lanes = project?.lanes ?: it.lanes, loading = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                _state.update { it.copy(loading = false, error = error.userMessage()) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: OnyxLocalization["Couldn't load the project"],
                    )
                }
            } finally {
                rpc.close()
            }
        }
    }
}

/**
 * « Les sessions d'un projet », comme `ProjectSessionsView`
 *
 * Un projet, ses voies (copie de dépôt et branche) et leurs conversations, la plus récente
 * d'abord, avec la recherche. Une adaptation assumée: Android n'a pas de transport
 * `api/sessions`, donc une rangée ne peut pas rouvrir la transcription — elle ouvre la fiche
 * d'usage de la session (`SessionUsageView`), qui dit ce qu'elle a consommé.
 */
@Composable
fun ProjectSessionsScreen(
    project: ServerProject,
    viewModel: ProjectSessionsViewModel,
    onOpenSession: (SessionSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = project.label, onBack = onBack, localizeTitle = false)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(theme.fill, RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OnyxIcon(Symbols.MagnifyingGlass, tint = theme.secondaryText, size = 20.dp)
            SearchField(placeholder = "Search in ${project.label}", value = query, onValueChange = { query = it })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp),
        ) {
            state.error?.takeIf { lanes.isEmpty() }?.let { message ->
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                        BasicText(
                            text = message,
                            localize = false,
                            style = theme.footnote.copy(color = theme.warning),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OnyxRowDivider()
                    OnyxRow(onClick = viewModel::refresh) {
                        BasicText("Try Again", style = theme.body.copy(color = theme.accent))
                    }
                }
            }

            if (state.loading && lanes.isEmpty()) {
                OnyxFormSection {
                    OnyxRow {
                        OnyxProgressView(size = 20.dp)
                        BasicText(
                            text = "Loading from Onyx…",
                            style = theme.body.copy(color = theme.secondaryText),
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }

            if (!state.loading && lanes.isEmpty() && state.error == null) {
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.Folder, tint = theme.secondaryText, size = 18.dp)
                        BasicText(
                            text = "No conversations yet",
                            style = theme.body.copy(color = theme.secondaryText),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    project.path?.let { path ->
                        OnyxRowDivider()
                        OnyxRow {
                            BasicText(
                                text = "Conversations started here work in its folder on your server: $path",
                                localize = false,
                                style = theme.footnote.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
            }

            lanes.forEach { lane ->
                // L'en-tête d'une voie: son dépôt et sa branche, quand il y en a plus d'une.
                if (lanes.size > 1 || lane.label != "main") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        lane.repoLabel?.takeIf { it != project.label }?.let { repo ->
                            BasicText(
                                text = repo,
                                localize = false,
                                style = theme.footnote.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
                            )
                        }
                        BasicText(
                            text = lane.label,
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
                OnyxFormSection {
                    lane.sessions.forEachIndexed { index, session ->
                        if (index > 0) OnyxRowDivider()
                        ProjectSessionRow(session = session, onClick = { onOpenSession(session) })
                    }
                }
            }
        }
    }
}

/** Une session du projet: son titre, son origine, sa date, ses messages et son aperçu. */
@Composable
private fun ProjectSessionRow(session: SessionSummary, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    OnyxRow(onClick = onClick) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = session.displayTitle,
                localize = false,
                style = theme.body.copy(color = theme.primaryText),
                maxLines = 1,
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
                session.usageLine?.let { line ->
                    BasicText(
                        text = line,
                        localize = false,
                        style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
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

/** Le champ de recherche: sa phrase grise tant qu'il est vide, le curseur en accent. */
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
                style = TextStyle(color = theme.tertiaryText, fontSize = 17.sp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = theme.textColor, fontSize = 17.sp),
            singleLine = true,
            cursorBrush = SolidColor(theme.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
