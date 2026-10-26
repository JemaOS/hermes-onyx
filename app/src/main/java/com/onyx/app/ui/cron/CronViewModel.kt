package com.onyx.app.ui.cron

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.CronJob
import com.onyx.app.data.model.serve.CronRun
import com.onyx.app.data.model.serve.DeliveryTarget
import com.onyx.app.data.model.serve.RoutineWhen
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeCronApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Le brouillon d'une tâche, neuf ou repris. */
data class CronDraft(
    val id: String? = null,
    val title: String = "",
    val schedule: String = "",
    val prompt: String = "",
    val deliverTo: String? = null,
)

data class CronUiState(
    val jobs: List<CronJob> = emptyList(),
    val targets: List<DeliveryTarget> = emptyList(),
    val loading: Boolean = true,
    val problem: String? = null,
    /** « No gateway configured »: les tâches vivent sur le gateway, pas sur la seule API de chat. */
    val needsDashboard: Boolean = false,
    val working: String? = null,
    val busy: Set<String> = emptySet(),
    val detail: CronJob? = null,
    val runs: List<CronRun> = emptyList(),
    val draft: CronDraft? = null,
    val saving: Boolean = false,
)

/**
 * Les tâches planifiées du gateway, toutes sections ou celles d'un seul bot.
 *
 * Le même état alimente `CronView` et `BotSchedulesView`: dans le second cas, seules les routines
 * `bot-chat` du profil sont retenues et chaque mutation reste dans le store de ce profil.
 */
class CronViewModel(
    private val config: ServerConfig,
    /** Un profil, quand on regarde les horaires d'un bot; `null` montre tous les profils. */
    private val profile: String? = null,
    /** Le nom affiché dans l'erreur de lecture d'un bot, pas son identifiant de profil. */
    private val botLabel: String? = null,
) : ViewModel() {

    private val cron = ServeCronApi(ServeClient(config))

    private val _state = MutableStateFlow(CronUiState())
    val state: StateFlow<CronUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        // Aucune connexion Dashboard: le gateway ne peut pas être interrogé du tout.
        if (profile == null && !config.canAdminister) {
            _state.update { it.copy(jobs = emptyList(), loading = false, problem = null, needsDashboard = true) }
            return
        }
        // Une routine de bot n'existe que dans le Dashboard; ne pas afficher une erreur réseau
        // par-dessus l'état « Needs the Hermes Dashboard » de la vue.
        if (profile != null && !config.canAdminister) {
            _state.update { it.copy(jobs = emptyList(), loading = false, problem = null) }
            return
        }
        viewModelScope.launch { load() }
    }

    /** Recharge dans la coroutine appelante: une action garde son état occupé jusqu'à la fin. */
    private suspend fun load() {
        _state.update { it.copy(loading = true, problem = null) }
        try {
            val jobs = cron.jobs(profile ?: "all")
                .filter { profile == null || it.isBotRoutine }
                .sortedWith(compareBy<CronJob> { it.nextRunAt ?: Long.MAX_VALUE })
            val targets = if (profile == null) {
                runCatching { cron.deliveryTargets() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _state.update { it.copy(jobs = jobs, targets = targets, loading = false) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val detail = (error as? TransportError)?.userMessage()
                ?: error.message
                ?: OnyxLocalization["Couldn't load jobs"]
            val message = if (profile != null) {
                OnyxLocalization.format("Couldn't read %1\$s's schedules: %2\$s", botLabel ?: profile, detail)
            } else {
                detail
            }
            _state.update { it.copy(loading = false, problem = message) }
        }
    }

    fun openDetail(job: CronJob) {
        _state.update { it.copy(detail = job, runs = emptyList()) }
        viewModelScope.launch {
            val runs = runCatching { cron.runs(job.id, profile ?: job.botName) }.getOrDefault(emptyList())
            _state.update { it.copy(runs = runs) }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null) }
    }

    fun startNew() {
        val bot = profile
        _state.update {
            it.copy(
                draft = CronDraft(
                    schedule = if (bot != null) RoutineWhen().schedule else "",
                    deliverTo = bot?.let { name -> "bot-chat:$name" },
                ),
                problem = null,
            )
        }
    }

    fun edit(job: CronJob) {
        val bot = profile
        _state.update {
            it.copy(
                draft = CronDraft(
                    id = job.id,
                    title = if (bot != null) job.untaggedTitle else job.title,
                    schedule = job.schedule,
                    prompt = job.prompt,
                    deliverTo = bot?.let { name -> "bot-chat:$name" } ?: job.deliverTo,
                ),
                detail = null,
                problem = null,
            )
        }
    }

    fun updateDraft(transform: (CronDraft) -> CronDraft) {
        _state.update { it.copy(draft = it.draft?.let(transform)) }
    }

    fun cancelDraft() {
        _state.update { it.copy(draft = null, saving = false, problem = null) }
    }

    fun saveDraft() {
        val draft = _state.value.draft ?: return
        val bot = profile
        if (draft.schedule.isBlank() || draft.prompt.isBlank() || (bot != null && draft.title.isBlank())) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, problem = null) }
            try {
                val title = if (bot != null) "[bot:$bot] ${draft.title.trim()}" else draft.title
                val deliverTo = bot?.let { "bot-chat:$it" } ?: draft.deliverTo
                if (draft.id == null) {
                    cron.create(title, draft.schedule, draft.prompt, deliverTo, bot)
                } else {
                    cron.update(draft.id, title, draft.schedule, draft.prompt, deliverTo, bot)
                }
                _state.update { it.copy(saving = false, draft = null, problem = null) }
                refresh()
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

    fun pause(job: CronJob) = perform(job, "Pausing…") { cron.pause(job.id, profile ?: job.botName) }

    fun resume(job: CronJob) = perform(job, "Resuming…") { cron.resume(job.id, profile ?: job.botName) }

    fun runNow(job: CronJob) = perform(job, "Running now…") { cron.trigger(job.id, profile ?: job.botName) }

    fun delete(job: CronJob) = perform(job, "Deleting…") {
        cron.delete(job.id, profile ?: job.botName)
        _state.update { it.copy(detail = null) }
    }

    private fun perform(job: CronJob, label: String, call: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(working = label, busy = it.busy + job.id, problem = null) }
            try {
                call()
                load()
                _state.update { it.copy(working = null, busy = it.busy - job.id) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        working = null,
                        busy = it.busy - job.id,
                        problem = (error as? TransportError)?.userMessage() ?: error.message,
                    )
                }
            }
        }
    }
}
