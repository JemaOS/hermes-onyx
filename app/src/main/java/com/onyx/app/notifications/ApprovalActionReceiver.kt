package com.onyx.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onyx.app.data.net.GatewayApprovalClient
import com.onyx.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Répond à une approbation depuis la notification, sans ouvrir l'app.
 *
 * C'est l'équivalent Android des boutons d'une notification référence : le choix est transmis au
 * gateway (`POST /v1/runs/{id}/approval`). La demande est retirée aussitôt — ses boutons ne
 * doivent pas rester à l'écran pendant l'appel — puis remplacée par son résultat : « Approved »
 * ou « Denied », ou « Couldn't answer » si l'appel a échoué.
 */
class ApprovalActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val choice = intent.getStringExtra(EXTRA_CHOICE)
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID)
        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID) ?: requestId
        val command = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE)

        // La demande s'efface d'abord : ses boutons ne doivent pas rester cliquables pendant
        // l'appel, et c'est le résultat qui reprend place sous le même identifiant.
        notificationId?.let { ApprovalNotifier.clear(context, it) }
        if (runId.isNullOrEmpty() || choice.isNullOrEmpty()) return
        val approved = choice != "deny"

        val server = AppContainer.servers.servers.value.firstOrNull { it.id == serverId }
            ?: AppContainer.servers.activeServer.value

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var failed = false
            try {
                GatewayApprovalClient(server).respond(runId, requestId, choice)
            } catch (error: Exception) {
                // Le refus ne peut pas s'afficher dans l'app (elle n'est pas ouverte) : c'est la
                // notification qui le dit, pour que la commande ne reste pas en attente muette.
                failed = true
            } finally {
                notificationId?.let {
                    ApprovalNotifier.notifyResolved(
                        context = context,
                        id = it,
                        command = command,
                        approved = approved,
                        failed = failed,
                        title = title,
                    )
                }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "com.onyx.app.action.APPROVE"
        const val ACTION_DENY = "com.onyx.app.action.DENY"

        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_CHOICE = "choice"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_TITLE = "title"
    }
}
