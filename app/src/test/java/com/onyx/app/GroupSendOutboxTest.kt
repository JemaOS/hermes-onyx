package com.onyx.app

import com.onyx.app.data.net.TransportError
import com.onyx.app.ui.bots.GroupSendOutbox
import com.onyx.app.ui.bots.sendIsWorthRetrying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Aucun message perdu, et jamais deux fois le même.**
 *
 * La file d'envoi d'une salle : le même identifiant n'y entre qu'une fois, un échec passager laisse
 * le message en file avec une pose croissante, et l'épuisement des poses le rend à l'appelant — qui
 * doit le **dire**, pas le jeter.
 */
class GroupSendOutboxTest {

    @Test
    fun `un meme identifiant n entre qu une fois`() {
        val outbox = GroupSendOutbox()
        assertTrue("la première fois", outbox.offer("ask-1", "vous êtes là", now = 0))
        assertFalse("la deuxième est refusée", outbox.offer("ask-1", "vous êtes là", now = 10))
        assertEquals(1, outbox.items.size)
        assertTrue(outbox.knows("ask-1"))
    }

    @Test
    fun `les poses sont croissantes`() {
        val outbox = GroupSendOutbox()
        outbox.offer("ask-1", "Bonjour", now = 0)
        assertEquals("le premier essai part tout de suite", 0L, outbox.waitFor(now = 0))

        val poses = mutableListOf<Long>()
        var now = 0L
        repeat(GroupSendOutbox.SEND_BACKOFF_MS.size) {
            val wait = outbox.retryLater("ask-1", now)
            poses += wait
            now += wait
        }

        assertEquals(listOf(1_000L, 2_000L, 3_000L, 5_000L), poses)
        assertEquals("les poses sont strictement croissantes", poses.sorted(), poses)
        assertTrue("les poses sont épuisées", outbox.exhausted("ask-1"))
        assertTrue("le message est toujours en file : il n'est pas perdu", outbox.isNotEmpty)
        assertEquals(
            "et il est en tête, prêt à repartir quand son tour vient",
            "ask-1",
            outbox.due(now = now)?.id,
        )
    }

    @Test
    fun `un message envoye quitte la file, l ordre est garde`() {
        val outbox = GroupSendOutbox()
        outbox.offer("a", "un", now = 0)
        outbox.offer("b", "deux", now = 0)
        assertEquals("la tête d'abord", "a", outbox.due(now = 0)?.id)

        outbox.sent("a")
        assertEquals("le suivant prend la tête", "b", outbox.due(now = 0)?.id)
        assertTrue("plus rien de « a »", !outbox.knows("a") || outbox.items.none { it.id == "a" })
    }

    @Test
    fun `les refus qui ne passeront jamais ne sont pas reessayes`() {
        // Une adresse invalide, une clé refusée, un 4xx métier : réessayer ne changerait rien.
        assertFalse(sendIsWorthRetrying(TransportError.BadUrl("bad")))
        assertFalse(sendIsWorthRetrying(TransportError.MissingApiKey))
        assertFalse(sendIsWorthRetrying(TransportError.Http(404, "not found")))
        assertFalse(sendIsWorthRetrying(TransportError.Http(401, "unauthorized")))
        assertFalse(sendIsWorthRetrying(TransportError.Rpc(5119, "no longer pending")))

        // Le réseau, un délai, un 5xx, une erreur interne du serveur : on réessaie.
        assertTrue(sendIsWorthRetrying(TransportError.Unreachable("offline")))
        assertTrue(sendIsWorthRetrying(TransportError.Timeout("timeout")))
        assertTrue(sendIsWorthRetrying(TransportError.StreamLost("cut")))
        assertTrue(sendIsWorthRetrying(TransportError.Http(502, "bad gateway")))
        assertTrue(sendIsWorthRetrying(TransportError.Http(429, "slow down")))
        assertTrue("une erreur interne du serveur se réessaie", sendIsWorthRetrying(TransportError.Rpc(-32603, "internal")))
    }

    @Test
    fun `renoncer sort le message de la file et le rend`() {
        val outbox = GroupSendOutbox()
        outbox.offer("ask-1", "Bonjour", now = 0)
        val given = outbox.giveUp("ask-1")
        assertEquals("ask-1", given?.id)
        assertTrue("la file est vide", !outbox.isNotEmpty)
        assertNull("et il n'y est plus", outbox.due(now = 0))
    }
}
