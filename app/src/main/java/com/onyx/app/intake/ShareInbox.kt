package com.onyx.app.intake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Un fichier venu d'une autre app: son adresse (`content://`), son type, et son nom si on l'a. */
data class SharedFile(
    val uri: String,
    val mimeType: String,
    val name: String = "",
)

/**
 * Ce qui entre dans l'app de l'extérieur: un partage (`ACTION_SEND`, texte ou fichier), une
 * question posée par l'assistant du téléphone (`ACTION_ASSIST`) ou par une App Action de Gemini,
 * et un fichier ouvert depuis une autre app (`ACTION_VIEW`).
 *
 * L'activité dépose le texte ici à la création ou sur un nouvel intent, et l'UI le consomme une
 * fois — c'est ce qui évite de perdre le partage quand l'activité était déjà ouverte.
 */
object ShareInbox {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private val _pendingFile = MutableStateFlow<SharedFile?>(null)
    /** Le fichier partagé, en attente d'être joint au prochain message. */
    val pendingFile: StateFlow<SharedFile?> = _pendingFile.asStateFlow()

    private val _voiceRequested = MutableStateFlow(false)
    /** Vrai quand l'entrée vient d'un raccourci « parler à Onyx ». */
    val voiceRequested: StateFlow<Boolean> = _voiceRequested.asStateFlow()

    private val _captureRequested = MutableStateFlow(false)
    /**
     * Vrai quand l'entrée vient d'un raccourci « prendre une note »: l'app s'ouvre sur l'écran
     * de la note, sans toucher à la conversation ouverte (c'est le `LaunchFlag.requestCapture`
     * du modèle, par lequel un pilotage du téléphone demande la capture).
     */
    val captureRequested: StateFlow<Boolean> = _captureRequested.asStateFlow()

    fun offer(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) _pending.value = clean
    }

    /**
     * Un fichier partagé — ou ouvert — depuis une autre app. Un partage sans adresse ne dit rien:
     * il est ignoré plutôt que de joindre un fichier vide au message suivant.
     */
    fun offerFile(uri: String?, mimeType: String?, name: String = "") {
        val clean = uri?.trim().orEmpty()
        if (clean.isEmpty()) return
        _pendingFile.value = SharedFile(uri = clean, mimeType = mimeType.orEmpty(), name = name)
    }

    /** Le raccourci « parler à Onyx »: l'app s'ouvre en mode vocal. */
    fun requestVoice(question: String? = null) {
        _voiceRequested.value = true
        question?.let(::offer)
    }

    /** Le raccourci « prendre une note »: l'app s'ouvre sur la capture rapide. */
    fun requestCapture() {
        _captureRequested.value = true
    }

    fun consume() {
        _pending.value = null
        _pendingFile.value = null
        _voiceRequested.value = false
        _captureRequested.value = false
    }
}
