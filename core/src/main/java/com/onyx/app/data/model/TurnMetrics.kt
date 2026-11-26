package com.onyx.app.data.model

import com.onyx.app.localization.OnyxLanguage
import kotlinx.serialization.Serializable

/**
 * Le temps qu'a mis une réponse, et à quelle vitesse elle est venue.
 *
 * Miroir de `TurnMetrics` (Echo/Models/Message) : la ligne « 2.2 s · 84 tok/s » sous une
 * réponse terminée. Le temps jusqu'au premier token est celui qui compte pour un modèle qui
 * réfléchit d'abord.
 */
@Serializable
data class TurnMetrics(
    /** Envoi du message. */
    val startedAt: Long,
    /** Premier token reçu (texte ou raisonnement). */
    val firstTokenAt: Long? = null,
    /** Fin du flux. */
    val completedAt: Long? = null,
    /** Tokens de sortie par seconde, mesurés entre le premier token et la fin. */
    val tokensPerSecond: Double? = null,
) {
    /** Durée totale, en secondes. */
    val total: Double?
        get() {
            val end = completedAt ?: return null
            return (end - startedAt).toDouble() / 1000.0
        }

    /** Temps jusqu'au premier token, en secondes. */
    val timeToFirstToken: Double?
        get() {
            val first = firstTokenAt ?: return null
            return (first - startedAt).toDouble() / 1000.0
        }

    /** « 2.2 s · 84 tok/s », comme `MessageRow.compact`. */
    fun compact(): String {
        val parts = mutableListOf<String>()
        (total ?: timeToFirstToken)?.let { parts += format(it) }
        tokensPerSecond?.let { parts += "${it.toInt()} tok/s" }
        return parts.joinToString(" · ").ifEmpty { summary() }
    }

    /** Tous les temps, quand on tape dessus. */
    fun summary(): String {
        val parts = mutableListOf<String>()
        timeToFirstToken?.let { parts += "premier token ${format(it)}" }
        total?.let { parts += "total ${format(it)}" }
        tokensPerSecond?.let { parts += "${String.format(OnyxLanguage.preferred, "%.1f", it)} tok/s" }
        return parts.joinToString(" · ").ifEmpty { "—" }
    }

    fun withFirstToken(at: Long): TurnMetrics = if (firstTokenAt == null) copy(firstTokenAt = at) else this

    fun completed(at: Long, outputTokens: Int): TurnMetrics {
        val first = firstTokenAt
        val rate = if (first != null && at > first && outputTokens > 0) {
            outputTokens * 1000.0 / (at - first).toDouble()
        } else {
            null
        }
        return copy(completedAt = at, tokensPerSecond = rate)
    }

    companion object {
        /** « 12.3 s » sous la minute, « 3m 12s » au-delà. */
        fun format(seconds: Double): String {
            if (seconds < 60) return String.format(OnyxLanguage.preferred, "%.1f s", seconds)
            val whole = seconds.toInt()
            return "${whole / 60}m %02ds".format(whole % 60)
        }
    }
}
