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
 * La notification d'avancement d'un tour : étape, durée, progression, bouton **Arrêter**.
 *
 * Un seul exemplaire dans la barre d'état — l'identifiant est fixe — et deux façons de la tenir :
 * le service de fond ([TurnService]) quand Android accepte de le démarrer, et une publication
 * directe ([TurnNotifier.notifyProgress]) quand il le refuse (démarrage depuis l'arrière-plan,
 * par exemple). Le contenu, lui, est **le même** : une seule fonction le construit, donc les deux
 * chemins ne peuvent pas diverger.
 *
 * Sur Android 16 elle prend le style « progression » et demande à être promue en **Live Update** :
 * le système peut alors la suivre en pastille de barre d'état (Android 16 QPR1 et plus), hors de
 * l'écran de notifications.
 */
object TurnNotification {

    /** L'identifiant de la notification d'avancement : un tour à la fois dans la barre d'état. */
    const val PROGRESS_ID = 41

    /** La clé de la demande de promotion en Live Update (Android 16 QPR1). */
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** Enregistre le canal ; Android ne modifie jamais un canal que l'utilisateur a réglé. */
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

    /** La notification, telle que la publient le service et le repli. */
    fun build(context: Context, conversationId: String, title: String, phase: String, percent: Int?): Notification {
        val thread = title.ifBlank { NotificationCopy.ONYX }
        val localizedPhase = context.notificationLabel(phase.ifBlank { "Answering…" })
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // « Arrêter » part d'un récepteur : il arrête le tour dans le magasin du processus, même
        // quand aucun écran n'est ouvert (voir [TurnStopReceiver]).
        val stop = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TurnStopReceiver::class.java)
                .setAction(TurnStopReceiver.ACTION_STOP)
                .putExtra(TurnStopReceiver.EXTRA_CONVERSATION_ID, conversationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 : le style « progression » et le texte court font de la notification un
            // Live Update, suivi par le système au-dessus de l'écran d'accueil — en pastille de
            // barre d'état sur un appareil qui a la promotion.
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
                // Le canal laisse passer le son de la notification de fin ; l'avancement, lui,
                // se met à jour en silence — jamais de son ni de vibration à chaque étape.
                .setSound(null)
                .setVibrate(null)
                .setContentIntent(open)
                .setShortCriticalText(localizedPhase)
                // La demande de promotion n'est exposée qu'à partir d'Android 16 QPR1 : à
                // `compileSdk 36` on l'écrit par sa clé, comme le recommande la documentation.
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
