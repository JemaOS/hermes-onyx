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

/** The bot's toolsets and skills that the editor shows. */
data class BotEditorUiState(
    /** All the toolsets the server offers (`BotToolsList` in the reference). */
    val toolsets: List<GatewayToolset> = emptyList(),
    /** Those of the main profile: what the bot follows when it has no list of its own. */
    val mainToolsets: Set<String> = emptySet(),
    /** Those of this bot; `null`: the main ones, like `toolsets` in the reference. */
    val toolsSelection: Set<String>? = null,
    /** Its skills, each switched on or off. */
    val skills: List<DashboardSkill> = emptyList(),
    /** Those it reads in full at the start of every message. */
    val alwaysLoaded: Set<String> = emptySet(),
    val loading: Boolean = true,
    /** What prevented a read or a write, ready to be shown. */
    val problem: String? = null,
)

/**
 * The settings of a bot that the editor writes **at the moment of the gesture**, like the reference editor:
 * its toolsets (`BotEditor:203`), its skills (`:214`).
 *
 * The reference writes skills and pins as soon as they are touched (`toggleSkill`, `setAlwaysLoadedSkills`
 * of `HermesServeClient`) and toolsets on save (`BotChange.toolsets`); this port
 * writes toolsets on the gesture too, as it already does for the model
 * (`BotsViewModel.setModel`), so that a switch has a visible and lasting effect.
 *
 * No write merely animates the screen: each one goes to the server, and a refusal is
 * surfaced in [BotEditorUiState.problem] with the state restored as before.
 */
class BotEditorViewModel(
    private val config: ServerConfig,
    /** The profile of the edited bot; `null`: a bot that does not exist yet has nothing to read. */
    private val profile: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(BotEditorUiState())
    val state: StateFlow<BotEditorUiState> = _state.asStateFlow()

    private val skills = ServeSkillsApi(ServeClient(config))

    /** Single read on open, like the `loaded` of the reference: reopening must write nothing back. */
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
                // A bot that has exactly the main profile's toolsets follows the main profile: that is `nil`
                // in the reference, and the "Same as …" row stays checked.
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
     * The bot's toolsets, written to the server right away (`profiles.configure`
     * `enabled_toolsets`, like `BotChange.toolsets` in the reference). `null`: the main profile's.
     *
     * The gesture is kept only if the server accepted it: without a Dashboard (or without a list to write),
     * nothing changes on screen and the screen says so, rather than showing a choice that does not exist.
     */
    fun setToolsSelection(selection: Set<String>?) {
        val bot = profile ?: return
        val before = _state.value
        val names = (selection ?: before.mainToolsets).toList()
        if (names.isEmpty()) return
        _state.update { it.copy(toolsSelection = selection, problem = null) }
        viewModelScope.launch {
            try {
                // Without a Dashboard, the write would go nowhere: we say so, rather than
                // leaving the switch on for a setting that nobody has.
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

    /** Switches a skill on or off for this bot alone (`PUT api/skills/toggle`). */
    fun toggleSkill(skill: DashboardSkill, enabled: Boolean) {
        val bot = profile ?: return
        val before = _state.value
        val wasPinned = before.alwaysLoaded.contains(skill.name)
        _state.update { state ->
            state.copy(
                skills = state.skills.map { if (it.name == skill.name) it.copy(enabled = enabled) else it },
                // Switched off, it is not read anyway: the pin falls with it.
                alwaysLoaded = if (enabled) state.alwaysLoaded else state.alwaysLoaded - skill.name,
                problem = null,
            )
        }
        viewModelScope.launch {
            try {
                skills.toggle(skill.name, enabled, bot)
                // It was pinned, it can no longer be: the list is rewritten, like the
                // `pin(skill, false)` of the reference after a skill is switched off.
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

    /** Pins a skill, or unpins it: the whole list is written at once. */
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

    /** The text of an error, as the app shows it elsewhere. */
    private fun Exception.said(): String = when (this) {
        is TransportError -> userMessage()
        else -> message ?: OnyxLocalization["Couldn't load the bots"]
    }

    companion object {
        /** The main profile, always named `default` on the server. */
        const val MAIN_PROFILE = "default"
    }
}
