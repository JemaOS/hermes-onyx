package com.onyx.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The call buttons, when the screen is not opened: "Decline" from the notification.
 *
 * "Answer" is not here: it opens [IncomingCallActivity] (unlock included), as in
 * the reference. "Decline" only needs to remove the call and let the fallback republish the
 * usual notification — hence a `BroadcastReceiver`, which has no screen to open.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(CallRinger.EXTRA_CALL_ID) ?: return
        when (intent.action) {
            ACTION_DECLINE -> CallRinger.decline(context.applicationContext, id)
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.onyx.app.action.DECLINE_CALL"

        /** The intent of the "Decline" button, carried by the call notification. */
        fun declinePendingIntent(context: Context, callId: String): PendingIntent {
            val intent = Intent(context, CallActionReceiver::class.java).apply {
                action = ACTION_DECLINE
                putExtra(CallRinger.EXTRA_CALL_ID, callId)
            }
            return PendingIntent.getBroadcast(
                context,
                (ACTION_DECLINE + callId).hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
