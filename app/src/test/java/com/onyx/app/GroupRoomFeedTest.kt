package com.onyx.app

import com.onyx.app.data.model.serve.GroupApproval
import com.onyx.app.data.model.serve.GroupDraft
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupLiveSession
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupRoomStatus
import com.onyx.app.data.model.serve.GroupSessionSlice
import com.onyx.app.data.model.serve.GroupState
import com.onyx.app.data.net.GroupRoomApi
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.GroupRoomFeed
import com.onyx.app.ui.bots.GroupRoomFeedState
import com.onyx.app.ui.bots.RoomPage
import com.onyx.app.ui.bots.GroupSending
import com.onyx.app.ui.bots.roomItemCount
import com.onyx.app.ui.bots.roomPage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le fil d'une salle de bots : ce qui y a été dit, ce que la personne y écrit, et les réponses que
 * les bots écrivent avant que la salle ne les ait.
 *
 * La référence est `GroupFeed` (Echo/Services/Transport/ServerGroups:647) : le journal lu
 * après un curseur, un message envoyé qui reste à l'écran tant que la salle ne l'a pas, et un refus
 * du serveur qui se lit au lieu de faire disparaître ce qui a été écrit.
 */
class GroupRoomFeedTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val events = mutableListOf<GroupEvent>()
    private val sent = mutableListOf<Triple<String, String, String>>()
    private var logFailure: Exception? = null
    private var sendFailure: Exception? = null
    private var stateFailure: Exception? = null
    private var stopped: String? = null
    private var retried: Pair<String, String>? = null
    private var approved: Triple<String, GroupApproval, String>? = null
    private var live: List<GroupLiveSession> = emptyList()
    /** Ce que chaque session cachée a envoyé, dans l'ordre : `since` rogne, comme Hermes. */
    private var sessionFeeds = mutableMapOf<String, List<JsonElement>>()
    private var desktop: List<GroupRoom> = emptyList()
    private val methods = mutableListOf<String>()

    private inner class Api : GroupRoomApi {
        override suspend fun log(room: String, since: Int): List<GroupEvent> {
            methods += "groups.log"
            logFailure?.let { throw it }
            return events.filter { it.seq > since }
        }

        override suspend fun roomState(room: String): GroupRoomStatus {
            methods += "groups.state"
            stateFailure?.let { throw it }
            return GroupRoomStatus(room = null, state = status)
        }

        override suspend fun send(room: String, text: String, eventID: String) {
            methods += "groups.send"
            sendFailure?.let { throw it }
            sent += Triple(room, text, eventID)
        }

        override suspend fun stop(room: String) {
            methods += "groups.stop"
            stopped = room
        }

        override suspend fun rename(room: String, name: String) {
            methods += "groups.rename"
        }

        override suspend fun disband(room: String) {
            methods += "groups.disband"
        }

        override suspend fun retry(room: String, task: String) {
            methods += "groups.retry"
            retried = room to task
        }

        override suspend fun approve(room: String, approval: GroupApproval, choice: String) {
            methods += "groups.approve"
            approved = Triple(room, approval, choice)
        }

        override suspend fun desktopRooms(): List<GroupRoom> {
            methods += "profiles.list"
            return desktop
        }

        override suspend fun liveSessions(room: String): List<GroupLiveSession> {
            methods += "session.active_list"
            return live
        }

        override suspend fun memberSession(room: String, profile: String): String? {
            methods += "session.list"
            return "key-" + profile
        }

        override suspend fun sessionEvents(session: String, since: Int): GroupSessionSlice {
            methods += "session.events.since"
            // Comme `session.events.since` : ce qui suit le dernier événement lu, et rien avant.
            val all = sessionFeeds[session] ?: emptyList()
            return GroupSessionSlice(events = all.drop(since), latest = all.size)
        }
    }

    private var status = GroupState()
    /** L'horloge du fil : celle du téléphone, pour être d'accord avec `GroupDraft.read`. */
    private var clockNow = System.currentTimeMillis()

    private val room = GroupRoom(
        id = "onyx-1",
        name = "Weekend hike",
        members = listOf(
            GroupMember(id = "default", profile = "default", handle = "hermes", displayName = "Onyx"),
            GroupMember(id = "scout", profile = "scout", handle = "scout", displayName = "Scout"),
        ),
    )

    private fun feed(initial: GroupRoom = room) = GroupRoomFeed(initial, Api(), clock = { clockNow })

    private fun message(seq: Int, kind: String, actor: String?, text: String): GroupEvent =
        GroupEvent.from(
            json(
                """{"seq":$seq,"kind":"$kind","actor":{"kind":"${if (kind == "message.user") "user" else "member"}","id":"$actor"},
                    "payload":{"text":"$text","member_id":"$actor"}}""",
            ).asObject()!!,
        )!!

    @Test
    fun `le journal d une salle se lit et se suit`() = runBlocking {
        val feed = feed()
        events += message(1, "message.user", "desktop", "Une randonnée samedi ?")
        events += message(2, "message.member", "scout", "Falls Loop est ouverte.")

        feed.look()

        val state = feed.state.value
        assertEquals(listOf(1, 2), state.events.map { it.seq })
        assertEquals(2, state.cursor)
        assertEquals(2.0, state.seenMark!!, 0.0001)
        assertTrue("le premier journal est lu", state.loaded)
        assertNull(state.problem)
        // Rien n'a été redemandé deux fois : le curseur est celui du journal.
        assertEquals(2, state.rows.size)

        // Ce qui est plus ancien que le curseur ne revient pas.
        feed.look()
        assertEquals(2, feed.state.value.events.size)
    }

    @Test
    fun `un message part dans la salle et n entre dans le fil que par le journal`() = runBlocking {
        val feed = feed()
        val id = feed.beginSend("Bonjour à tous")

        // Il s'affiche tout de suite, et rien n'est écrit dans le fil de la salle avant le serveur.
        assertEquals(listOf("Bonjour à tous"), feed.state.value.sending.map { it.text })
        assertTrue("aucun message dans le journal", feed.state.value.events.isEmpty())

        feed.deliver(id, "Bonjour à tous")

        assertEquals(listOf(Triple("onyx-1", "Bonjour à tous", id)), sent)
        assertEquals("le journal ne l'a pas encore", 1, feed.state.value.sending.size)

        // La salle l'a : il quitte l'attente, et il est là par le journal (son propre texte).
        events += message(1, "message.user", "desktop", "Bonjour à tous")
        feed.look()

        assertTrue("plus rien en attente", feed.state.value.sending.isEmpty())
        assertEquals(listOf(1), feed.state.value.events.map { it.seq })
    }

    @Test
    fun `un envoi refuse se lit et le message quitte l ecran`() = runBlocking {
        val feed = feed()
        sendFailure = TransportError.Rpc(5119, "no longer pending")
        val id = feed.beginSend("Bonjour")

        feed.deliver(id, "Bonjour")

        val state = feed.state.value
        assertTrue("rien ne reste en attente", state.sending.isEmpty())
        assertTrue("le refus se lit", state.problem!!.contains("no longer pending"))
    }

    @Test
    fun `un serveur qui refuse de lire la salle le dit et garde ce qui attend`() = runBlocking {
        val feed = feed()
        logFailure = TransportError.Rpc(-32000, "unknown room onyx-1")

        feed.look()

        val state = feed.state.value
        assertTrue(state.problem!!.contains("unknown room onyx-1"))
        assertTrue("le fil est vide mais rien n'est inventé", state.events.isEmpty())

        // Ce qui a été écrit reste à l'écran : il ne disparaît pas en silence.
        val id = feed.beginSend("Bonjour")
        feed.deliver(id, "Bonjour")
        assertEquals(1, feed.state.value.sending.size)
    }

    @Test
    fun `une reponse en cours s ecrit avant d arriver dans la salle`() = runBlocking {
        val feed = feed()
        events += message(1, "message.user", "desktop", "Où marcher samedi ?")
        status = GroupState(working = true)
        live = listOf(GroupLiveSession(id = "sess-1", key = "key-scout", status = "working"))
        sessionFeeds["sess-1"] = listOf(
            json("""{"type":"message.start"}"""),
            json("""{"type":"message.delta","payload":{"text":"Falls Loop est ouverte"}}"""),
        )

        feed.look()
        feed.lookAtDrafts()

        val writing = feed.state.value.shownDrafts.single()
        assertEquals("sess-1", writing.id)
        assertEquals("scout", writing.memberID)
        assertEquals("Falls Loop est ouverte", writing.text)
        assertNull("elle s'écrit encore", writing.finishedAtMillis)
        assertEquals("elle est sous son bot", "Scout", feed.state.value.room.member(writing.memberID)?.label)
        // Le journal de la salle, lui, n'a pas encore la réponse.
        assertEquals(listOf(1), feed.state.value.events.map { it.seq })
        assertEquals("un bot écrit", writing, feed.state.value.writing)
        assertTrue("la salle travaille", feed.state.value.busy)

        // La salle l'a maintenant : le brouillon part, la réponse est dans le fil.
        events += message(2, "message.member", "scout", "Falls Loop est ouverte")
        status = GroupState()
        feed.look()
        feed.lookAtDrafts()

        assertTrue("plus de brouillon", feed.state.value.shownDrafts.isEmpty())
        assertEquals(listOf(1, 2), feed.state.value.events.map { it.seq })
    }

    @Test
    fun `une reponse finie que la salle n a jamais ecrite s efface apres un moment`() = runBlocking {
        val feed = feed()
        events += message(1, "message.user", "desktop", "Où marcher samedi ?")
        status = GroupState(working = true)
        live = listOf(GroupLiveSession(id = "sess-1", key = "key-scout", status = "working"))
        sessionFeeds["sess-1"] = listOf(json("""{"type":"message.complete","payload":{"text":"Pass."}}"""))

        feed.look()
        feed.lookAtDrafts()
        assertEquals("Pass.", feed.state.value.shownDrafts.single().text)
        assertTrue("elle est finie", feed.state.value.shownDrafts.single().finishedAtMillis != null)

        // Vingt secondes plus tard, une réponse que la salle n'a jamais écrite (un bot qui passe)
        // cesse d'être montrée.
        clockNow += 21_000
        status = GroupState()
        feed.lookAtDrafts()

        assertTrue(feed.state.value.shownDrafts.isEmpty())
    }

    @Test
    fun `un brouillon deja dans la salle ne s affiche pas deux fois`() = runBlocking {
        val feed = feed()
        events += message(1, "message.user", "desktop", "Où marcher samedi ?")
        status = GroupState(working = true)
        live = listOf(GroupLiveSession(id = "sess-1", key = "key-scout", status = "working"))
        sessionFeeds["sess-1"] = listOf(
            json("""{"type":"message.start"}"""),
            json("""{"type":"message.delta","payload":{"text":"Falls Loop"}}"""),
        )
        feed.look()
        feed.lookAtDrafts()
        assertEquals(1, feed.state.value.shownDrafts.size)

        // La réponse arrive dans le journal : le brouillon a sa ligne dans le fil, une seule.
        events += message(2, "message.member", "scout", "Falls Loop est ouverte.")
        feed.look()
        feed.lookAtDrafts()

        assertTrue(feed.state.value.shownDrafts.isEmpty())
        assertEquals(2, feed.state.value.events.size)
    }

    @Test
    fun `l arret part au serveur`() = runBlocking {
        val feed = feed()

        feed.stop()

        assertEquals("onyx-1", stopped)
        assertTrue("groups.stop" in methods)
    }

    @Test
    fun `une question repondue se cache et part au serveur`() = runBlocking {
        val feed = feed()
        val approval = GroupApproval.from(
            json(
                """{"kind":"approval","member_id":"scout","task_id":"t1","execution_generation":1,
                    "request_id":"req-1","approval":{"command":"ls","choices":["once","deny"]}}""",
            ).asObject()!!,
        )!!
        status = GroupState(approvals = listOf(approval))

        feed.answer(approval, "once")

        assertEquals(Triple("onyx-1", approval, "once"), approved)
        assertTrue("la carte est rangée", feed.state.value.answering.isEmpty())
    }

    @Test
    fun `une relance de reponse part avec sa tache`() = runBlocking {
        val feed = feed()

        feed.retry("t9")

        assertEquals("onyx-1" to "t9", retried)
    }

    @Test
    fun `une salle qu Hermes Desktop tient se lit sans parler de salle au serveur`() = runBlocking {
        val desktopRoom = GroupRoom(
            id = "desktop:id:demo",
            name = "Garage project",
            members = listOf(GroupMember(id = "fixit", profile = "fixit", handle = "fixit", displayName = "Fix-it")),
            inDesktop = true,
            desktopMessages = listOf(
                GroupRoom.Companion.DesktopMessage("a", 1_761_300_000_000L, true, "You", "L'établi peut-il aller au fond ?"),
                GroupRoom.Companion.DesktopMessage("b", 1_761_300_060_000L, false, "fixit", "Oui, 60 cm de profondeur au plus."),
            ),
        )
        desktop = listOf(desktopRoom)
        val feed = feed(initial = desktopRoom.copy(desktopMessages = emptyList()))

        feed.look()

        val state = feed.state.value
        assertEquals("Garage project", state.room.name)
        assertEquals(listOf("L'établi peut-il aller au fond ?", "Oui, 60 cm de profondeur au plus."), state.events.map { it.text })
        assertEquals("fixit", state.events.last().actorID)
        assertFalse("elle ne se parle pas ici", state.discussing)
        assertTrue("groups.log n'est pas appelé pour une salle Desktop", methods.none { it == "groups.log" })
        assertEquals(listOf("profiles.list"), methods.distinct())
    }

    @Test
    fun `une salle Desktop terminee ailleurs le dit`() = runBlocking {
        val feed = feed(initial = room.copy(id = "desktop:id:gone", inDesktop = true))
        desktop = emptyList()

        feed.look()

        assertEquals(OnyxLocalization["This group chat was ended in Hermes Desktop."], feed.state.value.problem)
    }

    @Test
    fun `une page de journal montre les derniers messages et les plus anciens a la demande`() {
        val feed = feed()
        val many = (1..40).map { seq -> message(seq, "message.member", "scout", "Message $seq") }

        val latest = roomPage(many, null)
        assertEquals(30, latest.shown.size)
        assertEquals(10, latest.hidden)
        assertEquals(11, latest.shown.first().seq)

        val earlier = roomPage(many, latest.shown.first().seq - 10)
        assertEquals(40, earlier.shown.size)
        assertEquals(0, earlier.hidden)
    }

    @Test
    fun `le journal d une salle Desktop garde la meme ligne pour un meme message`() {
        val desktopRoom = GroupRoom(
            id = "desktop:id:demo",
            name = "Garage",
            inDesktop = true,
            desktopMessages = listOf(
                GroupRoom.Companion.DesktopMessage("a", 1_761_300_000_000L, true, "You", "Bonjour"),
                GroupRoom.Companion.DesktopMessage("b", 1_761_300_060_000L, false, "fixit", "Salut"),
            ),
        )
        val seqs = mutableMapOf<String, Int>()

        val first = GroupRoomFeed.desktopEvents(desktopRoom, seqs).map { it.seq }
        val again = GroupRoomFeed.desktopEvents(desktopRoom, seqs).map { it.seq }

        assertEquals(listOf(1, 2), first)
        assertEquals(first, again)
    }

    @Test
    fun `la liste de la salle compte ses lignes`() {
        val said = listOf(message(1, "message.user", "desktop", "Bonjour"))
        val page = roomPage(said, null)
        // La ligne du journal, et la salle qui discute encore (aucune fin de tour après elle).
        assertEquals(2, roomItemCount(GroupRoomFeedState(room, events = said), page, said))
        // La discussion finie, il ne reste que la ligne (la fin d'un tour n'est pas une note ici).
        val done = listOf(said.first(), message(2, "room.activity", null, ""))
        val drawn = done.filter { it.isMessage }
        assertEquals(1, roomItemCount(GroupRoomFeedState(room, events = done), roomPage(drawn, null), drawn))
        // Rien dit : l'ouverture de la salle.
        assertEquals(1, roomItemCount(GroupRoomFeedState(room), RoomPage(emptyList(), 0, 0), emptyList()))

        val approval = GroupApproval.from(
            json(
                """{"kind":"approval","member_id":"scout","task_id":"t1","execution_generation":1,
                    "request_id":"req-1","approval":{"command":"ls","choices":["once","deny"]}}""",
            ).asObject()!!,
        )!!
        val busy = GroupRoomFeedState(
            room = room,
            events = said,
            sending = listOf(GroupSending("evt-1", "Bonjour")),
            status = GroupState(approvals = listOf(approval), retries = listOf("t9")),
            problem = "The server refused: unknown room",
        )
        // La ligne, le message en route, la salle qui attend, la carte, la relance, le refus.
        assertEquals(6, roomItemCount(busy, page, said))
        // Une carte répondue se range : sa ligne disparaît (la salle attend encore la personne).
        assertEquals(4, roomItemCount(busy.copy(answering = setOf("req-1", "t9")), page, said))
    }
}
