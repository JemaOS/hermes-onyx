package com.onyx.app.ui.chat

/**
 * Les commandes « / » que l'app exécute elle-même.
 *
 * ne garde qu'une seule commande locale — `/model` nu ouvre le sélecteur de modèle
 * — et confie toutes les autres au gateway (`slash.exec`, puis
 * `command.dispatch` sur l'erreur 4018). Android n'a pas la ligne de statut pour rendre
 * la sortie d'une commande, donc les commandes qui ne parlent qu'à l'écran sont exécutées ici,
 * et tout le reste part au gateway comme.
 */
enum class SlashAction {
    /** `/model` nu: le sélecteur de modèle de l'app. */
    OPEN_MODEL_PICKER,

    /** Une nouvelle conversation avec l'agent, comme ⌘N. */
    NEW_CONVERSATION,

    /** « Find in conversation », comme ⌘F. */
    FIND,

    /** L'écran des réglages. */
    SETTINGS,

    /** Le mode vocal. */
    VOICE,

    /** La liste des commandes connues. */
    HELP,

    /** Rien à traiter ici: la commande part au gateway. */
    GATEWAY,
}

/** Une commande connue — ce qu'elle s'écrit, ce qu'elle affiche, et ce qu'elle fait. */
data class SlashCommand(
    val name: String,
    val display: String,
    val detail: String,
    val action: SlashAction,
)

object SlashCommands {
    /**
     * Les commandes que l'app exécute. L'ordre est celui du menu local.
     */
    val builtIn: List<SlashCommand> = listOf(
        SlashCommand("/model", "/model", "Choose the model", SlashAction.OPEN_MODEL_PICKER),
        SlashCommand("/new", "/new", "New conversation", SlashAction.NEW_CONVERSATION),
        SlashCommand("/find", "/find", "Find in conversation", SlashAction.FIND),
        SlashCommand("/settings", "/settings", "Settings", SlashAction.SETTINGS),
        SlashCommand("/voice", "/voice", "Voice mode", SlashAction.VOICE),
        SlashCommand("/help", "/help", "Commands", SlashAction.HELP),
    )

    /**
     * Ce que cette ligne de brouillon déclenche.
     *
     * Un `/model` nu ouvre le sélecteur; `/model <quelque chose>` reste une commande du
     * gateway, exactement comme (« Bare /model opens the picker; with
     * an argument it stays a serve slash command »).
     */
    fun action(text: String): SlashAction {
        val clean = text.trim().lowercase()
        if (!clean.startsWith("/")) return SlashAction.GATEWAY
        // Commande nue: premier mot seul, sans argument après.
        val bare = clean.substringBefore(' ')
        val command = builtIn.firstOrNull { it.name.equals(bare, ignoreCase = true) }
        val hasArgument = clean.length > bare.length
        return when {
            command == null -> SlashAction.GATEWAY
            hasArgument && command.action != SlashAction.HELP -> SlashAction.GATEWAY
            else -> command.action
        }
    }

    /** La commande portée par cette ligne, telle quelle, ou `null` si elle vient du gateway. */
    fun commandFor(text: String): SlashCommand? {
        val clean = text.trim().lowercase()
        if (!clean.startsWith("/")) return null
        val bare = clean.substringBefore(' ')
        if (clean.length > bare.length) return null
        return builtIn.firstOrNull { it.name.equals(bare, ignoreCase = true) }
    }

    /** Ce que le menu local propose pour un brouillon qui commence par « / ». */
    fun matches(prefix: String): List<SlashCommand> {
        val clean = prefix.trim().lowercase()
        if (!clean.startsWith("/")) return emptyList()
        val typed = clean.substringBefore(' ')
        val matches = builtIn.filter { it.name.startsWith(typed) }
        return (if (matches.isEmpty()) builtIn else matches).take(8)
    }

    /** Le texte que `/help` écrit dans le fil. */
    fun helpText(): String = buildString {
        appendLine("Slash commands")
        appendLine()
        builtIn.forEach { command ->
            appendLine("- `${command.name}` — ${command.detail}")
        }
        appendLine()
        append("Anything else that starts with `/` goes to the gateway.")
    }
}
