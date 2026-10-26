package com.onyx.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Une conversation enregistrée.
 *
 * Comme l'`Envelope` de: un identifiant, un titre, les dates qui
 * ordonnent la liste, le serveur d'origine et le fil tel qu'il était.
 */
@Serializable
data class Conversation(
    val id: String,
    /** Le titre, pris du premier message de l'utilisateur. « New Conversation » avant ça. */
    val title: String = DefaultTitle,
    val createdAt: Long,
    val updatedAt: Long,
    /** Serveur avec qui elle a été écrite: la liste s'y rapporte. */
    val serverId: String = "",
    /** Session côté gateway, quand le transport en tient une. */
    val serverSessionId: String? = null,
    /** Épinglée en tête de liste. */
    val pinned: Boolean = false,
    /** Rangée dans les archives. */
    val archived: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    /** Les messages non envoyés survivent à la fermeture et à la réouverture de la conversation. */
    val outbox: List<OutboxItem> = emptyList(),
) {
    /** Le nombre de tours, c'est-à-dire de messages de l'utilisateur. */
    val turnCount: Int get() = messages.count { it.role == Role.USER }

    val isEmpty: Boolean get() = messages.isEmpty() && outbox.isEmpty()

    /**
     * Le nom donné à la conversation par « Renommer », ou `null` quand le titre n'est encore que
     * la première question (ou « New Conversation »). L'en-tête du chat préfère ce nom au premier
     * message, comme `Conversation.name`.
     */
    fun customName(): String? {
        val first = messages.firstOrNull { it.role == Role.USER }?.text
        val derived = first?.let { titleFrom(it) }
        return title.takeIf { it != DefaultTitle && it != derived }
    }

    fun withMessage(message: ChatMessage, now: Long): Conversation = copy(
        messages = messages + message,
        updatedAt = now,
        title = if (title == DefaultTitle && message.role == Role.USER) titleFrom(message.text) else title,
    )

    companion object {
        const val DefaultTitle = "New Conversation"

        /** Le titre d'une conversation: la première ligne du premier message, coupée court. */
        fun titleFrom(text: String): String {
            val firstLine = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()
            if (firstLine.isEmpty()) return DefaultTitle
            return if (firstLine.length <= 42) firstLine else firstLine.take(41).trimEnd() + "…"
        }

        fun start(id: String = UUID.randomUUID().toString(), now: Long = System.currentTimeMillis()): Conversation =
            Conversation(id = id, createdAt = now, updatedAt = now)
    }
}
