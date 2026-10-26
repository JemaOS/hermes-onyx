package com.onyx.app.data.model

import kotlinx.serialization.Serializable

/** Une question posée par l'agent, avec ses choix et la possibilité de répondre librement. */
@Serializable
data class ClarifyQuestion(
    val id: String,
    val question: String,
    val choices: List<String> = emptyList(),
    val multiSelect: Boolean = false,
)

/** « Onyx a une ou plusieurs questions » (`ClarifyRequest`). */
@Serializable
data class ClarifyRequest(
    val id: String,
    val questions: List<ClarifyQuestion> = emptyList(),
    /** Une série répond par `question_id`, même si elle ne contient qu'une question. */
    val isBatch: Boolean = false,
    /** Le run auquel la réponse doit être adressée. */
    val runId: String? = null,
    val answered: String? = null,
)

/** Un secret demandé par l'agent (une clé d'API, un mot de passe d'un service). */
@Serializable
data class SecretRequest(
    val id: String,
    /** Consigne du serveur, affichée verbatim; une variable seule reçoit le libellé de repli. */
    val prompt: String = "",
    val envVar: String = "",
    val runId: String? = null,
    /** Valeur fournie, ou `null` tant que la carte est ouverte. */
    val provided: String? = null,
)

/**
 * Ce qui arrête l'agent et attend la personne (`Interrupt`): une approbation, une question, un
 * mot de passe sudo, ou un secret.
 */
sealed interface Interrupt {
    val id: String

    data class Approval(val request: ApprovalRequest) : Interrupt {
        override val id: String get() = request.id
    }

    data class Clarify(val request: ClarifyRequest) : Interrupt {
        override val id: String get() = request.id
    }

    data class Sudo(val runId: String, val requestId: String, val answered: Boolean = false) : Interrupt {
        override val id: String get() = requestId
    }

    data class Secret(val request: SecretRequest) : Interrupt {
        override val id: String get() = request.id
    }
}
