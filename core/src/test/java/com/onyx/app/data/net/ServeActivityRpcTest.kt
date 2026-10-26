package com.onyx.app.data.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'activité que le Dashboard signale (`session.active_list`): ce qui travaille, ce qui attend
 * quelqu'un, et à qui cela appartient.
 *
 * Le modèle est `ChatActivity.kinds` / `roomKinds`:
 * les sessions cachées d'une salle comptent pour leur salle et jamais pour une conversation, et
 * une salle qui attend l'emporte sur une salle qui travaille.
 */
class ServeActivityRpcTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `les conversations en cours se lisent par cle de session`() = runBlocking {
        val asked = mutableListOf<String>()
        val rpc = RpcChannel { method, _, _ ->
            asked += method
            json(
                """{"sessions":[
                    {"id":"runtime-1","session_key":"stored-1","title":"Bench","status":"working"},
                    {"id":"runtime-2","session_key":"stored-2","title":"Trail","status":"starting"},
                    {"id":"runtime-3","title":"Notes","status":"waiting"},
                    {"id":"runtime-4","session_key":"stored-4","title":"Idle","status":"idle"}
                ]}""",
            )
        }

        val kinds = ServeActivityRpc(rpc).activeKinds()

        assertEquals(listOf("session.active_list"), asked)
        assertEquals(ActivityKind.WORKING, kinds.sessions["stored-1"])
        assertEquals(ActivityKind.WORKING, kinds.sessions["stored-2"])
        assertEquals(ActivityKind.NEEDS_YOU, kinds.sessions["runtime-3"])
        assertEquals(null, kinds.sessions["stored-4"])
        assertTrue(kinds.rooms.isEmpty())
    }

    @Test
    fun `les sessions d une salle comptent pour leur salle`() {
        val list = json(
            """{"sessions":[
                {"id":"a","title":"Group: onyx-1","status":"working"},
                {"id":"b","title":"Group: onyx-1","status":"waiting"},
                {"id":"c","title":"Group: onyx-2","status":"starting"},
                {"id":"d","session_key":"stored","title":"Bench","status":"working"}
            ]}""",
        )

        val kinds = ServeActivityRpc.kindsOf(list)

        // Une salle qui attend l'emporte sur une salle qui travaille.
        assertEquals(ActivityKind.NEEDS_YOU, kinds.rooms["onyx-1"])
        assertEquals(ActivityKind.WORKING, kinds.rooms["onyx-2"])
        // Les sessions de salle ne comptent pas comme des conversations.
        assertEquals(1, kinds.sessions.size)
        assertEquals(ActivityKind.WORKING, kinds.sessions["stored"])
    }

    @Test
    fun `une salle d Hermes Desktop garde son identifiant de salle`() {
        val list = json(
            """{"sessions":[{"id":"a","title":"Group: id:demo-desk · main","status":"working"}]}""",
        )

        // Le titre d'une salle Desktop porte son fil après « · »: la salle est nommée sans lui,
        // comme le fait `roomKinds`.
        assertEquals(mapOf("desktop:id:id:demo-desk" to ActivityKind.WORKING), ServeActivityRpc.roomKinds(list))
    }

    @Test
    fun `un statut sans tour en cours n est pas une activite`() {
        val list = json(
            """{"sessions":[{"id":"a","session_key":"s","status":"idle"},{"id":"b","title":"Group: r","status":"idle"}]}""",
        )
        assertTrue(ServeActivityRpc.kindsOf(list).isEmpty)
    }
}
