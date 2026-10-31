package com.onyx.app.ui.conversations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ChatProjectsApi
import com.onyx.app.data.net.ChatSessionActions
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServerProject
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.projectFolderName
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxPrompt

/**
 * Ce qu'on fait à un projet et à ses conversations, par les mêmes routes que la référence :
 * `projects.create` / `projects.update` / `projects.delete` (le RPC du Dashboard) et
 * `session.workspace.move` pour déplacer, `api/sessions/{id}` (PATCH) et `session.delete` pour
 * archiver et supprimer.
 *
 * Un canal par geste, refermé aussitôt : ces actions sont rares, et garder un socket ouvert pour
 * elles laisserait une connexion pendante derrière chaque écran.
 */
object ProjectManage {

    suspend fun <T> withProjects(config: ServerConfig, block: suspend (ChatProjectsApi) -> T): T {
        val rpc = ServeRpc(config)
        return try {
            block(ChatProjectsApi(rpc, ServeFilesApi(ServeClient(config))))
        } finally {
            rpc.close()
        }
    }

    suspend fun <T> withSessions(config: ServerConfig, block: suspend (ChatSessionActions) -> T): T {
        val rpc = ServeRpc(config)
        return try {
            block(ChatSessionActions(rpc, ServeClient(config)))
        } finally {
            rpc.close()
        }
    }

    suspend fun rename(config: ServerConfig, id: String, name: String) =
        withProjects(config) { it.rename(id, name) }

    /** Crée le projet (son dossier dans `~/Projects` du serveur) et rend son nom et son chemin. */
    suspend fun create(config: ServerConfig, name: String) =
        withProjects(config) { it.create(name) }

    suspend fun remove(config: ServerConfig, id: String) =
        withProjects(config) { it.remove(id) }

    /** Archive les conversations choisies, une par une : la route en prend une à la fois. */
    suspend fun archive(config: ServerConfig, ids: Set<String>) =
        withSessions(config) { actions -> ids.forEach { actions.archive(it) } }

    /** Supprime les conversations choisies ; elles partent du registre, pour tous les clients. */
    suspend fun delete(config: ServerConfig, ids: Set<String>) =
        withSessions(config) { actions -> ids.forEach { actions.delete(it) } }

    /**
     * **Les sessions serveur d'un lot de conversations** : celles qui ont parlé en portent une
     * (`serverSessionId`), les autres n'en ont pas — il n'y a rien à retirer au serveur. C'est le
     * pont entre la liste du téléphone (qui connaît ses conversations) et la route `session.delete`
     * (qui ne connaît que les sessions du registre). Pur, donc mesurable.
     */
    fun serverSessionIds(conversations: List<Conversation>): Set<String> =
        conversations.mapNotNull { it.serverSessionId?.takeIf { id -> id.isNotBlank() } }.toSet()

    /** Déplace les conversations choisies dans ce projet : son dossier devient leur `cwd`. */
    suspend fun move(config: ServerConfig, ids: Set<String>, to: ServerProject) {
        val path = to.path ?: return
        withProjects(config) { actions -> ids.forEach { actions.move(it, path) } }
    }

    /** La raison d'un échec, dans les mots de l'app : le message d'un transport, sinon le sien. */
    fun reason(error: Throwable): String =
        (error as? TransportError)?.userMessage() ?: error.message ?: OnyxLocalization["unknown error"]
}

/**
 * Les feuilles de la gestion des projets, **une seule fois** pour toute l'app : la liste des
 * conversations et l'écran d'un projet les ouvrent tous les deux, et c'est l'appelant qui fait les
 * appels — un seul endroit tient donc l'état et le canal.
 *
 * Les mots sont ceux de la référence, un par un : « New Project » avec son dossier dans
 * `~/Projects`, « Rename project » (« its folder keeps its name »), et la confirmation
 * « Remove “X” from Projects? » qui rappelle que le dossier et les conversations restent.
 */
@Composable
fun ProjectDialogs(
    creating: Boolean,
    onCreatingChange: (Boolean) -> Unit,
    renaming: ServerProject?,
    onRenamingChange: (ServerProject?) -> Unit,
    removing: ServerProject?,
    onRemovingChange: (ServerProject?) -> Unit,
    problem: String?,
    onProblemChange: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (ServerProject, String) -> Unit,
    onRemove: (ServerProject) -> Unit,
) {
    if (creating) {
        var name by remember { mutableStateOf("") }
        OnyxPrompt(
            title = "New Project",
            message = "A folder of that name is made in ~/Projects on your server. The project's conversations work in it.",
            placeholder = "Name",
            value = name,
            onValueChange = { name = it },
            confirmLabel = "Create",
            confirmEnabled = projectFolderName(name) != null,
            onConfirm = {
                val typed = name
                name = ""
                onCreatingChange(false)
                onCreate(typed)
            },
            onDismiss = {
                name = ""
                onCreatingChange(false)
            },
        )
    }

    renaming?.let { project ->
        // Le nom part de celui du projet, comme le `TextField` de la référence.
        var name by remember(project.id) { mutableStateOf(project.label) }
        OnyxPrompt(
            title = "Rename project",
            message = "Its folder keeps its name.",
            placeholder = "Name",
            value = name,
            onValueChange = { name = it },
            confirmLabel = "Save",
            confirmEnabled = projectFolderName(name) != null,
            onConfirm = {
                val typed = name
                onRenamingChange(null)
                onRename(project, typed)
            },
            onDismiss = { onRenamingChange(null) },
        )
    }

    removing?.let { project ->
        OnyxAlert(
            // Le titre nomme le projet et la question de la référence : « Retirer « X » des projets ? »
            title = OnyxLocalization.format("Remove “%1\$s” from Projects?", project.displayLabel),
            message = "Its folder and its conversations stay on the server. The conversations just aren't grouped under it any more.",
            actions = listOf(
                AlertAction("Remove Project", destructive = true) {
                    onRemovingChange(null)
                    onRemove(project)
                },
                AlertAction("Cancel") { onRemovingChange(null) },
            ),
            onDismiss = { onRemovingChange(null) },
        )
    }

    problem?.let { message ->
        OnyxAlert(
            title = "Couldn't do it",
            message = message,
            localizeMessage = false,
            actions = listOf(AlertAction("OK") { onProblemChange(null) }),
            onDismiss = { onProblemChange(null) },
        )
    }
}
