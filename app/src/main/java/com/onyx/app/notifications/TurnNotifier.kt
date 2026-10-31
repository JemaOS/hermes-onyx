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
 * La notification de fin : un bot a fini son travail, l'app n'est pas ouverte sur sa conversation.
 *
 * C'est le pendant Android de ce que la référence annonce à la fin d'un tour : un mot sur le
 * résultat — « Terminé » ou « Échec » — et de quoi reconnaître la réponse, sans l'ouvrir. Un tap
 * ramène à la conversation, là où tout est écrit.
 *
 * **Un tour, une notification.** L'identifiant est dérivé de la conversation : republier la même
 * fin remplace la notification au lieu d'en empiler une deuxième, et c'est la même clé qui la fait
 * disparaître ([clear]).
 */
object TurnNotifier {

    /** Le canal des tours : le même que l'avancement, décrit comme « Réponses ». */
    const val CHANNEL_ID = TurnService.CHANNEL_ID

    /** L'identifiant d'une fin de tour, stable par conversation. */
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

    /** Le tour est arrêté ou oublié : sa fin ne doit pas rester à l'écran. */
    fun clear(context: Context, conversationId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(id(conversationId)) }
    }

    /**
     * Le repli quand Android refuse le service de fond (une app en arrière-plan n'a pas le droit
     * d'en démarrer un) : la **même** notification est publiée directement, pour que le suivi
     * tienne quand même. Même identifiant, donc elle remplace celle du service au lieu de
     * s'empiler.
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

    /** Le tour est fini ou arrêté : la notification d'avancement s'efface, quel que soit son auteur. */
    fun clearProgress(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(TurnNotification.PROGRESS_ID) }
    }

    /**
     * Android 13+ : sans POST_NOTIFICATIONS accordée, `notify` lève une SecurityException — on
     * vérifie donc la permission au lieu de compter sur un `runCatching` (même règle que
     * [ApprovalNotifier], que lint fait respecter).
     */
    private fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}
