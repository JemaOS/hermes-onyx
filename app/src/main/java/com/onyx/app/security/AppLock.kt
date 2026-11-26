package com.onyx.app.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import java.security.MessageDigest
import java.security.SecureRandom

/** How long after leaving the app you have to re-authenticate. */
enum class LockDelay(val millis: Long, val label: String) {
    IMMEDIATELY(0L, "Immediately"),
    ONE_MINUTE(60_000L, "1 minute"),
    FIVE_MINUTES(5 * 60_000L, "5 minutes"),
    FIFTEEN_MINUTES(15 * 60_000L, "15 minutes"),
    ONE_HOUR(60 * 60_000L, "1 hour"),
    ;

    companion object {
        fun fromMillis(value: Long?): LockDelay = entries.firstOrNull { it.millis == value } ?: IMMEDIATELY
    }
}

/**
 * The app lock: biometrics, and a fallback code.
 *
 * Mirror of `AppLock` on the reference side: the face or the finger is requested on return to the app,
 * after the chosen delay. The code is Android's fallback when biometrics are not
 * available — it is salted and hashed, never kept in clear.
 */
class AppLock(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_lock", Context.MODE_PRIVATE)

    val isEnabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false)

    val delay: LockDelay get() = LockDelay.fromMillis(prefs.getLong(KEY_DELAY, 0L))

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setDelay(value: LockDelay) {
        prefs.edit().putLong(KEY_DELAY, value.millis).apply()
    }

    val hasPin: Boolean get() = prefs.contains(KEY_HASH)

    /** Stores a code of 4 to 12 digits; an empty value erases it. */
    fun setPin(pin: String) {
        if (pin.isEmpty()) {
            prefs.edit().remove(KEY_HASH).remove(KEY_SALT).apply()
            return
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.hexToBytes() ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())
    }

    /** Should it lock, [leftAtAt] being the moment the app went to the background? */
    fun shouldLock(leftAtAt: Long): Boolean {
        if (!isEnabled) return false
        if (leftAtAt == 0L) return true
        return System.currentTimeMillis() - leftAtAt >= delay.millis
    }

    /**
     * What the phone's biometrics can do, or `null` if they cannot.
     *
     * The check is made **before** offering a button: a phone without enrolled fingerprint or face,
     * or whose hardware cannot answer, otherwise left a button with no effect. The name
     * follows what the device really has (`Fingerprint`, `Face unlock`), without borrowing the vocabulary
     * of the reference, which does not apply here.
     */
    fun biometryLabel(context: Context): String? {
        if (!canAuthenticate(context)) return null
        val features = context.packageManager
        val face = features.hasSystemFeature(PackageManager.FEATURE_FACE)
        val finger = features.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        // **The face is not deduced from a `hasSystemFeature`.** Many phones
        // recognize the face without publishing `FEATURE_FACE` (it exists only for the standard
        // class): the label therefore announced "Exiger l'empreinte" ("Require fingerprint") on a device that has
        // both, and the face seemed absent. Only the **certain** absence of face (a fingerprint
        // published, and nothing else) allows the "fingerprint" label.
        return when {
            finger && !face -> "Fingerprint"
            else -> "Face or fingerprint"
        }
    }

    /** True if the phone has usable biometrics: hardware present **and** class enrolled. */
    fun canAuthenticate(context: Context): Boolean {
        // **One class at a time.** `canAuthenticate(FORT or FAIBLE)` is a **combination** that
        // the API evaluates literally: on devices where only face recognition (weak
        // class) is enrolled, it returns `BIOMETRIC_ERROR_NONE_ENROLLED` — the app believed
        // itself without biometrics while the phone had one ("l'empreinte ne fonctionne pas",
        // "the fingerprint doesn't work"). Each class separately, device credential as a last resort.
        val manager = BiometricManager.from(context)
        fun ok(authenticators: Int) =
            manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        return ok(BiometricManager.Authenticators.BIOMETRIC_STRONG) ||
            ok(BiometricManager.Authenticators.BIOMETRIC_WEAK) ||
            ok(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray())
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        /**
         * The strong **and** the weak: many devices (Honor, Xiaomi) offer only the
         * strong classes, and the weak one alone left them without an answer.
         */
        val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

        const val KEY_ENABLED = "enabled"
        const val KEY_DELAY = "delay_millis"
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
    }
}
