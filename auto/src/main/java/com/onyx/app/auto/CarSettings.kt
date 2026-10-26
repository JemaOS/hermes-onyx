package com.onyx.app.auto

import android.content.Context
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport

/** La voiture reprend le serveur OpenAI-compatible actif du téléphone, avec repli local. */
class CarSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_car", Context.MODE_PRIVATE)
    private val bridge = AutoSettingsBridge(context)

    fun server(): ServerConfig = bridge.server() ?: ServerConfig(
        id = "car",
        name = prefs.getString(KEY_NAME, null) ?: "Onyx",
        baseUrl = prefs.getString(KEY_URL, null).orEmpty(),
        apiKey = prefs.getString(KEY_KEY, null).orEmpty(),
        model = prefs.getString(KEY_MODEL, null).orEmpty(),
        transport = Transport.CHAT_COMPLETIONS,
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

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_NAME = "name"
    }
}
