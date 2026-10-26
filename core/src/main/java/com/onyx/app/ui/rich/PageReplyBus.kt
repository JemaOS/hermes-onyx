package com.onyx.app.ui.rich

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Ce que les pages d'agent reçoivent en retour.
 *
 * Une page qui envoie un message par `Onyx.postMessage` attend la réponse de l'agent; elle
 * arrive par le fil normal, et ce bus la lui remet. Une page s'abonne à l'ouverture, se
 * désabonne en partant.
 */
object PageReplyBus {

    private val _replies = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val replies: SharedFlow<String> = _replies.asSharedFlow()

    /** Publie la réponse d'un tour: chaque page ouverte la reçoit. */
    fun publish(text: String) {
        if (text.isNotBlank()) _replies.tryEmit(text)
    }
}
