package com.onyx.app.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.BotToolsets
import com.onyx.app.data.net.DashboardSkill
import com.onyx.app.data.net.GatewayToolset
import com.onyx.app.data.net.ServeBotsRpc
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServeSkillsApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Les jeux d'outils et les compétences du bot que l'éditeur montre. */
data class BotEditorUiState(
    /** Tous les jeux que le serveur propose (`BotToolsList` ). */
    val toolsets: List<GatewayToolset> = emptyList(),
    /** Ceux du profil principal: ce que le bot suit quand il n'a pas de liste à lui. */
    val mainToolsets: Set<String> = emptySet(),
    /** Ceux de ce bot; `null`: ceux du principal, comme `toolsets`. */
    val toolsSelection: Set<String>? = null,
    /** Ses compétences, chacune allumée ou non. */
    val skills: List<DashboardSkill> = emptyList(),
    /** Celles qu'il lit en entier au début de chaque message. */
    val alwaysLoaded: Set<String> = emptySet(),
    val loading: Boolean = true,
    /** Ce qui a empêché une lecture ou une écriture, prêt à être montré. */
    val problem: String? = null,
)

/**
 * Les réglages d'un bot que l'éditeur écrit **au moment du geste**, comme l'éditeur:
 * ses jeux d'outils , ses compétences (`:214`).
 *
 * écrit les compétences et les épingles sitôt touchées (`toggleSkill`, `setAlwaysLoadedSkills`
 * de `HermesServeClient`) et les jeux d'outils à l'enregistrement (`BotChange.toolsets`); ce port
 * écrit les jeux d'outils au geste aussi, comme il le fait déjà pour le modèle
 * (`BotsViewModel.setModel`), pour qu'un interrupteur ait un effet visible et durable.
 *
 * Aucune écriture ne se contente d'animer l'écran: chacune part au serveur, et un refus est
 * remonté dans [BotEditorUiState.problem] avec l'état remis comme avant.
 */
class BotEditorViewModel(
    private val config: ServerConfig,
    /** Le profil du bot édité; `null`: un bot qui n'existe pas encore n'a rien à lire. */
    private val profile: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(BotEditorUiState())
    val state: StateFlow<BotEditorUiState> = _state.asStateFlow()

    private val skills = ServeSkillsApi(ServeClient(config))

    /** Lecture unique à l'ouverture, comme le `loaded`: rouvrir ne doit rien réécrire. */
    private var loaded = false

    init {
        load()
    }

    fun load() {
        if (loaded || !config.hasServeCredentials) {
            _state.update { it.copy(loading = false) }
            return
        }
        loaded = true
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val rpc = ServeRpc(config)
                val main: BotToolsets
                val own: BotToolsets?
                try {
                    val bots = ServeBotsRpc(rpc)
                    main = bots.describeToolsets(MAIN_PROFILE)
                    own = profile?.let { bots.describeToolsets(it) }
                } finally {
                    rpc.close()
                }
                // Un bot qui a exactement les jeux du principal suit le principal: c'est `nil`
                // , et la ligne « Same as … » reste cochée.
                val selection = own?.enabled?.takeIf { it != main.enabled }
                val list = profile?.let { skills.skills(it) } ?: emptyList()
                val preloaded = profile?.let { skills.alwaysLoaded(it) }?.toSet() ?: emptySet()
                _state.update {
                    it.copy(
                        toolsets = own?.offered?.takeIf { offered -> offered.isNotEmpty() } ?: main.offered,
                        mainToolsets = main.enabled,
                        toolsSelection = selection,
                        skills = list,
                        alwaysLoaded = preloaded,
                        loading = false,
                        problem = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = OnyxLocalization.format("Couldn't read this bot: %1\$s", error.said())
                _state.update { it.copy(loading = false, problem = message) }
            }
        }
    }

    /**
     * Les jeux d'outils du bot, écrits au serveur tout de suite (`profiles.configure`
     * `enabled_toolsets`, comme `BotChange.toolsets` ). `null`: ceux du principal.
     *
     * Le geste n'est gardé que si le serveur l'a pris: sans Dashboard (ou sans liste à écrire),
     * rien ne change à l'écran et l'écran le dit, plutôt que de montrer un choix qui n'existe pas.
     */
    fun setToolsSelection(selection: Set<String>?) {
        val bot = profile ?: return
        val before = _state.value
        val names = (selection ?: before.mainToolsets).toList()
        if (names.isEmpty()) return
        _state.update { it.copy(toolsSelection = selection, problem = null) }
        viewModelScope.launch {
            try {
                // Sans Dashboard, l'écriture ne partirait nulle part: on le dit, plutôt que de
                // laisser l'interrupteur allumé sur un réglage que personne n'a.
                if (!config.hasServeCredentials) {
                    throw TransportError.MissingApiKey
                }
                val rpc = ServeRpc(config)
                try {
                    ServeBotsRpc(rpc).setToolsets(bot, names)
                } finally {
                    rpc.close()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        toolsSelection = before.toolsSelection,
                        problem = OnyxLocalization.format("Couldn't set that up: %1\$s", error.said()),
                    )
                }
            }
        }
    }

    /** Allume ou éteint une compétence pour ce bot seul (`PUT api/skills/toggle`). */
    fun toggleSkill(skill: DashboardSkill, enabled: Boolean) {
        val bot = profile ?: return
        val before = _state.value
        val wasPinned = before.alwaysLoaded.contains(skill.name)
        _state.update { state ->
            state.copy(
                skills = state.skills.map { if (it.name == skill.name) it.copy(enabled = enabled) else it },
                // Éteinte, elle n'est de toute façon pas lue: l'épingle tombe avec elle.
                alwaysLoaded = if (enabled) state.alwaysLoaded else state.alwaysLoaded - skill.name,
                problem = null,
            )
        }
        viewModelScope.launch {
            try {
                skills.toggle(skill.name, enabled, bot)
                // Elle était épinglée, elle ne peut plus l'être: la liste est réécrite, comme le
                // `pin(skill, false)` après une compétence éteinte.
                if (!enabled && wasPinned) writeAlwaysLoaded(bot, _state.value.alwaysLoaded.toList())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        skills = before.skills,
                        alwaysLoaded = before.alwaysLoaded,
                        problem = OnyxLocalization.format(
                            if (enabled) "Couldn't switch %1\$s on: %2\$s" else "Couldn't switch %1\$s off: %2\$s",
                            skill.name,
                            error.said(),
                        ),
                    )
                }
            }
        }
    }

    /** Épingle une compétence, ou la détache: tout la liste est écrite d'un coup. */
    fun pinSkill(skill: DashboardSkill, pinned: Boolean) {
        val bot = profile
        if (bot == null) return
        val before = _state.value.alwaysLoaded
        val next = if (pinned) before + skill.name else before - skill.name
        _state.update { it.copy(alwaysLoaded = next, problem = null) }
        viewModelScope.launch {
            try {
                writeAlwaysLoaded(bot, next.toList())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        alwaysLoaded = before,
                        problem = OnyxLocalization.format(
                            if (pinned) "Couldn't pin %1\$s: %2\$s" else "Couldn't unpin %1\$s: %2\$s",
                            skill.name,
                            error.said(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun writeAlwaysLoaded(bot: String, names: List<String>) {
        skills.setAlwaysLoaded(names.sorted(), bot)
    }

    /** Le texte d'une erreur, tel que l'app le montre ailleurs. */
    private fun Exception.said(): String = when (this) {
        is TransportError -> userMessage()
        else -> message ?: OnyxLocalization["Couldn't load the bots"]
    }

    companion object {
        /** Le profil principal, toujours nommé `default` côté serveur. */
        const val MAIN_PROFILE = "default"
    }
}
