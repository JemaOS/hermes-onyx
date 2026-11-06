package com.onyx.app.ui.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Un refus vaut pour la conversation, pas pour la demande qu'on vient de voir.**
 *
 * Le défaut signalé : « quand je fais refuser il réapparaît à chaque fois quand je réécris un
 * prompt, et dans toutes les autres conversations ». La cause est dans la clé du registre : il
 * retenait l'**identifiant de la demande**, or le tour suivant en publie une **neuve**. Refuser
 * était donc un geste sans suite — la personne disait non, et l'app lui reposait la question avec
 * un identifiant qu'elle n'avait jamais vu.
 *
 * Le registre retient maintenant la **session serveur** (la conversation) en plus de la demande,
 * et seulement pour ce serveur-là : ailleurs, la question reste légitime.
 */
class TakeoverDeclinesTest {

    @Test
    fun `une demande neuve dans la meme conversation reste refusee`() {
        // Le cœur du défaut : l'identifiant change à chaque tour, la conversation non.
        TakeoverDeclines.refuse(serverId = "srv-a", session = "sess-a", id = "demande-1")

        assertTrue(
            "le tour suivant republie une demande neuve : elle est déjà refusée",
            TakeoverDeclines.isRefused("srv-a", "sess-a", "demande-2"),
        )
    }

    @Test
    fun `une autre conversation n'herite pas du refus`() {
        TakeoverDeclines.refuse(serverId = "srv-b", session = "sess-b", id = "demande-b1")

        assertFalse(
            "refuser dans un fil ne ferme pas la question dans un autre",
            TakeoverDeclines.isRefused("srv-b", "sess-c", "demande-c1"),
        )
    }

    @Test
    fun `un autre serveur n'herite pas du refus`() {
        TakeoverDeclines.refuse(serverId = "srv-d", session = "sess-d", id = "demande-d1")

        assertFalse(
            "deux passerelles différentes ne partagent pas un refus",
            TakeoverDeclines.isRefused("srv-e", "sess-d", "demande-d1"),
        )
    }

    @Test
    fun `sans session, la demande exacte reste refusee`() {
        // Serveur plus ancien : il ne publie pas la conversation. On retombe sur la demande
        // elle-même — c'est ce que l'app savait faire, et cela ne doit pas régresser.
        TakeoverDeclines.refuse(serverId = "srv-f", session = "", id = "demande-f1")

        assertTrue(TakeoverDeclines.isRefused("srv-f", "", "demande-f1"))
        assertFalse(
            "faute de conversation, on ne devine pas les autres demandes",
            TakeoverDeclines.isRefused("srv-f", "", "demande-f2"),
        )
    }

    @Test
    fun `oublier la session rouvre la question`() {
        // Un fil qui se rattache à une autre session serveur ne doit pas hériter d'un refus
        // prononcé pour une autre conversation.
        TakeoverDeclines.refuse(serverId = "srv-g", session = "sess-g", id = "demande-g1")
        TakeoverDeclines.forgetSession("srv-g", "sess-g")

        assertFalse(TakeoverDeclines.isRefused("srv-g", "sess-g", "demande-g2"))
    }
}