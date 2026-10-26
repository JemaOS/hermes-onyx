package com.onyx.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onyx.app.MainActivity
import com.onyx.app.core.R
import com.onyx.app.localization.OnyxLocalization

/**
 * Le service de fond d'un tour en cours.
 *
 * Comme l'app appelle « continued processing »: pendant que l'agent travaille, Onyx
 * reste vivant et le dit dans la barre d'état. Sur Android 16 la notification est promue en
 * « Live Update » (elle reste visible et suit la progression); ailleurs c'est une notification
 * ordinaire, avec son bouton d'arrêt.
 */
class TurnService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                start(
                    title = title,
                    phase = intent.getStringExtra(EXTRA_PHASE).orEmpty(),
                    percent = intent.getIntExtra(EXTRA_PERCENT, -1).takeIf { it >= 0 },
                )
            }

            ACTION_PHASE -> update(
                phase = intent.getStringExtra(EXTRA_PHASE).orEmpty(),
                percent = intent.getIntExtra(EXTRA_PERCENT, -1).takeIf { it >= 0 },
            )
            ACTION_STOP -> {
                // Arrêter depuis la notification arrête aussi le flux.
                sendBroadcast(Intent(ACTION_STOP_REQUESTED).setPackage(packageName))
                stopSelf()
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(title: String, phase: String, percent: Int?) {
        ensureChannel()
        val notification = build(title, phase, percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun update(phase: String, percent: Int?) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, build(lastTitle, phase, percent)) }
    }

    private var lastTitle: String = "Onyx"

    private fun build(title: String, phase: String, percent: Int? = null): Notification {
        lastTitle = title
        val localizedPhase = OnyxLocalization[phase.ifEmpty { "Answering…" }]
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TurnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16: le style « progression » et le texte court font de la notification un
            // Live Update, suivi par le système au-dessus de l'écran d'accueil.
            val style = android.app.Notification.ProgressStyle()
            if (percent != null) {
                style.setProgress(percent.coerceIn(0, 100))
            } else {
                style.setProgressIndeterminate(true)
            }
            style.setStyledByProgress(true)

            return android.app.Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(localizedPhase)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setShortCriticalText(localizedPhase)
                .setStyle(style)
                .addAction(android.app.Notification.Action.Builder(null, OnyxLocalization["Stop"], stop).build())
                .build()
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(localizedPhase)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open)
            .addAction(0, OnyxLocalization["Stop"], stop)

        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, OnyxLocalization["Answers"], NotificationManager.IMPORTANCE_LOW).apply {
                description = OnyxLocalization["Shows while Onyx is answering."]
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "onyx.turns"
        private const val NOTIFICATION_ID = 41

        const val ACTION_START = "com.onyx.app.action.TURN_START"
        const val ACTION_PHASE = "com.onyx.app.action.TURN_PHASE"
        const val ACTION_STOP = "com.onyx.app.action.TURN_STOP_SERVICE"
        const val ACTION_STOP_REQUESTED = "com.onyx.app.action.TURN_STOP"

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_PERCENT = "percent"

        /** Démarre le service, ou change sa phrase s'il tourne déjà. */
        fun start(context: Context, title: String, phase: String = "Answering…", percent: Int? = null) {
            val intent = Intent(context, TurnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PHASE, phase)
                .putExtra(EXTRA_PERCENT, percent ?: -1)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun phase(context: Context, text: String, percent: Int? = null) {
            runCatching {
                context.startService(
                    Intent(context, TurnService::class.java)
                        .setAction(ACTION_PHASE)
                        .putExtra(EXTRA_PHASE, text)
                        .putExtra(EXTRA_PERCENT, percent ?: -1),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TurnService::class.java)) }
        }
    }
}
