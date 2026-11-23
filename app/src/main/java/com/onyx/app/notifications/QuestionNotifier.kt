package com.onyx.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.onyx.app.MainActivity
import com.onyx.app.core.R
import com.onyx.app.localization.OnyxLocalization

/**
 * The notification of an agent question: it is waiting for an answer, the app is not in the foreground.
 *
 * Android counterpart of the reference question: a question, a sudo password or a secret arrives in
 * the same conversation, and Onyx says so without anyone opening the app. The "Reply" action carries a
 * `RemoteInput`: you answer from the notification itself. When inline input is not
 * possible — a secret that must not linger in a notification field, a series of
 * questions — the action opens the app on the card, which remains the only place to type.
 *
 * The answer goes through [QuestionActionReceiver], which calls the same gateway as the card: the
 * choice is real, not just an opening of the screen.
 */
object QuestionNotifier {

    /** The key of the notification's input field. */
    const val REPLY_KEY = "onyx.reply"

    /**
     * Publishes a question, with its "Reply" action when inline input is possible.
     *
     * @param kind the kind of interrupt (question, sudo, secret).
     * @param requestId the identifier the gateway expects in the answer.
     * @param questionId the specific question, for a series (`clarify.lock`).
     * @param sessionId the open session, for sudo and secret.
     */
    fun notifyQuestion(
        context: Context,
        id: String,
        prompt: String,
        kind: NotificationCopy.QuestionKind,
        requestId: String,
        questionId: String? = null,
        sessionId: String? = null,
        /** The name of whoever asks the question: the bot in its thread, otherwise "Onyx". */
        name: String? = null,
        canReplyInline: Boolean = true,
        enabled: Boolean = true,
        sound: Boolean = true,
    ) {
        if (!enabled) return
        ApprovalNotifier.ensureChannel(context)
        if (!ApprovalNotifier.canNotify(context)) return

        val open = openIntent(context, id)
        val text = NotificationCopy.questionText(prompt)
        val builder = NotificationCompat.Builder(context, ApprovalNotifier.channelForSound(sound))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(OnyxLocalization[NotificationCopy.questionTitle(kind, name)])
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)

        if (canReplyInline) {
            builder.addAction(
                replyAction(context, id, kind, requestId, questionId, sessionId, prompt, sound),
            )
        } else {
            // Without inline input, the action opens the app: that is where the card waits.
            builder.addAction(0, OnyxLocalization[NotificationCopy.OPEN], open)
        }

        post(context, id, builder)
    }

    /** The question received its answer: the notification says so, or admits the failure. */
    fun notifyResolved(context: Context, id: String, prompt: String, failed: Boolean) {
        ApprovalNotifier.ensureChannel(context)
        if (!ApprovalNotifier.canNotify(context)) return

        val text = if (failed) NotificationCopy.UNANSWERED_TEXT else NotificationCopy.questionText(prompt)
        val builder = NotificationCompat.Builder(context, ApprovalNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                OnyxLocalization[
                    if (failed) NotificationCopy.UNANSWERED_TITLE else NotificationCopy.ANSWERED_TITLE,
                ],
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, id))
        post(context, id, builder)
    }

    fun clear(context: Context, id: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(id.hashCode()) }
    }

    private fun replyAction(
        context: Context,
        id: String,
        kind: NotificationCopy.QuestionKind,
        requestId: String,
        questionId: String?,
        sessionId: String?,
        prompt: String,
        sound: Boolean,
    ): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel(OnyxLocalization[NotificationCopy.REPLY])
            .build()
        val intent = Intent(context, QuestionActionReceiver::class.java).apply {
            action = QuestionActionReceiver.ACTION_REPLY
            putExtra(QuestionActionReceiver.EXTRA_KIND, kind.name)
            putExtra(QuestionActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(QuestionActionReceiver.EXTRA_QUESTION_ID, questionId)
            putExtra(QuestionActionReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(QuestionActionReceiver.EXTRA_PROMPT, prompt)
            putExtra(QuestionActionReceiver.EXTRA_NOTIFICATION_ID, id)
            putExtra(QuestionActionReceiver.EXTRA_SOUND, sound)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (id + "reply").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, OnyxLocalization[NotificationCopy.REPLY], pending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun openIntent(context: Context, id: String): PendingIntent = PendingIntent.getActivity(
        context,
        id.hashCode(),
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(context: Context, id: String, builder: NotificationCompat.Builder) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build()) }
        }
    }
}
