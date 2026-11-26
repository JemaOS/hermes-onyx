package com.onyx.app.auto

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.core.net.toUri
import com.onyx.app.car.AutoCardLook
import com.onyx.app.car.AutoSettingsContract
import com.onyx.app.car.AutoVoiceLook
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport

/** Reads the phone when both apps are installed; otherwise keeps the defaults and the local setting. */
class AutoSettingsBridge(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private var cached: Bundle? = null
    private var cachedAt = 0L

    data class Looks(val cards: AutoCardLook, val voice: AutoVoiceLook)

    fun looks(): Looks {
        val result = snapshot()
        return Looks(
            cards = AutoCardLook.fromId(result?.getString(AutoSettingsContract.EXTRA_CARDS)),
            voice = AutoVoiceLook.fromId(result?.getString(AutoSettingsContract.EXTRA_VOICE)),
        )
    }

    /** Only the transport that ChatCompletionsClient can actually serve in Auto is carried over. */
    fun server(): ServerConfig? {
        val result = snapshot() ?: return null
        val url = result.getString(AutoSettingsContract.EXTRA_SERVER_URL)?.takeIf { it.isNotBlank() }
            ?: return null
        return ServerConfig(
            id = "phone",
            name = result.getString(AutoSettingsContract.EXTRA_SERVER_NAME) ?: "Onyx",
            baseUrl = url,
            apiKey = result.getString(AutoSettingsContract.EXTRA_SERVER_KEY).orEmpty(),
            model = result.getString(AutoSettingsContract.EXTRA_SERVER_MODEL).orEmpty(),
            transport = Transport.CHAT_COMPLETIONS,
            readTimeoutSeconds = 60L,
            callTimeoutSeconds = 600L,
        )
    }

    @Synchronized
    private fun snapshot(): Bundle? {
        val now = SystemClock.uptimeMillis()
        if (cachedAt > 0 && now - cachedAt < CACHE_MS) return cached
        cached = runCatching {
            resolver.call(
                "content://${AutoSettingsContract.AUTHORITY}".toUri(),
                AutoSettingsContract.METHOD_LOOKS,
                null,
                null,
            )
        }.getOrNull()
        cachedAt = now.coerceAtLeast(1L)
        return cached
    }

    private companion object {
        const val CACHE_MS = 2_000L
    }
}
