package com.onyx.app.data.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les salles de bots du serveur : ce que `groups.capabilities` autorise, ce que `groups.list`
 * donne page après page, et la copie des salles qu'Hermes Desktop tient lui-même.
 *
 * La référence est `ServerGroups` (`groupSupport`, `groupRooms`, `desktopRooms`) : les mêmes
 * méthodes, les mêmes champs, les mêmes replis.
 */
class ServeGroupsRpcTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `les capacites disent si une salle est possible maintenant`() = runBlocking {
        val asked = mutableListOf<String>()
        val rpc = RpcChannel { method, _, _ ->
            asked += method
            json("""{"methods":["groups.list","groups.create","groups.state","groups.send","groups.log"],"driver":true}""")
        }
        val support = ServeGroupsRpc(rpc).support()
        assertTrue("les cinq méthodes suffisent", support.supported)
        assertTrue("le worker écoute", support.writable)
        assertEquals(listOf("groups.capabilities"), asked)
    }

    @Test
    fun `un Dashboard sans les cinq methodes n a pas de salles`() {
        val partial = ServeGroupsRpc.supportFrom(json("""{"methods":["groups.list","groups.state"]}"""))
        assertFalse(partial.supported)
        assertFalse(partial.writable)
        // Le worker tourne, mais il manque des méthodes : rien à ouvrir.
        val noDriver = ServeGroupsRpc.supportFrom(
            json("""{"methods":["groups.list","groups.create","groups.state","groups.send","groups.log"]}"""),
        )
        assertTrue(noDriver.supported)
        assertFalse("le worker ne tourne pas encore", noDriver.writable)
    }

    @Test
    fun `un Dashboard qui ne connait pas les salles n en montre aucune`() = runBlocking {
        val rpc = RpcChannel { _, _, _ -> throw TransportError.Malformed("Method not found") }
        assertEquals(GroupSupport(false, false), ServeGroupsRpc(rpc).support())
    }

    @Test
    fun `les salles se lisent page apres page`() = runBlocking {
        val offsets = mutableListOf<Int>()
        val rpc = RpcChannel { method, params, _ ->
            assertEquals("groups.list", method)
            val offset = params.int("offset") ?: 0
            offsets += offset
            when (offset) {
                0 -> json(
                    """{"rooms":[
                        {"room_id":"onyx-1","name":"Weekend hike","latest_seq":41,
                         "members":[{"member_id":"scout","profile":"scout","handle":"scout","display_name":"Scout"}]},
                        {"room_id":"onyx-2","name":"Garage","latest_seq":7}
                    ],"next_offset":2}""",
                )
                else -> json("""{"rooms":[{"room_id":"onyx-3","name":"Trips","latest_seq":9}]}""")
            }
        }

        val rooms = ServeGroupsRpc(rpc).rooms()

        assertEquals(listOf(0, 2), offsets)
        assertEquals(listOf("onyx-1", "onyx-2", "onyx-3"), rooms.map { it.id })
        assertEquals("Weekend hike", rooms.first().name)
        assertEquals(41, rooms.first().latestSeq)
        assertEquals(41.0, rooms.first().activityMark!!, 0.0001)
        assertEquals("Scout", rooms.first().members.single().label)
    }

    @Test
    fun `les salles d Hermes Desktop viennent de la copie du profil principal`() {
        val profiles = json(
            """{"profiles":[
                {"name":"scout","ui_meta":{}},
                {"name":"default","is_default":true,"ui_meta":{"hermes-bots-groups":{
                  "version":3,
                  "rooms":{
                    "id:demo-desk":{"name":"Garage project","revision":3,"omitted":6,
                      "members":[{"name":"fixit","title":"Fix-it"}],
                      "log":[{"at":1761300000000,"from":{"kind":"user","name":"You"},"text":"Bench along the back wall?"},
                             {"at":1761300060000,"from":{"kind":"member","name":"fixit"},"text":"Yes, 60 cm deep at most."}]},
                    "id:gone":{"name":"Ended","revision":1}
                  },
                  "deleted":{"id:gone":2}
                }}}]}""",
        )

        val rooms = ServeGroupsRpc.desktopRoomsFrom(profiles)

        assertEquals(1, rooms.size)
        val room = rooms.single()
        assertEquals("desktop:id:demo-desk", room.id)
        assertEquals("Garage project", room.name)
        assertTrue(room.inDesktop)
        assertEquals("Fix-it", room.members.single().label)
        // L'heure de la copie, en millisecondes : la marque d'activité d'une salle Desktop.
        assertEquals(1761300060000L, room.updatedAtMillis)
        assertEquals(1761300060.0, room.activityMark!!, 0.0001)
    }

    @Test
    fun `une copie trop ancienne ou terminee ne donne pas de salle`() {
        val old = json("""{"version":2,"rooms":{"id:a":{"name":"A","revision":1}}}""")
        assertTrue(com.onyx.app.data.model.serve.GroupRoom.desktopRooms(old).isEmpty())

        val allGone = json(
            """{"version":3,"rooms":{"id:a":{"name":"A","revision":2}},"deleted":{"id:a":3}}""",
        )
        assertTrue(com.onyx.app.data.model.serve.GroupRoom.desktopRooms(allGone).isEmpty())
    }

    @Test
    fun `sans nom, une salle Desktop montre qui a parle`() {
        val copy = json(
            """{"version":3,"rooms":{"id:x":{"revision":1,
              "log":[{"at":1,"from":{"kind":"member","name":"scout"},"text":"Hello"},
                     {"at":2,"from":{"kind":"member","name":"writer"},"text":"Hi"}]}}}""",
        )

        val room = com.onyx.app.data.model.serve.GroupRoom.desktopRooms(copy).single()

        assertEquals(listOf("scout", "writer"), room.members.map { it.profile })
        assertEquals("x", room.name)
    }

    @Test
    fun `la cle d une salle Desktop sert de nom a defaut`() {
        val copy = json("""{"version":3,"rooms":{"id:abc":{"revision":1,"log":[]}}}""")
        val room = com.onyx.app.data.model.serve.GroupRoom.desktopRooms(copy).single()
        assertEquals("abc", room.name)
    }

    // MARK: Le fil d'une salle — journal, état, envoi, arrêt (`GroupFeed` / `ServerGroups`).

    @Test
    fun `le journal d une salle se lit page apres page depuis le curseur`() = runBlocking {
        val calls = mutableListOf<Pair<String, kotlinx.serialization.json.JsonObject>>()
        val rpc = RpcChannel { method, params, _ ->
            calls += method to params
            when (params.int("since_seq") ?: 0) {
                0 -> json(
                    """{"events":[{"seq":1,"kind":"message.user","actor":{"kind":"user","id":"desktop"},
                        "payload":{"text":"Une randonnée samedi ?"},"created_at":1761300000}],
                        "cursor":1,"has_more":true}""",
                )
                else -> json(
                    """{"events":[{"seq":2,"kind":"message.member","actor":{"kind":"member","id":"scout"},
                        "payload":{"text":"Falls Loop est ouverte.","member_id":"scout"}}],"cursor":2,"has_more":false}""",
                )
            }
        }

        val events = ServeGroupsRpc(rpc).log("onyx-1", 0)

        assertEquals(listOf("groups.log", "groups.log"), calls.map { it.first })
        assertEquals("onyx-1", calls.first().second.str("room_id"))
        assertEquals(200, calls.first().second.int("limit"))
        assertEquals(listOf(0, 1), calls.map { it.second.int("since_seq") })
        assertEquals(listOf(1, 2), events.map { it.seq })
        assertEquals("Une randonnée samedi ?", events.first().text)
        assertEquals("scout", events.last().actorID)
        // `created_at` est en secondes Unix, comme `Date(timeIntervalSince1970:)` de la référence.
        assertEquals(1761300000000L, events.first().createdAtMillis)
    }

    @Test
    fun `un evenement deja lu n est pas repris`() = runBlocking {
        val rpc = RpcChannel { _, _, _ ->
            json("""{"events":[{"seq":1,"kind":"message.user","payload":{"text":"Déjà lu"}}],"cursor":1,"has_more":false}""")
        }

        // Le journal est repris après le curseur : ce qui est avant lui ne revient pas.
        assertTrue(ServeGroupsRpc(rpc).log("onyx-1", 1).isEmpty())
    }

    @Test
    fun `groups send envoie le meme corps d appel que la référence`() = runBlocking {
        var method: String? = null
        var params: kotlinx.serialization.json.JsonObject? = null
        val rpc = RpcChannel { called, sent, _ ->
            method = called
            params = sent
            json("""{"ok":true}""")
        }

        ServeGroupsRpc(rpc).send("onyx-1", "Bonjour à tous", "evt-9")

        assertEquals("groups.send", method)
        assertEquals(setOf("room_id", "event_id", "payload"), params!!.keys)
        assertEquals("onyx-1", params!!.str("room_id"))
        assertEquals("evt-9", params!!.str("event_id"))
        assertEquals("Bonjour à tous", params!!.obj("payload")?.str("text"))
        assertEquals("main", params!!.obj("payload")?.str("thread_id"))
    }

    @Test
    fun `groups state donne la salle et ce qu elle attend`() = runBlocking {
        val rpc = RpcChannel { _, params, _ ->
            assertEquals("onyx-1", params.str("room_id"))
            json(
                """{"room":{"room_id":"onyx-1","name":"Weekend hike","latest_seq":41,
                    "members":[{"member_id":"scout","profile":"scout","handle":"scout","display_name":"Scout"}]},
                    "driver_status":{"working":true,"blocked":false,"counts":{"stopping":1},
                      "pending_actions":[
                        {"kind":"approval","member_id":"scout","task_id":"t1","execution_generation":2,
                         "request_id":"req-1","approval":{"command":"rm -rf build","description":"recursive delete",
                         "choices":["once","session","deny"]}},
                        {"kind":"retry","task_id":"t9"}]}}""",
            )
        }

        val (room, state) = ServeGroupsRpc(rpc).roomState("onyx-1")

        assertEquals("Weekend hike", room?.name)
        assertEquals(41, room?.latestSeq)
        assertEquals("Scout", room?.members?.single()?.label)
        assertTrue("la salle travaille", state.working)
        assertTrue("elle s'arrête", state.stopping)
        assertFalse(state.blocked)
        val approval = state.approvals.single()
        assertEquals("scout", approval.memberID)
        assertEquals("t1", approval.taskID)
        assertEquals(2, approval.executionGeneration)
        assertEquals("req-1", approval.id)
        // Une salle ne prend que « once » ou « deny ».
        assertEquals(listOf("once", "deny"), approval.request.choices)
        assertEquals("rm -rf build", approval.request.command)
        assertEquals(listOf("t9"), state.retries)
        assertTrue(state.waitsOnPerson)
    }

    @Test
    fun `groups stop envoie un cancel id neuf a chaque fois`() = runBlocking {
        val cancels = mutableListOf<String?>()
        val rpc = RpcChannel { method, params, _ ->
            assertEquals("groups.stop", method)
            assertEquals("onyx-1", params.str("room_id"))
            cancels += params.strOrNull("cancel_id")
            json("""{"stopped":1}""")
        }

        ServeGroupsRpc(rpc).stop("onyx-1")
        ServeGroupsRpc(rpc).stop("onyx-1")

        assertEquals(2, cancels.size)
        assertTrue(cancels.all { it!!.startsWith("stop-") })
        assertTrue("un identifiant neuf à chaque fois", cancels[0] != cancels[1])
    }

    @Test
    fun `les autres appels d une salle portent leurs propres identifiants`() = runBlocking {
        val calls = mutableListOf<Triple<String, kotlinx.serialization.json.JsonObject, kotlinx.serialization.json.JsonObject?>>()
        val rpc = RpcChannel { method, params, _ ->
            calls += Triple(method, params, params.obj("payload"))
            json("""{"ok":true}""")
        }
        val groups = ServeGroupsRpc(rpc)
        val approval = com.onyx.app.data.model.serve.GroupApproval.from(
            json(
                """{"kind":"approval","member_id":"scout","task_id":"t1","execution_generation":3,
                    "request_id":"req-1","approval":{"command":"ls","choices":["once","deny"]}}""",
            ).asObject()!!,
        )!!

        groups.rename("onyx-1", "Garage")
        groups.retry("onyx-1", "t9")
        groups.approve("onyx-1", approval, "once")
        groups.disband("onyx-1")

        assertEquals(listOf("groups.rename", "groups.retry", "groups.approve", "groups.disband"), calls.map { it.first })
        assertEquals("Garage", calls[0].second.str("name"))
        assertEquals("onyx-1", calls[0].second.str("room_id"))
        assertTrue(calls[0].second.str("event_id").startsWith("rename-"))
        assertEquals("t9", calls[1].second.str("task_id"))
        assertEquals("scout", calls[2].second.str("member_id"))
        assertEquals("t1", calls[2].second.str("task_id"))
        assertEquals(3, calls[2].second.int("execution_generation"))
        assertEquals("req-1", calls[2].second.str("request_id"))
        assertEquals("once", calls[2].second.str("choice"))
        assertTrue(calls[3].second.str("cancel_id").startsWith("disband-"))
    }

    @Test
    fun `les sessions vivantes d une salle sont celles titrees Group`() = runBlocking {
        val rpc = RpcChannel { method, _, _ ->
            assertEquals("session.active_list", method)
            json(
                """{"sessions":[
                    {"id":"sess-1","session_key":"scout","title":"Group: onyx-1","status":"working"},
                    {"id":"sess-2","session_key":"writer","title":"Bot Chat","status":"working"},
                    {"id":"sess-3","session_key":"default","title":"Group: onyx-1","status":"idle"}]}""",
            )
        }

        val live = ServeGroupsRpc(rpc).liveSessions("onyx-1")

        assertEquals(listOf("sess-1", "sess-3"), live.map { it.id })
        assertEquals(listOf("scout", "default"), live.map { it.key })
        assertEquals("working", live.first().status)
    }

    @Test
    fun `la session d un bot se retrouve par son titre sur son profil`() = runBlocking {
        val rpc = RpcChannel { method, params, _ ->
            assertEquals("session.list", method)
            assertEquals("scout", params.str("profile"))
            assertEquals("Group: onyx-1", params.str("title"))
            assertTrue("les sessions cachées se demandent", params.bool("include_hidden") == true)
            json("""{"sessions":[{"id":"old","title":"Bot Chat"},{"id":"long-id","resolved_id":"sess-1","title":"Group: onyx-1"}]}""")
        }

        assertEquals("sess-1", ServeGroupsRpc(rpc).memberSession("onyx-1", "scout"))
    }

    @Test
    fun `session events since garde le dernier numero et la troncature`() = runBlocking {
        val rpc = RpcChannel { method, params, _ ->
            assertEquals("session.events.since", method)
            assertEquals("sess-1", params.str("session_id"))
            assertEquals(7, params.int("last_seen"))
            json("""{"events":[{"type":"message.delta","payload":{"text":"Bon"}}],"latest_seq":12,"truncated":true}""")
        }

        val slice = ServeGroupsRpc(rpc).sessionEvents("sess-1", 7)

        assertEquals(1, slice.events.size)
        assertEquals(12, slice.latest)
        assertTrue(slice.truncated)
    }

    @Test
    fun `une salle sans journal et sans reponse ne dit rien`() = runBlocking {
        val rpc = RpcChannel { method, _, _ ->
            when (method) {
                "groups.log" -> json("""{"events":[],"cursor":0,"has_more":false}""")
                else -> json("""{"driver_status":{}}""")
            }
        }
        val groups = ServeGroupsRpc(rpc)
        assertTrue(groups.log("onyx-1", 0).isEmpty())
        val (room, state) = groups.roomState("onyx-1")
        assertEquals(null, room)
        assertEquals(com.onyx.app.data.model.serve.GroupState(), state)
    }

    @Test
    fun `une salle inconnue fait remonter le refus du serveur`() = runBlocking {
        val rpc = RpcChannel { _, _, _ -> throw TransportError.Rpc(-32000, "unknown room onyx-9") }

        val failure = runCatching { ServeGroupsRpc(rpc).log("onyx-9", 0) }.exceptionOrNull()

        assertTrue(failure is TransportError)
        // Le refus se lit tel quel dans l'application : jamais un fil qui se vide en silence.
        assertTrue((failure as TransportError).userMessage(java.util.Locale.US).contains("unknown room onyx-9"))
    }

    // MARK: Ouvrir une salle (`groups.create` / `createGroup(named:bots:mainName:)`).

    @Test
    fun `groups create ouvre la salle avec le corps d appel de la reference`() = runBlocking {
        var method: String? = null
        var params: kotlinx.serialization.json.JsonObject? = null
        val rpc = RpcChannel { called, sent, _ ->
            method = called
            params = sent
            json(
                """{"room":{"room_id":"onyx-1","name":"Weekend hike","latest_seq":1,
                    "members":[{"member_id":"default","profile":"default","handle":"hermes","display_name":"Onyx"},
                               {"member_id":"scout","profile":"scout","handle":"scout","display_name":"Scout"}]}}""",
            )
        }

        val made = ServeGroupsRpc(rpc).create(
            "onyx-1",
            "Weekend hike",
            listOf(
                com.onyx.app.data.model.serve.GroupMember("default", "default", "hermes", "Onyx"),
                com.onyx.app.data.model.serve.GroupMember("scout", "scout", "scout", "Scout"),
            ),
        )

        assertEquals("groups.create", method)
        assertEquals(setOf("room_id", "name", "members"), params!!.keys)
        assertEquals("onyx-1", params!!.str("room_id"))
        assertEquals("Weekend hike", params!!.str("name"))
        val sent = params!!.array("members").mapNotNull { it.asObject() }
        assertEquals(listOf("default", "scout"), sent.map { it.str("member_id") })
        assertEquals("hermes", sent.first().str("handle"))
        assertEquals("Onyx", sent.first().str("display_name"))
        // La salle telle qu'Hermes l'a faite, prête pour la liste.
        assertEquals("onyx-1", made?.id)
        assertEquals("Weekend hike", made?.name)
        assertEquals(listOf("Onyx", "Scout"), made?.members?.map { it.label })
    }

    @Test
    fun `un membre sans nom de salle ne porte pas de display_name`() = runBlocking {
        var params: kotlinx.serialization.json.JsonObject? = null
        val rpc = RpcChannel { _, sent, _ ->
            params = sent
            json("""{"room":{"room_id":"onyx-1","name":"R"}}""")
        }

        ServeGroupsRpc(rpc).create(
            "onyx-1",
            "R",
            listOf(com.onyx.app.data.model.serve.GroupMember("scout", "scout", "scout")),
        )

        assertEquals(
            setOf("member_id", "profile", "handle"),
            params!!.array("members").single().asObject()!!.keys,
        )
    }

    @Test
    fun `un serveur qui ne dit rien de la salle n en a pas fait`() = runBlocking {
        val rpc = RpcChannel { _, _, _ -> json("""{"ok":true}""") }

        assertNull("rien à montrer : l'appelant ne doit pas croire qu'elle existe", ServeGroupsRpc(rpc).create("onyx-1", "R", emptyList()))
    }

    @Test
    fun `un refus du serveur remonte au lieu de passer pour une salle faite`() = runBlocking {
        val rpc = RpcChannel { _, _, _ -> throw TransportError.Rpc(-32000, "room worker is not running") }

        val failure = runCatching { ServeGroupsRpc(rpc).create("onyx-1", "R", emptyList()) }.exceptionOrNull()

        assertTrue(failure is TransportError)
        assertTrue((failure as TransportError).userMessage(java.util.Locale.US).contains("room worker is not running"))
    }
}
