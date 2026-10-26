package com.onyx.app.data.repo

import androidx.datastore.core.DataStore
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * L'historique des conversations, sur disque.
 *
 * Comme: les conversations sont triées de la plus
 * récente à la plus ancienne, et l'écran de chat enregistre le fil à la fin de chaque tour.
 */
class ConversationRepository(private val store: DataStore<List<Conversation>>) {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** Les conversations, la plus récente d'abord. */
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    /** Lit le fichier au démarrage. */
    suspend fun load() {
        val list = withContext(Dispatchers.IO) {
            runCatching { store.data.first() }.getOrDefault(emptyList())
        }
        _conversations.value = list.sortedByDescending { it.updatedAt }
    }

    fun find(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    /** Épingle ou désépingle une conversation, et réécrit le fichier. */
    suspend fun setPinned(id: String, pinned: Boolean) {
        val existing = find(id) ?: return
        save(existing.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
    }

    /** Range une conversation dans les archives, ou l'en sort. */
    suspend fun setArchived(id: String, archived: Boolean) {
        val existing = find(id) ?: return
        save(existing.copy(archived = archived, updatedAt = System.currentTimeMillis()))
    }

    /** Renomme une conversation. */
    suspend fun rename(id: String, title: String) {
        val existing = find(id) ?: return
        save(existing.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    /** Épingle, archive ou supprime plusieurs conversations d'un coup (la sélection ). */
    suspend fun pinAll(ids: Set<String>, pinned: Boolean) {
        ids.forEach { id -> find(id)?.let { save(it.copy(pinned = pinned)) } }
    }

    suspend fun archiveAll(ids: Set<String>) {
        ids.forEach { id -> find(id)?.let { save(it.copy(archived = true)) } }
    }

    suspend fun deleteAll(ids: Set<String>) {
        _conversations.value = _conversations.value.filterNot { it.id in ids }
        persist()
    }

    /**
     * Efface **toutes** les conversations de ce téléphone, comme `Conversation.eraseAll`.
     * Rien n'est touché sur le serveur.
     */
    suspend fun eraseAll() {
        _conversations.value = emptyList()
        persist()
    }

    /** Enregistre (ou remplace) une conversation et réécrit le fichier. */
    suspend fun save(conversation: Conversation) {
        _conversations.value = (_conversations.value.filterNot { it.id == conversation.id } + conversation)
            .sortedByDescending { it.updatedAt }
        persist()
    }

    /** Ajoute un message au fil enregistré, en créant la conversation au besoin. */
    suspend fun append(conversationId: String, serverId: String, message: ChatMessage, now: Long = System.currentTimeMillis()) {
        val existing = find(conversationId)
        val updated = (existing ?: Conversation.start(id = conversationId, now = now).copy(serverId = serverId))
            .withMessage(message, now)
        save(updated)
    }

    /**
     * Écrit (ou remplace) **un** message du fil enregistré.
     *
     * C'est ce qui fait survivre la réponse d'un tour dont l'écran a disparu: le magasin des
     * tours l'écrit au fil de son avancement, sans personne pour appeler `save` depuis un écran.
     */
    suspend fun upsertMessage(conversationId: String, message: ChatMessage) {
        val existing = find(conversationId) ?: return
        val messages = (existing.messages.filterNot { it.id == message.id } + message)
            .sortedBy { it.createdAt }
        save(
            existing.copy(
                messages = messages,
                updatedAt = maxOf(existing.updatedAt, message.createdAt),
                title = if (existing.title == Conversation.DefaultTitle && message.role == com.onyx.app.data.model.Role.USER) {
                    Conversation.titleFrom(message.text)
                } else {
                    existing.title
                },
            ),
        )
    }

    suspend fun delete(id: String) {
        _conversations.value = _conversations.value.filterNot { it.id == id }
        persist()
    }

    /** La conversation la plus récente à laquelle on peut revenir depuis l'écran de démarrage. */
    fun mostRecent(excluding: String? = null): Conversation? =
        _conversations.value.firstOrNull { it.id != excluding && it.turnCount > 0 }

    private suspend fun persist() {
        val snapshot = _conversations.value
        withContext(Dispatchers.IO) { store.updateData { snapshot } }
    }
}
