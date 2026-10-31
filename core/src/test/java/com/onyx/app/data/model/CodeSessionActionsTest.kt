package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les actions d'une fiche de code, **pures** : renommer, modifier, relancer.
 *
 * Ce sont elles que l'écran appelle avant d'écrire sur le téléphone. Les règles à tenir : le
 * renommage ne touche **que** le titre (la branche est celle du dépôt), un champ vide ne remplace
 * rien, et une **relance** est une session **neuve** — jamais la même fiche réécrite, jamais la
 * même conversation Hermes.
 */
class CodeSessionActionsTest {

    private fun record(
        id: String = "conv-1",
        status: CodeStatus = CodeStatus.READY,
    ) = CodeSession(
        id = id,
        title = "Bonjour Onyx",
        branch = "code/bonjour-onyx",
        repoPath = "/home/john/Projects/john",
        worktreePath = "/home/john/Projects/john/.worktrees/bonjour",
        createdAtMillis = 1_700_000_000_000L,
        status = status,
        statusAtMillis = 1_700_000_050_000L,
        sessionKey = "20261027_abc",
        prompt = "Réponds uniquement par le mot bonjour",
    )

    // MARK: - Renommer

    @Test
    fun `renommer ne change que le titre`() {
        val renamed = record().renamed("Ma tâche à moi")
        assertEquals("Ma tâche à moi", renamed.title)
        // Le reste est intact : même identifiant, même branche, même conversation, même demande.
        assertEquals("conv-1", renamed.id)
        assertEquals("code/bonjour-onyx", renamed.branch)
        assertEquals("/home/john/Projects/john", renamed.repoPath)
        assertEquals("20261027_abc", renamed.sessionKey)
        assertEquals("Réponds uniquement par le mot bonjour", renamed.prompt)
        assertEquals(CodeStatus.READY, renamed.status)
        assertEquals(record(), renamed.copy(title = "Bonjour Onyx"))
    }

    @Test
    fun `un titre vide ou fait d espaces ne remplace rien`() {
        assertEquals("Bonjour Onyx", record().renamed("").title)
        assertEquals("Bonjour Onyx", record().renamed("   ").title)
        // Et le titre est nettoyé de ses espaces de bord, sans être coupé.
        assertEquals("Autre tâche", record().renamed("  Autre tâche  ").title)
    }

    // MARK: - Modifier

    @Test
    fun `modifier applique le titre, la branche, le depot, la copie et la demande`() {
        val edited = record().edited(
            title = "Autre chose",
            branch = "code/autre-chose",
            prompt = "Corrige la page de connexion",
            repoPath = "/home/john/Projects/autre",
            worktreePath = "/home/john/Projects/autre/.worktrees/x",
        )
        assertEquals("Autre chose", edited.title)
        assertEquals("code/autre-chose", edited.branch)
        assertEquals("Corrige la page de connexion", edited.prompt)
        assertEquals("/home/john/Projects/autre", edited.repoPath)
        assertEquals("/home/john/Projects/autre/.worktrees/x", edited.worktreePath)
        // L'identité de la fiche ne bouge pas : c'est la même session, réécrite.
        assertEquals("conv-1", edited.id)
        assertEquals("20261027_abc", edited.sessionKey)
    }

    @Test
    fun `un champ laisse vide garde sa valeur precedente`() {
        val edited = record().edited(title = "", branch = "   ")
        assertEquals("Bonjour Onyx", edited.title)
        assertEquals("code/bonjour-onyx", edited.branch)
        // La demande, elle, se laisse effacer : elle n'est alors plus gardée du tout.
        assertNull(record().edited(prompt = "").prompt)
        assertNull(record().edited(prompt = "   ").prompt)
    }

    @Test
    fun `la copie de travail peut etre retiree`() {
        // « Aucune pour le moment » : la fiche travaille alors dans le dépôt lui-même.
        val edited = record().edited(worktreePath = null)
        assertNull(edited.worktreePath)
        assertEquals("/home/john/Projects/john", edited.repoPath)
    }

    // MARK: - Relancer

    @Test
    fun `relancer ouvre une session neuve, pas la meme fiche`() {
        val relaunched = record(status = CodeStatus.READY).relaunched("conv-2", 1_700_000_999_000L)

        // L'identifiant est celui de la **nouvelle** conversation : la précédente n'est pas reprise.
        assertEquals("conv-2", relaunched.id)
        assertNotEquals(record().id, relaunched.id)
        // Elle repart en cours, horodatée maintenant, sans clé de session : le tour réel la donnera.
        assertEquals(CodeStatus.RUNNING, relaunched.status)
        assertEquals(1_700_000_999_000L, relaunched.createdAtMillis)
        assertEquals(1_700_000_999_000L, relaunched.statusAtMillis)
        assertNull(relaunched.sessionKey)
        assertTrue(relaunched.local)
        // Et elle garde tout ce qui décrit le travail : dépôt, copie, branche, demande.
        assertEquals(record().repoPath, relaunched.repoPath)
        assertEquals(record().worktreePath, relaunched.worktreePath)
        assertEquals(record().branch, relaunched.branch)
        assertEquals(record().prompt, relaunched.prompt)
    }

    @Test
    fun `la fiche d origine reste intacte apres une relance`() {
        val original = record(status = CodeStatus.READY)
        val relaunched = original.relaunched("conv-2", 1_700_000_999_000L)
        // Deux fiches différentes : l'originale n'a pas été réécrite par la relance.
        assertNotEquals(original.id, relaunched.id)
        assertEquals(CodeStatus.READY, original.status)
        assertEquals(1_700_000_050_000L, original.statusAtMillis)
    }
}
