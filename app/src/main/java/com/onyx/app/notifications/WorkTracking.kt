package com.onyx.app.notifications

/**
 * How much a bot's work is tracked in the status bar.
 *
 * This is the "Suivi du travail" ("Work tracking") setting in Settings → Notifications, in three choices:
 *
 *  - [NEVER]: nothing, ever;
 *  - [ON_FINISH] (the default): **one** finished notification, with the excerpt of the answer. During
 *    the turn, a discreet notification holds the process — without it Android puts the app to sleep, the
 *    channel dies, and the turn is lost at the moment the person was expecting it;
 *  - [EVERY_STEP]: the progress notification stays in the status bar during the whole
 *    work — current step, elapsed time, Stop button, and chip on Android 16 — then the
 *    finished notification.
 *
 * The label is the English key of the dictionary: it is translated by `OnyxLocalization`, as
 * everywhere else in the app.
 */
enum class WorkTracking(val id: String, val label: String) {
    NEVER("never", "Never"),
    ON_FINISH("finish", "When the turn ends"),
    EVERY_STEP("step", "At every step");

    /** True as soon as this choice asks for at least one notification. */
    val notifies: Boolean get() = this != NEVER

    /** True when the work is tracked **during** the turn, and not only at its end. */
    val live: Boolean get() = this == EVERY_STEP

    companion object {
        /**
         * The default: the end of the work, without holding the status bar during the turn — it is the
         * choice to offer so as not to turn every turn into a permanent banner.
         */
        val DEFAULT: WorkTracking = ON_FINISH

        fun fromId(id: String?): WorkTracking = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** The choices, in the order the screen presents them. */
        val choices: List<WorkTracking> = listOf(NEVER, ON_FINISH, EVERY_STEP)
    }
}
