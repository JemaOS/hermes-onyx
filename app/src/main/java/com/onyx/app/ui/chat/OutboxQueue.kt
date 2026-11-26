package com.onyx.app.ui.chat

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.OutboxState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** La file strictement ordonnée de la conversation ; toutes ses transitions sont testables hors UI. */
internal class OutboxQueue(initial: List<OutboxItem> = emptyList()) {
    private val values = initial.toMutableList()

    val items: List<OutboxItem> get() = values.toList()

    fun replace(next: List<OutboxItem>) {
        values.clear()
        values.addAll(next)
    }

    fun clear() = values.clear()

    fun enqueue(item: OutboxItem, waiting: Boolean) {
        val mustWait = waiting || values.firstOrNull()?.state == OutboxState.WAITING_FOR_CONNECTION
        values += item.copy(state = if (mustWait) OutboxState.WAITING_FOR_CONNECTION else OutboxState.QUEUED)
    }

    /** Le tour qui n'a rien reçu revient en tête ; les messages déjà en file attendent aussi. */
    fun hold(item: OutboxItem) {
        for (index in values.indices) {
            if (values[index].state == OutboxState.QUEUED) {
                values[index] = values[index].copy(state = OutboxState.WAITING_FOR_CONNECTION)
            }
        }
        values.add(0, item.copy(state = OutboxState.WAITING_FOR_CONNECTION))
    }

    /** Après Stop ou une erreur définitive, les envois automatiques s'arrêtent. */
    fun pause() {
        for (index in values.indices) {
            if (values[index].state == OutboxState.QUEUED) {
                values[index] = values[index].copy(state = OutboxState.PAUSED)
            }
        }
    }

    /** « Send now » ne concerne que la tête et confirme aussi la reprise des suivants. */
    fun confirmFirst(id: String, now: Long): Boolean {
        val first = values.firstOrNull() ?: return false
        if (first.id != id) return false
        for (index in values.indices) {
            if (values[index].state == OutboxState.PAUSED) {
                values[index] = values[index].copy(state = OutboxState.QUEUED)
            }
        }
        values[0] = values[0].copy(state = OutboxState.QUEUED, queuedAt = now)
        return true
    }

    /** Retire la tête si elle peut partir ; une attente périmée passe toute la file en pause. */
    fun takeNext(now: Long): OutboxItem? {
        val first = values.firstOrNull() ?: return null
        if (first.state == OutboxState.PAUSED) return null
        if (first.state == OutboxState.WAITING_FOR_CONNECTION && first.isStale(now)) {
            for (index in values.indices) values[index] = values[index].copy(state = OutboxState.PAUSED)
            return null
        }
        return values.removeAt(0)
    }

    fun remove(id: String): OutboxItem? {
        val index = values.indexOfFirst { it.id == id }
        return if (index < 0) null else values.removeAt(index)
    }
}

/** Le statut de `QueuedMessageRow`, y compris l'heure dans la locale et le fuseau demandés. */
internal fun outboxStatusText(
    item: OutboxItem,
    isFirst: Boolean,
    now: Long = System.currentTimeMillis(),
    locale: Locale = OnyxLanguage.preferred,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { this.timeZone = timeZone }
    val time = formatter.format(Date(item.queuedAt))
    return when (item.state) {
        OutboxState.QUEUED -> if (isFirst) "Sends after this reply" else "Queued"
        OutboxState.WAITING_FOR_CONNECTION ->
            if (isFirst) "Waiting for connection · $time" else "Waiting · $time"
        OutboxState.PAUSED -> if (item.isStale(now)) "Not sent · from $time" else "Not sent"
    }
}

internal fun outboxAttachmentLabel(count: Int): String =
    if (count == 1) "1 attachment" else "$count attachments"
