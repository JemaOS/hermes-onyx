package com.onyx.app.ui.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Les quatre fichiers qui font l'agent : qui il est, son monde, sa mémoire, et vous.
 *
 * Miroir de `AgentSettings` côté référence : `SOUL.md` et `ENVIRONMENT.md` d'un côté, `MEMORY.md` et
 * `USER.md` de l'autre, édités sur place à travers le Dashboard.
 */
enum class ContextFile(val label: String, val purpose: String, val relative: String) {
    SOUL(
        label = "SOUL.md",
        purpose = "The agent's persona: who Onyx is, how it speaks, what it values.",
        relative = ".hermes/SOUL.md",
    ),
    ENVIRONMENT(
        label = "ENVIRONMENT.md",
        purpose = "Standing facts about your setup: machines, services, names, conventions.",
        relative = ".hermes/ENVIRONMENT.md",
    ),
    MEMORY(
        label = "MEMORY.md",
        purpose = "The agent's own notes: things it decided to remember across conversations. Injected into every system prompt.",
        relative = ".hermes/memories/MEMORY.md",
    ),
    USER(
        label = "USER.md",
        purpose = "What the agent knows about you: preferences, facts, how you like answers. Also injected into every prompt.",
        relative = ".hermes/memories/USER.md",
    ),
}

data class ContextUiState(
    val home: String? = null,
    val loading: Boolean = true,
    val problem: String? = null,
) {
    /** Le chemin absolu d'un des quatre fichiers, dès que le dossier de l'agent est connu. */
    fun pathFor(file: ContextFile): String? =
        home?.trimEnd('/')?.let { base ->
            // L'API renvoie déjà HERMES_HOME, même lorsqu'il vaut `/opt/data` : le préfixe
            // `.hermes/` du modèle ne doit donc jamais être ajouté une seconde fois.
            val relative = file.relative.removePrefix(".hermes/")
            "$base/$relative"
        }
}

/** Charge le dossier de l'agent, puis laisse l'éditeur de fichiers faire le reste. */
class ContextViewModel(private val config: ServerConfig) : ViewModel() {

    private val files = ServeFilesApi(ServeClient(config))

    private val _state = MutableStateFlow(ContextUiState())
    val state: StateFlow<ContextUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val home = files.home(config.effectiveProfile)
                _state.update {
                    it.copy(
                        home = home,
                        loading = false,
                        problem = if (home == null) "The gateway didn't say where the agent's folder is." else null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, problem = error.message ?: OnyxLocalization["Couldn't reach the Dashboard."]) }
            }
        }
    }
}
