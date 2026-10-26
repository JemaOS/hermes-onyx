package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.TokenUsage
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le chat du transport Dashboard (WebSocket).
 *
 * Un faux canal tient lieu de `ServeRpc`: il rejoue la session, le `prompt.submit` et les
 * notifications `method == "event"` — sans ouvrir de socket, mais sans tricher non plus: c'est
 * bien le vrai [HermesServeChat] qui tourne, avec sa machine à états d'événements.
 */
class HermesServeChatTest {

    private val config = ServerConfig(
        id = "s",
        name = "Dashboard",
        serveBaseUrl = "https://onyx.example",
        serveUsername = "alice",
        servePassword = "secret",
        transport = Transport.HERMES_SERVE,
    )

    private fun request(text: String = "Bonjour") = ChatRequest(
        history = listOf(ChatTurn(role = Role.USER, text = text)),
        model = "hermes-kilo",
    )

    @Test
    fun `un tour neuf cree la session et ramasse le texte`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject {
                    put("session_id", "runtime-1")
                    put("stored_session_id", "stored-1")
                })
                "prompt.submit" -> Reply(
                    buildJsonObject { },
                    emit = listOf(
                        delta("runtime-1", "Bon"),
                        delta("runtime-1", "jour"),
                        complete("runtime-1"),
                    ),
                )
                else -> Reply(buildJsonObject { })
            }
        }

        val events = withTimeout(5_000) { HermesServeChat(config, channel).stream(request()).toList() }

        assertEquals(
            listOf(
                TurnEvent.SessionId("stored-1"),
                TurnEvent.TextDelta("Bon"),
                TurnEvent.TextDelta("jour"),
                TurnEvent.Done,
            ),
            events,
        )
        val create = channel.calls.first { it.first == "session.create" }.second
        assertEquals("desktop", create["source"].asString())
        assertEquals("hermes-kilo", create["model"].asString())
    }

    @Test
    fun `une session connue est reprise, pas recreee`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            when (method) {
                "session.resume" -> Reply(buildJsonObject { put("session_id", "runtime-9") })
                "prompt.submit" -> Reply(
                    buildJsonObject { },
                    emit = listOf(delta("runtime-9", "suite"), complete("runtime-9")),
                )
                else -> Reply(buildJsonObject { })
            }
        }

        val events = withTimeout(5_000) {
            HermesServeChat(config, channel).stream(request(), storedSessionId = "stored-9").toList()
        }

        assertTrue(channel.calls.none { it.first == "session.create" })
        assertEquals("stored-9", channel.calls.first { it.first == "session.resume" }.second["session_id"].asString())
        assertEquals("stored-9", (events.first() as TurnEvent.SessionId).id)
        assertEquals(TurnEvent.Done, events.last())
    }

    @Test
    fun `un message complete reprend le texte quand rien n'a streame`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject { put("session_id", "r") })
                "prompt.submit" -> Reply(
                    buildJsonObject { },
                    emit = listOf(
                        ServeEvent(
                            type = "message.complete",
                            sessionId = "r",
                            payload = buildJsonObject {
                                put("text", "réponse entière")
                                put("usage", buildJsonObject {
                                    put("input", 12)
                                    put("output", 4)
                                    put("context_used", 16)
                                    put("context_max", 200)
                                })
                            },
                        ),
                    ),
                )
                else -> Reply(buildJsonObject { })
            }
        }

        val events = withTimeout(5_000) { HermesServeChat(config, channel).stream(request()).toList() }

        assertEquals(
            listOf(
                TurnEvent.SessionId("r"),
                TurnEvent.TextDelta("réponse entière"),
                TurnEvent.Usage(TokenUsage(input = 12, output = 4, contextUsed = 16, contextMax = 200)),
                TurnEvent.Done,
            ),
            events,
        )
    }

    @Test
    fun `un Dashboard qui ne connait pas la methode echoue franchement`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            if (method == "session.create") {
                Reply(buildJsonObject { }, fail = TransportError.Rpc(-32601, "Method not found"))
            } else {
                Reply(buildJsonObject { })
            }
        }

        val failure = runCatching {
            withTimeout(5_000) { HermesServeChat(config, channel).stream(request()).toList() }
        }.exceptionOrNull()

        assertTrue("un faux succès a été produit", failure != null)
        assertEquals(-32601, (failure as? TransportError.Rpc)?.code)
    }

    @Test
    fun `une connexion perdue pendant le tour ne passe pas pour un succes`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject { put("session_id", "r") })
                "prompt.submit" -> Reply(
                    buildJsonObject { },
                    emit = listOf(
                        delta("r", "commence"),
                        ServeEvent("connection.lost", null, buildJsonObject { }),
                    ),
                )
                else -> Reply(buildJsonObject { })
            }
        }

        val failure = runCatching {
            withTimeout(5_000) { HermesServeChat(config, channel).stream(request()).toList() }
        }.exceptionOrNull()

        assertTrue(failure is TransportError)
    }

    @Test
    fun `un arret de l'utilisateur interrompt le tour cote serveur`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject { put("session_id", "runtime-x") })
                else -> Reply(buildJsonObject { })
            }
        }

        // Le tour ne se termine jamais: le `withTimeout` annule le flux, et le `finally` doit
        // demander l'interruption du tour au serveur.
        runCatching {
            withTimeout(1_200) { HermesServeChat(config, channel).stream(request()).toList() }
        }

        assertTrue(
            "session.interrupt n'a pas été appelé",
            channel.calls.any { it.first == "session.interrupt" },
        )
    }

    private fun delta(session: String, text: String) = ServeEvent(
        type = "message.delta",
        sessionId = session,
        payload = buildJsonObject { put("text", text) },
    )

    private fun complete(session: String) = ServeEvent(
        type = "message.complete",
        sessionId = session,
        payload = buildJsonObject { put("status", "ok") },
    )

    /** Ce qu'un appel rend: un résultat, des événements à pousser, ou un échec. */
    private data class Reply(
        val result: JsonElement,
        val emit: List<ServeEvent> = emptyList(),
        val fail: TransportError? = null,
    )

    /**
     * Ce qui a été décidé du tour décide de ce que l'hôte en sait: le bouton d'arrêt l'interrompt,
     * un abandon le laisse finir (comme `LeavingATurnTests` du modèle).
     */
    @Test
    fun `un tour arrete interrompt l hote, un tour abandonne le laisse finir`() = runBlocking {
        fun channel() = FakeChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(
                    buildJsonObject {
                        put("session_id", "runtime-1")
                        put("stored_session_id", "stored-1")
                    },
                )

                // Le tour ne finit jamais de lui-même: c'est l'annulation qui décide.
                else -> Reply(buildJsonObject { }, emit = emptyList())
            }
        }

        val stopped = channel()
        val stop = launch {
            HermesServeChat(config, stopped).stream(request(), null, TurnDeparture()).collect { }
        }
        withTimeout(5_000) { while (stopped.calls.none { it.first == "prompt.submit" }) delay(10) }
        stop.cancelAndJoin()
        assertTrue(
            "l'arrêt interrompt le tour chez l'hôte",
            stopped.calls.any { it.first == "session.interrupt" },
        )

        val left = channel()
        val departure = TurnDeparture()
        departure.leave()
        val leaving = launch {
            HermesServeChat(config, left).stream(request(), null, departure).collect { }
        }
        withTimeout(5_000) { while (left.calls.none { it.first == "prompt.submit" }) delay(10) }
        leaving.cancelAndJoin()
        assertFalse(
            "un abandon laisse l'hôte finir le tour",
            left.calls.any { it.first == "session.interrupt" },
        )
    }

    /** Un `ServeChatChannel` scripté, sans socket. */
    private class FakeChannel(
        private val responder: (String, JsonObject) -> Reply,
    ) : ServeChatChannel {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        private var listener: ((ServeEvent) -> Unit)? = null

        override suspend fun connect() = Unit

        override suspend fun call(method: String, params: JsonObject, timeoutMillis: Long): JsonElement {
            calls += method to params
            val reply = responder(method, params)
            reply.emit.forEach { listener?.invoke(it) }
            reply.fail?.let { throw it }
            return reply.result
        }

        override fun addListener(listener: (ServeEvent) -> Unit): String {
            this.listener = listener
            return "listener"
        }

        override fun removeListener(key: String) {
            listener = null
        }

        override fun close() = Unit
    }
}
