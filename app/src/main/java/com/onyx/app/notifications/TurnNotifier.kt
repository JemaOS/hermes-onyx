package com.onyx.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.onyx.app.MainActivity
import com.onyx.app.core.R

/**
 * The finished notification: a bot has finished its work, the app is not open on its conversation.
 *
 * This is the Android counterpart of what the reference announces at the end of a turn: a word on
 * the result — "Terminé" ("Done") or "Échec" ("Failed") — and enough to recognize the answer,
 * without opening it. A tap brings you back to the conversation, where everything is written.
 *
 * **One turn, one notification.** The identifier is derived from the conversation: republishing the
 * same finish replaces the notification instead of stacking a second one, and it is the same key
 * that makes it disappear ([clear]).
 */
object TurnNotifier {

    /** The turns channel: the same as the progress one, described as "Réponses" ("Answers"). */
    const val CHANNEL_ID = TurnService.CHANNEL_ID

    /** The identifier of a finished turn, stable per conversation. */
    fun id(conversationId: String): Int = ("onyx.turn." + conversationId).hashCode()

    fun notifyFinished(
        context: Context,
        conversationId: String,
        title: String,
        reply: String,
        failed: Boolean,
        sound: Boolean = true,
        enabled: Boolean = true,
    ) {
        if (!enabled) return
        if (!ApprovalNotifier.canNotify(context)) return

        val text = reply.ifBlank { context.notificationLabel("The server sent no answer for this turn.") }
        val open = PendingIntent.getActivity(
            context,
            id(conversationId),
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.notificationLabel(NotificationCopy.finishedTitle(failed)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(title.takeIf { it.isNotBlank() })
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setSilent(!sound)
            .setAutoCancel(true)
            .setContentIntent(open)

        if (canPost(context)) {
            runCatching {
                NotificationManagerCompat.from(context).notify(id(conversationId), builder.build())
            }
        }
    }

    /** The turn is stopped or forgotten: its finished notification must not stay on screen. */
    fun clear(context: Context, conversationId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(id(conversationId)) }
    }

    /**
     * The fallback when Android refuses the foreground service (a background app has no right
     * to start one): the **same** notification is published directly, so that tracking holds
     * anyway. Same identifier, so it replaces the service's notification instead of
     * stacking up.
     */
    fun notifyProgress(
        context: Context,
        conversationId: String,
        title: String,
        text: String,
        percent: Int?,
    ) {
        if (!canPost(context)) return
        TurnNotification.ensureChannel(context)
        val notification = TurnNotification.build(context, conversationId, title, text, percent)
        runCatching { NotificationManagerCompat.from(context).notify(TurnNotification.PROGRESS_ID, notification) }
    }

    /** The turn is finished or stopped: the progress notification clears, whoever published it. */
    fun clearProgress(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(TurnNotification.PROGRESS_ID) }
    }

    /**
     * Android 13+: without POST_NOTIFICATIONS granted, `notify` throws a SecurityException — so
     * the permission is checked instead of relying on a `runCatching` (same rule as
     * [ApprovalNotifier], which lint enforces).
     */
    private fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}
