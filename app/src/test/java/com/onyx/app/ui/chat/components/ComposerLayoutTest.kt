package com.onyx.app.ui.chat.components

import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxSpace
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
    fun `les glyphes du composer utilisent les styles de texte référence`() {
        assertEquals(20.dp, ComposerPlusGlyphSize)
        assertEquals(17.dp, ComposerDictateGlyphSize)
        assertEquals(13.dp, ComposerExpandGlyphSize)
    }

    @Test
    fun `les commandes du composer prennent la cible de la maison, pas les 38 points du modele`() {
        // **Pourquoi la valeur a changé.** Le lot « verre liquide » a relevé sur `1.0.0+547` que le
        // nœud du bouton rond mesurait 42 × 48 dp pour une boîte de 38 : Android élargit lui-même
        // toute cible tactile sous son minimum de 48 dp, de 5 dp par côté. Avec l'écart de 4 dp
        // d'alors, deux commandes voisines se recouvraient de 6 dp — c'est la superposition que le
        // propriétaire voyait. La commande prend donc la cible de la maison (44 dp, le jeton
        // `OnyxSpace.ThumbnailSize`) et l'écart de la rangée vaut 12 dp : le débordement du système
        // (2 dp par côté) est couvert deux fois.
        val source = File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()
        assertTrue(
            "la commande doit prendre la cible de la maison",
            source.contains("internal val ComposerControlSlotHeight = OnyxSpace.ThumbnailSize"),
        )
        assertTrue(source.contains("height = ComposerControlSlotHeight"))
        assertEquals(
            "et cette cible vaut bien 44 dp",
            OnyxSpace.ThumbnailSize,
            ComposerControlSlotHeight,
        )
        assertTrue("la cible ne descend pas sous 44 dp", ComposerControlSlotHeight >= 44.dp)
        // **L'élargissement d'agrandissement suit la même largeur** : à 30 dp, le système l'élargissait
        // de 9 dp par côté et elle recouvrait sa voisine.
        assertFalse(
            "aucune commande de la rangée ne doit être allouée sous 44 dp de large",
            source.contains("size = 30.dp,"),
        )
    }

    @Test
    fun `le steer et la file sont deux boutons et le groupe garde la fleche envoi`() {
        val composer = File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()
        val group = File("src/main/java/com/onyx/app/ui/bots/GroupComposer.kt").readText()
        assertTrue(composer.contains("listOf(\"steer\", \"after\")"))
        assertTrue(composer.contains("onSteer"))
        // **Le groupe n'a plus de compositeur à lui** : il passe par celui de la maison, donc il
        // garde la flèche d'envoi (le bouton rond de `OnyxComposer`) et il n'y a toujours **pas** de
        // file d'attente pendant un tour.
        assertTrue(group.contains("OnyxComposer("))
        assertFalse("la salle ne décide plus elle-même de son bouton d'envoi", group.contains("GroupComposerAction.SEND"))
        assertFalse(group.contains("queueWhileStreaming"))
    }

    @Test
    fun `la bague du champ exige le focus comme sur la référence`() {
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
        // Le défilement était ramené de force à chaque morceau de texte reçu : on ne pouvait pas
        // remonter le fil pendant que l'agent travaillait. On suit la fin seulement tant que la
        // personne reste en bas.
        val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue("la personne décide : on suit si elle est dans la marge d'ancrage", screen.contains("val withinAnchor by remember(anchorMarginPx) {"))
        assertTrue(screen.contains("var following by remember { mutableStateOf(true) }"))
        // Le verrou nomme désormais la **décision** (`ScrollFollow.followsNewContent`) au lieu de la
        // seule variable : un suivi détaché ne ramène jamais la vue, y compris à une transition
        // d'état. Rien n'est relâché — la condition « il y a des messages » reste exigée.
        assertTrue(
            "le suivi est conditionné",
            screen.contains("if (ScrollFollow.followsNewContent(following) && state.messages.isNotEmpty())"),
        )
        assertTrue(
            "un geste en cours reprend la main",
            screen.contains("snapshotFlow { listState.isScrollInProgress to withinAnchor }"),
        )
    }
}
