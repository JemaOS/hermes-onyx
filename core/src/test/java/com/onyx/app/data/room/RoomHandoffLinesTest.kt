package com.onyx.app.data.room

import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupMember
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le passage de main, côté client** : la ligne que la salle ajoute, et le destinataire qu'elle
 * change.
 *
 * Ce qui est verrouillé ici, c'est ce que la ligne ne fait **jamais** : apparaître deux fois pour le
 * même passage, apparaître sans qu'un membre ait nommé quelqu'un, ou bousculer l'ordre du journal.
 * Et l'autre moitié du contrat : `@nom` adresse **toujours** directement, meneur ou pas.
 */
class RoomHandoffLinesTest {

    private val members = listOf(
        GroupMember(id = "talk", profile = "Talk", handle = "talk", displayName = "Talk"),
        GroupMember(id = "coder", profile = "Coder", handle = "coder", displayName = "Coder"),
        GroupMember(id = "planner", profile = "Planner", handle = "planner", displayName = "Planner"),
    )

    private fun answer(seq: Int, from: String, text: String): GroupEvent = GroupEvent(
        seq = seq,
        kind = "message.member",
        actorKind = "member",
        actorID = from,
        payload = buildJsonObject { put("text", text) },
        createdAtMillis = seq.toLong() * 1_000,
    )

    private fun said(seq: Int, text: String): GroupEvent = GroupEvent(
        seq = seq,
        kind = "message.user",
        actorKind = "user",
        actorID = null,
        payload = buildJsonObject { put("text", text) },
        createdAtMillis = seq.toLong() * 1_000,
    )

    @Test
    fun `une reponse qui nomme un membre ajoute une ligne, a sa place`() {
        val lines = roomHandoffs(
            listOf(
                said(1, "Qui prend la facture ?"),
                answer(2, "Talk", "C'est du Kotlin. @Coder prend la suite sur la facture."),
                answer(3, "Coder", "Je m'en occupe."),
            ),
            members,
        )

        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(2, line.seq)
        assertEquals("Talk", line.from)
        assertEquals("Coder", line.to)
        assertEquals("@Coder prend la suite sur la facture", line.sentence)
    }

    @Test
    fun `deux reponses successives avec la meme mention n en ajoutent qu une`() {
        val lines = roomHandoffs(
            listOf(
                answer(2, "Talk", "@Coder prend la suite."),
                answer(3, "Planner", "@Coder garde la suite, il a le contexte."),
            ),
            members,
        )

        // Le destinataire n'a pas changé : ce n'est pas un nouveau passage.
        assertEquals(1, lines.size)
        assertEquals(2, lines.single().seq)
    }

    @Test
    fun `un changement de destinataire ajoute une ligne de plus`() {
        val lines = roomHandoffs(
            listOf(
                answer(2, "Talk", "@Coder prend la suite."),
                answer(3, "Coder", "Fait. @Planner relit ?"),
            ),
            members,
        )

        assertEquals(listOf("Coder", "Planner"), lines.map { it.to })
        assertEquals(listOf(2, 3), lines.map { it.seq })
    }

    @Test
    fun `une reponse sans mention n ajoute aucune ligne`() {
        assertTrue(
            roomHandoffs(
                listOf(
                    said(1, "Bonjour à tous"),
                    answer(2, "Talk", "J'ai fini, à toi de voir."),
                    answer(3, "Coder", "Rien à ajouter."),
                ),
                members,
            ).isEmpty(),
        )
    }

    @Test
    fun `un code ou une auto-mention ne passent pas la main`() {
        assertTrue(
            roomHandoffs(
                listOf(
                    answer(2, "Talk", "On l'appelle comme ça : `@Coder prends ça`."),
                    answer(3, "Coder", "@Coder a fini, suite au prochain."),
                ),
                members,
            ).isEmpty(),
        )
    }

    @Test
    fun `les lignes suivent l ordre du journal, sans le reordonner`() {
        val events = listOf(
            said(1, "Qui commence ?"),
            answer(2, "Talk", "@Planner commence."),
            answer(3, "Planner", "D'accord."),
            answer(4, "Planner", "@Coder prend la suite."),
        )

        val lines = roomHandoffs(events, members)

        assertEquals(listOf(2, 4), lines.map { it.seq })
        assertEquals(lines.map { it.seq }.sorted(), lines.map { it.seq })
        // Deux lectures du même journal rendent les mêmes lignes : aucune ne peut donc s'afficher deux fois.
        assertEquals(lines, roomHandoffs(events, members))
    }

    @Test
    fun `la ligne porte le genre du journal et se dessine comme lui`() {
        val event = roomHandoffs(listOf(answer(2, "Talk", "@Coder prend la suite.")), members)
            .single()
            .asEvent()

        assertEquals(ROOM_HANDOFF_KIND, event.kind)
        assertEquals(2, event.seq)
        assertEquals("Talk", event.actorID)
        assertEquals("Coder", event.payload["to"]?.toString()?.trim('"'))
    }

    // — Le destinataire de l'envoi —

    @Test
    fun `sans mention, le message part au meneur`() {
        assertEquals(
            "@coder la facture est prête",
            roomAddressed("la facture est prête", to = "Coder", members = members),
        )
    }

    @Test
    fun `une mention ecrite par la personne prime sur le meneur`() {
        assertEquals(
            "@Planner regarde plutôt ça",
            roomAddressed("@Planner regarde plutôt ça", to = "Coder", members = members),
        )
    }

    @Test
    fun `sans meneur, le message ne change pas d un caractere`() {
        assertEquals("la facture est prête", roomAddressed("la facture est prête", to = null, members = members))
        // Un meneur qui n'est plus un membre de la salle n'adresse rien non plus.
        assertEquals("la facture est prête", roomAddressed("la facture est prête", to = "Parti", members = members))
    }

    @Test
    fun `une mention se lit hors code, et jamais dans une adresse`() {
        assertEquals("Coder", roomMentioned("@Coder prend la suite."))
        assertEquals(null, roomMentioned("jean@coder.example"))
        assertEquals(null, roomMentioned("la syntaxe est `@Coder` ici"))
    }
}
