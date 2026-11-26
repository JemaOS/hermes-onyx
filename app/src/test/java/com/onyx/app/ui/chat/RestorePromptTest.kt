package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La restauration à un message : ce qui serait défait, et ce qui ne l'est pas.
 *
 * La règle vit dans [restorePlan] et [messagesThroughRestore] — des fonctions **pures**, donc
 * vérifiables ici sans appareil : c'est elles que le ViewModel appelle.
 */
class RestorePromptTest {

    private fun user(id: String, steer: Boolean = false) =
        ChatMessage(id = id, role = Role.USER, text = "q", createdAt = 0L, isSteer = steer)

    private fun reply(id: String) = ChatMessage(id = id, role = Role.ASSISTANT, text = "a", createdAt = 0L)

    private val thread = listOf(
        user("u1"), reply("a1"), user("u2"), reply("a2"), user("u3"), reply("a3"),
    )

    @Test
    fun `restaurer a un message garde ce message et compte ce qui suit`() {
        val plan = restorePlan(thread, "a2")!!
        assertEquals("a2", plan.messageId)
        // Le message visé reste : seuls u3 et a3 sont derrière lui.
        assertEquals(2, plan.removedMessages)
        // Un seul tour utilisateur à rembobiner côté serveur (`session.undo` compte les tours).
        assertEquals(1, plan.removedTurns)
    }

    @Test
    fun `restaurer a une reponse sans suite ne retire rien`() {
        val plan = restorePlan(thread, "a3")!!
        assertEquals(0, plan.removedMessages)
        assertEquals(0, plan.removedTurns)
    }

    @Test
    fun `restaurer a un message absent du fil ne propose rien`() {
        assertNull(restorePlan(thread, "inconnu"))
    }

    @Test
    fun `les notes de pilotage ne comptent pas comme des tours a rembobiner`() {
        val messages = listOf(
            user("u1"), reply("a1"), user("u2"),
            // Une note de pilotage (`isSteer`) n'est pas une question : le gateway ne la compte pas
            // comme un tour.
            user("s1", steer = true), reply("a2"), user("u3"), reply("a3"),
        )
        val plan = restorePlan(messages, "a2")!!
        assertEquals(2, plan.removedMessages)
        assertEquals(1, plan.removedTurns)
    }

    @Test
    fun `le fil ramene a un message s arrete sur ce message`() {
        val kept = messagesThroughRestore(thread, "a2")
        assertEquals(listOf("u1", "a1", "u2", "a2"), kept.map { it.id })
    }

    @Test
    fun `un message absent ne coupe rien`() {
        assertEquals(thread, messagesThroughRestore(thread, "inconnu"))
    }

    @Test
    fun `la phrase de confirmation dit ce qui sera defait et l irreversible`() {
        val message = restorePromptMessage(RestorePrompt("a2", removedMessages = 2, removedTurns = 1))
        // Le compte est dans la phrase, et l'irréversibilité est dite.
        assertTrue(message.contains("2"))
        assertTrue(message.contains("irréversible") || message.contains("can't be undone"))
    }

    @Test
    fun `le compte au singulier reste au singulier`() {
        assertTrue(restoreCountPhrase(1).startsWith("1 "))
        assertTrue(restoreCountPhrase(3).startsWith("3 "))
    }

    // ---- Le compte affiché est celui des messages réellement retirés -------------------------

    /**
     * **Le vrai nombre, et sa preuve.** Sur un fil de N messages, quel que soit le message visé, le
     * compte de la phrase **est** le nombre de messages que la restauration retire : tout ce qui suit
     * le message visé. La démonstration est arithmétique — le fil gardé plus le compte égale le fil
     * entier — donc elle ne dépend d'aucune supposition.
     */
    @Test
    fun `le compte affiche est le nombre de messages reellement retires`() {
        thread.forEachIndexed { index, message ->
            val plan = restorePlan(thread, message.id)!!
            val kept = messagesThroughRestore(thread, message.id)
            assertEquals(
                "à ${message.id} : le compte annoncé n'est pas ce qui suit le message",
                thread.size - index - 1,
                plan.removedMessages,
            )
            assertEquals(
                "à ${message.id} : le fil gardé plus le compte ne redonne pas le fil entier",
                thread.size,
                kept.size + plan.removedMessages,
            )
            if (plan.removedMessages > 0) {
                val phrase = restorePromptMessage(plan)
                assertTrue(
                    "à ${message.id} : la phrase n'annonce pas le vrai nombre (${plan.removedMessages}) — « $phrase »",
                    phrase.contains(plan.removedMessages.toString()),
                )
            }
        }
    }

    /**
     * **Aucune phrase ne dit zéro.** Quand rien ne suit le message visé, la phrase change : elle dit
     * ce qui est vrai, au lieu de compter des messages qu'elle ne retirerait pas.
     */
    @Test
    fun `a zero la phrase ne dit pas zero et change`() {
        val plan = restorePlan(thread, "a3")!!
        assertEquals(0, plan.removedMessages)
        val phrase = restorePromptMessage(plan)
        assertEquals("la phrase du zéro est celle de la maison", restoreZeroPhrase(), phrase)
        assertFalse("la phrase dit encore « 0 » : « $phrase »", phrase.contains("0"))
        assertFalse(
            "la phrase annonce encore un retrait : « $phrase »",
            phrase.contains("retire 0", ignoreCase = true) || phrase.contains("removes 0", ignoreCase = true),
        )
        // Et le message n'est pas vide de sens : il dit ce qui se passe vraiment.
        assertTrue("la phrase du zéro doit expliquer : « $phrase »", phrase.isNotBlank())
    }

    /**
     * **Une démonstration chiffrée**, telle qu'elle se lit dans le rapport : une conversation de six
     * messages (`u1 a1 u2 a2 u3 a3`), et ce que chaque point visé retire.
     */
    @Test
    fun `la demonstration du calcul sur six messages`() {
        val expected = mapOf(
            "u1" to 5, "a1" to 4, "u2" to 3, "a2" to 2, "u3" to 1, "a3" to 0,
        )
        assertEquals(6, thread.size)
        assertEquals(expected.keys, thread.map { it.id }.toSet())
        expected.forEach { (id, count) ->
            assertEquals("le fil de 6 messages, visé à $id", count, restorePlan(thread, id)!!.removedMessages)
        }
    }
}
