package com.onyx.app.ui.chat.components

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerLayoutTest {
    @Test
    fun `le bouton rond suit la hauteur du champ dans la densite reelle`() {
        assertEquals(48.dp, measuredComposerButtonSize(rowHeightPx = 76, density = 2f))
        assertEquals(50.dp, measuredComposerButtonSize(rowHeightPx = 100, density = 2.5f))
    }

    @Test
    fun `les glyphes du composer utilisent les styles de texte `() {
        assertEquals(20.dp, ComposerPlusGlyphSize)
        assertEquals(17.dp, ComposerDictateGlyphSize)
        assertEquals(13.dp, ComposerExpandGlyphSize)
    }

    @Test
    fun `le micro et lediteur gardent les 38 points de haut `() {
        val source = File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()
        assertTrue(source.contains("ComposerControlSlotHeight = 38.dp"))
        assertTrue(source.contains("height = ComposerControlSlotHeight"))
    }

    @Test
    fun `le steer et la file sont deux boutons et le groupe garde la fleche envoi`() {
        val composer = File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()
        val group = File("src/main/java/com/onyx/app/ui/bots/GroupComposer.kt").readText()
        assertTrue(composer.contains("listOf(\"steer\", \"after\")"))
        assertTrue(composer.contains("onSteer"))
        // Le groupe a son propre compositeur (`GroupComposer`): le bouton rond garde la flèche
        // d'envoi et il n'y a pas de file d'attente pendant un tour.
        assertTrue(group.contains("GroupComposerAction.SEND"))
        assertTrue(group.contains("Symbols.ArrowUp"))
        assertFalse(group.contains("queueWhileStreaming"))
    }

    @Test
    fun `la bague du champ exige le focus comme `() {
        val source = File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()
        assertTrue(source.contains("focused && hasDraft"))
        assertTrue(source.contains("onFocusChanged"))
    }

    @Test
    fun `jump to latest a une vraie action et nest plus inerte`() {
        val source = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue(source.contains("JumpToLatestPill(live = state.isStreaming, onClick ="))
        assertFalse(source.contains("GlassPill(modifier = Modifier.onyxClickable { })"))
    }

    @Test
    fun `le fil ne ramene pas la personne en bas pendant que l agent reflechit`() {
        // Le défilement était ramené de force à chaque morceau de texte reçu: on ne pouvait pas
        // remonter le fil pendant que l'agent travaillait. On suit la fin seulement tant que la
        // personne reste en bas.
        val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue("la personne décide : on suit si elle est en bas", screen.contains("val atBottom by remember"))
        assertTrue(screen.contains("var following by remember { mutableStateOf(true) }"))
        assertTrue("le suivi est conditionné", screen.contains("if (following && state.messages.isNotEmpty())"))
        assertTrue(
            "un geste en cours reprend la main",
            screen.contains("snapshotFlow { listState.isScrollInProgress to atBottom }"),
        )
    }
}
