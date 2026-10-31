package com.onyx.app.ui.code

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.local.CodeSessionStorage
import com.onyx.app.data.local.CodeSessionStore
import com.onyx.app.data.model.CodeFilter
import com.onyx.app.data.model.CodeSession
import com.onyx.app.data.model.CodeStatus
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.codeBranch
import com.onyx.app.data.model.edited
import com.onyx.app.data.model.relaunched
import com.onyx.app.data.model.renamed
import com.onyx.app.data.net.GitWorktree
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeGitApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.TurnSession
import com.onyx.app.ui.chat.TurnStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** [CodeSessionStorage] sur les préférences d'Android : ce qui est écrit là survit à une relance. */
class PreferencesCodeSessionStorage(context: Context) : CodeSessionStorage {
    private val prefs = context.applicationContext
        .getSharedPreferences("onyx_code_sessions", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

data class CodeUiState(
    val sessions: List<CodeSession> = emptyList(),
    val filter: CodeFilter = CodeFilter.ALL,
    /** Les dépôts que le serveur connaît (`projects.tree`), leurs dossiers. */
    val repos: List<String> = emptyList(),
    /** Les copies de travail du dépôt choisi (`/api/git/worktrees`). */
    val worktrees: List<GitWorktree> = emptyList(),
    /** Les branches du dépôt choisi (`/api/git/branches`). */
    val branches: List<String> = emptyList(),
    val repo: String? = null,
    /** Le chemin de la copie de travail retenue ; `null` = « Nouveau worktree ». */
    val worktree: String? = null,
    /** La branche imposée, ou `null` pour celle dérivée du titre. */
    val branch: String? = null,
    val draft: String = "",
    val detail: CodeSession? = null,
    val loading: Boolean = true,
    val working: String? = null,
    val problem: String? = null,
    /** Le dépôt n'est joignable que par le Dashboard Hermes. */
    val needsDashboard: Boolean = false,
    /**
     * La fiche ouverte à la **modification**, et ses champs : la feuille « Modifier » travaille sur
     * une copie, et n'écrit qu'à l'enregistrement. `null` : aucune feuille ouverte.
     */
    val editing: CodeSession? = null,
    val editTitle: String = "",
    val editBranch: String = "",
    val editPrompt: String = "",
    val editRepo: String? = null,
    val editWorktree: String? = null,
    /** Les copies de travail du dépôt de la fiche modifiée (le sien, pas celui du compositeur). */
    val editWorktrees: List<GitWorktree> = emptyList(),
) {
    fun visible(): List<CodeSession> =
        sessions.filter { filter.accepts(it.status) }.sortedByDescending { it.createdAtMillis }
}

/**
 * Les sessions de code.
 *
 * **Ce qui est réel et ce qui est local.** Le serveur Hermes expose le **dépôt**
 * (`GET /api/git/worktrees`, `GET /api/git/branches`, `GET /api/git/status`,
 * `POST /api/git/worktree/add`) et ses **sessions** ; il n'expose **aucune** ressource « session de
 * code » avec un statut. La fiche d'une session de code — sa tâche, sa branche `code/<slug>`
 * dérivée du titre, son horodatage — est donc **tenue sur ce téléphone**, et son **statut vient du
 * tour Hermes réel** que le message a démarré : en cours tant qu'un tour vit, prêt ou échec sur
 * l'issue réellement rapportée, « à toi » quand le tour attend une réponse. Rien n'est posé
 * d'avance.
 */
class CodeViewModel(
    private val config: ServerConfig,
    private val storage: CodeSessionStorage,
    private val turns: TurnStore,
    /** Démarre une **vraie** session Hermes pour ce message et rend sa conversation. */
    private val starter: suspend (text: String, cwd: String?) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val git = ServeGitApi(ServeClient(config))
    private val store = CodeSessionStore(storage)

    private val _state = MutableStateFlow(CodeUiState())
    val state: StateFlow<CodeUiState> = _state.asStateFlow()

    init {
        reload()
        // Le statut suit le tour **réel** : à chaque changement du magasin de tours, les fiches se
        // réalignent. Aucune minuterie, aucun statut inventé.
        viewModelScope.launch {
            turns.sessions.collect { sessions -> applyTurns(sessions) }
        }
    }

    /** Les fiches gardées pour cette connexion, relues du disque. */
    private fun reload() {
        _state.update { it.copy(sessions = store.sessions(serverKey())) }
        reconcile()
    }

    /** Les dépôts, les copies de travail et les branches du serveur. */
    fun refresh() {
        if (!config.canAdminister) {
            _state.update { it.copy(loading = false, needsDashboard = true, repos = emptyList()) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, problem = null) }
            try {
                val repos = git.repos()
                    .mapNotNull { it.path }
                    .distinct()
                val chosen = _state.value.repo?.takeIf { it in repos } ?: repos.firstOrNull()
                _state.update { it.copy(repos = repos, repo = chosen, loading = false) }
                chosen?.let { loadRepo(it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        problem = (error as? TransportError)?.userMessage() ?: error.message,
                    )
                }
            }
        }
    }

    private suspend fun loadRepo(path: String) {
        val trees = runCatching { git.worktrees(path) }.getOrDefault(emptyList())
        val branchNames = runCatching { git.branches(path) }.map { it.map { b -> b.name } }
            .getOrDefault(emptyList())
        _state.update { it.copy(worktrees = trees, branches = branchNames) }
    }

    fun select(filter: CodeFilter) = _state.update { it.copy(filter = filter) }

    fun chooseRepo(path: String) {
        _state.update { it.copy(repo = path, worktree = null, branch = null) }
        viewModelScope.launch { loadRepo(path) }
    }

    fun chooseWorktree(path: String?) = _state.update { it.copy(worktree = path) }

    fun chooseBranch(name: String?) = _state.update { it.copy(branch = name) }

    fun updateDraft(text: String) = _state.update { it.copy(draft = text) }

    fun openDetail(session: CodeSession) = _state.update { it.copy(detail = session) }

    fun closeDetail() = _state.update { it.copy(detail = null) }

    fun clearProblem() = _state.update { it.copy(problem = null) }

    // — Les actions d'une fiche : renommer, modifier, relancer, supprimer —

    /**
     * **Renommer** une fiche : le titre seul, écrit tout de suite sur ce téléphone.
     *
     * La branche n'est pas retouchée (c'est celle d'une vraie branche du dépôt), et la
     * **conversation Hermes** sous-jacente n'est pas touchée non plus : seule la fiche locale change.
     */
    fun rename(session: CodeSession, title: String) {
        val next = session.renamed(title)
        if (next == session) return
        store.save(serverKey(), next)
        _state.update {
            it.copy(
                sessions = store.sessions(serverKey()),
                detail = it.detail?.let { open -> if (open.id == next.id) next else open },
            )
        }
    }

    /**
     * **Supprimer** une fiche : elle disparaît de ce téléphone, et rien d'autre.
     *
     * La **conversation Hermes reste** — c'est une ressource du serveur, pas de cette fiche — et
     * l'écran le dit dans sa confirmation. Seul ce magasin local est réécrit.
     */
    fun delete(session: CodeSession) {
        store.remove(serverKey(), session.id)
        _state.update {
            it.copy(
                sessions = store.sessions(serverKey()),
                detail = it.detail?.takeIf { open -> open.id != session.id },
            )
        }
    }

    /**
     * **Relancer** une fiche : la demande est **rejouée dans une session neuve**.
     *
     * Une **autre** conversation Hermes est ouverte (la précédente n'est ni reprise ni fermée), et
     * une **nouvelle** fiche est écrite — même dépôt, même copie de travail, même branche, même
     * demande. La fiche d'origine reste dans la liste, telle qu'elle était.
     */
    fun rerun(session: CodeSession) = launchWork("Starting the session…") {
        val text = session.prompt?.takeIf { it.isNotBlank() } ?: session.title
        val cwd = session.worktreePath ?: session.repoPath
        val conversationId = starter(text, cwd) ?: UUID.randomUUID().toString()
        val now = clock()
        val record = session.relaunched(conversationId, now).copy(
            prompt = text,
            sessionKey = turns.session(conversationId)?.live?.value?.serverSessionId,
        )
        store.save(serverKey(), record)
        _state.update { it.copy(sessions = store.sessions(serverKey())) }
    }

    /** Ouvre la feuille **Modifier** sur une copie de la fiche : rien n'est écrit avant d'enregistrer. */
    fun beginEdit(session: CodeSession) {
        _state.update {
            it.copy(
                editing = session,
                editTitle = session.title,
                editBranch = session.branch,
                editPrompt = session.prompt.orEmpty(),
                editRepo = session.repoPath,
                editWorktree = session.worktreePath,
                editWorktrees = emptyList(),
            )
        }
        session.repoPath?.let { path ->
            viewModelScope.launch {
                val trees = runCatching { git.worktrees(path) }.getOrDefault(emptyList())
                // Le dépôt de la fiche a pu changer entre-temps : on ne pose que le sien.
                _state.update { if (it.editRepo == path) it.copy(editWorktrees = trees) else it }
            }
        }
    }

    fun editTitle(text: String) = _state.update { it.copy(editTitle = text) }

    fun editBranch(text: String) = _state.update { it.copy(editBranch = text) }

    fun editPrompt(text: String) = _state.update { it.copy(editPrompt = text) }

    fun editChooseRepo(path: String) {
        _state.update { it.copy(editRepo = path, editWorktree = null, editWorktrees = emptyList()) }
        viewModelScope.launch {
            val trees = runCatching { git.worktrees(path) }.getOrDefault(emptyList())
            _state.update { if (it.editRepo == path) it.copy(editWorktrees = trees) else it }
        }
    }

    fun editChooseWorktree(path: String?) = _state.update { it.copy(editWorktree = path) }

    fun cancelEdit() = _state.update { it.copy(editing = null) }

    /** Enregistre la feuille **Modifier** : la fiche réécrite, tout de suite sur ce téléphone. */
    fun saveEdit() {
        val session = _state.value.editing ?: return
        val next = session.edited(
            title = _state.value.editTitle,
            branch = _state.value.editBranch,
            prompt = _state.value.editPrompt,
            repoPath = _state.value.editRepo,
            worktreePath = _state.value.editWorktree,
        )
        store.save(serverKey(), next)
        _state.update {
            it.copy(
                sessions = store.sessions(serverKey()),
                editing = null,
                detail = it.detail?.let { open -> if (open.id == next.id) next else open },
            )
        }
    }

    /**
     * « Nouveau worktree » : le serveur le crée pour de vrai (`POST /api/git/worktree/add`), sur la
     * branche dérivée du brouillon. La copie est ensuite retenue pour la session.
     */
    fun createWorktree() = launchWork("Creating the worktree…") {
        val repo = _state.value.repo ?: return@launchWork
        val title = _state.value.draft.trim().ifEmpty { "session" }
        val branch = _state.value.branch?.takeIf { it.isNotBlank() } ?: codeBranch(title)
        git.addWorktree(repoPath = repo, name = branch.substringAfterLast('/'), branch = branch)
        val trees = runCatching { git.worktrees(repo) }.getOrDefault(emptyList())
        val created = trees.firstOrNull { it.branch == branch }
        _state.update { it.copy(worktrees = trees, worktree = created?.path) }
    }

    /**
     * Envoie le message : **chaque message démarre sa propre session de code**.
     *
     * Une **vraie** session Hermes est ouverte pour ce message ([starter]), la fiche locale est
     * écrite avec sa branche dérivée du titre, et son statut naît « en cours » parce qu'un tour
     * vient réellement d'être lancé — il ne devient « prêt » ou « échec » que sur l'issue observée.
     */
    fun start() = launchWork(null) {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return@launchWork
        val title = text.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { text.trim() }
        val branch = _state.value.branch?.takeIf { it.isNotBlank() } ?: codeBranch(title)
        val cwd = _state.value.worktree ?: _state.value.repo
        val conversationId = starter(text, cwd) ?: UUID.randomUUID().toString()
        val now = clock()
        val record = CodeSession(
            id = conversationId,
            title = title,
            branch = branch,
            repoPath = _state.value.repo,
            worktreePath = _state.value.worktree,
            createdAtMillis = now,
            status = CodeStatus.RUNNING,
            statusAtMillis = now,
            sessionKey = turns.session(conversationId)?.live?.value?.serverSessionId,
            // La demande est gardée avec la fiche : c'est elle que « Relancer » rejoue.
            prompt = text,
        )
        store.save(serverKey(), record)
        _state.update { it.copy(draft = "", sessions = store.sessions(serverKey())) }
    }

    /**
     * Le statut d'une fiche, dérivé du **tour réel**.
     *
     * Un tour vivant : en cours, ou « à toi » s'il attend une réponse. Un tour fini : prêt, ou
     * échec si le transport a rapporté une erreur. Sans tour dans ce processus, la fiche garde le
     * dernier fait observé — jamais une valeur posée d'avance.
     */
    private fun statusOf(record: CodeSession, live: TurnSession?): Pair<CodeStatus, Long> {
        if (live == null) return record.status to record.statusAtMillis
        if (!live.isFinished) {
            val waiting = live.message.clarify != null
            val status = if (waiting) CodeStatus.NEEDS_YOU else CodeStatus.RUNNING
            return status to record.statusAtMillis
        }
        val status = if (live.failure != null) CodeStatus.FAILED else CodeStatus.READY
        return status to clock()
    }

    private fun applyTurns(sessions: Map<String, TurnSession>) {
        val kept = _state.value.sessions
        if (kept.isEmpty()) return
        var changed = false
        val updated = kept.map { record ->
            val result = statusOf(record, sessions[record.id])
            val sessionKey = record.sessionKey ?: sessions[record.id]?.live?.value?.serverSessionId
            if (result.first == record.status && sessionKey == record.sessionKey) {
                record
            } else {
                changed = true
                record.copy(status = result.first, statusAtMillis = result.second, sessionKey = sessionKey)
            }
        }
        if (!changed) return
        updated.forEach { store.save(serverKey(), it) }
        _state.update { it.copy(sessions = updated) }
    }

    /**
     * Les fiches laissées « en cours » par une fermeture de l'app sont réconciliées auprès du
     * serveur : si la session réelle porte une réponse de l'agent, elle est prête. Sans session
     * réelle à interroger, la fiche garde son dernier fait observé — rien n'est promu.
     */
    private fun reconcile() {
        val stuck = _state.value.sessions.filter { it.status == CodeStatus.RUNNING && it.sessionKey != null }
        if (stuck.isEmpty()) return
        viewModelScope.launch {
            var changed = false
            val updated = _state.value.sessions.map { record ->
                if (record !in stuck) return@map record
                val role = runCatching { git.lastMessageRole(record.sessionKey!!) }.getOrNull()
                if (role == "assistant") {
                    changed = true
                    record.copy(status = CodeStatus.READY, statusAtMillis = clock())
                } else {
                    record
                }
            }
            if (changed) {
                updated.forEach { store.save(serverKey(), it) }
                _state.update { it.copy(sessions = updated) }
            }
        }
    }

    private fun launchWork(label: String?, call: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(working = label, problem = null) }
            try {
                call()
                _state.update { it.copy(working = null, problem = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = (error as? TransportError)?.userMessage()
                    ?: error.message
                    ?: OnyxLocalization["The session couldn't start: %1\$s"].format("?")
                _state.update { it.copy(working = null, problem = message) }
            }
        }
    }

    /** La clé de rangement : la connexion, comme les autres magasins locaux de l'app. */
    private fun serverKey(): String = config.id
}
