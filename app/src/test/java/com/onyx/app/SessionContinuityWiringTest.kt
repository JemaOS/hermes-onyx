package com.onyx.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La reprise de session est branchée des deux bouts.**
 *
 * Le client sait envoyer `X-Hermes-Session-Id` (`SessionContinuityTest`, core) et le fil sait
 * reprendre l'identifiant que le serveur renvoie (`TurnEvent.SessionId`). Ce qui reste invisible
 * en test unitaire, c'est le **câblage** : un tour ne part avec sa session que si
 * `ChatViewModel` la passe, et seulement sur le transport Hermes — un serveur
 * OpenAI-compatible quelconque n'a pas cette en-tête.
 */
class SessionContinuityWiringTest {

    private fun read(path: String) = File(path).readText()

    private val viewModel = read("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt")
    private val turnSession = read("src/main/java/com/onyx/app/ui/chat/TurnSession.kt")

    @Test
    fun `un tour repart avec la session connue, et seulement sur le transport Hermes`() {
        assertTrue(
            "le tour doit porter l'identifiant de la conversation",
            viewModel.contains("sessionId = serverSessionId"),
        )
        assertTrue(
            "l'en-tête ne part que vers un gateway Hermes",
            viewModel.contains("takeIf { server.transport == Transport.HERMES_SESSIONS }"),
        )
        assertTrue(
            "un identifiant vide ne compte pas comme une session",
            viewModel.contains("takeIf { it.isNotBlank() }"),
        )
    }

    @Test
    fun `le fil reprend l identifiant que le serveur declare en vigueur`() {
        assertTrue(
            "une compression change l'id en cours de route : le flux doit le reprendre",
            turnSession.contains("is TurnEvent.SessionId -> _live.update { it.copy(serverSessionId = event.id) }"),
        )
    }
}