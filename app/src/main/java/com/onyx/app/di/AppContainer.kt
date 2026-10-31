package com.onyx.app.di

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.onyx.app.data.local.DraftStore
import com.onyx.app.data.local.SecretStore
import com.onyx.app.data.local.SharedPreferencesDeletedConversations
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
 * L'ordre compte : les dépôts se remplissent depuis le disque dès le démarrage, et les écrans
 * observent des `StateFlow` déjà chargés.
 */
object AppContainer {

    private lateinit var applicationContext: Context

    /** Portée du processus : les lectures disque au démarrage n'appartiennent à aucun écran. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** La portée du processus, pour ce qui doit vivre au-delà d'un écran (le pont d'appel). */
    val processScope: CoroutineScope get() = scope

    lateinit var servers: ServerRepository
        private set

    lateinit var conversations: ConversationRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    lateinit var drafts: DraftStore
        private set

    /** Les visages et les profils ajoutés à la main : « Erase everything » les efface aussi. */
    lateinit var botFaces: com.onyx.app.ui.bots.BotFaceStore
        private set

    /**
     * Les tours de conversation en cours, **au-dessus des écrans**.
     *
     * Sa portée est celle du processus : quitter la conversation, détruire l'activité ou éteindre
     * l'écran n'interrompt plus le travail, et le fil le retrouve au retour.
     */
    lateinit var turns: com.onyx.app.ui.chat.TurnStore
        private set

    /**
     * Le suivi du travail des bots : la notification d'avancement et la notification de fin.
     *
     * Tenu par le processus, comme les tours qu'il regarde — c'est ce qui lui permet d'annoncer
     * une réponse arrivée alors qu'aucun écran ne l'attendait.
     */
    lateinit var work: com.onyx.app.notifications.TurnWorkNotifier
        private set

    /**
     * Le pont de l'**appel de l'agent vers le téléphone** (`phone_call_me`).
     *
     * Tenu par le processus, comme le suivi du travail : un appel qui arrive alors qu'aucun écran
     * ne l'attend doit quand même sonner, et son issue doit remonter à l'agent.
     */
    lateinit var agentCalls: com.onyx.app.calls.AgentCallBridge
        private set

    /** Les salles de groupe gardées sur ce téléphone (un « New Group Chat » sans Dashboard). */
    lateinit var groupRooms: com.onyx.app.data.local.GroupRoomStore
        private set

    /** Où joindre le pont « Ordinateur » et son jeton : l'adresse ici, le jeton dans le Keystore. */
    lateinit var computer: com.onyx.app.data.local.ComputerBridgeStore
        private set

    /**
     * La session « Ordinateur », gardée par le processus et non par l'écran : quitter l'aperçu et
     * y revenir retrouve la même connexion, la même image et la même main.
     */
    lateinit var computerSession: com.onyx.app.ui.computer.ComputerSession
        private set

    /** Vrai une fois [install] passé : le widget s'en sert pour attendre le dépôt. */
    val isInstalled: Boolean get() = ::servers.isInitialized

    /**
     * Le contexte de l'application, une fois [install] passé.
     *
     * Ce que lisent les services qui n'ont pas d'écran : les raccourcis de l'icône
     * ([com.onyx.app.shortcuts.RosterShortcuts]) sont republiés depuis la section des bots, un
     * `ViewModel` — et un `ViewModel` n'a pas de contexte.
     */
    val appContext: Context get() = applicationContext

    /** À appeler une fois, depuis `Application.onCreate`. */
    fun install(context: Context) {
        if (isInstalled) return
        applicationContext = context.applicationContext
        val preferences = applicationContext.onyxStore
        val secrets = SecretStore(applicationContext)
        settings = SettingsRepository(preferences)
        // La langue choisie est posée **avant** la première composition : sans cela l'interface
        // s'afficherait une fraction de seconde dans la langue d'Android avant de suivre le choix.
        // La lecture est synchrone (un petit fichier) et ne retarde que le démarrage du processus.
        //
        // **Le thème et l'apparence suivent le même chemin, et doivent le suivre** : `load()` est
        // asynchrone, si bien que sans cette lecture-ci la première image serait rendue avec le
        // défaut `AppearanceMode.SYSTEM`. Sur un téléphone en clair, l'utilisateur qui a choisi
        // « Sombre » rouvrait donc l'app en clair jusqu'à la fin de `load()`.
        kotlinx.coroutines.runBlocking {
            settings.applyStoredLanguage()
            settings.applyStoredTheme()
        }
        servers = ServerRepository(preferences, secrets)
        conversations = ConversationRepository(
            conversationsDataStore(applicationContext),
            // La marque des conversations supprimées : sur les préférences du téléphone, pour
            // qu'une suppression ne revienne ni au redémarrage ni par un chemin qui réécrit le fil.
            SharedPreferencesDeletedConversations(applicationContext),
        )
        com.onyx.app.ui.rich.WebBlockHeights.install(applicationContext)
        com.onyx.app.data.link.LinkPreviews.install(applicationContext)
        drafts = DraftStore(applicationContext)
        botFaces = com.onyx.app.ui.bots.BotFaceStore(applicationContext)
        groupRooms = com.onyx.app.data.local.GroupRoomStore(applicationContext)
        // Les tours tournent dans la portée du processus : leur réponse s'écrit sur disque même
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
        // Le suivi du travail : la notification d'avancement et celle de fin, tenues par le
        // processus et branchées sur le magasin de tours. Le nom du fil vient des conversations.
        work = com.onyx.app.notifications.TurnWorkNotifier(
            context = applicationContext,
            turns = turns,
            settings = settings,
            titleOf = { conversationId ->
                runCatching { conversations.find(conversationId)?.title }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: com.onyx.app.notifications.NotificationCopy.ONYX
            },
        )
        work.start(scope)
        // Le pont de l'appel de l'agent : la boucle qui attend un `phone_call_me` et le fait
        // sonner. Sa portée est celle du processus (comme le suivi du travail) ; il démarre quand
        // « Appels pour une réponse » est allumé, et s'arrête quand il est éteint.
        agentCalls = com.onyx.app.calls.AgentCallBridge(
            context = applicationContext,
            settings = settings,
            servers = servers,
            titleOf = { conversationId ->
                runCatching { conversations.find(conversationId)?.title }.getOrNull()?.takeIf { it.isNotBlank() }
            },
        )
        scope.launch {
            settings.phoneRings.collect { on ->
                if (on) {
                    agentCalls.start(scope)
                    // « La ligne reste ouverte » : le service de premier plan garde le processus
                    // vivant pour qu'un appel qui arrive écran éteint sonne quand même.
                    runCatching { com.onyx.app.notifications.AgentCallService.start(applicationContext) }
                } else {
                    agentCalls.stop()
                    runCatching { com.onyx.app.notifications.AgentCallService.stop(applicationContext) }
                }
            }
        }
        // L'écran Ordinateur : le pont compagnon vit sur le poste, l'app s'y rattache par son adresse.
        computer = com.onyx.app.data.local.ComputerBridgeStore(applicationContext)
        computerSession = com.onyx.app.ui.computer.ComputerSession(computer)
        scope.launch {
            settings.load()
            servers.load()
            conversations.load()
        }
    }

    /**
     * « Erase everything », le `eraseEverything()` de `ResetSettings` (SettingsSections:707) :
     * les conversations et leurs brouillons, les serveurs avec leurs clés et leurs identifiants, les
     * visages des bots, et les réglages remis à leurs valeurs d'origine.
     *
     * Rien n'est touché sur les serveurs : leurs conversations et leurs fichiers y restent, comme
     * le dit le pied de page de la référence.
     */
    suspend fun eraseEverything() {
        if (!isInstalled) return
        conversations.eraseAll()
        drafts.clearAll()
        botFaces.clearAll()
        groupRooms.clearAll()
        com.onyx.app.data.link.LinkPreviews.shared.clear()
        // Les réglages d'abord : ils partagent le même fichier de préférences, et les vider
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
