package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile

/**
 * The roster's QA stand-in: the sample bots, for a device that has no Dashboard where
 * to read them.
 *
 * The reference has exactly the same thing — `ConversationsView.demoBots`
 * (Echo/Views/ConversationsView:2268), placed in the `BotDirectory` when
 * `-echo.demoBots` is passed at launch. Here it is the extra `onyx.demoBots` (`am start -e
 * onyx.demoBots true`) that places it. Nothing reads it otherwise: without the extra, the roster is
 * the server's, or empty.
 *
 * The four bots are those of the reference stand-in, with their same names and labels:
 * the main profile (`@hermes`, without a title), `scout` ("Scout"), `writer` ("Quill") and
 * `fixit` ("Fix-it") — enough to see the list of `@` mentions with several bots, the roster's
 * order (main, then the most recently spoken to) and the filtering by what follows the "@".
 */
object BotRosterStandIn {

    /** The stand-in profiles, with their activity dates relative to [now]. */
    fun bots(now: Long = System.currentTimeMillis()): List<BotProfile> = listOf(
        BotProfile(
            name = "default",
            isDefault = true,
            lastActive = now - 3_600_000,
            preview = "Done: the reminder is set for 4 pm.",
        ),
        BotProfile(
            name = "scout",
            title = "Scout",
            description = "Finds things out",
            lastActive = now - 600_000,
            preview = "Three trails under two hours from you, all open.",
        ),
        BotProfile(
            name = "writer",
            displayName = "Quill",
            description = "Edits and drafts",
            lastActive = now - 120_000,
            preview = "Want me to carry that through the next paragraph?",
        ),
        BotProfile(
            name = "fixit",
            title = "Fix-it",
            description = "Looks after the home server",
        ),
    )
}
