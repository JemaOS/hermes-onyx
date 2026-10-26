package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les libellés des notifications, en français: les états d'une approbation, les titres des trois
 * questions, et la phrase d'un tour en cours avec son outil et sa durée.
 */
class NotificationLocalizationTest {
    private val fr = Locale.FRANCE

    @Test
    fun `les etats d'une approbation sont traduits`() {
        assertEquals("Approuvée", OnyxLocalization.translate("Approved", fr))
        assertEquals("Refusée", OnyxLocalization.translate("Denied", fr))
        assertEquals("Impossible de répondre", OnyxLocalization.translate("Couldn't answer", fr))
        assertEquals(
            "Ouvrez Onyx pour répondre depuis la conversation.",
            OnyxLocalization.translate("Open Onyx to answer from the conversation.", fr),
        )
    }

    @Test
    fun `les actions de notification sont traduites`() {
        assertEquals("Répondre", OnyxLocalization.translate("Reply", fr))
        assertEquals("Approuver", OnyxLocalization.translate("Approve", fr))
        assertEquals("Refuser", OnyxLocalization.translate("Deny", fr))
    }

    @Test
    fun `les titres des questions de l'agent sont traduits`() {
        assertEquals("Onyx a une question", OnyxLocalization.translate("Onyx has a question", fr))
        assertEquals("Un mot de passe est requis", OnyxLocalization.translate("A password is needed", fr))
        assertEquals("Un secret est requis", OnyxLocalization.translate("A secret is needed", fr))
        // Dans le fil d'un bot, le titre nomme le bot: le motif insère son nom.
        assertEquals("writer a une question", OnyxLocalization.translate("writer has a question", fr))
    }

    @Test
    fun `la phrase du tour cite l'outil et la duree`() {
        assertEquals("Exécution de bash · 12s", OnyxLocalization.translate("Running bash · 12s", fr))
        assertEquals("Exécution de read_file", OnyxLocalization.translate("Running read_file", fr))
        assertEquals("Réponse en cours… · 1m 05s", OnyxLocalization.translate("Answering… · 1m 05s", fr))
        assertEquals("42 % lus", OnyxLocalization.translate("42% read", fr))
        assertEquals("90 % lus · 1h 02m", OnyxLocalization.translate("90% read · 1h 02m", fr))
    }

    @Test
    fun `les messages du sous-agent sont traduits`() {
        assertEquals("Envoyé au sous-agent", OnyxLocalization.translate("Sent to the subagent", fr))
        assertEquals(
            "Le sous-agent n’accepte pas de consigne pour le moment",
            OnyxLocalization.translate("The subagent isn't accepting steering right now", fr),
        )
        assertEquals("Le sous-agent a déjà terminé", OnyxLocalization.translate("Subagent already finished", fr))
    }
}
