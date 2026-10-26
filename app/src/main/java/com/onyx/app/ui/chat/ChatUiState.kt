package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.PrefillProgress
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentActivity

/**
 * Tout ce que l'écran de chat doit rendre.
 *
 * Immuable: le ViewModel publie une nouvelle valeur à chaque événement du flux.
 */
data class ChatUiState(
    val server: ServerConfig,
    /** Conversation locale affichée: la liste large surligne exactement cette rangée. */
    val conversationId: String? = null,
    /** Identifiant de session serveur utilisé par les projets et réglages de conversation. */
    val serverSessionId: String? = null,
    /** Le nom donné à la conversation par « Renommer » (`Conversation.name` ); `null` tant
     * qu'aucun nom n'a été donné, auquel cas l'en-tête montre la première question. */
    val conversationName: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    /**
     * L'état honnête du tour en cours de cette conversation: l'étape connue et les horaires.
     * Le fil s'en sert pour dire ce qui se passe (le serveur n'envoie pas toujours de quoi le
     * montrer), et il est le même après un retour dans la conversation.
     */
    val live: TurnPhase? = null,
    /** Progression du préremplissage, quand le serveur la remonte (llama.cpp). */
    val prefill: PrefillProgress? = null,
    /** Statut du gateway/provider pendant le tour (ex. « provider indisponible, réessai »). */
    val providerStatus: String? = null,
    /**
     * Ce qu'a répondu le serveur à une note de pilotage (« steer queued », « steer rejected… »).
     * La ligne de statut (`Conversation.statusLine`); elle s'efface à la fin du tour.
     */
    val steerStatus: String? = null,
    /** Dernière erreur à afficher en bandeau, ou `null`. */
    val error: String? = null,
    /** Vrai si l'erreur peut être rejouée avec le même message. */
    val canRetry: Boolean = false,
    /** Serveur injoignable: utile pour proposer les réglages. */
    val isConnectionProblem: Boolean = false,
    /** Messages écrits pendant une réponse ou retenus par une coupure, avec leurs pièces jointes. */
    val queued: List<com.onyx.app.data.model.OutboxItem> = emptyList(),
    /** Ce qui est joint au message en cours d'écriture. */
    val attachments: List<PendingAttachment> = emptyList(),
    /** La recherche dans le fil est ouverte, et ce qu'elle a trouvé. */
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val followUps: List<String> = emptyList(),
    /** Le menu « / » du composeur, quand le brouillon commence par une barre oblique. */
    val slashItems: List<SlashItem> = emptyList(),
    /** Un `/model` nu a été envoyé: l'écran ouvre le sélecteur de modèle (une seule fois). */
    val modelPickerRequested: Boolean = false,
    /**
     * Le modèle réellement employé par le dernier envoi, une fois résolu contre le serveur: c'est
     * lui que la seconde ligne de la barre du haut nomme, à côté du « @handle » du bot
     * (`modelChipLabel`).
     */
    val modelInUse: String? = null,
    /**
     * La commande « / » que l'app doit encore exécuter (ouvrir le sélecteur de modèle, une
     * nouvelle conversation…). L'écran la consomme puis appelle [ChatViewModel.consumeSlash]:
     * une commande ne doit pas se rejouer à la recomposition suivante.
     */
    val pendingSlash: SlashAction? = null,
    val searchHits: List<String> = emptyList(),
    val searchCurrent: Int = 0,
    /**
     * Le roster des sous-agents vivants, tel que `subagent.list` le rend (Dashboard seulement).
     *
     * Il comble ce que le fil ne sait pas: la **date de départ** et le **dernier outil** d'un
     * enfant lancé avant que l'app regarde, ou pendant une reconnexion. Les lignes du fil restent
     * la source vive; le roster les complète.
     */
    val subagentRoster: List<SubagentActivity> = emptyList(),
) {
    val isEmpty: Boolean get() = messages.isEmpty() && queued.isEmpty()
    val canSend: Boolean get() = (input.isNotBlank() || attachments.isNotEmpty()) && !isStreaming

    /**
     * Tous les sous-agents de la conversation: ceux des réponses, complétés par le roster.
     *
     * Une seule ligne par `subagent_id` — un enfant annoncé par `subagent.start` puis relu par
     * `subagent.list` ne doit pas apparaître deux fois.
     */
    val subagents: List<SubagentActivity>
        get() {
            val fromMessages = messages.flatMap { it.subagents }
            if (subagentRoster.isEmpty()) return fromMessages
            val known = fromMessages.map { it.id }.toSet()
            val merged = fromMessages.map { row ->
                val server = subagentRoster.firstOrNull { it.id == row.id } ?: return@map row
                row.copy(
                    startedAt = row.startedAt.takeIf { it > 0 } ?: server.startedAt,
                    lastTool = row.lastTool ?: server.lastTool,
                    toolCount = row.toolCount.takeIf { it > 0 } ?: server.toolCount,
                    childSessionId = row.childSessionId ?: server.childSessionId,
                    model = row.model ?: server.model,
                    acceptingSteer = row.acceptingSteer && server.acceptingSteer,
                )
            }
            return merged + subagentRoster.filter { it.id !in known }
        }

    /**
     * Vrai quand un message écrit maintenant peut piloter la réponse en cours plutôt que
     * d'attendre. Comme `Conversation.canSteer`: il faut un tour en cours et un transport
     * qui garde un registre côté serveur (`hasLedger`: sessions Hermes ou serve).
     */
    val canSteer: Boolean get() = isStreaming && server.transport.isStateful
}

/** Change le fil présenté sans laisser fuiter les contrôles éphémères du fil précédent. */
internal fun ChatUiState.switchToConversation(
    conversationId: String,
    serverSessionId: String?,
    messages: List<ChatMessage>,
    input: String,
    queued: List<OutboxItem>,
    conversationName: String? = null,
): ChatUiState = copy(
    conversationId = conversationId,
    serverSessionId = serverSessionId,
    conversationName = conversationName,
    messages = messages,
    input = input,
    isStreaming = false,
    live = null,
    prefill = null,
    providerStatus = null,
    steerStatus = null,
    error = null,
    canRetry = false,
    isConnectionProblem = false,
    queued = queued,
    attachments = emptyList(),
    searchOpen = false,
    searchQuery = "",
    followUps = emptyList(),
    slashItems = emptyList(),
    modelPickerRequested = false,
    pendingSlash = null,
    searchHits = emptyList(),
    searchCurrent = 0,
)

/** Une entrée du menu `/`: ce qui s'écrit dans le brouillon, et ce qui s'affiche. */
data class SlashItem(val text: String, val display: String, val detail: String = "")
