package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les trames du pont, dans les deux sens — et le refus, qui doit rester lisible. */
class ComputerWireTest {

    @Test
    fun `le salut porte les deux tailles et qui regarde`() {
        val event = ComputerWire.parse(
            """{"t":"hello","screen":[3840,2160],"view":[1280,720],"fps":6,"viewer":"a1b2c3d4","holder":"agent"}""",
        )
        val hello = (event as ComputerEvent.Hello).hello
        assertEquals(3840, hello.screenWidth)
        assertEquals(2160, hello.screenHeight)
        assertEquals(1280, hello.viewWidth)
        assertEquals(720, hello.viewHeight)
        assertEquals(6, hello.fps)
        assertEquals("a1b2c3d4", hello.viewer)
    }

    @Test
    fun `l'etat dit qui a la main, ce qui vient de se passer et si ca travaille`() {
        val event = ComputerWire.parse(
            """{"t":"state","holder":"human","viewer":"a1b2","busy":true,""" +
                """"last":{"kind":"click","detail":"bouton 1","x":186,"y":1101,"at":1792977029607}}""",
        )
        val state = (event as ComputerEvent.State).state
        assertTrue(state.humanHolds)
        assertTrue(state.busy)
        assertEquals("click", state.last?.kind)
        assertEquals(186, state.last?.x)
        assertEquals(1101, state.last?.y)
    }

    @Test
    fun `un etat sans derniere action ne rend pas d'action`() {
        val event = ComputerWire.parse("""{"t":"state","holder":"agent","viewer":null,"busy":false,"last":null}""")
        val state = (event as ComputerEvent.State).state
        assertEquals(ComputerState.HOLDER_AGENT, state.holder)
        assertEquals(false, state.humanHolds)
        assertNull(state.last)
    }

    @Test
    fun `un refus et une sortie de commande se lisent`() {
        val denied = ComputerWire.parse("""{"t":"denied","id":3,"reason":"not_holder"}""")
        assertEquals("not_holder", (denied as ComputerEvent.Denied).reason)
        assertEquals(3, denied.id)

        val result = ComputerWire.parse("""{"t":"run_result","id":7,"ok":true,"exit":0,"output":"pont-ok\n"}""")
        val run = result as ComputerEvent.RunResult
        assertTrue(run.ok)
        assertEquals(0, run.exit)
        assertEquals("pont-ok\n", run.output)
    }

    @Test
    fun `une trame inconnue ou abimee est ignoree, jamais une erreur`() {
        assertEquals(ComputerEvent.Ignored, ComputerWire.parse("pas du json"))
        assertEquals(ComputerEvent.Ignored, ComputerWire.parse("""{"t":"devinette"}"""))
        assertEquals(ComputerEvent.Ignored, ComputerWire.parse("{}"))
    }

    @Test
    fun `les entrees partent avec leurs coordonnees et leur approbation`() {
        assertTrue(ComputerWire.take(1).contains("\"t\":\"take\""))
        assertTrue(ComputerWire.release(2).contains("\"id\":2"))

        val tap = ComputerWire.tap(3, 186, 1101)
        assertTrue(tap.contains("\"t\":\"tap\""))
        assertTrue(tap.contains("\"x\":186"))
        assertTrue(tap.contains("\"y\":1101"))
        assertTrue(tap.contains("\"button\":1"))
        // L'app dit que ses coordonnées sont déjà des pixels de l'écran du PC: sans cela, le pont
        // les agrandirait une seconde fois et le clic partirait ailleurs.
        assertTrue(tap.contains("\"space\":\"screen\""))

        val drag = ComputerWire.drag(4, 10, 20, 300, 400)
        assertTrue(drag.contains("\"from\":[10,20]"))
        assertTrue(drag.contains("\"space\":\"screen\""))
        assertTrue(drag.contains("\"to\":[300,400]"))

        assertTrue(ComputerWire.typeText(5, "bonjour").contains("\"text\":\"bonjour\""))
        assertTrue(ComputerWire.scroll(6, 5, 6, -3).contains("\"amount\":-3"))
        // L'autre espace reste possible: c'est celui des clients qui envoient l'image telle quelle.
        assertTrue(ComputerWire.tap(7, 10, 20, space = ComputerWire.SPACE_VIEW)
            .contains("\"space\":\"view\""))
    }

    @Test
    fun `une commande et un raccourci disent s'ils sont approuves`() {
        // Rien ne part apprové par défaut: l'approbation est une décision, pas une valeur par défaut.
        assertTrue(ComputerWire.run(1, "uname -a", approved = false).contains("\"approved\":false"))
        assertTrue(ComputerWire.run(2, "uname -a", approved = true).contains("\"approved\":true"))
        assertTrue(ComputerWire.key(3, "ctrl+alt+l", approved = false).contains("\"approved\":false"))
        assertTrue(ComputerWire.key(4, "Return", approved = true).contains("\"approved\":true"))
    }

    @Test
    fun `chaque refus du pont se dit en clair`() {
        assertEquals("Someone else has the hand.", ComputerWire.denialMessage("not_holder"))
        assertEquals("This needs your approval.", ComputerWire.denialMessage("needs_approval"))
        assertEquals("The bridge refuses this command.", ComputerWire.denialMessage("blocked"))
        assertEquals("The bridge refused this action.", ComputerWire.denialMessage("inconnu"))
    }

    @Test
    fun `la derniere action se raconte avec son verbe et son detail`() {
        val click = ComputerAction(kind = "click", detail = "bouton 1", x = 12, y = 34).text()
        assertEquals("click", click?.verb)
        assertEquals("12,34", click?.detail)

        val typed = ComputerAction(kind = "type", detail = "42").text()
        assertEquals("typing", typed?.verb)
        assertEquals("« 42 »", typed?.detail)

        assertEquals("command", ComputerAction(kind = "run", detail = "uname -a").text()?.verb)
        assertNull((null as ComputerAction?).text())
    }
}
