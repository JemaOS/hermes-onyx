package com.onyx.app.notifications

/**
 * The notification channels Android keeps for Onyx, and the state the system reports for them.
 *
 * This is the Android counterpart of the pairing list in `PushSettingsView`: where the reference
 * enumerates paired Hermes and lets you remove one, Android enumerates its channels and lets
 * Android decide on each of them (sound, importance) in its own settings. Each channel therefore
 * plays the role a pairing plays there: a destination with tracked state, that you come to adjust.
 *
 * The identifiers come from the two objects that actually publish the alerts
 * ([ApprovalNotifier] and `TurnService`): the list therefore cannot drift from them.
 */
object NotificationChannels {

    /** An Onyx channel: its Android identifier, its localized label and its description. */
    data class Channel(val id: String, val label: String, val description: String)

    /** The turns channel: it carries the progress **and** the end of a bot's work. */
    const val ANSWERS_LABEL = "Answers"
    const val ANSWERS_DESCRIPTION = "Shows while a bot is working, and when its answer is ready."

    /** The channels Onyx registers, in the order the screen presents them. */
    val all: List<Channel> = listOf(
        Channel(
            id = ApprovalNotifier.CHANNEL_ID,
            label = "Approvals",
            description = "Asks when Onyx wants to run something on your machine.",
        ),
        Channel(
            id = ApprovalNotifier.SILENT_CHANNEL_ID,
            label = "Silent approvals",
            description = "Asks when Onyx wants to run something on your machine.",
        ),
        Channel(
            id = TurnService.CHANNEL_ID,
            label = ANSWERS_LABEL,
            description = ANSWERS_DESCRIPTION,
        ),
        Channel(
            id = CallRinger.CHANNEL_ID,
            label = CallRinger.CALLS_LABEL,
            description = CallRinger.CALLS_DESCRIPTION,
        ),
        Channel(
            id = AgentCallService.CHANNEL_ID,
            label = AgentCallService.CHANNEL_LABEL,
            description = AgentCallService.CHANNEL_DESCRIPTION,
        ),
    )

    /**
     * How Android treats a channel, according to its importance.
     *
     * `ABSENT` is the Android counterpart of the reference's "Waiting for its first notification": Android
     * only creates a channel on first publication — a channel never created has no state yet.
     */
    enum class State { ABSENT, OFF, ON }

    /** `NotificationManager.IMPORTANCE_NONE`: the channel is set to "none" ("aucun"). */
    const val IMPORTANCE_NONE = 0

    /** `importance` comes from `NotificationChannel.getImportance()`; `null`: the channel does not exist. */
    fun state(importance: Int?): State = when {
        importance == null -> State.ABSENT
        importance == IMPORTANCE_NONE -> State.OFF
        else -> State.ON
    }
}
