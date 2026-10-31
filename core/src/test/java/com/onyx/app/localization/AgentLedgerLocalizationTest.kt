package com.onyx.app.localization

import com.onyx.app.data.model.serve.AgentLedger
import com.onyx.app.data.model.serve.LedgerFormat
import com.onyx.app.data.model.serve.LedgerNoPrice
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le lexique du **relevé d'agent** sort en français — et il sort **entier**.
 *
 * Deux verrous, parce que les deux fautes sont différentes :
 *
 * 1. **chaque clé du dictionnaire est atteignable** par le traducteur (une entrée ajoutée dans un
 *    fichier non joint ne traduirait rien) ;
 * 2. **chaque libellé et chaque phrase que le relevé compose réellement** est traduit — c'est le
 *    test qui attrape une rangée neuve dont le texte resterait en anglais au milieu d'un écran
 *    français.
 */
class AgentLedgerLocalizationTest {

    private val french = Locale.FRANCE

    @Test
    fun `chaque entree du dictionnaire du releve est atteignable`() {
        var checked = 0
        onyxLedgerFrench.forEach { (key, value) ->
            if (key == value) return@forEach
            assertEquals("la clé « $key » doit sortir en français", value, OnyxLocalization.translate(key, french))
            checked++
        }
        assertTrue("le dictionnaire doit porter de vraies traductions", checked > 30)
    }

    @Test
    fun `les libelles des sections et des rangees sont en francais`() {
        val expected = mapOf(
            "Agent ledger" to "Relevé de l’agent",
            "This conversation" to "Cette conversation",
            "Per bot" to "Par bot",
            "Model" to "Modèle",
            "Origin" to "Origine",
            "Work" to "Travail",
            "Working time" to "Temps de travail",
            "Answers without a duration" to "Réponses sans durée mesurée",
            "Tool calls" to "Appels d’outils",
            "Calls that succeeded" to "Appels réussis",
            "Calls that failed" to "Appels échoués",
            "Still running" to "En cours",
            "Tool calls (server)" to "Appels d’outils (serveur)",
            "Subagents" to "Sous-agents",
            "Launched" to "Lancés",
            "Tracked by the server" to "Suivis par le serveur",
            "Tokens" to "Jetons",
            "Input" to "Entrée",
            "Output" to "Sortie",
            "Cache read" to "Cache lu",
            "Cost" to "Coût",
            "Estimated" to "Estimé",
            "Actual" to "Réel",
            "Price" to "Prix",
            "Conversations without a price" to "Conversations sans prix",
            "Compare the bots" to "Comparer les bots",
        )
        expected.forEach { (key, value) ->
            assertEquals(value, OnyxLocalization.translate(key, french))
        }
    }

    @Test
    fun `les valeurs et les notes du releve sont en francais, chiffres compris`() {
        val expected = mapOf(
            "not recorded by this thread" to "non consigné dans ce fil",
            "not measured" to "non mesuré",
            "not reported" to "non remonté",
            "not provided" to "non fourni",
            "this phone" to "ce téléphone",
            "the server's registry" to "le registre du serveur",
            "counted by the server's session registry" to "comptés par le registre des sessions du serveur",
            "counted by this phone, from the thread it kept" to
                "comptés par ce téléphone, d’après le fil qu’il garde",
            "the server's registry did not answer for this session" to
                "le registre du serveur n’a pas répondu pour cette session",
            "this conversation has no session on the server" to
                "cette conversation n’a pas de session sur le serveur",
            "counted one by one in the thread" to "comptés un par un dans le fil",
            "every launch was announced by the server" to "chaque lancement a été annoncé par le serveur",
            "no answer of this thread carried a measured duration" to
                "aucune réponse de ce fil ne porte de durée mesurée",
            "this transport did not report any tokens for this thread" to
                "ce transport n’a remonté aucun jeton pour ce fil",
            "they are missing from the total above, which stays incomplete" to
                "elles manquent au total ci-dessus, qui reste incomplet",
            "the server's registry counts a different total for the same session" to
                "le registre du serveur compte un total différent pour la même session",
            "measured on one answer" to "mesuré sur une réponse",
            "one answer stays outside the total above" to "une réponse reste hors du total ci-dessus",
            "one call had not finished when this ledger was written" to
                "un appel n’était pas terminé quand ce relevé a été écrit",
            "one task was launched blind: this transport says nothing about its state" to
                "une tâche a été lancée à l’aveugle : ce transport ne dit rien de son état",
            "cost from the server's registry" to "coût du registre du serveur",
            "estimated at the price the server publishes" to "estimation au prix publié par le serveur",
            "the price line the server publishes" to "la ligne de prix publiée par le serveur",
            "neither the server's registry nor its price table gave a price" to
                "ni le registre du serveur ni sa table de prix n’ont donné de prix",
            "the server publishes no price for this model" to
                "le serveur ne publie aucun prix pour ce modèle",
            "the server's price table did not answer" to "la table de prix du serveur n’a pas répondu",
            "the server's price table does not list this model" to
                "la table de prix du serveur ne liste pas ce modèle",
            "no tokens were counted, so no cost could be estimated" to
                "aucun jeton n’a été compté : aucun coût n’a pu être estimé",
            "This bot has no conversation on this phone yet." to
                "Ce bot n’a encore aucune conversation sur ce téléphone.",
            "A bot’s total adds up the conversations this phone ties to it." to
                "Le total d’un bot additionne les conversations que ce téléphone lui rattache.",
        )
        expected.forEach { (key, value) ->
            assertEquals(value, OnyxLocalization.translate(key, french))
        }
        // Les gabarits, remplis comme le relevé les remplit.
        assertEquals("mesuré sur %1\$d réponses", OnyxLocalization.translate(AgentLedger.KEY_MEASURED_ON, french))
        assertEquals("relevé le %1\$s", OnyxLocalization.translate(AgentLedger.KEY_READ_ON, french))
        assertNotEquals(
            AgentLedger.KEY_BLIND,
            OnyxLocalization.translate(AgentLedger.KEY_BLIND, french),
        )
    }

    @Test
    fun `les deux lectures du serveur et leurs issues sont en francais`() {
        // Ces huit textes sont **au bas** de l'écran : ils y restaient en anglais tant qu'ils
        // n'étaient pas au dictionnaire — vu en capture, et verrouillé ici.
        val expected = mapOf(
            AgentLedger.KEY_READING_REGISTRY to "lecture du registre des sessions du serveur…",
            AgentLedger.KEY_READING_PRICES to "lecture de la table de prix du serveur…",
            AgentLedger.KEY_REGISTRY_READ to "le registre des sessions du serveur a répondu",
            AgentLedger.KEY_PRICES_READ to "la table de prix du serveur a répondu",
            AgentLedger.KEY_REGISTRY_MISSING to
                "le registre des sessions n’a pas pu être lu (%1\$s) — les comptes ci-dessous sont ceux de ce téléphone",
            AgentLedger.KEY_PRICES_MISSING to
                "la table de prix n’a pas pu être lue (%1\$s) — aucun coût ne peut être estimé sans elle",
            AgentLedger.KEY_NO_DASHBOARD to "les identifiants du tableau de bord manquent sur ce téléphone",
            AgentLedger.KEY_BOTS_WITHOUT_CHATS to "%1\$d bots n’ont encore aucune conversation sur ce téléphone.",
        )
        expected.forEach { (key, value) ->
            assertEquals(value, OnyxLocalization.translate(key, french))
        }
    }

    @Test
    fun `les raisons du refus de calculer sont toutes en francais`() {
        LedgerNoPrice.entries.forEach { reason ->
            val label = reason.label ?: return@forEach
            assertNotEquals(
                "la raison « $label » doit être traduite",
                label,
                OnyxLocalization.translate(label, french),
            )
        }
    }

    @Test
    fun `les unites sont ecrites, et les identifiants n entrent pas au dictionnaire`() {
        // Un chiffre nu ne dit pas ce qu'il compte : l'unité est écrite, en français.
        assertEquals("18,4k jetons", LedgerFormat.tokens(18_400, french))
        assertEquals("2 min 12", LedgerFormat.duration(132.4, french))
        assertEquals("0,42 $", LedgerFormat.amount(0.42, french))
        // Les identifiants du serveur se rendent verbatim : ils ne sont pas des mots à traduire.
        assertEquals(
            "minimax/minimax-m2.7",
            OnyxLocalization.translate("minimax/minimax-m2.7", french),
        )
        assertEquals("sage-a1.5", OnyxLocalization.translate("sage-a1.5", french))
    }
}
