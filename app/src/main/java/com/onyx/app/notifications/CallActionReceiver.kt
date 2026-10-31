package com.onyx.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Les boutons de l'appel, quand on n'ouvre pas l'écran : « Refuser » depuis la notification.
 *
 * « Décrocher » n'est pas ici : il ouvre [IncomingCallActivity] (déverrouillage compris), comme dans
 * la référence. « Refuser » n'a besoin que de retirer l'appel et de laisser le repli republier la
 * notification habituelle — d'où un `BroadcastReceiver`, qui n'a aucun écran à ouvrir.
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

        /** L'intention du bouton « Refuser », portée par la notification d'appel. */
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
