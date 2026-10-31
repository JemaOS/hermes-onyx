package com.onyx.app.ui.terminal

import android.content.Context
import com.onyx.app.data.local.SecretStore
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.PublicKey
import java.util.Base64

/** Clé Ed25519 de ce téléphone ; la seed privée est chiffrée par le Keystore Android. */
class SshIdentity(context: Context) {
    private val secrets = SecretStore(context.applicationContext)

    fun keyPair(): KeyPair? {
        val stored = secrets.get(KEY_ID)
        if (stored.isNotEmpty()) {
            val restored = runCatching {
                keyPairFromSeed(Base64.getDecoder().decode(stored))
            }.getOrNull()
            if (restored != null) return restored
            secrets.remove(KEY_ID)
        }
        val generated = net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair()
        val seed = (generated.private as EdDSAPrivateKey).seed
        val encoded = Base64.getEncoder().encodeToString(seed)
        secrets.put(KEY_ID, encoded)
        // SecretStore ne fait pas semblant d'avoir écrit : une clé non conservée n'est pas utilisée.
        if (secrets.get(KEY_ID) != encoded) return null
        return generated
    }

    fun publicKeyLine(): String? = keyPair()?.let { publicKeyLine(it.public) }

    fun replace(): String? {
        secrets.remove(KEY_ID)
        return publicKeyLine()
    }

    companion object {
        private const val KEY_ID = "terminal.ssh.ed25519"

        fun keyPairFromSeed(seed: ByteArray): KeyPair {
            val params = EdDSANamedCurveTable.ED_25519_CURVE_SPEC
            val private = EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, params))
            val public = EdDSAPublicKey(EdDSAPublicKeySpec(private.a, params))
            return KeyPair(public, private)
        }

        fun publicKeyLine(publicKey: PublicKey): String {
            require(publicKey is EdDSAPublicKey) { "Ed25519 key required" }
            val algorithm = "ssh-ed25519".toByteArray(Charsets.US_ASCII)
            val body = ByteArrayOutputStream()
            DataOutputStream(body).use { output ->
                output.writeInt(algorithm.size)
                output.write(algorithm)
                output.writeInt(publicKey.abyte.size)
                output.write(publicKey.abyte)
            }
            return "ssh-ed25519 " + Base64.getEncoder().encodeToString(body.toByteArray()) + " onyx"
        }
    }
}
