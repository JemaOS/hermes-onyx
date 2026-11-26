package com.onyx.app.ui.chat

import com.onyx.app.data.net.DashboardSkill
import com.onyx.app.localization.OnyxLocalization

/**
 * Les commandes « / » que l'app exécute elle-même.
 *
 * référence ne garde qu'une seule commande locale — `/model` nu ouvre le sélecteur de modèle
 * (`ComposerView:569`) — et confie toutes les autres au gateway (`slash.exec`, puis
 * `command.dispatch` sur l'erreur 4018). Android n'a pas la ligne de statut de la référence pour rendre
 * la sortie d'une commande, donc les commandes qui ne parlent qu'à l'écran sont exécutées ici,
 * et tout le reste part au gateway comme sur la référence.
 *
 * Les **compétences du profil** sont des commandes comme les autres : Hermes expose chaque
 * compétence installée en `/<nom>` (docs « Skills System », section *slash commands*), et c'est
 * cette même ligne que le gateway sait charger. La liste que le menu et l'exécution lisent vient
 * donc d'un seul fournisseur — [SlashCommands.commands] — et non plus d'une liste codée en dur.
 */
enum class SlashAction {
    /** `/model` nu : le sélecteur de modèle de l'app. */
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

    /**
     * Une compétence du profil : son message de chargement part comme un tour
     * (`command.dispatch` → `type: skill`).
     */
    INVOKE_SKILL,

    /** Rien à traiter ici : la commande part au gateway. */
    GATEWAY,
}

/** Une commande connue — ce qu'elle s'écrit, ce qu'elle affiche, et ce qu'elle fait. */
data class SlashCommand(
    val name: String,
    val display: String,
    val detail: String,
    val action: SlashAction,
    /**
     * Vrai quand la commande vient d'une compétence du profil plutôt que du code.
     *
     * [detail] est alors la **description écrite par l'auteur de la compétence** (une donnée du
     * serveur) : elle s'affiche telle quelle, sans passer par le catalogue de localisation.
     */
    val skill: Boolean = false,
) {
    /**
     * Le détail tel qu'il s'affiche.
     *
     * Les libellés de l'app sont des **clés** du catalogue de localisation ; la description d'une
     * compétence est une donnée du serveur, rendue telle quelle. Une compétence sans description
     * reçoit le mot du catalogue (« Skill ») plutôt qu'une ligne vide.
     */
    val shownDetail: String
        get() = when {
            detail.isBlank() -> OnyxLocalization["Skill"]
            skill -> detail
            else -> OnyxLocalization[detail]
        }
}

object SlashCommands {
    /**
     * Les commandes que l'app exécute. L'ordre est celui du menu local.
     */
    val defaultCommands: List<SlashCommand> = listOf(
        SlashCommand("/model", "/model", "Choose the model", SlashAction.OPEN_MODEL_PICKER),
        SlashCommand("/new", "/new", "New conversation", SlashAction.NEW_CONVERSATION),
        SlashCommand("/find", "/find", "Find in conversation", SlashAction.FIND),
        SlashCommand("/settings", "/settings", "Settings", SlashAction.SETTINGS),
        SlashCommand("/voice", "/voice", "Voice mode", SlashAction.VOICE),
        SlashCommand("/help", "/help", "Commands", SlashAction.HELP),
    )

    /** Le nom historique de [defaultCommands] : les six commandes fixes, avant les compétences. */
    val builtIn: List<SlashCommand> get() = defaultCommands

    /**
     * Les compétences du profil, une commande par compétence.
     *
     * Le serveur nomme une compétence par son titre ; la commande porte le **slug** — la même
     * forme que `agent.skill_commands.slugify_skill_name` côté Hermes, et la clé que
     * `command.dispatch` attend. Une compétence éteinte n'est pas invocable : elle n'apparaît pas.
     *
     * Une compétence dont le nom prendrait une commande fixe (`/model`, `/help`…) n'a **pas** de
     * commande à elle : Hermes la garde derrière `/skill <nom>` et réserve le nom au built-in.
     * On ne la propose donc pas — la proposer enverrait le gateway sur la mauvaise commande.
     */
    fun skillCommands(skills: List<DashboardSkill>): List<SlashCommand> {
        val taken = defaultCommands.map { it.name.lowercase() }.toSet()
        return skills.asSequence()
            .filter { it.enabled }
            .mapNotNull { skill ->
                val name = "/" + slugOf(skill.name)
                if (name == "/" || name.lowercase() in taken) return@mapNotNull null
                SlashCommand(
                    name = name,
                    display = name,
                    // La description du serveur, telle quelle ; vide, l'affichage met « Skill ».
                    detail = skill.description,
                    action = SlashAction.INVOKE_SKILL,
                    skill = true,
                )
            }
            .distinctBy { it.name.lowercase() }
            .toList()
    }

    /**
     * **Le fournisseur** : les six commandes fixes, puis les compétences du profil.
     *
     * C'est la seule liste que le menu et l'exécution lisent. Sans compétence — un profil qui n'en
     * a aucune, ou une réponse illisible — elle vaut exactement [defaultCommands].
     */
    fun commands(skills: List<DashboardSkill> = emptyList()): List<SlashCommand> =
        defaultCommands + skillCommands(skills)

    /**
     * Le slug d'une compétence, tel que le serveur le calcule : minuscules, espaces et `_` en `-`,
     * tout ce qui n'est ni lettre, ni chiffre, ni `_`, ni `-` retiré, les tirets doublés réduits.
     */
    fun slugOf(name: String): String =
        name.lowercase()
            .replace(' ', '-')
            .replace('_', '-')
            .replace(SLUG_INVALID, "")
            .replace(SLUG_HYPHENS, "-")
            .trim('-')

    /**
     * Ce que cette ligne de brouillon déclenche.
     *
     * Un `/model` nu ouvre le sélecteur ; `/model <quelque chose>` reste une commande du
     * gateway, exactement comme `ComposerView:569` (« Bare /model opens the picker; with
     * an argument it stays a serve slash command »).
     *
     * Une compétence, elle, prend son instruction **en argument** : `/resume ce fil` charge la
     * compétence et lui donne « ce fil ». Son argument ne l'envoie donc pas au gateway.
     */
    fun action(text: String, commands: List<SlashCommand> = defaultCommands): SlashAction {
        val clean = text.trim().lowercase()
        if (!clean.startsWith("/")) return SlashAction.GATEWAY
        // Commande nue : premier mot seul, sans argument après.
        val bare = clean.substringBefore(' ')
        val command = commands.firstOrNull { it.name.equals(bare, ignoreCase = true) }
        val hasArgument = clean.length > bare.length
        return when {
            command == null -> SlashAction.GATEWAY
            command.action == SlashAction.INVOKE_SKILL -> SlashAction.INVOKE_SKILL
            hasArgument && command.action != SlashAction.HELP -> SlashAction.GATEWAY
            else -> command.action
        }
    }

    /** La commande portée par cette ligne, telle quelle, ou `null` si elle vient du gateway. */
    fun commandFor(text: String, commands: List<SlashCommand> = defaultCommands): SlashCommand? {
        val clean = text.trim().lowercase()
        if (!clean.startsWith("/")) return null
        val bare = clean.substringBefore(' ')
        if (clean.length > bare.length) return null
        return commands.firstOrNull { it.name.equals(bare, ignoreCase = true) }
    }

    /** Ce que le menu local propose pour un brouillon qui commence par « / ». */
    fun matches(prefix: String, commands: List<SlashCommand> = defaultCommands): List<SlashCommand> {
        val clean = prefix.trim().lowercase()
        if (!clean.startsWith("/")) return emptyList()
        val typed = clean.substringBefore(' ')
        val matches = commands.filter { it.name.startsWith(typed) }
        return (if (matches.isEmpty()) commands else matches).take(8)
    }

    /** Le texte que `/help` écrit dans le fil. */
    fun helpText(commands: List<SlashCommand> = defaultCommands): String = buildString {
        appendLine(OnyxLocalization["Slash commands"])
        appendLine()
        commands.forEach { command ->
            appendLine("- `${command.name}` — ${command.shownDetail}")
        }
        appendLine()
        append(OnyxLocalization["Anything else that starts with `/` goes to the gateway."])
    }

    /** Les caractères qu'un slug ne garde pas — `\w` du serveur, plus le tiret. */
    private val SLUG_INVALID = Regex("[^\\p{L}\\p{N}_-]")

    /** Deux tirets ou plus n'en font qu'un. */
    private val SLUG_HYPHENS = Regex("-{2,}")
}
