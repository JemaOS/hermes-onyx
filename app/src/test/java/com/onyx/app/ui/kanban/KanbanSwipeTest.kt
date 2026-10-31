package com.onyx.app.ui.kanban

import com.onyx.app.data.model.serve.KanbanCard
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le glissement d'une carte du tableau kanban (la capture 2.0) et l'âge de sa rangée.
 *
 * Le seuil du glissement franc est celui d'`allowsFullSwipe` de la référence : `KanbanView:52` place
 * l'action de la colonne suivante en `edge: .leading`, et la rangée reprend le texte de la
 * colonne — c'est `KanbanStatus.label`, traduit par la couche de localisation.
 */
class KanbanSwipeTest {
    private val screen = File("src/main/java/com/onyx/app/ui/kanban/KanbanScreen.kt").readText()
    private val swipe = File("src/main/java/com/onyx/app/ui/kanban/KanbanSwipe.kt").readText()
    private val viewModel = File("src/main/java/com/onyx/app/ui/kanban/KanbanViewModel.kt").readText()

    @Test
    fun `un glissement court ne declenche pas l action d un coup`() {
        assertFalse(fullSwipeReached(travelled = 40f, rowWidth = 400f, velocity = 200f))
        assertFalse(fullSwipeReached(travelled = 179f, rowWidth = 400f, velocity = 0f))
    }

    @Test
    fun `le seuil est la moitie de la rangee ou l elan`() {
        assertTrue(fullSwipeReached(travelled = 180f, rowWidth = 400f, velocity = 0f))
        assertTrue(fullSwipeReached(travelled = 20f, rowWidth = 400f, velocity = 900f))
        // Le geste d'un doigt lent sur toute la largeur passe aussi.
        assertTrue(fullSwipeReached(travelled = 400f, rowWidth = 400f, velocity = 10f))
    }

    @Test
    fun `la carte se glisse vers l avant et vers la suppression`() {
        assertTrue("la rangée ne se glisse pas", screen.contains("KanbanSwipeRow("))
        assertTrue(screen.contains("forward = next?.let { status ->"))
        assertTrue(screen.contains("label = status.label"))
        assertTrue(screen.contains("label = \"Delete\""))
        assertTrue(screen.contains("symbol = Symbols.Trash"))
        // Le geste suit le doigt et lâche en revenant à sa place.
        assertTrue(swipe.contains("detectHorizontalDragGestures"))
        assertTrue(swipe.contains("OnyxMotion.Sites.SidePanel"))
        assertTrue(swipe.contains("LocalReduceMotion"))
    }

    @Test
    fun `un glissement vers l avant deplace la carte vers la colonne suivante`() {
        // Le menu de déplacement et le glissement partagent la même table de colonnes.
        assertTrue(screen.contains("if (next.movePrompt != null) movingCard = card to next else viewModel.move(card, next)"))
        assertTrue(screen.contains("KanbanStatus.TRIAGE -> KanbanStatus.TODO"))
        assertTrue(screen.contains("KanbanStatus.TODO, KanbanStatus.BLOCKED -> KanbanStatus.READY"))
    }

    @Test
    fun `l en-tete porte l etat du direct, la fleche tant qu il n ecoute pas`() {
        assertTrue(screen.contains("Symbols.DotRadiowavesLeftAndRight"))
        // `KanbanView` : `if available { Image(systemName: live.isLive ?
        // "dot.radiowaves.left.and.right" : "arrow.clockwise") }` — la flèche secondaire tant que
        // le direct n'écoute pas encore, la pastille verte quand il écoute, rien sans Dashboard.
        assertTrue(screen.contains("else -> Symbols.ArrowClockwise"))
        assertTrue(screen.contains("state.needsDashboard -> null"))
        assertTrue(screen.contains("trailingLabel = if (state.live) \"Live updates on\" else \"Polling for updates\""))
        assertTrue(screen.contains("trailingTint = if (state.live) theme.success else theme.secondaryText"))
    }

    @Test
    fun `le tableau se relit tout seul et s arrete avec l ecran`() {
        assertTrue(screen.contains("viewModel.startLive()"))
        assertTrue(screen.contains("onDispose { viewModel.stopLive() }"))
        // Le direct rafraîchit **sans** indicateur : sinon il clignotait à chaque tentative.
        assertTrue(viewModel.contains("liveBoard.start(_state.value.latestEventId) { refresh(silent = true) }"))
        assertTrue(viewModel.contains("liveBoard.close()"))
    }

    @Test
    fun `la rangee montre l age de la carte comme référence`() {
        val now = 1_700_000_000_000L
        val card = KanbanCard(id = "t1", title = "Compare heat pump quotes", createdAtMillis = now - 3_600_000L)
        val stale = KanbanCard(id = "t2", title = "Vieux serveur", createdAt = "1780000000")

        assertEquals("1 hour ago", cardAge(card, nowMillis = now, locale = Locale.US))
        assertEquals("il y a 1 heure", cardAge(card, nowMillis = now, locale = Locale.FRANCE))
        // Sans estampille numérique, la valeur du serveur est rendue telle quelle.
        assertEquals("1780000000", cardAge(stale, nowMillis = now, locale = Locale.US))
    }

    @Test
    fun `le detail garde la date, la rangee garde l age`() {
        val now = 1_700_000_000_000L
        val card = KanbanCard(id = "t1", title = "Book the Lisbon flights", createdAtMillis = now)
        val stale = KanbanCard(id = "t2", title = "Vieux serveur", createdAt = "hier")

        assertEquals("hier", cardCreatedLabel(stale))
        assertFalse(cardCreatedLabel(card).orEmpty().isBlank())
    }
}
