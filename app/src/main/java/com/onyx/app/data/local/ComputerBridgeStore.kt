package com.onyx.app.data.local

import android.content.Context

/**
 * Where to reach the "Computer" ("Ordinateur") bridge, and with which token.
 *
 * The address is not a secret: it lives in the preferences, like the TTS server address.
 * The **token**, however, is a password for the PC: it goes through [SecretStore], hence through the
 * Keystore, and it is never rewritten elsewhere. An empty token erases the entry.
 */
class ComputerBridgeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("onyx_computer", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)

    /**
     * The token kept **in memory** when the keystore refused to keep it.
     *
     * A Keystore can be unavailable or lose its key (and `SecretStore` then erases the entry
     * that became unreadable, on purpose). Losing the token silently would force retyping it without knowing
     * why: it therefore stays in memory for the session, and the screen says so.
     */
    private var memoryToken: String = ""

    /** True when the keystore refused the token on save. */
    private var refusedAtSave: Boolean = false

    /**
     * True when the keystore does not hold the token — on save, or later: a lost Keystore
     * key makes the entry unreadable, and the store then erases it. In both cases
     * the screen says so, rather than letting one believe the address was forgotten.
     */
    val keystoreRefused: Boolean
        get() = refusedAtSave ||
            (prefs.getBoolean(KEY_HAD_TOKEN, false) && secrets.get(KEY_TOKEN).isEmpty() && memoryToken.isEmpty())

    /** The bridge address, as entered ("http://10.0.2.2:8795"). */
    var address: String
        get() = prefs.getString(KEY_ADDRESS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ADDRESS, value.trim()).apply()

    /** The bridge token — in the Keystore, never in the preferences. */
    var token: String
        get() = secrets.get(KEY_TOKEN).ifEmpty { memoryToken }
        set(value) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                secrets.remove(KEY_TOKEN)
                memoryToken = ""
                refusedAtSave = false
                prefs.edit().remove(KEY_HAD_TOKEN).apply()
                return
            }
            secrets.put(KEY_TOKEN, trimmed)
            refusedAtSave = secrets.get(KEY_TOKEN) != trimmed
            memoryToken = trimmed
            prefs.edit().putBoolean(KEY_HAD_TOKEN, true).apply()
        }

    /** True when both are there: only the connection is missing. */
    val configured: Boolean get() = address.isNotBlank() && token.isNotEmpty()

    /** The token as it is displayed: never in clear, and all one knows is whether there is one. */
    val maskedToken: String get() = if (token.isEmpty()) "" else MASK

    fun clear() {
        secrets.remove(KEY_TOKEN)
        memoryToken = ""
        refusedAtSave = false
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_HAD_TOKEN).apply()
    }

    private companion object {
        const val KEY_ADDRESS = "bridge_address"
        const val KEY_TOKEN = "computer-bridge-token"
        /** A token was saved: if it no longer reads back, it is the keystore, not memory. */
        const val KEY_HAD_TOKEN = "bridge_had_token"
        const val MASK = "••••••••"
    }
}
