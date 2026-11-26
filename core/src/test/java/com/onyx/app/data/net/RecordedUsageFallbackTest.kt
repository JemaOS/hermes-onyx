package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux feuilles du panneau d'actions — consommation et processus en arrière-plan — doivent
 * répondre **toujours**, et notamment sur une conversation que le serveur ne tient plus vivante.
 *
 * Le défaut relevé, capture à l'appui : « Consommation et coût » affichait « Ce serveur n'a pas
 * donné la consommation de cette conversation » et « Processus en arrière-plan » affichait « Le
 * serveur ne tient plus la session vivante de cette conversation », sur le chat d'un bot qui venait
 * de répondre. Les deux lectures ne savaient parler qu'au gateway, qui ne répond que pour une
 * session **en cours** (« ça fonctionne pas tout le temps »).
 */
class RecordedUsageFallbackTest {

    private val row = SessionSummary(
        id = "api-f0c10b83447764e9",
        model = "deepseek/deepseek-v4.1-flash",
        inputTokens = 16216,
        outputTokens = 163,
        reasoningTokens = 157,
        cacheReadTokens = 128,
        cacheWriteTokens = 0,
        apiCallCount = 1,
        estimatedCostUsd = 0.00253212,
        lastActive = 1_793_804_958.775798,
    )

    @Test
    fun `la ligne du registre donne la consommation d'une session morte`() {
        val usage = ServeChatActions.usageOfSummary(row)

        assertEquals(16216, usage.inputTokens)
        assertEquals(163, usage.outputTokens)
        assertEquals(157, usage.reasoningTokens)
        assertEquals(128, usage.cacheReadTokens)
        assertEquals(1, usage.apiCalls)
        // Le total se calcule, il ne se demande pas : le registre ne le porte pas.
        assertEquals(16379, usage.totalTokens)
        assertEquals(0.00253212, usage.estimatedCostUsd!!, 1e-12)
        // Le prix vient d'une estimation du serveur, et la feuille le dit comme tel.
        assertEquals("estimated", usage.costStatus)
    }

    @Test
    fun `ces chiffres s'annoncent comme consignes, pas comme mesures d'instant`() {
        val usage = ServeChatActions.usageOfSummary(row)

        assertTrue("une lecture de registre est consignée", usage.isRecorded)
        assertEquals(1_793_804_958_775L, usage.recordedAt)
        // La contrepartie : une lecture vivante n'est jamais marquée consignée.
        assertTrue(!ServeChatActions.usageOf(null, null).isRecorded)
    }

    @Test
    fun `une ligne vide ne se fait pas passer pour une consommation`() {
        val vide = ServeChatActions.usageOfSummary(SessionSummary(id = "api-vide"))
        assertTrue("aucun compteur, aucun prix : la feuille dira qu'il n'y a rien", vide.isEmpty)
        assertEquals(null, vide.costUsd)
    }

    @Test
    fun `une session terminee rend une liste vide qui se declare morte`() = runBlocking {
        // Le gateway ne tient plus la session : la lecture n'est pas une panne, et la feuille doit
        // pouvoir dire « il n'y a plus de session », pas « rien ne tourne » — deux choses
        // différentes, la seconde étant une mesure.
        val channel = RpcChannel { _, _, _ -> throw TransportError.Rpc(4001, "session not found") }
        val lu = ServeChatActions(ServerConfig(id = "qa"), channel).processes("sess-morte").getOrThrow()
        assertTrue("aucun processus", lu.processes.isEmpty())
        assertTrue("la lecture se déclare non vivante", !lu.live)
    }

    @Test
    fun `une panne reseau reste une panne, pas une session morte`() = runBlocking {
        val channel = RpcChannel { _, _, _ -> throw TransportError.Unreachable("connection reset") }
        val resultat = runCatching {
            ServeChatActions(ServerConfig(id = "qa"), channel).processes("sess-1").getOrThrow()
        }
        assertTrue("une panne ne doit pas se faire passer pour une absence", resultat.isFailure)
    }

    @Test
    fun `une session terminee se reconnait au refus du gateway`() {
        // Les trois formes que le gateway renvoie quand la session n'est plus en mémoire.
        for (refus in listOf("4001 session not found", "session not in memory", "not found")) {
            assertTrue(refus, ServeChatActions.refusesLiveSession(refus))
        }
        // Et ce qui n'est PAS une session morte : une route inconnue, une panne.
        assertTrue(!ServeChatActions.refusesLiveSession("unknown method"))
        assertTrue(!ServeChatActions.refusesLiveSession("connection reset"))
    }
}
