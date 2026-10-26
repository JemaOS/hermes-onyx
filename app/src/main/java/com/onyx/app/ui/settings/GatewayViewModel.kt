package com.onyx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.GatewayStatus
import com.onyx.app.data.model.serve.McpProbe
import com.onyx.app.data.model.serve.McpServer
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeGatewayApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tout ce que l'écran Gateway affiche. */
data class GatewayUiState(
    val loading: Boolean = true,
    val status: GatewayStatus? = null,
    val servers: List<McpServer> = emptyList(),
    val probes: Map<String, McpProbe> = emptyMap(),
    val testing: Set<String> = emptySet(),
    val problem: String? = null,
    /** Une opération de maintenance en cours: « Updating Hermes… », « Restarting the gateway… ». */
    val working: String? = null,
    val progress: List<String> = emptyList(),
    val outcome: String? = null,
    val update: ServeGatewayApi.UpdateInfo? = null,
    val canUpdate: Boolean = true,
)

/**
 * L'écran Gateway: l'état d'Hermes, ses serveurs MCP, ses journaux et sa maintenance.
 *
 * Mêmes sections, mêmes phrases, mêmes
 * routes — les seules différences sont celles d'Android (pas de « Check for Updates » si le
 * serveur ne l'expose pas).
 */
class GatewayViewModel(private val config: ServerConfig) : ViewModel() {

    private val gateway = ServeGatewayApi(ServeClient(config))

    private val _state = MutableStateFlow(GatewayUiState())
    val state: StateFlow<GatewayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Relit l'état, les serveurs MCP, et la version disponible. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, problem = null) }
            try {
                val status = gateway.status()
                val servers = runCatching { gateway.mcpServers() }.getOrDefault(emptyList())
                val update = runCatching { gateway.checkUpdate() }.getOrNull()
                _state.update {
                    it.copy(
                        loading = false,
                        status = status,
                        servers = servers,
                        update = update,
                        canUpdate = update != null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                _state.update { it.copy(loading = false, problem = error.userMessage()) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, problem = error.message ?: OnyxLocalization["Couldn't reach the gateway."]) }
            }
        }
    }

    /** L'interrupteur d'un serveur MCP — il change la configuration d'Hermes, comme le Dashboard. */
    fun setServer(name: String, enabled: Boolean) {
        val previous = _state.value.servers
        _state.update { state ->
            state.copy(servers = state.servers.map { if (it.name == name) it.copy(enabled = enabled) else it })
        }
        viewModelScope.launch {
            try {
                gateway.setMcpEnabled(name, enabled)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // L'interrupteur revient en place si Hermes refuse: l'écran ne ment pas.
                _state.update { it.copy(servers = previous, problem = (error as? TransportError)?.userMessage()) }
            }
        }
    }

    /** « Test »: Hermes se connecte au serveur et liste ses outils (jusqu'à 90 s). */
    fun testServer(name: String) {
        _state.update { it.copy(testing = it.testing + name) }
        viewModelScope.launch {
            val probe = try {
                gateway.testMcp(name)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                McpProbe(ok = false, summary = "", error = (error as? TransportError)?.userMessage() ?: error.message)
            }
            _state.update {
                it.copy(probes = it.probes + (name to probe), testing = it.testing - name)
            }
        }
    }

    /** « Restart Gateway… », confirmé par l'écran. */
    fun restart() {
        runMaintenance(label = "Restarting the gateway…", action = "gateway-restart") {
            gateway.restartGateway()
        }
    }

    /** « Update Hermes… », confirmé par l'écran. */
    fun updateHermes() {
        runMaintenance(label = "Updating Hermes…", action = "hermes-update") {
            gateway.updateHermes()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val update = runCatching { gateway.checkUpdate() }.getOrNull()
            _state.update {
                it.copy(
                    update = update,
                    canUpdate = update != null,
                    outcome = if (update == null) OnyxLocalization["Hermes is up to date."] else null,
                )
            }
        }
    }

    fun clearOutcome() {
        _state.update { it.copy(outcome = null) }
    }

    private fun runMaintenance(label: String, action: String, call: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(working = label, progress = emptyList(), outcome = null) }
            try {
                call()
                // Le serveur travaille en tâche de fond: on suit ses lignes jusqu'à l'arrêt.
                repeat(PROGRESS_POLLS) {
                    val lines = runCatching { gateway.actionStatus(action) }.getOrDefault(emptyList())
                    _state.update { it.copy(progress = lines.takeLast(4)) }
                    kotlinx.coroutines.delay(1_500)
                }
                _state.update { it.copy(working = null, outcome = OnyxLocalization["Done."]) }
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = (error as? TransportError)?.userMessage() ?: error.message ?: OnyxLocalization["It didn't finish."]
                _state.update { it.copy(working = null, outcome = message) }
            }
        }
    }

    private companion object {
        const val PROGRESS_POLLS = 6
    }
}
