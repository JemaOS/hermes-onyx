package com.onyx.app.notifications

import com.onyx.app.ui.chat.TurnLive
import com.onyx.app.ui.chat.TurnSession
import com.onyx.app.ui.chat.TurnStep

/**
 * Qui se notifie, et quand — décidé **sans Android**, donc vérifiable.
 *
 * Trois règles, et rien d'autre :
 *
 *  1. le choix « Jamais » ne notifie rien, jamais ;
 *  2. l'app ouverte **sur la conversation concernée** ne notifie pas : l'utilisateur a le travail
 *     sous les yeux (même règle que pour une approbation ou une question) ;
 *  3. un tour **arrêté** ne se raconte pas : sa notification s'efface, il ne reçoit pas de
 *     notification de fin — l'utilisateur sait qu'il vient d'arrêter.
 *
 * Ce qui reste est l'issue d'un tour : « Terminé » ou « Échec », une seule fois.
 */
object WorkTrackingPolicy {

    /** Faut-il notifier ce tour-ci (sa fin, et son avancement s'il se suit en direct) ? */
    fun tracks(
        tracking: WorkTracking,
        appInForeground: Boolean,
        visibleConversationId: String?,
        conversationId: String,
    ): Boolean {
        if (!tracking.notifies) return false
        if (appInForeground && visibleConversationId == conversationId) return false
        return true
    }

    /** L'issue d'un tour fini, quand elle mérite une notification de fin. */
    fun finishOf(live: TurnLive): TurnFinish? = when (live.phase.step) {
        TurnStep.DONE -> TurnFinish(failed = false)
        TurnStep.FAILED -> TurnFinish(failed = true)
        // Arrêté : l'utilisateur l'a fait, il le sait.
        TurnStep.STOPPED -> null
        TurnStep.STARTING, TurnStep.THINKING, TurnStep.TOOL, TurnStep.WRITING -> null
    }

    /**
     * Le tour que la barre d'état suit pendant son travail : le plus récent en cours.
     *
     * `null` quand le choix ne demande pas de suivi en direct (« à la fin seulement »), quand
     * l'app le montre déjà, ou quand plus rien ne tourne. Un seul tour à la fois : la barre d'état
     * n'a qu'une place pour ce genre de suivi, et deux bandeaux se lisent comme du bruit.
     */
    fun tracked(
        sessions: Collection<TurnSession>,
        tracking: WorkTracking,
        appInForeground: Boolean,
        visibleConversationId: String?,
    ): TurnSession? {
        if (!tracking.live) return null
        return sessions
            .filter { !it.isFinished }
            .filter { tracks(tracking, appInForeground, visibleConversationId, it.conversationId) }
            .maxByOrNull { it.startedAt }
    }

    /**
     * Le suivi doit-il égrener (un battement par seconde) ?
     *
     * Oui dès qu'un tour **tourne**, même s'il n'est pas celui qu'on montre : la conversation
     * ouverte change (retour, autre onglet, écran éteint), et un tour qui finit doit s'annoncer
     * dans la seconde. Se réveiller seulement sur un changement du magasin de tours fait dormir
     * pendant tout le tour — et le suivi le manque entièrement.
     */
    fun keepsTicking(sessions: Collection<TurnSession>, tracking: WorkTracking): Boolean =
        tracking.notifies && sessions.any { !it.isFinished }

    /** L'issue d'un tour fini : succès, ou échec. */
    data class TurnFinish(val failed: Boolean)
}
