package com.onyx.app.ui.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TerminalSupportTest {
    @Test
    fun `le compte indique le premier champ manquant`() {
        assertEquals(TerminalMissing.HOST, TerminalAccount("", 22, "alice").missing)
        assertEquals(TerminalMissing.PORT, TerminalAccount("host", 0, "alice").missing)
        assertEquals(TerminalMissing.USER, TerminalAccount("host", 22, " ").missing)
        assertNull(TerminalAccount("host", 65_535, "alice").missing)
        assertFalse(TerminalAccount("host", 65_536, "alice").isComplete)
    }

    @Test
    fun `l alerte du terminal nomme l hote quand rien n est rempli`() {
        // Comme le modèle: rien n'est prérempli tant qu'aucun compte n'a été enregistré. Le
        // champ Host s'affiche vide, l'hôte que le serveur suggère n'est que son exemple en gris,
        // et la première alerte nomme donc le **nom d'hôte** — jamais l'utilisateur.
        val screen = File("src/main/java/com/onyx/app/ui/terminal/TerminalScreen.kt").readText()
        assertTrue(screen.contains("account = accounts.saved(server.id) ?: TerminalAccount()"))
        assertTrue(screen.contains("TerminalField(\"Host\", account.host, hostPlaceholder, hostFocus)"))
        assertTrue(screen.contains("text = \"To connect, fill in \${missing.words}.\""))
        assertEquals("the server's host name", TerminalMissing.HOST.words)
    }

    @Test
    fun `le nom known_hosts distingue le port non standard`() {
        assertEquals("hermes.example", TerminalKnownHosts.name("HERMES.example", 22))
        assertEquals("[hermes.example]:2222", TerminalKnownHosts.name("HERMES.example", 2222))
    }

    @Test
    fun `le fingerprint est celui de ssh-keygen`() {
        val line = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        assertEquals(
            "SHA256:kmYcvdi2GkPeWxB6XLjrZB8JHsy2Hm8luHMFp9GMvqk",
            TerminalKnownHosts.fingerprint(line),
        )
    }

    @Test
    fun `le dossier de depart est quote avant le shell de login`() {
        assertEquals(
            "cd '/work/it'\\''s' 2>/dev/null || printf '%s\\r\\n' " +
                "'That folder is not on this server: starting in your home folder.'; " +
                "exec \"\${SHELL:-/bin/sh}\" -l",
            SshTerminal.startCommand("/work/it's"),
        )
    }

    @Test
    fun `les touches Ctrl produisent les octets du terminal`() {
        assertEquals(3, TerminalKeys.control('c'))
        assertEquals(26, TerminalKeys.control('z'))
        assertNull(TerminalKeys.control('1'))
    }

    @Test
    fun `une seed Ed25519 restaure la meme cle publique OpenSSH`() {
        val generated = net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair()
        val private = generated.private as net.i2p.crypto.eddsa.EdDSAPrivateKey
        val expectedPublic = (generated.public as net.i2p.crypto.eddsa.EdDSAPublicKey).abyte

        val restored = SshIdentity.keyPairFromSeed(private.seed)
        val restoredPublic = (restored.public as net.i2p.crypto.eddsa.EdDSAPublicKey).abyte
        val line = SshIdentity.publicKeyLine(restored.public)

        assertArrayEquals(expectedPublic, restoredPublic)
        assertTrue(line.startsWith("ssh-ed25519 "))
        assertTrue(line.endsWith(" onyx"))
    }

    @Test
    fun `la cle du serveur est TOFU et tout changement est refuse`() {
        assertEquals(
            TerminalHostKeyStatus.UNKNOWN,
            checkTerminalHostKey(expected = null, presented = "SHA256:new").status,
        )
        assertEquals(
            TerminalHostKeyStatus.TRUSTED,
            checkTerminalHostKey(expected = "SHA256:same", presented = "SHA256:same").status,
        )
        val changed = checkTerminalHostKey(expected = "SHA256:old", presented = "SHA256:new")
        assertEquals(TerminalHostKeyStatus.CHANGED, changed.status)
        assertEquals("SHA256:old", changed.expected)
        assertEquals("SHA256:new", changed.presented)
    }
}
