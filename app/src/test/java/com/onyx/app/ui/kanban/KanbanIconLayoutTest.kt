package com.onyx.app.ui.kanban

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanIconLayoutTest {
    private val source = File("src/main/java/com/onyx/app/ui/kanban/KanbanScreen.kt").readText()

    @Test
    fun `chaque colonne a son symbole dans sa capsule et son etat vide`() {
        assertTrue(source.contains("symbol = kanbanStatusSymbol(column)"))
        assertTrue(source.contains("symbol = kanbanStatusSymbol(state.column)"))
        assertTrue(source.contains(".padding(horizontal = 10.dp, vertical = 6.dp)"))
        assertTrue(source.contains("Arrangement.spacedBy(4.dp)"))
        listOf(
            "ic_sym_tray.xml",
            "ic_sym_play_circle.xml",
            "ic_sym_hand_raised.xml",
            "ic_sym_eye.xml",
            "ic_sym_archivebox.xml",
            "ic_sym_person.xml",
            "ic_sym_text_bubble.xml",
            "ic_sym_arrow_right_square.xml",
        ).forEach { assertTrue("$it manquant", File("../core/src/main/res/drawable/$it").isFile) }
    }

    @Test
    fun `la carte garde titre deux lignes capsules et icones de metadonnees`() {
        assertTrue(source.contains("fontWeight = FontWeight.Medium"))
        assertTrue(source.contains("maxLines = 2"))
        assertTrue(source.contains("Symbols.Person"))
        assertTrue(source.contains("Symbols.TextBubble"))
        assertTrue(source.contains("RoundedCornerShape(50)"))
        assertTrue(source.contains("Symbols.SquareAndPencil"))
        assertFalse(source.contains("text = \"@${'$'}{it}\""))
        assertFalse(source.contains("OnyxFormSection(header = state.column.label)"))
    }

    @Test
    fun `le deplacement suit lordre metier de la référence`() {
        assertTrue(source.contains("KanbanStatus.TRIAGE -> KanbanStatus.TODO"))
        assertTrue(source.contains("KanbanStatus.TODO, KanbanStatus.BLOCKED -> KanbanStatus.READY"))
        assertTrue(source.contains("KanbanStatus.REVIEW -> KanbanStatus.DONE"))
        assertTrue(source.contains("KanbanStatus.DONE -> KanbanStatus.ARCHIVED"))
        assertTrue(source.contains("else -> null"))
    }

    @Test
    fun `la barre de Kanban porte la fleche de rafraichissement tant que le direct n ecoute pas`() {
        // `KanbanView` : `if available { Image(systemName: live.isLive ?
        // "dot.radiowaves.left.and.right" : "arrow.clockwise") }` — la flèche secondaire quand le
        // direct n'écoute pas encore, la pastille verte quand il écoute, rien sans Dashboard.
        assertTrue(source.contains("else -> Symbols.ArrowClockwise"))
        assertTrue(source.contains("state.live -> Symbols.DotRadiowavesLeftAndRight"))
        assertTrue(source.contains("state.needsDashboard -> null"))
        assertTrue(source.contains("trailingTint = if (state.live) theme.success else theme.secondaryText"))
        assertTrue(source.contains("trailingLabel = if (state.live) \"Live updates on\" else \"Polling for updates\""))
        assertTrue(File("../core/src/main/res/drawable/ic_sym_arrow_clockwise.xml").isFile)
    }

    @Test
    fun `sans Dashboard le tableau montre rectangle split et desactive New card`() {
        // `ContentUnavailableView("Needs the Hermes Dashboard", systemImage: "rectangle.split.3x1")`
        assertTrue(source.contains("title = \"Needs the Hermes Dashboard\""))
        assertTrue(source.contains("symbol = Symbols.RectangleSplit"))
        assertTrue(source.contains("actionEnabled = !state.needsDashboard"))
        val vm = File("src/main/java/com/onyx/app/ui/kanban/KanbanViewModel.kt").readText()
        assertTrue(vm.contains("val needsDashboard: Boolean = false"))
        assertTrue(vm.contains("if (!config.canAdminister)"))
    }

    @Test
    fun `un tableau qui ne charge pas porte wifi exclamation comme la référence`() {
        // `ContentUnavailableView("Couldn't load the board", systemImage: "wifi.exclamationmark")`
        assertTrue(source.contains("OnyxIcon(Symbols.WifiExclamation, tint = theme.warning, size = 16.dp)"))
        assertFalse(source.contains("OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)"))
    }
}
