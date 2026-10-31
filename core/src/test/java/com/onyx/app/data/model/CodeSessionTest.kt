package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les règles pures de l'écran Code : la branche dérivée du titre, les compteurs des filtres, ce que
 * chaque filtre retient, et le temps écoulé.
 */
class CodeSessionTest {

    private fun session(
        id: String,
        status: CodeStatus,
        created: Long = 1_000L,
    ) = CodeSession(
        id = id,
        title = "tâche $id",
        branch = "code/tache-$id",
        createdAtMillis = created,
        status = status,
        statusAtMillis = created,
    )

    @Test
    fun `la branche derive du titre en slug`() {
        assertEquals("code/fix-the-login-page", codeBranch("Fix the login page"))
        // Les accents tombent, la ponctuation devient des tirets.
        assertEquals("code/refaire-l-ecran-code", codeBranch("Refaire l'écran « Code »"))
        assertEquals("code/session", codeBranch("   "))
        assertEquals("code/session", codeBranch("!!!"))
    }

    @Test
    fun `les compteurs suivent les etats reellement observes`() {
        val sessions = listOf(
            session("a", CodeStatus.READY),
            session("b", CodeStatus.READY),
            session("c", CodeStatus.RUNNING),
            session("d", CodeStatus.NEEDS_YOU),
        )
        val counts = codeFilterCounts(sessions)
        assertEquals(4, counts[CodeFilter.ALL])
        assertEquals(1, counts[CodeFilter.RUNNING])
        assertEquals(1, counts[CodeFilter.NEEDS_YOU])
        assertEquals(2, counts[CodeFilter.READY])
    }

    @Test
    fun `un echec reste visible dans Tout, sans filtre a lui`() {
        val sessions = listOf(session("a", CodeStatus.FAILED), session("b", CodeStatus.READY))
        val counts = codeFilterCounts(sessions)
        assertEquals(2, counts[CodeFilter.ALL])
        assertEquals(0, counts[CodeFilter.RUNNING])
        assertEquals(0, counts[CodeFilter.NEEDS_YOU])
        assertEquals(1, counts[CodeFilter.READY])
        assertTrue(filteredCodeSessions(sessions, CodeFilter.ALL).any { it.id == "a" })
    }

    @Test
    fun `le filtre change la liste, la plus recente d'abord`() {
        val sessions = listOf(
            session("vieux", CodeStatus.READY, created = 1_000L),
            session("neuf", CodeStatus.READY, created = 9_000L),
            session("encours", CodeStatus.RUNNING, created = 5_000L),
        )
        assertEquals(
            listOf("neuf", "vieux"),
            filteredCodeSessions(sessions, CodeFilter.READY).map { it.id },
        )
        assertEquals(listOf("encours"), filteredCodeSessions(sessions, CodeFilter.RUNNING).map { it.id })
        assertEquals(emptyList<String>(), filteredCodeSessions(sessions, CodeFilter.NEEDS_YOU).map { it.id })
        assertEquals(3, filteredCodeSessions(sessions, CodeFilter.ALL).size)
    }

    @Test
    fun `le libelle d un filtre est traduit puis compte`() {
        assertEquals("Tout 2", codeFilterLabel(CodeFilter.ALL, 2, java.util.Locale.FRANCE))
        assertEquals("En cours 0", codeFilterLabel(CodeFilter.RUNNING, 0, java.util.Locale.FRANCE))
        assertEquals("À toi 1", codeFilterLabel(CodeFilter.NEEDS_YOU, 1, java.util.Locale.FRANCE))
        assertEquals("Prêt 3", codeFilterLabel(CodeFilter.READY, 3, java.util.Locale.FRANCE))
        // Le libellé ne doit jamais retomber sur la tournure « Running … » d'un autre écran.
        assertEquals("En cours 0", codeFilterLabel(CodeFilter.RUNNING, 0, java.util.Locale.FRANCE))
    }

    @Test
    fun `le temps ecoule par unite courte`() {
        assertEquals("0 s", codeElapsed(0L, 0L))
        assertEquals("3 s", codeElapsed(0L, 3_000L))
        assertEquals("2 min", codeElapsed(0L, 90_000L))
        assertEquals("1 h", codeElapsed(0L, 3_600_000L))
        assertEquals("4 j", codeElapsed(0L, 4L * 86_400_000L))
        // Un horodatage dans le futur ne compte jamais à rebours sous zéro.
        assertEquals("0 s", codeElapsed(5_000L, 1_000L))
    }
}
