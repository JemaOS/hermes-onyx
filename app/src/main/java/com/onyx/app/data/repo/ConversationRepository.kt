package com.onyx.app.data.repo

import androidx.datastore.core.DataStore
import com.onyx.app.data.local.DeletedConversations
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
 * Miroir de `ConversationStore` côté référence : les conversations sont triées de la plus
 * récente à la plus ancienne, et l'écran de chat enregistre le fil à la fin de chaque tour.
 *
 * **Une suppression est définitive.** Le fichier ne suffit pas à le garantir : d'autres chemins
 * réécrivent une conversation sans savoir qu'elle a été supprimée (la fin d'un tour qui enregistre
 * son fil, un rattachement de bot, une migration). Chaque suppression pose donc une **marque**
 * ([DeletedConversations]) — durable, hors du fichier — et [save] refuse d'écrire un identifiant
 * marqué : une conversation supprimée ne peut plus revenir par aucun chemin, ni au redémarrage.
 * Seul [restore] (le « Annuler » du bandeau) retire la marque.
 */
class ConversationRepository(
    private val store: DataStore<List<Conversation>>,
    private val deleted: DeletedConversations = DeletedConversations.InMemory,
) {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** Les conversations, la plus récente d'abord. */
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    /** Lit le fichier au démarrage. */
    suspend fun load() {
        val list = withContext(Dispatchers.IO) {
            runCatching { store.data.first() }.getOrDefault(emptyList())
        }
        // Une conversation marquée supprimée est écartée **aussi** à la lecture : un fichier écrit
        // par une version antérieure (ou par une synchronisation) ne la ramène pas non plus.
        _conversations.value = list.filterNot { it.id in deleted.marked() }.sortedByDescending { it.updatedAt }
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

    /**
     * « Créer un fil » : une **branche** de cette conversation — un fil neuf qui porte la **copie**
     * du transcript, et s'ouvre à part. La copie vit **sur ce téléphone** : le serveur d'Onyx n'a
     * pas de route pour bifurquer une session (`POST api/sessions/{id}/fork` n'existe pas côté
     * serveur), il n'y a donc rien à lui demander — la branche est locale, comme le reste du fil.
     *
     * Rend la conversation créée (l'appelant l'ouvre), ou `null` si l'originale a disparu.
     */
    suspend fun fork(id: String, now: Long = System.currentTimeMillis()): Conversation? {
        val existing = find(id) ?: return null
        val branch = forkedConversation(existing, java.util.UUID.randomUUID().toString(), now)
        save(branch)
        return branch
    }

    /**
     * « Brancher à partir d'ici » : une **branche** de [conversation] qui ne garde que ses messages
     * **jusqu'à [messageId] inclus** — le message visé et tout ce qui le précède, dans l'ordre.
     *
     * C'est la **même** règle de coupe que la restauration (`messagesThroughRestore`) ; ce qui
     * change est le sens : la restauration **retire** de la conversation, la branche **copie**
     * ailleurs et laisse l'originale intacte. La règle elle-même vit dans [branchedConversation],
     * pure, un peu plus bas.
     *
     * Enregistre la branche et la rend (l'appelant l'ouvre) ; rien n'est envoyé nulle part, et
     * l'originale n'est ni modifiée ni réécrite. Rend `null` quand le message visé a disparu.
     */
    suspend fun branchFrom(conversation: Conversation, messageId: String, now: Long = System.currentTimeMillis()): Conversation? {
        val branch = branchedConversation(conversation, java.util.UUID.randomUUID().toString(), messageId, now) ?: return null
        save(branch)
        return branch
    }

    /** Épingle, archive ou supprime plusieurs conversations d'un coup (la sélection de la référence). */
    suspend fun pinAll(ids: Set<String>, pinned: Boolean) {
        ids.forEach { id -> find(id)?.let { save(it.copy(pinned = pinned)) } }
    }

    suspend fun archiveAll(ids: Set<String>) {
        ids.forEach { id -> find(id)?.let { save(it.copy(archived = true)) } }
    }

    suspend fun deleteAll(ids: Set<String>) {
        ids.forEach { deleted.mark(it) }
        _conversations.value = _conversations.value.filterNot { it.id in ids }
        persist()
    }

    /**
     * Efface **toutes** les conversations de ce téléphone, comme `Conversation.eraseAll()` de la référence.
     * Rien n'est touché sur le serveur — mais chaque conversation effacée est **marquée** : ce qui a
     * été effacé ne revient pas, même par un chemin qui ne lit pas le fichier.
     */
    suspend fun eraseAll() {
        _conversations.value.forEach { deleted.mark(it.id) }
        _conversations.value = emptyList()
        persist()
    }

    /**
     * Enregistre (ou remplace) une conversation et réécrit le fichier.
     *
     * Refuse un identifiant **marqué supprimé** : c'est la garantie qu'aucun chemin ne ramène une
     * conversation effacée (voir la note de classe). Le « Annuler » passe par [restore], qui défait
     * la marque d'abord.
     */
    suspend fun save(conversation: Conversation) {
        if (conversation.id in deleted.marked()) return
        _conversations.value = (_conversations.value.filterNot { it.id == conversation.id } + conversation)
            .sortedByDescending { it.updatedAt }
        persist()
    }

    /** « Annuler » du bandeau : défait la marque de suppression, puis réenregistre la conversation. */
    suspend fun restore(conversation: Conversation) {
        deleted.unmark(conversation.id)
        save(conversation)
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
     * C'est ce qui fait survivre la réponse d'un tour dont l'écran a disparu : le magasin des
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

    /**
     * Supprime une conversation : elle quitte le fichier, et son identifiant est **marqué** — elle
     * ne peut plus revenir par un `save` (l'enregistrement d'un tour qui se terminait, un
     * rattachement de bot, une migration de profil), ni au redémarrage.
     */
    suspend fun delete(id: String) {
        deleted.mark(id)
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

/** Le titre d'une branche : celui de son originale plus « (fork) », comme la référence. */
internal fun forkTitle(title: String): String = "$title (fork)"

/**
 * La conversation d'une **branche à partir d'ici** : la copie des messages jusqu'à [messageId]
 * **inclus**, sous un titre de branche, dans un fil neuf.
 *
 * Ce que la copie emporte, et ce qu'elle n'emporte pas — c'est ici que tout se décide :
 *
 *  - les **messages** tels qu'ils sont tenus en mémoire : texte, raisonnement, étapes d'outils avec
 *    leurs entrées et sorties, **pièces jointes** (leurs références aux fichiers de ce téléphone),
 *    cartes et mesures ;
 *  - **pas** l'**outbox** : un message encore non envoyé dans l'originale ne doit jamais partir tout
 *    seul depuis la branche. C'est ce qui garantit qu'un branchement ne déclenche aucun tour ;
 *  - **pas** l'identifiant de session serveur : la branche n'a pas de session chez le gateway, donc
 *    rien de ce qu'on y enverra plus tard ne s'écrira dans l'originale. Le serveur n'a aucune route
 *    pour bifurquer une session ; la copie vit sur ce téléphone, et c'est le **premier envoi** dans
 *    la branche qui ouvrira sa session (le fil copié part alors comme historique, comme toujours).
 *
 * Le titre suit la convention de « Créer un fil » ([forkTitle]) ; un titre encore au défaut prend
 * celui de la première question, sans quoi la branche d'une conversation jamais renommée
 * s'appellerait « New Conversation (fork) ».
 *
 * **Pure**, donc vérifiable sans stockage ni appareil ; `null` quand le message n'est pas dans
 * l'originale (auquel cas il n'y a rien à brancher).
 */
internal fun branchedConversation(
    existing: Conversation,
    id: String,
    messageId: String,
    now: Long,
): Conversation? {
    val index = existing.messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    val titled = if (existing.title == Conversation.DefaultTitle) {
        existing.messages.firstOrNull { it.role == com.onyx.app.data.model.Role.USER }
            ?.let { existing.copy(title = Conversation.titleFrom(it.text)) }
            ?: existing
    } else {
        existing
    }
    return forkedConversation(titled, id, now).copy(
        messages = existing.messages.take(index + 1),
        outbox = emptyList(),
        serverSessionId = null,
    )
}

/**
 * La conversation d'une branche : la **copie** du transcript sous un nouveau titre, qui n'hérite
 * ni de l'épingle ni de l'archive de son originale (une branche neuve arrive dans les Chats, en
 * tête, jamais dans les archives). Pure, donc vérifiable sans stockage.
 */
internal fun forkedConversation(existing: Conversation, id: String, now: Long): Conversation =
    existing.copy(
        id = id,
        title = forkTitle(existing.title),
        createdAt = now,
        updatedAt = now,
        pinned = false,
        archived = false,
    )
