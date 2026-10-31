package com.onyx.app.data.net

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Une copie de travail (worktree) d'un dépôt, telle que `GET /api/git/worktrees` la rend.
 *
 * Route serveur **réelle** : `200` sur le Dashboard Hermes avec `?path=<dépôt>`.
 */
data class GitWorktree(
    val path: String,
    val branch: String? = null,
    val isMain: Boolean = false,
    val detached: Boolean = false,
    val locked: Boolean = false,
)

/** Une branche d'un dépôt, telle que `GET /api/git/branches` la rend. */
data class GitBranch(
    val name: String,
    val checkedOut: Boolean = false,
    val isDefault: Boolean = false,
    val isRemote: Boolean = false,
    val worktreePath: String? = null,
)

/** Une ligne de dépôt, telle que `GET /api/profiles/projects/tree` la donne. */
data class RepoRow(val id: String, val label: String, val path: String)

/** L'état d'un dépôt, tel que `GET /api/git/status` le rend. */
data class GitStatus(
    val branch: String? = null,
    val defaultBranch: String? = null,
    val changed: Int = 0,
    val ahead: Int = 0,
    val behind: Int = 0,
)

/**
 * Les routes **git** du Dashboard Hermes, celles qui existent pour de vrai.
 *
 * Vérifiées sur le serveur de la machine (codes HTTP relevés au rapport) :
 *  - `GET  /api/git/worktrees?path=<dépôt>` → `200`
 *  - `GET  /api/git/branches?path=<dépôt>` → `200`
 *  - `GET  /api/git/status?path=<dépôt>`   → `200`
 *  - `POST /api/git/worktree/add`          → crée une copie de travail
 *
 * Il n'existe **aucune** route « session de code » : l'écran Code nomme donc le dépôt et la copie
 * de travail en s'appuyant sur ces routes, et suit l'état d'un tour Hermes réel pour le statut.
 */
class ServeGitApi(private val client: ServeClient) {

    /**
     * Les dépôts du serveur : `GET /api/profiles/projects/tree`.
     *
     * La route rend les projets, chacun avec le dossier où il travaille : c'est cette liste qui
     * alimente la puce « Dépôt » du compositeur.
     */
    suspend fun repos(): List<RepoRow> {
        val payload = client.request(path = "api/profiles/projects/tree")
        return payload.asObject()?.array("projects").orEmpty().mapNotNull { element ->
            val row = element.asObject() ?: return@mapNotNull null
            val path = row.strOrNull("path") ?: return@mapNotNull null
            if (row.bool("is_home") == true) return@mapNotNull null
            RepoRow(id = row.strOrNull("id") ?: path, label = row.strOrNull("label") ?: path, path = path)
        }
    }

    /** Les copies de travail d'un dépôt. */
    suspend fun worktrees(repoPath: String): List<GitWorktree> {
        val payload = client.request(path = "api/git/worktrees", query = mapOf("path" to repoPath))
        return payload.asObject()?.array("worktrees").orEmpty().mapNotNull { element ->
            val row = element.asObject() ?: return@mapNotNull null
            val path = row.strOrNull("path") ?: return@mapNotNull null
            GitWorktree(
                path = path,
                branch = row.strOrNull("branch"),
                isMain = row.bool("isMain") ?: false,
                detached = row.bool("detached") ?: false,
                locked = row.bool("locked") ?: false,
            )
        }
    }

    /** Les branches d'un dépôt, sans les distantes. */
    suspend fun branches(repoPath: String): List<GitBranch> {
        val payload = client.request(path = "api/git/branches", query = mapOf("path" to repoPath))
        return payload.asObject()?.array("branches").orEmpty().mapNotNull { element ->
            val row = element.asObject() ?: return@mapNotNull null
            val name = row.strOrNull("name") ?: return@mapNotNull null
            GitBranch(
                name = name,
                checkedOut = row.bool("checkedOut") ?: false,
                isDefault = row.bool("isDefault") ?: false,
                isRemote = row.bool("isRemote") ?: false,
                worktreePath = row.strOrNull("worktreePath"),
            )
        }.filterNot { it.isRemote }
    }

    /** L'état d'un dépôt : la branche courante, et de combien elle a bougé. */
    suspend fun status(repoPath: String): GitStatus? {
        val row = client.request(path = "api/git/status", query = mapOf("path" to repoPath)).asObject()
            ?: return null
        return GitStatus(
            branch = row.strOrNull("branch"),
            defaultBranch = row.strOrNull("defaultBranch"),
            changed = row.looseInt("changed") ?: 0,
            ahead = row.looseInt("ahead") ?: 0,
            behind = row.looseInt("behind") ?: 0,
        )
    }

    /**
     * Crée une copie de travail pour un dépôt : `POST /api/git/worktree/add`.
     *
     * [name] nomme le dossier sous `.worktrees/`, [branch] la branche à sortir (créée depuis
     * [base] si elle n'existe pas encore) — c'est la branche dérivée du titre de la tâche.
     */
    suspend fun addWorktree(repoPath: String, name: String, branch: String, base: String? = null) {
        client.request(
            path = "api/git/worktree/add",
            method = "POST",
            body = buildJsonObject {
                put("path", repoPath)
                put("name", name)
                put("branch", branch)
                if (!base.isNullOrBlank()) put("base", base)
            },
        )
    }

    /** Le dernier message d'un tour, pour réconcilier une session laissée « en cours ». */
    suspend fun lastMessageRole(sessionId: String): String? {
        val payload = client.request(
            path = "api/sessions/$sessionId/messages",
            query = mapOf("limit" to "1", "order" to "latest"),
        )
        val messages = payload.asObject()?.array("messages")
            ?: payload.asObject()?.array("data")
            ?: payload.asArray()
            ?: return null
        return messages.lastOrNull()?.asObject()?.strOrNull("role")
    }
}
