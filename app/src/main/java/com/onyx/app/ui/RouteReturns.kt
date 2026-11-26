package com.onyx.app.ui

/**
 * **The table of returns: one row per destination of the application.**
 *
 * It is the "every back must return where I previously was, throughout the whole
 * App" deliverable. The source of truth remains **the navigation graph** (`private enum class Route` and the
 * `when (presentedRoute)` of `OnyxApp.kt`); this table is its **readable translation**, and the
 * [com.onyx.app.ui.TableOfRoutesLockTest] lock holds it against the graph:
 *
 * - an enum value **absent** from the table makes the test fail, **by naming it**;
 * - a row whose [declaration] **can no longer be found** in its branch (or its support) makes
 *   the test fail, **by naming the destination** — that is "a route without a declared return";
 * - a row whose [declaration] is **empty** makes the test fail — same verdict;
 * - a **stale** row (a destination that no longer exists in the enum) makes the test fail;
 * - a [repli] that does not name a destination of the enum makes the test fail.
 *
 * The table does not replace the code: [declaration] is **the exact text** that, in the source, carries
 * the return (the `onBack`, the `onClose`, the `onDone`, the `onCancel`, or the `BackHandler` of the
 * layout for the root). The lock therefore looks for it **where it lives** — in the router branch, in
 * the pane that carries the screen, or in the layout for the feed.
 */
enum class NatureRetour {
    /** The root: back is not consumed, it hands control back to the system (the app closes). */
    RACINE_SYSTEME,

    /** `if (!navBack()) navTo(<fallback>)` — one level at a time, then the semantic fallback. */
    PILE,

    /** A **fixed** fallback: the destination has only one entry, the page left is always the same. */
    FIXE,
}

/** The support where the lock looks for [RetourRoute.declaration]. */
const val SUPPORT_BRANCHE = "branche"

/**
 * A destination and its return.
 *
 * @param route the name of the `Route` enum value (the graph's key).
 * @param ecran the screen the router branch actually displays (in French).
 * @param nature what back does (see [NatureRetour]).
 * @param declaration **the exact text** that carries the return in the source; empty = no return
 *   declared, which the lock refuses.
 * @param repli the destination of the fallback when the stack is empty, or `null` when it is dynamic
 *   (the page left, carried by a state — `contextReturn`).
 * @param support where to look for [declaration]: [SUPPORT_BRANCHE], `panneau:<name>`, or `layout`.
 */
data class RetourRoute(
    val route: String,
    val ecran: String,
    val nature: NatureRetour,
    val declaration: String,
    val repli: String?,
    val support: String = SUPPORT_BRANCHE,
)

/**
 * **The 68 destinations of the app, each with its declared return.**
 *
 * The order is that of the enum so that the table reads like the graph. No row is written
 * "from memory": each [RetourRoute.declaration] is the text taken from `OnyxApp.kt`, and the
 * lock re-checks it on every test run.
 */
object TableDesRetours {

    val toutes: List<RetourRoute> = listOf(
        RetourRoute(
            route = "CHAT",
            ecran = "le fil (conversation)",
            nature = NatureRetour.RACINE_SYSTEME,
            declaration = "BackHandler(enabled = backHandled)",
            repli = "CHAT",
            support = "layout",
        ),
        RetourRoute(
            route = "SETTINGS",
            ecran = "les Réglages",
            nature = NatureRetour.PILE,
            declaration = "onDone = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "APPEARANCE",
            ecran = "l'Apparence",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "MODELS",
            ecran = "les Modèles",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "VOICE",
            ecran = "la voix seule",
            nature = NatureRetour.PILE,
            declaration = "if (!navBack()) navTo(Route.CHAT.name)",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "GATEWAY",
            ecran = "la Configuration",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "LOGS",
            ecran = "les Journaux",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "FILES",
            ecran = "les Fichiers du serveur",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "FILE_EDITOR",
            ecran = "l'éditeur de fichier",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.FILES.name) }",
            repli = "FILES",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "CONTEXT",
            ecran = "les fichiers de contexte",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "MEMORY",
            ecran = "la Mémoire",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "CONTEXT_EDITOR",
            ecran = "l'éditeur de contexte / personnalité",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(contextReturn) }",
            repli = "contextReturn",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "VOICE_SETTINGS",
            ecran = "les réglages de Voix",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "NOTIFICATIONS",
            ecran = "les Notifications",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "KOKORO_VOICES",
            ecran = "les voix Kokoro",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) }",
            repli = "VOICE_SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "CRON",
            ecran = "les Planifiés",
            nature = NatureRetour.PILE,
            declaration = "val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })",
            repli = "CHAT",
            support = "panneau:cronPane",
        ),
        RetourRoute(
            route = "KANBAN",
            ecran = "le tableau Kanban",
            nature = NatureRetour.PILE,
            declaration = "val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })",
            repli = "CHAT",
            support = "panneau:kanbanPane",
        ),
        RetourRoute(
            route = "CODE",
            ecran = "l'écran Code (sessions)",
            nature = NatureRetour.PILE,
            declaration = "val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })",
            repli = "CHAT",
            support = "panneau:codePane",
        ),
        RetourRoute(
            route = "COMPOSER_EDITOR",
            ecran = "l'éditeur du brouillon",
            nature = NatureRetour.PILE,
            declaration = "onDone = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "PHONE_TOOLS",
            ecran = "les outils du téléphone",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "CALLS",
            ecran = "les Appels",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "LOCK",
            ecran = "le Verrouillage",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "CONNECT",
            ecran = "Configurer un autre appareil",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "BOTS",
            ecran = "la grille des bots",
            nature = NatureRetour.PILE,
            declaration = "onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.SETTINGS.name) })",
            repli = "SETTINGS",
            support = "panneau:botsPane",
        ),
        RetourRoute(
            route = "BOT_EDITOR",
            ecran = "l'éditeur d'un bot",
            nature = NatureRetour.PILE,
            declaration = "onCancel = { if (!navBack()) navTo(Route.BOTS.name) }",
            repli = "BOTS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "BOT_FACE",
            ecran = "le choix du visage",
            nature = NatureRetour.PILE,
            declaration = "onCancel = { if (!navBack()) navTo(Route.BOT_EDITOR.name) }",
            repli = "BOT_EDITOR",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "BOT_SCHEDULES",
            ecran = "les horaires d'un bot",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.BOT_EDITOR.name) }",
            repli = "BOT_EDITOR",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "BOT_VOICE",
            ecran = "la voix d'un bot",
            nature = NatureRetour.PILE,
            declaration = "if (!navBack()) navTo(if (bot.isDefault) Route.SETTINGS.name else Route.BOT_EDITOR.name)",
            repli = "BOT_EDITOR",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "GROUP_CHAT",
            ecran = "le fil d'une salle",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.BOTS.name) }",
            repli = "BOTS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "GROUP_VOICE",
            ecran = "la voix d'une salle",
            nature = NatureRetour.PILE,
            declaration = "if (!navBack()) navTo(Route.GROUP_CHAT.name)",
            repli = "GROUP_CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "ACKNOWLEDGEMENTS",
            ecran = "les Remerciements",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SPEECH_LANGUAGE",
            ecran = "la langue d'écoute",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) }",
            repli = "VOICE_SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "REPLY_LANGUAGE",
            ecran = "la langue de réponse",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) }",
            repli = "VOICE_SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "COMPUTER",
            ecran = "l'Ordinateur (pont)",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "STOP_PHRASES",
            ecran = "les phrases d'arrêt",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) }",
            repli = "VOICE_SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SERVERS",
            ecran = "les Serveurs",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SETUP",
            ecran = "la configuration",
            nature = NatureRetour.PILE,
            declaration = "if (!navBack()) navTo(Route.SERVERS.name)",
            repli = "SERVERS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SPOKEN_PREFIXES",
            ecran = "les préfixes parlés",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.VOICE_SETTINGS.name) }",
            repli = "VOICE_SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SPOKEN_PREFIX_EDITOR",
            ecran = "l'éditeur d'un préfixe",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SPOKEN_PREFIXES.name) }",
            repli = "SPOKEN_PREFIXES",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "PROFILES",
            ecran = "le choix du profil",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "WHATS_NEW",
            ecran = "les Nouveautés",
            nature = NatureRetour.PILE,
            declaration = "onContinue = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "PATCH",
            ecran = "le diff (Modifier)",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SELECT_TEXT",
            ecran = "Sélectionner du texte",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "PHOTO",
            ecran = "la visionneuse photo",
            nature = NatureRetour.PILE,
            declaration = "onClose = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "PDF",
            ecran = "le lecteur PDF",
            nature = NatureRetour.PILE,
            declaration = "onClose = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "VIDEO",
            ecran = "le lecteur vidéo",
            nature = NatureRetour.PILE,
            declaration = "onClose = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SKILL_EDITOR",
            ecran = "l'éditeur de compétence",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CAPABILITIES.name) }",
            repli = "CAPABILITIES",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "TOOLS",
            ecran = "les Greffons (outils)",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "API_BOT",
            ecran = "un bot par clé d'API",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.BOTS.name) }",
            repli = "BOTS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "CAMERA",
            ecran = "l'appareil photo",
            nature = NatureRetour.PILE,
            declaration = "onClose = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SUBAGENT",
            ecran = "le fil d'un sous-agent",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SUBAGENTS",
            ecran = "les tâches lancées",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "TERMINAL",
            ecran = "le Terminal",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "TERMINAL_SETTINGS",
            ecran = "les réglages du Terminal",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "AUDIO",
            ecran = "le lecteur de son",
            nature = NatureRetour.PILE,
            declaration = "onClose = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "DOCUMENT",
            ecran = "le lecteur de document",
            nature = NatureRetour.PILE,
            declaration = "onClose = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "ARCHIVE",
            ecran = "le contenu d'une archive",
            nature = NatureRetour.PILE,
            declaration = "onClose = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "QUICK_CAPTURE",
            ecran = "la capture rapide",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "CONNECTION_DETAILS",
            ecran = "les détails de connexion",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "HIDDEN_BOTS",
            ecran = "les bots masqués",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.BOTS.name) }",
            repli = "BOTS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "SESSION_USAGE",
            ecran = "l'usage et le coût d'une session",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "PROJECT_SESSIONS",
            ecran = "les sessions d'un projet",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "CAPABILITIES",
            ecran = "les Capacités",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        // The Vault has **its** row in Settings (the "Secrets" section) and its icon shortcut:
        // it no longer opens from "Capacités" ("Capabilities"), so its fallback is Settings.
        RetourRoute(
            route = "BROWSER_SCREEN",
            ecran = "le navigateur de l'agent",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "VAULT",
            ecran = "le Coffre de secrets",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "INSIGHTS",
            ecran = "les Aperçus",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "ARCHIVED",
            ecran = "les Archives",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),
        RetourRoute(
            route = "LEDGER",
            ecran = "le Relevé de l'agent",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.CHAT.name) }",
            repli = "CHAT",
            support = SUPPORT_BRANCHE,
        ),    )

    /** The row of a destination, or `null` if the table does not carry it (which the lock refuses). */
    fun de(route: String): RetourRoute? = toutes.firstOrNull { it.route == route }

    /** The destinations of the table, in order. */
    val routes: List<String> get() = toutes.map { it.route }

    /** The destinations with a **fixed fallback**: a single entry, the page left is always the same. */
    val aRepliFixe: List<String> get() = toutes.filter { it.nature == NatureRetour.FIXE }.map { it.route }

    /** The destinations that go up the stack (`if (!navBack()) …`). */
    val remontentLaPile: List<String> get() = toutes.filter { it.nature == NatureRetour.PILE }.map { it.route }
}
