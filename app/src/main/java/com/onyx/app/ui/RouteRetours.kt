package com.onyx.app.ui

/**
 * **La table des retours : une ligne par destination de l'application.**
 *
 * C'est le livrable « chaque retour doit bien revenir où je me trouvais précédemment, dans toute
 * l'App ». La source de vérité reste **le graphe de navigation** (`private enum class Route` et le
 * `when (presentedRoute)` d'`OnyxApp.kt`) ; cette table en est la **traduction lisible**, et le
 * verrou [com.onyx.app.ui.TableDesRoutesVerrouTest] la tient contre le graphe :
 *
 * - une valeur de l'énum **absente** de la table fait échouer le test, **en la nommant** ;
 * - une ligne dont la [declaration] **ne se retrouve plus** dans sa branche (ou son support) fait
 *   échouer le test, **en nommant la destination** — c'est « une route sans retour déclaré » ;
 * - une ligne dont la [declaration] est **vide** fait échouer le test — même verdict ;
 * - une ligne **périmée** (une destination qui n'existe plus dans l'énum) fait échouer le test ;
 * - un [repli] qui ne nomme pas une destination de l'énum fait échouer le test.
 *
 * La table ne remplace pas le code : [declaration] est **le texte exact** qui, dans la source, porte
 * le retour (le `onBack`, le `onClose`, le `onDone`, le `onCancel`, ou le `BackHandler` de la mise en
 * page pour la racine). Le verrou le cherche donc **là où il vit** — dans la branche du routeur, dans
 * le panneau qui porte l'écran, ou dans la mise en page pour le fil.
 */
enum class NatureRetour {
    /** La racine : le retour n'est pas consommé, il rend la main au système (l'app se ferme). */
    RACINE_SYSTEME,

    /** `if (!navBack()) navTo(<repli>)` — un niveau à la fois, puis le repli sémantique. */
    PILE,

    /** Un repli **fixe** : la destination n'a qu'une seule entrée, la page quittée y est toujours la même. */
    FIXE,
}

/** Le support où le verrou cherche [RetourRoute.declaration]. */
const val SUPPORT_BRANCHE = "branche"

/**
 * Une destination et son retour.
 *
 * @param route le nom de la valeur de l'énum `Route` (la clé du graphe).
 * @param ecran l'écran que la branche du routeur affiche réellement (en français).
 * @param nature ce que le retour fait (voir [NatureRetour]).
 * @param declaration **le texte exact** qui porte le retour dans la source ; vide = aucun retour
 *   déclaré, ce que le verrou refuse.
 * @param repli la destination du repli quand la pile est vide, ou `null` quand elle est dynamique
 *   (la page quittée, portée par un état — `contextReturn`).
 * @param support où chercher [declaration] : [SUPPORT_BRANCHE], `panneau:<nom>`, ou `layout`.
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
 * **Les 67 destinations de l'app, chacune avec son retour déclaré.**
 *
 * L'ordre est celui de l'énum pour que la table se lise comme le graphe. Aucune ligne n'est écrite
 * « de mémoire » : chaque [RetourRoute.declaration] est le texte relevé dans `OnyxApp.kt`, et le
 * verrou le re-vérifie à chaque exécution des tests.
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
            route = "TIP_JAR",
            ecran = "Soutenir Onyx",
            nature = NatureRetour.PILE,
            declaration = "onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }",
            repli = "SETTINGS",
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

    /** La ligne d'une destination, ou `null` si la table ne la porte pas (ce que le verrou refuse). */
    fun de(route: String): RetourRoute? = toutes.firstOrNull { it.route == route }

    /** Les destinations de la table, dans l'ordre. */
    val routes: List<String> get() = toutes.map { it.route }

    /** Les destinations à **repli fixe** : une seule entrée, la page quittée y est toujours la même. */
    val aRepliFixe: List<String> get() = toutes.filter { it.nature == NatureRetour.FIXE }.map { it.route }

    /** Les destinations qui remontent la pile (`if (!navBack()) …`). */
    val remontentLaPile: List<String> get() = toutes.filter { it.nature == NatureRetour.PILE }.map { it.route }
}
