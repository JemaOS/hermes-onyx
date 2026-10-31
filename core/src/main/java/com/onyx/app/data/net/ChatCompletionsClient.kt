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
 * Miroir de `ChatCompletionsTransport` côté référence
 * (Echo/Services/Transport/ChatCompletionsTransport) : le flux se termine sur `[DONE]` —
 * jamais sur `finish_reason`, car le bloc `usage` arrive après — et chaque `choices[].delta`
 * alimente soit le texte, soit le « reasoning », soit un appel d'outil.
 *
 * Le transport est **sans état** : l'appelant renvoie tout l'historique à chaque tour.
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
     * Sonde de connexion : `GET /v1/models`, avec un timeout court.
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
                    // **L'adresse tentée, le code, et le corps du serveur.** Un 401 qui dit
                    // « clé d'installation absente ou invalide » ne doit pas se lire « vérifiez
                    // l'adresse » : le corps est repris verbatim (voir ProbeFailure).
                    return@withContext ConnectionProbe(
                        ok = false,
                        message = ProbeFailure.message(
                            address = config.apiBaseUrl,
                            reason = OnyxLocalization.format("The server returned HTTP %1\$d.%2\$s", response.code, ""),
                            rawBody = body,
                        ),
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
                        // LOT anatomie-des-contenus : le compte s'accorde, et la phrase **ne répète
                        // plus l'état** — la feuille de connexion l'affiche sous un titre qui dit
                        // déjà « Connecté ». « 1 modèle » / « 12 modèles », jamais « 1 modèle(s) ».
                        OnyxLocalization.format(
                            if (models.size == 1) "%1\$d model" else "%1\$d models",
                            models.size,
                        )
                    },
                    latencyMs = elapsed(),
                    models = models,
                )
            }
        } catch (error: IOException) {
            // L'adresse **réellement tentée** est nommée : « adresse introuvable » sans elle
            // envoie l'utilisateur chercher dans les réglages sans savoir quoi corriger.
            ConnectionProbe(
                ok = false,
                message = ProbeFailure.message(
                    config.apiBaseUrl,
                    TransportError.from(error, afterHeaders = false).userMessage(),
                ),
                latencyMs = elapsed(),
                models = emptyList(),
            )
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

        /**
         * Les fragments d'une trame **pas encore entière**. Le fil peut couper une trame d'appel
         * d'outil entre deux morceaux : on accumule ici jusqu'à obtenir un JSON complet. Un morceau
         * partiel n'est **jamais** une erreur, et ne montre jamais le JSON brut.
         */
        private val pending = StringBuilder()

        /** Les sous-agents déjà annoncés, par `subagent_id` : `subagent.start` n'arrive qu'une fois. */
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

            // **Un fragment resté sans son entier : le flux s'est interrompu avant la fin.** On le
            // dit en mots (aucun JSON brut, aucun chemin, aucune commande), et le tour vaut un vrai
            // réessai — jamais un « Réessayer » qui ne réessaie rien.
            if (!done && pending.isNotEmpty()) {
                throw TransportError.Truncated()
            }

            // Un appel d'outil dont les arguments ne se sont pas clos est montré quand même : le tour
            // se termine, on n'attend pas une pièce qui ne viendra pas.
            toolCalls.flush().forEach { call ->
                announcedTools += call.name
                scope.send(TurnEvent.ToolStarted(name = call.name, preview = null, args = call.args))
            }

            // Un flux OpenAI n'a pas d'événement de fin par outil : on clôt ce qui était annoncé.
            announcedTools.forEach { scope.send(TurnEvent.ToolFinished(name = it, failed = false)) }
            // Un sous-agent dont le `subagent.complete` n'est pas venu est clos de même : une
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

        /** Traite un événement SSE ; renvoie `true` si le flux est terminé. */
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

            // Un sous-agent délégué : le gateway le décrit à part des outils, sur ses propres
            // trames `subagent.*` (contrat `tui_gateway/contracts/events.py`). C'est elles, et
            // elles seules, qui portent le `subagent_id` qu'attendent steer, stop et la
            // transcription — sans elles la ligne ne serait qu'une carte d'outil inerte.
            if (SubagentUpdate.isSubagentEvent(event.event.orEmpty())) {
                handleHermesSubagent(event.event.orEmpty(), event.data.orEmpty())
                return false
            }

            val piece = event.data
            val trimmed = piece.trim()
            // Seule une pièce **réellement vide** est ignorée : un morceau d'un seul espace est une
            // partie de la trame, on ne le jette pas — sinon un fragment coupé au milieu d'une
            // chaîne perdrait cet espace.
            if (piece.isEmpty()) return false
            if (trimmed == DONE_SENTINEL) return true

            // **Un fragment n'est JAMAIS une erreur.** Le fil peut couper une trame d'appel d'outil
            // entre deux morceaux : on accumule jusqu'à obtenir un JSON entier, puis on l'analyse.
            // On accumule la pièce **telle quelle** : un `.trim()` en couperait les espaces de bord,
            // et un fragment coupé au milieu d'une chaîne perdrait cet espace en se réassemblant.
            for (candidate in if (piece == trimmed) listOf(piece) else listOf(piece, trimmed)) {
                parseChunk(candidate)?.let {
                    pending.setLength(0)
                    return consumeChunk(it)
                }
            }
            if (pending.isNotEmpty()) {
                parseChunk(pending.toString() + piece)?.let {
                    pending.setLength(0)
                    return consumeChunk(it)
                }
            }
            pending.append(piece)
            return false
        }

        /** Analyse un texte qui **est** un chunk entier ; `null` tant qu'il ne l'est pas encore. */
        private fun parseChunk(text: String): ChatChunk? =
            runCatching { json.decodeFromString<ChatChunk>(text) }.getOrNull()

        /** Traite un chunk entier : erreur applicative, usage, modèle, texte, raisonnement, outils. */
        private suspend fun consumeChunk(chunk: ChatChunk): Boolean {
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

            // Le modèle que la réponse dit servir : il peut différer de l'alias demandé, et la
            // barre du haut doit nommer le vrai (il suit donc un changement de modèle).
            chunk.model?.takeIf { it.isNotBlank() && it != announcedModel }?.let { served ->
                announcedModel = served
                scope.send(TurnEvent.ModelInUse(served))
            }

            for (choice in chunk.choices.orEmpty()) {
                // Réponse NON streamée : le serveur a ignoré `stream` et renvoie le texte
                // entier dans `message.content` (pas de `delta`). Sans ce cas, la réponse
                // était purement perdue : bulle vide, aucune erreur.
                choice.message?.content?.takeIf { it.isNotEmpty() }?.let { whole ->
                    scope.send(TurnEvent.TextFinal(whole))
                }
                val delta = choice.delta ?: continue
                delta.content?.takeIf { it.isNotEmpty() }?.let { scope.send(TurnEvent.TextDelta(it)) }
                val reasoning = delta.reasoningContent ?: delta.reasoning
                reasoning?.takeIf { it.isNotEmpty() }?.let { scope.send(TurnEvent.ReasoningDelta(it)) }
                for (toolCall in delta.toolCalls.orEmpty()) {
                    for (call in toolCalls.feed(toolCall)) {
                        announcedTools += call.name
                        scope.send(TurnEvent.ToolStarted(name = call.name, preview = null, args = call.args))
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
                        // Le libellé passe la garde : un chemin interne du serveur n'atteint jamais
                        // la rangée de l'appel (le nom du fichier reste, le dossier non).
                        scope.send(TurnEvent.ToolStarted(name = name, preview = StreamLeak.preview(progress.label)))
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
     * [TurnEvent.Subagent] : la ligne se crée au `start`, se met à jour au `tool`/`progress`, et
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
     * Un `delegate_task` vu comme un simple outil : sur l'API de conversation, le gateway
     * **n'envoie aucune trame `subagent.*`** (le `/v1` ne relaie pas ce vocabulaire). Le seul
     * vestige de la délégation est cet appel d'outil, dont le libellé porte le but (« Delegating
     * N tasks: … »). On en fait une ligne **dispatchée** : le but est connu, l'état réel de
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

    /** Statut du gateway (`hermes.status`) : réessai provider, indisponibilité, etc. */
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

    /**
     * Assemble les `tool_calls` **fragmentés** en plusieurs deltas : le nom, puis les arguments,
     * morceau par morceau.
     *
     * Rien n'est analysé avant que le JSON des arguments ne soit **complet** — un fragment partiel
     * n'est jamais une erreur, et aucun morceau brut n'est montré. Un identifiant **neuf** sur un
     * index déjà vu ouvre un appel **de plus** (les fournisseurs d'appels parallèles réutilisent
     * l'index, et distinguent les appels par l'identifiant seul).
     */
    private class ToolCallAccumulator {

        private class Slot {
            var id: String? = null
            var name: String? = null
            var seenArguments = false
            val arguments = StringBuilder()
            var announced = false
        }

        /** Un appel prêt : son nom, et ses arguments entiers (`null` quand il n'en porte pas). */
        class Call(val name: String, val args: String?)

        private val slots = LinkedHashMap<Int, Slot>()

        /** L'appel courant par index brut : un identifiant neuf le fait changer d'appel. */
        private val activeSlot = HashMap<Int, Int>()

        /** Fusionne un delta ; rend les appels devenus **complets** depuis la dernière fois. */
        fun feed(call: ChatChunk.WireToolCall): List<Call> {
            val raw = call.index
            activeSlot.putIfAbsent(raw, raw)
            val current = activeSlot.getValue(raw)
            val newId = call.id
            // Un identifiant neuf sur un index déjà servi, une fois ses arguments clos, ouvre un appel.
            val previous = slots[current]
            if (!newId.isNullOrEmpty() && previous != null && previous.id != null && previous.id != newId &&
                StreamFragment.argumentsClosed(previous.arguments.toString())
            ) {
                activeSlot[raw] = (slots.keys.maxOrNull() ?: -1) + 1
            }
            val slot = slots.getOrPut(activeSlot.getValue(raw)) { Slot() }
            if (!newId.isNullOrEmpty()) slot.id = newId
            call.function?.let { function ->
                function.name?.takeIf { it.isNotEmpty() }?.let { slot.name = it }
                function.arguments?.let { arguments ->
                    slot.seenArguments = true
                    if (arguments.isNotEmpty()) slot.arguments.append(arguments)
                }
            }
            return if (!slot.announced && slot.name != null && slot.seenArguments &&
                slot.arguments.isNotEmpty() && StreamFragment.argumentsClosed(slot.arguments.toString())
            ) {
                slot.announced = true
                listOf(Call(slot.name!!, slot.arguments.toString()))
            } else {
                emptyList()
            }
        }

        /** Les appels jamais annoncés (arguments non clos) : montrés en fin de flux, sans attendre. */
        fun flush(): List<Call> = slots.values
            .filter { !it.announced && it.name != null }
            .map { slot ->
                slot.announced = true
                Call(slot.name!!, slot.arguments.toString().ifEmpty { null })
            }
    }

    /**
     * Les règles **pures** de l'assemblage d'un fragment : quand un JSON est-il complet ?
     *
     * On ne conclut pas sur la **validité** JSON stricte (un appel réparable, à virgule finale, se
     * pose aussi) mais sur la **fermeture** : accolades et crochets équilibrés, hors chaînes, en
     * tenant compte de l'échappement `\"`. C'est ce qui décide qu'un morceau est entier — et donc
     * qu'on peut l'analyser sans jamais exposer un demi-JSON.
     */
    internal object StreamFragment {
        fun argumentsClosed(text: String): Boolean {
            var depth = 0
            var started = false
            var inString = false
            var escaped = false
            for (character in text) {
                if (inString) {
                    when {
                        escaped -> escaped = false
                        character == '\\' -> escaped = true
                        character == '"' -> inString = false
                    }
                    continue
                }
                when (character) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth += 1
                        started = true
                    }
                    '}', ']' -> depth -= 1
                }
            }
            return started && depth == 0 && !inString
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
        /** L'identifiant de l'appel : stable entre le départ et la fin, sert de clé de ligne. */
        @SerialName("toolCallId") val callId: String? = null,
    )

    companion object {
        const val ENDPOINT_CHAT = "/v1/chat/completions"
        const val ENDPOINT_MODELS = "/v1/models"

        private const val DONE_SENTINEL = "[DONE]"
        private const val HERMES_TOOL_PROGRESS_EVENT = "hermes.tool.progress"
        private const val HERMES_STATUS_EVENT = "hermes.status"

        /**
         * Le nom du `delegate_task` du gateway : c'est lui, et lui seul, qui délègue une tâche à
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
