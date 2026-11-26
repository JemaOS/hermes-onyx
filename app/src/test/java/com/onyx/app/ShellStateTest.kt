package com.onyx.app

import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.ui.ShellState
import com.onyx.app.ui.chat.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La coquille ne recompose pas pendant un flux.**
 *
 * `OnyxApp()` lisait l'état du chat **entier** : chaque publication de texte — trente-trois par
 * seconde pendant une réponse — remplaçait l'état, donc la racine rejouait, et avec elle trois
 * balayages complets du fil. La projection [ShellState] existe pour que l'égalité tienne : ces
 * tests sont la serrure. Si quelqu'un remet un champ vivant (le texte, le dernier message, l'état
 * du tour jeton par jeton) dans la projection, le deuxième test échoue.
 */
class ShellStateTest {

    private val server = ServerConfig(id = "srv", name = "Onyx", baseUrl = "http://host:8080", model = "m")

    private fun message(
        id: String,
        role: Role,
        text: String,
        streaming: Boolean = false,
        approvals: List<ApprovalRequest> = emptyList(),
    ) = ChatMessage(
        id = id,
        role = role,
        createdAt = 0L,
        text = text,
        isStreaming = streaming,
        approvals = approvals,
    )

    private fun state(vararg messages: ChatMessage, isStreaming: Boolean = false) = ChatUiState(
        server = server,
        conversationId = "c1",
        messages = messages.toList(),
        isStreaming = isStreaming,
    )

    @Test
    fun `une reponse qui grandit ne change pas la projection de la coquille`() {
        val question = message("m1", Role.USER, "Explique le cache")
        val before = ShellState.of(state(question, message("m2", Role.ASSISTANT, "", streaming = true), isStreaming = true))
        val after = ShellState.of(
            state(question, message("m2", Role.ASSISTANT, "Le cache garde ", streaming = true), isStreaming = true),
            )
        val later = ShellState.of(
            state(
                question,
                message("m2", Role.ASSISTANT, "Le cache garde 128 entrées et ne relit rien.", streaming = true),
                isStreaming = true,
            ),
        )

        assertEquals("un jeton ne doit pas faire recomposer la racine", before, after)
        assertEquals("un paragraphe non plus", before, later)
        assertEquals(before.hashCode(), later.hashCode())
    }

    @Test
    fun `la fin du tour, une approbation ou un message neuf changent la projection`() {
        val question = message("m1", Role.USER, "Explique le cache")
        val live = message("m2", Role.ASSISTANT, "Le cache", streaming = true)
        val streaming = ShellState.of(state(question, live, isStreaming = true))

        assertNotEquals("la fin du tour est un vrai changement", streaming, ShellState.of(state(question, live)))

        val approval = ApprovalRequest(id = "a1", command = "rm -rf /tmp/x", description = "Exécuter")
        val withApproval = ShellState.of(
            state(question, message("m2", Role.ASSISTANT, "Le cache", approvals = listOf(approval))),
        )
        assertEquals(1, withApproval.pendingApprovals.size)
        assertNotEquals(streaming, withApproval)

        assertNotEquals(streaming, ShellState.of(state(question, live, message("m3", Role.USER, "Et ensuite ?"))))
    }

    @Test
    fun `la projection porte ce que la coquille lit, et rien de plus fin`() {
        val question = message("m1", Role.USER, "Première question")
        val answer = message("m2", Role.ASSISTANT, "Réponse finie")
        val live = message("m3", Role.ASSISTANT, "En cours", streaming = true)
        val shell = ShellState.of(state(question, answer, live, isStreaming = true))

        assertEquals(3, shell.messagesSize)
        assertEquals("Première question", shell.firstUserQuestion)
        assertEquals("c1", shell.conversationId)
        assertTrue(shell.isStreaming)
        // La voix ne reçoit que les tours **finis** : celui qui s'écrit n'y entre pas.
        assertEquals(1, shell.voiceTurns.size)
        assertEquals("Première question", shell.voiceTurns[0].question)
        assertEquals("Réponse finie", shell.voiceTurns[0].answer)
    }
}
