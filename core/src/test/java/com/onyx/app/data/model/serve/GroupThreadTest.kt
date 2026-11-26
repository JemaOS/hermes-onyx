package com.onyx.app.data.model.serve

import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.bool
import com.onyx.app.data.net.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le fil d'une salle : ce qu'une entrée du journal dit d'un tour, la réponse qui s'écrit dans la
 * session cachée d'un bot, et ce que la salle attend.
 *
 * La référence est `GroupEvent.note(in:)` / `GroupDraft.read(_:)` / `GroupState`
 * (Echo/Services/Transport/ServerGroups:183, :257 et :217).
 */
class GroupThreadTest {

    /** Les libellés d'un fil sont localisés : ces tests les lisent en anglais, comme la référence. */
    private var previousLocale: java.util.Locale = java.util.Locale.getDefault()

    @org.junit.Before
    fun english() {
        previousLocale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.US)
    }

    @org.junit.After
    fun restoreLocale() {
        java.util.Locale.setDefault(previousLocale)
    }

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val room = GroupRoom(
        id = "onyx-1",
        name = "Weekend hike",
        members = listOf(
            GroupMember(id = "default", profile = "default", handle = "hermes", displayName = "Onyx"),
            GroupMember(id = "scout", profile = "scout", handle = "scout", displayName = "Scout"),
        ),
    )

    private fun event(seq: Int, kind: String, actor: String? = null, payload: String = "{}"): GroupEvent =
        GroupEvent.from(
            json("""{"seq":$seq,"kind":"$kind","actor":{"kind":"member","id":"${actor ?: ""}"},"payload":$payload}""")
                .asObject()!!,
        )!!

    // MARK: Ce qu'une entrée du journal dit

    @Test
    fun `un bot qui passe et un bot qui a parle se lisent dans le journal`() {
        assertEquals(
            "Scout passed",
            event(4, "turn.settled", "scout", """{"passed":true}""").note(room),
        )
        assertNull("un tour qui n'a pas passé ne dit rien", event(4, "turn.settled", "scout", "{}").note(room))
        assertEquals(
            "Scout couldn't answer: unreachable",
            event(5, "turn.failed", "scout", """{"error":"unreachable"}""").note(room),
        )
        assertEquals("Scout was stopped", event(6, "turn.cancelled", "scout", """{"reason":"stop"}""").note(room))
        // Une réponse remplacée par un message plus récent n'est pas une ligne.
        assertNull(event(7, "turn.cancelled", "scout", """{"reason":"superseded_by_newer_user_event"}""").note(room))
        assertEquals("Scout didn't finish", event(8, "turn.deferred", "scout", "{}").note(room))
        // Un membre inconnu de la salle reste « A bot ».
        assertEquals("A bot passed", event(9, "turn.settled", "writer", """{"passed":true}""").note(room))
    }

    @Test
    fun `la fin d une discussion et un renommage se lisent, le reste se tait`() {
        assertEquals(
            "That's as long as a round goes. Say something to carry on.",
            event(10, "room.activity", "room", """{"status":"bounded"}""").note(room),
        )
        assertNull(event(11, "room.activity", "room", """{"status":"settled"}""").note(room))
        assertEquals("Renamed “Garage”", event(12, "room.renamed", "room", """{"name":"Garage"}""").note(room))
        assertNull(event(13, "message.member", "scout", """{"text":"Salut"}""").note(room))
    }

    @Test
    fun `une fin de discussion ferme le tour, pas une reponse`() {
        assertTrue(event(1, "room.activity").endsDiscussion)
        assertTrue(event(2, "room.stop_requested").endsDiscussion)
        assertFalse(event(3, "message.member", "scout", """{"text":"Salut"}""").endsDiscussion)
        assertFalse(event(4, "turn.settled", "scout").endsDiscussion)
    }

    @Test
    fun `un evenement se lit comme la référence le lit`() {
        val message = GroupEvent.from(
            json(
                """{"seq":12,"kind":"message.member","actor":{"kind":"member","id":"scout"},
                    "payload":{"text":"Falls Loop est ouverte.","member_id":"scout"},"created_at":1761300060}""",
            ).asObject()!!,
        )!!
        assertEquals(12, message.seq)
        assertTrue(message.isMessage)
        assertEquals("Falls Loop est ouverte.", message.text)
        assertEquals("scout", message.actorID)
        assertEquals(1761300060000L, message.createdAtMillis)

        // Le membre d'un événement de tour n'est que dans la charge utile.
        val settled = GroupEvent.from(
            json("""{"seq":13,"kind":"turn.settled","actor":{"kind":"gateway","id":"gw"},"payload":{"member_id":"writer"}}""")
                .asObject()!!,
        )!!
        assertEquals("writer", settled.actorID)
        assertNull("sans numéro, pas d'entrée", GroupEvent.from(json("""{"kind":"message.member"}""").asObject()!!))
    }

    // MARK: La réponse qui s'écrit (`GroupDraft`)

    @Test
    fun `une reponse se lit a mesure qu elle s ecrit`() {
        var draft = GroupDraft(id = "sess-1", memberID = "scout", after = 11)

        var read = draft.read(listOf(json("""{"type":"message.start"}"""), json("""{"type":"message.delta","payload":{"text":"Falls Loop "}}""")))
        assertTrue("un début de réponse était là", read.started)
        draft = read.draft
        assertEquals("Falls Loop ", draft.text)
        assertNull("elle n'est pas finie", draft.finishedAtMillis)

        // Ce qu'un bot écrit à côté d'un appel d'outil est remis à zéro, comme la référence.
        read = draft.read(listOf(json("""{"type":"message.interim"}"""), json("""{"type":"message.delta","payload":{"text":"Falls Loop est ouverte"}}""")))
        draft = read.draft
        assertFalse(read.started)
        assertEquals("Falls Loop est ouverte", draft.text)

        // Entière à `message.complete`, et finie.
        read = draft.read(listOf(json("""{"type":"message.complete","payload":{"text":"Falls Loop est ouverte. Prenez des couches !"}}""")))
        draft = read.draft
        assertEquals("Falls Loop est ouverte. Prenez des couches !", draft.text)
        assertTrue("elle ne s'écrit plus", draft.finishedAtMillis != null)
        assertTrue(draft.finishedBefore(draft.finishedAtMillis!! + 1))
    }

    @Test
    fun `une reponse lue sans son debut reprend son texte`() {
        val draft = GroupDraft(id = "sess-1", memberID = "scout", text = "la fin d'une réponse", after = 3)
        val read = draft.read(listOf(json("""{"type":"message.delta","payload":{"text":" et la suite."}}""")))
        assertFalse("aucun début n'était là", read.started)
        // C'est l'appelant qui pose les points de suspension, comme la référence (`first`, `truncated`).
        assertEquals("la fin d'une réponse et la suite.", read.draft.text)
    }

    @Test
    fun `le titre d une session de salle est celui d Hermes`() {
        assertEquals("Group: onyx-1", GroupDraft.sessionTitle("onyx-1"))
    }

    // MARK: Ce qu'une salle attend

    @Test
    fun `l etat d une salle dit ce qui travaille et ce qui attend la personne`() {
        val state = GroupState.from(
            json(
                """{"working":true,"blocked":true,"counts":{"stopping":0},
                    "pending_actions":[
                      {"kind":"approval","member_id":"scout","task_id":"t1","execution_generation":1,
                       "request_id":"req-1","approval":{"command":"ls","choices":["once","session","deny"]}},
                      {"kind":"retry","task_id":"t9"}]}""",
            ),
        )
        assertTrue(state.working)
        assertTrue(state.blocked)
        assertFalse(state.stopping)
        assertTrue(state.waitsOnPerson)
        assertEquals("once", state.approvals.single().request.approveChoice)
        assertEquals("deny", state.approvals.single().request.denyChoice)
        assertEquals(listOf("t9"), state.retries)

        // Rien : la salle ne travaille pas et n'attend personne.
        val idle = GroupState.from(json("""{}"""))
        assertEquals(GroupState(), idle)
        assertFalse(idle.waitsOnPerson)
        assertFalse(GroupState().working)
    }

    @Test
    fun `une approbation sans les siens ne se lit pas`() {
        assertNull(
            GroupApproval.from(
                json("""{"kind":"retry","task_id":"t9"}""").asObject()!!,
            ),
        )
        assertNull(
            GroupApproval.from(
                json("""{"kind":"approval","member_id":"scout","task_id":"t1","execution_generation":1}""").asObject()!!,
            ),
        )
        // Les choix d'une salle se limitent à « once » et « deny ».
        val approval = GroupApproval.from(
            json(
                """{"kind":"approval","member_id":"scout","task_id":"t1","execution_generation":1,
                    "request_id":"req-2","approval":{"command":"ls","choices":["session","always"]}}""",
            ).asObject()!!,
        )!!
        assertEquals(listOf("once", "deny"), approval.request.choices)
    }
}
