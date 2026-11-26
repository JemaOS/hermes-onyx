package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.room.ROOM_HANDOFF_KIND
import com.onyx.app.data.room.RoomHandoffLine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le passage de main dans la liste de la salle.**
 *
 * La ligne est rendue par la **même** liste que les autres événements (`state.rows`) : elle prend
 * la place de la réponse qui la porte, et rien d'autre ne bouge. Ce fichier verrouille les deux
 * défauts qu'un journal mal lu fabrique — une ligne **deux fois** pour le même passage, et un
 * ordre **bousculé** —, plus l'application du meneur, qui ne doit agir qu'une fois par passage.
 */
class RoomLeadTest {

    private val members = listOf(
        GroupMember(id = "talk", profile = "Talk", handle = "talk", displayName = "Talk"),
        GroupMember(id = "coder", profile = "Coder", handle = "coder", displayName = "Coder"),
    )

    private val room = GroupRoom(id = "onyx-1", name = "Facture", members = members)

    private fun said(seq: Int, text: String): GroupEvent = GroupEvent(
        seq = seq,
        kind = "message.user",
        actorKind = "user",
        actorID = null,
        payload = buildJsonObject { put("text", text) },
        createdAtMillis = seq.toLong() * 1_000,
    )

    private fun answer(seq: Int, from: String, text: String): GroupEvent = GroupEvent(
        seq = seq,
        kind = "message.member",
        actorKind = "member",
        actorID = from,
        payload = buildJsonObject { put("text", text) },
        createdAtMillis = seq.toLong() * 1_000,
    )

    private fun state(vararg events: GroupEvent) = GroupRoomFeedState(room = room, events = events.toList())

    @Test
    fun `la ligne de passage suit sa reponse, et les rangees existantes ne bougent pas`() {
        val rows = state(
            said(1, "Qui prend la facture ?"),
            answer(2, "Talk", "C'est du Kotlin. @Coder prend la suite sur la facture."),
            answer(3, "Coder", "Je m'en occupe."),
        ).rows

        // Quatre rangées : les trois dites, plus **une** ligne de passage — à sa place, juste après
        // la réponse qui la porte.
        assertEquals(listOf("message.user", "message.member", ROOM_HANDOFF_KIND, "message.member"), rows.map { it.kind })
        assertEquals(listOf(1, 2, 2, 3), rows.map { it.seq })

        // Ce qui était déjà là garde son ordre, et son nombre.
        val kept = rows.filter { it.kind != ROOM_HANDOFF_KIND }
        assertEquals(listOf(1, 2, 3), kept.map { it.seq })
        assertEquals(3, kept.size)
    }

    @Test
    fun `relire le meme journal ne dessine pas deux fois le passage`() {
        val events = listOf(
            said(1, "Qui prend la facture ?"),
            answer(2, "Talk", "@Coder prend la suite."),
        )

        val first = state(*events.toTypedArray()).rows
        val second = state(*events.toTypedArray()).rows

        assertEquals(first, second)
        assertEquals(1, first.count { it.kind == ROOM_HANDOFF_KIND })
    }

    @Test
    fun `deux reponses avec la meme mention ne font qu une ligne`() {
        val rows = state(
            said(1, "On commence ?"),
            answer(2, "Talk", "@Coder prend la suite."),
            answer(3, "Coder", "Entendu. @Coder continue."),
        ).rows

        assertEquals(1, rows.count { it.kind == ROOM_HANDOFF_KIND })
    }

    @Test
    fun `sans passage, la liste est exactement celle d avant`() {
        val events = listOf(
            said(1, "Bonjour à tous"),
            answer(2, "Talk", "J'ai fini, à toi de voir."),
        )

        val rows = state(*events.toTypedArray()).rows

        assertEquals(events, rows)
        assertTrue(rows.none { it.kind == ROOM_HANDOFF_KIND })
    }

    @Test
    fun `un passage ne change le destinataire qu une fois`() {
        val lines = listOf(
            RoomHandoffLine(seq = 2, atMillis = 2_000, from = "Talk", to = "Coder", sentence = "@Coder prend la suite"),
        )

        val first = roomLeadRead(lead = null, lines = lines, through = 0)
        assertEquals("Coder", first.lead)
        assertEquals(2, first.through)

        // Relire le journal (chaque seconde, à chaque reconnexion) ne rejoue pas le passage.
        val second = roomLeadRead(lead = first.lead, lines = lines, through = first.through)
        assertEquals("Coder", second.lead)
        assertEquals(2, second.through)
    }

    @Test
    fun `l histoire de la salle ne defait pas le choix d aujourd hui`() {
        // Le journal d'hier propose Coder : rien n'est appliqué tant que la salle n'a pas parlé.
        val read = roomLeadRead(lead = "Planner", lines = emptyList(), through = 0)
        assertEquals("Planner", read.lead)

        // Un passage nouveau, lui, l'emporte — c'est la règle du produit.
        val fresh = roomLeadRead(
            lead = "Planner",
            lines = listOf(RoomHandoffLine(seq = 9, atMillis = 9_000, from = "Talk", to = "Coder", sentence = "@Coder")),
            through = 4,
        )
        assertEquals("Coder", fresh.lead)
    }

    /**
     * **Le meneur annoncé par la salle** (`context.lead_member_id`) est un identifiant de membre ;
     * l'écran, lui, coche un profil. C'est le roster qui fait le pont — et un identifiant inconnu
     * du roster est rendu tel quel plutôt qu'effacé en silence.
     */
    @Test
    fun `le meneur de la salle est resolu dans le roster`() {
        val members = listOf(
            GroupMember(id = "m-planner", profile = "planner", handle = "planner", displayName = "Planner"),
            GroupMember(id = "coder", profile = "coder", handle = "coder", displayName = "coder"),
        )

        assertEquals("planner", roomLeadProfile("m-planner", members))
        assertEquals("coder", roomLeadProfile("coder", members))
        // Un identifiant que le roster ne connaît pas garde son nom : mieux vaut un nom inconnu
        // qu'un meneur qui disparaît.
        assertEquals("ghost", roomLeadProfile("ghost", members))
        // Rien à nommer : pas de meneur.
        assertNull(roomLeadProfile("   ", members))
    }
}
