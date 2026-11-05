package com.onyx.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ReplyWatch
import com.onyx.app.data.net.ServeSessionMessages
import com.onyx.app.di.AppContainer

/**
 * « Me prévenir quand une réponse arrive, app fermée » — sans service de push.
 *
 * **Le manque qu'il comble** : un bot finit son tour dans son coin, l'app est fermée, et personne
 * ne l'apprend avant de la rouvrir. Le gateway, lui, a déjà écrit la réponse dans la session
 * (`GET /api/sessions/{id}/messages`, la route du rattrapage d'une bulle vide). Ce travail relit
 * donc ce journal à intervalles réguliers — toutes les quinze minutes, réseau présent — et publie
 * la notification de fin sur le canal des réponses ([TurnService.CHANNEL_ID]) quand quelque chose
 * de **nouveau** y est apparu.
 *
 * Rien ne passe par un relais ni par un service payant : c'est une lecture HTTP directe vers le
 * serveur configuré dans Réglages, avec la clé de ce serveur, depuis ce téléphone.
 *
 * **Ce qu'il n'invente pas** : la décision « est-ce nouveau ? » est entièrement dans [ReplyWatch],
 * une règle pure testée sur la JVM. Ici il n'y a que le transport, le disque et la notification —
 * et aucune de leurs pannes ne doit faire échouer le travail en boucle : un passage raté est
 * simplement un passage sans nouvelle, le suivant rattrapera.
 */
class ReplyWatcher(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Un travail périodique ne « réessaie » pas utilement : `Result.success()` dans tous les
        // cas, et le passage suivant (quinze minutes plus tard) fait le travail.
        runCatching { watch() }
            .onFailure { Log.w(TAG, "le surveillant de réponses a échoué sur ce passage", it) }
        return Result.success()
    }

    private suspend fun watch() {
        val context = applicationContext

        // Le processus du travail peut être neuf : le conteneur se réinstalle sans rien refaire
        // s'il l'était déjà (l'`Application` passe en premier, mais on ne s'appuie pas dessus).
        if (!AppContainer.isInstalled) AppContainer.install(context)

        val settings = AppContainer.settings
        // Les réglages peuvent n'être pas encore lus quand Android démarre le processus pour ce
        // travail : sans cette lecture, un interrupteur éteint se lirait comme son défaut.
        if (!settings.loaded.value) settings.load()

        // Le réglage peut avoir été éteint alors qu'un passage était déjà en file : le travail
        // s'arrête et se désinscrit, plutôt que de publier une alerte qu'on ne veut plus.
        if (!settings.replyWatch.value) {
            ReplyWatchScheduler.cancel(context)
            return
        }
        // Sans la permission Android, il n'y a rien à publier — et avancer les mesures ferait
        // manquer la première réponse une fois la permission accordée.
        if (!ApprovalNotifier.canNotify(context)) return

        val config = activeConfig() ?: return
        val conversations = conversationsToWatch(config)
        if (conversations.isEmpty()) return

        val sessions = ServeSessionMessages(config)
        val store = ReplyWatchStore(context)
        val sound = settings.notificationSound.value
        for (conversation in conversations) {
            val sessionId = conversation.serverSessionId?.trim().orEmpty()
            if (sessionId.isEmpty()) continue
            // **Une fenêtre, pas le journal entier.** Mesuré sur cette machine : le veilleur tirait
            // 7,8 Mo toutes les seize minutes (3,4 + 4,4 Mo pour deux sessions) sur un téléphone en
            // 4G, parce qu'il demandait la page par défaut de 500 messages. La règle ne consomme que
            // la fin du journal (`ReplyWatch.trailingOf` + la dernière réponse) : vingt lignes
            // suffisent, et la mesure reste la même.
            val rows = sessions.rows(
                sessionId,
                conversation.botProfile,
                limit = com.onyx.app.data.net.ReplyWatch.WATCH_TAIL,
            )
            // Journal vide : rien n'a été lu (réseau, refus du serveur, session vide). On ne touche
            // pas à la mesure — sinon un passage hors ligne ferait croire, au suivant, que tout est
            // neuf, et la conversation sonnerait pour des réponses déjà lues.
            if (rows.isEmpty()) continue
            val current = ReplyWatch.snapshotOf(rows)
            val previous = store.read(sessionId)
            // La réponse que ce téléphone tient déjà : si c'est la même, l'app l'a suivie (le suivi
            // du travail l'a annoncée) et le surveillant n'a rien à ajouter.
            val alreadyHere = conversation.messages
                .lastOrNull { it.role == Role.ASSISTANT && it.text.isNotBlank() }
                ?.let { ReplyWatch.fingerprintOf(it.text) }
            if (ReplyWatch.isNewReply(previous, current, alreadyHere) && !onScreen(conversation.id)) {
                TurnNotifier.notifyFinished(
                    context = context,
                    conversationId = conversation.id,
                    title = conversation.title,
                    reply = ReplyWatch.preview(ReplyWatch.lastAnswer(rows)),
                    failed = false,
                    sound = sound,
                )
            }
            store.write(sessionId, current)
        }
    }

    /**
     * Le serveur en cours d'emploi, ou `null` quand rien n'est configuré.
     *
     * La lecture n'est faite que si le conteneur n'a pas encore de quoi parler au serveur : un
     * processus démarré par le travail n'a pas fini son chargement au moment où l'on passe.
     */
    private suspend fun activeConfig(): ServerConfig? {
        val servers = AppContainer.servers
        if (servers.activeServer.value.apiBaseUrl.isBlank()) servers.load()
        return servers.activeServer.value.takeIf { it.apiBaseUrl.isNotBlank() }
    }

    /**
     * Les conversations suivies : celles du serveur actif qui ont une session côté gateway, la
     * plus récente d'abord, et pas plus de [MAX_SESSIONS].
     *
     * La borne est volontaire : chaque session est une requête HTTP, et le passage doit rester
     * court. Une conversation ancienne ne reçoit plus de réponse de toute façon.
     */
    private suspend fun conversationsToWatch(config: ServerConfig): List<Conversation> {
        val repository = AppContainer.conversations
        if (repository.conversations.value.isEmpty()) repository.load()
        return repository.conversations.value
            .asSequence()
            .filter { it.serverId == config.id || it.serverId.isBlank() }
            .filter { !it.serverSessionId.isNullOrBlank() }
            .sortedByDescending { it.updatedAt }
            .take(MAX_SESSIONS)
            .toList()
    }

    /**
     * La conversation est-elle sous les yeux de l'utilisateur ?
     *
     * Dans ce cas la réponse s'écrit à l'écran : une notification par-dessus serait du bruit —
     * c'est la règle que suit déjà le suivi du travail ([TurnWorkNotifier]).
     */
    private fun onScreen(conversationId: String): Boolean {
        val work = AppContainer.work
        return work.appInForeground && work.visibleConversationId == conversationId
    }

    /** Les mesures d'un passage à l'autre, gardées sur le téléphone. */
    private class ReplyWatchStore(context: Context) {

        private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        fun read(sessionId: String): ReplyWatch.Snapshot? =
            ReplyWatch.decode(preferences.getString(key(sessionId), null))

        fun write(sessionId: String, snapshot: ReplyWatch.Snapshot) {
            runCatching {
                preferences.edit().putString(key(sessionId), ReplyWatch.encode(snapshot)).apply()
            }
        }

        private fun key(sessionId: String): String = "session." + sessionId

        private companion object {
            /** Un fichier à part : la mesure d'une session n'est pas un réglage de l'app. */
            const val FILE = "onyx_reply_watch"
        }
    }

    private companion object {
        const val TAG = "OnyxReplyWatch"

        /**
         * Combien de sessions un passage relit : les cinq conversations les plus récentes.
         * Au-delà, les requêtes s'empileraient pour des fils que personne ne rouvre.
         */
        const val MAX_SESSIONS = 5
    }
}
