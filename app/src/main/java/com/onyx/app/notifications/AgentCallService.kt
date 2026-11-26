package com.onyx.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onyx.app.core.R
import com.onyx.app.calls.AgentCallBridge
import com.onyx.app.di.AppContainer
import com.onyx.app.localization.OnyxLocalization

/**
 * "The line stays open": the foreground service that **keeps Onyx reachable** while
 * the app is not in the foreground — screen off, another app in front.
 *
 * This is the counterpart of the reference `VoiceCallService.kt`, whose comment says the essentials:
 * *"the only way Android lets an app listen out of sight"*. There the service holds
 * the ringing call; here it holds the **polling loop** ([AgentCallBridge]) that waits
 * for the agent's call. Without it, the process sleeps, the loop stops, and a `phone_call_me`
 * launched five minutes later never rings.
 *
 * This service **does not ring**: it only publishes a discreet notification (low importance, no
 * sound) — the ringtone itself comes from [CallRinger]. Its channel is distinct from the calls channel, so
 * that no chime sounds when the service starts.
 */
class AgentCallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_OPEN -> start()
            ACTION_CLOSE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> start()
        }
        // Restarted by Android after a stop: the line resumes on its own.
        return START_STICKY
    }

    private fun start() {
        try {
            ensureChannel(this)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(OnyxLocalization[REACH_TITLE])
                .setContentText(OnyxLocalization[REACH_TEXT])
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setContentIntent(contentIntent(this))
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(REACH_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(REACH_ID, notification)
            }
        } catch (error: Exception) {
            // A service that cannot hold the process must not take the app down with it.
            Log.w(TAG, "le service de veille d'appel n'a pas pu passer au premier plan", error)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The service goes away: the loop keeps its process scope, but the stop is logged.
        Log.i(TAG, "le service de veille d'appel s'arrête")
    }

    companion object {
        private const val TAG = "OnyxCalls"

        /** The keep-alive channel: low importance, **no sound** — a call does not ring twice. */
        const val CHANNEL_ID = "onyx.calls.reach"
        const val CHANNEL_LABEL = "Reachable"
        const val CHANNEL_DESCRIPTION = "Keeps Onyx able to ring while it's in the background."

        private const val REACH_TITLE = "Ready for a call"
        private const val REACH_TEXT = "Hermes can reach this phone while Onyx isn't open."

        /** The keep-alive notification identifier (distinct from the calls one). */
        const val REACH_ID = 0x0C41

        const val ACTION_OPEN = "com.onyx.app.action.CALL_WATCH_OPEN"
        const val ACTION_CLOSE = "com.onyx.app.action.CALL_WATCH_CLOSE"

        /** Creates the channel if it does not exist: low importance, never a sound. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                OnyxLocalization[CHANNEL_LABEL],
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = OnyxLocalization[CHANNEL_DESCRIPTION]
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        private fun contentIntent(context: Context): PendingIntent {
            val intent = Intent(context, com.onyx.app.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context,
                REACH_ID,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        /**
         * Starts the keep-alive and the polling loop. Returns `false` when Android refuses the
         * start (a background app is not allowed to launch one): the caller knows,
         * and the loop continues as long as the process lives.
         */
        fun start(context: Context): Boolean = runCatching {
            // The loop is carried by the process: it is (re)started here, once and for all.
            if (AppContainer.isInstalled) {
                AppContainer.agentCalls.start(AppContainer.processScope)
            }
            ensureChannel(context)
            val intent = Intent(context, AgentCallService::class.java).setAction(ACTION_OPEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "la veille d'appel n'a pas pu démarrer", error)
            false
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AgentCallService::class.java)) }
            if (AppContainer.isInstalled) AppContainer.agentCalls.stop()
        }
    }
}
