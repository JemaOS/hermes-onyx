package com.onyx.app.data.net

import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Un projet du Dashboard, tel que `projects.tree` le décrit. */
data class ServerProject(
    val id: String,
    val label: String,
    val path: String?,
    val isHome: Boolean = false,
    /** Le fourre-tout de `hermes serve`: « no project », qui n'est pas un dossier. */
    val isNoProject: Boolean = false,
    /** Combien de conversations le projet réunit. */
    val sessionCount: Int = 0,
    /** La dernière activité, en millisecondes. */
    val lastActive: Long? = null,
    /** Les voies du projet (copie de dépôt, branche) et leurs sessions. */
    val lanes: List<ProjectLane> = emptyList(),
) {
    /** Une conversation peut démarrer ici: un dossier, pas le fourre-tout ni le dossier personnel. */
    val canStart: Boolean get() = path != null && !isHome && !isNoProject

    companion object {
        /** Le libellé du fourre-tout de `hermes serve`, qui n'est pas un dossier. */
        const val NO_PROJECT_LABEL = "No project"
    }
}

/** Une voie d'un projet: `repos` → `groups` du RPC, et les sessions qu'elle porte. */
data class ProjectLane(
    val id: String,
    val label: String,
    val repoLabel: String? = null,
    val sessions: List<SessionSummary> = emptyList(),
)

data class ProjectPlace(val name: String, val path: String)

/** Le nom de dossier de `ServerProjects.projectFolderName`, borné à 80 caractères. */
fun projectFolderName(name: String): String? {
    var folder = name.lineSequence().joinToString(" ")
        .replace("/", "-")
        .replace(":", "-")
        .trim()
    while (folder.startsWith(".")) folder = folder.drop(1)
    folder = folder.trim().take(80)
    return folder.ifEmpty { null }
}

/** Les projets qui sont réellement un dossier, sans le fourre-tout « No project ». */
fun projectFolders(all: List<ServerProject>): List<ServerProject> =
    all.filter { !it.isHome && !it.isNoProject && !it.path.isNullOrEmpty() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

/** Le projet courant n'est pas une destination de déplacement. */
fun projectMoveTargets(all: List<ServerProject>, currentName: String?): List<ServerProject> =
    projectFolders(all).filter { it.label != currentName }

fun serverPathJoin(base: String, vararg parts: String): String =
    parts.fold(base.trimEnd('/')) { path, part -> path + "/" + part.trim('/') }

/**
 * Un projet lu sur `projects.tree` / `projects.project_sessions`.
 *
 * Comme `HermesServeClient.Project.init`: l'identifiant, le libellé, le dossier, et —
 * pour `project_sessions` — les voies (`repos` → `groups`) avec leurs sessions.
 */
internal fun serverProjectFromRow(row: JsonObject): ServerProject? {
    val id = row.firstString("id", "project_id") ?: return null
    val noProject = row["is_no_project"].asBool()
        ?: row["isNoProject"].asBool()
        ?: (id == "__no_project__")
    return ServerProject(
        id = id,
        label = if (noProject) ServerProject.NO_PROJECT_LABEL else (row.firstString("label", "name") ?: id.substringAfterLast('/')),
        path = if (noProject) null else row.strOrNull("path"),
        isHome = row["is_home"].asBool() ?: false,
        isNoProject = noProject,
        sessionCount = row.looseInt("session_count") ?: row.looseInt("sessionCount") ?: 0,
        lastActive = row.looseDouble("last_active")?.let { seconds ->
            if (kotlin.math.abs(seconds) < 100_000_000_000.0) (seconds * 1000).toLong() else seconds.toLong()
        },
        lanes = projectLanesFromRow(row),
    )
}

/** Les voies d'un projet: `repos` → `groups`, ou `groups` à la racine. */
internal fun projectLanesFromRow(row: JsonObject): List<ProjectLane> {
    val repos = row.array("repos")
    if (repos.isEmpty()) {
        return row.array("groups").mapNotNull { element -> element.asObject()?.let { laneFromRow(it, null) } }
    }
    return repos.flatMap { element ->
        val repo = element.asObject() ?: return@flatMap emptyList()
        val repoLabel = repo.firstString("label", "name")
        repo.array("groups").mapNotNull { group -> group.asObject()?.let { laneFromRow(it, repoLabel) } }
    }
}

private fun laneFromRow(row: JsonObject, repoLabel: String?): ProjectLane? {
    val id = row.firstString("id", "lane_id") ?: return null
    return ProjectLane(
        id = id,
        label = row.firstString("label", "name") ?: "main",
        repoLabel = repoLabel,
        sessions = row.array("sessions").mapNotNull { element ->
            element.asObject()?.let(::sessionSummaryFromRow)
        },
    )
}

/** Création et déplacement de projets par les mêmes routes RPC que. */
class ChatProjectsApi(
    private val rpc: ServeRpc,
    private val files: ServeFilesApi,
) {
    suspend fun tree(): List<ServerProject> {
        val result = rpc.call("projects.tree", buildJsonObject { put("preview_limit", 0) }).asObject()
        return result?.array("projects").orEmpty().mapNotNull { element ->
            element.asObject()?.let(::serverProjectFromRow)
        }
    }

    /**
     * Un projet entier, ses voies et leurs sessions: `projects.project_sessions`, la route que
     * `HermesServeClient.projectSessions` appelle.
     */
    suspend fun projectSessions(id: String, timeoutMillis: Long = 30_000): ServerProject? {
        val result = rpc.call(
            "projects.project_sessions",
            buildJsonObject { put("project_id", id) },
            timeoutMillis = timeoutMillis,
        ).asObject()
        return result?.obj("project")?.let(::serverProjectFromRow)
    }

    suspend fun create(name: String): ProjectPlace {
        val clean = name.trim()
        val folderName = projectFolderName(clean)
            ?: throw TransportError.Malformed(OnyxLocalization["Give the project a name."])
        val home = files.folderPath(null)
        val path = serverPathJoin(home, "Projects", folderName)
        files.makeFolder(path)
        val result = rpc.call(
            "projects.create",
            buildJsonObject {
                put("name", clean)
                put("folders", buildJsonArray { add(JsonPrimitive(path)) })
                put("primary_path", path)
            },
        ).asObject()
        val project = result?.obj("project")
        return ProjectPlace(
            name = project?.strOrNull("name") ?: clean,
            path = project?.strOrNull("primary_path") ?: path,
        )
    }

    suspend fun move(stored: String, toPath: String) {
        rpc.call(
            "session.workspace.move",
            buildJsonObject {
                put("session_key", stored)
                put("cwd", toPath)
            },
        )
    }
}
