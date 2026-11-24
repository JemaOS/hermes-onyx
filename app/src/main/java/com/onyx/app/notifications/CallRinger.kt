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
 * The incoming call: a turn is waiting for a **decision** (an approval, a question), and the
 * phone **rings** like a call instead of posting a discreet notification.
 *
 * Reproduces the behavior of the reference `ChatNotifications` (`androidApp/.../notify/ChatNotifications.kt`):
 * a `calls` channel with ringtone and vibration, a `CallStyle` notification in
 * **full screen** on the lock screen, a ringtone that insists until one answers or
 * declines, and the fallback to the usual notification when nobody answers.
 *
 * The decision to **ring or not** (the "Calls for a reply" switch, the quiet
 * hours, the foreground) does not live here: it is made where the approval arrives
 * (`OnyxApp`), which calls [ring] when it is time. This class only **rings**.
 *
 * No server route is needed: the ringtone is triggered by the local state (a pending
 * turn), exactly like today's approval notification.
 */
object CallRinger {

    /** The Android call channel: ringtone + vibration, high importance. */
    const val CHANNEL_ID = "onyx.calls"

    /** How long a call rings before becoming an ordinary notification again (45 s, the reference). */
    const val RING_MS = 45_000L

    /** Android's grace period before automatically removing the notification. */
    private const val RING_GRACE_MS = 5_000L

    /** The voice shortcut action: voice mode listens on open (see `MainActivity`). */
    private const val ACTION_VOICE = "com.onyx.app.action.VOICE"

    /** The Android channel identifier, used by [NotificationChannels]. */
    const val CALLS_LABEL = "Calls"
    const val CALLS_DESCRIPTION = "Rings like a call when a turn is waiting for your decision."

    /**
     * A ringing call: who is calling, why, and where one ends up.
     *
     * [talk] true: answering puts Hermes **online** (voice mode) — this is the reference's
     * default behavior. [onMiss] is the fallback when the call is not answered: it republishes
     * the usual notification (approval or question), so that nothing is lost.
     *
     * [onOutcome]: the call outcome — answered, declined, or rung out — returned to whoever placed
     * it. A call **from the agent** (`phone_call_me`) uses it to report `answered` /
     * `declined` / `no_answer` back to the agent (`CallMeTool.kt`, `CallOutcome`). A local call
     * (approval, question) leaves it `null`: nobody is waiting for an outcome.
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

    /** The calls ringing now, by identifier: the call screen closes with its own. */
    val ringing: StateFlow<Map<String, Ringing>> = _ringing.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    /** The call in progress for [id], or `null`. */
    fun current(id: String): Ringing? = _ringing.value[id]

    /** Is a call currently ringing? (the agent's call does not ring over it). */
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

    /** Android 13+ requires POST_NOTIFICATIONS; it is checked instead of relying on a `runCatching`. */
    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Rings [call]. False when the app cannot post a notification (nothing to do, then):
     * the caller republishes the usual notification. A call already ringing is not replaced.
     */
    fun ring(context: Context, call: Ringing): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)
        _ringing.update { it + (call.id to call) }
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(call.id), notification(context, call)) }
        main.postDelayed({ rangOut(context, call.id) }, call.id, RING_MS)
        return true
    }

    /** Nobody answered: the call is removed and the fallback ([Ringing.onMiss]) takes over. */
    private fun rangOut(context: Context, id: String) {
        val call = remove(context, id) ?: return
        call.onOutcome?.invoke(com.onyx.app.calls.CallOutcome.NO_ANSWER)
        call.onMiss?.invoke()
    }

    /** Answered: the call is removed, **without** the fallback — we are talking. */
    fun answered(context: Context, id: String) {
        val call = remove(context, id)
        call?.onOutcome?.invoke(com.onyx.app.calls.CallOutcome.ANSWERED)
    }

    /** Declined: the call is removed and the fallback ([Ringing.onMiss]) republishes the usual notification. */
    fun decline(context: Context, id: String) {
        val call = remove(context, id) ?: return
        call.onOutcome?.invoke(com.onyx.app.calls.CallOutcome.DECLINED)
        call.onMiss?.invoke()
    }

    /** Removes the call (cancels the notification, the timer, the entry). Nothing if there is none. */
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
     * What "Answer" opens: **voice mode** (`talk`), or the app on the conversation. A
     * locked screen requests its unlock along the way, through [IncomingCallActivity].
     */
    fun answerIntent(context: Context, call: Ringing): Intent {
        val base = Intent(context, MainActivity::class.java)
        // Voice mode listens on open: it is the "live voice" of an answered call.
        if (call.talk) base.action = ACTION_VOICE
        base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return base
    }

    /** The call screen intent (the tap on the notification, and the full-screen call). */
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
            // Full screen: on the lock screen, the call takes the whole screen, like a real call.
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_MS + RING_GRACE_MS)
            .build()
        // The ringtone insists to the end; otherwise the chime would play only once.
        notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        return notification
    }

    /** The identifier carried by the call screen intent and by the decline notification. */
    const val EXTRA_CALL_ID = "onyx.call.id"

    private val RING_VIBRATION = longArrayOf(0, 800, 600, 800, 600, 800, 1_600)
}
