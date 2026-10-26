package com.onyx.app.ui.terminal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyPair
import java.security.PublicKey

/** Où se connecter: coquille de login dans ce dossier, avec la clé du téléphone uniquement. */
data class TerminalTarget(
    val host: String,
    val port: Int = 22,
    val user: String,
    val folder: String? = null,
)

sealed class TerminalProblem(message: String) : Exception(message) {
    data object NoKey : TerminalProblem("This device couldn't make or keep its SSH key. Unlock it and try again.")
    data class UnknownServer(val fingerprint: String) : TerminalProblem(
        "This device hasn't met this server before (its key is $fingerprint).",
    )
    data class ServerKeyChanged(val expected: String, val presented: String) : TerminalProblem(
        "The server's key has changed since you trusted it (was $expected, now $presented). " +
            "If the server was reinstalled, forget its old key in Settings › Terminal and connect again; " +
            "if not, someone may be in the way.",
    )
    data object KeyNotAccepted : TerminalProblem(
        "The server didn't accept this device's key. Add the key below to ~/.ssh/authorized_keys for that user.",
    )
    data class Unreachable(val reason: String) : TerminalProblem("Couldn't reach the server: $reason")
    data object NoShell : TerminalProblem("The server didn't start a shell.")

    val wantsKey: Boolean get() = this is KeyNotAccepted
}

/**
 * Session SSH réelle de `SSHShell`: vérification TOFU de la clé du serveur, clé Ed25519 du
 * téléphone, PTY, flux continu dans les deux sens, redimensionnement et fin de coquille.
 */
class SshTerminal(private val knownHosts: TerminalKnownHostsStore) {
    private var client: SSHClient? = null
    private var session: Session? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var generation = 0L

    val isConnected: Boolean get() = session?.isOpen == true
    val currentGeneration: Long get() = generation

    suspend fun connect(
        target: TerminalTarget,
        keyPair: KeyPair,
        columns: Int = 120,
        rows: Int = 40,
    ): Long = withContext(Dispatchers.IO) {
        disconnect()
        var ssh: SSHClient? = null
        var hostProblem: TerminalProblem? = null
        try {
            val openedSsh = SSHClient().also { ssh = it }
            openedSsh.connectTimeout = 10_000
            openedSsh.addHostKeyVerifier(
                object : HostKeyVerifier {
                    override fun verify(host: String, port: Int, publicKey: PublicKey): Boolean {
                        val presented = fingerprint(publicKey)
                        val expected = knownHosts.trusted(TerminalKnownHosts.name(host, port))
                        return when (checkTerminalHostKey(expected, presented).status) {
                            TerminalHostKeyStatus.TRUSTED -> true
                            TerminalHostKeyStatus.UNKNOWN -> {
                                hostProblem = TerminalProblem.UnknownServer(presented)
                                false
                            }
                            TerminalHostKeyStatus.CHANGED -> {
                                hostProblem = TerminalProblem.ServerKeyChanged(expected.orEmpty(), presented)
                                false
                            }
                        }
                    }

                    override fun findExistingAlgorithms(host: String, port: Int): List<String> = emptyList()
                },
            )
            openedSsh.connect(target.host, target.port)
            hostProblem?.let { throw it }
            openedSsh.authPublickey(target.user, KeyPairWrapper(keyPair))
            val opened = openedSsh.startSession()
            opened.allocatePTY(
                "xterm-256color",
                columns.coerceAtLeast(20),
                rows.coerceAtLeast(5),
                0,
                0,
                emptyMap(),
            )
            try {
                if (target.folder.isNullOrEmpty()) opened.startShell() else opened.exec(startCommand(target.folder!!))
            } catch (error: Exception) {
                opened.close()
                throw TerminalProblem.NoShell
            }
            session = opened
            client = openedSsh
            input = opened.inputStream
            output = opened.outputStream
            generation += 1
            generation
        } catch (error: CancellationException) {
            runCatching { ssh?.disconnect() }
            throw error
        } catch (error: Exception) {
            runCatching { ssh?.disconnect() }
            val problem = hostProblem
            when {
                problem != null -> throw problem
                error is TerminalProblem -> throw error
                error is UserAuthException -> throw TerminalProblem.KeyNotAccepted
                else -> throw TerminalProblem.Unreachable(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    /** Lit sans attendre une commande: shells interactifs, prompts et programmes plein écran. */
    suspend fun read(
        expectedGeneration: Long,
        onOutput: suspend (String) -> Unit,
        onEnded: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val reader = input ?: return@withContext
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val bytes = ByteArray(8_192)
        try {
            while (expectedGeneration == generation) {
                val count = reader.read(bytes)
                if (count < 0) break
                if (count == 0) continue
                val chars = CharBuffer.allocate(count)
                decoder.decode(ByteBuffer.wrap(bytes, 0, count), chars, false)
                chars.flip()
                if (chars.hasRemaining() && expectedGeneration == generation) {
                    onOutput(chars.toString())
                }
            }
        } catch (_: IOException) {
            // La fermeture du flux est aussi la façon dont disconnect réveille cette lecture.
        }
        if (expectedGeneration == generation) {
            val status = (session as? SessionChannel)?.exitStatus
            onEnded(status?.let { "The shell ended (exit status $it)." } ?: "The connection closed.")
        }
    }

    suspend fun send(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val writer = output ?: throw TerminalProblem.NoShell
        try {
            writer.write(bytes)
            writer.flush()
        } catch (error: IOException) {
            throw TerminalProblem.Unreachable(error.message ?: "the connection closed.")
        }
    }

    suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        runCatching {
            (session as? SessionChannel)?.changeWindowDimensions(
                columns.coerceAtLeast(20),
                rows.coerceAtLeast(5),
                0,
                0,
            )
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) { close() }

    /** Fermeture synchrone pour `onDispose`; sshj libère ensuite son thread de lecture. */
    fun close() {
        generation += 1
        runCatching { session?.close() }
        runCatching { client?.disconnect() }
        session = null
        client = null
        input = null
        output = null
    }

    private fun fingerprint(publicKey: PublicKey): String {
        val packet = SSHPacket()
        KeyType.fromKey(publicKey).putPubKeyIntoBuffer(publicKey, packet)
        return TerminalKnownHosts.fingerprint(packet.compactData)
    }

    companion object {
        /** Même commande que `SSHShell.startCommand`: `cd` sûr, puis le shell de login. */
        fun startCommand(folder: String): String {
            val quoted = "'" + folder.replace("'", "'\\''") + "'"
            return "cd $quoted 2>/dev/null || printf '%s\\r\\n' " +
                "'That folder is not on this server: starting in your home folder.'; " +
                "exec \"\${SHELL:-/bin/sh}\" -l"
        }
    }
}
