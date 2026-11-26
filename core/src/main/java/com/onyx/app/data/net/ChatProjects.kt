package com.onyx.app.data.net

import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * L'identifiant du groupe par défaut — **celui du serveur** (`tui_gateway/project_tree.py`,
 * `NO_PROJECT_ID = "__no_project__"`) : la route qui liste ses conversations est la même que pour
 * un projet, il faut donc lui parler avec son identifiant.
 */
const val NO_PROJECT_ID = "__no_project__"

/**
 * Le nombre de conversations demandé au serveur, **le même pour l'arbre et pour la descente**.
 *
 * La référence le dit d'une ligne (`ProjectsApi.SESSION_LIMIT`) : « The same window as the tree, so
 * a chip's count matches what it opens. » Deux fenêtres différentes (l'arbre en demandait 2000 par
 * défaut, la descente 5000) suffisent à faire mentir un compte.
 */
const val PROJECT_SESSION_WINDOW = 2000

/**
 * Le **groupe par défaut** complété : « Home » porte tout ce qu'aucun projet ne réclame.
 *
 * Le serveur ne met dans son fourre-tout que ce qu'il a su placer : une conversation lancée en
 * ligne de commande, une exécution sans message, une session d'un genre qu'il ne range pas
 * (`oneshot`, `cron`, `kanban` — `_PROJECT_TREE_EXCLUDED_SOURCES`) n'entrent dans **aucun** de ses
 * groupes. Le volet, lui, doit toutes les montrer. D'où la règle, et elle est courte :
 *
 *     Home = toutes les conversations du registre − celles qu'un projet nommé réclame
 *
 * Par construction il n'y a donc **aucune orpheline** (`revendiquées ∪ Home = registre`), le compte
 * de chaque projet est **le nombre de conversations qu'il liste**, et Home les liste toutes quand
 * aucun projet n'en réclame aucune — ce que le propriétaire voit dans la référence (« Home »).
 *
 * [base] est le registre du serveur, ou `null` quand il ne répond pas : on rend alors les projets
 * du serveur tels quels, sans inventer de compte.
 */
internal fun withHomeGroup(projects: List<ServerProject>, base: Set<String>?): List<ServerProject> {
    if (base == null) return projects
    val claimed = projects.filterNot { it.isNoProject }.flatMapTo(LinkedHashSet()) { it.sessionIds }
    val homeIds = base - claimed
    val serverHome = projects.firstOrNull { it.isNoProject }
    val home = ServerProject(
        id = NO_PROJECT_ID,
        label = serverHome?.label?.takeIf { it.isNotBlank() } ?: ServerProject.NO_PROJECT_LABEL,
        path = null,
        isNoProject = true,
        isAuto = false,
        sessionCount = homeIds.size,
        lastActive = serverHome?.lastActive,
        sessionIds = homeIds,
    )
    return listOf(home) + projects.filterNot { it.isNoProject }
}

/** Les conversations que ces voies listent — ce que l'écran d'un projet montre vraiment. */
internal fun lanesSessionIds(lanes: List<ProjectLane>): Set<String> =
    lanes.flatMapTo(LinkedHashSet()) { lane -> lane.sessions.map { it.id } }

/**
 * Les conversations du registre qu'aucun groupe ne porte, et qu'il faut donc ranger dans le groupe
 * par défaut. Vide après [withHomeGroup] : c'est l'invariant du lot, et `HomeGroupTest` le mesure.
 */
internal fun unclaimedSessions(projects: List<ServerProject>, base: Set<String>): Set<String> {
    val covered = projects.flatMapTo(LinkedHashSet()) { it.sessionIds }
    return base - covered
}

/**
 * La descente d'un projet, **complétée** des conversations que le serveur n'y a pas rangées.
 *
 * Elles descendent dans la **première voie** : l'écran d'un projet ne change pas de forme, et rien
 * de ce que le serveur a rangé n'est déplacé. Le compte du projet est alors **recalculé sur ce
 * qu'il liste** — annoncé = listé, la règle du lot, et [unclaimedSessions] doit être vide après.
 */
internal fun ServerProject.filledWith(missing: List<SessionSummary>): ServerProject {
    if (missing.isEmpty()) return this
    val first = lanes.firstOrNull()
    val lanes = if (first == null) {
        listOf(ProjectLane(id = NO_PROJECT_ID, label = label, sessions = missing))
    } else {
        listOf(first.copy(sessions = first.sessions + missing)) + lanes.drop(1)
    }
    val ids = lanesSessionIds(lanes)
    return copy(lanes = lanes, sessionIds = ids, sessionCount = ids.size)
}

/** Un projet du Dashboard, tel que `projects.tree` le décrit. */
data class ServerProject(
    val id: String,
    val label: String,
    val path: String?,
    val isHome: Boolean = false,
    /** Le fourre-tout du serveur : « no project », qui n'est pas un dossier. */
    val isNoProject: Boolean = false,
    /**
     * Trouvé par Hermes (un dépôt où ses conversations ont tourné), pas fait par quelqu'un.
     * Dit par 0.21.0 et au-delà ; sans la clé, le projet compte comme trouvé, comme sur la référence.
     */
    val isAuto: Boolean = true,
    /** Combien de conversations le projet réunit — le nombre de [sessionIds], pas un total d'ailleurs. */
    val sessionCount: Int = 0,
    /** La dernière activité, en millisecondes. */
    val lastActive: Long? = null,
    /**
     * Les conversations que le projet porte dans la fenêtre (`sessionIds` du fil) : c'est **la
     * liste** qu'ouvre sa puce, donc aussi ce que son compte annonce.
     */
    val sessionIds: Set<String> = emptySet(),
    /** Les voies du projet (copie de dépôt, branche) et leurs sessions. */
    val lanes: List<ProjectLane> = emptyList(),
) {
    /** Une conversation peut démarrer ici : un dossier, pas le fourre-tout ni le dossier personnel. */
    val canStart: Boolean get() = path != null && !isHome && !isNoProject

    /**
     * Fait par quelqu'un (ici, ou dans Hermes Desktop) : un nom à lui sur un dossier, qu'on peut
     * renommer et retirer de la liste. Le `isOwn` de la référence — le fourre-tout et le dossier
     * personnel n'en sont pas, et un projet que Hermes a trouvé tout seul non plus.
     */
    val isOwn: Boolean get() = !isNoProject && !isHome && !isAuto

    /**
     * Le libellé d'un projet tel qu'on le **montre** : celui du serveur, sans détour.
     *
     * Le fourre-tout du serveur s'appelle **« Home »** (`tui_gateway/project_tree.py`,
     * `NO_PROJECT_LABEL`) : c'est le nom que la référence montre dans sa rangée de projets, et le
     * seul que le serveur lui donne. L'app, elle, l'écrivait « No project » puis le traduisait
     * « Sans projet » — deux mots à elle pour un nom qui n'est pas le sien, et un groupe par défaut
     * que rien d'autre n'appelait comme la référence. Un projet porte donc son nom, tel quel :
     * « Home » comme « test » ou « tesy2 ».
     */
    val displayLabel: String
        get() = label

    companion object {
        /**
         * Le nom du fourre-tout, mot pour mot de la source du serveur
         * (`tui_gateway/project_tree.py` `NO_PROJECT_LABEL = "Home"`) — le défaut quand le fil ne
         * dit pas le libellé.
         */
        const val NO_PROJECT_LABEL = "Home"

        /** Le mot qu'une version antérieure du serveur donnait à ce même fourre-tout. */
        const val LEGACY_NO_PROJECT_LABEL = "No project"
    }
}

/** Une voie d'un projet : `repos` → `groups` du RPC, et les sessions qu'elle porte. */
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

/**
 * Les projets que la liste montre, dans l'ordre de la référence (`Project.listed`) : le
 * fourre-tout des conversations qui ne sont dans aucun projet d'abord, puis le plus récemment
 * utilisé. Un projet que quelqu'un a fait s'y montre avant sa première conversation ; un projet
 * que Hermes a trouvé tout seul, seulement quand il en porte au moins une.
 *
 * Le **groupe par défaut** s'y montre toujours, même vide : c'est la porte de secours du volet —
 * le retirer ferait disparaître d'un coup toutes les conversations qu'il porte.
 */
fun listedProjects(all: List<ServerProject>): List<ServerProject> =
    all.filter { it.sessionCount > 0 || it.isOwn || it.isNoProject }
        .sortedWith(
            compareByDescending<ServerProject> { it.isNoProject }
                .thenByDescending { it.lastActive ?: 0L },
        )

/** Le projet courant n'est pas une destination de déplacement. */
fun projectMoveTargets(all: List<ServerProject>, currentName: String?): List<ServerProject> =
    projectFolders(all).filter { it.label != currentName }

fun serverPathJoin(base: String, vararg parts: String): String =
    parts.fold(base.trimEnd('/')) { path, part -> path + "/" + part.trim('/') }

/**
 * Un projet lu sur `projects.tree` / `projects.project_sessions`.
 *
 * Miroir de `HermesServeClient.Project.init` côté référence : l'identifiant, le libellé, le dossier, et —
 * pour `project_sessions` — les voies (`repos` → `groups`) avec leurs sessions.
 */
internal fun serverProjectFromRow(row: JsonObject): ServerProject? {
    val id = row.firstString("id", "project_id") ?: return null
    val noProject = row["is_no_project"].asBool()
        ?: row["isNoProject"].asBool()
        ?: (id == NO_PROJECT_ID)
    // `sessionIds` est ce que le projet porte **vraiment** dans la fenêtre : le compte se lit
    // dessus, jamais sur le `sessionCount` du fil — un dépôt trouvé y annonce des années
    // d'historique (exécutions de cron, aides) alors que sa puce n'ouvre sur rien. C'est la règle
    // de la référence (`ProjectsApi.parseProjects` : « `sessionIds` are the chats the project
    // really holds in the window »).
    val ids = row.stringList("sessionIds").toSet()
    val declaredCount = row.looseInt("session_count") ?: row.looseInt("sessionCount")
    return ServerProject(
        id = id,
        // Le serveur dit lui-même le nom du groupe par défaut (« Home ») : on le garde tel quel,
        // et le mot d'une version antérieure (« No project ») est ramené au nom du serveur.
        label = row.firstString("label", "name")
            ?.takeIf { !noProject || it != ServerProject.LEGACY_NO_PROJECT_LABEL }
            ?: if (noProject) ServerProject.NO_PROJECT_LABEL else id.substringAfterLast('/'),
        path = if (noProject) null else row.strOrNull("path"),
        isHome = row["is_home"].asBool() ?: false,
        isNoProject = noProject,
        // « isAuto » est dit par 0.21.0 et au-delà ; sans lui, le projet compte comme trouvé.
        isAuto = row["isAuto"].asBool() ?: row["is_auto"].asBool() ?: true,
        sessionCount = if (row["sessionIds"] != null) ids.size else declaredCount ?: 0,
        // `projects.tree` dit `lastActive` (camelCase) ; `last_active` garde le reste. Zéro vaut
        // « rien à dire » — un projet sans conversation ne date pas de 1970, comme sur la référence
        // (`lastActive` y est ignoré tant qu'il n'est pas > 0).
        lastActive = (row.looseDouble("last_active") ?: row.looseDouble("lastActive"))
            ?.takeIf { it > 0 }
            ?.let { seconds ->
                if (kotlin.math.abs(seconds) < 100_000_000_000.0) (seconds * 1000).toLong() else seconds.toLong()
            },
        sessionIds = ids,
        lanes = projectLanesFromRow(row),
    )
}

/** Les voies d'un projet : `repos` → `groups`, ou `groups` à la racine. */
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

/** Création et déplacement de projets par les mêmes routes RPC que `ChatProjectMenu`. */
class ChatProjectsApi(
    private val rpc: ServeRpc,
    private val files: ServeFilesApi,
) {
    /**
     * `projects.tree` : les projets du serveur, **le groupe par défaut complété**.
     *
     * La fenêtre demandée est la même que celle de [projectSessions] ([PROJECT_SESSION_WINDOW]) :
     * c'est ce qui fait qu'un compte annoncé est le nombre de rangées qu'on ouvre. Puis le
     * fourre-tout est recalculé sur le **registre** des conversations (`GET api/sessions`) pour
     * qu'aucune ne reste sans groupe (voir [withHomeGroup]) — le registre est une seconde lecture,
     * et son échec laisse l'arbre tel que le serveur le donne.
     */
    suspend fun tree(): List<ServerProject> {
        val projects = treeFromServer()
        return withHomeGroup(projects, registeredSessionIds())
    }

    /** L'arbre du serveur, sans le groupe par défaut complété. */
    private suspend fun treeFromServer(): List<ServerProject> {
        val result = rpc.call(
            "projects.tree",
            buildJsonObject {
                put("preview_limit", 0)
                put("session_limit", PROJECT_SESSION_WINDOW)
            },
        ).asObject()
        return result?.array("projects").orEmpty().mapNotNull { element ->
            element.asObject()?.let(::serverProjectFromRow)
        }
    }

    /** Les conversations que le registre du serveur connaît, ou `null` s'il ne répond pas. */
    private suspend fun registeredSessionIds(): Set<String>? =
        runCatching { files.sessions().allSummaries().mapNotNullTo(LinkedHashSet()) { it.id } }.getOrNull()

    /**
     * Un projet entier, ses voies et leurs sessions : `projects.project_sessions`, la route que
     * `HermesServeClient.projectSessions` appelle côté référence.
     *
     * Pour le **groupe par défaut**, le serveur ne rend que les conversations qu'il a su placer :
     * [withUnclaimedSessions] y ajoute celles qu'aucun projet ne réclame, sans quoi le compte de la
     * puce et la liste de l'écran ne diraient pas la même chose.
     */
    suspend fun projectSessions(id: String, timeoutMillis: Long = 30_000): ServerProject? {
        val result = rpc.call(
            "projects.project_sessions",
            buildJsonObject {
                put("project_id", id)
                // La même fenêtre que l'arbre : le compte d'une puce dit ce que la puce ouvre.
                put("session_limit", PROJECT_SESSION_WINDOW)
            },
            timeoutMillis = timeoutMillis,
        ).asObject()
        val project = result?.obj("project")?.let(::serverProjectFromRow)
        if (project == null || !project.isNoProject) return project
        return project.withUnclaimedSessions()
    }

    /**
     * La descente d'un projet **complétée** des conversations qu'aucun groupe ne porte.
     *
     * Elles viennent du registre (`GET api/sessions`) et descendent dans la **première voie** : la
     * ligne d'un volet est la même, et rien de ce que le serveur a rangé n'est déplacé. Le compte
     * du projet est recalculé sur ses identifiants — annoncé = listé ([filledWith]).
     */
    private suspend fun ServerProject.withUnclaimedSessions(): ServerProject {
        val registered = runCatching { files.sessions().allSummaries() }.getOrNull() ?: return this
        val groups = withHomeGroup(treeFromServer(), registered.mapNotNullTo(LinkedHashSet()) { it.id })
        val shown = lanesSessionIds(lanes)
        val missing = groups.firstOrNull { it.isNoProject }?.sessionIds.orEmpty() - shown
        return filledWith(registered.filter { it.id in missing && it.id !in shown })
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

    /**
     * Un nouveau nom pour un projet (`projects.update`) ; son **dossier** garde le sien — les
     * conversations qui y travaillent ne bougent pas, comme `renameProject` de la référence.
     */
    suspend fun rename(id: String, name: String) {
        val clean = name.trim()
        if (projectFolderName(clean) == null) {
            throw TransportError.Malformed(OnyxLocalization["Give the project a name."])
        }
        rpc.call("projects.update", buildJsonObject { put("id", id); put("name", clean) })
    }

    /**
     * Retire un projet de la liste d'Onyx (`projects.delete`). Son **dossier** et ses
     * conversations restent sur le serveur ; les conversations ne sont simplement plus réunies
     * sous ce projet — exactement ce que `removeProject` de la référence annonce avant de le faire.
     */
    suspend fun remove(id: String) {
        rpc.call("projects.delete", buildJsonObject { put("id", id) })
    }
}

/**
 * Ce qu'on fait d'une conversation **du serveur** depuis un écran : l'archiver, la supprimer.
 *
 * Miroir des deux routes de `HermesServeClient` côté référence : `updateSession` (le PATCH REST
 * `api/sessions/{id}`, le même que Rename/Pin/Archive du registre) et `deleteSession`
 * (`session.delete` par le canal JSON-RPC).
 */
class ChatSessionActions(
    private val rpc: ServeRpc,
    private val client: ServeClient,
) {
    /** Archive (ou désarchive) une conversation du registre. */
    suspend fun archive(stored: String, archived: Boolean = true) {
        client.request(
            path = "api/sessions/$stored",
            method = "PATCH",
            body = buildJsonObject { put("archived", archived) },
        )
    }

    /** Supprime une conversation : elle part du registre pour tous les clients. */
    suspend fun delete(stored: String) {
        rpc.call("session.delete", buildJsonObject { put("session_id", stored) })
    }

    fun close() = rpc.close()
}
