package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'image jointe, dans le transport Dashboard (WebSocket).
 *
 * `prompt.submit` ne prend que du texte : une image se dépose d'abord sur la session par
 * `image.attach_bytes` (base64 + nom), comme `HermesServeTransport.attachFrame` de la référence.
 * Sans cet appel, l'image jointe était **silencieusement perdue** — le message partait, la
 * silhouette ne l'avait jamais reçue. Ces tests tiennent l'ordre des appels, le corps du dépôt, et
 * le fait qu'un refus **ne bloque pas** le tour (il est dit, le texte part quand même).
 */
class HermesServeChatAttachTest {

    private val config = ServerConfig(
        id = "s",
        name = "Dashboard",
        serveBaseUrl = "https://onyx.example",
        serveUsername = "alice",
        servePassword = "secret",
        transport = Transport.HERMES_SERVE,
    )

    /** Un PNG d'un pixel, en data URL : ce que `ChatViewModel` met dans `ChatTurn.images`. */
    private val pixelPngDataUrl =
        "data:image/png;base64," +
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAen63NgAAAAASUVORK5CYII="

    private fun request(images: List<String> = emptyList()) = ChatRequest(
        history = listOf(ChatTurn(role = Role.USER, text = "Regarde cette image", images = images)),
        model = "hermes-agent",
    )

    @Test
    fun `l image jointe part par le depot de la session avant le prompt`() = runBlocking {
        val channel = RecordingChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(
                    buildJsonObject {
                        put("session_id", "runtime-1")
                        put("stored_session_id", "stored-1")
                    },
                )
                "image.attach_bytes" -> Reply(
                    buildJsonObject { put("attached", true); put("count", 1) },
                )
                "prompt.submit" -> Reply(
                    buildJsonObject { },
                    emit = listOf(complete("runtime-1")),
                )
                else -> Reply(buildJsonObject { })
            }
        }

        val events = withTimeout(5_000) {
            HermesServeChat(config, channel).stream(request(listOf(pixelPngDataUrl))).toList()
        }

        val order = channel.calls.map { it.first }
        assertTrue("la session est ouverte d'abord", order.indexOf("session.create") == 0)
        assertTrue("l'image est déposée avant le prompt", order.indexOf("image.attach_bytes") < order.indexOf("prompt.submit"))

        val attach = channel.calls.first { it.first == "image.attach_bytes" }.second
        assertEquals("runtime-1", attach["session_id"].toString().trim('"'))
        assertEquals(
            "le base64 du data URL part tel quel",
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAen63NgAAAAASUVORK5CYII=",
            attach["content_base64"].toString().trim('"'),
        )
        assertEquals("image.png", attach["filename"].toString().trim('"'))

        // Le tour se termine normalement : l'image n'a rien ajouté au texte du message.
        assertTrue("le tour finit", events.contains(TurnEvent.Done))
        val submit = channel.calls.first { it.first == "prompt.submit" }.second
        assertEquals("Regarde cette image", submit["text"].toString().trim('"'))
    }

    @Test
    fun `un depot refuse ne bloque pas le tour, et le dit`() = runBlocking {
        val channel = RecordingChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject { put("session_id", "runtime-2") })
                // Le serveur refuse l'image : extension inconnue, ou octets illisibles.
                "image.attach_bytes" -> Reply(buildJsonObject { })
                "prompt.submit" -> Reply(
                    buildJsonObject { },
                    emit = listOf(delta("runtime-2", "Je ne vois pas d'image."), complete("runtime-2")),
                )
                else -> Reply(buildJsonObject { })
            }
        }

        val events = withTimeout(5_000) {
            HermesServeChat(config, channel).stream(request(listOf(pixelPngDataUrl))).toList()
        }

        assertTrue(
            "le texte part quand même — le refus ne bloque pas la conversation",
            channel.calls.any { it.first == "prompt.submit" },
        )
        assertTrue(
            "la raison est dite à l'écran",
            events.any { it is TurnEvent.Status && it.text.isNotBlank() },
        )
        assertTrue("et la réponse arrive", events.any { it == TurnEvent.TextDelta("Je ne vois pas d'image.") })
    }

    @Test
    fun `un tour sans image ne depose rien`() = runBlocking {
        val channel = RecordingChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(buildJsonObject { put("session_id", "runtime-3") })
                "prompt.submit" -> Reply(buildJsonObject { }, emit = listOf(complete("runtime-3")))
                else -> Reply(buildJsonObject { })
            }
        }

        withTimeout(5_000) { HermesServeChat(config, channel).stream(request()).toList() }

        assertTrue(
            "rien à déposer : aucun appel inutile",
            channel.calls.none { it.first == "image.attach_bytes" },
        )
    }

    @Test
    fun `un data url se coupe en base64 et en nom, selon son type`() {
        val png = DataImage.of(pixelPngDataUrl)
        assertEquals("image.png", png?.name)
        assertEquals(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAen63NgAAAAASUVORK5CYII=",
            png?.base64,
        )
        assertEquals("image.jpg", DataImage.of("data:image/jpeg;base64,AAAA")?.name)
        assertEquals("image.webp", DataImage.of("data:image/webp;base64,AAAA")?.name)

        assertNull("un texte nu n'est pas une image", DataImage.of("Regarde cette image"))
        assertNull("un data URL sans base64 est refusé", DataImage.of("data:image/png,notbase64"))
        assertNull("un data URL vide est refusé", DataImage.of("data:image/png;base64,"))
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

    private data class Reply(
        val result: JsonElement,
        val emit: List<ServeEvent> = emptyList(),
        val fail: TransportError? = null,
    )

    /** Un `ServeChatChannel` scripté : il note chaque appel, dans l'ordre. */
    private class RecordingChannel(
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
