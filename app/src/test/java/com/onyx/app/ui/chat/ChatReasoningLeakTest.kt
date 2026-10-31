package com.onyx.app.ui.chat

import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.TurnEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LE VERROU DE FUITE, sur les DEUX surfaces du fil.**
 *
 * Le même défaut se lisait deux fois chez le propriétaire : dans le **fil** (la ligne d'erreur du
 * message) et dans le bloc **« Raisonnement du modèle »** — les deux lisent le même `ChatMessage`.
 * Aucun fragment brut (JSON, chemin interne `/home/`, `.hermes`, `native-inputs`, commande) ne doit
 * atteindre le texte visible, ni par l'un ni par l'autre.
 *
 * Échoue sur la tête avant correction (le raisonnement et l'erreur portaient le fragment) et passe
 * après.
 */
class ChatReasoningLeakTest {

    private val fragment =
        "{\"execution_generation\": 1, \"command\": \"sleep 3; cd /home/john/.hermes/cache/documents/" +
            "native-inputs/50829f630e53a20d850ebe358d573017e616cf1d4ff6a6b09261ae1b915c8c67/ && " +
            "python3 -c \\\"from PIL import Image\\\"\"}"

    private fun leakMarkers(text: String): List<String> =
        listOf("/home/", ".hermes", "native-inputs", "{", "execution_generation", "sleep 3", "&&", "python3")
            .filter { text.contains(it) }

    private fun session(): TurnSession =
        TurnSession(conversationId = "c", assistantId = "a", userText = "salut", startedAt = 1L)

    @Test
    fun `a fragment on the reasoning channel never reaches the visible reasoning`() {
        val session = session()
        session.apply(TurnEvent.ReasoningDelta(fragment), now = 2L)
        val seen = session.message.reasoning
        assertTrue("le raisonnement montre le fragment brut : ${leakMarkers(seen)}", leakMarkers(seen).isEmpty())
    }

    @Test
    fun `a genuine reasoning fragment is still shown`() {
        val session = session()
        session.apply(TurnEvent.ReasoningDelta("Je réfléchis au problème posé par la question."), now = 2L)
        assertTrue(session.message.reasoning.isNotEmpty())
    }

    @Test
    fun `the error line never shows raw json or an internal path`() {
        val session = session()
        session.fail(TransportError.Malformed("unreadable chunk: $fragment"), now = 2L)
        val line = session.message.error.orEmpty()
        assertTrue("la ligne d'erreur fuit : ${leakMarkers(line)}", leakMarkers(line).isEmpty())
    }
}
