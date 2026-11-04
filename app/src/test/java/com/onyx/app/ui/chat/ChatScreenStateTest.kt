package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **L'écran du fil ne recompose pas pendant un flux.**
 *
 * `ChatScreen` lisait l'état du chat **entier** : le corps de l'écran fait 2 200 lignes (barre du
 * haut, pageur d'onglets, compositeur, menus, feuilles), et chaque publication de texte — 33 par
 * seconde pendant une réponse — le rejouait d'un bout à l'autre. Le texte vivant n'est pourtant lu
 * que par **une** ligne : celle qui s'écrit.
 *
 * La projection [ChatScreenState] est la serrure : elle ne porte que ce qui ne bouge **pas** d'un
 * jeton à l'autre, et son égalité structurelle tient pendant tout le flux. Ces tests sont la
 * preuve : si quelqu'un remet un champ vivant (le texte, la longueur de la réponse) dans la
 * projection, le premier test échoue.
 */
class ChatScreenStateTest {

    private val server = ServerConfig(id = "srv", name = "Onyx", baseUrl = "http://host:8080", model = "m")

    private fun message(
        id: String,
        role: Role,
        text: String,
        streaming: Boolean = false,
    ) = ChatMessage(
        id = id,
        role = role,
        createdAt = 0L,
        text = text,
        isStreaming = streaming,
    )

    private fun state(vararg messages: ChatMessage, isStreaming: Boolean = false) = ChatUiState(
        server = server,
        conversationId = "c1",
        messages = messages.toList(),
        isStreaming = isStreaming,
    )

    @Test
    fun `une reponse qui grandit ne change pas la projection de l ecran`() {
        val question = message("m1", Role.USER, "Explique le cache")
        val before = ChatScreenState.of(
            state(question, message("m2", Role.ASSISTANT, "", streaming = true), isStreaming = true),
        )
        val after = ChatScreenState.of(
            state(question, message("m2", Role.ASSISTANT, "Le cache garde ", streaming = true), isStreaming = true),
        )
        val later = ChatScreenState.of(
            state(
                question,
                message("m2", Role.ASSISTANT, "Le cache garde 128 entrées et ne relit rien.", streaming = true),
                isStreaming = true,
            ),
        )

        assertEquals("un jeton ne doit pas faire recomposer l'écran", before, after)
        assertEquals("un paragraphe non plus", before, later)
        assertEquals(before.hashCode(), later.hashCode())
    }

    @Test
    fun `la fin du tour ou un message neuf changent la projection`() {
        val question = message("m1", Role.USER, "Explique le cache")
        val live = message("m2", Role.ASSISTANT, "Le cache", streaming = true)
        val streaming = ChatScreenState.of(state(question, live, isStreaming = true))

        assertNotEquals(
            "la fin du tour est un vrai changement d'écran",
            streaming,
            ChatScreenState.of(state(question, live)),
        )
        assertNotEquals(
            "un message neuf est un vrai changement d'écran",
            streaming,
            ChatScreenState.of(state(question, live, message("m3", Role.USER, "Et ensuite ?"))),
        )
    }

    @Test
    fun `la projection porte la forme du fil, jamais son texte`() {
        val question = message("m1", Role.USER, "Première question")
        val answer = message("m2", Role.ASSISTANT, "Réponse finie")
        val live = message("m3", Role.ASSISTANT, "En cours", streaming = true)
        val screen = ChatScreenState.of(state(question, answer, live, isStreaming = true))

        assertEquals(3, screen.messagesSize)
        assertEquals("Première question", screen.firstUserQuestion)
        assertEquals("c1", screen.conversationId)
        assertTrue(screen.isStreaming)
        // Les messages **finis** : le Flux et les Fichiers s'arrêtent là.
        assertEquals(2, screen.settledCount)
        assertEquals("m2", screen.settledLastId)
        // Le nombre de tours de la personne : c'est lui qui ramène la vue en bas.
        assertEquals(1, screen.userTurnCount)
        assertTrue(screen.hasUserTurn)
    }

    @Test
    fun `la projection de l ecran ne porte aucun texte de reponse`() {
        val live = message("m2", Role.ASSISTANT, "Le cache garde 128 entrées", streaming = true)
        val screen = ChatScreenState.of(state(live, isStreaming = true))

        // Le texte de la réponse qui s'écrit ne doit pas être dans la projection : c'est lui qui
        // change à chaque jeton, et c'est lui qui ferait recomposer l'écran 33 fois par seconde.
        val fields = screen::class.java.declaredFields.map { it.name }
        for (name in fields) {
            val value = screen::class.java.getDeclaredField(name).apply { isAccessible = true }.get(screen)
            if (value is String) {
                assertNotEquals(
                    "la projection porte un texte de réponse ($name) : l'écran recomposera à chaque jeton",
                    "Le cache garde 128 entrées",
                    value,
                )
            }
        }
    }
}
