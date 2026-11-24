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
 * The conversation store: a single JSON file, rewritten whole and atomically by
 * DataStore.
 *
 * Mirrors `ConversationStore` on the reference side, which keeps an index file and one file per
 * conversation; here everything fits in one list, the history staying small.
 *
 * A single instance per process: `DataStoreFactory` rejects two stores on the same file,
 * so [com.onyx.app.di.AppContainer] keeps it.
 */
fun conversationsDataStore(context: Context): DataStore<List<Conversation>> =
    DataStoreFactory.create(
        serializer = ConversationSerializer(),
        produceFile = { conversationsFile(context) },
    )

/** The file of the **local copy** of the conversations — a single one, rewritten whole. */
fun conversationsFile(context: Context): File =
    File(context.applicationContext.filesDir, "conversations.json")

/**
 * **When the local copy was last saved** (milliseconds since the epoch),
 * or `null` if this phone has not saved anything yet.
 *
 * It is the date carried by the pane's status line when the gateway no longer responds: “Copie
 * enregistrée · il y a 9 h.” (copy saved · 9 h ago) — and it comes from the **file itself**, not from a
 * session clock. A session date would reset on every app start and would say “il y a
 * 2 secondes” (2 seconds ago) of a copy three days old: exactly what must not happen.
 */
fun savedCopyAtMs(context: Context): Long? {
    val file = conversationsFile(context)
    return if (file.isFile) file.lastModified().takeIf { it > 0L } else null
}

/** Settings: small, therefore `Preferences`. */
val Context.onyxPreferences: DataStore<Preferences> by preferencesDataStore(name = "onyx_settings")

/** Keys of `onyx_settings`. */
object SettingsKeys {
    /** List of servers, as JSON, without the API keys (those live in the Keystore). */
    val SERVERS = stringPreferencesKey("servers")

    /** Identifier of the active server. */
    val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")

    /** Chosen theme (`OnyxTheme.id`). */
    val THEME = stringPreferencesKey("theme")

    /** Appearance: `system`, `light` or `dark`. */
    val APPEARANCE = stringPreferencesKey("appearance")

    /**
     * The insights period: 7, 30 or 90 days.
     *
     * It is remembered **per device**, like the other interface settings: reopening the
     * insights finds back the period one was looking at, instead of always returning to the month.
     */
    val INSIGHTS_PERIOD = intPreferencesKey("insights_period_days")

    /**
     * **The pane's shortcut grid**, as the person composed it: the identifiers of the
     * displayed tiles, in its order, separated by a comma (see `SidebarTileLayout`).
     *
     * A **dedicated** key: this setting shares its place with no other, so that a value
     * already saved by an earlier version is never invalidated by another setting. Key
     * absent = the original grid (the six tiles of the reference).
     */
    val SIDEBAR_TILES = stringPreferencesKey("sidebar_tiles")

    /** What the widget displays of the context window, written at the end of each turn. */
    val WIDGET_CONTEXT_USED = intPreferencesKey("widget_context_used")
    val WIDGET_CONTEXT_WINDOW = intPreferencesKey("widget_context_window")
}

/** Serializes the conversation list as JSON, without going through a binary format. */
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
        /** An empty file is not valid JSON: `[]` is written for an empty list. */
        const val EMPTY_LITERAL = "[]"
    }
}
