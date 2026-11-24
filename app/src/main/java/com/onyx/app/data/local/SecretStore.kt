package com.onyx.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The secrets (API keys) encrypted by the Android Keystore — the reference Keychain equivalent.
 *
 * An AES-256 key lives in the hardware Keystore and never leaves it; only the initialization
 * vector and the ciphertext are written to the preferences. The file is excluded from
 * backups (`backup_rules.xml`), as Keychain entries can be.
 */
class SecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Stores a secret; an empty value erases the entry. */
    fun put(id: String, secret: String) {
        if (secret.isEmpty()) {
            remove(id)
            return
        }
        val encrypted = runCatching { encrypt(secret) }.getOrNull() ?: return
        prefs.edit().putString(key(id), encrypted).apply()
    }

    /** Reads a secret back; returns `""` if the entry is gone or no longer decrypts. */
    fun get(id: String): String {
        val stored = prefs.getString(key(id), null) ?: return ""
        val plain = runCatching { decrypt(stored) }.getOrNull()
        if (plain == null) {
            // The Keystore key changed (uninstall, restore): the entry is dead.
            remove(id)
            return ""
        }
        return plain
    }

    fun remove(id: String) {
        prefs.edit().remove(key(id)).apply()
    }

    /** Erases **all** secrets on this phone — the Settings' “Erase everything”. */
    fun clearAll() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun key(id: String) = "$PREFIX$id"

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(iv, Base64.NO_WRAP) + SEPARATOR + Base64.encodeToString(body, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(SEPARATOR)
        require(parts.size == 2) { "secret illisible" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val body = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "onyx.api.keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
        const val PREFS_NAME = "onyx_secure_prefs"
        const val PREFIX = "secret_"
    }
}
