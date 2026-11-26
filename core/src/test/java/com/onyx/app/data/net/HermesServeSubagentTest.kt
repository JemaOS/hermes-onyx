package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.SubagentUpdate
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La route « sous-agent » du transport Dashboard, rejouée avec les vraies frames du gateway.
 *
 * C'est la preuve que la route n'est plus morte : les trames `subagent.*` qui tombaient dans
 * l'ignorance deviennent des [TurnEvent.Subagent], et l'identifiant **runtime** de la session (que
 * `subagent.tail` / `.steer` attendent) est remonté à l'appelant.
 */
class HermesServeSubagentTest {

    private val config = ServerConfig(
        id = "s",
        name = "Dashboard",
        serveBaseUrl = "https://onyx.example",
        serveUsername = "alice",
        servePassword = "secret",
        transport = Transport.HERMES_SERVE,
    )

    private fun request() = ChatRequest(history = listOf(ChatTurn(role = Role.USER, text = "go")))

    private fun event(session: String, type: String, payload: JsonObject) =
        ServeEvent(type = type, sessionId = session, payload = payload)

    @Test
    fun `les trames subagent du socket deviennent des evenements de tour`() = runBlocking {
        var runtimeSeen: String? = null
        val channel = ScriptedChannel { method, _ ->
            when (method) {
                "session.create" -> Reply(
                    buildJsonObject {
                        put("session_id", "runtime-7")
                        put("stored_session_id", "stored-7")
                    },
                )
                "prompt.submit" -> Reply(
                    buildJsonObject { },
                    emit = listOf(
                        // Les frames que le gateway émet vraiment, telles quelles.
                        event(
                            "runtime-7",
                            "subagent.start",
                            buildJsonObject {
                                put("subagent_id", "sa-1")
                                put("child_session_id", "c1")
                                put("goal", "price the three plans")
                                put("task_index", 0)
                                put("task_count", 3)
                                put("tool_count", 0)
                            },
                        ),
                        event(
                            "runtime-7",
                            "subagent.tool",
                            buildJsonObject {
                                put("subagent_id", "sa-1")
                                put("tool_name", "web_extract")
                                put("tool_count", 3)
                            },
                        ),
                        event(
                            "runtime-7",
                            "subagent.progress",
                            buildJsonObject {
                                put("subagent_id", "sa-1")
                                put("text", "3 plans read")
                            },
                        ),
                        event(
                            "runtime-7",
                            "subagent.complete",
                            buildJsonObject {
                                put("subagent_id", "sa-1")
                                put("status", "success")
                                put("summary", "Three tiers.")
                                put("duration_seconds", 12.5)
                                put("tool_count", 7)
                            },
                        ),
                        event("runtime-7", "message.complete", buildJsonObject { put("status", "ok") }),
                    ),
                )
                else -> Reply(buildJsonObject { })
            }
        }

        val events = withTimeout(2_000) {
            HermesServeChat(config, channel)
                .stream(request(), null, onRuntimeSessionId = { runtimeSeen = it })
                .toList()
        }

        val subagents = events.filterIsInstance<TurnEvent.Subagent>().map { it.update }
        assertEquals(4, subagents.size)
        assertEquals("runtime-7", runtimeSeen)

        assertEquals(SubagentUpdate.Phase.Started, subagents[0].phase)
        assertEquals("price the three plans", subagents[0].goal)
        assertEquals("c1", subagents[0].childSessionId)
        assertEquals(3, subagents[0].taskCount)
        assertEquals(SubagentUpdate.Phase.Tool("web_extract"), subagents[1].phase)
        assertEquals(3, subagents[1].toolCount)
        assertEquals(SubagentUpdate.Phase.Progress("3 plans read"), subagents[2].phase)
        assertEquals(
            SubagentUpdate.Phase.Completed(succeeded = true, summary = "Three tiers.", duration = 12.5),
            subagents[3].phase,
        )
        // Le tour se termine normalement : la trame sous-agent ne l'a pas court-circuité.
        assertTrue(events.any { it is TurnEvent.Done })
    }

    @Test
    fun `une trame subagent compte comme un evenement de tour`() {
        // `underWay` décide si un `session.info running=false` clôt ce tour : une trame de
        // sous-agent prouve que le tour travaille, donc elle doit le compter.
        assertTrue(ServeChatEvents.isTurnEvent("subagent.start"))
        assertTrue(ServeChatEvents.isTurnEvent("subagent.tool"))
        assertTrue(ServeChatEvents.isTurnEvent("subagent.progress"))
        assertTrue(ServeChatEvents.isTurnEvent("subagent.complete"))
        assertFalse(ServeChatEvents.isTurnEvent("subagent.unknown"))
    }

    @Test
    fun `un sous-agent sans identifiant ne fait rien`() {
        val step = ServeChatEvents.step(
            event("runtime-7", "subagent.start", buildJsonObject { put("goal", "orphan") }),
            streamed = false,
            underWay = true,
        )
        assertEquals(ServeStep.Continue, step)
    }

    @Test
    fun `le sous-agent du transport Dashboard porte de quoi ouvrir sa transcription`() {
        val update = SubagentUpdate.parse(
            "subagent.start",
            buildJsonObject {
                put("subagent_id", "sa-1")
                put("child_session_id", "c1")
            },
        )!!
        assertEquals("c1", update.childSessionId)

        // Le statut que l'app dessine pour un sous-agent du socket : il travaille.
        val row = com.onyx.app.data.model.SubagentActivity(id = update.id).applying(update, 0L)
        assertEquals(SubagentStatus.RUNNING, row.status)
        assertTrue(row.hasTranscript)
    }

    @Test
    fun `le roster, la transcription et les deux gestes se lisent par le RPC`() = runBlocking {
        // Les réponses sont celles du gateway (`methods_subagents.py`) : le roster borne ses
        // champs, `tail` rend `available: false` quand l'enfant n'a rien écrit, `steer` dit
        // « queued » ou « rejected », `interrupt` dit « found ».
        val calls = mutableListOf<Pair<String, JsonObject>>()
        val rpc = RpcChannel { method, params, _ ->
            calls += method to params
            when (method) {
                "subagent.list" -> buildJsonObject {
                    put(
                        "subagents",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("subagent_id", "sa-1")
                                    put("goal", "price it")
                                    put("depth", 0)
                                    put("status", "running")
                                    put("tool_count", 4)
                                    put("last_tool", "read_file")
                                    put("started_at", 1700000000.0)
                                    put("accepting_steer", true)
                                    put("child_session_id", "c1")
                                },
                            )
                        },
                    )
                }
                "subagent.tail" -> buildJsonObject {
                    put("subagent_id", "sa-1")
                    put("available", true)
                    put("text", "[user] price it\n[assistant] three tiers")
                    put("truncated", false)
                }
                "subagent.steer" -> buildJsonObject {
                    put("status", "queued")
                    put("subagent_id", "sa-1")
                }
                "subagent.interrupt" -> buildJsonObject {
                    put("found", true)
                    put("subagent_id", "sa-1")
                }
                else -> buildJsonObject { }
            }
        }
        val client = ServeSubagentRpc(rpc)

        val roster = client.list("runtime-7")
        assertEquals(1, roster.size)
        assertEquals("sa-1", roster[0].id)
        assertEquals("price it", roster[0].goal)
        assertEquals(4, roster[0].toolCount)
        assertEquals("read_file", roster[0].lastTool)
        assertEquals(1_700_000_000_000L, roster[0].startedAt)

        val tail = client.tail("runtime-7", "sa-1")!!
        assertTrue(tail.available)
        assertTrue(tail.text.contains("three tiers"))

        // La session runtime part avec les deux gestes : sans elle le gateway refuse.
        assertTrue(client.steer("runtime-7", "sa-1", "drop the priciest"))
        assertTrue(client.interrupt("runtime-7", "sa-1"))
        assertEquals(
            "runtime-7",
            calls.first { it.first == "subagent.steer" }.second["session_id"].toString().trim('"'),
        )
        // Un identifiant vide ne fait pas d'appel : rien à demander.
        assertTrue(client.list("").isEmpty())
        assertTrue(client.tail("runtime-7", "").let { it == null })
    }

    @Test
    fun `une transcription indisponible se dit, elle ne tourne pas dans le vide`() = runBlocking {
        val rpc = RpcChannel { _, _, _ ->
            buildJsonObject {
                put("subagent_id", "sa-2")
                put("available", false)
                put("text", "")
                put("truncated", false)
            }
        }
        val tail = ServeSubagentRpc(rpc).tail("runtime-7", "sa-2")!!
        assertFalse(tail.available)
        assertEquals("", tail.text)
    }

    private data class Reply(
        val result: JsonElement,
        val emit: List<ServeEvent> = emptyList(),
    )
    /** Un canal scripté, sans socket : c'est le vrai [HermesServeChat] qui tourne. */
    private class ScriptedChannel(
        private val responder: (String, JsonObject) -> Reply,
    ) : ServeChatChannel {
        private var listener: ((ServeEvent) -> Unit)? = null

        override suspend fun connect() = Unit

        override suspend fun call(method: String, params: JsonObject, timeoutMillis: Long): JsonElement {
            val reply = responder(method, params)
            reply.emit.forEach { listener?.invoke(it) }
            return reply.result
        }

        override fun addListener(listener: (ServeEvent) -> Unit): String {
            this.listener = listener
            return "k"
        }

        override fun removeListener(key: String) {
            listener = null
        }

        override fun close() = Unit
    }
}
