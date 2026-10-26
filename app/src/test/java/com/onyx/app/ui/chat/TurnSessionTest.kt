package com.onyx.app.ui.chat

import com.onyx.app.data.model.Role
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce qu'un tour en cours écrit, et ce qu'il dit de lui-même.
 *
 * Le propriétaire a signalé deux choses: un agent qui « travaille dans le vide » (une onde, et
 * rien à lire) et son propre message réaffiché comme une réponse du bot. Le tour doit donc
 * toujours dire où il en est, et ne jamais peindre le message de l'utilisateur comme sa réponse.
 */
class TurnSessionTest {

    private fun session(userText: String = "Regarde toutes les routes de l'application."): TurnSession =
        TurnSession(
            conversationId = "conversation",
            assistantId = "answer",
            userText = userText,
            startedAt = 1_000L,
        )

    @Test
    fun `un tour qui n'a rien recu dit qu'il attend le serveur, avec le temps ecoule`() {
        val session = session()
        val phase = session.phase

        assertEquals(TurnStep.STARTING, phase.step)
        assertTrue(phase.isStreaming)
        assertEquals(22.0, phase.elapsedSeconds(23_000L), 0.001)
        assertEquals(
            "En attente du serveur · 22,0 s",
            TurnStatusCopy.line(phase, 23_000L, Locale.FRANCE),
        )
    }

    @Test
    fun `un tour qui n'a rien produit depuis longtemps le dit au lieu de tourner en silence`() {
        val session = session()
        // Le serveur a parlé une fois (à 10 s), puis plus rien: le tour tourne toujours.
        session.apply(TurnEvent.ReasoningDelta("je cherche"), 11_000L)

        assertFalse("vingt secondes de silence ne sont pas anormales", session.phase.stalled(31_000L))
        assertTrue("au-delà du délai, l'écran le dit", session.phase.stalled(71_000L))
        assertEquals(
            "Aucune nouvelle du serveur depuis 1m 00s · 1m 10s",
            TurnStatusCopy.line(session.phase, 71_000L, Locale.FRANCE),
        )
    }

    @Test
    fun `chaque etape connue a sa phrase, et le raisonnement comme l'outil la donnent`() {
        val session = session()

        session.apply(TurnEvent.ReasoningDelta("je cherche"), 2_000L)
        assertEquals(TurnStep.THINKING, session.phase.step)
        assertEquals(
            "Réflexion · 2,0 s",
            TurnStatusCopy.line(session.phase, 3_000L, Locale.FRANCE),
        )

        session.apply(TurnEvent.ToolStarted(name = "terminal", preview = null), 4_000L)
        assertEquals(TurnStep.TOOL, session.phase.step)
        assertEquals("terminal", session.phase.toolName)
        assertEquals(
            "Exécution de terminal · 4,0 s",
            TurnStatusCopy.line(session.phase, 5_000L, Locale.FRANCE),
        )

        session.apply(TurnEvent.ToolFinished(name = "terminal", failed = false), 6_000L)
        assertTrue(session.message.tools.single().endedAt == 6_000L)

        session.apply(TurnEvent.TextDelta("Voici les routes."), 7_000L)
        assertEquals(TurnStep.WRITING, session.phase.step)
        assertEquals("Voici les routes.", session.message.text)
        assertEquals(
            "Rédaction de la réponse · 7,0 s",
            TurnStatusCopy.line(session.phase, 8_000L, Locale.FRANCE),
        )
    }

    @Test
    fun `le message de l'utilisateur renvoye par le serveur n'est jamais la reponse du bot`() {
        val question = "J'ai besoin de toi tu vas repasser toutes les routes possible de l'App dans un émulateur Android."
        val session = session(question)

        // Le serveur renvoie le message tel quel: c'est un écho du tour, pas une réponse.
        session.apply(TurnEvent.TextDelta(question), 2_000L)

        assertEquals("la réponse reste vide", "", session.message.text)
        assertTrue("le fil le dit", session.phase.echoedPrompt)
        assertEquals(
            "Le serveur a renvoyé votre message — en attente de la réponse · 2,0 s",
            TurnStatusCopy.line(session.phase, 3_000L, Locale.FRANCE),
        )

        // La vraie réponse, elle, s'écrit.
        session.apply(TurnEvent.TextDelta("J'ai repris les 54 routes : tout revient bien en arrière."), 4_000L)
        assertTrue(session.message.text.startsWith("J'ai repris les 54 routes"))
    }

    @Test
    fun `un echo arrive d'un bloc, sans fragment, est jete aussi`() {
        val question = "Explique-moi les routes de l'application et leurs retours, en détail."
        val session = session(question)

        session.apply(TurnEvent.TextFinal(question), 2_000L)

        assertEquals("", session.message.text)
        assertTrue(session.phase.echoedPrompt)
    }

    @Test
    fun `une reponse courte reste une reponse, meme si elle ressemble au message`() {
        // « oui » est une vraie réponse: on ne jette que l'écho d'un message long.
        val session = session("oui")
        session.apply(TurnEvent.TextDelta("oui"), 2_000L)
        assertEquals("oui", session.message.text)

        // Et une réponse qui commence par le message de l'utilisateur reste une réponse.
        val long = "Explique-moi les routes de l'application et leurs retours, en détail."
        val second = session(long)
        second.apply(TurnEvent.TextDelta(long + " — voici ce que j'ai trouvé."), 3_000L)
        assertTrue(second.message.text.endsWith("voici ce que j'ai trouvé."))
    }

    @Test
    fun `un flux qui n'a produit qu'un statut se dit comme un echec, pas une bulle vide`() {
        val session = session()
        session.apply(TurnEvent.Status("provider indisponible, réessai"), 2_000L)

        session.succeed(3_000L)

        assertFalse(session.phase.isStreaming)
        assertEquals(TurnStep.FAILED, session.phase.step)
        assertNotNull(session.message.error)
        assertNull(TurnStatusCopy.line(session.phase, 4_000L))
    }

    @Test
    fun `un tour qui finit sans rien dire le dit, au lieu d'une bulle vide coiffee du nom du bot`() {
        val session = session()
        session.succeed(2_000L)

        assertFalse(session.phase.isStreaming)
        assertEquals(TurnStep.FAILED, session.phase.step)
        assertEquals(
            "Le serveur n'a rien répondu à ce tour.",
            OnyxLocalization.translate(session.message.error.orEmpty(), Locale.FRANCE),
        )
        assertNull(TurnStatusCopy.line(session.phase, 3_000L))
    }

    @Test
    fun `le tour fini ne dit plus rien et garde sa reponse`() {
        val session = session()
        session.apply(TurnEvent.TextDelta("Terminé."), 2_000L)
        session.succeed(3_000L)

        assertEquals("Terminé.", session.message.text)
        assertFalse(session.message.isStreaming)
        assertFalse(session.phase.isStreaming)
        assertNull(TurnStatusCopy.line(session.phase, 9_000L))
    }

    @Test
    fun `un tour arrete par l'utilisateur est fini, et son texte reste`() {
        val session = session()
        session.apply(TurnEvent.TextDelta("Je commençais à…"), 2_000L)
        session.stop(3_000L)

        assertEquals(TurnStep.STOPPED, session.phase.step)
        assertFalse(session.phase.isStreaming)
        assertEquals("Je commençais à…", session.message.text)
        assertNull(TurnStatusCopy.line(session.phase, 4_000L))
    }

    @Test
    fun `un echec de connexion dit sa raison et son genre`() {
        val session = session()
        session.apply(TurnEvent.TextDelta("Bonjour"), 2_000L)
        session.fail(TransportError.Unreachable("hôte injoignable"), 3_000L)

        assertEquals(TurnStep.FAILED, session.phase.step)
        assertTrue(session.phase.connectivity)
        assertNotNull(session.phase.error)
        assertEquals(Role.ASSISTANT, session.message.role)
    }

    @Test
    fun `l'abandon laisse l'hote finir, l'arret non`() {
        val left = session()
        left.departure.leave()
        assertTrue(left.departure.hasLeft)

        val stopped = session()
        assertFalse(stopped.departure.hasLeft)
    }
}
