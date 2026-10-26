package com.onyx.app

import com.onyx.app.ui.terminal.TerminalBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tampon VT décide de ce qu'on lit dans le terminal: une séquence mal digérée et la sortie
 * d'une commande devient illisible. Ces cas couvrent ce que `ls`, `git` et `systemctl` envoient.
 */
class TerminalBufferTest {

    @Test
    fun `le texte simple s'ecrit a la suite`() {
        val buffer = TerminalBuffer(rows = 3, columns = 20)
        buffer.write("bonjour")

        assertEquals("bonjour", buffer.lines()[0])
        assertEquals(0, buffer.cursorRow)
        assertEquals(7, buffer.cursorColumn)
    }

    @Test
    fun `le retour chariot revient au debut de la ligne`() {
        val buffer = TerminalBuffer(rows = 3, columns = 20)
        buffer.write("abc\rX")

        assertEquals("Xbc", buffer.lines()[0])
    }

    @Test
    fun `un saut de ligne seul descend sans revenir a la colonne zero`() {
        val buffer = TerminalBuffer(rows = 3, columns = 20)
        buffer.write("un\ndeux")

        // C'est la règle du VT: LF descend, CR revient au début. Les coquilles envoient « \r\n ».
        assertEquals("un", buffer.lines()[0])
        assertEquals("  deux", buffer.lines()[1])
        assertEquals(1, buffer.cursorRow)
    }

    @Test
    fun `un retour chariot et un saut de ligne ensemble reviennent au debut`() {
        val buffer = TerminalBuffer(rows = 3, columns = 20)
        buffer.write("un\r\ndeux")

        assertEquals("un", buffer.lines()[0])
        assertEquals("deux", buffer.lines()[1])
    }

    @Test
    fun `au bas de l'ecran, tout remonte d'un cran`() {
        val buffer = TerminalBuffer(rows = 2, columns = 20)
        buffer.write("un\r\ndeux\r\ntrois")

        assertEquals("deux", buffer.lines()[0])
        assertEquals("trois", buffer.lines()[1])
    }

    @Test
    fun `le curseur se deplace avec CSI`() {
        val buffer = TerminalBuffer(rows = 5, columns = 20)
        buffer.write("\u001B[2;3HX")

        assertEquals("  X", buffer.lines()[1])
    }

    @Test
    fun `effacer la ligne et l'ecran`() {
        val buffer = TerminalBuffer(rows = 3, columns = 20)
        buffer.write("bonjour\u001B[2K")
        assertEquals("", buffer.lines()[0])

        buffer.write("a\nb\u001B[2J")
        assertEquals("", buffer.lines()[0])
        assertEquals("", buffer.lines()[1])
    }

    @Test
    fun `les couleurs SGR sont retenues par cellule`() {
        val buffer = TerminalBuffer(rows = 2, columns = 20)
        buffer.write("\u001B[31mrouge\u001B[0mnormal")

        assertEquals(1, buffer.cell(0, 0).style.foreground)
        assertEquals(-1, buffer.cell(0, 5).style.foreground)
    }

    @Test
    fun `une sequence coupee entre deux morceaux est reprise`() {
        val buffer = TerminalBuffer(rows = 3, columns = 20)
        buffer.write("\u001B[3")
        buffer.write("1mrouge")

        assertEquals(1, buffer.cell(0, 0).style.foreground)
        assertEquals("rouge", buffer.lines()[0])
    }

    @Test
    fun `les sequences privees sont ignorees sans casser le parseur`() {
        val buffer = TerminalBuffer(rows = 2, columns = 20)
        buffer.write("\u001B[?25lbonjour")

        assertTrue(buffer.lines()[0].startsWith("bonjour"))
    }

    @Test
    fun `le texte complet se copie sans espaces de fin`() {
        val buffer = TerminalBuffer(rows = 2, columns = 10)
        buffer.write("a\r\nbb")

        assertEquals("a\nbb", buffer.text())
    }
}
