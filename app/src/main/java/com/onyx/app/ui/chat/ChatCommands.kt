package com.onyx.app.ui.chat

import com.onyx.app.localization.OnyxLocalization

/**
 * The `/` commands that Onyx handles itself, without sending them to the server.
 *
 * Mirror of the special case in `ComposerView.send` (Echo/Views/ComposerView:569):
 * a bare `/model` opens the model picker; with an argument, the line remains an ordinary
 * `slash` command and goes to the gateway.
 */
internal const val MODEL_COMMAND = "/model"

/**
 * True if the draft is exactly `/model` (spaces and case ignored).
 *
 * A `/model gpt-5` is **not** this command: it is a line for the server.
 */
internal fun isModelCommand(draft: String): Boolean = draft.trim().lowercase() == MODEL_COMMAND

/**
 * Guarantees `/model` at the top of the `/` menu when it matches what is being typed.
 *
 * The menu comes from the gateway's `complete.slash`; `/model` is a local command that Onyx
 * intercepts on send, so we add it to the menu so that it is visible even if the server does not
 * offer it. Nothing is duplicated if the gateway already returns it.
 */
internal fun withModelCommand(items: List<SlashItem>, draft: String): List<SlashItem> {
    if (items.any { it.text.equals(MODEL_COMMAND, ignoreCase = true) }) return items
    if (!MODEL_COMMAND.startsWith(draft.trim().lowercase())) return items
    return listOf(
        SlashItem(
            text = MODEL_COMMAND,
            display = MODEL_COMMAND,
            detail = OnyxLocalization["Change the model for this conversation"],
        ),
    ) + items
}

/** The size of the menu: the reference shows eight. */
internal const val SLASH_MENU_LINES = 8

/**
 * Adds to the menu the commands the app knows — the six fixed ones and the **profile skills** —
 * and makes them **come before** what the gateway offers.
 *
 * Defect found on the device: "the skills are unusable". `complete.slash` from a server that
 * fills the eight lines of the menu (its own commands) left no room for the skills, added
 * *afterwards* then cut off by `take(8)`: they existed without ever showing. Nothing was broken
 * in the provided list — it was room that was missing.
 *
 * The order is therefore: the app's commands that respond to the prefix (the six fixed ones, then
 * the skills), then what the server offers, deduplicated, all cut at [SLASH_MENU_LINES].
 * A more precise prefix (`/you`, `/re`) hands the floor to the server as soon as the app's
 * commands no longer respond.
 */
internal fun withLocalCommands(
    items: List<SlashItem>,
    draft: String,
    commands: List<SlashCommand>,
): List<SlashItem> {
    val typed = draft.trim().lowercase().substringBefore(' ')
    if (!typed.startsWith("/")) return items
    val present = items.map { it.text.lowercase() }.toSet()
    val local = commands
        .filter { it.name.startsWith(typed) && it.name.lowercase() !in present }
        .map { command ->
            SlashItem(text = command.name, display = command.display, detail = command.shownDetail)
        }
    if (local.isEmpty()) return items
    return (local + items).take(SLASH_MENU_LINES)
}
