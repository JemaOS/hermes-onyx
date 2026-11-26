package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.ui.ShellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Le travail de la coquille ne suit plus la longueur de l'historique.**
 *
 * Plainte du propriétaire : « plus j'ai d'infos, plus c'est lent… ça fige parfois ». La cause
 * mesurable était là : `ShellState.of` est appelé à **chaque jeton** (trente fois par seconde
 * pendant une réponse) et refaisait trois balayages du fil entier, dont une copie de N éléments
 * (`filterNot { isStreaming }`) et **un `VoiceTurn` par tour** de la conversation. Avec vingt pages
 * d'historique, ce coût grandit avec ce que l'app contient — et il est payé pour un texte qui
 * n'entre même pas encore dans l'historique.
 *
 * La propriété vérifiée ici n'est pas un temps (il dépend de la machine) mais un **compte de
 * calculs** : trente publications de texte, une seule projection du fil fini.
 */
class SettledScansPerTokenTest {

    private fun message(index: Int): ChatMessage = ChatMessage(
        id = "m$index",
        role = if (index % 2 == 0) Role.USER else Role.ASSISTANT,
        text = "message $index ".repeat(40),
        createdAt = index.toLong(),
    )

    @Before
    fun reset() {
        SettledScans.clear()
    }

    private fun state(messages: List<ChatMessage>, streaming: Boolean) = ChatUiState(
        server = ServerConfig(id = "s", baseUrl = "http://127.0.0.1:8642"),
        messages = messages,
        isStreaming = streaming,
    )

    @Test
    fun `trente jetons ne refont pas le calcul du fil fini`() {
        val settled = (0 until 200).map(::message)
        val live = message(200).copy(role = Role.ASSISTANT, text = "")

        // Un tour s'écrit : la dernière ligne grandit à chaque jeton, la frontière ne bouge pas.
        var projected: ShellState? = null
        for (token in 0 until 30) {
            val growing = live.copy(text = "mot ".repeat(token + 1))
            projected = ShellState.of(state(settled + growing, streaming = true))
        }

        assertEquals(
            "la projection du fil fini est faite une fois, pas trente",
            1,
            SettledScans.computations,
        )
        assertEquals("et la dernière question reste dans l'historique vocal", 100, projected!!.voiceTurns.size)
    }

    @Test
    fun `deux projections du meme fil partagent la meme liste`() {
        // `derivedStateOf` décide de recomposer en comparant l'ancienne et la nouvelle projection :
        // `ShellState.voiceTurns` porte le texte de **tous** les tours. Rendre la **même instance**
        // fait que cette comparaison s'arrête à l'identité (premier test de `ArrayList.equals`) au
        // lieu de relire vingt pages de conversation à chaque publication.
        val settled = (0 until 30).map(::message)
        val first = ShellState.of(state(settled, streaming = false))
        val second = ShellState.of(state(settled, streaming = false))

        assertSame("la même liste, donc une comparaison gratuite", first.voiceTurns, second.voiceTurns)
        assertSame("et la première question aussi", first.firstUserQuestion, second.firstUserQuestion)
    }

    @Test
    fun `un message de plus refait le calcul, et lui seul`() {
        val settled = (0 until 40).map(::message)
        ShellState.of(state(settled, streaming = false))
        assertEquals(1, SettledScans.computations)

        // Le tour se clôt : la ligne entre dans l'historique, la frontière bouge.
        ShellState.of(state(settled + message(40), streaming = false))
        assertEquals("un fil plus long, un calcul de plus", 2, SettledScans.computations)
    }

    @Test
    fun `une edition de texte est vue, sans refaire la copie du fil`() {
        val settled = (0 until 20).map(::message)
        val before = ShellState.of(state(settled, streaming = false)).voiceTurns
        val edited = settled.toMutableList().also { it[0] = it[0].copy(text = "question corrigée") }

        val after = ShellState.of(state(edited, streaming = false)).voiceTurns

        assertEquals("l'édition change le calcul", 2, SettledScans.computations)
        assertEquals("et la question affichée suit", "question corrigée", after.first().question)
        assertTrue("les autres tours sont inchangés", before.drop(1) == after.drop(1))
    }

    @Test
    fun `la reponse en cours d ecriture n entre pas dans l historique vocal`() {
        val settled = (0 until 10).map(::message)
        val live = message(10).copy(role = Role.ASSISTANT, text = "je réponds…")

        val projected = ShellState.of(state(settled + live, streaming = true))

        // 10 messages = 5 questions, et la réponse qui s'écrit ne compte pas encore.
        assertEquals(5, projected.voiceTurns.size)
    }

    @Test
    fun `les approbations ne passent pas par le cache`() {
        // Une carte qui attend une décision paraît et disparaît **pendant** le tour : elle ne peut
        // pas attendre un changement de frontière pour être vue.
        val settled = (0 until 5).map(::message)
        val pending = com.onyx.app.data.model.ApprovalRequest(id = "a1", command = "ls")
        val first = ShellState.of(state(settled, streaming = false))
        val second = ShellState.of(state(settled + message(5).copy(role = Role.ASSISTANT, approvals = listOf(pending)),
                                      streaming = false))

        assertEquals("aucune approbation au départ", 0, first.pendingApprovals.size)
        assertEquals("elle paraît dès qu'elle existe", 1, second.pendingApprovals.size)
    }
}