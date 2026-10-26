package com.onyx.app.ui.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.KanbanCard
import com.onyx.app.data.model.serve.KanbanStatus
import com.onyx.app.data.net.KanbanLiveBoard
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeKanbanApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Le brouillon d'une carte, neuve ou reprise. */
data class KanbanDraft(
    val id: String? = null,
    val title: String = "",
    val body: String = "",
    val priority: Int = 0,
    val assignee: String = "",
    val startReady: Boolean = false,
)

data class KanbanUiState(
    val cards: List<KanbanCard> = emptyList(),
    val column: KanbanStatus = KanbanStatus.TRIAGE,
    val assignees: List<String> = emptyList(),
    val loading: Boolean = true,
    val problem: String? = null,
    /** « Needs the Hermes Dashboard »: le tableau vit sur le Dashboard, pas sur l'API seule. */
    val needsDashboard: Boolean = false,
    val working: String? = null,
    val detail: KanbanCard? = null,
    val draft: KanbanDraft? = null,
    val saving: Boolean = false,
    /** Le socket d'événements est ouvert: l'en-tête montre la diffusion, comme. */
    val live: Boolean = false,
    /** Le curseur d'événements du dernier tableau lu: le socket reprend de là (`since=`). */
    val latestEventId: Int = 0,
) {
    fun cardsIn(column: KanbanStatus): List<KanbanCard> =
        cards.filter { KanbanStatus.fromId(it.status) == column }
}

/**
 * Le tableau kanban du Dashboard.
 *
 * Comme: une colonne à la fois (le sélecteur en haut), ses cartes, et
 * l'éditeur d'une carte — avec les mêmes colonnes, les mêmes priorités, les mêmes actions, et le
 * même direct: le socket d'événements du plugin relit le tableau dès qu'une carte change, avec
 * l'interrogation périodique en repli (`LiveBoard` ).
 */
class KanbanViewModel(private val config: ServerConfig) : ViewModel() {

    private val kanban = ServeKanbanApi(ServeClient(config))
    private val liveBoard = KanbanLiveBoard(config)

    private val _state = MutableStateFlow(KanbanUiState())
    val state: StateFlow<KanbanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            liveBoard.isLive.collect { live -> _state.update { it.copy(live = live) } }
        }
        refresh()
    }

    /**
     * Le tableau se rafraîchit tout seul tant que l'écran l'affiche.
     *
     * Le socket reprend au curseur du dernier tableau lu, comme `latestEventID`: les
     * changements déjà vus ne sont pas rejoués.
     */
    fun startLive() {
        if (!config.canAdminister) return
        liveBoard.start(_state.value.latestEventId) { refresh(silent = true) }
    }

    /** L'écran n'est plus visible: le socket se referme ( le fait en `onDisappear`). */
    fun stopLive() = liveBoard.stop()

    override fun onCleared() {
        liveBoard.close()
    }

    /** Un rafraîchissement est en cours; les demandes reçues pendant se cumulent en un seul. */
    private var refreshing = false
    private var againAfter = false

    /**
     * Relit le tableau.
     *
     * Le **premier** chargement montre l'indicateur; ceux qui suivent (le direct du tableau qui
     * signale un changement) se font **sans** lui: l'indicateur clignotait à chaque événement et
     * donnait l'impression d'une boucle de chargement. Une seule lecture court à la fois.
     */
    fun refresh(silent: Boolean = false) {
        // The board lives on the Dashboard: without its login there is nothing to ask.
        if (!config.canAdminister) {
            _state.update { it.copy(cards = emptyList(), loading = false, problem = null, needsDashboard = true) }
            return
        }
        if (refreshing) { againAfter = true; return }
        refreshing = true
        viewModelScope.launch {
            val first = _state.value.cards.isEmpty() && !silent
            if (first) _state.update { it.copy(loading = true, problem = null) }
            else _state.update { it.copy(problem = null) }
            try {
                val board = kanban.board()
                val assignees = board.assignees.ifEmpty {
                    runCatching { kanban.assignees() }.getOrDefault(emptyList())
                }
                _state.update {
                    it.copy(
                        cards = board.cards,
                        assignees = assignees,
                        // Le curseur ne recule jamais: un tableau lu en retard ne rejoue rien.
                        latestEventId = maxOf(board.latestEventId, it.latestEventId),
                        loading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                _state.update { it.copy(loading = false, problem = error.userMessage()) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, problem = error.message ?: OnyxLocalization["Couldn't load the board"]) }
            } finally {
                refreshing = false
                if (againAfter) { againAfter = false; refresh(silent = true) }
            }
        }
    }

    fun selectColumn(column: KanbanStatus) {
        _state.update { it.copy(column = column) }
    }

    fun openDetail(card: KanbanCard) {
        _state.update { it.copy(detail = card) }
        viewModelScope.launch {
            val fresh = runCatching { kanban.task(card.id) }.getOrNull()
            if (fresh != null) _state.update { it.copy(detail = fresh) }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null) }
    }

    fun startNew() {
        _state.update {
            it.copy(draft = KanbanDraft(priority = 0), column = it.column)
        }
    }

    fun edit(card: KanbanCard) {
        _state.update {
            it.copy(
                detail = null,
                draft = KanbanDraft(
                    id = card.id,
                    title = card.title,
                    body = card.body.orEmpty(),
                    priority = card.priority,
                    assignee = card.assignee.orEmpty(),
                ),
            )
        }
    }

    fun updateDraft(transform: (KanbanDraft) -> KanbanDraft) {
        _state.update { it.copy(draft = it.draft?.let(transform)) }
    }

    fun cancelDraft() {
        _state.update { it.copy(draft = null) }
    }

    fun saveDraft() {
        val draft = _state.value.draft ?: return
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                if (draft.id == null) {
                    kanban.create(
                        title = draft.title,
                        body = draft.body.takeIf { it.isNotBlank() },
                        priority = draft.priority,
                        assignee = draft.assignee.takeIf { it.isNotBlank() },
                        startReady = draft.startReady,
                    )
                } else {
                    kanban.update(
                        id = draft.id,
                        title = draft.title,
                        body = draft.body,
                        priority = draft.priority,
                        assignee = draft.assignee,
                    )
                }
                _state.update { it.copy(saving = false, draft = null) }
                refresh(silent = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        problem = (error as? TransportError)?.userMessage() ?: error.message,
                    )
                }
            }
        }
    }

    /** Déplace une carte; `note` est la raison demandée avant le déplacement. */
    fun move(card: KanbanCard, status: KanbanStatus, note: String? = null) = perform("Moving…") {
        kanban.update(id = card.id, status = status, note = note)
        _state.update { it.copy(detail = null) }
    }

    fun delete(card: KanbanCard) = perform("Deleting…") {
        kanban.delete(card.id)
        _state.update { it.copy(detail = null) }
    }

    fun comment(card: KanbanCard, body: String) = perform("Posting…") {
        kanban.comment(card.id, body)
        val fresh = runCatching { kanban.task(card.id) }.getOrNull()
        if (fresh != null) _state.update { it.copy(detail = fresh) }
    }

    private fun perform(label: String, call: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(working = label) }
            try {
                call()
                _state.update { it.copy(working = null) }
                refresh(silent = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        working = null,
                        problem = (error as? TransportError)?.userMessage() ?: error.message,
                    )
                }
            }
        }
    }
}
