package com.onyx.app.ui.chat

import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.ui.design.FlowState
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **L'état du fil est VRAI — le déclencheur de chaque état, prouvé.**
 *
 * Le concept « fil vivant » peint quatre états ; ce test est ce qui empêche le cinquième d'exister —
 * un état décoratif, qui ne correspondrait à rien du tour. Chaque état est dérivé d'une donnée que
 * le transport a réellement produite ([TurnPhase], les approbations du fil), et rien d'autre :
 *
 * | état | ce qui le déclenche |
 * |---|---|
 * | [FlowState.REST] | aucun tour, ou tour fini |
 * | [FlowState.THINKING] | le tour est ouvert, rien n'est écrit ([TurnStep.STARTING], [TurnStep.THINKING]) |
 * | [FlowState.WORKING] | un outil tourne, ou la réponse s'écrit ([TurnStep.TOOL], [TurnStep.WRITING]) |
 * | [FlowState.DECIDING] | au moins une approbation **en attente** |
 *
 * Et **l'attente l'emporte sur le travail** : un tour qui attend une réponse est un tour *arrêté*,
 * pas un tour qui travaille.
 */
class TurnFlowTest {

    private val previousLocale = Locale.getDefault()

    @Before
    fun frenchInterface() {
        Locale.setDefault(Locale.FRANCE)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    private fun phase(step: TurnStep, streaming: Boolean = true) = TurnPhase(
        startedAt = 1_000L,
        lastEventAt = 2_000L,
        step = step,
        toolName = if (step == TurnStep.TOOL) "terminal" else null,
        isStreaming = streaming,
    )

    private fun approval(id: String, decision: String? = null) = ApprovalRequest(
        id = id,
        command = "rm -rf /tmp/x",
        decision = decision,
    )

    private fun message(vararg approvals: ApprovalRequest) = ChatMessage(
        id = "m",
        role = Role.ASSISTANT,
        text = "réponse",
        createdAt = 0L,
        approvals = approvals.toList(),
    )

    @Test
    fun `aucun tour, ou tour fini, le fil est au repos`() {
        assertEquals("sans tour, le fil est au repos", FlowState.REST, turnFlowState(null, 0))
        listOf(TurnStep.DONE, TurnStep.STOPPED, TurnStep.FAILED).forEach { step ->
            assertEquals(
                "un tour $step n'est plus en cours",
                FlowState.REST,
                turnFlowState(phase(step, streaming = false), 0),
            )
        }
    }

    @Test
    fun `le tour ouvert qui n a rien ecrit est une reflexion`() {
        listOf(TurnStep.STARTING, TurnStep.THINKING).forEach { step ->
            assertEquals(
                "$step : le tour est ouvert, la réponse n'a pas commencé",
                FlowState.THINKING,
                turnFlowState(phase(step), 0),
            )
        }
    }

    @Test
    fun `un outil qui tourne ou une reponse qui s ecrit sont un travail`() {
        listOf(TurnStep.TOOL, TurnStep.WRITING).forEach { step ->
            assertEquals(
                "$step : le tour produit quelque chose",
                FlowState.WORKING,
                turnFlowState(phase(step), 0),
            )
        }
    }

    @Test
    fun `une approbation en attente arrete le tour, quel que soit son etape`() {
        listOf(TurnStep.STARTING, TurnStep.THINKING, TurnStep.TOOL, TurnStep.WRITING).forEach { step ->
            assertEquals(
                "$step : une décision est attendue, le travail est arrêté",
                FlowState.DECIDING,
                turnFlowState(phase(step), pendingApprovals = 1),
            )
        }
        assertEquals(
            "deux approbations en attente, c'est le même état",
            FlowState.DECIDING,
            turnFlowState(phase(TurnStep.TOOL), pendingApprovals = 2),
        )
    }

    @Test
    fun `une approbation deja repondue ne gele plus rien`() {
        val answered = message(approval("a1", decision = ApprovalRequest.CHOICE_ONCE))
        assertEquals("une question répondue n'attend plus", 0, pendingApprovalCount(listOf(answered)))
        assertEquals(
            "le tour a donc repris son étape réelle",
            FlowState.WORKING,
            turnFlowState(phase(TurnStep.TOOL), pendingApprovalCount(listOf(answered))),
        )
    }

    @Test
    fun `le compte des approbations ne regarde que les questions ouvertes`() {
        val thread = listOf(
            message(approval("a1"), approval("a2", decision = ApprovalRequest.CHOICE_DENY)),
            message(approval("a3")),
        )
        assertEquals("deux questions ouvertes sur trois", 2, pendingApprovalCount(thread))
        assertEquals("aucune question ouverte", 0, pendingApprovalCount(emptyList()))
        assertEquals("un fil sans approbation ne compte rien", 0, pendingApprovalCount(listOf(message())))
    }

    /**
     * **Le câblage de l'écran, verrouillé par une lecture de source.** L'état et le signal de
     * lecture sont calculés une fois dans `ChatScreen` et passés à la barre du haut **et** à la
     * carte d'activité ; les cartes du fil reçoivent le signal de lecture pour ralentir.
     */
    @Test
    fun `l ecran passe l etat reel et le signal de lecture a l en-tete et aux cartes`() {
        val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue("l'état vient du fil, pas d'un ornement", chat.contains("val flow = turnFlowState(state.live, pendingApprovals)"))
        assertTrue(
            "le compte des approbations vient du fil",
            chat.contains("val pendingApprovals = pendingApprovalCount(state.messages)"),
        )
        assertTrue(
            "« on remonte lire » est le suivi détaché pendant un tour",
            chat.contains("val readingUp = state.isStreaming && !following"),
        )
        assertTrue("l'en-tête reçoit l'état", chat.contains("flow = flow,"))
        assertTrue("et le signal de lecture", chat.contains("readingUp = readingUp,"))
    }
}
