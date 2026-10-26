package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.PrefillProgress
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentUpdate
import com.onyx.app.data.model.TokenUsage
import com.onyx.app.data.model.Transport
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Client du point d'entrée OpenAI-compatible `/v1/chat/completions` en streaming SSE.
 *
 * Comme `ChatCompletionsTransport` 
 *: le flux se termine sur `[DONE]` —
 * jamais sur `finish_reason`, car le bloc `usage` arrive après — et chaque `choices[].delta`
 * alimente soit le texte, soit le « reasoning », soit un appel d'outil.
 *
 * Le transport est **sans état**: l'appelant renvoie tout l'historique à chaque tour.
 */
class ChatCompletionsClient(
    private val config: ServerConfig,
    private val httpClient: OkHttpClient = OnyxHttp.clientFor(config),
) {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
    }

    /**
     * Lance un tour et émet les [TurnEvent] au fil de l'eau.
     *
     * L'annulation du collecteur annule la requête HTTP. Les erreurs réseau sont converties en
     * [TransportError] classées « injoignable » (réessai sûr) ou « flux coupé ».
     */
    fun stream(request: ChatRequest): Flow<TurnEvent> = channelFlow {
        val scope = this
        val httpRequest = try {
            buildRequest(request)
        } catch (error: TransportError) {
            close(error)
            return@channelFlow
        } catch (error: IOException) {
            close(TransportError.from(error, afterHeaders = false))
            return@channelFlow
        }

        val call = httpClient.newCall(httpRequest)
        invokeOnClose { call.cancel() }

        withContext(Dispatchers.IO) {
            var afterHeaders = false
            try {
                call.execute().use { response ->
                    afterHeaders = true
                    if (!response.isSuccessful) {
                        val body = runCatching { response.peekBody(MAX_ERROR_BODY).string() }.getOrDefault("")
                        scope.close(TransportError.Http(response.code, body))
                        return@use
                    }
                    StreamPump(json, scope).pump(response)
                }
            } catch (error: IOException) {
                if (call.isCanceled()) {
                    scope.close(TransportError.Cancelled)
                } else {
                    scope.close(TransportError.from(error, afterHeaders))
                }
            }
        }
    }

    /**
     * Sonde de connexion: `GET /v1/models`, avec un timeout court.
     *
     * Alimente le bouton « Tester la connexion » de l'écran Réglages.
     */
    suspend fun testConnection(): ConnectionProbe = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        fun elapsed() = System.currentTimeMillis() - started

        if (!config.isUsable) {
            return@withContext ConnectionProbe(false, OnyxLocalization["Base URL is empty."], 0L, emptyList())
        }

        val request = Request.Builder()
            .url(config.apiBaseUrl + ENDPOINT_MODELS)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .apply {
                val key = config.effectiveApiKey.trim()
                if (key.isNotEmpty()) header("Authorization", "Bearer $key")
            }
            .build()

        try {
            OnyxHttp.shortClient().newCall(request).execute().use { response ->
                val body = runCatching { response.peekBody(MAX_ERROR_BODY).string() }.getOrDefault("")
                if (!response.isSuccessful) {
                    val detail = body.takeIf { it.isNotBlank() }?.let { " ${it.take(200)}" }.orEmpty()
                    return@withContext ConnectionProbe(
                        ok = false,
                        message = OnyxLocalization.format("The server returned HTTP %1\$d.%2\$s", response.code, detail),
                        latencyMs = elapsed(),
                        models = emptyList(),
                    )
                }
                val models = runCatching {
                    json.decodeFromString<WireModelList>(body).data.orEmpty().mapNotNull { it.id }
                }.getOrDefault(emptyList())
                ConnectionProbe(
                    ok = true,
                    message = if (models.isEmpty()) {
                        OnyxLocalization["Connected."]
                    } else {
                        OnyxLocalization.format("Connected — %1\$d model(s).", models.size)
                    },
                    latencyMs = elapsed(),
                    models = models,
                )
            }
        } catch (error: IOException) {
            ConnectionProbe(false, TransportError.from(error, afterHeaders = false).userMessage(), elapsed(), emptyList())
        }
    }

    /** Construit la requête HTTP POST du flux. */
    private fun buildRequest(request: ChatRequest): Request {
        if (!config.isUsable) throw TransportError.BadUrl(OnyxLocalization["empty base URL"])
        if (config.transport == Transport.HERMES_SESSIONS && config.effectiveApiKey.isBlank()) {
            throw TransportError.MissingApiKey
        }

        val messages = buildList {
            request.instructions?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(WireMessage(role = Role.SYSTEM.wire, content = JsonPrimitive(it)))
            }
            request.history.forEach { add(it.toWireMessage()) }
        }

        val payload = ChatRequestBody(
            model = request.model ?: config.effectiveModel ?: "",
            messages = messages,
            stream = true,
            streamOptions = ChatRequestBody.StreamOptions(include_usage = true),
            returnProgress = if (request.returnProgress) true else null,
            reasoningEffort = request.reasoningEffort,
            modelOptions = chatModelOptions(request),
            chatTemplateKwargs = if (request.reasoningEffort == null || request.reasoningEffort == "none") {
                null
            } else {
                buildJsonObject { put("enable_thinking", true) }
            },
        )

        val body = json.encodeToString(ChatRequestBody.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url(config.apiBaseUrl + ENDPOINT_CHAT)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .apply {
                val key = config.effectiveApiKey.trim()
                if (key.isNotEmpty()) header("Authorization", "Bearer $key")
            }
            .build()
    }

    private fun ChatTurn.toWireMessage(): WireMessage {
        val content = if (images.isEmpty()) {
            JsonPrimitive(text)
        } else {
            buildJsonArray {
                if (text.isNotEmpty()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                }
                images.forEach { url ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", url) })
                    })
                }
            }
        }
        return WireMessage(role = role.wire, content = content)
    }

    /** Lit le corps SSE ligne par ligne et pousse les événements dans le canal. */
    private class StreamPump(
        private val json: Json,
        private val scope: ProducerScope<TurnEvent>,
    ) {
        private val parser = SseParser()
        private val toolCalls = ToolCallAccumulator()
        private val announcedTools = LinkedHashSet<String>()

        /** Les sous-agents déjà annoncés, par `subagent_id`: `subagent.start` n'arrive qu'une fois. */
        private val announcedSubagents = LinkedHashSet<String>()

        suspend fun pump(response: Response) {
            val body = response.body
            if (body == null) {
                scope.close(TransportError.Malformed(OnyxLocalization["empty response body"]))
                return
            }

            val source = body.source()
            var done = false
            while (!done) {
                val line = source.readUtf8Line() ?: break
                done = handle(parser.feed(line))
            }

            if (!done) {
                handle(parser.finish())
            }

            // Un flux OpenAI n'a pas d'événement de fin par outil: on clôt ce qui était annoncé.
            announcedTools.forEach { scope.send(TurnEvent.ToolFinished(name = it, failed = false)) }
            // Un sous-agent dont le `subagent.complete` n'est pas venu est clos de même: une
            // ligne restée « en cours » tournerait pour toujours après la fin du tour.
            announcedSubagents.toList().forEach { id ->
                announcedSubagents -= id
                scope.send(
                    TurnEvent.Subagent(
                        SubagentUpdate(
                            id = id,
                            phase = SubagentUpdate.Phase.Completed(succeeded = true, summary = null, duration = null),
                        ),
                    ),
                )
            }
            scope.send(TurnEvent.Done)
            scope.close()
        }

        /** Traite un événement SSE; renvoie `true` si le flux est terminé. */
        /** Le dernier modèle annoncé par le serveur, pour ne l'envoyer qu'au changement. */
        private var announcedModel: String? = null

        private suspend fun handle(event: SseEvent?): Boolean {
            if (event == null) return false

            if (event.event == HERMES_TOOL_PROGRESS_EVENT) {
                handleHermesToolProgress(event.data)
                return false
            }

            if (event.event == HERMES_STATUS_EVENT) {
                handleHermesStatus(event.data)
                return false
            }

            // Un sous-agent délégué: le gateway le décrit à part des outils, sur ses propres
            // trames `subagent.*` (contrat `tui_gateway/contracts/events.py`). C'est elles, et
            // elles seules, qui portent le `subagent_id` qu'attendent steer, stop et la
            // transcription — sans elles la ligne ne serait qu'une carte d'outil inerte.
            if (SubagentUpdate.isSubagentEvent(event.event.orEmpty())) {
                handleHermesSubagent(event.event.orEmpty(), event.data.orEmpty())
                return false
            }

            val data = event.data.trim()
            if (data.isEmpty()) return false
            if (data == DONE_SENTINEL) return true

            val chunk = try {
                json.decodeFromString<ChatChunk>(data)
            } catch (error: Exception) {
                throw TransportError.Malformed(OnyxLocalization.format("unreadable chunk: %1\$s", data.take(200)))
            }

            chunk.error?.let { wire ->
                throw TransportError.Malformed(wire.message ?: OnyxLocalization["the server reported an error"])
            }

            chunk.promptProgress?.let { progress ->
                val total = progress.total ?: 0
                if (total > 0) {
                    scope.send(
                        TurnEvent.Prefill(
                            PrefillProgress(
                                processed = progress.processed ?: 0,
                                total = total,
                                cached = progress.cache ?: 0,
                            ),
                        ),
                    )
                }
            }

            chunk.usage?.let { scope.send(TurnEvent.Usage(it.toTokenUsage())) }

            // Le modèle que la réponse dit servir: il peut différer de l'alias demandé, et la
            // barre du haut doit nommer le vrai (il suit donc un changement de modèle).
            chunk.model?.takeIf { it.isNotBlank() && it != announcedModel }?.let { served ->
                announcedModel = served
                scope.send(TurnEvent.ModelInUse(served))
            }

            for (choice in chunk.choices.orEmpty()) {
                // Réponse NON streamée: le serveur a ignoré `stream` et renvoie le texte
                // entier dans `message.content` (pas de `delta`). Sans ce cas, la réponse
                // était purement perdue: bulle vide, aucune erreur.
                choice.message?.content?.takeIf { it.isNotEmpty() }?.let { whole ->
                    scope.send(TurnEvent.TextFinal(whole))
                }
                val delta = choice.delta ?: continue
                delta.content?.takeIf { it.isNotEmpty() }?.let { scope.send(TurnEvent.TextDelta(it)) }
                val reasoning = delta.reasoningContent ?: delta.reasoning
                reasoning?.takeIf { it.isNotEmpty() }?.let { scope.send(TurnEvent.ReasoningDelta(it)) }
                for (toolCall in delta.toolCalls.orEmpty()) {
                    val started = toolCalls.append(toolCall)
                    if (started != null) {
                        announcedTools += started
                        scope.send(TurnEvent.ToolStarted(name = started, preview = null, args = null))
                    }
                }
            }
            return false
        }

        /** Événement propriétaire du gateway Hermes, émis au-dessus du protocole OpenAI. */
        private suspend fun handleHermesToolProgress(data: String) {
            val progress = try {
                json.decodeFromString<HermesToolProgress>(data)
            } catch (error: Exception) {
                return
            }
            val name = progress.tool ?: return
            when (progress.status?.lowercase()) {
                "completed" -> {
                    announcedTools -= name
                    scope.send(TurnEvent.ToolFinished(name = name, failed = false))
                    announceDelegatedTool(progress, running = false)
                }
                "failed", "error" -> {
                    announcedTools -= name
                    scope.send(TurnEvent.ToolFinished(name = name, failed = true))
                    announceDelegatedTool(progress, running = false)
                }
                else -> {
                    if (announcedTools.add(name)) {
                        scope.send(TurnEvent.ToolStarted(name = name, preview = progress.label))
                        announceDelegatedTool(progress, running = true)
                    }
                }
            }
        }

    /**
     * Une trame `subagent.*` du gateway (`subagent.start` / `.tool` / `.progress` / `.complete`).
     *
     * Le gateway nomme l'enfant par `subagent_id` (sinon `child_session_id`), porte son but, sa
     * place dans une série, son dernier outil et sa session. Tout cela devient un
     * [TurnEvent.Subagent]: la ligne se crée au `start`, se met à jour au `tool`/`progress`, et
     * se ferme au `complete`. Un identifiant manquant fait ignorer la trame — mieux vaut pas de
     * ligne qu'une ligne qui piloterait dans le vide.
     */
    private suspend fun handleHermesSubagent(event: String, data: String) {
        val payload = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        val update = SubagentUpdate.parse(event, payload) ?: return
        when (update.phase) {
            SubagentUpdate.Phase.Started -> announcedSubagents += update.id
            is SubagentUpdate.Phase.Completed -> announcedSubagents -= update.id
            else -> Unit
        }
        scope.send(TurnEvent.Subagent(update))
    }

    /**
     * Un `delegate_task` vu comme un simple outil: sur l'API de conversation, le gateway
     * **n'envoie aucune trame `subagent.*`** (le `/v1` ne relaie pas ce vocabulaire). Le seul
     * vestige de la délégation est cet appel d'outil, dont le libellé porte le but (« Delegating
     * N tasks: … »). On en fait une ligne **dispatchée**: le but est connu, l'état réel de
     * l'enfant ne l'est pas, et rien ne peut être piloté — ce que l'écran dit.
     */
    private suspend fun announceDelegatedTool(progress: HermesToolProgress, running: Boolean) {
        val name = progress.tool ?: return
        if (name != DELEGATE_TOOL) return
        val goal = progress.label.orEmpty()
        if (running && goal.isBlank()) return
        val id = "delegate:" + (progress.callId ?: name)
        if (running) {
            announcedSubagents += id
            scope.send(
                TurnEvent.Subagent(
                    SubagentUpdate(id = id, goal = goal, taskCount = 1, phase = SubagentUpdate.Phase.Dispatched),
                ),
            )
        } else if (announcedSubagents.remove(id)) {
            scope.send(
                TurnEvent.Subagent(
                    SubagentUpdate(
                        id = id,
                        goal = goal,
                        phase = SubagentUpdate.Phase.Completed(succeeded = true, summary = null, duration = null),
                    ),
                ),
            )
        }
    }

    /** Statut du gateway (`hermes.status`): réessai provider, indisponibilité, etc. */
    private suspend fun handleHermesStatus(data: String) {
        val status = try {
            json.decodeFromString<HermesStatus>(data)
        } catch (error: Exception) {
            return
        }
        val text = status.text?.trim().orEmpty()
        if (text.isEmpty()) return
        val error = status.kind?.lowercase()?.contains("error") == true
        scope.send(TurnEvent.Status(text = text.take(300), isError = error))
    }
    }

    /** Assemble les `tool_calls` fragmentés en plusieurs deltas (nom puis arguments). */
    private class ToolCallAccumulator {
        private val announced = LinkedHashMap<Int, String>()

        /** Renvoie le nom de l'outil la première fois qu'il devient connu. */
        fun append(call: ChatChunk.WireToolCall): String? {
            val name = call.function?.name
            if (!name.isNullOrEmpty() && announced[call.index] != name) {
                announced[call.index] = name
                return name
            }
            return null
        }
    }

    @Serializable
    private data class HermesStatus(
        val kind: String? = null,
        val text: String? = null,
    )

    @Serializable
    private data class HermesToolProgress(
        val tool: String? = null,
        val label: String? = null,
        val status: String? = null,
        /** L'identifiant de l'appel: stable entre le départ et la fin, sert de clé de ligne. */
        @SerialName("toolCallId") val callId: String? = null,
    )

    companion object {
        const val ENDPOINT_CHAT = "/v1/chat/completions"
        const val ENDPOINT_MODELS = "/v1/models"

        private const val DONE_SENTINEL = "[DONE]"
        private const val HERMES_TOOL_PROGRESS_EVENT = "hermes.tool.progress"
        private const val HERMES_STATUS_EVENT = "hermes.status"

        /**
         * Le nom du `delegate_task` du gateway: c'est lui, et lui seul, qui délègue une tâche à
         * un sous-agent. L'API de conversation ne nomme jamais l'enfant — il n'y a donc que ce
         * nom d'outil pour reconnaître une délégation sur ce transport.
         */
        private const val DELEGATE_TOOL = "delegate_task"
        private const val MAX_ERROR_BODY = 4_000L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Seul un tour explicitement rapide porte `model_options`; les autres requêtes restent inchangées. */
internal fun chatModelOptions(request: ChatRequest): JsonObject? =
    if (!request.fast) null else buildJsonObject { put("fast", true) }

private fun WireUsage.toTokenUsage(): TokenUsage {
    val input = promptTokens ?: 0
    val output = completionTokens ?: 0
    return TokenUsage(
        input = input,
        output = output,
        cached = promptTokensDetails?.cachedTokens,
        contextUsed = input + output,
    )
}

/** Résultat d'une sonde de connexion, pour l'écran Réglages. */
data class ConnectionProbe(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long,
    val models: List<String>,
)
