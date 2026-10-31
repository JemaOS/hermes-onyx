package com.onyx.app.ui.terminal

import android.content.Context
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

/** Compte SSH non secret, conservé par serveur Hermes comme `TerminalAccount` sur la référence. */
data class TerminalAccount(
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
) {
    val isComplete: Boolean get() = missing == null

    val missing: TerminalMissing?
        get() = when {
            host.trim().isEmpty() -> TerminalMissing.HOST
            port !in 1..65_535 -> TerminalMissing.PORT
            user.trim().isEmpty() -> TerminalMissing.USER
            else -> null
        }

    companion object {
        fun suggestedHost(serve: String, gateway: String): String =
            host(serve) ?: host(gateway).orEmpty()

        private fun host(raw: String): String? =
            runCatching { URI(raw).host }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}

enum class TerminalMissing(val words: String) {
    HOST("the server's host name"),
    PORT("a port (22, unless your server uses another)"),
    USER("the user: the account you log in to on the server, such as hermes"),
}

class TerminalAccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("onyx_terminal_accounts", Context.MODE_PRIVATE)

    fun saved(server: String): TerminalAccount? {
        if (!prefs.contains("$server.host")) return null
        return TerminalAccount(
            host = prefs.getString("$server.host", "").orEmpty(),
            port = prefs.getInt("$server.port", 22),
            user = prefs.getString("$server.user", "").orEmpty(),
        )
    }

    fun save(account: TerminalAccount, server: String) {
        prefs.edit()
            .putString("$server.host", account.host)
            .putInt("$server.port", account.port)
            .putString("$server.user", account.user)
            .apply()
    }
}

/** Calculs de `KnownHosts`, sans stockage : nom d'hôte et empreinte OpenSSH/SHA-256. */
object TerminalKnownHosts {
    fun name(host: String, port: Int): String =
        if (port == 22) host.lowercase() else "[${host.lowercase()}]:$port"

    fun fingerprint(openSSHLine: String): String {
        val parts = openSSHLine.trim().split(Regex("\\s+"))
        if (parts.size < 2) return openSSHLine
        val blob = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return openSSHLine
        return fingerprint(blob)
    }

    fun fingerprint(blob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().encodeToString(digest).trimEnd('=')
    }
}

enum class TerminalHostKeyStatus { TRUSTED, UNKNOWN, CHANGED }

data class TerminalHostKeyCheck(
    val status: TerminalHostKeyStatus,
    val expected: String?,
    val presented: String,
)

fun checkTerminalHostKey(expected: String?, presented: String): TerminalHostKeyCheck {
    val status = when {
        expected == null -> TerminalHostKeyStatus.UNKNOWN
        expected != presented -> TerminalHostKeyStatus.CHANGED
        else -> TerminalHostKeyStatus.TRUSTED
    }
    return TerminalHostKeyCheck(status, expected, presented)
}

/** Empreintes TOFU ; elles ne sont pas secrètes et restent indépendantes des comptes. */
class TerminalKnownHostsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("onyx_terminal_known_hosts", Context.MODE_PRIVATE)

    fun trusted(name: String): String? = prefs.getString(name, null)

    fun trust(fingerprint: String, name: String) {
        prefs.edit().putString(name, fingerprint).apply()
    }

    fun forget(name: String) {
        prefs.edit().remove(name).apply()
    }
}

object TerminalKeys {
    fun control(char: Char): Int? = when (char) {
        in 'a'..'z' -> char.code - 'a'.code + 1
        in 'A'..'Z' -> char.code - 'A'.code + 1
        else -> null
    }

    const val ESCAPE: Int = 27
    const val BACKSPACE: Int = 127
    const val TAB: Int = 9
    const val RETURN: Int = 13

    fun arrow(direction: Char): ByteArray = "\u001B[$direction".toByteArray()
}
