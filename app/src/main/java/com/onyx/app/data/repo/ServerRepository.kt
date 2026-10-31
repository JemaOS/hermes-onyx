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
 * Les serveurs connus de l'app et celui qui est actif.
 *
 * Équivalent Android de `HermesServer` + du serveur actif côté référence (`Settings.activeServer`).
 * La liste est écrite en JSON dans les préférences ; les clés API n'y figurent jamais : elles
 * vont dans le Keystore ([SecretStore]) et sont recomposées à la lecture.
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

    /** Lit la liste enregistrée et la clé API du serveur actif. */
    suspend fun load() {
        val stored = withContext(Dispatchers.IO) {
            val preferences = store.data.first()
            val raw = preferences[SettingsKeys.SERVERS]
            val decoded = raw
                ?.let { text -> runCatching { json.decodeFromString(ServerListSerializer, text) }.getOrNull() }
                .orEmpty()
            decoded to preferences[SettingsKeys.ACTIVE_SERVER_ID]
        }
        // Le nom par défaut a changé avec le renommage : un serveur encore appelé « Onyx » est
        // l'ancien nom par défaut, pas un choix — il devient « Onyx » au chargement, pas seulement
        // quand on rouvre sa fiche.
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

    /** Ajoute ou remplace un serveur ; la clé API part dans le Keystore. */
    suspend fun upsert(config: ServerConfig) {
        val clean = config.copy(
            // Le nom par défaut a changé avec le renommage : un serveur encore appelé « Onyx »
            // est l'ancien nom par défaut, pas un choix — il s'affiche « Onyx » dans Réglages.
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
        if (clean.id == _activeId.value || _activeId.value.isEmpty()) _activeId.value = clean.id
        refreshActive()
        persist()
    }

    /** Supprime un serveur ; s'il était actif, l'actif devient le premier restant. */
    suspend fun remove(id: String) {
        val remaining = _servers.value.filterNot { it.id == id }
        _servers.value = remaining.ifEmpty { listOf(DEFAULT_SERVER) }
        if (_activeId.value == id) _activeId.value = _servers.value.first().id
        refreshActive()
        persist()
    }

    /** Définit le serveur actif ; sans effet si l'identifiant est inconnu. */
    suspend fun setActive(id: String) {
        if (_servers.value.none { it.id == id }) return
        _activeId.value = id
        refreshActive()
        store.edit { it[SettingsKeys.ACTIVE_SERVER_ID] = id }
    }

    /** Crée un identifiant de serveur stable et lisible. */
    fun newId(): String = "srv-" + UUID.randomUUID().toString().take(8)

    /**
     * Oublie **tous** les serveurs, leurs clés et leurs identifiants, et revient au serveur
     * d'usine : le « Erase everything » des Réglages, qui ne touche à rien sur les serveurs.
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

        /** Le sérialiseur d'une liste de serveurs, sans dépendre d'un type englobant. */
        private val ServerListSerializer =
            kotlinx.serialization.builtins.ListSerializer(ServerConfig.serializer())

        /**
         * Le serveur d'une installation neuve : pas d'adresse.
         *
         * référence part de même — rien n'est prérempli, et `SetupView` demande l'adresse. Une adresse
         * d'émulateur (`10.0.2.2`) serait de toute façon injoignable depuis un téléphone.
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
