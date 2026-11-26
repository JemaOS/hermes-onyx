package com.onyx.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Scheduling of the reply watcher: a single periodic job, or none.
 *
 * Android is willing to wake the app every fifteen minutes **at the earliest** (that is the smallest
 * period WorkManager accepts for a periodic job), and only when the
 * phone has network: the job therefore never runs for nothing, and never offline.
 *
 * **Idempotent in both directions.** Scheduling while the job already exists does not reset it
 * ([ExistingPeriodicWorkPolicy.KEEP]): calling [apply] on every app start therefore does not
 * shift the next deadline, nor restart the counter. Cancelling an absent job does nothing.
 * That is what allows wiring the scheduling both to the start
 * and to the setting's switch, without wondering who has already done what.
 */
object ReplyWatchScheduler {

    /** The job's unique name: a single watcher per phone, whatever the caller. */
    const val UNIQUE_WORK = "onyx.reply.watch"

    /** The tag, to find (or cancel) the job from WorkManager. */
    const val TAG = "onyx.reply.watch"

    /** The requested period: the shortest WorkManager accepts for a periodic job. */
    const val PERIOD_MINUTES = 15L

    /** Schedules the watcher if it is not already scheduled. */
    fun schedule(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request(),
            )
        }
    }

    /** Removes the watcher: no more reading of the log in the background. */
    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK) }
    }

    /** The setting is authoritative: on → scheduled, off → cancelled. */
    fun apply(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    /** The request itself: fifteen minutes, network present. */
    fun request(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<ReplyWatcher>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(TAG)
            .build()
}
