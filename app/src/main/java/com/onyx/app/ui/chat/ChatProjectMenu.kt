package com.onyx.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ChatControlsApi
import com.onyx.app.data.net.ChatProjectsApi
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServerProject
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.projectFolderName
import com.onyx.app.data.net.projectMoveTargets
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import kotlinx.coroutines.CancellationException

/** Le dernier arbre de projets lu pour la conversation ouverte (`ProjectDirectory`). */
class ChatProjectDirectory internal constructor(
    private val rpc: ServeRpc,
    private val projectsApi: ChatProjectsApi,
    private val controlsApi: ChatControlsApi,
) {
    var folders by mutableStateOf<List<ServerProject>>(emptyList())
        private set
    var currentName by mutableStateOf<String?>(null)
        private set

    suspend fun refresh() {
        val tree = projectsApi.tree()
        folders = projectMoveTargets(tree, null)
        currentName = controlsApi.read(currentStoredId).projectName
    }

    private var currentStoredId: String = ""

    fun stored(id: String) {
        currentStoredId = id
    }

    suspend fun move(project: ServerProject) {
        val path = project.path ?: return
        projectsApi.move(currentStoredId, path)
        currentName = project.label
        refresh()
    }

    suspend fun create(name: String) {
        val place = projectsApi.create(name)
        projectsApi.move(currentStoredId, place.path)
        currentName = place.name
        refresh()
    }

    fun close() = rpc.close()
}

/** Charge les projets à l'ouverture de la conversation et ferme le canal en la quittant. */
@Composable
fun rememberChatProjectDirectory(server: ServerConfig, stored: String?): ChatProjectDirectory? {
    val available = server.transport == Transport.HERMES_SERVE && server.hasServeCredentials && !stored.isNullOrEmpty()
    val directory = remember(server.configSignature, stored, available) {
        if (!available) {
            null
        } else {
            val rpc = ServeRpc(server)
            ChatProjectDirectory(
                rpc = rpc,
                projectsApi = ChatProjectsApi(rpc, ServeFilesApi(ServeClient(server))),
                controlsApi = ChatControlsApi(rpc),
            ).also { it.stored(stored) }
        }
    }
    LaunchedEffect(directory) {
        if (directory == null) return@LaunchedEffect
        try {
            directory.refresh()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Comme sur la référence, un échec de simple lecture laisse seulement « New Project… ».
        }
    }
    DisposableEffect(directory) { onDispose { directory?.close() } }
    return directory
}

/** Le sous-menu « Add to Project » / « Move to Project », destinations et création. */
@Composable
fun ChatProjectMenu(
    directory: ChatProjectDirectory,
    onMove: (ServerProject) -> Unit,
    onNewProject: () -> Unit,
    onDismiss: () -> Unit,
) {
    val targets = projectMoveTargets(directory.folders, directory.currentName)
    OnyxMenu(
        items = buildList {
            targets.forEach { project ->
                add(MenuItem(project.label, localize = false) { onMove(project) })
            }
            add(MenuItem("New Project…", Symbols.FolderBadgePlus, onSelect = onNewProject))
        },
        onDismiss = onDismiss,
    )
}

/** Le nom du nouveau projet et l'alerte d'échec qui accompagnent le menu. */
@Composable
fun ChatProjectPrompt(
    naming: Boolean,
    problem: String?,
    onNamingChange: (Boolean) -> Unit,
    onProblemChange: (String?) -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember(naming) { mutableStateOf("") }
    if (naming) {
        OnyxPrompt(
            title = "New Project",
            message = "A folder of that name is made in ~/Projects on your server, and this conversation works in it from now on.",
            placeholder = "Name",
            value = name,
            onValueChange = { name = it },
            confirmLabel = "Create",
            confirmEnabled = projectFolderName(name) != null,
            onConfirm = {
                val typed = name
                name = ""
                onNamingChange(false)
                onCreate(typed)
            },
            onDismiss = {
                name = ""
                onNamingChange(false)
            },
        )
    }
    if (problem != null) {
        OnyxAlert(
            title = "Couldn't add it to a project",
            message = problem,
            localizeMessage = false,
            actions = listOf(AlertAction("OK") { onProblemChange(null) }),
            onDismiss = { onProblemChange(null) },
        )
    }
}

internal fun projectProblemReason(error: Exception): String =
    (error as? TransportError)?.userMessage() ?: error.message ?: OnyxLocalization["unknown error"]
