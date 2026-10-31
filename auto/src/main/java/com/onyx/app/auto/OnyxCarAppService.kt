package com.onyx.app.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.onyx.app.data.model.ChatTurn

/** Le service que la voiture démarre ; l'hôte fournit les templates et la dictée de l'assistant. */
class OnyxCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        // En développement la voiture n'est pas signée par nous ; la validation est désactivée.
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(sessionInfo: SessionInfo): Session = OnyxCarSession()
}

/** Une histoire courte est partagée par les cartes et les questions d'une même session Auto. */
class OnyxCarSession : Session() {
    private val history = mutableListOf<ChatTurn>()

    override fun onCreateScreen(intent: Intent): androidx.car.app.Screen {
        val query = intent.getStringExtra(EXTRA_QUERY)
        return if (query.isNullOrBlank()) {
            AutoHomeScreen(carContext, history)
        } else {
            AskScreen(carContext, query, history)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val query = intent.getStringExtra(EXTRA_QUERY) ?: return
        val manager = carContext.getCarService(ScreenManager::class.java)
        val top = manager.top
        if (top is AskScreen) {
            top.ask(query)
        } else {
            manager.push(AskScreen(carContext, query, history, showBack = true))
        }
    }

    companion object {
        const val EXTRA_QUERY = "query"
    }
}
