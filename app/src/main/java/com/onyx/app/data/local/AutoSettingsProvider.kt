package com.onyx.app.data.local

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.onyx.app.car.AutoSettingsContract
import com.onyx.app.data.model.Transport
import com.onyx.app.di.AppContainer
import kotlinx.coroutines.runBlocking

/**
 * Expose les habillages et la connexion OpenAI-compatible active au seul APK Auto signé comme Onyx.
 * Aucun identifiant Dashboard, historique ou autre serveur n'est inclus.
 */
class AutoSettingsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != AutoSettingsContract.METHOD_LOOKS || !AppContainer.isInstalled) return null
        // Un cold start du provider doit rendre les valeurs du disque, pas les défauts du tout premier
        // instant de AppContainer. Le thread Binder peut attendre cette petite lecture DataStore.
        runCatching {
            runBlocking {
                AppContainer.settings.load()
                AppContainer.servers.load()
            }
        }
        val settings = AppContainer.settings
        val server = AppContainer.servers.activeServer.value
        return Bundle().apply {
            putString(AutoSettingsContract.EXTRA_CARDS, settings.autoCardLook.value.id)
            putString(AutoSettingsContract.EXTRA_VOICE, settings.autoVoiceLook.value.id)
            if (server.transport == Transport.CHAT_COMPLETIONS && server.baseUrl.isNotBlank()) {
                putString(AutoSettingsContract.EXTRA_SERVER_NAME, server.displayName)
                putString(AutoSettingsContract.EXTRA_SERVER_URL, server.baseUrl)
                putString(AutoSettingsContract.EXTRA_SERVER_KEY, server.apiKey)
                putString(AutoSettingsContract.EXTRA_SERVER_MODEL, server.model)
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
