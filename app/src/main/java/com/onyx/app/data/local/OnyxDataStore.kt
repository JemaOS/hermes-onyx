package com.onyx.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.onyx.app.data.model.Conversation
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Le magasin des conversations: un seul fichier JSON, réécrit en entier et de façon atomique par
 * DataStore.
 *
 * Comme , qui tient un fichier d'index et un fichier par
 * conversation; ici tout tient dans une liste, l'historique restant petit.
 *
 * Une seule instance par processus: `DataStoreFactory` refuse deux magasins sur le même fichier,
 * donc [com.onyx.app.di.AppContainer] la garde.
 */
fun conversationsDataStore(context: Context): DataStore<List<Conversation>> =
    DataStoreFactory.create(
        serializer = ConversationSerializer(),
        produceFile = { File(context.applicationContext.filesDir, "conversations.json") },
    )

/** Réglages: petits, donc `Preferences`. */
val Context.onyxPreferences: DataStore<Preferences> by preferencesDataStore(name = "onyx_settings")

/** Clés de `onyx_settings`. */
object SettingsKeys {
    /** Liste des serveurs, en JSON, sans les clés API (celles-ci vivent dans le Keystore). */
    val SERVERS = stringPreferencesKey("servers")

    /** Identifiant du serveur actif. */
    val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")

    /** Thème choisi (`OnyxTheme.id`). */
    val THEME = stringPreferencesKey("theme")

    /** Apparence: `system`, `light` ou `dark`. */
    val APPEARANCE = stringPreferencesKey("appearance")

    /** Ce que le widget affiche de la fenêtre de contexte, écrit à la fin de chaque tour. */
    val WIDGET_CONTEXT_USED = intPreferencesKey("widget_context_used")
    val WIDGET_CONTEXT_WINDOW = intPreferencesKey("widget_context_window")
}

/** Sérialise la liste des conversations en JSON, sans passer par un format binaire. */
class ConversationSerializer(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) : Serializer<List<Conversation>> {

    override val defaultValue: List<Conversation> = emptyList()

    override suspend fun readFrom(input: InputStream): List<Conversation> {
        val text = input.readBytes().decodeToString()
        if (text.isBlank() || text == EMPTY_LITERAL) return defaultValue
        return runCatching {
            json.decodeFromString(ListSerializer(Conversation.serializer()), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: List<Conversation>, output: OutputStream) {
        val text = if (t.isEmpty()) EMPTY_LITERAL else json.encodeToString(ListSerializer(Conversation.serializer()), t)
        output.write(text.encodeToByteArray())
        output.flush()
    }

    private companion object {
        /** Un fichier vide n'est pas du JSON valide: on écrit `[]` pour une liste vide. */
        const val EMPTY_LITERAL = "[]"
    }
}
