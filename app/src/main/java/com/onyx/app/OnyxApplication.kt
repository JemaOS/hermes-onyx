package com.onyx.app

import android.app.Application
import com.onyx.app.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Installs the app's singletons. Nothing else is done here: the services (voice, widgets)
 * arrive at their own stages and will plug into the same container.
 */
class OnyxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // **Before everything else**: a crash during the singleton installation must leave
        // a trace, otherwise only an « l'app s'est arrêtée » (the app has stopped) remains, without explanation.
        runCatching { CrashLog.install(this) }
        AppContainer.install(this)
        // The "quick capture" note box: a note is written to disk at the moment
        // it is taken, and handed over as soon as the server answers — even if the screen is closed.
        com.onyx.app.data.capture.QuickCapture.install(this)
        // **The channels exist from the first launch.** Android only creates a channel on
        // first publication: "Replies" and "Reachable" (French UI strings « Réponses » and « Joignable ») thus stayed "Not created yet" (French UI string « Pas encore créée »)
        // in the Notifications screen, invisible in the system settings, and nothing could be
        // enabled there by default. We register them here, all of them, with their default importance:
        // once created, Android shows them and the person can adjust them — and Android keeps
        // the settings they already made (an app cannot change them afterwards).
        runCatching {
            com.onyx.app.notifications.ApprovalNotifier.ensureChannel(this)
            com.onyx.app.notifications.TurnNotification.ensureChannel(this)
            com.onyx.app.notifications.CallRinger.ensureChannel(this)
            com.onyx.app.notifications.AgentCallService.ensureChannel(this)
        }
        // "Notify me when a reply arrives, app closed" (French UI string « Me prévenir quand une réponse arrive, app fermée »): the periodic work follows the
        // setting — at startup (a restarted phone finds it scheduled without any screen
        // having been opened) as well as on every toggle of the switch. `apply` is idempotent:
        // scheduling already scheduled work does not push back its deadline.
        AppContainer.processScope.launch {
            // We wait for the disk read: before it, the flow carries its default, not the choice.
            AppContainer.settings.loaded.first { it }
            AppContainer.settings.replyWatch.collect { enabled ->
                com.onyx.app.notifications.ReplyWatchScheduler.apply(this@OnyxApplication, enabled)
            }
        }
    }
}
