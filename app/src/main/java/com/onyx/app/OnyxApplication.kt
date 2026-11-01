package com.onyx.app

import android.app.Application
import com.onyx.app.di.AppContainer

/**
 * Installe les singletons de l'app. Rien d'autre n'est fait ici : les services (voix, widgets)
 * arrivent à leurs étapes et se brancheront sur le même conteneur.
 */
class OnyxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.install(this)
        // La boîte des notes de « capture rapide » : une note est écrite sur le disque au moment
        // où elle est prise, et remise dès que le serveur répond — même si l'écran est fermé.
        com.onyx.app.data.capture.QuickCapture.install(this)
        // **Les canaux existent dès le premier lancement.** Android ne crée un canal qu'à la
        // première publication : « Réponses » et « Joignable » restaient donc « Pas encore créée »
        // dans l'écran Notifications, invisibles dans les réglages du système, et rien ne pouvait y
        // être activé par défaut. On les enregistre ici, tous, avec leur importance par défaut :
        // une fois créés, Android les affiche et la personne peut les régler — et Android conserve
        // les réglages qu'elle a déjà faits (une app ne peut pas les modifier ensuite).
        runCatching {
            com.onyx.app.notifications.ApprovalNotifier.ensureChannel(this)
            com.onyx.app.notifications.TurnNotification.ensureChannel(this)
            com.onyx.app.notifications.CallRinger.ensureChannel(this)
            com.onyx.app.notifications.AgentCallService.ensureChannel(this)
        }
    }
}
