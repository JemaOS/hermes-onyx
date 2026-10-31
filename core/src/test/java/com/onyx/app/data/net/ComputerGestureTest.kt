package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce qui se touche, ce qui se glisse, et ce qui ne fait rien. */
class ComputerGestureTest {

    private val slop = 12f * 2.75f // un doigt sur un téléphone à 2,75 de densité

    @Test
    fun `un appui court et immobile est un clic`() {
        assertEquals(ComputerGesture.TAP, ComputerGestures.classify(0f, 0f, 90L, slop))
        assertEquals(ComputerGesture.TAP, ComputerGestures.classify(3f, 4f, 200L, slop))
    }

    @Test
    fun `un doigt qui bouge est un glisser`() {
        assertEquals(ComputerGesture.DRAG, ComputerGestures.classify(60f, 0f, 300L, slop))
        assertEquals(ComputerGesture.DRAG, ComputerGestures.classify(0f, -60f, 800L, slop))
        // Une diagonale compte aussi : c'est la distance, pas l'axe.
        assertEquals(ComputerGesture.DRAG, ComputerGestures.classify(40f, 40f, 300L, slop))
    }

    @Test
    fun `un appui long immobile ne devient rien`() {
        // Le doigt posé pour lire l'écran ne doit pas cliquer là où il se trouve.
        assertEquals(ComputerGesture.IGNORED, ComputerGestures.classify(2f, 2f, 900L, slop))
    }

    @Test
    fun `la tolerance suit la densite de l'ecran`() {
        assertTrue(ComputerGestures.slopPx(3f) > ComputerGestures.slopPx(1f))
        assertEquals(36f, ComputerGestures.slopPx(3f), 0.001f)
    }

    @Test
    fun `un raccourci de session demande une approbation, un raccourci ordinaire non`() {
        assertTrue(ComputerSensitivity.requiresApproval(ComputerInput.KEY, "ctrl+alt+l"))
        assertTrue(ComputerSensitivity.requiresApproval(ComputerInput.KEY, "Super+L"))
        assertFalse(ComputerSensitivity.requiresApproval(ComputerInput.KEY, "ctrl+c"))
        assertFalse(ComputerSensitivity.requiresApproval(ComputerInput.KEY, "Return"))
    }

    @Test
    fun `une commande demande toujours une approbation, les gestes jamais`() {
        assertTrue(ComputerSensitivity.requiresApproval(ComputerInput.RUN, "uname -a"))
        assertFalse(ComputerSensitivity.requiresApproval(ComputerInput.TAP, "12,34"))
        assertFalse(ComputerSensitivity.requiresApproval(ComputerInput.TEXT, "bonjour"))
        assertFalse(ComputerSensitivity.requiresApproval(ComputerInput.DRAG, "1,2 → 3,4"))
        assertFalse(ComputerSensitivity.requiresApproval(ComputerInput.SCROLL, "3 crans"))
    }

    @Test
    fun `une commande qui casserait la machine est refusee d'avance`() {
        assertTrue(ComputerSensitivity.blocked("sudo mkfs.ext4 /dev/sda1"))
        assertTrue(ComputerSensitivity.blocked("dd if=/dev/zero of=/dev/sda bs=1M"))
        assertTrue(ComputerSensitivity.blocked("rm -rf /"))
        assertTrue(ComputerSensitivity.blocked("reboot"))
        assertTrue(ComputerSensitivity.blocked(":(){ :|:& };:"))
    }

    @Test
    fun `les commandes ordinaires ne sont pas bloquees`() {
        assertFalse(ComputerSensitivity.blocked("uname -a"))
        assertFalse(ComputerSensitivity.blocked("rm -rf /tmp/onyx-essai"))
        assertFalse(ComputerSensitivity.blocked("xcalc -geometry 400x430+60+60"))
        assertFalse(ComputerSensitivity.blocked("git status"))
    }

    @Test
    fun `une adresse ecrite comme on la tape devient une URL de WebSocket`() {
        assertEquals("ws://10.0.2.2:8795/ws", ComputerAddress.websocketUrl("http://10.0.2.2:8795"))
        assertEquals("wss://pc.exemple.fr/ws", ComputerAddress.websocketUrl("https://pc.exemple.fr"))
        assertEquals("ws://10.0.2.2:8795/ws", ComputerAddress.websocketUrl("10.0.2.2:8795"))
        assertEquals("ws://10.0.2.2:8795/ws", ComputerAddress.websocketUrl("http://10.0.2.2:8795/"))
        assertEquals("ws://10.0.2.2:8795/ws", ComputerAddress.websocketUrl("ws://10.0.2.2:8795/ws"))
        assertEquals("wss://pc.exemple.fr/ordinateur/ws", ComputerAddress.websocketUrl("https://pc.exemple.fr/ordinateur"))
    }

    @Test
    fun `une adresse vide ou absurde ne donne rien`() {
        assertEquals(null, ComputerAddress.websocketUrl(""))
        assertEquals(null, ComputerAddress.websocketUrl("   "))
        assertEquals(null, ComputerAddress.websocketUrl("http://"))
        assertEquals(null, ComputerAddress.websocketUrl("https://"))
        assertEquals(null, ComputerAddress.websocketUrl("http://un hote"))
        assertEquals(null, ComputerAddress.websocketUrl("/ws"))
    }

    @Test
    fun `l'affichage enleve le schema sans toucher au reste`() {
        assertEquals("10.0.2.2:8795", ComputerAddress.display("http://10.0.2.2:8795"))
        assertEquals("10.0.2.2:8795", ComputerAddress.display("ws://10.0.2.2:8795"))
        assertEquals("pc.exemple.fr", ComputerAddress.display("wss://pc.exemple.fr/"))
        assertEquals("", ComputerAddress.display("  "))
    }
}
