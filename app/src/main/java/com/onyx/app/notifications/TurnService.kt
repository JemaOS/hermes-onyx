package com.onyx.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Le service de fond d'un tour : il garde le processus vivant pendant que l'agent travaille.
 *
 * Miroir de ce qu'appelle « continued processing » la référence : quitter la conversation, passer
 * à une autre app ou éteindre l'écran n'interrompt ni le tour ni son suivi.
 *
 * La notification elle-même vient de [TurnNotification] — la même que celle du repli, donc les
 * deux chemins ne peuvent pas diverger. La règle de publication n'est pas ici non plus : c'est
 * [TurnWorkNotifier] qui décide quoi montrer, et à qui.
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
            // Un suivi qui ne peut pas tenir le processus ne doit pas emporter l'app : la
            // notification, elle, est publiée par le repli de [TurnNotifier].
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
         * Le canal des tours. Son importance laisse passer le **son** de la notification de fin,
         * mais la notification d'avancement est publiée en silence : le suivi ne vibre donc
         * jamais à chaque étape.
         */
        const val CHANNEL_ID = "onyx.turns"

        const val ACTION_START = "com.onyx.app.action.TURN_START"
        const val ACTION_PHASE = "com.onyx.app.action.TURN_PHASE"

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_PERCENT = "percent"
        private const val EXTRA_CONVERSATION_ID = "conversation_id"

        /**
         * Démarre le service sur le tour d'une conversation.
         *
         * Rend `false` quand Android refuse le démarrage (une app en arrière-plan n'a pas le
         * droit d'en lancer un) : l'appelant publie alors la **même** notification directement,
         * pour que le suivi tienne quand même.
         */
        fun start(
            context: Context,
            conversationId: String,
            title: String,
            phase: String,
            percent: Int? = null,
        ): Boolean = runCatching {
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

        /** Change la phrase du tour déjà suivi. */
        fun phase(context: Context, text: String, percent: Int? = null) {
            runCatching {
                context.startService(
                    Intent(context, TurnService::class.java)
                        .setAction(ACTION_PHASE)
                        .putExtra(EXTRA_PHASE, text)
                        .putExtra(EXTRA_PERCENT, percent ?: -1),
                )
            }.onFailure { Log.w(TAG, "la phrase du suivi n'a pas pu être transmise", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TurnService::class.java)) }
        }
    }
}
