package com.onyx.app

import com.onyx.app.notifications.NotificationCopy
import com.onyx.app.notifications.NotificationCopy.QuestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le texte des notifications : les états d'une approbation, les trois questions de l'agent, et la
 * phrase d'un tour en cours avec l'outil et la durée. Ce sont les mêmes mots que la carte de
 * conversation — la notification ne réinvente rien.
 */
class NotificationCopyTest {

    @Test
    fun `une approbation repondue dit ce qui a ete decide`() {
        assertEquals("Approved", NotificationCopy.resolvedTitle(approved = true))
        assertEquals("Denied", NotificationCopy.resolvedTitle(approved = false))
    }

    @Test
    fun `une reponse qui n'a pas pu partir garde la marche a suivre`() {
        assertEquals("Open Onyx to answer from the conversation.", NotificationCopy.answeredText("ls", failed = true))
        assertEquals("ls", NotificationCopy.answeredText("ls", failed = false))
    }

    @Test
    fun `chaque question de l'agent a son titre`() {
        assertEquals("Onyx has a question", NotificationCopy.questionTitle(QuestionKind.CLARIFY))
        assertEquals("A password is needed", NotificationCopy.questionTitle(QuestionKind.SUDO))
        assertEquals("A secret is needed", NotificationCopy.questionTitle(QuestionKind.SECRET))
    }

    @Test
    fun `une question dans le fil d'un bot nomme le bot`() {
        // Le fil d'un bot : la notification dit son nom, pas celui du fil par défaut.
        assertEquals("writer has a question", NotificationCopy.questionTitle(QuestionKind.CLARIFY, "writer"))
        // Un fil simple (nom d'agent vide ou « Onyx ») garde le titre de Onyx.
        assertEquals("Onyx has a question", NotificationCopy.questionTitle(QuestionKind.CLARIFY, "Onyx"))
        assertEquals("Onyx has a question", NotificationCopy.questionTitle(QuestionKind.CLARIFY, "  "))
    }

    @Test
    fun `une reponse en ligne depend de la sorte de question`() {
        // Une question seule se répond dans la notification.
        assertTrue(NotificationCopy.canAnswerInline(QuestionKind.CLARIFY, hasSession = false, questionCount = 1))
        // Une série ne tient pas dans un seul champ : on répond depuis l'app.
        assertFalse(NotificationCopy.canAnswerInline(QuestionKind.CLARIFY, hasSession = false, questionCount = 3))
        // sudo et secret s'adressent à la session ouverte : sans elle, pas de réponse en ligne.
        assertFalse(NotificationCopy.canAnswerInline(QuestionKind.SUDO, hasSession = false, questionCount = 1))
        assertTrue(NotificationCopy.canAnswerInline(QuestionKind.SUDO, hasSession = true, questionCount = 1))
        assertFalse(NotificationCopy.canAnswerInline(QuestionKind.SECRET, hasSession = false, questionCount = 1))
        assertTrue(NotificationCopy.canAnswerInline(QuestionKind.SECRET, hasSession = true, questionCount = 1))
    }

    @Test
    fun `la phrase du tour cite l'outil en cours et la duree`() {
        assertEquals("Answering…", NotificationCopy.turnPhase(prefillPercent = null, toolName = null, elapsedSeconds = 0))
        assertEquals("42% read", NotificationCopy.turnPhase(prefillPercent = 42, toolName = null, elapsedSeconds = null))
        assertEquals(
            "Running bash · 12s",
            NotificationCopy.turnPhase(prefillPercent = null, toolName = "bash", elapsedSeconds = 12),
        )
        // L'outil prime sur le préremplissage : c'est ce qui se passe à l'écran.
        assertEquals(
            "Running read_file · 1m 05s",
            NotificationCopy.turnPhase(prefillPercent = 90, toolName = "read_file", elapsedSeconds = 65),
        )
    }

    @Test
    fun `la duree reste courte et lisible`() {
        assertEquals("9s", NotificationCopy.elapsed(9))
        assertEquals("59s", NotificationCopy.elapsed(59))
        assertEquals("1m 00s", NotificationCopy.elapsed(60))
        assertEquals("1m 05s", NotificationCopy.elapsed(65))
        assertEquals("1h 02m", NotificationCopy.elapsed(3_720))
        assertEquals("0s", NotificationCopy.elapsed(-3))
    }

    @Test
    fun `la fin du travail dit son resultat en un mot`() {
        assertEquals("Finished", NotificationCopy.finishedTitle(failed = false))
        assertEquals("Failed", NotificationCopy.finishedTitle(failed = true))
    }

    @Test
    fun `l'extrait garde de quoi reconnaitre la reponse, sur une seule ligne`() {
        assertEquals("54 routes, toutes cohérentes.", NotificationCopy.excerpt("  54 routes,\n toutes cohérentes.  "))
        // Un paragraphe ne déroule pas la notification : il est coupé, et jamais en trois lignes.
        val long = "a ".repeat(400)
        val excerpt = NotificationCopy.excerpt(long)
        assertTrue("jamais plus long que la limite", excerpt.length <= NotificationCopy.EXCERPT_CHARS)
        assertTrue("l'extrait n'est pas vide", excerpt.isNotEmpty())
        assertFalse(excerpt.contains("\n"))
    }
}
