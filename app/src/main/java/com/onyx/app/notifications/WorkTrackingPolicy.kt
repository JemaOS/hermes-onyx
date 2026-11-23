package com.onyx.app.notifications

import com.onyx.app.ui.chat.TurnLive
import com.onyx.app.ui.chat.TurnSession
import com.onyx.app.ui.chat.TurnStep

/**
 * Who gets notified, and when — decided **without Android**, hence verifiable.
 *
 * Three rules, and nothing else:
 *
 *  1. the "Jamais" ("Never") choice notifies nothing, ever;
 *  2. the app open **on the conversation concerned** does not notify: the user has the work
 *     before their eyes (same rule as for an approval or a question);
 *  3. a **stopped** turn is not recounted: its notification clears, it does not get a
 *     finished notification — the user knows they have just stopped it.
 *
 * What remains is the outcome of a turn: "Terminé" ("Done") or "Échec" ("Failed"), once only.
 */
object WorkTrackingPolicy {

    /** Should this turn be notified (its finish, and its progress if it is tracked live)? */
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

    /** The outcome of a finished turn, when it deserves a finished notification. */
    fun finishOf(live: TurnLive): TurnFinish? = when (live.phase.step) {
        TurnStep.DONE -> TurnFinish(failed = false)
        TurnStep.FAILED -> TurnFinish(failed = true)
        // Stopped: the user did it, they know it.
        TurnStep.STOPPED -> null
        TurnStep.STARTING, TurnStep.THINKING, TurnStep.TOOL, TurnStep.WRITING -> null
    }

    /**
     * The turn the service must **hold**.
     *
     * Independent of the notification policy: a policy says *what is shown*, not
     * *whether the work survives*. A turn running while the app falls asleep must hold —
     * otherwise Android puts the process to sleep, the channel dies, and the turn is lost at the exact
     * moment the person was expecting it.
     *
     * The service is **tied to the turn**, not to the background: it starts when the turn begins and
     * lives until its end. This is an Android constraint, not a taste: since API 31, a
     * foreground service cannot be launched from the background (only a brief
     * window exists right after the home button — too short for a one-second
     * beat). Starting it at the moment the turn begins is putting it in the foreground
     * while the app is **visible**: that is allowed, and it is the only reliable moment.
     *
     * The "never" choice is respected: it is an explicit request to show nothing, and a
     * foreground service is inevitably visible in the status bar.
     */
    fun holding(
        sessions: Collection<TurnSession>,
        tracking: WorkTracking,
    ): TurnSession? {
        if (tracking == WorkTracking.NEVER) return null
        return sessions.filter { !it.isFinished }.maxByOrNull { it.startedAt }
    }

    /**
     * The turn the status bar tracks during its work: the most recent one in progress.
     *
     * `null` when the choice does not ask for live tracking ("at the end only"), when
     * the app already shows it, or when nothing is running anymore. One turn at a time: the status bar
     * has only one place for this kind of tracking, and two banners read as noise.
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
     * Must tracking tick (one beat per second)?
     *
     * Yes as soon as a turn is **running**, even if it is not the one being shown: the open
     * conversation changes (back, another tab, screen off), and a turn that finishes must be announced
     * within the second. Waking up only on a change of the turn store makes it sleep
     * during the whole turn — and tracking misses it entirely.
     */
    fun keepsTicking(sessions: Collection<TurnSession>, tracking: WorkTracking): Boolean =
        tracking.notifies && sessions.any { !it.isFinished }

    /** The outcome of a finished turn: success, or failure. */
    data class TurnFinish(val failed: Boolean)
}
