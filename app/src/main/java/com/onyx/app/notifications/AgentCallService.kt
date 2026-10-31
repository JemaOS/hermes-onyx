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
 * « La ligne reste ouverte » : le service de premier plan qui **garde Onyx joignable** pendant que
 * l'app n'est pas au premier plan — l'écran éteint, une autre app devant.
 *
 * C'est le pendant de `VoiceCallService.kt` de la référence, dont le commentaire dit l'essentiel :
 * *« la seule façon dont Android laisse une app écouter hors de vue »*. Chez elle le service tient
 * l'appel qui sonne ; ici il tient la **boucle d'interrogation** ([AgentCallBridge]) qui attend
 * l'appel de l'agent. Sans lui, le processus s'endort, la boucle s'arrête, et un `phone_call_me`
 * lancé cinq minutes plus tard ne sonne jamais.
 *
 * Ce service **ne sonne pas** : il ne publie qu'une notification discrète (importance basse, aucun
 * son) — la sonnerie, elle, vient de [CallRinger]. Son canal est distinct de celui des appels, pour
 * qu'aucun carillon ne parte au démarrage du service.
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
        // Redémarré par Android après un arrêt : la ligne reprend toute seule.
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
            // Un service qui ne peut pas tenir le processus ne doit pas emporter l'app.
            Log.w(TAG, "le service de veille d'appel n'a pas pu passer au premier plan", error)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Le service s'en va : la boucle garde sa portée de processus, mais on note l'arrêt.
        Log.i(TAG, "le service de veille d'appel s'arrête")
    }

    companion object {
        private const val TAG = "OnyxCalls"

        /** Le canal de veille : importance basse, **aucun son** — un appel ne sonne pas deux fois. */
        const val CHANNEL_ID = "onyx.calls.reach"
        const val CHANNEL_LABEL = "Reachable"
        const val CHANNEL_DESCRIPTION = "Keeps Onyx able to ring while it's in the background."

        private const val REACH_TITLE = "Ready for a call"
        private const val REACH_TEXT = "Hermes can reach this phone while Onyx isn't open."

        /** L'identifiant de la notification de veille (distinct de celui des appels). */
        const val REACH_ID = 0x0C41

        const val ACTION_OPEN = "com.onyx.app.action.CALL_WATCH_OPEN"
        const val ACTION_CLOSE = "com.onyx.app.action.CALL_WATCH_CLOSE"

        /** Crée le canal s'il n'existe pas : importance basse, jamais de son. */
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
         * Démarre la veille et la boucle d'interrogation. Rend `false` quand Android refuse le
         * démarrage (une app en arrière-plan n'a pas le droit d'en lancer un) : l'appelant le sait,
         * et la boucle continue tant que le processus vit.
         */
        fun start(context: Context): Boolean = runCatching {
            // La boucle est portée par le processus : on la (re)démarre ici, une fois pour toutes.
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
