package com.onyx.app.notifications

import android.content.Context
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.ui.chat.TurnSession
import com.onyx.app.ui.chat.TurnStep
import com.onyx.app.ui.chat.TurnStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What there is to do with a turn, decided **without Android** — hence verifiable.
 *
 * The sorting is explicit because it carries the two promises of tracking: a single
 * progress notification (the status bar has only one place), and **a single** finished
 * notification per turn, whatever the frequency at which the decision is run again.
 */
sealed interface TurnNotice {
    /** The status bar tracks this turn: step, duration, progress, Stop button. */
    data class Keep(
        val conversationId: String,
        val title: String,
        val text: String,
        val percent: Int?,
    ) : TurnNotice

    /** The turn is finished: a word on the result, and the excerpt of the answer. */
    data class Finish(
        val conversationId: String,
        val title: String,
        val reply: String,
        val failed: Boolean,
        val sound: Boolean,
    ) : TurnNotice

    /** The turn is stopped or forgotten: its notification clears. */
    data class Drop(val conversationId: String) : TurnNotice

    /** Nothing left to track: the progress notification disappears. */
    data object Idle : TurnNotice
}

/**
 * The state of tracking: what has already been announced, and what remains to be said.
 *
 * Separated from the Android calls for two reasons, both verifiable: the decision can be tested on
 * the JVM, and "a single finished notification" is proven by running the decision several times —
 * which is exactly what was producing three notifications when the one-second loop republished
 * on each pass.
 */
class TurnNoticePlanner(
    private val tracking: () -> WorkTracking,
    private val sound: () -> Boolean,
    private val titleOf: (String) -> String = { NotificationCopy.ONYX },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** The turns whose finish has already been said — or deliberately left unsaid. */
    private val announcedFinish = mutableSetOf<String>()

    /** The current choice: it is what decides whether the work is tracked during the turn. */
    fun trackingNow(): WorkTracking = tracking()

    /** What there is to do for these sessions. */
    fun plan(
        sessions: Map<String, TurnSession>,
        appInForeground: Boolean,
        visibleConversationId: String?,
    ): List<TurnNotice> {
        val choice = tracking()
        val notices = mutableListOf<TurnNotice>()

        // The service holds the process, and it is **tied to the turn**: it starts when the turn
        // begins — the app is visible, the only moment when Android allows launching a
        // foreground service — and it lives until the end of the turn. The policy decides *what
        // is shown*, not *whether the work survives*.
        val held = WorkTrackingPolicy.holding(sessions.values, choice)
        notices += if (held == null) {
            TurnNotice.Idle
        } else {
            TurnNotice.Keep(
                conversationId = held.conversationId,
                title = titleOf(held.conversationId),
                text = progressText(held),
                // Visible tracking shows the progress; simply holding the process does
                // without it — a word, not a counting banner.
                percent = if (choice.live) percentOf(held) else null,
            )
        }

        sessions.values.forEach { session ->
            if (session.assistantId in announcedFinish) return@forEach
            val finish = WorkTrackingPolicy.finishOf(session.live.value)
            if (finish == null) {
                // Still running, or stopped: nothing to say. A stopped turn sees its notification
                // clear — the user knows they have just stopped it.
                if (session.live.value.phase.step == TurnStep.STOPPED) {
                    notices += TurnNotice.Drop(session.conversationId)
                }
                return@forEach
            }
            announcedFinish += session.assistantId
            // The user had the conversation before their eyes: they have already read the answer.
            if (!WorkTrackingPolicy.tracks(choice, appInForeground, visibleConversationId, session.conversationId)) {
                return@forEach
            }
            notices += TurnNotice.Finish(
                conversationId = session.conversationId,
                title = titleOf(session.conversationId),
                reply = NotificationCopy.excerpt(session.message.text),
                failed = finish.failed,
                sound = sound(),
            )
        }
        return notices
    }

    /** The progress phrase: the tool that is running, the reading, and the elapsed duration. */
    fun progressText(session: TurnSession): String = NotificationCopy.turnPhase(
        prefillPercent = percentOf(session),
        toolName = session.live.value.message.tools.lastOrNull { it.isRunning }?.name,
        elapsedSeconds = elapsedOf(session),
    )

    /** The context prefill, when the turn has one — that is the known progress. */
    fun percentOf(session: TurnSession): Int? =
        session.live.value.phase.prefill?.let { (it.fraction * 100).toInt() }

    /** Seconds elapsed since the message was sent. */
    fun elapsedOf(session: TurnSession): Long =
        ((clock() - session.startedAt) / 1_000L).coerceAtLeast(0)
}

/**
 * The tracking of a turn in the status bar, held by the **process**, not by the screen.
 *
 * The process store ([TurnStore]) already carries what a turn becomes — the known step, the
 * duration, the answer being written — even when no screen is watching it. Tracking therefore
 * plugs in **there**, and not on a screen state: leaving the conversation, changing route or
 * destroying the activity does not interrupt it.
 *
 * The app only says what the user has **before their eyes** ([watching]): on the conversation
 * concerned, in the foreground, it notifies nothing.
 */
class TurnWorkNotifier(
    private val context: Context,
    private val turns: TurnStore,
    settings: SettingsRepository,
    titleOf: (String) -> String = { NotificationCopy.ONYX },
    clock: () -> Long = System::currentTimeMillis,
) {

    private val planner = TurnNoticePlanner(
        tracking = { settings.workTracking.value },
        sound = { settings.notificationSound.value },
        titleOf = titleOf,
        clock = clock,
    )

    /** Is the app in the foreground? */
    @Volatile
    var appInForeground: Boolean = true

    /** The conversation open on screen, when there is one. */
    @Volatile
    var visibleConversationId: String? = null

    /** The conversation the service holds on screen, so as not to start it twice. */
    private var servedConversationId: String? = null
    private var lastPublishedKeep: TurnNotice.Keep? = null

    /** What the screen has before its eyes: tracking does not notify what the user sees. */
    fun watching(conversationId: String?, foreground: Boolean) {
        visibleConversationId = conversationId
        appInForeground = foreground
    }

    /** Plugs tracking into the process scope. To be called once. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (currentCoroutineContext().isActive) {
                val sessions = turns.sessions.value
                try {
                    apply(planner.plan(sessions, appInForeground, visibleConversationId))
                } catch (error: Exception) {
                    // A notification must never be able to kill a turn's tracking.
                    android.util.Log.w("OnyxTurn", "le suivi du travail a échoué sur ce passage", error)
                    runCatching { TurnService.stop(context) }
                }
                // We tick as long as a turn is **running**, even if it is not (yet) the one being
                // shown: the open conversation changes (back, another tab, screen off), and
                // a finished turn must be announced within the second.
                if (WorkTrackingPolicy.keepsTicking(sessions.values, planner.trackingNow())) {
                    delay(TICK_MS)
                } else {
                    // Nothing is running anymore: we wait for a turn to start, without getting stuck.
                    withTimeoutOrNull(IDLE_WAIT_MS) { turns.sessions.first { it != sessions } }
                }
            }
        }
    }

    /** The Android calls of the decision. */
    fun apply(notices: List<TurnNotice>) {
        notices.forEach { notice ->
            when (notice) {
                is TurnNotice.Keep -> {
                    if (notice == lastPublishedKeep && notice.conversationId == servedConversationId) {
                        return@forEach
                    }
                    lastPublishedKeep = notice
                    if (notice.conversationId == servedConversationId) {
                        TurnService.phase(context, notice.text, notice.percent)
                    } else {
                        val held = TurnService.start(
                            context = context,
                            conversationId = notice.conversationId,
                            title = notice.title,
                            phase = notice.text,
                            percent = notice.percent,
                        )
                        servedConversationId = notice.conversationId
                        if (!held) {
                            // Android refused the background service: the same notification is
                            // published directly, tracking holds anyway.
                            TurnNotifier.notifyProgress(
                                context = context,
                                conversationId = notice.conversationId,
                                title = notice.title,
                                text = notice.text,
                                percent = notice.percent,
                            )
                        }
                    }
                }

                is TurnNotice.Finish -> TurnNotifier.notifyFinished(
                    context = context,
                    conversationId = notice.conversationId,
                    title = notice.title,
                    reply = notice.reply,
                    failed = notice.failed,
                    sound = notice.sound,
                )

                is TurnNotice.Drop -> TurnNotifier.clear(context, notice.conversationId)

                TurnNotice.Idle -> {
                    if (servedConversationId != null || lastPublishedKeep != null) {
                        TurnService.stop(context)
                        // The fallback may have published the notification without a service: it is cleared too.
                        TurnNotifier.clearProgress(context)
                        servedConversationId = null
                        lastPublishedKeep = null
                    }
                }
            }
        }
    }

    private companion object {
        /** The beat of the displayed timer. */
        const val TICK_MS = 1_000L

        /** With no turn in progress, we wait for a change in the store — with a safety net. */
        const val IDLE_WAIT_MS = 10_000L
    }
}
