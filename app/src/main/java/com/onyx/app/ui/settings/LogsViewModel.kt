package com.onyx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.LogLevel
import com.onyx.app.data.model.serve.ServeLogLine
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeGatewayApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.voice.VoiceDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Les trois journaux qu'offre le Dashboard, comme le `Picker` de la référence, plus le journal local. */
enum class LogFile(val id: String, val label: String) {
    AGENT("agent", "Agent"),
    ERRORS("errors", "Errors"),
    GATEWAY("gateway", "Gateway"),

    /**
     * Le journal **de l'appareil** : le chemin vocal local — démarrage du moteur, permission,
     * trames reçues, erreur exacte. Il ne vient pas du serveur, et c'est le seul endroit où se lit
     * ce que le micro a réellement donné : le journal du serveur ne dit rien de ce qui se passe
     * entre le micro et le moteur. Il ne contient **jamais** de jeton ni les mots reconnus.
     */
    VOICE("voice", "Voice"),
}

/** Le filtre de niveau du menu de la référence. */
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
 * Le lecteur de journaux (`GatewayLogView`).
 *
 * Il relit tout seul toutes les deux secondes tant que l'écran est ouvert : un journal qu'on
 * regarde est un journal qui bouge.
 *
 * [localOnly] : aucun Dashboard joignable (adresse ou clé absente, ou Passerelle en échec). La page
 * s'ouvre alors sur le journal **vocal**, qui vit sur l'appareil : quelqu'un dont le micro ne marche
 * pas doit pouvoir lire ce qui casse **sans** identifiant de Dashboard. Les trois journaux du
 * serveur restent proposés — ils disent alors honnêtement pourquoi ils ne répondent pas.
 */
class LogsViewModel(
    private val config: ServerConfig,
    private val localOnly: Boolean = false,
) : ViewModel() {

    private val gateway = ServeGatewayApi(ServeClient(config))

    private val _state = MutableStateFlow(LogsUiState(file = if (localOnly) LogFile.VOICE else LogFile.AGENT))
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

    /** Les lignes telles que l'écran les montre : le filtre est appliqué par le serveur, le
     *  texte cherché aussi — on ne garde côté client que ce que le serveur a déjà filtré. */
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
        if (state.file == LogFile.VOICE) {
            // Le journal vocal est **local** : aucun appel au serveur, aucun jeton en jeu.
            loadVoiceLog(state)
            return
        }
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

    /**
     * Le journal vocal **de l'appareil** : lu dans [VoiceDiagnostics], filtré ici (le serveur n'y
     * touche pas). Le texte cherché et le filtre de niveau s'appliquent en local.
     */
    private fun loadVoiceLog(state: LogsUiState) {
        _state.update {
            it.copy(
                lines = voiceLogLines(VoiceDiagnostics.lines(), state.filter, state.search),
                loading = false,
                problem = null,
            )
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

/**
 * Les lignes du journal vocal telles que la page Journaux les montre : coloriées par
 * [voiceLogLevel], filtrées par niveau et par le texte cherché, **en local** (le serveur n'a rien
 * à voir avec ce journal-là).
 */
fun voiceLogLines(lines: List<String>, filter: LogFilter, search: String = ""): List<ServeLogLine> {
    val needle = search.trim()
    return lines
        .map { ServeLogLine(text = it, level = voiceLogLevel(it)) }
        .filter { line ->
            val matchesLevel = when (filter) {
                LogFilter.EVERYTHING -> true
                LogFilter.WARNINGS -> line.level != LogLevel.PLAIN
                LogFilter.ERRORS_ONLY -> line.level == LogLevel.ERROR
            }
            matchesLevel && (needle.isEmpty() || line.text.contains(needle, ignoreCase = true))
        }
}

/**
 * Le niveau d'une ligne du journal vocal, pour la coloration : rouge quand le moteur a échoué ou
 * que l'écoute a été close, orange pour l'alerte « on n'entend rien », neutre sinon. Les mots ne
 * sont jamais dans le journal — seulement ces faits-là.
 */
fun voiceLogLevel(text: String): LogLevel = when {
    listOf("erreur", "refus", "échec", "close").any { text.contains(it, ignoreCase = true) } ->
        LogLevel.ERROR

    text.contains("aucun son", ignoreCase = true) -> LogLevel.WARNING

    // « On n'entend rien » : la ligne courte de l'écoute muette (moteur qui tourne, aucune trame).
    // Orange comme l'alerte de l'écran, parce que c'est la même chose dite au journal.
    text.contains("on n'entend rien", ignoreCase = true) -> LogLevel.WARNING

    // La **bascule hors ligne → en ligne** se voit (orange) : ce n'est pas une ligne ordinaire,
    // c'est une décision du moteur — le hors ligne demandé n'a pas servi la langue, l'écoute repart
    // en ligne. C'est aussi la ligne qu'on demande au propriétaire de nous transmettre.
    text.contains("bascule", ignoreCase = true) -> LogLevel.WARNING

    else -> LogLevel.PLAIN
}

/** La couleur d'une ligne selon son niveau, comme la coloration de la référence. */
fun ServeLogLine.isWarning(): Boolean = level == LogLevel.WARNING

fun ServeLogLine.isError(): Boolean = level == LogLevel.ERROR
