package com.onyx.app.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.onyx.app.data.model.ChatTurn

/** The service the car starts; the host provides the templates and the assistant's dictation. */
class OnyxCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        // In development the car is not signed by us; validation is disabled.
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(sessionInfo: SessionInfo): Session = OnyxCarSession()
}

/** A short history is shared by the cards and the questions of a single Auto session. */
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
