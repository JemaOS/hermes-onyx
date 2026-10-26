package com.onyx.app

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ServeGroupsRpc
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.ui.bots.GroupRoomFeed
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Le fil d'une salle jusqu'au bout du fil: `GroupRoomFeed` → `groups.send` → le canal du Dashboard
 * (ticket, WebSocket, trames JSON-RPC) → une salle qui garde ses événements → le journal relu.
 *
 * C'est la preuve que ce qui est écrit dans une salle **y est écrit**: le serveur de ce test tient
 * son propre journal, et c'est lui — pas l'application — qui le rend au fil.
 */
class GroupRoomSendEndToEndTest {

    private lateinit var dashboard: MockWebServer

    /** Le journal de la salle, tel qu'un Dashboard le tiendrait. */
    private val roomLog = mutableListOf<JsonElement>()
    private val sentEventIDs = mutableListOf<String>()
    private val stopped = mutableListOf<String>()
    private var nextSeq = 1

    private val config = ServerConfig(
        id = "server",
        baseUrl = "",
        model = "hermes-agent",
        transport = Transport.HERMES_SERVE,
        serveBaseUrl = "http://placeholder",
        serveUsername = "onyx",
        servePassword = "port-only",
    )

    private val room = GroupRoom(
        id = "onyx-1",
        name = "Weekend hike",
        members = listOf(
            GroupMember(id = "default", profile = "default", handle = "hermes", displayName = "Onyx"),
            GroupMember(id = "scout", profile = "scout", handle = "scout", displayName = "Scout"),
        ),
    )

    @Before
    fun start() {
        dashboard = MockWebServer()
        dashboard.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.startsWith("/api/auth/ws-ticket") == true ->
                    MockResponse().setBody("""{"ticket":"ticket-1"}""")

                request.path?.startsWith("/api/ws") == true ->
                    MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val frame = Json.parseToJsonElement(text).jsonObject
                            val id = frame["id"]?.jsonPrimitive?.content ?: return
                            val method = frame["method"]?.jsonPrimitive?.content.orEmpty()
                            val params = frame["params"]?.jsonObject ?: buildJsonObject { }
                            val result = answer(method, params)
                            webSocket.send(
                                buildJsonObject {
                                    put("jsonrpc", "2.0")
                                    put("id", id)
                                    put("result", result)
                                }.toString(),
                            )
                        }
                    })

                else -> MockResponse().setResponseCode(404)
            }
        }
        dashboard.start()
        val address = dashboard.url("/").toString().removeSuffix("/")
        configWithAddress(address)
    }

    private var live: ServerConfig = config

    private fun configWithAddress(address: String) {
        live = config.copy(serveBaseUrl = address)
    }

    @After
    fun stop() {
        dashboard.shutdown()
    }

    /** Ce que ce Dashboard répond, et ce qu'il retient. */
    private fun answer(method: String, params: JsonObject): JsonElement = when (method) {
        "groups.capabilities" -> buildJsonObject {
            put(
                "methods",
                buildJsonArray {
                    listOf("groups.list", "groups.create", "groups.state", "groups.send", "groups.log")
                        .forEach { add(JsonPrimitive(it)) }
                },
            )
            put("driver", true)
        }

        // Le journal de la salle, entier: c'est `groups.log` qui rogne après `since_seq`.
        "groups.log" -> buildJsonObject {
            put("events", buildJsonArray { roomLog.forEach { add(it) } })
            put("cursor", nextSeq - 1)
            put("has_more", false)
        }

        "groups.state" -> buildJsonObject {
            put("room", buildJsonObject {
                put("room_id", room.id)
                put("name", room.name)
            })
            put("driver_status", buildJsonObject { put("working", false) })
        }

        "groups.send" -> {
            val eventID = params["event_id"]?.jsonPrimitive?.content.orEmpty()
            val said = params["payload"]?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
            val thread = params["payload"]?.jsonObject?.get("thread_id")?.jsonPrimitive?.content
            sentEventIDs += eventID
            // Ce que la salle retient: le message, sous le fil « main », avec son identifiant.
            roomLog += buildJsonObject {
                put("seq", nextSeq)
                put("kind", "message.user")
                put("actor", buildJsonObject { put("kind", "user"); put("id", "desktop") })
                put("payload", buildJsonObject {
                    put("text", said)
                    put("thread_id", thread)
                    put("event_id", eventID)
                })
            }
            nextSeq += 1
            buildJsonObject { put("accepted", true) }
        }

        "groups.stop" -> {
            stopped += params["cancel_id"]?.jsonPrimitive?.content.orEmpty()
            buildJsonObject { put("stopped", true) }
        }

        else -> JsonObject(emptyMap())
    }

    @Test
    fun `un message ecrit dans une salle y est ecrit, et revient par son journal`() = runBlocking {
        val feed = GroupRoomFeed(room, ServeGroupsRpc(ServeRpc(live, OkHttpClient())))
        assertTrue("la salle part vide", feed.state.value.events.isEmpty())

        val id = feed.beginSend("Bonjour à tous")
        feed.deliver(id, "Bonjour à tous")

        // La salle l'a retenu: c'est ce que `groups.send` a écrit, avec l'identifiant du message.
        assertEquals(1, roomLog.size)
        val kept = roomLog.single().jsonObject
        assertEquals("message.user", kept["kind"]!!.jsonPrimitive.content)
        assertEquals("Bonjour à tous", kept["payload"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("main", kept["payload"]!!.jsonObject["thread_id"]!!.jsonPrimitive.content)
        assertEquals(id, sentEventIDs.single())
        assertEquals(id, kept["payload"]!!.jsonObject["event_id"]!!.jsonPrimitive.content)

        // Et il revient au fil par le journal de la salle, plus par l'attente locale.
        val state = feed.state.value
        assertEquals(listOf("Bonjour à tous"), state.events.filter { it.kind == "message.user" }.map { it.text })
        assertTrue("le message n'attend plus", state.sending.isEmpty())
        assertEquals(1, state.cursor)
    }

    @Test
    fun `l arret d une salle part au Dashboard avec un identifiant neuf`() = runBlocking {
        val feed = GroupRoomFeed(room, ServeGroupsRpc(ServeRpc(live, OkHttpClient())))

        feed.stop()

        assertEquals(1, stopped.size)
        assertTrue(stopped.single().startsWith("stop-"))
    }
}
