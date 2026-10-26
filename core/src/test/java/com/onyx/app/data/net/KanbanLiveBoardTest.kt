package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le direct du tableau kanban: le socket d'événements du plugin (`api/plugins/kanban/events`),
 * dont fait `KanbanClient.LiveBoard` — un ticket, une reprise au curseur, et une relecture du
 * tableau à chaque trame.
 */
class KanbanLiveBoardTest {

    @Test
    fun `une trame d'evenements donne son curseur et ses cartes`() {
        val frame = kanbanEventFrame(
            """{"events":[{"id":1,"task_id":"t1","kind":"update"},{"id":2,"task_id":"t2"}],"cursor":7}""",
        )

        assertEquals(7, frame?.cursor)
        assertEquals(listOf("t1", "t2"), frame?.taskIds)
        assertTrue(frame?.hadEvents == true)
    }

    @Test
    fun `une trame sans carte demande quand meme une relecture`() {
        val frame = kanbanEventFrame("""{"events":[],"cursor":3}""")

        assertEquals(3, frame?.cursor)
        assertTrue(frame?.taskIds.orEmpty().isEmpty())
        assertTrue(frame?.hadEvents == true)
    }

    @Test
    fun `ce qui n est pas une trame d'evenements ne declenche rien`() {
        assertNull(kanbanEventFrame("""{"hello":"world"}"""))
        assertNull(kanbanEventFrame("pong"))
        assertNull(kanbanEventFrame(""))
    }

    @Test
    fun `le socket passe en ws selon l adresse du Dashboard`() {
        assertEquals("wss://me.trycloudflare.com", webSocketBase("https://me.trycloudflare.com"))
        assertEquals("ws://10.0.2.2:9119", webSocketBase("http://10.0.2.2:9119"))
        assertEquals("ws://hote", webSocketBase("hote"))
    }

    @Test
    fun `le direct ouvre le socket avec le ticket et le curseur`() = runBlocking {
        val server = MockWebServer()
        val opened = Channel<WebSocket>(1)
        var livePath = ""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.startsWith("/api/sessions") == true ->
                    MockResponse().setResponseCode(200).setBody("""{"sessions":[]}""")

                request.path?.startsWith("/api/auth/ws-ticket") == true ->
                    MockResponse().setResponseCode(200).setBody("""{"ticket":"t-1"}""")

                request.path?.startsWith("/api/plugins/kanban/events") == true -> {
                    livePath = request.path.orEmpty()
                    MockResponse().withWebSocketUpgrade(
                        object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                opened.trySend(webSocket)
                            }
                        },
                    )
                }

                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val board = KanbanLiveBoard(configFor(server))
        val hits = Channel<List<String>>(Channel.UNLIMITED)
        try {
            board.start(since = 4) { ids -> hits.trySend(ids) }

            val socket = withTimeout(10_000) { opened.receive() }
            assertTrue("le direct doit se dire en ligne", awaitTrue { board.isLive.value })
            assertTrue("le curseur de reprise manque : $livePath", livePath.contains("since=4"))
            assertTrue("le ticket manque : $livePath", livePath.contains("ticket=t-1"))

            socket.send("""{"events":[{"id":9,"task_id":"card-9","kind":"update"}],"cursor":12}""")
            assertEquals(listOf("card-9"), withTimeout(5_000) { hits.receive() })

            // Le curseur avance: la trame suivante sans carte demande une relecture complète.
            socket.send("""{"events":[],"cursor":13}""")
            assertEquals(emptyList<String>(), withTimeout(5_000) { hits.receive() })

            board.stop()
            assertFalse("arrêté, le direct se dit hors ligne", board.isLive.value)
        } finally {
            board.close()
            server.shutdown()
        }
    }

    @Test
    fun `le tableau porte son curseur ses assignes et l age de ses cartes`() = runBlocking {
        val now = System.currentTimeMillis()
        val created = now / 1000 - 3_600
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.startsWith("/api/sessions") == true ->
                    MockResponse().setResponseCode(200).setBody("""{"sessions":[]}""")

                request.path?.startsWith("/api/plugins/kanban/board") == true -> MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {"latest_event_id":42,"assignees":["onyx","home"],"columns":[
                          {"name":"triage","tasks":[{"id":"t1","title":"Tidy the recipe notes","created_at":$created}]},
                          {"name":"todo","tasks":[{
                             "id":"t2","title":"Compare heat pump quotes","priority":2,
                             "body":"Three installers so far.","latest_summary":"Two quotes in.",
                             "assignee":"onyx","created_at":$created,"comment_count":2,
                             "last_failure_error":"boom"
                          }]}
                        ]}
                        """.trimIndent(),
                    )

                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val board = ServeKanbanApi(ServeClient(configFor(server))).board()

            assertEquals(42, board.latestEventId)
            assertEquals(listOf("onyx", "home"), board.assignees)
            assertEquals(2, board.cards.size)
            assertEquals("triage", board.cards.first { it.id == "t1" }.status)
            val card = board.cards.first { it.id == "t2" }
            assertEquals("todo", card.status)
            assertEquals(2, card.priority)
            // `latest_summary` l'emporte sur `body`, comme `task.latestSummary ?? task.body`.
            assertEquals("Two quotes in.", card.summary)
            assertEquals("Three installers so far.", card.body)
            assertEquals("boom", card.lastFailure)
            assertTrue(card.failed)
            // `created_at` en secondes d'époque: des millisecondes, pour l'âge de la rangée.
            assertEquals(created * 1000L, card.createdAtMillis)
        } finally {
            server.shutdown()
        }
    }

    private fun configFor(server: MockWebServer) = ServerConfig(
        id = "kanban-live",
        name = "Kanban live",
        serveBaseUrl = server.url("/").toString().trimEnd('/'),
        serveUsername = "onyx",
        servePassword = "s3cret",
    )

    private suspend fun awaitTrue(timeoutMillis: Long = 5_000, check: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (check()) return true
            delay(20)
        }
        return check()
    }
}

// Un direct refusé ne doit PAS réveiller le tableau: c'est ce réveil à chaque échec qui faisait
// clignoter l'indicateur de chargement en boucle. Le propriétaire: « des loops de chargement en
// boucle… corrige pour que ça charge tout d'un coup ».
class KanbanLiveBoardQuietTest {
    @Test
    fun `un echec de direct ne declenche aucun rafraichissement`() {
        val source = java.io.File("src/main/java/com/onyx/app/data/net/KanbanLiveBoard.kt").readText()
        assertFalse(
            "aucun onChange sur le chemin d'échec",
            source.contains("onChange(emptyList())"),
        )
        assertTrue("l'état du direct reste signalé", source.contains("_isLive.value = false"))
    }
}
