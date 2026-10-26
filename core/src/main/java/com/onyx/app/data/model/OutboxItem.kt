package com.onyx.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pourquoi un message n'est pas encore parti (`OutboxItem.State`). */
@Serializable
enum class OutboxState {
    @SerialName("queued") QUEUED,
    @SerialName("waitingForConnection") WAITING_FOR_CONNECTION,
    @SerialName("paused") PAUSED,
}

/** Un message de l'utilisateur conservé dans la file, avec ses pièces jointes et son ancienneté. */
@Serializable
data class OutboxItem(
    val id: String,
    val text: String = "",
    val attachments: List<Attachment> = emptyList(),
    val state: OutboxState = OutboxState.QUEUED,
    val queuedAt: Long,
) {
    /** L'identité Compose reste distincte de la bulle qui réutilisera l'id du message. */
    val rowID: String get() = "queued-$id"

    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - queuedAt > STALE_AFTER_MS

    companion object {
        const val STALE_AFTER_MS = 60L * 60L * 1000L
    }
}
