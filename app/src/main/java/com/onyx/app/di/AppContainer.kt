package com.onyx.app.di

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.onyx.app.data.local.DraftStore
import com.onyx.app.data.local.SecretStore
import com.onyx.app.data.local.SettingsKeys
import com.onyx.app.data.local.conversationsDataStore
import com.onyx.app.data.repo.ConversationRepository
import com.onyx.app.data.repo.ServerRepository
import com.onyx.app.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Les singletons de l'application — pas de framework d'injection, juste un conteneur installé
 * par [com.onyx.app.OnyxApplication].
 *
 * L'ordre compte: les dépôts se remplissent depuis le disque dès le démarrage, et les écrans
 * observent des `StateFlow` déjà chargés.
 */
object AppContainer {

    private lateinit var applicationContext: Context

    /** Portée du processus: les lectures disque au démarrage n'appartiennent à aucun écran. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var servers: ServerRepository
        private set

    lateinit var conversations: ConversationRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    lateinit var drafts: DraftStore
        private set

    /** Les visages et les profils ajoutés à la main: « Erase everything » les efface aussi. */
    lateinit var botFaces: com.onyx.app.ui.bots.BotFaceStore
        private set

    /**
     * Les tours de conversation en cours, **au-dessus des écrans**.
     *
     * Sa portée est celle du processus: quitter la conversation, détruire l'activité ou éteindre
     * l'écran n'interrompt plus le travail, et le fil le retrouve au retour.
     */
    lateinit var turns: com.onyx.app.ui.chat.TurnStore
        private set

    /** Les salles de groupe gardées sur ce téléphone (un « New Group Chat » sans Dashboard). */
    lateinit var groupRooms: com.onyx.app.data.local.GroupRoomStore
        private set

    /** Où joindre le pont « Ordinateur » et son jeton: l'adresse ici, le jeton dans le Keystore. */
    lateinit var computer: com.onyx.app.data.local.ComputerBridgeStore
        private set

    /**
     * La session « Ordinateur », gardée par le processus et non par l'écran: quitter l'aperçu et
     * y revenir retrouve la même connexion, la même image et la même main.
     */
    lateinit var computerSession: com.onyx.app.ui.computer.ComputerSession
        private set

    /** Vrai une fois [install] passé: le widget s'en sert pour attendre le dépôt. */
    val isInstalled: Boolean get() = ::servers.isInitialized

    /** À appeler une fois, depuis `Application.onCreate`. */
    fun install(context: Context) {
        if (isInstalled) return
        applicationContext = context.applicationContext
        val preferences = applicationContext.onyxStore
        val secrets = SecretStore(applicationContext)
        settings = SettingsRepository(preferences)
        servers = ServerRepository(preferences, secrets)
        conversations = ConversationRepository(conversationsDataStore(applicationContext))
        com.onyx.app.ui.rich.WebBlockHeights.install(applicationContext)
        com.onyx.app.data.link.LinkPreviews.install(applicationContext)
        drafts = DraftStore(applicationContext)
        botFaces = com.onyx.app.ui.bots.BotFaceStore(applicationContext)
        groupRooms = com.onyx.app.data.local.GroupRoomStore(applicationContext)
        // Les tours tournent dans la portée du processus: leur réponse s'écrit sur disque même
        // quand aucun écran ne la regarde (l'app est en arrière-plan, l'activité est détruite).
        turns = com.onyx.app.ui.chat.TurnStore(
            scope = scope,
            journal = object : com.onyx.app.ui.chat.TurnJournal {
                override fun noteAssistant(conversationId: String, message: com.onyx.app.data.model.ChatMessage) {
                    scope.launch { conversations.upsertMessage(conversationId, message) }
                }

                override fun turnEnded(conversationId: String, session: com.onyx.app.ui.chat.TurnSession) {
                    scope.launch { conversations.upsertMessage(conversationId, session.message) }
                }
            },
        )
        // L'écran Ordinateur: le pont compagnon vit sur le poste, l'app s'y rattache par son adresse.
        computer = com.onyx.app.data.local.ComputerBridgeStore(applicationContext)
        computerSession = com.onyx.app.ui.computer.ComputerSession(computer)
        scope.launch {
            settings.load()
            servers.load()
            conversations.load()
        }
    }

    /**
     * « Erase everything », le `eraseEverything` de `ResetSettings`:
     * les conversations et leurs brouillons, les serveurs avec leurs clés et leurs identifiants, les
     * visages des bots, et les réglages remis à leurs valeurs d'origine.
     *
     * Rien n'est touché sur les serveurs: leurs conversations et leurs fichiers y restent, comme
     * le dit le pied de page.
     */
    suspend fun eraseEverything() {
        if (!isInstalled) return
        conversations.eraseAll()
        drafts.clearAll()
        botFaces.clearAll()
        groupRooms.clearAll()
        com.onyx.app.data.link.LinkPreviews.shared.clear()
        // Les réglages d'abord: ils partagent le même fichier de préférences, et les vider
        // effacerait le serveur d'usine qu'on vient d'y écrire.
        settings.reset()
        servers.eraseAll()
        applicationContext.onyxStore.edit { preferences ->
            preferences.remove(SettingsKeys.WIDGET_CONTEXT_USED)
            preferences.remove(SettingsKeys.WIDGET_CONTEXT_WINDOW)
        }
        reload()
    }

    /** Garde le remplissage du contexte pour le widget, qui ne lit pas la conversation. */
    fun publishContext(used: Int?, window: Int?) {
        if (!isInstalled) return
        if (used == null || window == null || window <= 0) return
        scope.launch {
            applicationContext.onyxStore.edit {
                it[SettingsKeys.WIDGET_CONTEXT_USED] = used
                it[SettingsKeys.WIDGET_CONTEXT_WINDOW] = window
            }
        }
    }

    /** Relit tout depuis le disque — après une restauration, ou pour le widget. */
    fun reload() {
        if (!isInstalled) return
        scope.launch {
            settings.load()
            servers.load()
            conversations.load()
        }
    }

    /** Les préférences brutes, pour ce qui n'a pas de dépôt (le widget lit deux entiers). */
    val preferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
        get() = applicationContext.onyxStore

    private val Context.onyxStore by preferencesDataStore(name = "onyx_settings")

    /** Clés de réglages, exposées pour les écrans qui les lisent directement. */
    val settingsKeys = SettingsKeys
}
