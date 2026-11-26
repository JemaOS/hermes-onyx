package com.onyx.app.ui

import android.content.Context

/**
 * What startup must do, decided before restoring anything.
 *
 * [interrupted] is true when the previous startup never reached its first frame:
 * the app died while it was restoring its state. In that case we **fall back to Chat**, we
 * **drop the draft's attachment** (the feed, itself, keeps its own: nothing is deleted) and
 * we forget the remembered tab.
 */
data class StartPlan(
    val interrupted: Boolean,
    val chatTab: Boolean,
    val dropDraftAttachments: Boolean,
)

/** The small store of the flag and the tab, so that the decision can be tested without a device. */
interface StartupStore {
    fun interrupted(): Boolean
    fun markRestoring()
    fun markSettled()
    fun lastTab(): Int
    fun rememberTab(index: Int)
}

/**
 * The "close / reopen" loop breaker.
 *
 * The app restores its route, its tab and its draft on every startup. If the restored state
 * contains something that kills the process (an attachment the rendering refuses, a reader that
 * throws), the restoration itself crashes: the system relaunches the app, it restores the same
 * state, it dies again. It is a loop, not an isolated crash.
 *
 * The remedy has three steps, in this order:
 * 1. [begin] **writes the flag before** anything is restored (blocking
 *    write: the flag must be on disk even if the process dies on the next
 *    frame);
 * 2. once the first frame has passed, [settled] **clears** the flag;
 * 3. if a startup finds the flag, it starts **in fallback mode**: Chat, draft without its
 *    attachment, tab reset to zero — and it clears the flag, so the next startup is
 *    normal. A faulty state therefore cannot condemn the app more than once.
 */
class StartupGuard(private val store: StartupStore) {

    /** To be called **at the very beginning** of composition, before any restoration. */
    fun begin(): StartPlan {
        val interrupted = store.interrupted()
        store.markRestoring()
        if (interrupted) {
            // The remembered tab is the one where the last startup died: we forget it.
            store.rememberTab(CHAT_TAB)
        }
        return StartPlan(
            interrupted = interrupted,
            chatTab = interrupted,
            dropDraftAttachments = interrupted,
        )
    }

    /** The first frame has happened: this startup is no longer "restoring". */
    fun settled() = store.markSettled()

    /** The current tab, to find it again on the next launch. */
    fun noteTab(index: Int) {
        if (index >= 0) store.rememberTab(index)
    }

    /** The tab of the last launch — Chat when none was noted. */
    fun lastTab(): Int = store.lastTab().coerceAtLeast(CHAT_TAB)

    companion object {
        /** The Chat tab: the fallback of every fallback-mode startup. */
        const val CHAT_TAB = 0

        fun with(context: Context): StartupGuard = StartupGuard(PrefsStartupStore(context))
    }
}

/** The real store: two keys in the app's preferences. */
private class PrefsStartupStore(context: Context) : StartupStore {

    private val prefs = context.getSharedPreferences("onyx-startup", Context.MODE_PRIVATE)

    override fun interrupted(): Boolean = prefs.getBoolean(KEY_RESTORING, false)

    override fun markRestoring() {
        // `commit`, not `apply`: the flag must be written **before** the first frame, and
        // `apply` would leave it in memory for the duration of a crash.
        prefs.edit().putBoolean(KEY_RESTORING, true).commit()
    }

    override fun markSettled() {
        prefs.edit().putBoolean(KEY_RESTORING, false).apply()
    }

    override fun lastTab(): Int = prefs.getInt(KEY_LAST_TAB, StartupGuard.CHAT_TAB)

    override fun rememberTab(index: Int) {
        prefs.edit().putInt(KEY_LAST_TAB, index).apply()
    }

    private companion object {
        const val KEY_RESTORING = "restoring"
        const val KEY_LAST_TAB = "last_tab"
    }
}
