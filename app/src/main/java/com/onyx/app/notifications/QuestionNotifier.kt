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
 * La notification d'une question de l'agent : il attend une réponse, l'app n'est pas au premier plan.
 *
 * Pendant Android de la question référence : une question, un mot de passe sudo ou un secret arrive dans
 * la même conversation, et Onyx le dit sans qu'on ouvre l'app. L'action « Reply » porte un
 * `RemoteInput` : on répond depuis la notification elle-même. Quand la saisie en ligne n'est pas
 * possible — un secret qui ne doit pas traîner dans un champ de notification, une série de
 * questions — l'action ouvre l'app sur la carte, qui reste le seul lieu de saisie.
 *
 * La réponse part par [QuestionActionReceiver], qui appelle le même gateway que la carte : le
 * choix est réel, pas une simple ouverture de l'écran.
 */
object QuestionNotifier {

    /** La clé du champ de saisie de la notification. */
    const val REPLY_KEY = "onyx.reply"

    /**
     * Publie une question, avec son action « Reply » quand la saisie en ligne est possible.
     *
     * @param kind la sorte d'interruption (question, sudo, secret).
     * @param requestId l'identifiant que le gateway attend dans la réponse.
     * @param questionId la question précise, pour une série (`clarify.lock`).
     * @param sessionId la session ouverte, pour sudo et secret.
     */
    fun notifyQuestion(
        context: Context,
        id: String,
        prompt: String,
        kind: NotificationCopy.QuestionKind,
        requestId: String,
        questionId: String? = null,
        sessionId: String? = null,
        /** Le nom de qui pose la question : le bot dans son fil, sinon « Onyx ». */
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
            // Sans saisie en ligne, l'action ouvre l'app : c'est là que la carte attend.
            builder.addAction(0, OnyxLocalization[NotificationCopy.OPEN], open)
        }

        post(context, id, builder)
    }

    /** La question a reçu sa réponse : la notification le dit, ou avoue l'échec. */
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
