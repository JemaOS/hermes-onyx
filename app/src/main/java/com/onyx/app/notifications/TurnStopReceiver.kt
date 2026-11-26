package com.onyx.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onyx.app.di.AppContainer

/**
 * "Arrêter" ("Stop") from the progress notification, without opening the app.
 *
 * The gesture goes through a receiver: it has no screen to confirm it, so it is the notification
 * that says so — it clears right away, and the turn is **really** stopped on the host
 * (`TurnStore.stop`, which interrupts the stream and notifies the server).
 *
 * Without this receiver, a notification's action would only make it disappear: that is the exact
 * case the owner reports as "ça marche pas" ("it doesn't work").
 */
class TurnStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        // The turn is stopped in the process store: that is what holds the turn, not
        // the screen — the conversation may very well not be open.
        if (!conversationId.isNullOrEmpty() && AppContainer.isInstalled) {
            AppContainer.turns.stop(conversationId)
        }
        TurnNotifier.clear(context, conversationId.orEmpty())
        TurnService.stop(context)
    }

    companion object {
        const val ACTION_STOP = "com.onyx.app.action.TURN_STOP"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
