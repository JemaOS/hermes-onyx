package com.onyx.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.onyx.app.data.local.SettingsKeys
import com.onyx.app.data.local.SecretStore
import com.onyx.app.data.model.CustomHeader
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The app's known servers and the active one.
 *
 * Android equivalent of `HermesServer` + the reference's active server (`Settings.activeServer`).
 * The list is written as JSON in the preferences; the API keys never appear there: they
 * go to the Keystore ([SecretStore]) and are recomposed on read.
 */
class ServerRepository(
    private val store: DataStore<Preferences>,
    private val secrets: SecretStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val _activeId = MutableStateFlow("")
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    private val _activeServer = MutableStateFlow(DEFAULT_SERVER)
    val activeServer: StateFlow<ServerConfig> = _activeServer.asStateFlow()

    /** Reads the saved list and the active server's API key. */
    suspend fun load() {
        val stored = withContext(Dispatchers.IO) {
            val preferences = store.data.first()
            val raw = preferences[SettingsKeys.SERVERS]
            val decoded = raw
                ?.let { text -> runCatching { json.decodeFromString(ServerListSerializer, text) }.getOrNull() }
                .orEmpty()
            decoded to preferences[SettingsKeys.ACTIVE_SERVER_ID]
        }
        // The default name changed with the rename: a server still called “Onyx” is
        // the old default name, not a choice — it becomes “Onyx” on load, not only
        // when its sheet is reopened.
        val migrated = stored.first.ifEmpty { listOf(DEFAULT_SERVER) }.map { server ->
            if (server.name.trim().equals("Onyx", ignoreCase = true)) server.copy(name = "Onyx") else server
        }
        val servers = migrated.map { withSecret(it) }
        if (migrated != stored.first) {
            _servers.value = migrated.map { withSecret(it) }
            runCatching { persist() }
        }
        _servers.value = servers
        _activeId.value = stored.second?.takeIf { id -> servers.any { it.id == id } } ?: servers.first().id
        refreshActive()
    }

    /**
     * Adds or replaces a server; the API key goes into the Keystore.
     *
     * A saved or corrected server **becomes the active server**: it is the gesture
     * “je configure cette passerelle-là” (I am configuring this gateway), and the app must answer the one just
     * entered. Without that, the just-modified server stays in the list and
     * the app keeps talking to the old one — whose address is stale — hence the screen
     * “No server set up yet” or an empty bot list while the test passes.
     *
     * [activate] is `false` when the caller wants to keep another server active
     * (importing a configuration code restores the previous server).
     */
    suspend fun upsert(config: ServerConfig, activate: Boolean = true) {
        val clean = config.copy(
            // The default name changed with the rename: a server still called “Onyx”
            // is the old default name, not a choice — it displays as “Onyx” in Settings.
            name = config.name.trim().let { if (it.equals("Onyx", ignoreCase = true)) "Onyx" else it },
            apiKey = config.apiKey.trim(),
            profileKey = config.profileKey.trim(),
            accessSecret = config.accessSecret.trim(),
            customHeaders = config.customHeaders.filter { it.isUsable },
        )
        _servers.value = _servers.value.let { current ->
            val index = current.indexOfFirst { it.id == clean.id }
            if (index < 0) current + clean else current.toMutableList().also { it[index] = clean }
        }
        if (activate) _activeId.value = clean.id
        refreshActive()
        persist()
    }

    /** Removes a server; if it was active, the active one becomes the first remaining. */
    suspend fun remove(id: String) {
        val remaining = _servers.value.filterNot { it.id == id }
        _servers.value = remaining.ifEmpty { listOf(DEFAULT_SERVER) }
        if (_activeId.value == id) _activeId.value = _servers.value.first().id
        refreshActive()
        persist()
    }

    /** Sets the active server; no effect if the identifier is unknown. */
    suspend fun setActive(id: String) {
        if (_servers.value.none { it.id == id }) return
        _activeId.value = id
        refreshActive()
        store.edit { it[SettingsKeys.ACTIVE_SERVER_ID] = id }
    }

    /** Creates a stable, readable server identifier. */
    fun newId(): String = "srv-" + UUID.randomUUID().toString().take(8)

    /**
     * Forgets **all** servers, their keys and their identifiers, and returns to the factory
     * server: the Settings' “Erase everything”, which touches nothing on the servers.
     */
    suspend fun eraseAll() {
        secrets.clearAll()
        _servers.value = listOf(DEFAULT_SERVER)
        _activeId.value = DEFAULT_SERVER.id
        refreshActive()
        persist()
    }

    private suspend fun persist() {
        withContext(Dispatchers.IO) {
            _servers.value.forEach { server ->
                secrets.put(server.id, server.apiKey.trim())
                secrets.put("${server.id}.profile", server.profileKey.trim())
                secrets.put("${server.id}.access", server.accessSecret.trim())
                secrets.put(
                    "${server.id}.headers",
                    if (server.customHeaders.isEmpty()) "" else json.encodeToString(HeaderListSerializer, server.customHeaders),
                )
            }
            val payload = json.encodeToString(
                ServerListSerializer,
                _servers.value.map {
                    it.copy(
                        apiKey = "",
                        profileKey = "",
                        accessSecret = "",
                        customHeaders = it.customHeaders.map { header -> header.copy(value = "") },
                    )
                },
            )
            store.edit { preferences ->
                preferences[SettingsKeys.SERVERS] = payload
                preferences[SettingsKeys.ACTIVE_SERVER_ID] = _activeId.value
            }
        }
    }

    private fun refreshActive() {
        _activeServer.value = _servers.value.firstOrNull { it.id == _activeId.value }
            ?: _servers.value.firstOrNull()
            ?: DEFAULT_SERVER
    }

    private fun withSecret(config: ServerConfig): ServerConfig {
        val headers = secrets.get("${config.id}.headers").takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { json.decodeFromString(HeaderListSerializer, raw) }.getOrDefault(emptyList())
        }.orEmpty()
        return config.copy(
            apiKey = secrets.get(config.id),
            profileKey = secrets.get("${config.id}.profile"),
            accessSecret = secrets.get("${config.id}.access"),
            customHeaders = headers,
        )
    }

    companion object {
        private val HeaderListSerializer = kotlinx.serialization.builtins.ListSerializer(CustomHeader.serializer())

        /** The serializer of a server list, without depending on an enclosing type. */
        private val ServerListSerializer =
            kotlinx.serialization.builtins.ListSerializer(ServerConfig.serializer())

        /**
         * The server of a fresh installation: no address.
         *
         * the reference starts the same way — nothing is prefilled, and `SetupView` asks for the address. An emulator
         * address (`10.0.2.2`) would be unreachable from a phone anyway.
         */
        val DEFAULT_SERVER = ServerConfig(
            id = "default",
            name = "Onyx",
            baseUrl = "",
            model = "",
            transport = Transport.CHAT_COMPLETIONS,
        )
    }
}
