package com.onyx.app.data.net

import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.PrefillProgress
import com.onyx.app.data.model.SubagentUpdate
import com.onyx.app.data.model.TokenUsage

/**
 * Événements d'un tour de conversation, miroir de `TurnEvent`
 * (Echo/Services/Transport/HermesTransport:5).
 *
 * Tous les transports (chat-completions OpenAI, sessions Hermes, runs) produisent ce même
 * vocabulaire : l'UI ne dépend donc pas du serveur choisi.
 */
sealed interface TurnEvent {
    /** Fragment de texte de la réponse, à concaténer. */
    data class TextDelta(val text: String) : TurnEvent

    /** Texte final complet (remplace ce qui a été streamé), quand le serveur le fournit. */
    data class TextFinal(val text: String) : TurnEvent

    /** Fragment de « reasoning » / chaîne de pensée, à afficher dans un bloc repliable. */
    data class ReasoningDelta(val text: String) : TurnEvent

    /** Un outil commence à tourner. */
    data class ToolStarted(val name: String, val preview: String?, val args: String? = null) : TurnEvent

    /** Un outil a fini (ou échoué). */
    data class ToolFinished(val name: String, val failed: Boolean, val output: String? = null) : TurnEvent

    /** Progression du préremplissage du prompt (llama.cpp). */
    data class Prefill(val progress: PrefillProgress) : TurnEvent

    /** Compteurs de tokens de fin de tour. */
    data class Usage(val usage: TokenUsage) : TurnEvent

    /**
     * Un sous-agent délégué commence, appelle un outil, progresse ou finit.
     *
     * Transporté par le socket du Dashboard (`hermes serve`), qui émet les trames `subagent.*`
     * **sur la session du parent** — l'enfant est nommé par `subagent_id`. L'API de conversation
     * (`/v1`) ne les transmet pas : là, seuls les buts d'un `delegate_task` se lisent dans ses
     * arguments, et la ligne reste [com.onyx.app.data.model.SubagentStatus.DISPATCHED].
     */
    data class Subagent(val update: SubagentUpdate) : TurnEvent

    /** Statut remonté par le gateway pendant le tour (réessai provider, indisponibilité). */
    data class Status(val text: String, val isError: Boolean = false) : TurnEvent

    /** Le gateway demande une approbation avant de poursuivre. */
    data class Interrupt(val request: ApprovalRequest) : TurnEvent

    /** Identifiant de session côté serveur, à conserver pour les tours suivants. */
    data class SessionId(val id: String) : TurnEvent

    /**
     * Le modèle que la session sert vraiment, quand le serveur le dit.
     *
     * `session.info` du Dashboard porte `model` : un alias peut cacher le vrai modèle (l'app envoie
     * `hermes-agent`, le serveur sert autre chose), et c'est le **vrai** que la barre du haut doit
     * nommer — il change donc dynamiquement.
     */
    data class ModelInUse(val model: String) : TurnEvent

    /** Fin normale du flux. */
    data object Done : TurnEvent
}
