package com.onyx.app.intake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A file coming from another app: its address (`content://`), its type, and its name if available. */
data class SharedFile(
    val uri: String,
    val mimeType: String,
    val name: String = "",
)

/**
 * What enters the app from outside: a share (`ACTION_SEND`, text or file), a
 * question asked by the phone assistant (`ACTION_ASSIST`) or by a Gemini App Action,
 * and a file opened from another app (`ACTION_VIEW`).
 *
 * The activity drops the text here on creation or on a new intent, and the UI consumes it
 * once — that is what avoids losing the share when the activity was already open.
 */
object ShareInbox {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private val _pendingFile = MutableStateFlow<SharedFile?>(null)
    /** The shared file, waiting to be attached to the next message. */
    val pendingFile: StateFlow<SharedFile?> = _pendingFile.asStateFlow()

    private val _voiceRequested = MutableStateFlow(false)
    /** True when the entry comes from a "talk to Onyx" shortcut. */
    val voiceRequested: StateFlow<Boolean> = _voiceRequested.asStateFlow()

    private val _captureRequested = MutableStateFlow(false)
    /**
     * True when the entry comes from a "take a note" shortcut: the app opens on the note
     * screen, without touching the open conversation (this is the `LaunchFlag.requestCapture`
     * of the reference, through which a phone drive requests the capture).
     */
    val captureRequested: StateFlow<Boolean> = _captureRequested.asStateFlow()

    fun offer(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) _pending.value = clean
    }

    /**
     * A file shared — or opened — from another app. A share without an address says nothing:
     * it is ignored rather than attaching an empty file to the next message.
     */
    fun offerFile(uri: String?, mimeType: String?, name: String = "") {
        val clean = uri?.trim().orEmpty()
        if (clean.isEmpty()) return
        _pendingFile.value = SharedFile(uri = clean, mimeType = mimeType.orEmpty(), name = name)
    }

    /** The "talk to Onyx" shortcut: the app opens in voice mode. */
    fun requestVoice(question: String? = null) {
        _voiceRequested.value = true
        question?.let(::offer)
    }

    /** The "take a note" shortcut: the app opens on quick capture. */
    fun requestCapture() {
        _captureRequested.value = true
    }

    /** The "secrets vault" shortcut: the app opens on the vault. */
    fun requestVault() {
        _vaultRequested.value = true
    }

    private val _vaultRequested = MutableStateFlow(false)
    /**
     * True when the entry comes from the icon's **"Secrets Vault"** shortcut: the app opens
     * on the vault, **without touching the open conversation** — same contract as "take a
     * note", and the tile only exists on a server that serves the plugin (`VaultShortcut`).
     */
    val vaultRequested: StateFlow<Boolean> = _vaultRequested.asStateFlow()

    private val _pendingTemplate = MutableStateFlow<String?>(null)
    /**
     * A **bot template link** received from outside (`onyx://bot?v=1&t=…`): opened from a
     * browser, another app, or shared as text.
     *
     * It does **not** go through the composer: a template link is not a message to send, it is
     * a bot to look at before adding it. The UI therefore opens the preview, once.
     */
    val pendingTemplate: StateFlow<String?> = _pendingTemplate.asStateFlow()

    /** A bot template link, dropped for the preview. Empty text says nothing: it is ignored. */
    fun offerTemplate(link: String?) {
        val clean = link?.trim().orEmpty()
        if (clean.isEmpty()) return
        _pendingTemplate.value = clean
    }

    fun consume() {
        _pending.value = null
        _pendingFile.value = null
        _voiceRequested.value = false
        _captureRequested.value = false
        _vaultRequested.value = false
        _pendingTemplate.value = null
    }
}
