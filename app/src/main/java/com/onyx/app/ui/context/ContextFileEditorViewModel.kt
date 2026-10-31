package com.onyx.app.ui.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Accès lecture/écriture dont le contrôleur a besoin ; le fake de tests exerce le même contrat. */
interface ContextFileStore {
    suspend fun home(profile: String?): String?
    suspend fun read(path: String): String?
    suspend fun write(path: String, content: String)
    suspend fun readSoul(profile: String): String?
    suspend fun writeSoul(profile: String, content: String)
}

/** Implémentation Dashboard réelle, sans état intermédiaire ni copie locale. */
class ServeContextFileStore(config: ServerConfig) : ContextFileStore {
    private val files = ServeFilesApi(ServeClient(config))

    override suspend fun home(profile: String?): String? = files.home(profile)
    override suspend fun read(path: String): String? = files.readTextOrNull(path)
    override suspend fun write(path: String, content: String) = files.writeText(path, content)
    override suspend fun readSoul(profile: String): String? = files.soul(profile)
    override suspend fun writeSoul(profile: String, content: String) = files.setSoul(profile, content)
}

data class ContextEditorState(
    val content: String = "",
    val original: String? = null,
    val path: String? = null,
    val homeKnown: Boolean = false,
    val missing: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val dirty: Boolean get() = content != original.orEmpty()
}

/** Le cycle complet de `ContextFileEditorView`, indépendant de Compose et testable sur JVM. */
class ContextFileEditorController(
    private val store: ContextFileStore,
    private val file: ContextFile,
    private val profile: String?,
) {
    private val _state = MutableStateFlow(ContextEditorState())
    val state: StateFlow<ContextEditorState> = _state.asStateFlow()

    suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val home = store.home(profile)
        val path = contextFilePath(file, home, profile)
        _state.update { it.copy(path = path, homeKnown = home != null) }
        try {
            val text = if (file == ContextFile.SOUL) {
                store.readSoul(profile ?: DEFAULT_PROFILE)
            } else {
                store.read(path)
            }
            _state.update {
                it.copy(
                    content = text.orEmpty(),
                    original = text.orEmpty(),
                    missing = text == null,
                    loading = false,
                    error = null,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(loading = false, error = error.displayMessage()) }
        }
    }

    fun onTextChange(value: String) {
        _state.update { it.copy(content = value) }
    }

    suspend fun save() {
        val before = _state.value
        val path = before.path ?: return
        if (before.saving || !before.dirty) return
        val savedText = before.content
        _state.update { it.copy(saving = true, error = null) }
        try {
            if (file == ContextFile.SOUL) {
                store.writeSoul(profile ?: DEFAULT_PROFILE, savedText)
            } else {
                store.write(path, savedText)
            }
            _state.update {
                // Si la personne a continué à écrire pendant la requête, seule la version réellement
                // envoyée devient l'original : le nouveau brouillon reste à sauvegarder.
                it.copy(original = savedText, missing = false, saving = false, error = null)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(saving = false, error = error.displayMessage()) }
        }
    }

    private fun contextFilePath(file: ContextFile, home: String?, profile: String?): String {
        val fallback = profile?.let { "~/.hermes/profiles/$it" } ?: "~/.hermes"
        val base = (home?.trim().orEmpty().ifEmpty { fallback }).trimEnd('/')
        val relative = file.relative.removePrefix(".hermes/")
        return if (base.isEmpty()) "/$relative" else "$base/$relative"
    }

    private companion object {
        const val DEFAULT_PROFILE = "default"
    }
}

/** Propriétaire Android du contrôleur ; les requêtes sont annulées avec l'écran. */
class ContextFileEditorViewModel(
    config: ServerConfig,
    file: ContextFile,
) : ViewModel() {
    private val controller = ContextFileEditorController(
        store = ServeContextFileStore(config),
        file = file,
        profile = config.effectiveProfile,
    )
    val state: StateFlow<ContextEditorState> = controller.state

    init {
        viewModelScope.launch { controller.load() }
    }

    fun onTextChange(value: String) = controller.onTextChange(value)

    fun save() {
        viewModelScope.launch { controller.save() }
    }
}

private fun Exception.displayMessage(): String = when (this) {
    is TransportError -> userMessage()
    else -> message ?: OnyxLocalization["unknown error"]
}
