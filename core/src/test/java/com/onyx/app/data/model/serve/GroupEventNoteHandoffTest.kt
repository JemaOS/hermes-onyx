package com.onyx.app.data.model.serve

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La ligne qu'un `turn.cancelled` écrit dans le journal d'une room.
 *
 * Le cas qui compte est `handed_over` : le serveur arrête le tour **parce qu'un autre le reprend**.
 * La ligne de passage de main raconte déjà le geste ; si `note()` écrivait en plus « X was stopped »,
 * un seul geste se lirait comme deux — un arrêt **et** une passation. C'est le piège que la spec du
 * lot 5 nomme, et que ce fichier fige côté client.
 */
class GroupEventNoteHandoffTest {

    private fun room() = GroupRoom(id = "r1", name = "Room", members = emptyList())

    private fun cancelled(reason: String?): GroupEvent {
        val payload = buildJsonObject { if (reason != null) put("reason", reason) }
        return GroupEvent(
            seq = 1, kind = "turn.cancelled", actorKind = "member", actorID = "talk",
            payload = payload, createdAtMillis = 0L,
        )
    }

    @Test
    fun `un passage de main n'ecrit pas un arret`() {
        assertNull(
            "handed_over ne doit rien écrire : la ligne de passage suffit",
            cancelled("handed_over").note(room()),
        )
    }

    @Test
    fun `un tour supersede n'ecrit rien non plus`() {
        assertNull(cancelled("superseded_by_newer_user_event").note(room()))
    }

    @Test
    fun `un vrai arret ecrit toujours sa ligne`() {
        assertNotNull("un arrêt demandé reste une ligne", cancelled("stopped").note(room()))
        assertNotNull("sans raison connue, la ligne reste", cancelled(null).note(room()))
    }
}
