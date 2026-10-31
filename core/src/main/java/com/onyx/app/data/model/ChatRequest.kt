package com.onyx.app.data.model

/** Un tour envoyé au modèle : un rôle et un contenu. */
data class ChatTurn(
    val role: Role,
    val text: String,
    /** URLs ou URI locales d'images, pour les modèles multimodaux. */
    val images: List<String> = emptyList(),
)

/**
 * Demande de complétion, indépendante du transport.
 *
 * Le transport `CHAT_COMPLETIONS` est **sans état** : [history] doit contenir tout le fil.
 */
data class ChatRequest(
    /** Tout l'historique utile, message système exclu (il vient de [instructions]). */
    val history: List<ChatTurn>,
    /** Consignes système (personnalité, langue de réponse, concision). */
    val instructions: String? = null,
    val model: String? = null,
    val provider: String? = null,
    /** `low` / `medium` / `high` — `null` laisse le serveur décider. */
    val reasoningEffort: String? = null,
    /** Demande la progression de préremplissage (serveurs llama.cpp uniquement). */
    val returnProgress: Boolean = false,
    /** API Hermes : demande le tier rapide pour ce tour de cette conversation. */
    val fast: Boolean = false,
)
