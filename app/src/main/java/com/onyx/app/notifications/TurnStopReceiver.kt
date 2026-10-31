package com.onyx.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onyx.app.di.AppContainer

/**
 * « Arrêter » depuis la notification d'avancement, sans ouvrir l'app.
 *
 * Le geste part d'un récepteur : il n'a aucun écran pour le confirmer, donc c'est la notification
 * qui le dit — elle s'efface tout de suite, et le tour est **vraiment** arrêté chez l'hôte
 * (`TurnStore.stop`, qui interrompt le flux et prévient le serveur).
 *
 * Sans ce récepteur, l'action d'une notification ne ferait que disparaître : c'est le cas exact
 * que le propriétaire rapporte comme « ça marche pas ».
 */
class TurnStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        // Le tour est arrêté dans le magasin du processus : c'est lui qui tient le tour, pas
        // l'écran — la conversation peut très bien ne pas être ouverte.
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
