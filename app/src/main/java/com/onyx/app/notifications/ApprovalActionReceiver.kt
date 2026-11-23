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
 * Answers an approval from the notification, without opening the app.
 *
 * This is the Android equivalent of the buttons of a reference notification: the choice is sent to the
 * gateway (`POST /v1/runs/{id}/approval`). The request is removed immediately — its buttons must
 * not stay on screen during the call — then replaced by its result: "Approved"
 * or "Denied", or "Couldn't answer" if the call failed.
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

        // The request is cleared first: its buttons must not stay clickable during
        // the call, and the result takes its place under the same identifier.
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
                // The refusal cannot be shown in the app (it is not open): the
                // notification says it, so that the command does not stay silently pending.
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
