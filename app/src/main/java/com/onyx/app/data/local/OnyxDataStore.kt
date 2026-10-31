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
 * Le magasin des conversations : un seul fichier JSON, réécrit en entier et de façon atomique par
 * DataStore.
 *
 * Miroir de `ConversationStore` côté référence, qui tient un fichier d'index et un fichier par
 * conversation ; ici tout tient dans une liste, l'historique restant petit.
 *
 * Une seule instance par processus : `DataStoreFactory` refuse deux magasins sur le même fichier,
 * donc [com.onyx.app.di.AppContainer] la garde.
 */
fun conversationsDataStore(context: Context): DataStore<List<Conversation>> =
    DataStoreFactory.create(
        serializer = ConversationSerializer(),
        produceFile = { conversationsFile(context) },
    )

/** Le fichier de la **copie locale** des conversations — un seul, réécrit en entier. */
fun conversationsFile(context: Context): File =
    File(context.applicationContext.filesDir, "conversations.json")

/**
 * **Quand la copie locale a été enregistrée pour la dernière fois** (millisecondes depuis l'époque),
 * ou `null` si ce téléphone n'a encore rien enregistré.
 *
 * C'est la date que porte la ligne d'état du volet quand la passerelle ne répond plus : « Copie
 * enregistrée · il y a 9 h. » — et elle vient du **fichier lui-même**, pas d'une horloge de
 * session. Une date de session se remettrait à zéro à chaque démarrage de l'app et dirait « il y a
 * 2 secondes » d'une copie vieille de trois jours : c'est exactement ce qu'il ne faut pas.
 */
fun savedCopyAtMs(context: Context): Long? {
    val file = conversationsFile(context)
    return if (file.isFile) file.lastModified().takeIf { it > 0L } else null
}

/** Réglages : petits, donc `Preferences`. */
val Context.onyxPreferences: DataStore<Preferences> by preferencesDataStore(name = "onyx_settings")

/** Clés de `onyx_settings`. */
object SettingsKeys {
    /** Liste des serveurs, en JSON, sans les clés API (celles-ci vivent dans le Keystore). */
    val SERVERS = stringPreferencesKey("servers")

    /** Identifiant du serveur actif. */
    val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")

    /** Thème choisi (`OnyxTheme.id`). */
    val THEME = stringPreferencesKey("theme")

    /** Apparence : `system`, `light` ou `dark`. */
    val APPEARANCE = stringPreferencesKey("appearance")

    /**
     * La période des aperçus : 7, 30 ou 90 jours.
     *
     * Elle est mémorisée **par appareil**, comme les autres réglages d'interface : rouvrir les
     * aperçus retrouve la période qu'on regardait, au lieu de revenir chaque fois au mois.
     */
    val INSIGHTS_PERIOD = intPreferencesKey("insights_period_days")

    /**
     * **La grille de raccourcis du volet**, telle que la personne l'a composée : les identifiants des
     * tuiles affichées, dans son ordre, séparés par une virgule (voir `SidebarTileLayout`).
     *
     * Une clé **dédiée** : ce réglage ne partage sa place avec aucun autre, si bien qu'une valeur
     * déjà enregistrée par une version antérieure n'est jamais invalidée par un autre réglage. Clé
     * absente = la grille d'origine (les six tuiles de la référence).
     */
    val SIDEBAR_TILES = stringPreferencesKey("sidebar_tiles")

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
        /** Un fichier vide n'est pas du JSON valide : on écrit `[]` pour une liste vide. */
        const val EMPTY_LITERAL = "[]"
    }
}
