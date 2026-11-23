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
 * "Notify me when a reply arrives, with the app closed" — without a push service.
 *
 * **The gap it fills**: a bot finishes its turn on its own, the app is closed, and nobody
 * learns about it before reopening it. The gateway, for its part, has already written the reply into the
 * session (`GET /api/sessions/{id}/messages`, the route the empty-bubble catch-up uses). This job therefore
 * rereads that log at regular intervals — every fifteen minutes, network present — and publishes
 * the finished notification on the replies channel ([TurnService.CHANNEL_ID]) when something
 * **new** has appeared there.
 *
 * Nothing goes through a relay or a paid service: it is a direct HTTP read to the
 * server configured in Settings, with that server's key, from this phone.
 *
 * **What it does not invent**: the "is this new?" decision lives entirely in [ReplyWatch],
 * a pure rule tested on the JVM. Here there is only the transport, the disk and the notification —
 * and none of their failures must make the job fail in a loop: a missed pass is
 * simply a pass without news, the next one will catch up.
 */
class ReplyWatcher(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // A periodic job does not usefully "retry": `Result.success()` in every
        // case, and the next pass (fifteen minutes later) does the work.
        runCatching { watch() }
            .onFailure { Log.w(TAG, "le surveillant de réponses a échoué sur ce passage", it) }
        return Result.success()
    }

    private suspend fun watch() {
        val context = applicationContext

        // The job's process may be new: the container reinstalls itself without redoing anything
        // if it was already installed (the `Application` runs first, but we do not rely on it).
        if (!AppContainer.isInstalled) AppContainer.install(context)

        val settings = AppContainer.settings
        // Settings may not have been read yet when Android starts the process for this
        // job: without this read, a switched-off toggle would read as its default.
        if (!settings.loaded.value) settings.load()

        // The setting may have been turned off while a pass was already queued: the job
        // stops and unregisters itself, rather than publishing an alert that is no longer wanted.
        if (!settings.replyWatch.value) {
            ReplyWatchScheduler.cancel(context)
            return
        }
        // Without the Android permission, there is nothing to publish — and advancing the marks would
        // make the first reply be missed once the permission is granted.
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
            // **A window, not the whole log.** Measured on this machine: the watcher was pulling
            // 7.8 MB every sixteen minutes (3.4 + 4.4 MB for two sessions) on a phone on
            // 4G, because it asked for the default page of 500 messages. The rule only consumes
            // the end of the log (`ReplyWatch.trailingOf` + the last reply): twenty lines
            // are enough, and the measurement stays the same.
            val rows = sessions.rows(
                sessionId,
                conversation.botProfile,
                limit = com.onyx.app.data.net.ReplyWatch.WATCH_TAIL,
            )
            // Empty log: nothing was read (network, server refusal, empty session). We do not touch
            // the mark — otherwise an offline pass would make the next one believe everything is
            // new, and the conversation would ring for replies already read.
            if (rows.isEmpty()) continue
            val current = ReplyWatch.snapshotOf(rows)
            val previous = store.read(sessionId)
            // The reply this phone already holds: if it is the same, the app has followed it (the turn
            // tracking announced it) and the watcher has nothing to add.
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
     * The server in use, or `null` when nothing is configured.
     *
     * The read is only done if the container does not yet have what it needs to talk to the server: a
     * process started by the job has not finished its loading by the time we run.
     */
    private suspend fun activeConfig(): ServerConfig? {
        val servers = AppContainer.servers
        if (servers.activeServer.value.apiBaseUrl.isBlank()) servers.load()
        return servers.activeServer.value.takeIf { it.apiBaseUrl.isNotBlank() }
    }

    /**
     * The watched conversations: those of the active server that have a session on the gateway side, the
     * most recent first, and no more than [MAX_SESSIONS].
     *
     * The bound is deliberate: each session is an HTTP request, and the pass must stay
     * short. An old conversation no longer receives replies anyway.
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
     * Is the conversation in front of the user's eyes?
     *
     * In that case the reply is being written on screen: a notification on top would be noise —
     * it is the rule the turn tracking already follows ([TurnWorkNotifier]).
     */
    private fun onScreen(conversationId: String): Boolean {
        val work = AppContainer.work
        return work.appInForeground && work.visibleConversationId == conversationId
    }

    /** The marks from one pass to the next, kept on the phone. */
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
            /** A separate file: a session's measurement is not an app setting. */
            const val FILE = "onyx_reply_watch"
        }
    }

    private companion object {
        const val TAG = "OnyxReplyWatch"

        /**
         * How many sessions one pass rereads: the five most recent conversations.
         * Beyond that, requests would pile up for threads nobody reopens.
         */
        const val MAX_SESSIONS = 5
    }
}
