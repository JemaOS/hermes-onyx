package com.onyx.app.wear

import android.content.Context
import com.onyx.app.core.R
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport

/**
 * The server configuration, as the watch keeps it.
 *
 * A watch has no full Settings screen: two fields on first launch, and that's it. The
 * same address and the same key as on the phone are enough, since the server is the same.
 */
class WearSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_wear", Context.MODE_PRIVATE)

    /** The server to query, as saved (empty on first launch). */
    fun server(): ServerConfig = ServerConfig(
        id = "wear",
        name = prefs.getString(KEY_NAME, null) ?: "Onyx",
        baseUrl = prefs.getString(KEY_URL, null).orEmpty(),
        apiKey = prefs.getString(KEY_KEY, null).orEmpty(),
        model = prefs.getString(KEY_MODEL, null).orEmpty(),
        transport = Transport.CHAT_COMPLETIONS,
        // A watch waits less time: a clear message is better than a silent wait.
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

    /** The tile icon, the same mark as on the phone. */
    companion object {
        const val MARK: Int = R.drawable.ic_onyx_graphite

        private const val KEY_URL = "base_url"
        private const val KEY_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_NAME = "name"
    }
}
