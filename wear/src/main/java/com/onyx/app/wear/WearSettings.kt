package com.onyx.app.wear

import android.content.Context
import com.onyx.app.core.R
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport

/**
 * La configuration du serveur, telle que la montre la garde.
 *
 * Une montre n'a pas de grandes Réglages : deux champs au premier lancement, et c'est tout. La
 * même adresse et la même clé que sur le téléphone suffisent, puisque le serveur est le même.
 */
class WearSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_wear", Context.MODE_PRIVATE)

    /** Le serveur à interroger, tel qu'enregistré (vide au premier lancement). */
    fun server(): ServerConfig = ServerConfig(
        id = "wear",
        name = prefs.getString(KEY_NAME, null) ?: "Onyx",
        baseUrl = prefs.getString(KEY_URL, null).orEmpty(),
        apiKey = prefs.getString(KEY_KEY, null).orEmpty(),
        model = prefs.getString(KEY_MODEL, null).orEmpty(),
        transport = Transport.CHAT_COMPLETIONS,
        // Une montre attend moins longtemps : mieux vaut un message clair qu'une attente muette.
        readTimeoutSeconds = 60L,
        callTimeoutSeconds = 600L,
    )

    val isConfigured: Boolean get() = server().baseUrl.isNotBlank()

    fun save(baseUrl: String, apiKey: String, model: String) {
        prefs.edit()
            .putString(KEY_URL, baseUrl.trim())
            .putString(KEY_KEY, apiKey.trim())
            .putString(KEY_MODEL, model.trim())
            .apply()
    }

    /** L'icône de la tuile, la même marque que sur le téléphone. */
    companion object {
        const val MARK: Int = R.drawable.ic_onyx_graphite

        private const val KEY_URL = "base_url"
        private const val KEY_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_NAME = "name"
    }
}
