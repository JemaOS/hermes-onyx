package com.onyx.app.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.FileEntry
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.humanFailure
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Où l'on est dans l'arborescence : une pile de dossiers, `null` pour la racine. */
data class FilesUiState(
    val stack: List<String?> = listOf(null),
    val entries: List<FileEntry> = emptyList(),
    val showHidden: Boolean = false,
    val loading: Boolean = true,
    val working: String? = null,
    val problem: String? = null,
    val home: String? = null,
) {
    val path: String? get() = stack.last()
    val atRoot: Boolean get() = stack.size <= 1
    val title: String get() = path?.substringAfterLast('/')?.ifEmpty { "Files" } ?: "Files"
}

/**
 * Le navigateur de fichiers du serveur.
 *
 * Miroir de `FilesView` + `ServerFilesModel` : une pile de dossiers, les fichiers
 * cachés qu'on peut montrer, le téléversement et la suppression — les mêmes gestes, les mêmes
 * phrases.
 */
class FilesViewModel(private val config: ServerConfig) : ViewModel() {

    private val files = ServeFilesApi(ServeClient(config))

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val home = runCatching { files.home() }.getOrNull()
            _state.update { it.copy(home = home) }
            reload()
        }
    }

    fun reload() {
        val path = _state.value.path
        viewModelScope.launch {
            _state.update { it.copy(loading = true, problem = null) }
            try {
                val entries = files.list(path)
                _state.update { it.copy(entries = entries, loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                _state.update { it.copy(loading = false, problem = error.userMessage()) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, problem = humanFailure(error, OnyxLocalization["Couldn't open this folder"])) }
            }
        }
    }

    fun open(folder: FileEntry) {
        _state.update { it.copy(stack = it.stack + folder.path, entries = emptyList()) }
        reload()
    }

    fun open(path: String) {
        _state.update { it.copy(stack = it.stack + path, entries = emptyList()) }
        reload()
    }

    /** Remonte d'un dossier ; faux à la racine, où l'écran se ferme. */
    fun back(): Boolean {
        if (_state.value.atRoot) return false
        _state.update { it.copy(stack = it.stack.dropLast(1), entries = emptyList()) }
        reload()
        return true
    }

    fun toggleHidden() {
        _state.update { it.copy(showHidden = !it.showHidden) }
    }

    fun makeFolder(name: String) {
        val current = _state.value.path ?: return
        val target = current.trimEnd('/') + "/" + name.trim()
        viewModelScope.launch {
            _state.update { it.copy(working = "Making the folder…") }
            runCatching { files.makeFolder(target) }
                .onFailure { error -> _state.update { it.copy(problem = humanFailure(error)) } }
            _state.update { it.copy(working = null) }
            reload()
        }
    }

    fun delete(entry: FileEntry) {
        viewModelScope.launch {
            _state.update { it.copy(working = "Deleting ${entry.name}…") }
            runCatching { files.delete(entry) }
                .onFailure { error -> _state.update { it.copy(problem = humanFailure(error)) } }
            _state.update { it.copy(working = null) }
            reload()
        }
    }

    /** Téléverse un fichier dans le dossier courant — ou écrase, si le nom existe déjà. */
    fun upload(name: String, bytes: ByteArray) {
        val current = _state.value.path ?: _state.value.home ?: return
        val exists = _state.value.entries.any { it.name == name && !it.isDirectory }
        viewModelScope.launch {
            _state.update { it.copy(working = "Uploading $name…") }
            runCatching { files.upload(current, name, bytes, overwrite = exists) }
                .onFailure { error -> _state.update { it.copy(problem = humanFailure(error)) } }
            _state.update { it.copy(working = null) }
            reload()
        }
    }

    /** Les octets d'un fichier, pour l'ouvrir ailleurs. */
    suspend fun bytes(entry: FileEntry): ByteArray? = runCatching { files.bytes(entry.path) }.getOrNull()

    /**
     * **Récupère** un fichier du serveur dans [destination], sans jamais le tenir en mémoire.
     *
     * Le défaut que ce chemin corrige : la rangée d'un binaire passait par [bytes], donc un APK de
     * 34 Mo était lu d'un bloc dans le tas de l'application avant d'être écrit — la mémoire du
     * téléphone décidait si « n'importe quel fichier » voulait dire « n'importe quel petit
     * fichier ». Ici le corps est écrit au fil de l'eau dans un `.part` renommé à la fin
     * (`ServeFileDownload.toFile`), et le poids reçu s'affiche pendant le transfert.
     *
     * Rend `true` quand le fichier est là, `false` sinon — la raison est alors dans [FilesUiState.problem].
     */
    suspend fun download(
        entry: FileEntry,
        destination: java.io.File,
        onProgress: (received: Long, total: Long?) -> Unit = { _, _ -> },
    ): Boolean {
        _state.update { it.copy(working = "Downloading ${entry.name}…", problem = null) }
        return try {
            com.onyx.app.data.net.ServeFileDownload(config)
                .toFile(entry.path, destination) { received, total ->
                    val label = if (total != null && total > 0) {
                        "Downloading ${entry.name}… ${received * 100 / total} %"
                    } else {
                        "Downloading ${entry.name}… ${received / 1024} KB"
                    }
                    _state.update { it.copy(working = label) }
                    onProgress(received, total)
                }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _state.update { it.copy(problem = humanFailure(error)) }
            false
        } finally {
            _state.update { it.copy(working = null) }
        }
    }

    fun clearProblem() {
        _state.update { it.copy(problem = null) }
    }

    /** Le nom de la nouvelle entrée, et le dossier où elle irait (pour l'alerte « New Folder »). */
    fun currentFolder(): String? = _state.value.path ?: _state.value.home
}

/**
 * L'éditeur d'un fichier texte du serveur.
 *
 * Miroir de `ServerTextFileView` : le chemin en en-tête, un éditeur monospace, et « Save ».
 */
class FileEditorViewModel(
    private val config: ServerConfig,
    private val path: String,
    /**
     * Quand on édite le prompt d'un bot, on passe par la route du profil (`api/profiles/{nom}/soul`)
     * plutôt que par le fichier : c'est celle que le Dashboard trouve tout seul.
     */
    private val soulProfile: String? = null,
) : ViewModel() {

    private val files = ServeFilesApi(ServeClient(config))

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val problem: String? = null,
        val truncated: Boolean = false,
        val binary: Boolean = false,
        val saved: Boolean = false,
    )

    init {
        viewModelScope.launch {
            try {
                if (soulProfile != null) {
                    _text.value = files.soul(soulProfile).orEmpty()
                    _state.update { it.copy(loading = false) }
                } else {
                    val file = files.readText(path)
                    _text.value = file.text
                    _state.update { it.copy(loading = false, truncated = file.truncated, binary = file.binary) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, problem = humanFailure(error))
                }
            }
        }
    }

    fun onTextChange(value: String) {
        _text.value = value
        _state.update { it.copy(saved = false) }
    }

    fun save() {
        if (_state.value.binary) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, problem = null) }
            try {
                if (soulProfile != null) {
                    files.setSoul(soulProfile, _text.value)
                } else {
                    files.writeText(path, _text.value)
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        problem = humanFailure(error),
                    )
                }
            }
        }
    }

    val filePath: String get() = path
}
