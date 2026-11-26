package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Une conversation retrouvée reprend sa session, elle ne se réécrit pas.**
 *
 * Tant qu'un tour repartait sans identifiant, le gateway retombait sur un id dérivé du premier
 * message : deux conversations au même premier mot se partageaient l'historique, et le tour
 * n'était plus « réveillable » côté serveur. Le pont est `X-Hermes-Session-Id` : l'app l'envoie
 * quand elle connaît la session, et reprend celui que le serveur renvoie (une compression le
 * change en cours de route).
 */
class SessionContinuityTest {

    private val sse = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Bonjour\"}}]}\n" +
        "\n" +
        "data: [DONE]\n" +
        "\n"

    private fun serve(headerSessionId: String? = null): MockWebServer {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .apply { headerSessionId?.let { setHeader("X-Hermes-Session-Id", it) } }
                .setBody(sse),
        )
        return server
    }

    private fun config(server: MockWebServer) = ServerConfig(
        id = "continuity-test",
        name = "Continuity test",
        baseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = "test-key",
        transport = Transport.HERMES_SESSIONS,
    )

    @Test
    fun `une session connue repart par son identifiant, et la reponse fixe celui en vigueur`() =
        runBlocking {
            val server = serve(headerSessionId = "sess-apres-compression")
            val events = ChatCompletionsClient(config(server), OkHttpClient())
                .stream(
                    ChatRequest(
                        history = listOf(ChatTurn(role = Role.USER, text = "salut")),
                        model = "hermes-kilo",
                        sessionId = "sess-42",
                    ),
                )
                .toList()

            val sent = server.takeRequest()
            assertEquals(
                "l'identifiant de la conversation voyage en en-tête",
                "sess-42",
                sent.getHeader("X-Hermes-Session-Id"),
            )
            val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
            assertEquals(
                "l'identifiant est aussi dans le corps, pour un relais qui ne garde pas l'en-tête",
                "sess-42",
                body["session_id"]?.jsonPrimitive?.content,
            )
            assertEquals(
                "l'app reprend l'identifiant que le serveur déclare en vigueur",
                listOf("sess-apres-compression"),
                events.filterIsInstance<TurnEvent.SessionId>().map { it.id },
            )
            server.shutdown()
        }

    @Test
    fun `une conversation neuve n annonce aucune session`() = runBlocking {
        val server = serve()
        val events = ChatCompletionsClient(config(server), OkHttpClient())
            .stream(
                ChatRequest(history = listOf(ChatTurn(role = Role.USER, text = "salut")), model = "hermes-kilo"),
            )
            .toList()

        val sent = server.takeRequest()
        assertNull(
            "sans session connue, l'app laisse le serveur dériver l'id du premier message",
            sent.getHeader("X-Hermes-Session-Id"),
        )
        val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertNull("aucun session_id dans le corps", body["session_id"])
        assertNull(
            "et rien à reprendre dans le flux",
            events.filterIsInstance<TurnEvent.SessionId>().firstOrNull(),
        )
        server.shutdown()
    }
}