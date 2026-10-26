package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le suivi du bas du fil: le défaut signalé deux fois — « quand il travaille en réflexion je ne
 * peux même pas descendre »; « quand l'IA réfléchit je peux pas remonter le fil de la discussion,
 * ça me bloque ». Le défilement se recollait en bas à chaque jeton de raisonnement, chaque ligne
 * d'outil, chaque battement du compteur de durée.
 *
 * Vérifications de source: elles portent sur le câblage, pas sur le rendu — c'est la capture
 * d'émulateur qui juge le rendu. La décision elle-même est éprouvée par [ScrollFollowTest].
 */
class ChatScrollFollowTest {
    private val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val group = File("src/main/java/com/onyx/app/ui/bots/GroupChatScreen.kt").readText()

    @Test
    fun `un doigt sur la liste detache le suivi sans attendre la position`() {
        assertTrue(
            "c'est le geste, pas la position, qui détache le suivi",
            screen.contains("is DragInteraction.Start"),
        )
        assertTrue(screen.contains("following = ScrollFollow.afterTouch()"))
    }

    @Test
    fun `le suivi ne se re-attache jamais au milieu d un geste`() {
        assertTrue(screen.contains("ScrollFollow.atRest(scrolling, bottom, following)"))
        // La règle retirée ré-attachait le suivi dès que la vue lisait « au bas »: cela arrivait à
        // chaque jeton, puisque le suivi venait justement de ramener la vue en bas.
        assertFalse(screen.contains("if (dragging) following = bottom"))
    }

    @Test
    fun `le bouton du dernier message parait des que le suivi est detache`() {
        assertTrue(
            "offert sur « détaché et pas au bas », comme la référence",
            screen.contains("val offersJump = state.messages.isNotEmpty() && !following && !atBottom"),
        )
    }

    @Test
    fun `le bouton ramene en bas en re-attachant le suivi`() {
        assertTrue(screen.contains("following = ScrollFollow.jumpToLatest()"))
        assertTrue(screen.contains("listState.scrollToItem(latestItemIndex, BottomScrollOffset)"))
    }

    @Test
    fun `un message de la personne ramene en bas`() {
        assertTrue(screen.contains("val userTurns = remember(state.messages)"))
        assertTrue(screen.contains("userTurns > seenUserTurns"))
    }

    @Test
    fun `la salle de groupe suit la meme regle`() {
        assertTrue(group.contains("is DragInteraction.Start"))
        assertTrue(group.contains("ScrollFollow.atRest("))
        assertFalse(group.contains("if (dragging) following = bottom"))
    }
}
