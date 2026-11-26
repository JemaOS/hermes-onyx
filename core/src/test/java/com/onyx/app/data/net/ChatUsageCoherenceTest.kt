package com.onyx.app.data.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La fiche ne se contredit pas.** Le propriétaire a lu, dans la même fiche, « Entrée 0 · Sortie 0 »
 * sous « Contexte utilisé 21,3k / 256,0k · 8 % » : des compteurs à zéro à côté d'un contexte rempli,
 * sans un mot. C'est cette contradiction que [ChatUsage.reportsContextWithoutTokens] nomme — elle
 * déclenche la phrase qui **dit** les zéros au lieu de les laisser se contredire.
 *
 * Les chiffres tapés sont ceux de sa capture, relus dans le même ordre que le tableau de la fiche.
 */
class ChatUsageCoherenceTest {

    /** Le cas mesuré : le serveur a mesuré un contexte, mais n'a compté aucun jeton. */
    private val captureProprietaire = ChatUsage(
        model = "sage-a1.5",
        inputTokens = 0,
        outputTokens = 0,
        apiCalls = 0,
        contextUsed = 21_300,
        contextMax = 256_000,
        contextPercent = 8,
    )

    @Test
    fun `les zeros a cote d un contexte rempli se disent`() {
        assertTrue(
            "des jetons à 0 sous un contexte à 21,3k : la fiche doit le dire",
            captureProprietaire.countsNoTokens,
        )
        assertTrue(captureProprietaire.reportsContextWithoutTokens)
    }

    @Test
    fun `des compteurs absents sous un contexte rempli se disent aussi`() {
        // Le serveur a répondu (un contexte), mais n'a nommé aucun compteur de jetons : « — » partout.
        val sansCompteurs = captureProprietaire.copy(inputTokens = null, outputTokens = null, apiCalls = null)
        assertTrue(sansCompteurs.reportsContextWithoutTokens)
    }

    @Test
    fun `des jetons reels ne declenchent pas la phrase`() {
        val reels = captureProprietaire.copy(inputTokens = 1_200, outputTokens = 300)
        assertFalse("le serveur a compté : rien à expliquer", reels.countsNoTokens)
        assertFalse(reels.reportsContextWithoutTokens)
    }

    @Test
    fun `sans contexte mesure, il n y a pas de contradiction a dire`() {
        val sansContexte = ChatUsage(inputTokens = 0, outputTokens = 0, apiCalls = 0)
        assertFalse(sansContexte.reportsContextWithoutTokens)
    }
}
