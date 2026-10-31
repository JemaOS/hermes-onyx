package com.onyx.app.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * DTO du protocole OpenAI-compatible `/v1/chat/completions`.
 *
 * Les noms de champs sont ceux du fil : ne pas les renommer.
 * Miroir de `ChatCompletionsTransport.Body` / `Chunk`
 * (Echo/Services/Transport/ChatCompletionsTransport:9 et :89).
 */
@Serializable
internal data class ChatRequestBody(
    val model: String,
    val messages: List<WireMessage>,
    /**
     * AUCUNE valeur par défaut ici, volontairement.
     *
     * Le Json du client est configuré en `encodeDefaults = false` : un défaut ferait
     * **disparaître le champ de la requête** (et `stream_options` aussi, puisqu'une valeur
     * égale au défaut n'est pas encodée). Le serveur croyait alors recevoir une demande
     * **non streamée**, répondait un JSON complet (`choices[].message.content`) au lieu du
     * flux SSE (`delta`), et l'app affichait une bulle vide sans erreur.
     */
    val stream: Boolean,
    @SerialName("stream_options") val streamOptions: StreamOptions? = null,
    /** Progression de préremplissage (llama.cpp uniquement). */
    @SerialName("return_progress") val returnProgress: Boolean? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    /** Options de l'API Hermes, notamment `fast` pour le tier prioritaire. */
    @SerialName("model_options") val modelOptions: JsonObject? = null,
    /**
     * Le gabarit de conversation : les endpoints auto-hébergés (vLLM, llama.cpp) s'en servent
     * pour activer le raisonnement, comme le fait l'app de référence.
     */
    @SerialName("chat_template_kwargs") val chatTemplateKwargs: JsonElement? = null,
) {
    @Serializable
    data class StreamOptions(val include_usage: Boolean)
}

@Serializable
internal data class WireMessage(
    val role: String,
    /** Chaîne, ou tableau de parties multimodales (`{"type":"text",…}` / `{"type":"image_url",…}`). */
    val content: JsonElement,
)

@Serializable
internal data class ChatChunk(
    /** Le modèle que le serveur dit servir — l'alias demandé peut cacher le vrai. */
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: WireUsage? = null,
    val error: WireError? = null,
    @SerialName("prompt_progress") val promptProgress: WirePromptProgress? = null,
) {
    @Serializable
    data class Choice(
        val delta: WireDelta? = null,
        /**
         * Réponse **complète** : un serveur qui ignore `stream` (ou une demande sans
         * `stream`) répond `choices[].message.content` et non un flux `delta`. Sans ce
         * champ, le texte était perdu et la bulle restait vide.
         */
        val message: WireAssistantMessage? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    data class WireAssistantMessage(val content: String? = null)

    @Serializable
    data class WireDelta(
        val content: String? = null,
        /** Certains serveurs nomment le « thinking » `reasoning_content` (DeepSeek, vLLM). */
        @SerialName("reasoning_content") val reasoningContent: String? = null,
        /** D'autres le nomment `reasoning` (OpenRouter, gateway Hermes). */
        val reasoning: String? = null,
        @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
    )

    @Serializable
    data class WireToolCall(
        val index: Int = 0,
        val id: String? = null,
        val type: String? = null,
        val function: WireFunction? = null,
    )

    @Serializable
    data class WireFunction(
        val name: String? = null,
        val arguments: String? = null,
    )

    @Serializable
    data class WireError(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null,
    )

    @Serializable
    data class WirePromptProgress(
        val total: Int? = null,
        val cache: Int? = null,
        val processed: Int? = null,
    )
}

@Serializable
internal data class WireUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("prompt_tokens_details") val promptTokensDetails: WirePromptDetails? = null,
) {
    @Serializable
    data class WirePromptDetails(
        @SerialName("cached_tokens") val cachedTokens: Int? = null,
    )
}

/** Réponse de `GET /v1/models`. */
@Serializable
internal data class WireModelList(
    val data: List<WireModel>? = null,
) {
    @Serializable
    data class WireModel(val id: String? = null)
}

/**
 * Un sous-agent délégué, tel que le gateway le décrit.
 *
 * Voir [com.onyx.app.data.model.SubagentUpdate] : la lecture des trames `subagent.*` vit avec le
 * modèle, pas ici — ce fichier ne garde que ce qui est propre au fil OpenAI.
 */
