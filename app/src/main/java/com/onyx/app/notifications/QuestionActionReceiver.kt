package com.onyx.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.onyx.app.data.net.ServeInterruptRpc
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Répond à la question d'un agent depuis la notification, sans ouvrir l'app.
 *
 * La saisie vient d'un `RemoteInput` (l'action « Reply »). La réponse part par le même gateway que
 * la carte : `clarify.lock` pour une question, `sudo.respond` pour un mot de passe,
 * `secret.respond` pour un secret. La notification est retirée aussitôt — le champ ne doit pas
 * rester ouvert pendant l'appel — puis remplacée par son résultat.
 */
class QuestionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)
        val kind = intent.getStringExtra(EXTRA_KIND)
            ?.let { name -> NotificationCopy.QuestionKind.entries.firstOrNull { it.name == name } }
            ?: return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val questionId = intent.getStringExtra(EXTRA_QUESTION_ID)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty()

        val answer = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(QuestionNotifier.REPLY_KEY)
            ?.toString()
            ?.trim()

        notificationId?.let { QuestionNotifier.clear(context, it) }
        if (answer.isNullOrEmpty() || requestId.isNullOrEmpty()) return

        val server = AppContainer.servers.activeServer.value
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var failed = false
            try {
                val rpc = ServeRpc(server)
                val client = ServeInterruptRpc(rpc)
                when (kind) {
                    NotificationCopy.QuestionKind.CLARIFY -> client.clarify(requestId, questionId, answer)
                    NotificationCopy.QuestionKind.SUDO -> client.sudo(sessionId.orEmpty(), requestId, answer)
                    NotificationCopy.QuestionKind.SECRET -> client.secret(sessionId.orEmpty(), requestId, answer)
                }
                rpc.close()
            } catch (error: Exception) {
                // L'app n'est pas ouverte : c'est la notification qui porte l'échec, sinon la
                // question disparaîtrait sans qu'on sache si l'agent l'a reçue.
                failed = true
            } finally {
                notificationId?.let { QuestionNotifier.notifyResolved(context, it, prompt, failed) }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.onyx.app.action.REPLY"

        const val EXTRA_KIND = "kind"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_QUESTION_ID = "question_id"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SOUND = "sound"
    }
}
