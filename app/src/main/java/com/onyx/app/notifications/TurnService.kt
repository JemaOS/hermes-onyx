package com.onyx.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * The background service of a turn: it keeps the process alive while the agent works.
 *
 * Mirror of what the reference calls "continued processing": leaving the conversation, switching
 * to another app or turning off the screen interrupts neither the turn nor its tracking.
 *
 * The notification itself comes from [TurnNotification] — the same as the fallback's, so the two
 * paths cannot diverge. The publication rule is not here either: it is
 * [TurnWorkNotifier] that decides what to show, and to whom.
 */
class TurnService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    private var lastTitle: String = NotificationCopy.ONYX
    private var lastConversationId: String = ""
    private var lastPhase: String = ""
    private var lastPercent: Int? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lastTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                lastConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
                lastPhase = intent.getStringExtra(EXTRA_PHASE).orEmpty()
                lastPercent = intent.getIntExtra(EXTRA_PERCENT, -1).takeIf { it >= 0 }
                start()
            }

            ACTION_PHASE -> {
                lastPhase = intent.getStringExtra(EXTRA_PHASE).orEmpty()
                lastPercent = intent.getIntExtra(EXTRA_PERCENT, -1).takeIf { it >= 0 }
                update()
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        try {
            TurnNotification.ensureChannel(this)
            val notification = TurnNotification.build(
                context = this,
                conversationId = lastConversationId,
                title = lastTitle,
                phase = lastPhase,
                percent = lastPercent,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TurnNotification.PROGRESS_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(TurnNotification.PROGRESS_ID, notification)
            }
        } catch (error: Exception) {
            // A tracking that cannot hold the process must not take the app down: the
            // notification itself is published by the fallback in [TurnNotifier].
            Log.w(TAG, "le service de suivi n'a pas pu passer au premier plan", error)
            stopSelf()
        }
    }

    private fun update() {
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)?.notify(
                TurnNotification.PROGRESS_ID,
                TurnNotification.build(this, lastConversationId, lastTitle, lastPhase, lastPercent),
            )
        }.onFailure { Log.w(TAG, "la notification de suivi n'a pas pu être mise à jour", it) }
    }

    companion object {
        private const val TAG = "OnyxTurn"

        /**
         * The turns channel. Its importance lets the **sound** of the finished notification
         * through, but the progress notification is published silently: tracking therefore never
         * vibrates at each step.
         */
        const val CHANNEL_ID = "onyx.turns"

        const val ACTION_START = "com.onyx.app.action.TURN_START"
        const val ACTION_PHASE = "com.onyx.app.action.TURN_PHASE"

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_PERCENT = "percent"
        private const val EXTRA_CONVERSATION_ID = "conversation_id"

        @Volatile
        private var activeConversationId: String = ""

        @Volatile
        private var activeTitle: String = NotificationCopy.ONYX

        /**
         * Starts the service on a conversation's turn.
         *
         * Returns `false` when Android refuses the start (a background app has no
         * right to launch one): the caller then publishes the **same** notification directly,
         * so that tracking holds anyway.
         */
        fun start(
            context: Context,
            conversationId: String,
            title: String,
            phase: String,
            percent: Int? = null,
        ): Boolean = runCatching {
            activeConversationId = conversationId
            activeTitle = title
            TurnNotification.ensureChannel(context)
            val intent = Intent(context, TurnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PHASE, phase)
                .putExtra(EXTRA_PERCENT, percent ?: -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "le service de suivi n'a pas pu démarrer, repli sur la notification", error)
            false
        }

        /** Changes the phrase of the turn already tracked, directly via NotificationManager without going back through startService. */
        fun phase(context: Context, text: String, percent: Int? = null) {
            if (activeConversationId.isEmpty()) return
            runCatching {
                context.getSystemService(android.app.NotificationManager::class.java)?.notify(
                    TurnNotification.PROGRESS_ID,
                    TurnNotification.build(context, activeConversationId, activeTitle, text, percent),
                )
            }.onFailure { Log.w(TAG, "la phrase du suivi n'a pas pu être transmise", it) }
        }

        fun stop(context: Context) {
            activeConversationId = ""
            runCatching { context.stopService(Intent(context, TurnService::class.java)) }
        }
    }
}
