package com.onyx.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.onyx.app.localization.OnyxLocalization

/**
 * The approval notification: the agent is waiting for an answer, the app is not in the foreground.
 *
 * This is the Android equivalent of the reference notification when an approval arrives: a tap opens
 * the app on the conversation, where the buttons wait.
 */
object ApprovalNotifier {

    const val CHANNEL_ID = "onyx.approvals"
    const val SILENT_CHANNEL_ID = "onyx.approvals.silent"

    /** Android channels keep their sound setting: the choice is therefore made at publish time. */
    fun channelForSound(sound: Boolean): String = if (sound) CHANNEL_ID else SILENT_CHANNEL_ID

    /** Creates the channels; no effect on settings the user has already customized. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_ID,
                    OnyxLocalization["Approvals"],
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = OnyxLocalization["Asks when Onyx wants to run something on your machine."]
                },
                NotificationChannel(
                    SILENT_CHANNEL_ID,
                    OnyxLocalization["Silent approvals"],
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = OnyxLocalization["Asks when Onyx wants to run something on your machine."]
                    setSound(null, null)
                    enableVibration(false)
                },
            ),
        )
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Publishes the approval request, with its two buttons when the run is known: approve
     * or deny without opening the app.
     */
    fun notifyApproval(
        context: Context,
        id: String,
        command: String,
        title: String,
        runId: String? = null,
        serverId: String? = null,
        enabled: Boolean = true,
        sound: Boolean = true,
    ) {
        if (!enabled) return
        ensureChannel(context)
        if (!canNotify(context)) return

        val open = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelForSound(sound))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(OnyxLocalization["Approval needed"])
            .setContentText(command.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(command))
            .setSubText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)

        if (!runId.isNullOrEmpty()) {
            builder.addAction(0, OnyxLocalization[NotificationCopy.APPROVE], approvalAction(context, id, runId, serverId, APPROVE, command, title))
            builder.addAction(0, OnyxLocalization[NotificationCopy.DENY], approvalAction(context, id, runId, serverId, DENY, command, title))
        }

        // Android 13+: without POST_NOTIFICATIONS granted, `notify` throws a SecurityException.
        // The permission (and the notification state) is therefore checked instead of relying on
        // `runCatching`: lint rejects it, and a silently lost notification would be worse.
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build()) }
        }
    }

    /**
     * A demo alert: it goes through the same channel and the same permission as the
     * real ones, which shows the user what they will receive and creates the channels along the way.
     *
     * This is the reference relay action (`hermes onyx-push test`, "It should send a notification
     * within a few seconds") adapted to Android: instead of sending from the server, the
     * notification is published here, like all Onyx Android alerts.
     */
    fun notifyTest(context: Context, sound: Boolean = true) {
        ensureChannel(context)
        if (!canNotify(context)) return

        val open = PendingIntent.getActivity(
            context,
            TEST_ID.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = OnyxLocalization["This is what an alert looks like. Onyx posts them on this phone."]
        val builder = NotificationCompat.Builder(context, channelForSound(sound))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(OnyxLocalization["Test notification"])
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(TEST_ID.hashCode(), builder.build()) }
        }
    }

    private fun approvalAction(
        context: Context,
        id: String,
        runId: String,
        serverId: String?,
        choice: String,
        command: String,
        title: String,
    ): PendingIntent {
        val intent = Intent(context, ApprovalActionReceiver::class.java).apply {
            action = if (choice == APPROVE) ApprovalActionReceiver.ACTION_APPROVE else ApprovalActionReceiver.ACTION_DENY
            putExtra(ApprovalActionReceiver.EXTRA_RUN_ID, runId)
            putExtra(ApprovalActionReceiver.EXTRA_REQUEST_ID, id)
            putExtra(ApprovalActionReceiver.EXTRA_CHOICE, choice)
            putExtra(ApprovalActionReceiver.EXTRA_SERVER_ID, serverId)
            putExtra(ApprovalActionReceiver.EXTRA_NOTIFICATION_ID, id)
            // The result replaces the request: it needs what is required to rewrite it.
            putExtra(ApprovalActionReceiver.EXTRA_COMMAND, command)
            putExtra(ApprovalActionReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            (id + choice).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun clear(context: Context, id: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(id.hashCode()) }
    }

    /**
     * Replaces the request with its result, once the answer has been sent.
     *
     * The choice comes from a `BroadcastReceiver`, which has no screen to confirm it: it is therefore the
     * notification that does it — "Approved" / "Denied", without buttons, or "Couldn't answer"
     * with the next step when the gateway call failed. Without this step, pressing
     * "Approve" would make the request disappear without one knowing whether the agent was heard.
     */
    fun notifyResolved(
        context: Context,
        id: String,
        command: String,
        approved: Boolean,
        failed: Boolean,
        title: String? = null,
        sound: Boolean = true,
    ) {
        ensureChannel(context)
        if (!canNotify(context)) return

        val open = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = NotificationCopy.answeredText(command, failed)
        val builder = NotificationCompat.Builder(context, channelForSound(sound))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                OnyxLocalization[
                    if (failed) NotificationCopy.UNANSWERED_TITLE else NotificationCopy.resolvedTitle(approved),
                ],
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open)
        title?.takeIf { it.isNotBlank() }?.let { builder.setSubText(it) }

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build()) }
        }
    }

    /** The two answers the notification offers (the gateway's words). */
    private const val APPROVE = "once"
    private const val DENY = "deny"

    /** The identifier of the demo alert. */
    private const val TEST_ID = "onyx.test"
}
