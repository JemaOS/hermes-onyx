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
 * La notification d'approbation: l'agent attend une réponse, l'app n'est pas au premier plan.
 *
 * C'est l'comme la notification quand une approbation arrive: un tap ouvre
 * l'app sur la conversation, où les boutons attendent.
 */
object ApprovalNotifier {

    const val CHANNEL_ID = "onyx.approvals"
    const val SILENT_CHANNEL_ID = "onyx.approvals.silent"

    /** Les canaux Android gardent leur réglage de son: le choix se fait donc à la publication. */
    fun channelForSound(sound: Boolean): String = if (sound) CHANNEL_ID else SILENT_CHANNEL_ID

    /** Crée les canaux; sans effet sur les réglages que l'utilisateur a déjà personnalisés. */
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
     * Publie la demande d'approbation, avec ses deux boutons quand le run est connu: approuver
     * ou refuser sans ouvrir l'app.
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

        // Android 13+: sans POST_NOTIFICATIONS accordée, `notify` lève une SecurityException.
        // On vérifie donc la permission (et l'état des notifications) au lieu de compter sur le
        // `runCatching`: lint le refuse, et une notification silencieusement perdue serait pire.
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build()) }
        }
    }

    /**
     * Une alerte de démonstration: elle passe par le même canal et la même permission que les
     * vraies, ce qui montre à l'utilisateur ce qu'il recevra et crée les canaux au passage.
     *
     * C'est l'action du relais (`hermes onyx-push test`, « It should send a notification
     * within a few seconds ») adaptée à Android: au lieu d'envoyer depuis le serveur, la
     * notification est publiée ici, comme toutes les alertes Android de Onyx.
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
            // Le résultat remplace la demande: il lui faut de quoi la réécrire.
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
     * Remplace la demande par son résultat, une fois la réponse envoyée.
     *
     * Le choix part d'un `BroadcastReceiver`, qui n'a aucun écran pour le confirmer: c'est donc la
     * notification qui le fait — « Approved » / « Denied », sans boutons, ou « Couldn't answer »
     * avec le pas à suivre quand l'appel au gateway a échoué. Sans cette étape, appuyer sur
     * « Approve » ferait disparaître la demande sans qu'on sache si l'agent a été entendu.
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

    /** Les deux réponses que la notification propose (les mots du gateway). */
    private const val APPROVE = "once"
    private const val DENY = "deny"

    /** L'identifiant de l'alerte de démonstration. */
    private const val TEST_ID = "onyx.test"
}
