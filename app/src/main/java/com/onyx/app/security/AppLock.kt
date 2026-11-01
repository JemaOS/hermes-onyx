package com.onyx.app.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import java.security.MessageDigest
import java.security.SecureRandom

/** Combien de temps après avoir quitté l'app il faut se réauthentifier. */
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
 * Le verrou de l'app : la biométrie, et un code en secours.
 *
 * Miroir de `AppLock` côté référence : on demande le visage ou le doigt au retour dans l'app,
 * après le délai choisi. Le code est le secours d'Android quand la biométrie n'est pas
 * disponible — il est salé et haché, jamais gardé en clair.
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

    /** Enregistre un code de 4 à 12 chiffres ; une valeur vide l'efface. */
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

    /** Faut-il verrouiller, [leftAtAt] étant le moment où l'app est passée en arrière-plan ? */
    fun shouldLock(leftAtAt: Long): Boolean {
        if (!isEnabled) return false
        if (leftAtAt == 0L) return true
        return System.currentTimeMillis() - leftAtAt >= delay.millis
    }

    /**
     * Ce que la biométrie du téléphone sait faire, ou `null` si elle ne peut pas.
     *
     * La vérification est faite **avant** d'offrir un bouton : un téléphone sans empreinte ni visage
     * enrôlés, ou dont le matériel ne peut pas répondre, laissait sinon un bouton sans effet. Le nom
     * suit ce que l'appareil a vraiment (`Fingerprint`, `Face unlock`), sans emprunter le vocabulaire
     * de la référence, qui n'a pas cours ici.
     */
    fun biometryLabel(context: Context): String? {
        if (!canAuthenticate(context)) return null
        val features = context.packageManager
        val face = features.hasSystemFeature(PackageManager.FEATURE_FACE)
        val finger = features.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        // **Le visage ne se déduit pas d'un `hasSystemFeature`.** Beaucoup de téléphones
        // reconnaissent le visage sans publier `FEATURE_FACE` (il n'existe que pour la classe
        // standard) : le libellé annonçait donc « Exiger l'empreinte » sur un appareil qui a les
        // deux, et le visage semblait absent. Seule l'absence **certaine** de visage (une empreinte
        // publiée, et rien d'autre) autorise le libellé « empreinte ».
        return when {
            finger && !face -> "Fingerprint"
            else -> "Face or fingerprint"
        }
    }

    /** Vrai si le téléphone a une biométrie utilisable : matériel présent **et** classe enrôlée. */
    fun canAuthenticate(context: Context): Boolean {
        // **Une classe à la fois.** `canAuthenticate(FORT or FAIBLE)` est une **combinaison** que
        // l'API évalue littéralement : sur les appareils où seule la reconnaissance faciale (classe
        // faible) est enrôlée, elle rend `BIOMETRIC_ERROR_NONE_ENROLLED` — l'app se croyait sans
        // biométrie alors que le téléphone en avait une (« l'empreinte ne fonctionne pas »). On
        // demande donc chaque classe séparément, et l'identifiant de l'appareil en dernier recours.
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
         * Le fort **et** le faible : beaucoup d'appareils (Honor, Xiaomi) ne proposent que les
         * classes fortes, et le faible seul les laissait sans réponse.
         */
        val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

        const val KEY_ENABLED = "enabled"
        const val KEY_DELAY = "delay_millis"
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
    }
}
