package com.onyx.app

import android.app.Application
import com.onyx.app.di.AppContainer

/**
 * Installe les singletons de l'app. Rien d'autre n'est fait ici: les services (voix, widgets)
 * arrivent à leurs étapes et se brancheront sur le même conteneur.
 */
class OnyxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.install(this)
        // La boîte des notes de « capture rapide »: une note est écrite sur le disque au moment
        // où elle est prise, et remise dès que le serveur répond — même si l'écran est fermé.
        com.onyx.app.data.capture.QuickCapture.install(this)
    }
}
