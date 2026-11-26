package com.onyx.app.ui.chat

/**
 * **La liste posée pour voir le menu « / » sur un émulateur** (`onyx.slashDemo`).
 *
 * Sans passerelle, `complete.slash` ne rend rien : le menu du fil n'existe alors ni à l'écran ni
 * pour une mesure. Cette liste reprend pourtant la **forme réelle** des deux familles d'entrées, et
 * c'est ce qui la rend utile :
 *
 *  - les commandes de l'app (`/find`, `/settings`, `/voice`) : nom court, détail court ;
 *  - les compétences du serveur (`/claude-code`, `/computer-use`) : nom long, et un détail qui
 *    **déborde** — c'est exactement lui qui faisait grandir certaines rangées et pas d'autres, et
 *    qui a imposé la règle « le détail tient sur une ligne ».
 *
 * Debug seulement : rien ici n'est lu sans le drapeau, et rien ne remplace le vrai menu.
 */
object SlashStandIn {

    val items: List<SlashItem> = listOf(
        SlashItem("/find", "/find", "Search this conversation"),
        SlashItem("/settings", "/settings", "Settings"),
        SlashItem("/voice", "/voice", "Voice mode"),
        SlashItem("/help", "/help", "Commands"),
        SlashItem(
            "/claude-code",
            "/claude-code",
            "Delegate coding to Claude Code CLI (features, refactors, pull requests).",
        ),
        SlashItem(
            "/codex",
            "/codex",
            "Delegate coding to OpenAI Codex CLI (features, bug fixes, reviews).",
        ),
        SlashItem(
            "/computer-use",
            "/computer-use",
            "Drive the desktop background-first; escalate on signal.",
        ),
        SlashItem("/new", "/new", "Start a new session (fresh session ID + history)."),
        SlashItem("/model", "/model", "Pick the model for this conversation."),
        SlashItem("/subagents", "/subagents", "See the tasks this conversation has launched."),
    )
}