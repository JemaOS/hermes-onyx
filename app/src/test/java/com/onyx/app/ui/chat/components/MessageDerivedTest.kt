package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Ce qu'une rangée déduit de son contenu, calculé une fois.**
 *
 * Le défaut visé : quatre analyses parcouraient tout le texte de la réponse à **chaque composition**
 * de la ligne — la recherche d'un diff, son découpage (appelé deux fois), les tâches à cocher et les
 * fichiers livrés. Ce test tient la valeur produite : une fois, complète, et sans double appel.
 */
class MessageDerivedTest {

    private fun message(text: String = "", tools: List<ToolActivity> = emptyList()) = ChatMessage(
        id = "m1",
        role = Role.ASSISTANT,
        text = text,
        createdAt = 0L,
        tools = tools,
    )

    private fun tool(output: String, id: String = "t1") = ToolActivity(
        id = id,
        name = "terminal",
        output = output,
        status = ToolStatus.COMPLETED,
    )

    @Test
    fun `une reponse sans diff sans tache et sans fichier ne produit rien`() {
        val derived = MessageDerived.of(message("Rien à signaler, tout va bien."))

        assertNull(derived.patch)
        assertEquals(0, derived.patchFileCount)
        assertNull(derived.patchSummary)
        assertTrue(derived.todos.isEmpty())
        assertTrue(derived.delivered.isEmpty())
    }

    @Test
    fun `un diff dans une sortie d outil est retenu, avec son compte`() {
        val patch = """
            --- a/app/A.kt
            +++ b/app/A.kt
            @@ -1,2 +1,2 @@
            -val a = 1
            +val a = 2
        """.trimIndent()
        val derived = MessageDerived.of(message(tools = listOf(tool(patch))))

        assertEquals(patch, derived.patch)
        assertEquals(1, derived.patchFileCount)
        assertTrue("le résumé n'est pas vide", !derived.patchSummary.isNullOrBlank())
    }

    @Test
    fun `les fichiers livres sont vus dans les sorties`() {
        val derived = MessageDerived.of(
            message(tools = listOf(tool("Écrit /home/john/rapport.pdf et capture.png"))),
        )

        assertTrue(
            "au moins le pdf",
            derived.delivered.any { it.name.endsWith(".pdf") || it.path.endsWith(".pdf") },
        )
    }

    /**
     * **Une réponse qui s'écrit n'est pas analysée.**
     *
     * `includeText = false` est ce que la rangée passe tant que le tour est vivant : sans lui, le
     * texte — qui change à chaque jeton — était relu pour y chercher un diff, et les clés du
     * `remember` changeaient à chaque publication. La carte du diff doit donc arriver **à la fin**,
     * et pas une seule fois pendant l'écriture ; les sorties d'outils, elles, restent lues.
     */
    @Test
    fun `une reponse en cours n est pas analysee pour un diff`() {
        val diff = """
            --- a/app/A.kt
            +++ b/app/A.kt
            @@ -1,2 +1,2 @@
            -val a = 1
            +val a = 2
        """.trimIndent()
        val reply = message(text = diff)

        assertNull("rien tant que la réponse s'écrit", MessageDerived.of(reply, includeText = false).patch)
        assertEquals("la carte arrive à la fin", diff, MessageDerived.of(reply, includeText = true).patch)
    }

    /** Et les cartes des **outils** restent calculées pendant que la réponse s'écrit. */
    @Test
    fun `les sorties d outils restent analysees pendant l ecriture`() {
        val diff = """
            --- a/app/A.kt
            +++ b/app/A.kt
            @@ -1,2 +1,2 @@
            -val a = 1
            +val a = 2
        """.trimIndent()
        val derived = MessageDerived.of(message(text = "en cours…", tools = listOf(tool(diff))), includeText = false)

        assertEquals(diff, derived.patch)
        assertEquals(1, derived.patchFileCount)
    }
}
