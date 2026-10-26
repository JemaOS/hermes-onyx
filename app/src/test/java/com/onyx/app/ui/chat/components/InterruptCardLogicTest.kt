package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.ClarifyQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptCardLogicTest {
    private val questions = listOf(
        ClarifyQuestion(id = "a", question = "First", choices = listOf("Zulu", "Alpha"), multiSelect = true),
        ClarifyQuestion(id = "b", question = "Second"),
    )

    @Test
    fun `la reponse libre nettoyee prime sur les choix`() {
        val answer = clarifyAnswer(
            question = questions[0],
            picked = setOf("Zulu", "Alpha"),
            typed = "  Une autre réponse\n",
        )

        assertEquals("Une autre réponse", answer)
    }

    @Test
    fun `les choix multiples sont joints dans l ordre alphabetique`() {
        val answer = clarifyAnswer(question = questions[0], picked = setOf("Zulu", "Alpha"), typed = "")

        assertEquals("Alpha, Zulu", answer)
    }

    @Test
    fun `toutes les questions doivent avoir une reponse avant envoi`() {
        assertFalse(clarifyAllAnswered(questions, picked = mapOf("a" to setOf("Alpha")), freeText = emptyMap()))
        assertTrue(
            clarifyAllAnswered(
                questions,
                picked = mapOf("a" to setOf("Alpha")),
                freeText = mapOf("b" to "  réponse  "),
            ),
        )
    }
}
