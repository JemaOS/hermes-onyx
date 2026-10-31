package com.onyx.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.onyx.app.MainActivity
import com.onyx.app.core.R
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * L'appel entrant : un tour attend une **décision** (une approbation, une question), et le
 * téléphone **sonne** comme un appel au lieu de poser une notification discrète.
 *
 * Reprend le comportement de `ChatNotifications` (`androidApp/.../notify/ChatNotifications.kt`) de
 * la référence : un canal `calls` avec sonnerie et vibration, une notification `CallStyle` en
 * **plein écran** sur l'écran verrouillé, une sonnerie qui insiste jusqu'à ce qu'on décroche ou
 * refuse, et le repli sur la notification habituelle quand personne ne décroche.
 *
 * La décision de **sonner ou non** (l'interrupteur « Appels pour une réponse », les heures de
 * tranquillité, le premier plan) ne vit pas ici : elle est prise là où l'approbation arrive
 * (`OnyxApp`), qui appelle [ring] quand c'est le moment. Cette classe ne fait que **sonner**.
 *
 * Aucune route serveur n'est nécessaire : le sonnerie est déclenchée par l'état local (un tour en
 * attente), exactement comme la notification d'approbation d'aujourd'hui.
 */
object CallRinger {

    /** Le canal Android de l'appel : sonnerie + vibration, importance haute. */
    const val CHANNEL_ID = "onyx.calls"

    /** Combien de temps sonne un appel avant de redevenir une notification ordinaire (45 s, la référence). */
    const val RING_MS = 45_000L

    /** La marge d'Android sur le retrait automatique de la notification. */
    private const val RING_GRACE_MS = 5_000L

    /** L'action du raccourci vocal : le mode vocal écoute à l'ouverture (voir `MainActivity`). */
    private const val ACTION_VOICE = "com.onyx.app.action.VOICE"

    /** L'identifiant du canal Android, employé par [NotificationChannels]. */
    const val CALLS_LABEL = "Calls"
    const val CALLS_DESCRIPTION = "Rings like a call when a turn is waiting for your decision."

    /**
     * Un appel qui sonne : qui appelle, pourquoi, et où l'on retombe.
     *
     * [talk] vrai : décrocher met Hermes **en ligne** (le mode vocal) — c'est le comportement par
     * défaut de la référence. [onMiss] est le repli quand l'appel n'est pas décroché : il republie
     * la notification habituelle (approbation ou question), pour que rien ne se perde.
     *
     * [onOutcome] : l'issue de l'appel — décroché, refusé, ou sonné dans le vide — rendue à qui l'a
     * placé. Un appel **de l'agent** (`phone_call_me`) s'en sert pour remonter `answered` /
     * `declined` / `no_answer` à l'agent (`CallMeTool.kt`, `CallOutcome`). Un appel local
     * (approbation, question) le laisse `null` : personne n'attend d'issue.
     */
    class Ringing(
        val id: String,
        val title: String?,
        val why: String,
        val talk: Boolean,
        val conversationId: String? = null,
        val onMiss: (() -> Unit)? = null,
        val onOutcome: ((com.onyx.app.calls.CallOutcome) -> Unit)? = null,
    )

    private val _ringing = MutableStateFlow<Map<String, Ringing>>(emptyMap())

    /** Les appels qui sonnent maintenant, par identifiant : l'écran d'appel se ferme avec le sien. */
    val ringing: StateFlow<Map<String, Ringing>> = _ringing.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    /** L'appel en cours pour [id], ou `null`. */
    fun current(id: String): Ringing? = _ringing.value[id]

    /** Y a-t-il un appel en train de sonner ? (l'appel de l'agent ne sonne pas par-dessus). */
    val isRinging: Boolean get() = _ringing.value.isNotEmpty()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            OnyxLocalization[CALLS_LABEL],
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = OnyxLocalization[CALLS_DESCRIPTION]
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = RING_VIBRATION
        }
        manager.createNotificationChannel(channel)
    }

    /** Android 13+ exige POST_NOTIFICATIONS ; on la vérifie au lieu de compter sur un `runCatching`. */
    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Fait sonner [call]. Faux quand l'app ne peut pas publier de notification (rien à faire, alors) :
     * l'appelant republie la notification habituelle. Un appel déjà en sonnerie n'est pas remplacé.
     */
    fun ring(context: Context, call: Ringing): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)
        _ringing.update { it + (call.id to call) }
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(call.id), notification(context, call)) }
        main.postDelayed({ rangOut(context, call.id) }, call.id, RING_MS)
        return true
    }

    /** Personne n'a décroché : l'appel se retire et le repli ([Ringing.onMiss]) reprend la main. */
    private fun rangOut(context: Context, id: String) {
        val call = remove(context, id) ?: return
        call.onOutcome?.invoke(com.onyx.app.calls.CallOutcome.NO_ANSWER)
        call.onMiss?.invoke()
    }

    /** Décroché : l'appel se retire, **sans** le repli — on est en train de parler. */
    fun answered(context: Context, id: String) {
        val call = remove(context, id)
        call?.onOutcome?.invoke(com.onyx.app.calls.CallOutcome.ANSWERED)
    }

    /** Refusé : l'appel se retire et le repli ([Ringing.onMiss]) republie la notification habituelle. */
    fun decline(context: Context, id: String) {
        val call = remove(context, id) ?: return
        call.onOutcome?.invoke(com.onyx.app.calls.CallOutcome.DECLINED)
        call.onMiss?.invoke()
    }

    /** Retire l'appel (annule la notification, le minuteur, l'entrée). Rien s'il n'y en a pas. */
    private fun remove(context: Context, id: String): Ringing? {
        var call: Ringing? = null
        _ringing.update {
            call = it[id]
            it - id
        }
        main.removeCallbacksAndMessages(id)
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(id)) }
        return call
    }

    /**
     * Ce que « Décrocher » ouvre : le **mode vocal** (`talk`), ou l'app sur la conversation. Un
     * écran verrouillé demande son déverrouillage en chemin, par [IncomingCallActivity].
     */
    fun answerIntent(context: Context, call: Ringing): Intent {
        val base = Intent(context, MainActivity::class.java)
        // Le mode vocal écoute à l'ouverture : c'est la « voix live » d'un appel décroché.
        if (call.talk) base.action = ACTION_VOICE
        base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return base
    }

    /** L'intention de l'écran d'appel (le tap sur la notification, et l'appel plein écran). */
    fun screenIntent(context: Context, id: String): Intent = Intent(context, IncomingCallActivity::class.java)
        .putExtra(EXTRA_CALL_ID, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    private fun notificationId(id: String): Int = ("onyx.call." + id).hashCode()

    private fun notification(context: Context, call: Ringing): android.app.Notification {
        val caller = Person.Builder()
            .setName(call.title?.takeIf { it.isNotBlank() } ?: OnyxLocalization["Hermes"])
            .setImportant(true)
            .build()

        val answer = PendingIntent.getActivity(
            context,
            ("answer" + call.id).hashCode(),
            screenIntent(context, call.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val decline = CallActionReceiver.declinePendingIntent(context, call.id)
        val screen = PendingIntent.getActivity(
            context,
            ("ring" + call.id).hashCode(),
            screenIntent(context, call.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
            .setContentText(call.why)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Plein écran : sur l'écran verrouillé, l'appel prend tout l'écran, comme un vrai appel.
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_MS + RING_GRACE_MS)
            .build()
        // La sonnerie insiste jusqu'au bout ; sinon le carillon ne jouerait qu'une fois.
        notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        return notification
    }

    /** L'identifiant porté par l'intention de l'écran d'appel et par la notification de refus. */
    const val EXTRA_CALL_ID = "onyx.call.id"

    private val RING_VIBRATION = longArrayOf(0, 800, 600, 800, 600, 800, 1_600)
}
