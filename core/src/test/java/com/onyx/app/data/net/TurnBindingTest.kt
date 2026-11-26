package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tour doit survivre au départ de l'app.
 *
 * Ce que la personne vit : elle pose une question, elle sort de l'app, elle revient — et le tour
 * était **coupé**. La cause n'était pas le réseau : l'app envoyait elle-même l'ordre d'arrêter le
 * travail chez l'hôte, et déclarait le tour mort dès que le canal coupait après le début de la
 * réponse.
 *
 * La règle tient en deux points :
 *
 *  1. une coupure **rattache** l'app au tour en cours — la question n'est pas rejouée, et le
 *     serveur finit le travail ;
 *  2. une annulation — l'app qui s'en va, le processus qui meurt — **n'interrompt pas** l'hôte.
 *     Seul l'arrêt demandé par l'utilisateur le fait.
 */
class TurnBindingTest {

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
    fun `apres une coupure, l app se rattache au tour au lieu de le rejouer`() = runBlocking {
        val channel = FakeChannel { method, params ->
            when (method) {
                "session.resume" -> {
                    if (params["omit_messages"] == JsonPrimitive(false)) {
                        // Le tour a fini pendant la coupure : l'hôte rend sa réponse.
                        Reply(buildJsonObject {
                            put("session_id", "runtime-1")
                            put("running", false)
                            put("messages", buildJsonArray {
                                add(buildJsonObject {
                                    put("role", "user")
                                    put("content", "Bonjour")
                                })
                                add(buildJsonObject {
                                    put("role", "assistant")
                                    put("content", "Le texte entier")
                                })
                            })
                        })
                    } else {
                        Reply(buildJsonObject { put("session_id", "runtime-1") })
                    }
                }
                // Le tour part, puis le canal coupe : la réponse a commencé.
                "prompt.submit" -> Reply(
                    buildJsonObject { },
                    emit = listOf(delta("runtime-1", "Le texte"), lost()),
                )
                else -> Reply(buildJsonObject { })
            }
        }

        val events = withTimeout(10_000) {
            HermesServeChat(config, channel).stream(request(), "stored-1").toList()
        }

        assertEquals(
            "la question ne se rejoue pas : le bot ne doit pas y répondre deux fois",
            1,
            channel.calls.count { it.first == "prompt.submit" },
        )
        assertTrue(
            "la réponse de l'hôte remplace le fragment reçu avant la coupure",
            events.any { it is TurnEvent.TextFinal && it.text == "Le texte entier" },
        )
        assertTrue("le tour se termine", events.any { it is TurnEvent.Done })
        assertFalse(
            "un tour coupé n'est pas interrompu chez l'hôte",
            channel.calls.any { it.first == "session.interrupt" },
        )
    }

    @Test
    fun `le rattachement demande l historique, c est lui qui porte la reponse`() = runBlocking {
        val channel = FakeChannel { method, params ->
            when (method) {
                "session.resume" -> if (params["omit_messages"] == JsonPrimitive(false)) {
                    Reply(buildJsonObject {
                        put("session_id", "runtime-1")
                        put("running", false)
                        put("messages", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "assistant")
                                put("content", "Fini pendant la coupure")
                            })
                        })
                    })
                } else {
                    Reply(buildJsonObject { put("session_id", "runtime-1") })
                }
                "prompt.submit" -> Reply(buildJsonObject { }, emit = listOf(lost()))
                else -> Reply(buildJsonObject { })
            }
        }

        withTimeout(10_000) {
            HermesServeChat(config, channel).stream(request(), "stored-1").toList()
        }

        val resumes = channel.calls.filter { it.first == "session.resume" }
        assertEquals("le tour est repris, puis rattaché", 2, resumes.size)
        assertEquals(
            "le premier passage ne demande pas l'historique",
            JsonPrimitive(true),
            resumes[0].second["omit_messages"],
        )
        assertEquals(
            "le rattachement le demande : c'est là qu'est la réponse",
            JsonPrimitive(false),
            resumes[1].second["omit_messages"],
        )
    }

    @Test
    fun `au rattachement, c est la session du tour qui est reprise`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject {
                    put("session_id", "runtime-1")
                    put("stored_session_id", "stored-1")
                })
                "prompt.submit" -> Reply(buildJsonObject { }, emit = listOf(lost()))
                "session.resume" -> Reply(buildJsonObject {
                    put("session_id", "runtime-1")
                    put("running", false)
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "assistant")
                            put("content", "Fini pendant la coupure")
                        })
                    })
                })
                else -> Reply(buildJsonObject { })
            }
        }

        withTimeout(10_000) {
            HermesServeChat(config, channel).stream(request()).toList()
        }

        assertEquals(
            "une session neuve ne reverrait pas le tour en cours",
            1,
            channel.calls.count { it.first == "session.create" },
        )
        val resume = channel.calls.first { it.first == "session.resume" }
        assertEquals(
            "c'est la session du tour qu on reprend",
            "stored-1",
            resume.second["session_id"].asString(),
        )
    }

    @Test
    fun `l app qui s en va n interrompt pas le travail chez l hote`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject {
                    put("session_id", "runtime-1")
                    put("stored_session_id", "stored-1")
                })
                // Aucune fin : le tour attend, et l'app s'en va.
                "prompt.submit" -> Reply(buildJsonObject { })
                else -> Reply(buildJsonObject { })
            }
        }

        val job = launch {
            HermesServeChat(config, channel).stream(request(), departure = TurnDeparture()).toList()
        }
        withTimeout(5_000) { while (channel.calls.none { it.first == "prompt.submit" }) delay(10) }
        job.cancelAndJoin()

        assertFalse(
            "sortir de l'app ne doit pas arreter le travail : sa reponse est la au retour",
            channel.calls.any { it.first == "session.interrupt" },
        )
    }

    @Test
    fun `l arret de l utilisateur interrompt bien le travail chez l hote`() = runBlocking {
        val channel = FakeChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject {
                    put("session_id", "runtime-1")
                    put("stored_session_id", "stored-1")
                })
                "prompt.submit" -> Reply(buildJsonObject { })
                else -> Reply(buildJsonObject { })
            }
        }

        val departure = TurnDeparture()
        departure.stop()
        val job = launch {
            HermesServeChat(config, channel).stream(request(), departure = departure).toList()
        }
        withTimeout(5_000) { while (channel.calls.none { it.first == "prompt.submit" }) delay(10) }
        job.cancelAndJoin()

        assertTrue(
            "l'arret demande par l'utilisateur interrompt le tour",
            channel.calls.any { it.first == "session.interrupt" },
        )
    }

    private fun delta(session: String, text: String) = ServeEvent(
        type = "message.delta",
        sessionId = session,
        payload = buildJsonObject { put("text", text) },
    )

    /** La coupure : l'evenement que le socket envoie quand il meurt. */
    private fun lost() = ServeEvent(
        type = "connection.lost",
        sessionId = null,
        payload = buildJsonObject { },
    )

    private data class Reply(
        val result: JsonElement,
        val emit: List<ServeEvent> = emptyList(),
        val fail: TransportError? = null,
    )

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
