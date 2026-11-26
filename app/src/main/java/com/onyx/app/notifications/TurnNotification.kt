package com.onyx.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.onyx.app.MainActivity
import com.onyx.app.core.R

/**
 * The progress notification of a turn: step, duration, progress, **Stop** button.
 *
 * A single instance in the status bar — the identifier is fixed — and two ways of keeping it up:
 * the foreground service ([TurnService]) when Android agrees to start it, and a direct
 * publication ([TurnNotifier.notifyProgress]) when it refuses (a start from the background,
 * for example). The content itself is **the same**: a single function builds it, so the two
 * paths cannot diverge.
 *
 * On Android 16 it takes the "progress" style and asks to be promoted to a **Live Update**:
 * the system can then follow it as a status bar chip (Android 16 QPR1 and later), outside of
 * the notification screen.
 */
object TurnNotification {

    /** The identifier of the progress notification: one turn at a time in the status bar. */
    const val PROGRESS_ID = 41

    /** The key of the Live Update promotion request (Android 16 QPR1). */
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** Registers the channel; Android never modifies a channel the user has configured. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                TurnService.CHANNEL_ID,
                context.notificationLabel(NotificationChannels.ANSWERS_LABEL),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.notificationLabel(NotificationChannels.ANSWERS_DESCRIPTION)
                setShowBadge(false)
            },
        )
    }

    /** The notification, as published by the service and by the fallback. */
    fun build(context: Context, conversationId: String, title: String, phase: String, percent: Int?): Notification {
        val thread = title.ifBlank { NotificationCopy.ONYX }
        val localizedPhase = context.notificationLabel(phase.ifBlank { "Answering…" })
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // "Arrêter" ("Stop") goes through a receiver: it stops the turn in the process store, even
        // when no screen is open (see [TurnStopReceiver]).
        val stop = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TurnStopReceiver::class.java)
                .setAction(TurnStopReceiver.ACTION_STOP)
                .putExtra(TurnStopReceiver.EXTRA_CONVERSATION_ID, conversationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16: the "progress" style and the short text turn the notification into a
            // Live Update, followed by the system above the home screen — as a status bar
            // chip on a device that has the promotion.
            val style = Notification.ProgressStyle()
            if (percent != null) {
                style.setProgress(percent.coerceIn(0, 100))
            } else {
                style.setProgressIndeterminate(true)
            }
            style.setStyledByProgress(true)

            return Notification.Builder(context, TurnService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(thread)
                .setContentText(localizedPhase)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                // The channel lets the sound of the finished notification through; the progress
                // one updates silently — never a sound or vibration at each step.
                .setSound(null)
                .setVibrate(null)
                .setContentIntent(open)
                .setShortCriticalText(localizedPhase)
                // The promotion request is only exposed from Android 16 QPR1 onward: with
                // `compileSdk 36` it is written by its key, as the documentation recommends.
                .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
                .setStyle(style)
                .addAction(Notification.Action.Builder(null, context.notificationLabel("Stop"), stop).build())
                .build()
        }

        return NotificationCompat.Builder(context, TurnService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(thread)
            .setContentText(localizedPhase)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open)
            .addAction(0, context.notificationLabel("Stop"), stop)
            .build()
    }
}
