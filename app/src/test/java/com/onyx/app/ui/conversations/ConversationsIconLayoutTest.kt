package com.onyx.app.ui.conversations

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les symboles et les mesures de la liste des conversations, contre. */
class ConversationsIconLayoutTest {
    private val source = File("src/main/java/com/onyx/app/ui/conversations/ConversationsScreen.kt").readText()

    @Test
    fun `le bouton Settings porte une seule roue comme dans `() {
        // `Button("Settings", systemImage: "gearshape")`: une roue, pas `gearshape.2` (Gateway).
        assertTrue(source.contains("symbol = Symbols.GearshapeSingle"))
        assertFalse(source.contains("symbol = Symbols.Gearshape,"))
        val symbols = File("../core/src/main/java/com/onyx/app/ui/design/Symbols.kt").readText()
        assertTrue(symbols.contains("val GearshapeSingle = R.drawable.ic_sym_gearshape"))
        val gear = File("../core/src/main/res/drawable/ic_sym_gearshape.xml").readText()
        assertTrue(gear.contains("strokeColor"))
        assertFalse(gear.contains("android:fillColor=\"#FFFFFFFF\""))
    }

    @Test
    fun `la barre garde le grand cercle et la capsule des deux actions`() {
        assertTrue(source.contains("BigCircleButton("))
        assertTrue(source.contains("CapsuleAction(Symbols.CheckmarkCircleOutline, \"Select\")"))
        assertTrue(source.contains("CapsuleAction(Symbols.SquareAndPencil, \"New conversation\")"))
    }

    @Test
    fun `la recherche et les segments sont les capsules `() {
        assertTrue(source.contains(".background(theme.fill, RoundedCornerShape(26.dp))"))
        assertTrue(source.contains("text = \"Search conversations\""))
        assertTrue(source.contains("OnyxSegmented("))
        assertTrue(source.contains("options = ListSection.entries.map { it.title }"))
    }
}
