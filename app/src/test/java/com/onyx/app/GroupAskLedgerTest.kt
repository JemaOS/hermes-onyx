package com.onyx.app

import com.onyx.app.ui.bots.GroupAskDecision
import com.onyx.app.ui.bots.GroupAskLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Une question reconnue n'entre qu'une fois.**
 *
 * Le défaut, tel que les bots l'ont décrit : « Tes messages “vous êtes là” reviennent en boucle ».
 * Le moteur de reconnaissance ne rend pas une phrase, il rend des **finales** : la transcription
 * grandit (« vous êtes là » → « vous êtes là ? » → « Vous êtes là, les gars ? »), et chaque variante
 * passait la garde d'égalité de la session. Trois messages identiques partaient donc dans la salle,
 * et les bots répondaient trois fois.
 */
class GroupAskLedgerTest {

    private var counter = 0

    private fun ledger() = GroupAskLedger(newId = { "ask-${++counter}" })

    @Test
    fun `une transcription qui grandit n envoie qu une fois`() {
        val ledger = ledger()
        val start = 1_000_000L

        val first = ledger.admit("vous êtes là", start)
        assertTrue(first is GroupAskDecision.Send)

        // Le moteur reprend le fil 400 ms plus tard : la même question, un mot de plus.
        val grown = ledger.admit("vous êtes là ?", start + 400) as GroupAskDecision.Again
        val grownAgain = ledger.admit("Vous êtes là, les gars ?", start + 900) as GroupAskDecision.Again

        assertEquals("une seule entrée dans la salle", 1, listOf(first, grown, grownAgain).count {
            it is GroupAskDecision.Send
        })
        assertEquals("la même question garde son identifiant", "ask-1", grown.id)
        assertEquals("ask-1", grownAgain.id)
        // La ponctuation tombe à la normalisation : « vous êtes là ? » est la même phrase. C'est
        // la troisième, plus longue, que seule la règle « la transcription a grandi » retient.
        assertEquals("la même phrase revient", grown.why)
        assertTrue("le journal dit pourquoi", grownAgain.why.contains("grandi"))
    }

    @Test
    fun `la meme phrase qui revient ne repart pas`() {
        val ledger = ledger()
        val start = 5_000_000L

        assertTrue(ledger.admit("Quel temps fait-il ?", start) is GroupAskDecision.Send)
        // À l'identique, ponctuation et casse comprises — un rejeu du moteur.
        val again = ledger.admit("quel temps fait-il", start + 1_200)
        assertTrue(again is GroupAskDecision.Again)
        assertEquals("ask-1", (again as GroupAskDecision.Again).id)
    }

    @Test
    fun `une transcription quasi identique ne repart pas`() {
        val ledger = ledger()
        val start = 9_000_000L

        assertTrue(ledger.admit("Est-ce que le sentier est ouvert demain matin", start) is GroupAskDecision.Send)
        // Le moteur a reconnu la même phrase autrement (un mot avalé).
        val again = ledger.admit("est ce que le sentier est ouvert demain", start + 2_000)
        assertTrue("une phrase quasi identique est la même question", again is GroupAskDecision.Again)
    }

    @Test
    fun `deux questions qui ne changent qu un mot sont deux questions`() {
        val ledger = ledger()
        val start = 30_000_000L

        // « Tour 1 » et « Tour 2 » ne different que d'un mot : ce sont bien deux questions.
        assertTrue(ledger.admit("Tour 1 : quel temps fera-t-il demain à Lyon ?", start) is GroupAskDecision.Send)
        assertTrue(
            ledger.admit("Tour 2 : quel temps fera-t-il demain à Lyon ?", start + 2_000) is GroupAskDecision.Send,
        )
        // Et « demain » → « mardi » change la question aussi.
        assertTrue(
            ledger.admit("Tour 2 : quel temps fera-t-il mardi à Lyon ?", start + 4_000) is GroupAskDecision.Send,
        )
    }

    @Test
    fun `deux questions differentes partent toutes les deux`() {
        val ledger = ledger()
        val start = 12_000_000L

        val first = ledger.admit("Quel temps fait-il demain à Lyon ?", start)
        val second = ledger.admit("Et à Paris, mardi matin ?", start + 3_000)

        assertTrue(first is GroupAskDecision.Send)
        assertTrue(second is GroupAskDecision.Send)
        assertNotEquals((first as GroupAskDecision.Send).id, (second as GroupAskDecision.Send).id)
    }

    @Test
    fun `la meme phrase redite plus tard est une nouvelle question`() {
        val ledger = ledger()
        val start = 15_000_000L

        assertTrue(ledger.admit("vous êtes là", start) is GroupAskDecision.Send)
        // Bien après la fenêtre : la personne repose vraiment la question.
        val later = ledger.admit(
            "vous êtes là",
            start + GroupAskLedger.ASK_REPEAT_WINDOW_MS + 1,
        )
        assertTrue("une question reposée plus tard repart", later is GroupAskDecision.Send)
    }

    @Test
    fun `un silence ne pose rien`() {
        val ledger = ledger()
        assertTrue(ledger.admit("   ", 1L) is GroupAskDecision.Blank)
        assertTrue(ledger.admit("...", 2L) is GroupAskDecision.Blank)
    }

    @Test
    fun `vingt questions distinctes font vingt envois, jamais vingt et un`() {
        val ledger = ledger()
        val start = 20_000_000L
        val ids = mutableListOf<String>()
        for (turn in 1..20) {
            // La question de la personne, telle que le moteur la rend deux fois (finale rejouée).
            val text = "Question numéro $turn sur la randonnée"
            val once = ledger.admit(text, start + turn * 1_000L)
            val replay = ledger.admit(text, start + turn * 1_000L + 300L)
            assertTrue("tour $turn : la question part", once is GroupAskDecision.Send)
            assertTrue("tour $turn : le rejeu ne repart pas", replay is GroupAskDecision.Again)
            ids += (once as GroupAskDecision.Send).id
        }
        assertEquals("vingt tours, vingt envois", 20, ids.size)
        assertEquals("et vingt identifiants distincts", 20, ids.toSet().size)
    }
}
