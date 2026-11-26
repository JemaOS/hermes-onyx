package com.onyx.app.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que le client lit comme « passage de main », et surtout ce qu'il **refuse** de lire.
 *
 * Le risque de cette fonction n'est pas de ne rien trouver : c'est de trouver un passage là où il
 * n'y en a pas — un exemple de code, une adresse électronique, le bot qui se cite lui-même. Un
 * faux passage change le destinataire par défaut d'une room, donc il fait perdre un tour.
 */
class RoomHandoffTest {

    private val members = listOf("Coder", "Talk", "Planner")

    @Test
    fun `une mention d'un membre est un passage de main`() {
        val handoff = roomHandoffProposal(
            "C'est du Kotlin. @Coder prend la suite sur la facture.",
            from = "Talk",
            members = members,
        )
        assertEquals("Coder", handoff?.to)
        assertEquals("@Coder prend la suite sur la facture", handoff?.sentence)
        assertTrue("la phrase porte la mention", handoff!!.sentence.contains("@Coder"))
    }

    @Test
    fun `la casse de la mention ne compte pas, celle de la room fait foi`() {
        val handoff = roomHandoffProposal("@coder peut prendre ça.", from = "Talk", members = members)
        assertEquals("Coder", handoff?.to)
    }

    @Test
    fun `un bot qui se cite ne se passe pas la main a lui-meme`() {
        assertNull(roomHandoffProposal("@Talk a fini, suite au prochain.", from = "Talk", members = members))
    }

    @Test
    fun `une mention inconnue est ignoree et la suivante est lue`() {
        val handoff = roomHandoffProposal(
            "@Jean n'est pas dans la room, mais @Planner oui.",
            from = "Talk",
            members = members,
        )
        assertEquals("Planner", handoff?.to)
    }

    @Test
    fun `une adresse electronique n'est pas une mention`() {
        assertNull(
            roomHandoffProposal(
                "Écris à jean@coder.example et il verra.",
                from = "Talk",
                members = members,
            ),
        )
    }

    @Test
    fun `une mention dans un bloc de code n'est pas une consigne`() {
        val answer = """
            Voici comment on l'appelle :
            ```
            client.send("@Coder prends ça")
            ```
            Rien d'autre.
        """.trimIndent()
        assertNull(roomHandoffProposal(answer, from = "Talk", members = members))
    }

    @Test
    fun `une mention en code en ligne n'est pas une consigne non plus`() {
        assertNull(roomHandoffProposal("La syntaxe est `@Coder` ici, sans effet.", from = "Talk", members = members))
    }

    @Test
    fun `sans mention, il n'y a pas de passage`() {
        assertNull(roomHandoffProposal("J'ai fini, à toi de voir.", from = "Talk", members = members))
        assertNull(roomHandoffProposal("", from = "Talk", members = members))
        assertNull(roomHandoffProposal("@Coder et toi.", from = "Talk", members = emptyList()))
    }

    @Test
    fun `la phrase est bornee, jamais un paragraphe`() {
        val long = "Mot " + "bla ".repeat(200) + "@Coder prend la suite"
        val handoff = roomHandoffProposal(long, from = "Talk", members = members)
        assertEquals("Coder", handoff?.to)
        assertTrue("la ligne de journal reste courte : ${handoff!!.sentence.length}", handoff.sentence.length <= 241)
    }
}
