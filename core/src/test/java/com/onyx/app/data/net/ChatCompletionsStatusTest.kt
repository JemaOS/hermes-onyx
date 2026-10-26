package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le gateway remonte ses réessais provider en `hermes.status`: sans ce décodage, l'app restait
 * sur « travaille » avec une bulle vide (le texte n'arrivait jamais).
 */
class ChatCompletionsStatusTest {
    @Test
    fun `hermes status is surfaced before the text`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val sse = "event: hermes.status\n" +
            "data: {\"kind\":\"lifecycle\",\"text\":\"Provider temporarily unavailable \u2014 retrying in 16s\"}\n" +
            "\n" +
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Bonjour\"}}]}\n" +
            "\n" +
            "data: [DONE]\n" +
            "\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )

        val config = ServerConfig(
            id = "status-test",
            name = "Status test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            transport = Transport.CHAT_COMPLETIONS,
        )
        val events = ChatCompletionsClient(config, OkHttpClient())
            .stream(ChatRequest(history = listOf(ChatTurn(role = Role.USER, text = "salut"))))
            .toList()

        val statuses = events.filterIsInstance<TurnEvent.Status>()
        assertEquals(1, statuses.size)
        assertTrue(statuses.single().text.contains("retrying in 16s"))
        assertTrue(events.any { it is TurnEvent.TextDelta && it.text == "Bonjour" })
        server.shutdown()
    }
}
