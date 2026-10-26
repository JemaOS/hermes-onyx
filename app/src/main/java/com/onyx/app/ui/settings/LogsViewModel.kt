package com.onyx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.LogLevel
import com.onyx.app.data.model.serve.ServeLogLine
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeGatewayApi
import com.onyx.app.data.net.TransportError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Les trois journaux qu'offre le Dashboard, comme le `Picker`. */
enum class LogFile(val id: String, val label: String) {
    AGENT("agent", "Agent"),
    ERRORS("errors", "Errors"),
    GATEWAY("gateway", "Gateway"),
}

/** Le filtre de niveau du menu. */
enum class LogFilter(val id: String?, val label: String) {
    EVERYTHING(null, "Everything"),
    WARNINGS("warning", "Warnings and errors"),
    ERRORS_ONLY("error", "Errors only"),
}

data class LogsUiState(
    val file: LogFile = LogFile.AGENT,
    val filter: LogFilter = LogFilter.EVERYTHING,
    val search: String = "",
    val lines: List<ServeLogLine> = emptyList(),
    val loading: Boolean = true,
    val problem: String? = null,
)

/**
 * Le lecteur de journaux.
 *
 * Il relit tout seul toutes les deux secondes tant que l'écran est ouvert: un journal qu'on
 * regarde est un journal qui bouge.
 */
class LogsViewModel(private val config: ServerConfig) : ViewModel() {

    private val gateway = ServeGatewayApi(ServeClient(config))

    private val _state = MutableStateFlow(LogsUiState())
    val state: StateFlow<LogsUiState> = _state.asStateFlow()

    private var poll: Job? = null

    init {
        watch()
    }

    fun select(file: LogFile) {
        _state.update { it.copy(file = file) }
        watch()
    }

    fun setFilter(filter: LogFilter) {
        _state.update { it.copy(filter = filter) }
        watch()
    }

    fun setSearch(text: String) {
        _state.update { it.copy(search = text) }
        watch()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** Les lignes telles que l'écran les montre: le filtre est appliqué par le serveur, le
     * texte cherché aussi — on ne garde côté client que ce que le serveur a déjà filtré. */
    fun visibleLines(): List<ServeLogLine> {
        val state = _state.value
        val search = state.search.trim()
        if (search.isEmpty()) return state.lines
        return state.lines.filter { it.text.contains(search, ignoreCase = true) }
    }

    /** Le texte complet, pour le partage. */
    fun exportText(): String = visibleLines().joinToString("\n") { it.text }

    private fun watch() {
        poll?.cancel()
        poll = viewModelScope.launch {
            while (true) {
                load()
                delay(POLL_MS)
            }
        }
    }

    private suspend fun load() {
        val state = _state.value
        _state.update { it.copy(loading = it.lines.isEmpty()) }
        try {
            val lines = gateway.logs(
                file = state.file.id,
                level = state.filter.id,
                search = state.search.trim().takeIf { it.isNotEmpty() },
            )
            _state.update { it.copy(lines = lines, loading = false, problem = null) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: TransportError) {
            _state.update { it.copy(loading = false, problem = error.userMessage()) }
        } catch (error: Exception) {
            _state.update { it.copy(loading = false, problem = error.message) }
        }
    }

    override fun onCleared() {
        poll?.cancel()
        super.onCleared()
    }

    private companion object {
        const val POLL_MS = 2_000L
    }
}

/** La couleur d'une ligne selon son niveau, comme la coloration. */
fun ServeLogLine.isWarning(): Boolean = level == LogLevel.WARNING

fun ServeLogLine.isError(): Boolean = level == LogLevel.ERROR
